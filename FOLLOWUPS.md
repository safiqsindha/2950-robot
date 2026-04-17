# Follow-ups

Intent queue for work surfaced during a session but not done in that session. Add dated entries on top. Oldest are at the bottom — delete a bullet only after the change is shipped (don't mark ✓ in place).

---

## 2026-04-18 — next session

- [ ] **2025→2026 deep migration (NOT just a rename).** Audit after research against WPILib's authoritative JSON narrowed the scope — not everything flagged in the first pass was actually wrong. Remaining work is real but smaller.

  **Authoritative source** — WPILib bundles the field JSON:
  - `apriltag/src/main/native/resources/edu/wpi/first/apriltag/2026-rebuilt-welded.json` — WELDED 16.541 m × 8.069 m, 32 tags (IDs 1–32)
  - `apriltag/src/main/native/resources/edu/wpi/first/apriltag/2026-rebuilt-andymark.json` — AndyMark 16.518 m × 8.043 m, 32 tags
  - 2025 Reefscape for contrast: 17.548 m × 8.052 m, 22 tags (1–22). Tag 26 does not exist in 2025.

  **What's actually wrong (corrected from prior pass):**

  - ~~`Helper.llSetup()` filters tags {2, 5, 10, 18, 21, 26} — 2025 Reefscape IDs~~. **False.** All six ARE in the 2026 HUB tag set {2, 3, 4, 5, 8, 9, 10, 11, 18, 19, 20, 21, 24, 25, 26, 27}. Tag 26 doesn't even exist in 2025. The filter and its "2026 REBUILT hub targets" comment are both correct. Remaining question: why these 6 of the 16 HUB tags — is this one HUB face per alliance, or a scoring-side choice? Worth documenting in a code comment, not changing.
  - ~~`Constants.kFieldLengthMeters = 16.541` is a 2025 value~~. **False.** 2025 was 17.548. The 16.541 value is 2026 WELDED exactly (also coincidentally the 2024 Crescendo length — the repo inherited Crescendo constants in the initial commit).
  - `Constants.kFieldWidthMeters = 8.211` — **Actually wrong.** This is the 2024 Crescendo width, doesn't match any post-2024 season. Fixed to 8.069 in this PR.
  - `src/main/deploy/navgrid.json` + `src/main/deploy/pathplanner/navgrid.json` — declared dims were `field_length_m: 16.46` / `field_width_m: 8.23` (matched no season). Fixed to 16.541 / 8.069 in this PR. `NavigationGrid.java` only reads `columns`/`rows`/`cell_size_m`/`grid`, so the fix is documentary only — does not affect pathfinding. The real problem is separate: the grid body itself traces 2025 Reef geometry — `NavigationGridTest.testHubLocation_isObstacle` asserts (3.39, 4.11) is blocked, which was the 2025 Blue Reef center. The 2026 Blue HUB is at approximately (4.6, 4.0) per the WPILib tag data. **Grid body still needs a full rebuild** against the 2026 CAD.
  - `Helper.rpmFromMeters` Lagrange points (1.125 → 2500, 1.714 → 3000, 2.500 → 3500) — provenance unknown from git history. Could be 2025 Coral calibration inherited, could be 2026 FUEL practice-session. Hardware verification required.
  - Choreo `.traj` files — current `reefToStation.traj` / `stationToReef.traj` use waypoints (4.50, 4.00) ↔ (0.90, 7.20). The (4.50, 4.00) point coincidentally matches the 2026 Blue HUB centerline, but (0.90, 7.20) doesn't land on any identifiable 2026 feature. Paths need Choreo desktop re-authoring anyway for 2026 HUB approach geometry.
  - `Constants.Autonomous.kSafeModeStaticShotRpm = 2800` / `kAutoStaticShotRpm = 3000` — RPMs are not inherently game-specific; only wrong if the RPM curve (Lagrange points) is wrong.
  - `tools/navgrid_generator.py` referenced by ARCHITECTURE.md doesn't exist. Either write it or fix the doc.
  - `STUDENT_TESTING_GUIDE.md` tag reference is **already correct** (says "2026 REBUILT hub scoring targets", 2026 AprilTag family). Prior pass claim that it listed 2025 IDs was wrong.

  **Migration work order (revised):**

  1. ~~Find the truth~~ — **DONE.** Authoritative JSON pulled from `wpilibsuite/allwpilib`. 2026 WELDED is the team's target (standard official field).

  2. **Field dimension constants + navgrid declared dims** — **DONE** this PR: `Constants.kFieldWidthMeters` 8.211 → 8.069; `tools/choreo_validator.py` FIELD_WIDTH_METERS 8.211 → 8.069; `src/main/deploy/navgrid.json` + `src/main/deploy/pathplanner/navgrid.json` declared `field_length_m`/`field_width_m` → 16.541 / 8.069. Length was already 2026-correct in Constants.

  3. **Replace navgrid obstacle body** — requires 2026 REBUILT field CAD (Onshape, or STEP files from FIRST's Playing Field Assets page). The grid body at `src/main/deploy/navgrid.json` + `src/main/deploy/pathplanner/navgrid.json` currently encodes 2025 Reef hexagons at 2025 coordinates. Write `tools/navgrid_generator.py` from the 2026 CAD or manually rasterize the HUB + TOWER + OUTPOST + TRENCH + STARTING ZONE footprints into the 164×82 grid. Confirm with `NavigationGridTest` — update the `testHubLocation_isObstacle` coordinates to the 2026 Blue HUB (~(4.6, 4.0)).

  4. **Re-calibrate flywheel** — hardware + practice session. Measure 3+ (distance, RPM) pairs with real 2026 FUEL vs 2026 HUB. `tools/rpm_curve_fit.py` regenerates Lagrange constants. Parallel: inspect git history of `Helper.rpmFromMeters` to confirm the existing points weren't already 2026-tuned (unlikely but possible).

  5. **Re-author trajectories** — Choreo desktop + 2026 field. New HUB approach + Fuel Intake approach paths. Delete `leaveStart.traj` / `reefToStation.traj` / `stationToReef.traj`; create new paths with game-accurate names.

  6. **Scoring pose constants** — audit `AutonomousStrategy`: `CLIMB_POSE = new Pose2d(8.23, 4.11, ...)` and `DEFAULT_COLLECT_POSE` at the same point. (8.23, 4.11) is approximately field center — likely a placeholder, not a 2026 TOWER location. Also audit any hard-coded Translation/Pose in `Constants.*`.

  7. **Auto routine redesign** — 2/3-coral pattern (preload + 2 station cycles) depends on whether 2026 HUB scoring + FUEL pickup supports that cycle count and game-piece limit. Depends on REBUILT scoring rules + point values.

  8. **Vision pose gates** — `kMaxTagDistM = 4.0` likely still OK (HUB tag height 1.124 m is similar to Reef). `kMaxLinearSpeedForVisionMps`, `kMaxCorrectionAutoMeters` are velocity/drift gates, not distance-sensitive. Low priority.

  9. **Terminology pass** — `twoCoralRoutine` → `twoFuelRoutine`, `threeCoralRoutine` → `threeFuelRoutine`, `reefToStation.traj` → `hubToIntake.traj` (etc.), `advantagescope-layout.json` game string, GLOSSARY Reef → HUB, test example poses, README/docs. Cosmetic but necessary to keep code self-documenting. Run:
     ```
     grep -rn "coral\|reef\|station\b" src/ *.md docs/ advantagescope-layout.json \
       --include=*.java --include=*.md --include=*.json \
       | grep -vE "swervelib|RebuiltFuel"
     ```

  **Cannot be done in a single PR.** Items 3, 4, 5 are hardware-/CAD-gated and each large. Item 9 is two sub-PRs (source renames vs trajectory-file renames). Expect ~6 more PRs after this one.

  **Risk of not doing it:** event-day behaviour would still be incorrect in: navgrid pathfinding (avoids wrong obstacles), flywheel RPMs (if Lagrange points really are 2025), scoring poses (CLIMB_POSE likely placeholder), trajectory waypoints. Would NOT be incorrect in: field dimension constants (fixed this PR), AprilTag filter (was already correct).

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
