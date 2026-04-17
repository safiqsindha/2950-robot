# Concepts — the math + control ideas behind the code

This doc is for students on Team 2950 who haven't taken calculus. It explains the "why" behind the code before you read it. It is not a Java tutorial, and it's not an FRC rules doc.

How to read it: open the file referenced in each section in a second window. Read the concept first, then look at the code line called out, and it should click.

---

## The big picture

Every 20 milliseconds, the robot runs one loop: read sensors, decide what to do, write outputs to motors. That's it. The rest of the codebase is just making each of those three steps correct and safe.

AdvantageKit logs everything that happens in that loop — sensor values, decisions, motor commands — so you can replay the match on your laptop afterward and see exactly what the robot "thought." You'll see `Logger.recordOutput(...)` calls all over the code; those are the logging hooks.

Understanding that loop is the key to understanding everything else in this doc.

---

## State machines — "what should the robot be doing right now?"

Think of a video game character. When you're standing still, the character is in IDLE. When you press jump, it switches to JUMPING. You can't be in JUMPING and ATTACKING at the same time — the game picks one.

Our robot works the same way. Rather than tracking ten separate boolean flags (`isIntaking`, `hasGamePiece`, `isShooting`, ...) that can contradict each other, we use one enum that can only hold one value at a time.

Look at `src/main/java/frc/robot/subsystems/SuperstructureStateMachine.java` — the `State` enum is at line 42:

```
IDLE, INTAKING, STAGING, SCORING
```

The transitions live in `computeNextState` at line 149. Each case answers: "given the current state and inputs, what state should we go to next?" Notice that INTAKING can only go to STAGING (when a game piece is detected) or back to IDLE (timeout or button release). It can't jump straight to SCORING. That's the whole point — the machine enforces the legal sequence.

**Play with it:** in `computeNextState`, change the condition `wheelCurrentAmps > currentThresholdAmps` to `false`. Run sim, press the intake button, and watch INTAKING refuse to advance to STAGING no matter what.

---

## Alliance flipping — mirroring coordinates

Imagine reading a map upside-down. The field is symmetric: blue side and red side are mirror images. All our auto paths are designed from the blue side. When we're on red, we flip every coordinate so the same path works.

Look at `src/main/java/frc/lib/AllianceFlip.java` lines 47–48:

```java
return new Translation2d(kFieldLengthMeters - translation.getX(), translation.getY());
```

The field is 16.541 m long. Flipping an X coordinate is just `16.541 − x`. If you're at x = 2 m on blue, the mirror position on red is x = 14.541 m. Y doesn't change because the flip is left-right, not up-down.

That's the whole thing. The Y coordinate stays the same; the rotation flip (handled just below in `flip(Rotation2d, boolean)`) mirrors the heading angle across the centerline.

**Play with it:** in sim, switch alliance color in the Driver Station, then run any auto path. Watch the starting pose jump to the opposite side.

---

## Smoothing noisy sensors — the IIR filter

Imagine you check your grade on every assignment and panic every time one quiz goes badly. A smarter move: average your last several grades to see the trend. One bad quiz barely moves the average.

An IIR (Infinite Impulse Response) filter does the same thing for sensor readings. Each new reading gets blended with the previous smoothed value:

```
smoothed = (old smoothed × most of the weight) + (new reading × a little weight)
```

The bigger the time constant, the more the "most of the weight" side dominates — slower to react, but much smoother.

Look at `src/main/java/frc/robot/Helper.java` lines 24–25:

```java
private static final LinearFilter distFilter = LinearFilter.singlePoleIIR(0.1, 0.02);
private static final LinearFilter aimFilter  = LinearFilter.singlePoleIIR(0.1, 0.02);
```

The `0.1` is the time constant (seconds) and `0.02` is the loop period. Without this, the Limelight AprilTag distance and aim angle readings jitter frame-to-frame, and the flywheel would constantly chase a noisy target.

**Play with it:** change `0.1` to `0.5` in those two lines and run sim. The aim will respond more slowly to movement — noticeably sluggish — but it won't flicker.

---

## PID — closing a loop on error

Picture driving a car. You glance at the lane and see you've drifted right (that's your **error** — how far off-center you are). You steer left proportionally to how far you've drifted (**P** — proportional). If you're about to overshoot the center, you back off before you get there (**D** — watching how fast the error is changing). If there's a steady crosswind pushing you right no matter what you do, you gradually increase your correction until it compensates (**I** — summing up the persistent error over time).

In formula form:

```
output = kP × error  +  kD × (how fast error is changing)  +  kI × (sum of past errors)
```

Look at `src/main/java/frc/robot/subsystems/Flywheel.java` lines 53–58 — the three `LoggedTunableNumber` entries for `kP`, `kI`, `kD`. These are editable live from AdvantageScope. The actual PID runs on the SPARK MAX hardware — the `io.setPid(...)` call at line 100 pushes updated gains to the controller.

In practice: start with kP only. It gets you most of the way there. Add kD if you're overshooting and oscillating. Add kI only if the flywheel settles slightly below the target and won't close the last gap — that persistent small error is called steady-state error, and kI is the fix.

**Play with it:** in AdvantageScope, find `Flywheel/kP` and crank it up to 3×. Run the flywheel. Watch the RPM graph oscillate — that's the symptom of too much P.

---

## Feedforward — guessing the right answer before measuring

Imagine you know from experience that you need to push the gas pedal to exactly 30% to maintain 60 mph on flat road. When you get on the highway, you go straight to 30% — not 0% and then slowly adding gas while watching the speedometer. Starting at the right answer is feedforward.

For a flywheel: if you know the motor needs roughly 4 V to spin at 3000 RPM, start there instead of starting at 0 V and letting PID ramp up. The formula:

```
voltage = kS + kV × (target velocity) + kA × (target acceleration)
```

`kS` is a constant offset (overcomes static friction). `kV` scales with velocity — faster spin needs more voltage. `kA` scales with how fast you're trying to accelerate.

SysId (see `MENTOR_GUIDE.md`) runs a calibration routine that measures those three numbers for the specific motors and gearbox on your robot.

Feedforward gets you ~90% of the way to the right speed. PID cleans up the remaining error caused by friction, battery sag, and load changes.

**Play with it:** in `src/main/java/frc/robot/Constants.java`, find the Flywheel `kV` constant and set it to `0`. Run the flywheel to 3000 RPM in sim. Compare how long the spinup takes versus the real value. Restore it when you're done.

---

## Rate limiting — "ramp toward the goal, don't jump"

Think of a phone volume button. Press it once, volume goes up one notch. You can't go from mute to full blast in one press. That controlled ramp is rate limiting.

If the flywheel jumped instantly from 0 to 3500 RPM in one 20 ms frame, the electrical current spike would brown out the robot. The rate limiter prevents that.

The math is simple — every frame, the new setpoint can only change by at most `maxAccel × dt`:

```
new_setpoint = clamp(goal, previous ± maxAccel × 0.02)
```

Two versions live in the codebase:

- `src/main/java/frc/lib/control/LinearProfile.java` — symmetric. Ramps up and down at the same rate. Right for the flywheel.
- `src/main/java/frc/lib/control/AsymmetricRateLimiter.java` — snaps to zero instantly, ramps up slowly. Right for intake/conveyor wheels where a slow ramp-down would leave the motor spinning during an emergency stop.

The flywheel uses `LinearProfile`. Look at `src/main/java/frc/robot/subsystems/Flywheel.java` line 77:

```java
setpointRpm = profile.calculate(goalRpm);
```

Every 20 ms, `goalRpm` might be 3500. `profile.calculate` returns a value that steps toward 3500 at the configured ramp rate — not 3500 all at once.

**Play with it:** in `Constants.java`, halve `Flywheel.kMaxAccelRpmPerSec`. Run the flywheel and watch the `Flywheel/SetpointRpm` trace in AdvantageScope climb at half speed.

---

## Lagrange quadratic — fitting a curve through 3 measured points

Three points determine a parabola — you've seen this in Algebra 2 when you solve for `a`, `b`, `c` in `y = a·x² + b·x + c` given three (x, y) pairs.

Here's the robot version: we measured that at 1.125 m from the HUB we need 2500 RPM, at 1.714 m we need 3000 RPM, and at 2.500 m we need 3500 RPM. We need a formula that gives the right RPM at any distance in between — and hits all three measurements exactly.

Lagrange interpolation is a specific way to build that parabola. Instead of solving a 3×3 system of equations, it builds the answer directly using "basis polynomials" — each one is 1 at one calibration point and 0 at the others.

Look at `src/main/java/frc/robot/Helper.java` line 46 — the `rpmFromMeters` method. Lines 60–63 are the three basis polynomial calculations (`l1`, `l2`, `l3`) and the weighted sum. The comment above the method lists the three calibration points.

Why not a lookup table? With only 3 points you'd have to pick "use nearest measured distance" — the RPM output would have steps at 1.125 and 1.714. The quadratic gives a smooth curve with no steps.

⚠ The calibration points at lines 48–50 may not be accurate for the current robot configuration. Re-run the calibration procedure before competition.

**Play with it:** change `y2` from 3000 to 3600 (a big change at the middle point). Re-run. Notice that the curve now bulges dramatically — the parabola is forced to pass through a point that breaks the smooth arc.

---

## Shoot-on-the-fly — compensating for robot motion

Think about a paintball gun on a moving cart. If the target is stationary and you shoot straight at it while moving sideways, you miss — the ball carries some of your sideways motion. You have to aim slightly ahead of where you're going to end up.

Our robot does this: instead of just using the current distance, we calculate where the robot will be when the ball arrives at the HUB, and aim for that virtual target instead.

The full three-argument overload lives at `src/main/java/frc/robot/Helper.java` line 119. It uses a technique called fixed-point iteration:

1. Guess the shot time: `time = distance / ball_speed`
2. Estimate where the robot will be at that time: `virtual_target = target − velocity × time`
3. Re-compute the distance to the virtual target
4. Repeat steps 1–3 two more times

Three repetitions is enough. Each iteration makes the answer more accurate, and by iteration 3 the remaining error is less than a centimeter at any realistic FRC speed. You can see the loop in `effectiveShotDistanceMeters` starting at line 147 — `for (int i = 0; i < 3; i++)`.

No calculus involved — just plugging a previous answer back into the same formula three times.

**Play with it:** in sim, drive the robot sideways at full speed while the flywheel is spinning. Check `Flywheel/GoalRpm` versus what it is when stationary. They should differ.

---

## Pose fusion — "where am I?"

Your phone knows its location from GPS, but GPS alone is slow and drifts a little. So your phone also uses the accelerometer and compass to fill in gaps between GPS updates. Each source lies a little; together they're accurate.

Our robot uses three sources:

- **Swerve wheel encoders + gyro** — very accurate over the short term (a few seconds), but drifts over a full match. Like a pedometer.
- **AprilTag camera (Limelight MegaTag2)** — gives an absolute field position every frame, but gets noisy when the robot is far from the tags or moving fast.

We blend these using a weighted average built into WPILib's `SwerveDrivePoseEstimator`. The tricky part is knowing when to trust the camera.

Look at `src/main/java/frc/robot/subsystems/VisionSubsystem.java`. The rejection logic is at lines 123 and 133:

- If the robot is moving faster than `kMaxLinearSpeedForVisionMps`, skip the camera reading.
- If the camera's reading is farther than `kMaxTagDistM` from the tags, skip it.

When the camera reading is accepted, it gets a "standard deviation" weight — smaller weight means less trust. Tags that are far away or seen at an angle get a bigger uncertainty number, so they move the estimate less.

**Play with it:** in sim, drive the robot at max speed and watch `Vision/RejectedForSpeed` flip to `true` in AdvantageScope. Slow down and watch it flip back.

---

## Potential-field obstacle avoidance

Picture iron filings on a sheet of paper with magnets underneath. Each obstacle is a magnet pushing the filings away. The goal is a magnet pulling them toward it. The filings follow the combined push-pull without any planning.

Our `DynamicAvoidanceLayer` does exactly this. Every 20 ms frame, it computes two force vectors and sums them:

- **Attraction to waypoint** — a vector pointing from the robot toward the next goal position. Magnitude is proportional to speed.
- **Repulsion from each opponent** — a vector pointing away from each detected opponent robot. Gets stronger as the robot gets closer.

Look at `src/main/java/frc/lib/pathfinding/DynamicAvoidanceLayer.java`. The repulsive force at line 112:

```java
magnitude = repulsiveGain × (influenceRadius − dist) / influenceRadius × maxSpeed
```

Distance drops out linearly — at the influence boundary, force is zero. Right next to an opponent, force is maximum. Sum all opponents, add to the attraction vector, cap the result at the robot's top speed.

Why not A*? A* re-plans an entire route every time an obstacle moves. Potential fields adjust every frame with no re-planning.

**Play with it:** in sim, put a simulated opponent directly in the robot's path. Watch `DynamicAvoidanceLayer` nudge the heading. Remove the opponent, watch the robot straighten out.

---

## Trajectory following — precomputed paths

A train on a track. Choreo designs the track offline: every 20 ms of the auto routine is pre-computed into a `.traj` file — exact position, velocity, and heading at each timestamp. At match time, the robot asks "where should I be right now?" and runs a PID loop to stay on that track.

Look at `src/main/java/frc/lib/trajectory/TrajectoryFollower.java`. The `computeSpeeds` method at line 94 does two things simultaneously:

1. **Feedforward** — `sample.fieldRelativeSpeeds()` is what Choreo says the velocity should be. Passed straight to the drivetrain, no calculation needed.
2. **PID correction** — three separate PID loops (x position, y position, heading) measure the error between where the robot actually is and where the trajectory says it should be. Their outputs add on top of the feedforward.

The formula per axis:

```
motor_command = FF_velocity + kP × (target_position − actual_position)
```

The PID pull keeps the robot on the path when disturbances happen (field bump, bad starting position). The FF means the PID only has to correct small errors — it doesn't have to do all the work.

**Play with it:** in sim, start the auto routine but nudge the robot off-path by teleop-driving briefly (if your sim supports it). Watch `TrajectoryFollower` pull the robot back to the trajectory while the FF continues its pre-planned motion.

---

## Where to go next

This doc covered the core math and control ideas. It deliberately left out:

- **SysId** — how we measure `kS`/`kV`/`kA` — see `MENTOR_GUIDE.md` and the WPILib docs
- **IO-layer pattern** — how subsystem code is structured for testability — see `CODE_TOUR.md`
- **AdvantageKit replay** — how to re-run a match log offline — see `FAQ.md`
- **ArchUnit** — the build-time rules that keep library code from tangling with robot code — see `CODE_TOUR.md`

Once you've read this, open `CODE_TOUR.md` for a guided walk through exactly where each piece lives in the file tree.
