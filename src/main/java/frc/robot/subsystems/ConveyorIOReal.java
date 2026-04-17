package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import frc.robot.Constants;

/**
 * Real SPARK MAX hardware implementation of {@link ConveyorIO}.
 *
 * <p>Motor layout:
 *
 * <ul>
 *   <li>conveyorMotor — brushed belt motor, brake mode
 *   <li>spindexerMotor — brushless NEO, brake mode, inverted
 * </ul>
 */
public class ConveyorIOReal implements ConveyorIO {

  private final SparkMax conveyorMotor;
  private final SparkMax spindexerMotor;

  public ConveyorIOReal() {
    conveyorMotor = new SparkMax(Constants.Conveyor.kConveyorMotorId, MotorType.kBrushed);
    spindexerMotor = new SparkMax(Constants.Conveyor.kSpindexerMotorId, MotorType.kBrushless);

    SparkMaxConfig config = new SparkMaxConfig();
    config.inverted(false).idleMode(IdleMode.kBrake).smartCurrentLimit(40);
    conveyorMotor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    SparkMaxConfig spinConfig = new SparkMaxConfig();
    spinConfig.apply(config).inverted(true);
    spindexerMotor.configure(
        spinConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  @Override
  public void updateInputs(ConveyorIOInputs inputs) {
    inputs.connected = true;
    inputs.conveyorAppliedOutput = conveyorMotor.getAppliedOutput();
    inputs.spindexerAppliedOutput = spindexerMotor.getAppliedOutput();
    inputs.conveyorCurrentAmps = conveyorMotor.getOutputCurrent();
    inputs.spindexerCurrentAmps = spindexerMotor.getOutputCurrent();
  }

  @Override
  public void setConveyor(double percent) {
    conveyorMotor.set(percent);
    spindexerMotor.set(percent);
  }

  @Override
  public void stop() {
    conveyorMotor.set(0);
    spindexerMotor.set(0);
  }
}
