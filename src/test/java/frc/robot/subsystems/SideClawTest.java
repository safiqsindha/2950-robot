package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SideClawTest {

  @Test
  void inputs_defaultsAreZero() {
    SideClawIO.SideClawIOInputs inputs = new SideClawIO.SideClawIOInputs();
    assertFalse(inputs.connected);
    assertEquals(0.0, inputs.currentAmps, 1e-9);
    assertEquals(0.0, inputs.appliedVoltage, 1e-9);
  }

  @Test
  void simIO_percentMapsToCurrent() {
    SideClawIOSim sim = new SideClawIOSim();
    SideClawIO.SideClawIOInputs inputs = new SideClawIO.SideClawIOInputs();
    sim.setPercentOutput(0.5);
    sim.updateInputs(inputs);
    assertEquals(7.5, inputs.currentAmps, 1e-9);
    assertEquals(6.0, inputs.appliedVoltage, 1e-9);
  }

  @Test
  void simIO_stopZerosCurrent() {
    SideClawIOSim sim = new SideClawIOSim();
    SideClawIO.SideClawIOInputs inputs = new SideClawIO.SideClawIOInputs();
    sim.setPercentOutput(1.0);
    sim.updateInputs(inputs);
    assertTrue(inputs.currentAmps > 0);
    sim.stop();
    sim.updateInputs(inputs);
    assertEquals(0.0, inputs.currentAmps, 1e-9);
  }

  @Test
  void simIO_negativeOutput_reportsPositiveCurrent() {
    SideClawIOSim sim = new SideClawIOSim();
    SideClawIO.SideClawIOInputs inputs = new SideClawIO.SideClawIOInputs();
    sim.setPercentOutput(-0.4);
    sim.updateInputs(inputs);
    assertEquals(6.0, inputs.currentAmps, 1e-9);
    assertEquals(-4.8, inputs.appliedVoltage, 1e-9);
  }

  @Test
  void simIO_constructor_doesNotThrow() {
    assertDoesNotThrow(SideClawIOSim::new);
  }
}
