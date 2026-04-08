# Student Testing Guide — FRC Team 2950 The Devastators

> **Who this is for:** Any team member testing the robot, even if you've never looked at the code.
> Follow each section step by step. If something fails, write it down and tell a mentor before moving on.

---

## Table of Contents

1. [Before You Start](#before-you-start)
2. [Quick Health Check (Pit Crew Diagnostic)](#quick-health-check)
3. [Swerve Drivetrain](#swerve-drivetrain)
4. [Encoder Calibration](#encoder-calibration)
5. [Flywheel](#flywheel)
6. [Intake](#intake)
7. [Conveyor & Spindexer](#conveyor--spindexer)
8. [Climber](#climber)
9. [Side Claw](#side-claw)
10. [Vision — AprilTag Detection](#vision--apriltag-detection)
11. [Vision — Auto-Align (Teleop)](#vision--auto-align)
12. [Vision — Auto Score](#vision--auto-score)
13. [Vision — Game Piece Detection](#vision--game-piece-detection)
14. [Autonomous Modes](#autonomous-modes)
15. [LED Indicators](#led-indicators)
16. [CAN Bus Reference](#can-bus-reference)
17. [Controller Map](#controller-map)
18. [Troubleshooting](#troubleshooting)

---

## Before You Start

### What You Need
- Fully charged battery (12.0V+ at rest)
- Driver Station laptop with FRC Driver Station installed
- Xbox controller (Port 0 = driver, Port 1 = operator)
- USB cable or radio connection to RoboRIO 2
- Limelight camera powered and connected via ethernet

### Safety Rules
1. **Robot on blocks** for all mechanism tests — wheels must be off the ground
2. **Keep hands clear** of intake arms, flywheel, conveyor, and climber at all times
3. **Call "ENABLING"** before enabling the robot so everyone near the robot knows
4. **E-stop** is always your first reaction if something goes wrong — spacebar on Driver Station

### Connecting to the Robot
1. Power on the robot (main breaker)
2. Wait 30 seconds for RoboRIO and radio to boot
3. Connect laptop to robot WiFi (`2950` network) or plug in USB
4. Open FRC Driver Station — you should see green indicators for Communications and Robot Code
5. If Robot Code is red, the code hasn't been deployed — ask a programming mentor

---

## Quick Health Check

The robot has a **built-in diagnostic** that tests every mechanism automatically.

**File:** `src/main/java/frc/robot/commands/PitCrewDiagnosticCommand.java`

### How to Run
1. Put the robot on blocks (wheels off ground)
2. Open Driver Station
3. Switch to **Test** mode (not Teleop, not Auto)
4. Enable the robot
5. The diagnostic starts automatically — watch the Driver Station console output

### What It Tests (10 steps, ~30 seconds)

| Step | What It Checks | Pass Criteria |
|------|---------------|---------------|
| 1. CAN Bus Health | Battery voltage, CAN utilization | Battery >= 12.0V, CAN < 70% |
| 2. Swerve Encoders | All 4 absolute encoders respond | No NaN values |
| 3. Gyro | ADIS16470 heading is stable | Drift < 2 degrees over 1.5s |
| 4. Flywheel Spin-Up | Main flywheel motors spin | Reaches >= 1200 RPM in 3s |
| 5. Flywheel Feed | Front/back feed wheels spin | Runs without CAN error |
| 6. Intake Arms | Arms move to commanded position | Arms visually move |
| 7. Intake Wheel | Intake roller spins | Current draw >= 2A |
| 8. Conveyor | Conveyor belt runs | Runs without CAN error |
| 9. Climber | Climber actuates small movement | Runs without CAN error |
| 10. Vision | Limelight connected | Limelight publishing to NetworkTables |

### Reading the Results
- Open the Driver Station console — each step prints `[PASS]`, `[WARN]`, or `[FAIL]`
- LEDs on the robot show the summary:
  - **Green flash** = all passed
  - **Yellow flash** = some warnings
  - **Red flash** = at least one failure
- Results are also logged to SmartDashboard under `Diagnostic/*`

### Common Failures

| Failure | What To Do |
|---------|-----------|
| Battery < 12V | Swap the battery — don't test on a low battery |
| Encoder NaN | Check the encoder cable for that module — may be loose |
| Gyro drift > 2° | Let the robot sit still for 10 seconds after power on, then rerun |
| Flywheel < 1200 RPM | Check flywheel belts for tension, check motor connections on CAN IDs 22 and 23 |
| Intake wheel < 2A | Check intake wheel is contacting rollers, check CAN ID 4 |

---

## Swerve Drivetrain

**Files:**
- `src/main/java/frc/robot/subsystems/SwerveSubsystem.java`
- `src/main/deploy/swerve/*.json` (YAGSL config)

**Hardware:** 4x Thrifty Swerve modules, 8x SparkMax controllers, 8x NEO motors, ADIS16470 gyro

### Module Layout

```
         FRONT
   ┌───────────────┐
   │ FL          FR │
   │ Drive:13  D:7  │
   │ Steer:5   S:19 │
   │                │
   │ BL          BR │
   │ D:10       D:8 │
   │ S:6        S:12│
   └───────────────┘
         BACK
```

### Test on the Physical Robot

**Preparation:** Robot on blocks, all 4 wheels off the ground.

1. Enable in **Teleop** mode
2. Push the left stick forward — **all 4 wheels should spin the same direction** (forward)
3. Push the left stick to the right — all wheels should spin to strafe right
4. Push the right stick to rotate — modules should angle and spin to rotate the robot
5. Let go of all sticks — wheels should stop within 1 second

**Pass Criteria:**
- All 4 modules respond to every input
- No modules are spinning the wrong direction
- No grinding, clicking, or skipping noises
- Modules return to straight-ahead when you stop rotating

**Failure Modes:**
- One module doesn't spin → check that module's drive motor CAN connection
- One module points the wrong direction → encoder offset is wrong (see Encoder Calibration)
- Robot drives in a circle when you push straight → one module has inverted drive or steer
- Grinding noise → gear mesh issue or loose belt in that module

### Gyro Zero Test
1. Enable in Teleop
2. Press **A button** on the driver controller — this zeros the gyro
3. The robot should now consider "forward" as the direction the robot is currently facing
4. Push left stick forward — robot should drive away from you
5. If the robot drives sideways, the gyro zero was off — face the robot away from you and press A again

### Wheel Lock Test
1. Enable in Teleop
2. Hold **Y button** — all 4 modules should angle into an X pattern
3. Try to push the robot — it should resist movement
4. Release Y — modules return to normal

---

## Encoder Calibration

**File:** `src/main/java/frc/robot/commands/EncoderCalibrationCommand.java`

Use this when modules aren't pointing straight or after replacing a swerve module.

### Step by Step

1. **Manually point all 4 modules straight forward.** The bevel gear on each module should face the same direction (check with a mentor which direction is correct for your module type).
2. Deploy the code to the robot
3. Switch to **Test** mode in Driver Station
4. Enable — the `EncoderCalibrationCommand` runs
5. Open the Driver Station console — you'll see output like:

```
========== ENCODER OFFSETS ==========
[encoder_offsets]
frontleft_offset_deg = 127.432
frontright_offset_deg = 243.891
backleft_offset_deg = 89.203
backright_offset_deg = 312.654
=====================================
Copy the above into hardware_config.ini [encoder_offsets]
Then run: python tools/generate_configs.py
```

6. Copy these values into `hardware_config.ini` under `[encoder_offsets]`
7. Run `python tools/generate_configs.py` from the project root to regenerate the YAGSL JSON files
8. Redeploy the code
9. Test drive — modules should now point straight when the joystick is centered

**Also visible in:** SmartDashboard and AdvantageScope under `Calibration/*`

---

## Flywheel

**File:** `src/main/java/frc/robot/subsystems/Flywheel.java`

**Hardware:**
- 2x SparkFlex + NEO Vortex (main flywheel) — CAN IDs 23 (left), 22 (right, follower)
- 2x SparkMax + NEO (feed wheels) — CAN IDs 15 (front), 2 (back)

### Test on the Physical Robot

**Safety:** Keep hands and loose clothing away from the flywheel at all times.

1. Robot on blocks, enable in **Teleop**
2. Press **D-Pad Right** — flywheel should spin up to 2400 RPM
3. Listen for smooth, consistent spin — no wobble or vibration
4. Press **D-Pad Down** — should spin to 2500 RPM
5. Press **D-Pad Left** — 3000 RPM
6. Press **D-Pad Up** — 3500 RPM
7. Release the D-Pad — flywheel should coast down and stop

**Pass Criteria:**
- Flywheel reaches target RPM within 2-3 seconds
- Steady RPM with no oscillation (check SmartDashboard for RPM value)
- Both Vortex motors are spinning (listen for both sides)
- RPM readback is within 10% of target (`Constants.Flywheel.kReadyThreshold = 0.10`)

**Failure Modes:**
- Flywheel doesn't spin → check CAN IDs 22 and 23, check belt connection
- RPM never stabilizes → PID tuning issue, check `kP=0.00075` in `Constants.Flywheel`
- Loud grinding → flywheel wheel contacting housing, check alignment
- Only one side spins → the follower motor (ID 22) may have lost CAN or the follower config is wrong

### RPM Presets Reference

| D-Pad | RPM | Typical Use |
|-------|-----|-------------|
| Right | 2400 | Close range shot |
| Down | 2500 | Medium close |
| Left | 3000 | Medium far |
| Up | 3500 | Far shot |

---

## Intake

**File:** `src/main/java/frc/robot/subsystems/Intake.java`

**Hardware:**
- 2x SparkMax + NEO (intake arms) — CAN IDs 16 (left), 17 (right)
- 1x SparkMax + NEO (intake wheel) — CAN ID 4

### Test on the Physical Robot

1. Enable in **Teleop**
2. The intake is bound to the driver controller via `IntakeControl` (default command on the `Intake` subsystem)
3. Use the designated intake button — the arms should deploy down and the wheel should spin to pull in game pieces
4. Release — arms should retract and wheel should stop

**Pass Criteria:**
- Both arms deploy and retract together (no one-arm-only movement)
- Intake wheel spins and draws >= 2A current (viewable on SmartDashboard)
- Arms reach full extension without grinding against frame
- Retraction is clean — no bouncing or oscillation

**Failure Modes:**
- Arms don't move → check CAN IDs 16 and 17
- Arms move unevenly → one arm motor may be disconnected or inverted
- Wheel doesn't spin → check CAN ID 4
- Arms slam down hard → PID needs tuning (`Constants.Intake.kP = 0.025`)
- Arms won't fully retract → encoder may need reset, or mechanical interference

---

## Conveyor & Spindexer

**File:** `src/main/java/frc/robot/subsystems/Conveyor.java`

**Hardware:**
- 1x SparkMax + Brushed motor (conveyor) — CAN ID 21
- 1x SparkMax + NEO (spindexer) — CAN ID 18

### Test on the Physical Robot

1. Enable in **Teleop**
2. Conveyor is controlled via `ConveyorControl` (default command on `Conveyor` subsystem)
3. Activate conveyor — belt should move game pieces upward toward the flywheel
4. Verify the spindexer rotates to index game pieces

**Pass Criteria:**
- Conveyor belt moves smoothly in the correct direction
- Spindexer rotates without jamming
- No game pieces get stuck at the transition between conveyor and flywheel

**Failure Modes:**
- Conveyor doesn't move → check CAN ID 21, note this is a **brushed** motor (not NEO)
- Spindexer jams → game piece stuck, manually clear and retest
- Conveyor runs but game piece doesn't advance → belt tension too loose

---

## Climber

**File:** `src/main/java/frc/robot/subsystems/Climber.java`

**Hardware:** 1x SparkMax + NEO — CAN ID 11

### Test on the Physical Robot

1. Robot on blocks
2. Enable in **Teleop**
3. Activate climber — it should extend or retract based on the control binding
4. Climber uses position control (`Constants.Climber.kP = 0.01`)

**Pass Criteria:**
- Climber extends and retracts on command
- No excessive noise or skipping
- Position holds when stopped (no drift back down)

**Failure Modes:**
- Climber doesn't move → check CAN ID 11
- Climber drifts down → PID holding current is too low, mechanical friction issue
- Grinding noise → check chain/rope for wear

---

## Side Claw

**File:** `src/main/java/frc/robot/subsystems/SideClaw.java`

**Hardware:** 1x SparkMax + NEO — CAN ID 20

### Test on the Physical Robot

1. Enable in **Teleop**
2. Activate claw — it should open/close or extend/retract based on bindings
3. Position control with `Constants.SideClaw.kP = 0.01`

**Pass Criteria:**
- Claw moves to commanded position
- Holds position when stopped
- Doesn't interfere with other mechanisms

**Failure Modes:**
- No movement → check CAN ID 20 (note: was originally ID 18, moved to 20 to avoid conflict with spindexer)
- Erratic movement → check that CAN ID isn't conflicting with spindexer (both were 18 at one point)

---

## Vision — AprilTag Detection

**File:** `src/main/java/frc/robot/subsystems/VisionSubsystem.java`

**Hardware:** 1x Limelight camera (single camera handles everything)

### Important: One Camera, Two Jobs
We use **one Limelight** for both AprilTag tracking and game piece detection. It switches between two pipelines:
- **Pipeline 0** — AprilTag detection (pose estimation, auto-align, scoring)
- **Pipeline 1** — Neural/YOLO game piece detection

**It can only do one at a time.** When you're auto-aligning or scoring (Pipeline 0), the robot cannot see game pieces. When you're driving to a game piece (Pipeline 1), the robot cannot see AprilTags. The code switches automatically — `setAprilTagPipeline()` and `setNeuralPipeline()` in `VisionSubsystem.java` — but be aware of this during testing. If auto-align isn't working, check that the pipeline hasn't been left on Neural mode (and vice versa).

### How It Works
The Limelight detects AprilTags on the field and publishes the robot's position via NetworkTables. The robot reads the `botpose_orb_wpiblue` key (MegaTag2 orientation-robust pose) and fuses it into the swerve odometry.

### Filtered AprilTag IDs
The robot only trusts these specific tag IDs for pose estimation: **{2, 5, 10, 18, 21, 26}** — these are the 2026 REBUILT hub scoring targets.

### Test Steps

1. **Print AprilTags:** Print at least 2-3 AprilTags from the 2026 FRC AprilTag family. Use IDs from the filtered set: **2, 5, 10, 18, 21, or 26**. Print them at the correct size (6.5 inches / 165mm for standard FRC tags).

2. **Place tags:** Tape tags to a wall at approximately hub height. Place them where you can see them from 1-4 meters away.

3. **Verify Limelight is on:**
   - Open a web browser and go to `http://limelight.local:5801`
   - You should see the Limelight web interface with a camera feed
   - Make sure Pipeline 0 (AprilTag) is selected

4. **Check NetworkTables:**
   - Open AdvantageScope or OutlineViewer
   - Navigate to the `limelight` table
   - Look for `botpose_orb_wpiblue` — it should be a double array with 11 values
   - Values should update when the camera can see a tag

5. **Verify quality gates in code (`VisionSubsystem.java`):**
   - `kMinTagCount = 1` — at least 1 tag must be visible
   - `kMaxLatencyMs = 50.0` — total latency must be under 50ms
   - `kMaxTagDistM = 4.0` — tag must be within 4 meters
   - Check `Diagnostic/Vision` on SmartDashboard after running pit diagnostic

6. **Check pose fusion:**
   - Open AdvantageScope field view
   - Drive the robot around within view of the tags
   - The robot's estimated position should snap to the correct location when tags are visible
   - When tags are NOT visible, odometry should still track (but will drift over time)

**Pass Criteria:**
- `botpose_orb_wpiblue` updates at >= 20 Hz when tags are visible
- Latency value (index 6) is under 50ms
- Tag count (index 7) shows correct number of visible tags
- Pose estimate on AdvantageScope matches actual robot position within ~10cm

**Failure Modes:**
- All zeros in botpose → Limelight isn't detecting any tags, check pipeline setting
- Latency > 50ms → Limelight is overloaded, check USB bandwidth or reduce resolution
- Position jumps randomly → tag might be at wrong height, or tag ID isn't in the filtered set
- No NetworkTable data at all → Limelight not connected, check ethernet cable

---

## Vision — Auto-Align

**File:** `src/main/java/frc/robot/commands/AutoAlignCommand.java`

**What it does:** While you hold the **right bumper**, the robot automatically rotates to face the nearest AprilTag scoring target. You keep full translation control (left stick).

### Test Steps

1. Place an AprilTag on a wall (use a filtered ID: 2, 5, 10, 18, 21, or 26)
2. Position the robot 2-3 meters from the tag, facing slightly off-center
3. Enable in **Teleop**
4. Hold **Right Bumper** on the driver controller
5. The robot should rotate to center the tag in the Limelight's view
6. While holding right bumper, drive left/right with the left stick — the robot should track the tag while translating

**Pass Criteria:**
- Robot rotates smoothly toward the tag (P-controller, `kP = 0.05`)
- No oscillation around the target (robot doesn't jitter back and forth)
- Driver can still translate freely while auto-aiming
- LEDs blink during alignment (`AnimationType.ALIGNING_BLINK`)
- Releasing right bumper immediately returns to normal manual control

**Failure Modes:**
- Robot doesn't rotate → Limelight can't see a tag, check `vision.hasTarget()`
- Robot oscillates → P-gain too high, but don't change `kP` without a mentor
- Robot rotates the wrong direction → check `kRotationSign = -1.0` in `AutoAlignCommand.java`

### Quick Vision Check Button
Press **B button** — LEDs will blink blue if a target is visible, red if not. This is a fast way to check if the Limelight sees a tag without needing SmartDashboard.

---

## Vision — Auto Score

**Files:**
- `src/main/java/frc/robot/commands/AutoScoreCommand.java`
- `src/main/java/frc/robot/commands/OneButtonScoreCommand.java`

**What it does:** Press **X button** and the robot runs the full scoring pipeline automatically:
1. Align to AprilTag
2. Wait for vision confirmation (0.25s steady lock)
3. Spin up flywheel
4. Feed game piece
5. Cooldown
6. Done (6 second total timeout)

### Test Steps

1. Place a tag on the wall at scoring distance (1-3 meters)
2. Load a game piece into the robot
3. Enable in **Teleop**
4. Face the robot roughly toward the tag
5. Press **X button** once
6. Watch the sequence: robot should align → flywheel spins up → game piece launches → robot stops

**Pass Criteria:**
- Full sequence completes in under 6 seconds
- Robot aligns to tag before firing
- Flywheel reaches target RPM (2800) before feeding
- Game piece exits cleanly
- Robot returns to normal control after scoring

**Failure Modes:**
- Sequence times out (6s) without scoring → tag not visible or flywheel not reaching RPM
- Scores without aligning → vision confirmation bypassed, check `kVisionConfirmSeconds`
- Game piece doesn't exit → conveyor feed issue, check feed wheel CAN IDs 15 and 2

### Distance-Predicted RPM (FlywheelAutoFeed)

**File:** `src/main/java/frc/robot/commands/flywheel/FlywheelAutoFeed.java`

When using the left trigger for manual aim + auto-feed, the robot predicts RPM based on AprilTag distance:

| Distance from Tag | Flywheel RPM |
|-------------------|-------------|
| <= 1.125m | 2500 |
| <= 1.714m | 3000 |
| > 1.714m | 3500 |

Test at each distance to verify shots land consistently.

---

## Vision — Game Piece Detection

**File:** `src/main/java/frc/robot/subsystems/FuelDetectionConsumer.java`

**What it does:** The Limelight runs a YOLOv11n neural network (Pipeline 1) to detect game pieces on the field. Data comes through the `llpython` NetworkTables key.

### Test Steps

1. Place a game piece on the floor 1-3 meters from the robot
2. Open `http://limelight.local:5801` and switch to Pipeline 1 (Neural)
3. Verify the camera feed shows a bounding box around the game piece
4. Check NetworkTables: `limelight/llpython` should show detection data

### Drive-To-Game-Piece Test

**File:** `src/main/java/frc/robot/commands/DriveToGamePieceCommand.java`

1. Place a game piece on the floor
2. Enable in **Teleop**
3. Hold **Left Bumper** — the robot should drive toward the nearest detected game piece
4. Robot uses P-controller (`kP = 0.4`) on the distance to the game piece
5. You retain rotation control via the right stick

**Pass Criteria:**
- Robot drives toward the game piece smoothly
- Stops near the game piece (intake range)
- Doesn't overshoot or oscillate

**Failure Modes:**
- Robot doesn't move → Pipeline isn't switched to Neural, or no detection
- Robot drives the wrong direction → detection coordinates may be inverted
- Robot oscillates → game piece rolling, or P-gain causing overshoot

---

## Autonomous Modes

**Files:**
- `src/main/java/frc/robot/commands/ChoreoAutoCommand.java`
- `src/main/java/frc/robot/commands/FullAutonomousCommand.java`
- `src/main/java/frc/robot/commands/AutonomousFallbackCommand.java`
- `src/main/java/frc/robot/autos/AutonomousStrategy.java`

### Selecting an Auto Mode
1. Open SmartDashboard or Shuffleboard
2. Find the auto chooser dropdown (labeled `SendableChooser`)
3. Select your desired autonomous mode
4. Switch to **Autonomous** mode in Driver Station

### Available Modes

| Mode | What It Does | When To Use |
|------|-------------|-------------|
| **Leave Only** (default) | Drives forward using Choreo trajectory `leaveStart.traj` | Minimum — just cross the auto line |
| **Leave Only (Raw)** | Waits 3s then drives forward at 1 m/s for 2s | Backup if Choreo trajectories aren't working |
| **Shoot Only** | Aims at AprilTag + fires flywheel for 19 seconds | When positioned at scoring distance |
| **Score + Leave** | Shoots preloaded game piece, then drives out | Standard safe auto |
| **2 Coral** | Scores preload, drives to station, cycles back to score again | Higher-risk, higher-reward |
| **3 Coral** | Three-piece auto — preload + 2 pickups | Requires reliable vision and intake |
| **Full Autonomous** | AI strategy engine with A* pathfinding, dynamic avoidance | Competition-level, requires extensive testing |

### Testing Each Mode

**Safe testing order — start simple:**

1. **Leave Only (Raw)** first — this doesn't use Choreo or vision, just drives straight. If this doesn't work, you have a swerve issue.
2. **Leave Only** — tests Choreo trajectory following. If this fails but Raw works, the issue is trajectory files.
3. **Shoot Only** — tests vision + flywheel integration. Requires AprilTag visible.
4. **Score + Leave** — combines shooting and driving.
5. **2 Coral / 3 Coral** — complex autos. Test only after simpler modes work.
6. **Full Autonomous** — competition mode. Test last.

**For each test:**
1. Reset the robot to the correct starting position
2. Make sure the auto chooser shows the correct mode
3. Switch to **Autonomous** mode
4. Enable — stand clear, watch for unexpected movement
5. After the auto finishes (or after 15 seconds), disable

**Pass Criteria:**
- Robot executes the expected path
- Doesn't collide with field elements
- Scores game pieces if the mode includes scoring
- Returns to a predictable position

**Safe Mode Fallback:** If vision fails during Full Autonomous, the code falls back to `AutonomousFallbackCommand` — drives 2 meters forward and shoots at 2800 RPM using dead reckoning (no vision needed).

---

## LED Indicators

**File:** `src/main/java/frc/robot/subsystems/LEDs.java`

**Hardware:** 60-LED strip on PWM port 0

| Animation | Meaning |
|-----------|---------|
| Idle (enabled) | Robot is enabled, no active command |
| Blue blink | Auto-aligning to AprilTag |
| Green flash | Diagnostic all passed / game piece acquired |
| Yellow flash | Diagnostic warnings |
| Red flash | Diagnostic failure / error |

Priority system: higher-priority animations override lower ones (`kPriorityIdle=0` < `kPriorityDriving=1` < `kPriorityAligning=2` < `kPriorityAlert=3`).

---

## CAN Bus Reference

Every motor controller on the robot. If a mechanism doesn't work, check this list first.

| CAN ID | Device | Motor | Subsystem |
|--------|--------|-------|-----------|
| 2 | SparkMax | NEO | Flywheel feed (back) |
| 4 | SparkMax | NEO | Intake wheel |
| 5 | SparkMax | NEO | Swerve FL steer |
| 6 | SparkMax | NEO | Swerve BL steer |
| 7 | SparkMax | NEO | Swerve FR drive |
| 8 | SparkMax | NEO | Swerve BR drive |
| 10 | SparkMax | NEO | Swerve BL drive |
| 11 | SparkMax | NEO | Climber |
| 12 | SparkMax | NEO | Swerve BR steer |
| 13 | SparkMax | NEO | Swerve FL drive |
| 15 | SparkMax | NEO | Flywheel feed (front) |
| 16 | SparkMax | NEO | Intake arm (left) |
| 17 | SparkMax | NEO | Intake arm (right) |
| 18 | SparkMax | NEO | Spindexer |
| 19 | SparkMax | NEO | Swerve FR steer |
| 20 | SparkMax | NEO | Side claw |
| 21 | SparkMax | Brushed | Conveyor |
| 22 | SparkFlex | NEO Vortex | Flywheel main (right, follower) |
| 23 | SparkFlex | NEO Vortex | Flywheel main (left) |

**IMU:** ADIS16470 on SPI port 0 (not CAN)

**Total devices on CAN bus:** 17 motor controllers

### CAN Health Check
1. Run the Pit Crew Diagnostic (Test mode) — Step 1 checks CAN utilization
2. Open REV Hardware Client on a USB-connected laptop
3. Scan the CAN bus — you should see all 17 devices
4. Any missing device = bad connection (check wiring at that CAN ID)

---

## Controller Map

### Driver Controller (Xbox, Port 0)

| Input | Action |
|-------|--------|
| Left Stick | Drive (translation) |
| Right Stick X | Rotate |
| A | Zero gyro |
| B | Vision check (LEDs blink blue=target, red=no target) |
| X | Auto score sequence |
| Y (hold) | Lock wheels in X pattern |
| Right Bumper (hold) | Auto-align to AprilTag (driver keeps translation) |
| Left Bumper (hold) | Drive to nearest game piece |
| Left Trigger (> 50%) | Manual aim + auto-feed |
| D-Pad Right | Flywheel 2400 RPM |
| D-Pad Down | Flywheel 2500 RPM |
| D-Pad Left | Flywheel 3000 RPM |
| D-Pad Up | Flywheel 3500 RPM |
| Back + Start | Reset to practice start pose (sim only) |

### Operator Controller (Xbox, Port 1)
Intake, conveyor, climber, and claw controls — bound via default commands in `RobotContainer.java`.

---

## Troubleshooting

### Robot Won't Enable
- Check battery voltage (must be > 8V for RoboRIO to function)
- Check main breaker is on
- Verify Driver Station shows green for Comms and Robot Code
- If Robot Code is red → code needs to be deployed (`./gradlew deploy` from a laptop connected via USB)

### One Swerve Module Isn't Working
1. Identify which module by the CAN ID (see CAN Bus Reference above)
2. Check the CAN cable at that motor controller — push connectors firmly
3. Open REV Hardware Client and scan — is the device visible?
4. If visible but not responding → try power cycling the robot
5. If not visible → wiring fault between that controller and the CAN chain

### Vision Isn't Working
1. Is the Limelight powered? (check for the green LED on the camera)
2. Can you reach `http://limelight.local:5801`?
3. Is the correct pipeline selected? (Pipeline 0 = AprilTag, Pipeline 1 = Neural/YOLO)
4. Are AprilTags in view and at the correct size?
5. Check `limelight/botpose_orb_wpiblue` in NetworkTables — is it updating?

### Flywheel Doesn't Reach Target RPM
1. Check belt tension between motors and flywheel wheels
2. Verify both SparkFlex controllers (CAN 22, 23) are visible in REV Hardware Client
3. Check SmartDashboard for actual RPM vs target RPM
4. If RPM oscillates wildly → PID issue, report to programming mentor

### Robot Drives in Wrong Direction
1. Press **A** to re-zero the gyro (face robot away from you first)
2. If still wrong → encoder offsets may be bad, run Encoder Calibration
3. Check that the robot wasn't turned on upside-down or sideways (gyro calibrates on boot)

### Auto Mode Does Nothing
1. Verify the correct auto is selected in the SmartDashboard chooser
2. Make sure you're in **Autonomous** mode, not Teleop
3. For Choreo-based autos, verify `.traj` files exist in `src/main/deploy/choreo/`
4. Try "Leave Only (Raw)" first — if that works, the issue is with Choreo or vision, not swerve

---

## Key File Quick Reference

| What You're Looking For | File Path |
|------------------------|-----------|
| All CAN IDs and constants | `src/main/java/frc/robot/Constants.java` |
| Button bindings | `src/main/java/frc/robot/RobotContainer.java` |
| Swerve drive logic | `src/main/java/frc/robot/subsystems/SwerveSubsystem.java` |
| Swerve JSON configs | `src/main/deploy/swerve/*.json` |
| Vision (Limelight) | `src/main/java/frc/robot/subsystems/VisionSubsystem.java` |
| Limelight helper functions | `src/main/java/frc/robot/Helper.java` |
| Auto-align command | `src/main/java/frc/robot/commands/AutoAlignCommand.java` |
| Auto score (one-button) | `src/main/java/frc/robot/commands/OneButtonScoreCommand.java` |
| Auto score (sequential) | `src/main/java/frc/robot/commands/AutoScoreCommand.java` |
| Game piece detection | `src/main/java/frc/robot/subsystems/FuelDetectionConsumer.java` |
| Drive to game piece | `src/main/java/frc/robot/commands/DriveToGamePieceCommand.java` |
| Flywheel subsystem | `src/main/java/frc/robot/subsystems/Flywheel.java` |
| Pit crew diagnostic | `src/main/java/frc/robot/commands/PitCrewDiagnosticCommand.java` |
| Encoder calibration | `src/main/java/frc/robot/commands/EncoderCalibrationCommand.java` |
| Choreo auto paths | `src/main/deploy/choreo/*.traj` |
| Autonomous strategy | `src/main/java/frc/robot/autos/AutonomousStrategy.java` |
| Full autonomous | `src/main/java/frc/robot/commands/FullAutonomousCommand.java` |
| State machine | `src/main/java/frc/robot/subsystems/SuperstructureStateMachine.java` |
