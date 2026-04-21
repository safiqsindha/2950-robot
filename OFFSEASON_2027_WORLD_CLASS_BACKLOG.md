# Offseason 2027 World-Class Backlog (100 Targeted Fixes)

Purpose: convert this already-strong 2026 codebase into a 2027-ready, world-championship-caliber platform with low-risk, high-leverage offseason improvements.

## A) Reliability, Safety, and Fault Tolerance (1-20)

1. Add a centralized `HealthManager` that computes subsystem health states (`OK`, `DEGRADED`, `FAILED`) and drives behavior gating.
2. Add brownout-aware command decorators so all velocity-producing commands uniformly scale outputs.
3. Add a global “de-rate mode” that reduces acceleration and peak current when battery sag is persistent.
4. Add CAN fault latching + clear conditions for every motor controller at the IO layer.
5. Add auto-disable logic for non-critical subsystems when brownout floor is approached.
6. Add a boot-time hardware sanity check command group and dashboard checklist pass/fail summary.
7. Add startup guardrails that block autonomous modes if required sensors are unavailable.
8. Add periodic watchdog metrics to detect loop overruns and correlate with active commands.
9. Add a `CommandFailureMonitor` that logs command exceptions, cancels dependents, and sets LED alert state.
10. Add stale-vision rejection and stale-game-piece rejection timestamps in one common utility.
11. Add retry/backoff strategy for Limelight reconnect and table key missing states.
12. Add roboRIO temp and CAN utilization threshold alarms with escalating warnings.
13. Add explicit “safe defaults” for all subsystem outputs during disabled->enabled transitions.
14. Add `DriverStation` disconnect/reconnect transition hooks to avoid stale controller state.
15. Add explicit “sensor disagreement” alarms (e.g., drivetrain pose vs vision divergence) with counters.
16. Add PDH channel budget monitoring and over-current trend tracking per mechanism.
17. Add command-level timeout constants for every long-running command path.
18. Add a single source of truth for all voltage assumptions (`kNominalVoltage`, low-voltage floors, scale thresholds).
19. Add battery internal-resistance estimation from logs to tune current-limit and acceleration policies.
20. Add season-start “pit mode” dashboard page with one-click safe behaviors + fault reset routines.

## B) Architecture and Code Organization (21-40)

21. Split `RobotContainer` into focused binding modules (drive, scoring, test, sim) to reduce coupling.
22. Introduce `ModeConfig` objects for comp/practice/sim behaviors instead of scattered conditionals.
23. Add an explicit `RobotServices` composition root for shared non-subsystem services.
24. Move all repeated trigger thresholds into `Constants.OI` (including left/right trigger activation points).
25. Define command naming and logging conventions doc (`CommandName/State/Reason/Duration`).
26. Create package-level README files for each major subsystem package with class-role maps.
27. Move ad-hoc dashboard writes into a `TelemetryPublisher` abstraction for consistency.
28. Add `AutoRoutineRegistry` class for all auto routine construction and metadata.
29. Add one “virtual subsystem” package for coordination logic currently spread across commands.
30. Introduce typed wrappers for critical units (e.g., RPM, meters, volts) where practical.
31. Add architectural test rules to enforce “commands don’t reach around IO boundaries.”
32. Add a documented strategy pattern for game-specific logic replacement between seasons.
33. Add a `FieldModel` abstraction so 2027 field geometry swap is low-risk.
34. Add one place for alliance-dependent strategic transforms and remove duplicates.
35. Add a strict no-literals policy for mechanism tuning values outside `Constants`.
36. Add docs for command interruption/ownership expectations by subsystem.
37. Convert “smart defaults” in constructors into explicit factory methods for readability.
38. Add compile-time checks for package-dependency layering beyond existing ArchUnit rules.
39. Add lightweight API docs for all public classes in `frc.lib`.
40. Add ADRs for every offseason architectural change with “keep/revert” criteria.

## C) Controls, Performance, and Motion Quality (41-60)

41. Add slew/jerk limits to all driver-controlled translation and rotation paths.
42. Add heading-hold behavior with tuned deadzones for more precise teleop aiming.
43. Add adaptive drive acceleration limits based on battery voltage and robot velocity.
44. Add drivetrain anti-tip heuristic based on acceleration and estimated CoM.
45. Add static friction/feedforward characterization tasks for all key mechanisms.
46. Add automatic flywheel warmup profiles by expected next action.
47. Add velocity-setpoint smoothing for conveyor/intake transitions to reduce current spikes.
48. Add drivetrain speed governors tied to mechanism state (e.g., intake extended).
49. Add command-level “settled” criteria utilities (position/velocity/time stable windows).
50. Add auto-shot confidence scoring that combines pose quality, target visibility, and velocity.
51. Add regression tests for drive and flywheel tuning constants (bounds + monotonic expectations).
52. Add anti-windup and integral clamp policy docs + tests where I gains are nonzero.
53. Add tunable shot map strategy interface (table/fit/hybrid) to make 2027 retune easy.
54. Add smart “coast vs brake” mode management by match phase and mechanism state.
55. Add dynamic current-limit profiles for drivetrain and shooter based on game phase.
56. Add auto-alignment fallback behavior when vision is absent but odometry is acceptable.
57. Add driver-assist blending mode (partial assist instead of binary on/off command behavior).
58. Add full-input recording/replay for practice driving and tuning repeatability.
59. Add continuous verification of kinematic constraints during autonomous command generation.
60. Add a “performance budget” tracker: max loop time, max command latency, max CAN bus usage.

## D) Autonomous and Strategy Layer (61-75)

61. Add autonomous routine metadata (expected points, risk level, required sensors, fallback).
62. Add auto chooser filtering by health state (hide risky autos when sensors are degraded).
63. Add a behavior-tree or state-graph visualizer for `FullAutonomousCommand` decision flow.
64. Add target-selection explainability logging (why target A beat B).
65. Add hard constraints for endgame transitions (force climb priorities as time expires).
66. Add dynamic replanning cool-down + hysteresis to avoid thrashing between targets.
67. Add per-opponent avoidance confidence and proximity weighting from vision certainty.
68. Add autonomous KPI dashboard (cycle time, misses, retarget count, abort reasons).
69. Add action-level postmortem events (intake attempt started/succeeded/failed + reason).
70. Add robust “safe mode” variants for each field side and alliance.
71. Add dry-run simulation for every auto routine as a CI check.
72. Add route-approval tests for path legality against nav grid + dynamic obstacle assumptions.
73. Add “late-match salvage” policy for best-effort scoring if primary path fails.
74. Add command-group-level interruption tests for every autonomous branch.
75. Add deterministic seeds/config for any stochastic simulation elements.

## E) Vision and Sensor Fusion (76-87)

76. Add a formal measurement-quality score pipeline for each vision update.
77. Add per-tag trust weighting by geometry, distance, ambiguity, and motion state.
78. Add automatic camera-pipeline arbitration with hysteresis and lockout timers.
79. Add camera extrinsics verification test against known-field snapshots.
80. Add simulation-time injected camera noise models to stress-test filtering logic.
81. Add vision dropout recovery strategies with gradual trust regain (not instant full trust).
82. Add latency compensation validation tests with synthetic delayed measurements.
83. Add explicit “vision acceptance reason” and “rejection reason” counters.
84. Add game-piece detection debounce tuning utilities + logged confusion matrices.
85. Add multiframe data association for opponent/game-piece tracks.
86. Add auto-calibration tooling for camera pitch/yaw offsets using field tags.
87. Add one-click vision health summary panel (FPS, latency, tag count, acceptance ratio).

## F) Test, CI, and Tooling Excellence (88-100)

88. Add mutation testing for `frc.lib` to improve test quality beyond coverage percentages.
89. Add property-based tests for geometry/pathfinding utilities to catch edge cases.
90. Add golden-log replay tests for key commands and strategy outcomes.
91. Add a dedicated HAL-enabled physics canary test suite gated behind a profile.
92. Add performance regression tests (loop time + allocation) in CI.
93. Add static analysis gate for duplicate log keys and inconsistent logged data types.
94. Add static analysis rule for duplicate literal constants across command classes.
95. Add test fixtures/builders for subsystem IO inputs to reduce test boilerplate.
96. Add command-scheduler integration test harness for interruption semantics.
97. Add docs+scripts for “one-command pre-event validation” (build, tests, sim smoke, CAN check).
98. Add automatic changelog generation from conventional commits.
99. Add a readiness rubric that scores each subsystem weekly (code quality + reliability + performance).
100. Add an offseason-to-inseason migration checklist that marks what is proven and what remains experimental.

## Suggested rollout order (high leverage first)

- Weeks 1-2: Items 1-20, 88-90, 97.
- Weeks 3-4: Items 21-40, 91-96.
- Weeks 5-6: Items 41-60.
- Weeks 7-8: Items 61-75 and 76-87.
- Ongoing: 98-100 as process hardening.
