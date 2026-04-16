package frc.robot;

import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.util.Units;
import swervelib.math.Matter;

/**
 * Robot-wide numerical or boolean constants. All constants are organized by subsystem as inner
 * classes. Convention: constants are prefixed with lowercase 'k' followed by descriptive camelCase.
 */
public final class Constants {

  private Constants() {}

  // Robot physical constants (used for velocity limiting in swerve)
  public static final double kRobotMassKg = (148 - 20.3) * 0.453592;

  /** FRC field length in meters (54 ft 3.25 in). */
  public static final double kFieldLengthMeters = 16.541;

  /** FRC field width in meters (26 ft 11.25 in). */
  public static final double kFieldWidthMeters = 8.211;

  public static final Matter kChassis =
      new Matter(new Translation3d(0, 0, Units.inchesToMeters(8)), kRobotMassKg);

  /** Operator interface constants. */
  public static final class OI {
    public static final int kDriverControllerPort = 0;
    public static final double kDriveDeadband = 0.1;
  }

  /** Swerve drive constants. These mirror the hardware-verified YAGSL JSON configs. */
  public static final class Swerve {
    // YAGSL JSON directory name (under deploy/)
    public static final String kSwerveJsonDirectory = "swerve";

    // Maximum speeds — matches hardware-tuned values from swerve-test branch
    public static final double kMaxSpeedMetersPerSec = Units.feetToMeters(14.5);
    public static final double kMaxAngularSpeedRadPerSec = Math.PI * 2;

    // Physical dimensions — matches hardware JSON (11 inches center-to-center)
    public static final double kModuleLocationInches = 11.0;
    public static final double kWheelDiameterInches = 4.0;
    public static final double kDriveGearRatio = 6.23; // Thrifty Swerve drive ratio
    public static final double kSteerGearRatio = 25.0; // Thrifty Swerve steer ratio
  }

  /** Flywheel subsystem CAN IDs and tuning constants. */
  public static final class Flywheel {
    public static final int kLeftVortexId = 23;
    public static final int kRightVortexId = 22;
    public static final int kFrontWheelId = 15;
    public static final int kBackWheelId = 2;

    // Velocity PID — tuned on hardware
    public static final double kP = 0.00075;
    public static final double kI = 0;
    public static final double kD = 0;
    public static final double kS = 0.15;
    public static final double kV = 12.0 / 6800.0;

    // RPM presets
    public static final double kMinRpm = 2400;
    public static final double kMaxRpm = 4000;
    public static final double kReadyThreshold = 0.10; // 10% of target RPM
  }

  /** Intake subsystem CAN IDs and tuning constants. */
  public static final class Intake {
    public static final int kLeftArmId = 16;
    public static final int kRightArmId = 17;
    public static final int kWheelId = 4;

    // Position PID — tuned on hardware
    public static final double kP = 0.025;
    public static final double kD = 0;

    // Arm position limits (in encoder rotations after gear reduction)
    public static final double kArmMinRotations = (15.0 / 360.0) * (32.0 / 12.0) * (45.0 / 1.0);
    public static final double kArmMaxRotations = (110.0 / 360.0) * (32.0 / 12.0) * (45.0 / 1.0);
  }

  /** Conveyor subsystem CAN IDs. */
  public static final class Conveyor {
    public static final int kConveyorMotorId = 21;
    public static final int kSpindexerMotorId = 18;
  }

  /** LED constants. */
  public static final class LEDs {
    public static final int kLedPort = 0;
    public static final int kLedLength = 60;

    // Animation priority levels (higher = overrides lower)
    public static final int kPriorityIdle = 0;
    public static final int kPriorityDriving = 1;
    public static final int kPriorityAligning = 2;
    public static final int kPriorityAlert = 3;
  }

  /** Pathfinding and autonomous strategy constants. */
  public static final class Pathfinding {
    // Dynamic avoidance
    public static final double kOpponentInfluenceRadiusMeters = 2.0;
    public static final double kMaxRobotSpeedMps = 4.5;
    // Attractive gain (toward waypoint)
    public static final double kAttractiveGain = 1.0;
    // Repulsive gain (away from opponents)
    public static final double kRepulsiveGain = 1.5;

    // Bot Aborter — abort if opponent arrives this many seconds before robot
    public static final double kAbortTimeThresholdSeconds = 0.75;

    // Decision engine
    public static final double kClimbTimeThresholdSeconds = 15.0;
    public static final double kFuelConfidenceThreshold = 0.80;
    public static final int kFuelPersistenceFrames = 3;
    public static final int kMaxFuelDetections = 8;
  }

  /** Superstructure state machine constants. */
  public static final class Superstructure {
    // Intake wheel current threshold for game piece detection (amps)
    public static final double kGamePieceCurrentThresholdAmps = 15.0;
    // Duration vision target must be continuously valid before scoring (seconds)
    public static final double kVisionConfirmSeconds = 0.25;
    // Limelight pipeline indices
    public static final int kAprilTagPipeline = 0;
    public static final int kNeuralPipeline = 1;
    // AutoScoreCommand total timeout (seconds)
    public static final double kAutoScoreTimeoutSeconds = 5.0;
    // SCORING state auto-exit timeout: if no requestIdle() arrives within this window, the SSM
    // self-clears back to IDLE so a missed scoring command never locks the superstructure.
    public static final double kScoringTimeoutSeconds = 2.0;
  }
}
