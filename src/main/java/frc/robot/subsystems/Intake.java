package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.LoggedTunableNumber;
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

  // ─── Tunable PID gains (visible and editable via AdvantageScope / NT) ───
  private final LoggedTunableNumber tunableKP =
      new LoggedTunableNumber("Intake/kP", Constants.Intake.kP);
  private final LoggedTunableNumber tunableKD =
      new LoggedTunableNumber("Intake/kD", Constants.Intake.kD);

  /** Tunable arm slew rate — live-edit from AdvantageScope to tighten / loosen the ramp. */
  private final LoggedTunableNumber tunableMaxArmAccel =
      new LoggedTunableNumber(
          "Intake/kMaxArmAccelRotPerSec", Constants.Intake.kMaxArmAccelRotPerSec);

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

    // Live-tune the slew rate if someone retuned it from NT.
    if (tunableMaxArmAccel.hasChanged(hashCode())) {
      armProfile.setMaxAccel(tunableMaxArmAccel.get());
    }

    // Derived telemetry
    Logger.recordOutput("Intake/GoalArmPositionRot", goalArmPositionRotations);
    Logger.recordOutput("Intake/SetpointArmPositionRot", armSetpointRotations);

    // Apply updated PID gains if any tunable changed since last check.
    // IntakeIOSim.setPid() is a no-op — no isSimulation() guard needed.
    if (tunableKP.hasChanged(hashCode()) || tunableKD.hasChanged(hashCode())) {
      io.setPid(tunableKP.get(), tunableKD.get());
    }
  }

  /**
   * Set intake wheel percent output. Open-loop — not rate-limited because the caller may need to
   * snap the wheel to zero on interrupt without a ramp-down.
   *
   * @param percent output (-1 to 1)
   */
  public void setWheel(double percent) {
    io.setWheel(percent);
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
    // Also reset the profile so the first post-reset ramp starts from zero — otherwise the
    // profile's last value would drive a phantom commanded position based on the pre-reset state.
    armProfile.reset(0.0);
    goalArmPositionRotations = 0.0;
    armSetpointRotations = 0.0;
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
