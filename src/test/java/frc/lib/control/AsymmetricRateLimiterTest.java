package frc.lib.control;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AsymmetricRateLimiterTest {

  @Test
  void constructor_negativeMaxAccel_throws() {
    assertThrows(IllegalArgumentException.class, () -> new AsymmetricRateLimiter(-1, 0.02));
  }

  @Test
  void constructor_zeroDt_throws() {
    assertThrows(IllegalArgumentException.class, () -> new AsymmetricRateLimiter(10, 0));
  }

  @Test
  void calculate_rampingUp_limitsDelta() {
    // 5.0 / s × 0.02 s = 0.1 per tick
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    assertEquals(0.1, r.calculate(1.0), 1e-9);
    assertEquals(0.2, r.calculate(1.0), 1e-9);
  }

  @Test
  void calculate_rampingDown_snapsImmediately() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    r.reset(0.8);
    // Going to a smaller magnitude — snap.
    assertEquals(0.0, r.calculate(0.0), 1e-9);
  }

  @Test
  void calculate_stayingAtMagnitude_doesNotOvershoot() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    r.reset(0.3);
    // Staying at same magnitude — no change.
    assertEquals(0.3, r.calculate(0.3), 1e-9);
  }

  @Test
  void calculate_signFlip_resetsToZeroThenRampsInNewDirection() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    r.reset(0.5);
    // +0.5 → -0.5 is a sign flip. On the flipping tick the limiter resets to 0 AND takes one
    // max-delta step toward the new goal — so the first call emits -0.1 (0 → -0.1 at
    // maxDelta=0.1/tick), and subsequent calls continue ramping.
    assertEquals(-0.1, r.calculate(-0.5), 1e-9);
    assertEquals(-0.2, r.calculate(-0.5), 1e-9);
  }

  @Test
  void calculate_reachingGoalWithinOneStep_stopsExactly() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    r.reset(0.0);
    // Goal 0.05 within the 0.1 max delta — should arrive exactly.
    assertEquals(0.05, r.calculate(0.05), 1e-9);
  }

  @Test
  void calculate_negativeRampUp_alsoLimited() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    // Goal -1.0 from 0 — limit is 0.1/tick by magnitude.
    assertEquals(-0.1, r.calculate(-1.0), 1e-9);
    assertEquals(-0.2, r.calculate(-1.0), 1e-9);
  }

  @Test
  void calculate_negativeSnapDown_snaps() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    r.reset(-0.7);
    // -0.7 → -0.2 is moving toward zero — snap.
    assertEquals(-0.2, r.calculate(-0.2), 1e-9);
  }

  @Test
  void calculateWithDt_negativeDt_throws() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    assertThrows(IllegalArgumentException.class, () -> r.calculateWithDt(0.5, -0.01));
  }

  @Test
  void calculate_safetyPath_snapsMidRampDown() {
    // Real-world scenario: intake wheel at 0.6, panic-button fires setWheel(0) mid-ramp.
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(4.0, 0.02); // 0.08 per tick
    r.reset(0.6);
    // Panic-button: snap to zero on this tick, not after multiple ramp-down ticks.
    assertEquals(0.0, r.calculate(0.0), 1e-9);
    assertEquals(0.0, r.getLastValue(), 1e-9);
  }

  @Test
  void reset_restoresInternalState() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    r.calculate(0.8);
    r.reset(0.2);
    assertEquals(0.2, r.getLastValue(), 1e-9);
  }

  @Test
  void setMaxAccel_changesRate() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    r.setMaxAccel(10.0);
    // 10 × 0.02 = 0.2 per tick
    assertEquals(0.2, r.calculate(1.0), 1e-9);
  }

  @Test
  void setMaxAccel_negative_throws() {
    AsymmetricRateLimiter r = new AsymmetricRateLimiter(5.0, 0.02);
    assertThrows(IllegalArgumentException.class, () -> r.setMaxAccel(-1.0));
  }
}
