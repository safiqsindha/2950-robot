package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * HAL-free tests for {@link ClimberIOSim}. The climber subsystem itself is HAL-coupled
 * ({@code SubsystemBase} registers with CommandScheduler) and is verified in sim/hardware; these
 * tests exercise the IO-layer contract directly.
 */
class ClimberTest {

  @Test
  void inputs_defaultsAreZeroAndFalse() {
    ClimberIO.ClimberIOInputs inputs = new ClimberIO.ClimberIOInputs();
    assertFalse(inputs.connected);
    assertEquals(0.0, inputs.positionRotations, 1e-9);
    assertEquals(0.0, inputs.velocityRotationsPerSec, 1e-9);
    assertEquals(0.0, inputs.currentAmps, 1e-9);
    assertEquals(0.0, inputs.appliedVoltage, 1e-9);
  }

  @Test
  void simIO_connectsAndReportsPosition() {
    ClimberIOSim sim = new ClimberIOSim();
    ClimberIO.ClimberIOInputs inputs = new ClimberIO.ClimberIOInputs();
    sim.setTargetPosition(10.0);
    // One tick gets us part-way there due to the first-order lag model.
    sim.updateInputs(inputs);
    assertTrue(inputs.connected);
    assertTrue(inputs.positionRotations > 0);
    assertTrue(inputs.positionRotations < 10.0);
  }

  @Test
  void simIO_multipleTicks_settlesOnTarget() {
    ClimberIOSim sim = new ClimberIOSim();
    ClimberIO.ClimberIOInputs inputs = new ClimberIO.ClimberIOInputs();
    sim.setTargetPosition(5.0);
    // ~10τ is enough for the first-order to settle within 0.01% of target.
    for (int i = 0; i < 200; i++) {
      sim.updateInputs(inputs);
    }
    assertEquals(5.0, inputs.positionRotations, 0.01);
  }

  @Test
  void simIO_stop_freezesPosition() {
    ClimberIOSim sim = new ClimberIOSim();
    ClimberIO.ClimberIOInputs inputs = new ClimberIO.ClimberIOInputs();
    sim.setTargetPosition(5.0);
    for (int i = 0; i < 50; i++) {
      sim.updateInputs(inputs);
    }
    double positionAtStop = inputs.positionRotations;
    sim.stop();
    for (int i = 0; i < 50; i++) {
      sim.updateInputs(inputs);
    }
    assertEquals(positionAtStop, inputs.positionRotations, 1e-3);
  }

  @Test
  void simIO_openLoopOverrideThenClosed_bothWork() {
    ClimberIOSim sim = new ClimberIOSim();
    ClimberIO.ClimberIOInputs inputs = new ClimberIO.ClimberIOInputs();
    sim.setPercentOutput(0.5);
    sim.updateInputs(inputs);
    assertTrue(inputs.currentAmps > 0);
    sim.setTargetPosition(10.0);
    // Transition back to closed-loop should not throw.
    assertDoesNotThrow(() -> sim.updateInputs(inputs));
  }

  @Test
  void simIO_constructor_doesNotThrow() {
    assertDoesNotThrow(ClimberIOSim::new);
  }
}
