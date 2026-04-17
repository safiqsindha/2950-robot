package frc.lib.diagnostics;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import org.littletonrobotics.junction.Logger;

/**
 * Publishes JVM memory and garbage-collection metrics to AdvantageKit. Makes GC pauses visible in
 * AdvantageScope — if loop-overrun events correlate with GC spikes (common on the roboRIO), you
 * want this.
 *
 * <p>Adapted from Team 3005 RoboChargers' {@code LoggedJVM}. Zero hardware dependency; pure {@code
 * java.lang.management} API access.
 *
 * <p>Usage:
 *
 * <pre>
 *   private final JvmLogger jvmLogger = new JvmLogger();
 *
 *   public void robotPeriodic() {
 *     jvmLogger.periodic();
 *     ...
 *   }
 * </pre>
 *
 * <p>Log keys:
 *
 * <ul>
 *   <li>{@code JVM/HeapUsedMB} — current heap usage (megabytes)
 *   <li>{@code JVM/HeapMaxMB} — maximum heap size (megabytes)
 *   <li>{@code JVM/NonHeapUsedMB} — non-heap usage: metaspace + code cache + etc
 *   <li>{@code JVM/GCTotalCount} — cumulative collection count across all collectors
 *   <li>{@code JVM/GCTotalTimeMs} — cumulative collection time across all collectors
 * </ul>
 */
public final class JvmLogger {

  private static final double BYTES_PER_MB = 1024.0 * 1024.0;

  private final MemoryMXBean memoryBean;
  private final List<GarbageCollectorMXBean> gcBeans;

  /** Creates a JvmLogger. Caches the management beans once; they're safe to reuse. */
  public JvmLogger() {
    this.memoryBean = ManagementFactory.getMemoryMXBean();
    this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
  }

  /**
   * Collects current JVM metrics. Package-private for unit testing — pure Java API, no {@code
   * Logger} side-effect, safe to call from JUnit.
   *
   * @return the current metrics snapshot
   */
  JvmMetrics collectMetrics() {
    long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
    long heapMax = memoryBean.getHeapMemoryUsage().getMax();
    long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
    long totalGcCount = 0;
    long totalGcTime = 0;
    for (GarbageCollectorMXBean bean : gcBeans) {
      long count = bean.getCollectionCount();
      long time = bean.getCollectionTime();
      // MX beans return -1 when unsupported; skip rather than pollute the total.
      if (count > 0) {
        totalGcCount += count;
      }
      if (time > 0) {
        totalGcTime += time;
      }
    }
    return new JvmMetrics(
        bytesToMegabytes(heapUsed),
        bytesToMegabytes(heapMax),
        bytesToMegabytes(nonHeapUsed),
        totalGcCount,
        totalGcTime);
  }

  /** Called from {@code Robot.robotPeriodic()} — cheap enough to run every 20 ms loop. */
  public void periodic() {
    JvmMetrics m = collectMetrics();
    Logger.recordOutput("JVM/HeapUsedMB", m.heapUsedMb());
    Logger.recordOutput("JVM/HeapMaxMB", m.heapMaxMb());
    Logger.recordOutput("JVM/NonHeapUsedMB", m.nonHeapUsedMb());
    Logger.recordOutput("JVM/GCTotalCount", m.gcTotalCount());
    Logger.recordOutput("JVM/GCTotalTimeMs", m.gcTotalTimeMs());
  }

  /** Converts bytes to megabytes. Package-private for testing. */
  static double bytesToMegabytes(long bytes) {
    return bytes / BYTES_PER_MB;
  }

  /** Snapshot of JVM metrics. Package-private for unit testing. */
  record JvmMetrics(
      double heapUsedMb,
      double heapMaxMb,
      double nonHeapUsedMb,
      long gcTotalCount,
      long gcTotalTimeMs) {}
}
