# AGENTS.md — contract for LLM / agent sessions on 2950-robot

> Pattern borrowed from Team 3005 RoboChargers. This file is the **authoritative
> command + convention reference** for Claude Code, Cursor, Copilot workspaces,
> and any human who wants to reproduce the session's workflow.

Read this first every session. It's short on purpose.

---

## Stack summary

- Java 17 (Temurin), WPILib 2026 (GradleRIO), AdvantageKit LoggedRobot
- YAGSL 2026.3.14 swerve (in-tree copy at `swervelib/` + vendordep)
- REV SPARK MAX / SPARK Flex only — no CTRE. Phoenix6 vendordep is pulled transitively by YAGSL only.
- Choreo 2026.0.2 pre-planned trajectories + PathPlanner AD* for runtime pathfinding
- Limelight 3 (hostname `limelight`) — MegaTag2 AprilTags + neural game-piece detector
- maple-sim 0.4.0-beta simulation physics
- Spotless (google-java-format), SpotBugs 4.8.6, JaCoCo — **80 % line coverage gate on `frc.lib.*`** enforced in CI

## Every-session commands

```bash
# Build + tests + Spotless + SpotBugs + JaCoCo
./gradlew build

# Sim (macOS: Sim GUI can't read game controllers — use AdvantageScope)
./gradlew simulateJava

# Deploy to robot
./gradlew deploy

# Event-week auto-commit + deploy (only fires on comp* branches)
./gradlew compDeploy

# Auto-format before committing
./gradlew spotlessApply
```

## Conventions that save rewrites

- **Constants** live in `frc.robot.Constants` organised by subsystem. Prefix with lowercase `k`. **Extract any literal used > 1 time**.
- **Sim IO classes use `@AutoLog`** and the 2590 pattern: `XxxIO` interface + `XxxIOInputs` + `XxxIOReal` + `XxxIOSim` + thin `Xxx extends SubsystemBase`. Don't introduce a new pattern.
- **Tests are HAL-free** unless explicitly opting into the HAL harness (see below).
- **Tunable PIDs** use `LoggedTunableNumber` with `hasChanged(hashCode())` gating — not `SmartDashboard.putNumber`.
- **Injectable time sources** (`DoubleSupplier`) for any class that reads `Timer.getFPGATimestamp()` — makes the class testable (StallDetector pattern).
- **Alliance-aware poses** route through `frc.lib.AllianceFlip`. Field-relative poses are blue-alliance origin.

## HAL-free test rule — what it means

`SubsystemBase` triggers `CommandScheduler` → loads `wpiHaljni`. `DCMotorSim.setInputVoltage` calls `RobotController.getBatteryVoltage` which ALSO loads `wpiHaljni`. Both crash headless JUnit.

Rules:
1. **Never instantiate `SubsystemBase` subclasses in tests.** Test the IO layer directly or extract pure-logic helpers.
2. **Never call `DCMotorSim.setInputVoltage` in tests** unless you explicitly opt into the HAL harness.
3. **Package-private helpers for testability** — `static boolean shouldFire(...)` beats `private boolean`. If it's logic, make it callable.

Future: one canary test class with `HAL.initialize(500, 0)` in `@BeforeAll` to unlock real physics tests. See `PR #21` / item 32 in the session plan.

## SpotBugs + JaCoCo gotchas we've hit

| Symptom | Cause | Fix |
|---|---|---|
| `CNT_ROUGH_CONSTANT_VALUE` on `3.14` in a test | SpotBugs wants `Math.PI` when a literal approximates it | Use `Math.PI` or a clearly non-π value (e.g. `7.5`) |
| `FL_FLOATS_AS_LOOP_COUNTERS` | `for (double d = ...; d <= ...; d += ...)` | Use `int` loop counters, convert inside the body |
| `CN_IDIOM_NO_SUPER_CALL` on `*AutoLogged` classes | AdvantageKit codegen doesn't call `super.clone()` | Already excluded via `~.*AutoLogged` regex in `config/spotbugs-exclude.xml` |
| JaCoCo "lines covered ratio is X, but expected minimum is 0.80" on `frc.lib.*` | New class in `frc.lib.*` without enough tests | Move to `frc.robot.*` (50 % target, not enforced), OR add pure-logic helpers with tests, OR restructure to make logic testable without HAL |
| Build fails at `:compileJava` with maple-sim type mismatch | YAGSL ships a **repackaged** copy of maple-sim at `swervelib.simulation.ironmaple.simulation.*` | Use YAGSL's package, NOT `org.ironmaple.*`. Same API, different package |
| `CommandLifecycleLogger::new` ambiguous for `assertDoesNotThrow` | Two constructors on the class | Use explicit lambda `() -> new CommandLifecycleLogger()` |

## CI failure recovery

When the PR turns red:

```bash
# Download the SpotBugs report (PR #4 wired this up)
gh run download <run-id> --name spotbugs-reports --dir /tmp/ci
cat /tmp/ci/test.xml | grep -A3 BugInstance

# Test results
gh run download <run-id> --name test-results --dir /tmp/ci
open /tmp/ci/*.html

# Coverage report
gh run download <run-id> --name coverage-report --dir /tmp/ci
open /tmp/ci/index.html
```

## Commit conventions

- Format: `<type>(<scope>): <summary>`
- Types we use: `feat`, `fix`, `chore`, `docs`, `ci`, `test`
- Scopes we use: `swerve`, `vision`, `shooter`, `sim`, `diagnostics`, `test`, `gradle`
- End every agent-written commit with the `Co-Authored-By` line for the model

## Common pitfalls for LLM agents

1. **Adding a new class to `frc.lib.*` without 80% test coverage** — triggers the JaCoCo gate. Move to `frc.robot.*` if untestable (e.g. HAL-dependent), or add pure-logic helpers.
2. **Forgetting YAGSL's repackaged maple-sim** — always use `swervelib.simulation.ironmaple.simulation.*`, not `org.ironmaple.*`.
3. **Using `System.out.println`** — grep enforces zero hits in `src/main/java/`. Use `Logger.recordOutput` instead.
4. **Amending commits when a pre-commit hook fails** — always make a NEW commit; see CLAUDE.md git-safety rules.
5. **Adding HAL-dependent code without a flag/guard** — in particular, `DriverStation.isAutonomous()` during init throws. Gate with `RobotBase.isSimulation()` or defer to periodic.

## Context-gathering shortcuts

When starting a new session:

```bash
# What phases / work is complete?
cat PLAN.md STATUS.md

# What hardware do we have?
cat CAN_ID_REFERENCE.md

# What did the audit flag?
cat AUDIT_2026-04-16.md

# What scout patterns are outstanding?
grep -A2 'Deferred\|Follow-up\|TODO' README.md
```

## Where-to-put-it index

| Kind of code | Package | Coverage gate |
|---|---|---|
| Pure-math utility (no HAL) | `frc.lib` | 80 % enforced |
| Hardware-coupled util | `frc.robot.diagnostics`, `frc.robot.simulation` | none |
| Commands | `frc.robot.commands` | none |
| Subsystem IO triplets | `frc.robot.subsystems` (same package as `Xxx`) | none |
| Auto routines | `frc.robot.autos` | none |
| Test only | `src/test/java/<mirror of main>` | n/a |
