package frc.robot.simulation;

import static org.junit.jupiter.api.Assertions.*;

import frc.robot.Constants;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ShotSimulation}. Only the pure-logic helpers are exercised — {@code fire()}
 * and {@code periodic()} both call into {@code SimulatedArena.getInstance()} and {@code
 * Logger.recordOutput}, neither of which are safe in JUnit without HAL + a running arena.
 */
class ShotSimulationTest {

  @Test
  void rpmToExitSpeedMps_zeroIsZero() {
    assertEquals(0.0, ShotSimulation.rpmToExitSpeedMps(0), 1e-9);
  }

  @Test
  void rpmToExitSpeedMps_maxRpmMapsToConfiguredExitSpeed() {
    assertEquals(
        Constants.Flywheel.kBallExitVelocityMps,
        ShotSimulation.rpmToExitSpeedMps(Constants.Flywheel.kMaxRpm),
        1e-9);
  }

  @Test
  void rpmToExitSpeedMps_halfMaxIsHalfSpeed() {
    assertEquals(
        Constants.Flywheel.kBallExitVelocityMps / 2.0,
        ShotSimulation.rpmToExitSpeedMps(Constants.Flywheel.kMaxRpm / 2.0),
        1e-9);
  }

  @Test
  void rpmToExitSpeedMps_negativeClampsToZero() {
    // Guards against physically meaningless negative RPM requests.
    assertEquals(0.0, ShotSimulation.rpmToExitSpeedMps(-100), 1e-9);
  }

  @Test
  void newInstance_countersAreZero() {
    ShotSimulation sim = new ShotSimulation();
    assertEquals(0, sim.shotsFired());
    assertEquals(0, sim.shotsScored());
  }

  @Test
  void onHit_incrementsScoredCounter() {
    // Exercise the callback path directly without needing a live arena.
    ShotSimulation sim = new ShotSimulation();
    sim.onHit(1);
    sim.onHit(2);
    assertEquals(2, sim.shotsScored());
    assertEquals(0, sim.shotsFired(), "shotsFired is NOT bumped by hit callback");
  }

  // ─── Rate-limit predicate (shouldFireNow) ────────────────────────────

  @Test
  void shouldFireNow_notAtSpeed_returnsFalse() {
    assertFalse(ShotSimulation.shouldFireNow(false, 10.0, 0.0));
  }

  @Test
  void shouldFireNow_atSpeedFirstCall_returnsTrue() {
    // lastFireTime = NEGATIVE_INFINITY means "never fired" — first call should fire.
    assertTrue(ShotSimulation.shouldFireNow(true, 0.0, Double.NEGATIVE_INFINITY));
  }

  @Test
  void shouldFireNow_atSpeedBeforeInterval_returnsFalse() {
    // Last fired at t=5.0, interval is 0.5, so t=5.3 is too soon.
    assertFalse(ShotSimulation.shouldFireNow(true, 5.3, 5.0));
  }

  @Test
  void shouldFireNow_atSpeedExactlyAtInterval_returnsTrue() {
    // t=5.5 is exactly one interval after last fire — boundary is inclusive (>=).
    assertTrue(ShotSimulation.shouldFireNow(true, 5.0 + ShotSimulation.kShotIntervalSeconds, 5.0));
  }

  @Test
  void shouldFireNow_atSpeedLongAfter_returnsTrue() {
    assertTrue(ShotSimulation.shouldFireNow(true, 100.0, 5.0));
  }
}
