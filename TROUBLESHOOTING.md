# Troubleshooting

Fast-reference playbook for when something goes wrong. Organized by *what symptom you're seeing*, not *what layer is broken*.

See also:
- [`AGENTS.md`](AGENTS.md) — for LLM-agent-specific pitfalls
- [`PRACTICE_SESSION_PLAYBOOK.md`](PRACTICE_SESSION_PLAYBOOK.md) — for in-pit triage during practice

---

## CI failed

### 1. Find out what failed

```bash
gh pr checks <PR_NUMBER>                    # green / pending / fail summary
gh run view <RUN_ID> --log-failed           # just the failing step
gh run view <RUN_ID> --log | tail -200      # last 200 lines of full log
```

### 2. Download artifacts when the log isn't enough

```bash
gh run download <RUN_ID> --name spotbugs-reports --dir /tmp/ci
gh run download <RUN_ID> --name test-results --dir /tmp/ci
gh run download <RUN_ID> --name coverage-report --dir /tmp/ci

# Then inspect
grep -E 'BugInstance|LongMessage|SourceLine' /tmp/ci/main.xml
open /tmp/ci/index.html   # coverage report
```

The SpotBugs upload was wired in PR #4 — before that, CI failures were flying blind.

### 3. Common failure classes

| Symptom in log | Likely cause | Fix |
|---|---|---|
| `> Task :spotbugsMain FAILED` / `:spotbugsTest FAILED` | A static-analysis rule tripped. Download `spotbugs-reports`, inspect `main.xml`/`test.xml` | Either fix the code or add a targeted exclusion in `config/spotbugs-exclude.xml` with a comment explaining why |
| `> Task :jacocoTestCoverageVerification FAILED` — `lines covered ratio is X, but expected minimum is 0.80` | New class in `frc.lib.*` below 80 % coverage | Either add tests, move to `frc.robot.*` (50 % target, not enforced), or refactor so pure logic is extracted and testable |
| `> Task :test FAILED` — `Gradle Test Executor 1 finished with non-zero exit value 1` (no specific test named) | JVM crashed — usually a HAL dependency loaded during class init | Find the class that imports `Debouncer`, `Timer`, or anything that calls `MathSharedStore.getTimestamp()`. Inject a `DoubleSupplier` time source and test with a `FakeClock` |
| `> Task :compileJava FAILED` with `incompatible types: X cannot be converted to Y` | YAGSL uses its own repackaged maple-sim types | Import `swervelib.simulation.ironmaple.simulation.*`, NOT `org.ironmaple.*` |
| `incompatible types: invalid method reference` | Two overloaded constructors + `assertDoesNotThrow(ClassName::new)` | Use explicit lambda: `assertDoesNotThrow(() -> new ClassName())` |
| `FL_FLOATS_AS_LOOP_COUNTERS` | `for (double d = ...; d <= X; d += ...)` | Use an `int` counter, convert inside the loop body |
| `CNT_ROUGH_CONSTANT_VALUE` on `3.14` | SpotBugs wants `Math.PI` when a literal approximates it | Use `Math.PI` or a clearly non-π value (e.g. `7.5`) |
| `CN_IDIOM_NO_SUPER_CALL` on `*AutoLogged` | AdvantageKit codegen | Already excluded in `config/spotbugs-exclude.xml` |
| `UC_USELESS_VOID_METHOD` | SpotBugs thinks a method just runs lambdas without adding logic | Either refactor or add a targeted exclusion (see PanicCommand.fire example) |

---

## Robot compiled but doesn't drive (sim)

| Symptom | Cause | Fix |
|---|---|---|
| Robot moves BACKWARD when you push joystick forward | maple-sim REV NEO force-direction bug | Check `Constants.Swerve.kUseMapleSimKinematicBypass == true`. If already true and still broken, YAGSL bumped the maple-sim fork — flip back to true |
| Pose drifts even with no joystick input | Odometry divergence — vision may be rejecting every frame | Check `Vision/RejectedForSpeed`, `Vision/InhibitedAfterReset`, `Vision/RejectedDistM` on AdvantageScope |
| Auto starts but immediately ends | Auto chooser defaulted to something unexpected | Verify `Auto Chooser` on SmartDashboard; default is "Leave Only" |
| Pose estimator snaps hard when driving near tags | `Vision/StdDevXY` too low | Increase `kBaseXyStdDevMeters` in `VisionSubsystem` (currently 0.5) |
| Pose estimator ignores vision entirely | `Vision/StdDevXY` too high OR correction-cap rejecting | Check `Vision/CorrectionThresholdM` — it's 0.5 in auto, 1.0 in teleop |

---

## Robot compiled but doesn't drive (real hardware)

### First checks (pit)

1. **Power**: all status LEDs on PDH / PDP lit?
2. **CAN**: open REV Hardware Client — does every expected ID enumerate?
3. **SparkFaults** on driver station — any stuck alerts?
4. **Encoder offsets**: are they calibrated? If `absoluteEncoderOffset` is still 0.0 in every module's JSON, **the robot will not drive straight** until you calibrate. See `PRACTICE_SESSION_PLAYBOOK.md` Phase A prerequisites.

### CAN diagnosis

```
Dashboard alert:  "Flywheel/leftVortex: Fault/Temperature"
→ Motor is thermal-throttling. Let it cool, or reduce current limit.

Dashboard alert:  "Intake/wheel: Fault/Sensor"
→ Encoder wiring issue. Check SPARK data port + Thrifty cable.

Dashboard alert:  "Swerve/Module0/drive: Fault/CAN"
→ CAN packet not received from that Spark. Reseat CAN cable.
  Check CAN bus utilization (Robot/CANBusUtilization).

Dashboard alert:  "Conveyor/spindexer: Warn/Brownout"
→ Voltage dipped below SPARK brownout threshold. Check battery.
  Cross-reference with Robot/BatteryVoltage.
```

### Swerve not initialized

Look for stack traces mentioning:
- `SwerveParser` → JSON config issue. Check `deploy/swerve/` files.
- `SwerveModuleConfiguration` → CAN ID collision between modules.
- `ADIS16470` → gyro wiring or SPI port mismatch.

---

## Deploy fails

```
./gradlew deploy --stacktrace
```

| Output | Likely cause | Fix |
|---|---|---|
| `deploy.<target> failed. Couldn't SSH to roboRIO` | Wrong team number, not on FRC network, robot off | Check `.wpilib/wpilib_preferences.json` for team 2950; radio / USB connectivity |
| `Failed to upload artifact` mid-deploy | Code built but upload died (network) | Retry; if persistent, power-cycle the radio |
| Code deploys but robot still runs old program | roboRIO didn't restart | Re-deploy, or reboot via Driver Station |
| `Compilation failed` | Wrong Java version locally | Ensure `./gradlew --version` reports Java 17 |
| "wpiHaljni not found" during deploy | Deploy is deploying a sim-only build | `wpi.java.debugJni = false` in build.gradle |

---

## AdvantageScope / NT dashboard issues

| Symptom | Fix |
|---|---|
| No keys showing up in AdvantageScope | Make sure AK's `Logger.start()` ran before any `recordOutput` call — check `Robot.robotInit()` |
| `Vision/*` keys but no others | Wrong AdvantageKit receiver — `Logger.addDataReceiver(new NT4Publisher())` needed for live, `WPILOGWriter` for replay |
| Keys appear but values are always 0 | Subsystem's `periodic()` isn't running — CommandScheduler might be stuck. Check `Commands/ActiveCount` |
| AdvantageScope slow + laggy at match | `SwerveDriveTelemetry.verbosity = HIGH` in production — flip to `POSE` (see `SwerveSubsystem.java` static init) |

---

## maple-sim specific

| Symptom | Fix |
|---|---|
| Robot appears at (0, 0) in sim instead of starting pose | YAGSL's simulation drive needs explicit `resetOdometry` — `SwerveSubsystem` already does this to `(2, 4)` |
| Game pieces spawn but intake doesn't pick them up | `IntakeSimulationAdapter` must be attached — check `RobotContainer` ctor calls `swerve.attachIntakeSimulation(simIntakeIO)` |
| Projectiles don't appear | Spawned into wrong arena? Verify `ShotSimulation` uses `swervelib.simulation.ironmaple.simulation.SimulatedArena`, NOT `org.ironmaple.*` — this trap burned us in PR #12 |
| Sim freezes / hangs | Double-ticked physics — `YAGSL.SwerveDrive` ticks `SimulatedArena.simulationPeriodic()` internally; don't call it from `Robot.simulationPeriodic` again |

---

## Tests: "how do I test X?"

Check these first, in order:

1. **Can I extract a pure-logic helper?** Most HAL-dependent classes have a testable seam (e.g. `ShotSimulation.shouldFireNow(...)`, `Helper.effectiveShotDistanceMeters(...)`).
2. **Can I inject a time source?** If the class reads `Timer.getFPGATimestamp()`, add a package-private ctor taking a `DoubleSupplier` (see `StallDetector`, `CommandLifecycleLogger`, `AreWeThereYetDebouncer`).
3. **Can I avoid instantiating `SubsystemBase`?** Don't test `Flywheel` directly; test `FlywheelIOSim` or the IO-layer inputs.
4. **Do I actually need HAL?** If yes, see [deferred] Phase 7.3 — HAL-init canary harness.

---

## Git / PR process

| Symptom | Fix |
|---|---|
| `git status` shows conflicts after merging | `git pull --ff-only` only works on fast-forward merges; if main moved and your branch has real changes, do `git merge origin/main` and resolve |
| PR shows changes from other files I didn't touch | Rebase onto latest main: `git fetch origin && git rebase origin/main` |
| CI starts at "Cache Gradle packages" failing | Transient GitHub cache issue; retrigger by pushing an empty commit |
| Want to squash commits | Don't — each commit in this repo captures a discrete review step. Merge commits preserve history (see `gh pr merge --merge`, not `--squash`) |

---

## Docs navigation

For a new team member / LLM agent opening this repo:

1. [`README.md`](README.md) — overview, stack, controllers, CAN bus summary
2. [`AGENTS.md`](AGENTS.md) — command + convention contract (read first for agents)
3. [`PRACTICE_SESSION_PLAYBOOK.md`](PRACTICE_SESSION_PLAYBOOK.md) — step-by-step pre-event testing
4. [`CAN_ID_REFERENCE.md`](CAN_ID_REFERENCE.md) — wiring truth
5. [`PLAN.md`](PLAN.md) + [`STATUS.md`](STATUS.md) — what's been shipped
6. [`AUDIT_2026-04-16.md`](AUDIT_2026-04-16.md) — the 50-finding audit
7. This file — when things break
