package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import org.littletonrobotics.junction.Logger;

/**
 * Climber subsystem — thin consumer of {@link ClimberIO} (2590 pattern).
 *
 * <p><b>Scaffold status:</b> the physical climber is not installed on the 2026 robot. This
 * subsystem ships so commands + sim can exercise it ahead of hardware arrival. When the real
 * climber is wired, instantiate with {@code new Climber(new ClimberIOReal(canId))} in
 * {@code RobotContainer}; until then, {@code new Climber(new ClimberIOSim())} keeps it HAL-free.
 */
public final class Climber extends SubsystemBase {

  private final ClimberIO io;
  private final ClimberIOInputsAutoLogged inputs = new ClimberIOInputsAutoLogged();

  private double goalPositionRotations = 0.0;

  public Climber(ClimberIO io) {
    this.io = io;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("Climber", inputs);
    Logger.recordOutput("Climber/GoalPositionRot", goalPositionRotations);
  }

  /**
   * Set the target climb position (encoder rotations). Direct — not rate-limited because the
   * climber only moves in response to explicit operator commands, not continuous inputs.
   */
  public void setTargetPosition(double rotations) {
    goalPositionRotations = rotations;
    io.setTargetPosition(rotations);
  }

  /** Set the climber to a percent output, bypassing closed-loop. */
  public void setPercentOutput(double percent) {
    io.setPercentOutput(percent);
  }

  /** Stop the climber and clear the closed-loop target. */
  public void stop() {
    goalPositionRotations = inputs.positionRotations;
    io.stop();
  }

  /** @return current climber position (encoder rotations). */
  public double getPositionRotations() {
    return inputs.positionRotations;
  }

  /** @return current drawn by the climber motor (A). */
  public double getCurrentAmps() {
    return inputs.currentAmps;
  }
}
