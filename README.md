# 2950-robot

FRC Team 2950 **The Devastators** -- Robot Code for the 2026 REBUILT season.

Swerve-drive competition robot with vision-assisted scoring, neural game piece detection, strategy-driven autonomous, and full physics simulation. Written in Java on WPILib with AdvantageKit deterministic logging.

## Hardware

| Component | Spec |
|---|---|
| Drivetrain | 4-module swerve (Thrifty Swerve, 6.23:1 drive / 25.0:1 steer) |
| Drive motors | 4x REV NEO + SPARK MAX |
| Steer motors | 4x REV NEO + SPARK MAX |
| Steer encoders | Thrifty 10-pin attached (SPARK MAX data port) |
| Gyro | ADIS16470 IMU |
| Flywheel | 2x REV NEO Vortex (SPARK Flex) main + 2x NEO (SPARK MAX) feed |
| Intake | Dual-arm (independent L/R NEO) + wheel motor |
| Conveyor | Brushed belt + brushless spindexer |
| Climber | Single NEO with position PID |
| Side claw | Single NEO with position PID |
| Vision | Limelight (MegaTag2 AprilTags + neural game piece detection) |
| LEDs | 60-LED addressable strip, priority-based animation |
| Max speed | 14.5 ft/s (~4.42 m/s) |

## Stack

| Layer | Choice |
|---|---|
| Language | Java 17 |
| Framework | WPILib 2026.2.1 (GradleRIO) |
| Logging | AdvantageKit (`LoggedRobot`) -- replay to USB + NetworkTables |
| Swerve | YAGSL (Yet Another Generic Swerve Library) |
| Path planning | PathPlanner AD* (runtime) + Choreo (pre-planned trajectories) |
| Vision | Limelight MegaTag2 (AprilTag pose) + neural detector (game pieces) |
| Simulation | maple-sim via YAGSL/ironmaple |
| Quality | Spotless (format), SpotBugs (static analysis), JaCoCo (80% coverage on `frc.lib`) |

## Subsystems

| Subsystem | What it does |
|---|---|
| **SwerveSubsystem** | YAGSL field-relative swerve, PathPlanner AD* integration, vision pose fusion, X-lock, brownout protection (linear scale 8V-6V) |
| **VisionSubsystem** | Limelight MegaTag2 pose estimation, dual pipeline (AprilTag scoring + neural game piece detection), distance-based Kalman filter std devs, 1m outlier rejection |
| **Flywheel** | Dual main + dual feed wheels, velocity PID via MAXMotion, RPM presets 2400-4000, full DCMotorSim |
| **Intake** | Dual-arm position PID (independent L/R for mechanical play), current-spike game piece detection |
| **Conveyor** | Belt + spindexer indexing fuel into flywheel |
| **Climber** | Position PID for Tower climbing |
| **SideClaw** | Position PID side-mounted claw |
| **SuperstructureStateMachine** | Coordinates IDLE -> INTAKING -> STAGING -> SCORING -> CLIMBING pipeline |
| **LEDs** | Priority-based animation system (idle, breathing, blink, flash, error) |
| **FuelDetectionConsumer** | Parses neural detection array -- 3-frame persistence, 80%+ confidence gating |
| **OdometryDivergenceDetector** | Monitors vision vs wheel odometry gap, escalating alerts (0.75m warn, 1.5m critical) |
| **StallDetector** | Reusable motor current stall detection |
| **RumbleManager** | Controller haptic feedback |

## Commands

### Teleop (Driver)

| Button | Command | Behavior |
|---|---|---|
| Left stick | **DriveCommand** | Field-relative translation |
| Right stick | **DriveCommand** | Field-relative rotation |
| A | Zero gyro | Resets heading |
| B | LED diagnostic | Blinks on vision target, flashes on no target |
| X | **AutoScoreCommand** | Full vision-aligned scoring: aim -> spin up -> confirm -> feed -> retract |
| Y | X-lock | Locks wheels in X pattern |
| Right bumper | **AutoAlignCommand** | Auto-rotates to nearest AprilTag, driver keeps translation |
| Left bumper | **DriveToGamePieceCommand** | Auto-drives toward nearest neural-detected fuel, driver keeps rotation |
| Left trigger | **FlywheelAim + AutoFeed** | Manual aim with automatic feeding |
| POV right/down/left/up | **FlywheelStatic** | Preset shots at 2400 / 2500 / 3000 / 3500 RPM |
| Back + Start | Practice reset | Teleport to scenario start (sim only) |

### Autonomous Modes (SmartDashboard chooser)

| Mode | Description |
|---|---|
| **Leave Only** (default) | Choreo trajectory, drive off starting line |
| **Leave Only (Raw)** | Direct `driveRobotRelative` test, no Choreo dependency |
| **Shoot Only** | Flywheel aim + auto-feed for 19 seconds |
| **Score + Leave** | Shoot preloaded coral, then Choreo leave |
| **2 Coral** | Score preloaded -> drive to station -> collect -> return -> score (conditional on game piece detection) |
| **3 Coral** | Two full station cycles, three total coral scored |
| **Full Autonomous** | Strategy-driven loop: evaluate targets -> pathfind -> execute -> repeat, with dynamic obstacle avoidance |
| **Safe Mode (No Vision)** | Dead reckoning fallback: drive 2m forward, blind shot at 2800 RPM |

### Full Autonomous Intelligence

The `FullAutonomousCommand` runs a strategy loop powered by:

- **AutonomousStrategy** -- evaluates CLIMB / SCORE / COLLECT targets by utility (distance penalty, opponent proximity penalty), re-targets every 0.5s
- **Bot Aborter** -- aborts current target if opponent ETA is 0.75s sooner than ours
- **DynamicAvoidanceLayer** -- opponent positions from neural detection injected as obstacles into the navigation grid
- **A\* Pathfinder** -- custom pure-Java 8-directional A\* over NavigationGrid for cost estimation
- **PathPlanner AD\*** -- runtime path planning with `navgrid.json`
- **CycleTracker + TelemetryPublisher** -- logs cycle times and strategy decisions to NetworkTables

## Simulation

Full physics simulation via maple-sim / YAGSL ironmaple. Includes:

- **DriverPracticeMode** -- 6 scenarios selectable via SmartDashboard, pose reset via Back+Start
- **ControllerAdapter** -- auto-detects Xbox vs Nintendo Pro Controller, auto-calibrates axis offsets on macOS
- DCMotorSim for flywheel, intake, climber, claw
- Game-specific arena elements (Fuel, Hub, Outpost, Tower)

```bash
./gradlew simulateJava
```

## Repo Structure

```
2950-robot/
  build.gradle                          # GradleRIO + Spotless + SpotBugs + JaCoCo
  vendordeps/                           # Phoenix 6, REVLib, ThriftyLib, ChoreoLib,
                                        #   PathplannerLib, AdvantageKit, YAGSL, maple-sim
  swervelib/                            # YAGSL library source (local copy)
  src/main/
    deploy/
      swerve/                           # YAGSL JSON configs (4 modules + controller + drive)
      choreo/                           # Pre-planned .traj trajectory files
      pathplanner/navgrid.json          # AD* navigation grid
    java/frc/
      lib/                              # AllianceFlip, A* pathfinder, NavigationGrid,
                                        #   DynamicAvoidanceLayer
      robot/
        Robot.java                      # LoggedRobot lifecycle
        RobotContainer.java             # Subsystem init, command bindings, auto chooser
        Constants.java                  # CAN IDs, PID gains, field dimensions
        ControllerAdapter.java          # Xbox / Pro Controller auto-detection
        DriverPracticeMode.java         # Simulation practice scenarios
        subsystems/                     # 13 subsystem classes
        commands/                       # 18 command classes + flywheel/ subpackage
        autos/                          # AutonomousStrategy, GameState, ScoredTarget,
                                        #   CycleTracker, TelemetryPublisher
  src/test/                             # 27 test files, mirrors main structure
```

## Build & Deploy

```bash
./gradlew build          # Build + tests + Spotless + SpotBugs + JaCoCo
./gradlew deploy         # Deploy to robot over USB/WiFi
./gradlew simulateJava   # Run full physics simulation
./gradlew spotlessApply  # Auto-format code
```

## Docs

Additional documentation lives in the repo root:

- `ARCHITECTURE.md` -- system design and subsystem interactions
- `CAN_ID_REFERENCE.md` -- complete CAN bus wiring map
- `STUDENT_TESTING_GUIDE.md` -- how to write and run tests
- `HARDWARE_QUESTIONNAIRE.md` -- hardware verification checklist

## Related

This repo resets each season. Cross-season intelligence, scouting, design tools, and the prediction engine live in [TheEngine](https://github.com/safiqsindha/TheEngine).
