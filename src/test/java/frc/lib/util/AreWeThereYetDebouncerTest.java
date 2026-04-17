package frc.lib.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AreWeThereYetDebouncerTest {

  @Test
  void noTargetSet_returnsFalse() {
    // Debouncer with 0 s window (fires immediately when within tolerance), but no target yet.
    var d = new AreWeThereYetDebouncer(0.1, 0.0);
    assertFalse(d.isAtTarget(5.0));
    assertFalse(d.hasTarget());
  }

  @Test
  void targetSetWithinTolerance_zeroDebounce_returnsTrueImmediately() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0);
    d.setTarget(5.0);
    assertTrue(d.hasTarget());
    // Within tolerance, 0-second debounce → immediate pass
    assertTrue(d.isAtTarget(5.05));
  }

  @Test
  void targetSetOutOfTolerance_returnsFalse() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0);
    d.setTarget(5.0);
    assertFalse(d.isAtTarget(5.5));
  }

  @Test
  void getCommandedTarget_returnsSetValue() {
    var d = new AreWeThereYetDebouncer(0.1, 0.0);
    d.setTarget(42.0);
    assertEquals(42.0, d.getCommandedTarget(), 1e-9);
  }

  @Test
  void retargetingWhileAtPreviousTarget_returnsFalseUntilRe_earned() {
    // At target A. Caller changes target to B (far from current reading). Expect false even
    // though we were just true for A.
    var d = new AreWeThereYetDebouncer(0.1, 0.0);
    d.setTarget(5.0);
    assertTrue(d.isAtTarget(5.0));
    d.setTarget(10.0);
    // Still at position 5.0, but target is now 10.0 — out of tolerance.
    assertFalse(d.isAtTarget(5.0));
  }

  @Test
  void sameTargetRepeated_doesNotResetDebouncer() {
    // Calling setTarget(same value) should NOT reset the debounce.
    var d = new AreWeThereYetDebouncer(0.1, 0.0);
    d.setTarget(5.0);
    assertTrue(d.isAtTarget(5.0));
    d.setTarget(5.0); // re-assert same target
    assertTrue(d.isAtTarget(5.0)); // should still be true
  }

  @Test
  void constructor_negativeTolerance_throws() {
    assertThrows(IllegalArgumentException.class, () -> new AreWeThereYetDebouncer(-0.1, 0.1));
  }

  @Test
  void constructor_zeroTolerance_throws() {
    assertThrows(IllegalArgumentException.class, () -> new AreWeThereYetDebouncer(0, 0.1));
  }

  @Test
  void constructor_negativeDebounceSeconds_throws() {
    assertThrows(IllegalArgumentException.class, () -> new AreWeThereYetDebouncer(0.1, -0.1));
  }
}
