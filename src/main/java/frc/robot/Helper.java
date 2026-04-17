package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.filter.LinearFilter;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import java.util.List;
import limelight.Limelight;
import limelight.results.RawFiducial;

/**
 * Utility methods for Limelight integration and RPM-distance lookup. Migrated from the
 * confirmed-working swerve-test branch.
 */
public class Helper {

  private static Limelight ll;

  private static Limelight getLl() {
    if (ll == null) ll = new Limelight("limelight");
    return ll;
  }

  private static final LinearFilter distFilter = LinearFilter.singlePoleIIR(0.1, 0.02);
  private static final LinearFilter aimFilter = LinearFilter.singlePoleIIR(0.1, 0.02);

  private Helper() {}

  /**
   * Calculate flywheel RPM setpoint from distance to hub using Lagrange quadratic interpolation.
   * Exact fit at three measured calibration points; single smooth curve with no kink at the segment
   * boundary (replaces the previous piecewise-linear implementation).
   *
   * <p>Calibration points (tuned on hardware):
   *
   * <ul>
   *   <li>1.125 m → 2500 RPM (short range)
   *   <li>1.714 m → 3000 RPM (mid range)
   *   <li>2.500 m → 3500 RPM (long range)
   * </ul>
   *
   * @param meters distance to the hub in meters
   * @return target RPM clamped to [{@link Constants.Flywheel#kMinRpm}, {@link
   *     Constants.Flywheel#kMaxRpm}]
   */
  public static double rpmFromMeters(double meters) {
    // Calibration points: (distance_m, target_rpm)
    final double x1 = 1.125, y1 = 2500.0;
    final double x2 = 1.714, y2 = 3000.0;
    final double x3 = 2.500, y3 = 3500.0;

    // Beyond the calibration range the concave-down quadratic turns negative quickly, so fall back
    // to linear extrapolation (same slope as the outer segment) outside [x1, x3].
    final double rpmGuess;
    if (meters > x3) {
      // Linear tail from x3: slope = (y3-y2)/(x3-x2)
      rpmGuess = y3 + (y3 - y2) / (x3 - x2) * (meters - x3);
    } else {
      // Lagrange quadratic basis polynomials — exact at each calibration point by construction.
      double l1 = (meters - x2) * (meters - x3) / ((x1 - x2) * (x1 - x3));
      double l2 = (meters - x1) * (meters - x3) / ((x2 - x1) * (x2 - x3));
      double l3 = (meters - x1) * (meters - x2) / ((x3 - x1) * (x3 - x2));
      rpmGuess = y1 * l1 + y2 * l2 + y3 * l3;
    }
    return MathUtil.clamp(rpmGuess, Constants.Flywheel.kMinRpm, Constants.Flywheel.kMaxRpm);
  }

  /**
   * Calculate flywheel RPM accounting for robot chassis velocity along the shot axis. When the
   * robot is moving away from the target ({@code vx > 0}), the ball must travel farther during
   * flight, so more RPM is needed. Moving toward the target ({@code vx < 0}) reduces the effective
   * distance.
   *
   * <p>Uses a first-order time-of-flight correction:
   *
   * <pre>
   *   effectiveMeters = meters × (1 + vx / v_ball)
   * </pre>
   *
   * where {@code vx} is the forward component of chassis speed and {@code v_ball = }{@link
   * Constants.Flywheel#kBallExitVelocityMps}.
   *
   * @param meters measured distance to hub in meters
   * @param robotSpeeds current chassis speeds (robot-relative; vx positive = moving away from
   *     target)
   * @return target RPM clamped to [{@link Constants.Flywheel#kMinRpm}, {@link
   *     Constants.Flywheel#kMaxRpm}]
   */
  public static double rpmFromMeters(double meters, ChassisSpeeds robotSpeeds) {
    double effectiveMeters =
        meters * (1.0 + robotSpeeds.vxMetersPerSecond / Constants.Flywheel.kBallExitVelocityMps);
    return rpmFromMeters(Math.max(0.0, effectiveMeters));
  }

  /**
   * Calculate flywheel RPM for a moving-shot with full 2D velocity compensation (Team 971
   * Spartan's 3-iteration fixed-point shoot-on-the-fly algorithm).
   *
   * <p>Given the robot-relative target offset (from Limelight {@code tx} + distance), chassis
   * velocity (robot-relative), and ball exit speed, this iteratively solves for a "virtual target"
   * — the position the target would need to be for a stationary launch to still intersect it after
   * the ball's flight time. Converges in 3 iterations because {@code ball_speed ≫ robot_speed}.
   *
   * <p>Unlike the {@link #rpmFromMeters(double, ChassisSpeeds)} overload (which only handles
   * forward motion via {@code vx}), this method properly accounts for strafing (lateral velocity)
   * by doing the full 2D geometry.
   *
   * <p>Sign convention: standard {@link ChassisSpeeds} — {@code vx} positive = robot moving
   * forward in its own frame; {@code vy} positive = robot moving left. Target is positioned at
   * polar {@code (meters, bearingRadians)} in robot frame.
   *
   * @param meters distance to the target from Limelight (meters)
   * @param bearingRadians direction to target in robot frame (Limelight {@code tx} converted to
   *     radians; 0 = straight ahead, positive = left)
   * @param robotSpeeds current chassis speeds (robot-relative)
   * @return target RPM clamped to [{@link Constants.Flywheel#kMinRpm}, {@link
   *     Constants.Flywheel#kMaxRpm}]
   */
  public static double rpmFromMeters(
      double meters, double bearingRadians, ChassisSpeeds robotSpeeds) {
    Translation2d target =
        new Translation2d(meters * Math.cos(bearingRadians), meters * Math.sin(bearingRadians));
    Translation2d velocity =
        new Translation2d(robotSpeeds.vxMetersPerSecond, robotSpeeds.vyMetersPerSecond);
    double effectiveMeters = effectiveShotDistanceMeters(target, velocity);
    return rpmFromMeters(Math.max(0.0, effectiveMeters));
  }

  /**
   * Computes the effective shot distance via Team 971's 3-iteration fixed-point shoot-on-the-fly.
   *
   * <p>At convergence, returns {@code ||virtualTarget||}, where {@code virtualTarget} is the shifted
   * aim point that accounts for robot motion during ball flight. Package-private for unit testing.
   *
   * @param targetRelative target position in robot frame (meters)
   * @param velocityRelative chassis velocity in robot frame (m/s)
   * @return effective shot distance in meters
   */
  static double effectiveShotDistanceMeters(
      Translation2d targetRelative, Translation2d velocityRelative) {
    final double ballExitSpeed = Constants.Flywheel.kBallExitVelocityMps;
    Translation2d virtualTarget = targetRelative;
    // 3 iterations is enough: convergence ratio q = ||velocity|| / ballExitSpeed is typically
    // 0.05–0.15 for FRC speeds, so 3 iters drive the residual to roughly q³ ≈ 10⁻³ of the
    // initial distance — sub-centimetre at any realistic shot range.
    for (int i = 0; i < 3; i++) {
      double airTime = virtualTarget.getNorm() / ballExitSpeed;
      virtualTarget = targetRelative.minus(velocityRelative.times(airTime));
    }
    return virtualTarget.getNorm();
  }

  /**
   * Configure Limelight to filter for the relevant AprilTag IDs for 2026 REBUILT HUB targets.
   *
   * <p>The 2026 REBUILT WELDED layout has 16 HUB tags total — 8 per HUB at z=1.124 m, arranged
   * on the four faces of each (square) HUB structure (two tags per face):
   *
   * <pre>
   *   Red HUB  (center ≈ 12.0, 4.0): {2, 3, 4, 5, 8, 9, 10, 11}
   *   Blue HUB (center ≈ 4.5,  4.0): {18, 19, 20, 21, 24, 25, 26, 27}
   * </pre>
   *
   * <p>The filter selects one tag from each of three faces per HUB, excluding the face that
   * points toward the opposing alliance wall:
   *
   * <pre>
   *   Red:  {2  (N face, yaw +90°),  5 (S face, yaw 270°), 10 (E face, yaw   0°)}
   *                                                        — W face excluded
   *   Blue: {18 (S face, yaw 270°), 21 (N face, yaw +90°), 26 (W face, yaw 180°)}
   *                                                        — E face excluded
   * </pre>
   *
   * <p>The excluded-face tags would only be visible when the robot is on the wrong side of
   * the HUB (past midfield on the opposing alliance's side); seeing them during normal play
   * would indicate an odometry error and could corrupt the pose estimate. Capping the filter
   * also reduces Limelight processing load vs. accepting all 16 HUB tags.
   *
   * <p>Verified against WPILib {@code 2026-rebuilt-welded.json} in {@code wpilibsuite/allwpilib}.
   * Non-HUB tags ({13,14,15,16,29,30,31,32} are short-z TOWER/OUTPOST tags; {1,6,7,12,17,22,23,28}
   * are z=0.889 m TRENCH tags) are deliberately excluded — this Limelight is configured for
   * HUB-only tracking; if we need TOWER tracking at climb time it would be a separate pipeline.
   */
  public static void llSetup() {
    getLl().getSettings().withAprilTagIdFilter(List.of(2, 5, 10, 18, 21, 26)).save();
  }

  /** Push latest Limelight fiducial data through the IIR filters. Call every 20ms cycle. */
  public static void updateFilters() {
    // Skip Limelight calls in simulation — no hardware, and getData() blocks for ~2s
    if (edu.wpi.first.wpilibj.RobotBase.isSimulation()) {
      return;
    }
    RawFiducial[] raw = getLl().getData().getRawFiducials();
    for (RawFiducial object : raw) {
      distFilter.calculate(object.distToCamera);
      aimFilter.calculate(object.txnc);
    }
  }

  /** Get the filtered distance to the nearest tracked AprilTag hub target. */
  public static double getAprilTagDist() {
    return distFilter.lastValue();
  }

  /** Get the filtered horizontal offset (tx) to the nearest tracked AprilTag hub target. */
  public static double getAprilTagAim() {
    return aimFilter.lastValue();
  }

  /** Reset IIR filters and feed one zero sample to avoid stale state. */
  public static void resetFilters() {
    aimFilter.reset();
    distFilter.reset();
    distFilter.calculate(0);
    aimFilter.calculate(0);
  }
}
