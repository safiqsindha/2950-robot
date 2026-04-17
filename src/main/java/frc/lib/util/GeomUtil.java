package frc.lib.util;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Twist2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import java.util.List;

/**
 * Small pure-math geometry helpers — adapted from Team 4481 Rembrandts' {@code GeometryUtil}.
 *
 * <p>Two high-value utilities here:
 *
 * <ul>
 *   <li>{@link #getClosestPose} — nearest waypoint by Euclidean distance
 *   <li>{@link #getClosestFuturePose} — same, but evaluated at "where I'll be in Δt" instead of
 *       "where I am now". Essential for HUB-aligners and scoring-pose selectors — eliminates the
 *       race between target selection and robot motion.
 * </ul>
 */
public final class GeomUtil {

  private GeomUtil() {}

  /**
   * @param current the robot's current pose (field-relative)
   * @param candidates non-empty list of candidate target poses
   * @return the candidate whose {@link Pose2d#getTranslation translation} is closest to {@code
   *     current.getTranslation()}
   * @throws IllegalArgumentException if {@code candidates} is empty
   */
  public static Pose2d getClosestPose(Pose2d current, List<Pose2d> candidates) {
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("candidates must be non-empty");
    }
    Pose2d best = candidates.get(0);
    double bestDist = current.getTranslation().getDistance(best.getTranslation());
    for (int i = 1; i < candidates.size(); i++) {
      Pose2d p = candidates.get(i);
      double d = current.getTranslation().getDistance(p.getTranslation());
      if (d < bestDist) {
        bestDist = d;
        best = p;
      }
    }
    return best;
  }

  /**
   * Extrapolates the robot's pose forward by {@code dt} using field-relative chassis velocity, then
   * returns the closest candidate to that <em>future</em> pose. Matches 4481's behaviour.
   *
   * <p>Why: if target selection happens based on {@code current}, but by the time the robot
   * finishes aligning it's already 0.5 m away, the wrong target gets picked. Extrapolating by the
   * time we expect to reach the target removes the race.
   *
   * @param current current pose (field-relative)
   * @param fieldRelativeSpeeds current chassis velocity (field-relative)
   * @param lookaheadSeconds how far to look ahead (seconds)
   * @param candidates non-empty list of candidate target poses
   */
  public static Pose2d getClosestFuturePose(
      Pose2d current,
      ChassisSpeeds fieldRelativeSpeeds,
      double lookaheadSeconds,
      List<Pose2d> candidates) {
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("candidates must be non-empty");
    }
    if (lookaheadSeconds < 0) {
      throw new IllegalArgumentException("lookaheadSeconds must be >= 0");
    }
    Pose2d future = extrapolate(current, fieldRelativeSpeeds, lookaheadSeconds);
    return getClosestPose(future, candidates);
  }

  /**
   * Extrapolate pose forward by {@code dt} seconds using field-relative chassis velocity. Uses
   * {@link Pose2d#exp(Twist2d)} for proper Lie-algebra integration (matters when omega is
   * non-zero). Package-private for unit testing.
   */
  static Pose2d extrapolate(Pose2d pose, ChassisSpeeds fieldRelSpeeds, double dt) {
    // Convert field-relative velocity into a body-frame twist so exp() integrates correctly.
    double heading = pose.getRotation().getRadians();
    double cos = Math.cos(heading);
    double sin = Math.sin(heading);
    double bodyVx = fieldRelSpeeds.vxMetersPerSecond * cos + fieldRelSpeeds.vyMetersPerSecond * sin;
    double bodyVy =
        -fieldRelSpeeds.vxMetersPerSecond * sin + fieldRelSpeeds.vyMetersPerSecond * cos;
    Twist2d twist =
        new Twist2d(bodyVx * dt, bodyVy * dt, fieldRelSpeeds.omegaRadiansPerSecond * dt);
    return pose.exp(twist);
  }

  /**
   * Compute the squared distance between two translations. Use when ranking N candidates — the
   * square root in {@link Translation2d#getDistance} is redundant work.
   */
  public static double squaredDistance(Translation2d a, Translation2d b) {
    double dx = a.getX() - b.getX();
    double dy = a.getY() - b.getY();
    return dx * dx + dy * dy;
  }
}
