package frc.lib.diagnostics;

import edu.wpi.first.hal.can.CANStatus;
import edu.wpi.first.wpilibj.RobotController;
import java.util.function.Supplier;
import org.littletonrobotics.junction.Logger;

/**
 * Publishes CAN-bus health metrics to AdvantageKit. Complements {@link JvmLogger} — together they
 * give you a single-pane view of "where is the roboRIO struggling?" (JVM heap vs CAN saturation vs
 * individual Spark faults via {@code SparkAlertLogger}).
 *
 * <p>The roboRIO surfaces CAN bus utilisation as a percentage (0-100) plus cumulative off-bus-error
 * / tx-full / rx-error counters. High utilisation correlates with motor-controller missed frames;
 * each counter spikes have their own meaning:
 *
 * <ul>
 *   <li><b>offCount</b> — controller went bus-off; a physical wiring issue (short, terminator, bad
 *       CAN cable).
 *   <li><b>txFullCount</b> — transmit queue saturated; usually we're firing too many signals.
 *   <li><b>receiveErrorCount</b> — malformed frames on the bus, most often a bad termination.
 * </ul>
 *
 * <p>Pure {@code java.lang.management}-style design — the {@link Supplier} injection lets unit
 * tests run without the WPILib HAL.
 *
 * <p>Usage:
 *
 * <pre>
 *   private final CanBusLogger canLogger = new CanBusLogger();
 *
 *   public void robotPeriodic() {
 *     canLogger.periodic();
 *   }
 * </pre>
 *
 * <p>Log keys (all under {@code CAN/}):
 *
 * <ul>
 *   <li>{@code CAN/UtilizationPct} — bus utilisation (0..100)
 *   <li>{@code CAN/OffCount} — cumulative bus-off events
 *   <li>{@code CAN/TxFullCount} — cumulative TX-queue-full events
 *   <li>{@code CAN/ReceiveErrorCount} — cumulative RX-error events
 *   <li>{@code CAN/TransmitErrorCount} — cumulative TX-error events
 * </ul>
 */
public final class CanBusLogger {

  private final Supplier<CANStatus> statusSupplier;

  /** Production constructor — reads the roboRIO via {@link RobotController#getCANStatus()}. */
  public CanBusLogger() {
    this(RobotController::getCANStatus);
  }

  /**
   * Injection constructor for tests — caller supplies a deterministic status snapshot. The supplier
   * is invoked once per {@link #periodic()} call; lambdas that return a new {@link CANStatus} each
   * call let a test simulate cumulative counter bumps.
   */
  public CanBusLogger(Supplier<CANStatus> statusSupplier) {
    this.statusSupplier = statusSupplier;
  }

  /** Sample + publish one snapshot. Cheap enough to call every 20 ms loop. */
  public void periodic() {
    Snapshot s = collect();
    Logger.recordOutput("CAN/UtilizationPct", s.utilizationPercent());
    Logger.recordOutput("CAN/OffCount", s.offCount());
    Logger.recordOutput("CAN/TxFullCount", s.txFullCount());
    Logger.recordOutput("CAN/ReceiveErrorCount", s.receiveErrorCount());
    Logger.recordOutput("CAN/TransmitErrorCount", s.transmitErrorCount());
  }

  /**
   * Package-private for tests — returns the current snapshot without touching the Logger. Lets a
   * JUnit test assert the conversion from the raw {@link CANStatus} fields into typed output.
   */
  Snapshot collect() {
    CANStatus raw = statusSupplier.get();
    // CANStatus fields are directly accessible (public), matches WPILib HAL conventions.
    return new Snapshot(
        raw.percentBusUtilization * 100.0,
        raw.busOffCount,
        raw.txFullCount,
        raw.receiveErrorCount,
        raw.transmitErrorCount);
  }

  /** Snapshot of one CAN-bus sample. Package-private for unit tests. */
  record Snapshot(
      double utilizationPercent,
      int offCount,
      int txFullCount,
      int receiveErrorCount,
      int transmitErrorCount) {}
}
