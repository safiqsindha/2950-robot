#!/usr/bin/env python3
"""
RPM curve-fit helper — recompute Helper.rpmFromMeters() calibration.

Flywheel-velocity-vs-distance is currently a 3-point Lagrange quadratic (see
`frc.robot.Helper.rpmFromMeters`). When students add more calibration data
points from the practice bot, this script:

    1. Reads (distance_m, target_rpm) pairs from stdin or a CSV.
    2. Fits a Lagrange quadratic through the 3 user-selected anchor points
       (bracketing the full shooting range is recommended).
    3. Plots the raw points + the fitted curve so you can eyeball whether
       the curve is physically plausible (monotonic-increasing, concave-down
       in the mid range).
    4. Prints Java constants you can drop into Helper.java.

No matplotlib? It degrades to an ASCII plot so students without a full
Python/plotting environment can still use the tool.

Usage:

    python3 tools/rpm_curve_fit.py path/to/calibration.csv
    # or stdin:
    python3 tools/rpm_curve_fit.py - <<EOF
    1.125,2500
    1.714,3000
    2.500,3500
    3.200,3900
    EOF

The script picks the 3 anchor points that best span the observed range:
  - shortest distance
  - longest distance
  - median distance
This matches the current Lagrange layout (short/mid/long).
"""

from __future__ import annotations

import csv
import statistics
import sys
from pathlib import Path


def read_points(source: str) -> list[tuple[float, float]]:
    """Read (distance_m, rpm) pairs from a CSV file or stdin ('-').

    Accepts any file with 2 numeric columns; header row optional. Non-numeric
    rows are skipped silently so students can annotate their CSV with notes.
    """
    points: list[tuple[float, float]] = []
    if source == "-":
        reader = csv.reader(sys.stdin)
    else:
        fp = Path(source)
        if not fp.exists():
            print(f"error: file not found: {source}", file=sys.stderr)
            sys.exit(2)
        reader = csv.reader(fp.open())

    for row in reader:
        if len(row) < 2:
            continue
        try:
            meters = float(row[0])
            rpm = float(row[1])
        except ValueError:
            continue
        points.append((meters, rpm))

    points.sort()
    return points


def pick_anchors(points: list[tuple[float, float]]) -> list[tuple[float, float]]:
    """Pick 3 anchors — shortest, median, longest — for the Lagrange fit."""
    if len(points) < 3:
        print(
            "error: need at least 3 calibration points (got "
            f"{len(points)}); Lagrange quadratic is exactly 3-point.",
            file=sys.stderr,
        )
        sys.exit(2)
    xs = [p[0] for p in points]
    median_x = statistics.median(xs)
    # Pick the calibration point whose x is nearest the median.
    median_point = min(points, key=lambda p: abs(p[0] - median_x))
    return [points[0], median_point, points[-1]]


def lagrange_eval(
    x: float, anchors: list[tuple[float, float]]
) -> float:
    """Evaluate the 3-point Lagrange polynomial at x."""
    (x1, y1), (x2, y2), (x3, y3) = anchors
    l1 = (x - x2) * (x - x3) / ((x1 - x2) * (x1 - x3))
    l2 = (x - x1) * (x - x3) / ((x2 - x1) * (x2 - x3))
    l3 = (x - x1) * (x - x2) / ((x3 - x1) * (x3 - x2))
    return y1 * l1 + y2 * l2 + y3 * l3


def emit_java_constants(anchors: list[tuple[float, float]]) -> str:
    (x1, y1), (x2, y2), (x3, y3) = anchors
    return (
        "// Replace the calibration block in Helper.rpmFromMeters() with:\n"
        f"final double x1 = {x1:.3f}, y1 = {y1:.1f};\n"
        f"final double x2 = {x2:.3f}, y2 = {y2:.1f};\n"
        f"final double x3 = {x3:.3f}, y3 = {y3:.1f};\n"
    )


def residuals(
    points: list[tuple[float, float]],
    anchors: list[tuple[float, float]],
) -> list[tuple[float, float, float]]:
    """Return (x, observed_rpm, fit_rpm - observed_rpm) per point."""
    out = []
    for x, y in points:
        fit = lagrange_eval(x, anchors)
        out.append((x, y, fit - y))
    return out


def ascii_plot(
    points: list[tuple[float, float]],
    anchors: list[tuple[float, float]],
    width: int = 60,
    height: int = 16,
) -> str:
    """Render the points + fit curve to a monospaced ASCII chart.

    Used as a fallback when matplotlib isn't available. Coarse — this is a
    sanity check, not a publication figure.
    """
    xs_data = [p[0] for p in points]
    ys_data = [p[1] for p in points]
    x_min, x_max = min(xs_data), max(xs_data)
    # Sample fit curve densely across the range.
    fit_points = [
        (x_min + i * (x_max - x_min) / (width - 1),
         lagrange_eval(x_min + i * (x_max - x_min) / (width - 1), anchors))
        for i in range(width)
    ]
    y_min = min(ys_data + [p[1] for p in fit_points])
    y_max = max(ys_data + [p[1] for p in fit_points])

    grid = [[" "] * width for _ in range(height)]

    def to_row(y: float) -> int:
        frac = (y - y_min) / (y_max - y_min) if y_max > y_min else 0.5
        row = int((1.0 - frac) * (height - 1))
        return max(0, min(height - 1, row))

    def to_col(x: float) -> int:
        frac = (x - x_min) / (x_max - x_min) if x_max > x_min else 0.5
        col = int(frac * (width - 1))
        return max(0, min(width - 1, col))

    # Fit curve
    for x, y in fit_points:
        grid[to_row(y)][to_col(x)] = "."
    # Raw points (overlay)
    for x, y in points:
        grid[to_row(y)][to_col(x)] = "o"

    header = f"rpm {y_max:6.0f} ┐"
    footer = f"    {y_min:6.0f} ┘  {x_min:.2f}m ─→ {x_max:.2f}m"
    body = "\n".join("            │" + "".join(row) for row in grid)
    return f"{header}\n{body}\n{footer}"


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2

    source = sys.argv[1]
    points = read_points(source)
    if not points:
        print("error: no usable calibration rows found", file=sys.stderr)
        return 2

    anchors = pick_anchors(points)
    resid = residuals(points, anchors)
    max_abs_resid = max(abs(r[2]) for r in resid)

    print(f"Read {len(points)} calibration points.")
    print(f"Anchors chosen (short / mid / long): {anchors}")
    print(f"Max |residual| across all points: {max_abs_resid:.1f} RPM")
    print()
    print("Residuals:")
    for x, y, r in resid:
        tag = "  anchor" if (x, y) in anchors else ""
        print(f"  {x:5.3f} m  obs={y:5.0f}  fit−obs={r:+6.1f}{tag}")
    print()
    print(emit_java_constants(anchors))
    print("Chart (o = calibration point, . = fit curve):")
    try:
        import matplotlib  # type: ignore  # noqa: F401
    except ImportError:
        print(ascii_plot(points, anchors))
    else:
        # Nicer plot if matplotlib is available.
        try:
            import matplotlib.pyplot as plt  # type: ignore

            xs = [p[0] for p in points]
            ys = [p[1] for p in points]
            x_min, x_max = min(xs), max(xs)
            dense_x = [x_min + i * (x_max - x_min) / 199 for i in range(200)]
            dense_y = [lagrange_eval(x, anchors) for x in dense_x]
            plt.plot(dense_x, dense_y, label="Lagrange fit")
            plt.scatter(xs, ys, label="calibration points")
            for (ax, ay) in anchors:
                plt.scatter([ax], [ay], marker="x", s=120, label="anchor")
            plt.xlabel("distance (m)")
            plt.ylabel("RPM")
            plt.title("Flywheel RPM vs distance")
            plt.legend()
            plt.grid(True)
            out_path = Path("tools/rpm_curve_fit.png")
            plt.savefig(out_path, dpi=100)
            print(f"(matplotlib) figure saved to {out_path}")
        except Exception as e:  # pragma: no cover - best-effort
            print(f"matplotlib failed ({e}); ASCII fallback:")
            print(ascii_plot(points, anchors))

    return 0


if __name__ == "__main__":
    sys.exit(main())
