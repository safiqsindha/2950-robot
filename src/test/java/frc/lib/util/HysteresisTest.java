package frc.lib.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HysteresisTest {

  // ─── createAsLowerLimit ────────────────────────────────────────────────

  @Test
  void lowerLimit_startsFalse() {
    var h = Hysteresis.createAsLowerLimit(10.0, 1.0);
    assertFalse(h.lessThan());
    assertFalse(h.greaterThan());
  }

  @Test
  void lowerLimit_belowLowerThreshold_flipsTrue() {
    var h = Hysteresis.createAsLowerLimit(10.0, 1.0);
    h.update(8.0); // below 10-1=9
    assertTrue(h.lessThan());
  }

  @Test
  void lowerLimit_inBand_holdsPreviousState() {
    var h = Hysteresis.createAsLowerLimit(10.0, 1.0);
    h.update(8.0); // flip true
    assertTrue(h.lessThan());
    h.update(9.5); // in band [9, 11] — should hold true
    assertTrue(h.lessThan());
    h.update(10.5); // still in band
    assertTrue(h.lessThan());
  }

  @Test
  void lowerLimit_aboveUpperThreshold_flipsFalse() {
    var h = Hysteresis.createAsLowerLimit(10.0, 1.0);
    h.update(8.0); // true
    h.update(12.0); // above 10+1=11
    assertFalse(h.lessThan());
  }

  @Test
  void lowerLimit_ringingAtBoundary_staysStable() {
    // Classic test — rapid oscillation across the limit should NOT flip repeatedly.
    var h = Hysteresis.createAsLowerLimit(10.0, 1.0);
    h.update(9.99); // in band — no flip yet
    assertFalse(h.lessThan());
    h.update(10.01); // in band — still no flip
    assertFalse(h.lessThan());
    h.update(10.5); // in band — still no flip
    assertFalse(h.lessThan());
  }

  // ─── createAsUpperLimit ────────────────────────────────────────────────

  @Test
  void upperLimit_aboveUpperThreshold_flipsTrue() {
    var h = Hysteresis.createAsUpperLimit(10.0, 1.0);
    h.update(12.0);
    assertTrue(h.greaterThan());
  }

  @Test
  void upperLimit_belowLowerThreshold_flipsFalse() {
    var h = Hysteresis.createAsUpperLimit(10.0, 1.0);
    h.update(12.0); // true
    h.update(8.0); // below 9 — flips false
    assertFalse(h.greaterThan());
  }

  @Test
  void upperLimit_lessThanReturnsFalse() {
    // lessThan is only meaningful on lower-limit instances
    var h = Hysteresis.createAsUpperLimit(10.0, 1.0);
    h.update(5.0);
    assertFalse(h.lessThan());
  }

  // ─── Input validation ─────────────────────────────────────────────────

  @Test
  void createAsLowerLimit_zeroHysteresis_throws() {
    assertThrows(IllegalArgumentException.class, () -> Hysteresis.createAsLowerLimit(10, 0));
  }

  @Test
  void createAsUpperLimit_negativeHysteresis_throws() {
    assertThrows(IllegalArgumentException.class, () -> Hysteresis.createAsUpperLimit(10, -1));
  }

  @Test
  void forceState_changesImmediately() {
    var h = Hysteresis.createAsLowerLimit(10.0, 1.0);
    h.forceState(true);
    assertTrue(h.lessThan());
    h.forceState(false);
    assertFalse(h.lessThan());
  }
}
