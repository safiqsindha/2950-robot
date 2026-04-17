package frc.robot.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SystemTestCommand} logic. Since the command requires Flywheel/Intake/Conveyor
 * subsystems (HAL), we test the {@link SystemTestCommand.TestResult} record and the constants that
 * govern pass/fail thresholds.
 *
 * <p>Following the AutoScoreCommandTest / BrownoutConsumptionTest pattern: pure logic + constant
 * validation, no subsystem instantiation.
 */
class SystemTestCommandTest {

  // ── TestResult record ─────────────────────────────────────────────────────

  @Test
  void testResult_passingResult_hasPassTrue() {
    var result = new SystemTestCommand.TestResult("Flywheel", 8.5, true);
    assertTrue(result.pass(), "TestResult with pass=true should report pass");
    assertEquals("Flywheel", result.name());
    assertEquals(8.5, result.measuredAmps(), 1e-9);
  }

  @Test
  void testResult_failingResult_hasPassFalse() {
    var result = new SystemTestCommand.TestResult("Conveyor", 0.01, false);
    assertFalse(result.pass(), "TestResult with pass=false should report fail");
    assertEquals("Conveyor", result.name());
  }

  @Test
  void testResult_zeroAmps_isFail() {
    // A motor drawing 0A is disconnected or not powered — always a fail
    double amps = 0.0;
    boolean pass = amps > SystemTestCommand.kMinMotorCurrentAmps;
    assertFalse(pass, "Zero amps must never pass the current threshold check");
  }

  @Test
  void testResult_aboveThreshold_isPass() {
    double amps = SystemTestCommand.kMinMotorCurrentAmps + 0.1;
    boolean pass = amps > SystemTestCommand.kMinMotorCurrentAmps;
    assertTrue(pass, "Amps above threshold must be a passing result");
  }

  // ── allPass reduction logic ───────────────────────────────────────────────
  // Replicates the `passCount == results.size()` check in end()

  @Test
  void allResults_allPass_reportsAllPass() {
    List<SystemTestCommand.TestResult> results =
        List.of(
            new SystemTestCommand.TestResult("Flywheel", 5.0, true),
            new SystemTestCommand.TestResult("IntakeWheel", 2.0, true),
            new SystemTestCommand.TestResult("Conveyor", 1.5, true));
    long passCount = results.stream().filter(SystemTestCommand.TestResult::pass).count();
    assertEquals(results.size(), passCount, "All results passing should yield full pass count");
  }

  @Test
  void allResults_oneFails_doesNotReportAllPass() {
    List<SystemTestCommand.TestResult> results =
        List.of(
            new SystemTestCommand.TestResult("Flywheel", 5.0, true),
            new SystemTestCommand.TestResult("IntakeWheel", 0.0, false), // disconnected
            new SystemTestCommand.TestResult("Conveyor", 1.5, true));
    long passCount = results.stream().filter(SystemTestCommand.TestResult::pass).count();
    assertNotEquals(results.size(), passCount, "One failing result must not give full pass count");
    assertEquals(2, passCount);
  }

  @Test
  void emptyResults_isNotAllPass() {
    // Guard: allPass requires !results.isEmpty()
    List<SystemTestCommand.TestResult> results = List.of();
    boolean allPass =
        results.stream().filter(SystemTestCommand.TestResult::pass).count() == results.size()
            && !results.isEmpty();
    assertFalse(allPass, "Empty results list must not report allPass");
  }

  // ── Threshold and timing constants ───────────────────────────────────────

  @Test
  void minFlywheelRpm_isPositiveAndMeasurable() {
    assertTrue(SystemTestCommand.kMinFlywheelRpm > 0);
    // Should be well below the test setpoint so a healthy motor always passes
    assertTrue(
        SystemTestCommand.kMinFlywheelRpm < SystemTestCommand.kFlywheelTestRpm,
        "Min RPM threshold must be less than test setpoint");
  }

  @Test
  void minCurrentThreshold_isPositiveAndLow() {
    assertTrue(SystemTestCommand.kMinMotorCurrentAmps > 0);
    // Threshold should be very low — just enough to detect "motor is alive"
    assertTrue(
        SystemTestCommand.kMinMotorCurrentAmps < 5.0,
        "Min current threshold should be low to avoid false failures on small motors");
  }

  @Test
  void testPhaseDuration_isPositiveAndShort() {
    assertTrue(SystemTestCommand.kTestPhaseSecs > 0);
    // Each phase should be under 2 seconds to keep the whole test under 10s
    assertTrue(
        SystemTestCommand.kTestPhaseSecs <= 2.0, "Each test phase should be at most 2 seconds");
  }

  @Test
  void wheelOutputPercent_isLowAndSafe() {
    assertTrue(SystemTestCommand.kWheelOutputPercent > 0);
    // Low enough to be safe (< 25%) but high enough to draw measurable current
    assertTrue(
        SystemTestCommand.kWheelOutputPercent <= 0.25,
        "Wheel test output must be <= 25% for safety");
    assertTrue(
        SystemTestCommand.kWheelOutputPercent >= 0.05,
        "Wheel test output must be >= 5% to draw measurable current");
  }

  @Test
  void flywheelTestRpm_isWithinOperatingRange() {
    // Operating range from Constants.Flywheel: kMinRpm=2400, kMaxRpm=4000
    // Test RPM is intentionally BELOW kMinRpm so the flywheel spins up quickly in test mode
    double kMinRpm = 2400.0;
    assertTrue(
        SystemTestCommand.kFlywheelTestRpm < kMinRpm,
        "Flywheel test RPM should be below operational minimum for fast spin-up");
    assertTrue(SystemTestCommand.kFlywheelTestRpm > 0);
  }

  @Test
  void totalTestDuration_isUnderTenSeconds() {
    // 3 phases × kTestPhaseSecs
    double estimatedTotalSecs = 3 * SystemTestCommand.kTestPhaseSecs;
    assertTrue(
        estimatedTotalSecs < 10.0,
        "Full system test should complete in under 10 seconds: " + estimatedTotalSecs + "s");
  }
}
