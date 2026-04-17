package frc.lib.util;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.filter.Debouncer;
import edu.wpi.first.math.filter.Debouncer.DebounceType;

/**
 * "At goal" debouncer that resets when the commanded target changes. Adapted from Team 1619
 * Up-A-Creek's {@code AreWeThereYetDebouncer}.
 *
 * <p>Wraps a {@link Debouncer} + commanded target + tolerance. {@link #isAtTarget(double)} only
 * returns {@code true} after the position has been within tolerance of the commanded target for
 * the debounce window — eliminating false-positive "at goal" transitions when passing through
 * the target on the way to elsewhere.
 *
 * <p>Calling {@link #setTarget(double)} with a new goal resets the debounce, so the caller is
 * forced to re-earn the "at target" flag for the new destination.
 *
 * <p>Example:
 *
 * <pre>{@code
 * private final AreWeThereYetDebouncer armAtGoal = new AreWeThereYetDebouncer(0.05, 0.1);
 *
 * public void setTargetAngle(double radians) {
 *   armAtGoal.setTarget(radians);
 *   armMotor.setSetpoint(radians);
 * }
 *
 * public boolean isAtGoal() {
 *   return armAtGoal.isAtTarget(armEncoder.getPosition());
 * }
 * }</pre>
 */
public final class AreWeThereYetDebouncer {

  private final Debouncer debouncer;
  private final double tolerance;
  private double commandedTarget = Double.NaN;

  /**
   * @param tolerance absolute units — current must be within ±tolerance of target
   * @param debounceSeconds how long the |error| must stay &lt; tolerance before {@link
   *     #isAtTarget} returns true
   */
  public AreWeThereYetDebouncer(double tolerance, double debounceSeconds) {
    if (tolerance <= 0) {
      throw new IllegalArgumentException("tolerance must be > 0");
    }
    if (debounceSeconds < 0) {
      throw new IllegalArgumentException("debounceSeconds must be >= 0");
    }
    this.tolerance = tolerance;
    this.debouncer = new Debouncer(debounceSeconds, DebounceType.kRising);
  }

  /**
   * Set the commanded target. Resets the debounce — the caller must re-earn {@link #isAtTarget}
   * for this new goal.
   */
  public void setTarget(double target) {
    if (target != commandedTarget) {
      commandedTarget = target;
      // Force the debouncer back to false by feeding a long run of false.
      debouncer.calculate(false);
    }
  }

  /**
   * @param currentPosition latest sensor reading
   * @return {@code true} once {@code |current − target|} has been &lt; tolerance for the full
   *     debounce window AND a target has been set. {@code false} before any target is set.
   */
  public boolean isAtTarget(double currentPosition) {
    if (Double.isNaN(commandedTarget)) {
      return false;
    }
    boolean withinTolerance = MathUtil.isNear(commandedTarget, currentPosition, tolerance);
    return debouncer.calculate(withinTolerance);
  }

  /** @return the currently commanded target (NaN if none set yet) */
  public double getCommandedTarget() {
    return commandedTarget;
  }

  /** @return true if a target has been set */
  public boolean hasTarget() {
    return !Double.isNaN(commandedTarget);
  }
}
