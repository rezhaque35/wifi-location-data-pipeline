package com.wifi.positioning.algorithm.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;

/**
 * Test suite for the Proximity Detection positioning algorithm. These tests verify the algorithm's
 * ability to determine position based on the strongest signal approach. The test suite covers basic
 * functionality, edge cases, and signal strength-based confidence calculations.
 *
 * <p>Key Test Areas: 1. Basic Functionality - Core algorithm behavior 2. Input Validation - Error
 * handling 3. Position Calculation - Strongest signal selection 4. Confidence Calculation - Signal
 * strength correlation 5. Weight Calculation - Algorithm weight factors
 */
@DisplayName("Proximity Detection Algorithm Tests")
class ProximityDetectionAlgorithmTest {
  private ProximityDetectionAlgorithm algorithm;

  @BeforeEach
  void setUp() {
    algorithm = new ProximityDetectionAlgorithm();
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
      assertEquals("proximity", algorithm.getName());
    }

    /**
     * Validates that confidence values are within valid range (0-1). The proximity detection
     * algorithm uses a base confidence of 0.6, which is lower than other algorithms due to its
     * simplistic approach.
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

    /** Verifies handling of empty WiFi scan data. Expected: Return null as no signals to process */
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
   * ability to: 1. Identify strongest signal correctly 2. Return corresponding AP position 3.
   * Handle unknown APs appropriately
   */
  @Nested
  @DisplayName("Position Calculation Tests")
  class PositionCalculationTests {
    /**
     * Tests the core strongest signal selection. Verifies that: 1. Strongest signal is correctly
     * identified 2. Position matches the AP with strongest signal 3. All coordinates (lat, lon,
     * alt) are correctly copied Expected: Position should exactly match AP2's location
     */
    @Test
    @DisplayName("should find the AP with the strongest signal")
    void shouldFindAPWithStrongestSignal() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0),
              createAP("AP2", 2.0, 2.0, 20.0, 5.0),
              createAP("AP3", 3.0, 3.0, 30.0, 5.0));

      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -70.0),
              createScan("AP2", -60.0), // Strongest signal
              createScan("AP3", -80.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // Position should match AP2 coordinates since it has the strongest signal
      assertEquals(2.0, position.latitude());
      assertEquals(2.0, position.longitude());
      assertEquals(20.0, position.altitude());
    }

    /**
     * Tests handling of unknown APs with strong signals. Verifies that: 1. Unknown APs are properly
     * identified 2. Algorithm returns null when strongest signal is from unknown AP Expected:
     * Return null when strongest signal is from unknown AP
     */
    @Test
    @DisplayName("should return null when strongest signal is from unknown AP")
    void shouldHandleUnknownAPs() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 2.0, 2.0, 20.0, 5.0));

      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -70.0),
              createScan("UNKNOWN", -60.0), // Unknown AP with strongest signal
              createScan("AP2", -80.0));

      // The algorithm returns null when the strongest signal AP is not in the known list
      assertNull(algorithm.calculatePosition(scans, knownAPs));
    }

    /**
     * Tests behavior when all detected APs are unknown. Expected: Return null as no valid reference
     * points
     */
    @Test
    @DisplayName("should handle only unknown APs")
    void shouldHandleOnlyUnknownAPs() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 2.0, 2.0, 20.0, 5.0));

      List<WifiScanResult> scans =
          Arrays.asList(createScan("UNKNOWN1", -70.0), createScan("UNKNOWN2", -60.0));

      assertNull(algorithm.calculatePosition(scans, knownAPs));
    }
  }

  /**
   * Tests for confidence calculation based on signal strength. These tests verify that confidence
   * values correctly reflect signal quality and follow the normalization formula.
   */
  @Nested
  @DisplayName("Confidence Calculation Tests")
  class ConfidenceCalculationTests {
    /**
     * Tests confidence calculation across signal strength range. Verifies that: 1. Stronger signals
     * produce higher confidence 2. Confidence values are properly normalized 3. Relative confidence
     * ordering is maintained Expected: Strong signal (-40dBm) should have higher confidence than
     * weak signal (-80dBm)
     */
    @Test
    @DisplayName("should calculate higher confidence for stronger signals")
    void shouldCalculateHigherConfidenceForStrongerSignals() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 2.0, 2.0, 20.0, 5.0));

      // Strong signal
      List<WifiScanResult> strongSignal = Collections.singletonList(createScan("AP1", -40.0));
      Position strongPosition = algorithm.calculatePosition(strongSignal, knownAPs);

      // Weak signal
      List<WifiScanResult> weakSignal = Collections.singletonList(createScan("AP2", -80.0));
      Position weakPosition = algorithm.calculatePosition(weakSignal, knownAPs);

      assertNotNull(strongPosition);
      assertNotNull(weakPosition);
      assertTrue(strongPosition.confidence() > weakPosition.confidence());
    }

    /**
     * Tests confidence calculation for very weak signals. Verifies that: 1. Very weak signals
     * produce low but non-zero confidence 2. Confidence calculation handles edge cases gracefully
     * Expected: Very weak signal (-89dBm) should have confidence < 0.2
     */
    @Test
    @DisplayName("should handle very weak signals with minimal confidence")
    void shouldHandleVeryWeakSignalsWithMinimalConfidence() {
      List<WifiAccessPoint> knownAPs =
          Collections.singletonList(createAP("AP1", 1.0, 1.0, 10.0, 5.0));

      // Very weak signal
      List<WifiScanResult> veryWeakSignal = Collections.singletonList(createScan("AP1", -89.0));

      Position position = algorithm.calculatePosition(veryWeakSignal, knownAPs);
      assertNotNull(position);
      assertTrue(position.confidence() > 0 && position.confidence() < 0.2);
    }

    @Test
    @DisplayName("should handle very strong signals with high confidence")
    void shouldHandleVeryStrongSignalsWithHighConfidence() {
      List<WifiAccessPoint> knownAPs =
          Collections.singletonList(createAP("AP1", 1.0, 1.0, 10.0, 5.0));

      // Very strong signal
      List<WifiScanResult> veryStrongSignal = Collections.singletonList(createScan("AP1", -35.0));

      Position position = algorithm.calculatePosition(veryStrongSignal, knownAPs);
      assertNotNull(position);
      assertTrue(position.confidence() > 0.8 && position.confidence() <= 0.85);
    }
  }

  @Nested
  @DisplayName("Accuracy and Confidence Range Tests")
  class AccuracyAndConfidenceRangeTests {
    @Test
    @DisplayName("should return expected accuracy and confidence for strong signal")
    void shouldReturnExpectedAccuracyAndConfidenceForStrongSignal() {
      WifiAccessPoint ap = createAP("AP1", 1.0, 1.0, 10.0, 12.0); // accuracy 12m
      WifiScanResult scan = createScan("AP1", -65.0);
      Position result = algorithm.calculatePosition(List.of(scan), List.of(ap));
      assertNotNull(result);
      // Accuracy: Should be within 10-15m for proximity detection
      assertTrue(
          result.accuracy() >= 10.0 && result.accuracy() <= 15.0,
          "Expected accuracy between 10 and 15, got " + result.accuracy());
      // Confidence: Should be in the expected range for proximity
      assertTrue(
          result.confidence() >= 0.4 && result.confidence() <= 0.5,
          "Expected confidence between 0.4 and 0.5, got " + result.confidence());
      // Latitude/Longitude: Should match the AP with the strongest signal
      assertEquals(1.0, result.latitude(), 0.0);
      assertEquals(1.0, result.longitude(), 0.0);
    }

    @Test
    @DisplayName("should return worse accuracy and lower confidence for weak signal")
    void shouldReturnWorseAccuracyAndLowerConfidenceForWeakSignal() {
      WifiAccessPoint ap = createAP("AP1", 1.0, 1.0, 10.0, 35.0); // accuracy 35m
      WifiScanResult scan = createScan("AP1", -85.0);
      Position result = algorithm.calculatePosition(List.of(scan), List.of(ap));
      assertNotNull(result);
      // Assert accuracy is worse (e.g., >30m)
      assertTrue(
          result.accuracy() >= 30.0 && result.accuracy() <= 40.0,
          "Expected accuracy between 30 and 40, got " + result.accuracy());
      // Assert confidence is low for weak signal
      assertTrue(
          result.confidence() > 0.0 && result.confidence() < 0.2,
          "Expected confidence < 0.2 for weak signal, got " + result.confidence());
    }
  }

  /**
   * Tests for weight calculation factors. These tests verify the algorithm's ability to: 1. Return
   * appropriate weights based on AP count 2. Apply signal quality adjustments correctly 3. Handle
   * geometric factor adjustments 4. Apply signal distribution adjustments 5. Calculate final
   * combined weights
   */
  @Nested
  @DisplayName("Weight Factor Calculation Tests")
  class WeightFactorTests {
    /**
     * Tests base weights for different AP count scenarios. Verifies that the proximity algorithm: -
     * Works best with single AP (highest weight) - Gets progressively less valuable as AP count
     * increases
     */
    @Test
    @DisplayName("should return correct base weights for different AP counts")
    void shouldReturnCorrectBaseWeightsForDifferentAPCounts() {
      // Given
      APCountFactor singleAP = APCountFactor.SINGLE_AP;
      APCountFactor twoAPs = APCountFactor.TWO_APS;
      APCountFactor threeAPs = APCountFactor.THREE_APS;
      APCountFactor fourPlusAPs = APCountFactor.FOUR_PLUS_APS;

      // When & Then
      assertEquals(
          1.0,
          algorithm.getBaseWeight(singleAP),
          0.001,
          "Proximity algorithm should have high weight for single AP");
      assertEquals(
          0.4,
          algorithm.getBaseWeight(twoAPs),
          0.001,
          "Proximity algorithm should have medium weight for two APs");
      assertEquals(
          0.3,
          algorithm.getBaseWeight(threeAPs),
          0.001,
          "Proximity algorithm should have lower weight for three APs");
      assertEquals(
          0.2,
          algorithm.getBaseWeight(fourPlusAPs),
          0.001,
          "Proximity algorithm should have low weight for four+ APs");
    }

    /**
     * Tests signal quality adjustments. Verifies that: - Strong signals provide positive adjustment
     * - Weak signals provide negative adjustment
     */
    @Test
    @DisplayName("should return correct signal quality adjustments")
    void shouldReturnCorrectSignalQualityAdjustments() {
      // Given
      SignalQualityFactor strongSignal = SignalQualityFactor.STRONG_SIGNAL;
      SignalQualityFactor mediumSignal = SignalQualityFactor.MEDIUM_SIGNAL;
      SignalQualityFactor weakSignal = SignalQualityFactor.WEAK_SIGNAL;
      SignalQualityFactor veryWeakSignal = SignalQualityFactor.VERY_WEAK_SIGNAL;

      // When & Then
      assertEquals(
          0.9,
          algorithm.getSignalQualityMultiplier(strongSignal),
          0.001,
          "Proximity algorithm should have multiplier of 0.9 for strong signals");
      assertEquals(
          0.7,
          algorithm.getSignalQualityMultiplier(mediumSignal),
          0.001,
          "Proximity algorithm should have multiplier of 0.7 for medium signals");
      assertEquals(
          0.4,
          algorithm.getSignalQualityMultiplier(weakSignal),
          0.001,
          "Proximity algorithm should have multiplier of 0.4 for weak signals");
      assertEquals(
          0.5,
          algorithm.getSignalQualityMultiplier(veryWeakSignal),
          0.001,
          "Proximity algorithm should have multiplier of 0.5 for very weak signals");
    }

    /**
     * Tests geometric quality adjustments. Verifies that: - Proximity algorithm doesn't rely on
     * geometric factors - All geometric factors return multiplier of 1.0
     */
    @Test
    @DisplayName("should return neutral multiplier for geometric quality factors")
    void shouldReturnZeroAdjustmentForGeometricFactors() {
      // Given
      GeometricQualityFactor excellentGDOP = GeometricQualityFactor.EXCELLENT_GDOP;

      // When & Then
      assertEquals(
          1.0,
          algorithm.getGeometricQualityMultiplier(excellentGDOP),
          0.001,
          "Proximity algorithm should have neutral multiplier for geometric factors");

      // Test all other GDOP factors as well
      assertEquals(
          1.0, algorithm.getGeometricQualityMultiplier(GeometricQualityFactor.GOOD_GDOP), 0.001);
      assertEquals(
          1.0, algorithm.getGeometricQualityMultiplier(GeometricQualityFactor.FAIR_GDOP), 0.001);
      assertEquals(
          1.0, algorithm.getGeometricQualityMultiplier(GeometricQualityFactor.POOR_GDOP), 0.001);
    }

    /**
     * Tests signal distribution multipliers. Verifies that: - Signal outliers have multiplier of
     * 0.9 - Uniform signals have multiplier of 1.0 - Mixed signals have multiplier of 0.7
     */
    @Test
    @DisplayName("should return correct signal distribution multipliers")
    void shouldReturnCorrectSignalDistributionAdjustments() {
      // Given
      SignalDistributionFactor uniformSignals = SignalDistributionFactor.UNIFORM_SIGNALS;
      SignalDistributionFactor mixedSignals = SignalDistributionFactor.MIXED_SIGNALS;
      SignalDistributionFactor signalOutliers = SignalDistributionFactor.SIGNAL_OUTLIERS;

      // When & Then
      assertEquals(
          1.0,
          algorithm.getSignalDistributionMultiplier(uniformSignals),
          0.001,
          "Proximity algorithm should have multiplier of 1.0 for uniform signals");
      assertEquals(
          0.7,
          algorithm.getSignalDistributionMultiplier(mixedSignals),
          0.001,
          "Proximity algorithm should have multiplier of 0.7 for mixed signals");
      assertEquals(
          0.9,
          algorithm.getSignalDistributionMultiplier(signalOutliers),
          0.001,
          "Proximity algorithm should have multiplier of 0.9 for signal outliers");
    }

    /**
     * Tests the final weight calculation. Verifies that: - Weight calculation properly combines all
     * factors - Single AP with strong signal gets highest weight - Weight values are in reasonable
     * range
     */
    @Test
    @DisplayName("should calculate correct final weight from combined factors")
    void shouldCalculateCorrectFinalWeight() {
      // Given
      // Single AP, strong signal, good GDOP
      List<WifiScanResult> strongSingleAP =
          Arrays.asList(WifiScanResult.of("00:11:22:33:44:55", -50.0, 2400, "TestAP"));

      // Three APs, weak signals, poor GDOP
      List<WifiScanResult> weakThreeAPs =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:55", -87.0, 2400, "TestAP1"),
              WifiScanResult.of("11:22:33:44:55:66", -85.0, 2400, "TestAP2"),
              WifiScanResult.of("22:33:44:55:66:77", -88.0, 2400, "TestAP3"));

      // When
      double weightSingleStrong = algorithm.calculateWeight(1, strongSingleAP, 3.0);
      double weightThreeWeak = algorithm.calculateWeight(3, weakThreeAPs, 7.0);

      // Then
      // Calculate expected value for single AP with strong signal using multipliers
      // 1.0 (base weight) * 0.9 (signal quality) * 1.0 (geometric) * 1.0 (distribution) = 0.9
      double expectedSingleStrong = 1.0 * 0.9 * 1.0 * 1.0;
      assertEquals(
          expectedSingleStrong,
          weightSingleStrong,
          0.001,
          "Weight calculation for single strong AP should be correct");

      // For three APs with weak signal (using multipliers)
      // 0.3 (base weight) * 0.4 (signal quality) * 1.0 (geometric) * 1.0 (distribution) = 0.12
      double expectedThreeWeak = 0.3 * 0.4 * 1.0 * 1.0;
      assertEquals(
          expectedThreeWeak,
          weightThreeWeak,
          0.001,
          "Weight calculation for three weak APs should be correct");

      // Verify reasonable ranges
      assertTrue(
          weightSingleStrong > weightThreeWeak,
          "Single strong AP should have higher weight than three weak APs for proximity algorithm");
      assertTrue(
          weightSingleStrong > 0 && weightSingleStrong <= 1.0,
          "Weight should be in reasonable range (0-1.0)");
      assertTrue(
          weightThreeWeak > 0 && weightThreeWeak <= 1.0,
          "Weight should be in reasonable range (0-1.0)");
    }
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
      // Create AP with null altitude
      WifiAccessPoint ap =
          WifiAccessPoint.builder()
              .macAddress("AP1")
              .latitude(1.0)
              .longitude(1.0)
              .altitude(null) // Null altitude
              .horizontalAccuracy(5.0)
              .confidence(0.8)
              .build();

      List<WifiAccessPoint> knownAPs = Collections.singletonList(ap);

      List<WifiScanResult> scans = Collections.singletonList(createScan("AP1", -60.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);

      // Verify position is calculated correctly
      assertNotNull(position);
      assertEquals(1.0, position.latitude());
      assertEquals(1.0, position.longitude());
      assertEquals(0.0, position.altitude());
    }

    /**
     * Tests algorithm behavior with missing verticalAccuracy data. Verifies that: 1. Algorithm can
     * properly handle null verticalAccuracy values 2. Position is calculated using only
     * horizontalAccuracy for accuracy Expected: Valid position with appropriate accuracy values
     */
    @Test
    @DisplayName("should handle null verticalAccuracy values")
    void shouldHandleNullVerticalAccuracyValues() {
      // Create AP with null verticalAccuracy (but valid altitude)
      WifiAccessPoint ap =
          WifiAccessPoint.builder()
              .macAddress("AP1")
              .latitude(1.0)
              .longitude(1.0)
              .altitude(10.0)
              .horizontalAccuracy(5.0)
              .verticalAccuracy(null) // Null verticalAccuracy
              .confidence(0.8)
              .build();

      List<WifiAccessPoint> knownAPs = Collections.singletonList(ap);

      List<WifiScanResult> scans = Collections.singletonList(createScan("AP1", -60.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);

      // Verify position is calculated correctly
      assertNotNull(position);
      assertEquals(1.0, position.latitude());
      assertEquals(1.0, position.longitude());
      assertEquals(10.0, position.altitude());
      assertEquals(5.0, position.accuracy()); // Should use horizontalAccuracy
    }

    /**
     * Tests algorithm behavior with both null altitude and verticalAccuracy. Verifies that: 1.
     * Algorithm operates correctly when both altitude and verticalAccuracy are null 2. Position is
     * calculated with 2D coordinates only Expected: Valid position with 2D coordinates and
     * appropriate default values
     */
    @Test
    @DisplayName("should handle both null altitude and verticalAccuracy")
    void shouldHandleBothNullAltitudeAndVerticalAccuracy() {
      // Create AP with null altitude and null verticalAccuracy
      WifiAccessPoint ap =
          WifiAccessPoint.builder()
              .macAddress("AP1")
              .latitude(1.0)
              .longitude(1.0)
              .altitude(null) // Null altitude
              .horizontalAccuracy(5.0)
              .verticalAccuracy(null) // Null verticalAccuracy
              .confidence(0.8)
              .build();

      List<WifiAccessPoint> knownAPs = Collections.singletonList(ap);

      List<WifiScanResult> scans = Collections.singletonList(createScan("AP1", -60.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);

      // Verify position is calculated correctly
      assertNotNull(position);
      assertEquals(1.0, position.latitude());
      assertEquals(1.0, position.longitude());
      assertEquals(0.0, position.altitude());
      assertEquals(5.0, position.accuracy()); // Should use horizontalAccuracy
    }
  }
}
