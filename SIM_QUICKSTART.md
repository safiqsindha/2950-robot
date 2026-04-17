# Sim Quickstart — running the robot code on a Java-ready laptop

Gets you from "fresh machine" to "robot moving in AdvantageScope" in under 15 minutes.

---

## Prerequisites

A laptop with:

- **Java 17 Temurin** on the PATH. Verify with `java -version` — must say 17.x.
- **Git** on the PATH.
- **AdvantageScope** installed — see [`docs/advantagescope-setup.md`](docs/advantagescope-setup.md).
- Optional: **Xvfb** if you want to run the sim headless (Linux only). Not needed on macOS/Windows.

If you don't have Java 17: download from [Adoptium](https://adoptium.net/temurin/releases/?version=17) → install → verify with `java -version`.

---

## Clone + build

```bash
git clone https://github.com/safiqsindha/2950-robot.git
cd 2950-robot
./gradlew build
```

First build takes ~2 minutes (downloads WPILib + vendor JNI). Subsequent builds are ~15 seconds.

Expected: `BUILD SUCCESSFUL`. If you see `wpiHaljni not found`, see [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md).

---

## Launch the sim

```bash
./gradlew simulateJavaRelease
```

You should see:

- **Sim GUI window** appear (on macOS, controllers won't attach — that's expected, use keyboard or AdvantageScope inputs).
- Console output ending with `[AdvantageKit] Logger started`.
- No stack traces.

Leave it running.

---

## Connect AdvantageScope

1. AdvantageScope → **File** → **Connect to Live Source** → `localhost:5810` (the AdvantageKit NT4 publisher default).
2. The status bar should turn green.
3. Load the team layout: **File** → **Import Layout** → pick `advantagescope-layout.json` from the repo root.

You should now see the six tabs (Flywheel, Field, Diagnostics Quartet, Intake+Conveyor, SSM, Vision) with live data.

---

## Drive the robot

**macOS — controllers don't work in the Sim GUI.** Use the AdvantageScope Input widget instead, or publish joystick-axes manually. There's a `tools/sim_keyboard_driver.py` for keyboard → NT4 mapping if you want WASD control.

**Windows / Linux:** drag an Xbox controller icon onto `Joystick[0]` in the Sim GUI. Enable Teleop in the DS. Left stick = drive, right stick = rotate.

---

## Run the smoke test

If you want CI-grade confidence without a robot:

```bash
python3 tools/sim_smoke_test.py --duration 20
```

This boots the sim headlessly, tails stdout/stderr for 20 seconds, and fails if anything looks wrong. If it passes, the codebase is in a shippable state.

---

## Run individual unit tests

```bash
./gradlew test
./gradlew test --tests HelperTest
./gradlew jacocoTestCoverageVerification
```

The full test suite should finish in under a minute.

---

## Deploy to real robot (once you graduate from sim)

Prerequisite: you're on the 2950 radio network or USB-tethered.

```bash
./gradlew deploy           # checks + deploys
./gradlew compDeploy       # auto-commits pit edits if on a comp-* branch
```

See [`PRACTICE_SESSION_PLAYBOOK.md`](PRACTICE_SESSION_PLAYBOOK.md) for the full pre-deploy checklist and [`PIT_CHECKLIST.md`](PIT_CHECKLIST.md) for the event-day runbook.

---

## Common quickstart problems

| Symptom | Fix |
|---|---|
| `./gradlew` not found | Run from the repo root, not a subdirectory. |
| `error: release version 17 not supported` | Wrong JDK. Check `java -version`; install Temurin 17. |
| Sim starts but AdvantageScope has no signals | AdvantageKit's NT4Publisher hasn't started. Look for `[AdvantageKit] Logger started` in the sim console; if missing, check `Robot.robotInit()`. |
| Robot pose stuck at (0, 0) | Expected right at start — we seed to (2, 4). If it's stuck after enable, check `Drive/GyroYaw` on AdvantageScope. |
| Flywheel tab empty | Normal until you command a flywheel action (POV presets or `X` for AutoScore). |

More in [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md).
