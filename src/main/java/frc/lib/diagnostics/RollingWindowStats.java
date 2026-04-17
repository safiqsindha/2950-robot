package frc.lib.diagnostics;

/**
 * Fixed-size rolling-window statistic bundle. Tracks min / max / mean / p95 over the most recent
 * {@code size} samples. Pure Java; no allocation on the happy path after construction.
 *
 * <p>Used by the telemetry add-ons (vision latency, loop time, whatever) that want to expose a
 * smoothed summary of a noisy signal without pulling in a full stats library.
 */
public final class RollingWindowStats {

  private final double[] samples;
  private int index = 0;
  private int filled = 0;

  public RollingWindowStats(int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("size must be > 0");
    }
    this.samples = new double[size];
  }

  /** Add a new sample, overwriting the oldest once the window is full. */
  public void add(double value) {
    samples[index] = value;
    index = (index + 1) % samples.length;
    if (filled < samples.length) {
      filled++;
    }
  }

  public int count() {
    return filled;
  }

  public double max() {
    if (filled == 0) return 0.0;
    double m = samples[0];
    for (int i = 1; i < filled; i++) {
      if (samples[i] > m) m = samples[i];
    }
    return m;
  }

  public double min() {
    if (filled == 0) return 0.0;
    double m = samples[0];
    for (int i = 1; i < filled; i++) {
      if (samples[i] < m) m = samples[i];
    }
    return m;
  }

  public double mean() {
    if (filled == 0) return 0.0;
    double sum = 0.0;
    for (int i = 0; i < filled; i++) {
      sum += samples[i];
    }
    return sum / filled;
  }

  /**
   * @return the 95th percentile across the current window. Allocates a sorted copy per call, so
   *     call sparingly (once per 20 ms is fine). Returns 0 if no samples.
   */
  public double p95() {
    if (filled == 0) return 0.0;
    double[] sorted = new double[filled];
    System.arraycopy(samples, 0, sorted, 0, filled);
    java.util.Arrays.sort(sorted);
    int idx = (int) Math.round(0.95 * (filled - 1));
    return sorted[idx];
  }

  /** Reset the window — all subsequent reads return 0 until new samples arrive. */
  public void reset() {
    index = 0;
    filled = 0;
    java.util.Arrays.fill(samples, 0.0);
  }
}
