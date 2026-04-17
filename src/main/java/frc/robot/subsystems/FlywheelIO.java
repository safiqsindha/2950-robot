package frc.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;

/**
 * Hardware abstraction interface for the Flywheel subsystem (2590 IO pattern).
 *
 * <p>Concrete implementations:
 *
 * <ul>
 *   <li>{@link FlywheelIOReal} — SPARK Flex (Vortex) + SPARK MAX (NEO) via REVLib
 *   <li>{@link FlywheelIOSim} — {@link edu.wpi.first.wpilibj.simulation.DCMotorSim} physics model
 * </ul>
 */
public interface FlywheelIO {

  /** Logged sensor readings refreshed every 20 ms via {@link #updateInputs}. */
  @AutoLog
  class FlywheelIOInputs {
    /** True when the primary Vortex motor is reachable on the CAN bus. */
    public boolean connected = false;

    /** Primary flywheel velocity in RPM (always non-negative). */
    public double velocityRpm = 0.0;

    /** Voltage applied to the primary Vortex motor (V). */
    public double appliedVoltage = 0.0;

    /** Supply current drawn by the primary Vortex motor (A). */
    public double supplyCurrentAmps = 0.0;

    /** Winding temperature of the primary Vortex motor (°C). */
    public double tempCelsius = 0.0;
  }

  /** Refresh all sensor readings into {@code inputs}. Must be called every 20 ms in periodic(). */
  void updateInputs(FlywheelIOInputs inputs);

  /**
   * Command the primary flywheel to a target velocity using closed-loop control.
   *
   * @param rpm target speed in RPM
   */
  void setTargetRpm(double rpm);

  /**
   * Set the primary Vortex motor to a percent output, bypassing closed-loop control. Used by {@link
   * frc.robot.commands.flywheel.FlywheelDynamic} for open-loop trigger driving.
   *
   * @param percent output (-1 to 1)
   */
  void setVortexOutput(double percent);

  /**
   * Set the lower feed wheel motors (front + back) to a percent output.
   *
   * @param percent output (-1 to 1)
   */
  void setLower(double percent);

  /** Zero all motors and cancel any active closed-loop setpoint. */
  void stop();

  /**
   * Push updated velocity PID gains to the hardware controller. Default is a no-op (e.g. simulation
   * uses a software controller instead).
   *
   * @param kP proportional gain
   * @param kI integral gain
   * @param kD derivative gain
   */
  default void setPid(double kP, double kI, double kD) {}
}
