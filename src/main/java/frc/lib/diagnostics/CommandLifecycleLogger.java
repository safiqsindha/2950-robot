package frc.lib.diagnostics;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import java.util.HashMap;
import java.util.Map;
import org.littletonrobotics.junction.Logger;

/**
 * Hooks CommandScheduler lifecycle events (initialize / finish / interrupt) and emits them to
 * AdvantageKit. Makes every command boundary searchable in AdvantageScope log replay — essential
 * for post-match diagnosis of "why did the panic fire?" or "did the auto command actually run?".
 *
 * <p>Adapted from Team 3005 RoboChargers' Python DataLogger pattern (their 2025-offseason-python
 * repo). Pure additive observability — never changes scheduler behaviour.
 *
 * <p>Usage: call {@link #start()} once during {@code Robot.robotInit()} after {@code
 * Logger.start()} has been invoked. Idempotent; subsequent calls are no-ops.
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
  private static boolean started = false;
  private static int activeCount = 0;
  private static int totalStarted = 0;
  private static int totalInterrupted = 0;
  private static final Map<String, Double> startTimes = new HashMap<>();

  private CommandLifecycleLogger() {}

  /**
   * Registers lifecycle hooks on {@link CommandScheduler}. Idempotent — safe to call multiple
   * times, but only the first call has effect.
   */
  public static void start() {
    if (started) {
      return;
    }
    started = true;
    CommandScheduler scheduler = CommandScheduler.getInstance();
    scheduler.onCommandInitialize(CommandLifecycleLogger::onInit);
    scheduler.onCommandInterrupt(CommandLifecycleLogger::onInterrupt);
    scheduler.onCommandFinish(CommandLifecycleLogger::onFinish);
  }

  private static void onInit(Command cmd) {
    String name = cmd.getName();
    startTimes.put(name, Timer.getFPGATimestamp());
    activeCount++;
    totalStarted++;
    Logger.recordOutput("Commands/LastStarted", name);
    Logger.recordOutput("Commands/ActiveCount", activeCount);
    Logger.recordOutput("Commands/TotalStarted", totalStarted);
  }

  private static void onFinish(Command cmd) {
    String name = cmd.getName();
    activeCount = Math.max(0, activeCount - 1);
    double duration = consumeDuration(name);
    Logger.recordOutput("Commands/LastFinished", name);
    Logger.recordOutput("Commands/ActiveCount", activeCount);
    Logger.recordOutput("Commands/Durations/" + name, duration);
  }

  private static void onInterrupt(Command cmd) {
    String name = cmd.getName();
    activeCount = Math.max(0, activeCount - 1);
    totalInterrupted++;
    double duration = consumeDuration(name);
    Logger.recordOutput("Commands/LastInterrupted", name);
    Logger.recordOutput("Commands/ActiveCount", activeCount);
    Logger.recordOutput("Commands/TotalInterrupted", totalInterrupted);
    Logger.recordOutput("Commands/Durations/" + name, duration);
  }

  private static double consumeDuration(String name) {
    Double start = startTimes.remove(name);
    if (start == null) {
      return 0.0;
    }
    return Timer.getFPGATimestamp() - start;
  }
}
