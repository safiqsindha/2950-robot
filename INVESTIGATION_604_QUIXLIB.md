# Investigation: Team 604 QuixSwerveDriveSimulation Adoption

**Date:** 2026-04-16
**Investigator:** Research agent for Team 2950
**Source examined:** https://github.com/frc604/2025-public/tree/main/FRC-2025/src/main/java/frc/quixlib/maplesim
**Team 2950 stack:** WPILib 2026, YAGSL 2026.3.14, maple-sim 0.4.0-beta, REVLib 2026.0.5 (NEO + SPARK MAX)

---

## TL;DR Recommendation

Do not adopt QuixSwerveDriveSimulation wholesale. The class is a thin (~130-line) wrapper over the same `AbstractDriveTrainSimulation` base that vanilla maple-sim uses, and its `simulationSubTick()` physics approach is genuinely superior to stock maple-sim's chassis-level friction model — but every critical path inside it calls `QuixSwerveModule.getGroundForceVector()`, which in turn calls `DCMotor.getKrakenX60Foc(1).getTorque(m_driveMotor.getTorqueCurrent())` and reads torque current from a CTRE `TalonFXSimState`. That is a hard CTRE/TalonFX dependency baked into the force-calculation hot path: the class cannot be used with REV SPARK motors without rewriting those methods. More importantly, the entire class is structurally glued to 604's `QuixSwerveModule` type, which itself hardcodes `QuixTalonFX` as the motor implementation. Porting it to YAGSL's module abstraction would require either (a) writing a YAGSL-compatible module adapter that exposes torque current from REV motors, or (b) fully rewriting the swerve layer. The right near-term action for Team 2950 is to keep the manual kinematic bypass already in `SwerveSubsystem.java` and track the upstream maple-sim issue #96 ("[Enhancement] Improve Accuracy by Simulating Force on Each Swerve Module"), which proposes exactly the per-module force model that 604 already implements. If that upstream enhancement ships, YAGSL can adopt it without a swerve rewrite.

---

## Architecture Overview

`QuixSwerveDriveSimulation` (single file, `frc/quixlib/maplesim/`) **wraps** maple-sim rather than replacing it. Specifically:

- It extends `org.ironmaple.simulation.drivesims.AbstractDriveTrainSimulation`, the same abstract base that stock maple-sim's `SwerveDriveSimulation` extends.
- It is registered with `SimulatedArena.getInstance().addDriveTrainSimulation(...)` and runs inside the same maple-sim dyn4j physics world that Team 2950 already has.
- It overrides exactly one method: `simulationSubTick()`. Everything else — the dyn4j body, pose tracking, the `SimulatedArena` game loop, gyro simulation — is inherited from maple-sim unchanged.

**What `simulationSubTick()` actually does differently:**

Stock maple-sim (`SwerveDriveSimulation`) computes physics at the chassis level: it derives a single aggregate "module speed" from all four modules, then applies one bulk friction force and one bulk rotational torque to the dyn4j rigid body. This is the source of the drift bug: when the robot is commanded to stop, the friction force calculation can produce a small non-zero residual because it works from desired module speeds rather than actual torque output, and the dyn4j integrator then produces a slow slide.

604's override instead:
1. Samples the actual chassis velocity from the physics body each sub-tick.
2. Decomposes that velocity into a per-module ground speed at each module's mounting location (translational + rotational component).
3. Calls `module.updateSimSubtick(moduleAlignedVelocity)` to push that speed into the motor sim.
4. Asks each module `getGroundForceVector(maxFrictionForce)` for the propulsive force based on actual motor torque, clamped to the friction limit.
5. Computes a lateral friction force per module from the module's lateral slip velocity.
6. Clamps combined force per module to not exceed `m_maxFrictionForcePerModule`.
7. Applies both drive and lateral friction forces to the dyn4j body at the module's world-space location (not the center of mass).

This is a more physically correct model: drive forces are proportional to actual motor torque output, not to desired speed, and lateral friction is computed per-module independently. It cannot produce a residual drift when motor output is zero because `getGroundForceVector()` returns a zero-magnitude vector when motor torque is zero.

---

## Motor API Compatibility (REV vs CTRE)

**Hard incompatibility. Cannot be used with REV SPARK motors without source changes.**

The incompatibility is in two places:

**1. `QuixSwerveModule.getGroundForceVector()` — the force calculation hot path:**

```java
public Translation2d getGroundForceVector(double maxFrictionForce) {
    final double motorTorque =
        DCMotor.getKrakenX60Foc(1).getTorque(m_driveMotor.getTorqueCurrent());
    ...
}
```

This calls `getTorqueCurrent()` on a `QuixTalonFX` instance, which reads the CTRE Phoenix 6 `TalonFXSimState` rotor torque current. There is no equivalent in the REV simulation API: `CANSparkMaxSim` / `SparkFlexSim` expose `getVelocity()` and `getPosition()` but not torque current. REV's simulation model does not track stator current at the physics level.

**2. `QuixSwerveModule.updateSimSubtick()` — the drive encoder update path:**

```java
m_driveMotor.setSimSensorVelocity(velocity, dt, m_driveRatio);
```

This calls `TalonFXSimState.setRotorVelocity()` and `addRotorPosition()`. REV's equivalent is `SparkSim.iterate()` which takes motor velocity and battery voltage but does not accept an externally-integrated velocity injection in the same way.

**3. `QuixSwerveModule` constructor hardcodes `QuixTalonFX`:**

```java
m_driveMotor = new QuixTalonFX(driveMotorID, driveRatio, ...);
m_steeringMotor = new QuixTalonFX(steeringMotorID, steeringRatio, ...);
```

The `QuixMotorControllerWithEncoder` interface does exist as a separate interface, and in principle a `QuixSparkMax` implementation could be written, but none exists in the codebase — only `QuixTalonFX.java` implements it.

**Summary:** The force-physics approach is motor-agnostic (it only cares about torque and gear ratio), but every concrete data path that feeds torque current into it is TalonFX-specific. There is no drop-in path for REV hardware.

---

## Swerve Library Coupling (YAGSL vs QuixSwerve)

**Tightly coupled to QuixSwerveModule. Not pluggable into YAGSL.**

`QuixSwerveDriveSimulation` has this constructor signature:

```java
public QuixSwerveDriveSimulation(
    DriveTrainSimulationConfig config,
    QuixSwerveModule[] modules,   // <-- hard typed to 604's module class
    double cof,
    Pose2d initialPoseOnField)
```

Its `simulationSubTick()` calls three methods on each `QuixSwerveModule`:
- `module.getPosition()` — returns `Translation2d`; this is a standard WPILib type and is portable.
- `module.updateSimSubtick(double velocity)` — injects velocity into `TalonFXSimState`; CTRE-specific.
- `module.getGroundForceVector(double maxFriction)` — reads TalonFX torque current; CTRE-specific.
- `module.getSteeringAngle()` — reads from `QuixTalonFX` sensor position; CTRE-specific.

YAGSL's internal module representation is `SwerveModule` (from the `swervelib` package). It exposes `getState()`, `getModulePosition()`, and `setDesiredState()` — but it does not expose:
- The raw drive motor torque current.
- A method to inject a ground-truth velocity into the motor sim state.
- The steer angle from the simulated encoder independently.

Bridging YAGSL to this API would require either subclassing YAGSL's `SwerveModule` (which is not designed for extension) or wrapping each YAGSL module with a facade that re-exposes these simulation signals — which in turn requires access to the underlying `CANSparkMax` sim state, which YAGSL wraps and hides behind its internal `SwerveMotor` abstraction.

**There is no clean adapter path.** Adopting `QuixSwerveDriveSimulation` with YAGSL would require either patching YAGSL to expose simulation internals, or abandoning YAGSL for swerve control entirely.

---

## maple-sim Bug Analysis

**604's approach directly addresses the root cause of the drift bug, but Team 2950 already has a working bypass.**

The drift-without-input bug in Team 2950's current setup (confirmed in `SwerveSubsystem.java`) has this root cause chain:

1. YAGSL delegates simulation to maple-sim's `SwerveDriveSimulation`.
2. `SwerveDriveSimulation.simulationSubTick()` calls `simulateChassisFrictionForce()`, which computes friction as `FRICTION_FORCE_GAIN * totalGrippingForce * speedsDifference`. The `speedsDifference` is the delta between chassis velocity and `getModuleSpeeds()`.
3. `getModuleSpeeds()` is derived from `SwerveModuleSimulation.getFreeSpinState()`, which calls `calculateMechanismVelocity()` using the last applied motor voltage. With REV's `GenericMotorController` sim, the applied voltage after a stop command can be a small residual from the PID integrator or the motor controller's internal state machine, not exactly zero.
4. The non-zero free-spin state produces a non-zero friction vector, which slightly accelerates the dyn4j body, producing visible drift.

The comment in Team 2950's `SwerveSubsystem.java` (line 159) accurately describes the symptom: "the maple-sim motor force pipeline applies forces in the wrong direction." The existing bypass (manual kinematic integration + `setRobotSpeeds()` override) is a correct pragmatic fix for simulation purposes.

**What 604 does differently:** By computing force from `DCMotor.getKrakenX60Foc(1).getTorque(actualTorqueCurrent)` instead of from `getFreeSpinState()`, the force is exactly zero when no current is flowing — which happens whenever the motor command is zero or near-zero. This eliminates the residual-voltage drift path entirely.

**However:** this fix only works because CTRE's `TalonFXSimState` accurately reports torque current even in sim. REV's simulation layer does not expose torque current, so the same fix cannot be applied to REV motors without REV adding torque-current simulation support, which is not currently on their public roadmap.

**Upstream status:** maple-sim issue #96 ("Improve Accuracy by Simulating Force on Each Swerve Module", open as of April 2026) explicitly proposes the per-module force model. If it lands in a future maple-sim release, YAGSL would likely pick it up and both the drift bug and the force accuracy gap would be resolved upstream.

---

## Adoption Effort Estimate

| Task | Effort | Feasibility |
|------|--------|-------------|
| Copy `QuixSwerveDriveSimulation.java` alone | 1 hour | Useless without the module layer |
| Write `QuixMotorControllerWithEncoder` impl for SPARK MAX/Flex | 2-3 days | Possible, but no REV torque-current signal exists in sim |
| Write `QuixSwerveModule` equivalent backed by YAGSL modules | 3-5 days | Requires YAGSL internals access not currently exposed |
| Abandon YAGSL, rewrite swerve on QuixSwerve + QuixTalonFX | 2-3 weeks | Requires switching motor hardware (NEO -> Kraken) or porting QuixLib to REV |
| Track upstream maple-sim #96 and update vendordep when it ships | 0-1 hour | Low risk, no code changes to robot |

The only path that preserves the existing YAGSL + REV hardware stack is the upstream fix. The adoption paths that use 604's code all ultimately require either replacing REV motors with CTRE hardware or building a substantial REV motor simulation layer from scratch.

---

## Conclusion

**Do not adopt. Track upstream. Keep the current bypass.**

604's `QuixSwerveDriveSimulation` is a well-designed solution to a real problem, but it is architecturally inseparable from the CTRE/TalonFX ecosystem:

- `QuixSwerveModule` hardcodes `QuixTalonFX` as its motor implementation.
- The force-calculation hot path reads CTRE `TalonFXSimState.torqueCurrent`, a signal REV does not expose in simulation.
- The `QuixSwerveModule[]` array parameter in the constructor makes the class non-pluggable without source changes.

Team 2950's current kinematic bypass in `SwerveSubsystem.periodic()` (lines 154-176) correctly addresses the drift symptom for simulation purposes. It manually integrates position from commanded speeds and overrides the physics body velocity each tick, which produces visually correct autonomous and teleop simulation behavior regardless of the underlying maple-sim force bugs.

The recommended path forward:
1. **Keep the existing bypass** — it works, is already battle-tested in this codebase, and has zero third-party dependencies.
2. **Watch maple-sim issue #96** — if a per-module force model ships upstream, upgrade the vendordep. YAGSL has a history of tracking maple-sim releases quickly.
3. **Do not adopt QuixLib** unless Team 2950 migrates to CTRE hardware, at which point the full 604 pattern (QuixSwerve + QuixSwerveDriveSimulation) would be a strong reference implementation.
4. **Optionally, file or upvote a REV issue** requesting torque-current exposure in `SparkFlexSim`/`CANSparkMaxSim`. That is the only change that would allow the 604 per-module force model to work with REV hardware.
