package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.hal.can.CANStatus;
import org.junit.jupiter.api.Test;

/**
 * HAL-free tests for {@link CanBusLogger}. {@link CANStatus} is a plain data holder — we can
 * construct one directly and populate it via {@code setStatus}, so the logger's math can be
 * verified without booting the roboRIO.
 */
class CanBusLoggerTest {

  private static CANStatus status(double utilPct, int off, int txFull, int rxErr, int txErr) {
    CANStatus s = new CANStatus();
    s.setStatus(utilPct, off, txFull, rxErr, txErr);
    return s;
  }

  @Test
  void collect_scalesUtilizationToPercent() {
    // setStatus takes utilisation as a fraction 0..1; we expect the logger to scale to 0..100.
    CanBusLogger logger = new CanBusLogger(() -> status(0.42, 0, 0, 0, 0));
    CanBusLogger.Snapshot snap = logger.collect();
    assertEquals(42.0, snap.utilizationPercent(), 1e-9);
  }

  @Test
  void collect_preservesCounters() {
    CanBusLogger logger = new CanBusLogger(() -> status(0.1, 2, 5, 11, 7));
    CanBusLogger.Snapshot snap = logger.collect();
    assertEquals(2, snap.offCount());
    assertEquals(5, snap.txFullCount());
    assertEquals(11, snap.receiveErrorCount());
    assertEquals(7, snap.transmitErrorCount());
  }

  @Test
  void collect_samplesSupplierEachCall() {
    // Rolling-state supplier simulates the CAN counters incrementing between ticks.
    int[] counter = {0};
    CanBusLogger logger =
        new CanBusLogger(
            () -> {
              counter[0]++;
              return status(0.0, counter[0], 0, 0, 0);
            });

    assertEquals(1, logger.collect().offCount());
    assertEquals(2, logger.collect().offCount());
    assertEquals(3, logger.collect().offCount());
  }

  @Test
  void periodic_doesNotThrowWithZeroStatus() {
    // periodic() calls Logger.recordOutput — make sure a clean zero status doesn't fault the
    // AdvantageKit Logger (which is lenient and buffers when no data log is open).
    CanBusLogger logger = new CanBusLogger(() -> status(0.0, 0, 0, 0, 0));
    assertDoesNotThrow(logger::periodic);
  }

  @Test
  void defaultConstructor_doesNotThrow() {
    // The no-arg ctor wires the supplier to RobotController::getCANStatus. We can't actually
    // call the supplier without HAL, but constructing the logger must be safe.
    assertDoesNotThrow(CanBusLogger::new);
  }
}
