package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.LoggedTunableNumber;
import frc.robot.Constants;
import org.littletonrobotics.junction.Logger;

/**
 * Flywheel subsystem — thin consumer of {@link FlywheelIO} (2590 IO pattern).
 *
 * <p>Hardware details (SPARK Flex MAXMotion PID, SPARK MAX lower wheels, DCMotorSim) are
 * encapsulated in {@link FlywheelIOReal} and {@link FlywheelIOSim}. This class owns scheduling,
 * telemetry, and live PID gain updates.
 *
 * <p>Motor layout:
 *
 * <ul>
 *   <li>Two SPARK Flex Vortex motors — main flywheel (velocity closed-loop via MAXMotion)
 *   <li>Two SPARK MAX NEO motors — lower feed wheels (open-loop percent output)
 * </ul>
 */
public class Flywheel extends SubsystemBase {

  private final FlywheelIO io;
  private final FlywheelIOInputsAutoLogged inputs = new FlywheelIOInputsAutoLogged();

  /** Last commanded RPM setpoint — used for AtSpeed telemetry. */
  private double setpointRpm = 0.0;

  // ─── Tunable PID gains (visible and editable via AdvantageScope / NT) ───
  private final LoggedTunableNumber tunableKP =
      new LoggedTunableNumber("Flywheel/kP", Constants.Flywheel.kP);
  private final LoggedTunableNumber tunableKI =
      new LoggedTunableNumber("Flywheel/kI", Constants.Flywheel.kI);
  private final LoggedTunableNumber tunableKD =
      new LoggedTunableNumber("Flywheel/kD", Constants.Flywheel.kD);

  public Flywheel(FlywheelIO io) {
    this.io = io;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("Flywheel", inputs);

    // Derived telemetry (computed from inputs + setpoint — not part of the IO contract).
    Logger.recordOutput("Flywheel/SetpointRpm", setpointRpm);
    Logger.recordOutput(
        "Flywheel/AtSpeed",
        setpointRpm > 0
            && Math.abs(inputs.velocityRpm - setpointRpm) / setpointRpm
                < Constants.Flywheel.kReadyThreshold);

    // Apply updated PID gains if any tunable changed since last check.
    // FlywheelIOSim.setPid() is a no-op — the isSimulation() guard is not needed here.
    if (tunableKP.hasChanged(hashCode())
        || tunableKI.hasChanged(hashCode())
        || tunableKD.hasChanged(hashCode())) {
      io.setPid(tunableKP.get(), tunableKI.get(), tunableKD.get());
    }
  }

  /**
   * Command the flywheel to a target velocity.
   *
   * @param rpm target speed in RPM
   */
  public void setTargetRpm(double rpm) {
    setpointRpm = rpm;
    io.setTargetRpm(rpm);
  }

  /**
   * Set the primary Vortex motor to a percent output, bypassing closed-loop control. Used by {@link
   * frc.robot.commands.flywheel.FlywheelDynamic} for open-loop trigger driving.
   *
   * @param percent output (-1 to 1)
   */
  public void setVortexOutput(double percent) {
    io.setVortexOutput(percent);
  }

  /**
   * Set the lower feed wheel motors to a percent output.
   *
   * @param percent output (-1 to 1)
   */
  public void setLower(double percent) {
    io.setLower(percent);
  }

  /**
   * Get the current flywheel speed. Reads from the last {@link FlywheelIO#updateInputs} cycle.
   * Always non-negative.
   */
  public double getCurrentRpm() {
    return inputs.velocityRpm;
  }

  /**
   * Get the primary motor supply current (A). Used by {@link frc.robot.commands.SystemTestCommand}
   * for motor connectivity checks.
   */
  public double getMotorCurrentAmps() {
    return inputs.supplyCurrentAmps;
  }
}
