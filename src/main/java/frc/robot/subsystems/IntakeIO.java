package frc.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;

/**
 * Hardware abstraction interface for the Intake subsystem (2590 IO pattern).
 *
 * <p>Concrete implementations:
 *
 * <ul>
 *   <li>{@link IntakeIOReal} — SPARK MAX (left arm, right arm, wheel) via REVLib
 *   <li>{@link IntakeIOSim} — pure-Java simulation (no DCMotorSim; current synthesised from wheel
 *       output and {@code simGamePieceAcquired} flag)
 * </ul>
 */
public interface IntakeIO {

  /** Logged sensor readings refreshed every 20 ms via {@link #updateInputs}. */
  @AutoLog
  class IntakeIOInputs {
    /** True when the primary arm SPARK MAX is reachable on the CAN bus. */
    public boolean connected = false;

    /** Left arm encoder position in rotations. */
    public double leftArmPositionRotations = 0.0;

    /** Right arm encoder position in rotations. */
    public double rightArmPositionRotations = 0.0;

    /** Output current drawn by the intake wheel motor (A). */
    public double wheelCurrentAmps = 0.0;

    /** Voltage applied to the intake wheel motor (V). */
    public double wheelAppliedVoltage = 0.0;
  }

  /** Refresh all sensor readings into {@code inputs}. Must be called every 20 ms in periodic(). */
  void updateInputs(IntakeIOInputs inputs);

  /**
   * Set intake wheel percent output.
   *
   * @param percent output (-1 to 1)
   */
  void setWheel(double percent);

  /**
   * Set the target arm position for both arms using onboard closed-loop position PID.
   *
   * @param target target position in encoder rotations
   */
  void updateTargetAngle(double target);

  /** Reset both arm encoders to zero. Call at the start of autonomous and teleop. */
  void resetEncoder();

  /**
   * Push updated position PID gains to both arm controllers. Default is a no-op (e.g. simulation).
   *
   * @param kP proportional gain
   * @param kD derivative gain
   */
  default void setPid(double kP, double kD) {}

  /**
   * Inject a simulated game piece into the intake. Enables current synthesis in {@link IntakeIOSim}
   * so the superstructure state machine can advance INTAKING → STAGING in simulation. Default is a
   * no-op on real hardware.
   */
  default void simulateGamePieceAcquired() {}

  /**
   * Remove the simulated game piece (e.g. after staging or scoring). Clears the current synthesis
   * gate so the next intake cycle starts clean. Default is a no-op on real hardware.
   */
  default void simulateGamePieceConsumed() {}
}
