package frc.robot.subsystems;

/**
 * Simulation implementation of {@link IntakeIO}.
 *
 * <p>No {@link edu.wpi.first.wpilibj.simulation.DCMotorSim} is used — the arm physics are not
 * critical to verify in simulation (arm position is not checked in any sim-based test). The wheel
 * current is synthesised from the commanded wheel output and the {@code simGamePieceAcquired} flag,
 * matching the behaviour previously in {@link Intake#simulationPeriodic()}.
 *
 * <p>This implementation is entirely HAL-free: {@link #updateInputs} performs only pure Java
 * arithmetic, making it safe to call from unit tests without WPILib native initialisation.
 *
 * <p>Arm position tracking: both arm positions report the last commanded {@code targetAngle} (a
 * simplified model — no PID lag). This is sufficient for telemetry and for any future test that
 * checks arm state.
 */
public class IntakeIOSim implements IntakeIO {

  /**
   * Simulated wheel current multiplier (A / fraction of full output). Matches the constant
   * previously embedded in {@link Intake#simulationPeriodic()}.
   */
  private static final double kSimCurrentAmpsAtFullOutput = 30.0;

  private double wheelPercent = 0.0;
  private double targetAngle = 0.0;

  /**
   * Gates current synthesis. When {@code true} a current spike proportional to wheel output is
   * synthesised, allowing the superstructure state machine to advance INTAKING → STAGING.
   */
  private boolean simGamePieceAcquired = false;

  @Override
  public void updateInputs(IntakeIOInputs inputs) {
    inputs.connected = true;
    // Arm positions track the commanded setpoint (no PID lag in sim).
    inputs.leftArmPositionRotations = targetAngle;
    inputs.rightArmPositionRotations = targetAngle;
    // Synthesise wheel current only when a game piece has been explicitly injected.
    inputs.wheelCurrentAmps =
        simGamePieceAcquired ? Math.abs(wheelPercent) * kSimCurrentAmpsAtFullOutput : 0.0;
    inputs.wheelAppliedVoltage = wheelPercent * 12.0;
  }

  @Override
  public void setWheel(double percent) {
    wheelPercent = percent;
  }

  @Override
  public void updateTargetAngle(double target) {
    targetAngle = target;
  }

  @Override
  public void resetEncoder() {
    targetAngle = 0.0;
  }

  @Override
  public void simulateGamePieceAcquired() {
    simGamePieceAcquired = true;
  }

  @Override
  public void simulateGamePieceConsumed() {
    simGamePieceAcquired = false;
  }
}
