package frc.robot.autos;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.wpilibj2.command.Commands;
import java.util.List;
import org.junit.jupiter.api.Test;

class RandomAutoRotatorTest {

  private static LoggedAutoChooser makeChooser(String... names) {
    LoggedAutoChooser chooser = new LoggedAutoChooser("RotatorTest");
    for (String name : names) {
      chooser.addOption(name, Commands.none());
    }
    return chooser;
  }

  @Test
  void advance_cyclesThroughAllOptions() {
    LoggedAutoChooser chooser = makeChooser("A", "B", "C");
    RandomAutoRotator rotator = new RandomAutoRotator(chooser, List.of("A", "B", "C"));
    assertEquals("A", rotator.advance());
    assertEquals("B", rotator.advance());
    assertEquals("C", rotator.advance());
  }

  @Test
  void advance_wrapsAroundAfterLast() {
    LoggedAutoChooser chooser = makeChooser("A", "B");
    RandomAutoRotator rotator = new RandomAutoRotator(chooser, List.of("A", "B"));
    rotator.advance();
    rotator.advance();
    assertEquals("A", rotator.advance(), "rotator must wrap back to first entry");
  }

  @Test
  void randomize_reshufflesDeterministicallyForSameSeed() {
    LoggedAutoChooser chooser = makeChooser("A", "B", "C", "D");
    RandomAutoRotator r1 = new RandomAutoRotator(chooser, List.of("A", "B", "C", "D"));
    RandomAutoRotator r2 = new RandomAutoRotator(chooser, List.of("A", "B", "C", "D"));
    r1.randomize(42L);
    r2.randomize(42L);
    assertEquals(r1.currentOrder(), r2.currentOrder());
  }

  @Test
  void randomize_differentSeeds_differentOrder() {
    LoggedAutoChooser chooser = makeChooser("A", "B", "C", "D", "E");
    RandomAutoRotator r1 = new RandomAutoRotator(chooser, List.of("A", "B", "C", "D", "E"));
    RandomAutoRotator r2 = new RandomAutoRotator(chooser, List.of("A", "B", "C", "D", "E"));
    r1.randomize(1L);
    r2.randomize(999L);
    assertNotEquals(r1.currentOrder(), r2.currentOrder());
  }

  @Test
  void randomize_resetsCursor() {
    LoggedAutoChooser chooser = makeChooser("A", "B", "C");
    RandomAutoRotator rotator = new RandomAutoRotator(chooser, List.of("A", "B", "C"));
    rotator.advance();
    rotator.advance();
    assertEquals(2, rotator.currentCursor());
    rotator.randomize(1L);
    assertEquals(0, rotator.currentCursor(), "randomize must reset cursor");
  }

  @Test
  void advance_emptyOptions_returnsEmptyString() {
    LoggedAutoChooser chooser = makeChooser();
    RandomAutoRotator rotator = new RandomAutoRotator(chooser, List.of());
    assertEquals("", rotator.advance());
  }
}
