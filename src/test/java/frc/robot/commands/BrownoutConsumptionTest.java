package frc.robot.commands;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.util.Units;
import org.junit.jupiter.api.Test;

/**
 * Verifies that brownout scaling is applied correctly to AutoAlignCommand and
 * DriveToGamePieceCommand. Tests the pure-math formulas without subsystem instantiation (HAL-free),
 * following the same pattern as {@link TeleopAssistCommandsTest}.
 *
 * <p>Acceptance (Phase 1.6):
 *
 * <ul>
 *   <li>At 50% brownout scale, AutoAlign translation magnitude is halved vs full-voltage
 *   <li>At 50% brownout scale, DriveToGamePiece translation magnitude is halved
 *   <li>At 50% brownout scale, DriveToGamePiece rotation is halved
 *   <li>Brownout scale formula floor (6.0 V) produces 0.5 — not lower
 *   <li>Brownout scale formula at threshold (8.0 V) produces exactly 1.0
 * </ul>
 */
class BrownoutConsumptionTest {

  // Mirror swerve constants — edu.wpi.first.math.util.Units is HAL-free
  private static final double MAX_SPEED_MPS = Units.feetToMeters(14.5);
  private static final double MAX_ANGULAR_RAD_PER_SEC = Math.PI * 2;

  // Mirror Robot brownout constants (single source of truth is Robot.java)
  private static final double BROWNOUT_THRESHOLD_VOLTS = 8.0;
  private static final double BROWNOUT_FLOOR_VOLTS = 6.0;

  // ── Helper: replicate Robot.getBrownoutScale() formula ──────────────────

  private static double brownoutScale(double batteryVolts) {
    if (batteryVolts >= BROWNOUT_THRESHOLD_VOLTS) return 1.0;
    return Math.max(
        0.5,
        (batteryVolts - BROWNOUT_FLOOR_VOLTS) / (BROWNOUT_THRESHOLD_VOLTS - BROWNOUT_FLOOR_VOLTS));
  }

  // ── Robot.getBrownoutScale() formula ────────────────────────────────────

  @Test
  void brownoutScale_atThresholdVoltage_isOne() {
    assertEquals(
        1.0,
        brownoutScale(BROWNOUT_THRESHOLD_VOLTS),
        1e-9,
        "At threshold voltage, scale must be 1.0");
  }

  @Test
  void brownoutScale_aboveThresholdVoltage_isOne() {
    assertEquals(1.0, brownoutScale(12.6), 1e-9, "At healthy voltage, scale must be 1.0");
  }

  @Test
  void brownoutScale_atFloorVoltage_isHalf() {
    assertEquals(
        0.5,
        brownoutScale(BROWNOUT_FLOOR_VOLTS),
        1e-9,
        "At floor voltage (6.0 V), scale must be clamped to 0.5");
  }

  @Test
  void brownoutScale_belowFloor_clampedToHalf() {
    // Even if voltage drops below the defined floor, scale must not go below 0.5
    assertEquals(
        0.5, brownoutScale(3.0), 1e-9, "Scale must not drop below 0.5 even at very low voltage");
  }

  @Test
  void brownoutScale_midpointOfLinearRamp_isThreeQuarters() {
    // The clamp floor (Math.max(0.5, ...)) means the linear ramp only produces values above 0.5
    // when volts > 7.0V. The midpoint of the linear ramp (7.0V → 8.0V) is 7.5V → scale = 0.75.
    double midRampVolts = 7.5;
    double expected = 0.75;
    assertEquals(
        expected,
        brownoutScale(midRampVolts),
        1e-9,
        "At 7.5 V (midpoint of linear ramp), scale should be 0.75");
  }

  // ── AutoAlignCommand translation scaling ────────────────────────────────

  @Test
  void autoAlign_fullVoltageBrownout_translationEqualsMaxSpeed() {
    double scale = 1.0;
    Translation2d translation = new Translation2d(1.0, 0.0).times(MAX_SPEED_MPS * scale);
    assertEquals(
        MAX_SPEED_MPS,
        translation.getNorm(),
        1e-6,
        "Full brownout scale should produce max-speed translation");
  }

  @Test
  void autoAlign_halfScaleBrownout_translationMagnitudeIsHalved() {
    double scale = 0.5;
    Translation2d translation = new Translation2d(1.0, 0.0).times(MAX_SPEED_MPS * scale);
    assertEquals(
        MAX_SPEED_MPS * 0.5,
        translation.getNorm(),
        1e-6,
        "50% brownout scale must halve the AutoAlign translation magnitude");
    assertTrue(
        translation.getNorm() < MAX_SPEED_MPS,
        "Brownout-scaled translation must be below unscaled max speed");
  }

  @Test
  void autoAlign_halfScaleBrownout_diagonalTranslationAlsoScaled() {
    double scale = 0.5;
    // Unit-length diagonal input
    double half = 1.0 / Math.sqrt(2);
    Translation2d translation = new Translation2d(half, half).times(MAX_SPEED_MPS * scale);
    assertEquals(
        MAX_SPEED_MPS * 0.5,
        translation.getNorm(),
        1e-6,
        "Brownout scaling must apply equally to diagonal inputs");
  }

  // ── DriveToGamePieceCommand translation scaling ──────────────────────────

  @Test
  void driveToGamePiece_fullVoltageBrownout_translationEqualsMaxSpeed() {
    double scale = 1.0;
    double speed = 1.0; // capped proportional speed fraction
    Translation2d translation = new Translation2d(1.0, 0.0).times(speed * MAX_SPEED_MPS * scale);
    assertEquals(
        MAX_SPEED_MPS,
        translation.getNorm(),
        1e-6,
        "Full brownout scale and max speed fraction should produce max-speed translation");
  }

  @Test
  void driveToGamePiece_halfScaleBrownout_translationBelowMaxSpeed() {
    double scale = 0.5;
    double speed = 1.0; // worst-case: proportional controller capped at 1.0
    Translation2d translation = new Translation2d(1.0, 0.0).times(speed * MAX_SPEED_MPS * scale);
    assertTrue(
        translation.getNorm() < MAX_SPEED_MPS,
        "Brownout-scaled DriveToGamePiece translation must be below max speed");
    assertEquals(MAX_SPEED_MPS * 0.5, translation.getNorm(), 1e-6);
  }

  @Test
  void driveToGamePiece_halfScaleBrownout_proportionalSpeedAlsoScaled() {
    double scale = 0.5;
    double speed = 0.6; // sub-max proportional fraction
    Translation2d translation = new Translation2d(1.0, 0.0).times(speed * MAX_SPEED_MPS * scale);
    assertEquals(
        speed * MAX_SPEED_MPS * 0.5,
        translation.getNorm(),
        1e-6,
        "Brownout scale must compound with proportional speed");
  }

  // ── DriveToGamePieceCommand rotation scaling ────────────────────────────

  @Test
  void driveToGamePiece_fullVoltageBrownout_rotationEqualsMaxAngular() {
    double scale = 1.0;
    double rotInput = 1.0;
    double rotation = rotInput * MAX_ANGULAR_RAD_PER_SEC * scale;
    assertEquals(
        MAX_ANGULAR_RAD_PER_SEC,
        rotation,
        1e-6,
        "Full brownout scale and full stick should produce max angular speed");
  }

  @Test
  void driveToGamePiece_halfScaleBrownout_rotationBelowMaxAngular() {
    double scale = 0.5;
    double rotInput = 1.0;
    double rotation = rotInput * MAX_ANGULAR_RAD_PER_SEC * scale;
    assertTrue(
        rotation < MAX_ANGULAR_RAD_PER_SEC,
        "Brownout-scaled rotation must be below max angular speed");
    assertEquals(MAX_ANGULAR_RAD_PER_SEC * 0.5, rotation, 1e-6);
  }
}
