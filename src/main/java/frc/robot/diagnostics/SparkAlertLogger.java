package frc.robot.diagnostics;

import com.revrobotics.spark.SparkBase;
import edu.wpi.first.wpilibj.Alert;
import edu.wpi.first.wpilibj.Alert.AlertType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * Maps each REV SPARK sticky fault and warning bit to a WPILib {@link Alert}. When a bit becomes
 * sticky-active, the dashboard lights up with <code>&lt;motor name&gt;: &lt;bit name&gt;</code> —
 * huge QoL for event-day CAN diagnosis.
 *
 * <p>Adapted from Team 4481 Rembrandts' SparkAlertLogger pattern. Uses sticky bits so faults
 * remain visible after the underlying condition recovers (call {@code clearFaults()} on the Spark
 * to reset).
 *
 * <p>Also publishes a cumulative <b>transition count</b> per bit under
 * {@code Faults/&lt;motorName&gt;/&lt;bitName&gt;_Count}. Each false→true transition increments
 * the counter so post-match replay can see "this Spark tripped brownout 4 times" even after
 * {@code clearFaults()} resets the sticky state between increments.
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

  /**
   * Registers all 15 fault + warning bits on a Spark. Fluent to allow chaining.
   *
   * <p>The {@code motorName} appears in both the DS alert text <i>and</i> as part of the
   * AdvantageKit log key: {@code Faults/&lt;motorName&gt;/&lt;bitName&gt;_Count}. Changing this
   * name silently breaks any AdvantageScope layout or dashboard binding that referenced the old
   * path. If you rename, update {@code advantagescope-layout.json}'s Spark-faults tab as well.
   *
   * <p>Canonical motor names used in this repo (keep these stable):
   *
   * <ul>
   *   <li>{@code Flywheel/leftVortex}, {@code Flywheel/rightVortex}, {@code Flywheel/frontWheel},
   *       {@code Flywheel/backWheel}
   *   <li>{@code Intake/leftArm}, {@code Intake/rightArm}, {@code Intake/wheel}
   *   <li>{@code Conveyor/conveyor}, {@code Conveyor/spindexer}
   *   <li>{@code Swerve/Module&lt;N&gt;/drive}, {@code Swerve/Module&lt;N&gt;/angle} for N in 0..3
   * </ul>
   */
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
    monitors.add(new BitMonitor(motorName, bitName, reader, alert));
  }

  /**
   * Polls every registered bit, updates its Alert's active state, and increments a cumulative
   * transition counter on every false→true edge. Call once per robot cycle.
   *
   * <p>Defensive: an individual bit read that throws (e.g. REVLib returns null from
   * {@code getStickyFaults()} during a transient CAN disconnect, or a lambda is invoked against
   * a closed Spark) is caught per-bit and counted as "not active" for that tick. Without this
   * guard, one misbehaving Spark could take down the whole robot loop via an NPE propagating
   * into CommandScheduler.
   */
  public void periodic() {
    for (BitMonitor m : monitors) {
      boolean current;
      try {
        current = m.reader.getAsBoolean();
      } catch (RuntimeException e) {
        // Treat as false this tick; leave the Alert + counter untouched. A real persistent
        // fault will show up via the other monitors (e.g. a Spark-disconnect alert elsewhere).
        current = false;
      }
      if (current && !m.lastState) {
        m.transitions++;
      }
      m.lastState = current;
      m.alert.set(current);
      Logger.recordOutput(
          "Faults/" + m.motorName + "/" + m.bitName + "_Count", m.transitions);
    }
  }

  /**
   * Total transition count across every registered bit. Package-private; main exposure is via
   * the per-bit {@code Faults/.../*_Count} keys.
   */
  long totalTransitions() {
    long total = 0;
    for (BitMonitor m : monitors) {
      total += m.transitions;
    }
    return total;
  }

  private static final class BitMonitor {
    final String motorName;
    final String bitName;
    final BooleanSupplier reader;
    final Alert alert;
    boolean lastState = false;
    long transitions = 0;

    BitMonitor(String motorName, String bitName, BooleanSupplier reader, Alert alert) {
      this.motorName = motorName;
      this.bitName = bitName;
      this.reader = reader;
      this.alert = alert;
    }
  }
}
