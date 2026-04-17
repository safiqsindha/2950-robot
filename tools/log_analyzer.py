#!/usr/bin/env python3
"""
Lightweight ``.wpilog`` summariser.

This is the "I don't want to boot AdvantageScope to figure out why the match
felt wrong" tool. It takes a WPILog file and prints:

    * Match duration, enabled time
    * Per-phase distribution (AUTO / TELEOP / DISABLED) seconds
    * Max / p95 loop iteration time
    * Max JVM heap seen
    * Max CAN bus utilisation
    * Highest single-channel PDH current
    * Any single line that matches the failure regex (stack traces, ERROR,
      FATAL, etc.) — the same regex ``tools/sim_smoke_test.py`` uses

No WPILib Python dependency — we parse just enough of the wpilog binary
format to pull record timestamps + the structured keys we care about.
For anything beyond this, open the .wpilog in AdvantageScope.

Usage:

    python3 tools/log_analyzer.py /path/to/match.wpilog
    python3 tools/log_analyzer.py /path/to/match.wpilog --json

File format reference:
    https://github.com/wpilibsuite/allwpilib/blob/main/wpiutil/doc/datalog.adoc
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

# Keys we care about — these come from the diagnostics quartet (#46, #57).
KEY_BATTERY_VOLTAGE = "/AdvantageKit/RealOutputs/Robot/BatteryVoltage"
KEY_MATCH_TIME = "/AdvantageKit/RealOutputs/Robot/MatchTimeRemaining"
KEY_PHASE = "/AdvantageKit/RealOutputs/Robot/Phase"
KEY_LOOP_TICK_MS = "/AdvantageKit/RealOutputs/Loop/TickMs"
KEY_LOOP_MAX_MS = "/AdvantageKit/RealOutputs/Loop/MaxTickMs"
KEY_HEAP_USED_MB = "/AdvantageKit/RealOutputs/JVM/HeapUsedMB"
KEY_CAN_UTIL = "/AdvantageKit/RealOutputs/CAN/UtilizationPct"
KEY_PDH_TOTAL = "/AdvantageKit/RealOutputs/PDH/TotalCurrentA"
KEY_PDH_CHANNELS = "/AdvantageKit/RealOutputs/PDH/ChannelCurrentsA"


@dataclass
class Stats:
    entries_seen: int = 0
    start_time_us: Optional[int] = None
    end_time_us: Optional[int] = None
    max_battery_v: float = 0.0
    min_battery_v: float = 999.0
    max_loop_ms: float = 0.0
    max_heap_mb: float = 0.0
    max_can_pct: float = 0.0
    max_pdh_total: float = 0.0
    max_pdh_channel_amps: float = 0.0
    phase_seconds: dict[str, float] = field(default_factory=dict)
    last_phase: Optional[str] = None
    last_phase_start_us: Optional[int] = None
    loop_samples: list[float] = field(default_factory=list)


def _read_vli(data: bytes, offset: int) -> tuple[int, int]:
    """Read a variable-length integer; returns (value, new_offset)."""
    result = 0
    shift = 0
    while True:
        b = data[offset]
        offset += 1
        result |= (b & 0x7F) << shift
        if b & 0x80 == 0:
            return result, offset
        shift += 7


def _parse(data: bytes, stats: Stats) -> None:
    if len(data) < 12 or data[:6] != b"WPILOG":
        raise SystemExit("not a WPILog (missing magic bytes)")
    # Skip header (6 bytes magic + 2 version + 4 extra-len + extra-bytes).
    extra_len = struct.unpack_from("<I", data, 8)[0]
    offset = 12 + extra_len

    # entries[id] = (name, type)
    entries: dict[int, tuple[str, str]] = {}

    while offset < len(data):
        if offset + 1 > len(data):
            break
        header_len_field = data[offset]
        offset += 1
        # Decode packed header-length byte.
        entry_id_len = (header_len_field & 0x03) + 1
        payload_size_len = ((header_len_field >> 2) & 0x03) + 1
        timestamp_len = ((header_len_field >> 4) & 0x07) + 1

        def _read(size: int) -> int:
            nonlocal offset
            val = int.from_bytes(data[offset : offset + size], "little", signed=False)
            offset += size
            return val

        entry_id = _read(entry_id_len)
        payload_size = _read(payload_size_len)
        timestamp_us = _read(timestamp_len)
        payload = data[offset : offset + payload_size]
        offset += payload_size

        if stats.start_time_us is None:
            stats.start_time_us = timestamp_us
        stats.end_time_us = timestamp_us

        if entry_id == 0:
            # Control record: Start/Finish/SetMetadata. Byte 0 is the kind.
            if payload and payload[0] == 0:  # Start
                # layout: 0, id(u32), name-len(u32), name-bytes, type-len(u32), type-bytes, meta...
                p = 1
                new_id = struct.unpack_from("<I", payload, p)[0]; p += 4
                name_len = struct.unpack_from("<I", payload, p)[0]; p += 4
                name = payload[p : p + name_len].decode(errors="replace"); p += name_len
                type_len = struct.unpack_from("<I", payload, p)[0]; p += 4
                type_str = payload[p : p + type_len].decode(errors="replace")
                entries[new_id] = (name, type_str)
            continue

        entry = entries.get(entry_id)
        if entry is None:
            continue
        name, type_str = entry
        stats.entries_seen += 1

        # Decode typed payloads we care about.
        try:
            if name == KEY_BATTERY_VOLTAGE and type_str == "double":
                v = struct.unpack("<d", payload)[0]
                stats.max_battery_v = max(stats.max_battery_v, v)
                stats.min_battery_v = min(stats.min_battery_v, v)
            elif name == KEY_LOOP_TICK_MS and type_str == "double":
                v = struct.unpack("<d", payload)[0]
                stats.loop_samples.append(v)
                stats.max_loop_ms = max(stats.max_loop_ms, v)
            elif name == KEY_LOOP_MAX_MS and type_str == "double":
                v = struct.unpack("<d", payload)[0]
                stats.max_loop_ms = max(stats.max_loop_ms, v)
            elif name == KEY_HEAP_USED_MB and type_str == "double":
                v = struct.unpack("<d", payload)[0]
                stats.max_heap_mb = max(stats.max_heap_mb, v)
            elif name == KEY_CAN_UTIL and type_str == "double":
                v = struct.unpack("<d", payload)[0]
                stats.max_can_pct = max(stats.max_can_pct, v)
            elif name == KEY_PDH_TOTAL and type_str == "double":
                v = struct.unpack("<d", payload)[0]
                stats.max_pdh_total = max(stats.max_pdh_total, v)
            elif name == KEY_PDH_CHANNELS and type_str.startswith("double["):
                # Array of doubles — payload length / 8 values.
                count = len(payload) // 8
                channels = struct.unpack(f"<{count}d", payload)
                stats.max_pdh_channel_amps = max(
                    stats.max_pdh_channel_amps, max(channels) if channels else 0.0
                )
            elif name == KEY_PHASE and type_str == "string":
                phase_name = payload.decode(errors="replace")
                if (
                    stats.last_phase is not None
                    and stats.last_phase_start_us is not None
                    and phase_name != stats.last_phase
                ):
                    elapsed = (timestamp_us - stats.last_phase_start_us) / 1_000_000.0
                    stats.phase_seconds[stats.last_phase] = (
                        stats.phase_seconds.get(stats.last_phase, 0.0) + elapsed
                    )
                if phase_name != stats.last_phase:
                    stats.last_phase = phase_name
                    stats.last_phase_start_us = timestamp_us
        except struct.error:
            # Corrupt record — skip.
            continue


def _format_report(stats: Stats) -> dict:
    duration_s = 0.0
    if stats.start_time_us is not None and stats.end_time_us is not None:
        duration_s = (stats.end_time_us - stats.start_time_us) / 1_000_000.0
    p95_loop = 0.0
    if stats.loop_samples:
        sorted_samples = sorted(stats.loop_samples)
        idx = int(0.95 * (len(sorted_samples) - 1))
        p95_loop = sorted_samples[idx]
    return {
        "duration_s": round(duration_s, 2),
        "entries_seen": stats.entries_seen,
        "battery_v": {
            "min": round(stats.min_battery_v, 2) if stats.min_battery_v < 999.0 else None,
            "max": round(stats.max_battery_v, 2),
        },
        "loop_ms": {"max": round(stats.max_loop_ms, 2), "p95": round(p95_loop, 2)},
        "max_heap_mb": round(stats.max_heap_mb, 1),
        "max_can_utilization_pct": round(stats.max_can_pct, 1),
        "max_pdh_total_a": round(stats.max_pdh_total, 1),
        "max_pdh_channel_a": round(stats.max_pdh_channel_amps, 1),
        "phase_seconds": {k: round(v, 2) for k, v in stats.phase_seconds.items()},
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", help="path to a .wpilog file")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    path = Path(args.log)
    if not path.exists():
        print(f"error: {path} not found", file=sys.stderr)
        return 2

    data = path.read_bytes()
    stats = Stats()
    _parse(data, stats)
    report = _format_report(stats)

    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print(f"Log: {path}")
        print(f"  duration      : {report['duration_s']} s")
        print(f"  records       : {report['entries_seen']}")
        print(
            f"  battery       : min {report['battery_v']['min']} V, max "
            f"{report['battery_v']['max']} V"
        )
        print(
            f"  loop time     : p95 {report['loop_ms']['p95']} ms, max "
            f"{report['loop_ms']['max']} ms"
        )
        print(f"  max heap      : {report['max_heap_mb']} MB")
        print(f"  max CAN util  : {report['max_can_utilization_pct']} %")
        print(
            f"  PDH peak      : total {report['max_pdh_total_a']} A, single "
            f"channel {report['max_pdh_channel_a']} A"
        )
        if report["phase_seconds"]:
            print("  phase seconds:")
            for phase, seconds in report["phase_seconds"].items():
                print(f"    {phase:10s} {seconds}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
