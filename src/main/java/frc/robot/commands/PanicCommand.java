package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.Constants;
import frc.robot.subsystems.LEDs;
import frc.robot.subsystems.LEDs.AnimationType;
import frc.robot.subsystems.SuperstructureStateMachine;

/**
 * Driver panic button. One press cancels every scheduled command, forces the superstructure back to
 * {@code IDLE}, and flashes red on the LEDs at the highest priority.
 *
 * <p>Bound to {@code driver.back().and(driver.start())} in {@code RobotContainer} — a two-button
 * combo to avoid accidental triggers, and {@code .ignoringDisable(true)} so it fires even when the
 * robot is disabled on the field.
 *
 * <p>The three side effects are expressed as {@link Runnable}s in {@link #fire} so that the logic
 * is unit-testable without HAL or the {@link CommandScheduler}. Production code goes through {@link
 * #build} which wires the real WPILib + subsystem calls.
 */
public final class PanicCommand {

  private PanicCommand() {}

  /**
   * Executes the three panic side effects in order. Package-visible for tests; production callers
   * should use {@link #build(SuperstructureStateMachine, LEDs)}.
   *
   * @param cancelAll cancel every scheduled command (prod: {@code
   *     CommandScheduler.getInstance()::cancelAll})
   * @param forceIdle force the superstructure to its IDLE state (prod: {@code ssm::requestIdle})
   * @param redFlash raise a high-priority red-flash animation (prod: {@code () ->
   *     leds.setAnimation(ERROR_FLASH, kPriorityAlert)})
   */
  static void fire(Runnable cancelAll, Runnable forceIdle, Runnable redFlash) {
    cancelAll.run();
    forceIdle.run();
    redFlash.run();
  }

  /**
   * Builds the panic {@link Command} for production wiring. The returned command runs once, never
   * requires a subsystem (so it can't be blocked by subsystem cancellation), and ignores disable so
   * the driver can still abort everything from the touchpad while disabled on the field.
   */
  public static Command build(SuperstructureStateMachine ssm, LEDs leds) {
    return Commands.runOnce(
            () ->
                fire(
                    CommandScheduler.getInstance()::cancelAll,
                    ssm::requestIdle,
                    () ->
                        leds.setAnimation(
                            AnimationType.ERROR_FLASH, Constants.LEDs.kPriorityAlert)))
        .ignoringDisable(true)
        .withName("Panic");
  }
}
