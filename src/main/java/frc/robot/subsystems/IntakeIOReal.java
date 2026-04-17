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
import frc.robot.Constants;
import frc.robot.diagnostics.SparkAlertLogger;

/**
 * Real SPARK MAX hardware implementation of {@link IntakeIO}.
 *
 * <p>Motor layout:
 *
 * <ul>
 *   <li>leftArm — primary position-PID motor (inverted = false)
 *   <li>rightArm — independent position-PID motor (inverted = true); no follow due to mechanical
 *       looseness in the arm assembly
 *   <li>wheel — open-loop intake wheel (inverted = true, coast mode)
 * </ul>
 */
public class IntakeIOReal implements IntakeIO {

  private static final double kFeedForward = 0.0;

  private final SparkMax leftArm;
  private final SparkMax rightArm;
  private final SparkMax wheel;

  private final SparkClosedLoopController leftClc;
  private final SparkClosedLoopController rightClc;

  private final RelativeEncoder leftEncoder;
  private final RelativeEncoder rightEncoder;
  private final SparkAlertLogger sparkAlerts = new SparkAlertLogger();

  public IntakeIOReal() {
    leftArm = new SparkMax(Constants.Intake.kLeftArmId, MotorType.kBrushless);
    rightArm = new SparkMax(Constants.Intake.kRightArmId, MotorType.kBrushless);
    wheel = new SparkMax(Constants.Intake.kWheelId, MotorType.kBrushless);

    leftClc = leftArm.getClosedLoopController();
    rightClc = rightArm.getClosedLoopController();
    leftEncoder = leftArm.getEncoder();
    rightEncoder = rightArm.getEncoder();

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

    SparkMaxConfig wheelConfig = new SparkMaxConfig();
    wheelConfig.smartCurrentLimit(60).inverted(true).idleMode(IdleMode.kCoast);

    leftArm.configure(lConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    rightArm.configure(rConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    wheel.configure(wheelConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    // Register every Spark for fault/warning visibility on the driver dashboard.
    sparkAlerts
        .register(leftArm, "Intake/leftArm")
        .register(rightArm, "Intake/rightArm")
        .register(wheel, "Intake/wheel");
  }

  @Override
  public void updateInputs(IntakeIOInputs inputs) {
    inputs.connected = true;
    inputs.leftArmPositionRotations = leftEncoder.getPosition();
    inputs.rightArmPositionRotations = rightEncoder.getPosition();
    inputs.wheelCurrentAmps = wheel.getOutputCurrent();
    inputs.wheelAppliedVoltage = wheel.getBusVoltage() * wheel.getAppliedOutput();
    sparkAlerts.periodic();
  }

  @Override
  public void setWheel(double percent) {
    wheel.set(percent);
  }

  @Override
  public void updateTargetAngle(double target) {
    leftClc.setSetpoint(
        target, ControlType.kPosition, ClosedLoopSlot.kSlot0, kFeedForward, ArbFFUnits.kPercentOut);
    rightClc.setSetpoint(
        target, ControlType.kPosition, ClosedLoopSlot.kSlot0, kFeedForward, ArbFFUnits.kPercentOut);
  }

  @Override
  public void resetEncoder() {
    leftEncoder.setPosition(0);
    rightEncoder.setPosition(0);
  }

  @Override
  public void setPid(double kP, double kD) {
    SparkMaxConfig update = new SparkMaxConfig();
    update.closedLoop.p(kP).d(kD);
    leftArm.configure(update, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    rightArm.configure(update, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
  }
}
