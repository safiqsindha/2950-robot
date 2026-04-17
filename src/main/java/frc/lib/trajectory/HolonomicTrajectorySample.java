package frc.lib.trajectory;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;

/**
 * Immutable snapshot of a {@link HolonomicTrajectory} at a single instant — the minimum a
 * follower needs to close the loop (current setpoint pose + feed-forward chassis speeds).
 *
 * <p>Time {@code timestampSeconds} is measured from the start of the trajectory (t=0 is the
 * initial sample). Chassis speeds are field-relative for consistency with {@link
 * edu.wpi.first.math.kinematics.ChassisSpeeds#fromFieldRelativeSpeeds}.
 *
 * @param timestampSeconds time since trajectory start, in seconds
 * @param pose expected robot pose at this instant
 * @param fieldRelativeSpeeds feed-forward chassis velocity at this instant, field-relative
 */
public record HolonomicTrajectorySample(
    double timestampSeconds, Pose2d pose, ChassisSpeeds fieldRelativeSpeeds) {}
