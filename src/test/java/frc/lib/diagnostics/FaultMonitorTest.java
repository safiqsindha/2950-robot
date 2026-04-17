package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class FaultMonitorTest {

  @Test
  void poll_supplierTrue_sinkReceivesTrue() {
    AtomicBoolean sink = new AtomicBoolean(false);
    FaultMonitor m = new FaultMonitor(() -> true, sink::set);
    m.poll();
    assertTrue(sink.get());
    assertTrue(m.isActive());
  }

  @Test
  void poll_supplierFalse_sinkReceivesFalse() {
    AtomicBoolean sink = new AtomicBoolean(true); // start polluted
    FaultMonitor m = new FaultMonitor(() -> false, sink::set);
    m.poll();
    assertFalse(sink.get());
    assertFalse(m.isActive());
  }

  @Test
  void poll_calledEveryTick() {
    AtomicInteger counter = new AtomicInteger(0);
    BooleanSupplier check = () -> {
      counter.incrementAndGet();
      return false;
    };
    FaultMonitor m = new FaultMonitor(check, b -> {});
    m.poll();
    m.poll();
    m.poll();
    assertEquals(3, counter.get());
  }

  @Test
  void poll_stateFollowsFlappingInput() {
    AtomicBoolean raw = new AtomicBoolean(false);
    AtomicBoolean sink = new AtomicBoolean(false);
    FaultMonitor m = new FaultMonitor(raw::get, sink::set);

    m.poll();
    assertFalse(sink.get());

    raw.set(true);
    m.poll();
    assertTrue(sink.get());

    raw.set(false);
    m.poll();
    assertFalse(sink.get()); // instant variant — flips immediately
  }

  @Test
  void isActive_beforePoll_returnsFalse() {
    FaultMonitor m = new FaultMonitor(() -> true, b -> {});
    assertFalse(m.isActive()); // lastState defaults to false until poll() runs
  }

  @Test
  void poll_supplierReentrancy_isolatesState() {
    // A fault supplier that flips its return value each call — verify the monitor reflects
    // exactly the latest return, not any cached state.
    AtomicInteger tick = new AtomicInteger(0);
    AtomicBoolean sink = new AtomicBoolean(false);
    FaultMonitor m = new FaultMonitor(() -> (tick.getAndIncrement() % 2) == 0, sink::set);

    m.poll(); // tick 0 — supplier returns true
    assertTrue(sink.get());
    assertTrue(m.isActive());

    m.poll(); // tick 1 — supplier returns false
    assertFalse(sink.get());
    assertFalse(m.isActive());

    m.poll(); // tick 2 — supplier returns true
    assertTrue(sink.get());
  }

  @Test
  void poll_sinkException_propagatesNotSwallowed() {
    // If the sink throws, poll() must propagate — callers need to know something is wrong with
    // their Alert wiring. The monitor itself does not try to paper over the failure.
    FaultMonitor m =
        new FaultMonitor(
            () -> true,
            b -> {
              throw new IllegalStateException("sink failure");
            });
    assertThrows(IllegalStateException.class, m::poll);
  }

  @Test
  void poll_supplierException_propagatesNotSwallowed() {
    // Same for the check supplier.
    FaultMonitor m =
        new FaultMonitor(
            () -> {
              throw new IllegalStateException("check failure");
            },
            b -> {});
    assertThrows(IllegalStateException.class, m::poll);
  }

  // NOTE: the public (String, AlertType, BooleanSupplier) constructor creates a WPILib Alert
  // which transitively loads HAL native code. Exercising it needs the HAL-init canary pattern
  // (see FlywheelIOSimPhysicsTest). Left as a pattern for future HAL-enabled tests.
}
