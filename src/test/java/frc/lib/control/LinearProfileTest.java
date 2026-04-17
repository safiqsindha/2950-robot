package frc.lib.control;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LinearProfileTest {

  @Test
  void constructor_negativeMaxAccel_throws() {
    assertThrows(IllegalArgumentException.class, () -> new LinearProfile(-1, 0.02));
  }

  @Test
  void constructor_zeroDt_throws() {
    assertThrows(IllegalArgumentException.class, () -> new LinearProfile(100, 0));
  }

  @Test
  void constructor_negativeDt_throws() {
    assertThrows(IllegalArgumentException.class, () -> new LinearProfile(100, -0.02));
  }

  @Test
  void calculate_singleStep_clampsToMaxDelta() {
    var p = new LinearProfile(5000, 0.02); // 5000 RPM/s × 20 ms = 100 RPM per tick
    // Jump to 3500 from 0 — should only advance 100 on the first tick.
    assertEquals(100.0, p.calculate(3500.0), 1e-9);
  }

  @Test
  void calculate_multipleSteps_reachesGoal() {
    var p = new LinearProfile(5000, 0.02);
    // 3500 / 100 = 35 ticks to arrive.
    double last = 0;
    for (int i = 0; i < 35; i++) {
      last = p.calculate(3500.0);
    }
    assertEquals(3500.0, last, 1e-9);
  }

  @Test
  void calculate_decreasingGoal_rampsDown() {
    var p = new LinearProfile(5000, 0.02);
    p.reset(1000);
    // Goal is 0 — should drop by 100 per tick.
    assertEquals(900.0, p.calculate(0.0), 1e-9);
    assertEquals(800.0, p.calculate(0.0), 1e-9);
  }

  @Test
  void calculate_goalWithinDelta_reachesExactly() {
    var p = new LinearProfile(5000, 0.02);
    p.reset(1000);
    // Goal 1050, delta = 50 < max delta of 100 — should reach exactly.
    assertEquals(1050.0, p.calculate(1050.0), 1e-9);
  }

  @Test
  void calculateWithDt_smallerDt_smallerDelta() {
    var p = new LinearProfile(5000, 0.02);
    // Half the default dt → half the delta
    assertEquals(50.0, p.calculateWithDt(3500, 0.01), 1e-9);
  }

  @Test
  void calculateWithDt_zeroDt_noAdvance() {
    var p = new LinearProfile(5000, 0.02);
    p.reset(500);
    assertEquals(500.0, p.calculateWithDt(3500, 0.0), 1e-9);
  }

  @Test
  void calculateWithDt_negative_throws() {
    var p = new LinearProfile(5000, 0.02);
    assertThrows(IllegalArgumentException.class, () -> p.calculateWithDt(0, -0.01));
  }

  @Test
  void reset_resetsLastValue() {
    var p = new LinearProfile(5000, 0.02);
    p.calculate(3500);
    assertEquals(100.0, p.getLastValue(), 1e-9);
    p.reset(2500);
    assertEquals(2500.0, p.getLastValue(), 1e-9);
  }

  @Test
  void setMaxAccel_updatesRateLimit() {
    var p = new LinearProfile(5000, 0.02);
    p.setMaxAccel(100); // drops 50× — new max delta per tick is 2
    assertEquals(2.0, p.calculate(1000), 1e-9);
    assertEquals(100.0, p.getMaxAccel(), 1e-9);
  }

  @Test
  void setMaxAccel_negative_throws() {
    var p = new LinearProfile(5000, 0.02);
    assertThrows(IllegalArgumentException.class, () -> p.setMaxAccel(-1));
  }
}
