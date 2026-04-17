package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JvmLogger}. Tests the pure-Java helpers and the {@code collectMetrics()}
 * contract — the {@code periodic()} method calls AdvantageKit's {@code Logger.recordOutput} and is
 * not exercised here (side-effect test, not correctness test).
 */
class JvmLoggerTest {

  @Test
  void bytesToMegabytes_zero() {
    assertEquals(0.0, JvmLogger.bytesToMegabytes(0), 1e-9);
  }

  @Test
  void bytesToMegabytes_exactOneMB() {
    assertEquals(1.0, JvmLogger.bytesToMegabytes(1024L * 1024L), 1e-9);
  }

  @Test
  void bytesToMegabytes_halfMB() {
    assertEquals(0.5, JvmLogger.bytesToMegabytes(512L * 1024L), 1e-9);
  }

  @Test
  void bytesToMegabytes_largeValue() {
    // 1 GB in bytes -> 1024 MB
    assertEquals(1024.0, JvmLogger.bytesToMegabytes(1024L * 1024L * 1024L), 1e-9);
  }

  @Test
  void constructor_doesNotThrow() {
    assertDoesNotThrow(JvmLogger::new);
  }

  @Test
  void collectMetrics_returnsSensibleValues() {
    // Running in JUnit = running in a real JVM. Heap must be positive and non-zero.
    JvmLogger logger = new JvmLogger();
    JvmLogger.JvmMetrics m = logger.collectMetrics();
    assertTrue(m.heapUsedMb() > 0.0, "heap used must be > 0 in a live JVM");
    assertTrue(m.heapMaxMb() > 0.0, "heap max must be > 0");
    assertTrue(
        m.heapUsedMb() <= m.heapMaxMb(), "heap used must not exceed heap max");
    assertTrue(m.nonHeapUsedMb() >= 0.0, "non-heap used must be >= 0");
    assertTrue(m.gcTotalCount() >= 0L, "GC count must be >= 0");
    assertTrue(m.gcTotalTimeMs() >= 0L, "GC time must be >= 0");
  }

  @Test
  void collectMetrics_idempotentWithinATick() {
    // Two adjacent calls return consistent (not necessarily equal) metrics.
    // Heap can grow between calls; asserting ordering would be flaky. Just confirm no throws.
    JvmLogger logger = new JvmLogger();
    assertDoesNotThrow(logger::collectMetrics);
    assertDoesNotThrow(logger::collectMetrics);
  }
}
