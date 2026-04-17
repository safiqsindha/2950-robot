package frc.lib.diagnostics;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * Annotates raw match time with useful derived flags — elapsed seconds, endgame-active flag, and a
 * "phase" label that combines {@code DriverStation.isAutonomous()} / {@code isTeleop()}. A single
 * call-site in {@code Robot.robotPeriodic()} instead of scattering the same booleans across every
 * subsystem.
 *
 * <p>HAL-free via supplier injection — pass {@code DriverStation::getMatchTime} and {@code
 * DriverStation::isAutonomous} in production; pass lambdas in tests.
 *
 * <p>Log keys (all under {@code Match/}):
 *
 * <ul>
 *   <li>{@code Match/Remaining} — seconds left (direct from DriverStation)
 *   <li>{@code Match/ElapsedInPhase} — seconds since the last phase transition
 *   <li>{@code Match/EndgameActive} — true when remaining < {@code endgameThresholdSeconds}
 *   <li>{@code Match/PhaseLabel} — "AUTO", "TELEOP", "ENDGAME", or "DISABLED"
 * </ul>
 */
public final class MatchPhaseOverlay {

  /** Default endgame start — last 30 s of teleop. */
  public static final double DEFAULT_ENDGAME_THRESHOLD_SECONDS = 30.0;

  private final DoubleSupplier matchTimeRemainingSupplier;
  private final BooleanSupplier isAutonomousSupplier;
  private final BooleanSupplier isTeleopSupplier;
  private final DoubleSupplier nowSecondsSupplier;
  private final double endgameThresholdSeconds;

  private String lastLabel = "DISABLED";
  private double labelStartSeconds = 0.0;

  /**
   * @param matchTimeRemainingSupplier typically {@code DriverStation::getMatchTime}
   * @param isAutonomousSupplier typically {@code DriverStation::isAutonomous}
   * @param isTeleopSupplier typically {@code DriverStation::isTeleop}
   * @param nowSecondsSupplier typically {@code Timer::getFPGATimestamp}
   * @param endgameThresholdSeconds remaining-seconds cutoff to flag endgame
   */
  public MatchPhaseOverlay(
      DoubleSupplier matchTimeRemainingSupplier,
      BooleanSupplier isAutonomousSupplier,
      BooleanSupplier isTeleopSupplier,
      DoubleSupplier nowSecondsSupplier,
      double endgameThresholdSeconds) {
    this.matchTimeRemainingSupplier = matchTimeRemainingSupplier;
    this.isAutonomousSupplier = isAutonomousSupplier;
    this.isTeleopSupplier = isTeleopSupplier;
    this.nowSecondsSupplier = nowSecondsSupplier;
    this.endgameThresholdSeconds = endgameThresholdSeconds;
  }

  /** Tick once per {@code robotPeriodic}. Cheap — four suppliers + four log writes. */
  public void periodic() {
    Snapshot s = collect();
    Logger.recordOutput("Match/Remaining", s.remainingSeconds());
    Logger.recordOutput("Match/ElapsedInPhase", s.elapsedInPhaseSeconds());
    Logger.recordOutput("Match/EndgameActive", s.endgameActive());
    Logger.recordOutput("Match/PhaseLabel", s.label());
  }

  /** Package-private for tests. */
  Snapshot collect() {
    double remaining = matchTimeRemainingSupplier.getAsDouble();
    double now = nowSecondsSupplier.getAsDouble();
    boolean auto = isAutonomousSupplier.getAsBoolean();
    boolean teleop = isTeleopSupplier.getAsBoolean();
    boolean endgame = teleop && remaining >= 0 && remaining < endgameThresholdSeconds;
    String label;
    if (auto) {
      label = "AUTO";
    } else if (endgame) {
      label = "ENDGAME";
    } else if (teleop) {
      label = "TELEOP";
    } else {
      label = "DISABLED";
    }
    if (!label.equals(lastLabel)) {
      lastLabel = label;
      labelStartSeconds = now;
    }
    return new Snapshot(remaining, now - labelStartSeconds, endgame, label);
  }

  record Snapshot(
      double remainingSeconds, double elapsedInPhaseSeconds, boolean endgameActive, String label) {}
}
