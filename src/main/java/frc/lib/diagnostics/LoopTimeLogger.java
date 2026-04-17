package frc.lib.diagnostics;

import java.util.function.DoubleSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * Publishes loop-iteration timing to AdvantageKit. Pairs with {@link JvmLogger}, {@link
 * CanBusLogger}, and {@link PdhLogger} to answer "where did that 40 ms spike come from?" — usually
 * you can correlate a loop overrun with a GC pause, a CAN saturation burst, or a sudden PDH current
 * spike.
 *
 * <p>Tracks:
 *
 * <ul>
 *   <li>Single-tick latency (last {@link #periodic()} call → this one)
 *   <li>Rolling max over the last {@link #ROLLING_MAX_WINDOW} ticks, so a 30-ms spike doesn't get
 *       smoothed away by normal 20-ms ticks
 *   <li>Overrun count — ticks that exceeded the configured threshold (default 25 ms, 5 ms above
 *       nominal)
 * </ul>
 *
 * <p>Design mirrors the other diagnostics — {@link DoubleSupplier} injection so tests never touch
 * HAL. Pure {@code java.lang.management}-style.
 *
 * <p>Log keys (all under {@code Loop/}):
 *
 * <ul>
 *   <li>{@code Loop/TickMs} — last tick duration
 *   <li>{@code Loop/MaxTickMs} — rolling max over the last N ticks
 *   <li>{@code Loop/OverrunCount} — cumulative count of ticks over the threshold
 *   <li>{@code Loop/OverrunActive} — boolean, true when the last tick was over threshold
 * </ul>
 */
public final class LoopTimeLogger {

  /** Default overrun threshold in milliseconds — 5 ms above a 20 ms tick. */
  public static final double DEFAULT_OVERRUN_THRESHOLD_MS = 25.0;

  /** How many recent ticks feed the rolling max. */
  public static final int ROLLING_MAX_WINDOW = 50;

  private final DoubleSupplier nowSecondsSupplier;
  private final double overrunThresholdMs;

  private double lastTickTimestampSeconds = Double.NaN;
  private long overrunCount = 0;
  private final double[] rolling = new double[ROLLING_MAX_WINDOW];
  private int rollingIndex = 0;
  private int rollingSize = 0;

  /**
   * Production ctor — uses WPILib's {@code Timer.getFPGATimestamp()} with the default threshold.
   */
  public LoopTimeLogger() {
    this(edu.wpi.first.wpilibj.Timer::getFPGATimestamp, DEFAULT_OVERRUN_THRESHOLD_MS);
  }

  /**
   * Test ctor — inject a clock supplier + threshold. Cheap enough that production code can use this
   * form too (e.g. for a "strict" 22 ms threshold during competition).
   *
   * @param nowSecondsSupplier clock — typically {@code Timer::getFPGATimestamp}
   * @param overrunThresholdMs ticks longer than this log an overrun
   */
  public LoopTimeLogger(DoubleSupplier nowSecondsSupplier, double overrunThresholdMs) {
    if (overrunThresholdMs <= 0) {
      throw new IllegalArgumentException("overrunThresholdMs must be > 0");
    }
    this.nowSecondsSupplier = nowSecondsSupplier;
    this.overrunThresholdMs = overrunThresholdMs;
  }

  /**
   * Sample one tick. Intended to be called once per {@code robotPeriodic()} at a stable cadence.
   */
  public void periodic() {
    Snapshot s = collect();
    Logger.recordOutput("Loop/TickMs", s.tickMs());
    Logger.recordOutput("Loop/MaxTickMs", s.maxTickMs());
    Logger.recordOutput("Loop/OverrunCount", s.overrunCount());
    Logger.recordOutput("Loop/OverrunActive", s.overrunActive());
  }

  /**
   * Package-private — returns the current snapshot. Useful for unit tests that want to assert the
   * threshold / rolling-max arithmetic without a Logger sink.
   */
  Snapshot collect() {
    double now = nowSecondsSupplier.getAsDouble();
    double tickMs;
    if (Double.isNaN(lastTickTimestampSeconds)) {
      // First call — nothing to diff against. Report zero so the graph doesn't see a spike
      // that's really "seconds since robot boot."
      tickMs = 0.0;
    } else {
      tickMs = (now - lastTickTimestampSeconds) * 1000.0;
    }
    lastTickTimestampSeconds = now;

    rolling[rollingIndex] = tickMs;
    rollingIndex = (rollingIndex + 1) % rolling.length;
    if (rollingSize < rolling.length) {
      rollingSize++;
    }

    double maxMs = 0.0;
    for (int i = 0; i < rollingSize; i++) {
      maxMs = Math.max(maxMs, rolling[i]);
    }

    boolean overrunActive = tickMs > overrunThresholdMs;
    if (overrunActive) {
      overrunCount++;
    }

    return new Snapshot(tickMs, maxMs, overrunCount, overrunActive);
  }

  /** Snapshot of one loop sample. Package-private for tests. */
  record Snapshot(double tickMs, double maxTickMs, long overrunCount, boolean overrunActive) {}
}
