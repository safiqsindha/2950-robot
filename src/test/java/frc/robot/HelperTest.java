package frc.robot;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.math.kinematics.ChassisSpeeds;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Helper#rpmFromMeters(double)} (Lagrange quadratic) and {@link
 * Helper#rpmFromMeters(double, ChassisSpeeds)} (moving-shot overload). No HAL or hardware
 * dependency.
 */
class HelperTest {

  // Calibration points — must match Helper.rpmFromMeters() constants
  private static final double X1 = 1.125, Y1 = 2500.0;
  private static final double X2 = 1.714, Y2 = 3000.0;
  private static final double X3 = 2.500, Y3 = 3500.0;

  // ── Calibration-point accuracy (Lagrange exactly hits each point) ─────────

  @Test
  void testRpmFromMeters_atX1_returnsY1() {
    assertEquals(Y1, Helper.rpmFromMeters(X1), 1.0);
  }

  @Test
  void testRpmFromMeters_atX2_returnsY2() {
    assertEquals(Y2, Helper.rpmFromMeters(X2), 1.0);
  }

  @Test
  void testRpmFromMeters_atX3_returnsY3() {
    assertEquals(Y3, Helper.rpmFromMeters(X3), 1.0);
  }

  // ── Smooth quadratic curve (replaces piecewise-linear) ───────────────────
  // For the concave-down parabola (negative leading coefficient), the curve lies
  // ABOVE the linear chord between any two calibration points.

  @Test
  void testRpmFromMeters_midpointX1X2_isAboveLinearMidpoint() {
    // Piecewise-linear midpoint: (Y1 + Y2) / 2 = 2750
    // Lagrange quadratic midpoint: ≈ 2763 (concave-down curve above the chord)
    double mid = (X1 + X2) / 2.0; // 1.4195 m
    double rpm = Helper.rpmFromMeters(mid);
    double linearMid = (Y1 + Y2) / 2.0; // 2750.0 RPM
    assertTrue(
        rpm > linearMid,
        "Quadratic midpoint should be above linear chord (concave-down curve): rpm=" + rpm);
    assertEquals(2763.0, rpm, 5.0, "Quadratic value at mid(X1,X2) should be ≈ 2763 RPM");
  }

  @Test
  void testRpmFromMeters_midpointX2X3_isAboveLinearMidpoint() {
    // Piecewise-linear midpoint: (Y2 + Y3) / 2 = 3250
    // Lagrange quadratic midpoint: ≈ 3274 (concave-down curve above the chord)
    double mid = (X2 + X3) / 2.0; // 2.107 m
    double rpm = Helper.rpmFromMeters(mid);
    double linearMid = (Y2 + Y3) / 2.0; // 3250.0 RPM
    assertTrue(
        rpm > linearMid,
        "Quadratic midpoint should be above linear chord (concave-down curve): rpm=" + rpm);
    assertEquals(3274.0, rpm, 5.0, "Quadratic value at mid(X2,X3) should be ≈ 3274 RPM");
  }

  @Test
  void testRpmFromMeters_isContinuousAtX2_noBoundaryKink() {
    // The piecewise-linear version had a slope change at X2; the quadratic must be smooth there.
    // Verify left and right derivatives are approximately equal by finite differences.
    double h = 1e-4;
    double slopeLeft = (Helper.rpmFromMeters(X2) - Helper.rpmFromMeters(X2 - h)) / h;
    double slopeRight = (Helper.rpmFromMeters(X2 + h) - Helper.rpmFromMeters(X2)) / h;
    // For the quadratic the slopes match at X2; allow 1 RPM/m tolerance for finite-diff rounding.
    assertEquals(
        slopeLeft, slopeRight, 1.0, "Slope must be continuous at the X2 calibration point");
  }

  // ── Clamping ──────────────────────────────────────────────────────────────

  @Test
  void testRpmFromMeters_belowMinDistance_clampedToMinRpm() {
    assertEquals(Constants.Flywheel.kMinRpm, Helper.rpmFromMeters(0.0), 1.0);
  }

  @Test
  void testRpmFromMeters_farAboveX3_clampedToMaxRpm() {
    assertEquals(Constants.Flywheel.kMaxRpm, Helper.rpmFromMeters(100.0), 1.0);
  }

  @Test
  void testRpmFromMeters_outputNeverExceedsMaxRpm() {
    for (int cm = 0; cm <= 1000; cm += 10) {
      double d = cm / 100.0;
      double rpm = Helper.rpmFromMeters(d);
      assertTrue(rpm <= Constants.Flywheel.kMaxRpm, "RPM exceeded max at distance " + d);
    }
  }

  @Test
  void testRpmFromMeters_outputNeverBelowMinRpm() {
    for (int cm = 0; cm <= 1000; cm += 10) {
      double d = cm / 100.0;
      double rpm = Helper.rpmFromMeters(d);
      assertTrue(rpm >= Constants.Flywheel.kMinRpm, "RPM below min at distance " + d);
    }
  }

  // ── Moving-shot overload (Phase 3.3) ─────────────────────────────────────

  @Test
  void rpmFromMeters_stationaryRobot_matchesBaseOverload() {
    double dist = 2.0;
    assertEquals(
        Helper.rpmFromMeters(dist),
        Helper.rpmFromMeters(dist, new ChassisSpeeds(0, 0, 0)),
        1e-6,
        "Stationary robot: overload must equal base rpmFromMeters");
  }

  @Test
  void rpmFromMeters_movingAwayFromTarget_requiresMoreRpm() {
    // vx > 0: robot moving away from target — effective distance increases — more RPM needed
    double dist = 2.0;
    double rpmStationary = Helper.rpmFromMeters(dist);
    double rpmMovingAway = Helper.rpmFromMeters(dist, new ChassisSpeeds(1.0, 0, 0));
    assertTrue(
        rpmMovingAway > rpmStationary,
        "Moving away (vx=+1) should require more RPM: stationary="
            + rpmStationary
            + " movingAway="
            + rpmMovingAway);
  }

  @Test
  void rpmFromMeters_movingTowardTarget_requiresLessRpm() {
    // vx < 0: robot moving toward target — effective distance decreases — less RPM needed
    double dist = 2.0;
    double rpmStationary = Helper.rpmFromMeters(dist);
    double rpmMovingToward = Helper.rpmFromMeters(dist, new ChassisSpeeds(-1.0, 0, 0));
    assertTrue(
        rpmMovingToward < rpmStationary,
        "Moving toward (vx=-1) should require less RPM: stationary="
            + rpmStationary
            + " movingToward="
            + rpmMovingToward);
  }

  @Test
  void rpmFromMeters_lateralVelocityOnly_doesNotAffectRpm() {
    // Lateral motion (vy only) does not change effective shot distance; RPM is unchanged.
    double dist = 2.0;
    assertEquals(
        Helper.rpmFromMeters(dist),
        Helper.rpmFromMeters(dist, new ChassisSpeeds(0, 2.0, 0)),
        1e-6,
        "Lateral-only motion must not change RPM setpoint");
  }

  @Test
  void rpmFromMeters_movingAwayCorrection_isProportionalToSpeed() {
    // Effective distance = dist * (1 + vx/vBall); doubling vx doubles the correction magnitude.
    double dist = 2.0;
    double vBall = Constants.Flywheel.kBallExitVelocityMps;
    double expectedEffective1 = dist * (1.0 + 1.0 / vBall);
    double expectedEffective2 = dist * (1.0 + 2.0 / vBall);
    double rpm1 = Helper.rpmFromMeters(dist, new ChassisSpeeds(1.0, 0, 0));
    double rpm2 = Helper.rpmFromMeters(dist, new ChassisSpeeds(2.0, 0, 0));
    // Both effective distances should produce RPMs consistent with the base function
    assertEquals(Helper.rpmFromMeters(expectedEffective1), rpm1, 1e-6);
    assertEquals(Helper.rpmFromMeters(expectedEffective2), rpm2, 1e-6);
    assertTrue(rpm2 > rpm1, "Higher speed-away should produce higher RPM");
  }

  @Test
  void rpmFromMeters_movingShotOverload_neverExceedsBounds() {
    // Even with extreme robot speeds the output stays within [kMinRpm, kMaxRpm]
    double dist = 2.0;
    for (int v = -20; v <= 20; v++) {
      double rpm = Helper.rpmFromMeters(dist, new ChassisSpeeds(v, 0, 0));
      assertTrue(rpm >= Constants.Flywheel.kMinRpm, "RPM below min at vx=" + v);
      assertTrue(rpm <= Constants.Flywheel.kMaxRpm, "RPM above max at vx=" + v);
    }
  }
}
