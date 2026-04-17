# ADR 0002: Use AdvantageKit `@AutoLog` for structured telemetry

**Status:** Accepted
**Date:** 2026-04 (Phase 0.2)
**Author:** @safiqsindha

## Context

WPILib's `NetworkTables.getEntry(...).setDouble(...)` is the default "publish a number" API. It works, but:

- Every caller writes the key string by hand → typos, inconsistent naming
- No replay — if you miss an event live, you can't go back
- No struct types — a `Pose2d` becomes four separate NT keys with no type info

Team 6328's AdvantageKit solves all three:

- `@AutoLog` on an IO inputs record generates a structured publisher
- `Logger.recordOutput(key, value)` accepts WPILib types (Pose2d, ChassisSpeeds, arrays) directly
- Every output is written to a `.wpilog` file on USB and simultaneously mirrored to NT4 for live viewing
- AdvantageScope replays the log offline — exact same visualisation as live

Alternatives:

1. Hand-rolled NT publishing — works, but opt-in and error-prone.
2. CTRE SignalLogger — CTRE-specific; we're REV-only.
3. AdvantageKit — batteries-included, well-documented, adopted by half the Einstein-tier teams.

## Decision

Every subsystem uses `@AutoLog` on its IO inputs record. Every command or logic class that wants to publish telemetry uses `Logger.recordOutput(key, value)` directly.

## Consequences

Easier:
- Replay during post-match debrief. Load the `.wpilog` in AdvantageScope, scrub to the moment something broke.
- Consistent key naming — the `@AutoLog` processor uses the field name.
- Type-aware visualisation — Pose2d renders as an arrow on the field, ChassisSpeeds as a vector.

Harder:
- Requires the annotation-processor dependency in `build.gradle` + `vendordeps/AdvantageKit.json`. One-time setup cost.
- Generated `*AutoLogged` classes show up in test / SpotBugs output — added the appropriate exclusions.

Locked out:
- We don't use CTRE's SignalLogger. If we ever add a CTRE motor controller we'd need to bridge it into AdvantageKit.

## Notes

- Upstream: https://github.com/Mechanical-Advantage/AdvantageKit
- `TELEMETRY_REFERENCE.md` lists every key the robot publishes.
- `JvmLogger`, `CanBusLogger`, `PdhLogger`, `LoopTimeLogger` follow the same pattern.
