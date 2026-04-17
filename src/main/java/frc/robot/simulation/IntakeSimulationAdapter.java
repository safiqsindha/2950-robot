package frc.robot.simulation;

import static edu.wpi.first.units.Units.Meters;

import edu.wpi.first.wpilibj.RobotBase;
import org.littletonrobotics.junction.Logger;
import swervelib.simulation.ironmaple.simulation.IntakeSimulation;
import swervelib.simulation.ironmaple.simulation.IntakeSimulation.IntakeSide;
import swervelib.simulation.ironmaple.simulation.drivesims.AbstractDriveTrainSimulation;

/**
 * Thin wrapper around maple-sim's {@link IntakeSimulation} that adds {@link RobotBase} gating,
 * idempotent lifecycle handling, and AdvantageKit telemetry. Lets an {@code IntakeIOSim} (or any
 * other caller) replace the hardcoded current-synthesis with real arena-driven pickup events — when
 * the robot's intake rectangle overlaps a spawned game piece, {@link #hasGamePiece()} flips.
 *
 * <p>Typical wiring (to be added in a follow-up PR):
 *
 * <pre>{@code
 * // In RobotContainer, after swerve is up:
 * if (RobotBase.isSimulation()) {
 *   swerve.getSwerveDrive().getMapleSimDrive().ifPresent(driveSim -> {
 *     IntakeSimulationAdapter adapter = new IntakeSimulationAdapter();
 *     adapter.attach(driveSim);
 *     intake.setSimAdapter(adapter);
 *   });
 * }
 *
 * // In IntakeIOSim.setWheel(percent):
 * adapter.ifPresent(a -> a.setRunning(Math.abs(percent) > 0.1));
 *
 * // In IntakeIOSim.updateInputs():
 * boolean hasPiece = adapter.map(IntakeSimulationAdapter::hasGamePiece)
 *                           .orElse(simGamePieceAcquired);
 * }</pre>
 *
 * <p>No-op in production: every public method checks {@link RobotBase#isSimulation()} and
 * short-circuits on a real robot.
 */
public final class IntakeSimulationAdapter {

  /** Intake width in meters. Matches the physical intake's outer dimension. */
  private static final double INTAKE_WIDTH_METERS = 0.5;

  /** Capacity — one fuel in the intake at a time matches the real robot. */
  private static final int CAPACITY = 1;

  /** Game-piece type name expected by the maple-sim arena for 2026 REBUILT. */
  private static final String GAME_PIECE_TYPE = "Fuel";

  private IntakeSimulation sim;

  /**
   * Attaches this adapter to a maple-sim drivetrain simulation. Idempotent — second calls no-op.
   * Must be called after the simulated arena is initialised.
   *
   * @param driveSim the swerve drive simulation from YAGSL's {@code getMapleSimDrive()}
   */
  public void attach(AbstractDriveTrainSimulation driveSim) {
    if (!RobotBase.isSimulation() || sim != null) {
      return;
    }
    // The factory self-registers the IntakeSimulation with the arena — no explicit
    // addIntakeSimulation call needed (the method is protected on SimulatedArena anyway).
    sim =
        IntakeSimulation.InTheFrameIntake(
            GAME_PIECE_TYPE, driveSim, Meters.of(INTAKE_WIDTH_METERS), IntakeSide.FRONT, CAPACITY);
    Logger.recordOutput("Sim/Intake/Attached", true);
  }

  /**
   * Starts or stops the intake. Idempotent — {@link IntakeSimulation#startIntake()} already
   * early-returns if already running, so safe to call every loop.
   */
  public void setRunning(boolean running) {
    if (sim == null) {
      return;
    }
    if (running) {
      sim.startIntake();
    } else {
      sim.stopIntake();
    }
    Logger.recordOutput("Sim/Intake/Running", running);
  }

  /**
   * @return {@code true} when the sim has at least one fuel held in the intake.
   */
  public boolean hasGamePiece() {
    return sim != null && sim.getGamePiecesAmount() > 0;
  }

  /**
   * Removes a game piece from the intake. Returns {@code true} if one was present (typically called
   * when the conveyor takes the fuel from the intake).
   */
  public boolean consumeGamePiece() {
    if (sim == null) {
      return false;
    }
    boolean removed = sim.obtainGamePieceFromIntake();
    Logger.recordOutput("Sim/Intake/Consumed", removed);
    return removed;
  }

  /**
   * Manually injects a game piece — useful for test scaffolding and {@link
   * frc.robot.DriverPracticeMode} scenarios. Returns {@code true} if accepted (capacity not full).
   */
  public boolean injectGamePiece() {
    if (sim == null) {
      return false;
    }
    return sim.addGamePieceToIntake();
  }

  /**
   * @return {@code true} iff the adapter is bound to a real {@link IntakeSimulation}.
   */
  public boolean isAttached() {
    return sim != null;
  }
}
