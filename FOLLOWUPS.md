# Follow-ups

Intent queue for work surfaced during a session but not done in that session. Add dated entries on top. Oldest are at the bottom — delete a bullet only after the change is shipped (don't mark ✓ in place).

---

## 2026-04-18 — next session

- [ ] **2025→2026 deep migration (NOT just a rename).** This is the largest single outstanding item on the repo. The codebase claims to be for 2026 REBUILT (top-level docs, README, a few sim classes using `RebuiltFuelOnFly`), but the substance — field map, AprilTag IDs, flywheel calibration, trajectory waypoints, scoring poses — is all 2025 Reefscape. The robot as currently coded would not score on a 2026 REBUILT field.

  **Evidence of 2025-ness in supposedly-2026 code:**
  - `Helper.llSetup()` filters tags `{2, 5, 10, 18, 21, 26}` — these are 2025 Reefscape tag IDs. Comment says "2026 REBUILT hub targets"; comment is wrong.
  - `src/main/deploy/navgrid.json` — 16.46m × 8.23m field (2025 Reefscape size) with obstacle layout tracing the 2025 Reef hexagons + Coral Stations. `Constants.kFieldLengthMeters = 16.541` disagrees with navgrid by 8 cm — even the 2025 values aren't consistent with each other.
  - `Helper.rpmFromMeters` Lagrange points (1.125 → 2500, 1.714 → 3000, 2.500 → 3500) were measured against 2025 Coral launching into 2025 Reef heights. FUEL has different mass/shape — curve is wrong.
  - Choreo `.traj` file contents are 2025 Reef + Coral Station waypoints.
  - `Constants.Autonomous.kSafeModeStaticShotRpm = 2800` / `kAutoStaticShotRpm = 3000` — Coral numbers.

  **Migration work order:**

  1. **Find the truth.** Pull the official 2026 REBUILT field JSON from FRC / Choreo's release. Note: field length, width, AprilTag placement, HUB + TOWER + Fuel Intake coordinates, allowed starting zones.

  2. **Replace field data** — PR scope: `navgrid.json`, `Constants.kFieldLengthMeters` / `kFieldWidthMeters`, regenerate via `tools/navgrid_generator.py` (or manual if no tool exists yet).

  3. **Replace AprilTag filter** — PR scope: `Helper.llSetup()` tag ID list. Update `STUDENT_TESTING_GUIDE.md` tag reference (currently lists 2025 IDs).

  4. **Re-calibrate flywheel** — requires hardware + practice session. Measure 3+ (distance, RPM) pairs with real 2026 FUEL against the 2026 HUB. Use `tools/rpm_curve_fit.py` to regenerate Lagrange constants.

  5. **Re-author trajectories** — requires Choreo desktop + 2026 field layout. New paths for HUB approach + Fuel Intake approach. Delete the old `.traj` files; create new ones with game-appropriate names.

  6. **Scoring pose constants** — audit every hard-coded pose in `Constants.*` + `AutonomousStrategy` for 2025-specific coordinates. Replace with 2026 equivalents.

  7. **Auto routine redesign** — scoring strategy likely needs a rewrite. 2/3-coral pattern (preload + 2 station cycles) may not be the right 2026 strategy. Depends on REBUILT scoring rules + point values.

  8. **Vision pose gates** — re-tune `Constants.Vision.kMaxTagDistM`, `kMaxCorrectionAutoMeters`, `kMaxLinearSpeedForVisionMps` if 2026 HUB scoring happens at different distances from 2025 Reef scoring.

  9. **Terminology pass (cosmetic, do last)** — rename `twoCoralRoutine` → `twoFuelRoutine`; `advantagescope-layout.json` game string; GLOSSARY Reef → HUB; test example poses; docs. Run the search:
     ```
     grep -rn "coral\|reef\|station\b" src/ *.md docs/ advantagescope-layout.json \
       --include=*.java --include=*.md --include=*.json \
       | grep -vE "swervelib|RebuiltFuel"
     ```
     (136 lines across 37 files. "station" has non-game mentions — driver station, test station — triage with grep `-v`.)

  **Cannot be done in a single PR.** Items 1-8 touch behaviour; item 9 is cosmetic. Even item 9 alone is two PRs (source renames vs trajectory-file renames). Expect ~8-12 PRs total over several sessions, ordered so each lands as a working unit (e.g., ship the new field data + tag IDs together or vision breaks in isolation).

  **Risk of not doing it:** every claim that this codebase is "for 2026 REBUILT" is marketing; the event-day behaviour would be incorrect in every game-specific dimension. The robot would still drive (swerve is game-agnostic), but auto wouldn't score, vision wouldn't lock, flywheel RPMs would be wrong.

- [ ] **Delete `.cursorrules`.** Stale from April 8, predates the offseason refactor. Lies in three specific ways — claims CTRE Phoenix 6 / Kraken drivetrain (we're REV-only), forbids software-side PIDs (contradicted by `LinearProfile`, `AsymmetricRateLimiter`, `TrajectoryFollower`, `BatteryAwareCurrentLimit`), forbids IO interfaces broadly (we deliberately adopted the 2590 IO-layer pattern for every mechanism except Swerve). Any Cursor agent reading both the file and `AGENTS.md` would hit contradictions. Successor docs already maintained: `AGENTS.md`, `DEVELOPER_TESTING_GUIDE.md`, `MENTOR_GUIDE.md`, `CODE_TOUR.md`, 10 ADRs.

- [ ] **Line-by-line audit of every source file.** Walk every `*.java` in `src/main/java/` and `src/test/java/`, plus every Python file in `tools/`, plus every markdown doc, and for each line ask: is this needed? Specifically look for:
  - Dead imports that Spotless missed (rare but possible after major refactors)
  - Stale `// TODO` / `// FIXME` referring to already-fixed issues
  - Commented-out code
  - Docstrings that describe an old behaviour (the "intake marker is bound" docstring we just fixed was one of these — caught by an external judge, not internal review)
  - Fields declared but never read
  - Private methods never called
  - Log keys emitted but never consumed by any dashboard / doc (dead telemetry, the `VisionLatencyTracker` class of bug)
  - Constants that look like copy-paste drift from a previous subsystem
  - ArchUnit / SpotBugs suppressions whose original rationale no longer applies
  - Test assertions that look defensive but never actually fail
  - Config entries in `build.gradle` / `vendordeps/` / `config/` that reference removed features

  **Method:** branch per subsystem / package. Keep each PR < 300 LOC so reviewers can actually check every deletion. Don't batch unrelated deletions — one reason per PR. Use `git blame` to verify the original intent before removing anything non-obvious.

  **Stop condition:** every line answers "yes, this is needed" with a specific reason.

---

## How to use this file

1. When you notice something that should be done later, add a bullet here with today's date.
2. Start each session by reading the top section.
3. When you ship a bullet's fix, remove the bullet from here and let the changelog bot record the PR.
4. Don't let this file grow past 50 items; if it does, stop adding and start doing.
