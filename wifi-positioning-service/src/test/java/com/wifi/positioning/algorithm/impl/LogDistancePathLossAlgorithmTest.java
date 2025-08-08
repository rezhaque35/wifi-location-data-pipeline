package com.wifi.positioning.algorithm.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;

/**
 * Test suite for the Log-Distance Path Loss Algorithm implementation. These tests verify the
 * algorithm's ability to handle various real-world scenarios and validate its core mathematical
 * model and assumptions.
 */
class LogDistancePathLossAlgorithmTest {

  private LogDistancePathLossAlgorithm algorithm;
  private static final double DELTA = 0.0001;

  @BeforeEach
  void setUp() {
    algorithm = new LogDistancePathLossAlgorithm();
  }

  /**
   * Basic input validation tests ensure the algorithm handles edge cases and invalid inputs
   * gracefully. These tests verify the robustness of the algorithm in production environments where
   * input data quality cannot be guaranteed.
   */
  @Nested
  @DisplayName("Basic Input Validation Tests")
  class InputValidationTests {
    /**
     * Verifies that the algorithm safely handles null WiFi scan data. This scenario can occur when
     * the scanning hardware fails or returns no data.
     */
    @Test
    @DisplayName("should return null when wifiScan is null")
    void shouldReturnNullWhenWifiScanIsNull() {
      assertNull(algorithm.calculatePosition(null, Collections.emptyList()));
    }

    /**
     * Verifies handling of empty scan results. This can happen when no APs are detected in range.
     */
    @Test
    @DisplayName("should return null when wifiScan is empty")
    void shouldReturnNullWhenWifiScanIsEmpty() {
      assertNull(algorithm.calculatePosition(Collections.emptyList(), Collections.emptyList()));
    }

    /**
     * Verifies handling of missing AP reference data. This scenario can occur when the AP database
     * is unavailable or corrupted.
     */
    @Test
    @DisplayName("should return null when knownAPs is null")
    void shouldReturnNullWhenKnownAPsIsNull() {
      List<WifiScanResult> scans =
          Collections.singletonList(
              WifiScanResult.of("00:11:22:33:44:55", -65.0, 2400, "test-ssid"));
      assertNull(algorithm.calculatePosition(scans, null));
    }
  }

  /**
   * Tests for vendor-specific path loss characteristics. These tests verify that the algorithm
   * correctly adjusts its calculations based on vendor-specific AP characteristics and
   * environmental factors.
   */
  @Nested
  @DisplayName("Vendor Information Handling Tests")
  class VendorInformationTests {
    private WifiAccessPoint createAP(String mac, String vendor, double lat, double lon) {
      return WifiAccessPoint.builder()
          .macAddress(mac)
          .vendor(vendor)
          .latitude(lat)
          .longitude(lon)
          .altitude(0.0)
          .horizontalAccuracy(5.0)
          .confidence(0.8)
          .build();
    }

    private WifiScanResult createScan(String mac, double signalStrength) {
      return WifiScanResult.of(mac, signalStrength, 2400, "test-ssid");
    }

    /**
     * Verifies that the algorithm properly utilizes vendor information to adjust path loss
     * calculations and confidence levels. Expected: Higher confidence due to known vendor
     * characteristics.
     */
    @Test
    @DisplayName("should handle known vendor information correctly")
    void shouldHandleKnownVendorCorrectly() {
      // Create APs with known vendor
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("00:11:22:33:44:55", "Cisco", 1.0, 1.0),
              createAP("66:77:88:99:AA:BB", "Cisco", 1.0, 2.0));

      List<WifiScanResult> scans =
          Arrays.asList(createScan("00:11:22:33:44:55", -65), createScan("66:77:88:99:AA:BB", -70));

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);
      assertTrue(position.confidence() > 0.7); // High confidence with known vendors
    }

    /**
     * Verifies degraded performance handling when vendor info is missing. The algorithm should
     * still function but with reduced confidence. Expected: Lower confidence due to unknown
     * environmental characteristics.
     */
    @Test
    @DisplayName("should handle missing vendor information with reduced confidence")
    void shouldHandleMissingVendorWithReducedConfidence() {
      // Create APs without vendor information
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("00:11:22:33:44:55", null, 1.0, 1.0),
              createAP("66:77:88:99:AA:BB", "", 1.0, 2.0));

      List<WifiScanResult> scans =
          Arrays.asList(createScan("00:11:22:33:44:55", -65), createScan("66:77:88:99:AA:BB", -70));

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);
      assertTrue(position.confidence() < 0.8); // Lower confidence without vendor info
    }

    /**
     * Tests algorithm's ability to handle mixed vendor information. Real-world deployments often
     * have APs from multiple vendors. Expected: Balanced confidence based on partial vendor
     * information.
     */
    @Test
    @DisplayName("should handle mixed vendor information appropriately")
    void shouldHandleMixedVendorInformation() {
      // Create APs with mixed vendor information
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("00:11:22:33:44:55", "Cisco", 1.0, 1.0),
              createAP("66:77:88:99:AA:BB", null, 1.0, 2.0),
              createAP("CC:DD:EE:FF:00:11", "Aruba", 1.0, 3.0));

      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("00:11:22:33:44:55", -65),
              createScan("66:77:88:99:AA:BB", -70),
              createScan("CC:DD:EE:FF:00:11", -75));

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);
      assertTrue(position.confidence() > 0.7); // Good confidence with some vendor info
    }
  }

  /**
   * Tests for path loss exponent calculations under different signal conditions. These tests verify
   * the algorithm's ability to adapt to varying signal qualities and propagation environments.
   */
  @Nested
  @DisplayName("Path Loss Exponent Tests")
  class PathLossExponentTests {
    /**
     * Verifies path loss model adaptation to different signal strengths. Tests three scenarios: 1.
     * Strong signals (-45dBm): Expect smaller path loss exponent 2. Medium signals (-65dBm): Expect
     * nominal path loss exponent 3. Weak signals (-85dBm): Expect larger path loss exponent
     * Expected: Increasing position uncertainty with decreasing signal strength
     */
    @Test
    @DisplayName("should use appropriate path loss exponents for different signal strengths")
    void shouldUseAppropriatePathLossExponents() {
      // Test with different signal strengths
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              WifiAccessPoint.builder()
                  .macAddress("00:11:22:33:44:55")
                  .latitude(1.0)
                  .longitude(1.0)
                  .altitude(0.0)
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build());

      // Test strong signal
      List<WifiScanResult> strongSignal =
          Collections.singletonList(
              WifiScanResult.of("00:11:22:33:44:55", -45.0, 2400, "test-ssid"));
      Position strongPosition = algorithm.calculatePosition(strongSignal, knownAPs);
      assertNotNull(strongPosition);

      // Test medium signal
      List<WifiScanResult> mediumSignal =
          Collections.singletonList(
              WifiScanResult.of("00:11:22:33:44:55", -65.0, 2400, "test-ssid"));
      Position mediumPosition = algorithm.calculatePosition(mediumSignal, knownAPs);
      assertNotNull(mediumPosition);

      // Test weak signal
      List<WifiScanResult> weakSignal =
          Collections.singletonList(
              WifiScanResult.of("00:11:22:33:44:55", -85.0, 2400, "test-ssid"));
      Position weakPosition = algorithm.calculatePosition(weakSignal, knownAPs);
      assertNotNull(weakPosition);

      // Verify that distances increase with signal weakness
      assertTrue(weakPosition.accuracy() > mediumPosition.accuracy());
      assertTrue(mediumPosition.accuracy() > strongPosition.accuracy());
    }
  }

  /**
   * Tests for the core position calculation functionality. These tests verify the algorithm's
   * ability to produce accurate position estimates under various real-world conditions.
   */
  @Nested
  @DisplayName("Position Calculation Tests")
  class PositionCalculationTests {
    /**
     * Comprehensive test of position calculation with mixed signal qualities. Tests the algorithm's
     * ability to: 1. Handle multiple APs with different signal strengths 2. Properly weight
     * contributions based on signal quality 3. Account for vendor-specific characteristics
     * Expected: Position estimates within reasonable bounds of actual AP locations
     */
    @Test
    @DisplayName("should calculate reasonable positions with mixed signal qualities")
    void shouldCalculateReasonablePositions() {
      // Create test data
      List<WifiScanResult> wifiScan =
          List.of(
              WifiScanResult.of("00:11:22:33:44:55", -65.0, 2400, "test-ssid"),
              WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 2400, "test-ssid"),
              WifiScanResult.of("11:22:33:44:55:66", -75.0, 2400, "test-ssid"));

      List<WifiAccessPoint> knownAPs =
          List.of(
              WifiAccessPoint.builder()
                  .macAddress("00:11:22:33:44:55")
                  .latitude(40.748817)
                  .longitude(-73.985428)
                  .altitude(100.0)
                  .horizontalAccuracy(10.0)
                  .verticalAccuracy(5.0)
                  .vendor("Vendor1")
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AA:BB:CC:DD:EE:FF")
                  .latitude(40.748192)
                  .longitude(-73.984870)
                  .altitude(105.0)
                  .horizontalAccuracy(8.0)
                  .verticalAccuracy(4.0)
                  .vendor("Vendor2")
                  .confidence(0.9)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("11:22:33:44:55:66")
                  .latitude(40.747500)
                  .longitude(-73.985800)
                  .altitude(95.0)
                  .horizontalAccuracy(12.0)
                  .verticalAccuracy(6.0)
                  .confidence(0.7)
                  .build());

      // Execute algorithm
      Position position = algorithm.calculatePosition(wifiScan, knownAPs);

      // Verify results
      assertNotNull(position);
      assertTrue(
          position.latitude() >= 40.74 && position.latitude() <= 40.75,
          "Latitude should be reasonable");
      assertTrue(
          position.longitude() >= -74.0 && position.longitude() <= -73.9,
          "Longitude should be reasonable");
      assertTrue(
          position.altitude() >= 90.0 && position.altitude() <= 110.0,
          "Altitude should be reasonable");
      assertTrue(
          position.accuracy() >= 10.0 && position.accuracy() <= 20.0,
          "Expected accuracy between 10 and 20, got " + position.accuracy());
      assertTrue(
          position.confidence() >= 0.6 && position.confidence() <= 0.9,
          "Confidence should be between 0.6 and 0.9");
    }
  }

  /**
   * Tests for handling incomplete data in access points. These tests verify the algorithm's ability
   * to calculate positions when altitude and/or verticalAccuracy data are missing.
   */
  @Nested
  @DisplayName("Incomplete Data Handling Tests")
  class IncompleteDataHandlingTests {
    /**
     * Tests algorithm behavior with missing altitude data. Verifies that: 1. Algorithm can properly
     * handle null altitude values 2. Position is calculated using only 2D coordinates 3. Default
     * altitude is set to 0.0 Expected: Valid position with 2D coordinates and altitude defaulted to
     * 0.0
     */
    @Test
    @DisplayName("should handle null altitude values")
    void shouldHandleNullAltitudeValues() {
      // Create test data with null altitude values
      List<WifiScanResult> wifiScan =
          List.of(
              WifiScanResult.of("00:11:22:33:44:55", -65.0, 2400, "test-ssid"),
              WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 2400, "test-ssid"));

      List<WifiAccessPoint> knownAPs =
          List.of(
              WifiAccessPoint.builder()
                  .macAddress("00:11:22:33:44:55")
                  .latitude(40.748817)
                  .longitude(-73.985428)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(10.0)
                  .verticalAccuracy(null) // Null verticalAccuracy
                  .vendor("Vendor1")
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AA:BB:CC:DD:EE:FF")
                  .latitude(40.748192)
                  .longitude(-73.984870)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(8.0)
                  .verticalAccuracy(null) // Null verticalAccuracy
                  .vendor("Vendor2")
                  .confidence(0.9)
                  .build());

      // Execute algorithm
      Position position = algorithm.calculatePosition(wifiScan, knownAPs);

      // Verify results
      assertNotNull(position);
      assertTrue(
          position.latitude() >= 40.74 && position.latitude() <= 40.75,
          "Latitude should be reasonable");
      assertTrue(
          position.longitude() >= -74.0 && position.longitude() <= -73.9,
          "Longitude should be reasonable");
      assertEquals(
          0.0,
          position.altitude(),
          0.0001,
          "Altitude should be 0.0 when all altitude data is null");
      assertTrue(position.accuracy() > 0.0, "Accuracy should be positive");
      assertTrue(
          position.confidence() >= 0.6 && position.confidence() <= 0.9,
          "Confidence should be between 0.6 and 0.9");
    }

    /**
     * Tests algorithm behavior with mixed altitude data (some null, some not). Verifies that: 1.
     * Algorithm correctly calculates altitude using only valid altitude values 2. Position
     * coordinates are calculated correctly Expected: Valid position with altitude calculated from
     * valid altitude data only
     */
    @Test
    @DisplayName("should handle mixed altitude values")
    void shouldHandleMixedAltitudeValues() {
      // Create test data with mixed altitude values
      List<WifiScanResult> wifiScan =
          List.of(
              WifiScanResult.of("00:11:22:33:44:55", -65.0, 2400, "test-ssid"),
              WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 2400, "test-ssid"),
              WifiScanResult.of("11:22:33:44:55:66", -75.0, 2400, "test-ssid"));

      List<WifiAccessPoint> knownAPs =
          List.of(
              WifiAccessPoint.builder()
                  .macAddress("00:11:22:33:44:55")
                  .latitude(40.748817)
                  .longitude(-73.985428)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(10.0)
                  .verticalAccuracy(null) // Null verticalAccuracy
                  .vendor("Vendor1")
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AA:BB:CC:DD:EE:FF")
                  .latitude(40.748192)
                  .longitude(-73.984870)
                  .altitude(105.0) // Valid altitude
                  .horizontalAccuracy(8.0)
                  .verticalAccuracy(4.0) // Valid verticalAccuracy
                  .vendor("Vendor2")
                  .confidence(0.9)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("11:22:33:44:55:66")
                  .latitude(40.747500)
                  .longitude(-73.985800)
                  .altitude(95.0) // Valid altitude
                  .horizontalAccuracy(12.0)
                  .verticalAccuracy(6.0) // Valid verticalAccuracy
                  .confidence(0.7)
                  .build());

      // Execute algorithm
      Position position = algorithm.calculatePosition(wifiScan, knownAPs);

      // Verify results
      assertNotNull(position);
      assertTrue(
          position.latitude() >= 40.74 && position.latitude() <= 40.75,
          "Latitude should be reasonable");
      assertTrue(
          position.longitude() >= -74.0 && position.longitude() <= -73.9,
          "Longitude should be reasonable");

      // Altitude should be calculated from AP2 and AP3 (since AP1 has null altitude)
      assertTrue(
          position.altitude() != 0.0,
          "Altitude should be calculated from valid altitude data only");

      assertTrue(position.accuracy() > 0.0, "Accuracy should be positive");
      assertTrue(
          position.confidence() >= 0.6 && position.confidence() <= 0.9,
          "Confidence should be between 0.6 and 0.9");
    }
  }
}
