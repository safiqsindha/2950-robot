package frc.lib.util;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.Timer;
import java.util.function.DoubleSupplier;

/**
 * "At goal" debouncer that resets when the commanded target changes. Adapted from Team 1619
 * Up-A-Creek's {@code AreWeThereYetDebouncer}.
 *
 * <p>Tracks a commanded target + tolerance. {@link #isAtTarget(double)} only returns {@code
 * true} after the position has been within tolerance of the commanded target for the debounce
 * window — eliminating false-positive "at goal" transitions when passing through the target on
 * the way to elsewhere.
 *
 * <p>Calling {@link #setTarget(double)} with a NEW goal resets the window, so the caller is
 * forced to re-earn the "at target" flag for the new destination. Re-asserting the same target
 * is a no-op.
 *
 * <p>Uses an injectable {@link DoubleSupplier} for time, so unit tests can avoid loading HAL
 * (WPILib's {@code Debouncer} pulls in {@code MathSharedStore.getTimestamp()} transitively, which
 * requires HAL init).
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

  private final double tolerance;
  private final double debounceSeconds;
  private final DoubleSupplier timeSource;

  private double commandedTarget = Double.NaN;
  /** Time at which the input first became within-tolerance for the current streak. NaN = not yet. */
  private double withinToleranceSinceSeconds = Double.NaN;

  /**
   * @param tolerance absolute units — current must be within ±tolerance of target
   * @param debounceSeconds how long the |error| must stay &lt; tolerance before {@link
   *     #isAtTarget} returns true
   */
  public AreWeThereYetDebouncer(double tolerance, double debounceSeconds) {
    this(tolerance, debounceSeconds, Timer::getFPGATimestamp);
  }

  /** Package-private ctor with injectable time source — unit tests use this. */
  AreWeThereYetDebouncer(double tolerance, double debounceSeconds, DoubleSupplier timeSource) {
    if (tolerance <= 0) {
      throw new IllegalArgumentException("tolerance must be > 0");
    }
    if (debounceSeconds < 0) {
      throw new IllegalArgumentException("debounceSeconds must be >= 0");
    }
    this.tolerance = tolerance;
    this.debounceSeconds = debounceSeconds;
    this.timeSource = timeSource;
  }

  /**
   * Set the commanded target. If the target CHANGES, resets the debounce window — the caller
   * must re-earn {@link #isAtTarget} for this new goal.
   */
  public void setTarget(double target) {
    if (target != commandedTarget) {
      commandedTarget = target;
      withinToleranceSinceSeconds = Double.NaN;
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
    if (!withinTolerance) {
      withinToleranceSinceSeconds = Double.NaN;
      return false;
    }
    double now = timeSource.getAsDouble();
    if (Double.isNaN(withinToleranceSinceSeconds)) {
      withinToleranceSinceSeconds = now;
    }
    return (now - withinToleranceSinceSeconds) >= debounceSeconds;
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
