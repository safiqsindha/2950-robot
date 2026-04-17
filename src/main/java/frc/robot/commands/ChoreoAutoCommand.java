package frc.robot.commands;

import choreo.auto.AutoFactory;
import choreo.auto.AutoRoutine;
import choreo.auto.AutoTrajectory;
import choreo.trajectory.SwerveSample;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.ConditionalCommand;
import frc.lib.trajectory.HolonomicTrajectorySample;
import frc.lib.trajectory.TrajectoryFollower;
import frc.robot.commands.flywheel.FlywheelAutoFeed;
import frc.robot.commands.flywheel.FlywheelStatic;
import frc.robot.subsystems.Conveyor;
import frc.robot.subsystems.Flywheel;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.SuperstructureStateMachine;
import frc.robot.subsystems.SwerveSubsystem;
import org.littletonrobotics.junction.Logger;

/**
 * Factory for Choreo trajectory-following autonomous routines.
 *
 * <p>Architecture notes:
 *
 * <ul>
 *   <li>Choreo is the sole trajectory pipeline — do NOT use PathPlannerLib.
 *   <li>Trajectories are created in the Choreo desktop app and exported to {@code
 *       src/main/deploy/choreo/} as {@code .traj} files.
 *   <li>All trajectories are authored on the blue-alliance side. {@code useAllianceFlipping = true}
 *       automatically mirrors them for red alliance.
 *   <li>The {@link AutoFactory} controller consumes a {@link SwerveSample} (not raw {@link
 *       edu.wpi.first.math.kinematics.ChassisSpeeds}) — call {@code sample.getChassisSpeeds()} to
 *       unwrap.
 * </ul>
 *
 * <p>To add a new path: create it in the Choreo desktop app, save to {@code
 * src/main/deploy/choreo/myPath.traj}, then add a routine method below and register it in {@code
 * RobotContainer.configureAutonomous()}.
 *
 * <p>Event markers added in the Choreo GUI (e.g. {@code "spinup"}, {@code "shoot"}) are bound to
 * commands via {@link AutoTrajectory#atTime(String)}.
 */
public final class ChoreoAutoCommand {

  // ─── Trajectory file names (must match .traj filenames without extension) ───
  /** Drive straight off the starting line (~2 m forward). */
  public static final String TRAJ_LEAVE_START = "leaveStart";

  /**
   * Drive from HUB scoring position to nearest FUEL intake.
   *
   * <p>Constant name retains legacy {@code REEF_TO_STATION} (and the value
   * {@code "reefToStation"}) because it must match the on-disk Choreo {@code .traj}
   * filename. The .traj files will be renamed when the 2026 paths are re-authored in
   * Choreo desktop (migration step 5); this constant will be renamed in lock-step.
   */
  public static final String TRAJ_REEF_TO_STATION = "reefToStation";

  /**
   * Drive from FUEL intake back to HUB scoring position.
   *
   * <p>Constant name retains legacy {@code STATION_TO_REEF} — see note on
   * {@link #TRAJ_REEF_TO_STATION}.
   */
  public static final String TRAJ_STATION_TO_REEF = "stationToReef";

  private ChoreoAutoCommand() {}

  // ═══════════════════════════════════════════════════════════════════════════
  // FACTORY
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Create a configured {@link AutoFactory} bound to the given swerve subsystem. Uses a shared
   * {@link TrajectoryFollower} so the feedforward coming out of Choreo is augmented by PID
   * correction on pose error — without this, a trajectory whose sampled speeds don't quite
   * match reality (e.g., after a bump or a pose reset) would drift for the whole run.
   *
   * <p>The controller lambda:
   *
   * <ol>
   *   <li>Unwraps {@link SwerveSample} into a {@link HolonomicTrajectorySample}.
   *   <li>Asks the follower for a {@link ChassisSpeeds} that combines sample FF + PID correction.
   *   <li>Pushes the corrected speeds into {@code swerve.driveRobotRelative}.
   *   <li>Logs both target + actual pose and the raw/corrected speeds under {@code Auto/}.
   * </ol>
   *
   * @param swerve the swerve subsystem
   * @return a configured, alliance-aware {@link AutoFactory}
   */
  public static AutoFactory factory(SwerveSubsystem swerve) {
    TrajectoryFollower follower = TrajectoryFollower.withDefaultGains();
    return new AutoFactory(
        swerve::getPose,
        swerve::resetOdometry,
        (SwerveSample sample) -> {
          // Wrap Choreo's SwerveSample as a HolonomicTrajectorySample so the follower can compute
          // FF + PID correction in one pass. Choreo samples are field-relative by design.
          HolonomicTrajectorySample wrapped =
              new HolonomicTrajectorySample(
                  sample.getTimestamp(), sample.getPose(), sample.getChassisSpeeds());
          ChassisSpeeds correctedFieldRel = follower.computeSpeeds(wrapped, swerve.getPose());

          // driveRobotRelative expects robot-frame speeds — convert explicitly. The previous
          // implementation forwarded Choreo's field-relative speeds directly, which silently
          // broke any trajectory that rotated the robot. Now the drivetrain correctly sees
          // intended motion regardless of heading.
          ChassisSpeeds correctedRobotRel =
              ChassisSpeeds.fromFieldRelativeSpeeds(correctedFieldRel, swerve.getHeading());
          swerve.driveRobotRelative(correctedRobotRel);

          // ── Auto telemetry ──────────────────────────────────────────────
          // AdvantageScope can plot Target/Actual pose as an overlaid field widget; the speeds
          // let a mentor compare raw trajectory velocities against the corrected output and see
          // exactly when the PID is working hard to cover a pose error.
          Logger.recordOutput("Auto/TargetPose", sample.getPose());
          Logger.recordOutput("Auto/ActualPose", swerve.getPose());
          Logger.recordOutput("Auto/TrajectoryTime", sample.getTimestamp());
          Logger.recordOutput("Auto/SampleVxFieldRel", sample.getChassisSpeeds().vxMetersPerSecond);
          Logger.recordOutput(
              "Auto/CorrectedVxFieldRel", correctedFieldRel.vxMetersPerSecond);
          Logger.recordOutput(
              "Auto/CorrectedVyFieldRel", correctedFieldRel.vyMetersPerSecond);
          Logger.recordOutput(
              "Auto/CorrectedOmegaRadPerSec", correctedFieldRel.omegaRadiansPerSecond);
        },
        true, // flip coordinates for red alliance
        swerve);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // CONVENIENCE — single-trajectory runner
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Create a command that follows a single named Choreo trajectory. Resets odometry to the
   * trajectory start pose before driving.
   *
   * @param trajectoryName the {@code .traj} filename without extension
   * @param swerve the swerve subsystem
   * @return a {@link Command} that follows the trajectory and finishes when complete
   */
  public static Command trajectory(String trajectoryName, SwerveSubsystem swerve) {
    AutoFactory f = factory(swerve);
    AutoRoutine routine = f.newRoutine(trajectoryName);
    AutoTrajectory traj = routine.trajectory(trajectoryName);
    routine.active().onTrue(traj.resetOdometry().andThen(traj.cmd()));
    return routine.cmd();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ROUTINES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * <b>Leave Only</b> — drive straight off the starting line to earn the leave point. No scoring.
   *
   * <p>Requires: {@value #TRAJ_LEAVE_START}.traj
   */
  public static AutoRoutine leaveRoutine(AutoFactory factory) {
    AutoRoutine routine = factory.newRoutine("Leave Only");
    AutoTrajectory leave = routine.trajectory(TRAJ_LEAVE_START);
    routine.active().onTrue(leave.resetOdometry().andThen(leave.cmd()));
    return routine;
  }

  /**
   * <b>Score and Leave</b> — shoot the preloaded FUEL with the flywheel, then drive off the
   * starting line.
   *
   * <p>Requires: {@value #TRAJ_LEAVE_START}.traj
   */
  public static AutoRoutine scoreAndLeaveRoutine(
      AutoFactory factory, Flywheel flywheel, Conveyor conveyor, SwerveSubsystem swerve) {
    AutoRoutine routine = factory.newRoutine("Score and Leave");
    AutoTrajectory leave = routine.trajectory(TRAJ_LEAVE_START);

    routine
        .active()
        .onTrue(
            Commands.sequence(
                // Shoot preloaded FUEL (up to 3 s), then drive off line
                new FlywheelAutoFeed(flywheel, conveyor, swerve)
                    .withTimeout(frc.robot.Constants.Autonomous.kShootTimeoutSeconds),
                leave.resetOdometry().andThen(leave.cmd())));

    return routine;
  }

  /**
   * <b>2 Fuel</b> — shoot preloaded FUEL, drive to the nearest FUEL intake to collect a second
   * piece, return to the HUB, and shoot again.
   *
   * <p>After the intake pickup, a {@link ConditionalCommand} checks {@code ssm.hasGamePiece()}
   * before attempting the second shot. If the pickup failed, the robot skips scoring and returns to
   * idle — preventing a wasted spin-up cycle.
   *
   * <p>Requires: {@value #TRAJ_REEF_TO_STATION}.traj, {@value #TRAJ_STATION_TO_REEF}.traj
   * (legacy filenames pending step-5 Choreo re-author).
   *
   * <p>Event markers expected in trajectories:
   *
   * <ul>
   *   <li>{@code "intake"} in {@value #TRAJ_REEF_TO_STATION} — extend intake as robot approaches
   *       the FUEL intake
   *   <li>{@code "spinup"} in {@value #TRAJ_STATION_TO_REEF} — spin flywheel before reaching HUB
   * </ul>
   */
  public static AutoRoutine twoFuelRoutine(
      AutoFactory factory,
      Flywheel flywheel,
      Conveyor conveyor,
      Intake intake,
      SuperstructureStateMachine ssm,
      SwerveSubsystem swerve) {
    AutoRoutine routine = factory.newRoutine("2 Fuel");
    AutoTrajectory toStation = routine.trajectory(TRAJ_REEF_TO_STATION);
    AutoTrajectory stationToReef = routine.trajectory(TRAJ_STATION_TO_REEF);

    // Step 1: shoot preloaded FUEL, then drive to the FUEL intake
    routine
        .active()
        .onTrue(
            Commands.sequence(
                new FlywheelAutoFeed(flywheel, conveyor, swerve)
                    .withTimeout(frc.robot.Constants.Autonomous.kShootTimeoutSeconds),
                toStation.resetOdometry().andThen(toStation.cmd())));

    // Bind the Choreo "intake" event marker so the intake wheel actually runs during
    // the approach to the FUEL intake. Without this, the routine's "intake" marker
    // would fire into empty air. See AutoIntakeCommand for details.
    toStation.atTime("intake").onTrue(new AutoIntakeCommand(intake, ssm).withTimeout(3.0));

    // Step 2: when arriving at the FUEL intake, drive back toward the HUB
    toStation.done().onTrue(stationToReef.cmd());

    // Step 3: spin up flywheel 1.5 s before reaching HUB — only if a game piece was acquired
    stationToReef
        .atTimeBeforeEnd(frc.robot.Constants.Autonomous.kFlywheelSpinupLeadSeconds)
        .onTrue(
            new ConditionalCommand(
                new FlywheelStatic(flywheel, conveyor, frc.robot.Constants.Autonomous.kAutoStaticShotRpm)
                    .withTimeout(frc.robot.Constants.Autonomous.kStaticSpinupDurationSeconds),
                Commands.none(),
                ssm::hasGamePiece));

    // Step 4: when back at HUB, shoot only if we successfully picked up a game piece
    stationToReef
        .done()
        .onTrue(
            new ConditionalCommand(
                new FlywheelAutoFeed(flywheel, conveyor, swerve)
                    .withTimeout(frc.robot.Constants.Autonomous.kShootTimeoutSeconds),
                Commands.runOnce(
                    () ->
                        org.littletonrobotics.junction.Logger.recordOutput(
                            "Auto/SkippedShot", "2Fuel-NoGamePiece")),
                ssm::hasGamePiece));

    return routine;
  }

  /**
   * <b>3 Fuel</b> — two full FUEL-intake cycles: shoot preloaded, collect two more FUEL pieces
   * from the intake, and score all three.
   *
   * <p>Both scoring attempts after intake pickups are gated by {@link ConditionalCommand} checking
   * {@code ssm.hasGamePiece()}. A missed pickup causes the robot to skip the shot and move on to
   * the next cycle rather than wasting time spinning up the flywheel.
   *
   * <p>Requires: {@value #TRAJ_REEF_TO_STATION}.traj, {@value #TRAJ_STATION_TO_REEF}.traj (both run
   * twice, legacy filenames pending step-5 Choreo re-author).
   *
   * <p>This routine reuses the same trajectory segments for both collection cycles. If the two
   * cycles require different paths, split them into separate {@code .traj} files.
   */
  public static AutoRoutine threeFuelRoutine(
      AutoFactory factory,
      Flywheel flywheel,
      Conveyor conveyor,
      Intake intake,
      SuperstructureStateMachine ssm,
      SwerveSubsystem swerve) {
    AutoRoutine routine = factory.newRoutine("3 Fuel");

    // First cycle uses split index 0 (or whole file if no splits defined)
    AutoTrajectory toStation1 = routine.trajectory(TRAJ_REEF_TO_STATION, 0);
    AutoTrajectory stationToReef1 = routine.trajectory(TRAJ_STATION_TO_REEF, 0);

    // Second cycle uses split index 1 (or falls back to whole file if no split)
    AutoTrajectory toStation2 = routine.trajectory(TRAJ_REEF_TO_STATION, 1);
    AutoTrajectory stationToReef2 = routine.trajectory(TRAJ_STATION_TO_REEF, 1);

    // ── Cycle 1 ──────────────────────────────────────────────────────────────
    routine
        .active()
        .onTrue(
            Commands.sequence(
                new FlywheelAutoFeed(flywheel, conveyor, swerve)
                    .withTimeout(frc.robot.Constants.Autonomous.kShootTimeoutSeconds),
                toStation1.resetOdometry().andThen(toStation1.cmd())));

    // FUEL intake marker, cycle 1 — run the intake during approach so hasGamePiece() can flip true.
    toStation1
        .atTime("intake")
        .onTrue(new AutoIntakeCommand(intake, ssm).withTimeout(3.0));

    toStation1.done().onTrue(stationToReef1.cmd());

    // Spin up only if game piece acquired at FUEL intake cycle 1
    stationToReef1
        .atTimeBeforeEnd(frc.robot.Constants.Autonomous.kFlywheelSpinupLeadSeconds)
        .onTrue(
            new ConditionalCommand(
                new FlywheelStatic(flywheel, conveyor, frc.robot.Constants.Autonomous.kAutoStaticShotRpm)
                    .withTimeout(frc.robot.Constants.Autonomous.kStaticSpinupDurationSeconds),
                Commands.none(),
                ssm::hasGamePiece));

    // After cycle 1 return: shoot if acquired, then always proceed to cycle 2
    stationToReef1
        .done()
        .onTrue(
            Commands.sequence(
                new ConditionalCommand(
                    new FlywheelAutoFeed(flywheel, conveyor, swerve)
                        .withTimeout(frc.robot.Constants.Autonomous.kShootTimeoutSeconds),
                    Commands.runOnce(
                        () ->
                            org.littletonrobotics.junction.Logger.recordOutput(
                                "Auto/SkippedShot", "3Fuel-Cycle1-NoGamePiece")),
                    ssm::hasGamePiece),
                // Always attempt cycle 2 regardless of cycle 1 outcome
                toStation2.cmd()));

    // ── Cycle 2 ──────────────────────────────────────────────────────────────
    // FUEL intake marker, cycle 2 — same pattern as cycle 1.
    toStation2
        .atTime("intake")
        .onTrue(new AutoIntakeCommand(intake, ssm).withTimeout(3.0));

    toStation2.done().onTrue(stationToReef2.cmd());

    // Spin up only if game piece acquired at FUEL intake cycle 2
    stationToReef2
        .atTimeBeforeEnd(frc.robot.Constants.Autonomous.kFlywheelSpinupLeadSeconds)
        .onTrue(
            new ConditionalCommand(
                new FlywheelStatic(flywheel, conveyor, frc.robot.Constants.Autonomous.kAutoStaticShotRpm)
                    .withTimeout(frc.robot.Constants.Autonomous.kStaticSpinupDurationSeconds),
                Commands.none(),
                ssm::hasGamePiece));

    // Shoot cycle 2 if acquired
    stationToReef2
        .done()
        .onTrue(
            new ConditionalCommand(
                new FlywheelAutoFeed(flywheel, conveyor, swerve)
                    .withTimeout(frc.robot.Constants.Autonomous.kShootTimeoutSeconds),
                Commands.runOnce(
                    () ->
                        org.littletonrobotics.junction.Logger.recordOutput(
                            "Auto/SkippedShot", "3Fuel-Cycle2-NoGamePiece")),
                ssm::hasGamePiece));

    return routine;
  }
}
