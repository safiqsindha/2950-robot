# ADR 0004: Enforce package-dependency rules via ArchUnit

**Status:** Accepted
**Date:** 2026-04 (Phase 6, PR #31)
**Author:** @safiqsindha

## Context

We wanted two layering invariants:

1. `frc.lib.*` stays reusable — it must not depend on `frc.robot.*` or `swervelib.*`.
2. Subsystems don't depend on commands — commands drive subsystems, never the reverse.

"Conventionally enforce it" works until the first time a student in a hurry doesn't know the rule. At PR #31 we found a real violation (`frc.lib.pathfinding.DynamicAvoidanceLayer` imported `frc.robot.Constants`) that had slipped past every prior review.

Alternatives:

1. Code-review convention — doesn't scale, relies on vigilance.
2. Checkstyle / custom SpotBugs rule — possible but custom bytecode analysis is expensive to write.
3. ArchUnit — purpose-built library, JUnit-integrated, cheap.

## Decision

Add ArchUnit as a test-scope dependency. `ArchitectureTest` enforces:

- `frc.lib.*` may not depend on `frc.robot.*`
- `frc.lib.*` may not depend on `swervelib.*`
- `frc.robot.subsystems.*` may not depend on `frc.robot.commands.*`
- `frc.lib.diagnostics.*` stays consumer-only (may only depend on `frc.lib`, `java`, `edu.wpi.first.*`, `org.littletonrobotics.*`)

Every rule has a `.because(...)` clause explaining the invariant.

## Consequences

Easier:
- Student PRs that break layering fail CI with a readable error.
- Refactors that cross boundaries are hard to miss — the test fails specifically at the cross.
- Onboarding — the rules are documented by the test itself.

Harder:
- Fixing a real violation is now a pre-merge blocker rather than a "clean up someday" task.
- PR #31 had to refactor `DynamicAvoidanceLayer` to take tuning params by constructor rather than import `Constants.Pathfinding.*`. One-time cost.

Locked out:
- Implicit cross-package coupling that used to be "fine if it worked" is now explicit.

## Notes

- Upstream: https://www.archunit.org/
- Version: `com.tngtech.archunit:archunit-junit5:1.3.0`
- Adding a new rule: a new `@Test` in `ArchitectureTest` with an `ArchRule` and `.because(...)`.
