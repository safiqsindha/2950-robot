package frc.robot.commands.flywheel;

import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants;
import frc.robot.Helper;
import frc.robot.subsystems.Conveyor;
import frc.robot.subsystems.Flywheel;
import org.littletonrobotics.junction.Logger;

/**
 * Spins up the flywheel to a fixed RPM setpoint and auto-feeds once within 10% of target. Used for
 * POV-button preset shots at known distances.
 */
public class FlywheelStatic extends Command {

  private final Flywheel flywheel;
  private final Conveyor conveyor;
  private final double targetRpm;

  private static final double kFeederPercent = 1.0;
  private static final double kConveyorPercent = 1.0;

  private boolean rpmReady = false;

  public FlywheelStatic(Flywheel flywheel, Conveyor conveyor, double staticSetpointRpm) {
    this.flywheel = flywheel;
    this.conveyor = conveyor;
    this.targetRpm = staticSetpointRpm;
    addRequirements(flywheel, conveyor);
  }

  @Override
  public void initialize() {
    flywheel.setTargetRpm(targetRpm);
    flywheel.setLower(frc.robot.Constants.Flywheel.kLowerRetractPercent);
    Helper.resetFilters();
    rpmReady = false;
  }

  @Override
  public void execute() {
    Helper.updateFilters();
    double distance = Helper.getAprilTagDist();
    Logger.recordOutput("Flywheel/DebugRpm", targetRpm);
    Logger.recordOutput("Flywheel/DebugDist", distance);

    if (rpmReady) {
      flywheel.setLower(kFeederPercent);
      conveyor.setConveyor(kConveyorPercent);
    } else if (targetRpm > 0) {
      // Divide-by-zero guard: the ctor accepts `staticSetpointRpm` with no validation so a caller
      // could pass 0 or negative. Without this branch the NaN rpmReady latches false forever.
      rpmReady =
          Math.abs((flywheel.getCurrentRpm() - targetRpm) / targetRpm)
              < Constants.Flywheel.kReadyThreshold;
    }
  }

  @Override
  public void end(boolean interrupted) {
    flywheel.setTargetRpm(0);
    flywheel.setLower(0);
    // Stop the conveyor too — otherwise a panic-button interrupt while the feed loop was
    // running would leave the conveyor belt spinning at 100% until the next conveyor command
    // overrides it. See FlywheelAutoFeed.end() for the same pattern.
    conveyor.setConveyor(0);
  }

  @Override
  public boolean isFinished() {
    return false;
  }
}
