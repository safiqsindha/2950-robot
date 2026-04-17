package frc.robot.subsystems;

import edu.wpi.first.math.MathUtil;

/**
 * Pure-Java sim for {@link ClimberIO}. No DCMotorSim dependency — the climber is position-
 * controlled and we model it as a first-order lag toward the target position.
 *
 * <p>HAL-free so unit tests can construct it directly (unlike {@code FlywheelIOSim}, which uses
 * {@code DCMotorSim} and therefore touches HAL via battery voltage readings).
 */
public class ClimberIOSim implements ClimberIO {

  /** Time constant of the first-order position response (seconds). */
  private static final double kSimTauSeconds = 0.1;

  /** 20 ms tick assumed for {@code updateInputs} calls. */
  private static final double kDtSecs = 0.02;

  /** Current limit during sim for the synthesized current reading (amps). */
  private static final double kSimCurrentAtFullOutput = 25.0;

  private double targetPosition = 0.0;
  private double currentPosition = 0.0;
  private double openLoopPercent = 0.0;
  private boolean openLoopOverride = false;

  @Override
  public void updateInputs(ClimberIOInputs inputs) {
    double appliedVolts;
    if (openLoopOverride) {
      appliedVolts = MathUtil.clamp(openLoopPercent * 12.0, -12.0, 12.0);
      currentPosition += openLoopPercent * kDtSecs; // rough percent → rotations integration
    } else {
      // First-order lag toward target.
      double alpha = kDtSecs / (kSimTauSeconds + kDtSecs);
      currentPosition = currentPosition + alpha * (targetPosition - currentPosition);
      // Applied voltage proportional to remaining error; useful for telemetry smoke.
      double error = targetPosition - currentPosition;
      appliedVolts = MathUtil.clamp(error * 4.0, -12.0, 12.0);
    }
    double velocity =
        openLoopOverride
            ? openLoopPercent / kDtSecs
            : (targetPosition - currentPosition) / kSimTauSeconds;

    inputs.connected = true;
    inputs.positionRotations = currentPosition;
    inputs.velocityRotationsPerSec = velocity;
    inputs.currentAmps = Math.abs(appliedVolts / 12.0) * kSimCurrentAtFullOutput;
    inputs.appliedVoltage = appliedVolts;
  }

  @Override
  public void setTargetPosition(double target) {
    targetPosition = target;
    openLoopOverride = false;
  }

  @Override
  public void setPercentOutput(double percent) {
    openLoopPercent = percent;
    openLoopOverride = true;
  }

  @Override
  public void stop() {
    openLoopOverride = false;
    openLoopPercent = 0.0;
    targetPosition = currentPosition;
  }
}
