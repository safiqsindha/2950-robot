package frc.lib.control;

/**
 * Acceleration-limited setpoint slew — adapted from Team 6328 Mechanical Advantage's {@code
 * LinearProfile}. Useful for flywheel / intake wheel velocity setpoints where a hard step-change
 * would cause current spikes, brownout, or motor stall.
 *
 * <p>Conceptually:
 *
 * <pre>
 *   lastSetpoint = clamp(goal,
 *                        lastSetpoint − maxAccel · dt,
 *                        lastSetpoint + maxAccel · dt)
 * </pre>
 *
 * where {@code dt} is the time since the last {@link #calculate} call (measured internally, so
 * the caller doesn't have to thread it through).
 *
 * <p>Usage (flywheel):
 *
 * <pre>{@code
 * // 5000 RPM/s ramp — 3500 RPM reachable in 0.7 s
 * private final LinearProfile flywheelProfile = new LinearProfile(5000, 0.02);
 *
 * public void setTargetRpm(double goalRpm) {
 *   double ramped = flywheelProfile.calculate(goalRpm);
 *   io.setTargetRpm(ramped);
 * }
 * }</pre>
 *
 * <p>This class is pure Java — no WPILib dependency beyond standard math. HAL-free and testable
 * without a robot.
 */
public final class LinearProfile {

  private final double defaultDtSeconds;
  private double maxAccel;
  private double lastValue;

  /**
   * @param maxAccel max rate of change of the output per second (same units as the setpoint)
   * @param defaultDtSeconds the period assumed when {@link #calculate(double)} is called without
   *     an explicit timestamp — usually 0.02 (robot loop period)
   */
  public LinearProfile(double maxAccel, double defaultDtSeconds) {
    if (maxAccel < 0) {
      throw new IllegalArgumentException("maxAccel must be >= 0");
    }
    if (defaultDtSeconds <= 0) {
      throw new IllegalArgumentException("defaultDtSeconds must be > 0");
    }
    this.maxAccel = maxAccel;
    this.defaultDtSeconds = defaultDtSeconds;
  }

  /**
   * Advance the profile using the default period. Assumes one robot-loop dt has passed since the
   * last call. Use {@link #calculateWithDt} if the actual dt is known.
   */
  public double calculate(double goal) {
    return calculateWithDt(goal, defaultDtSeconds);
  }

  /**
   * Advance the profile using an explicit dt. Returns the new ramped setpoint. Clamps the rate
   * of change of the internal "last value" to ±(maxAccel · dt).
   */
  public double calculateWithDt(double goal, double dt) {
    if (dt < 0) {
      throw new IllegalArgumentException("dt must be >= 0");
    }
    double maxDelta = maxAccel * dt;
    double delta = goal - lastValue;
    if (delta > maxDelta) {
      delta = maxDelta;
    } else if (delta < -maxDelta) {
      delta = -maxDelta;
    }
    lastValue += delta;
    return lastValue;
  }

  /**
   * Reset the profile to a known value. Call when the downstream controller is also reset (e.g.
   * when the flywheel is disabled, you probably want the profile to start from 0 next spin-up).
   */
  public void reset(double value) {
    lastValue = value;
  }

  /** @return the last ramped value (setpoint emitted to the consumer) */
  public double getLastValue() {
    return lastValue;
  }

  /** Change the max acceleration on the fly — useful when tuning with a LoggedTunableNumber. */
  public void setMaxAccel(double maxAccel) {
    if (maxAccel < 0) {
      throw new IllegalArgumentException("maxAccel must be >= 0");
    }
    this.maxAccel = maxAccel;
  }

  /** @return the current max-acceleration setting */
  public double getMaxAccel() {
    return maxAccel;
  }
}
