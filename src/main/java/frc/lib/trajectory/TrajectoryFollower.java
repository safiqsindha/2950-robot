package frc.lib.trajectory;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import java.util.Optional;

/**
 * Planner-agnostic trajectory follower — adapted from Team 4481 Rembrandts.
 *
 * <p>Given a {@link HolonomicTrajectory} and the robot's current pose + elapsed time, computes a
 * field-relative {@link ChassisSpeeds} that combines:
 *
 * <ol>
 *   <li><b>Feedforward</b>: the sample's own field-relative speeds at {@code t}.
 *   <li><b>Feedback</b>: three PID loops (x, y, heading) driving the pose error to zero.
 * </ol>
 *
 * <p>The follower itself is stateful only in the PID controllers — the current time + pose come
 * from the caller each tick. This keeps it trivially testable (no HAL, no clocks) and lets the same
 * follower instance be shared across a whole auto routine if desired.
 *
 * <p>Typical wiring:
 *
 * <pre>{@code
 * TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();
 * Command followCmd = followerCommand(trajectory, swerve::getPose, swerve::driveFieldRelative,
 *                                      swerve, timer::get, follower);
 * }</pre>
 *
 * Or wire it into your own Command:
 *
 * <pre>{@code
 * follower.follow(trajectory, swerve.getPose(), elapsedSeconds)
 *         .ifPresent(swerve::driveFieldRelative);
 * }</pre>
 *
 * <p>The class lives in {@code frc.lib} and has no robot-specific dependencies — gains flow through
 * the constructor and defaults are exposed as static finals for callers who don't need to tune.
 */
public final class TrajectoryFollower {

  /** Default translation kP — tuned for the 2026 HUB (4.5 m/s cap, 22"×22" chassis). */
  public static final double DEFAULT_TRANSLATION_KP = 4.0;

  /** Default heading kP — radians of error → rad/s of correction. */
  public static final double DEFAULT_HEADING_KP = 3.0;

  private final PIDController xController;
  private final PIDController yController;
  private final PIDController headingController;

  /**
   * Primary constructor — explicit gains.
   *
   * @param translationKp P gain applied independently on x + y (m/s per m of error)
   * @param headingKp P gain on the heading error (rad/s per rad of error)
   */
  public TrajectoryFollower(double translationKp, double headingKp) {
    this.xController = new PIDController(translationKp, 0, 0);
    this.yController = new PIDController(translationKp, 0, 0);
    this.headingController = new PIDController(headingKp, 0, 0);
    // Wrap heading so the shortest-rotation direction is always chosen.
    this.headingController.enableContinuousInput(-Math.PI, Math.PI);
  }

  /** Convenience constructor — uses the {@code DEFAULT_*} gains. */
  public static TrajectoryFollower withDefaultGains() {
    return new TrajectoryFollower(DEFAULT_TRANSLATION_KP, DEFAULT_HEADING_KP);
  }

  /**
   * Compute a field-relative {@link ChassisSpeeds} that follows the trajectory at the given time.
   *
   * @param trajectory the trajectory to follow
   * @param currentPose the robot's current field-relative pose
   * @param currentTimeSeconds seconds elapsed since the trajectory started
   * @return field-relative chassis speeds combining feedforward + PID correction, or empty if the
   *     trajectory has no sample at this time (before start / after end / empty trajectory)
   */
  public Optional<ChassisSpeeds> follow(
      HolonomicTrajectory trajectory, Pose2d currentPose, double currentTimeSeconds) {
    return trajectory
        .sampleAt(currentTimeSeconds)
        .map(sample -> computeSpeeds(sample, currentPose));
  }

  /**
   * Compute the chassis speeds for a known sample + pose without the {@link HolonomicTrajectory}
   * indirection. Useful when the caller already has a sample in hand (e.g. the Choreo auto factory
   * unwraps {@code SwerveSample} → {@link HolonomicTrajectorySample} inline) and also valuable for
   * unit tests that exercise the feedforward + feedback math directly.
   */
  public ChassisSpeeds computeSpeeds(HolonomicTrajectorySample sample, Pose2d currentPose) {
    Pose2d target = sample.pose();
    ChassisSpeeds ff = sample.fieldRelativeSpeeds();

    double xCorrection = xController.calculate(currentPose.getX(), target.getX());
    double yCorrection = yController.calculate(currentPose.getY(), target.getY());
    double headingCorrection =
        headingController.calculate(
            currentPose.getRotation().getRadians(), target.getRotation().getRadians());

    return new ChassisSpeeds(
        ff.vxMetersPerSecond + xCorrection,
        ff.vyMetersPerSecond + yCorrection,
        ff.omegaRadiansPerSecond + headingCorrection);
  }

  /**
   * Zero the internal PID integrators / derivative history. Call at the start of every trajectory
   * so stale error from the previous run doesn't leak into the new one.
   */
  public void reset() {
    xController.reset();
    yController.reset();
    headingController.reset();
  }

  // ─── Accessors for tuning / telemetry ─────────────────────────────────────

  public double getTranslationKp() {
    return xController.getP();
  }

  public double getHeadingKp() {
    return headingController.getP();
  }

  public void setTranslationKp(double kp) {
    xController.setP(kp);
    yController.setP(kp);
  }

  public void setHeadingKp(double kp) {
    headingController.setP(kp);
  }
}
