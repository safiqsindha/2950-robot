package frc.lib.control;

/**
 * Rate limiter that rate-limits <i>only</i> toward higher magnitudes — asymmetric by design.
 * Going from 0 → 1.0 ramps at {@code maxAccel}; going from 1.0 → 0 snaps instantly.
 *
 * <p>Why this exists: {@link LinearProfile} is the symmetric version and is correct for flywheel
 * velocity setpoints (where a smooth ramp-down is fine and actually desirable — the flywheel
 * physically can't snap to zero anyway). Open-loop mechanisms like the intake wheel and conveyor
 * are different: a smooth ramp from 0 → 1.0 cuts current spikes, but when a panic button or
 * command interruption fires {@code setPercent(0)} we want the motor to stop <i>immediately</i>.
 * A symmetric ramp-down here would leave the motor running during the emergency-stop window.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>{@code calculate(goal)} when {@code |goal| > |lastValue|} ramps toward {@code goal} at
 *       {@code maxAccel} per {@code dt}.
 *   <li>{@code calculate(goal)} when {@code |goal| <= |lastValue|} snaps to {@code goal}
 *       immediately.
 *   <li>Zero-crossing (sign change) snaps to the new sign's zero first, then ramps up from there.
 *       This prevents a 1.0 → -1.0 command from taking 2× the ramp time through zero.
 * </ul>
 *
 * <p>Pure Java; HAL-free; testable without WPILib Timer.
 */
public final class AsymmetricRateLimiter {

  private final double defaultDtSeconds;
  private double maxAccel;
  private double lastValue;

  /**
   * @param maxAccel maximum rate of change in the "harder" direction (same units/sec as the
   *     setpoint)
   * @param defaultDtSeconds period assumed by the no-arg {@link #calculate} — typically 0.02
   */
  public AsymmetricRateLimiter(double maxAccel, double defaultDtSeconds) {
    if (maxAccel < 0) {
      throw new IllegalArgumentException("maxAccel must be >= 0");
    }
    if (defaultDtSeconds <= 0) {
      throw new IllegalArgumentException("defaultDtSeconds must be > 0");
    }
    this.maxAccel = maxAccel;
    this.defaultDtSeconds = defaultDtSeconds;
  }

  /** Advance the limiter using the default period. */
  public double calculate(double goal) {
    return calculateWithDt(goal, defaultDtSeconds);
  }

  /**
   * Advance with an explicit dt. Zero-crossing (sign change) snaps through zero first — a command
   * from +0.8 to -0.8 will emit 0 on this call, then ramp up in the negative direction on
   * subsequent calls. This keeps the "snap to zero for safety" behaviour symmetric across sign.
   */
  public double calculateWithDt(double goal, double dt) {
    if (dt < 0) {
      throw new IllegalArgumentException("dt must be >= 0");
    }
    // Sign flip: pass through zero in one step rather than ramping down one side and up the other.
    if (Math.signum(goal) * Math.signum(lastValue) < 0) {
      lastValue = 0.0;
    }
    double absLast = Math.abs(lastValue);
    double absGoal = Math.abs(goal);
    if (absGoal <= absLast) {
      // Moving toward zero (or staying put): snap.
      lastValue = goal;
      return lastValue;
    }
    // Moving toward higher magnitude: ramp.
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

  /** Reset the internal state to a known value. */
  public void reset(double value) {
    lastValue = value;
  }

  /** @return the last emitted value */
  public double getLastValue() {
    return lastValue;
  }

  /** Change max-accel on the fly (useful with a {@code LoggedTunableNumber}). */
  public void setMaxAccel(double maxAccel) {
    if (maxAccel < 0) {
      throw new IllegalArgumentException("maxAccel must be >= 0");
    }
    this.maxAccel = maxAccel;
  }

  public double getMaxAccel() {
    return maxAccel;
  }
}
