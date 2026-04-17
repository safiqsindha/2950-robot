package frc.robot.subsystems;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import java.util.function.DoubleSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * Pure state tracker for the robot superstructure. Coordinates the intake → conveyor → flywheel →
 * scoring pipeline without directly commanding motors. Commands read the current state and decide
 * what to do; this class handles state transitions and logging.
 *
 * <p>States:
 *
 * <ul>
 *   <li>{@link State#IDLE} — No game piece, mechanisms at rest.
 *   <li>{@link State#INTAKING} — Intake deployed, wheels spinning; waiting for game piece.
 *       Auto-idles after {@link Constants.Superstructure#kIntakingTimeoutSeconds} if no piece is
 *       detected, so the driver doesn't need to remember to release the button.
 *   <li>{@link State#STAGING} — Game piece acquired; staging in conveyor for scoring. Auto-idles
 *       after {@link Constants.Superstructure#kStagingTimeoutSeconds} so a stale STAGING doesn't
 *       mislead downstream LED / dashboard indicators.
 *   <li>{@link State#SCORING} — Flywheel spinning up, feeding, or ejecting. Auto-exits to IDLE
 *       after {@link Constants.Superstructure#kScoringTimeoutSeconds} if no {@link #requestIdle()}
 *       is received, preventing a missed command from permanently locking the superstructure.
 * </ul>
 *
 * <p>Every state entry stamps a timestamp; {@code Superstructure/TimeInStateSec} surfaces the
 * current dwell to AdvantageScope so a mentor can see how long the machine has been stuck at a
 * glance.
 *
 * <p>Game piece detection uses a current-spike heuristic: when the intake wheel current exceeds
 * {@link Constants.Superstructure#kGamePieceCurrentThresholdAmps}, a game piece is assumed to have
 * been captured and the state advances from INTAKING → STAGING.
 *
 * <p>Note: the CLIMBING state was removed — no Climber subsystem is installed on this robot.
 */
public class SuperstructureStateMachine extends SubsystemBase {

  /** Superstructure operating states. */
  public enum State {
    IDLE,
    INTAKING,
    STAGING,
    SCORING
  }

  private final Intake intake;
  private final DoubleSupplier timeSource;

  private State currentState = State.IDLE;

  /** FPGA seconds of the most recent state transition — used for "time in state" telemetry. */
  private double stateEntryTimeSeconds = 0.0;

  // Whether scoring was requested externally (e.g. AutoScoreCommand or driver button)
  private boolean scoreRequested = false;
  // Whether intake was requested externally
  private boolean intakeRequested = false;

  /**
   * Creates the superstructure state machine (production — uses WPILib {@link Timer}).
   *
   * @param intake intake subsystem, used to read wheel current for game piece detection
   */
  public SuperstructureStateMachine(Intake intake) {
    this(intake, Timer::getFPGATimestamp);
  }

  /**
   * Package-private constructor that accepts an injectable time source. Follows the StallDetector /
   * LoggedTunableNumber convention: tests pass a mutable {@link DoubleSupplier} to control time
   * without HAL or WPILib Timer.
   */
  SuperstructureStateMachine(Intake intake, DoubleSupplier timeSource) {
    this.intake = intake;
    this.timeSource = timeSource;
  }

  /**
   * Free-form sub-state label — commands that own the SSM during a state set this for telemetry so
   * post-match replay shows <i>what</i> the robot was doing during SCORING (AIMING / SPINUP /
   * FEEDING) without extending the {@link State} enum. Cleared on state transitions.
   */
  private String subState = "";

  @Override
  public void periodic() {
    double now = timeSource.getAsDouble();
    double timeInState = now - stateEntryTimeSeconds;

    State nextState =
        computeNextState(
            currentState,
            intake.getWheelCurrent(),
            Constants.Superstructure.kGamePieceCurrentThresholdAmps,
            intakeRequested,
            scoreRequested,
            timeInState);

    if (nextState != currentState) {
      Logger.recordOutput(
          "Superstructure/StateTransition",
          currentState + " → " + nextState + " (after " + timeInState + "s)");
      Logger.recordOutput("Superstructure/StateTransitionTime", now);
      Logger.recordOutput("Superstructure/StateTransitionTo", nextState.name());
      currentState = nextState;
      stateEntryTimeSeconds = now;
      timeInState = 0.0;
      // Clear sub-state on transition — the old label belonged to the old state and would be
      // misleading telemetry in the new one. Commands re-assert their label as needed.
      subState = "";
    }
    Logger.recordOutput("Superstructure/State", currentState.name());
    Logger.recordOutput("Superstructure/SubState", subState);
    Logger.recordOutput("Superstructure/TimeInStateSec", timeInState);
    Logger.recordOutput("Superstructure/ScoreRequested", scoreRequested);
    Logger.recordOutput("Superstructure/IntakeRequested", intakeRequested);
    Logger.recordOutput("Superstructure/IntakeWheelCurrentAmps", intake.getWheelCurrent());
  }

  /**
   * Set a free-form sub-state label that a command owns during its execution. Cleared on the next
   * state transition.
   */
  public void setSubState(String label) {
    subState = (label == null) ? "" : label;
  }

  /**
   * @return the current sub-state label, or empty string if none.
   */
  public String getSubState() {
    return subState;
  }

  /**
   * Pure state transition logic. Package-private and static for unit testing without HAL.
   *
   * @param state current state
   * @param wheelCurrentAmps intake wheel current in amps
   * @param currentThresholdAmps game piece detection threshold in amps
   * @param intakeReq whether intake is requested
   * @param scoreReq whether score is requested
   * @param timeInStateSeconds how long the machine has been in {@code state}
   * @return the next state
   */
  static State computeNextState(
      State state,
      double wheelCurrentAmps,
      double currentThresholdAmps,
      boolean intakeReq,
      boolean scoreReq,
      double timeInStateSeconds) {
    switch (state) {
      case IDLE:
        if (intakeReq) return State.INTAKING;
        return State.IDLE;

      case INTAKING:
        if (!intakeReq) return State.IDLE;
        // Game piece detected by current spike → advance to staging
        if (wheelCurrentAmps > currentThresholdAmps) {
          return State.STAGING;
        }
        // Safety net — if the driver forgot to release the button and no piece landed, idle out.
        if (timeInStateSeconds >= Constants.Superstructure.kIntakingTimeoutSeconds) {
          return State.IDLE;
        }
        return State.INTAKING;

      case STAGING:
        if (scoreReq) return State.SCORING;
        // If intake command was cancelled before scoring, return to idle
        if (!intakeReq) return State.IDLE;
        // Stale-staging safety: if we've been here too long without a score request, idle.
        // The piece physically stays in the conveyor — this only affects the state label.
        if (timeInStateSeconds >= Constants.Superstructure.kStagingTimeoutSeconds) {
          return State.IDLE;
        }
        return State.STAGING;

      case SCORING:
        // Auto-exit to IDLE if the scoring window has elapsed. This prevents a missed requestIdle()
        // call from permanently locking the superstructure in the SCORING state.
        if (timeInStateSeconds >= Constants.Superstructure.kScoringTimeoutSeconds) {
          return State.IDLE;
        }
        return State.SCORING;

      default:
        return State.IDLE;
    }
  }

  // ─── External request API ─────────────────────────────────────────────────

  /**
   * Request the superstructure to begin intaking. Call continuously while the intake command is
   * running; the state machine will auto-advance to STAGING when a game piece is detected.
   */
  public void requestIntake() {
    intakeRequested = true;
    scoreRequested = false;
  }

  /**
   * Request the superstructure to score. Transitions from STAGING → SCORING. Has no effect if no
   * game piece has been staged.
   */
  public void requestScore() {
    scoreRequested = true;
  }

  /**
   * Return all requests to idle. Use this after a scoring sequence completes or when cancelling
   * intake.
   *
   * <p>Emits a transition log line so post-match replay can see the explicit reset. Previously the
   * direct-assign path skipped the transition hook entirely, making it impossible to tell from
   * telemetry whether the SSM naturally idled out (via {@link #computeNextState}) or was forced by
   * a caller.
   */
  public void requestIdle() {
    intakeRequested = false;
    scoreRequested = false;
    if (currentState != State.IDLE) {
      double now = timeSource.getAsDouble();
      double timeInState = now - stateEntryTimeSeconds;
      Logger.recordOutput(
          "Superstructure/StateTransition",
          currentState + " → " + State.IDLE + " (requestIdle, after " + timeInState + "s)");
      Logger.recordOutput("Superstructure/StateTransitionTime", now);
      Logger.recordOutput("Superstructure/StateTransitionTo", State.IDLE.name());
      stateEntryTimeSeconds = now;
      subState = "";
    }
    currentState = State.IDLE;
  }

  // ─── State queries ────────────────────────────────────────────────────────

  /** The current superstructure state. */
  public State getState() {
    return currentState;
  }

  /** Whether the superstructure has a staged game piece ready to score. */
  public boolean hasGamePiece() {
    return currentState == State.STAGING || currentState == State.SCORING;
  }

  /**
   * @return seconds since the current state was entered — useful for time-based command decisions
   *     (e.g. "don't fire until STAGING has settled for 0.2 s"). Package-private; primary exposure
   *     is via {@code Superstructure/TimeInStateSec} in AdvantageScope.
   */
  double getTimeInStateSeconds() {
    return timeSource.getAsDouble() - stateEntryTimeSeconds;
  }
}
