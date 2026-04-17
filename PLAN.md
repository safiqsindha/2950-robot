# 2950 Robot — Offseason Agentic Execution Plan

**Started:** 2026-04-16
**Merge policy:** Option B — auto-merge if tests pass + spotless + spotbugs clean; Opus reviews at phase gates.
**Orchestration:** Opus plans. Sonnet executes. Haiku check-in every 15 min (cron).

## Progress — updated on every status write

- [x] Phase 0 — Foundation (3 tasks, ~2.0h) ✓
  - [x] 0.1 Audit sweep → `AUDIT_2026-04-16.md` (1.0h) ✓
  - [x] 0.2 Wire `@AutoLog` annotation processor (0.5h) ✓
  - [x] 0.3 Vendor `LoggedTunableNumber` (0.5h) ✓
- [x] Phase 1 — Small wins (6 tasks, ~4.5h — scope grew slightly from audit) ✓
  - [x] 1.1 Panic button binding (0.5h) ✓
  - [x] 1.2 + 1.5 SuperstructureStateMachine cleanup (SCORING timeout + CLIMBING removal) (1.0h) ✓
  - [x] 1.3 AllianceFlip on HUB_POSE / CLIMB_POSE (1.0h) ✓
  - [x] 1.4 Sysout cleanup + 2 log-key bugs (Helper, Robot.logPhaseTransition, FullAuto/AvoidanceVelocity, StallDetector) (1.0h) ✓
  - [x] 1.6 Brownout scale — wire into AutoAlignCommand + DriveToGamePieceCommand + de-dup 6.0 magic (1.0h) ✓
- [x] Phase 2 — Near-free borrows (2 tasks, ~4.5h) ✓
  - [x] 2.1 Wrap Flywheel + Intake PID gains with LoggedTunableNumber (2.0h) ✓
  - [x] 2.2 Port Team 862 SystemTestCommand (2.5h) ✓
- [x] Phase 3 — Flywheel scoring physics (3 tasks, ~6.5h) ✓
  - [x] 3.1 Replace Helper.rpmFromMeters with Lagrange quadratic + linear tail (3.0h) ✓
  - [x] 3.2 Fix Intake sim wheel-current model (simGamePieceAcquired gate) (2.0h) ✓
  - [x] 3.3 Wire MovingShotCompensation chassis-velocity term into RPM lookup (1.5h) ✓
- [x] Phase 4 — IO-layer refactor (3 tasks, ~20h — sequential) ✓
  - [x] 4.1 Flywheel → IO layer (2590 pattern) (7.0h) ✓
  - [x] 4.2 Intake → IO layer (7.0h) ✓
  - [x] 4.3 Conveyor → IO layer (6.0h) ✓
- [x] Phase 5 — Stretch (1 task, ~3h) ✓
  - [x] 5.1 Investigate 604 QuixSwerveDriveSimulation for YAGSL+SPARK maple-sim bug (3.0h) ✓

**Phase 0–5 total:** 40.0 agent-hours across 18 tasks ✓

---

## Phase 6 — Post-scout refinement (merged 2026-04-17)

Generated from the 5-team Einstein scout + audit follow-ups. Every item shipped as a
separate PR so CI history documents each decision.

- [x] 6.1 YAGSL self-audit cleanup (PR #2) ✓
- [x] 6.2 Vision consensus rejection + stddev weighting (PR #3) ✓
- [x] 6.3 CommandLifecycleLogger + JvmLogger (PR #4) ✓
- [x] 6.4 SparkAlertLogger for 9 mechanism motors (PR #5) ✓
- [x] 6.5 971 3-iter fixed-point shoot-on-the-fly (PR #6) ✓
- [x] 6.6 ShotSimulation + IntakeSimulationAdapter stubs (PR #7) ✓
- [x] 6.7 README comprehensive rewrite (PR #8) ✓
- [x] 6.8 compDeploy Gradle task (PR #9) ✓
- [x] 6.9 Maple-sim kinematic bypass flag (PR #10) ✓
- [x] 6.10 Wire ShotSimulation into simulationPeriodic (PR #11) ✓
- [x] 6.11 Wire IntakeSimulationAdapter into IntakeIOSim (PR #12) ✓
- [x] 6.12 SparkAlertLogger covers YAGSL swerve (PR #13) ✓
- [x] 6.13 HolonomicTrajectory + ChoreoTrajectoryAdapter (PR #14) ✓
- [x] 6.14 AGENTS.md + PRACTICE_SESSION_PLAYBOOK (PR #15) ✓
- [x] 6.15 4 utility borrows — Hysteresis, AreWeThereYetDebouncer, GeomUtil, RobotName (PR #16) ✓
- [x] 6.16 LinearProfile (PR #17) ✓
- [x] 6.17 Magic-number extraction batch 1 (PR #18) ✓
- [x] 6.18 README delta refresh (PR #19) ✓

**Phase 6 total:** 18 PRs, all CI-green, all merged.

## Phase 7 — Deferred backlog (next session)

Not yet started. Each item has a reason to wait.

- [ ] 7.1 Drive-feel polish — 2056 jerk slew, 350 ms heading-hold gate, UpdateDepartPose
- [ ] 7.2 FlywheelAutoFeed 2D upgrade — thread Limelight tx into rpmFromMeters(d,θ,speeds)
- [ ] 7.3 HAL-init test harness — one canary class unlocks physics tests
- [ ] 7.4 TrajectoryFollower port (4481)
- [ ] 7.5 @AutoRoutine annotation + reflective AutoSelector (4481)
- [ ] 7.6 Migrate existing Choreo autos onto HolonomicTrajectory + TrajectoryFollower
- [ ] 7.7 971 CapU current limiting (dynamic battery-aware motor output ceiling)
- [ ] 7.8 971 hybrid EKF with replay buffer
- [ ] 7.9 Magic-number extraction batch 2 — ChoreoAutoCommand timing literals
- [ ] 7.10 Wire VisionSubsystem's local constants to Constants.Vision
- [ ] 7.11 Sim validation on a Java-ready laptop (Tier 1 of the session catalogue)

## Audit-driven backlog — Phase 6 status

Phase 0.1 audit surfaced 42 magic-number findings (see `AUDIT_2026-04-16.md` Section 2).
Status as of PR #18:

- [x] `-0.1` lower-retract — extracted to `Constants.Flywheel.kLowerRetractPercent` ✓
- [x] `0.05` heading-PID kP — extracted to `Constants.Align.kHeadingKP` ✓
- [x] `12.0` ball exit velocity — extracted as `Constants.Flywheel.kBallExitVelocityMps` ✓
- [x] Vision std-dev constants — new `Constants.Vision` block; values documented but
      VisionSubsystem still uses local static finals (deferred, 7.10)
- [ ] `3.0` / `1.5` / `3000` ChoreoAutoCommand timing literals — deferred (7.9)
- [ ] Field pose literals `3.39, 4.11, 8.23` — partially addressed by AllianceFlip;
      remaining extraction deferred

Remaining magic-number work is documented as Phase 7.9 / 7.10 above.

## Task specifications

### 0.1 — Audit sweep (read-only)
- Output: `AUDIT_2026-04-16.md`
- Scope: sysouts, magic numbers, brownout consumption, duplicate log keys, missing `@Override`
- Acceptance: report file exists with 5 sections; STATUS.md entry

### 0.2 — @AutoLog annotation processor
- Files: `build.gradle`, possibly `vendordeps/AdvantageKit.json`
- Reference: `/tmp/t2590/2025_Robot_Base_Project/build.gradle`
- Acceptance: `./gradlew build` passes; STATUS.md entry

### 0.3 — LoggedTunableNumber vendor
- Files: `src/main/java/frc/lib/LoggedTunableNumber.java`, `src/test/java/frc/lib/LoggedTunableNumberTest.java`
- Acceptance: 3+ unit tests pass; build clean; STATUS.md entry

### 1.1 — Panic button (back+start)
- File: `src/main/java/frc/robot/RobotContainer.java`
- Binding: cancel all commands, set SSM to IDLE, blink LEDs red
- Acceptance: test in `RobotContainer` subsystem or binding smoke test

### 1.2 + 1.5 — SSM cleanup (combined)
- File: `src/main/java/frc/robot/subsystems/SuperstructureStateMachine.java`, + its test
- Changes: SCORING auto-exits to IDLE after 2.0s if no `requestIdle()`; delete CLIMBING state (no Climber exists)
- Acceptance: new test for timeout path; existing tests updated

### 1.3 — AllianceFlip on strategy poses
- File: `src/main/java/frc/robot/autos/AutonomousStrategy.java`
- Change: HUB_POSE/CLIMB_POSE routed through `AllianceFlip.apply()`
- Acceptance: new red-alliance test

### 1.4 — Helper sysout cleanup + two log-key bugs (expanded per audit)
- Files:
  - `src/main/java/frc/robot/Helper.java` — delete `printRpmDistance()`; audit-verified that the three call sites (`FlywheelStatic`, `FlywheelAutoFeed`, `FlywheelAim`) use it via `// Helper.printRpmDistance(...)` or directly. Replace each call with `Logger.recordOutput("Flywheel/DebugRpm", rpm)` gated the same way. (Audit finding: effective 5 Hz loop-rate console I/O is a CAN latency risk.)
  - `src/main/java/frc/robot/Robot.java:106` — delete the `System.out.printf` in `logPhaseTransition` (data already in `Logger.recordOutput` on the three preceding lines).
  - `src/main/java/frc/robot/commands/FullAutonomousCommand.java:138` — change `Logger.recordOutput("FullAuto/AvoidanceVelocity", <Translation2d>.toString())` to log the `Translation2d` directly (AdvantageKit serializes it natively; enables field visualization).
  - `src/main/java/frc/robot/subsystems/StallDetector.java:76` — also log `false` when stall clears, not only `true`. Pair every `recordOutput(key, true)` with a `recordOutput(key, false)` on the reset path.
- Acceptance: grep `System.out\|System.err` shows zero matches in `src/main/java/`; new tests (or updated existing) cover the Translation2d log type and the StallDetector-clear path.

### 1.6 — Brownout scale consumption (retargeted per audit)
Audit confirmed `DriveCommand` already multiplies `kMaxSpeedMetersPerSec` and `kMaxAngularSpeedRadPerSec` by `Robot.getBrownoutScale()` (verified at DriveCommand.java:54-57). The real gap is:
- **`src/main/java/frc/robot/commands/AutoAlignCommand.java:65`** — translation built with `kMaxSpeedMetersPerSec` unscaled. Multiply by `Robot.getBrownoutScale()`.
- **`src/main/java/frc/robot/commands/DriveToGamePieceCommand.java:75`** — same issue. Multiply by `Robot.getBrownoutScale()`.
- **`src/main/java/frc/robot/Robot.java`** — eliminate the duplicated `6.0` magic literal (lines 86 and 97) by extracting `private static final double kBrownoutFloorVolts = 6.0`. Audit flagged this as CRITICAL — if only one site is tuned, the other silently diverges.
- Acceptance: new `BrownoutConsumptionTest` verifies both AutoAlign and DriveToGamePiece outputs are scaled below threshold; existing DriveCommand test still passes; constant appears exactly once.

### 2.1 — Tunable PID gains
- Files: `Flywheel.java`, `Intake.java`
- Change: wrap `kP`/`kI`/`kD` via `LoggedTunableNumber`; apply on change via `hasChanged(hashCode())`
- Acceptance: tuning from AdvantageScope works (manual verify documented in PR)

### 2.2 — SystemTestCommand
- Reference: Team 862 Thunder `LightningLib/src/main/java/frc/thunder/testing/SystemTestCommand.java`
- File: `src/main/java/frc/robot/commands/SystemTestCommand.java`
- Binding: driver.start() while in test mode (or a SmartDashboard "Run System Test" button)
- Acceptance: logs current/voltage of every motor; reports pass/fail per subsystem

### 3.1 — Projectile RPM model
- Reference: `/tmp/scream/SCREAMLib/src/main/java/com/teamscreamrobotics/physics/Trajectory.java` (already cloned), or fetch Team 972 `ShooterPhysics.java`
- Recommendation: polynomial regression over 3 calibration points (smoother than piecewise-linear)
- Acceptance: matches current 3-point interp within 50 RPM at calibration points; smoother curve between

### 3.2 — Intake sim current fix
- File: `src/main/java/frc/robot/subsystems/Intake.java`
- Change: gate `simWheelCurrentAmps` on a `simGamePieceAcquired` flag (settable by test or practice mode), not on raw output
- Acceptance: SSM test proves INTAKING does NOT auto-advance to STAGING without explicit injection

### 3.3 — MovingShotCompensation integration
- Files: `Helper.java`, `commands/MovingShotCompensation.java`
- Change: new `rpmFromMeters(double meters, ChassisSpeeds robotSpeeds)` overload
- Acceptance: test covering stationary vs forward-moving cases

### 4.1/4.2/4.3 — IO-layer refactor
- Reference: `/tmp/t2590/2025_Robot_Base_Project/src/main/java/frc/robot/subsystems/intake/`
- Pattern: XxxIO interface + XxxIOInputs (@AutoLog) + XxxIOReal + XxxIOSim, Xxx becomes thin consumer
- Acceptance: subsystem tests updated to use fake IO; sim still works; HALSim clean

### 5.1 — 604 QuixSwerveDriveSimulation investigation
- Reference: https://github.com/frc604/2025-public/tree/main/FRC-2025/src/main/java/frc/quixlib/maplesim
- Output: `INVESTIGATION_604_QUIXLIB.md` with adoption recommendation
- Acceptance: report only, no code changes

## Status tracking conventions

- `STATUS.md` — append-only log, each entry timestamped
- Every task-complete writes one entry
- Haiku 15-min check-ins append a summary paragraph
- Opus orchestration writes phase transitions
- Percent-complete math:
  - Current task: `(steps_complete / total_steps)`
  - Overall: `sum(weight × frac_complete) / 39.5`
