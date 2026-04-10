package frc.robot;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Meta-test that parses Constants.java as text to detect CAN ID conflicts, out-of-range IDs, and
 * missing critical motor definitions. Does NOT import frc.robot.Constants to avoid HAL loading.
 */
class CANIDConflictTest {

  private static final Path CONSTANTS_PATH = Path.of("src/main/java/frc/robot/Constants.java");

  private static final Pattern CAN_ID_PATTERN = Pattern.compile("(k\\w*[Ii][Dd])\\s*=\\s*(\\d+)");

  private Map<Integer, String> parseAllCANIDs() throws Exception {
    String source = Files.readString(CONSTANTS_PATH);
    Matcher matcher = CAN_ID_PATTERN.matcher(source);
    Map<Integer, String> idMap = new HashMap<>();
    while (matcher.find()) {
      String name = matcher.group(1);
      int id = Integer.parseInt(matcher.group(2));
      // Collect all — caller decides how to assert
      if (idMap.containsKey(id)) {
        fail("CAN ID conflict: " + name + " and " + idMap.get(id) + " both use ID " + id);
      }
      idMap.put(id, name);
    }
    return idMap;
  }

  @Test
  void noDuplicateCANIDs() throws Exception {
    Map<Integer, String> idMap = parseAllCANIDs();
    assertFalse(idMap.isEmpty(), "Should have found at least one CAN ID constant");
  }

  @Test
  void allCANIDsInValidRange() throws Exception {
    String source = Files.readString(CONSTANTS_PATH);
    Matcher matcher = CAN_ID_PATTERN.matcher(source);

    int count = 0;
    while (matcher.find()) {
      String name = matcher.group(1);
      int id = Integer.parseInt(matcher.group(2));
      assertTrue(id >= 0 && id <= 62, "CAN ID out of range [0-62]: " + name + " = " + id);
      count++;
    }
    assertTrue(count > 0, "Should have found at least one CAN ID constant");
  }

  @Test
  void knownCriticalIDsPresent() throws Exception {
    Map<Integer, String> idMap = parseAllCANIDs();
    // Flywheel (4) + Intake (3) + Conveyor (2) = at least 9 motor CAN IDs
    assertTrue(idMap.size() >= 9, "Should find at least 9 motor CAN IDs, found " + idMap.size());
  }
}
