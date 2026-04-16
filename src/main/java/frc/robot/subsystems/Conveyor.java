package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import org.littletonrobotics.junction.Logger;

/**
 * Conveyor subsystem — thin consumer of {@link ConveyorIO} (2590 IO pattern).
 *
 * <p>Hardware details (SPARK MAX brushed belt motor and SPARK MAX brushless spindexer) are
 * encapsulated in {@link ConveyorIOReal} and {@link ConveyorIOSim}.
 *
 * <p>Motor layout:
 *
 * <ul>
 *   <li>Brushed conveyor belt motor — indexes fuel toward flywheel
 *   <li>Brushless spindexer motor — agitates/rotates the fuel column before indexing
 * </ul>
 */
public class Conveyor extends SubsystemBase {

  private final ConveyorIO io;
  private final ConveyorIOInputsAutoLogged inputs = new ConveyorIOInputsAutoLogged();

  public Conveyor(ConveyorIO io) {
    this.io = io;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("Conveyor", inputs);
  }

  /**
   * Set both the conveyor belt and spindexer motors to the same percent output.
   *
   * @param percent output (-1 to 1)
   */
  public void setConveyor(double percent) {
    io.setConveyor(percent);
  }

  /**
   * Current drawn by the brushed conveyor belt motor. Used by {@link
   * frc.robot.commands.SystemTestCommand} to verify motor connectivity.
   */
  public double getConveyorCurrentAmps() {
    return inputs.conveyorCurrentAmps;
  }

  /**
   * Current drawn by the brushless spindexer motor. Used by {@link
   * frc.robot.commands.SystemTestCommand} to verify motor connectivity.
   */
  public double getSpindexerCurrentAmps() {
    return inputs.spindexerCurrentAmps;
  }
}
