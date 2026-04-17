# ADR 0005: Work around the maple-sim REV NEO sign bug via a kinematic bypass flag

**Status:** Accepted (pending maple-sim upstream fix)
**Date:** 2026-04 (Phase 6, PR #10)
**Author:** @safiqsindha

## Context

maple-sim 0.4.0-beta ships with a bug: for REV SPARK MAX + NEO swerve modules, the propelling force is applied in the wrong direction. Result: in simulation, pushing the joystick forward drives the robot backward.

We documented this in [`MAPLE_SIM_BUG_REPORT.md`](../../MAPLE_SIM_BUG_REPORT.md) with the exact line of code + expected behavior. Upstream fix pending.

Alternatives:

1. Wait for the maple-sim fix — blocks every offseason sim session.
2. Fork maple-sim + patch — maintenance tax every time upstream ships a new release.
3. Kinematic bypass — intercept YAGSL's sim path and manually integrate the commanded chassis speeds.

## Decision

`Constants.Swerve.kUseMapleSimKinematicBypass = true` enables option 3 in `SwerveSubsystem.periodic()`:

```java
if (RobotBase.isSimulation() && kUseMapleSimKinematicBypass) {
  lastCommandedSpeeds = chassisSpeeds;
  swerveDrive.getMapleSimDrive().ifPresent(sim -> sim.setRobotSpeeds(chassisSpeeds));
}
swerveDrive.drive(chassisSpeeds);
```

The bypass runs maple-sim's odometry thread against our commanded speeds rather than its own physics-resolved ones. Simulation behaviour is now "what we told it to do" rather than "what its physics says," which is fine for teleop + auto verification — the things we care about in sim don't require force-level accuracy.

The flag default is `true`. When upstream fixes the REV sign bug, we flip to `false` on a practice-bot session and verify the physics path before merging the change.

## Consequences

Easier:
- Sim works now. Students can iterate on trajectories + commands.
- Flag is a single Boolean — easy to flip and re-test.

Harder:
- Force-level sim accuracy is lost (robot mass / wheel slip modeling). We don't currently use those signals, but if we ever want to tune the maple-sim physics (e.g. "how much does the intake weigh down the front left?") we'd need to flip the bypass off first.

Locked out:
- Nothing permanent. The bypass is reversible.

## Notes

- Report: [`MAPLE_SIM_BUG_REPORT.md`](../../MAPLE_SIM_BUG_REPORT.md)
- Upstream: https://github.com/Shenzhen-Robotics-Alliance/maple-sim
- To test upstream's fix: set `kUseMapleSimKinematicBypass = false`, `./gradlew simulateJavaRelease`, push the joystick forward, verify forward motion in AdvantageScope.
