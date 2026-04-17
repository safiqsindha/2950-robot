# Changelog

- 2026-04-17  #82  docs(followups): add 2025→2026 terminology sweep
- 2026-04-17  #81  docs: add FOLLOWUPS.md for next-session todo queue
- 2026-04-17  #80  fix: judge's 3 event-critical findings — intake bindings, vision latency, HID swallow
- 2026-04-17  #79  fix: final-pass polish — idempotent publish, Spark NPE guard, 2 doc notes
- 2026-04-17  #78  hotfix(autos): lazy-init SendableChooser so LoggedAutoChooser is HAL-free
- 2026-04-17  #76  feat: changelog workflow + sim tuning ref + fault tab (Batch P)
Every merged PR appends one line here via `.github/workflows/changelog.yml`. Dependabot PRs are skipped — see `.github/dependabot.yml` for the dependency-update cadence.

---

## Offseason 2026 — summary

The last 60+ PRs shipped a full offseason refactor. Highlights:

**Infrastructure**
- Diagnostics quintet (JVM / CAN / PDH / Loop time / Vision latency) + MatchPhaseOverlay + DriverInputRecorder all ticking in `Robot.robotPeriodic()`
- ArchUnit rules enforce the `frc.lib.* ↛ frc.robot.*` invariant and the subsystems-can't-import-commands boundary
- JaCoCo 80% line-coverage gate on `frc.lib.*`
- Headless sim smoke test in CI, PR-size summary, release-notes-on-tag workflow, PR-preview-artifact workflow, pre-commit hook installer
- Dependabot monthly cadence with human-triage policy (ADR 0009)

**Primitives (all in `frc.lib`, reusable next season)**
- `LinearProfile` (6328 pattern) + `AsymmetricRateLimiter` (snap-to-zero for open-loop)
- `LoggedTunableNumber`, `RollingWindowStats`, `BatteryAwareCurrentLimit` (971 CapU)
- `HolonomicTrajectory` / `HolonomicTrajectorySample` / `ChoreoTrajectoryAdapter` / `TrajectoryFollower` (4481 pattern)
- `Hysteresis`, `AreWeThereYetDebouncer`, `GeomUtil`, `RobotName`, `AllianceFlip`
- `FaultMonitor` / `TimedFaultMonitor` / `JvmLogger` / `CanBusLogger` / `PdhLogger` / `LoopTimeLogger` / `VisionLatencyTracker` / `MatchPhaseOverlay` / `DriverInputRecorder` / `CommandLifecycleLogger`
- `NavigationGrid` + `AStarPathfinder` + `DynamicAvoidanceLayer` (refactored off `Constants` as of PR #31)

**Subsystem patterns**
- Every mechanism is a 2590-style IO layer (`XxxIO` + `XxxIOInputs` + `XxxIOReal` + `XxxIOSim` + thin consumer)
- `Flywheel` + `Intake` slew their setpoints via `LinearProfile` / `AsymmetricRateLimiter`
- `SuperstructureStateMachine` has per-state timeouts, time-in-state telemetry, sub-state labels, explicit requestIdle transition log, and `SsmLedAdapter` for state→LED mapping
- `SwerveSubsystem` exposes SysId routines (drive + steer, quasistatic + dynamic) as SmartDashboard buttons

**Autonomous**
- `ChoreoAutoCommand` factory routes every sample through `TrajectoryFollower` (FF + PID) with explicit field→robot frame conversion
- `LoggedAutoChooser` publishes `Auto/SelectedName` every cycle; `selectRandom` + `RandomAutoRotator` rotate routines in practice
- `@AutoRoutine` annotation + `AutoRoutineRegistrar` for declarative routine registration (4481 pattern)

**Tooling**
- `tools/can_id_validator.py` — pre-deploy CAN conflict + undocumented-ID scanner
- `tools/rpm_curve_fit.py` — Lagrange quadratic refit from calibration CSV
- `tools/deploy_health_check.py` — ~2-second pre-deploy sanity bundle
- `tools/sim_smoke_test.py` — headless sim-crash detector (runs in CI)
- `tools/encoder_offset_finder.py` — in-place YAGSL JSON offset update
- `tools/choreo_validator.py` — Java code ↔ `.traj` file cross-check
- `tools/log_analyzer.py` — `.wpilog` summariser (pure-Python, no WPILib dep)
- `tools/install_hooks.sh` — pre-commit hook installer

**Documentation**
- `CODE_TOUR.md` / `TELEMETRY_REFERENCE.md` / `DEVELOPER_TESTING_GUIDE.md` / `MENTOR_GUIDE.md` / `FAQ.md` / `GLOSSARY.md`
- `SIM_QUICKSTART.md` — 15-minute onboarding for a Java-ready laptop
- `PIT_CHECKLIST.md` — printable event runbook
- `PRACTICE_SESSION_PLAYBOOK.md` — Phase A/B/C practice script
- `TROUBLESHOOTING.md` — symptom-indexed playbook
- `advantagescope-layout.json` — version-controlled 6-tab dashboard
- `docs/advantagescope-setup.md` — one-page AS onboarding
- `docs/adr/0001`–`docs/adr/0010` — 10 ADRs

**Bug fixes along the way**
- 4 flywheel bugs (2× divide-by-zero, missing conveyor stop, FlywheelAim empty end)
- Choreo field/robot frame bug (sample speeds were field-relative, passed as robot-relative)
- TrajectoryFollower `computeSpeeds` visibility
- CanBusLogger `::new` overload ambiguity
- FullAutonomousCommand NO_TARGETS path didn't `requestIdle()` the SSM
- SSM `requestIdle` didn't emit transition log
- `VisionLatencyTracker::new` overload ambiguity
- `BatteryAwareCurrentLimitTest` assertion vs implementation mismatch

---

## Per-PR ledger

Populated by `.github/workflows/changelog.yml` on every merge. Below this line is bot territory.
