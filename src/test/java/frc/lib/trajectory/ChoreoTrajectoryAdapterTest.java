package frc.lib.trajectory;

import static org.junit.jupiter.api.Assertions.*;

import choreo.trajectory.SwerveSample;
import choreo.trajectory.Trajectory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChoreoTrajectoryAdapter}. Builds a hand-crafted Choreo {@link Trajectory}
 * with three samples and verifies the adapter maps every query correctly.
 */
class ChoreoTrajectoryAdapterTest {

  /**
   * Builds a sample at time {@code t} with a pose at {@code (t, 2·t)} and matching constant
   * velocities {@code (vx=1, vy=2)}. Choreo's {@code SwerveSample.interpolate} uses the prior
   * sample's (v, a) to kinematically integrate intermediate poses, so for linearly-interpolated
   * test results the velocities must be consistent with the pose trajectory.
   */
  private static SwerveSample sampleAt(double t) {
    return new SwerveSample(
        t, // timestamp
        t, // x
        2 * t, // y
        0.0, // heading
        1.0, // vx — matches dx/dt of this path
        2.0, // vy — matches dy/dt of this path (y = 2t)
        0.0, // omega
        0.0, // ax
        0.0, // ay
        0.0, // alpha
        new double[] {0, 0, 0, 0},
        new double[] {0, 0, 0, 0});
  }

  private static Trajectory<SwerveSample> buildTrajectory() {
    return new Trajectory<>(
        "test", List.of(sampleAt(0.0), sampleAt(0.5), sampleAt(1.0)), List.of(), List.of());
  }

  @Test
  void getTotalTime_returnsLastSampleTimestamp() {
    var adapter = new ChoreoTrajectoryAdapter(buildTrajectory());
    assertEquals(1.0, adapter.getTotalTime(), 1e-9);
  }

  @Test
  void getInitialPose_returnsFirstSamplePose() {
    var adapter = new ChoreoTrajectoryAdapter(buildTrajectory());
    var pose = adapter.getInitialPose();
    assertTrue(pose.isPresent());
    assertEquals(0.0, pose.get().getX(), 1e-9);
    assertEquals(0.0, pose.get().getY(), 1e-9);
  }

  @Test
  void getFinalPose_returnsLastSamplePose() {
    var adapter = new ChoreoTrajectoryAdapter(buildTrajectory());
    var pose = adapter.getFinalPose();
    assertTrue(pose.isPresent());
    assertEquals(1.0, pose.get().getX(), 1e-9);
    assertEquals(2.0, pose.get().getY(), 1e-9);
  }

  @Test
  void sampleAt_midTrajectory_returnsInterpolatedSample() {
    var adapter = new ChoreoTrajectoryAdapter(buildTrajectory());
    Optional<HolonomicTrajectorySample> sample = adapter.sampleAt(0.5);
    assertTrue(sample.isPresent());
    assertEquals(0.5, sample.get().pose().getX(), 1e-6);
    assertEquals(1.0, sample.get().pose().getY(), 1e-6);
    assertEquals(1.0, sample.get().fieldRelativeSpeeds().vxMetersPerSecond, 1e-9);
    assertEquals(2.0, sample.get().fieldRelativeSpeeds().vyMetersPerSecond, 1e-9);
  }

  @Test
  void sampleAt_zero_returnsInitialSample() {
    var adapter = new ChoreoTrajectoryAdapter(buildTrajectory());
    Optional<HolonomicTrajectorySample> sample = adapter.sampleAt(0.0);
    assertTrue(sample.isPresent());
    assertEquals(0.0, sample.get().timestampSeconds(), 1e-9);
  }

  @Test
  void emptyTrajectory_getInitialPoseIsEmpty() {
    var empty = new Trajectory<SwerveSample>("empty", List.of(), List.of(), List.of());
    var adapter = new ChoreoTrajectoryAdapter(empty);
    assertFalse(adapter.getInitialPose().isPresent());
    assertFalse(adapter.getFinalPose().isPresent());
  }

  @Test
  void mirrorFlag_isPropagatedThroughEachCall() {
    // Verify the adapter wires mirrorForRedAlliance into downstream Choreo calls. Constructing
    // a trajectory and inspecting the flipped() output is easier than reflecting on flags;
    // just confirm no exception + that mirrored adapter yields DIFFERENT poses from unmirrored.
    Trajectory<SwerveSample> trajectory = buildTrajectory();
    var unmirrored = new ChoreoTrajectoryAdapter(trajectory, false);
    var mirrored = new ChoreoTrajectoryAdapter(trajectory, true);
    assertNotEquals(
        unmirrored.getInitialPose().orElseThrow(),
        mirrored.getInitialPose().orElseThrow(),
        "Mirrored initial pose must differ from unmirrored (field is flipped across Y axis)");
  }

  @Test
  void constructorWithoutMirrorFlag_defaultsToFalse() {
    var explicit = new ChoreoTrajectoryAdapter(buildTrajectory(), false);
    var implicit = new ChoreoTrajectoryAdapter(buildTrajectory());
    assertEquals(
        explicit.getInitialPose().orElseThrow(),
        implicit.getInitialPose().orElseThrow(),
        "No-flag ctor must equal explicit-false");
  }
}
