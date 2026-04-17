# Developer Testing Guide

For team members writing code. Complements [`STUDENT_TESTING_GUIDE.md`](STUDENT_TESTING_GUIDE.md) (which covers hardware-side testing of the built robot) with the discipline needed to write tests that run in CI without a robot attached.

---

## The core constraint: HAL-free

Most WPILib classes transitively load `wpiHaljni` — the C++ HAL. In a JUnit JVM, loading HAL crashes the test process. You can't just `new Flywheel(...)` in a test; it'll bring down the whole suite.

Rules:

1. **Never instantiate a `SubsystemBase` subclass in a test.** `Flywheel`, `Intake`, `Conveyor`, `SwerveSubsystem`, `VisionSubsystem`, `SuperstructureStateMachine` — all off-limits. Test the logic they wrap, not the wrapper.

2. **Never call `Timer.getFPGATimestamp()` or `Debouncer`.** These touch HAL. Inject a `DoubleSupplier` for time instead — see `StallDetector`, `CommandLifecycleLogger`, `AreWeThereYetDebouncer`.

3. **Never `new Alert(...)` in a test.** Alerts register with `SendableRegistry`, which touches HAL. If your class needs an `Alert` sink, accept a `Consumer<Boolean>` in the ctor so tests can pass a fake.

4. **Never create `PowerDistribution`, `Notifier`, `CANSparkMax`, or similar hardware handles.** Each either loads HAL or requires a physical device.

---

## The FakeClock pattern

The canonical approach for time-dependent logic. Given:

```java
public final class MyWidget {
  private final DoubleSupplier clockSupplier;
  public MyWidget(DoubleSupplier clockSupplier) { this.clockSupplier = clockSupplier; }
  public MyWidget() { this(Timer::getFPGATimestamp); }
  // ... uses clockSupplier.getAsDouble() internally ...
}
```

The test:

```java
private static final class FakeClock implements DoubleSupplier {
  double now = 0.0;
  public double getAsDouble() { return now; }
  void advance(double seconds) { now += seconds; }
}

@Test
void mywidget_afterDelay_doesX() {
  FakeClock clock = new FakeClock();
  MyWidget w = new MyWidget(clock);
  w.tick();
  clock.advance(0.5);
  w.tick();
  // assertions
}
```

Every time-dependent class in `frc.lib` uses this pattern. Follow it for new code.

---

## The IO-layer pattern (testability half)

Every subsystem ships as four files: `XxxIO` interface, `XxxIOReal`, `XxxIOSim`, `Xxx extends SubsystemBase`. Tests target `XxxIOSim` directly:

```java
@Test
void flywheelSim_setTargetRpm_doesNotThrow() {
  FlywheelIOSim sim = new FlywheelIOSim();  // pure Java, HAL-free
  assertDoesNotThrow(() -> sim.setTargetRpm(3500.0));
}
```

The `Xxx` class itself is never instantiated in a test — its logic (periodic wiring, telemetry publishing) is verified on the sim or robot at runtime.

**If you want to test a subsystem's *logic* (state transitions, setpoint slewing):** extract the pure-math core into a package-private static helper. See `SuperstructureStateMachine.computeNextState` (static pure function, fully tested) vs `SuperstructureStateMachine.periodic` (HAL-coupled, exercised at runtime).

---

## Command testability

Commands are harder than subsystems because they take SubsystemBase subclasses in their ctors. Two options:

**A. Pure factory pattern.** If the command's complex logic can be expressed as a static method, do that and test the method directly. See `PanicCommand.fire` (static, takes a runnable list; `PanicCommand.build` wraps it for the command scheduler).

**B. Minimal integration.** If the command is dominated by hardware wiring (like the Flywheel family), accept that it'll be validated at sim/hardware time, not in JUnit. Extract just enough pure logic for unit coverage — see `FlywheelAutoFeed.computePredictedRpm` (package-private helper that takes a distance; the full execute() is HAL-coupled).

---

## Fake IO implementations

For logic that needs to see IO-layer responses in a test (e.g. "does this command advance to feeder when the flywheel reports at-speed?"):

```java
static final class FakeFlywheelIO implements FlywheelIO {
  double lastCommandedRpm = 0.0;
  double currentRpm = 0.0;  // test harness can set this

  @Override public void updateInputs(FlywheelIOInputs inputs) {
    inputs.velocityRpm = currentRpm;
    inputs.connected = true;
  }
  @Override public void setTargetRpm(double rpm) { lastCommandedRpm = rpm; }
  // ... other methods no-op ...
}
```

Write these test-side (not production) to avoid ArchUnit flagging them as production code in the wrong package.

---

## ArchUnit guardrails

`ArchitectureTest` enforces package dependencies at test time:

- `frc.lib.*` must not depend on `frc.robot.*`
- `frc.lib.*` must not depend on `swervelib.*`
- `frc.robot.subsystems.*` must not depend on `frc.robot.commands.*`
- `frc.lib.diagnostics.*` stays consumer-only
- No production class may call `Thread.sleep`
- IO interfaces live in `frc.robot.subsystems.*`
- `*AutoLogged` classes live only where AdvantageKit emits them
- No ad-hoc `frc.robot.config` / `frc.robot.constants` packages

If ArchUnit fails your PR, read the `.because(...)` clause — the rule explains itself. Don't suppress; refactor.

---

## JaCoCo 80% gate

`frc.lib.*` is gated at 80% line coverage. Failing the gate blocks merge. Add tests for every new class in `frc.lib`; the existing tests are the template.

`frc.robot.*` isn't gated because much of it requires HAL. Write tests anyway where you can — the `HelperTest`, `SuperstructureStateMachineTest`, and `ArchitectureTest` files are your models.

---

## Running tests locally

```bash
./gradlew test                       # all tests
./gradlew test --tests HelperTest    # one class
./gradlew jacocoTestCoverageVerification   # coverage gate
./gradlew check                      # spotless + spotbugs + tests + coverage
```

The full `./gradlew build` takes ~90 s on ubuntu-latest CI. Locally with a warm Gradle daemon, ~15 s. If `--daemon` feels laggy, nuke it: `./gradlew --stop`.

---

## What to do when a test crashes the JVM

You probably touched HAL without meaning to. Common culprits:

1. **Subsystem ctor.** `new Flywheel(new FlywheelIOSim())` — the `SubsystemBase.<init>` registers with the CommandScheduler, which loads Notifier → HAL. Test the IO class directly instead.
2. **`WPIUtilJNI`, `MathSharedStore`, `Alert`.** Any of these in your main-source class means the test can't touch that class. Refactor to inject the WPILib dependency via a supplier/consumer.
3. **Static initialiser.** A `static final` of a WPILib type fires during class load. Move it to an instance field or inject it.

If you genuinely need HAL in a test, see `FlywheelIOSimPhysicsTest` — it's the HAL-init canary pattern (currently `@Disabled` because CI flakes). That's the escape hatch, not the default.

---

## See also

- [`AGENTS.md`](AGENTS.md) — conventions for LLM-driven contributions
- [`CODE_TOUR.md`](CODE_TOUR.md) — where things live
- [`docs/adr/`](docs/adr/) — why things are the way they are
- [`FAQ.md`](FAQ.md) — first-question answers for new contributors
