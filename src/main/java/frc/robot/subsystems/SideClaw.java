package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.control.AsymmetricRateLimiter;
import org.littletonrobotics.junction.Logger;

/**
 * SideClaw subsystem — thin consumer of {@link SideClawIO} (2590 pattern).
 *
 * <p><b>Scaffold status:</b> wired on sim only. Real hardware needs the SPARK MAX reflashed from
 * CAN ID 18 → 20 (see CAN_ID_REFERENCE.md) before {@code SideClawIOReal} can be added and this
 * class pointed at it.
 *
 * <p>The claw uses the same {@link AsymmetricRateLimiter} safety-stop pattern as the Intake
 * wheel: ramps up to avoid a current spike on startup, snaps to zero when the command ends to
 * avoid dragging an object after a panic interrupt.
 */
public final class SideClaw extends SubsystemBase {

  /** Claw ramp rate — full output in 0.2 s, snap-down instant. */
  private static final double kMaxPercentPerSec = 5.0;

  private final SideClawIO io;
  private final SideClawIOInputsAutoLogged inputs = new SideClawIOInputsAutoLogged();

  private double goalPercent = 0.0;
  private final AsymmetricRateLimiter limiter = new AsymmetricRateLimiter(kMaxPercentPerSec, 0.02);

  public SideClaw(SideClawIO io) {
    this.io = io;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("SideClaw", inputs);
    double setpoint = limiter.calculate(goalPercent);
    io.setPercentOutput(setpoint);
    Logger.recordOutput("SideClaw/GoalPercent", goalPercent);
    Logger.recordOutput("SideClaw/SetpointPercent", setpoint);
  }

  /** Command a claw percent output. Snapping to 0 is instant; ramping up is rate-limited. */
  public void setPercent(double percent) {
    goalPercent = percent;
  }

  /** Stop the claw. */
  public void stop() {
    goalPercent = 0.0;
    io.stop();
    limiter.reset(0.0);
  }

  /** @return current drawn by the claw motor (A). */
  public double getCurrentAmps() {
    return inputs.currentAmps;
  }
}
