package frc.robot.simulation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntakeSimulationAdapter}. Only the default-construction + unattached-path
 * contract is exercised here — {@link IntakeSimulationAdapter#attach} requires a live drive
 * simulation and {@link org.ironmaple.simulation.SimulatedArena} singleton, neither of which are
 * available in the JUnit headless JVM.
 */
class IntakeSimulationAdapterTest {

  @Test
  void newAdapter_isNotAttached() {
    IntakeSimulationAdapter adapter = new IntakeSimulationAdapter();
    assertFalse(adapter.isAttached());
  }

  @Test
  void unattached_hasGamePieceReturnsFalse() {
    IntakeSimulationAdapter adapter = new IntakeSimulationAdapter();
    assertFalse(adapter.hasGamePiece());
  }

  @Test
  void unattached_consumeGamePieceReturnsFalse() {
    IntakeSimulationAdapter adapter = new IntakeSimulationAdapter();
    assertFalse(adapter.consumeGamePiece());
  }

  @Test
  void unattached_injectGamePieceReturnsFalse() {
    IntakeSimulationAdapter adapter = new IntakeSimulationAdapter();
    assertFalse(adapter.injectGamePiece());
  }

  @Test
  void unattached_setRunningDoesNotThrow() {
    IntakeSimulationAdapter adapter = new IntakeSimulationAdapter();
    assertDoesNotThrow(() -> adapter.setRunning(true));
    assertDoesNotThrow(() -> adapter.setRunning(false));
  }

  @Test
  void defaultConstructor_doesNotThrow() {
    assertDoesNotThrow(() -> new IntakeSimulationAdapter());
  }
}
