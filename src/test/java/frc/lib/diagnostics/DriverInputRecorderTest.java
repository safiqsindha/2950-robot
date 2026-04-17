package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DriverInputRecorderTest {

  @Test
  void periodic_noAxes_doesNotThrow() {
    DriverInputRecorder r = DriverInputRecorder.of();
    assertDoesNotThrow(r::periodic);
  }

  @Test
  void periodic_multipleAxes_doesNotThrow() {
    double[] leftY = {0.0};
    double[] rightX = {0.0};
    DriverInputRecorder r =
        DriverInputRecorder.of(
            new DriverInputRecorder.Axis("LeftY", () -> leftY[0]),
            new DriverInputRecorder.Axis("RightX", () -> rightX[0]));
    leftY[0] = 0.5;
    rightX[0] = -0.25;
    assertDoesNotThrow(r::periodic);
  }
}
