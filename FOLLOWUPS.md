# Follow-ups

Intent queue for work surfaced during a session but not done in that session. Add dated entries on top. Oldest are at the bottom — delete a bullet only after the change is shipped (don't mark ✓ in place).

---

## 2026-04-17 — remaining migration items (all externally blocked)

The 2025 Reefscape → 2026 REBUILT migration is 6-of-9 shipped (see `PLAN.md` Phase 9). Three
steps remain, each blocked on an input we don't currently have.

- [ ] **Migration step 3 — rebuild navgrid obstacle body for 2026.** Grid dims are correct
  (16.541 × 8.069) and declared dims in both `src/main/deploy/navgrid.json` and
  `src/main/deploy/pathplanner/navgrid.json` are fixed. The obstacle body itself still
  traces 2025 Reef hexagons at 2025 coordinates — `NavigationGridTest.testHubLocation_isObstacle`
  asserts (3.39, 4.11) is blocked, which was the 2025 Blue Reef center; 2026 Blue HUB is at
  approximately (4.6, 4.0). **Blocker:** 2026 REBUILT CAD (Onshape, or STEP files from FIRST's
  Playing Field Assets page). Either write `tools/navgrid_generator.py` from the CAD or
  manually rasterize the HUB + TOWER + OUTPOST + TRENCH + STARTING ZONE footprints into the
  164×82 grid. Update the `testHubLocation_isObstacle` coordinates to the 2026 Blue HUB.

- [ ] **Migration step 4 — re-calibrate the flywheel Lagrange curve.** `Helper.rpmFromMeters`
  uses three calibration points (1.125 → 2500, 1.714 → 3000, 2.500 → 3500) whose provenance
  isn't captured in git history — could be 2025 Coral calibration, could be 2026 FUEL
  practice-session. The docstring has been updated with a hardware-verification warning.
  **Blocker:** hardware + practice time. Measure 3+ (distance, RPM) pairs with real 2026 FUEL
  vs 2026 HUB, then run `tools/rpm_curve_fit.py` to regenerate Lagrange constants. A side-task
  would be `git log -p src/main/java/frc/robot/Helper.java` to confirm whether the existing
  points are 2026-tuned (would skip the hardware work).

- [ ] **Migration step 5 — re-author Choreo trajectories on the 2026 field.** Current
  `leaveStart.traj`, `reefToStation.traj`, `stationToReef.traj` use 2025-shaped waypoints.
  The (4.50, 4.00) point in the HUB approach coincidentally matches the 2026 Blue HUB
  centerline, but (0.90, 7.20) doesn't land on any identifiable 2026 feature. **Blocker:**
  Choreo desktop + 2026 REBUILT field descriptor. Delete the 2025-named `.traj` files; create
  new paths named after 2026 geometry (e.g. `hubToIntake.traj`). The `TRAJ_REEF_TO_STATION` /
  `TRAJ_STATION_TO_REEF` constant names and the local variable names mirroring those files
  (`stationToReef`, `toStation1`, etc.) should rename in the same PR.

- [ ] **Migration step 7 — redesign the 2- and 3-fuel auto routines.** Depends on step 5
  (re-authored trajectories) and on whether 2026 HUB scoring + FUEL pickup supports the
  preload + 2 station-cycle pattern at the point total the team wants to hit. Depends on
  REBUILT scoring rules + point values.

- [ ] **Migration step 8 (lower priority) — further tune vision pose gates.** `kMaxTagDistM`
  (4.0 m) is likely fine — 2026 HUB tag height is 1.124 m, similar to 2025 Reef.
  `kMaxLinearSpeedForVisionMps` + `kMaxCorrectionAutoMeters` are velocity/drift gates, not
  distance-sensitive. Re-tune only if event-day data shows vision pose estimates are under-
  or over-trusted.

**Risk of not doing any of the above:** event-day behaviour would be incorrect in navgrid
pathfinding (avoids wrong obstacles), flywheel RPMs (if Lagrange points are 2025), and
trajectory waypoints. Field dimension constants, AprilTag filter, and terminology are all
already correct.

---

## Always check before deleting apparent dead code

See [`SCAFFOLDS.md`](SCAFFOLDS.md). Four categories of intentionally-unwired classes currently
exist (`StallDetector`, `BatteryAwareCurrentLimit`, `Climber`, `SideClaw` + their IO layers;
plus `FlywheelIO.stop`/`ConveyorIO.stop`). An audit that greps for `new <Foo>()` will flag
them; `SCAFFOLDS.md` explains why each one stays.

---

## How to use this file

1. When you notice something that should be done later, add a bullet here with today's date.
2. Start each session by reading the top section.
3. When you ship a bullet's fix, remove the bullet from here and let the changelog bot record the PR.
4. Don't let this file grow past 50 items; if it does, stop adding and start doing.
