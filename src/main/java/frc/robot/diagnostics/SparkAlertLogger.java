package frc.robot.diagnostics;

import com.revrobotics.spark.SparkBase;
import edu.wpi.first.wpilibj.Alert;
import edu.wpi.first.wpilibj.Alert.AlertType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Maps each REV SPARK sticky fault and warning bit to a WPILib {@link Alert}. When a bit becomes
 * sticky-active, the dashboard lights up with <code>&lt;motor name&gt;: &lt;bit name&gt;</code> —
 * huge QoL for event-day CAN diagnosis.
 *
 * <p>Adapted from Team 4481 Rembrandts' SparkAlertLogger pattern. Uses sticky bits so faults
 * remain visible after the underlying condition recovers (call {@code clearFaults()} on the Spark
 * to reset).
 *
 * <p>Usage in an IOReal constructor:
 *
 * <pre>{@code
 * private final SparkAlertLogger sparkAlerts = new SparkAlertLogger();
 *
 * public FlywheelIOReal() {
 *   leftVortex = new SparkFlex(...);
 *   rightVortex = new SparkFlex(...);
 *   // ... configure ...
 *   sparkAlerts
 *       .register(leftVortex, "Flywheel/leftVortex")
 *       .register(rightVortex, "Flywheel/rightVortex");
 * }
 *
 * public void updateInputs(...) {
 *   // ...
 *   sparkAlerts.periodic();
 * }
 * }</pre>
 *
 * <p>This class is in {@code frc.robot.diagnostics} (not {@code frc.lib.diagnostics}) because it
 * has a hard dependency on REVLib and can't be unit-tested without HAL; {@code frc.lib} is
 * reserved for pure-math utilities held to an 80% line-coverage gate.
 */
public final class SparkAlertLogger {

  private static final String ALERT_GROUP = "SparkFaults";

  private final List<BitMonitor> monitors = new ArrayList<>();

  /** Registers all 15 fault + warning bits on a Spark. Fluent to allow chaining. */
  public SparkAlertLogger register(SparkBase spark, String motorName) {
    // ─── Faults (errors) ─────────────────────────────────────────────────
    addMonitor(motorName, "Fault/Other", AlertType.kError, () -> spark.getStickyFaults().other);
    addMonitor(
        motorName, "Fault/MotorType", AlertType.kError, () -> spark.getStickyFaults().motorType);
    addMonitor(motorName, "Fault/Sensor", AlertType.kError, () -> spark.getStickyFaults().sensor);
    addMonitor(
        motorName,
        "Fault/Temperature",
        AlertType.kError,
        () -> spark.getStickyFaults().temperature);
    addMonitor(
        motorName,
        "Fault/GateDriver",
        AlertType.kError,
        () -> spark.getStickyFaults().gateDriver);
    addMonitor(
        motorName, "Fault/EscEeprom", AlertType.kError, () -> spark.getStickyFaults().escEeprom);
    addMonitor(
        motorName, "Fault/Firmware", AlertType.kError, () -> spark.getStickyFaults().firmware);

    // ─── Warnings (recoverable) ──────────────────────────────────────────
    addMonitor(
        motorName,
        "Warn/Brownout",
        AlertType.kWarning,
        () -> spark.getStickyWarnings().brownout);
    addMonitor(
        motorName,
        "Warn/Overcurrent",
        AlertType.kWarning,
        () -> spark.getStickyWarnings().overcurrent);
    addMonitor(
        motorName,
        "Warn/EscEeprom",
        AlertType.kWarning,
        () -> spark.getStickyWarnings().escEeprom);
    addMonitor(
        motorName,
        "Warn/ExtEeprom",
        AlertType.kWarning,
        () -> spark.getStickyWarnings().extEeprom);
    addMonitor(
        motorName, "Warn/Sensor", AlertType.kWarning, () -> spark.getStickyWarnings().sensor);
    addMonitor(
        motorName, "Warn/Stall", AlertType.kWarning, () -> spark.getStickyWarnings().stall);
    addMonitor(
        motorName,
        "Warn/HasReset",
        AlertType.kWarning,
        () -> spark.getStickyWarnings().hasReset);
    addMonitor(motorName, "Warn/Other", AlertType.kWarning, () -> spark.getStickyWarnings().other);
    return this;
  }

  private void addMonitor(
      String motorName, String bitName, AlertType type, BooleanSupplier reader) {
    Alert alert = new Alert(ALERT_GROUP, motorName + ": " + bitName, type);
    monitors.add(new BitMonitor(reader, alert));
  }

  /** Polls every registered bit and updates its Alert's active state. Call once per robot cycle. */
  public void periodic() {
    for (BitMonitor m : monitors) {
      m.alert.set(m.reader.getAsBoolean());
    }
  }

  private record BitMonitor(BooleanSupplier reader, Alert alert) {}
}
