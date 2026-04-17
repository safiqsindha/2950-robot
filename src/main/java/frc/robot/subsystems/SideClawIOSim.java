package frc.robot.subsystems;

import edu.wpi.first.math.MathUtil;

/**
 * Pure-Java sim for {@link SideClawIO}. Synthesizes current proportional to the commanded output —
 * matches what we'd see on hardware when the claw is running free. No "object grabbed" simulation
 * (that's a game-piece-physics concern, handled by maple-sim elsewhere).
 */
public class SideClawIOSim implements SideClawIO {

  private static final double kSimCurrentAtFullOutput = 15.0;

  private double percent = 0.0;

  @Override
  public void updateInputs(SideClawIOInputs inputs) {
    double appliedVolts = MathUtil.clamp(percent * 12.0, -12.0, 12.0);
    inputs.connected = true;
    inputs.appliedVoltage = appliedVolts;
    inputs.currentAmps = Math.abs(percent) * kSimCurrentAtFullOutput;
  }

  @Override
  public void setPercentOutput(double p) {
    percent = p;
  }

  @Override
  public void stop() {
    percent = 0.0;
  }
}
