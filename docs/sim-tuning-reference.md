# Sim Tuning Reference

Where the simulation's knobs live and what each one does. Complements [`SIM_QUICKSTART.md`](../SIM_QUICKSTART.md) (how to run the sim) and [`SIM_VALIDATION_SCRIPT.md`](../SIM_VALIDATION_SCRIPT.md) (how to verify sim behaves correctly).

---

## Robot-level

| Setting | Default | Location | Effect |
|---|---|---|---|
| `kUseMapleSimKinematicBypass` | `true` | `Constants.Swerve` | When `true`, `SwerveSubsystem` manually integrates pose from commanded speeds in sim, bypassing maple-sim physics. Works around the REV NEO force-direction bug (ADR 0005). Flip to `false` once upstream fixes the REV sign convention. |
| `kBrownoutThresholdVolts` | 8.0 V | `Robot.java` | Voltage below which `Robot.getBrownoutScale()` starts derating. |
| `kBrownoutFloorVolts` | 6.0 V | `Robot.java` | Voltage at which brownout scale hits 0.5 (hard floor). |
| `kCriticalVoltageVolts` | 6.5 V | `Robot.java` | Voltage below which a critical-warning log key fires. |

## Flywheel sim

| Setting | Default | Location | Effect |
|---|---|---|---|
| `kSimJKgM2` | 0.004 | `FlywheelIOSim` | Flywheel disk moment of inertia. Lower → faster spin-up in sim. |
| `kSimKpVoltsPerRpm` | 0.008 | `FlywheelIOSim` | Software P gain for sim's P+FF controller. Separate from real `kP`. |
| `kMaxAccelRpmPerSec` | 8000 | `Constants.Flywheel` | Setpoint slew rate (via `LinearProfile`). Live-tunable via NT. |
| `kReadyThreshold` | 0.10 (10%) | `Constants.Flywheel` | "AtSpeed" tolerance — `isAtSpeed()` returns true within this fraction of goal. |

## Intake sim

| Setting | Default | Location | Effect |
|---|---|---|---|
| `simWheelCurrentAmps` at full output | 30 A | `IntakeIOSim` | Synthesized current when `simGamePieceAcquired == true`. Must exceed `kGamePieceCurrentThresholdAmps` (15 A) so the SSM advances INTAKING → STAGING. |
| `kMaxArmAccelRotPerSec` | 100 rot/s | `Constants.Intake` | Arm position slew rate via `LinearProfile`. |
| `kMaxWheelAccelPerSec` | 4.0 /s | `Constants.Intake` | Wheel percent slew via `AsymmetricRateLimiter` (snap to zero on cleanup). |

## Climber sim (scaffold)

| Setting | Default | Location | Effect |
|---|---|---|---|
| `kSimTauSeconds` | 0.1 | `ClimberIOSim` | Time constant of the first-order position response. |
| `kSimCurrentAtFullOutput` | 25 A | `ClimberIOSim` | Synthesized current at full open-loop output. |

## SideClaw sim (scaffold)

| Setting | Default | Location | Effect |
|---|---|---|---|
| `kSimCurrentAtFullOutput` | 15 A | `SideClawIOSim` | Synthesized current at full percent. No "grabbed object" model — pure open-loop. |
| `kMaxPercentPerSec` | 5.0 /s | `SideClaw` | Claw percent slew via `AsymmetricRateLimiter`. |

## Vision sim

| Setting | Default | Location | Effect |
|---|---|---|---|
| `kResetInhibitionSeconds` | 0.12 | `Constants.Vision` | Window after `resetOdometry` / `zeroGyro` during which vision frames are dropped. |
| `kMaxLinearSpeedForVisionMps` | 4.0 | `Constants.Vision` | Skip vision when robot's linear speed exceeds this — Limelight latency corrupts fast poses. |
| `kMaxCorrectionAutoMeters` | 0.5 | `Constants.Vision` | Reject vision if it would shift the pose by more than this in auto (tight). |
| `kMaxCorrectionTeleopMeters` | 1.0 | `Constants.Vision` | Same, teleop (loose). |
| `kBaseXyStdDevMeters` | 0.5 | `Constants.Vision` | XY std-dev at 1 m. Sim's fabricated pose uses this to compute a realistic std-dev. |
| `kRejectThetaStdDev` | 1000 | `Constants.Vision` | Sentinel for "ignore vision yaw" (single-tag case). |

## State-machine timeouts

| Setting | Default | Location | Effect |
|---|---|---|---|
| `kScoringTimeoutSeconds` | 2.0 | `Constants.Superstructure` | SSM auto-exits SCORING after this if no `requestIdle()`. |
| `kIntakingTimeoutSeconds` | 6.0 | `Constants.Superstructure` | SSM auto-exits INTAKING if no game piece is detected. |
| `kStagingTimeoutSeconds` | 10.0 | `Constants.Superstructure` | SSM auto-exits STAGING if no score is requested. |

## Diagnostics (sim-only visible)

| Setting | Default | Location | Effect |
|---|---|---|---|
| Loop overrun threshold | 25 ms | `LoopTimeLogger` | Ticks longer than this increment the `Loop/OverrunCount` metric. |
| Rolling window size | 50 ticks | `LoopTimeLogger` | `Loop/MaxTickMs` tracks max across this window. |
| Vision latency window | 200 samples | `VisionLatencyTracker` | Stats window for `VisionLatency/{Min,Max,Mean,P95}Ms`. |
| Endgame threshold | 30 s | `MatchPhaseOverlay` | `Match/EndgameActive` flips true when remaining < this (teleop only). |

## When to retune

| Symptom | Knob |
|---|---|
| Flywheel spin-up too slow in sim | Drop `kSimJKgM2` |
| Sim "feels sluggish" to drive | Verify `kUseMapleSimKinematicBypass = true` |
| Intake never advances INTAKING → STAGING in sim | Call `intake.simulateGamePieceAcquired()` — no piece = no current spike |
| Vision rejects every frame | Check `kMaxLinearSpeedForVisionMps` + the "Rejected*" log keys |
| SSM stuck in a state | Check `kIntakingTimeoutSeconds` / `kStagingTimeoutSeconds` — if they're too long, the safety net is too loose |
| Loop overrun flag always on | Real loop time is > 25 ms; find the offender with `CommandLifecycleLogger` + `JvmLogger/GCTotalTimeMs` correlation |

---

## See also

- [`SIM_QUICKSTART.md`](../SIM_QUICKSTART.md) — bring up the sim in 15 minutes
- [`SIM_VALIDATION_SCRIPT.md`](../SIM_VALIDATION_SCRIPT.md) — verify the sim matches expectations
- [`TELEMETRY_REFERENCE.md`](../TELEMETRY_REFERENCE.md) — every log key published by the robot
- [`TROUBLESHOOTING.md`](../TROUBLESHOOTING.md) — when the sim acts weird
