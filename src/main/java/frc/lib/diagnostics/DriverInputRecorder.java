package frc.lib.diagnostics;

import java.util.function.DoubleSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * Snapshots driver controller axes + trigger values to the AdvantageKit log every cycle. Useful
 * for post-match debriefs: "was the driver actually holding the joystick forward when we
 * stalled?" — replay answers instantly.
 *
 * <p>Generic over N double-valued axes. Each axis is supplied via a {@link DoubleSupplier} so
 * the recorder never touches the WPILib controller classes directly and stays testable.
 *
 * <p>Log keys (all under {@code DriverInput/}):
 *
 * <ul>
 *   <li>{@code DriverInput/{axisName}} — the current value for each registered axis
 * </ul>
 */
public final class DriverInputRecorder {

  private final Axis[] axes;

  private DriverInputRecorder(Axis[] axes) {
    this.axes = axes;
  }

  /**
   * Build a recorder with the given named axes. Pass {@code DoubleSupplier} lambdas for each —
   * typically {@code () -> controller.getLeftY()} and friends.
   */
  public static DriverInputRecorder of(Axis... axes) {
    return new DriverInputRecorder(axes);
  }

  /** Emit one snapshot. Cheap — one log write per axis. */
  public void periodic() {
    for (Axis axis : axes) {
      Logger.recordOutput("DriverInput/" + axis.name, axis.supplier.getAsDouble());
    }
  }

  /** Named axis descriptor — pairs a log-key suffix with a value supplier. */
  public record Axis(String name, DoubleSupplier supplier) {}
}
