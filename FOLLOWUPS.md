# Follow-ups

Intent queue for work surfaced during a session but not done in that session. Add dated entries on top. Oldest are at the bottom — delete a bullet only after the change is shipped (don't mark ✓ in place).

---

## 2026-04-18 — next session

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
