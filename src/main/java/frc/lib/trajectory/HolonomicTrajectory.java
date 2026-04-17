package frc.lib.trajectory;

import edu.wpi.first.math.geometry.Pose2d;
import java.util.Optional;

/**
 * Planner-agnostic interface for a time-parameterised holonomic trajectory. Adapted from Team
 * 4481 Rembrandts' {@code HolonomicTrajectory}.
 *
 * <p>Decouples trajectory <em>followers</em> from trajectory <em>sources</em>. A {@link
 * ChoreoTrajectoryAdapter} wraps a Choreo {@code Trajectory<SwerveSample>} today; a future
 * adapter could wrap PathPlanner, a runtime HUB-aligner, or a hand-rolled trapezoid — the
 * follower doesn't have to change.
 *
 * <p>All methods return {@link Optional} because some adapters can't produce a sample for every
 * time (e.g. before {@code t=0}, after the trajectory ends, or when the backing trajectory is
 * empty).
 */
public interface HolonomicTrajectory {

  /**
   * @param timestampSeconds seconds from the start of the trajectory (0 = initial pose)
   * @return interpolated pose + chassis speeds at the given time, or empty if out of range
   */
  Optional<HolonomicTrajectorySample> sampleAt(double timestampSeconds);

  /** @return the pose at {@code t=0}, or empty if the trajectory has no samples */
  Optional<Pose2d> getInitialPose();

  /** @return the pose at {@code t=getTotalTime()}, or empty if the trajectory has no samples */
  Optional<Pose2d> getFinalPose();

  /** @return total trajectory duration in seconds. Zero for an empty trajectory. */
  double getTotalTime();
}
