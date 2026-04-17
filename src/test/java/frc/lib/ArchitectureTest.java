package frc.lib;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Package-dependency guardrails enforced at test time via ArchUnit.
 *
 * <p>The rules codify the layering we've talked about informally:
 *
 * <ul>
 *   <li>{@code frc.lib.*} is the reusable utility layer. It must NOT depend on {@code
 *       frc.robot.*} — otherwise it can't be lifted into another repo next season.
 *   <li>{@code frc.lib.*} must not depend on the in-tree YAGSL copy ({@code swervelib.*}) so the
 *       utility layer stays drivetrain-agnostic.
 *   <li>Subsystems (under {@code frc.robot.subsystems.*}) must not reach up into commands.
 *       Commands drive subsystems, never the reverse.
 *   <li>{@code frc.lib.diagnostics.*} is consumer-only telemetry — any outbound edge into {@code
 *       frc.robot.*} would defeat its reuse story.
 * </ul>
 *
 * <p>Adding a new rule? Prefer an {@link ArchRule} with a clear {@code .because(...)} clause so
 * when CI breaks a student can read the failure and understand why the rule exists.
 */
class ArchitectureTest {

  /** Cached imported classes; shared across tests to keep the suite fast. */
  private static JavaClasses allClasses;

  @BeforeAll
  static void importClasses() {
    allClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .withImportOption(new ImportOption.DoNotIncludeJars())
            .importPackages("frc");
  }

  @Test
  void lib_mustNotDependOnRobotPackages() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("frc.lib..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("frc.robot..")
            .because(
                "frc.lib is the reusable utility layer — taking an inbound edge from frc.robot"
                    + " means we can't lift it into next season's repo without surgery.");
    rule.check(allClasses);
  }

  @Test
  void lib_mustNotDependOnSwerveLib() {
    // The YAGSL in-tree copy lives under swervelib.* and is hardware-coupled. frc.lib should be
    // drivetrain-agnostic so we can hand it to another robot that uses different modules.
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("frc.lib..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("swervelib..")
            .because(
                "frc.lib must stay drivetrain-agnostic — YAGSL is in-tree, so an accidental"
                    + " import could sneak through if we don't enforce this.");
    rule.check(allClasses);
  }

  @Test
  void subsystems_mustNotDependOnCommands() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("frc.robot.subsystems..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("frc.robot.commands..")
            .because(
                "commands drive subsystems, never the reverse. A subsystem that imports a"
                    + " command is a cyclic-dependency trap waiting to happen.");
    rule.check(allClasses);
  }

  @Test
  void diagnosticsMustStayConsumerOnly() {
    // Diagnostics classes in frc.lib.diagnostics are meant to be consumed; they shouldn't reach
    // across to frc.robot.* internals. Keeps the fault-monitoring infrastructure reusable and
    // prevents telemetry wiring from becoming an accidental bridge for business logic.
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("frc.lib.diagnostics..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "frc.lib..",
                "java..",
                "javax..",
                "edu.wpi.first..",
                "org.littletonrobotics..")
            .because(
                "frc.lib.diagnostics is consumer-only — any outbound edge to frc.robot.* means"
                    + " the telemetry layer has grown knowledge of the specific robot, defeating"
                    + " its reuse story.");
    rule.check(allClasses);
  }

  @Test
  void productionCodeMustNotCallThreadSleep() {
    // Thread.sleep inside any production class would block the robot loop — even a 10 ms sleep
    // causes a loop overrun. Async work belongs in Notifier / Commands.waitSeconds, never a
    // hand-rolled sleep.
    ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage("frc.robot..", "frc.lib..")
            .should()
            .callMethod(Thread.class, "sleep", long.class)
            .orShould()
            .callMethod(Thread.class, "sleep", long.class, int.class)
            .because(
                "Thread.sleep blocks the 20 ms robot loop. Use Commands.waitSeconds() or a"
                    + " Notifier if you need async behaviour.");
    rule.check(allClasses);
  }

  @Test
  void ioInterfacesMustLiveInSubsystemsPackage() {
    // XxxIO interfaces are the "real-vs-sim" seam for the 2590 pattern. They only make sense
    // alongside the subsystem they abstract — finding one in a random package is a smell.
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("IO")
            .and()
            .areInterfaces()
            .and()
            .resideInAPackage("frc.robot..")
            .should()
            .resideInAPackage("frc.robot.subsystems..")
            .because(
                "The 2590 IO-layer pattern pairs an XxxIO interface with an XxxIOReal + XxxIOSim"
                    + " implementation, all in frc.robot.subsystems.*.");
    rule.check(allClasses);
  }

  @Test
  void autoLoggedClassesMustNotBeHandEdited() {
    // Any class ending in "AutoLogged" is generated by the AdvantageKit @AutoLog annotation
    // processor. We enforce the naming convention here so a student doesn't accidentally hand-write
    // a class with that suffix (which would be clobbered on next build).
    //
    // The rule: every AutoLogged class must live next to a corresponding IO interface in the same
    // package (which is how the annotation processor places its output).
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("AutoLogged")
            .should()
            .resideInAPackage("frc.robot.subsystems..")
            .because(
                "*AutoLogged classes are generated by @AutoLog next to their IO interface. A"
                    + " hand-written class with that suffix will be silently overwritten on the"
                    + " next build — pick a different name.");
    rule.check(allClasses);
  }

  @Test
  void configPackageMustNotBeCreatedAdHoc() {
    // We centralise robot-wide constants in frc.robot.Constants (inner classes per subsystem).
    // A new top-level package like frc.robot.config or frc.robot.constants fragments the
    // tuning story — reject it.
    //
    // Rule framed as "the matched set is always empty" with allowEmptyShould=true so ArchUnit
    // doesn't treat the zero-match case as a failed expectation. The moment someone adds a
    // class to frc.robot.config / frc.robot.constants, the rule fires because .should(neverAny)
    // is unsatisfiable for any class.
    ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage("frc.robot.config..", "frc.robot.constants..")
            .should()
            .beAssignableTo(Object.class) // always true — so any matching class fails.
            .allowEmptyShould(true)
            .because(
                "Robot-wide constants live in frc.robot.Constants (inner classes per subsystem)."
                    + " Adding a parallel frc.robot.config / constants package fragments the"
                    + " tuning story. Add a new inner class to Constants instead.");
    rule.check(allClasses);
  }
}
