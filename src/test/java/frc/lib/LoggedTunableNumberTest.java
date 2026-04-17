package frc.lib;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LoggedTunableNumber}. Follows the 2950 convention established by {@code
 * StallDetectorTest} and {@code AllianceFlipTest}: uses a package-private constructor that accepts
 * an injected {@code DoubleSupplier} so the tests never touch the NetworkTables native library.
 *
 * <p>{@code Logger.recordOutput} inside {@link LoggedTunableNumber#get()} is a no-op in the test
 * JVM because AdvantageKit's {@code Logger} is never {@code start()}ed.
 */
class LoggedTunableNumberTest {

  @Test
  void defaultValueReturnedWhenSourceReturnsDefault() {
    LoggedTunableNumber tunable = new LoggedTunableNumber("Test/defaultOnly", 42.0, () -> 42.0);

    assertEquals(
        42.0, tunable.get(), 1e-9, "Should return the source value when it matches the default");
  }

  @Test
  void sourceOverrideIsSurfacedByGet() {
    double[] backing = {5.0};
    LoggedTunableNumber tunable = new LoggedTunableNumber("Test/override", 0.0, () -> backing[0]);

    assertEquals(5.0, tunable.get(), 1e-9, "Initial source value");

    backing[0] = 17.5;
    assertEquals(17.5, tunable.get(), 1e-9, "get() should reflect the updated source value");
  }

  @Test
  void hasChangedReturnsTrueOnFirstCallThenFalse() {
    LoggedTunableNumber tunable = new LoggedTunableNumber("Test/firstCall", 7.0, () -> 7.0);

    assertTrue(
        tunable.hasChanged(1), "First hasChanged call should return true (no prior value stored)");
    assertFalse(tunable.hasChanged(1), "Second hasChanged call with no change should return false");
  }

  @Test
  void differentCallersTrackIndependently() {
    LoggedTunableNumber tunable =
        new LoggedTunableNumber("Test/multiCaller", Math.PI, () -> Math.PI);

    // Both callers see the value as changed on first call.
    assertTrue(tunable.hasChanged(1), "Caller 1 first call should be true");
    assertTrue(
        tunable.hasChanged(2), "Caller 2 first call should be true (independent of caller 1)");

    // Each caller already consumed the change.
    assertFalse(tunable.hasChanged(1), "Caller 1 second call should be false");
    assertFalse(tunable.hasChanged(2), "Caller 2 second call should be false");
  }

  @Test
  void hasChangedFlipsAfterSourceValueMutates() {
    double[] backing = {5.0};
    LoggedTunableNumber tunable = new LoggedTunableNumber("Test/mutate", 5.0, () -> backing[0]);

    // Stabilize: consume the initial "first call is always true" state.
    assertTrue(tunable.hasChanged(1), "Initial call should be true");
    assertFalse(tunable.hasChanged(1), "After stabilizing, should be false");

    // Mutate the backing source.
    backing[0] = 10.0;

    assertTrue(tunable.hasChanged(1), "hasChanged should be true after source value changed");
    assertFalse(tunable.hasChanged(1), "…and false again once the change is consumed");
  }

  @Test
  void getDefaultReturnsConstructorDefaultEvenIfSourceDrifts() {
    LoggedTunableNumber tunable = new LoggedTunableNumber("Test/default", 9.0, () -> 999.0);

    assertEquals(
        9.0, tunable.getDefault(), 1e-9, "getDefault() returns the constructor default, not get()");
  }

  @Test
  void getKeyPrependsTuningTablePrefix() {
    LoggedTunableNumber tunable = new LoggedTunableNumber("Flywheel/kP", 0.0, () -> 0.0);

    assertEquals("/Tuning/Flywheel/kP", tunable.getKey(), "Key should be prefixed with /Tuning/");
  }
}
