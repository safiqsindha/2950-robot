#!/usr/bin/env python3
"""
Encoder offset helper — read YAGSL module JSONs, write back updated offsets.

Workflow:

    1. Point all swerve wheels forward (bevel gears aligned) on the robot.
    2. Deploy current code so the SmartDashboard / NT shows
       ``Drive/Module N/Absolute Encoder Position`` for each module.
    3. Run this script with the four observed positions:

           python3 tools/encoder_offset_finder.py --fl 0.123 --fr 0.456 \\
               --bl 0.789 --br 0.012

       It rewrites the ``absoluteEncoderOffset`` field in each
       ``src/main/deploy/swerve/modules/*.json`` in place and prints a
       diff-friendly summary.

    4. Redeploy and verify the robot drives straight.

Safety:

    * The script never touches CAN IDs, inversion flags, or any field
      other than ``absoluteEncoderOffset``.
    * Backups are written next to each file as ``*.json.bak`` before
      overwriting.
    * Passing ``--dry-run`` prints the changes without writing.

Design note: next year we'll probably want to drive this from the
.wpilog — let the script read a log, pull the four absolute encoder
positions at the moment the team flagged them, and apply. That avoids
human transcription errors. For now the CLI args keep this usable in
the pit without a log-parse dependency.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
MODULE_DIR = REPO_ROOT / "src" / "main" / "deploy" / "swerve" / "modules"


# Maps the argparse flag → the filename prefix we expect in deploy/swerve/modules/.
_FLAGS = [
    ("fl", "frontleft"),
    ("fr", "frontright"),
    ("bl", "backleft"),
    ("br", "backright"),
]


def _find_module_json(flag_name: str) -> Path:
    """Locate the JSON for a given corner. Case-insensitive filename match."""
    if not MODULE_DIR.exists():
        raise SystemExit(f"error: {MODULE_DIR} missing")
    for p in MODULE_DIR.iterdir():
        if p.is_file() and p.suffix == ".json" and flag_name in p.stem.lower():
            return p
    raise SystemExit(f"error: no module JSON found for '{flag_name}' in {MODULE_DIR}")


def _update_offset(path: Path, new_offset: float, dry_run: bool) -> tuple[float, float]:
    """Rewrite the absoluteEncoderOffset field. Returns (old, new)."""
    data = json.loads(path.read_text())
    old = data.get("absoluteEncoderOffset", 0.0)
    if dry_run:
        return old, new_offset
    # Preserve any line-ending / indent style as best we can.
    shutil.copyfile(path, path.with_suffix(path.suffix + ".bak"))
    data["absoluteEncoderOffset"] = new_offset
    path.write_text(json.dumps(data, indent=2) + "\n")
    return old, new_offset


def main() -> int:
    parser = argparse.ArgumentParser()
    for flag, _file in _FLAGS:
        parser.add_argument(
            f"--{flag}",
            type=float,
            required=True,
            help=f"observed absolute encoder position for {flag} (rotations)",
        )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="print changes without writing (or creating .bak files)",
    )
    args = parser.parse_args()
    mode = "DRY RUN" if args.dry_run else "WRITE"
    print(f"Encoder offset update ({mode})")
    print(f"  repo: {REPO_ROOT}")
    print()

    any_change = False
    for flag, name in _FLAGS:
        path = _find_module_json(name)
        new_val = getattr(args, flag)
        old, new = _update_offset(path, new_val, args.dry_run)
        changed = "" if abs(old - new) < 1e-9 else "  (changed)"
        print(f"  {path.name:30s}  {old:+.6f} -> {new:+.6f}{changed}")
        if changed:
            any_change = True

    print()
    if args.dry_run:
        print("DRY RUN: no files written. Re-run without --dry-run to apply.")
    elif any_change:
        print(
            "OK. Backup files left next to each *.json as *.json.bak. Redeploy "
            "and drive the robot to verify it tracks straight."
        )
    else:
        print("No changes detected. Files untouched.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
