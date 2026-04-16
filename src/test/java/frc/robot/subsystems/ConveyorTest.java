package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * HAL-free tests for the Phase 4.3 Conveyor IO-layer refactor.
 *
 * <p>{@link ConveyorIOSim} performs only pure Java assignments in {@link
 * ConveyorIOSim#updateInputs(ConveyorIO.ConveyorIOInputs)} — no WPILib native libraries are loaded
 * — so the full simulation contract is exercisable in the headless test JVM.
 *
 * <p>{@link Conveyor} extends {@code SubsystemBase} and requires HAL initialisation, so the thin
 * consumer itself is not instantiated here. The IO layer contract is verified directly.
 */
class ConveyorTest {

  // ─── ConveyorIOInputs contract ────────────────────────────────────────────

  @Test
  void inputs_defaultsAreZeroAndFalse() {
    ConveyorIO.ConveyorIOInputs inputs = new ConveyorIO.ConveyorIOInputs();
    assertFalse(inputs.connected, "connected must default to false");
    assertEquals(0.0, inputs.conveyorAppliedOutput, 1e-9);
    assertEquals(0.0, inputs.spindexerAppliedOutput, 1e-9);
    assertEquals(0.0, inputs.conveyorCurrentAmps, 1e-9);
    assertEquals(0.0, inputs.spindexerCurrentAmps, 1e-9);
  }

  // ─── ConveyorIOSim: percent output tracking ───────────────────────────────

  @Test
  void simIO_setConveyorReflectedInInputs() {
    ConveyorIOSim simIO = new ConveyorIOSim();
    simIO.setConveyor(0.75);
    ConveyorIO.ConveyorIOInputs inputs = new ConveyorIO.ConveyorIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(
        0.75, inputs.conveyorAppliedOutput, 1e-9, "conveyorAppliedOutput must match setConveyor()");
    assertEquals(
        0.75,
        inputs.spindexerAppliedOutput,
        1e-9,
        "spindexerAppliedOutput must match setConveyor()");
  }

  @Test
  void simIO_stopZerosBothMotors() {
    ConveyorIOSim simIO = new ConveyorIOSim();
    simIO.setConveyor(0.9);
    simIO.stop();
    ConveyorIO.ConveyorIOInputs inputs = new ConveyorIO.ConveyorIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(
        0.0, inputs.conveyorAppliedOutput, 1e-9, "stop() must zero conveyor applied output");
    assertEquals(
        0.0, inputs.spindexerAppliedOutput, 1e-9, "stop() must zero spindexer applied output");
  }

  @Test
  void simIO_negativePercentOutput() {
    // Negative percent output (reverse) must be stored and reported accurately.
    ConveyorIOSim simIO = new ConveyorIOSim();
    simIO.setConveyor(-0.5);
    ConveyorIO.ConveyorIOInputs inputs = new ConveyorIO.ConveyorIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(-0.5, inputs.conveyorAppliedOutput, 1e-9);
    assertEquals(-0.5, inputs.spindexerAppliedOutput, 1e-9);
  }

  @Test
  void simIO_zeroPercentOutput() {
    ConveyorIOSim simIO = new ConveyorIOSim();
    simIO.setConveyor(0.0);
    ConveyorIO.ConveyorIOInputs inputs = new ConveyorIO.ConveyorIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(0.0, inputs.conveyorAppliedOutput, 1e-9);
    assertEquals(0.0, inputs.spindexerAppliedOutput, 1e-9);
  }

  // ─── ConveyorIOSim: current always zero in sim ────────────────────────────

  @Test
  void simIO_motorCurrentsAlwaysZero() {
    // SPARK MAX getOutputCurrent() returns 0 in simulation; ConveyorIOSim must match.
    ConveyorIOSim simIO = new ConveyorIOSim();
    simIO.setConveyor(1.0);
    ConveyorIO.ConveyorIOInputs inputs = new ConveyorIO.ConveyorIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(
        0.0, inputs.conveyorCurrentAmps, 1e-9, "Conveyor motor current must be 0 in simulation");
    assertEquals(
        0.0, inputs.spindexerCurrentAmps, 1e-9, "Spindexer motor current must be 0 in simulation");
  }

  @Test
  void simIO_connectedAlwaysTrue() {
    ConveyorIOSim simIO = new ConveyorIOSim();
    ConveyorIO.ConveyorIOInputs inputs = new ConveyorIO.ConveyorIOInputs();
    simIO.updateInputs(inputs);
    assertTrue(inputs.connected, "ConveyorIOSim must always report connected = true");
  }

  @Test
  void simIO_constructorDoesNotThrow() {
    assertDoesNotThrow(ConveyorIOSim::new);
  }

  @Test
  void simIO_setConveyorAfterStopRestoresOutput() {
    ConveyorIOSim simIO = new ConveyorIOSim();
    simIO.setConveyor(0.8);
    simIO.stop();
    simIO.setConveyor(0.6);
    ConveyorIO.ConveyorIOInputs inputs = new ConveyorIO.ConveyorIOInputs();
    simIO.updateInputs(inputs);
    assertEquals(
        0.6,
        inputs.conveyorAppliedOutput,
        1e-9,
        "setConveyor after stop must restore output to new value");
  }
}
