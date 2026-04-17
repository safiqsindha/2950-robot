package frc.lib.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RobotNameTest {

  @AfterEach
  void restoreDefault() {
    // Tests mutate the static file path; reset to avoid leaking between tests.
    RobotName.setFilePath(Path.of("/home/lvuser/ROBOT_NAME"));
    RobotName.clearCache();
  }

  @Test
  void readFromFile_missingFile_returnsEmpty(@TempDir Path tmp) {
    var missing = tmp.resolve("nope");
    assertTrue(RobotName.readFromFile(missing).isEmpty());
  }

  @Test
  void readFromFile_emptyFile_returnsEmpty(@TempDir Path tmp) throws IOException {
    var f = Files.writeString(tmp.resolve("ROBOT_NAME"), "");
    assertTrue(RobotName.readFromFile(f).isEmpty());
  }

  @Test
  void readFromFile_whitespaceOnly_returnsEmpty(@TempDir Path tmp) throws IOException {
    var f = Files.writeString(tmp.resolve("ROBOT_NAME"), "   \n  \t");
    assertTrue(RobotName.readFromFile(f).isEmpty());
  }

  @Test
  void readFromFile_compUppercase(@TempDir Path tmp) throws IOException {
    var f = Files.writeString(tmp.resolve("ROBOT_NAME"), "COMP");
    assertEquals(RobotName.COMP, RobotName.readFromFile(f).orElseThrow());
  }

  @Test
  void readFromFile_compLowercase_accepted(@TempDir Path tmp) throws IOException {
    // File content is case-insensitive — trim + upper before comparing.
    var f = Files.writeString(tmp.resolve("ROBOT_NAME"), "comp\n");
    assertEquals(RobotName.COMP, RobotName.readFromFile(f).orElseThrow());
  }

  @Test
  void readFromFile_unknownString_returnsEmpty(@TempDir Path tmp) throws IOException {
    var f = Files.writeString(tmp.resolve("ROBOT_NAME"), "SuperSecretBot");
    assertTrue(RobotName.readFromFile(f).isEmpty());
  }

  @Test
  void current_noFile_returnsUnknown(@TempDir Path tmp) {
    RobotName.setFilePath(tmp.resolve("missing"));
    assertEquals(RobotName.UNKNOWN, RobotName.current());
  }

  @Test
  void current_practiceFile_returnsPractice(@TempDir Path tmp) throws IOException {
    var f = Files.writeString(tmp.resolve("ROBOT_NAME"), "PRACTICE");
    RobotName.setFilePath(f);
    assertEquals(RobotName.PRACTICE, RobotName.current());
  }

  @Test
  void current_caches_firstReadWins(@TempDir Path tmp) throws IOException {
    var f = Files.writeString(tmp.resolve("ROBOT_NAME"), "COMP");
    RobotName.setFilePath(f);
    assertEquals(RobotName.COMP, RobotName.current());
    // Modify the file AFTER the first read — cached value should still be COMP.
    Files.writeString(f, "PRACTICE");
    assertEquals(RobotName.COMP, RobotName.current());
  }

  @Test
  void select_noOverrides_returnsDefault(@TempDir Path tmp) {
    RobotName.setFilePath(tmp.resolve("missing")); // UNKNOWN
    assertEquals(42.0, RobotName.select(42.0), 1e-9);
  }

  @Test
  void select_matchingOverride_wins(@TempDir Path tmp) throws IOException {
    Files.writeString(Files.createFile(tmp.resolve("ROBOT_NAME")), "COMP");
    RobotName.setFilePath(tmp.resolve("ROBOT_NAME"));
    double result = RobotName.select(1.0, RobotName.COMP, 2.0, RobotName.PRACTICE, 3.0);
    assertEquals(2.0, result, 1e-9);
  }

  @Test
  void select_noMatchingOverride_returnsDefault(@TempDir Path tmp) throws IOException {
    Files.writeString(Files.createFile(tmp.resolve("ROBOT_NAME")), "PROGRAMMING");
    RobotName.setFilePath(tmp.resolve("ROBOT_NAME"));
    double result = RobotName.select(1.0, RobotName.COMP, 2.0, RobotName.PRACTICE, 3.0);
    assertEquals(1.0, result, 1e-9);
  }

  @Test
  void select_oddNumberOfArgs_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> RobotName.select(0.0, RobotName.COMP, 1.0, RobotName.PRACTICE));
  }

  @Test
  void select_nonRobotNameKey_throws() {
    assertThrows(IllegalArgumentException.class, () -> RobotName.select(0.0, "COMP", 1.0));
  }
}
