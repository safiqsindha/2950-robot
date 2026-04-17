package frc.lib.diagnostics;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * Tracks CommandScheduler lifecycle events (initialize / finish / interrupt) and emits them to
 * AdvantageKit. Makes every command boundary searchable in AdvantageScope log replay — essential
 * for post-match diagnosis of "why did the panic fire?" or "did the auto command actually run?".
 *
 * <p>Adapted from Team 3005 RoboChargers' Python DataLogger pattern. Pure additive observability —
 * never changes scheduler behaviour.
 *
 * <p>This class intentionally does <b>not</b> wire itself to {@code CommandScheduler}; the consumer
 * (typically {@code Robot.robotInit()}) is responsible for that wiring. Keeping the HAL-coupled
 * plumbing out of this class makes it 100% testable without bringing up WPILib native.
 *
 * <p>Usage (in {@code Robot.robotInit()} after {@code Logger.start()}):
 *
 * <pre>{@code
 * CommandLifecycleLogger commandLogger = new CommandLifecycleLogger();
 * CommandScheduler sch = CommandScheduler.getInstance();
 * sch.onCommandInitialize(commandLogger::onInit);
 * sch.onCommandInterrupt(commandLogger::onInterrupt);
 * sch.onCommandFinish(commandLogger::onFinish);
 * }</pre>
 *
 * <p>Log keys emitted to AdvantageKit:
 *
 * <ul>
 *   <li>{@code Commands/LastStarted} — name of the most recently initialised command
 *   <li>{@code Commands/LastFinished} — name of the most recently finished command
 *   <li>{@code Commands/LastInterrupted} — name of the most recently interrupted command
 *   <li>{@code Commands/ActiveCount} — current count of scheduled commands
 *   <li>{@code Commands/TotalStarted} — cumulative initialization count
 *   <li>{@code Commands/TotalInterrupted} — cumulative interruption count
 *   <li>{@code Commands/Durations/<name>} — seconds the last run of {@code <name>} took
 * </ul>
 */
public final class CommandLifecycleLogger {

  private final DoubleSupplier timeSource;
  private final Map<String, Double> startTimes = new HashMap<>();
  private int activeCount = 0;
  private int totalStarted = 0;
  private int totalInterrupted = 0;

  /** Creates a logger using FPGA time. */
  public CommandLifecycleLogger() {
    this(Timer::getFPGATimestamp);
  }

  /** Package-private ctor that injects a time source — unit tests use this. */
  CommandLifecycleLogger(DoubleSupplier timeSource) {
    this.timeSource = timeSource;
  }

  /** Called by {@code CommandScheduler.onCommandInitialize}. */
  public void onInit(Command cmd) {
    String name = cmd.getName();
    startTimes.put(name, timeSource.getAsDouble());
    activeCount++;
    totalStarted++;
    Logger.recordOutput("Commands/LastStarted", name);
    Logger.recordOutput("Commands/ActiveCount", activeCount);
    Logger.recordOutput("Commands/TotalStarted", totalStarted);
  }

  /** Called by {@code CommandScheduler.onCommandFinish}. */
  public void onFinish(Command cmd) {
    String name = cmd.getName();
    activeCount = Math.max(0, activeCount - 1);
    double duration = consumeDuration(name);
    Logger.recordOutput("Commands/LastFinished", name);
    Logger.recordOutput("Commands/ActiveCount", activeCount);
    Logger.recordOutput("Commands/Durations/" + name, duration);
  }

  /** Called by {@code CommandScheduler.onCommandInterrupt}. */
  public void onInterrupt(Command cmd) {
    String name = cmd.getName();
    activeCount = Math.max(0, activeCount - 1);
    totalInterrupted++;
    double duration = consumeDuration(name);
    Logger.recordOutput("Commands/LastInterrupted", name);
    Logger.recordOutput("Commands/ActiveCount", activeCount);
    Logger.recordOutput("Commands/TotalInterrupted", totalInterrupted);
    Logger.recordOutput("Commands/Durations/" + name, duration);
  }

  private double consumeDuration(String name) {
    Double start = startTimes.remove(name);
    if (start == null) {
      return 0.0;
    }
    return timeSource.getAsDouble() - start;
  }

  // ─── Getters for unit tests + external introspection ─────────────────────

  /** Number of commands currently scheduled (seen init but not yet finish/interrupt). */
  public int activeCount() {
    return activeCount;
  }

  /** Cumulative count of command initialisations since construction. */
  public int totalStarted() {
    return totalStarted;
  }

  /** Cumulative count of interruptions since construction. */
  public int totalInterrupted() {
    return totalInterrupted;
  }
}
