# Pit-Day Checklist — Event Runbook

Printable, in-order, time-boxed. Laminate this if you want to keep it pristine through a regional.

Companion docs:

- [`PRACTICE_SESSION_PLAYBOOK.md`](PRACTICE_SESSION_PLAYBOOK.md) — longer-form pre-event session script
- [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — when a step fails
- [`CAN_ID_REFERENCE.md`](CAN_ID_REFERENCE.md) — wiring source of truth
- [`TELEMETRY_REFERENCE.md`](TELEMETRY_REFERENCE.md) — every log key the robot publishes

---

## First arrival (≈ 15 min)

- [ ] Robot on cart, battery charged (≥ 12.6 V at rest)
- [ ] Tether laptop + radio to robot
- [ ] `python3 tools/deploy_health_check.py` → all green (or explicit warnings noted)
- [ ] `./gradlew deploy` → success
- [ ] Driver station connects; robot enters Disabled

---

## Before first match (≈ 20 min)

- [ ] Load `src/main/deploy/elastic-layout.json` into Elastic (or Shuffleboard layout file if Shuffleboard is in use)
- [ ] Verify Elastic **Match** tab shows:
    - Auto chooser populated with all routines (Leave, Leave Only (Raw), Score + Leave, 2 Coral, 3 Coral, Full Autonomous, Safe Mode)
    - Battery voltage live
    - Field widget with robot pose
    - Gyro reads 0° with robot pointed downfield
    - Flywheel graph responds when trigger pulled (teleop check in bench test)
- [ ] Elastic **Pit** tab — click **Run System Test**, watch all four check lights go green within 2 s
- [ ] CAN bus utilisation stays < 60 % during System Test
- [ ] Auto chooser set to the default routine for this match's strategy

---

## Between matches (≈ 5 min each)

- [ ] Fresh battery (discharge ≤ 20 % since last rest)
- [ ] `python3 tools/deploy_health_check.py --warn` (warnings OK; fails are blockers)
- [ ] Check CAN bus utilization history for the last match — any spikes? (`CAN/UtilizationPct` graph)
- [ ] Check PDH current history — any channel pegged at limit? (`PDH/ChannelCurrentsA` per-channel)
- [ ] Check JVM heap — any upward drift? (`JVM/HeapUsedMB`)
- [ ] Confirm autonomous chooser still set to the intended routine
- [ ] Visually: bumpers tight, bolts checked, wheels spin free

If any of the above is off, pull the robot for a longer pit session.

---

## SysId session (once per event — or if tuning drifted)

- [ ] Robot on blocks (for steer SysId) — wheels must spin free
- [ ] Click **SysId/Steer Full Sweep** in Elastic → wait for the 16 s sweep to complete
- [ ] Robot on carpet with ≥ 2 m clear lane (for drive SysId)
- [ ] Click **SysId/Drive Full Sweep (Quasi F/R + Dynamic F/R)** → wait for the 16 s sweep
- [ ] Pull the DataLog (`/U/logs/*.wpilog`) to laptop
- [ ] Run WPILib SysId GUI against the log → copy `kS` / `kV` / `kA`
- [ ] Update Constants / YAGSL config with new gains; redeploy
- [ ] Re-run Run System Test to verify nothing drifted

---

## When something goes wrong

1. Note the symptom *in words* before touching anything (what light, what motion, what screen).
2. Check [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — organized by symptom.
3. If not found there: grab the last 5 s of DataLog + a screenshot of Elastic's Match tab, ask a mentor.
4. **Do not** change Constants or PID gains in the pit without a corresponding log-back-up of the old value.

---

## Between qualification rounds and elimination

- [ ] Swap in fresh primary battery; charge backup
- [ ] Verify alliance colour matches match schedule
- [ ] If alliance changes from blue to red: no code change needed (`AllianceFlip` + Choreo `useAllianceFlipping` handle it)
- [ ] Confirm radio is on the correct event SSID (field staff radio assignments)
