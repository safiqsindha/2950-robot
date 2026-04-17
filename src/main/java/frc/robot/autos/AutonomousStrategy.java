package frc.robot.autos;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import frc.lib.AllianceFlip;
import frc.robot.Constants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decision engine that evaluates candidate autonomous targets and ranks them by utility.
 *
 * <p>Scoring factors:
 *
 * <ul>
 *   <li><b>Action priority:</b> CLIMB overrides everything when time is low; SCORE beats COLLECT
 *       when HUB is active and robot holds FUEL.
 *   <li><b>Distance:</b> closer targets score higher (inverse-distance weighting).
 *   <li><b>Opponent penalty:</b> targets near detected opponents are penalized.
 * </ul>
 *
 * <p>Also provides the <b>Bot Aborter</b> — a static check that aborts a target if an opponent will
 * arrive {@link Constants.Pathfinding#kAbortTimeThresholdSeconds} before the robot.
 */
public final class AutonomousStrategy {

  // Fixed field locations defined in blue-alliance coordinates.
  // evaluateTargets() flips these via AllianceFlip when GameState.isRedAlliance() is true.
  //
  // ⚠ 2025-era placeholders — strategic update pending for 2026 REBUILT ⚠
  // Tracked in FOLLOWUPS.md step 6. Changing these values requires re-authoring
  // the Choreo .traj paths that approach them — coordinate with trajectory PR.
  //
  // Authoritative 2026 REBUILT WELDED reference data (from WPILib
  // apriltag/src/main/native/resources/edu/wpi/first/apriltag/2026-rebuilt-welded.json
  // in wpilibsuite/allwpilib), blue-alliance-origin coords:
  //
  //   Blue HUB center (avg of 8 face tags 18,19,20,21,24,25,26,27 at z=1.124 m):
  //     (4.537, 4.035) — typical scoring approach would sit ~1 m west, e.g. (3.5, 4.0)
  //   Red HUB center (tags 2,3,4,5,8,9,10,11, mirrored after AllianceFlip):
  //     (12.004, 4.035)
  //   Blue TOWER tags (29,30,31,32 on west wall at x=0.008, z=0.552 m):
  //     y = 0.666 / 1.098 (pair 1) and 3.746 / 4.178 (pair 2)

  /**
   * Blue HUB scoring pose. 2025-era placeholder — (3.39, 4.11) was the 2025 Blue Reef center.
   * 2026 Blue HUB center is at (4.537, 4.035); appropriate approach pose depends on shot
   * strategy (front-face vs flank, moving-shot vs static). See FOLLOWUPS.md step 6.
   */
  private static final Pose2d HUB_POSE = new Pose2d(3.39, 4.11, new Rotation2d());

  /**
   * Blue climbing pose. 2025-era placeholder — (8.23, 4.11) is approximately field center,
   * which is not where any 2026 REBUILT TOWER sits. Blue TOWERs are on the west wall
   * (x=0.008); a reasonable approach pose is ~(1.0, 4.0) for the upper TOWER pair.
   * See FOLLOWUPS.md step 6.
   */
  private static final Pose2d CLIMB_POSE = new Pose2d(8.23, 4.11, new Rotation2d());

  /**
   * Default fallback collect pose when no FUEL is detected. Currently duplicate of
   * CLIMB_POSE (both field-center placeholders). Intent was probably "go to a safe
   * neutral spot"; with real 2026 poses this should diverge from CLIMB_POSE — e.g. a
   * staging area near the FUEL intake. See FOLLOWUPS.md step 6.
   */
  private static final Pose2d DEFAULT_COLLECT_POSE = new Pose2d(8.23, 4.11, new Rotation2d());

  /**
   * Evaluate all candidate targets given the current game state and return them ranked by utility
   * (highest first).
   *
   * @param state current game snapshot
   * @return scored targets sorted descending by utility; never null, may be empty
   */
  public List<ScoredTarget> evaluateTargets(GameState state) {
    List<ScoredTarget> targets = new ArrayList<>();
    Translation2d robotPos = state.getRobotPose().getTranslation();
    boolean isRed = state.isRedAlliance();

    // Flip fixed poses to match the current alliance origin.
    Pose2d hubPose = AllianceFlip.flip(HUB_POSE, isRed);
    Pose2d climbPose = AllianceFlip.flip(CLIMB_POSE, isRed);

    // ── CLIMB — dominates when time is low ──
    if (state.getTimeRemaining() <= Constants.Pathfinding.kClimbTimeThresholdSeconds) {
      double dist = robotPos.getDistance(climbPose.getTranslation());
      // High base utility; still distance-weighted so nearer is better
      double utility = 100.0 - dist;
      targets.add(new ScoredTarget(ActionType.CLIMB, climbPose, utility));
    }

    // ── SCORE — when HUB is active and robot holds FUEL ──
    if (state.isHubActive() && state.getFuelHeld() > 0) {
      double dist = robotPos.getDistance(hubPose.getTranslation());
      // Base utility 50 + fuel bonus; penalize by distance
      double utility = 50.0 + state.getFuelHeld() * 5.0 - dist;
      targets.add(new ScoredTarget(ActionType.SCORE, hubPose, utility));
    }

    // ── COLLECT — one target per detected FUEL position ──
    for (Translation2d fuelPos : state.getDetectedFuel()) {
      double dist = robotPos.getDistance(fuelPos);
      // Base utility 20; penalize by distance and opponent proximity
      double utility = 20.0 - dist;
      utility -= opponentPenalty(fuelPos, state.getDetectedOpponents());
      Pose2d collectPose = new Pose2d(fuelPos, new Rotation2d());
      targets.add(new ScoredTarget(ActionType.COLLECT, collectPose, utility));
    }

    // If nothing else, still offer COLLECT at the default fallback position (flipped for alliance).
    if (targets.isEmpty()) {
      targets.add(
          new ScoredTarget(
              ActionType.COLLECT, AllianceFlip.flip(DEFAULT_COLLECT_POSE, isRed), 0.0));
    }

    targets.sort(Comparator.comparingDouble(ScoredTarget::utility).reversed());
    return targets;
  }

  /**
   * Bot Aborter — returns true if the opponent will reach the target before the robot by at least
   * {@link Constants.Pathfinding#kAbortTimeThresholdSeconds}.
   *
   * @param robotDist robot's distance to target (meters)
   * @param robotSpeed robot's current speed (m/s)
   * @param opponentDist opponent's distance to target (meters)
   * @param opponentSpeed opponent's estimated speed (m/s)
   * @return true if the robot should abort this target
   */
  public static boolean shouldAbortTarget(
      double robotDist, double robotSpeed, double opponentDist, double opponentSpeed) {
    if (robotSpeed <= 0) return true; // can't reach if not moving
    if (opponentSpeed <= 0) return false; // opponent stationary — no threat

    double robotEta = robotDist / robotSpeed;
    double opponentEta = opponentDist / opponentSpeed;
    return (robotEta - opponentEta) >= Constants.Pathfinding.kAbortTimeThresholdSeconds;
  }

  // ---- private helpers ----

  /**
   * Compute opponent proximity penalty for a target position. Targets near opponents are penalized
   * proportionally to how close the nearest opponent is.
   */
  private static double opponentPenalty(Translation2d targetPos, List<Translation2d> opponents) {
    double penalty = 0.0;
    for (Translation2d opp : opponents) {
      double dist = targetPos.getDistance(opp);
      if (dist < Constants.Pathfinding.kOpponentInfluenceRadiusMeters) {
        // Penalty increases as opponent gets closer (inverse relationship)
        penalty +=
            (Constants.Pathfinding.kOpponentInfluenceRadiusMeters - dist)
                * Constants.Pathfinding.kRepulsiveGain;
      }
    }
    return penalty;
  }
}
