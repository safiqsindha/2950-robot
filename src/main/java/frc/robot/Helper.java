package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.filter.LinearFilter;
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

  /** Configure Limelight to filter for the relevant AprilTag IDs for 2026 REBUILT hub targets. */
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
