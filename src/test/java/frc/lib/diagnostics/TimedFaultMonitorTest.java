package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class TimedFaultMonitorTest {

  /** Mutable fake clock — tests step forward explicitly. */
  private static class FakeClock implements DoubleSupplier {
    private double now = 0.0;

    @Override
    public double getAsDouble() {
      return now;
    }

    void advance(double seconds) {
      now += seconds;
    }
  }

  @Test
  void poll_rawTrue_withinWindow_gatedFalse() {
    AtomicBoolean raw = new AtomicBoolean(true);
    AtomicBoolean sink = new AtomicBoolean(false);
    var clock = new FakeClock();
    var m = new TimedFaultMonitor(raw::get, sink::set, 0.5, clock);
    m.poll();
    assertFalse(sink.get(), "Initial tick: inside debounce window");
    clock.advance(0.3);
    m.poll();
    assertFalse(sink.get(), "0.3s elapsed, debounce is 0.5s");
  }

  @Test
  void poll_rawTrue_pastWindow_gatedTrue() {
    AtomicBoolean sink = new AtomicBoolean(false);
    var clock = new FakeClock();
    var m = new TimedFaultMonitor(() -> true, sink::set, 0.5, clock);
    m.poll(); // start streak
    clock.advance(0.6);
    m.poll();
    assertTrue(sink.get(), "0.6s elapsed, past 0.5s debounce");
    assertTrue(m.isActive());
  }

  @Test
  void poll_rawFalse_resetsStreak() {
    AtomicBoolean raw = new AtomicBoolean(true);
    AtomicBoolean sink = new AtomicBoolean(false);
    var clock = new FakeClock();
    var m = new TimedFaultMonitor(raw::get, sink::set, 0.5, clock);
    m.poll();
    clock.advance(0.3);
    m.poll();
    raw.set(false); // excursion — signal cleared
    clock.advance(0.1);
    m.poll();
    assertFalse(sink.get());
    assertEquals(0.0, m.activeDurationSeconds(), 1e-9);

    // Now it goes back true — streak restarts from this moment.
    raw.set(true);
    clock.advance(0.1);
    m.poll(); // streak starts at t=0.5
    assertFalse(sink.get());
    clock.advance(0.5);
    m.poll(); // 0.5s into new streak → hits threshold
    assertTrue(sink.get());
  }

  @Test
  void activeDurationSeconds_tracksStreakLength() {
    var clock = new FakeClock();
    var m = new TimedFaultMonitor(() -> true, b -> {}, 0.5, clock);
    m.poll(); // t=0, streak begins
    assertEquals(0.0, m.activeDurationSeconds(), 1e-9);
    clock.advance(0.2);
    m.poll();
    assertEquals(0.2, m.activeDurationSeconds(), 1e-9);
    clock.advance(0.4);
    // Even without calling poll, the clock moved — active duration should reflect that.
    assertEquals(0.6, m.activeDurationSeconds(), 1e-9);
  }

  @Test
  void activeDurationSeconds_returnsZeroWhenIdle() {
    var m = new TimedFaultMonitor(() -> false, b -> {}, 0.5, new FakeClock());
    m.poll();
    assertEquals(0.0, m.activeDurationSeconds(), 1e-9);
  }

  @Test
  void zeroDebounce_firesImmediately() {
    AtomicBoolean sink = new AtomicBoolean(false);
    var m = new TimedFaultMonitor(() -> true, sink::set, 0.0, new FakeClock());
    m.poll();
    assertTrue(sink.get());
  }

  @Test
  void constructor_negativeDebounce_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TimedFaultMonitor(() -> true, b -> {}, -0.1, new FakeClock()));
  }
}
