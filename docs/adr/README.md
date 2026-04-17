# Architecture Decision Records

One markdown file per significant architectural decision. New decisions go in with a monotonically-increasing number.

Format: short, informal, skimmable. Not a requirements doc — more like "here's why we did that, so when the next team asks 'why this instead of X' we have an answer."

## Index

- [0001](./0001-io-layer-pattern.md) — Adopt the 2590 IO-layer pattern for all subsystems
- [0002](./0002-advantagekit-logging.md) — Use AdvantageKit `@AutoLog` over hand-rolled `Logger.recordOutput` duplication
- [0003](./0003-yagsl-over-custom-swerve.md) — Use YAGSL instead of rolling our own swerve kinematics
- [0004](./0004-archunit-package-rules.md) — Enforce package-dependency rules via ArchUnit rather than convention
- [0005](./0005-maple-sim-kinematic-bypass.md) — Work around the REV NEO force-direction bug via a kinematic bypass flag
- [0006](./0006-frc-lib-no-robot-deps.md) — `frc.lib` stays HAL-free and has no `frc.robot` dependencies — the "next-season lift" invariant
- [0007](./0007-supplier-injection-for-hal-free-tests.md) — Supplier injection as the default HAL-free testability pattern
- [0008](./0008-asymmetric-rate-limiting-for-open-loop.md) — Asymmetric rate limiting for open-loop mechanisms
- [0009](./0009-dependabot-scope.md) — Dependabot scope: bots do minor/patch, humans do major

## Writing a new one

Copy `0000-template.md` to `NNNN-your-decision-title.md`, fill in the sections, link from this index. One decision per file — don't batch.
