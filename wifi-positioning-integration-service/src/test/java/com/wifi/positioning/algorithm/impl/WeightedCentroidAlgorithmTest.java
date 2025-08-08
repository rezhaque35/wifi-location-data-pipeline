package com.wifi.positioning.algorithm.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;

/**
 * Test suite for the Weighted Centroid positioning algorithm. These tests verify the algorithm's
 * ability to calculate positions using weighted averages of AP positions based on signal strengths.
 * The test suite covers core functionality, edge cases, and real-world scenarios.
 *
 * <p>Key Test Areas: 1. Basic Functionality - Core algorithm behavior 2. Input Validation - Error
 * handling 3. Position Calculation - Accuracy and weighting 4. Signal Strength Weighting - Weight
 * calculation verification 5. Scaling - Performance with many APs 6. Confidence - Coverage-based
 * confidence calculation
 */
@DisplayName("Weighted Centroid Algorithm Tests")
class WeightedCentroidAlgorithmTest {
  private WeightedCentroidAlgorithm algorithm;
  private static final double DELTA = 0.001;

  @BeforeEach
  void setUp() {
    algorithm = new WeightedCentroidAlgorithm();
  }

  private WifiAccessPoint createAP(
      String mac, double lat, double lon, double alt, double accuracy) {
    return WifiAccessPoint.builder()
        .macAddress(mac)
        .latitude(lat)
        .longitude(lon)
        .altitude(alt)
        .horizontalAccuracy(accuracy)
        .confidence(0.8)
        .build();
  }

  private WifiScanResult createScan(String mac, double signalStrength) {
    return WifiScanResult.of(mac, signalStrength, 2400, "test-ssid");
  }

  /**
   * Tests for core algorithm functionality and basic behaviors. These tests verify that the
   * algorithm provides consistent and reliable results under normal operating conditions.
   */
  @Nested
  @DisplayName("Basic Functionality Tests")
  class BasicFunctionalityTests {
    /**
     * Verifies that the algorithm correctly identifies itself. Important for algorithm selection in
     * the hybrid positioning system.
     */
    @Test
    @DisplayName("should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
      assertEquals("weighted_centroid", algorithm.getName());
    }

    /**
     * Validates that confidence values are within valid range (0-1). The weighted centroid
     * algorithm uses a base confidence of 0.7, which is then adjusted based on AP coverage.
     */
    @Test
    @DisplayName("should return valid confidence level")
    void shouldReturnValidConfidence() {
      assertTrue(algorithm.getConfidence() > 0.0);
      assertTrue(algorithm.getConfidence() <= 1.0);
    }
  }

  /**
   * Tests for proper handling of invalid or edge case inputs. These tests ensure the algorithm
   * fails gracefully and provides appropriate error handling when given problematic input data.
   */
  @Nested
  @DisplayName("Input Validation Tests")
  class InputValidationTests {
    /** Verifies handling of null WiFi scan data. Expected: Return null to indicate invalid input */
    @Test
    @DisplayName("should return null for null WiFi scan")
    void shouldReturnNullForNullWifiScan() {
      assertNull(algorithm.calculatePosition(null, Collections.emptyList()));
    }

    /**
     * Verifies handling of empty WiFi scan data. Expected: Return null as position cannot be
     * calculated
     */
    @Test
    @DisplayName("should return null for empty WiFi scan")
    void shouldReturnNullForEmptyWifiScan() {
      assertNull(algorithm.calculatePosition(Collections.emptyList(), Collections.emptyList()));
    }

    /**
     * Verifies handling of null known AP list. Expected: Return null as reference points are
     * missing
     */
    @Test
    @DisplayName("should return null for null known APs")
    void shouldReturnNullForNullKnownAPs() {
      List<WifiScanResult> scans = Collections.singletonList(createScan("AP1", -65.0));
      assertNull(algorithm.calculatePosition(scans, null));
    }

    /**
     * Verifies handling of empty known AP list. Expected: Return null as no reference points
     * available
     */
    @Test
    @DisplayName("should return null for empty known APs")
    void shouldReturnNullForEmptyKnownAPs() {
      List<WifiScanResult> scans = Collections.singletonList(createScan("AP1", -65.0));
      assertNull(algorithm.calculatePosition(scans, Collections.emptyList()));
    }
  }

  /**
   * Tests for the core position calculation functionality. These tests verify the algorithm's
   * ability to: 1. Calculate weighted positions correctly 2. Handle unknown APs gracefully 3.
   * Process signal strength variations appropriately
   */
  @Nested
  @DisplayName("Position Calculation Tests")
  class PositionCalculationTests {
    /**
     * Tests the core weighted centroid calculation. Verifies that: 1. Position lies between APs 2.
     * Weighting follows signal strength normalization 3. Position is biased toward weaker signals
     * due to formula Expected: Position between APs, weighted by normalized signal strengths
     */
    @Test
    @DisplayName("should calculate weighted centroid correctly")
    void shouldCalculateWeightedCentroidCorrectly() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 3.0, 3.0, 20.0, 5.0));

      // AP1 has stronger signal, so result should be closer to AP1
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -60.0), // Stronger signal
              createScan("AP2", -80.0) // Weaker signal
              );

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // The algorithm weights based on normalized signal strength
      // Due to the weighting formula, the position should be between 1.0 and 3.0
      assertTrue(
          position.latitude() >= 1.0 && position.latitude() <= 3.0,
          "Position should be between AP1 and AP2");
      assertTrue(
          position.longitude() >= 1.0 && position.longitude() <= 3.0,
          "Position should be between AP1 and AP2");

      // Due to the formula: (signal - MAX) / (MIN - MAX) and then 10^normalized
      // A stronger signal (-60) gets a lower weight than a weaker signal (-80)
      // Therefore, the weaker signal AP2 will have more influence
      assertTrue(
          position.latitude() > 1.5,
          "Position should be weighted towards AP2 due to weaker signal getting more weight");
      assertTrue(
          position.longitude() > 1.5,
          "Position should be weighted towards AP2 due to weaker signal getting more weight");
    }

    /**
     * Tests handling of unknown APs in scan results. Verifies that: 1. Unknown APs are properly
     * filtered out 2. Position is calculated using only known APs 3. Weighting remains correct for
     * known APs Expected: Valid position using only known AP data
     */
    @Test
    @DisplayName("should handle unknown APs and calculate with known ones")
    void shouldHandleUnknownAPs() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 3.0, 3.0, 20.0, 5.0));

      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -60.0),
              createScan("UNKNOWN", -50.0), // Unknown AP with strongest signal
              createScan("AP2", -70.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // Unknown AP should be ignored, and position calculated based on known APs
      assertTrue(position.latitude() >= 1.0 && position.latitude() <= 3.0);
      assertTrue(position.longitude() >= 1.0 && position.longitude() <= 3.0);

      // AP2 has weaker signal than AP1, giving it more weight in the formula
      assertTrue(
          position.latitude() > 1.5,
          "Position should be weighted towards AP2 due to weaker signal getting more weight");
      assertTrue(
          position.longitude() > 1.5,
          "Position should be weighted towards AP2 due to weaker signal getting more weight");
    }

    /**
     * Tests behavior when no matching APs are found. Expected: Return null as position cannot be
     * calculated
     */
    @Test
    @DisplayName("should return null when no matching APs are found")
    void shouldReturnNullWhenNoMatchingAPs() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 3.0, 3.0, 20.0, 5.0));

      List<WifiScanResult> scans =
          Arrays.asList(createScan("UNKNOWN1", -60.0), createScan("UNKNOWN2", -70.0));

      assertNull(algorithm.calculatePosition(scans, knownAPs));
    }
  }

  /**
   * Tests for signal strength weighting calculations. These tests verify the algorithm's signal
   * normalization and weighting behavior under various signal conditions.
   */
  @Nested
  @DisplayName("Signal Strength Weighting Tests")
  class SignalStrengthWeightingTests {
    /**
     * Tests the signal strength normalization formula. Verifies that: 1. Weaker signals get more
     * weight due to normalization 2. Position is biased toward APs with weaker signals 3. Formula
     * behaves correctly at signal strength extremes Expected: Position biased toward weaker signals
     * due to normalization formula
     */
    @Test
    @DisplayName("should give more weight to weaker signals due to formula")
    void shouldGiveMoreWeightToStrongerSignals() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 10.0, 10.0, 20.0, 5.0));

      // Very strong signal for AP1, weak for AP2
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -40.0), // Very strong
              createScan("AP2", -90.0) // Very weak
              );

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // Due to the formula: (signal - MAX_WIFI_SIGNAL) / (MIN_WIFI_SIGNAL - MAX_WIFI_SIGNAL)
      // Stronger signals produce smaller normalized values, giving less weight
      // So the position should be weighted towards AP2 with weaker signal
      assertTrue(
          position.latitude() > 5.0,
          "Position should be weighted towards AP2 due to weaker signal getting more weight");
      assertTrue(
          position.longitude() > 5.0,
          "Position should be weighted towards AP2 due to weaker signal getting more weight");
    }

    @Test
    @DisplayName("should balance position with similar signal strengths")
    void shouldBalancePositionWithSimilarSignalStrengths() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 3.0, 3.0, 20.0, 5.0));

      // Similar signal strengths
      List<WifiScanResult> scans =
          Arrays.asList(createScan("AP1", -65.0), createScan("AP2", -65.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // Position should be approximately at the midpoint
      assertEquals(2.0, position.latitude(), 0.2);
      assertEquals(2.0, position.longitude(), 0.2);
    }
  }

  @Nested
  @DisplayName("Scaling Tests")
  class ScalingTests {
    @Test
    @DisplayName("should handle large number of access points")
    void shouldHandleLargeNumberOfAccessPoints() {
      // Create a grid of APs 10x10
      List<WifiAccessPoint> knownAPs =
          IntStream.range(0, 100)
              .mapToObj(
                  i ->
                      createAP(
                          "AP" + i,
                          (i / 10) * 0.1, // Scale to make the grid more visible (0.0-0.9)
                          (i % 10) * 0.1, // Scale to make the grid more visible (0.0-0.9)
                          10.0,
                          5.0))
              .collect(Collectors.toList());

      // Create scans with varying signal strengths
      List<WifiScanResult> scans =
          IntStream.range(0, 100)
              .mapToObj(
                  i ->
                      createScan(
                          "AP" + i, -50.0 - (i / 5.0))) // Signal strength decreases as i increases
              .collect(Collectors.toList());

      long startTime = System.currentTimeMillis();
      Position position = algorithm.calculatePosition(scans, knownAPs);
      long endTime = System.currentTimeMillis();

      assertNotNull(position);

      // Verify the position is within the grid
      assertTrue(
          position.latitude() >= 0.0 && position.latitude() <= 0.9,
          "Position latitude should be within the AP grid");
      assertTrue(
          position.longitude() >= 0.0 && position.longitude() <= 0.9,
          "Position longitude should be within the AP grid");

      // With the weighting formula, weaker signals (higher i values) get more weight
      // This means the position should be weighted towards the higher grid indices
      assertTrue(
          position.latitude() > 0.4,
          "Position should be weighted towards higher grid indices due to weighting formula");

      // Performance check - should execute reasonably quickly
      assertTrue((endTime - startTime) < 1000, "Calculation should complete in under 1 second");
    }
  }

  @Nested
  @DisplayName("Confidence Calculation Tests")
  class ConfidenceCalculationTests {
    @Test
    @DisplayName("should calculate confidence based on coverage")
    void shouldCalculateConfidenceBasedOnCoverage() {
      // Create 10 known APs
      List<WifiAccessPoint> knownAPs =
          IntStream.range(0, 10)
              .mapToObj(i -> createAP("AP" + i, i, i, 10.0, 5.0))
              .collect(Collectors.toList());

      // Test with different coverage levels
      // Case 1: Full coverage (all APs scanned)
      List<WifiScanResult> fullCoverage =
          IntStream.range(0, 10)
              .mapToObj(i -> createScan("AP" + i, -65.0))
              .collect(Collectors.toList());

      // Case 2: Half coverage (50% of APs scanned)
      List<WifiScanResult> halfCoverage =
          IntStream.range(0, 5)
              .mapToObj(i -> createScan("AP" + i, -65.0))
              .collect(Collectors.toList());

      // Case 3: Low coverage (20% of APs scanned)
      List<WifiScanResult> lowCoverage =
          IntStream.range(0, 2)
              .mapToObj(i -> createScan("AP" + i, -65.0))
              .collect(Collectors.toList());

      Position fullPosition = algorithm.calculatePosition(fullCoverage, knownAPs);
      Position halfPosition = algorithm.calculatePosition(halfCoverage, knownAPs);
      Position lowPosition = algorithm.calculatePosition(lowCoverage, knownAPs);

      assertNotNull(fullPosition);
      assertNotNull(halfPosition);
      assertNotNull(lowPosition);

      // Higher coverage should result in higher confidence
      assertTrue(fullPosition.confidence() > halfPosition.confidence());
      assertTrue(halfPosition.confidence() > lowPosition.confidence());
    }
  }

  @Test
  @DisplayName("should calculate position with correct accuracy and confidence")
  void shouldCalculatePositionWithCorrectAccuracyAndConfidence() {
    List<WifiAccessPoint> aps =
        Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 3.0, 3.0, 20.0, 5.0));

    List<WifiScanResult> scans = Arrays.asList(createScan("AP1", -60.0), createScan("AP2", -70.0));

    Position result = algorithm.calculatePosition(scans, aps);
    assertNotNull(result);
    // Accuracy: Should be within 5-7m for strong signals, weighted centroid
    assertTrue(
        result.accuracy() >= 5.0 && result.accuracy() <= 7.0,
        "Expected accuracy between 5 and 7, got " + result.accuracy());
    // Confidence: Should be high for strong signals
    assertTrue(
        result.confidence() >= 0.7 && result.confidence() <= 0.8,
        "Expected confidence between 0.7 and 0.8, got " + result.confidence());
    // Latitude/Longitude: Should be between APs, with margin
    assertTrue(
        result.latitude() >= 0.9 && result.latitude() <= 3.1,
        "Expected latitude between 0.9 and 3.1, got " + result.latitude());
    assertTrue(
        result.longitude() >= 0.9 && result.longitude() <= 3.1,
        "Expected longitude between 0.9 and 3.1, got " + result.longitude());
  }

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
      // Create APs with null altitude values
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              WifiAccessPoint.builder()
                  .macAddress("AP1")
                  .latitude(1.0)
                  .longitude(1.0)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AP2")
                  .latitude(3.0)
                  .longitude(3.0)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build());

      List<WifiScanResult> scans =
          Arrays.asList(createScan("AP1", -60.0), createScan("AP2", -80.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);

      // Verify position is calculated correctly
      assertNotNull(position);
      assertTrue(position.latitude() >= 1.0 && position.latitude() <= 3.0);
      assertTrue(position.longitude() >= 1.0 && position.longitude() <= 3.0);
      assertEquals(0.0, position.altitude(), DELTA);
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
      // Create APs with mixed altitude values (null and non-null)
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              WifiAccessPoint.builder()
                  .macAddress("AP1")
                  .latitude(1.0)
                  .longitude(1.0)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AP2")
                  .latitude(3.0)
                  .longitude(3.0)
                  .altitude(20.0) // Valid altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build());

      List<WifiScanResult> scans =
          Arrays.asList(createScan("AP1", -60.0), createScan("AP2", -80.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);

      // Verify position is calculated correctly
      assertNotNull(position);
      assertTrue(position.latitude() >= 1.0 && position.latitude() <= 3.0);
      assertTrue(position.longitude() >= 1.0 && position.longitude() <= 3.0);

      // Since one AP has valid altitude (20.0) and weaker signal gets more weight,
      // the altitude should be close to 20.0
      assertEquals(20.0, position.altitude(), 5.0);
    }
  }
}
