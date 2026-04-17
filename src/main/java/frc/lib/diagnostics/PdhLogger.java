package frc.lib.diagnostics;

import edu.wpi.first.wpilibj.PowerDistribution;
import java.util.function.Supplier;
import org.littletonrobotics.junction.Logger;

/**
 * Publishes {@link PowerDistribution} (REV PDH or CTRE PDP) telemetry to AdvantageKit. Completes
 * the health-monitoring trio alongside {@link JvmLogger} and {@link CanBusLogger}: you can scrub
 * back in AdvantageScope and cross-reference heap spikes, CAN saturation, and per-channel current
 * draw for a single loop-overrun incident.
 *
 * <p>Design mirrors {@link CanBusLogger}:
 *
 * <ul>
 *   <li>Primary ctor takes a {@link PowerDistribution} — the caller (normally {@code Robot.java})
 *       owns its lifecycle.
 *   <li>Test ctor takes a {@link Supplier} that returns a {@link Snapshot} so JUnit can exercise
 *       the logger without booting HAL.
 * </ul>
 *
 * <p>Log keys (all under {@code PDH/}):
 *
 * <ul>
 *   <li>{@code PDH/VoltageV} — input bus voltage
 *   <li>{@code PDH/TotalCurrentA} — summed output current
 *   <li>{@code PDH/TemperatureC} — internal PDH temperature
 *   <li>{@code PDH/ChannelCurrentsA} — full 24-element array (REV) / 16-element (CTRE), matches
 *       {@link PowerDistribution#getAllCurrents()}
 * </ul>
 */
public final class PdhLogger {

  private final Supplier<Snapshot> supplier;

  /** Production ctor — wires to the given {@link PowerDistribution}. */
  public PdhLogger(PowerDistribution pdh) {
    this(
        () ->
            new Snapshot(
                pdh.getVoltage(),
                pdh.getTotalCurrent(),
                pdh.getTemperature(),
                pdh.getAllCurrents()));
  }

  /** Test ctor — inject a snapshot supplier to skip the HAL-coupled {@link PowerDistribution}. */
  PdhLogger(Supplier<Snapshot> supplier) {
    this.supplier = supplier;
  }

  /** Sample + publish one snapshot. Safe to call every 20 ms. */
  public void periodic() {
    Snapshot s = supplier.get();
    Logger.recordOutput("PDH/VoltageV", s.voltageV());
    Logger.recordOutput("PDH/TotalCurrentA", s.totalCurrentA());
    Logger.recordOutput("PDH/TemperatureC", s.temperatureC());
    Logger.recordOutput("PDH/ChannelCurrentsA", s.channelCurrentsA());
  }

  /**
   * Package-private for tests — returns the current snapshot without pushing to Logger. Lets a
   * JUnit test assert the copy-through behaviour.
   */
  Snapshot collect() {
    return supplier.get();
  }

  /**
   * Immutable snapshot of one PDH sample. {@code channelCurrentsA} is stored by reference for
   * efficiency — the logger trusts the supplier to return a fresh array or a safe read-only one.
   * Package-private for unit tests.
   */
  record Snapshot(
      double voltageV, double totalCurrentA, double temperatureC, double[] channelCurrentsA) {}
}
