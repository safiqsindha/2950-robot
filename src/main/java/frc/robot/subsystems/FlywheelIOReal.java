package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.FeedbackSensor;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkFlexConfig;
import com.revrobotics.spark.config.SparkMaxConfig;
import frc.robot.Constants;

/**
 * Real SPARK Flex (Vortex) + SPARK MAX (NEO) implementation of {@link FlywheelIO}.
 *
 * <p>Motor layout:
 *
 * <ul>
 *   <li>leftVortex — primary; hosts the MAXMotion velocity PID
 *   <li>rightVortex — follower (inverted relative to left)
 *   <li>frontWheel / backWheel — lower feed wheels, open-loop percent output
 * </ul>
 */
public class FlywheelIOReal implements FlywheelIO {

  private final SparkFlex leftVortex;
  private final SparkFlex rightVortex;
  private final SparkMax frontWheel;
  private final SparkMax backWheel;

  private final SparkClosedLoopController closedLoopController;
  private final RelativeEncoder encoder;

  public FlywheelIOReal() {
    leftVortex = new SparkFlex(Constants.Flywheel.kLeftVortexId, MotorType.kBrushless);
    rightVortex = new SparkFlex(Constants.Flywheel.kRightVortexId, MotorType.kBrushless);
    frontWheel = new SparkMax(Constants.Flywheel.kFrontWheelId, MotorType.kBrushless);
    backWheel = new SparkMax(Constants.Flywheel.kBackWheelId, MotorType.kBrushless);

    closedLoopController = leftVortex.getClosedLoopController();
    encoder = leftVortex.getEncoder();

    SparkFlexConfig lVortexConfig = new SparkFlexConfig();
    lVortexConfig
        .smartCurrentLimit(80)
        .inverted(true)
        .closedLoop
        .feedbackSensor(FeedbackSensor.kPrimaryEncoder)
        .p(Constants.Flywheel.kP)
        .i(Constants.Flywheel.kI)
        .d(Constants.Flywheel.kD)
        .outputRange(0, 1)
        .feedForward
        .kS(Constants.Flywheel.kS)
        .kV(Constants.Flywheel.kV)
        .kA(0);
    lVortexConfig.closedLoop.maxMotion.maxAcceleration(1000);

    SparkFlexConfig rVortexConfig = new SparkFlexConfig();
    rVortexConfig.apply(lVortexConfig).follow(leftVortex, true);

    leftVortex.configure(
        lVortexConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    rightVortex.configure(
        rVortexConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    SparkMaxConfig fConfig = new SparkMaxConfig();
    fConfig.inverted(true).idleMode(IdleMode.kBrake).smartCurrentLimit(40);

    SparkMaxConfig bConfig = new SparkMaxConfig();
    bConfig.apply(fConfig).inverted(false);

    frontWheel.configure(fConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    backWheel.configure(bConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    // Warm-up: first REVLib CAN transaction during robotInit() prevents a 2-second overrun
    // on the first command loop that would expire a Choreo trajectory before it can move.
    frontWheel.set(0);
    backWheel.set(0);
    leftVortex.set(0);
    rightVortex.set(0);
  }

  @Override
  public void updateInputs(FlywheelIOInputs inputs) {
    // SPARK Flex doesn't surface a simple "connected" signal; treat as connected unless a CAN
    // fault is detected. The Alert system (RobotContainer) handles persistent fault reporting.
    inputs.connected = true;
    inputs.velocityRpm = Math.abs(encoder.getVelocity());
    inputs.appliedVoltage = leftVortex.getBusVoltage() * leftVortex.getAppliedOutput();
    inputs.supplyCurrentAmps = leftVortex.getOutputCurrent();
    inputs.tempCelsius = leftVortex.getMotorTemperature();
  }

  @Override
  public void setTargetRpm(double rpm) {
    closedLoopController.setSetpoint(rpm, ControlType.kMAXMotionVelocityControl);
  }

  @Override
  public void setVortexOutput(double percent) {
    leftVortex.set(percent);
  }

  @Override
  public void setLower(double percent) {
    frontWheel.set(percent);
    backWheel.set(percent);
  }

  @Override
  public void stop() {
    leftVortex.set(0);
    frontWheel.set(0);
    backWheel.set(0);
  }

  @Override
  public void setPid(double kP, double kI, double kD) {
    SparkFlexConfig update = new SparkFlexConfig();
    update.closedLoop.p(kP).i(kI).d(kD);
    leftVortex.configure(
        update, ResetMode.kNoResetSafeParameters, PersistMode.kNoPersistParameters);
    // rightVortex follows leftVortex output — PID runs only on leftVortex, no reconfigure needed.
  }
}
