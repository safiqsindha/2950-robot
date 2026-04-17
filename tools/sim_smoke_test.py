#!/usr/bin/env python3
"""
Headless simulator smoke test.

Boots `./gradlew simulateJavaRelease` under a virtual display, tails the combined
stdout/stderr for a fixed window, and fails if an uncaught exception or
FATAL/ERROR line shows up during that window.

Used in CI to catch startup regressions that a normal `./gradlew build` can't see:
for example, a bad YAGSL config, a missing deploy/ file, a null-dereference in
RobotContainer, or a subsystem whose constructor throws.

Usage:

    python3 tools/sim_smoke_test.py --duration 25

Exit codes:
    0  sim survived the window without logging anything that looks like a failure
    1  sim crashed, exited early, or logged an Exception / ERROR line
    2  usage / tooling error (e.g. gradlew not executable)

Philosophy: the task isn't to exercise the robot. It's to catch the "boots
up at all" regressions that would otherwise surprise us at a practice session.
Keep the window short (20-30 s) and the failure heuristic strict.
"""

from __future__ import annotations

import argparse
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path


# Lines that trip the smoke test.
# Use anchored regex so legit words like "Exception" in a javadoc-style log message
# (e.g. "Running example exception handler demo") don't false-positive.
_FAILURE_PATTERNS = [
    re.compile(r"\bException\b.*at\s+[\w.$]+"),      # stack trace header
    re.compile(r"^\s*at\s+[\w.$]+\([\w.:]+\)"),      # stack-trace line
    re.compile(r"\b(FATAL|SEVERE|ERROR)\b"),         # explicit severity
    re.compile(r"java\.lang\.\w+Error"),             # JVM errors
    re.compile(r"Could not find or load main class"),
]

# Lines we INTENTIONALLY allow — these are WPILib diagnostic noise, not crashes.
_ALLOWLIST_PATTERNS = [
    re.compile(r"DEBUG", re.IGNORECASE),
    # Logger startup banner sometimes mentions "Error" as a log level name.
    re.compile(r"\[Logger\].*level.*ERROR"),
    # The NT warning about mandatory v5 server reassignment is chatty on boot.
    re.compile(r"Network Tables.*server"),
]


def _is_failure(line: str) -> bool:
    if any(p.search(line) for p in _ALLOWLIST_PATTERNS):
        return False
    return any(p.search(line) for p in _FAILURE_PATTERNS)


def run_sim(duration_s: float) -> int:
    """Launch the sim, tail output for `duration_s`, then kill and report."""
    repo_root = Path(__file__).resolve().parent.parent
    gradlew = repo_root / "gradlew"
    if not gradlew.exists():
        print(f"error: gradlew not found at {gradlew}", file=sys.stderr)
        return 2

    # CI needs a DISPLAY for the sim GUI; the caller is responsible for setting
    # one up (xvfb-run wrapper in the workflow). If DISPLAY is unset and we're
    # on a CI-like runner, warn and continue — the sim task might have its own
    # GUI toggle. Don't hard-fail here; let the sim decide.
    if not os.environ.get("DISPLAY") and os.environ.get("CI"):
        print(
            "warning: DISPLAY is not set in a CI environment; "
            "`xvfb-run` wrapping is recommended.",
            file=sys.stderr,
        )

    cmd = [str(gradlew), "simulateJavaRelease", "-Pskip-inspector-wrapper", "--no-daemon"]
    print(f"$ {' '.join(cmd)}")
    proc = subprocess.Popen(
        cmd,
        cwd=str(repo_root),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )

    start = time.monotonic()
    deadline = start + duration_s
    failure_line: str | None = None
    lines_seen = 0

    try:
        assert proc.stdout is not None
        while True:
            now = time.monotonic()
            if now >= deadline:
                break
            # Poll with a short timeout so we can check the deadline regularly.
            proc.stdout.flush() if hasattr(proc.stdout, "flush") else None

            line = proc.stdout.readline()
            if line == "":
                # Sim process exited on its own — that's a crash, because a healthy sim
                # never terminates inside the window.
                if proc.poll() is not None and now < deadline:
                    print(f"FAIL: sim exited after {now - start:.1f}s (returncode={proc.returncode})")
                    return 1
                # EOF on the pipe without an exit — shouldn't happen, but guard anyway.
                time.sleep(0.05)
                continue

            lines_seen += 1
            print(line, end="")
            if _is_failure(line):
                failure_line = line.strip()
                break

    finally:
        # Always tear the sim down cleanly. SIGTERM first, then SIGKILL if it lingers.
        if proc.poll() is None:
            proc.send_signal(signal.SIGTERM)
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=5)

    elapsed = time.monotonic() - start
    if failure_line is not None:
        print(
            f"\nFAIL: matched failure pattern after {elapsed:.1f}s "
            f"({lines_seen} lines processed):\n  {failure_line}"
        )
        return 1

    print(
        f"\nPASS: sim stayed up for {elapsed:.1f}s without logging any failures "
        f"({lines_seen} lines processed)."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--duration",
        type=float,
        default=20.0,
        help="Seconds to keep the sim alive before declaring a pass (default 20).",
    )
    args = parser.parse_args()
    return run_sim(args.duration)


if __name__ == "__main__":
    sys.exit(main())
