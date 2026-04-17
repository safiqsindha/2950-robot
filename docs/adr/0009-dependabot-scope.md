# ADR 0009: Dependabot scope — bots do minor/patch, humans do major

**Status:** Accepted
**Date:** 2026-04
**Author:** @safiqsindha

## Context

We enabled Dependabot to surface stale dependencies each month (PR #46 added `.github/dependabot.yml`). The first wave included 9 PRs; 4 of them were major bumps we closed without merging:

- `actions/upload-artifact` 4 → 7 (breaking API changes in v5/v6/v7)
- `com.diffplug.spotless` 7 → 8 (deprecated options, new config API)
- `org.junit.jupiter` 5 → 6 (assertion API + extension changes)
- `gradle-wrapper` 8 → 9 (known breaking changes vs GradleRIO 2026)

The remaining 5 (actions/cache, actions/checkout, actions/setup-java, archunit, spotbugs) were minor/patch bumps; we merged them in 10 minutes with zero issues.

Patterns worth codifying:

1. **Major bumps are migrations, not maintenance.** They deserve a dedicated PR with explicit test plans and a rollback story.
2. **WPILib / vendor-specific deps are kickoff-time decisions.** We ignore them in `dependabot.yml` (already done).
3. **Auto-merge for Dependabot was tempting.** We chose not to because a SpotBugs / ArchUnit rule might silently accept a major-version change we'd rather review.

## Decision

Dependabot scope is explicit:

- **Bot territory (merge if CI green):** minor + patch bumps for infrastructure deps (archunit, spotbugs, spotless within a major, junit patches, GitHub Actions within a major version).
- **Human territory (close + flag for future migration):** any major-version bump, any WPILib-ecosystem dep (already ignored in config), any change to `gradlew` / `gradle-wrapper.properties` version.
- **Never bot-merge:** a PR that changes both code and a dependency in the same diff.

No auto-merge flag on Dependabot PRs. A mentor or maintainer still clicks merge, which is the checkpoint where the above rules get applied.

## Consequences

Easier:
- Predictable cadence — monthly notifications, small PRs to review.
- Minor bumps picked up quickly; major bumps don't sneak in.

Harder:
- Someone has to close the major-bump PRs periodically. ~1 minute each.
- If we forget to close, the bot re-opens eventually with the same bump. Annoying but harmless.

Locked out:
- Fully-automated dependency updates. We accept the human checkpoint as the cost of confidence.

## Notes

- Config: `.github/dependabot.yml`
- Rules referenced in `MENTOR_GUIDE.md` and `FAQ.md`.
- Future: if the team grows and PR review capacity tightens, consider letting CI gate auto-merge on patch-only bumps.
