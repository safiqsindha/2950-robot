package frc.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;

/**
 * Hardware abstraction interface for the Climber subsystem — 2590 IO pattern.
 *
 * <p><b>Scaffold status:</b> the physical climber is not installed on the 2026 robot. This
 * interface + its {@link ClimberIOSim} ship so the subsystem can be tested + exercised in sim ahead
 * of hardware arrival. When the real climber is wired, implement {@code ClimberIOReal} against
 * SPARK MAX + the vertical motor on CAN ID 11 (see CAN_ID_REFERENCE.md) and flip the wiring in
 * {@code RobotContainer}.
 *
 * <p>Motor layout (planned):
 *
 * <ul>
 *   <li>One NEO brushless motor on SPARK MAX, CAN ID 11, position PID
 * </ul>
 */
public interface ClimberIO {

  /** Logged sensor readings refreshed every 20 ms via {@link #updateInputs}. */
  @AutoLog
  class ClimberIOInputs {
    /** True when the climber SPARK MAX is reachable on the CAN bus. */
    public boolean connected = false;

    /** Current climber position (encoder rotations). */
    public double positionRotations = 0.0;

    /** Current climber velocity (rotations / sec). */
    public double velocityRotationsPerSec = 0.0;

    /** Output current drawn by the climber motor (amps). */
    public double currentAmps = 0.0;

    /** Voltage applied to the climber motor (V). */
    public double appliedVoltage = 0.0;
  }

  /** Refresh all sensor readings into {@code inputs}. Must be called every 20 ms in periodic(). */
  void updateInputs(ClimberIOInputs inputs);

  /**
   * Set the target climber position using on-board closed-loop PID.
   *
   * @param target target position in encoder rotations
   */
  void setTargetPosition(double target);

  /**
   * Set the climber motor to a percent output, bypassing closed-loop control.
   *
   * @param percent output (-1 to 1)
   */
  void setPercentOutput(double percent);

  /** Zero the climber motor and cancel any closed-loop setpoint. */
  void stop();

  /**
   * Push updated PID gains to the hardware controller. Default is a no-op (sim uses a software
   * controller instead).
   */
  default void setPid(double kP, double kI, double kD) {}
}
