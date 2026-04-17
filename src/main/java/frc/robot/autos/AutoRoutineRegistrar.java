package frc.robot.autos;

import edu.wpi.first.wpilibj2.command.Command;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Walks a target instance's methods, finds every one annotated with {@link AutoRoutine}, and
 * registers it on the given {@link LoggedAutoChooser}. Companion to the annotation.
 *
 * <p>Runtime-reflective, zero code generation. Errors are explicit — a method with the wrong
 * return type or signature throws {@link IllegalStateException} at register time with a pointer
 * to the offending method.
 *
 * <p>The registrar alphabetises the routines before registration so the chooser displays them in
 * a stable order regardless of Java's reflection ordering (which is JVM-implementation-defined).
 */
public final class AutoRoutineRegistrar {

  private AutoRoutineRegistrar() {}

  /**
   * Scan {@code target} (or, if {@code target} is a Class, its static methods) for
   * {@code @AutoRoutine}-annotated methods and register each on {@code chooser}.
   *
   * @return number of routines registered
   */
  public static int register(LoggedAutoChooser chooser, Object target) {
    Class<?> cls = (target instanceof Class<?> c) ? c : target.getClass();
    Object instance = (target instanceof Class<?>) ? null : target;

    // Sort alphabetically by display name (value() or method name) for stable chooser order.
    Method[] methods = cls.getDeclaredMethods();
    Arrays.sort(
        methods,
        Comparator.comparing((Method m) -> displayName(m)).thenComparing(Method::getName));

    int count = 0;
    for (Method method : methods) {
      AutoRoutine ann = method.getAnnotation(AutoRoutine.class);
      if (ann == null) {
        continue;
      }
      validate(method);
      String name = displayName(method);
      Command command = invoke(method, instance);
      if (ann.isDefault()) {
        chooser.setDefaultOption(name, command);
      } else {
        chooser.addOption(name, command);
      }
      count++;
    }
    return count;
  }

  private static String displayName(Method m) {
    AutoRoutine ann = m.getAnnotation(AutoRoutine.class);
    if (ann == null) {
      return m.getName();
    }
    return ann.value().isBlank() ? m.getName() : ann.value();
  }

  private static void validate(Method method) {
    if (!Command.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalStateException(
          "@AutoRoutine method "
              + method.getDeclaringClass().getSimpleName()
              + "#"
              + method.getName()
              + " must return Command, got "
              + method.getReturnType().getSimpleName());
    }
    if (method.getParameterCount() != 0) {
      throw new IllegalStateException(
          "@AutoRoutine method "
              + method.getDeclaringClass().getSimpleName()
              + "#"
              + method.getName()
              + " must take zero parameters; got "
              + method.getParameterCount());
    }
  }

  private static Command invoke(Method method, Object instance) {
    try {
      method.setAccessible(true);
      return (Command) method.invoke(instance);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Failed to invoke @AutoRoutine method "
              + method.getDeclaringClass().getSimpleName()
              + "#"
              + method.getName(),
          e);
    }
  }
}
