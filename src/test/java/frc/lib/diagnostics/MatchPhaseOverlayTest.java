package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MatchPhaseOverlayTest {

  /** Mutable clock + supplier harness so the test drives every input deterministically. */
  private static class Harness {
    double remaining = 150.0;
    boolean auto = false;
    boolean teleop = false;
    double now = 0.0;

    MatchPhaseOverlay build() {
      return new MatchPhaseOverlay(() -> remaining, () -> auto, () -> teleop, () -> now, 30.0);
    }
  }

  @Test
  void disabledByDefault() {
    var h = new Harness();
    var snap = h.build().collect();
    assertEquals("DISABLED", snap.label());
    assertFalse(snap.endgameActive());
  }

  @Test
  void auto_labelledAuto() {
    var h = new Harness();
    h.auto = true;
    assertEquals("AUTO", h.build().collect().label());
  }

  @Test
  void teleopOutsideEndgame_labelledTeleop() {
    var h = new Harness();
    h.teleop = true;
    h.remaining = 90.0;
    var snap = h.build().collect();
    assertEquals("TELEOP", snap.label());
    assertFalse(snap.endgameActive());
  }

  @Test
  void teleopInsideEndgame_labelledEndgame() {
    var h = new Harness();
    h.teleop = true;
    h.remaining = 20.0; // < 30 s threshold
    var snap = h.build().collect();
    assertEquals("ENDGAME", snap.label());
    assertTrue(snap.endgameActive());
  }

  @Test
  void phaseTransition_resetsElapsed() {
    var h = new Harness();
    var overlay = h.build();
    // Start in DISABLED at t=0
    overlay.collect();
    h.now = 3.0;
    // Transition to AUTO
    h.auto = true;
    var snap = overlay.collect();
    assertEquals("AUTO", snap.label());
    assertEquals(0.0, snap.elapsedInPhaseSeconds(), 1e-9);
    // Stay in AUTO for 2 more seconds
    h.now = 5.0;
    assertEquals(2.0, overlay.collect().elapsedInPhaseSeconds(), 1e-9);
  }

  @Test
  void endgame_onlyDuringTeleop_notAuto() {
    // Even if remaining is small during AUTO, we shouldn't flag ENDGAME.
    var h = new Harness();
    h.auto = true;
    h.remaining = 5.0;
    assertEquals("AUTO", h.build().collect().label());
  }

  @Test
  void negativeRemaining_notEndgame() {
    // DriverStation returns -1 when no match data is available. Must not trigger endgame.
    var h = new Harness();
    h.teleop = true;
    h.remaining = -1.0;
    assertEquals("TELEOP", h.build().collect().label());
  }

  @Test
  void periodic_doesNotThrow() {
    var overlay = new Harness().build();
    assertDoesNotThrow(overlay::periodic);
  }
}
