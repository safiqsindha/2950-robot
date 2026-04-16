package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.filter.LinearFilter;
import java.util.List;
import limelight.Limelight;
import limelight.results.RawFiducial;

/**
 * Utility methods for Limelight integration and RPM-distance lookup. Migrated from the
 * confirmed-working swerve-test branch.
 */
public class Helper {

  private static Limelight ll;

  private static Limelight getLl() {
    if (ll == null) ll = new Limelight("limelight");
    return ll;
  }

  private static final LinearFilter distFilter = LinearFilter.singlePoleIIR(0.1, 0.02);
  private static final LinearFilter aimFilter = LinearFilter.singlePoleIIR(0.1, 0.02);

  private Helper() {}

  /**
   * Calculate flywheel RPM setpoint from distance to hub using piecewise-linear interpolation.
   * Calibrated at three measured distances.
   *
   * @param meters distance to the hub in meters
   * @return target RPM clamped to [kMinRpm, kMaxRpm]
   */
  public static double rpmFromMeters(double meters) {
    double x1 = 1.125, y1 = 2500.0;
    double x2 = 1.714, y2 = 3000.0;
    double x3 = 2.5, y3 = 3500.0;

    double rpmGuess;
    if (meters > x2) {
      rpmGuess = y2 + (meters - x2) * (y3 - y2) / (x3 - x2);
    } else {
      rpmGuess = y1 + (meters - x1) * (y2 - y1) / (x2 - x1);
    }
    return MathUtil.clamp(rpmGuess, Constants.Flywheel.kMinRpm, Constants.Flywheel.kMaxRpm);
  }

  /** Configure Limelight to filter for the relevant AprilTag IDs for 2026 REBUILT hub targets. */
  public static void llSetup() {
    getLl().getSettings().withAprilTagIdFilter(List.of(2, 5, 10, 18, 21, 26)).save();
  }

  /** Push latest Limelight fiducial data through the IIR filters. Call every 20ms cycle. */
  public static void updateFilters() {
    // Skip Limelight calls in simulation — no hardware, and getData() blocks for ~2s
    if (edu.wpi.first.wpilibj.RobotBase.isSimulation()) {
      return;
    }
    RawFiducial[] raw = getLl().getData().getRawFiducials();
    for (RawFiducial object : raw) {
      distFilter.calculate(object.distToCamera);
      aimFilter.calculate(object.txnc);
    }
  }

  /** Get the filtered distance to the nearest tracked AprilTag hub target. */
  public static double getAprilTagDist() {
    return distFilter.lastValue();
  }

  /** Get the filtered horizontal offset (tx) to the nearest tracked AprilTag hub target. */
  public static double getAprilTagAim() {
    return aimFilter.lastValue();
  }

  /** Reset IIR filters and feed one zero sample to avoid stale state. */
  public static void resetFilters() {
    aimFilter.reset();
    distFilter.reset();
    distFilter.calculate(0);
    aimFilter.calculate(0);
  }
}
