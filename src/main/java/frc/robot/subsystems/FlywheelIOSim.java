package frc.robot.subsystems;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.wpilibj.simulation.DCMotorSim;
import frc.robot.Constants;

/**
 * Simulation implementation of {@link FlywheelIO}.
 *
 * <p>Models two NEO Vortex motors coupled 1:1 to the flywheel disk using a {@link DCMotorSim}. A
 * software P+FF controller applies voltage each 20 ms step, matching the physics from the pre-IO
 * {@link Flywheel} implementation and enabling velocity PID behavior to be verified in HALSim.
 *
 * <p>The lower feed wheels (front/back) are not physically modeled — {@link #setLower} is a no-op,
 * which is correct: the sim concentrates on flywheel speed convergence.
 */
public class FlywheelIOSim implements FlywheelIO {

  /** Moment of inertia for a typical FRC flywheel disk (~4 g·m²). */
  private static final double kSimJKgM2 = 0.004;

  /** Software P gain (V / RPM) — sim-only, tuned separately from the SPARK kP. */
  private static final double kSimKpVoltsPerRpm = 0.008;

  private static final double kDtSecs = 0.02;

  private final DCMotorSim sim;

  private double targetRpm = 0.0;
  private double currentRpm = 0.0;
  private double appliedVoltage = 0.0;

  /** When true, {@code openLoopPercent} overrides the closed-loop P+FF controller. */
  private boolean openLoopOverride = false;

  private double openLoopPercent = 0.0;

  public FlywheelIOSim() {
    DCMotor vortexPair = DCMotor.getNeoVortex(2);
    sim =
        new DCMotorSim(LinearSystemId.createDCMotorSystem(vortexPair, kSimJKgM2, 1.0), vortexPair);
  }

  @Override
  public void updateInputs(FlywheelIOInputs inputs) {
    if (openLoopOverride) {
      appliedVoltage = MathUtil.clamp(openLoopPercent * 12.0, -12.0, 12.0);
    } else {
      // Software P+FF (mirrors the pre-IO periodic() logic).
      double errorRpm = targetRpm - currentRpm;
      double ffVolts =
          Constants.Flywheel.kS * Math.signum(targetRpm) + Constants.Flywheel.kV * targetRpm;
      appliedVoltage = MathUtil.clamp(ffVolts + kSimKpVoltsPerRpm * errorRpm, 0.0, 12.0);
    }

    sim.setInputVoltage(appliedVoltage);
    sim.update(kDtSecs);

    // rad/s → RPM; take absolute value to match hardware getCurrentRpm() behavior.
    currentRpm = Math.abs(sim.getAngularVelocityRadPerSec() * 60.0 / (2.0 * Math.PI));

    inputs.connected = true;
    inputs.velocityRpm = currentRpm;
    inputs.appliedVoltage = appliedVoltage;
    inputs.supplyCurrentAmps = sim.getCurrentDrawAmps();
    inputs.tempCelsius = 0.0;
  }

  @Override
  public void setTargetRpm(double rpm) {
    targetRpm = rpm;
    openLoopOverride = false;
  }

  @Override
  public void setVortexOutput(double percent) {
    openLoopPercent = percent;
    openLoopOverride = true;
  }

  @Override
  public void setLower(double percent) {
    // Lower feed wheels are not modeled in sim — no-op is correct.
  }

  @Override
  public void stop() {
    targetRpm = 0.0;
    openLoopOverride = false;
    appliedVoltage = 0.0;
    // sim.setInputVoltage() is deliberately NOT called here: it calls
    // RobotController.getBatteryVoltage() which requires the WPILib HAL native library.
    // The zero voltage is applied on the next updateInputs() cycle when the P+FF
    // controller computes: ffVolts = kS * sign(0) + kV * 0 = 0, error = 0, voltage = 0.
  }
}
