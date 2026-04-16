package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.ClosedLoopSlot;
import com.revrobotics.spark.FeedbackSensor;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkClosedLoopController.ArbFFUnits;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.LoggedTunableNumber;
import frc.robot.Constants;
import org.littletonrobotics.junction.Logger;

/**
 * Intake subsystem. Two independently controlled arm motors (left/right, no follow due to 'loose'
 * arm mechanical assembly) and one intake wheel motor. Position PID runs on SPARK MAX onboard
 * controller.
 */
public class Intake extends SubsystemBase {

  private final SparkMax leftArm = new SparkMax(Constants.Intake.kLeftArmId, MotorType.kBrushless);

  private final SparkMax rightArm =
      new SparkMax(Constants.Intake.kRightArmId, MotorType.kBrushless);

  public final SparkMax wheel = new SparkMax(Constants.Intake.kWheelId, MotorType.kBrushless);

  private final SparkClosedLoopController leftClc = leftArm.getClosedLoopController();
  private final SparkClosedLoopController rightClc = rightArm.getClosedLoopController();

  private final RelativeEncoder leftEncoder = leftArm.getEncoder();
  private final RelativeEncoder rightEncoder = rightArm.getEncoder();

  private static final double kFeedForward = 0.0;

  // ─── Tunable PID gains (visible and editable via AdvantageScope / NT) ───
  private final LoggedTunableNumber tunableKP =
      new LoggedTunableNumber("Intake/kP", Constants.Intake.kP);
  private final LoggedTunableNumber tunableKD =
      new LoggedTunableNumber("Intake/kD", Constants.Intake.kD);

  /**
   * Simulated wheel current (amps). Only non-zero when {@link #simGamePieceAcquired} is true, so
   * the SSM's current-spike detection requires an explicit game-piece injection call rather than
   * firing on any wheel command. Without this gate every wheel spin auto-advanced INTAKING →
   * STAGING in simulation.
   */
  private double simWheelCurrentAmps = 0.0;

  /**
   * Flag that gates simulated current synthesis. Set via {@link #simulateGamePieceAcquired()};
   * cleared via {@link #simulateGamePieceConsumed()}. Package-private for DriverPracticeMode.
   */
  private boolean simGamePieceAcquired = false;

  public Intake() {
    SparkMaxConfig rConfig = new SparkMaxConfig();
    rConfig
        .inverted(true)
        .idleMode(IdleMode.kBrake)
        .smartCurrentLimit(20)
        .closedLoop
        .feedbackSensor(FeedbackSensor.kPrimaryEncoder)
        .p(Constants.Intake.kP)
        .d(Constants.Intake.kD)
        .outputRange(-1, 1);

    SparkMaxConfig lConfig = new SparkMaxConfig();
    lConfig.apply(rConfig).inverted(false);
    // No follow — arms are controlled independently due to mechanical looseness

    SparkMaxConfig wheelConfig = new SparkMaxConfig();
    wheelConfig.smartCurrentLimit(60).inverted(true).idleMode(IdleMode.kCoast);

    leftArm.configure(lConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    rightArm.configure(rConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    wheel.configure(wheelConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  @Override
  public void periodic() {
    Logger.recordOutput("Intake/LeftArmPosition", leftEncoder.getPosition());
    Logger.recordOutput("Intake/RightArmPosition", rightEncoder.getPosition());
    Logger.recordOutput("Intake/WheelCurrentAmps", getWheelCurrent());
    Logger.recordOutput("Intake/WheelVoltage", wheel.getBusVoltage() * wheel.getAppliedOutput());

    // Apply updated PID gains if any tunable changed since last check.
    // Both arm controllers share the same gains (symmetric arm geometry).
    if (!RobotBase.isSimulation()
        && (tunableKP.hasChanged(hashCode()) || tunableKD.hasChanged(hashCode()))) {
      SparkMaxConfig update = new SparkMaxConfig();
      update.closedLoop.p(tunableKP.get()).d(tunableKD.get());
      leftArm.configure(update, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
      rightArm.configure(
          update, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    }
  }

  @Override
  public void simulationPeriodic() {
    // Only synthesize current spike when a game piece has been explicitly injected.
    // Before this fix, any wheel command produced ~30A (> SSM threshold 15A), causing INTAKING
    // to auto-advance to STAGING every time the wheel ran — even with no game piece present.
    simWheelCurrentAmps = simGamePieceAcquired ? Math.abs(wheel.getAppliedOutput()) * 30.0 : 0.0;
  }

  /**
   * Inject a simulated game piece into the intake. Call from {@link frc.robot.DriverPracticeMode}
   * or test scaffolding to allow the superstructure state machine to advance INTAKING → STAGING in
   * simulation.
   */
  public void simulateGamePieceAcquired() {
    simGamePieceAcquired = true;
  }

  /**
   * Remove the simulated game piece (e.g. after it has been staged or scored). Resets the current
   * synthesis gate so the next intake cycle starts clean.
   */
  public void simulateGamePieceConsumed() {
    simGamePieceAcquired = false;
  }

  /** Reset both arm encoders to zero. Call at the start of autonomous and teleop. */
  public void resetEncoder() {
    leftEncoder.setPosition(0);
    rightEncoder.setPosition(0);
  }

  /**
   * Set the target arm position for both arms.
   *
   * @param target target position in encoder rotations
   */
  public void updateTargetAngle(double target) {
    leftClc.setSetpoint(
        target, ControlType.kPosition, ClosedLoopSlot.kSlot0, kFeedForward, ArbFFUnits.kPercentOut);
    rightClc.setSetpoint(
        target, ControlType.kPosition, ClosedLoopSlot.kSlot0, kFeedForward, ArbFFUnits.kPercentOut);
  }

  /**
   * Set intake wheel percent output.
   *
   * @param percent output (-1 to 1)
   */
  public void setWheel(double percent) {
    wheel.set(percent);
  }

  /**
   * Get the current drawn by the intake wheel motor. Used by the SuperstructureStateMachine to
   * detect game piece acquisition via current spike. In simulation, returns a synthetic current
   * proportional to wheel output since SPARK MAX getOutputCurrent() returns 0 in sim.
   *
   * @return wheel motor output current in amps
   */
  public double getWheelCurrent() {
    if (RobotBase.isSimulation()) {
      return simWheelCurrentAmps;
    }
    return wheel.getOutputCurrent();
  }
}
