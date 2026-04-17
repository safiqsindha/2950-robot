package frc.robot.subsystems;

import frc.robot.simulation.IntakeSimulationAdapter;
import swervelib.simulation.ironmaple.simulation.drivesims.AbstractDriveTrainSimulation;

/**
 * Simulation implementation of {@link IntakeIO}.
 *
 * <p>Dual-path game-piece detection:
 *
 * <ol>
 *   <li><b>Arena-driven (preferred)</b> — when {@link #attachArenaSimulation} has been called with
 *       a live maple-sim drivetrain, the inner {@link IntakeSimulationAdapter} runs a real
 *       collision-based intake rectangle. {@code hasGamePiece()} flips when the robot drives over a
 *       spawned fuel.
 *   <li><b>Flag-driven (fallback)</b> — if the adapter isn't attached (e.g. unit tests), the {@code
 *       simGamePieceAcquired} flag — set by {@link #simulateGamePieceAcquired} — drives current
 *       synthesis. Keeps existing SSM tests green.
 * </ol>
 *
 * <p>Wheel current is synthesised when either path reports a held game piece, proportional to the
 * commanded wheel output.
 *
 * <p>Arm position tracking: both arm positions report the last commanded {@code targetAngle} (a
 * simplified model — no PID lag). This is sufficient for telemetry and any future test that checks
 * arm state.
 *
 * <p>The {@link #updateInputs} body is HAL-free whenever the adapter is unattached (pure Java
 * arithmetic), so existing unit tests that don't wire the adapter continue to run headless.
 */
public class IntakeIOSim implements IntakeIO {

  /**
   * Simulated wheel current multiplier (A / fraction of full output). Matches the constant
   * previously embedded in Intake's simulationPeriodic before the Phase 4 IO-layer refactor.
   */
  private static final double kSimCurrentAmpsAtFullOutput = 30.0;

  /** Minimum |wheelPercent| before the arena intake is considered "running". */
  private static final double kRunningThreshold = 0.1;

  private double wheelPercent = 0.0;
  private double targetAngle = 0.0;

  /**
   * Gates current synthesis in the no-adapter fallback path. When {@code true} a current spike
   * proportional to wheel output is synthesised, allowing the superstructure state machine to
   * advance INTAKING → STAGING without needing a live arena.
   */
  private boolean simGamePieceAcquired = false;

  /**
   * Non-null; returns unattached-path behaviour (all-false) until {@link #attachArenaSimulation}.
   */
  private final IntakeSimulationAdapter arenaIntake = new IntakeSimulationAdapter();

  /**
   * Binds this sim IO to a live maple-sim drivetrain, switching the hasGamePiece source from the
   * explicit flag to real arena collision detection. Idempotent.
   *
   * @param driveSim the swerve drive simulation from YAGSL's {@code getMapleSimDrive()}
   */
  public void attachArenaSimulation(AbstractDriveTrainSimulation driveSim) {
    arenaIntake.attach(driveSim);
  }

  @Override
  public void updateInputs(IntakeIOInputs inputs) {
    inputs.connected = true;
    // Arm positions track the commanded setpoint (no PID lag in sim).
    inputs.leftArmPositionRotations = targetAngle;
    inputs.rightArmPositionRotations = targetAngle;
    // Prefer arena-driven state when available; fall back to the explicit test flag otherwise.
    boolean hasPiece = arenaIntake.hasGamePiece() || simGamePieceAcquired;
    inputs.wheelCurrentAmps = hasPiece ? Math.abs(wheelPercent) * kSimCurrentAmpsAtFullOutput : 0.0;
    inputs.wheelAppliedVoltage = wheelPercent * 12.0;
  }

  @Override
  public void setWheel(double percent) {
    wheelPercent = percent;
    // When an arena sim is attached, start/stop the rectangle collision test based on wheel
    // command. Threshold filters noise / dead-zone jitter.
    arenaIntake.setRunning(Math.abs(percent) > kRunningThreshold);
  }

  @Override
  public void updateTargetAngle(double target) {
    targetAngle = target;
  }

  @Override
  public void resetEncoder() {
    targetAngle = 0.0;
  }

  @Override
  public void simulateGamePieceAcquired() {
    simGamePieceAcquired = true;
  }

  @Override
  public void simulateGamePieceConsumed() {
    simGamePieceAcquired = false;
  }
}
