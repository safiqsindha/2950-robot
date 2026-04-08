# 2950-robot

FRC Team 2950 **The Devastators** — Robot Code (2026 Season: REBUILT)

## Stack

- **Language:** Java 17
- **Framework:** WPILib 2025.2.1
- **Swerve:** YAGSL (Yet Another Generic Swerve Library)
- **Autos:** PathPlanner + Choreo
- **Vision:** PhotonVision + Limelight
- **Motor Controllers:** CTRE Phoenix 6 (Kraken X60) + REV (NEO)
- **Build:** Gradle

## Structure

```
src/main/java/frc/
├── robot/
│   ├── Robot.java              ← Main robot lifecycle
│   ├── RobotContainer.java     ← Subsystem + command bindings
│   ├── Constants.java          ← All constants
│   ├── subsystems/             ← Drivetrain, elevator, intake, etc.
│   ├── commands/               ← Autonomous + teleop commands
│   └── autos/                  ← Auto routines
├── lib/                        ← Shared utilities
│   └── pathfinding/
deploy/
├── swerve/                     ← YAGSL JSON configs
├── choreo/                     ← Choreo auto paths
└── pathplanner/                ← PathPlanner paths
```

## Build & Deploy

```bash
./gradlew build          # Build + test
./gradlew deploy         # Deploy to robot
./gradlew simulateJava   # Run in simulation
```

## Related

This repo resets each season. Cross-season intelligence, design tools, and scouting systems live in [TheEngine](https://github.com/safiqsindha/TheEngine).
