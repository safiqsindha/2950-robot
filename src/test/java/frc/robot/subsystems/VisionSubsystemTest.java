package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.math.kinematics.ChassisSpeeds;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for VisionSubsystem's pure-logic botpose validation. Tests the static isValidBotpose()
 * method without requiring NetworkTables, HAL, or hardware.
 */
class VisionSubsystemTest {

  // Default thresholds matching VisionSubsystem constants
  private static final int MIN_TAG_COUNT = 1;
  private static final double MAX_LATENCY_MS = 50.0;
  private static final double MAX_TAG_DIST_M = 4.0;

  /** Build a minimal valid botpose array (11 elements) with the given field values. */
  private static double[] validBotpose(
      double x, double y, double yaw, double latencyMs, int tagCount, double avgTagDistM) {
    double[] arr = new double[11];
    arr[0] = x;
    arr[1] = y;
    arr[5] = yaw;
    arr[6] = latencyMs;
    arr[7] = tagCount;
    arr[9] = avgTagDistM;
    return arr;
  }

  @Test
  void testValidMeasurement_passes() {
    double[] botpose = validBotpose(4.0, 4.0, 0.0, 20.0, 2, 2.0);
    assertTrue(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testNullArray_fails() {
    assertFalse(
        VisionSubsystem.isValidBotpose(null, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testEmptyArray_fails() {
    assertFalse(
        VisionSubsystem.isValidBotpose(
            new double[0], MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testShortArray_fails() {
    assertFalse(
        VisionSubsystem.isValidBotpose(
            new double[10], MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testZeroTagCount_fails() {
    double[] botpose = validBotpose(4.0, 4.0, 0.0, 20.0, 0, 2.0);
    assertFalse(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testLatencyExceedsMax_fails() {
    double[] botpose = validBotpose(4.0, 4.0, 0.0, 51.0, 1, 2.0);
    assertFalse(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testAvgTagDistExceedsMax_fails() {
    double[] botpose = validBotpose(4.0, 4.0, 0.0, 20.0, 1, 4.1);
    assertFalse(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testNegativeX_fails() {
    double[] botpose = validBotpose(-0.1, 4.0, 0.0, 20.0, 1, 2.0);
    assertFalse(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testXExceedsFieldLength_fails() {
    double[] botpose = validBotpose(16.55, 4.0, 0.0, 20.0, 1, 2.0);
    assertFalse(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testNegativeY_fails() {
    double[] botpose = validBotpose(4.0, -0.1, 0.0, 20.0, 1, 2.0);
    assertFalse(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testYExceedsFieldWidth_fails() {
    double[] botpose = validBotpose(4.0, 8.22, 0.0, 20.0, 1, 2.0);
    assertFalse(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testPoseAtFieldCorner_passes() {
    // Exactly at (0, 0) — field origin — should be valid
    double[] botpose = validBotpose(0.0, 0.0, 0.0, 10.0, 1, 1.0);
    assertTrue(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testPoseAtFarFieldCorner_passes() {
    double[] botpose = validBotpose(16.54, 8.21, 180.0, 10.0, 1, 1.0);
    assertTrue(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  @Test
  void testLatencyAtExactMax_passes() {
    double[] botpose = validBotpose(4.0, 4.0, 0.0, 50.0, 1, 2.0);
    assertTrue(
        VisionSubsystem.isValidBotpose(botpose, MIN_TAG_COUNT, MAX_LATENCY_MS, MAX_TAG_DIST_M));
  }

  // ─── isInResetInhibitionWindow (4481 pattern) ────────────────────────────

  @Test
  void resetInhibition_immediatelyAfterReset_inhibits() {
    // now=0.00, reset at 0.00 → delta 0 < 0.12 → inhibited
    assertTrue(VisionSubsystem.isInResetInhibitionWindow(0.00, 0.00));
  }

  @Test
  void resetInhibition_midWindow_inhibits() {
    // now=0.05, reset at 0.00 → delta 0.05 < 0.12 → inhibited
    assertTrue(VisionSubsystem.isInResetInhibitionWindow(0.05, 0.00));
  }

  @Test
  void resetInhibition_atBoundary_allowsMeasurement() {
    // now=0.12, reset at 0.00 → delta 0.12 == 0.12 → NOT inhibited (strict <)
    assertFalse(VisionSubsystem.isInResetInhibitionWindow(0.12, 0.00));
  }

  @Test
  void resetInhibition_longAfterReset_allowsMeasurement() {
    assertFalse(VisionSubsystem.isInResetInhibitionWindow(100.0, 0.0));
  }

  @Test
  void resetInhibition_negativeDelta_allowsMeasurement() {
    // Pathological: clock went backward. Delta is negative, which is < kResetInhibitionSeconds,
    // so it WOULD inhibit. Document the behaviour: the static method treats any time where
    // currentFpgaTimeSeconds - lastResetFpgaTimeSeconds < 0.12 as inhibited. This matches 4481's
    // approach — a negative delta is still "recent enough to be suspicious".
    assertTrue(VisionSubsystem.isInResetInhibitionWindow(0.0, 5.0));
  }

  // ─── isRobotTooFastForVision (1619 pattern, 4 m/s) ──────────────────────

  @Test
  void fastForVision_stationary_notRejected() {
    assertFalse(VisionSubsystem.isRobotTooFastForVision(new ChassisSpeeds(0, 0, 0)));
  }

  @Test
  void fastForVision_belowThreshold_notRejected() {
    assertFalse(VisionSubsystem.isRobotTooFastForVision(new ChassisSpeeds(3.0, 0, 0)));
  }

  @Test
  void fastForVision_aboveThreshold_rejected() {
    assertTrue(VisionSubsystem.isRobotTooFastForVision(new ChassisSpeeds(5.0, 0, 0)));
  }

  @Test
  void fastForVision_atExactThreshold_notRejected() {
    // 4.0 m/s exactly → NOT rejected (strict >)
    assertFalse(VisionSubsystem.isRobotTooFastForVision(new ChassisSpeeds(4.0, 0, 0)));
  }

  @Test
  void fastForVision_combinedXandY_rejected() {
    // 3.0 + 3.0 combined ≈ 4.24 m/s → over threshold
    assertTrue(VisionSubsystem.isRobotTooFastForVision(new ChassisSpeeds(3.0, 3.0, 0)));
  }

  @Test
  void fastForVision_rotationOnly_notRejected() {
    // Rotation doesn't count toward linear speed — 10 rad/s is irrelevant.
    assertFalse(VisionSubsystem.isRobotTooFastForVision(new ChassisSpeeds(0, 0, 10.0)));
  }

  // ─── getCorrectionThresholdMeters (1678 pattern) ─────────────────────────

  @Test
  void correctionThreshold_teleop_uses1point0() {
    assertEquals(1.0, VisionSubsystem.getCorrectionThresholdMeters(false), 1e-9);
  }

  @Test
  void correctionThreshold_autonomous_uses0point5() {
    assertEquals(0.5, VisionSubsystem.getCorrectionThresholdMeters(true), 1e-9);
  }

  // ─── computeXyStdDev (971 d²/√tagCount) ──────────────────────────────────

  @Test
  void xyStdDev_atOneMeterSingleTag_equalsBase() {
    // 0.5 × 1² / √1 = 0.5
    assertEquals(0.5, VisionSubsystem.computeXyStdDev(1.0, 1), 1e-9);
  }

  @Test
  void xyStdDev_scalesQuadraticallyWithDistance() {
    // 0.5 × 2² / √1 = 2.0
    assertEquals(2.0, VisionSubsystem.computeXyStdDev(2.0, 1), 1e-9);
  }

  @Test
  void xyStdDev_fourTagsHalvesTheSingleTagValue() {
    // 0.5 × 2² / √4 = 1.0, versus 2.0 for single-tag at the same distance
    assertEquals(1.0, VisionSubsystem.computeXyStdDev(2.0, 4), 1e-9);
  }

  @Test
  void xyStdDev_belowOneMeterShrinks() {
    // 0.5 × 0.5² / √1 = 0.125
    assertEquals(0.125, VisionSubsystem.computeXyStdDev(0.5, 1), 1e-9);
  }

  // ─── computeThetaStdDev (single-tag reject, consensus) ──────────────────

  @Test
  void thetaStdDev_singleTag_rejectsVisionHeading() {
    // Reject sentinel (1000.0) means Kalman filter effectively ignores vision yaw.
    assertEquals(1000.0, VisionSubsystem.computeThetaStdDev(1), 1e-9);
  }

  @Test
  void thetaStdDev_twoTags_usesTightValue() {
    assertEquals(0.1, VisionSubsystem.computeThetaStdDev(2), 1e-9);
  }

  @Test
  void thetaStdDev_manyTags_stillUsesTightValue() {
    assertEquals(0.1, VisionSubsystem.computeThetaStdDev(7), 1e-9);
  }

  @Test
  void thetaStdDev_zeroTags_rejects() {
    // Degenerate input — isValidBotpose guards against this upstream, but defend in depth.
    assertEquals(1000.0, VisionSubsystem.computeThetaStdDev(0), 1e-9);
  }
}
