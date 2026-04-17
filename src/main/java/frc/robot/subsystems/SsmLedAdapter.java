package frc.robot.subsystems;

import frc.robot.Constants;
import frc.robot.subsystems.LEDs.AnimationType;
import frc.robot.subsystems.SuperstructureStateMachine.State;

/**
 * Translates {@link SuperstructureStateMachine} state transitions into {@link LEDs} animations.
 * Keeps the SSM itself strictly logic — commands + state tracking — and delegates all colour
 * decisions here.
 *
 * <p>One row per state:
 *
 * <table>
 *   <tr><th>SSM state</th><th>Animation</th></tr>
 *   <tr><td>IDLE</td><td>default (ENABLED_IDLE breathing green)</td></tr>
 *   <tr><td>INTAKING</td><td>DRIVING_BREATHE (green breathing, priority idle)</td></tr>
 *   <tr><td>STAGING</td><td>ACQUIRED_FLASH (rapid green flash, priority driving)</td></tr>
 *   <tr><td>SCORING</td><td>SCORED_FLASH (rapid white flash, priority aligning)</td></tr>
 * </table>
 *
 * <p>Priority is chosen so that a scripted high-priority override (ERROR_FLASH, PANIC) can still
 * interrupt the per-state pattern.
 *
 * <p>Pattern: call {@link #tick(State)} once per robot cycle from wherever owns the SSM + LED
 * handles (typically inside a composed command or a top-level wiring hook). Only fires an animation
 * when the observed state changes from the previous call — avoids spamming the LED controller's
 * priority comparator 50 Hz.
 */
public final class SsmLedAdapter {

  private final LEDs leds;
  private State lastState = null;

  public SsmLedAdapter(LEDs leds) {
    this.leds = leds;
  }

  /**
   * Observe the SSM's current state and apply the matching animation if it differs from the
   * previous observation.
   */
  public void tick(State current) {
    if (current == lastState) {
      return;
    }
    lastState = current;
    switch (current) {
      case IDLE -> leds.setAnimation(AnimationType.ENABLED_IDLE, Constants.LEDs.kPriorityIdle);
      case INTAKING ->
          leds.setAnimation(AnimationType.DRIVING_BREATHE, Constants.LEDs.kPriorityIdle);
      case STAGING ->
          leds.setAnimation(AnimationType.ACQUIRED_FLASH, Constants.LEDs.kPriorityDriving);
      case SCORING ->
          leds.setAnimation(AnimationType.SCORED_FLASH, Constants.LEDs.kPriorityAligning);
    }
  }

  /**
   * Force-clear the remembered state so the next {@link #tick(State)} re-applies the animation. Use
   * when a higher-priority command (panic, manual LED mode) has temporarily overridden the
   * SSM-driven animation and we want to resume it.
   */
  public void reset() {
    lastState = null;
  }
}
