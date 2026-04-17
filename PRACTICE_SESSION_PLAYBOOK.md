# Practice-session playbook — 2950 robot

> **Goal**: turn a practice session into a data-collection run, not a debugging session.
> Every code bug should be caught before wheels touch carpet.

Three phases, ~90 minutes total if everything goes smoothly. If any Phase-A item
fails, **stop** and fix at the desk — do not burn practice field time on compile
bugs.

## Before you leave the shop

- [ ] `./gradlew build` clean on `main` (CI badge green)
- [ ] `./gradlew simulateJava` boots, drives around in AdvantageScope
- [ ] All Phase-0 smoke tests on a pit bench (items 70–76 in the session catalogue)
- [ ] Encoder offsets calibrated (items 60 in the session catalogue)
- [ ] SideClaw SPARK MAX reflashed to CAN ID 20 (was 18 — conflict with spindexer)
- [ ] Laptop + spare battery + REV Hardware Client installed
- [ ] `comp-practice` branch created off `main` for any pit edits

---

## Phase A — "Does it drive?" (15 min)

Robot on the floor. Low-speed only. Goal: verify **nothing regressed** from the last known-good state.

| Step | Action | Watch for | Pass criterion |
|---|---|---|---|
| A1 | Enable + stand still (no joystick) | Any alert in `SparkFaults` group, LED idle animation | Zero alerts; LED is idle-breathing |
| A2 | Drive 1 m forward at 25 % stick | Smooth motion, no loud oscillation, gyro stays within 2° | No jerkiness, gyro drift < 2° |
| A3 | Drive 1 m sideways each direction | Translation stays strafe-only, not banana-curve | Lateral deviation < 0.1 m |
| A4 | Spin 360° in place | Smooth rotation, no skipping | Rotation rate feels proportional to stick |
| A5 | Press **A** (zero gyro) | `Vision/InhibitedAfterReset = true` on dashboard for ~120 ms, then false | Flag flips visibly |
| A6 | Press **Y** (X-lock) | All 4 wheels snap to X pattern, robot holds position when pushed | Shoulder push doesn't move robot |
| A7 | **Panic button: Back + Start** (disabled) | Every command cancels, LEDs flash red briefly | No motor movement afterward |

If any A-step fails: **stop driving**, diagnose at the pit, don't move on.

---

## Phase B — Calibration data collection (45 min, the main event)

Bring the calibration notebook. Each row below = one data point. Record into `tools/calibration_log_YYYY-MM-DD.csv` in the repo.

### B.1 Shooter — new Lagrange calibration (most valuable output)

The 3 hardcoded calibration points in `Helper.rpmFromMeters` are from last season:
`1.125 m → 2500 RPM`, `1.714 m → 3000`, `2.500 m → 3500`.

Procedure:
- [ ] Tape-measure the Hub-to-robot distances `{1.0, 1.5, 2.0, 2.5, 3.0, 3.5}` m on the practice field
- [ ] At each distance, start at the nominal RPM (e.g., 2500 at 1.5 m from the old calibration), shoot 3 balls
- [ ] If they overshoot, drop RPM by 50 and re-shoot; if undershoot, raise by 50
- [ ] When 3/3 land centered, record that `(distance, RPM)` pair
- [ ] Repeat for each distance

Write the new 3-point curve into `Helper.rpmFromMeters` before the event. If the curve is bimodal (e.g. different at close vs. far), add a 4th point.

### B.2 Shooter — ball exit velocity (critical for moving-shot)

Currently assumed `Constants.Flywheel.kBallExitVelocityMps = 12.0 m/s`. If wrong, the 971 moving-shot compensation is biased.

Procedure:
- [ ] Static shot from 2.5 m at 3500 RPM
- [ ] Record time-of-flight — use a slow-mo video + ball's first frame leaving shooter + first frame hitting goal
- [ ] `velocity = distance / time_of_flight`
- [ ] Update `kBallExitVelocityMps` if > 5 % off

### B.3 Shooter — moving-shot accuracy

Verifies PR #6 (971 3-iter fixed-point compensation).

Procedure for each target distance `{1.5, 2.5} m`:
- [ ] **Stationary baseline**: 5 shots, record hit count
- [ ] **Moving away (1 m/s)**: drive backward at constant speed, 5 shots, record hits
- [ ] **Moving toward (1 m/s)**: drive forward at constant speed, 5 shots, record hits
- [ ] **Strafing left (1 m/s)**: 5 shots, record hits
- [ ] **Strafing right (1 m/s)**: 5 shots, record hits

Success: moving-shot hit rate ≥ 60 % of stationary hit rate at the same distance.

### B.4 Vision — stddev tuning

Verifies PR #3 (5-team consensus rejection).

Procedure:
- [ ] Drive to 1, 2, 3, 4 m from a tag — at each, hold position, record `Vision/StdDevXY` and `Vision/StdDevTheta` from AdvantageScope
- [ ] Drive at 2, 3, 5 m/s past a tag — verify `Vision/RejectedForSpeed = true` ONLY above 4 m/s
- [ ] With `Vision/StdDevXY` visible, slowly walk toward a tag until `avgTagDist < 4 m` — verify the measurement gets accepted (stddev ramps down)
- [ ] Zero gyro while 2 m from a tag — verify `Vision/InhibitedAfterReset = true` for exactly 6 loops, then false

Tune the base `k = 0.5` in `VisionSubsystem.kBaseXyStdDevMeters` if pose snaps too hard (increase) or drifts too much (decrease).

### B.5 SwerveSubsystem SysId (wheels OFF ground)

Verifies PR #2 (`SwerveDriveTest.setDriveSysIdRoutine`).

- [ ] Lift robot, wheels free
- [ ] On SmartDashboard: run the `SysId Drive Quasistatic Forward/Backward` commands (create command bindings if not yet — see TODO below)
- [ ] Run `SysId Drive Dynamic Forward/Backward`
- [ ] Export log, run through `SysId Analyzer`, get kS / kV / kA
- [ ] Update YAGSL feedforward via `SwerveDrive.replaceSwerveModuleFeedforward`

*TODO*: wire the SysId quasistatic/dynamic commands to SmartDashboard buttons. One-time, ~10 min in `RobotContainer.configureTestMode`.

### B.6 YAGSL feature flag sweep

Verify each YAGSL setting makes the robot feel better or worse. Each is one-line toggle + re-deploy:

- [ ] **Heading correction**: set `setHeadingCorrection(true, 0.05)` in `SwerveSubsystem` — does "drive-while-translating drift" improve?
- [ ] **Angular velocity compensation coefficient**: sweep `{0.05, 0.1, 0.15, 0.2}` via tunable — pick the one where rotating-while-translating is straightest
- [ ] **Telemetry verbosity**: confirm we're on `POSE` at match (was `HIGH` before PR #2) — no NT lag in dashboard
- [ ] **Auto-sync encoders**: drive for 10 minutes, verify modules don't visibly drift (the 2° auto-sync should catch it)

---

## Phase C — Auto rehearsal (30 min)

### C.1 Choreo autos

- [ ] `Leave Only` — runs clean? Drives straight? Stops at expected pose?
- [ ] `Score + Leave` — flywheel spins up + feeds + robot drives off? Timing reasonable?
- [ ] `2 Coral` and `3 Coral` — can they reliably complete?
- [ ] Repeat each auto ≥ 3 times — tally success / partial / failure rate

### C.2 Full Autonomous (strategy-driven)

- [ ] Place 2–3 fuel obstacles on the field
- [ ] Run Full Autonomous — does it route around them?
- [ ] Add a "moving opponent" (a person walking slowly) — does Bot Aborter kick in? Watch `Autonomous/AbortedTarget` in AdvantageScope
- [ ] Run both alliances — verify flip is correct

### C.3 compDeploy safety net

- [ ] On branch `comp-practice`, make a small tuning change (e.g. raise `kReadyThreshold` from 0.1 → 0.12)
- [ ] `./gradlew compDeploy` → verify: auto-commit fires, deploy succeeds
- [ ] Verify the commit shows in `git log` with the timestamped `comp-auto:` message
- [ ] Revert on `comp-practice`, push, confirm `compDeploy` is a no-op on clean tree

---

## Data to capture off the robot

After the session, pull these off the roboRIO USB stick for AdvantageScope replay:

```bash
# From dev laptop, with robot on USB / WiFi:
scp lvuser@10.29.50.2:/home/lvuser/logs/*.wpilog ./practice-$(date +%F)/
```

Check each log file answers:
- [ ] `Sim/Shots/Fired` vs `Sim/Shots/Scored` trend — did the shot sim correlate with reality?
- [ ] `SparkFaults/*` — any alerts fire? Which motor, what kind?
- [ ] `Commands/Durations/*` — any command run longer than expected?
- [ ] `Robot/BrownoutActive` — did we hit a brownout?
- [ ] `JVM/HeapUsedMB` — memory stable? GC spikes correlated with loop overruns (`LoopPeriod/RealLoopTime`)?
- [ ] `Vision/RejectedForSpeed`, `Vision/InhibitedAfterReset`, `Vision/RejectedDistM` — counters sensible?

Attach notable findings to the PR description when you commit calibration changes.

---

## Green-to-go checklist before competition

Only enter an event if all these are ✅:

- [ ] Every step in Phase A passes
- [ ] New Lagrange calibration points committed to `Helper.rpmFromMeters`
- [ ] `kBallExitVelocityMps` measured and committed (±5 %)
- [ ] SysId feedforward values committed
- [ ] Moving-shot hit rate ≥ 60 % of stationary at ≥ 2 m
- [ ] At least 2 of each Choreo auto routine completed successfully
- [ ] `compDeploy` tested end-to-end on `comp-test` branch
- [ ] `SparkFaults` group on Elastic Dashboard shows zero stuck alerts
- [ ] AdvantageScope layout saved + committed (`tools/advantagescope-layout.json`)
- [ ] Elastic layout exported + committed
- [ ] Pit checklist card printed

If even ONE item is red, either fix before the event OR add a compensating runbook item in `PIT_TRIAGE.md` (future doc).

---

## When things go wrong in the pit

```
Phase A failing?              → stop, diagnose at desk, skip the rest
Motor fault (SparkFaults)?    → check CAN cable, reseat, redeploy
Pose wandering in sim?        → check maple-sim bypass flag, flip if needed
Vision never fusing?          → check Limelight pipeline index, check tag visibility
Deploy failing?               → `./gradlew deploy --stacktrace`, likely a wpilib version mismatch
Git a mess?                   → `git stash`, `git checkout main`, `git pull`, start fresh on new branch
```
