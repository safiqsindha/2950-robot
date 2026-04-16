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
 *   <li>{@link State#STAGING} — Game piece acquired; staging in conveyor for scoring.
 *   <li>{@link State#SCORING} — Flywheel spinning up, feeding, or ejecting. Auto-exits to IDLE
 *       after {@link Constants.Superstructure#kScoringTimeoutSeconds} if no {@link #requestIdle()}
 *       is received, preventing a missed command from permanently locking the superstructure.
 * </ul>
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

  // Time at which the SCORING state was last entered; used to compute the auto-exit duration.
  private double scoringEntryTimeSeconds = 0.0;

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

  @Override
  public void periodic() {
    double scoringDuration =
        (currentState == State.SCORING) ? timeSource.getAsDouble() - scoringEntryTimeSeconds : 0.0;

    State nextState =
        computeNextState(
            currentState,
            intake.getWheelCurrent(),
            Constants.Superstructure.kGamePieceCurrentThresholdAmps,
            intakeRequested,
            scoreRequested,
            scoringDuration);

    if (nextState != currentState) {
      Logger.recordOutput("Superstructure/StateTransition", currentState + " → " + nextState);
      if (nextState == State.SCORING) {
        scoringEntryTimeSeconds = timeSource.getAsDouble();
      }
      currentState = nextState;
    }
    Logger.recordOutput("Superstructure/State", currentState.name());
    Logger.recordOutput("Superstructure/ScoreRequested", scoreRequested);
    Logger.recordOutput("Superstructure/IntakeRequested", intakeRequested);
    Logger.recordOutput("Superstructure/IntakeWheelCurrentAmps", intake.getWheelCurrent());
  }

  /**
   * Pure state transition logic. Package-private and static for unit testing without HAL.
   *
   * @param state current state
   * @param wheelCurrentAmps intake wheel current in amps
   * @param currentThresholdAmps game piece detection threshold in amps
   * @param intakeReq whether intake is requested
   * @param scoreReq whether score is requested
   * @param scoringDurationSeconds how long the machine has been in SCORING (0 if not in SCORING)
   * @return the next state
   */
  static State computeNextState(
      State state,
      double wheelCurrentAmps,
      double currentThresholdAmps,
      boolean intakeReq,
      boolean scoreReq,
      double scoringDurationSeconds) {
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
        return State.INTAKING;

      case STAGING:
        if (scoreReq) return State.SCORING;
        // If intake command was cancelled before scoring, return to idle
        if (!intakeReq) return State.IDLE;
        return State.STAGING;

      case SCORING:
        // Auto-exit to IDLE if the scoring window has elapsed. This prevents a missed requestIdle()
        // call from permanently locking the superstructure in the SCORING state.
        if (scoringDurationSeconds >= Constants.Superstructure.kScoringTimeoutSeconds) {
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
   */
  public void requestIdle() {
    intakeRequested = false;
    scoreRequested = false;
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
}
