package frc.robot.commands.flywheel;

import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Helper;
import frc.robot.subsystems.SwerveSubsystem;
import org.littletonrobotics.junction.Logger;

/**
 * Rotates the robot to aim at the hub target using Limelight AprilTag tracking. Uses a simple P
 * controller on horizontal offset. Intended to run in parallel with FlywheelAutoFeed during
 * autonomous or semi-autonomous scoring.
 */
public class FlywheelAim extends Command {

  private final SwerveSubsystem swerve;

  /** Shared with AutoAlignCommand — extracted to Constants.Align during audit cleanup. */
  private static final double kP = frc.robot.Constants.Align.kHeadingKP;

  private static final double kSign = -1;

  public FlywheelAim(SwerveSubsystem swerve) {
    this.swerve = swerve;
    addRequirements(swerve);
  }

  @Override
  public void initialize() {
    Helper.resetFilters();
  }

  @Override
  public void execute() {
    Helper.updateFilters();
    double xDist = Helper.getAprilTagAim();
    double rotation = kSign * (xDist * kP);
    Logger.recordOutput("Flywheel/DebugAimError", xDist);
    Logger.recordOutput("Flywheel/DebugAimRotation", rotation);
    swerve.drive(new Translation2d(), rotation, false);
  }

  @Override
  public void end(boolean interrupted) {}

  @Override
  public boolean isFinished() {
    return false;
  }
}
