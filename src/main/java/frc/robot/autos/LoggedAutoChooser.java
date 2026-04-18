package frc.robot.autos;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.networktables.StringSubscriber;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import java.util.LinkedHashMap;
import java.util.Map;
import org.littletonrobotics.junction.Logger;

/**
 * Wrapper around {@link SendableChooser} that logs the currently-selected auto routine to
 * AdvantageKit on every {@link #periodic()}. Without this, the only evidence of which routine
 * actually ran is indirect (state transitions, trajectory samples). With it, post-match replay
 * shows the student's explicit choice.
 *
 * <p>Also adds a tiny randomised-selection helper for practice sessions — {@link
 * #selectRandom(long)} picks one of the registered options deterministically (seedable) so students
 * can rehearse without the "always runs my favourite" bias.
 *
 * <p><b>Testability:</b> the underlying {@link SendableChooser} is <i>lazily</i> created on {@link
 * #publish()}. Constructing a {@code LoggedAutoChooser} in a JUnit test doesn't touch HAL; only
 * calling {@link #publish()} does. Tests that exercise registration + selection logic stay
 * HAL-free.
 *
 * <p>Log key: {@code Auto/SelectedName}. Matches the SmartDashboard entry name (if published) so
 * the dashboard and replay stay consistent.
 */
public final class LoggedAutoChooser {

  private final String nameSmartDashboardKey;

  /** Preserves insertion order so telemetry keys sort the way the author added them. */
  private final Map<String, Command> options = new LinkedHashMap<>();

  /**
   * Lazily instantiated in {@link #publish()} so HAL-free unit tests can construct a chooser,
   * exercise {@link #addOption(String, Command)} / {@link #setDefaultOption(String, Command)} /
   * {@link #selectByName(String)}, and never touch the underlying SendableChooser — whose
   * constructor transitively loads WPILib's HAL.
   */
  private SendableChooser<Command> chooser;

  /**
   * Set by {@link #publish()} so subsequent addOption calls still propagate to the live chooser.
   */
  private boolean published = false;

  private String defaultName = "";
  private String programmaticSelection = "";

  /**
   * Cached NT publisher/subscriber for the chooser's {@code /selected} entry. Allocated once in
   * {@link #publish()} and reused — avoids creating new NT handles on every {@link
   * #selectByName(String)} call or every {@link #selectedNameOrEmpty()} call (which runs at 50 Hz).
   */
  private StringPublisher selectedPublisher;

  private StringSubscriber selectedSubscriber;

  /**
   * @param smartDashboardKey SmartDashboard key under which the chooser is published (e.g. "Auto
   *     Chooser"). Used for both the NT widget and the log-output suffix.
   */
  public LoggedAutoChooser(String smartDashboardKey) {
    this.nameSmartDashboardKey = smartDashboardKey;
  }

  /** Adds a named option. Idempotent — re-adding a name replaces the command. */
  public void addOption(String name, Command command) {
    options.put(name, command);
    if (published) {
      chooser.addOption(name, command);
    }
  }

  /** Adds the default option — {@code getSelected()} returns this when no user selection. */
  public void setDefaultOption(String name, Command command) {
    options.put(name, command);
    defaultName = name;
    if (published) {
      chooser.setDefaultOption(name, command);
    }
  }

  /**
   * Instantiate the underlying SendableChooser and push it to SmartDashboard under the configured
   * key. Call once from {@code RobotContainer} after all options are added; subsequent {@code
   * addOption} / {@code setDefaultOption} calls still propagate.
   *
   * <p>This is the only method that touches HAL. Tests must not call it.
   */
  public void publish() {
    // Idempotent: second + later calls are no-ops. A double-call would otherwise orphan the
    // first SendableChooser (registered in SmartDashboard's internal map but unreachable via
    // our field), which AdvantageScope / the live NT widget would handle but is still a leak.
    if (published) {
      return;
    }
    chooser = new SendableChooser<>();
    for (Map.Entry<String, Command> entry : options.entrySet()) {
      if (entry.getKey().equals(defaultName)) {
        chooser.setDefaultOption(entry.getKey(), entry.getValue());
      } else {
        chooser.addOption(entry.getKey(), entry.getValue());
      }
    }
    published = true;
    SmartDashboard.putData(nameSmartDashboardKey, chooser);

    // Cache one publisher + subscriber for the chooser's /selected NT entry. Both are reused on
    // every selectByName() call and every selectedNameOrEmpty() call (50 Hz periodic path).
    String ntPath = "/SmartDashboard/" + nameSmartDashboardKey + "/selected";
    selectedPublisher = NetworkTableInstance.getDefault().getStringTopic(ntPath).publish();
    selectedSubscriber = NetworkTableInstance.getDefault().getStringTopic(ntPath).subscribe("");
  }

  /**
   * @return the currently-selected command, or the default if nothing selected.
   */
  public Command getSelected() {
    if (published) {
      return chooser.getSelected();
    }
    // Pre-publish fallback (test-mode): honour any programmatic selection, else default.
    if (!programmaticSelection.isEmpty() && options.containsKey(programmaticSelection)) {
      return options.get(programmaticSelection);
    }
    return options.get(defaultName);
  }

  /**
   * Force the chooser to report the option whose name matches {@code name}. The NT-selected widget
   * still reflects the user's last pick; this only affects what {@link #getSelected()} returns on
   * the next call. Useful for scripted practice sessions that iterate through routines
   * programmatically.
   *
   * @return {@code true} if the name was known; {@code false} otherwise (call is a no-op).
   */
  public boolean selectByName(String name) {
    if (!options.containsKey(name)) {
      return false;
    }
    programmaticSelection = name;
    if (published) {
      // SendableChooser doesn't expose a programmatic setter, so write directly to NT under the
      // chooser's "selected" key. Uses the pre-allocated publisher — no per-call NT allocation.
      selectedPublisher.set(name);
    }
    return true;
  }

  /**
   * Deterministically pick a random option using the given seed. Returns the selected name so
   * callers can log it. Practice-only; in a match you want the student's explicit choice.
   */
  public String selectRandom(long seed) {
    String[] names = options.keySet().toArray(new String[0]);
    if (names.length == 0) {
      return defaultName;
    }
    // Integer-hash-based picker — deterministic for a given seed + option list.
    int idx = Math.floorMod(Long.hashCode(seed), names.length);
    String chosen = names[idx];
    selectByName(chosen);
    return chosen;
  }

  /** Push the current selection to the AdvantageKit log. Call once per robot cycle. */
  public void periodic() {
    String selectedName = selectedNameOrEmpty();
    Logger.recordOutput("Auto/SelectedName", selectedName);
    Logger.recordOutput("Auto/OptionCount", options.size());
  }

  /**
   * @return the currently-selected option name, or empty string if none. Package-private for tests;
   *     the production path is {@link #periodic()}.
   */
  String selectedNameOrEmpty() {
    if (!published) {
      return programmaticSelection.isEmpty() ? defaultName : programmaticSelection;
    }
    // SendableChooser's NT-backed selected string lives at <key>/selected. Fall back to the
    // declared default if the subscription hasn't yielded yet. Uses the pre-allocated subscriber
    // — no per-call NT allocation (this method runs every 50 Hz periodic tick).
    String nt = selectedSubscriber.get();
    if (nt == null || nt.isEmpty()) {
      return defaultName;
    }
    return nt;
  }
}
