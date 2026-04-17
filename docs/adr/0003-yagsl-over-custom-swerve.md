# ADR 0003: Use YAGSL instead of a hand-rolled swerve stack

**Status:** Accepted
**Date:** 2026-04 (inherited from the 2025 swerve-test branch)
**Author:** @safiqsindha

## Context

FRC swerve kinematics + module control + odometry is 500+ lines of careful code. Several teams publish library-grade abstractions:

- **YAGSL** (Yet Another Generic Swerve Library) — REV/CTRE/mixed support, JSON-configured, in-tree copy
- **CTRE SwerveDrivetrain** — vendor-specific, requires CTRE Phoenix6
- **Hand-rolled** (WPILib primitives) — total control, total responsibility

The 2025 team settled on YAGSL after a swerve-test branch that got the drivetrain tuned. We inherited that decision.

Alternatives at the Phase-0 audit point:

1. Keep YAGSL — lowest friction, already working.
2. Migrate to CTRE — would require buying CTRE motor controllers (currently REV-only).
3. Hand-roll — probably a 2-week project we don't have budget for.

## Decision

Stay on YAGSL. Vendor it in-tree under `swervelib/` so a YAGSL bug-fix or API change doesn't blindside us in the middle of a season. (YAGSL ships a repackaged maple-sim at `swervelib.simulation.ironmaple.simulation.*` — this caused a confusing test-compile bug during the Phase-6 scout; see [`MAPLE_SIM_BUG_REPORT.md`](../../MAPLE_SIM_BUG_REPORT.md).)

## Consequences

Easier:
- Module JSON edits are the only change needed when hardware swaps (encoder offsets, CAN IDs, gear ratios).
- YAGSL ships SysId command builders (`SwerveDriveTest.setDriveSysIdRoutine`); we use them directly.
- AdvantageKit integration is straightforward (YAGSL emits NT4; AdvantageKit reads NT4).

Harder:
- YAGSL is a large API surface — students need to learn *its* patterns on top of WPILib's.
- Upgrading YAGSL requires a test session; the in-tree copy lets us rev at our own pace.

Locked out:
- CTRE motor controllers without a YAGSL adapter (YAGSL supports CTRE already, so we're not blocked if we ever add one).

## Notes

- Reference: https://github.com/Yet-Another-Software-Suite, https://docs.yagsl.com
- Repackaged namespace gotcha: `import org.ironmaple.*` will NOT compile. Use `import swervelib.simulation.ironmaple.simulation.*`.
- `Constants.Swerve.kUseMapleSimKinematicBypass` is our workaround for the maple-sim REV sign bug (see ADR 0005).
