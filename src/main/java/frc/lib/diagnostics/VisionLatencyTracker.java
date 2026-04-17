package frc.lib.diagnostics;

import org.littletonrobotics.junction.Logger;

/**
 * Tracks Limelight (or any vision source) per-frame latency over a rolling window. Useful for
 * spotting gradual degradation — the Limelight will silently fall behind if the coprocessor is
 * thermally throttling, the network is saturated, or the pipeline is misconfigured.
 *
 * <p>{@link VisionSubsystem} (or any caller that publishes a pose) calls {@link #record(double)}
 * with the frame's latency in milliseconds after a successful sample. The tracker maintains a
 * rolling 200-sample window (≈ 4 s at 50 Hz Limelight update) and publishes min / max / mean / p95
 * every {@link #periodic}.
 *
 * <p>Log keys (all under {@code VisionLatency/}):
 *
 * <ul>
 *   <li>{@code VisionLatency/LastMs} — most recent frame's latency
 *   <li>{@code VisionLatency/MinMs}, {@code MaxMs}, {@code MeanMs}, {@code P95Ms}
 *   <li>{@code VisionLatency/SampleCount} — window fill level (0 until we have 200)
 * </ul>
 */
public final class VisionLatencyTracker {

  public static final int DEFAULT_WINDOW_SIZE = 200;

  private final RollingWindowStats stats;
  private double lastMs = 0.0;

  public VisionLatencyTracker() {
    this(DEFAULT_WINDOW_SIZE);
  }

  public VisionLatencyTracker(int windowSize) {
    this.stats = new RollingWindowStats(windowSize);
  }

  /** Record one frame's total latency (pipeline + capture). Call on every accepted frame. */
  public void record(double latencyMs) {
    lastMs = latencyMs;
    stats.add(latencyMs);
  }

  /** Publish the current window stats. Safe to call every robot cycle. */
  public void periodic() {
    Logger.recordOutput("VisionLatency/LastMs", lastMs);
    Logger.recordOutput("VisionLatency/MinMs", stats.min());
    Logger.recordOutput("VisionLatency/MaxMs", stats.max());
    Logger.recordOutput("VisionLatency/MeanMs", stats.mean());
    Logger.recordOutput("VisionLatency/P95Ms", stats.p95());
    Logger.recordOutput("VisionLatency/SampleCount", stats.count());
  }

  /** Reset the rolling window — useful after mode transitions so stale data doesn't pollute. */
  public void reset() {
    stats.reset();
    lastMs = 0.0;
  }

  /**
   * @return the rolling stats bundle. Package-private for tests.
   */
  RollingWindowStats stats() {
    return stats;
  }
}
