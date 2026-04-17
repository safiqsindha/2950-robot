package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VisionLatencyTrackerTest {

  @Test
  void record_tracksLastAndStats() {
    VisionLatencyTracker t = new VisionLatencyTracker(10);
    t.record(20.0);
    t.record(25.0);
    t.record(30.0);
    assertEquals(3, t.stats().count());
    assertEquals(20.0, t.stats().min(), 1e-9);
    assertEquals(30.0, t.stats().max(), 1e-9);
    assertEquals(25.0, t.stats().mean(), 1e-9);
  }

  @Test
  void reset_clearsRollingWindow() {
    VisionLatencyTracker t = new VisionLatencyTracker(5);
    t.record(50.0);
    t.reset();
    assertEquals(0, t.stats().count());
    assertEquals(0.0, t.stats().max(), 1e-9);
  }

  @Test
  void periodic_doesNotThrow() {
    VisionLatencyTracker t = new VisionLatencyTracker(5);
    t.record(15.0);
    assertDoesNotThrow(t::periodic);
  }

  @Test
  void defaultConstructor_ok() {
    // Use an explicit lambda rather than ::new — the two-constructor class makes the
    // method-reference form ambiguous to Java's overload resolution for assertDoesNotThrow.
    assertDoesNotThrow(() -> new VisionLatencyTracker());
  }
}
