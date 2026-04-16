package frc.lib;

import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.NetworkTableInstance;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import org.littletonrobotics.junction.Logger;

/**
 * A NetworkTables-backed tunable double that lets teammates adjust constants live from
 * AdvantageScope or Shuffleboard without redeploying code.
 *
 * <p>Pattern popularized by Team 5940 BREAD and Team 6328 Mechanical Advantage. Publishes under
 * {@code /Tuning/<key>} and mirrors every read to AdvantageKit via {@code Logger.recordOutput}.
 *
 * <p>Change tracking via {@link #hasChanged(int)} is per-caller: each caller gets an independent
 * view of whether the value has changed since they last checked. Uses a {@link ConcurrentHashMap}
 * so robot loop and dashboard threads can safely read simultaneously.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * private static final LoggedTunableNumber kP = new LoggedTunableNumber("Flywheel/kP", 0.5);
 *
 * // In periodic:
 * if (kP.hasChanged(hashCode())) {
 *   controller.setP(kP.get());
 * }
 * }</pre>
 *
 * <p>Tests should use the package-private constructor that accepts a {@link DoubleSupplier}
 * (StallDetector pattern) — this keeps unit tests free of NetworkTables native loads.
 */
public final class LoggedTunableNumber {

  private static final String TABLE_PREFIX = "/Tuning/";

  private final String key;
  private final double defaultValue;
  private final DoubleSupplier valueSource;

  /** Last-seen value for each caller ID, used for change detection. */
  private final ConcurrentHashMap<Integer, Double> lastValues = new ConcurrentHashMap<>();

  /**
   * Creates a tunable number published under {@code /Tuning/<key>}.
   *
   * @param key the NT key (may include slashes for sub-tables, e.g. {@code "Flywheel/kP"})
   * @param defaultValue the value returned when NT has no override
   */
  public LoggedTunableNumber(String key, double defaultValue) {
    this(key, defaultValue, networkTablesSource(key, defaultValue));
  }

  /**
   * Package-private constructor that accepts an arbitrary value source. Used by tests to inject a
   * mutable {@link DoubleSupplier} without triggering the NetworkTables native load, mirroring the
   * pattern used by {@code StallDetector}.
   */
  LoggedTunableNumber(String key, double defaultValue, DoubleSupplier valueSource) {
    this.key = key;
    this.defaultValue = defaultValue;
    this.valueSource = valueSource;
  }

  /** Builds the NT publisher+subscriber pair and returns a supplier reading from NT. */
  private static DoubleSupplier networkTablesSource(String key, double defaultValue) {
    var topic = NetworkTableInstance.getDefault().getDoubleTopic(TABLE_PREFIX + key);
    DoublePublisher publisher = topic.publish();
    publisher.set(defaultValue);
    DoubleSubscriber subscriber = topic.subscribe(defaultValue);
    return () -> subscriber.get(defaultValue);
  }

  /**
   * Returns the current value from the backing source (NetworkTables in production, an injected
   * supplier in tests). Mirrors the value to AdvantageKit (no-op when logger is not running).
   */
  public double get() {
    double value = valueSource.getAsDouble();
    Logger.recordOutput("Tuning/" + key, value);
    return value;
  }

  /**
   * Returns {@code true} if the value has changed since the last call from this caller.
   *
   * <p>The caller is identified by {@code callerId} — typically {@code hashCode()} of the enclosing
   * object or a unique constant. Different callers track changes independently.
   *
   * @param callerId an integer that uniquely identifies the call site
   */
  public boolean hasChanged(int callerId) {
    double current = get();
    Double last = lastValues.get(callerId);
    if (last == null || last != current) {
      lastValues.put(callerId, current);
      return true;
    }
    return false;
  }

  /** Returns the default value originally passed to the constructor. */
  public double getDefault() {
    return defaultValue;
  }

  /** Returns the full NT key, e.g. {@code "/Tuning/Flywheel/kP"}. */
  public String getKey() {
    return TABLE_PREFIX + key;
  }
}
