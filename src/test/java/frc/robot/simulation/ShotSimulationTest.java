package frc.robot.simulation;

import static org.junit.jupiter.api.Assertions.*;

import frc.robot.Constants;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ShotSimulation}. Only the pure-logic helpers are exercised —
 * {@code fire()} and {@code periodic()} both call into {@code SimulatedArena.getInstance()} and
 * {@code Logger.recordOutput}, neither of which are safe in JUnit without HAL + a running arena.
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
}
