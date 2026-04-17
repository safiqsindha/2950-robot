# ADR 0006: `frc.lib` stays HAL-free and has no `frc.robot` dependencies

**Status:** Accepted
**Date:** 2026-04
**Author:** @safiqsindha

## Context

Every year, FRC teams rewrite ~70% of their robot code to match the new game's mechanisms. The ~30% that stays the same is infrastructure: drive utilities, telemetry, diagnostics, trajectory following, etc.

If that 30% is tangled with game-specific logic, the rewrite cost is higher — you can't lift it cleanly into next year's repo. If it's cleanly separated, the next kickoff starts with a working stack and only the game logic to build.

## Decision

Split the source tree into two packages:

- `frc.lib.*` — reusable, HAL-free, no `frc.robot` / `swervelib` dependencies. Every class here transfers year-over-year verbatim.
- `frc.robot.*` — this year's robot. Subsystems, commands, game-specific state machines, game-specific constants.

Invariants (enforced by ArchUnit; see ADR 0004):

1. `frc.lib.*` may not depend on `frc.robot.*`.
2. `frc.lib.*` may not depend on `swervelib.*` (drivetrain-agnostic).
3. `frc.lib.*` classes must be testable without HAL — use `DoubleSupplier` injection for time, no `SubsystemBase` subclassing, no direct SPARK imports.

JaCoCo gates `frc.lib.*` at 80% line coverage (enforced). `frc.robot.*` is not gated because much of it requires HAL.

## Consequences

Easier:
- Lifting to next year. Every class under `frc.lib.*` is a candidate for a submodule / published JAR / template repo.
- Unit testing. `frc.lib.*` code has no HAL dependency, so tests run in milliseconds.
- Confidence in the library layer — 80% coverage means real assertions, not just "compiles."

Harder:
- New utilities must sometimes take tuning params via constructor rather than referencing `Constants.*`. Minor API overhead.
- The first time a student needs a utility, they ask "where does this go?" — the split forces them to think about layering.

Locked out:
- Shared state between the library and robot code. `frc.lib` can't reach down to `frc.robot` for state; all dependencies go the other direction. Intentional.

## Notes

- Enforcement: `src/test/java/frc/lib/ArchitectureTest.java`
- Candidate next-year move: publish `frc.lib.*` as `com.team2950:robot-lib:YYYY.N.N` (Gradle vendordep JSON). See the session's "next-season strategy" discussion for the full plan.
- Seed repo template for next season would include everything in `frc.lib.*` pre-wired.
