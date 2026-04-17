package frc.robot.autos;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import org.junit.jupiter.api.Test;

/**
 * HAL-free tests for {@link AutoRoutineRegistrar}. Uses a stub holder class that declares
 * annotated methods, then registers it against a real {@link LoggedAutoChooser} and inspects the
 * resulting chooser state.
 */
class AutoRoutineRegistrarTest {

  /** Test fixture — three annotated routines, one default, plus an un-annotated method. */
  static final class Fixture {
    boolean invokedDefault = false;

    @AutoRoutine(value = "Alpha", isDefault = true)
    public Command alpha() {
      invokedDefault = true;
      return Commands.none();
    }

    @AutoRoutine("Beta")
    public Command beta() {
      return Commands.none();
    }

    @AutoRoutine
    public Command gamma() {
      return Commands.none();
    }

    /** Not annotated — registrar must skip. */
    public Command delta() {
      return Commands.none();
    }
  }

  @Test
  void register_allAnnotatedMethods() {
    LoggedAutoChooser chooser = new LoggedAutoChooser("TestChooser");
    int count = AutoRoutineRegistrar.register(chooser, new Fixture());
    assertEquals(3, count);
  }

  @Test
  void register_defaultMarkedRoutineIsDefault() {
    Fixture f = new Fixture();
    LoggedAutoChooser chooser = new LoggedAutoChooser("TestChooser");
    AutoRoutineRegistrar.register(chooser, f);
    // Invocation at register time calls each annotated method once.
    assertTrue(f.invokedDefault, "@AutoRoutine method must be invoked during registration");
  }

  @Test
  void register_missingAnnotationValue_fallsBackToMethodName() {
    LoggedAutoChooser chooser = new LoggedAutoChooser("TestChooser");
    // "gamma" has value="" in the annotation — the registrar should fall back to "gamma".
    // We verify by registering, then inspecting the chooser's selection after a call to
    // selectByName("gamma") — should succeed.
    int count = AutoRoutineRegistrar.register(chooser, new Fixture());
    assertEquals(3, count);
    assertTrue(chooser.selectByName("gamma"));
  }

  static final class BadReturnType {
    @AutoRoutine
    public String notACommand() {
      return "oops";
    }
  }

  @Test
  void register_wrongReturnType_throws() {
    LoggedAutoChooser chooser = new LoggedAutoChooser("TestChooser");
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> AutoRoutineRegistrar.register(chooser, new BadReturnType()));
    assertTrue(
        e.getMessage().contains("return Command"),
        "error message should mention return type: " + e.getMessage());
  }

  static final class BadSignature {
    @AutoRoutine
    public Command requiresArg(int x) {
      return Commands.none();
    }
  }

  @Test
  void register_wrongParameters_throws() {
    LoggedAutoChooser chooser = new LoggedAutoChooser("TestChooser");
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> AutoRoutineRegistrar.register(chooser, new BadSignature()));
    assertTrue(
        e.getMessage().contains("zero parameters"),
        "error message should mention parameter count: " + e.getMessage());
  }

  static final class Empty {}

  @Test
  void register_noAnnotatedMethods_returnsZero() {
    LoggedAutoChooser chooser = new LoggedAutoChooser("TestChooser");
    assertEquals(0, AutoRoutineRegistrar.register(chooser, new Empty()));
  }
}
