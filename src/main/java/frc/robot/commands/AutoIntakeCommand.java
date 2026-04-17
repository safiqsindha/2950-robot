package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.SuperstructureStateMachine;

/**
 * Runs the intake wheel at full output during autonomous until the SSM reports a game piece or a
 * timeout expires. Bind to a Choreo {@code .atTime("intake")} event marker in the routine factory
 * so the robot actually collects during the station approach.
 *
 * <p>Why this exists: the default {@code IntakeControl} command reads the operator trigger, which
 * is zero during autonomous. Without a dedicated auto command, the routine's "intake" marker
 * docstring was a lie — the intake motor never ran, the SSM never saw the current spike, and every
 * conditional scoring branch downstream was silently skipped. A post-shipment external review
 * surfaced this as a match-losing bug.
 *
 * <p>Lifecycle:
 *
 * <ul>
 *   <li>{@code initialize()}: notify SSM we're intaking, spin wheel at 100 %
 *   <li>{@code execute()}: keep the wheel on; SSM transitions to STAGING when current spikes
 *   <li>{@code isFinished()}: true once {@code ssm.hasGamePiece()} flips true, or on external
 *       timeout (callers typically chain with {@code .withTimeout(2.0)})
 *   <li>{@code end()}: stop the wheel, leave the SSM alone so downstream commands see STAGING
 * </ul>
 *
 * <p>This claims the Intake subsystem as a requirement, so scheduling it interrupts {@code
 * IntakeControl} for the duration. Safe because nothing else should be driving the intake mid-
 * auto.
 */
public final class AutoIntakeCommand extends Command {

  /** Wheel percent output to command. 100 % matches the real-robot practice-session tuning. */
  private static final double kWheelPercent = 1.0;

  private final Intake intake;
  private final SuperstructureStateMachine ssm;

  public AutoIntakeCommand(Intake intake, SuperstructureStateMachine ssm) {
    this.intake = intake;
    this.ssm = ssm;
    addRequirements(intake);
  }

  @Override
  public void initialize() {
    ssm.requestIntake();
    intake.setWheel(kWheelPercent);
  }

  @Override
  public void execute() {
    // Re-assert the wheel each tick in case another parallel command tried to override. The
    // requirement means nothing else should, but the defensive write is free.
    intake.setWheel(kWheelPercent);
  }

  @Override
  public boolean isFinished() {
    return ssm.hasGamePiece();
  }

  @Override
  public void end(boolean interrupted) {
    // Stop the wheel. The SSM stays in STAGING (or whatever the natural state transition
    // produced); clearing it would cause the next conditional branch to think we have no piece.
    intake.setWheel(0);
  }
}
