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

  // NOTE: the public (String, AlertType, BooleanSupplier) constructor creates a WPILib Alert
  // which transitively loads HAL native code. Exercising it needs the HAL-init canary pattern
  // (see FlywheelIOSimPhysicsTest). Left as a pattern for future HAL-enabled tests.
}
