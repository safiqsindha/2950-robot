package frc.robot.autos;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a declaratively-registered autonomous routine.
 *
 * <p>Adapted from Team 4481's {@code @AutoRoutine} pattern. Annotate a {@code public static} (or
 * instance) method returning a {@code Command}; {@link AutoRoutineRegistrar} walks the target
 * class with reflection and adds each annotated method to a {@link LoggedAutoChooser} under the
 * name {@link #value()} (falling back to the method name if unset).
 *
 * <p>Runtime-reflective — no annotation processor, no code generation. That keeps the build
 * simple and the mechanism transparent; the trade-off is that typos in a routine's signature
 * (e.g. wrong return type) surface at boot, not compile. The registrar's error messages are
 * explicit.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * public final class RoutineLibrary {
 *   @AutoRoutine("Leave Only")
 *   public Command leaveOnly() { return ChoreoAutoCommand.leaveRoutine(factory).cmd(); }
 *
 *   @AutoRoutine  // name defaults to method name
 *   public Command scoreAndLeave() { return ChoreoAutoCommand.scoreAndLeaveRoutine(...).cmd(); }
 * }
 *
 * // In RobotContainer:
 * AutoRoutineRegistrar.register(autoChooser, new RoutineLibrary());
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AutoRoutine {

  /**
   * Human-readable display name shown in the chooser. If blank, the registrar falls back to the
   * method's simple name.
   */
  String value() default "";

  /**
   * Whether this routine should be the default selection. Only one routine per chooser should
   * set this {@code true}; behaviour is undefined if multiple do (first wins, alphabetical).
   */
  boolean isDefault() default false;
}
