#!/usr/bin/env bash
#
# Install the repo's git pre-commit hook — runs quick sanity checks before every commit.
#
# Usage:
#   bash tools/install_hooks.sh
#
# What the pre-commit hook does:
#   1. tools/can_id_validator.py (under 1 s) — CAN conflict check
#   2. tools/choreo_validator.py (under 1 s) — Choreo JSON sanity
#   3. Scan for System.out / System.err in src/main/java (under 0.1 s)
#
# What it DOES NOT do: run ./gradlew build. That's CI's job. The hook should stay under
# 3 seconds total so students don't learn to `--no-verify` every commit.
#
# Safe to run multiple times — the hook is idempotent; re-running just refreshes it.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || {
  echo "error: not inside a git repo" >&2
  exit 1
})"
HOOK_PATH="$REPO_ROOT/.git/hooks/pre-commit"

cat > "$HOOK_PATH" <<'HOOK'
#!/usr/bin/env bash
# 2950 pre-commit hook. Installed by tools/install_hooks.sh — edit there, not here.

set -e
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "2950 pre-commit — sanity checks"

# 1. CAN ID conflict check
if ! python3 tools/can_id_validator.py > /tmp/2950_can.log 2>&1; then
  echo "--- CAN ID validator failed ---"
  cat /tmp/2950_can.log
  echo "--- commit blocked ---"
  exit 1
fi

# 2. Choreo validator — non-blocking warnings are fine; only fail on issues.
if ! python3 tools/choreo_validator.py > /tmp/2950_choreo.log 2>&1; then
  echo "--- Choreo validator failed ---"
  cat /tmp/2950_choreo.log
  echo "--- commit blocked ---"
  exit 1
fi

# 3. No System.out in src/main/java — ArchUnit will catch this in CI, but fail fast locally too.
if git diff --cached --name-only --diff-filter=ACMRTUXB -- 'src/main/java/**/*.java' \
   | xargs -I {} git show ":{}" 2>/dev/null \
   | grep -E '^[+].*System\.(out|err)\.print' > /dev/null; then
  echo "--- added System.out/err in staged Java ---"
  echo "Use Logger.recordOutput or DriverStation.reportError instead."
  echo "--- commit blocked ---"
  exit 1
fi

echo "2950 pre-commit — OK"
HOOK

chmod +x "$HOOK_PATH"

echo "Installed $HOOK_PATH"
echo "To skip the hook for one commit: git commit --no-verify"
