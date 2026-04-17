# Telemetry Reference

Authoritative list of every AdvantageKit log key the robot publishes, organized by the top-level namespace. Use this when:

- Building an AdvantageScope layout
- Writing a dashboard widget
- Adding a new check that needs a signal — look here *before* adding a new key (usually one already exists)
- Debugging "why isn't key X showing up" — see the publisher column

All keys are published via `Logger.recordOutput(key, value)`. AdvantageKit handles both live NT4 visibility and wpilog replay from the same API call.

---

## `Robot/*` — top-level chassis health

| Key | Type | Publisher | Notes |
|---|---|---|---|
| `Robot/BatteryVoltage` | double | `Robot.robotPeriodic` | V, from `RobotController` |
| `Robot/MatchTimeRemaining` | double | `Robot.robotPeriodic` | s, from `DriverStation` |
| `Robot/BrownoutActive` | boolean | `Robot.robotPeriodic` | true when `< kBrownoutThresholdVolts` (8 V) |
| `Robot/CANBusUtilization` | double | `Robot.robotPeriodic` | % — legacy key kept for dashboard compat; also published under `CAN/UtilizationPct` |
| `Robot/CriticalVoltageWarning` | boolean | `Robot.robotPeriodic` | true only when battery drops below 6.5 V |
| `Robot/BrownoutScale` | double | `Robot.robotPeriodic` | 1.0 healthy → 0.5 at 6 V; every motor-output scales by this |
| `Robot/Phase` | enum string | `Robot.logPhaseTransition` | DISABLED / AUTO / TELEOP / TEST |
| `Robot/PhaseElapsedSec` | double | `Robot.logPhaseTransition` | seconds in the previous phase |
| `Robot/PhaseTransitionTo` | enum string | `Robot.logPhaseTransition` | the new phase |

---

## `JVM/*` — JVM memory + GC (via `JvmLogger`)

| Key | Type | Notes |
|---|---|---|
| `JVM/HeapUsedMB` | double | Eden + survivor + tenured |
| `JVM/HeapMaxMB` | double | -Xmx cap |
| `JVM/NonHeapUsedMB` | double | metaspace + code cache |
| `JVM/GCTotalCount` | long | cumulative across all collectors |
| `JVM/GCTotalTimeMs` | long | cumulative across all collectors |

Use: spikes here correlate with loop-overruns. Cross-reference with `CAN/UtilizationPct` and `Commands/ActiveCount`.

---

## `CAN/*` — roboRIO CAN bus (via `CanBusLogger`)

| Key | Type | Notes |
|---|---|---|
| `CAN/UtilizationPct` | double | 0..100; sustained > 80 indicates trouble |
| `CAN/OffCount` | int | cumulative; any increment = bus-off event, investigate wiring |
| `CAN/TxFullCount` | int | cumulative; TX queue saturation |
| `CAN/ReceiveErrorCount` | int | cumulative; malformed frames |
| `CAN/TransmitErrorCount` | int | cumulative; TX errors |

---

## `PDH/*` — power distribution (via `PdhLogger`)

| Key | Type | Notes |
|---|---|---|
| `PDH/VoltageV` | double | bus input voltage |
| `PDH/TotalCurrentA` | double | sum of all output channels |
| `PDH/TemperatureC` | double | internal PDH temp |
| `PDH/ChannelCurrentsA` | double[24] | per-channel currents; index = PDH channel number |

---

## `Drive/*` — swerve drive (via `SwerveSubsystem`)

| Key | Type | Notes |
|---|---|---|
| `Drive/Pose` | Pose2d | field-relative |
| `Drive/RobotVelocity` | ChassisSpeeds | robot-relative |
| `Drive/GyroYaw` | double | degrees; canonical heading source |
| `Drive/PoseResetTime` | double | FPGA seconds of last `resetOdometry` call (vision uses this for reset-inhibition window) |
| `Drive/Module{0..3}/*` | various | YAGSL telemetry — states, encoder positions, etc. Verbosity gated by `SwerveDriveTelemetry.verbosity` (HIGH in sim, POSE in match) |

---

## `Vision/*` — Limelight MegaTag2 (via `VisionSubsystem`)

| Key | Type | Notes |
|---|---|---|
| `Vision/HasTarget` | boolean | from MegaTag2 status byte |
| `Vision/Pose` | Pose2d | MegaTag2 botpose_orb_wpiblue |
| `Vision/TagCount` | int | tags contributing to this MT2 estimate |
| `Vision/TagDistM` | double | avg distance of contributing tags |
| `Vision/LatencyMs` | double | total latency (pipeline + capture) |
| `Vision/Rejected{ForSpeed,ByLatency,ByDistance,ByTagCount,BySingleTagYaw}` | boolean | which acceptance gate vetoed this frame — only one is `true` per rejected frame |
| `Vision/InhibitedAfterReset` | boolean | true for `kResetInhibitionSeconds` after odometry reset |
| `Vision/CorrectionMagM` | double | ‖visionPose − currentPose‖ — if > cap, frame is vetoed |
| `Vision/StdDevXy` | double | computed std-dev applied to this measurement (971 d² / tagCount) |
| `Vision/StdDevTheta` | double | either `kMultiTagThetaStdDev` or `kRejectThetaStdDev` |
| `Vision/FuelDetectionCount` | int | neural detector output count |
| `Vision/OpponentDetectionCount` | int | neural detector count for opponents |

---

## `Flywheel/*` — flywheel state (via `Flywheel`)

| Key | Type | Notes |
|---|---|---|
| `Flywheel/GoalRpm` | double | what the caller wants (from `setTargetRpm`) |
| `Flywheel/SetpointRpm` | double | what the PID sees — slewed from goal at `kMaxAccelRpmPerSec` |
| `Flywheel/VelocityRpm` | double | measured |
| `Flywheel/AppliedVoltage` | double | V |
| `Flywheel/SupplyCurrentAmps` | double | A |
| `Flywheel/TempCelsius` | double | motor winding temp |
| `Flywheel/OpenLoop` | boolean | true when `setVortexOutput` is in effect |
| `Flywheel/AtSpeed` | boolean | measured within `kReadyThreshold` of goal |
| `Flywheel/kP`, `kI`, `kD`, `kMaxAccelRpmPerSec` | double | LoggedTunableNumber entries — editable from NT |
| `Flywheel/DebugRpm`, `DebugDist`, `DebugTxRad`, `DebugVxRobot`, `DebugVyRobot` | double | FlywheelAutoFeed intermediate state — useful during shot calibration |

---

## `Intake/*` — intake state (via `Intake`)

| Key | Type | Notes |
|---|---|---|
| `Intake/GoalArmPositionRot` | double | commanded arm position goal |
| `Intake/SetpointArmPositionRot` | double | slewed setpoint fed to PID |
| `Intake/LeftArmPositionRotations` | double | measured, left arm |
| `Intake/RightArmPositionRotations` | double | measured, right arm |
| `Intake/WheelCurrentAmps` | double | SSM uses this for game-piece detection |
| `Intake/WheelAppliedVoltage` | double | V |
| `Intake/Connected` | boolean | CAN reachability |
| `Intake/kP`, `kD`, `kMaxArmAccelRotPerSec` | double | LoggedTunableNumber entries |

---

## `Conveyor/*` — conveyor state (via `Conveyor`)

| Key | Type | Notes |
|---|---|---|
| `Conveyor/ConveyorCurrentAmps` | double | belt motor |
| `Conveyor/SpindexerCurrentAmps` | double | spindexer motor |
| `Conveyor/Connected` | boolean | CAN reachability |

---

## `Auto/*` — autonomous trajectory execution

Populated from `ChoreoAutoCommand.factory(...)` controller lambda — fires every sample during auto.

| Key | Type | Notes |
|---|---|---|
| `Auto/TargetPose` | Pose2d | trajectory sample pose |
| `Auto/ActualPose` | Pose2d | robot's current pose (odometry) |
| `Auto/TrajectoryTime` | double | seconds since trajectory start |
| `Auto/SampleVxFieldRel` | double | raw Choreo sample vx (field frame) |
| `Auto/CorrectedVxFieldRel`, `CorrectedVyFieldRel`, `CorrectedOmegaRadPerSec` | double | TrajectoryFollower output (FF + PID correction, field frame) |
| `Auto/SkippedShot` | string | `<routine>-<cycle>-<reason>` when a conditional shot was skipped |

---

## `SystemTest/*` — motor connectivity smoke (via `SystemTestCommand`)

| Key | Type | Notes |
|---|---|---|
| `SystemTest/Status` | string | RUNNING / INTERRUPTED / COMPLETE |
| `SystemTest/AllPass` | boolean | aggregate |
| `SystemTest/PassCount`, `TotalChecks` | double | progress |
| `SystemTest/Flywheel/{RPM,CurrentAmps,RpmPass,CurrentPass,Pass}` | various | per-phase result |
| `SystemTest/IntakeWheel/{CurrentAmps,Pass}` | various |
| `SystemTest/Conveyor/{CurrentAmps,Pass}` | various |
| `SystemTest/Spindexer/CurrentAmps` | double |

---

## `Commands/*` — command lifecycle (via `CommandLifecycleLogger`)

Every scheduler event leaves a trail:

| Key | Type | Notes |
|---|---|---|
| `Commands/Initialized/<ClassName>` | double | FPGA timestamp of last init |
| `Commands/Finished/<ClassName>` | double | FPGA timestamp of clean finish |
| `Commands/Interrupted/<ClassName>` | double | FPGA timestamp of interrupt |
| `Commands/ActiveCount` | int | approximate — scheduler snapshot |

---

## `Faults/*` — REV Spark faults (via `SparkAlertLogger`)

One key per monitored Spark, each a boolean (OR of all fault bits). Example:

```
Faults/Flywheel/leftVortex
Faults/Intake/wheel
Faults/Drive/Module0/drive
```

See `SparkAlertLogger.java` for the full list of monitored Sparks and fault bits (17 motors × 15 fault bits = 255 alert slots on the Driver Station).

---

## Adding a new key

Before adding, grep this file and the source tree:

```bash
grep -rn "Logger.recordOutput" src/main/java | grep "YourPrefix/"
```

If there's not already a home for your signal, add it here too. The rule: every key published should appear in this reference.
