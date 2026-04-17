# Simulation validation script

> Items 43–53 from the session catalogue — verifies that the simulation wiring
> landed in PRs #7 / #11 / #12 actually behaves as advertised.
> Requires a laptop with JDK 17. Run `./gradlew simulateJava` + AdvantageScope;
> **run each step in order**, record pass/fail in the rightmost column.

This script was authored alongside PRs #1–#25 but couldn't be executed in the
originating session (no local JDK). Any contributor with a functional sim can
run through it in ~15–20 minutes. Results feed directly into the
[`PRACTICE_SESSION_PLAYBOOK.md`](PRACTICE_SESSION_PLAYBOOK.md) confidence gate.

---

## Setup

```bash
./gradlew simulateJava
# wait for the Sim GUI to open + AdvantageScope to attach
```

Driver-station: set the alliance to Blue, enable teleop once the sim
stabilises. On macOS, controller input is blocked — use
`tools/sim_keyboard_driver.py` or the Sim GUI joystick sliders.

AdvantageScope: `Connect` → `localhost`. Expect to see key trees for:
`Drive/`, `Vision/`, `Sim/`, `Commands/`, `JVM/`, `Robot/`, `SystemTest/`,
`SparkFaults/`.

---

## Checklist

| # | Item | Action | Expected signal | Pass? |
|---|---|---|---|---|
| 43 | Boot smoke | Launch sim | No stack traces; `Drive/Pose` populated at approx `(2, 4, 0)` | ☐ |
| 44 | Shot cadence | Drive to Hub-facing position, hold POV-up (3500 RPM) | `Sim/Shots/Fired` ticks at ~2/sec while Flywheel/AtSpeed=true; `Sim/Shots/Scored` increments when aiming at default Hub | ☐ |
| 45 | Intake pickup | `SimulatedArena.getInstance().addGamePiece(new RebuiltFuelOnField(new Pose2d(2, 4, 0)))` via test-mode hook; drive over it w/ intake wheel running | `inputs.wheelCurrentAmps` spikes > 15 A, SSM advances INTAKING → STAGING | ☐ |
| 46 | Reset inhibition | Press A (zero gyro) | `Vision/InhibitedAfterReset` = `true` for ~6 loops (120 ms), then `false` | ☐ |
| 47 | Velocity reject | Drive forward at 5 m/s past a known tag | `Vision/RejectedForSpeed` flips `true`; pose doesn't snap to vision | ☐ |
| 48 | Command lifecycle | Run any auto from the chooser | `Commands/LastStarted` + `Commands/Durations/<name>` populate; `Commands/ActiveCount` rises and falls | ☐ |
| 49 | JVM telemetry | Let sim run 2 minutes idle | `JVM/HeapUsedMB` updates; if `JVM/GCTotalCount` increments, record the timestamp and check against `RobotPeriodic` loop overrun logs | ☐ |
| 50 | Bypass toggle | Set `Constants.Swerve.kUseMapleSimKinematicBypass = false`; redeploy sim | Robot still drives in the correct direction (confirms maple-sim upstream REV fix), **OR** drives backward (confirms bug still present — flip flag back to `true`) | ☐ |
| 51 | SysId wiring | From pit/test mode, run the SysId quasistatic forward command (if wired) | `SysId/State` key shows forward/backward/dynamic phases; export log and run through SysId Analyzer | ☐ |
| 52 | AdvantageScope layout | Arrange panels for Drive, Vision, Shots, Commands, JVM, SparkFaults | Save as `tools/advantagescope-layout.json` and commit | ☐ |
| 53 | Elastic layout | Pin alert widgets for `SparkFaults` group + auto chooser + match timer | Export layout, commit to `deploy/elastic/`. Confirm no NT traffic warnings at `HIGH` verbosity (should only happen in sim) | ☐ |

---

## Record findings

For each item that fails or behaves unexpectedly, open an issue referencing
this doc + the specific row. Don't attempt to fix in-sim without a git
checkpoint — revert is cheap, debugging a broken sim through 3 unrelated
changes is not.

## Item 45 helper — quick-spawn game piece

Add this to `DriverPracticeMode` (or a throwaway `TestMode` command) if you
need to spawn fuel without rebuilding the whole arena manager:

```java
import swervelib.simulation.ironmaple.simulation.SimulatedArena;
import swervelib.simulation.ironmaple.simulation.seasonspecific.rebuilt2026.RebuiltFuelOnField;
import edu.wpi.first.math.geometry.Translation2d;

public static void spawnFuelAt(Translation2d position) {
  if (!RobotBase.isSimulation()) return;
  SimulatedArena.getInstance()
      .addGamePiece(new RebuiltFuelOnField(position));
}
```

Call from the pit test chooser or wire to an unused dashboard button.

## Item 50 helper — flipping the bypass live

The flag is compile-time (`static final`). Options to toggle without a
rebuild cycle:

1. Change the constant + redeploy (cleanest, ~30 s compile)
2. Convert to a `LoggedTunableNumber` so you can flip from AdvantageScope
   (treat sim flip as single-use — don't burn a tunable slot on something
   that changes once a year)
3. Gate on a system property: `-Psim-bypass=false` passed to
   `./gradlew simulateJava`

Option 1 is recommended.

---

## After you run it

- [ ] Attach the completed checklist (with your name + date) to this file
      or a PR updating it
- [ ] Any failures that aren't trivially fixable → open an issue, link
      to the item number here
- [ ] If everything green, mark `PRACTICE_SESSION_PLAYBOOK.md` Phase B step
      "sim-validated" ✓ before the session

Green across the board means the sim layer is trustworthy for further
autonomous-routine authoring without requiring practice-bot time. Red or
partial means specific items need practice-bot or manual verification.
