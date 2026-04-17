# ADR 0007: Supplier injection as the default HAL-free testability pattern

**Status:** Accepted
**Date:** 2026-04
**Author:** @safiqsindha

## Context

A chunk of our code reads real-time inputs from WPILib: `Timer.getFPGATimestamp`, `RobotController.getCANStatus`, `DriverStation.getMatchTime`, `PowerDistribution.getVoltage`. Every one of those transitively loads `wpiHaljni`.

In a JUnit JVM on ubuntu-latest CI, `wpiHaljni` isn't available. The first test that touches HAL crashes the whole test process.

We hit this repeatedly during Phase 6 — `CommandLifecycleLogger`, `AreWeThereYetDebouncer`, `StallDetector` all had to be refactored from "read time directly" to "accept a time source."

## Decision

Any class that reads a WPILib-provided runtime value takes that value through a `Supplier<T>`, `DoubleSupplier`, or `BooleanSupplier` injected via the constructor. The production ctor wraps the real WPILib call; test ctors pass a lambda or a mutable fake.

Canonical examples:

```java
public final class CanBusLogger {
  private final Supplier<CANStatus> statusSupplier;
  public CanBusLogger() { this(RobotController::getCANStatus); }
  public CanBusLogger(Supplier<CANStatus> s) { this.statusSupplier = s; }
  // ...
}

public final class MatchPhaseOverlay {
  private final DoubleSupplier matchTimeRemainingSupplier;
  public MatchPhaseOverlay(
      DoubleSupplier matchTimeRemainingSupplier, /* ... */) { /* ... */ }
}
```

For mutable test state, the `FakeClock` / harness pattern:

```java
static class FakeClock implements DoubleSupplier {
  double now = 0.0;
  public double getAsDouble() { return now; }
  void advance(double s) { now += s; }
}
```

## Consequences

Easier:
- Every time-dependent class is JUnit-testable without HAL.
- Deterministic tests — no `Thread.sleep`, no wall-clock flakiness.
- Spec-driven assertions — "at t=3.0s the fault latches" rather than "wait 3 seconds and check."

Harder:
- Slightly more verbose class signatures (two constructors: production + test).
- New contributors have to learn the pattern — usually one PR comment.

Locked out:
- Direct `Timer.getFPGATimestamp` reads inside any class we expect to cover at unit level. Always a supplier.

## Notes

- Applied across `frc.lib.diagnostics.*`, `frc.lib.util.AreWeThereYetDebouncer`, `frc.robot.subsystems.StallDetector`, and `frc.robot.commands.SystemTestCommand`.
- `DEVELOPER_TESTING_GUIDE.md` is the contributor-facing doc for this.
