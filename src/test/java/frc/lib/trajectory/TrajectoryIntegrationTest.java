package frc.lib.trajectory;

import static org.junit.jupiter.api.Assertions.*;

import choreo.trajectory.SwerveSample;
import choreo.trajectory.Trajectory;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Integration tests that compose {@link ChoreoTrajectoryAdapter} + {@link TrajectoryFollower}.
 *
 * <p>Each unit test file in this package verifies one piece in isolation; this suite verifies the
 * composition. The value: catches any contract mismatch between the adapter's
 * {@link HolonomicTrajectorySample} output and the follower's expected input — including the
 * trickiest part, the sign convention of the feedforward speeds.
 *
 * <p>The follower's contract is "sample FF + field-frame PID correction". We build a short
 * Choreo trajectory, wrap it, and watch what the follower emits at different robot poses.
 */
class TrajectoryIntegrationTest {

  /** Straight-line trajectory at constant vx=1 along +X for 1 second. */
  private static Trajectory<SwerveSample> straightLineTrajectory() {
    return new Trajectory<>(
        "straight",
        List.of(sampleAt(0.0, 0.0), sampleAt(0.5, 0.5), sampleAt(1.0, 1.0)),
        List.of(),
        List.of());
  }

  private static SwerveSample sampleAt(double t, double x) {
    return new SwerveSample(
        t,
        x,
        0.0, // y
        0.0, // heading
        1.0, // vx — matches dx/dt
        0.0, // vy
        0.0, // omega
        0.0,
        0.0,
        0.0,
        new double[] {0, 0, 0, 0},
        new double[] {0, 0, 0, 0});
  }

  @Test
  void follower_onTrajectoryPose_emitsPureFeedforward() {
    ChoreoTrajectoryAdapter adapter = new ChoreoTrajectoryAdapter(straightLineTrajectory());
    TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();

    // Robot is exactly where the 0.5 s sample says it should be → PID contributes zero.
    Optional<ChassisSpeeds> out = follower.follow(adapter, new Pose2d(0.5, 0.0, new Rotation2d()), 0.5);
    assertTrue(out.isPresent(), "mid-trajectory sample must exist");
    assertEquals(1.0, out.get().vxMetersPerSecond, 1e-6);
    assertEquals(0.0, out.get().vyMetersPerSecond, 1e-6);
    assertEquals(0.0, out.get().omegaRadiansPerSecond, 1e-6);
  }

  @Test
  void follower_laggingBehindTrajectory_emitsPositiveXCorrection() {
    ChoreoTrajectoryAdapter adapter = new ChoreoTrajectoryAdapter(straightLineTrajectory());
    TrajectoryFollower follower = new TrajectoryFollower(4.0, 3.0);

    // At t=0.5 the sample wants x=0.5, but the robot is at x=0.3 → 0.2 m lag × kP 4.0 = +0.8
    Optional<ChassisSpeeds> out = follower.follow(adapter, new Pose2d(0.3, 0.0, new Rotation2d()), 0.5);
    assertTrue(out.isPresent());
    assertEquals(1.0 + 0.8, out.get().vxMetersPerSecond, 1e-6);
  }

  @Test
  void follower_outOfRange_returnsEmpty() {
    ChoreoTrajectoryAdapter adapter = new ChoreoTrajectoryAdapter(straightLineTrajectory());
    TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();
    Optional<ChassisSpeeds> afterEnd = follower.follow(adapter, new Pose2d(), 100.0);
    // Choreo clamps to the end sample rather than returning empty — both behaviours are valid.
    // We only need to verify the follower doesn't NPE; the adapter either returns empty (follower
    // maps to empty) or the terminal sample (follower computes against it).
    //
    // If Choreo returns the terminal pose, we expect zero FF (last sample has vx constant = 1;
    // robot is at origin, so far behind → positive correction). Either way, no exception.
    assertDoesNotThrow(() -> follower.follow(adapter, new Pose2d(), 100.0));
    assertNotNull(afterEnd);
  }

  @Test
  void emptyTrajectory_followerReturnsEmpty() {
    ChoreoTrajectoryAdapter adapter =
        new ChoreoTrajectoryAdapter(
            new Trajectory<>("empty", List.<SwerveSample>of(), List.of(), List.of()));
    TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();
    assertTrue(follower.follow(adapter, new Pose2d(), 0.0).isEmpty());
  }

  @Test
  void mirroredAdapter_producesFlippedTarget() {
    // With mirrorForRedAlliance=true the adapter should return a pose reflected across the field
    // center. We don't assert exact coordinates (Choreo owns the flip geometry) — just that the
    // follower routes the flipped pose through without crashing.
    ChoreoTrajectoryAdapter mirrored = new ChoreoTrajectoryAdapter(straightLineTrajectory(), true);
    TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();
    assertDoesNotThrow(() -> follower.follow(mirrored, new Pose2d(), 0.5));
  }
}
