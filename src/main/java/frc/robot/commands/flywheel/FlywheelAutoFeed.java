package frc.robot.commands.flywheel;

import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Helper;
import frc.robot.subsystems.Conveyor;
import frc.robot.subsystems.Flywheel;
import frc.robot.subsystems.SwerveSubsystem;
import org.littletonrobotics.junction.Logger;

/**
 * Spins up the flywheel to the distance-predicted RPM and auto-feeds once within 10% of target.
 * Uses Limelight AprilTag distance <i>and</i> horizontal offset ({@code tx}) together with the
 * robot's chassis velocity to compute an RPM that accounts for both radial and lateral motion —
 * the Team 971 3-iteration fixed-point shoot-on-the-fly (Phase 7.2).
 *
 * <p>If {@code SwerveSubsystem} is not supplied (1-arg constructor for backward compat with
 * static-test wiring), the command falls back to the 1-D distance-only math.
 */
public class FlywheelAutoFeed extends Command {

  private final Flywheel flywheel;
  private final Conveyor conveyor;

  /**
   * Optional — when present, FlywheelAutoFeed threads the chassis velocity through the 971
   * shoot-on-the-fly solver so the RPM leads a moving robot. {@code null} degrades gracefully to
   * the 1-D {@link Helper#rpmFromMeters(double)} lookup.
   */
  private final SwerveSubsystem swerve;

  private static final double kFeederPercent = 1.0;
  private static final double kConveyorPercent = 1.0;

  private boolean rpmReady = false;

  /** 2-D shoot-on-the-fly constructor — preferred. */
  public FlywheelAutoFeed(Flywheel flywheel, Conveyor conveyor, SwerveSubsystem swerve) {
    this.flywheel = flywheel;
    this.conveyor = conveyor;
    this.swerve = swerve;
    // Important: we do NOT require swerve here. The shooter commands run in parallel with
    // heading-control commands that already own the drivetrain; claiming it here would race.
    addRequirements(flywheel, conveyor);
  }

  /**
   * Legacy 1-D constructor — kept so existing tests and unit-test harnesses that don't thread a
   * swerve reference through still compile. On a real robot, prefer the 3-arg form.
   */
  public FlywheelAutoFeed(Flywheel flywheel, Conveyor conveyor) {
    this(flywheel, conveyor, null);
  }

  @Override
  public void initialize() {
    rpmReady = false;
    flywheel.setLower(frc.robot.Constants.Flywheel.kLowerRetractPercent);
    conveyor.setConveyor(0);
    Helper.resetFilters();
  }

  @Override
  public void execute() {
    Helper.updateFilters();
    double distance = Helper.getAprilTagDist();
    double predictedRpm = computePredictedRpm(distance);

    Logger.recordOutput("Flywheel/DebugRpm", predictedRpm);
    Logger.recordOutput("Flywheel/DebugDist", distance);
    flywheel.setTargetRpm(predictedRpm);

    if (rpmReady) {
      flywheel.setLower(kFeederPercent);
      conveyor.setConveyor(kConveyorPercent);
    } else {
      rpmReady =
          Math.abs((flywheel.getCurrentRpm() - predictedRpm) / predictedRpm)
              < frc.robot.Constants.Flywheel.kReadyThreshold;
    }
  }

  /**
   * Pure helper — resolves to the 2-D shoot-on-the-fly math when a swerve reference is threaded in,
   * otherwise the 1-D fallback. Exposed to simplify the {@link #execute()} flow and isolate the
   * branch for potential unit-level testing of the selector logic.
   *
   * @param distance filtered Limelight distance in meters
   * @return predicted flywheel RPM clamped to [{@code kMinRpm}, {@code kMaxRpm}] by {@code Helper}.
   */
  double computePredictedRpm(double distance) {
    if (swerve == null) {
      return Helper.rpmFromMeters(distance);
    }
    // Limelight's tx is degrees, positive to the right on screen. Our rpmFromMeters 2D overload
    // expects radians, positive = robot's left side. Flip the sign during conversion.
    double txDegrees = Helper.getAprilTagAim();
    double bearingRadians = Math.toRadians(-txDegrees);
    ChassisSpeeds speeds = swerve.getRobotVelocity();
    Logger.recordOutput("Flywheel/DebugTxRad", bearingRadians);
    Logger.recordOutput("Flywheel/DebugVxRobot", speeds.vxMetersPerSecond);
    Logger.recordOutput("Flywheel/DebugVyRobot", speeds.vyMetersPerSecond);
    return Helper.rpmFromMeters(distance, bearingRadians, speeds);
  }

  @Override
  public void end(boolean interrupted) {
    flywheel.setTargetRpm(0);
    flywheel.setLower(0);
    conveyor.setConveyor(0);
  }

  @Override
  public boolean isFinished() {
    return false;
  }
}
