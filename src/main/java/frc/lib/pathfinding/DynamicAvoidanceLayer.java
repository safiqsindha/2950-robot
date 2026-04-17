package frc.lib.pathfinding;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Translation2d;
import java.util.List;

/**
 * Artificial potential-field layer for dynamic opponent avoidance.
 *
 * <p>Computes a corrected velocity vector each loop cycle by summing:
 *
 * <ul>
 *   <li><b>Attractive force</b> — points from the robot toward the next waypoint, magnitude
 *       proportional to {@code attractiveGain × maxRobotSpeedMps}.
 *   <li><b>Repulsive force</b> — for each opponent within {@code opponentInfluenceRadiusMeters}, a
 *       force pointing away from the opponent, linearly scaled from zero at the boundary to max
 *       at dist→0.
 * </ul>
 *
 * <p>The resulting vector is capped at {@code maxRobotSpeedMps} so the output can be passed
 * directly to {@code SwerveSubsystem.driveRobotRelative} (after a Pose2d→ChassisSpeeds conversion
 * in the caller).
 *
 * <p>This class lives in {@code frc.lib.pathfinding} and has no robot-specific dependencies — the
 * tuning constants flow through the constructor so the class stays reusable across seasons. The
 * {@code DEFAULT_*} constants below match the 2026 HUB tuning (see {@code
 * Constants.Pathfinding}) so existing tests and the no-arg convenience constructor keep the same
 * behavior.
 */
public final class DynamicAvoidanceLayer {

  /** Default opponent influence radius (m) — 2026 HUB tuning. */
  public static final double DEFAULT_OPPONENT_INFLUENCE_RADIUS_M = 2.0;

  /** Default max robot speed (m/s) — 2026 HUB tuning. */
  public static final double DEFAULT_MAX_ROBOT_SPEED_MPS = 4.5;

  /** Default attractive gain toward waypoint. */
  public static final double DEFAULT_ATTRACTIVE_GAIN = 1.0;

  /** Default repulsive gain away from opponents. */
  public static final double DEFAULT_REPULSIVE_GAIN = 1.5;

  private final double maxRobotSpeedMps;
  private final double attractiveGain;
  private final double opponentInfluenceRadiusMeters;
  private final double repulsiveGain;

  /** Convenience ctor using the {@code DEFAULT_*} tuning. */
  public DynamicAvoidanceLayer() {
    this(
        DEFAULT_MAX_ROBOT_SPEED_MPS,
        DEFAULT_ATTRACTIVE_GAIN,
        DEFAULT_OPPONENT_INFLUENCE_RADIUS_M,
        DEFAULT_REPULSIVE_GAIN);
  }

  /**
   * Primary ctor — explicit tuning for reuse in non-default environments (different drivetrain,
   * different season, etc.).
   *
   * @param maxRobotSpeedMps cap applied to the returned velocity vector's magnitude
   * @param attractiveGain scales the waypoint-seeking component
   * @param opponentInfluenceRadiusMeters opponents farther than this produce zero repulsion
   * @param repulsiveGain scales the opponent-repelling component
   */
  public DynamicAvoidanceLayer(
      double maxRobotSpeedMps,
      double attractiveGain,
      double opponentInfluenceRadiusMeters,
      double repulsiveGain) {
    this.maxRobotSpeedMps = maxRobotSpeedMps;
    this.attractiveGain = attractiveGain;
    this.opponentInfluenceRadiusMeters = opponentInfluenceRadiusMeters;
    this.repulsiveGain = repulsiveGain;
  }

  /**
   * Compute a corrected field-relative velocity vector.
   *
   * @param robotPose current robot pose (field-relative)
   * @param waypoint next path waypoint to drive toward (field-relative, meters)
   * @param opponents list of detected opponent positions (field-relative, meters)
   * @return corrected velocity vector (m/s, field-relative), magnitude ≤ {@code maxRobotSpeedMps}
   */
  public Translation2d computeCorrectedVelocity(
      Pose2d robotPose, Translation2d waypoint, List<Translation2d> opponents) {

    Translation2d robotPos = robotPose.getTranslation();

    // ── Attractive force ──────────────────────────────────────────────────────
    Translation2d toWaypoint = waypoint.minus(robotPos);
    double waypointDist = toWaypoint.getNorm();

    double attrX = 0;
    double attrY = 0;
    if (waypointDist > 1e-6) {
      double scale = maxRobotSpeedMps * attractiveGain / waypointDist;
      attrX = toWaypoint.getX() * scale;
      attrY = toWaypoint.getY() * scale;
    }

    // ── Repulsive forces ──────────────────────────────────────────────────────
    double repX = 0;
    double repY = 0;
    for (Translation2d opp : opponents) {
      Translation2d fromOpp = robotPos.minus(opp);
      double dist = fromOpp.getNorm();
      if (dist > 1e-6 && dist < opponentInfluenceRadiusMeters) {
        // Linear falloff: full strength at dist→0, zero at the influence boundary
        double magnitude =
            repulsiveGain
                * (opponentInfluenceRadiusMeters - dist)
                / opponentInfluenceRadiusMeters
                * maxRobotSpeedMps;
        repX += (fromOpp.getX() / dist) * magnitude;
        repY += (fromOpp.getY() / dist) * magnitude;
      }
    }

    // ── Sum and cap ───────────────────────────────────────────────────────────
    double totalX = attrX + repX;
    double totalY = attrY + repY;
    double totalMag = Math.sqrt(totalX * totalX + totalY * totalY);

    if (totalMag > maxRobotSpeedMps) {
      double scale = maxRobotSpeedMps / totalMag;
      totalX *= scale;
      totalY *= scale;
    }

    return new Translation2d(totalX, totalY);
  }
}
