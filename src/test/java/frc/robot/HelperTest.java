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

  @Test
  void testRpmFromMeters_knownC1DiscontinuityAtX3_stayedBelowBudget() {
    // DOCUMENTATION TEST (session audit, PR #8 + #23): Lagrange-to-linear transition at X3 has
    // an accepted slope discontinuity. Audit-derived budget:
    //   - Quadratic slope at X3 ≈ 514.6 RPM/m
    //   - Linear-tail slope   ≈ 636.1 RPM/m  (= (Y3-Y2)/(X3-X2))
    //   - Jump ≈ 121.5 RPM/m ≈ 24 % relative
    //
    // If this assertion starts failing it means someone tightened the curve (good — maybe swap
    // the linear tail for a C1-matching slope) or loosened it (bad — investigate). Either way,
    // the test forces a conscious choice.
    double h = 1e-4;
    double slopeLeft = (Helper.rpmFromMeters(X3) - Helper.rpmFromMeters(X3 - h)) / h;
    double slopeRight = (Helper.rpmFromMeters(X3 + h) - Helper.rpmFromMeters(X3)) / h;
    double jump = slopeRight - slopeLeft;
    // The jump is positive (linear tail is steeper) and should stay in a 30 % relative band.
    assertTrue(jump > 100.0 && jump < 150.0,
        "C1 slope jump at X3 must stay near 121 RPM/m; observed " + jump);
    double relative = jump / slopeLeft;
    assertTrue(
        relative > 0.15 && relative < 0.30,
        "Relative slope jump at X3 must stay ≈ 24 %; observed " + (relative * 100) + " %");
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

  // ═════════════════════════════════════════════════════════════════════════
  // 971-style 3-iteration fixed-point moving shot — rpmFromMeters(d, θ, V)
  // ═════════════════════════════════════════════════════════════════════════

  @Test
  void fixedPoint_stationaryRobot_matchesBaseOverload() {
    double dist = 2.0;
    assertEquals(
        Helper.rpmFromMeters(dist),
        Helper.rpmFromMeters(dist, 0.0, new ChassisSpeeds(0, 0, 0)),
        1e-9,
        "Stationary robot: 3-arg overload must equal base rpmFromMeters");
  }

  @Test
  void fixedPoint_stationaryRobot_bearingDoesNotMatter() {
    // With zero velocity, the iteration has no work to do regardless of bearing.
    double dist = 2.0;
    double a = Helper.rpmFromMeters(dist, 0.0, new ChassisSpeeds(0, 0, 0));
    double b = Helper.rpmFromMeters(dist, Math.PI / 4, new ChassisSpeeds(0, 0, 0));
    double c = Helper.rpmFromMeters(dist, Math.PI / 2, new ChassisSpeeds(0, 0, 0));
    assertEquals(a, b, 1e-9);
    assertEquals(a, c, 1e-9);
  }

  @Test
  void fixedPoint_movingTowardTarget_requiresLessRpm() {
    // Bearing 0 = target straight ahead; vx > 0 = moving toward it.
    double dist = 2.0;
    double stationary = Helper.rpmFromMeters(dist);
    double moving = Helper.rpmFromMeters(dist, 0.0, new ChassisSpeeds(1.0, 0, 0));
    assertTrue(
        moving < stationary,
        "Moving TOWARD target should reduce RPM; stationary=" + stationary + ", moving=" + moving);
  }

  @Test
  void fixedPoint_movingAwayFromTarget_requiresMoreRpm() {
    double dist = 2.0;
    double stationary = Helper.rpmFromMeters(dist);
    // Target straight ahead, moving BACKWARD (negative vx = away from target)
    double moving = Helper.rpmFromMeters(dist, 0.0, new ChassisSpeeds(-1.0, 0, 0));
    assertTrue(moving > stationary, "Moving AWAY from target should raise RPM");
  }

  @Test
  void fixedPoint_lateralStrafe_addsSmallCorrection() {
    // Pure lateral velocity: virtual target drifts perpendicular, total distance
    // grows via Pythagorean effect. RPM should be slightly higher than stationary.
    double dist = 2.0;
    double stationary = Helper.rpmFromMeters(dist);
    double strafing = Helper.rpmFromMeters(dist, 0.0, new ChassisSpeeds(0, 2.0, 0));
    assertTrue(strafing > stationary, "Lateral strafing increases effective distance");
    // But not by much — the Pythagorean correction is second-order.
    assertTrue(
        (strafing - stationary) / stationary < 0.10,
        "Lateral correction for 2 m/s strafe should be < 10%");
  }

  @Test
  void fixedPoint_rotationDoesNotAffectResult() {
    // Pure rotation (omega only) doesn't translate the robot — no effect on RPM.
    double dist = 2.0;
    double stationary = Helper.rpmFromMeters(dist, 0.0, new ChassisSpeeds(0, 0, 0));
    double rotating = Helper.rpmFromMeters(dist, 0.0, new ChassisSpeeds(0, 0, 5.0));
    assertEquals(stationary, rotating, 1e-9);
  }

  @Test
  void fixedPoint_bearingAwayFromForward_swapsVxBehaviour() {
    // With bearing = π (target BEHIND robot), moving forward (+vx) is moving AWAY,
    // so RPM should be higher than stationary — opposite of bearing=0 case.
    double dist = 2.0;
    double stationary = Helper.rpmFromMeters(dist);
    double forwardButTargetBehind = Helper.rpmFromMeters(dist, Math.PI, new ChassisSpeeds(1.0, 0, 0));
    assertTrue(
        forwardButTargetBehind > stationary,
        "vx+ with target behind = moving away = more RPM");
  }

  @Test
  void fixedPoint_staysWithinBoundsForAllScenarios() {
    // Sweep: distances {0.5, 1.0, ..., 4.0}, bearings {-π, -3π/4, ..., π},
    // speeds vx,vy ∈ {-3,-1,1,3}. Int loop counters (SpotBugs FL_FLOATS_AS_LOOP_COUNTERS).
    for (int di = 1; di <= 8; di++) {
      double d = di * 0.5;
      for (int ti = -4; ti <= 4; ti++) {
        double theta = ti * Math.PI / 4;
        for (int vx = -3; vx <= 3; vx += 2) {
          for (int vy = -3; vy <= 3; vy += 2) {
            double rpm = Helper.rpmFromMeters(d, theta, new ChassisSpeeds(vx, vy, 0));
            assertTrue(
                rpm >= Constants.Flywheel.kMinRpm && rpm <= Constants.Flywheel.kMaxRpm,
                "Out of bounds at d=" + d + " theta=" + theta + " vx=" + vx + " vy=" + vy);
          }
        }
      }
    }
  }

  @Test
  void effectiveShotDistance_stationaryReturnsInputDistance() {
    // Direct test of the pure-function iteration core.
    var target = new edu.wpi.first.math.geometry.Translation2d(2.5, 0);
    var velocity = new edu.wpi.first.math.geometry.Translation2d(0, 0);
    assertEquals(2.5, Helper.effectiveShotDistanceMeters(target, velocity), 1e-9);
  }

  @Test
  void effectiveShotDistance_movingTowardShortens() {
    // Target at (2, 0), moving at (1, 0) = toward target. Effective distance should be less.
    var target = new edu.wpi.first.math.geometry.Translation2d(2.0, 0);
    var velocity = new edu.wpi.first.math.geometry.Translation2d(1.0, 0);
    double effective = Helper.effectiveShotDistanceMeters(target, velocity);
    assertTrue(effective < 2.0, "Moving toward target shortens effective distance");
  }

  @Test
  void effectiveShotDistance_convergenceIsStable() {
    // After 3 iterations, the result should be close to a converged fixed point.
    // Run a reference 6-iter computation manually and compare.
    var target = new edu.wpi.first.math.geometry.Translation2d(3.0, 1.0);
    var velocity = new edu.wpi.first.math.geometry.Translation2d(1.5, 0.5);
    double ballSpeed = Constants.Flywheel.kBallExitVelocityMps;

    // Reference: 6-iter ground truth
    var virtual = target;
    for (int i = 0; i < 6; i++) {
      double airTime = virtual.getNorm() / ballSpeed;
      virtual = target.minus(velocity.times(airTime));
    }
    double groundTruth = virtual.getNorm();

    double actual = Helper.effectiveShotDistanceMeters(target, velocity);
    // Convergence ratio q = ||v|| / ballSpeed ≈ 0.13 for this test; after 3 iters the residual
    // relative to 6-iter ground truth is ≈ q³ * initial_distance ≈ 2.3 mm for d=3 m. 1 mm is
    // well within flywheel tuning noise, so a stricter tolerance would be false precision.
    assertEquals(groundTruth, actual, 1e-3, "3-iter result must match 6-iter within 1 mm");
  }
}
