package frc.robot.autos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.littletonrobotics.junction.Logger;

/**
 * Practice-session helper that rotates through registered auto routines deterministically. Pairs
 * with {@link LoggedAutoChooser#selectByName(String)} to iterate routines without always
 * defaulting to the student's favourite.
 *
 * <p>Two modes:
 *
 * <ul>
 *   <li>{@link #advance()} — advances to the next routine in the shuffle order, wrapping around.
 *   <li>{@link #randomize(long)} — reshuffles the list with a given seed. Use the current epoch
 *       seconds for genuine practice randomness, or a fixed seed for reproducible sim runs.
 * </ul>
 *
 * <p>Pure Java; HAL-free. Tests drive it with any seed they want.
 */
public final class RandomAutoRotator {

  private final LoggedAutoChooser chooser;
  private final List<String> options;
  private int cursor = 0;

  /**
   * @param chooser the chooser being rotated
   * @param optionNames the routine names to cycle through (must all be registered on chooser)
   */
  public RandomAutoRotator(LoggedAutoChooser chooser, List<String> optionNames) {
    this.chooser = chooser;
    this.options = new ArrayList<>(optionNames);
  }

  /** Reshuffle the rotation order. Deterministic given the seed. */
  public void randomize(long seed) {
    Collections.shuffle(options, new Random(seed));
    cursor = 0;
    Logger.recordOutput("Auto/Rotator/Seed", seed);
    Logger.recordOutput("Auto/Rotator/Order", options.toArray(new String[0]));
  }

  /**
   * Advance to the next routine in the rotation order and push it to the chooser. Wraps around
   * at the end of the list.
   *
   * @return the name of the routine now selected, or empty if the rotator has no options
   */
  public String advance() {
    if (options.isEmpty()) {
      return "";
    }
    String name = options.get(cursor);
    chooser.selectByName(name);
    cursor = (cursor + 1) % options.size();
    Logger.recordOutput("Auto/Rotator/Current", name);
    Logger.recordOutput("Auto/Rotator/Cursor", cursor);
    return name;
  }

  /** @return the current rotation order (live view — do not modify). Package-private for tests. */
  List<String> currentOrder() {
    return Collections.unmodifiableList(options);
  }

  /** @return the current cursor index. Package-private for tests. */
  int currentCursor() {
    return cursor;
  }
}
