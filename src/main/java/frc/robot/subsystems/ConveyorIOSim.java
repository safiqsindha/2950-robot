package frc.robot.subsystems;

/**
 * Simulation implementation of {@link ConveyorIO}.
 *
 * <p>No physics simulation is performed — both motors are open-loop only, so tracking the commanded
 * percent output is sufficient for telemetry and test verification. Motor currents are zero (SPARK
 * MAX {@code getOutputCurrent()} returns 0 in simulation anyway).
 *
 * <p>This implementation is entirely HAL-free: {@link #updateInputs} performs only pure Java
 * assignments.
 */
public class ConveyorIOSim implements ConveyorIO {

  private double conveyorPercent = 0.0;
  private double spindexerPercent = 0.0;

  @Override
  public void updateInputs(ConveyorIOInputs inputs) {
    inputs.connected = true;
    inputs.conveyorAppliedOutput = conveyorPercent;
    inputs.spindexerAppliedOutput = spindexerPercent;
    // SPARK MAX getOutputCurrent() returns 0 in simulation; match that behaviour.
    inputs.conveyorCurrentAmps = 0.0;
    inputs.spindexerCurrentAmps = 0.0;
  }

  @Override
  public void setConveyor(double percent) {
    conveyorPercent = percent;
    spindexerPercent = percent;
  }

  @Override
  public void stop() {
    conveyorPercent = 0.0;
    spindexerPercent = 0.0;
  }
}
