# 2950-robot

FRC Team 2950 **The Devastators** — robot code for the 2026 REBUILT season.

Swerve-drive competition robot with vision-assisted scoring, neural game-piece detection,
strategy-driven autonomous, full physics simulation, and event-day diagnostics. Written in
Java 17 on WPILib 2026 with AdvantageKit deterministic logging.

[![Build & Test](https://github.com/safiqsindha/2950-robot/actions/workflows/build.yml/badge.svg)](https://github.com/safiqsindha/2950-robot/actions/workflows/build.yml)

---

## Contents

- [Hardware](#hardware)
- [CAN bus](#can-bus)
- [Stack](#stack)
- [Subsystems](#subsystems)
- [Driver controls](#driver-controls)
- [Autonomous](#autonomous)
- [Vision pipeline](#vision-pipeline)
- [Simulation](#simulation)
- [Observability & diagnostics](#observability--diagnostics)
- [What we kept the same](#what-we-kept-the-same)
- [Build & deploy](#build--deploy)
- [Repo structure](#repo-structure)
- [Additional docs](#additional-docs)

---

## Hardware

| Component | Spec |
|---|---|
| Drivetrain | 4-module Thrifty Swerve (6.23:1 drive, 25:1 steer) |
| Drive motors | 4× REV NEO + SPARK MAX |
| Steer motors | 4× REV NEO + SPARK MAX |
| Steer encoders | Thrifty 10-pin magnetic, attached via SPARK MAX data port |
| Gyro | ADIS16470 (roboRIO SPI, port 0) |
| Flywheel | 2× NEO Vortex (SPARK Flex) main + 2× NEO (SPARK MAX) feed |
| Intake | Dual-arm NEO + wheel NEO (all SPARK MAX) |
| Conveyor | Brushed belt (SPARK MAX) + brushless spindexer (SPARK MAX) |
| Vision | Limelight 3, hostname `limelight` — MegaTag2 AprilTags + neural detector |
| LEDs | 60 addressable, PWM port 0, priority-based animations |
| Bumpers | 22" × 22" frame, ±11" module center-to-center |
| Max speed | 14.5 ft/s (≈4.42 m/s) |
| Max angular | 2π rad/s |

**All REV — no CTRE.** Every motor controller is a SPARK MAX or SPARK Flex. Phoenix6 is in
vendordeps only because YAGSL transitively depends on it.

---

## CAN bus

Full wiring reference: **[`CAN_ID_REFERENCE.md`](CAN_ID_REFERENCE.md)**. Quick summary:

| Subsystem | Device | CAN ID |
|---|---|---|
| Swerve | FL drive / steer | 13 / 5 |
|  | FR drive / steer | 7 / 19 |
|  | BL drive / steer | 10 / 6 |
|  | BR drive / steer | 8 / 12 |
| Flywheel | Left Vortex (PID master) | **23** |
|  | Right Vortex (follows left) | **22** |
|  | Front / back feed wheel | 15 / 2 |
| Intake | Left arm / right arm / wheel | 16 / 17 / 4 |
| Conveyor | Belt / spindexer | 21 / 18 |
| Climber | Vertical | 11 |
| Side claw | Claw motor | **20** (was 18 — conflict with spindexer, re-flashed) |

Encoder offsets, gyro mount notes, and hardware-summary constants live in `CAN_ID_REFERENCE.md`.

---

## Stack

| Layer | Choice | Version |
|---|---|---|
| Language | Java | 17 (Temurin) |
| Framework | WPILib (GradleRIO) | 2026.2.1 |
| Logging | AdvantageKit LoggedRobot | replay to USB + NetworkTables |
| Swerve | YAGSL | 2026.3.14 |
| Path planning | Choreo (pre-planned) + PathPlanner AD* (runtime) | |
| Vision | LimelightLib (MegaTag2 + neural) via YALL | |
| Simulation | maple-sim (IronMaple) | 0.4.0-beta |
| Units | dyn4j (physics), wpilib units API | |
| Quality gates | Spotless (google-java-format), SpotBugs 4.8.6, JaCoCo (80% on `frc.lib`) | |

---

## Subsystems

| Subsystem | What it does |
|---|---|
| **SwerveSubsystem** | YAGSL field-relative swerve, PathPlanner AD\* + Choreo integration, vision pose fusion, X-lock, angular-velocity compensation, brownout protection (linear scale 8 V → 6 V), SysId routine hooked in pit |
| **VisionSubsystem** | Limelight MegaTag2 pose estimation with 5-team consensus rejection (distance² stddev weighting, velocity gate > 4 m/s, 120 ms post-reset inhibition, per-mode correction cap 0.5 m auto / 1.0 m teleop, single-tag heading reject σ_θ=1000) |
| **Flywheel** | Dual main + dual feed wheels, velocity PID via MAXMotion, RPM presets 2400–3500, Lagrange quadratic + linear-tail RPM curve, 971-style 3-iter fixed-point shoot-on-the-fly |
| **Intake** | Dual-arm position PID (independent L/R for mechanical play), current-spike game-piece detection, IntakeSide-aware sim via maple-sim adapter |
| **Conveyor** | Brushed belt + brushless spindexer indexing fuel into flywheel |
| **SuperstructureStateMachine** | IDLE → INTAKING → STAGING → SCORING pipeline, 2 s SCORING auto-exit (never locks up) |
| **LEDs** | Priority-based animations — idle (0), driving (1), aligning (2), alert (3) |
| **FuelDetectionConsumer** | Parses neural detection array — 3-frame persistence, 80% confidence gate |
| **OdometryDivergenceDetector** | Monitors vision-vs-wheel gap, escalating alerts (0.75 m warn, 1.5 m critical) |
| **StallDetector** | Reusable motor-current stall detection (pure-logic, fully tested) |
| **RumbleManager** | Controller haptic feedback |

Each motor subsystem follows the **2590 IO-layer pattern** — `XxxIO` interface + `@AutoLog`
`XxxIOInputs` + `XxxIOReal` + `XxxIOSim` + thin consumer. AdvantageKit replay works on every
subsystem from a competition log.

---

## Driver controls

Single Xbox controller (or Nintendo Pro Controller — auto-detected via `ControllerAdapter`).

### Teleop

| Input | Command | Behavior |
|---|---|---|
| Left stick | **DriveCommand** | Field-relative translation |
| Right stick | **DriveCommand** | Field-relative rotation |
| **Back + Start** | **PanicCommand** | 🛑 Cancels every scheduled command, forces SSM → IDLE, flashes LEDs red. Works while disabled. |
| A | Zero gyro | Resets heading (robot must face away from driver) |
| B | Vision diagnostic | Blinks LEDs when vision has a target, red flash when not |
| X | **AutoScoreCommand** | Vision-aligned scoring: aim → spin up → confirm → feed → retract |
| Y | X-lock | Wheels locked in X pattern (anti-push) |
| Right bumper | **AutoAlignCommand** | Rotates to nearest AprilTag; driver keeps translation |
| Left bumper | **DriveToGamePieceCommand** | Drives toward nearest neural-detected fuel; driver keeps rotation |
| Left trigger (> 0.5) | **FlywheelAim + FlywheelAutoFeed** | Manual aim + automatic feeding |
| POV right | FlywheelStatic | Preset shot at 2400 RPM |
| POV down | FlywheelStatic | Preset shot at 2500 RPM |
| POV left | FlywheelStatic | Preset shot at 3000 RPM |
| POV up | FlywheelStatic | Preset shot at 3500 RPM |
| **Start + POV up** | Practice reset | Teleport robot to scenario start pose (sim only) |

### SmartDashboard (pit / test mode)

| Button | Purpose |
|---|---|
| `Auto Chooser` | Picks the autonomous routine (see [Autonomous](#autonomous)) |
| `Run System Test` | 3-phase motor connectivity test — Flywheel 500 RPM → Intake wheel 15 % → Conveyor 15 % — logs per-subsystem pass/fail (ported from Team 862 LightningLib) |
| `Practice Scenario` | In sim: selects driver practice scenario (teleop-blue-center, teleop-red-trench, auto-shoot-and-leave, …) |

### Notes on layout

- **Panic button** takes the "two-finger emergency" gesture (Back + Start). Practice reset
  moved to Start + POV-Up to keep the panic combo unambiguous.
- **Y** moved from X-lock to … still X-lock; previously it was on right bumper, which is
  now AutoAlignCommand.
- **Left trigger** is the deep-aim mode (manual aim + auto-feed). Left bumper is the
  game-piece drive-to; the two are orthogonal hands-on behaviours.

---

## Autonomous

Mode picker lives on the SmartDashboard `Auto Chooser`.

| Mode | Description |
|---|---|
| **Leave Only** *(default)* | Choreo trajectory, drives off the starting line |
| Leave Only (Raw) | Direct `driveRobotRelative` — sanity-test swerve without Choreo |
| Shoot Only | Flywheel aim + auto-feed for 19 s |
| Score + Leave | Shoot preloaded, then Choreo leave |
| 2 Coral | Score preloaded → station → collect → return → score |
| 3 Coral | Two full station cycles, three total scored |
| **Full Autonomous** | Strategy-driven loop with dynamic obstacle avoidance |
| Safe Mode (No Vision) | Dead reckoning fallback — drive 2 m forward, blind shot at 2800 RPM |

### Full Autonomous intelligence

`FullAutonomousCommand` runs a strategy loop:

- **AutonomousStrategy** — evaluates CLIMB / SCORE / COLLECT targets by utility (distance
  penalty, opponent proximity penalty), re-targets every 0.5 s
- **Bot Aborter** — aborts the current target if an opponent's ETA is 0.75 s earlier
- **DynamicAvoidanceLayer** — neural-detected opponent positions injected as obstacles
  into the navigation grid
- **A\* pathfinder** — custom pure-Java 8-directional A\* over `NavigationGrid` for cost
  estimation (in `frc.lib.pathfinding`, 80 %+ test coverage)
- **PathPlanner AD\*** — runtime path planning with `deploy/pathplanner/navgrid.json`
- **CycleTracker + TelemetryPublisher** — cycle times + strategy decisions → NetworkTables
- **AllianceFlip** — HUB / CLIMB / fallback-collect poses automatically mirrored for red

---

## Vision pipeline

Limelight 3 with two pipelines, auto-switched per mode:

| Pipeline | Used in | Purpose |
|---|---|---|
| 0 (AprilTag) | Teleop + scoring autos | MegaTag2 `botpose_orb_wpiblue` pose estimation |
| 1 (Neural) | Full Autonomous | Game-piece detection (llpython array with class/confidence) |

### Pose-estimator rejection rules

All five consensus patterns from the Einstein-tier team scout (971, 1678, 1619, 4481, 6328):

1. **Distance² stddev weighting** — `σ_xy = 0.5 · d² / √tagCount`; far-tag single reads
   contribute far less than close multi-tag reads.
2. **Single-tag heading reject** — `σ_θ = 1000` for single-tag, `0.1` for multi-tag.
   MegaTag2 single-tag heading is noisy; trust the gyro.
3. **Velocity gate** — skip the frame if linear chassis speed > 4 m/s (Limelight latency +
   motion blur corrupt pose).
4. **Reset inhibition** — drop vision for 120 ms after `resetOdometry` / `zeroGyro` to
   prevent Kalman re-snap.
5. **Per-mode correction cap** — 1.0 m rejection threshold in teleop, 0.5 m in auto
   (tighter to protect Choreo trajectory following).

Plus existing gates: tag count ≥ 1, latency ≤ 50 ms, average tag distance ≤ 4 m, field-bounds
check.

### Shoot-on-the-fly

Moving-shot RPM via Team 971 Spartan's 3-iteration fixed-point:

```
virtualTarget = target
for i in 1..3:                       # converges because ball_speed ≫ robot_speed
  airTime = ||virtualTarget|| / ballExitSpeed
  virtualTarget = target − velocity × airTime
rpm = rpmFromMeters(||virtualTarget||)
```

3 iterations is enough — convergence ratio `q = ||v|| / ballSpeed ≈ 0.1`, residual drops to
~10⁻³ of the initial distance, sub-centimetre at any realistic shot range. See
`Helper.rpmFromMeters(double, double, ChassisSpeeds)`.

---

## Simulation

Full physics simulation via maple-sim 0.4.0-beta + YAGSL's built-in drive bridge.

```bash
./gradlew simulateJava
```

- **DriverPracticeMode** — 6 scenarios via SmartDashboard, pose reset via `Start + POV-Up`
- **ControllerAdapter** — Xbox vs Nintendo Pro Controller auto-detect, macOS axis-offset calibration
- **Per-subsystem sim IO** — Flywheel, Intake, Conveyor each have a pure-Java `XxxIOSim`
  that plugs into the same `@AutoLog` inputs as real
- **`ShotSimulation`** — wraps `RebuiltFuelOnFly`, fires projectiles when the flywheel hits
  its commanded setpoint in sim. Wired into `Robot.simulationPeriodic()`; logs
  `Sim/Shots/Fired` + `Sim/Shots/Scored` for AdvantageScope replay.
- **`IntakeSimulationAdapter`** — wraps `IntakeSimulation.InTheFrameIntake`. Wired into
  `IntakeIOSim`: when arena fuel overlaps the intake rectangle, `hasGamePiece()` flips and
  SSM auto-advances INTAKING → STAGING. Flag-based `simGamePieceAcquired` preserved as
  fallback for unit tests.
- **`MAPLE_SIM_BUG_REPORT.md` kinematic bypass** — gated behind
  `Constants.Swerve.kUseMapleSimKinematicBypass` (default `true`). Flip to `false` once
  upstream maple-sim fixes the REV NEO sign-convention bug.

maple-sim physics is gravity-only (no drag, no spin). Use sim to verify **aim logic**,
not ballistic tuning. Production RPM calibration still happens on hardware.

---

## Observability & diagnostics

The robot logs to three surfaces simultaneously:

1. **AdvantageKit replay** (`.wpilog` on USB + NT4 publish) — every subsystem input +
   calibration output is recorded for post-match reconstruction in AdvantageScope.
2. **SmartDashboard / Elastic Dashboard** — driver-facing pose, auto selector, alerts.
3. **WPILib `Alert` group "SparkFaults"** — every REV fault/warning bit lights up with the
   motor name.

### Three new diagnostic subsystems (event-day tooling)

| Component | Pattern source | What it emits |
|---|---|---|
| **`CommandLifecycleLogger`** | Team 3005 `DataLogger` | Every command init / finish / interrupt → `Commands/Last*`, `Commands/ActiveCount`, `Commands/Durations/<name>` |
| **`JvmLogger`** | Team 3005 `LoggedJVM` | Heap used / max / non-heap, cumulative GC count + time → `JVM/*`. Correlate loop overruns with GC spikes. |
| **`SparkAlertLogger`** | Team 4481 Rembrandts | 15 alert slots per Spark (7 faults + 8 warnings) — **17 motors × 15 = 255 alert slots** (9 mechanism + 8 YAGSL swerve). When CAN drops on one motor, the dashboard says exactly which one and why. |

### Pose / speed telemetry

- `Drive/Pose`, `Drive/GyroYaw`, `Drive/RobotVelocity`
- `Drive/SimGroundTruth` — simulation-only, for comparing against the Kalman output
- `Vision/BotPose`, `Vision/StdDevXY`, `Vision/StdDevTheta`, `Vision/InhibitedAfterReset`,
  `Vision/RejectedForSpeed`, `Vision/CorrectionThresholdM`
- `Robot/BatteryVoltage`, `Robot/BrownoutActive`, `Robot/BrownoutScale`,
  `Robot/CANBusUtilization`, `Robot/CriticalVoltageWarning`
- `Sim/Shots/Fired`, `Sim/Shots/Scored` (sim only)

---

## What we kept the same

Hardware-verified constants and deliberate non-default choices. **If you're tempted to
change any of these, read the comment in the code first — the history is hard-won.**

### YAGSL swerve config

- `setHeadingCorrection(false)` — hardware-verified; the existing comment contradicts YAGSL
  docs, but practice testing chose this. A TODO is noted in `SwerveSubsystem.java` to re-evaluate.
- `setCosineCompensator(false)` — disagrees with YAGSL's default recommendation; hardware
  testing found it caused discrepancies not seen in real life.
- `setAngularVelocityCompensation(true, true, 0.1)` — coefficient tuned on hardware. Don't
  change without a rotation-while-translating test.
- `setChassisDiscretization(true, 0.02)` — explicit rather than relying on library default.
- `setModuleEncoderAutoSynchronize(true, 2.0)` — enabled after the YAGSL self-audit.
- Telemetry verbosity: `POSE` on real robot, `HIGH` in sim (HIGH floods NT at match).

### Mechanical

- Drive gear ratio **6.23:1** — Thrifty Swerve, not an SDS MK4 (8.14 / 6.75 / 6.12) or
  MAXSwerve ratio. Verified against physical modules.
- Steer gear ratio **25:1** — also Thrifty-specific, not SDS (21.43 / 12.8) or MAXSwerve (46.42).
- Max speed **14.5 ft/s** — tuned on hardware, below module free speed.
- Module center-to-center **11 inches** — measured on the real chassis, not catalog default.

### Tuned constants (from swerve-test branch)

- Flywheel PID: `kP = 0.00075`, `kI = 0`, `kD = 0`, `kS = 0.15`, `kV = 12/6800`
- Intake PID: `kP = 0.025`, `kD = 0`
- Swerve module drive/angle PID: `kP = 0.0020645` (SPARK MAX closed-loop units)
- Heading PID (for when correction is enabled): `p = 0.4`, `d = 0.01`
- Ball exit velocity for shot compensation: **12.0 m/s**
- Brownout: threshold **8.0 V**, floor **6.0 V**, ramp to 50 % output linearly between
- Drive deadband: **0.1**
- Flywheel "ready" threshold: **10 %** of target RPM

### State-machine timeouts

- SCORING auto-exit: **2.0 s** (never locks up; mirrors Team 6328's `AutoSelector` safety pattern)
- AutoScoreCommand total timeout: **5.0 s**
- Vision confirm duration: **0.25 s** (continuous validity before firing)

### Game-piece detection

- Intake current threshold: **15 A**
- Neural confidence gate: **80 %**
- Persistence frames: **3** (fuel) / **1** (opponent — immediate hazard)

### Full-auto strategy

- Abort time threshold: **0.75 s** (opponent earlier than us)
- Climb time threshold: **15.0 s**
- Max fuel detections: **8**
- Opponent influence radius: **2.0 m**
- Attractive / repulsive gains: **1.0 / 1.5**

### Deliberately NOT taken from other teams

- Team 604's QuixSwerveDriveSimulation (hard CTRE TalonFX dependency; investigated in
  `INVESTIGATION_604_QUIXLIB.md`, rejected)
- Team 3005's Python pivot (their offseason rewrite; their IO-triplet rejection is
  interesting but we have multiple programmers and AdvantageKit replay is load-bearing)
- BlendY templated multi-output interp (971) — only valuable with correlated outputs
  (RPM + hood + feeder); we only have RPM, so skipped

---

## Build & deploy

```bash
./gradlew build          # Build + tests + Spotless + SpotBugs + JaCoCo
./gradlew test           # Unit tests only
./gradlew deploy         # Deploy to robot over USB / WiFi
./gradlew simulateJava   # Run full physics simulation
./gradlew spotlessApply  # Auto-format code
```

CI runs `./gradlew build` on every push and PR — Spotless + SpotBugs + JaCoCo + JUnit.
80 %+ line coverage is enforced on `frc.lib.*` packages; `frc.robot.*` hardware-coupled
code has no gate.

When CI fails, artifacts (`test-results`, `coverage-report`, `spotbugs-reports`) are
uploaded — download via `gh run download <run-id> --name <artifact>`.

---

## Repo structure

```
2950-robot/
  build.gradle                          # GradleRIO + Spotless + SpotBugs + JaCoCo
  vendordeps/                           # YAGSL, maple-sim, REVLib, YALL, AdvantageKit,
                                        #   Choreo, PathPlanner, Phoenix6 (transitive only)
  .github/workflows/build.yml           # CI — build + test + coverage + spotbugs artifacts
  config/spotbugs-exclude.xml           # per-bug-pattern suppressions (AutoLogged clone etc.)
  src/main/
    deploy/
      swerve/                           # YAGSL JSON (4 modules + controller + physical)
      choreo/                           # Pre-planned .traj trajectory files
      pathplanner/navgrid.json          # AD* navigation grid
    java/frc/
      lib/                              # Pure-logic utilities — 80 %+ coverage
        LoggedTunableNumber.java        # NT-tunable double, per-caller hasChanged tracking
        AllianceFlip.java               # Pose / translation / rotation alliance mirroring
        control/                        # LinearProfile (6328 acceleration-limited slew)
        diagnostics/                    # CommandLifecycleLogger, JvmLogger (3005 patterns)
        pathfinding/                    # A*, NavigationGrid, DynamicAvoidanceLayer
        trajectory/                     # HolonomicTrajectory + ChoreoTrajectoryAdapter (4481)
        util/                           # Hysteresis, AreWeThereYetDebouncer, GeomUtil, RobotName
      robot/
        Robot.java                      # LoggedRobot lifecycle + brownout + JVM telemetry
        RobotContainer.java             # Subsystem init, command bindings, auto chooser
        Constants.java                  # CAN IDs, PID gains, field dimensions, timeouts
        Helper.java                     # Limelight filters + rpmFromMeters (Lagrange + 971)
        ControllerAdapter.java          # Xbox vs Pro Controller auto-detection
        DriverPracticeMode.java         # 6 sim practice scenarios
        subsystems/                     # 13 subsystems (incl. IOReal + IOSim + IO triplets)
        commands/                       # 18 command classes incl. flywheel/ + PanicCommand
        autos/                          # AutonomousStrategy, GameState, CycleTracker, …
        diagnostics/                    # SparkAlertLogger (4481 pattern)
        simulation/                     # ShotSimulation, IntakeSimulationAdapter (maple-sim)
  src/test/                             # 35+ test files; mirrors src/main/java layout
```

### Pure-logic utilities in `frc.lib` (available, not all wired yet)

| Class | Source | Purpose |
|---|---|---|
| `LoggedTunableNumber` | 6328 / 5940 | NT-tunable double with per-caller `hasChanged` |
| `AllianceFlip` | consensus | Pose/translation/rotation red-blue mirror |
| `control.LinearProfile` | 6328 | Acceleration-limited setpoint slew (flywheel anti-brownout) |
| `trajectory.HolonomicTrajectory` + `ChoreoTrajectoryAdapter` | 4481 | Planner-agnostic trajectory interface — allows future swap of Choreo / PathPlanner / custom aligner without follower rewrite |
| `util.Hysteresis` | 3005 | Symmetric schmitt-trigger for boundary stability |
| `util.AreWeThereYetDebouncer` | 1619 | "At goal" debounce that resets on target change |
| `util.GeomUtil` | 4481 | `getClosestPose` / `getClosestFuturePose` (Lie-algebra extrapolation) |
| `util.RobotName` | 3005 | File-backed per-bot enum (`/home/lvuser/ROBOT_NAME` → `COMP`/`PRACTICE`/…) |
| `pathfinding.*` | 2950 | A* navigation grid + dynamic avoidance |
| `diagnostics.CommandLifecycleLogger` | 3005 | Command init/finish/interrupt → AdvantageKit |
| `diagnostics.JvmLogger` | 3005 | Heap + GC telemetry |

### Deferred backlog (queued for the next session)

Still on the shelf, not yet shipped:

- **TrajectoryFollower** (4481) — closed-loop follower using `HolonomicTrajectory`. Foundation is in place; follower + auto migration is next.
- **`@AutoRoutine` annotation + reflective `AutoSelector`** (4481) — declarative auto registration
- **`FlywheelAutoFeed` 2D upgrade** — thread Limelight `tx` into `rpmFromMeters(d, θ, speeds)` so the 971 fixed-point compensation is actually used
- **Drive-feel polish** — 2056 jerk-limited slew, 2056 350 ms heading-hold release gate, 2056 `UpdateDepartPose` post-score anchor
- **HAL-init test harness** — one canary class with `HAL.initialize(500, 0)` in `@BeforeAll` to unlock physics tests in IOSim
- **971 CapU current-limiting** — dynamic battery-aware ceiling
- **971 hybrid EKF with replay buffer** — the big one; highest effort, highest intellectual leverage
- **CI polish** — `./gradlew wrapper` to clear the comp-purge warning, Node 24 migration

See `PRACTICE_SESSION_PLAYBOOK.md` for how to use the existing tooling to de-risk practice.

---

## Additional docs

- **[`AGENTS.md`](AGENTS.md)** — authoritative command + convention contract for Claude / Cursor / agent sessions
- **[`PRACTICE_SESSION_PLAYBOOK.md`](PRACTICE_SESSION_PLAYBOOK.md)** — stepwise guide (Phase A smoke → Phase B calibration → Phase C auto rehearsal)
- **[`CAN_ID_REFERENCE.md`](CAN_ID_REFERENCE.md)** — complete CAN bus wiring
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — system design + subsystem interactions
- [`STUDENT_TESTING_GUIDE.md`](STUDENT_TESTING_GUIDE.md) — how to write + run tests
- [`HARDWARE_QUESTIONNAIRE.md`](HARDWARE_QUESTIONNAIRE.md) — hardware verification checklist
- [`AUDIT_2026-04-16.md`](AUDIT_2026-04-16.md) — 50-finding codebase audit
- [`PLAN.md`](PLAN.md) + [`STATUS.md`](STATUS.md) — offseason refactor tracking (all 18 tasks complete)
- [`INVESTIGATION_604_QUIXLIB.md`](INVESTIGATION_604_QUIXLIB.md) — why we didn't adopt 604's swerve sim
- [`MAPLE_SIM_BUG_REPORT.md`](MAPLE_SIM_BUG_REPORT.md) — open upstream issue + our bypass
- [`VENDORDEP_URLS.md`](VENDORDEP_URLS.md) — exact JSON URLs for every vendor dep

---

## Related

Cross-season intelligence, scouting, design tools, and the prediction engine live in
[TheEngine](https://github.com/safiqsindha/TheEngine). This repo resets each season; that
one persists.
