package frc.robot.subsystems;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.networktables.DoubleArraySubscriber;
import edu.wpi.first.networktables.IntegerPublisher;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.diagnostics.VisionLatencyTracker;
import frc.robot.Constants;
import java.util.List;
import org.littletonrobotics.junction.Logger;

/**
 * Vision subsystem. Reads Limelight MegaTag2 botpose from NetworkTables and fuses it into the
 * swerve pose estimator via {@link SwerveSubsystem#addVisionMeasurement}.
 *
 * <p>Uses the {@code botpose_orb_wpiblue} key — this is MegaTag2's orientation-robust botpose in
 * the WPI blue-origin coordinate system. Published by Limelight firmware ≥ 2024.1.
 *
 * <p>NT4 array layout (index → meaning):
 *
 * <ul>
 *   <li>0 → X (meters, field-relative)
 *   <li>1 → Y (meters, field-relative)
 *   <li>2 → Z (meters, ignored for 2D)
 *   <li>3–5 → roll, pitch, yaw (degrees — yaw used as heading)
 *   <li>6 → total latency (ms, pipeline + capture)
 *   <li>7 → tag count
 *   <li>8 → tag span (meters)
 *   <li>9 → average tag distance (meters)
 *   <li>10 → average tag area (% of image)
 * </ul>
 */
public class VisionSubsystem extends SubsystemBase {

  // Single source of truth lives in Constants.Vision — mirrored here as package-private
  // finals so unit tests can reference them without importing Constants (stable test API).
  // See Constants.Vision for the full documentation of each value.

  // ─── Acceptance gates ─────────────────────────────────────────────────────
  private static final int kMinTagCount = Constants.Vision.kMinTagCount;
  private static final double kMaxLatencyMs = Constants.Vision.kMaxLatencyMs;
  private static final double kMaxTagDistM = Constants.Vision.kMaxTagDistM;

  // ─── Consensus rejection rules (971 / 1678 / 1619 / 4481) ────────────────
  static final double kMaxLinearSpeedForVisionMps = Constants.Vision.kMaxLinearSpeedForVisionMps;
  static final double kResetInhibitionSeconds = Constants.Vision.kResetInhibitionSeconds;
  static final double kMaxCorrectionTeleopMeters = Constants.Vision.kMaxCorrectionTeleopMeters;
  static final double kMaxCorrectionAutoMeters = Constants.Vision.kMaxCorrectionAutoMeters;

  // ─── Stddev weighting (971 d²/tagCount pattern) ──────────────────────────
  static final double kBaseXyStdDevMeters = Constants.Vision.kBaseXyStdDevMeters;
  static final double kMultiTagThetaStdDev = Constants.Vision.kMultiTagThetaStdDev;
  static final double kRejectThetaStdDev = Constants.Vision.kRejectThetaStdDev;

  private final SwerveSubsystem swerve;

  private final DoubleArraySubscriber botposeSub;
  private final DoubleArraySubscriber llpythonSub;
  private final IntegerPublisher pipelinePub;

  /** Parses the llpython neural-detection array for FUEL positions. */
  private final FuelDetectionConsumer fuelDetection = new FuelDetectionConsumer();

  private boolean hasTarget = false;
  private Pose2d lastPose = new Pose2d();

  // Timestamp (FPGA seconds) when the vision target was first confirmed valid this streak
  private double targetValidSince = Double.MAX_VALUE;

  /**
   * Rolling-window stats on accepted-frame latency. Fed via {@link #periodic()} on every frame that
   * passes the acceptance gates; ticked once per cycle to publish min/max/mean/p95 under {@code
   * VisionLatency/*}. Owning it here (rather than on {@code Robot.java}) means the only signals
   * published are for frames we actually trusted.
   */
  private final VisionLatencyTracker visionLatencyTracker = new VisionLatencyTracker();

  /** Creates the vision subsystem. */
  public VisionSubsystem(SwerveSubsystem swerve) {
    this.swerve = swerve;

    NetworkTable limelightTable = NetworkTableInstance.getDefault().getTable("limelight");

    // MegaTag2 orientation-robust botpose in WPI blue origin
    botposeSub = limelightTable.getDoubleArrayTopic("botpose_orb_wpiblue").subscribe(new double[0]);

    // Neural detector output: [numFuel, x1, y1, conf1, x2, y2, conf2, ...]
    llpythonSub = limelightTable.getDoubleArrayTopic("llpython").subscribe(new double[0]);

    // Pipeline command publisher — write pipeline index to switch Limelight mode
    pipelinePub = limelightTable.getIntegerTopic("pipeline").publish();
  }

  @Override
  public void periodic() {
    // Feed neural-detection array into the fuel/opponent consumer every loop cycle
    fuelDetection.updateFromRawArray(llpythonSub.get());
    Logger.recordOutput(
        "Vision/FuelDetectionCount", fuelDetection.getDetectedFuelPositions().size());

    double now = Timer.getFPGATimestamp();

    // Inhibit vision briefly after any pose reset to prevent re-snap (4481 pattern).
    if (isInResetInhibitionWindow(now, swerve.getLastPoseResetTimeSeconds())) {
      hasTarget = false;
      Logger.recordOutput("Vision/HasTarget", false);
      Logger.recordOutput("Vision/InhibitedAfterReset", true);
      return;
    }
    Logger.recordOutput("Vision/InhibitedAfterReset", false);

    // Skip vision when the robot is moving too fast — latency-corrupted pose (1619 pattern).
    if (isRobotTooFastForVision(swerve.getRobotVelocity())) {
      hasTarget = false;
      Logger.recordOutput("Vision/HasTarget", false);
      Logger.recordOutput("Vision/RejectedForSpeed", true);
      return;
    }
    Logger.recordOutput("Vision/RejectedForSpeed", false);

    double[] botpose = botposeSub.get();

    if (!isValidBotpose(botpose, kMinTagCount, kMaxLatencyMs, kMaxTagDistM)) {
      hasTarget = false;
      Logger.recordOutput("Vision/HasTarget", false);
      return;
    }

    double x = botpose[0];
    double y = botpose[1];
    double yawDeg = botpose[5];
    double latencyMs = botpose[6];
    int tagCount = (int) botpose[7];
    double avgTagDistM = botpose[9];

    lastPose = new Pose2d(new Translation2d(x, y), Rotation2d.fromDegrees(yawDeg));

    // Reject if vision pose disagrees with odometry beyond the per-mode threshold —
    // tighter in auto to protect trajectory following (1678 pattern).
    double distFromOdometry =
        lastPose.getTranslation().getDistance(swerve.getPose().getTranslation());
    double correctionThreshold = getCorrectionThresholdMeters(DriverStation.isAutonomous());
    if (distFromOdometry > correctionThreshold) {
      hasTarget = false;
      Logger.recordOutput("Vision/HasTarget", false);
      Logger.recordOutput("Vision/RejectedDistM", distFromOdometry);
      return;
    }

    // Timestamp: current time minus latency (convert ms → seconds)
    double timestampSeconds = now - (latencyMs / 1000.0);

    // Distance-squared xy stddev (971 pattern); theta rejected for single-tag (consensus).
    double xyStdDev = computeXyStdDev(avgTagDistM, tagCount);
    double thetaStdDev = computeThetaStdDev(tagCount);
    swerve.addVisionMeasurement(
        lastPose, timestampSeconds, VecBuilder.fill(xyStdDev, xyStdDev, thetaStdDev));

    // Start (or continue) the continuous-valid timer
    if (!hasTarget) {
      targetValidSince = now;
    }
    hasTarget = true;

    // Feed the accepted frame's latency into the rolling-window tracker. We only record here —
    // inside the accept path — so a run of rejected frames doesn't dilute the stats with what
    // the pose estimator never actually saw.
    visionLatencyTracker.record(latencyMs);
    visionLatencyTracker.periodic();

    Logger.recordOutput("Vision/HasTarget", true);
    Logger.recordOutput("Vision/BotPose", lastPose);
    Logger.recordOutput("Vision/TagCount", tagCount);
    Logger.recordOutput("Vision/LatencyMs", latencyMs);
    Logger.recordOutput("Vision/AvgTagDistM", avgTagDistM);
    Logger.recordOutput("Vision/StdDevXY", xyStdDev);
    Logger.recordOutput("Vision/StdDevTheta", thetaStdDev);
    Logger.recordOutput("Vision/CorrectionThresholdM", correctionThreshold);
    Logger.recordOutput("Vision/TargetValidDurationSec", now - targetValidSince);
  }

  /** Whether a valid MegaTag2 measurement was received this cycle. */
  public boolean hasTarget() {
    return hasTarget;
  }

  /**
   * Whether the vision target has been continuously valid for at least {@code seconds}. Used by
   * AutoScoreCommand to confirm the Limelight has a stable AprilTag lock before firing.
   *
   * @param seconds minimum continuous lock duration
   * @return true if target has been valid without interruption for at least this long
   */
  public boolean isTargetValidFor(double seconds) {
    if (!hasTarget) return false;
    return (Timer.getFPGATimestamp() - targetValidSince) >= seconds;
  }

  /**
   * Switch the Limelight to the AprilTag pipeline (teleop / scoring mode). Pipeline index from
   * {@link frc.robot.Constants.Superstructure#kAprilTagPipeline}.
   */
  public void setAprilTagPipeline() {
    pipelinePub.set(Constants.Superstructure.kAprilTagPipeline);
    Logger.recordOutput("Vision/Pipeline", "AprilTag");
  }

  /**
   * Switch the Limelight to the neural detector pipeline (autonomous game piece detection).
   * Pipeline index from {@link frc.robot.Constants.Superstructure#kNeuralPipeline}.
   */
  public void setNeuralPipeline() {
    pipelinePub.set(Constants.Superstructure.kNeuralPipeline);
    Logger.recordOutput("Vision/Pipeline", "Neural");
  }

  /**
   * Confirmed FUEL positions from the Limelight neural detector. Requires 3 consecutive frames at
   * ≥80% confidence. Capped at {@link Constants.Pathfinding#kMaxFuelDetections} entries.
   *
   * @return unmodifiable list of field-space FUEL positions (meters)
   */
  public List<Translation2d> getFuelPositions() {
    return fuelDetection.getDetectedFuelPositions();
  }

  /**
   * Opponent robot positions. Always returns an empty list — the YOLOv11n model is fuel-only and
   * has no opponent class. The avoidance layer that consumes this feed is effectively inert until a
   * real detection source is added (e.g., AprilTag-based pose comparison against known alliance
   * station positions).
   *
   * @return always an empty, unmodifiable list
   */
  public List<Translation2d> getOpponentPositions() {
    return fuelDetection.getDetectedOpponentPositions();
  }

  /**
   * Whether vision measurements should be inhibited due to a recent pose reset. Drops frames for
   * {@link #kResetInhibitionSeconds} after {@code resetOdometry}/{@code zeroGyro} so the Kalman
   * filter doesn't immediately re-snap to a stale vision pose. Package-private for testing.
   */
  static boolean isInResetInhibitionWindow(
      double currentFpgaTimeSeconds, double lastResetFpgaTimeSeconds) {
    return currentFpgaTimeSeconds - lastResetFpgaTimeSeconds < kResetInhibitionSeconds;
  }

  /**
   * Whether the robot is moving too fast for reliable vision fusion. Limelight latency combined
   * with motion blur makes fast-moving pose data unreliable. Package-private for testing.
   */
  static boolean isRobotTooFastForVision(ChassisSpeeds velocity) {
    double linearMps = Math.hypot(velocity.vxMetersPerSecond, velocity.vyMetersPerSecond);
    return linearMps > kMaxLinearSpeedForVisionMps;
  }

  /**
   * Allowed vision-vs-odometry correction threshold (meters). Tighter in auto to protect Choreo
   * trajectory following; looser in teleop so driver correction is possible. Package-private for
   * testing.
   */
  static double getCorrectionThresholdMeters(boolean isAutonomous) {
    return isAutonomous ? kMaxCorrectionAutoMeters : kMaxCorrectionTeleopMeters;
  }

  /**
   * XY stddev for the vision measurement — 971 pattern: {@code base · d² / √tagCount}. More tags +
   * closer distance = tighter (smaller) stddev. Package-private for testing.
   */
  static double computeXyStdDev(double avgTagDistM, int tagCount) {
    return kBaseXyStdDevMeters * avgTagDistM * avgTagDistM / Math.sqrt(tagCount);
  }

  /**
   * Theta stddev for the vision measurement. Multi-tag (MegaTag2 orientation-robust) gets the tight
   * value; single-tag gets the reject sentinel so the Kalman filter ignores vision yaw and the gyro
   * drives heading. Package-private for testing.
   */
  static double computeThetaStdDev(int tagCount) {
    return tagCount >= 2 ? kMultiTagThetaStdDev : kRejectThetaStdDev;
  }

  /**
   * Validates a {@code botpose_orb_wpiblue} MegaTag2 array against quality thresholds and field
   * bounds. Package-private for unit testing.
   *
   * @param botpose the raw NT4 double array (must be ≥11 elements)
   * @param minTagCount minimum number of AprilTags required
   * @param maxLatencyMs maximum allowed total latency in milliseconds
   * @param maxTagDistM maximum allowed average tag distance in meters
   * @return true if the measurement passes all checks
   */
  static boolean isValidBotpose(
      double[] botpose, int minTagCount, double maxLatencyMs, double maxTagDistM) {
    if (botpose == null || botpose.length < 11) return false;
    int tagCount = (int) botpose[7];
    double latencyMs = botpose[6];
    double avgTagDistM = botpose[9];
    if (tagCount < minTagCount || latencyMs > maxLatencyMs || avgTagDistM > maxTagDistM) {
      return false;
    }
    double x = botpose[0];
    double y = botpose[1];
    return x >= 0
        && x <= Constants.kFieldLengthMeters
        && y >= 0
        && y <= Constants.kFieldWidthMeters;
  }
}
