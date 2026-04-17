package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

/**
 * HAL-free tests for {@link LoopTimeLogger}. A mutable {@link DoubleSupplier} lets tests drive
 * the clock precisely so we can assert tick math, rolling max, and overrun counting.
 */
class LoopTimeLoggerTest {

  /** Tiny mutable clock — tests bump it forward in milliseconds for readability. */
  private static final class FakeClock implements DoubleSupplier {
    double nowSeconds = 0.0;

    @Override
    public double getAsDouble() {
      return nowSeconds;
    }

    void advanceMs(double ms) {
      nowSeconds += ms / 1000.0;
    }
  }

  @Test
  void firstCall_reportsZeroTickMs() {
    FakeClock clock = new FakeClock();
    LoopTimeLogger logger = new LoopTimeLogger(clock, 25.0);
    LoopTimeLogger.Snapshot s = logger.collect();
    assertEquals(0.0, s.tickMs(), 1e-9, "First tick has no baseline — report zero, not seconds-since-boot");
  }

  @Test
  void tickMs_reflectsClockDelta() {
    FakeClock clock = new FakeClock();
    LoopTimeLogger logger = new LoopTimeLogger(clock, 25.0);
    logger.collect();
    clock.advanceMs(20.0);
    assertEquals(20.0, logger.collect().tickMs(), 1e-9);
    clock.advanceMs(15.5);
    assertEquals(15.5, logger.collect().tickMs(), 1e-9);
  }

  @Test
  void overrunCount_incrementsOnlyOnLongTicks() {
    FakeClock clock = new FakeClock();
    LoopTimeLogger logger = new LoopTimeLogger(clock, 25.0);
    logger.collect(); // baseline

    clock.advanceMs(20.0);
    assertEquals(0, logger.collect().overrunCount(), "20ms < threshold; no overrun");

    clock.advanceMs(30.0);
    assertEquals(1, logger.collect().overrunCount(), "30ms > threshold; overrun count +1");

    clock.advanceMs(20.0);
    assertEquals(1, logger.collect().overrunCount(), "Back under threshold; count unchanged");

    clock.advanceMs(40.0);
    assertEquals(2, logger.collect().overrunCount(), "Another overrun; count now 2");
  }

  @Test
  void overrunActive_tracksLastTickOnly() {
    FakeClock clock = new FakeClock();
    LoopTimeLogger logger = new LoopTimeLogger(clock, 25.0);
    logger.collect();

    clock.advanceMs(30.0);
    assertTrue(logger.collect().overrunActive());

    clock.advanceMs(20.0);
    assertFalse(logger.collect().overrunActive(), "Back under threshold → overrunActive resets");
  }

  @Test
  void maxTickMs_tracksRollingMax() {
    FakeClock clock = new FakeClock();
    LoopTimeLogger logger = new LoopTimeLogger(clock, 25.0);
    logger.collect();

    clock.advanceMs(20.0);
    assertEquals(20.0, logger.collect().maxTickMs(), 1e-9);
    clock.advanceMs(50.0);
    assertEquals(50.0, logger.collect().maxTickMs(), 1e-9);
    clock.advanceMs(20.0);
    // Max holds through shorter ticks until the 50ms tick rolls off the window.
    assertEquals(50.0, logger.collect().maxTickMs(), 1e-9);
  }

  @Test
  void maxTickMs_rollsOffAfterWindow() {
    FakeClock clock = new FakeClock();
    LoopTimeLogger logger = new LoopTimeLogger(clock, 25.0);
    logger.collect();

    // One big spike
    clock.advanceMs(100.0);
    logger.collect();
    // Fill the window with small ticks; the 100ms tick should roll off after ROLLING_MAX_WINDOW
    // entries.
    for (int i = 0; i < LoopTimeLogger.ROLLING_MAX_WINDOW; i++) {
      clock.advanceMs(10.0);
      logger.collect();
    }
    double maxAfter = logger.collect().maxTickMs();
    assertTrue(maxAfter < 100.0, "Spike should have rolled off after window fills; got " + maxAfter);
  }

  @Test
  void periodic_doesNotThrow() {
    LoopTimeLogger logger = new LoopTimeLogger(new FakeClock(), 25.0);
    assertDoesNotThrow(logger::periodic);
  }

  @Test
  void constructor_nonPositiveThreshold_throws() {
    assertThrows(IllegalArgumentException.class, () -> new LoopTimeLogger(new FakeClock(), 0.0));
    assertThrows(IllegalArgumentException.class, () -> new LoopTimeLogger(new FakeClock(), -5.0));
  }

  @Test
  void defaultConstructor_doesNotThrow() {
    assertDoesNotThrow(() -> new LoopTimeLogger());
  }
}
