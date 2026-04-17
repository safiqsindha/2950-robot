package frc.lib.util;

/**
 * Symmetric schmitt-trigger hysteresis — eliminates boundary oscillation when a continuous value
 * crosses a threshold. Adapted from Team 3005 RoboChargers' {@code Hysteresis} utility.
 *
 * <p>Two factory methods:
 *
 * <ul>
 *   <li>{@link #createAsLowerLimit(double, double)} — returns {@code lessThan()=true} below the
 *       lower threshold ({@code limit - hysteresis}) and {@code lessThan()=false} above the upper
 *       threshold ({@code limit + hysteresis}). Stable in the band between.
 *   <li>{@link #createAsUpperLimit(double, double)} — returns {@code greaterThan()=true} above the
 *       upper threshold and {@code false} below the lower. Same stability guarantee.
 * </ul>
 *
 * <p>Intended use:
 *
 * <pre>{@code
 * private final Hysteresis elevatorSlowZone =
 *     Hysteresis.createAsLowerLimit(Meters.of(0.5).in(Meters), Meters.of(0.05).in(Meters));
 *
 * public void periodic() {
 *   elevatorSlowZone.update(elevator.getHeightMeters());
 *   if (elevatorSlowZone.lessThan()) {
 *     // in slow zone — reduce max output
 *   }
 * }
 * }</pre>
 */
public final class Hysteresis {

  private final double lowerThreshold;
  private final double upperThreshold;
  private final boolean invert;
  private boolean state;

  private Hysteresis(double lowerThreshold, double upperThreshold, boolean invert) {
    if (lowerThreshold >= upperThreshold) {
      throw new IllegalArgumentException(
          "lowerThreshold ("
              + lowerThreshold
              + ") must be < upperThreshold ("
              + upperThreshold
              + ")");
    }
    this.lowerThreshold = lowerThreshold;
    this.upperThreshold = upperThreshold;
    this.invert = invert;
  }

  /**
   * Creates a hysteresis where {@link #lessThan()} becomes {@code true} when the value drops below
   * {@code limit − hysteresis} and becomes {@code false} when the value rises above {@code limit +
   * hysteresis}.
   *
   * @param limit centerline of the hysteresis band
   * @param hysteresis half-width of the band (must be &gt; 0)
   */
  public static Hysteresis createAsLowerLimit(double limit, double hysteresis) {
    if (hysteresis <= 0) {
      throw new IllegalArgumentException("hysteresis must be > 0");
    }
    return new Hysteresis(limit - hysteresis, limit + hysteresis, false);
  }

  /**
   * Creates a hysteresis where {@link #greaterThan()} becomes {@code true} when the value rises
   * above {@code limit + hysteresis} and becomes {@code false} when the value drops below {@code
   * limit − hysteresis}.
   */
  public static Hysteresis createAsUpperLimit(double limit, double hysteresis) {
    if (hysteresis <= 0) {
      throw new IllegalArgumentException("hysteresis must be > 0");
    }
    return new Hysteresis(limit - hysteresis, limit + hysteresis, true);
  }

  /** Feeds a new sample. Updates internal state if the sample crosses the upper/lower threshold. */
  public void update(double value) {
    if (invert) {
      if (value > upperThreshold) {
        state = true;
      } else if (value < lowerThreshold) {
        state = false;
      }
    } else {
      if (value < lowerThreshold) {
        state = true;
      } else if (value > upperThreshold) {
        state = false;
      }
    }
  }

  /**
   * @return {@code true} if the tracked value has crossed below the lower threshold
   */
  public boolean lessThan() {
    return !invert && state;
  }

  /**
   * @return {@code true} if the tracked value has crossed above the upper threshold
   */
  public boolean greaterThan() {
    return invert && state;
  }

  /** Force the state — useful for tests or initial setup. Package-private for unit testing. */
  void forceState(boolean value) {
    this.state = value;
  }
}
