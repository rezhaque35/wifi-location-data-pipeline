package com.wifi.positioning.algorithm.selection.factor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;

/**
 * Test class for algorithm selection factor enums. Verifies the functionality of each factor class
 * after refactoring.
 */
public class AlgorithmFactorTest {

  @Test
  public void testAPCountFactor() {
    // Test fromCount method
    assertEquals(APCountFactor.SINGLE_AP, APCountFactor.fromCount(1));
    assertEquals(APCountFactor.TWO_APS, APCountFactor.fromCount(2));
    assertEquals(APCountFactor.THREE_APS, APCountFactor.fromCount(3));
    assertEquals(APCountFactor.FOUR_PLUS_APS, APCountFactor.fromCount(4));
    assertEquals(APCountFactor.FOUR_PLUS_APS, APCountFactor.fromCount(5));

    // Test getMinimumCount method
    assertEquals(1, APCountFactor.SINGLE_AP.getMinimumCount());
    assertEquals(2, APCountFactor.TWO_APS.getMinimumCount());
    assertEquals(3, APCountFactor.THREE_APS.getMinimumCount());
    assertEquals(4, APCountFactor.FOUR_PLUS_APS.getMinimumCount());
  }

  @Test
  public void testSignalQualityFactor() {
    // Test fromSignalStrength method
    assertEquals(SignalQualityFactor.STRONG_SIGNAL, SignalQualityFactor.fromSignalStrength(-65));
    assertEquals(SignalQualityFactor.MEDIUM_SIGNAL, SignalQualityFactor.fromSignalStrength(-75));
    assertEquals(SignalQualityFactor.WEAK_SIGNAL, SignalQualityFactor.fromSignalStrength(-90));
    assertEquals(
        SignalQualityFactor.VERY_WEAK_SIGNAL, SignalQualityFactor.fromSignalStrength(-100));

    // Test fromWifiScans method
    List<WifiScanResult> strongSignals =
        Arrays.asList(
            WifiScanResult.of("AA:BB:CC:DD:EE:01", -60.0, 2450, "Test1"),
            WifiScanResult.of("AA:BB:CC:DD:EE:02", -65.0, 2450, "Test2"));
    assertEquals(
        SignalQualityFactor.STRONG_SIGNAL, SignalQualityFactor.fromWifiScans(strongSignals));

    List<WifiScanResult> weakSignals =
        Arrays.asList(
            WifiScanResult.of("AA:BB:CC:DD:EE:01", -87.0, 2450, "Test1"),
            WifiScanResult.of("AA:BB:CC:DD:EE:02", -92.0, 2450, "Test2"));
    assertEquals(SignalQualityFactor.WEAK_SIGNAL, SignalQualityFactor.fromWifiScans(weakSignals));
  }

  @Test
  public void testSignalDistributionFactor() {
    // Test fromWifiScans method with uniform signals
    List<WifiScanResult> uniformSignals =
        Arrays.asList(
            WifiScanResult.of("AA:BB:CC:DD:EE:01", -70.0, 2450, "Test1"),
            WifiScanResult.of("AA:BB:CC:DD:EE:02", -71.0, 2450, "Test2"),
            WifiScanResult.of("AA:BB:CC:DD:EE:03", -72.0, 2450, "Test3"));
    assertEquals(
        SignalDistributionFactor.UNIFORM_SIGNALS,
        SignalDistributionFactor.fromWifiScans(uniformSignals));

    // Test with mixed signals
    List<WifiScanResult> mixedSignals =
        Arrays.asList(
            WifiScanResult.of("AA:BB:CC:DD:EE:01", -65.0, 2450, "Test1"),
            WifiScanResult.of("AA:BB:CC:DD:EE:02", -75.0, 2450, "Test2"),
            WifiScanResult.of("AA:BB:CC:DD:EE:03", -80.0, 2450, "Test3"));
    assertEquals(
        SignalDistributionFactor.MIXED_SIGNALS,
        SignalDistributionFactor.fromWifiScans(mixedSignals));
  }

  @Test
  public void testGeometricQualityFactor() {
    // Test fromGDOP method
    assertEquals(GeometricQualityFactor.EXCELLENT_GDOP, GeometricQualityFactor.fromGDOP(1.5));
    assertEquals(GeometricQualityFactor.GOOD_GDOP, GeometricQualityFactor.fromGDOP(3.0));
    assertEquals(GeometricQualityFactor.FAIR_GDOP, GeometricQualityFactor.fromGDOP(5.0));
    assertEquals(GeometricQualityFactor.POOR_GDOP, GeometricQualityFactor.fromGDOP(7.0));

    // Test isCollinear method with collinear points
    List<Position> collinearPositions =
        Arrays.asList(
            new Position(1.0, 1.0, 0.0, 0.0, 0.0),
            new Position(2.0, 2.0, 0.0, 0.0, 0.0),
            new Position(3.0, 3.0, 0.0, 0.0, 0.0));
    assertTrue(GeometricQualityFactor.isCollinear(collinearPositions));
  }
}
