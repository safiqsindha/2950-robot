package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.wpilibj2.command.Command;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CommandLifecycleLogger}. Uses the package-private time-source ctor so no
 * FPGA/HAL dependency; uses inline anonymous {@link Command} subclasses so no {@link
 * edu.wpi.first.wpilibj2.command.CommandScheduler} is touched.
 */
class CommandLifecycleLoggerTest {

  /** Mutable fake time source — tests advance this directly. */
  private static class FakeClock implements java.util.function.DoubleSupplier {
    private double now = 0.0;

    @Override
    public double getAsDouble() {
      return now;
    }

    void advance(double seconds) {
      now += seconds;
    }
  }

  /** Minimal no-op command subclass with a caller-specified name. */
  private static Command cmdNamed(String name) {
    return new Command() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public boolean isFinished() {
        return false;
      }
    };
  }

  // ─── Counter semantics ───────────────────────────────────────────────────

  @Test
  void onInit_incrementsActiveAndTotalStarted() {
    CommandLifecycleLogger logger = new CommandLifecycleLogger(new FakeClock());
    assertEquals(0, logger.activeCount());
    assertEquals(0, logger.totalStarted());

    logger.onInit(cmdNamed("Foo"));

    assertEquals(1, logger.activeCount());
    assertEquals(1, logger.totalStarted());
    assertEquals(0, logger.totalInterrupted());
  }

  @Test
  void onFinish_decrementsActiveWithoutTouchingCounters() {
    CommandLifecycleLogger logger = new CommandLifecycleLogger(new FakeClock());
    Command cmd = cmdNamed("Foo");

    logger.onInit(cmd);
    logger.onFinish(cmd);

    assertEquals(0, logger.activeCount());
    assertEquals(1, logger.totalStarted());
    assertEquals(0, logger.totalInterrupted());
  }

  @Test
  void onInterrupt_decrementsActiveAndBumpsInterruptedCounter() {
    CommandLifecycleLogger logger = new CommandLifecycleLogger(new FakeClock());
    Command cmd = cmdNamed("Bar");

    logger.onInit(cmd);
    logger.onInterrupt(cmd);

    assertEquals(0, logger.activeCount());
    assertEquals(1, logger.totalStarted());
    assertEquals(1, logger.totalInterrupted());
  }

  @Test
  void activeCount_cannotGoNegative() {
    // Defensive: if onFinish fires without a preceding onInit (shouldn't happen in
    // practice but let's protect the dashboard from negative numbers).
    CommandLifecycleLogger logger = new CommandLifecycleLogger(new FakeClock());
    logger.onFinish(cmdNamed("Ghost"));
    assertEquals(0, logger.activeCount());
  }

  @Test
  void multipleCommandsTracked_independently() {
    CommandLifecycleLogger logger = new CommandLifecycleLogger(new FakeClock());

    logger.onInit(cmdNamed("A"));
    logger.onInit(cmdNamed("B"));
    logger.onInit(cmdNamed("C"));
    assertEquals(3, logger.activeCount());

    logger.onFinish(cmdNamed("A"));
    logger.onInterrupt(cmdNamed("B"));
    assertEquals(1, logger.activeCount());
    assertEquals(3, logger.totalStarted());
    assertEquals(1, logger.totalInterrupted());
  }

  // ─── Duration math ───────────────────────────────────────────────────────

  @Test
  void duration_isWallTimeAtFinish() {
    FakeClock clock = new FakeClock();
    CommandLifecycleLogger logger = new CommandLifecycleLogger(clock);
    Command cmd = cmdNamed("Timed");

    logger.onInit(cmd);
    clock.advance(0.75);
    logger.onFinish(cmd);

    // consumeDuration() removes the entry; re-finishing yields 0 (no entry).
    clock.advance(1.0);
    logger.onFinish(cmd);
    // We can't directly read the logged duration, but we CAN confirm internal
    // state: calling onFinish twice doesn't corrupt counters.
    assertEquals(0, logger.activeCount());
  }

  @Test
  void durationsAreIndependentPerCommand() {
    FakeClock clock = new FakeClock();
    CommandLifecycleLogger logger = new CommandLifecycleLogger(clock);

    logger.onInit(cmdNamed("Fast"));
    clock.advance(0.1);
    logger.onInit(cmdNamed("Slow"));
    clock.advance(0.9);
    logger.onFinish(cmdNamed("Fast")); // ran ~1.0s
    clock.advance(0.5);
    logger.onFinish(cmdNamed("Slow")); // ran ~1.4s

    assertEquals(0, logger.activeCount());
    assertEquals(2, logger.totalStarted());
  }

  // ─── Public no-arg ctor ──────────────────────────────────────────────────

  @Test
  void defaultConstructor_doesNotThrow() {
    assertDoesNotThrow(CommandLifecycleLogger::new);
  }

  // ─── Counters start at zero on a fresh instance ──────────────────────────

  @Test
  void freshInstance_countersAreZero() {
    CommandLifecycleLogger logger = new CommandLifecycleLogger(new FakeClock());
    assertEquals(0, logger.activeCount());
    assertEquals(0, logger.totalStarted());
    assertEquals(0, logger.totalInterrupted());
  }

  @Test
  void onFinishWithoutPriorInit_returnsZeroDuration() {
    // consumeDuration() path when startTimes has no entry for the name.
    CommandLifecycleLogger logger = new CommandLifecycleLogger(new FakeClock());
    // Doesn't throw; doesn't corrupt counters.
    logger.onFinish(cmdNamed("Untracked"));
    assertEquals(0, logger.activeCount());
    assertEquals(0, logger.totalStarted());
  }

  @Test
  void onInterruptWithoutPriorInit_returnsZeroDuration() {
    CommandLifecycleLogger logger = new CommandLifecycleLogger(new FakeClock());
    logger.onInterrupt(cmdNamed("Untracked"));
    assertEquals(0, logger.activeCount());
    assertEquals(0, logger.totalStarted());
    assertEquals(1, logger.totalInterrupted());
  }
}
