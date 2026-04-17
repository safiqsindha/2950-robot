# Code Tour

A guided walk through the repository, oriented for:

- A new student onboarding the codebase
- A next-season's-mentor who wants to know what transfers
- An LLM agent scoping a change

It intentionally does *not* repeat the architectural doctrine — for that, see [`ARCHITECTURE.md`](ARCHITECTURE.md) and [`AGENTS.md`](AGENTS.md). Here we describe *where things live* and *why*.

> **If you only read one section:** the `frc.lib` → `frc.robot` → tooling split is the most important thing to internalize. Everything below reinforces it.

---

## Top level

| Path | What's there | Year-specific? |
|---|---|---|
| `src/main/java/frc/lib/` | Reusable, HAL-free utilities. Pure Java + WPILib math + AdvantageKit. Covered by the ArchUnit reuse guardrail. | **No** — these lift to next year's repo as-is |
| `src/main/java/frc/robot/` | This year's robot code: subsystems (IO layer), commands, autos, constants | **Yes** — substantial rewrite expected each year |
| `src/main/java/frc/robot/Main.java` | GradleRIO-generated entry point | No — stays |
| `src/main/java/frc/robot/BuildConstants.java` | AdvantageKit build-info codegen — regenerated every `./gradlew build` | No — regenerated |
| `src/main/deploy/` | Non-code assets that ship to the roboRIO (YAGSL JSONs, Choreo `.traj`, `navgrid.json`) | Partially (YAGSL config transfers; Choreo paths are game-specific) |
| `swervelib/` | In-tree YAGSL copy. Includes a repackaged maple-sim at `swervelib.simulation.ironmaple.simulation.*` — see `MAPLE_SIM_BUG_REPORT.md` | No (drivetrain-agnostic), unless we change modules |
| `src/test/java/` | HAL-free tests. JUnit 5. Covered by JaCoCo at 80 % on `frc.lib.*` (enforced). | No — test patterns transfer |
| `tools/` | Python operator scripts (validators, curve fits, pre-deploy checks) | No — scripts transfer |
| `config/spotbugs-exclude.xml` | Targeted SpotBugs suppressions with comments | No — transfer with small tweaks |
| `.github/workflows/build.yml` | Spotless + SpotBugs + JaCoCo + ArchUnit + headless sim smoke, all gated | No — transfers wholesale |
| `vendordeps/*.json` | Vendor-dependency descriptors (WPILib, REVLib, Choreo, YAGSL, YALL, AdvantageKit, maple-sim) | Mostly — versions update each season |

---

## `frc.lib.*` — the reusable layer

Everything under here is safe to transfer year-over-year. Each subpackage is drivetrain-agnostic and mechanism-agnostic. The ArchUnit rule `lib_mustNotDependOnRobotPackages` (see `ArchitectureTest.java`) makes this a compile-time invariant.

### `frc.lib.LoggedTunableNumber`

Wraps a `double` in a NetworkTables-backed mutable. Callers poll `hasChanged(hashCode)` and re-apply when a mentor edits the value in AdvantageScope. Used everywhere PID gains, slew rates, or any other knob live — flywheel, intake, trajectory follower.

### `frc.lib.AllianceFlip`

Single function that mirrors a field pose about x=½·field for red-alliance autos. Keeps alliance handling out of subsystem / command logic.

### `frc.lib.control`

- **`LinearProfile`** — acceleration-limited slew (6328 pattern). The shared setpoint rate-limiter; wired into `Flywheel` and `Intake.arm`.

### `frc.lib.diagnostics`

The health-monitoring trio. All use the `Supplier<Snapshot>` injection pattern so tests stay HAL-free:

- **`JvmLogger`** — heap, non-heap, GC count + time. Visible under `JVM/*`.
- **`CanBusLogger`** — bus utilisation + bus-off / tx-full / rx-err / tx-err counters. Visible under `CAN/*`.
- **`PdhLogger`** — voltage, total current, temperature, 24-channel current array. Visible under `PDH/*`.
- **`CommandLifecycleLogger`** — scheduler onInit/onInterrupt/onFinish hooks; logs command activity per frame.

### `frc.lib.pathfinding`

- **`NavigationGrid`** — 2D grid cost map loaded from `deploy/navgrid.json`.
- **`AStarPathfinder`** — A* search over the grid.
- **`DynamicAvoidanceLayer`** — artificial potential-field velocity correction (opponent-aware). The PR #31 refactor lifted the Constants dependency so this class is fully library-safe.

### `frc.lib.trajectory`

- **`HolonomicTrajectory`** — planner-agnostic interface (adapted from 4481).
- **`HolonomicTrajectorySample`** — record: `(timestamp, pose, fieldRelativeSpeeds)`.
- **`ChoreoTrajectoryAdapter`** — wraps a Choreo `Trajectory<SwerveSample>`.
- **`TrajectoryFollower`** — PID-augmented follower; combines sample FF with 3 PID loops (x, y, heading).

### `frc.lib.util`

- **`Hysteresis`** — symmetric schmitt-trigger (3005 pattern).
- **`AreWeThereYetDebouncer`** — 1619 pattern; injectable time source (rewritten after WPILib `Debouncer` crashed JUnit).
- **`GeomUtil`** — `getClosestPose`, `getClosestFuturePose`, `squaredDistance` (4481 pattern).
- **`RobotName`** — file-backed enum (practice bot vs. comp bot identity via `/home/lvuser/ROBOT_NAME`).

---

## `frc.robot.*` — this year's code

### `frc.robot.subsystems`

Every subsystem follows the 2590 IO-layer pattern:

- `XxxIO` — interface with `@AutoLog` inputs record
- `XxxIOInputsAutoLogged` — generated by AdvantageKit annotation processor
- `XxxIOReal` — real hardware implementation (SPARK MAX / SPARK Flex)
- `XxxIOSim` — simulation implementation (DCMotorSim or pure arithmetic)
- `Xxx extends SubsystemBase` — thin consumer; holds the `IO` instance + `LoggedTunableNumber`s for PID + any cross-class telemetry

Applies to: `Flywheel`, `Intake`, `Conveyor`.

Exceptions — subsystems that are too tightly coupled to a library for the IO split to add value:

- `SwerveSubsystem` — directly wraps YAGSL's `SwerveDrive`. YAGSL is the IO layer.
- `VisionSubsystem` — directly reads Limelight NetworkTables. No benefit to an IO interface.
- `LEDs`, `SuperstructureStateMachine`, `StallDetector` — pure logic, no hardware interaction.

### `frc.robot.commands`

Flat layout by default; `commands.flywheel` subpackage for the four `Flywheel*` commands because they're related and share a family. Apply the same grouping if a future subsystem spawns >3 commands.

### `frc.robot.autos`

Strategy-pattern authors (`AutonomousStrategy.java`) — the runtime decision engine for which game-state to target. `ChoreoAutoCommand.java` is the factory for trajectory-following routines; `FullAutonomousCommand.java` is the strategy-driven loop.

### `frc.robot.diagnostics`

Hardware-coupled diagnostic classes. **Not** in `frc.lib.diagnostics` because they depend on REV SPARK types.

- **`SparkAlertLogger`** — wraps REV Spark's fault registers into `Alert`s on Driver Station.

### `frc.robot.simulation`

Sim-only code that shouldn't be on the real robot.

- **`ShotSimulation`** — spawns game-piece projectiles in maple-sim arena when the flywheel hits its setpoint.
- **`IntakeSimulationAdapter`** — wraps maple-sim's `InTheFrameIntake`.

### `frc.robot.Robot`, `frc.robot.RobotContainer`, `frc.robot.Constants`, `frc.robot.Helper`

- **`Robot`** — `LoggedRobot` subclass. Owns the `JvmLogger`, `CanBusLogger`, `PdhLogger`, `PowerDistribution`, `CommandLifecycleLogger`. Brownout scale lives here because every command reads it.
- **`RobotContainer`** — subsystem instantiation + button bindings + auto chooser + SmartDashboard test-mode buttons (SysId, System Test).
- **`Constants`** — inner classes by subsystem. Magic numbers land here; audit batch 1 (PR #18) and batch 2 (PR #35) did the heavy extraction.
- **`Helper`** — Limelight glue + RPM lookups. Has a mild "god object" smell; next year's offseason is a good time to split this into `ShotMath` and `LimelightFilters`.

---

## `src/test/java/`

Mirrors the main-source layout: tests for `frc.lib.X.Y` go under `src/test/java/frc/lib/X/`.

Coverage gate: **80 % on `frc.lib.*`** (enforced — build fails if below). `frc.robot.*` is not gated because much of it requires HAL.

Test conventions — see `AGENTS.md` for the full list, but the headlines:

- Never call `new Flywheel(...)` or any `SubsystemBase` subclass in a test (HAL crash).
- Inject a `DoubleSupplier` for any class that reads `Timer` or `MathSharedStore.getTimestamp()`.
- The `FakeClock` pattern (see `StallDetector` tests) is the canonical time-injection approach.
- ArchUnit tests (`ArchitectureTest`) enforce layering — `./gradlew test` fails if a frc.lib class imports frc.robot.

Notable test files:

- **`LinearProfileTest`** — 11 unit tests covering constructor validation, slew math, reset behavior, direction changes.
- **`TrajectoryFollowerTest`** — 6 tests, uses an `EmptyTrajectory` fake for edge-case coverage.
- **`HelperTest`** — covers the 971 fixed-point shoot-on-the-fly math.
- **`VisionConsensusTest`** — covers the 5-team stddev weighting + rejection rules.
- **`ArchitectureTest`** — the ArchUnit guardrail.

---

## `tools/` — operator scripts

Python, standalone, no Java dependency. Every script has a top-of-file docstring explaining purpose + usage.

| Script | Purpose | When to run |
|---|---|---|
| `can_id_validator.py` | Cross-checks Constants.java + YAGSL configs + CAN_ID_REFERENCE.md | Pre-deploy, pre-PR |
| `rpm_curve_fit.py` | Refits the `Helper.rpmFromMeters` Lagrange quadratic from a CSV of calibration points | After a new shot-calibration session |
| `deploy_health_check.py` | Pre-deploy smoke bundle: working tree, vendordeps, CAN IDs, JDK, branch | Before pressing Deploy |
| `sim_smoke_test.py` | Boots sim under Xvfb, watches for exceptions, kills | Called by CI — runs after every push |

---

## `.github/workflows/build.yml`

One job: `build`. Runs on push to `main` / `develop` and on every PR.

Steps in order:

1. Checkout + Java 17 Temurin
2. Cache Gradle packages (key includes `**/*.gradle` + wrapper.properties)
3. `./gradlew build -Pskip-inspector-wrapper`
   - Runs Spotless (auto-format, blocks on dirty working tree would be ideal; currently just prints diff)
   - Runs SpotBugs
   - Runs JUnit tests (includes ArchUnit rules)
   - Runs JaCoCo coverage verification (80 % gate on `frc.lib.*`)
4. Upload `test-results`, `coverage-report`, `spotbugs-reports` as artifacts
5. Install Xvfb
6. `python3 tools/sim_smoke_test.py --duration 20` (currently `continue-on-error: true`)
7. Upload `sim-smoke-log`

When something fails, see [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — it's organized by symptom, not by layer.

---

## Where to add new code

| What you're writing | Where it goes |
|---|---|
| A math helper (no WPILib dependency beyond math + kinematics) | `frc.lib.util` or `frc.lib.control` |
| A logger that reads from HAL/WPILib and writes to AdvantageKit | `frc.lib.diagnostics` |
| A subsystem wrapping REV / CTRE hardware | `frc.robot.subsystems` (as IO-layer triple) |
| A command | `frc.robot.commands` — use a subpackage if the command family grows |
| A constant | `frc.robot.Constants` inner class matching the subsystem |
| An operator script | `tools/` — Python, standalone |
| A hardware diagnostic (reads SPARK registers, PDH channels, etc) | `frc.robot.diagnostics` (not `frc.lib`, because it depends on REVLib) |
| A sim-only helper | `frc.robot.simulation` |

Running `./gradlew test` will catch most placement mistakes via ArchUnit. If the tests pass, the structure is compliant.
