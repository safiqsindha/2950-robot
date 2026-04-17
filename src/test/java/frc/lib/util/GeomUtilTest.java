package frc.lib.util;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeomUtilTest {

  private static Pose2d pose(double x, double y) {
    return new Pose2d(x, y, new Rotation2d());
  }

  // ─── getClosestPose ────────────────────────────────────────────────────

  @Test
  void getClosestPose_emptyCandidates_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> GeomUtil.getClosestPose(pose(0, 0), List.of()));
  }

  @Test
  void getClosestPose_singleCandidate_returnsIt() {
    Pose2d only = pose(5, 5);
    assertSame(only, GeomUtil.getClosestPose(pose(0, 0), List.of(only)));
  }

  @Test
  void getClosestPose_picksNearestByEuclidean() {
    Pose2d near = pose(1, 0);
    Pose2d far = pose(10, 0);
    Pose2d mid = pose(3, 0);
    assertSame(near, GeomUtil.getClosestPose(pose(0, 0), List.of(far, near, mid)));
  }

  @Test
  void getClosestPose_tiedCandidates_returnsFirst() {
    // Stable preference: when distances are equal, the first match wins.
    Pose2d a = pose(3, 4); // dist 5
    Pose2d b = pose(-3, -4); // dist 5
    assertSame(a, GeomUtil.getClosestPose(pose(0, 0), List.of(a, b)));
  }

  // ─── getClosestFuturePose ──────────────────────────────────────────────

  @Test
  void getClosestFuturePose_stationary_behavesLikeClosestPose() {
    var speeds = new ChassisSpeeds(0, 0, 0);
    Pose2d candidate = pose(2, 0);
    assertSame(
        candidate,
        GeomUtil.getClosestFuturePose(pose(0, 0), speeds, 1.0, List.of(candidate, pose(10, 0))));
  }

  @Test
  void getClosestFuturePose_movingTowardFarTarget_picksFar() {
    // At (0,0) with (vx=5, vy=0), extrapolating 1s ahead puts us at (5,0).
    // Candidates at (2,0) and (10,0): (10,0) is closer to (5,0).
    var speeds = new ChassisSpeeds(5.0, 0, 0);
    var result =
        GeomUtil.getClosestFuturePose(pose(0, 0), speeds, 1.0, List.of(pose(2, 0), pose(10, 0)));
    assertEquals(10.0, result.getX(), 1e-9);
  }

  @Test
  void getClosestFuturePose_zeroLookahead_equalsClosestPose() {
    var speeds = new ChassisSpeeds(5.0, 5.0, 1.0);
    Pose2d a = pose(10, 0);
    Pose2d b = pose(0, 0.5);
    // At 0 lookahead, current pose is the query — b is closer to (0,0).
    assertSame(
        b, GeomUtil.getClosestFuturePose(pose(0, 0), speeds, 0.0, List.of(a, b)));
  }

  @Test
  void getClosestFuturePose_negativeLookahead_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GeomUtil.getClosestFuturePose(
                pose(0, 0), new ChassisSpeeds(), -0.1, List.of(pose(1, 0))));
  }

  @Test
  void getClosestFuturePose_emptyCandidates_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GeomUtil.getClosestFuturePose(
                pose(0, 0), new ChassisSpeeds(), 0.1, List.of()));
  }

  // ─── extrapolate (package-private) ─────────────────────────────────────

  @Test
  void extrapolate_zeroVelocity_returnsSamePose() {
    var p = new Pose2d(1, 2, new Rotation2d(0.5));
    var result = GeomUtil.extrapolate(p, new ChassisSpeeds(), 1.0);
    assertEquals(p.getX(), result.getX(), 1e-9);
    assertEquals(p.getY(), result.getY(), 1e-9);
    assertEquals(p.getRotation().getRadians(), result.getRotation().getRadians(), 1e-9);
  }

  @Test
  void extrapolate_forwardMotionWithZeroHeading_advancesX() {
    var p = new Pose2d(0, 0, new Rotation2d(0));
    var result = GeomUtil.extrapolate(p, new ChassisSpeeds(1.0, 0, 0), 2.0);
    assertEquals(2.0, result.getX(), 1e-9);
    assertEquals(0.0, result.getY(), 1e-9);
  }

  // ─── squaredDistance ───────────────────────────────────────────────────

  @Test
  void squaredDistance_correctlyComputed() {
    // (3,4) → (0,0) distance = 5, squared = 25
    assertEquals(25.0, GeomUtil.squaredDistance(new Translation2d(3, 4), new Translation2d()), 1e-9);
  }

  @Test
  void squaredDistance_identical_isZero() {
    var t = new Translation2d(5, 7);
    assertEquals(0.0, GeomUtil.squaredDistance(t, t), 1e-9);
  }
}
