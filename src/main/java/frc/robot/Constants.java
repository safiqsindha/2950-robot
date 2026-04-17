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

  /**
   * FRC field length in meters — 2026 REBUILT WELDED variant, per WPILib
   * {@code 2026-rebuilt-welded.json} ({@code AprilTagFields.k2026RebuiltWelded}).
   * AndyMark variant is 16.518 m; teams running the AndyMark field layout should override.
   */
  public static final double kFieldLengthMeters = 16.541;

  /**
   * FRC field width in meters — 2026 REBUILT WELDED variant, per WPILib
   * {@code 2026-rebuilt-welded.json}. AndyMark variant is 8.043 m.
   *
   * <p>Historical note: prior value was 8.211 m (2024 Crescendo width) — incorrect for every
   * season since. Migration PR #84 corrected to 2026 WELDED. Confirmed against the authoritative
   * {@code apriltag/src/main/native/resources/edu/wpi/first/apriltag/2026-rebuilt-welded.json}
   * in {@code wpilibsuite/allwpilib}.
   */
  public static final double kFieldWidthMeters = 8.069;

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

    /**
     * Kinematic-bypass toggle for maple-sim. When {@code true}, {@code SwerveSubsystem.periodic()}
     * manually integrates pose from commanded ChassisSpeeds in simulation, overriding the
     * physics-driven pose + velocity. This bypass exists because maple-sim 0.4.0-beta applies
     * propelling forces in the wrong direction for REV SPARK MAX + NEO swerve modules — see
     * {@code MAPLE_SIM_BUG_REPORT.md} for the full writeup.
     *
     * <p>When {@code false}, we trust maple-sim's physics pipeline — the robot will only move if
     * the force bug is fixed upstream. Flip this to {@code false} on a practice-bot session when
     * testing whether a new maple-sim release has addressed the REV sign convention.
     *
     * <p>As of 2026-04-17, maple-sim's release notes (through 0.4.0-beta) do not mention a REV
     * motor-direction fix, so the default remains {@code true}.
     */
    public static final boolean kUseMapleSimKinematicBypass = true;
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

    /**
     * Ball exit speed from the flywheel (m/s). Used by {@link frc.robot.Helper#rpmFromMeters}
     * for effective-distance adjustment in the 971 3-iter fixed-point shoot-on-the-fly.
     */
    public static final double kBallExitVelocityMps = 12.0;

    /**
     * Lower feed-wheel retract percent — used by Flywheel*Command classes while the flywheel is
     * spinning up to keep the ball from feeding prematurely. Was duplicated in 3 commands as the
     * literal {@code -0.1}; extracted during audit cleanup.
     */
    public static final double kLowerRetractPercent = -0.1;

    /**
     * Maximum rate of change of the flywheel RPM setpoint (RPM / s). Feeds a {@link
     * frc.lib.control.LinearProfile} inside {@code Flywheel.periodic()} so the closed-loop velocity
     * reference doesn't step change when commands call {@code setTargetRpm}. 8000 RPM/s is
     * effectively transparent for the real Vortex pair (which physically accelerates ~7000 RPM/s
     * from rest) but strips the discontinuity the PID+FF layer sees, which lowers peak current and
     * helps the brownout budget.
     *
     * <p>Live-tunable at runtime via the {@code Flywheel/kMaxAccelRpmPerSec} AdvantageScope /
     * NetworkTables entry.
     */
    public static final double kMaxAccelRpmPerSec = 8000.0;
  }

  /** Vision pose-estimator tuning constants — extracted from hardcoded values in VisionSubsystem. */
  public static final class Vision {
    /** Minimum tags visible before we trust the MegaTag2 measurement. */
    public static final int kMinTagCount = 1;

    /** Maximum total latency before we discard the measurement (ms). */
    public static final double kMaxLatencyMs = 50.0;

    /** Maximum average tag distance before we discount heavily (m). */
    public static final double kMaxTagDistM = 4.0;

    /** Skip vision when robot linear speed exceeds this — Limelight latency corrupts fast poses. */
    public static final double kMaxLinearSpeedForVisionMps = 4.0;

    /** Inhibit vision for this long after resetOdometry / zeroGyro (4481 pattern). */
    public static final double kResetInhibitionSeconds = 0.12;

    /** Max vision-vs-odometry drift — tighter in auto (protects Choreo). */
    public static final double kMaxCorrectionTeleopMeters = 1.0;

    public static final double kMaxCorrectionAutoMeters = 0.5;

    /** Base xy stddev at 1 m; multi-tag divides by sqrt(tagCount). 971 d² pattern. */
    public static final double kBaseXyStdDevMeters = 0.5;

    /** Theta stddev for multi-tag (MegaTag2 heading is accurate when ≥ 2 tags). */
    public static final double kMultiTagThetaStdDev = 0.1;

    /** Sentinel that effectively rejects vision yaw — trust gyro instead. 971/1619/4481 consensus. */
    public static final double kRejectThetaStdDev = 1000.0;
  }

  /** PID gains shared across heading-based alignment commands. */
  public static final class Align {
    /**
     * Proportional gain on the heading / horizontal-offset PID used by both AutoAlignCommand and
     * FlywheelAim. Was duplicated as {@code 0.05}; extracted during audit cleanup.
     */
    public static final double kHeadingKP = 0.05;
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

    /**
     * Maximum rate of change of the intake arm position setpoint (rotations / s). Feeds a
     * {@link frc.lib.control.LinearProfile} in {@code Intake.periodic()} so the onboard PID sees a
     * smooth ramp when a command calls {@code updateTargetAngle}. 100 rot/s traverses the full
     * ~32-rotation travel range in ~0.3 s — faster than the arm can mechanically accelerate, so
     * the rate limit is effectively transparent but strips the step-discontinuity that would
     * otherwise cause a peak current spike.
     *
     * <p>Live-tunable at runtime via {@code Intake/kMaxArmAccelRotPerSec} on NT.
     */
    public static final double kMaxArmAccelRotPerSec = 100.0;

    /**
     * Maximum rate of change of the intake wheel percent output (percent / s). Feeds an
     * {@link frc.lib.control.AsymmetricRateLimiter} in {@code Intake.periodic()} — ramps UP at
     * this rate but snaps to the commanded value when going to a smaller magnitude, so a panic
     * interrupt's {@code setWheel(0)} is instant. 4.0 /s reaches full output in 0.25 s, slow
     * enough to smooth the SPARK MAX's input spike but fast enough that no human-driven trigger
     * pull feels laggy.
     *
     * <p>Live-tunable via {@code Intake/kMaxWheelAccelPerSec} on NT.
     */
    public static final double kMaxWheelAccelPerSec = 4.0;
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
    // Dynamic avoidance — source the defaults from frc.lib.pathfinding so the library stays the
    // single source of truth. Override here if the robot needs non-default tuning; otherwise edit
    // `DynamicAvoidanceLayer.DEFAULT_*` and both the library callers and this block move together.
    public static final double kOpponentInfluenceRadiusMeters =
        frc.lib.pathfinding.DynamicAvoidanceLayer.DEFAULT_OPPONENT_INFLUENCE_RADIUS_M;
    public static final double kMaxRobotSpeedMps =
        frc.lib.pathfinding.DynamicAvoidanceLayer.DEFAULT_MAX_ROBOT_SPEED_MPS;
    public static final double kAttractiveGain =
        frc.lib.pathfinding.DynamicAvoidanceLayer.DEFAULT_ATTRACTIVE_GAIN;
    public static final double kRepulsiveGain =
        frc.lib.pathfinding.DynamicAvoidanceLayer.DEFAULT_REPULSIVE_GAIN;

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

    /**
     * INTAKING state auto-exit timeout — if no game piece is detected within this window, fall
     * back to IDLE so a missed release of the intake button doesn't leave the state machine
     * permanently fishing. 6 s matches the practice-session default "if it didn't catch by now,
     * reset and try again."
     */
    public static final double kIntakingTimeoutSeconds = 6.0;

    /**
     * STAGING state auto-exit timeout — if score wasn't requested within this window the piece
     * stays staged but the SSM doesn't flag it as "actively waiting to score"; a stale STAGING
     * can confuse downstream LED / dashboard indicators. 10 s is generous — a normal auto
     * workflow acquires + scores in 2-3 s.
     */
    public static final double kStagingTimeoutSeconds = 10.0;
  }

  /**
   * Autonomous-routine timing constants. Previously lived as magic literals inside {@code
   * ChoreoAutoCommand} (3.0, 1.5, 3000, 2.5) — extracted during audit follow-up so a future
   * re-tune changes one place, not nine.
   */
  public static final class Autonomous {
    /** Shoot-cycle timeout per scoring attempt (seconds). */
    public static final double kShootTimeoutSeconds = 3.0;

    /** How long before the end of a trajectory to start spinning up the flywheel (seconds). */
    public static final double kFlywheelSpinupLeadSeconds = 1.5;

    /** Default RPM for static-shot presets in auto routines. */
    public static final double kAutoStaticShotRpm = 3000;

    /** Duration to hold a static-shot spin-up during trajectory approach (seconds). */
    public static final double kStaticSpinupDurationSeconds = 2.5;

    // ── AutoScoreCommand (vision-driven score) ─────────────────────────────
    /** Cap on how long the feed-shot sub-command runs once the flywheel is at speed (s). */
    public static final double kAutoScoreFeedShotTimeoutSeconds = 2.0;

    /** Error-flash LED duration on AutoScore timeout (s). */
    public static final double kAutoScoreErrorFlashSeconds = 1.5;

    // ── "Leave Only (Raw)" sim-smoke fallback ──────────────────────────────
    /** Pre-drive pause so sim has time to initialise before "Leave Only (Raw)" drives (s). */
    public static final double kLeaveOnlyRawDriveWaitSeconds = 3.0;

    /** Forward speed for the raw fallback leave (m/s). */
    public static final double kLeaveOnlyRawDriveSpeedMps = 1.0;

    /** Drive duration for the raw fallback leave (s). */
    public static final double kLeaveOnlyRawDriveDurationSeconds = 2.0;

    // ── Safe Mode (no vision, dead-reckoning) ──────────────────────────────
    /** Forward speed during Safe Mode's dead-reckoning leave (m/s). */
    public static final double kSafeModeDriveSpeedMps = 1.0;

    /** Duration of Safe Mode's forward drive (s) — ~2 m at 1 m/s. */
    public static final double kSafeModeDriveDurationSeconds = 2.0;

    /** How long Safe Mode holds the flywheel on the blind preload shot (s). */
    public static final double kSafeModeSpinupDurationSeconds = 3.0;

    /** Blind-shot RPM used by Safe Mode when no vision target exists. */
    public static final double kSafeModeStaticShotRpm = 2800;
  }
}
