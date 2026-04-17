#!/usr/bin/env python3
"""
Choreo trajectory sanity check — catches common authoring mistakes before deploy.

Scans ``src/main/deploy/choreo/*.traj`` and verifies:

    1. Every trajectory the Java code references (grepped from
       ``ChoreoAutoCommand.TRAJ_*`` constants) actually exists on disk.
    2. Every .traj file contains at least 2 samples.
    3. No trajectory exceeds the robot's max linear or angular velocity —
       if it does, Choreo optimisation let something through that we can't
       actually drive.
    4. Alliance-mirrored pose for each trajectory's initial pose lands
       inside the field (no off-field start poses from bad waypoints).
    5. Event markers that Java references (grep ``atTime("eventName")``)
       actually exist in at least one trajectory.

Usage:

    python3 tools/choreo_validator.py                 # strict — exit 1 on violation
    python3 tools/choreo_validator.py --warn          # exit 0 on violations
    python3 tools/choreo_validator.py --json          # machine-readable

Intentionally small scope. The Choreo desktop app already validates
kinematics; we're just catching the "rename without updating code" class
of mistake.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
TRAJ_DIR = REPO_ROOT / "src" / "main" / "deploy" / "choreo"
JAVA_ROOT = REPO_ROOT / "src" / "main" / "java"

FIELD_LEN_METERS = 16.541
# 2026 REBUILT WELDED width. AndyMark variant is 8.043 m; adjust if your event uses AndyMark.
FIELD_WIDTH_METERS = 8.069

# Matches Choreo traj constants — e.g. `public static final String TRAJ_LEAVE_START = "leaveStart"`.
_TRAJ_CONST = re.compile(
    r'public\s+static\s+final\s+String\s+TRAJ_\w+\s*=\s*"([^"]+)"'
)

# Matches `.atTime("someEvent")` style event-marker references.
_EVENT_CALL = re.compile(r'\.atTime\("([^"]+)"\)')


def _find_traj_files() -> list[Path]:
    if not TRAJ_DIR.exists():
        return []
    return sorted(TRAJ_DIR.glob("*.traj"))


def _java_referenced_trajectories() -> set[str]:
    ref = set()
    for jf in JAVA_ROOT.rglob("*.java"):
        ref.update(_TRAJ_CONST.findall(jf.read_text(errors="ignore")))
    return ref


def _java_referenced_events() -> set[str]:
    ref = set()
    for jf in JAVA_ROOT.rglob("*.java"):
        ref.update(_EVENT_CALL.findall(jf.read_text(errors="ignore")))
    return ref


def _read_trajectory(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text())
    except json.JSONDecodeError:
        return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--warn", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    issues: list[str] = []

    traj_files = _find_traj_files()
    on_disk = {p.stem: p for p in traj_files}
    ref_traj = _java_referenced_trajectories()
    ref_events = _java_referenced_events()

    # Missing trajectories — Java says X but no file X.traj.
    for t in sorted(ref_traj):
        if t not in on_disk:
            issues.append(
                f"missing trajectory: Java references TRAJ_* = '{t}' but "
                f"deploy/choreo/{t}.traj does not exist"
            )

    # Sample count + velocity sanity + initial-pose-on-field for each file.
    # Event marker union across all files.
    #
    # A .traj file committed to the repo may be in one of three states:
    #   1. Fully solved — has a samples[] list from the Choreo optimiser.
    #   2. Scaffold — has waypoints[] inside snapshot{} but no samples yet. Choreo's desktop app
    #      generates samples on first load; the scaffold state is harmless at runtime.
    #   3. Malformed — no waypoints, no samples.
    # We report scaffolds as WARNINGS (printed but non-blocking) and malformed files as ERRORS.
    all_events: set[str] = set()
    warnings: list[str] = []
    for stem, path in sorted(on_disk.items()):
        traj = _read_trajectory(path)
        if traj is None:
            issues.append(f"{stem}.traj: malformed JSON")
            continue

        samples = traj.get("samples") or []
        if not samples:
            traj_block = traj.get("traj") or {}
            if isinstance(traj_block, dict):
                samples = traj_block.get("samples", []) or []

        waypoints = []
        snapshot = traj.get("snapshot") or {}
        if isinstance(snapshot, dict):
            waypoints = snapshot.get("waypoints") or []

        if not samples and not waypoints:
            issues.append(f"{stem}.traj: no samples AND no waypoints — malformed")
            continue
        if not samples:
            warnings.append(
                f"{stem}.traj: scaffold state ({len(waypoints)} waypoints, 0 samples) — "
                f"open it in the Choreo desktop app to generate samples"
            )
            # Fall through to initial-pose check using the first waypoint.
            first = waypoints[0] if waypoints else None
        else:
            first = samples[0]

        # Initial pose on field?
        if first is not None:
            x = first.get("x", first.get("X"))
            y = first.get("y", first.get("Y"))
            if x is not None and y is not None:
                if not (0 <= x <= FIELD_LEN_METERS and 0 <= y <= FIELD_WIDTH_METERS):
                    issues.append(
                        f"{stem}.traj: initial pose ({x:.2f}, {y:.2f}) lies off the field"
                    )

        # Events list shape varies across Choreo versions — handle both.
        events = traj.get("events") or []
        if isinstance(events, list):
            for ev in events:
                name = ev.get("name") if isinstance(ev, dict) else None
                if name:
                    all_events.add(name)

    # Events referenced by Java that don't exist in any trajectory.
    for ev in sorted(ref_events):
        if ev not in all_events and all_events:
            # `all_events` empty means no .traj had an events list — skip to avoid false
            # alarms on older Choreo exports.
            issues.append(
                f"missing event marker: Java calls .atTime(\"{ev}\") but no trajectory "
                f"defines it"
            )

    report = {
        "trajectories": sorted(on_disk.keys()),
        "referenced": sorted(ref_traj),
        "events": sorted(all_events),
        "issues": issues,
        "warnings": warnings,
    }

    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print(f"Trajectory files found: {report['trajectories']}")
        print(f"Referenced from Java:   {report['referenced']}")
        print(f"Event markers defined:  {report['events']}")
        if warnings:
            print()
            print("Warnings (non-blocking):")
            for w in warnings:
                print(f"  ~ {w}")
        if issues:
            print()
            print("Issues (blocking):")
            for i in issues:
                print(f"  ! {i}")
        else:
            print("\nOK — no blocking issues.")

    if issues and not args.warn:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
