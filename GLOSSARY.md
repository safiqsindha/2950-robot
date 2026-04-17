# Glossary

Definitions for terms that come up in the codebase / docs. FRC lingo + project-specific.

---

## A

**AdvantageKit** — deterministic logging library by Team 6328. Replaces manual `Logger.recordOutput` duplication; supports offline replay of logs for debugging. The `@AutoLog` annotation generates `XxxIOInputsAutoLogged` classes at compile time.

**AdvantageScope** — the log viewer that reads AdvantageKit logs (live or replay). Our go-to dashboard for debugging.

**AD\*** — incremental path planner used by PathPlannerLib. We call it via `swerve.pathfindToPose(...)`.

**AllianceFlip** — utility in `frc.lib` that mirrors a blue-alliance pose to red. See `AllianceFlip.apply()`.

**ArchUnit** — test-time package-dependency rule engine. `ArchitectureTest` enforces that `frc.lib.*` doesn't depend on `frc.robot.*`, etc.

**AutoFactory** — Choreo's factory for trajectory-following commands. See `ChoreoAutoCommand.factory(...)`.

**`@AutoLog`** — AdvantageKit annotation on an IO inputs record. Generates a logged-inputs class at compile time.

---

## B

**Brownout** — when battery voltage drops below the FRC brownout threshold (6.8 V). Our brownout scale ramps down motor output from 1.0 to 0.5 as voltage drops from 8.0 V to 6.0 V.

---

## C

**Choreo** — trajectory-design desktop app + runtime library. Produces `.traj` files that Choreo's Java loader reads at runtime.

**CodeQL** — GitHub's static analysis; runs on every PR.

**Comp deploy** — `./gradlew compDeploy` — at an event on a `comp-*` branch, auto-commits pit edits before deploying so we never lose "I fixed it but it didn't save" changes. See `build.gradle`.

---

## D

**DataLog** — WPILib's native log format (`.wpilog`). Required for SysId output.

**DCMotorSim** — WPILib sim class modeling DC motor + gearbox + moment of inertia. Used by `FlywheelIOSim`.

**Debouncer** — WPILib filter that requires N consecutive positive samples before flipping. Do NOT use in tests — it loads HAL. Use `frc.lib.util.AreWeThereYetDebouncer` instead.

---

## F

**Feedforward** — open-loop term in a control loop (`kS + kV·v + kA·a`). Usually tuned via SysId.

**FPGA timestamp** — WPILib clock source backed by the roboRIO FPGA. Accessible via `Timer.getFPGATimestamp()`. In tests, inject a `DoubleSupplier` instead.

---

## G

**GradleRIO** — the Gradle plugin that wires WPILib, vendor deps, and deploy tasks.

---

## H

**HAL** — WPILib's Hardware Abstraction Layer. Loading `wpiHaljni` requires the native library; tests that touch it crash the JVM.

**HolonomicTrajectory** — frc.lib interface for any planner-generated trajectory (Choreo, PathPlanner, etc.). See `ChoreoTrajectoryAdapter` + `TrajectoryFollower`.

**HUB** — the 2026 REBUILT scoring structure (eight AprilTags per HUB: Red = {2, 3, 4, 5, 8, 9, 10, 11}, Blue = {18, 19, 20, 21, 24, 25, 26, 27} — see `Helper.llSetup()`). Replaces the 2025 Reefscape "Reef" nomenclature; the 2026 source code, constants, and log keys use "HUB" throughout after PR #86.

---

## I

**IO-layer pattern** — 2590's subsystem-structuring convention: `XxxIO` interface + `@AutoLog` inputs + `XxxIOReal` + `XxxIOSim` + `Xxx extends SubsystemBase`.

---

## J

**JaCoCo** — Java test-coverage tool. We gate at 80% line coverage on `frc.lib.*`.

---

## L

**Limelight** — Limelight 3 camera for AprilTag + neural detection. Connects via NetworkTables (`/limelight/...`).

**LoggedTunableNumber** — wraps a `double` in a NT4 mutable so mentors can retune without a rebuild. See `frc.lib.LoggedTunableNumber`.

---

## M

**maple-sim** — 3rd-party physics engine used in YAGSL for swerve simulation. Has a known REV NEO force-direction bug (see `MAPLE_SIM_BUG_REPORT.md`) which we work around via `Constants.Swerve.kUseMapleSimKinematicBypass = true`.

**MegaTag2** — Limelight's orientation-robust AprilTag fusion. Publishes `botpose_orb_wpiblue` to NT.

---

## P

**PDH** — REV Power Distribution Hub. Provides voltage, total current, per-channel current, temperature.

**PID** — proportional-integral-derivative controller. Our SPARK MAX / Flex PIDs are tuned on hardware; sim uses a software P+FF controller.

---

## R

**REV Hardware Client** — REV's desktop tool for flashing CAN IDs, updating firmware, tuning PIDs.

---

## S

**SSM** — Superstructure State Machine. See `SuperstructureStateMachine` for states IDLE → INTAKING → STAGING → SCORING.

**SparkAlertLogger** — 4481 pattern; surfaces each SPARK's fault register bits as WPILib Alerts on the Driver Station.

**Spotless** — Gradle plugin that auto-formats Java via Google Java Format. Runs before every `compileJava`.

**SpotBugs** — static analysis. Runs at `MEDIUM` confidence; fails the build on violations.

**SysId** — WPILib system-identification routine. Exports a DataLog that the SysId GUI tool converts into `kS / kV / kA` feedforward gains.

---

## T

**Thrifty** — Thrifty Swerve module. Our drivetrain hardware; 6.23:1 drive, 25:1 steer, 4" wheels.

**TrajectoryFollower** — 4481 pattern; combines sample FF + PID feedback on pose error. See `frc.lib.trajectory.TrajectoryFollower`.

---

## Y

**YAGSL** — Yet Another Generic Swerve Library. Our drivetrain abstraction; wraps SPARK + REV encoder config in JSON.

**YALL** — Limelight's Java library (3rd-party, distinct from Limelight's official one). We use its `RawFiducial` API.

---

## Z

**Zero gyro** — reset the heading reference to 0° with the robot facing away from the driver. `A` button on the driver controller (see `RobotContainer.configureDriverBindings`).
