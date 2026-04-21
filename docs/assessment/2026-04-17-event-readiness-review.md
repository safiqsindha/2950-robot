# 2026-04-17 Event Readiness Regrade (second pass after new work)

## Verdict
The repo remains an asset for district play, but there are still two event-risk clusters: autonomous behavior coupling and chooser/telemetry runtime hygiene.

## Updated weighted score (0-10)
- Layering discipline: 8.9 (x1.5)
- Test hygiene: 7.4
- Telemetry diagnosability: 8.0
- Safety semantics: 8.1 (x1.5)
- Documentation/onboarding: 8.6
- Build infrastructure: 7.5 (x1.5)
- Hardware↔code fidelity: 7.8
- Subsystem parity: 7.6
- Autonomous quality: 7.0
- Next-season transferability: 8.5 (x2.0)

Weighted total: **7.6/10** (letter **C**, CI estimate **[C, B-]**).

## Evidence updates from second pass

### Still strong
1. **Layering guardrails are executable** via ArchUnit package rules (lib isolation + subsystem/command directionality).
2. **Safety controls are explicit** (panic command, brownout scaling, SSM timeout/idle semantics).
3. **HAL-free testing intent is codified** and reinforced by ADRs + architecture tests.

### New/confirmed liabilities
1. **LoggedAutoChooser allocates NT objects in hot paths**
   - `selectByName` publishes a new topic publisher on each call.
   - `selectedNameOrEmpty` creates a new subscription each periodic call.
   - This can create unnecessary allocations/handles in a 50 Hz loop.

2. **Vision opponent API contract mismatch**
   - `VisionSubsystem.getOpponentPositions()` is documented as neural detector output.
   - `FuelDetectionConsumer.getDetectedOpponentPositions()` always returns empty.
   - Autonomous avoidance/bot-aborter then looks wired but is effectively inert unless another feed is added.

3. **Autonomous routine intent mismatch remains**
   - `ChoreoAutoCommand` documents expected `"intake"` marker behavior for station approach.
   - Existing routine wiring shown for two-coral path binds spinup/shot gates but not an explicit intake marker action.

4. **Runtime CI still non-blocking for sim smoke**
   - Workflow keeps smoke test `continue-on-error: true`, so startup/runtime regressions can merge.

5. **HAL canary still disabled**
   - `FlywheelIOSimPhysicsTest` remains `@Disabled`, reducing CI signal on sim-physics regressions.

## Priority fixes before the next event
1. Refactor `LoggedAutoChooser` to cache one publisher/subscriber object at `publish()` time and reuse it.
2. Either implement opponent extraction or rename/retire opponent-facing API to avoid false confidence in avoidance.
3. Wire and test explicit intake trigger in two/three-piece Choreo routines (marker or timed command).
4. Make sim smoke blocking once flake causes are removed.
5. Re-enable HAL flywheel physics canary in a dedicated lane/container.

## Files reviewed in this second pass
- `src/main/java/frc/robot/autos/LoggedAutoChooser.java`
- `src/main/java/frc/robot/commands/FullAutonomousCommand.java`
- `src/main/java/frc/robot/autos/AutonomousStrategy.java`
- `src/main/java/frc/robot/subsystems/FuelDetectionConsumer.java`
- `src/main/java/frc/robot/subsystems/VisionSubsystem.java`
- `src/main/java/frc/robot/commands/ChoreoAutoCommand.java`
- `.github/workflows/build.yml`
- `src/test/java/frc/robot/subsystems/FlywheelIOSimPhysicsTest.java`
- `src/test/java/frc/lib/ArchitectureTest.java`

## Note
Direct GitHub inline-review retrieval remains unavailable from this environment (HTTP 403), so this update applies a fresh source-level pass against current repo contents.
