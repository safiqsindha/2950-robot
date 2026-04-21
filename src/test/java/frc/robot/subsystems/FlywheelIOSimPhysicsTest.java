package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.hal.HAL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * HAL-init canary — demonstrates the pattern for exercising WPILib simulation classes ({@link
 * edu.wpi.first.wpilibj.simulation.DCMotorSim}, {@link
 * edu.wpi.first.wpilibj.simulation.ElevatorSim}, etc.) in JUnit by initialising HAL once before any
 * test method runs.
 *
 * <p>Without HAL, {@code DCMotorSim.setInputVoltage} internally calls {@code
 * RobotController.getBatteryVoltage()} which loads {@code wpiHaljni} and crashes the test JVM.
 * {@link HAL#initialize(int, int)} in {@link BeforeAll} bootstraps the native bridge so {@code
 * FlywheelIOSim.updateInputs()} (which does the voltage apply + physics tick) runs to completion.
 *
 * <p>Future physics-sensitive tests should follow this pattern:
 *
 * <ol>
 *   <li>Put the test in {@code frc.robot.*} — frc.lib has an 80 % coverage gate and this test
 *       depends on HAL.
 *   <li>{@code @BeforeAll} calls {@code HAL.initialize(500, 0)}.
 *   <li>{@code @AfterAll} calls {@code HAL.shutdown()} so state doesn't leak between suites.
 *   <li>Keep the test deterministic — don't rely on wall-clock time.
 * </ol>
 *
 * <p>This test exists primarily as documentation + a smoke check that the canary pattern still
 * compiles after WPILib version bumps. Richer physics tests can slot in alongside it.
 */
// Runs via the dedicated `halTest` Gradle task (see build.gradle), which forks a separate JVM
// for these tests. That isolation prevents native-library conflicts with other test classes that
// previously caused intermittent JVM crashes in the shared ubuntu-latest runner.
@Tag("hal")
class FlywheelIOSimPhysicsTest {

  @BeforeAll
  static void initHal() {
    // HAL.initialize arguments: (timeout ms, mode). 500 ms is plenty for a JUnit test environment.
    // Mode 0 = kMain (default). Returns true on success; throws if the native library isn't
    // available.
    assertTrue(HAL.initialize(500, 0), "HAL must initialise for DCMotorSim to function");
  }

  @AfterAll
  static void shutdownHal() {
    HAL.shutdown();
  }

  @Test
  void flywheelIOSim_commandedSetpoint_spinsUpToward() {
    FlywheelIOSim io = new FlywheelIOSim();
    FlywheelIO.FlywheelIOInputs inputs = new FlywheelIO.FlywheelIOInputs();

    // First tick at zero to establish baseline.
    io.updateInputs(inputs);
    double baseline = inputs.velocityRpm;

    // Command a mid-range setpoint and let the P+FF + DCMotorSim physics integrate.
    io.setTargetRpm(2000);
    for (int i = 0; i < 50; i++) {
      io.updateInputs(inputs);
    }

    // After 50 × 20 ms = 1 second of simulated time, velocity should be well above baseline
    // and positive. Exact convergence depends on kSimJKgM2 + motor model; this is a smoke check.
    assertTrue(
        inputs.velocityRpm > baseline + 500,
        "Flywheel velocity should climb substantially from baseline; got " + inputs.velocityRpm);
    assertTrue(inputs.velocityRpm > 0, "Velocity must be non-negative");
  }

  @Test
  void flywheelIOSim_stop_zeroesVelocity() {
    FlywheelIOSim io = new FlywheelIOSim();
    FlywheelIO.FlywheelIOInputs inputs = new FlywheelIO.FlywheelIOInputs();

    // Spin up, then stop.
    io.setTargetRpm(2000);
    for (int i = 0; i < 50; i++) {
      io.updateInputs(inputs);
    }
    double peak = inputs.velocityRpm;
    assertTrue(peak > 500, "Must spin up before stopping");

    io.stop();
    for (int i = 0; i < 100; i++) {
      io.updateInputs(inputs);
    }

    // After 2 s of zero-input coast, velocity should have decayed substantially.
    assertTrue(
        inputs.velocityRpm < peak * 0.5,
        "Post-stop velocity should decay below 50% of peak; got " + inputs.velocityRpm);
  }

  @Test
  void flywheelIOSim_inputsConnectedAlwaysTrue() {
    FlywheelIOSim io = new FlywheelIOSim();
    FlywheelIO.FlywheelIOInputs inputs = new FlywheelIO.FlywheelIOInputs();
    io.updateInputs(inputs);
    assertTrue(inputs.connected, "Sim IO always reports connected");
  }
}
