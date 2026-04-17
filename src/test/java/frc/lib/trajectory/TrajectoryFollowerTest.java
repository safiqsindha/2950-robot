package frc.lib.trajectory;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * HAL-free verification of the feedforward+feedback composition. A fake {@link
 * HolonomicTrajectory} lets us drive the follower with hand-picked samples and verify that:
 *
 * <ul>
 *   <li>When the robot is exactly on the setpoint pose, correction is zero → output equals the
 *       sample's feedforward speeds.
 *   <li>When the robot lags behind in +x, the follower produces a positive x correction.
 *   <li>When the robot has a heading error, the correction wraps the shortest way around.
 *   <li>{@link TrajectoryFollower#follow} returns {@link Optional#empty()} when the trajectory
 *       has no sample at the given time.
 * </ul>
 */
class TrajectoryFollowerTest {

  @Test
  void follow_onSetpointPose_correctionIsZero() {
    TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();
    // Sample says "be at (3,4) facing 0, moving +x at 1 m/s"
    HolonomicTrajectorySample sample =
        new HolonomicTrajectorySample(
            0.5, new Pose2d(3.0, 4.0, new Rotation2d()), new ChassisSpeeds(1.0, 0.0, 0.0));

    ChassisSpeeds out = follower.computeSpeeds(sample, new Pose2d(3.0, 4.0, new Rotation2d()));
    assertEquals(1.0, out.vxMetersPerSecond, 1e-9, "FF vx should pass through untouched");
    assertEquals(0.0, out.vyMetersPerSecond, 1e-9);
    assertEquals(0.0, out.omegaRadiansPerSecond, 1e-9);
  }

  @Test
  void follow_laggingInX_emitsPositiveCorrection() {
    TrajectoryFollower follower = new TrajectoryFollower(4.0, 3.0);
    HolonomicTrajectorySample sample =
        new HolonomicTrajectorySample(
            0.5, new Pose2d(3.0, 4.0, new Rotation2d()), new ChassisSpeeds());
    // Robot is at x=2.5, setpoint wants x=3.0 → 0.5 m error × kP 4.0 = +2.0 m/s correction
    ChassisSpeeds out = follower.computeSpeeds(sample, new Pose2d(2.5, 4.0, new Rotation2d()));
    assertEquals(2.0, out.vxMetersPerSecond, 1e-9);
    assertEquals(0.0, out.vyMetersPerSecond, 1e-9);
    assertEquals(0.0, out.omegaRadiansPerSecond, 1e-9);
  }

  @Test
  void follow_headingError_wrapsShortWay() {
    TrajectoryFollower follower = new TrajectoryFollower(4.0, 3.0);
    // Target heading near +π, robot at just past −π → error should be small positive (short way
    // through the wrap), not nearly 2π the long way around.
    HolonomicTrajectorySample sample =
        new HolonomicTrajectorySample(
            0.0,
            new Pose2d(0.0, 0.0, Rotation2d.fromRadians(Math.PI - 0.1)),
            new ChassisSpeeds());
    ChassisSpeeds out =
        follower.computeSpeeds(
            sample, new Pose2d(0.0, 0.0, Rotation2d.fromRadians(-Math.PI + 0.1)));
    // Error = (π - 0.1) - (-π + 0.1) = 2π - 0.2; wrapped → -0.2 (shorter the OTHER way through -π)
    // kP 3.0 × -0.2 = -0.6
    assertEquals(-0.6, out.omegaRadiansPerSecond, 1e-6);
  }

  @Test
  void follow_sampleOutOfRange_returnsEmpty() {
    TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();
    HolonomicTrajectory empty = new EmptyTrajectory();
    Optional<ChassisSpeeds> out = follower.follow(empty, new Pose2d(), 1.0);
    assertTrue(out.isEmpty(), "Empty trajectory should produce no chassis speeds");
  }

  @Test
  void setGains_updateControllers() {
    TrajectoryFollower follower = new TrajectoryFollower(4.0, 3.0);
    follower.setTranslationKp(10.0);
    follower.setHeadingKp(2.0);
    assertEquals(10.0, follower.getTranslationKp(), 1e-9);
    assertEquals(2.0, follower.getHeadingKp(), 1e-9);
  }

  @Test
  void reset_doesNotThrow() {
    TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();
    follower.computeSpeeds(
        new HolonomicTrajectorySample(0.0, new Pose2d(1, 2, new Rotation2d()), new ChassisSpeeds()),
        new Pose2d(0, 0, new Rotation2d()));
    assertDoesNotThrow(follower::reset);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  /** Empty trajectory — {@code sampleAt} always returns empty. */
  private static final class EmptyTrajectory implements HolonomicTrajectory {
    @Override
    public Optional<HolonomicTrajectorySample> sampleAt(double timestampSeconds) {
      return Optional.empty();
    }

    @Override
    public Optional<Pose2d> getInitialPose() {
      return Optional.empty();
    }

    @Override
    public Optional<Pose2d> getFinalPose() {
      return Optional.empty();
    }

    @Override
    public double getTotalTime() {
      return 0.0;
    }
  }
}
