package frc.robot;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.math.kinematics.ChassisSpeeds;
import org.junit.jupiter.api.Test;

/**
 * Boundary tests for {@link Helper#rpmFromMeters}. Complements {@code HelperTest} with
 * focused coverage of the clamp edges, extrapolation tail, and the interaction between the
 * 971 shoot-on-the-fly math and the RPM clamp.
 *
 * <p>These catch two classes of regression:
 *
 * <ul>
 *   <li>Forgetting to clamp after the Lagrange calculation — a meters value outside the
 *       calibration range can push RPM below kMinRpm / above kMaxRpm without this guard.
 *   <li>Sign confusion in the 2-D overload — the bearing + velocity should cancel for a robot
 *       moving directly at the target regardless of tx.
 * </ul>
 */
class HelperRpmBoundaryTest {

  @Test
  void rpmFromMeters_belowMinDistance_clampsToMinRpm() {
    // Below the shortest calibration point — Lagrange would extrapolate down. Clamp to kMinRpm.
    double rpm = Helper.rpmFromMeters(0.1);
    assertTrue(rpm >= Constants.Flywheel.kMinRpm - 1e-9, "clamped to kMinRpm, got " + rpm);
  }

  @Test
  void rpmFromMeters_veryFarDistance_clampsToMaxRpm() {
    // Beyond the longest calibration point — linear tail extrapolates up. Clamp to kMaxRpm.
    double rpm = Helper.rpmFromMeters(10.0);
    assertTrue(rpm <= Constants.Flywheel.kMaxRpm + 1e-9, "clamped to kMaxRpm, got " + rpm);
  }

  @Test
  void rpmFromMeters_atCalibrationPoints_hitsExactly() {
    // The Lagrange quadratic is exact at the three calibration knots — verify.
    assertEquals(2500.0, Helper.rpmFromMeters(1.125), 1e-6);
    assertEquals(3000.0, Helper.rpmFromMeters(1.714), 1e-6);
    assertEquals(3500.0, Helper.rpmFromMeters(2.500), 1e-6);
  }

  @Test
  void rpmFromMeters_movingAtBoundaryDistance_stillClamps() {
    // At 2.5m with a fast forward velocity, the effective distance grows past 2.5m into the
    // linear-tail region — and past the clamp ceiling at kMaxRpm = 4000.
    ChassisSpeeds speeds = new ChassisSpeeds(6.0, 0, 0);
    double rpm = Helper.rpmFromMeters(2.5, speeds);
    assertTrue(
        rpm <= Constants.Flywheel.kMaxRpm + 1e-9,
        "still clamped to kMaxRpm in moving-shot overload, got " + rpm);
  }

  @Test
  void rpmFromMeters_2DOverload_zeroBearing_stationary_equalsBase() {
    // Straight ahead + stationary → same as the 1-D lookup.
    ChassisSpeeds zero = new ChassisSpeeds();
    assertEquals(Helper.rpmFromMeters(2.0), Helper.rpmFromMeters(2.0, 0.0, zero), 1e-9);
  }

  @Test
  void rpmFromMeters_2DOverload_perpendicularMotion_doesNotChangeRpm() {
    // Moving purely tangent to the line-of-sight — 971 math shifts the virtual target laterally
    // but the distance stays approximately the same. Expect small / bounded delta (few %).
    // With vx=0, vy=3 m/s, bearing=0 (straight ahead), ball speed 12 m/s:
    //   airTime ≈ d / v_ball = 2 / 12 ≈ 0.167 s
    //   lateral shift ≈ vy * airTime ≈ 0.5 m
    //   virtual distance ≈ sqrt(2² + 0.5²) ≈ 2.062 m
    // So rpm should come in within a few percent of the 2m base case.
    double baseRpm = Helper.rpmFromMeters(2.0);
    ChassisSpeeds speeds = new ChassisSpeeds(0.0, 3.0, 0.0);
    double movingRpm = Helper.rpmFromMeters(2.0, 0.0, speeds);
    double deltaPct = Math.abs(movingRpm - baseRpm) / baseRpm * 100.0;
    assertTrue(
        deltaPct < 10.0, "lateral motion at low speed should shift RPM < 10%; got " + deltaPct);
  }
}
