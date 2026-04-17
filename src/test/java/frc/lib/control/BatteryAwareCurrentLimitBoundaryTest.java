package frc.lib.control;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Boundary coverage for {@link BatteryAwareCurrentLimit}. Focuses on the exact-value edges that the
 * main test suite interpolates through.
 */
class BatteryAwareCurrentLimitBoundaryTest {

  @Test
  void exactlyHealthyVoltage_returnsFullScale() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(1.0, limit.compute(8.0, 0.0), 1e-9);
  }

  @Test
  void exactlyBrownoutFloor_returnsHalfScale() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(0.5, limit.compute(6.0, 0.0), 1e-9);
  }

  @Test
  void currentAtZero_returnsFullHeadroomIfVoltageIsHealthy() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    assertEquals(1.0, limit.compute(13.0, 0.0), 1e-9);
  }

  @Test
  void currentMuchAboveLimit_stillClampsToZero() {
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    // 10× the limit — still zero, not negative.
    assertEquals(0.0, limit.compute(13.0, 400.0), 1e-9);
  }

  @Test
  void tinyFractionAboveBrownoutFloor_interpolates() {
    // Just above floor — voltage scale should be slightly above 0.5.
    var limit = new BatteryAwareCurrentLimit(6.0, 8.0, 40.0);
    double s = limit.compute(6.1, 0.0);
    assertTrue(s > 0.5 && s < 0.55, "expected slight derating, got " + s);
  }

  @Test
  void constructor_equalVoltages_throws() {
    // Degenerate case — brownout == healthy would divide by zero.
    assertThrows(
        IllegalArgumentException.class, () -> new BatteryAwareCurrentLimit(7.0, 7.0, 40.0));
  }
}
