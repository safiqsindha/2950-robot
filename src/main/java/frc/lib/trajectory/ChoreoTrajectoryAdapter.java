package frc.lib.trajectory;

import choreo.trajectory.SwerveSample;
import choreo.trajectory.Trajectory;
import edu.wpi.first.math.geometry.Pose2d;
import java.util.Optional;

/**
 * Wraps a Choreo {@link Trajectory} of {@link SwerveSample} as a {@link HolonomicTrajectory}.
 *
 * <p>Adapted from Team 4481 Rembrandts' {@code ChoreoTrajectoryAdapter}. The wrapper forwards
 * time-domain queries to Choreo and lifts each {@link SwerveSample} into a {@link
 * HolonomicTrajectorySample} on demand.
 *
 * <p>Alliance mirroring is handled in one place — construct with {@code mirrorForRedAlliance =
 * true} when a flipped trajectory is needed; the adapter propagates the flag into every Choreo
 * sample call. This centralises red/blue handling so downstream followers don't need to know about
 * alliance.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Trajectory<SwerveSample> choreo = autoFactory.trajectory("myPath");
 * HolonomicTrajectory trajectory = new ChoreoTrajectoryAdapter(choreo, isRedAlliance());
 * trajectory.sampleAt(1.5).ifPresent(follower::follow);
 * }</pre>
 */
public final class ChoreoTrajectoryAdapter implements HolonomicTrajectory {

  private final Trajectory<SwerveSample> choreoTrajectory;
  private final boolean mirrorForRedAlliance;

  public ChoreoTrajectoryAdapter(
      Trajectory<SwerveSample> choreoTrajectory, boolean mirrorForRedAlliance) {
    this.choreoTrajectory = choreoTrajectory;
    this.mirrorForRedAlliance = mirrorForRedAlliance;
  }

  /** Convenience overload — no alliance mirroring. */
  public ChoreoTrajectoryAdapter(Trajectory<SwerveSample> choreoTrajectory) {
    this(choreoTrajectory, false);
  }

  @Override
  public Optional<HolonomicTrajectorySample> sampleAt(double timestampSeconds) {
    return choreoTrajectory
        .sampleAt(timestampSeconds, mirrorForRedAlliance)
        .map(
            s ->
                new HolonomicTrajectorySample(s.getTimestamp(), s.getPose(), s.getChassisSpeeds()));
  }

  @Override
  public Optional<Pose2d> getInitialPose() {
    return choreoTrajectory.getInitialPose(mirrorForRedAlliance);
  }

  @Override
  public Optional<Pose2d> getFinalPose() {
    return choreoTrajectory.getFinalPose(mirrorForRedAlliance);
  }

  @Override
  public double getTotalTime() {
    return choreoTrajectory.getTotalTime();
  }
}
