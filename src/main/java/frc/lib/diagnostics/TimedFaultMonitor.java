package frc.lib.diagnostics;

import edu.wpi.first.wpilibj.Alert;
import edu.wpi.first.wpilibj.Alert.AlertType;
import edu.wpi.first.wpilibj.Timer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * Fault monitor with a persistence gate — the underlying signal must stay true for at least
 * {@code debounceSeconds} before the alert fires. Adapted from Team 862 LightningLib.
 *
 * <p>Useful for transient signals you don't want to alarm on: battery dip during a brownout
 * ramp, a single-frame vision dropout, a CAN blip. Set the gate large enough to filter noise,
 * small enough that real persistent faults still surface quickly.
 *
 * <p>Separate from {@link FaultMonitor} (not a subclass) because the gating logic is
 * fundamentally different — the instant version emits each tick's value, the timed version
 * withholds until the persistence window elapses.
 *
 * <p>Uses an injectable {@link DoubleSupplier} time source for HAL-free testing.
 */
public final class TimedFaultMonitor {

  private final BooleanSupplier check;
  private final Consumer<Boolean> sink;
  private final double debounceSeconds;
  private final DoubleSupplier timeSource;

  /** Time at which {@code check} first went true for the current streak. NaN when idle. */
  private double faultActiveSinceSeconds = Double.NaN;

  /** Latched gated state — the value the sink last saw. */
  private boolean gatedActive = false;

  /** Production ctor — FPGA time + WPILib {@link Alert} sink. */
  public TimedFaultMonitor(
      String faultText, AlertType type, BooleanSupplier check, double debounceSeconds) {
    this(check, new Alert(faultText, type)::set, debounceSeconds, Timer::getFPGATimestamp);
  }

  /** Package-private ctor — injectable sink + time source for unit tests. */
  TimedFaultMonitor(
      BooleanSupplier check,
      Consumer<Boolean> sink,
      double debounceSeconds,
      DoubleSupplier timeSource) {
    if (debounceSeconds < 0) {
      throw new IllegalArgumentException("debounceSeconds must be >= 0");
    }
    this.check = check;
    this.sink = sink;
    this.debounceSeconds = debounceSeconds;
    this.timeSource = timeSource;
  }

  /** Polls the supplier, applies the persistence gate, and forwards the gated state to the sink. */
  public void poll() {
    boolean raw = check.getAsBoolean();
    double now = timeSource.getAsDouble();

    if (!raw) {
      faultActiveSinceSeconds = Double.NaN;
      gatedActive = false;
    } else {
      if (Double.isNaN(faultActiveSinceSeconds)) {
        faultActiveSinceSeconds = now;
      }
      gatedActive = (now - faultActiveSinceSeconds) >= debounceSeconds;
    }

    sink.accept(gatedActive);
  }

  /** @return the gated state — the value last forwarded to the sink. */
  public boolean isActive() {
    return gatedActive;
  }

  /** @return seconds the underlying check has been continuously true; 0 when not active. */
  public double activeDurationSeconds() {
    if (Double.isNaN(faultActiveSinceSeconds)) {
      return 0.0;
    }
    return timeSource.getAsDouble() - faultActiveSinceSeconds;
  }
}
