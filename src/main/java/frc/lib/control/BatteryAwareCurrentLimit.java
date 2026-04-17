package frc.lib.control;

/**
 * Computes a per-motor output scale factor that respects both a per-motor current budget AND the
 * overall battery voltage. Adapted from Team 971's "CapU" concept: when the battery is at rest
 * voltage we're generous, when it's sagging we tighten each motor's ceiling so no one motor pulls
 * the whole robot into brownout.
 *
 * <p>Pure math — HAL-free, testable, no WPILib time dependency. Call {@link #compute} each tick
 * with the latest battery voltage and the motor's most recent current draw; apply the returned
 * scale to your commanded output.
 *
 * <p>The two-axis model:
 *
 * <pre>
 *   voltageScale   = clamp((V - brownoutFloor) / (healthy - brownoutFloor), 0.5, 1.0)
 *   currentHeadroom = clamp((currentLimit - measuredCurrent) / currentLimit, 0, 1)
 *   output         = min(voltageScale, currentHeadroom)
 * </pre>
 *
 * <p>At rest voltage (>= 12 V) with zero current, you get 1.0 — no derating. At 7 V with a motor
 * already at 80% of its current limit, you get {@code min(0.5, 0.2) = 0.2} — sharp clamp. The
 * min-of-two keeps whichever constraint is tighter; typically voltage wins during brownout, current
 * wins when a single motor is being over-driven.
 */
public final class BatteryAwareCurrentLimit {

  private final double brownoutFloorVolts;
  private final double healthyVolts;
  private final double currentLimitAmps;

  /**
   * @param brownoutFloorVolts voltage at which scale clamps to 0.5 (typically 6.0 V)
   * @param healthyVolts voltage at which no derating applies (typically 8.0 V)
   * @param currentLimitAmps per-motor current ceiling; output scales inversely as we approach it
   */
  public BatteryAwareCurrentLimit(
      double brownoutFloorVolts, double healthyVolts, double currentLimitAmps) {
    if (healthyVolts <= brownoutFloorVolts) {
      throw new IllegalArgumentException("healthyVolts must be greater than brownoutFloorVolts");
    }
    if (currentLimitAmps <= 0) {
      throw new IllegalArgumentException("currentLimitAmps must be > 0");
    }
    this.brownoutFloorVolts = brownoutFloorVolts;
    this.healthyVolts = healthyVolts;
    this.currentLimitAmps = currentLimitAmps;
  }

  /**
   * @return a scale factor in {@code [0.0, 1.0]} to apply to the motor's commanded output.
   */
  public double compute(double batteryVoltage, double measuredCurrentAmps) {
    double voltageScale = voltageScale(batteryVoltage);
    double headroom = currentHeadroom(measuredCurrentAmps);
    return Math.min(voltageScale, headroom);
  }

  private double voltageScale(double volts) {
    if (volts >= healthyVolts) {
      return 1.0;
    }
    if (volts <= brownoutFloorVolts) {
      return 0.5;
    }
    return 0.5 + 0.5 * (volts - brownoutFloorVolts) / (healthyVolts - brownoutFloorVolts);
  }

  private double currentHeadroom(double amps) {
    double remaining = (currentLimitAmps - Math.max(0, amps)) / currentLimitAmps;
    if (remaining <= 0) {
      return 0.0;
    }
    if (remaining >= 1) {
      return 1.0;
    }
    return remaining;
  }

  public double brownoutFloorVolts() {
    return brownoutFloorVolts;
  }

  public double healthyVolts() {
    return healthyVolts;
  }

  public double currentLimitAmps() {
    return currentLimitAmps;
  }
}
