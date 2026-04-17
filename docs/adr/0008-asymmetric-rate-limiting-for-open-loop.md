# ADR 0008: Asymmetric rate limiting for open-loop mechanisms

**Status:** Accepted
**Date:** 2026-04
**Author:** @safiqsindha

## Context

The Flywheel subsystem adopted `LinearProfile` (symmetric rate limiter) for its closed-loop velocity setpoint — step changes into the PID were causing needless current spikes.

Applying the same pattern to the Intake wheel + Conveyor (both open-loop percent output) raised a new concern: what happens on a panic-button interrupt? A symmetric ramp-down leaves the motor running at partial output for the full ramp window (250 ms at `kMaxWheelAccelPerSec = 4.0`). That's bad for safety — "emergency stop" must mean "stop now."

Alternatives:

1. **Skip rate limiting entirely on open-loop** — no current smoothing, but instant stop.
2. **Symmetric ramp with a snap override** — requires every caller that wants instant stop to call a special `hardStop()` method. Easy to miss.
3. **Asymmetric limiter** — ramp *up* at the configured rate, snap *down* regardless.

## Decision

New `frc.lib.control.AsymmetricRateLimiter` — rate-limits toward higher magnitudes, snaps toward zero. A sign flip (positive → negative or vice versa) snaps through zero in a single tick and ramps up from there.

Wired into `Intake.setWheel`. `Conveyor.setConveyor` is a candidate for the same pattern but was not rate-limited in the initial integration — the belt motors are brushed, fast response, low current spike risk. Revisit if we observe current issues.

## Consequences

Easier:
- Panic button → `setWheel(0)` → motor stops this tick. No half-speed coasting.
- Ramp-up still smooths the initial current transient.

Harder:
- Students learning the pattern need to understand why the ramp is asymmetric — easy to explain but non-obvious on first read.
- Two rate-limiter classes (`LinearProfile` + `AsymmetricRateLimiter`) in `frc.lib.control`. Clear naming keeps confusion minimal.

Locked out:
- A callers that legitimately wants a controlled ramp-DOWN can't use `AsymmetricRateLimiter`. Use `LinearProfile` instead.

## Notes

- Implementation: `src/main/java/frc/lib/control/AsymmetricRateLimiter.java`
- 13 HAL-free unit tests cover ramp-up, snap-down, sign-flip-through-zero, and the realistic panic scenario.
- Tuning lives in `Constants.Intake.kMaxWheelAccelPerSec` (4.0 /s → full output in 0.25 s).
