# ADR 0001: Adopt the 2590 IO-layer pattern for all subsystems

**Status:** Accepted
**Date:** 2026-04 (Phase 4 of the offseason refactor)
**Author:** @safiqsindha

## Context

Our 2025 code had subsystems that directly wrapped SPARK MAX / SPARK Flex. This worked fine on the real robot but made simulation nearly impossible — every test needed HAL, every sim run pretended to drive real hardware. It also made swapping hardware painful (e.g. moving a motor to a different SPARK requires editing the subsystem class).

Team 2590 and its successors (4481 Rembrandts, 6328 Mechanical Advantage) converged on a 4-class pattern:

- `XxxIO` — Java interface describing hardware capabilities
- `XxxIOInputs` — record of sensor readings (with `@AutoLog` from AdvantageKit)
- `XxxIOReal` — SPARK-backed implementation
- `XxxIOSim` — pure-Java or DCMotorSim-backed implementation
- `Xxx extends SubsystemBase` — thin consumer that owns `XxxIO` + telemetry + tunables

Alternatives considered:

1. Keep old wrappers — simple but sim-hostile.
2. Wholly separate "fake" and "real" subsystem classes — duplicated surface area, easy to drift.
3. IO pattern — overhead of 4 files per subsystem but clean separation.

## Decision

Every new subsystem ships as the 4-file IO pattern. Existing subsystems were refactored in Phase 4 (Flywheel, Intake, Conveyor).

Exceptions:

- `SwerveSubsystem` wraps YAGSL's `SwerveDrive`; YAGSL *is* the IO layer, so the four-class pattern would be redundant.
- `VisionSubsystem` reads Limelight NetworkTables; no hardware handle to mock, so no benefit to abstraction.
- Pure state machines (`SuperstructureStateMachine`, `StallDetector`) don't touch hardware — no IO layer needed.

## Consequences

Easier:
- Testing. Run subsystem logic against `XxxIOSim` without HAL.
- Simulation. `DCMotorSim` integration is already scoped inside `XxxIOSim`.
- Hardware swaps. Change `XxxIOReal` without touching the subsystem.
- AdvantageKit replay. `@AutoLog` covers every field.

Harder:
- Per-subsystem line count roughly doubles (from ~80 to ~200 lines across 4 files).
- New subsystems cost ~30 minutes of scaffolding before the first test runs.

Locked out:
- Fields can't be `private final` across the IO boundary — the interface forces setters rather than immediable construction. This is a trade-off we accept.

## Notes

- Reference: `/tmp/t2590/2025_Robot_Base_Project/src/main/java/frc/robot/subsystems/intake/` (cloned during offseason scout)
- Phase-4 PRs: #8–#10 (Flywheel, Intake, Conveyor refactors)
- `CODE_TOUR.md` has the current canonical example.
