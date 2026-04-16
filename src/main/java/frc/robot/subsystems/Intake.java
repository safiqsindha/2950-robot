package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.LoggedTunableNumber;
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
 */
public class Intake extends SubsystemBase {

  private final IntakeIO io;
  private final IntakeIOInputsAutoLogged inputs = new IntakeIOInputsAutoLogged();

  // ─── Tunable PID gains (visible and editable via AdvantageScope / NT) ───
  private final LoggedTunableNumber tunableKP =
      new LoggedTunableNumber("Intake/kP", Constants.Intake.kP);
  private final LoggedTunableNumber tunableKD =
      new LoggedTunableNumber("Intake/kD", Constants.Intake.kD);

  public Intake(IntakeIO io) {
    this.io = io;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("Intake", inputs);

    // Apply updated PID gains if any tunable changed since last check.
    // IntakeIOSim.setPid() is a no-op — no isSimulation() guard needed.
    if (tunableKP.hasChanged(hashCode()) || tunableKD.hasChanged(hashCode())) {
      io.setPid(tunableKP.get(), tunableKD.get());
    }
  }

  /**
   * Set intake wheel percent output.
   *
   * @param percent output (-1 to 1)
   */
  public void setWheel(double percent) {
    io.setWheel(percent);
  }

  /**
   * Set the target arm position for both arms.
   *
   * @param target target position in encoder rotations
   */
  public void updateTargetAngle(double target) {
    io.updateTargetAngle(target);
  }

  /** Reset both arm encoders to zero. Call at the start of autonomous and teleop. */
  public void resetEncoder() {
    io.resetEncoder();
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
