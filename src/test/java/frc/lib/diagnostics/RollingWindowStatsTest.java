package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RollingWindowStatsTest {

  @Test
  void constructor_zeroSize_throws() {
    assertThrows(IllegalArgumentException.class, () -> new RollingWindowStats(0));
  }

  @Test
  void emptyWindow_returnsZeroForAllStats() {
    RollingWindowStats s = new RollingWindowStats(10);
    assertEquals(0, s.count());
    assertEquals(0.0, s.min(), 1e-9);
    assertEquals(0.0, s.max(), 1e-9);
    assertEquals(0.0, s.mean(), 1e-9);
    assertEquals(0.0, s.p95(), 1e-9);
  }

  @Test
  void add_partialFill_tracksOnlySeenSamples() {
    RollingWindowStats s = new RollingWindowStats(10);
    s.add(5.0);
    s.add(15.0);
    s.add(10.0);
    assertEquals(3, s.count());
    assertEquals(5.0, s.min(), 1e-9);
    assertEquals(15.0, s.max(), 1e-9);
    assertEquals(10.0, s.mean(), 1e-9);
  }

  @Test
  void add_overflow_windowRolls() {
    RollingWindowStats s = new RollingWindowStats(3);
    s.add(1.0);
    s.add(2.0);
    s.add(3.0);
    // Window full — next add evicts 1.0.
    s.add(4.0);
    assertEquals(3, s.count());
    assertEquals(2.0, s.min(), 1e-9);
    assertEquals(4.0, s.max(), 1e-9);
    assertEquals(3.0, s.mean(), 1e-9);
  }

  @Test
  void p95_withSimpleDistribution() {
    RollingWindowStats s = new RollingWindowStats(100);
    for (int i = 1; i <= 100; i++) {
      s.add(i);
    }
    // 95th of 1..100 sorted is value at index 94 (0-based) = 95.
    assertEquals(95.0, s.p95(), 1e-9);
  }

  @Test
  void reset_clearsWindow() {
    RollingWindowStats s = new RollingWindowStats(5);
    s.add(10);
    s.add(20);
    s.reset();
    assertEquals(0, s.count());
    assertEquals(0.0, s.max(), 1e-9);
  }

  @Test
  void mean_doesNotDivideByZeroOnEmpty() {
    RollingWindowStats s = new RollingWindowStats(5);
    assertDoesNotThrow(s::mean);
  }
}
