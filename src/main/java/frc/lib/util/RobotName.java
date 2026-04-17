package frc.lib.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * File-backed identifier for WHICH physical robot this roboRIO is running on. Adapted from Team
 * 3005 RoboChargers' {@code RobotName}.
 *
 * <p>Solution to the "practice bot vs. comp bot" constants-divergence problem. Each physical bot
 * writes its name to {@code /home/lvuser/ROBOT_NAME} once (via SSH or a one-time deploy). At
 * runtime, {@link #current()} reads the file and returns a typed enum so constants tables can fork
 * per-bot without env vars, Preferences, or branch management.
 *
 * <pre>{@code
 * public static final double kDriveGearRatio =
 *     RobotName.select(
 *         6.23,                                // default (unknown bot)
 *         RobotName.COMP, 6.23,
 *         RobotName.PRACTICE, 6.12);           // practice bot uses old modules
 * }</pre>
 *
 * <p>Default file path: {@code /home/lvuser/ROBOT_NAME}. Alternate path can be injected for testing
 * via {@link #setFilePath(Path)}.
 *
 * <p>All enum values here are placeholders — rename freely to match your fleet.
 */
public enum RobotName {
  /** The competition robot. First choice for anything that needs to "just work" at an event. */
  COMP,
  /** The practice / programming bot. May have older modules or different mechanicals. */
  PRACTICE,
  /** A drive-only chassis used for sim / onboarding. No full superstructure. */
  PROGRAMMING,
  /** Value returned when no {@code ROBOT_NAME} file is present or parseable. */
  UNKNOWN;

  private static Path filePath = Paths.get("/home/lvuser/ROBOT_NAME");
  private static RobotName cachedValue = null;

  /**
   * Override the path from which {@link #current()} reads the identifier. Package-private for unit
   * testing — production code uses the default {@code /home/lvuser/ROBOT_NAME}.
   */
  static void setFilePath(Path path) {
    filePath = path;
    cachedValue = null; // force re-read
  }

  /**
   * @return the robot name read from the ROBOT_NAME file (cached after first call), or {@link
   *     #UNKNOWN} if the file is missing / empty / unparseable.
   */
  public static RobotName current() {
    if (cachedValue != null) {
      return cachedValue;
    }
    cachedValue = readFromFile(filePath).orElse(UNKNOWN);
    return cachedValue;
  }

  /** Clear the cached value so a subsequent {@link #current()} re-reads the file. */
  static void clearCache() {
    cachedValue = null;
  }

  /** Parse the file without caching. Package-private for testing. */
  static Optional<RobotName> readFromFile(Path path) {
    try {
      if (!Files.exists(path)) {
        return Optional.empty();
      }
      String content = Files.readString(path).trim().toUpperCase();
      if (content.isEmpty()) {
        return Optional.empty();
      }
      for (RobotName name : values()) {
        if (name.name().equals(content)) {
          return Optional.of(name);
        }
      }
      return Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /**
   * Pick a value based on the current robot name. Defaults are returned when the current name
   * doesn't appear in the overrides.
   *
   * @param defaultValue returned when no override matches
   * @param overrides alternating name/value pairs — e.g. {@code (COMP, v1, PRACTICE, v2)}
   */
  @SuppressWarnings("unchecked")
  public static <T> T select(T defaultValue, Object... overrides) {
    if (overrides.length % 2 != 0) {
      throw new IllegalArgumentException("overrides must alternate name/value pairs");
    }
    RobotName current = current();
    for (int i = 0; i < overrides.length; i += 2) {
      if (!(overrides[i] instanceof RobotName)) {
        throw new IllegalArgumentException("odd args must be RobotName instances");
      }
      if (overrides[i] == current) {
        return (T) overrides[i + 1];
      }
    }
    return defaultValue;
  }
}
