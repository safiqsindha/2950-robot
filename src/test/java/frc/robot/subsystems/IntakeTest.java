package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.*;

import frc.robot.Constants;
import org.junit.jupiter.api.Test;

/**
 * HAL-free tests for the Phase 4.2 Intake IO-layer refactor.
 *
 * <p>{@link IntakeIOSim} performs only pure Java arithmetic in {@link
 * IntakeIOSim#updateInputs(IntakeIO.IntakeIOInputs)} — no WPILib native libraries are loaded — so
 * this test class can exercise the full simulation contract including current synthesis.
 *
 * <p>{@link Intake} extends {@code SubsystemBase} and requires HAL initialisation, so the thin
 * consumer itself is not instantiated here. The IO layer contract is verified directly.
 */
class IntakeTest {

  // ─── IntakeIOInputs contract ─────────────────────────────────────────────

  @Test
  void inputs_defaultsAreZeroAndFalse() {
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    assertFalse(inputs.connected, "connected must default to false");
    assertEquals(0.0, inputs.leftArmPositionRotations, 1e-9);
    assertEquals(0.0, inputs.rightArmPositionRotations, 1e-9);
    assertEquals(0.0, inputs.wheelCurrentAmps, 1e-9);
    assertEquals(0.0, inputs.wheelAppliedVoltage, 1e-9);
  }

  // ─── IntakeIOSim: wheel current synthesis ────────────────────────────────

  @Test
  void simIO_wheelCurrentIsZeroWithNoGamePiece() {
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.setWheel(1.0); // wheel spinning at full output
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(
        0.0,
        inputs.wheelCurrentAmps,
        1e-9,
        "No game piece → current must be zero despite wheel spinning");
  }

  @Test
  void simIO_wheelCurrentSpikeWhenGamePieceAcquired() {
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.setWheel(1.0);
    simIO.simulateGamePieceAcquired();
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    simIO.updateInputs(inputs);
    assertTrue(
        inputs.wheelCurrentAmps > 0,
        "After simulateGamePieceAcquired, wheel current must be positive at full output");
  }

  @Test
  void simIO_wheelCurrentExceedsThresholdAtFullOutput() {
    // At full wheel output with game piece injected, current must exceed the SSM threshold (15 A).
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.setWheel(1.0);
    simIO.simulateGamePieceAcquired();
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    simIO.updateInputs(inputs);
    double threshold = Constants.Superstructure.kGamePieceCurrentThresholdAmps;
    assertTrue(
        inputs.wheelCurrentAmps > threshold,
        "Injected current at full output must exceed SSM threshold ("
            + threshold
            + " A): got "
            + inputs.wheelCurrentAmps);
  }

  @Test
  void simIO_wheelCurrentIsProportionalToWheelOutput() {
    // Current at 50% wheel output must be half of current at 100%.
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.simulateGamePieceAcquired();
    IntakeIO.IntakeIOInputs fullInputs = new IntakeIO.IntakeIOInputs();
    IntakeIO.IntakeIOInputs halfInputs = new IntakeIO.IntakeIOInputs();
    simIO.setWheel(1.0);
    simIO.updateInputs(fullInputs);
    simIO.setWheel(0.5);
    simIO.updateInputs(halfInputs);
    assertEquals(
        fullInputs.wheelCurrentAmps,
        halfInputs.wheelCurrentAmps * 2.0,
        1e-9,
        "Wheel current must be proportional to percent output");
  }

  @Test
  void simIO_consumedGamePieceClearsCurrentSpike() {
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.setWheel(1.0);
    simIO.simulateGamePieceAcquired();
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    simIO.updateInputs(inputs);
    double currentWithPiece = inputs.wheelCurrentAmps;
    assertTrue(currentWithPiece > 0, "Precondition: current must be positive with game piece");

    simIO.simulateGamePieceConsumed();
    simIO.updateInputs(inputs);
    assertEquals(
        0.0,
        inputs.wheelCurrentAmps,
        1e-9,
        "After simulateGamePieceConsumed, current must return to zero");
  }

  @Test
  void simIO_acquireConsumeAcquire_cycleSynthesesCurrent() {
    // Second acquisition cycle must re-enable current spike.
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.setWheel(1.0);
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();

    simIO.simulateGamePieceAcquired();
    simIO.updateInputs(inputs);
    assertTrue(inputs.wheelCurrentAmps > 0, "First acquisition must produce current");

    simIO.simulateGamePieceConsumed();
    simIO.updateInputs(inputs);
    assertEquals(0.0, inputs.wheelCurrentAmps, 1e-9, "After consume, current must be zero");

    simIO.simulateGamePieceAcquired();
    simIO.updateInputs(inputs);
    assertTrue(inputs.wheelCurrentAmps > 0, "Second acquisition must produce current again");
  }

  // ─── IntakeIOSim: arm position tracking ──────────────────────────────────

  @Test
  void simIO_armPositionTracksSetpoint() {
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.updateTargetAngle(5.0);
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(5.0, inputs.leftArmPositionRotations, 1e-9);
    assertEquals(5.0, inputs.rightArmPositionRotations, 1e-9);
  }

  @Test
  void simIO_resetEncoderSetsArmPositionToZero() {
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.updateTargetAngle(7.5);
    simIO.resetEncoder();
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(
        0.0,
        inputs.leftArmPositionRotations,
        1e-9,
        "resetEncoder must zero the tracked arm position");
    assertEquals(
        0.0,
        inputs.rightArmPositionRotations,
        1e-9,
        "resetEncoder must zero the tracked arm position");
  }

  // ─── IntakeIOSim: miscellaneous ───────────────────────────────────────────

  @Test
  void simIO_connectedAlwaysTrue() {
    IntakeIOSim simIO = new IntakeIOSim();
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    simIO.updateInputs(inputs);
    assertTrue(inputs.connected, "IntakeIOSim must always report connected = true");
  }

  @Test
  void simIO_wheelAppliedVoltageProportionalToPercent() {
    IntakeIOSim simIO = new IntakeIOSim();
    simIO.setWheel(0.5);
    IntakeIO.IntakeIOInputs inputs = new IntakeIO.IntakeIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(
        6.0,
        inputs.wheelAppliedVoltage,
        1e-9,
        "50% wheel output at 12 V must produce 6 V applied voltage");
  }

  @Test
  void simIO_constructorDoesNotThrow() {
    assertDoesNotThrow(IntakeIOSim::new);
  }
}
