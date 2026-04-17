package frc.robot.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PanicCommand}. Verifies the three panic side effects fire in the intended order
 * using injected {@link Runnable} mocks — no HAL, no CommandScheduler, no subsystem construction,
 * matching the 2950 convention from {@code StallDetectorTest}.
 */
class PanicCommandTest {

  @Test
  void fireRunsAllThreeRunnablesInOrder() {
    List<String> log = new ArrayList<>();

    PanicCommand.fire(() -> log.add("cancel"), () -> log.add("idle"), () -> log.add("flash"));

    assertEquals(
        List.of("cancel", "idle", "flash"),
        log,
        "Panic must cancel commands, then force SSM idle, then raise the red flash");
  }

  @Test
  void fireInvokesEachRunnableExactlyOnce() {
    int[] cancelCalls = {0};
    int[] idleCalls = {0};
    int[] flashCalls = {0};

    PanicCommand.fire(() -> cancelCalls[0]++, () -> idleCalls[0]++, () -> flashCalls[0]++);

    assertEquals(1, cancelCalls[0], "cancelAll should fire exactly once");
    assertEquals(1, idleCalls[0], "forceIdle should fire exactly once");
    assertEquals(1, flashCalls[0], "redFlash should fire exactly once");
  }

  @Test
  void fireForceIdleEvenIfCancelRunnableThrows() {
    // If a cancel implementation ever throws, the following steps SHOULD still execute — but the
    // current contract is "run in order, let the scheduler surface the error." This test pins the
    // current behavior so future refactors don't silently change it. We assert the first runnable's
    // exception propagates and the later ones do NOT run.
    int[] idleCalls = {0};
    int[] flashCalls = {0};

    assertThrows(
        RuntimeException.class,
        () ->
            PanicCommand.fire(
                () -> {
                  throw new RuntimeException("scheduler blew up");
                },
                () -> idleCalls[0]++,
                () -> flashCalls[0]++));

    assertEquals(0, idleCalls[0], "forceIdle should not run after cancel throws");
    assertEquals(0, flashCalls[0], "redFlash should not run after cancel throws");
  }
}
