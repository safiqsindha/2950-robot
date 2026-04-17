package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.*;

import frc.robot.Constants;
import frc.robot.subsystems.SuperstructureStateMachine.State;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SuperstructureStateMachine}. Exercises the static pure state-transition function
 * {@code computeNextState(State, double, double, boolean, boolean, double)} directly, bypassing HAL
 * and hardware dependencies.
 *
 * <p>The CLIMBING state was removed in Phase 1.5 — no Climber subsystem is installed.
 */
class SuperstructureStateMachineTest {

  /** Game piece current threshold (amps) — must match Constants.Superstructure value. */
  private static final double THRESHOLD = Constants.Superstructure.kGamePieceCurrentThresholdAmps;

  /** SCORING auto-exit timeout (seconds) — must match Constants.Superstructure value. */
  private static final double TIMEOUT = Constants.Superstructure.kScoringTimeoutSeconds;

  /** Shorthand: call the static transition with a 0.0 scoring duration. */
  private static State next(State state, double current, boolean intake, boolean score) {
    return SuperstructureStateMachine.computeNextState(
        state, current, THRESHOLD, intake, score, 0.0);
  }

  /** Shorthand: call with an explicit scoring duration. */
  private static State nextWithDuration(
      State state, double current, boolean intake, boolean score, double scoringDuration) {
    return SuperstructureStateMachine.computeNextState(
        state, current, THRESHOLD, intake, score, scoringDuration);
  }

  // ── IDLE state transitions ──────────────────────────────────────────────

  @Test
  void idle_staysIdleWithNoRequests() {
    assertEquals(State.IDLE, next(State.IDLE, 0.0, false, false));
  }

  @Test
  void idle_transitionsToIntakingOnRequest() {
    assertEquals(State.INTAKING, next(State.IDLE, 0.0, true, false));
  }

  // ── INTAKING state transitions ──────────────────────────────────────────

  @Test
  void intaking_staysIntakingBelowCurrentThreshold() {
    assertEquals(State.INTAKING, next(State.INTAKING, THRESHOLD - 1.0, true, false));
  }

  @Test
  void intaking_transitionsToStagingOnCurrentSpike() {
    assertEquals(State.STAGING, next(State.INTAKING, THRESHOLD + 5.0, true, false));
  }

  @Test
  void intaking_returnsToIdleWhenIntakeCancelled() {
    assertEquals(State.IDLE, next(State.INTAKING, 0.0, false, false));
  }

  @Test
  void intaking_exactThresholdDoesNotTrigger() {
    // Must exceed threshold, not equal
    assertEquals(State.INTAKING, next(State.INTAKING, THRESHOLD, true, false));
  }

  @Test
  void intaking_cancelsEvenWithHighCurrent() {
    // If intake is cancelled, goes to IDLE regardless of current
    assertEquals(State.IDLE, next(State.INTAKING, THRESHOLD + 20.0, false, false));
  }

  // ── STAGING state transitions ──────────────────────────────────────────

  @Test
  void staging_staysStagingWithIntakeActive() {
    assertEquals(State.STAGING, next(State.STAGING, 0.0, true, false));
  }

  @Test
  void staging_transitionsToScoringOnScoreRequest() {
    assertEquals(State.SCORING, next(State.STAGING, 0.0, true, true));
  }

  @Test
  void staging_returnsToIdleWhenIntakeCancelled() {
    assertEquals(State.IDLE, next(State.STAGING, 0.0, false, false));
  }

  @Test
  void staging_scoreWithoutIntakeStillScores() {
    // scoreRequested takes priority check before intakeRequested check
    assertEquals(State.SCORING, next(State.STAGING, 0.0, false, true));
  }

  // ── SCORING state transitions ──────────────────────────────────────────

  @Test
  void scoring_staysScoringBelowTimeout() {
    // Just under the 2 s window — should stay SCORING.
    assertEquals(
        State.SCORING, nextWithDuration(State.SCORING, 0.0, false, false, TIMEOUT - 0.001));
    assertEquals(State.SCORING, nextWithDuration(State.SCORING, 0.0, true, true, 0.0));
  }

  @Test
  void scoring_autoExitsToIdleAtTimeout() {
    // Exactly at the timeout boundary — must exit.
    assertEquals(State.IDLE, nextWithDuration(State.SCORING, 0.0, false, false, TIMEOUT));
  }

  @Test
  void scoring_autoExitsToIdleAfterTimeout() {
    // Well past the timeout.
    assertEquals(State.IDLE, nextWithDuration(State.SCORING, 0.0, false, false, TIMEOUT + 5.0));
  }

  @Test
  void scoring_zeroScoringDurationStaysScoring() {
    // At t=0 (just entered SCORING), should definitely stay.
    assertEquals(State.SCORING, nextWithDuration(State.SCORING, 0.0, false, false, 0.0));
  }

  // ── Full lifecycle: IDLE → INTAKING → STAGING → SCORING ───────────────

  @Test
  void fullScoringLifecycle() {
    // IDLE → INTAKING (intake requested)
    assertEquals(State.INTAKING, next(State.IDLE, 0.0, true, false));

    // INTAKING → STAGING (current spike)
    assertEquals(State.STAGING, next(State.INTAKING, THRESHOLD + 10.0, true, false));

    // STAGING → SCORING (score requested)
    assertEquals(State.SCORING, next(State.STAGING, 0.0, true, true));

    // SCORING stays well within timeout
    assertEquals(State.SCORING, nextWithDuration(State.SCORING, 0.0, false, false, TIMEOUT * 0.5));

    // SCORING auto-exits at timeout
    assertEquals(State.IDLE, nextWithDuration(State.SCORING, 0.0, false, false, TIMEOUT));
  }

  // ── Edge cases ─────────────────────────────────────────────────────────

  @Test
  void negativeCurrent_doesNotTriggerStaging() {
    assertEquals(State.INTAKING, next(State.INTAKING, -5.0, true, false));
  }

  @Test
  void zeroCurrent_doesNotTriggerStaging() {
    assertEquals(State.INTAKING, next(State.INTAKING, 0.0, true, false));
  }

  // ── Phase 3.2 — Sim current gate (acceptance criterion) ──────────────────

  /**
   * Documents the Phase 3.2 sim-fix contract: {@code Intake.simulationPeriodic()} now gates current
   * synthesis on {@code simGamePieceAcquired}. Without that flag being set, the wheel current stays
   * 0 even when the wheel is commanded — and INTAKING must NOT auto-advance to STAGING, preventing
   * false game-piece detection during every routine wheel spin.
   */
  @Test
  void intaking_withUngatedSimCurrent_staysIntaking() {
    // In simulation before Phase 3.2: any wheel command produced ~30A (> threshold), causing
    // INTAKING → STAGING on every spin. Post-fix: wheel current is 0 unless a game piece is
    // explicitly injected. The SSM contract: zero current in INTAKING → stay INTAKING.
    assertEquals(State.INTAKING, next(State.INTAKING, 0.0, true, false));
  }

  /**
   * Complements the above: after {@code simulateGamePieceAcquired()} sets the flag, the synthesized
   * current spike causes SSM to advance INTAKING → STAGING as intended.
   */
  @Test
  void intaking_withInjectedSimCurrent_advancesToStaging() {
    // simWheelCurrentAmps = 30.0 when simGamePieceAcquired=true and wheel at full output.
    // Verifies the SSM correctly advances on the injected spike.
    double injectedCurrent = 30.0; // matches Intake.simulationPeriodic() at full output
    assertEquals(State.STAGING, next(State.INTAKING, injectedCurrent, true, false));
  }

  // ── Per-state timeout safety nets (SSM hardening) ────────────────────────

  @Test
  void intaking_autoIdlesAfterTimeoutWhenNoPieceDetected() {
    // Driver forgot to release the intake button; no current spike. After kIntakingTimeoutSeconds
    // the SSM bails out rather than fishing forever.
    double timeout = Constants.Superstructure.kIntakingTimeoutSeconds;
    assertEquals(
        State.IDLE,
        nextWithDuration(State.INTAKING, 0.0, /* intake */ true, /* score */ false, timeout));
  }

  @Test
  void intaking_pieceDetectionBeatsTimeout() {
    // Even right at the timeout boundary, if the current spike is present the transition wins.
    double timeout = Constants.Superstructure.kIntakingTimeoutSeconds;
    assertEquals(
        State.STAGING,
        nextWithDuration(State.INTAKING, THRESHOLD + 5.0, true, false, timeout + 0.1));
  }

  @Test
  void staging_autoIdlesAfterTimeoutWhenNoScoreRequest() {
    // Stale staging — nobody asked to score. The piece stays physically in the conveyor; only the
    // state label is reset.
    double timeout = Constants.Superstructure.kStagingTimeoutSeconds;
    assertEquals(
        State.IDLE, nextWithDuration(State.STAGING, 0.0, /* intake */ true, false, timeout));
  }

  @Test
  void staging_scoreRequestBeatsTimeout() {
    // If score lands exactly at the timeout, we still advance to SCORING (score takes priority).
    double timeout = Constants.Superstructure.kStagingTimeoutSeconds;
    assertEquals(
        State.SCORING, nextWithDuration(State.STAGING, 0.0, true, /* score */ true, timeout + 5.0));
  }

  @Test
  void staging_cancelBeatsTimeout() {
    // If the intake command is cancelled before the timeout we route to IDLE via the normal
    // "intake cancelled" path, not the timeout path — both lead to the same state but for
    // different reasons.
    assertEquals(State.IDLE, nextWithDuration(State.STAGING, 0.0, false, false, 0.5));
  }
}
