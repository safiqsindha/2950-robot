package frc.robot.subsystems;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.config.ModuleConfig;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.pathplanner.lib.path.PathConstraints;
import com.pathplanner.lib.pathfinding.LocalADStar;
import com.pathplanner.lib.pathfinding.Pathfinding;
import com.revrobotics.spark.SparkBase;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.robot.Constants;
import frc.robot.diagnostics.SparkAlertLogger;
import java.io.File;
import org.littletonrobotics.junction.Logger;
import swervelib.SwerveDrive;
import swervelib.SwerveDriveTest;
import swervelib.SwerveModule;
import swervelib.parser.SwerveParser;
import swervelib.telemetry.SwerveDriveTelemetry;
import swervelib.telemetry.SwerveDriveTelemetry.TelemetryVerbosity;

/**
 * The swerve drive subsystem. This is the ONLY class that interacts directly with YAGSL's
 * SwerveDrive. All other subsystems and commands go through this class.
 *
 * <p>YAGSL handles all motor controller instantiation, module kinematics, and odometry internally.
 * AdvantageKit logs YAGSL telemetry as a consumer (not a control-path wrapper).
 *
 * <p>Hardware: 4x NEO + SPARK MAX (drive), 4x NEO + SPARK MAX (steer), Thrifty 10-pin encoders
 * attached to SPARK MAX data port, ADIS16470 gyro.
 */
public final class SwerveSubsystem extends SubsystemBase {

  // Static init: configure telemetry before SwerveDrive is instantiated.
  // Avoids ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD SpotBugs warning.
  // HIGH in sim for AdvantageScope debugging; POSE in match to keep NT traffic bounded.
  static {
    SwerveDriveTelemetry.verbosity =
        edu.wpi.first.wpilibj.RobotBase.isSimulation()
            ? TelemetryVerbosity.HIGH
            : TelemetryVerbosity.POSE;
  }

  private final SwerveDrive swerveDrive;

  /**
   * Last commanded robot-relative speeds, stored so we can re-apply them after the physics step.
   * The maple-sim motor force pipeline applies forces in the wrong direction, so we must override
   * the physics body velocity each tick after updateOdometry() steps the sim.
   */
  private ChassisSpeeds lastCommandedSpeeds = new ChassisSpeeds();

  /**
   * Manually integrated pose for simulation, bypassing the broken physics position tracking. Null
   * until the first periodic() tick in simulation.
   */
  private Pose2d simOverridePose;

  /**
   * FPGA timestamp (seconds) of the last pose-reset call ({@link #resetOdometry} / {@link
   * #zeroGyro}). Vision consumers inhibit measurement acceptance for a short window after a reset
   * to prevent the Kalman filter from snapping back to a stale vision pose (4481 pattern).
   */
  private double lastPoseResetTimeSeconds = 0.0;

  /**
   * Fault / warning logger for every SPARK drive + steer motor inside YAGSL's modules. Populated in
   * the constructor by reflecting over {@code swerveDrive.getModules()} and registering any motor
   * whose underlying driver is a {@link SparkBase}. Ticked from {@link #periodic()}.
   */
  private final SparkAlertLogger swerveSparkAlerts = new SparkAlertLogger();

  /** Creates the swerve subsystem by parsing YAGSL JSON configuration. */
  public SwerveSubsystem() {
    File swerveJsonDir =
        new File(Filesystem.getDeployDirectory(), Constants.Swerve.kSwerveJsonDirectory);
    try {
      swerveDrive =
          new SwerveParser(swerveJsonDir).createSwerveDrive(Constants.Swerve.kMaxSpeedMetersPerSec);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create swerve drive from JSON config", e);
    }

    // Hardware-verified settings from swerve-test branch:
    // Heading correction off — only useful with absolute angle control mode
    // TODO: re-evaluate on practice bot with setHeadingCorrection(true, 0.05) —
    //       YAGSL docs contradict the above comment; feature works with velocity control too.
    swerveDrive.setHeadingCorrection(false);
    // Cosine compensation off — causes discrepancies not seen in real life when enabled
    // (this disagrees with YAGSL's default recommendation; kept because it was hardware-verified)
    swerveDrive.setCosineCompensator(false);
    // Correct for skew that worsens with angular velocity (coefficient tuned on hardware)
    swerveDrive.setAngularVelocityCompensation(true, true, 0.1);
    // Auto-sync absolute vs integrated encoder when module rests >500ms with delta >2°.
    // Cheap insurance against encoder drift over a long match. Fires only on rest, so has
    // no teleop latency impact.
    swerveDrive.setModuleEncoderAutoSynchronize(true, 2.0);
    // Reduce translational skew during rotation. Called out as a core YAGSL feature;
    // explicit > implicit even if the library would enable it by default.
    swerveDrive.setChassisDiscretization(true, 0.02);

    // Wire SysId characterization routine (pit-only; not invoked unless a dashboard button
    // triggers it). Enables kS/kV/kA measurement for drive motors via SwerveDriveTest helpers.
    SwerveDriveTest.setDriveSysIdRoutine(new SysIdRoutine.Config(), this, swerveDrive, 12.0, true);

    // In simulation, stop YAGSL's 4ms Notifier and drive odometry manually from periodic().
    // The Notifier does not fire reliably in HALSim — calling updateOdometry() from the 20ms
    // robot loop avoids missed physics steps and keeps simulated time advancing correctly.
    // stopOdometryThread() also configures SimulatedArena for 5 sub-ticks per 20ms period.
    if (edu.wpi.first.wpilibj.RobotBase.isSimulation()) {
      swerveDrive.stopOdometryThread();
    }

    // In simulation, spawn the robot at a clearly visible field position instead of (0,0)
    // which clips into the corner wall.
    if (edu.wpi.first.wpilibj.RobotBase.isSimulation()) {
      swerveDrive.resetOdometry(new Pose2d(2.0, 4.0, new Rotation2d(0)));
    }

    registerSwerveSparkAlerts();
    setupPathPlanner();
  }

  /**
   * Register every REV {@link SparkBase} in YAGSL's swerve modules (drive + angle) with the {@link
   * SparkAlertLogger}. YAGSL's {@code SwerveMotor.getMotor()} returns {@code Object} — only {@link
   * SparkBase} instances are registered; TalonFX / other hardware is silently skipped, so this is
   * safe to call regardless of vendordep mix.
   */
  private void registerSwerveSparkAlerts() {
    for (SwerveModule module : swerveDrive.getModules()) {
      Object driveMotor = module.getDriveMotor().getMotor();
      Object angleMotor = module.getAngleMotor().getMotor();
      if (driveMotor instanceof SparkBase drive) {
        swerveSparkAlerts.register(drive, "Swerve/Module" + module.moduleNumber + "/drive");
      }
      if (angleMotor instanceof SparkBase angle) {
        swerveSparkAlerts.register(angle, "Swerve/Module" + module.moduleNumber + "/angle");
      }
    }
  }

  /**
   * Configure PathPlannerLib's AutoBuilder and AD* pathfinder for runtime path planning. Uses our
   * hardware-confirmed gear ratios, wheel diameter, and module locations. The AD* pathfinder loads
   * the navigation grid from {@code deploy/pathplanner/navgrid.json}.
   */
  private void setupPathPlanner() {
    double halfTrack = Units.inchesToMeters(Constants.Swerve.kModuleLocationInches);
    double wheelRadiusM = Units.inchesToMeters(Constants.Swerve.kWheelDiameterInches / 2.0);

    // Build module config from hardware-confirmed constants.
    ModuleConfig moduleConfig =
        new ModuleConfig(
            wheelRadiusM,
            Constants.Swerve.kMaxSpeedMetersPerSec,
            1.2, // wheel coefficient of friction (carpet)
            DCMotor.getNEO(1).withReduction(Constants.Swerve.kDriveGearRatio),
            40, // drive motor current limit (amps)
            1); // one drive motor per module

    // Moment of inertia estimate for square chassis: (1/6) * m * L^2
    double sideLength = 2.0 * halfTrack;
    double moi = (1.0 / 6.0) * Constants.kRobotMassKg * sideLength * sideLength;

    RobotConfig config =
        new RobotConfig(
            Constants.kRobotMassKg,
            moi,
            moduleConfig,
            new Translation2d(halfTrack, halfTrack), // FL
            new Translation2d(halfTrack, -halfTrack), // FR
            new Translation2d(-halfTrack, halfTrack), // BL
            new Translation2d(-halfTrack, -halfTrack)); // BR

    AutoBuilder.configure(
        this::getPose,
        this::resetOdometry,
        this::getRobotVelocity,
        (speeds, feedforwards) -> driveRobotRelative(speeds),
        new PPHolonomicDriveController(
            new PIDConstants(5.0, 0.0, 0.0), // Translation PID
            new PIDConstants(5.0, 0.0, 0.0)), // Rotation PID
        config,
        this::isRedAlliance,
        this);

    // Use AD* pathfinder (loads navgrid.json from deploy/pathplanner/)
    Pathfinding.setPathfinder(new LocalADStar());
  }

  @Override
  public void periodic() {
    // In simulation, manually step odometry (and maple-sim physics) each robot loop tick.
    // YAGSL's Notifier does not fire in HALSim; stopOdometryThread() was called in the constructor.
    if (edu.wpi.first.wpilibj.RobotBase.isSimulation()) {
      // Step the physics engine (needed for encoder/gyro sim state updates).
      swerveDrive.updateOdometry();

      if (Constants.Swerve.kUseMapleSimKinematicBypass) {
        // Full physics position bypass: the maple-sim motor force pipeline applies forces in the
        // wrong direction for REV SPARK MAX / NEO modules, corrupting both velocity and position
        // during the physics step. Instead of relying on physics for pose tracking, we integrate
        // position manually from the last commanded ChassisSpeeds and override odometry each
        // tick. See MAPLE_SIM_BUG_REPORT.md for the root-cause writeup.
        double dt = 0.02; // 20ms robot loop period
        Pose2d current = simOverridePose != null ? simOverridePose : getPose();
        double heading = current.getRotation().getRadians();
        double vx = lastCommandedSpeeds.vxMetersPerSecond;
        double vy = lastCommandedSpeeds.vyMetersPerSecond;
        double omega = lastCommandedSpeeds.omegaRadiansPerSecond;
        // Robot-relative to field-relative conversion
        double dx = (vx * Math.cos(heading) - vy * Math.sin(heading)) * dt;
        double dy = (vx * Math.sin(heading) + vy * Math.cos(heading)) * dt;
        double dtheta = omega * dt;
        simOverridePose =
            new Pose2d(current.getX() + dx, current.getY() + dy, new Rotation2d(heading + dtheta));
        // Override the physics-corrupted pose and velocity
        swerveDrive.resetOdometry(simOverridePose);
        swerveDrive.getMapleSimDrive().ifPresent(sim -> sim.setRobotSpeeds(lastCommandedSpeeds));
      }
      // When the bypass is disabled, we trust maple-sim's physics. If the force-direction
      // bug is still present upstream, the robot will appear to move backwards — that's the
      // signal to re-enable the bypass until a newer maple-sim release ships a REV fix.
    }
    Pose2d logPose = getPose();
    Logger.recordOutput("Drive/Pose", logPose);
    Logger.recordOutput("Drive/GyroYaw", getHeading().getDegrees());
    Logger.recordOutput("Drive/RobotVelocity", getRobotVelocity());
    swerveDrive
        .getSimulationDriveTrainPose()
        .ifPresent(p -> Logger.recordOutput("Drive/SimGroundTruth", p));
    // REV swerve fault / warning bits → WPILib Alerts under the "SparkFaults" group.
    swerveSparkAlerts.periodic();
  }

  /**
   * Drive the robot using field-relative or robot-relative speeds.
   *
   * <p>In simulation, captures the commanded speeds (converted to robot-relative) so the maple-sim
   * kinematic bypass in {@link #periodic()} can integrate pose correctly. Without this, teleop
   * joystick commands bypass {@link #driveRobotRelative} and the sim robot stays frozen or moves
   * backwards due to the maple-sim force-direction bug.
   *
   * @param translation desired X/Y velocity in meters per second
   * @param rotation desired angular velocity in radians per second
   * @param fieldRelative whether the translation is field-relative
   */
  public void drive(Translation2d translation, double rotation, boolean fieldRelative) {
    if (edu.wpi.first.wpilibj.RobotBase.isSimulation()
        && Constants.Swerve.kUseMapleSimKinematicBypass) {
      // Mirror YAGSL's internal conversion so lastCommandedSpeeds is always robot-relative,
      // matching the format the periodic() bypass integrates.
      ChassisSpeeds speeds = new ChassisSpeeds(translation.getX(), translation.getY(), rotation);
      if (fieldRelative) {
        speeds = ChassisSpeeds.fromFieldRelativeSpeeds(speeds, getHeading());
      }
      lastCommandedSpeeds = speeds;
    }
    swerveDrive.drive(translation, rotation, fieldRelative, false);
  }

  /**
   * Drive the robot using robot-relative ChassisSpeeds. Used by Choreo trajectory following.
   *
   * <p>In simulation, the maple-sim motor-force pipeline is currently non-functional (propelling
   * forces from motor voltage produce zero velocity change in dyn4j despite correct math — root
   * cause TBD). As a bypass, we directly set the physics body velocity so the robot visibly follows
   * trajectories in HALSim. {@code swerveDrive.drive()} is still called so module encoder states
   * update for odometry and friction forces stay consistent.
   *
   * @param chassisSpeeds robot-relative chassis speeds
   */
  public void driveRobotRelative(ChassisSpeeds chassisSpeeds) {
    if (edu.wpi.first.wpilibj.RobotBase.isSimulation()
        && Constants.Swerve.kUseMapleSimKinematicBypass) {
      lastCommandedSpeeds = chassisSpeeds;
      swerveDrive.getMapleSimDrive().ifPresent(sim -> sim.setRobotSpeeds(chassisSpeeds));
    }
    swerveDrive.drive(chassisSpeeds);
  }

  /** Get the robot's current pose from odometry. */
  public Pose2d getPose() {
    return swerveDrive.getPose();
  }

  /** Reset odometry to a specific pose. */
  public void resetOdometry(Pose2d pose) {
    if (edu.wpi.first.wpilibj.RobotBase.isSimulation()) {
      simOverridePose = pose;
    }
    lastPoseResetTimeSeconds = Timer.getFPGATimestamp();
    swerveDrive.resetOdometry(pose);
  }

  /** Get the robot's current heading from the gyroscope. */
  public Rotation2d getHeading() {
    return getPose().getRotation();
  }

  /** Get the robot's current robot-relative chassis speeds. */
  public ChassisSpeeds getRobotVelocity() {
    return swerveDrive.getRobotVelocity();
  }

  /** Zero the gyroscope heading. Call this when robot is facing away from driver. */
  public void zeroGyro() {
    lastPoseResetTimeSeconds = Timer.getFPGATimestamp();
    swerveDrive.zeroGyro();
  }

  /**
   * FPGA timestamp (seconds) of the last pose-reset call. Used by {@link VisionSubsystem} to
   * inhibit measurement acceptance briefly after a reset.
   */
  public double getLastPoseResetTimeSeconds() {
    return lastPoseResetTimeSeconds;
  }

  /**
   * Attaches an {@link IntakeIOSim} to this drive's maple-sim drivetrain, so the sim intake can
   * pick up game pieces spawned in the arena. No-op if maple-sim isn't configured (e.g. on a real
   * robot). Called from {@link frc.robot.RobotContainer} during sim setup.
   */
  public void attachIntakeSimulation(IntakeIOSim intakeSim) {
    swerveDrive.getMapleSimDrive().ifPresent(intakeSim::attachArenaSimulation);
  }

  /** Lock the swerve modules in an X pattern to prevent pushing. */
  public void lock() {
    swerveDrive.lockPose();
  }

  /**
   * Set all drive motors to brake or coast idle mode.
   *
   * @param brake true for brake mode, false for coast
   */
  public void setMotorBrake(boolean brake) {
    swerveDrive.setMotorIdleMode(brake);
  }

  /**
   * Add a vision pose measurement with explicit standard deviations for the Kalman filter.
   *
   * @param pose the measured robot pose
   * @param timestampSeconds the timestamp of the measurement in seconds
   * @param stdDevs standard deviations [x meters, y meters, theta radians]
   */
  public void addVisionMeasurement(
      Pose2d pose,
      double timestampSeconds,
      edu.wpi.first.math.Matrix<edu.wpi.first.math.numbers.N3, edu.wpi.first.math.numbers.N1>
          stdDevs) {
    swerveDrive.addVisionMeasurement(pose, timestampSeconds, stdDevs);
  }

  /**
   * Create a command that pathfinds to the given pose using PathPlannerLib's AD* algorithm. The
   * robot avoids static field obstacles loaded from navgrid.json. Used by PathfindToGoalCommand and
   * FullAutonomousCommand.
   *
   * @param targetPose the desired end pose (field-relative)
   * @return a Command that drives the robot to the target, finishing when it arrives
   */
  public Command pathfindToPose(Pose2d targetPose) {
    return AutoBuilder.pathfindToPose(
        targetPose,
        new PathConstraints(
            Constants.Swerve.kMaxSpeedMetersPerSec,
            Constants.Swerve.kMaxSpeedMetersPerSec, // max accel ≈ max speed for snappy response
            Constants.Swerve.kMaxAngularSpeedRadPerSec,
            Constants.Swerve.kMaxAngularSpeedRadPerSec));
  }

  // ─── SysId characterization commands ─────────────────────────────────────
  //
  // WPILib sysid wraps a standard quasistatic + dynamic sweep that solves for
  // kS, kV, kA from the resulting motion data. YAGSL 2026 exposes both the
  // drive and the angle (steer) routines via SwerveDriveTest; we thin-wrap
  // them here so RobotContainer can bind them to SmartDashboard buttons and
  // students can run characterization without editing code.
  //
  // Usage (from a practice session):
  //   1. Place the robot on carpet with >2 m of clear runway (for drive)
  //      or on blocks with wheels off the ground (for angle).
  //   2. Enable the DS in Test mode.
  //   3. Click one of the 4 "Drive/SysId ..." or "Steer/SysId ..." buttons
  //      in Elastic / SmartDashboard.
  //   4. Export the log with the WPILib SysId tool for analysis.

  /**
   * Lazily-created SysId routine for the drive motors. Reusing the same instance across calls is
   * important — each SysIdRoutine registers itself with DataLog and creating a new one per click
   * would leak logger state.
   */
  private SysIdRoutine driveSysIdRoutine;

  /** Lazily-created SysId routine for the steer/angle motors. */
  private SysIdRoutine steerSysIdRoutine;

  private SysIdRoutine getDriveSysIdRoutine() {
    if (driveSysIdRoutine == null) {
      // `testWithSpinning = false` drives the robot forward in a straight line — use a clear
      // runway. Max applied voltage capped at 6 V to avoid wheel slip on carpet; increase if your
      // surface has good grip.
      driveSysIdRoutine =
          SwerveDriveTest.setDriveSysIdRoutine(
              new SysIdRoutine.Config(), this, swerveDrive, 6.0, false);
    }
    return driveSysIdRoutine;
  }

  private SysIdRoutine getSteerSysIdRoutine() {
    if (steerSysIdRoutine == null) {
      steerSysIdRoutine =
          SwerveDriveTest.setAngleSysIdRoutine(new SysIdRoutine.Config(), this, swerveDrive);
    }
    return steerSysIdRoutine;
  }

  /**
   * Full drive-motor SysId sweep: quasistatic forward → reverse → dynamic forward → reverse, with
   * 1-second pauses between phases. Total run-time ≈ 16 s.
   */
  public Command driveSysIdFullRoutine() {
    return SwerveDriveTest.generateSysIdCommand(
        getDriveSysIdRoutine(), /*delay*/ 1.0, /*quasiTimeout*/ 4.0, /*dynamicTimeout*/ 2.5);
  }

  /** Full steer-motor SysId sweep — place the robot on blocks first. Total run-time ≈ 16 s. */
  public Command steerSysIdFullRoutine() {
    return SwerveDriveTest.generateSysIdCommand(
        getSteerSysIdRoutine(), /*delay*/ 1.0, /*quasiTimeout*/ 4.0, /*dynamicTimeout*/ 2.5);
  }

  /** Individual drive-motor phases — bind separately if a student wants granular control. */
  public Command driveSysIdQuasistatic(SysIdRoutine.Direction direction) {
    return getDriveSysIdRoutine().quasistatic(direction);
  }

  public Command driveSysIdDynamic(SysIdRoutine.Direction direction) {
    return getDriveSysIdRoutine().dynamic(direction);
  }

  public Command steerSysIdQuasistatic(SysIdRoutine.Direction direction) {
    return getSteerSysIdRoutine().quasistatic(direction);
  }

  public Command steerSysIdDynamic(SysIdRoutine.Direction direction) {
    return getSteerSysIdRoutine().dynamic(direction);
  }

  private boolean isRedAlliance() {
    var alliance = DriverStation.getAlliance();
    return alliance.isPresent() && alliance.get() == Alliance.Red;
  }
}
