# Scaffolds — deliberately-unwired classes

**Read before deleting any "unused" class.** This file is the single source of truth for code
that exists on purpose but isn't instantiated yet. Audits that run a grep for `new <Foo>()` will
flag every one of these as dead. They're not.

> **If you're an agent doing an audit sweep:** check this file FIRST. If a class you're about
> to delete is listed here, it stays. If the scaffold is no longer relevant, update this file
> AND delete the class in the same PR.

---

## Why keep scaffolds?

Four reasons we've actually hit:

1. **Pattern exemplar** — the class is cited in multiple docs as the canonical example of a
   pattern (time-injection, rate-limit math, etc). Deleting it breaks the docs and forces a
   student to re-discover a pattern we already wrote down.
2. **Mechanism scaffold** — subsystem code exists, but the mechanism isn't welded yet. When it
   arrives, the wiring is already done.
3. **Forward-looking infra** — a class is complete but the integration PR that wires it in is
   gated on an unrelated decision (e.g. per-motor current-limit API shape).
4. **Test harness hook** — a method is unused by production but exercised by tests that assert
   a specific contract (`FlywheelIO.stop()` "must zero applied output").

If none of these apply, the class really is dead — delete it and send a PR. If one does apply,
it belongs here.

---

## Current scaffolds

### Pattern exemplars

| Class | Pattern | Cited in | Wire-up trigger |
|---|---|---|---|
| `frc.robot.subsystems.StallDetector` | `Supplier<Double>` time injection for HAL-free testing | ADR 0001, ADR 0007, AGENTS.md, CODE_TOUR.md, DEVELOPER_TESTING_GUIDE.md, FAQ.md | Wire if a real stall scenario needs detection; do not delete in the interim. |
| `frc.lib.control.BatteryAwareCurrentLimit` | 971 CapU dynamic current ceiling | PLAN.md (7.7), CODE_TOUR.md | Wire when a per-motor integration PR lands (API shape is the blocker, not the math). |

### Mechanism scaffolds

| Subsystem | Files | Wire-up trigger |
|---|---|---|
| `Climber` | `Climber.java`, `ClimberIO.java`, `ClimberIOSim.java` (+ `ClimberIOReal` when wired) | Instantiate in `RobotContainer` once the TOWER climber is welded and a CAN ID is reserved (currently 11 in `CAN_ID_REFERENCE.md`). |
| `SideClaw` | `SideClaw.java`, `SideClawIO.java`, `SideClawIOSim.java` | Instantiate in `RobotContainer` if the 2026 strategy adopts a side-claw mechanism. CAN ID 20 is already reserved (re-flashed off 18). |

Both scaffolds follow the 2590 IO-layer pattern (`XxxIO` interface + `XxxIOInputs` + `XxxIOReal`
+ `XxxIOSim` + thin consumer). They compile and pass ArchUnit today; a future student only needs
to write `XxxIOReal` and instantiate.

### Test-harness hooks

| Method | Why kept | Test that asserts the contract |
|---|---|---|
| `FlywheelIO.stop()` (impl on `FlywheelIOReal`, `FlywheelIOSim`) | Interrupt-path contract | `FlywheelTest`, `FlywheelIOSimPhysicsTest` |
| `ConveyorIO.stop()` (impl on `ConveyorIOReal`, `ConveyorIOSim`) | Interrupt-path contract | `ConveyorTest` asserts `"stop() must zero conveyor applied output"` |
| `ClimberIO.stop()` / `SideClawIO.stop()` | Part of the scaffold IO contract; lifts cost of wiring later | Scaffold tests only (if any) |

If these methods move to private or get inlined into a specific command, the above tests must
move with them.

---

## Process for removing a scaffold

1. Confirm the scaffold's trigger fired (the mechanism shipped; the blocker resolved; the
   pattern moved to a better home).
2. Delete the class + its tests + the scaffold's row in this file in one PR.
3. If the class was a pattern exemplar, update every doc that cited it to reference the
   replacement.

## Process for adding a scaffold

Only add an entry here if you've argued through Reasons 1–4 above and at least one applies.
A "we might want this someday" is not a scaffold — that's a comment in `FOLLOWUPS.md`.

---

## History

- **2026-04-17** — File created after the audit sweep (PRs #88, #89, #90, #91 deleted ~2,300
  LOC of actually-dead code). Several classes were almost deleted in the same pass before
  being caught as either pattern exemplars (`StallDetector`) or pending-integration scaffolds
  (`BatteryAwareCurrentLimit`). This file prevents that round-trip next time.
