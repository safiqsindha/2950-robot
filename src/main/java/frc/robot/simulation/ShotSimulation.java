package frc.robot.simulation;

import static edu.wpi.first.units.Units.Degrees;
import static edu.wpi.first.units.Units.Meters;
import static edu.wpi.first.units.Units.MetersPerSecond;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.RobotBase;
import frc.robot.Constants;
import swervelib.simulation.ironmaple.simulation.SimulatedArena;
import swervelib.simulation.ironmaple.simulation.seasonspecific.rebuilt2026.RebuiltFuelOnFly;
import org.littletonrobotics.junction.Logger;

/**
 * Spawns {@link RebuiltFuelOnFly} projectiles in maple-sim when the real robot fires a shot.
 * Lets us regression-test the Lagrange RPM curve, moving-shot compensation, and aim code in sim
 * rather than only in practice.
 *
 * <p>This is pure sim infrastructure — every entry point short-circuits via
 * {@link RobotBase#isSimulation()} so calling it from production paths is safe but no-op on a
 * real robot.
 *
 * <p>Adapted from maple-sim's projectile-simulation docs (Shenzhen Robotics Alliance,
 * {@code maple-sim} v0.4.0-beta). Note: maple-sim projectile physics is gravity-only — no drag or
 * spin — so don't use sim tuning as a substitute for on-hardware calibration. This simulator is
 * for verifying <em>aim logic</em>, not shot ballistics.
 *
 * <p>Log keys:
 *
 * <ul>
 *   <li>{@code Sim/Shots/Fired} — cumulative projectiles spawned
 *   <li>{@code Sim/Shots/Scored} — cumulative hits on the configured target
 *   <li>{@code Sim/Shots/LastHitFlag} — {@code true} for one tick when a shot scores
 * </ul>
 */
public final class ShotSimulation {

  /**
   * REBUILT Hub target position in field coordinates (blue alliance origin). Matches the
   * calibration table in {@link Constants.Flywheel}.
   */
  private static final Translation3d DEFAULT_HUB_POSITION =
      new Translation3d(0.25, Constants.kFieldWidthMeters / 2.0, 2.3);

  /** Half-extent hit-box around the hub in x, y, z (meters). */
  private static final Translation3d DEFAULT_HUB_TOLERANCE = new Translation3d(0.5, 1.2, 0.3);

  /**
   * Typical flywheel exit speed at {@code kMaxRpm}. Used to scale RPM → m/s. Tune on-hardware
   * separately from this constant.
   */
  private static final double EXIT_SPEED_AT_MAX_RPM_MPS = Constants.Flywheel.kBallExitVelocityMps;

  /** Initial ball height at launch (shooter lip above floor). */
  private static final double LAUNCH_HEIGHT_METERS = 0.45;

  /** Shooter pitch (elevation angle). Degrees above horizontal. */
  private static final double LAUNCH_PITCH_DEGREES = 55.0;

  /** Shooter horizontal offset from robot center (+x = forward in robot frame). */
  private static final Translation2d SHOOTER_OFFSET_ON_ROBOT = new Translation2d(0.2, 0.0);

  /** Minimum time between rate-limited shots (seconds). 2 shots/second is realistic for a feed. */
  static final double kShotIntervalSeconds = 0.5;

  private int shotsFired = 0;
  private int shotsScored = 0;
  private boolean lastHitFlag = false;
  private double lastFireTimeSec = Double.NEGATIVE_INFINITY;

  /**
   * Fires a projectile using the robot's current pose + velocity and a commanded flywheel RPM.
   * No-op on a real robot.
   *
   * @param robotPose current robot pose (field-relative)
   * @param fieldRelativeSpeeds current chassis velocity (field-relative)
   * @param launchRpm commanded flywheel RPM
   */
  public void fire(Pose2d robotPose, ChassisSpeeds fieldRelativeSpeeds, double launchRpm) {
    if (!RobotBase.isSimulation()) {
      return;
    }
    shotsFired++;
    lastHitFlag = false;
    double launchSpeedMps = rpmToExitSpeedMps(launchRpm);
    int shotNumber = shotsFired;

    RebuiltFuelOnFly projectile =
        new RebuiltFuelOnFly(
            robotPose.getTranslation(),
            SHOOTER_OFFSET_ON_ROBOT,
            fieldRelativeSpeeds,
            robotPose.getRotation(),
            Meters.of(LAUNCH_HEIGHT_METERS),
            MetersPerSecond.of(launchSpeedMps),
            Degrees.of(LAUNCH_PITCH_DEGREES));
    projectile.withTargetPosition(() -> DEFAULT_HUB_POSITION);
    projectile.withTargetTolerance(DEFAULT_HUB_TOLERANCE);
    projectile.withHitTargetCallBack(() -> onHit(shotNumber));

    SimulatedArena.getInstance().addGamePieceProjectile(projectile);
    Logger.recordOutput("Sim/Shots/Fired", shotsFired);
  }

  /** Callback for scored shots. Package-private visibility for testing. */
  void onHit(int shotNumber) {
    shotsScored++;
    lastHitFlag = true;
    Logger.recordOutput("Sim/Shots/Scored", shotsScored);
    Logger.recordOutput("Sim/Shots/LastHitShotNumber", shotNumber);
  }

  /**
   * Called from {@code Robot.simulationPeriodic()} to update the transient hit flag (true for one
   * tick after a successful shot, then auto-reset). Safe to call on real robots — no-ops.
   */
  public void periodic() {
    if (!RobotBase.isSimulation()) {
      return;
    }
    Logger.recordOutput("Sim/Shots/LastHitFlag", lastHitFlag);
    lastHitFlag = false;
  }

  /**
   * Rate-limited firing hook for periodic wiring. Call each robot loop with the current robot
   * pose, field-relative chassis velocity, commanded flywheel RPM, and whether the flywheel is at
   * speed. Spawns one projectile per {@link #kShotIntervalSeconds} while at-speed; otherwise
   * no-ops.
   *
   * <p>On a real robot (non-simulation) this method returns immediately. Safe to call
   * unconditionally from {@code Robot.simulationPeriodic}.
   *
   * @return {@code true} if a projectile was spawned this tick
   */
  public boolean tryFireRateLimited(
      Pose2d robotPose,
      ChassisSpeeds fieldRelativeSpeeds,
      double launchRpm,
      boolean atSpeed,
      double currentTimeSec) {
    if (!RobotBase.isSimulation()) {
      return false;
    }
    if (!shouldFireNow(atSpeed, currentTimeSec, lastFireTimeSec)) {
      return false;
    }
    lastFireTimeSec = currentTimeSec;
    fire(robotPose, fieldRelativeSpeeds, launchRpm);
    return true;
  }

  /**
   * Pure rate-limit predicate — extracted from {@link #tryFireRateLimited} for unit testability.
   * Package-private. Returns {@code true} iff the flywheel is at speed AND at least {@link
   * #kShotIntervalSeconds} has passed since the last fire.
   */
  static boolean shouldFireNow(
      boolean atSpeed, double currentTimeSec, double lastFireTimeSec) {
    if (!atSpeed) {
      return false;
    }
    return currentTimeSec - lastFireTimeSec >= kShotIntervalSeconds;
  }

  /** Cumulative projectiles fired this session. */
  public int shotsFired() {
    return shotsFired;
  }

  /** Cumulative hits on the configured target. */
  public int shotsScored() {
    return shotsScored;
  }

  /**
   * Converts commanded RPM to ball exit speed in m/s. Linear interp from 0 to {@code kMaxRpm}
   * (maps to {@link #EXIT_SPEED_AT_MAX_RPM_MPS}). Package-private for testing.
   */
  static double rpmToExitSpeedMps(double rpm) {
    if (Constants.Flywheel.kMaxRpm <= 0) {
      return 0.0;
    }
    return Math.max(0.0, rpm / Constants.Flywheel.kMaxRpm * EXIT_SPEED_AT_MAX_RPM_MPS);
  }
}
