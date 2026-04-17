# FAQ

Questions that have come up more than once. Organized by who's asking.

---

## For students new to the codebase

### How do I run the robot without a robot?

```bash
./gradlew simulateJavaRelease
```

Opens Sim GUI + NT4 + WebSockets. Load `AdvantageScope` separately; connect to `localhost` to see the logged signals.

On macOS the Sim GUI can't read game controllers. Use `tools/sim_keyboard_driver.py` (if present) or just drive via AdvantageScope's input widgets.

### How do I add a new subsystem?

Follow the IO-layer pattern (see [`CODE_TOUR.md`](CODE_TOUR.md)). Short version:

1. Create `XxxIO` interface with an `@AutoLog` `XxxIOInputs` class inside it
2. Create `XxxIOReal` implementation (REV SPARK wiring)
3. Create `XxxIOSim` implementation (DCMotorSim or pure math)
4. Create `Xxx extends SubsystemBase` that holds an `XxxIO` + owns a `LoggedTunableNumber` for each PID gain
5. Instantiate in `RobotContainer` selecting real vs. sim based on `RobotBase.isReal()`

The 80%-coverage gate on `frc.lib.*` does not apply to `frc.robot.subsystems.*`, but write the tests anyway — see `FlywheelTest` for the IO-simulation-test pattern.

### Where does my AdvantageKit log key need to go?

Pick a top-level namespace from [`TELEMETRY_REFERENCE.md`](TELEMETRY_REFERENCE.md). If none fit, add a new one, document it there, and keep keys under your namespace consistent.

### Why can't I `new Flywheel(...)` in a test?

`SubsystemBase.<init>` registers with the CommandScheduler, which transitively loads the WPILib HAL. In a JUnit JVM without HAL init, this crashes the test process. See [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) for the HAL-free test discipline.

### What's the difference between `frc.lib.*` and `frc.robot.*`?

- `frc.lib.*` is reusable across seasons — no hardware coupling, no game-specific logic. Enforced by `ArchitectureTest`.
- `frc.robot.*` is this year's robot. Gets substantially rewritten each offseason.

Add shared utilities to `frc.lib`; add game-specific logic to `frc.robot`.

---

## For mentors

### When do I accept a PR vs. ask for changes?

- **Accept** when: build green, tests pass, ArchUnit rules pass, JaCoCo gate met, SpotBugs clean, commit message explains the why. Style is Spotless-enforced so you don't need to nit-pick formatting.
- **Ask for changes** when: magic numbers added without a Constants entry, new code in `frc.lib` depends on `frc.robot`, a test is changed to avoid an HAL crash (fix the underlying design, don't work around).
- **Reject / rewrite** when: changes alter brownout / safety behavior without clear mentor buy-in, trajectory files changed alongside code changes (those should be separate PRs for reviewability), or CAN IDs change without a matching `CAN_ID_REFERENCE.md` update.

### What's the testing discipline?

Three layers, in order of where you add a test:

1. **Unit** (`frc.lib.*`, 80% gate): pure logic, no HAL. Inject a `DoubleSupplier` for time.
2. **IO-layer** (`frc.robot.subsystems.*`): exercise `XxxIOSim` directly — no `SubsystemBase` instantiation.
3. **Integration** (on-robot / sim): documented checklists, not JUnit — see `SIM_VALIDATION_SCRIPT.md`.

### When should we bump WPILib / YAGSL / Choreo?

- WPILib: annually, during kickoff prep. Dependabot is configured to ignore WPILib bumps.
- YAGSL: on a release with a REV sign fix (see `MAPLE_SIM_BUG_REPORT.md`) or a new feature we need. Always test via `./gradlew simulateJavaRelease` before merging.
- Choreo: only when a new feature (new sample fields, new event markers) is needed; version is pinned in `vendordeps/`.

### What's the pre-deploy checklist?

```bash
python3 tools/deploy_health_check.py
```

That runs CAN ID validation, branch check (comp-*), JDK version, vendor-dep JSON sanity. See [`PIT_CHECKLIST.md`](PIT_CHECKLIST.md) for the full-event runbook.

---

## For LLM agents

### What do I read first?

1. [`AGENTS.md`](AGENTS.md) — conventions and pitfalls
2. [`CODE_TOUR.md`](CODE_TOUR.md) — directory-level orientation
3. `PLAN.md` — what's been shipped + deferred

### What breaks CI most often?

- `UNUSED_IMPORT` — Spotless will add/remove imports; never leave an unused one manually
- `CNT_ROUGH_CONSTANT_VALUE` — SpotBugs wants `Math.PI`, not `3.14`
- `FL_FLOATS_AS_LOOP_COUNTERS` — use `int` loop counters, cast to `double` inside the body
- `EI_EXPOSE_REP*` — already suppressed globally (command-based pattern)
- `UC_USELESS_VOID_METHOD` — suppress on the specific class if the method is a testable seam
- JaCoCo `80%` gate on `frc.lib.*` — write tests for every new class in `frc.lib`
- ArchUnit `lib_mustNotDependOnRobotPackages` — a new class in `frc.lib` referencing `frc.robot.Constants` will fail this rule; refactor to take the constants as ctor params

### How do I test a class that touches HAL?

Don't — extract the logic into a pure-Java helper or use `DoubleSupplier` injection for time. See `StallDetector` for the canonical pattern.

### What's the merge policy?

`gh pr merge <number> --squash --auto`. Auto-merge triggers once CI passes; Spotless fixes format; reviewer approval is optional at the solo-dev phase.
