package frc.robot.commands;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Conveyor;
import frc.robot.subsystems.Flywheel;
import frc.robot.subsystems.Intake;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * Motor connectivity and response test. Runs each subsystem briefly at low output and verifies that
 * current/RPM respond above minimum thresholds.
 *
 * <p>Ported from Team 862 Thunder LightningLib SystemTestCommand pattern. Designed for use in test
 * mode — add a SmartDashboard button ("Run System Test") or bind to driver.start() in test mode.
 *
 * <p>Test sequence (~1.5 s total):
 *
 * <ol>
 *   <li>Flywheel: command 500 RPM for {@value #kTestPhaseSecs} s, check measured RPM responds
 *   <li>Intake wheel: run at {@value #kWheelOutputPercent} % for {@value #kTestPhaseSecs} s, check
 *       current
 *   <li>Conveyor: run at {@value #kWheelOutputPercent} % for {@value #kTestPhaseSecs} s, check
 *       current
 * </ol>
 *
 * <p>Results are logged under {@code SystemTest/} in AdvantageScope and published to
 * SmartDashboard. {@link #getResults()} returns the list for assertions in unit-adjacent tests.
 */
public class SystemTestCommand extends Command {

  // ── Timing / output constants ─────────────────────────────────────────────

  /**
   * Duration (s) to run each motor before sampling results. 350 ms is ~9× longer than the Intake
   * wheel's rate-limiter ramp time ({@code Constants.Intake.kMaxWheelAccelPerSec = 4.0} → reaches
   * {@code kWheelOutputPercent = 0.15} in 37.5 ms), so the current measurement lands well after the
   * ramp has settled. If either constant is retuned aggressively, re-check that invariant: {@code
   * kTestPhaseSecs > kWheelOutputPercent / kMaxWheelAccelPerSec × 3}.
   */
  static final double kTestPhaseSecs = 0.35;

  /** Open-loop output for wheel/conveyor tests (small but enough to draw measurable current). */
  static final double kWheelOutputPercent = 0.15;

  /** Flywheel RPM setpoint during test — low enough to spin up quickly, high enough to measure. */
  static final double kFlywheelTestRpm = 500.0;

  // ── Pass/fail thresholds ──────────────────────────────────────────────────

  /** Minimum RPM for flywheel to report PASS — proves the motor spun up. */
  static final double kMinFlywheelRpm = 50.0;

  /** Minimum current (A) for wheel/conveyor motors to report PASS — proves motor is connected. */
  static final double kMinMotorCurrentAmps = 0.5;

  // ── Result record ─────────────────────────────────────────────────────────

  /**
   * Immutable snapshot of one motor group's test result.
   *
   * @param name human-readable motor/subsystem label
   * @param measuredAmps current drawn during test (amps)
   * @param pass {@code true} if the measured value exceeded the acceptance threshold
   */
  public record TestResult(String name, double measuredAmps, boolean pass) {}

  // ── State ─────────────────────────────────────────────────────────────────

  private enum Phase {
    FLYWHEEL,
    INTAKE_WHEEL,
    CONVEYOR,
    DONE
  }

  private final Flywheel flywheel;
  private final Intake intake;
  private final Conveyor conveyor;
  private final DoubleSupplier timeSource;

  private Phase phase;
  private double phaseStartTime;
  private final List<TestResult> results = new ArrayList<>();

  // ── Constructors ──────────────────────────────────────────────────────────

  /** Production constructor — uses real FPGA timer. */
  public SystemTestCommand(Flywheel flywheel, Intake intake, Conveyor conveyor) {
    this(flywheel, intake, conveyor, Timer::getFPGATimestamp);
  }

  /**
   * Injection constructor for testing — caller supplies a deterministic time source.
   *
   * @param timeSource returns the current timestamp in seconds
   */
  SystemTestCommand(
      Flywheel flywheel, Intake intake, Conveyor conveyor, DoubleSupplier timeSource) {
    this.flywheel = flywheel;
    this.intake = intake;
    this.conveyor = conveyor;
    this.timeSource = timeSource;
    addRequirements(flywheel, intake, conveyor);
  }

  // ── Command lifecycle ─────────────────────────────────────────────────────

  @Override
  public void initialize() {
    phase = Phase.FLYWHEEL;
    phaseStartTime = timeSource.getAsDouble();
    results.clear();
    flywheel.setTargetRpm(kFlywheelTestRpm);
    Logger.recordOutput("SystemTest/Status", "RUNNING");
    Logger.recordOutput("SystemTest/AllPass", false);
  }

  @Override
  public void execute() {
    double elapsed = timeSource.getAsDouble() - phaseStartTime;

    switch (phase) {
      case FLYWHEEL -> {
        if (elapsed >= kTestPhaseSecs) {
          double rpm = flywheel.getCurrentRpm();
          double amps = flywheel.getMotorCurrentAmps();
          // RPM-only check could silently pass a disconnected motor whose encoder is still
          // spinning from inertia. Require current too — but skip the current check in sim
          // where DCMotorSim's reported current depends on the specific motor model and
          // isn't guaranteed to exceed a pit-realistic threshold.
          boolean rpmPass = rpm > kMinFlywheelRpm;
          boolean currentPass =
              amps > kMinMotorCurrentAmps || edu.wpi.first.wpilibj.RobotBase.isSimulation();
          boolean pass = rpmPass && currentPass;
          results.add(new TestResult("Flywheel", amps, pass));
          Logger.recordOutput("SystemTest/Flywheel/RPM", rpm);
          Logger.recordOutput("SystemTest/Flywheel/CurrentAmps", amps);
          Logger.recordOutput("SystemTest/Flywheel/RpmPass", rpmPass);
          Logger.recordOutput("SystemTest/Flywheel/CurrentPass", currentPass);
          Logger.recordOutput("SystemTest/Flywheel/Pass", pass);

          flywheel.setTargetRpm(0);
          intake.setWheel(kWheelOutputPercent);
          phase = Phase.INTAKE_WHEEL;
          phaseStartTime = timeSource.getAsDouble();
        }
      }
      case INTAKE_WHEEL -> {
        if (elapsed >= kTestPhaseSecs) {
          double amps = intake.getWheelCurrent();
          boolean pass = amps > kMinMotorCurrentAmps;
          results.add(new TestResult("IntakeWheel", amps, pass));
          Logger.recordOutput("SystemTest/IntakeWheel/CurrentAmps", amps);
          Logger.recordOutput("SystemTest/IntakeWheel/Pass", pass);

          intake.setWheel(0);
          conveyor.setConveyor(kWheelOutputPercent);
          phase = Phase.CONVEYOR;
          phaseStartTime = timeSource.getAsDouble();
        }
      }
      case CONVEYOR -> {
        if (elapsed >= kTestPhaseSecs) {
          double amps = conveyor.getConveyorCurrentAmps();
          boolean pass = amps > kMinMotorCurrentAmps;
          results.add(new TestResult("Conveyor", amps, pass));
          Logger.recordOutput("SystemTest/Conveyor/CurrentAmps", amps);
          Logger.recordOutput(
              "SystemTest/Spindexer/CurrentAmps", conveyor.getSpindexerCurrentAmps());
          Logger.recordOutput("SystemTest/Conveyor/Pass", pass);

          conveyor.setConveyor(0);
          phase = Phase.DONE;
        }
      }
      case DONE -> {
        // No-op — isFinished() returns true next cycle
      }
    }
  }

  @Override
  public void end(boolean interrupted) {
    // Safety: ensure all motors are zeroed even if the command is cancelled mid-test
    flywheel.setTargetRpm(0);
    intake.setWheel(0);
    conveyor.setConveyor(0);

    long passCount = results.stream().filter(TestResult::pass).count();
    boolean allPass = !interrupted && passCount == results.size() && !results.isEmpty();
    Logger.recordOutput("SystemTest/Status", interrupted ? "INTERRUPTED" : "COMPLETE");
    Logger.recordOutput("SystemTest/PassCount", (double) passCount);
    Logger.recordOutput("SystemTest/TotalChecks", (double) results.size());
    Logger.recordOutput("SystemTest/AllPass", allPass);
  }

  @Override
  public boolean isFinished() {
    return phase == Phase.DONE;
  }

  // ── Test accessor ─────────────────────────────────────────────────────────

  /**
   * Returns an unmodifiable view of the results collected so far. Safe to call after the command
   * has finished; empty if called before {@link #initialize()}.
   */
  List<TestResult> getResults() {
    return Collections.unmodifiableList(results);
  }
}
