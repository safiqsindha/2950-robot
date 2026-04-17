# ADR 0010: Avoid `::new` method references with `assertDoesNotThrow`

**Status:** Accepted
**Date:** 2026-04
**Author:** @safiqsindha

## Context

JUnit 5's `assertDoesNotThrow` has two overloads:

```java
void assertDoesNotThrow(Executable executable);
<T> T assertDoesNotThrow(ThrowingSupplier<T> supplier);
```

Java's overload resolution happens before the method reference is resolved. When the target class has <b>two constructors</b>, `assertDoesNotThrow(MyClass::new)` becomes ambiguous — the method reference matches both `Executable::execute` (via no-arg ctor) and `ThrowingSupplier<MyClass>::get` (via no-arg ctor returning MyClass).

The compile error is clear but has now bitten us at least four times:

- **PR #25** — `FaultMonitor::new` (two ctors). Dropped the test.
- **PR #37** — `CanBusLogger::new` (two ctors). Fixed in #44.
- **PR #63** — `VisionLatencyTracker::new` (two ctors). Fixed in #73.
- **PR #60** — would have hit the same issue for `LinearProfile::new` if we'd added a no-arg ctor.

For classes with a **single** constructor, `::new` is unambiguous and works fine. The issue only manifests when we add an injection ctor alongside the default.

## Decision

For any class that has (or might gain) two constructors — which is every supplier-injection-style class in this repo — use an explicit lambda in `assertDoesNotThrow`:

```java
// ✅ Safe — always unambiguous
assertDoesNotThrow(() -> new MyClass());

// ❌ Risky — breaks the moment a second ctor is added
assertDoesNotThrow(MyClass::new);
```

Add this to the developer testing guide + agent guide.

## Consequences

Easier:
- Tests don't break when a production class gains a second constructor.
- Agents writing tests don't repeat this mistake.

Harder:
- Slightly more verbose test code — one extra `() ->` per usage.

Locked out:
- Nothing. The lambda form is strictly more general.

## Notes

- The JUnit team has discussed this on their issue tracker; there's no "safe default" overload resolution because the ambiguity is structural.
- `DEVELOPER_TESTING_GUIDE.md` should reference this ADR.
- Static analysis could catch it — SpotBugs and ErrorProne both have rules for ambiguous method references, but neither is wired into our build. Future CI improvement.
