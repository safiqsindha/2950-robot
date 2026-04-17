package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.LoggedTunableNumber;
import frc.lib.control.AsymmetricRateLimiter;
import frc.lib.control.LinearProfile;
import frc.robot.Constants;
import org.littletonrobotics.junction.Logger;

/**
 * Intake subsystem — thin consumer of {@link IntakeIO} (2590 IO pattern).
 *
 * <p>Hardware details (SPARK MAX arm motors, SPARK MAX wheel motor, sim current synthesis) are
 * encapsulated in {@link IntakeIOReal} and {@link IntakeIOSim}. This class owns scheduling,
 * telemetry, and live PID gain updates.
 *
 * <p>Motor layout:
 *
 * <ul>
 *   <li>Two SPARK MAX NEO arm motors (left + right, position PID, independently controlled due to
 *       mechanical looseness — no follow)
 *   <li>One SPARK MAX NEO wheel motor (open-loop percent output)
 * </ul>
 *
 * <p><b>Arm setpoint slewing.</b> {@link #updateTargetAngle} records the caller's <i>goal</i>
 * position; the setpoint actually pushed to the onboard PID is rate-limited by a {@link
 * LinearProfile} in {@link #periodic()}. Mirrors the Flywheel pattern — keeps the PID from
 * seeing a step change and lowers peak arm-motor current. The rate limit ({@link
 * Constants.Intake#kMaxArmAccelRotPerSec}) is tuned to be effectively transparent for normal
 * mechanical motion while still smoothing command-triggered transients.
 */
public class Intake extends SubsystemBase {

  private final IntakeIO io;
  private final IntakeIOInputsAutoLogged inputs = new IntakeIOInputsAutoLogged();

  /** Caller's target arm position — what the driver / command ultimately wants. */
  private double goalArmPositionRotations = 0.0;

  /**
   * Actual arm position commanded to the PID (ramped toward {@link #goalArmPositionRotations}
   * via {@link #armProfile}). Logged for telemetry.
   */
  private double armSetpointRotations = 0.0;

  /** Rate limiter that slews {@link #armSetpointRotations} toward the goal in {@link #periodic()}. */
  private final LinearProfile armProfile =
      new LinearProfile(Constants.Intake.kMaxArmAccelRotPerSec, 0.02);

  /**
   * Caller's goal wheel percent — stored so {@link #periodic()} can slew it. A direct write from
   * {@link #setWheel} would bypass the rate limiter.
   */
  private double goalWheelPercent = 0.0;

  /** Most recent ramped wheel percent pushed to the IO — for telemetry. */
  private double setpointWheelPercent = 0.0;

  /**
   * Asymmetric rate limiter on the wheel percent — ramps UP at {@link
   * Constants.Intake#kMaxWheelAccelPerSec} but snaps down when going to a smaller magnitude. The
   * asymmetric behaviour is deliberate: a panic-button {@code setWheel(0)} should stop the wheel
   * <i>now</i>, not ramp down over 0.25 s.
   */
  private final AsymmetricRateLimiter wheelLimiter =
      new AsymmetricRateLimiter(Constants.Intake.kMaxWheelAccelPerSec, 0.02);

  // ─── Tunable PID gains (visible and editable via AdvantageScope / NT) ───
  private final LoggedTunableNumber tunableKP =
      new LoggedTunableNumber("Intake/kP", Constants.Intake.kP);
  private final LoggedTunableNumber tunableKD =
      new LoggedTunableNumber("Intake/kD", Constants.Intake.kD);

  /** Tunable arm slew rate — live-edit from AdvantageScope to tighten / loosen the ramp. */
  private final LoggedTunableNumber tunableMaxArmAccel =
      new LoggedTunableNumber(
          "Intake/kMaxArmAccelRotPerSec", Constants.Intake.kMaxArmAccelRotPerSec);

  /** Tunable wheel slew rate. */
  private final LoggedTunableNumber tunableMaxWheelAccel =
      new LoggedTunableNumber(
          "Intake/kMaxWheelAccelPerSec", Constants.Intake.kMaxWheelAccelPerSec);

  public Intake(IntakeIO io) {
    this.io = io;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("Intake", inputs);

    // Slew the arm setpoint toward the goal and push the ramped value to the onboard PID.
    armSetpointRotations = armProfile.calculate(goalArmPositionRotations);
    io.updateTargetAngle(armSetpointRotations);

    // Asymmetric wheel slew — ramps up, snaps down.
    setpointWheelPercent = wheelLimiter.calculate(goalWheelPercent);
    io.setWheel(setpointWheelPercent);

    // Live-tune the slew rates if someone retuned them from NT.
    if (tunableMaxArmAccel.hasChanged(hashCode())) {
      armProfile.setMaxAccel(tunableMaxArmAccel.get());
    }
    if (tunableMaxWheelAccel.hasChanged(hashCode())) {
      wheelLimiter.setMaxAccel(tunableMaxWheelAccel.get());
    }

    // Derived telemetry
    Logger.recordOutput("Intake/GoalArmPositionRot", goalArmPositionRotations);
    Logger.recordOutput("Intake/SetpointArmPositionRot", armSetpointRotations);
    Logger.recordOutput("Intake/GoalWheelPercent", goalWheelPercent);
    Logger.recordOutput("Intake/SetpointWheelPercent", setpointWheelPercent);

    // Apply updated PID gains if any tunable changed since last check.
    // IntakeIOSim.setPid() is a no-op — no isSimulation() guard needed.
    if (tunableKP.hasChanged(hashCode()) || tunableKD.hasChanged(hashCode())) {
      io.setPid(tunableKP.get(), tunableKD.get());
    }
  }

  /**
   * Set intake wheel percent output. Stored as a goal; {@link #periodic()} slews toward it via
   * {@link AsymmetricRateLimiter} — ramping up at {@link Constants.Intake#kMaxWheelAccelPerSec}
   * but snapping to the commanded value on ramp-down so a panic {@code setWheel(0)} stops the
   * motor immediately.
   *
   * @param percent output (-1 to 1)
   */
  public void setWheel(double percent) {
    goalWheelPercent = percent;
  }

  /**
   * Set the target arm position for both arms. The goal is stored and the PID setpoint is slewed
   * toward it in {@link #periodic()} at up to {@link Constants.Intake#kMaxArmAccelRotPerSec}.
   *
   * @param target target position in encoder rotations
   */
  public void updateTargetAngle(double target) {
    goalArmPositionRotations = target;
  }

  /** Reset both arm encoders to zero. Call at the start of autonomous and teleop. */
  public void resetEncoder() {
    io.resetEncoder();
    // Also reset the profile + wheel limiter so the first post-reset ramp starts from zero —
    // otherwise stale limiter state would drive a phantom commanded output based on the
    // pre-reset goal.
    armProfile.reset(0.0);
    goalArmPositionRotations = 0.0;
    armSetpointRotations = 0.0;
    wheelLimiter.reset(0.0);
    goalWheelPercent = 0.0;
    setpointWheelPercent = 0.0;
  }

  /**
   * Get the current drawn by the intake wheel motor. Used by the SuperstructureStateMachine to
   * detect game piece acquisition via current spike. In simulation, returns a synthetic current
   * proportional to wheel output when {@code simulateGamePieceAcquired()} has been called.
   *
   * @return wheel motor output current in amps
   */
  public double getWheelCurrent() {
    return inputs.wheelCurrentAmps;
  }

  /** @return the goal arm position most recently set by {@link #updateTargetAngle}. */
  public double getGoalArmPositionRotations() {
    return goalArmPositionRotations;
  }

  /** @return the ramped arm setpoint currently fed to the onboard PID. */
  public double getArmSetpointRotations() {
    return armSetpointRotations;
  }

  /**
   * Inject a simulated game piece into the intake. Enables current synthesis in {@link IntakeIOSim}
   * so the superstructure state machine can advance INTAKING → STAGING in simulation. No-op on real
   * hardware.
   */
  public void simulateGamePieceAcquired() {
    io.simulateGamePieceAcquired();
  }

  /**
   * Remove the simulated game piece (e.g. after staging or scoring). Clears the current synthesis
   * gate so the next intake cycle starts clean. No-op on real hardware.
   */
  public void simulateGamePieceConsumed() {
    io.simulateGamePieceConsumed();
  }
}
