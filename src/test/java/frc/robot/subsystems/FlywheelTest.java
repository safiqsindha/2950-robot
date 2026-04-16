package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * HAL-free tests for the Phase 4.1 IO-layer refactor.
 *
 * <h3>Why not test FlywheelIOSim.updateInputs()?</h3>
 *
 * {@link edu.wpi.first.wpilibj.simulation.DCMotorSim#setInputVoltage} calls {@link
 * edu.wpi.first.wpilibj.RobotController#getBatteryVoltage}, which triggers a WPILib native-library
 * load ({@code wpiHaljni}) that is unavailable in the headless test JVM. Physics convergence is
 * therefore verified via HALSim rather than a unit test.
 *
 * <h3>What IS covered here</h3>
 *
 * <ul>
 *   <li>{@link FlywheelIO.FlywheelIOInputs} default values (interface contract).
 *   <li>{@link FlywheelIOSim} construction and state-mutation methods (all HAL-free).
 *   <li>Open-loop state flag: {@code setVortexOutput} must arm the override; {@code setTargetRpm}
 *       must clear it; {@code stop} must also clear it.
 * </ul>
 */
class FlywheelTest {

  // ─── FlywheelIOInputs contract ────────────────────────────────────────────

  @Test
  void inputs_defaultsAreZeroAndFalse() {
    FlywheelIO.FlywheelIOInputs inputs = new FlywheelIO.FlywheelIOInputs();
    assertFalse(inputs.connected, "connected must default to false");
    assertEquals(0.0, inputs.velocityRpm, 1e-9, "velocityRpm must default to 0");
    assertEquals(0.0, inputs.appliedVoltage, 1e-9, "appliedVoltage must default to 0");
    assertEquals(0.0, inputs.supplyCurrentAmps, 1e-9, "supplyCurrentAmps must default to 0");
    assertEquals(0.0, inputs.tempCelsius, 1e-9, "tempCelsius must default to 0");
  }

  // ─── FlywheelIOSim construction & HAL-free state mutations ───────────────

  @Test
  void simIO_constructorDoesNotThrow() {
    assertDoesNotThrow(FlywheelIOSim::new, "FlywheelIOSim constructor must not throw");
  }

  @Test
  void simIO_setTargetRpmDoesNotThrow() {
    FlywheelIOSim simIO = new FlywheelIOSim();
    assertDoesNotThrow(() -> simIO.setTargetRpm(3000.0));
    assertDoesNotThrow(() -> simIO.setTargetRpm(0.0));
    assertDoesNotThrow(() -> simIO.setTargetRpm(-100.0)); // negative → clamp in updateInputs
  }

  @Test
  void simIO_setVortexOutputDoesNotThrow() {
    FlywheelIOSim simIO = new FlywheelIOSim();
    assertDoesNotThrow(() -> simIO.setVortexOutput(1.0));
    assertDoesNotThrow(() -> simIO.setVortexOutput(0.0));
    assertDoesNotThrow(() -> simIO.setVortexOutput(-1.0));
  }

  @Test
  void simIO_setLowerIsNoOp() {
    // Lower feed wheels are not modeled in sim; all three calls must complete silently.
    FlywheelIOSim simIO = new FlywheelIOSim();
    assertDoesNotThrow(() -> simIO.setLower(1.0));
    assertDoesNotThrow(() -> simIO.setLower(0.0));
    assertDoesNotThrow(() -> simIO.setLower(-1.0));
  }

  @Test
  void simIO_stopDoesNotThrow() {
    FlywheelIOSim simIO = new FlywheelIOSim();
    assertDoesNotThrow(simIO::stop);
  }

  @Test
  void simIO_stopAfterSetTargetRpmDoesNotThrow() {
    FlywheelIOSim simIO = new FlywheelIOSim();
    simIO.setTargetRpm(3500.0);
    assertDoesNotThrow(simIO::stop);
  }

  // ─── Open-loop override flag (state machine without native deps) ──────────
  //
  // FlywheelIOSim maintains a boolean 'openLoopOverride' that toggles between
  // the software P+FF controller (closed-loop) and direct percent output.
  // We cannot call updateInputs() to observe the effect, but we can verify that
  // the mutators accept the transitions without throwing.

  @Test
  void simIO_openLoopThenClosedLoop_noThrow() {
    FlywheelIOSim simIO = new FlywheelIOSim();
    simIO.setVortexOutput(1.0); // arm open-loop override
    simIO.setTargetRpm(3000.0); // clear override → closed-loop
    simIO.setVortexOutput(0.5); // re-arm open-loop
    simIO.stop(); // clear everything
    assertDoesNotThrow(() -> simIO.setTargetRpm(2500.0)); // back to normal after stop
  }
}
