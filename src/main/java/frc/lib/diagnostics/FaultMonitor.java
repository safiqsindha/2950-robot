package frc.lib.diagnostics;

import edu.wpi.first.wpilibj.Alert;
import edu.wpi.first.wpilibj.Alert.AlertType;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Generic fault monitor — polls a {@link BooleanSupplier} each tick and surfaces the result as a
 * WPILib {@link Alert}. Complements {@code frc.robot.diagnostics.SparkAlertLogger}, which handles
 * REV-specific motor faults; {@code FaultMonitor} covers everything else (vision disconnect, pose
 * divergence, CAN bus saturation, battery critical, etc.).
 *
 * <p>Adapted from Team 862 LightningLib's {@code FaultMonitor}. This class is the instant
 * variant — the fault flag flips the same tick the supplier returns {@code true}. For time-based
 * gating (fault must persist for N seconds), use {@link TimedFaultMonitor}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * private final FaultMonitor visionDisconnect = new FaultMonitor(
 *     "Vision/LimelightDisconnected",
 *     AlertType.kWarning,
 *     () -> !vision.hasTarget() && Timer.getFPGATimestamp() - lastSeen > 2.0);
 *
 * public void periodic() {
 *   visionDisconnect.poll();
 *   // … other checks …
 * }
 * }</pre>
 *
 * <p>Tests use the package-private constructor with a custom sink to avoid constructing an {@link
 * Alert} (which touches WPILib's SendableRegistry).
 */
public final class FaultMonitor {

  private final BooleanSupplier check;
  private final Consumer<Boolean> sink;
  private boolean lastState = false;

  /** Production constructor — backs the monitor with a WPILib {@link Alert}. */
  public FaultMonitor(String faultText, AlertType type, BooleanSupplier check) {
    this(check, new Alert(faultText, type)::set);
  }

  /** Package-private ctor — accepts a custom sink for HAL-free unit tests. */
  FaultMonitor(BooleanSupplier check, Consumer<Boolean> sink) {
    this.check = check;
    this.sink = sink;
  }

  /** Polls the supplier and forwards the result to the sink. Call once per robot cycle. */
  public void poll() {
    lastState = check.getAsBoolean();
    sink.accept(lastState);
  }

  /** @return the result of the last {@link #poll} call. Useful for chaining or test inspection. */
  public boolean isActive() {
    return lastState;
  }
}
