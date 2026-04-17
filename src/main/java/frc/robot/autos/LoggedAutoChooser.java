package frc.robot.autos;

import edu.wpi.first.networktables.NetworkTableInstance;
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
 * <p>Also adds a tiny randomised-selection helper for practice sessions — {@link #selectRandom()}
 * picks one of the registered options deterministically (seedable) so students can rehearse
 * without the "always runs my favourite" bias.
 *
 * <p>The wrapper owns the underlying {@link SendableChooser} so ownership + NT publishing stay
 * in one place. Call {@link #getSelected()} to read the chosen command at auto start; call
 * {@link #periodic()} once per robot loop to refresh the log key.
 *
 * <p>Log key: {@code Auto/SelectedName}. Matches the SmartDashboard entry name (if published) so
 * the dashboard and replay stay consistent.
 */
public final class LoggedAutoChooser {

  private final String nameSmartDashboardKey;
  private final SendableChooser<Command> chooser = new SendableChooser<>();
  /** Preserves insertion order so telemetry keys sort the way the author added them. */
  private final Map<String, Command> options = new LinkedHashMap<>();

  private String defaultName = "";

  /**
   * @param smartDashboardKey SmartDashboard key under which the chooser is published (e.g. "Auto
   *     Chooser"). Used for both the NT widget and the log-output suffix.
   */
  public LoggedAutoChooser(String smartDashboardKey) {
    this.nameSmartDashboardKey = smartDashboardKey;
  }

  /** Adds a named option. Idempotent — re-adding a name replaces the command. */
  public void addOption(String name, Command command) {
    chooser.addOption(name, command);
    options.put(name, command);
  }

  /** Adds the default option — {@code getSelected()} returns this when no user selection. */
  public void setDefaultOption(String name, Command command) {
    chooser.setDefaultOption(name, command);
    options.put(name, command);
    defaultName = name;
  }

  /** Push this chooser to SmartDashboard under the configured key. Call once after adding. */
  public void publish() {
    SmartDashboard.putData(nameSmartDashboardKey, chooser);
  }

  /** @return the currently-selected command, or the default if nothing selected. */
  public Command getSelected() {
    return chooser.getSelected();
  }

  /**
   * Force the chooser to report the option whose name matches {@code name}. The NT-selected
   * widget still reflects the user's last pick; this only affects what {@link #getSelected()}
   * returns on the next call. Useful for scripted practice sessions that iterate through
   * routines programmatically.
   *
   * @return {@code true} if the name was known; {@code false} otherwise (call is a no-op).
   */
  public boolean selectByName(String name) {
    if (!options.containsKey(name)) {
      return false;
    }
    // SendableChooser doesn't expose a programmatic setter, so we publish the name directly to
    // NT under the chooser's own "selected" key.
    NetworkTableInstance.getDefault()
        .getStringTopic("/SmartDashboard/" + nameSmartDashboardKey + "/selected")
        .publish()
        .set(name);
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
    // Reverse lookup — the chosen Command can show up in multiple options if authors aliased it,
    // so we log the option name that best matches the current NT selection.
    String selectedName = selectedNameOrEmpty();
    Logger.recordOutput("Auto/SelectedName", selectedName);
    Logger.recordOutput("Auto/OptionCount", options.size());
  }

  /**
   * @return the currently-selected option name, or empty string if none. Package-private for
   *     tests; the production path is {@link #periodic()}.
   */
  String selectedNameOrEmpty() {
    // SendableChooser's NT-backed selected string lives at <key>/selected. Fall back to the
    // declared default if the subscription hasn't yielded yet.
    String nt =
        NetworkTableInstance.getDefault()
            .getStringTopic("/SmartDashboard/" + nameSmartDashboardKey + "/selected")
            .subscribe("")
            .get();
    if (nt == null || nt.isEmpty()) {
      return defaultName;
    }
    return nt;
  }
}
