# Mentor Guide

For mentors who are responsible for reviewing PRs, guiding students, and keeping the robot safe. If you're a student, start with [`CODE_TOUR.md`](CODE_TOUR.md) and [`FAQ.md`](FAQ.md).

---

## The mentor review checklist

For every PR — before approving / merging, verify:

### Safety

- [ ] Does this change alter motor output ranges, current limits, brownout scale, or emergency-stop behavior? If yes, extra eyes + a bench test required.
- [ ] Are new CAN IDs added without a matching `CAN_ID_REFERENCE.md` update? Reject.
- [ ] Are encoder offsets or gear ratios being changed? Verify against hardware before merging.

### Correctness

- [ ] CI green (build + SpotBugs + JaCoCo + ArchUnit + headless sim)
- [ ] Commit message explains *why*, not just *what*
- [ ] New constants land in `Constants.java` (not hardcoded literals in commands/subsystems)
- [ ] New log keys documented in `TELEMETRY_REFERENCE.md`

### Testability

- [ ] Any new class in `frc.lib.*` has ≥ 80% line coverage (enforced by JaCoCo)
- [ ] Any new class that reads `Timer.getFPGATimestamp()` takes a `DoubleSupplier` for injection
- [ ] Any new subsystem follows the IO-layer pattern (`XxxIO` interface + `XxxIOReal` + `XxxIOSim` + `Xxx extends SubsystemBase`)
- [ ] Any test that tries to `new Subsystem(...)` is a red flag — it will crash HAL

### Architectural boundaries (enforced by ArchUnit)

- [ ] `frc.lib.*` does not import `frc.robot.*` or `swervelib.*`
- [ ] `frc.robot.subsystems.*` does not import `frc.robot.commands.*`
- [ ] `frc.lib.diagnostics.*` does not import `frc.robot.*`

---

## When to reject, ask changes, or merge

### Merge without changes

- Spotless + SpotBugs + JaCoCo + ArchUnit all green
- Tests pass
- Commit message is self-explanatory
- Change is reversible if it turns out wrong (small, focused, one concern)

### Ask for changes

- New magic numbers without a `Constants` entry — ask for extraction
- New class that crosses a layer boundary — ask for refactor
- Test changed to avoid a HAL crash (e.g. `@Disabled`, `assumeTrue(isReal())`) — this papers over a design problem; ask for the underlying fix
- Large PR that bundles multiple unrelated changes — ask to split

### Reject outright

- Forces a `git push --force` to `main`
- Removes or weakens a safety check (brownout, current limit, SSM timeout) without a clear explanation of the trade-off
- Disables or suppresses an ArchUnit rule without an explanatory comment
- Adds a SpotBugs `<Match>` suppression without a javadoc explaining why the bug is a false positive

---

## Common student mistakes (educational, not blockers)

- Adding `// TODO:` comments instead of opening a follow-up task — coach, don't reject. The task system is more visible than comments.
- Writing `private static final double kFoo = ...` inside a command class — coach toward `Constants.Foo.kFoo`.
- Writing `System.out.println(...)` for debugging — the ArchUnit rule will catch this at CI time; still worth mentioning in review so they know about `Logger.recordOutput`.
- Trying to test a subsystem class directly — coach toward the IO-layer pattern; see the `FlywheelTest` file for the template.

---

## When things break at an event

1. **Stop the blame cycle.** Students will panic and argue about whose fault it is. Your first job is to get everyone calm and looking at the same screen.
2. **Open [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md)** — it's organized by symptom, not by layer. Find the symptom, follow the steps.
3. **Preserve evidence.** Before power-cycling or re-deploying, grab a screenshot of the DS, a copy of the last `.wpilog`, and a photo of any LED / physical state. This is the difference between "fixed the symptom" and "fixed the bug."
4. **Never commit a fix at an event without a log entry.** Use the `compDeploy` Gradle task on a `comp-*` branch — it auto-commits pit edits.
5. **Tell the team how you fixed it.** A 30-second post-match debrief with "here's what we did, here's why" is how students learn.

---

## Pre-event prep sanity

A week before the event:

- [ ] Every subsystem has at least one PR that exercises it in the last 30 days (staleness = risk)
- [ ] CI history on `main` has been green for at least the last 5 PRs
- [ ] `./gradlew deploy` works on a student laptop with a fresh clone
- [ ] `python3 tools/deploy_health_check.py` is all-green
- [ ] `./gradlew simulateJavaRelease` runs for 60 seconds without an exception in the log
- [ ] AdvantageScope connects, field widget shows pose, basic keys visible
- [ ] Elastic layout loads from `src/main/deploy/elastic-layout.json` and every widget binds
- [ ] Battery inventory — at least 6 fully-charged batteries labeled with cycle counts

Day of event, see [`PIT_CHECKLIST.md`](PIT_CHECKLIST.md).

---

## Teaching the IO-layer pattern

The biggest conceptual leap for students moving from "old-style" subsystems to the 2590 IO-layer pattern. Here's the short version to teach:

> A subsystem is one class. We split it into four: an **interface** that describes what the hardware can do, a **real** implementation that talks to the SPARK, a **sim** implementation that runs in a JVM without hardware, and a **thin wrapper** that picks which one to use at runtime.
>
> Why? Because when you want to test your command logic, you can swap in the sim. When you want to run a match, you use the real one. The interface guarantees they behave the same.

Three files, not one, but each file is small. The template generator (future `tools/new_subsystem.py`) will make this mechanical.

---

## Adding yourself as a reviewer

When you're confident enough to review PRs without a senior mentor looking over your shoulder, edit `.github/CODEOWNERS` to add your GitHub handle next to the relevant paths. Commit to `main` via a PR so your first addition is itself reviewed.
