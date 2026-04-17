package frc.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;

/**
 * Hardware abstraction interface for the SideClaw subsystem — 2590 IO pattern.
 *
 * <p><b>Scaffold status:</b> physical SideClaw needs its SPARK MAX reflashed from CAN ID 18 to
 * CAN ID 20 before first use (conflict with Conveyor spindexer). See CAN_ID_REFERENCE.md for
 * the procedure. Until the reflash happens, wire {@code SideClawIOSim} into RobotContainer.
 *
 * <p>Motor layout:
 *
 * <ul>
 *   <li>One NEO brushless motor on SPARK MAX, CAN ID 20, open-loop percent output
 * </ul>
 */
public interface SideClawIO {

  /** Logged sensor readings refreshed every 20 ms via {@link #updateInputs}. */
  @AutoLog
  class SideClawIOInputs {
    public boolean connected = false;

    /** Current drawn by the claw motor (A). Spike indicates object grabbed. */
    public double currentAmps = 0.0;

    /** Voltage applied to the claw motor (V). */
    public double appliedVoltage = 0.0;
  }

  /** Refresh all sensor readings. */
  void updateInputs(SideClawIOInputs inputs);

  /**
   * Set the claw motor to a percent output.
   *
   * @param percent output (-1 to 1)
   */
  void setPercentOutput(double percent);

  /** Zero the claw motor. */
  void stop();
}
