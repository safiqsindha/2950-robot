package frc.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;

/**
 * Hardware abstraction interface for the Conveyor subsystem (2590 IO pattern).
 *
 * <p>Concrete implementations:
 *
 * <ul>
 *   <li>{@link ConveyorIOReal} — SPARK MAX (brushed conveyor + brushless spindexer) via REVLib
 *   <li>{@link ConveyorIOSim} — pure-Java simulation (no DCMotorSim; tracks percent output)
 * </ul>
 */
public interface ConveyorIO {

  /** Logged sensor readings refreshed every 20 ms via {@link #updateInputs}. */
  @AutoLog
  class ConveyorIOInputs {
    /** True when the conveyor motor SPARK MAX is reachable on the CAN bus. */
    public boolean connected = false;

    /** Applied output fraction of the brushed conveyor belt motor (-1 to 1). */
    public double conveyorAppliedOutput = 0.0;

    /** Applied output fraction of the brushless spindexer motor (-1 to 1). */
    public double spindexerAppliedOutput = 0.0;

    /** Supply current drawn by the brushed conveyor belt motor (A). */
    public double conveyorCurrentAmps = 0.0;

    /** Supply current drawn by the brushless spindexer motor (A). */
    public double spindexerCurrentAmps = 0.0;
  }

  /** Refresh all sensor readings into {@code inputs}. Must be called every 20 ms in periodic(). */
  void updateInputs(ConveyorIOInputs inputs);

  /**
   * Set both the conveyor belt and spindexer motors to the same percent output.
   *
   * @param percent output (-1 to 1)
   */
  void setConveyor(double percent);

  /** Zero both motors. */
  void stop();
}
