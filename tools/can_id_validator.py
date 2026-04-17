#!/usr/bin/env python3
"""
CAN ID validator — static cross-check for FRC Team 2950.

Scans the Java source tree and YAGSL swerve configs for every CAN ID used, and verifies:
  1. No two devices share the same CAN ID.
  2. Every ID declared in `CAN_ID_REFERENCE.md` actually appears somewhere in code.
  3. Every ID referenced in code is listed in `CAN_ID_REFERENCE.md`.

Run from repo root:

    python3 tools/can_id_validator.py

Exit codes:
    0  all checks pass
    1  conflicts or undocumented IDs found (prints a report)

Philosophy: pre-deploy sanity. CAN bus conflicts are the single most common
"won't move in the pit" failure for REV-based swerve. Catch them before the
bot leaves the bench.
"""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent

# Regexes that mine CAN IDs out of various config/source files.
# Each returns (id, context_tag) so conflicts can be reported with a human-friendly label.
_CONSTANTS_ID_RE = re.compile(
    r"""
    public\s+static\s+final\s+int\s+   # "public static final int"
    (k[A-Z][A-Za-z0-9]*(?:Id|ID|CanId|CANId))  # identifier ending in Id/ID/CanId
    \s*=\s*                             # "="
    (\d+)                               # the value
    \s*;
    """,
    re.VERBOSE,
)

_YAGSL_ID_RE = re.compile(r'"id"\s*:\s*(\d+)')


def scan_constants(constants_path: Path) -> list[tuple[int, str]]:
    """Extract (can_id, field_name) from Constants.java."""
    if not constants_path.exists():
        return []
    text = constants_path.read_text()
    return [(int(m.group(2)), m.group(1)) for m in _CONSTANTS_ID_RE.finditer(text)]


def scan_yagsl_configs(swerve_dir: Path) -> list[tuple[int, str]]:
    """Extract (can_id, module_file) from YAGSL deploy/swerve/modules/*.json.

    Skips CANCoder entries — they aren't CAN IDs for mechanism motors.
    YAGSL 2026 schema stores drive/angle motor IDs and the (optional) absolute
    encoder ID at top-level `id` fields. We pull every integer-valued "id" and
    tag it with the filename + outer field for traceability.
    """
    ids: list[tuple[int, str]] = []
    if not swerve_dir.exists():
        return ids

    for json_path in sorted(swerve_dir.glob("modules/*.json")):
        try:
            data = json.loads(json_path.read_text())
        except json.JSONDecodeError as e:
            print(f"warning: could not parse {json_path}: {e}", file=sys.stderr)
            continue

        for section_name in ("drive", "angle"):
            section = data.get(section_name, {})
            if isinstance(section, dict) and "id" in section:
                raw = section["id"]
                if isinstance(raw, int):
                    ids.append((raw, f"{json_path.stem}.{section_name}"))

        encoder = data.get("encoder", {})
        if (
            isinstance(encoder, dict)
            and "id" in encoder
            and isinstance(encoder["id"], int)
            # YAGSL uses -1 / absent to mean "attached" (no separate CAN device).
            and encoder["id"] >= 0
            and encoder.get("type", "").lower() != "attached"
        ):
            ids.append((encoder["id"], f"{json_path.stem}.encoder"))

    return ids


def scan_documented_ids(reference_md: Path) -> set[int]:
    """Pull every numeric CAN ID out of CAN_ID_REFERENCE.md.

    The reference table formats IDs as `**N**` in markdown. Any bolded integer
    in the file is treated as a declared CAN ID. This is intentionally
    permissive — false positives are cheap, false negatives would silently
    let a new device slip in undocumented.
    """
    if not reference_md.exists():
        return set()
    text = reference_md.read_text()
    # Match **N** where N is a 1-3 digit integer — CAN IDs are 0-62 by REV spec.
    return {int(m.group(1)) for m in re.finditer(r"\*\*(\d{1,2})\*\*", text)}


def _format_conflicts(conflicts: dict[int, list[str]]) -> str:
    lines = ["CAN ID CONFLICTS:"]
    for can_id in sorted(conflicts):
        owners = ", ".join(conflicts[can_id])
        lines.append(f"  id {can_id}: {owners}")
    return "\n".join(lines)


def main() -> int:
    constants = scan_constants(
        REPO_ROOT / "src/main/java/frc/robot/Constants.java"
    )
    yagsl = scan_yagsl_configs(REPO_ROOT / "src/main/deploy/swerve")
    documented = scan_documented_ids(REPO_ROOT / "CAN_ID_REFERENCE.md")

    all_found: list[tuple[int, str]] = constants + yagsl

    # Detect conflicts — the same CAN ID used by two different devices.
    by_id: dict[int, list[str]] = defaultdict(list)
    for can_id, owner in all_found:
        by_id[can_id].append(owner)
    conflicts = {k: v for k, v in by_id.items() if len(v) > 1}

    used_ids = set(by_id.keys())
    undocumented = used_ids - documented
    # Not fatal: documented-but-unused (e.g. SideClaw on a test robot) is OK.
    unused = documented - used_ids

    failed = bool(conflicts or undocumented)

    print(f"Scanned {len(constants)} IDs in Constants.java")
    print(f"Scanned {len(yagsl)} IDs in YAGSL module configs")
    print(f"Documented in CAN_ID_REFERENCE.md: {sorted(documented)}")
    print(f"Used in code: {sorted(used_ids)}")

    if conflicts:
        print()
        print(_format_conflicts(conflicts))

    if undocumented:
        print()
        print("UNDOCUMENTED CAN IDs (in code but missing from CAN_ID_REFERENCE.md):")
        for can_id in sorted(undocumented):
            owners = ", ".join(by_id[can_id])
            print(f"  id {can_id}: {owners}")

    if unused:
        # Informational only.
        print()
        print("NOTE: documented IDs not currently referenced in code: "
              f"{sorted(unused)}")

    if failed:
        print()
        print("FAIL — fix the above before deploying.")
        return 1
    print()
    print("OK — no CAN ID conflicts; every used ID is documented.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
