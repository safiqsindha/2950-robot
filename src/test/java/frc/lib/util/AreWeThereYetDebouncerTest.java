package frc.lib.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class AreWeThereYetDebouncerTest {

  /** Mutable fake time source — tests advance this directly. */
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
  void noTargetSet_returnsFalse() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0, new FakeClock());
    assertFalse(d.isAtTarget(5.0));
    assertFalse(d.hasTarget());
  }

  @Test
  void targetSetWithinTolerance_zeroDebounce_returnsTrueImmediately() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0, new FakeClock());
    d.setTarget(5.0);
    assertTrue(d.hasTarget());
    // Within tolerance, 0-second debounce → immediate pass
    assertTrue(d.isAtTarget(5.05));
  }

  @Test
  void targetSetOutOfTolerance_returnsFalse() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0, new FakeClock());
    d.setTarget(5.0);
    assertFalse(d.isAtTarget(5.5));
  }

  @Test
  void getCommandedTarget_returnsSetValue() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0, new FakeClock());
    d.setTarget(42.0);
    assertEquals(42.0, d.getCommandedTarget(), 1e-9);
  }

  @Test
  void retargetingWhileAtPreviousTarget_returnsFalseUntilRe_earned() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0, new FakeClock());
    d.setTarget(5.0);
    assertTrue(d.isAtTarget(5.0));
    d.setTarget(10.0);
    // Still at position 5.0, but target is now 10.0 — out of tolerance.
    assertFalse(d.isAtTarget(5.0));
  }

  @Test
  void sameTargetRepeated_doesNotResetWindow() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0, new FakeClock());
    d.setTarget(5.0);
    assertTrue(d.isAtTarget(5.0));
    d.setTarget(5.0); // same value — should no-op
    assertTrue(d.isAtTarget(5.0));
  }

  @Test
  void nonZeroDebounce_requiresSustainedInTolerance() {
    var clock = new FakeClock();
    var d = new AreWeThereYetDebouncer(0.1, 0.5, clock);
    d.setTarget(5.0);
    assertFalse(d.isAtTarget(5.0)); // t=0, streak begins; < 0.5 s, so false
    clock.advance(0.3);
    assertFalse(d.isAtTarget(5.0)); // 0.3 s elapsed, still too early
    clock.advance(0.3);
    assertTrue(d.isAtTarget(5.0)); // 0.6 s elapsed — passed threshold
  }

  @Test
  void nonZeroDebounce_excursionResetsWindow() {
    var clock = new FakeClock();
    var d = new AreWeThereYetDebouncer(0.1, 0.5, clock);
    d.setTarget(5.0);
    assertFalse(d.isAtTarget(5.0)); // t=0, streak begins
    clock.advance(0.3);
    assertFalse(d.isAtTarget(6.0)); // out of tolerance — streak broken
    clock.advance(0.3);
    // Back in tolerance, streak restarted at re-entry (now t=0.6)
    assertFalse(d.isAtTarget(5.0));
    clock.advance(0.6);
    assertTrue(d.isAtTarget(5.0));
  }

  @Test
  void constructor_negativeTolerance_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AreWeThereYetDebouncer(-0.1, 0.1, new FakeClock()));
  }

  @Test
  void constructor_zeroTolerance_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> new AreWeThereYetDebouncer(0, 0.1, new FakeClock()));
  }

  @Test
  void constructor_negativeDebounceSeconds_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AreWeThereYetDebouncer(0.1, -0.1, new FakeClock()));
  }

  @Test
  void defaultConstructor_doesNotThrow() {
    // No-arg-time-source ctor stores Timer::getFPGATimestamp as a lambda — it's not invoked
    // until isAtTarget is called, so construction itself is HAL-free.
    assertDoesNotThrow(() -> new AreWeThereYetDebouncer(0.1, 0.1));
  }
}
