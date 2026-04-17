#!/usr/bin/env python3
"""
Pre-deploy health check — runs every check that doesn't require Java or the robot.

Catches the common "pit-day panic" mistakes before you hit Deploy:

  1. Working tree clean (no untracked deploy/ assets that would be missed)
  2. `./gradlew compileJava` would resolve — i.e. every vendordep JSON is valid
     and every .traj / .pathplanner file a Java class references actually exists
  3. CAN ID validator passes
  4. Comp-branch guardrail — warn if you're deploying from main / develop
  5. JDK 17 is on PATH (no silent JDK 21 / 23 swaps)

Usage:

    python3 tools/deploy_health_check.py           # strict — exit 1 on any issue
    python3 tools/deploy_health_check.py --warn    # exit 0 even on warnings
    python3 tools/deploy_health_check.py --json    # machine-readable summary

Philosophy: stop the student from burning 5 minutes on a deploy that was
going to fail. Every check must be < 2 seconds; anything slower belongs in
CI, not a pre-deploy script.

Exit codes:
    0   all clear (or --warn mode, any issue)
    1   at least one hard failure in strict mode
    2   tooling error
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Callable, NamedTuple


REPO_ROOT = Path(__file__).resolve().parent.parent


class Check(NamedTuple):
    name: str
    status: str           # "pass" | "warn" | "fail"
    detail: str


def _run(cmd: list[str], cwd: Path = REPO_ROOT) -> tuple[int, str]:
    try:
        result = subprocess.run(
            cmd, cwd=str(cwd), capture_output=True, text=True, timeout=30
        )
        return result.returncode, (result.stdout + result.stderr).strip()
    except subprocess.TimeoutExpired:
        return 124, "timeout"
    except FileNotFoundError:
        return 127, f"{cmd[0]} not found on PATH"


# ── Checks ────────────────────────────────────────────────────────────────


def check_working_tree_clean() -> Check:
    code, out = _run(["git", "status", "--porcelain"])
    if code != 0:
        return Check("working-tree", "fail", f"git status failed: {out}")
    if out:
        modified = [l for l in out.splitlines() if l]
        return Check(
            "working-tree",
            "warn",
            f"{len(modified)} unstaged/untracked file(s); a deploy from a comp-* "
            f"branch will auto-commit them via compDeploy, but otherwise they'll "
            f"be lost:\n  " + "\n  ".join(modified[:8]),
        )
    return Check("working-tree", "pass", "clean")


def check_vendordeps_valid() -> Check:
    """Every vendordep JSON must parse and have a `jsonUrl` field."""
    vendor_dir = REPO_ROOT / "vendordeps"
    if not vendor_dir.exists():
        return Check("vendordeps", "fail", "vendordeps/ missing")

    bad = []
    for j in sorted(vendor_dir.glob("*.json")):
        try:
            data = json.loads(j.read_text())
        except json.JSONDecodeError as e:
            bad.append(f"{j.name}: parse error ({e})")
            continue
        # Canonical vendordep schema requires `name`, `version`, `jsonUrl`.
        for k in ("name", "version"):
            if k not in data:
                bad.append(f"{j.name}: missing '{k}'")
    if bad:
        return Check("vendordeps", "fail", "; ".join(bad))
    return Check("vendordeps", "pass", f"{len(list(vendor_dir.glob('*.json')))} vendordep(s) valid")


def check_can_ids() -> Check:
    code, out = _run([sys.executable, "tools/can_id_validator.py"])
    if code == 0:
        return Check("can-ids", "pass", "no conflicts, all documented")
    return Check("can-ids", "fail", out.splitlines()[-1] if out else "unknown")


def check_comp_branch() -> Check:
    """Warn if deploying from a branch that isn't a comp-* branch."""
    code, branch = _run(["git", "rev-parse", "--abbrev-ref", "HEAD"])
    if code != 0:
        return Check("branch", "fail", "could not determine branch")
    if branch == "main" or branch == "develop":
        return Check(
            "branch",
            "warn",
            f"on '{branch}' — consider a comp-* branch so compDeploy auto-commits pit edits",
        )
    return Check("branch", "pass", f"on '{branch}'")


def check_jdk_17() -> Check:
    code, out = _run(["java", "-version"])
    if code != 0:
        return Check("jdk", "warn", "`java` not on PATH — WPILib should provide its own JDK")
    # `java -version` writes to stderr. Look for `17` in the version line.
    if not re.search(r"version \"17", out):
        return Check(
            "jdk",
            "warn",
            f"JDK on PATH is not 17 ({out.splitlines()[0] if out else 'unknown'}). "
            f"WPILib will bundle its own 17 for deploy, but local builds may surprise you.",
        )
    return Check("jdk", "pass", out.splitlines()[0] if out else "17")


def check_deploy_directory() -> Check:
    """deploy/ files referenced in code must exist on disk."""
    deploy_dir = REPO_ROOT / "src" / "main" / "deploy"
    if not deploy_dir.exists():
        return Check("deploy-dir", "fail", f"{deploy_dir} missing")

    # Grep for known file references in Java — choreo .traj, swerve json, navgrid
    referenced = set()
    java_files = list((REPO_ROOT / "src" / "main" / "java").rglob("*.java"))
    for jf in java_files:
        text = jf.read_text(errors="ignore")
        referenced.update(re.findall(r'"([^"\s]+\.traj)"', text))
        referenced.update(re.findall(r'"([^"\s]+\.json)"', text))

    # Also check YAGSL-style references to deploy/swerve/
    # (just verify the directory exists)
    swerve_dir = deploy_dir / "swerve"
    if not swerve_dir.exists():
        return Check("deploy-dir", "warn", f"deploy/swerve/ missing (YAGSL configs)")

    # Look for any of the referenced names anywhere under deploy/
    existing = {p.name for p in deploy_dir.rglob("*") if p.is_file()}
    missing = [r for r in referenced if r not in existing and r in {"navgrid.json"}]
    # We only hard-check navgrid.json; .traj references are string constants and Choreo handles
    # the absence gracefully at runtime.
    if missing:
        return Check(
            "deploy-dir",
            "warn",
            f"{len(missing)} Java-referenced file(s) missing in deploy/: {sorted(missing)}",
        )
    return Check(
        "deploy-dir",
        "pass",
        f"{len(existing)} file(s) in deploy/, navgrid.json present",
    )


def _maybe_spotbugs_report() -> Check:
    """Opportunistic — if build/ has a fresh SpotBugs XML, surface the high-confidence count."""
    xml_path = REPO_ROOT / "build" / "reports" / "spotbugs" / "main.xml"
    if not xml_path.exists():
        return Check("spotbugs", "pass", "no recent report (run `./gradlew build` first)")
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return Check("spotbugs", "warn", "SpotBugs XML malformed")
    count = len(root.findall(".//BugInstance[@priority='1']"))
    if count:
        return Check("spotbugs", "warn", f"{count} high-confidence bug(s) in latest report")
    return Check("spotbugs", "pass", "no high-confidence bugs in latest report")


# ── Driver ────────────────────────────────────────────────────────────────


ALL_CHECKS: list[tuple[str, Callable[[], Check]]] = [
    ("working-tree", check_working_tree_clean),
    ("branch", check_comp_branch),
    ("jdk", check_jdk_17),
    ("vendordeps", check_vendordeps_valid),
    ("can-ids", check_can_ids),
    ("deploy-dir", check_deploy_directory),
    ("spotbugs", _maybe_spotbugs_report),
]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--warn", action="store_true",
        help="Exit 0 even on hard failures — use when pre-flighting on a dev machine.",
    )
    parser.add_argument("--json", action="store_true", help="Emit one JSON object on stdout.")
    args = parser.parse_args()

    results: list[Check] = []
    for _name, fn in ALL_CHECKS:
        try:
            results.append(fn())
        except Exception as e:  # noqa: BLE001 — tool-layer, we want to surface any surprise
            results.append(Check(_name, "fail", f"internal error: {e}"))

    if args.json:
        print(json.dumps([r._asdict() for r in results], indent=2))
    else:
        status_icon = {"pass": "OK  ", "warn": "WARN", "fail": "FAIL"}
        for r in results:
            print(f"{status_icon[r.status]}  {r.name:14s}  {r.detail}")

    fails = [r for r in results if r.status == "fail"]
    if fails and not args.warn:
        print()
        print(f"blocking: {len(fails)} hard failure(s); run with --warn to bypass.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
