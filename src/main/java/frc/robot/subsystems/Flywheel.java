package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.LoggedTunableNumber;
import frc.lib.control.LinearProfile;
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
 *
 * <p><b>Setpoint slewing.</b> {@link #setTargetRpm} records the caller's <i>goal</i> RPM; the
 * actual setpoint pushed to the IO is rate-limited by a {@link LinearProfile} in {@link
 * #periodic()}. This keeps the PID+FF layer from seeing a step change when a command transitions
 * from idle → spin-up, lowering peak current draw and easing the brownout budget. The rate limit
 * ({@link Constants.Flywheel#kMaxAccelRpmPerSec}) is tuned to be effectively transparent on the
 * real Vortex pair while still smoothing the electrical transient.
 */
public class Flywheel extends SubsystemBase {

  private final FlywheelIO io;
  private final FlywheelIOInputsAutoLogged inputs = new FlywheelIOInputsAutoLogged();

  /** Caller's target RPM — what the operator / command ultimately wants the flywheel doing. */
  private double goalRpm = 0.0;

  /**
   * Actual RPM currently commanded to the PID (ramped toward {@link #goalRpm} via {@link
   * #profile}). Logged for telemetry; consumers should check {@link #isAtSpeed()} against the
   * goal, not this.
   */
  private double setpointRpm = 0.0;

  /** True while open-loop percent output overrides the velocity controller (see FlywheelDynamic). */
  private boolean openLoopMode = false;

  /** Rate limiter that slews {@link #setpointRpm} toward {@link #goalRpm} in {@link #periodic()}. */
  private final LinearProfile profile =
      new LinearProfile(Constants.Flywheel.kMaxAccelRpmPerSec, 0.02);

  // ─── Tunable PID gains (visible and editable via AdvantageScope / NT) ───
  private final LoggedTunableNumber tunableKP =
      new LoggedTunableNumber("Flywheel/kP", Constants.Flywheel.kP);
  private final LoggedTunableNumber tunableKI =
      new LoggedTunableNumber("Flywheel/kI", Constants.Flywheel.kI);
  private final LoggedTunableNumber tunableKD =
      new LoggedTunableNumber("Flywheel/kD", Constants.Flywheel.kD);

  /** Tunable setpoint slew rate — change from AdvantageScope to tighten / loosen the ramp. */
  private final LoggedTunableNumber tunableMaxAccel =
      new LoggedTunableNumber("Flywheel/kMaxAccelRpmPerSec", Constants.Flywheel.kMaxAccelRpmPerSec);

  public Flywheel(FlywheelIO io) {
    this.io = io;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("Flywheel", inputs);

    // Push the slewed closed-loop setpoint unless a command has explicitly claimed open-loop.
    // Open-loop callers (FlywheelDynamic) set their percent through setVortexOutput(); replaying
    // a stale setTargetRpm() over that would tear the motor between two conflicting commands.
    if (!openLoopMode) {
      setpointRpm = profile.calculate(goalRpm);
      io.setTargetRpm(setpointRpm);
    }

    // Live-tuning hook — refresh the slew rate if someone retuned it from NT.
    if (tunableMaxAccel.hasChanged(hashCode())) {
      profile.setMaxAccel(tunableMaxAccel.get());
    }

    // Derived telemetry (computed from inputs + setpoint — not part of the IO contract).
    Logger.recordOutput("Flywheel/GoalRpm", goalRpm);
    Logger.recordOutput("Flywheel/SetpointRpm", setpointRpm);
    Logger.recordOutput("Flywheel/OpenLoop", openLoopMode);
    Logger.recordOutput("Flywheel/AtSpeed", isAtSpeed());

    // Apply updated PID gains if any tunable changed since last check.
    // FlywheelIOSim.setPid() is a no-op — the isSimulation() guard is not needed here.
    if (tunableKP.hasChanged(hashCode())
        || tunableKI.hasChanged(hashCode())
        || tunableKD.hasChanged(hashCode())) {
      io.setPid(tunableKP.get(), tunableKI.get(), tunableKD.get());
    }
  }

  /**
   * Command the flywheel to a target velocity. The goal is stored and the PID setpoint is slewed
   * toward it in {@link #periodic()} at up to {@link Constants.Flywheel#kMaxAccelRpmPerSec}.
   *
   * <p>If the caller was previously in open-loop mode (via {@link #setVortexOutput}), the internal
   * profile is re-seeded to the currently measured RPM so the ramp starts from reality rather
   * than a stale setpoint.
   *
   * @param rpm target speed in RPM
   */
  public void setTargetRpm(double rpm) {
    if (openLoopMode) {
      // Bootstrap the ramp from the physical wheel speed we just observed — otherwise the first
      // post-transition setpoint could be wildly off and the LinearProfile would then clip back
      // toward it at maxAccel.
      profile.reset(inputs.velocityRpm);
    }
    goalRpm = rpm;
    openLoopMode = false;
  }

  /**
   * Set the primary Vortex motor to a percent output, bypassing closed-loop control. Used by {@link
   * frc.robot.commands.flywheel.FlywheelDynamic} for open-loop trigger driving.
   *
   * <p>Flips the subsystem into open-loop mode — {@link #periodic()} will stop pushing slewed
   * velocity setpoints until a subsequent {@link #setTargetRpm} call clears the flag.
   *
   * @param percent output (-1 to 1)
   */
  public void setVortexOutput(double percent) {
    openLoopMode = true;
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

  /**
   * @return the RPM goal most recently set by {@link #setTargetRpm}. Distinct from {@link
   *     #getSetpointRpm()}, which is the currently-ramped value fed to the PID.
   */
  public double getGoalRpm() {
    return goalRpm;
  }

  /**
   * @return the RPM currently commanded to the PID (ramped toward {@link #getGoalRpm()} at
   *     {@link Constants.Flywheel#kMaxAccelRpmPerSec}).
   */
  public double getSetpointRpm() {
    return setpointRpm;
  }

  /**
   * @return {@code true} when a non-zero <i>goal</i> has been commanded and the measured velocity
   *     is within {@link Constants.Flywheel#kReadyThreshold} of the goal. Used by {@code
   *     AutoScoreCommand}, {@code FlywheelAutoFeed}, and simulation shot triggers.
   *
   *     <p>Important: this compares against the final goal, not the slewed intermediate setpoint
   *     — otherwise a caller could think the wheel is ready when in reality the ramp is still
   *     below the goal.
   */
  public boolean isAtSpeed() {
    return goalRpm > 0
        && Math.abs(inputs.velocityRpm - goalRpm) / goalRpm
            < Constants.Flywheel.kReadyThreshold;
  }
}
