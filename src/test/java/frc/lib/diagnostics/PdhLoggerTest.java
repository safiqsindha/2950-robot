package frc.lib.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * HAL-free tests for {@link PdhLogger}. {@link PdhLogger.Snapshot} is a plain record, so we can
 * build one by hand and assert the logger's passthrough behaviour without instantiating a {@link
 * edu.wpi.first.wpilibj.PowerDistribution} (which loads {@code wpiHaljni}).
 */
class PdhLoggerTest {

  @Test
  void collect_passesVoltageCurrentTemperatureThrough() {
    PdhLogger.Snapshot snap = new PdhLogger.Snapshot(12.3, 45.6, 37.0, new double[] {1.0, 2.0});
    PdhLogger logger = new PdhLogger(() -> snap);
    PdhLogger.Snapshot out = logger.collect();
    assertEquals(12.3, out.voltageV(), 1e-9);
    assertEquals(45.6, out.totalCurrentA(), 1e-9);
    assertEquals(37.0, out.temperatureC(), 1e-9);
  }

  @Test
  void collect_preservesChannelArray() {
    double[] channels = new double[] {0.0, 1.5, 10.0, 22.0};
    PdhLogger.Snapshot snap = new PdhLogger.Snapshot(12.0, 33.5, 35.0, channels);
    PdhLogger logger = new PdhLogger(() -> snap);
    assertArrayEquals(channels, logger.collect().channelCurrentsA(), 1e-9);
  }

  @Test
  void collect_samplesSupplierEachCall() {
    int[] tick = {0};
    PdhLogger logger =
        new PdhLogger(
            () -> {
              tick[0]++;
              return new PdhLogger.Snapshot(12.0, tick[0], 30.0, new double[] {tick[0]});
            });
    assertEquals(1.0, logger.collect().totalCurrentA(), 1e-9);
    assertEquals(2.0, logger.collect().totalCurrentA(), 1e-9);
    assertEquals(3.0, logger.collect().totalCurrentA(), 1e-9);
  }

  @Test
  void periodic_doesNotThrowOnCleanSnapshot() {
    PdhLogger logger = new PdhLogger(() -> new PdhLogger.Snapshot(0.0, 0.0, 0.0, new double[] {}));
    assertDoesNotThrow(logger::periodic);
  }

  @Test
  void periodic_handlesPdhSizedChannelArray() {
    // REV PDH has 24 channels; make sure the logger doesn't care about array length.
    double[] pdh = new double[24];
    for (int i = 0; i < 24; i++) {
      pdh[i] = i * 0.1;
    }
    PdhLogger logger = new PdhLogger(() -> new PdhLogger.Snapshot(12.5, 30.0, 40.0, pdh));
    assertDoesNotThrow(logger::periodic);
  }
}
