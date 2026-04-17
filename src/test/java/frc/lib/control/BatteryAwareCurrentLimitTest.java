package frc.lib.control;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BatteryAwareCurrentLimitTest {

  @Test
  void constructor_rejectsSwappedVoltages() {
    assertThrows(
        IllegalArgumentException.class, () -> new BatteryAwareCurrentLimit(8.0, 6.0, 40.0));
  }

  @Test
  void constructor_rejectsZeroCurrentLimit() {
    assertThrows(IllegalArgumentException.class, () -> new BatteryAwareCurrentLimit(6.0, 8.0, 0.0));
  }

  @Test
  void healthyVoltage_lowCurrent_mostlyFullScale() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    // At 5A / 40A the linear-headroom model gives (40-5)/40 = 0.875. Voltage at 12.5 V is
    // healthy (→ 1.0), so current wins: 0.875. The original test name suggested "returns 1.0"
    // but that's inconsistent with the headroom model the other tests assume.
    assertEquals(0.875, limit.compute(12.5, 5.0), 1e-9);
  }

  @Test
  void sagVoltage_lowCurrent_returnsHalfScale() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    // At 6.0 V exactly — voltage scale clamps to 0.5.
    assertEquals(0.5, limit.compute(6.0, 0.0), 1e-9);
  }

  @Test
  void intermediateVoltage_interpolatesLinearly() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    // At 7.0 V — halfway between 6 and 8 — expected 0.75.
    assertEquals(0.75, limit.compute(7.0, 0.0), 1e-9);
  }

  @Test
  void zeroCurrent_returnsVoltageScale() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    // At 10 V healthy + zero current → 1.0 (voltage wins).
    assertEquals(1.0, limit.compute(10.0, 0.0), 1e-9);
  }

  @Test
  void highCurrent_atHealthyVoltage_tightensOutput() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    // 30 A measured / 40 A limit → 25 % headroom. Voltage is healthy (1.0), current wins.
    assertEquals(0.25, limit.compute(12.5, 30.0), 1e-9);
  }

  @Test
  void currentAtLimit_returnsZeroScale() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(0.0, limit.compute(12.5, 40.0), 1e-9);
  }

  @Test
  void currentAboveLimit_clampsToZero() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(0.0, limit.compute(12.5, 55.0), 1e-9);
  }

  @Test
  void negativeCurrent_treatedAsZero() {
    // A regen / braking event could produce negative current. Headroom should still report
    // full (1.0) in that case — don't derate the motor when it's feeding the battery.
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(1.0, limit.compute(12.0, -5.0), 1e-9);
  }

  @Test
  void bothConstraintsActive_minWins() {
    // Low-ish voltage (7 V → scale 0.75) + moderate current (20 A of 40 → headroom 0.5).
    // Current is tighter → 0.5.
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(0.5, limit.compute(7.0, 20.0), 1e-9);
  }

  @Test
  void veryLowVoltage_clampsToHalf() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(0.5, limit.compute(4.0, 0.0), 1e-9);
  }

  @Test
  void accessors_roundTripParameters() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(6.0, limit.brownoutFloorVolts(), 1e-9);
    assertEquals(8.0, limit.healthyVolts(), 1e-9);
    assertEquals(40.0, limit.currentLimitAmps(), 1e-9);
  }
}
