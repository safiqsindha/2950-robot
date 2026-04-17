# AdvantageScope Setup

One-time setup so new team members load the same layout we use for match debriefs + practice review.

---

## Install AdvantageScope

1. Download the latest installer: https://github.com/Mechanical-Advantage/AdvantageScope/releases
2. On macOS you'll need to right-click → Open the first time (unsigned build).

## Load the team layout

The layout file lives at `advantagescope-layout.json` in the repo root. It defines six tabs:

| Tab | What it shows |
|---|---|
| **Flywheel** | Goal / setpoint / measured RPM plus the AtSpeed boolean |
| **Field** | 2026 Reefscape field with robot pose, trajectory target ghost, vision pose ghost |
| **Diagnostics Quartet** | JVM heap, CAN utilisation, loop tick ms, PDH total current — four channels in one graph |
| **Intake + Conveyor** | Arm goal / setpoint / measured + wheel current + belt current |
| **Superstructure state machine** | State label, transitions, intake/score flags, match phase, selected auto name |
| **Vision** | Tag count, std dev XY, correction magnitude, latency p95, HasTarget |

Load it via AdvantageScope → **File** → **Import Layout** → pick `advantagescope-layout.json`.

The layout is version-controlled so edits flow through PRs. If you find yourself customising a tab, consider opening a PR with the improved JSON.

---

## Live connect to the robot

1. Connect your laptop to the 2950 radio (or USB to the roboRIO).
2. In AdvantageScope: **File** → **Connect to Live Source** → `roboRIO-2950-FRC.local` (or the USB-tethered IP).
3. The status bar turns green when connected.

## Load a match replay

1. Pull the `.wpilog` off the roboRIO (see [`PRACTICE_SESSION_PLAYBOOK.md`](../PRACTICE_SESSION_PLAYBOOK.md) — section on log retrieval).
2. AdvantageScope → **File** → **Open Log** → pick the file.
3. The loaded signals match what you see live — same tabs, same keys.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Layout missing fields" after load | Open the tab, check the field keys against [`TELEMETRY_REFERENCE.md`](../TELEMETRY_REFERENCE.md). The log might be from before a key was added. |
| Robot pose appears off-field | Mis-alliance. In the Field tab's controller, swap the robot-pose alliance from `blue` to `red`. |
| Flywheel tab graph is flat | Check the key path — newer AdvantageKit versions prefix with `AdvantageKit/RealOutputs/`. The layout JSON already uses that convention. |
| No signals at all | Logger.start() hasn't run — that happens in `Robot.robotInit`. If connecting live, confirm DS + robot code are green. |

---

## What this doesn't cover

- **Limelight dashboard**. Use `http://limelight.local:5801` for camera feed + raw target data.
- **SysId analysis**. That's a separate WPILib GUI app — feed it the `.wpilog` after running a characterization routine from the Elastic pit dashboard.
- **Custom widgets** (e.g. a visual SSM state diagram). AdvantageScope's tab types cover most needs; for very custom views, look at Elastic's widget library or a small custom Shuffleboard widget.
