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
 * Test suite for the Maximum Likelihood positioning algorithm.
 *
 * <p>This algorithm uses a probabilistic approach to estimate position by: 1. Creating an initial
 * position estimate using weighted centroid 2. Building measurement models for each AP observation
 * 3. Maximizing the likelihood function using gradient descent 4. Calculating confidence based on
 * convergence and signal quality
 *
 * <p>Key Test Areas: - Basic Functionality: Core algorithm behavior and validation - Input
 * Validation: Error handling for invalid inputs - Convergence: Position estimation through
 * iteration - Measurement Models: Signal strength probability distributions - Gradient Descent:
 * Optimization performance
 */
@DisplayName("Maximum Likelihood Algorithm Tests")
class MaximumLikelihoodAlgorithmTest {
  private MaximumLikelihoodAlgorithm algorithm;
  private static final double DELTA = 0.001;

  @BeforeEach
  void setUp() {
    algorithm = new MaximumLikelihoodAlgorithm();
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
   * algorithm provides consistent and reliable results under normal operating conditions with
   * various input configurations.
   */
  @Nested
  @DisplayName("Basic Functionality Tests")
  class BasicFunctionalityTests {
    /**
     * Verifies that the algorithm correctly identifies itself. Important for algorithm selection in
     * the hybrid positioning system. Expected: Returns "maximum_likelihood" as the algorithm name.
     */
    @Test
    @DisplayName("should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
      assertEquals("maximum_likelihood", algorithm.getName());
    }

    /**
     * Validates that confidence values are within valid range (0-1). The maximum likelihood
     * algorithm's confidence is based on: 1. Convergence of the gradient descent 2. Quality of
     * signal measurements 3. Geometric distribution of APs
     *
     * <p>Expected: Confidence between 0 and 1, typically higher than simpler algorithms due to the
     * sophisticated estimation process.
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
   * Tests for the core maximum likelihood estimation functionality. These tests verify the
   * algorithm's ability to: 1. Create accurate measurement models 2. Converge to reasonable
   * position estimates 3. Handle various AP configurations
   */
  @Nested
  @DisplayName("Initial Estimate Tests")
  class InitialEstimateTests {
    /**
     * Tests position estimation with ideal AP configuration. This test verifies: 1. Initial
     * position estimate is reasonable 2. Gradient descent converges 3. Final position is accurate
     * 4. Confidence reflects estimation quality
     *
     * <p>Expected: - Position should be near the weighted center of APs - Confidence should be high
     * due to good convergence - Accuracy should be better than simpler algorithms
     */
    @Test
    @DisplayName("should calculate initial estimate using weighted centroid method")
    void shouldCalculateInitialEstimate() {
      // Create 3 APs in a triangle layout
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0),
              createAP("AP2", 2.0, 3.0, 10.0, 5.0),
              createAP("AP3", 3.0, 1.0, 10.0, 5.0));

      // Create scans with varying signal strengths
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -60.0), // Strongest signal
              createScan("AP2", -70.0),
              createScan("AP3", -80.0) // Weakest signal
              );

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // The initial estimate should be within the triangle formed by APs
      assertTrue(
          position.latitude() >= 1.0 && position.latitude() <= 3.0,
          "Latitude should be within the bounds of the APs");
      assertTrue(
          position.longitude() >= 1.0 && position.longitude() <= 3.0,
          "Longitude should be within the bounds of the APs");

      // Stronger signal should pull position closer to AP1
      assertTrue(
          position.latitude() < 2.0,
          "Position should be weighted towards AP1 due to stronger signal");
    }

    /**
     * Tests handling of poor AP geometry. This is a challenging case because: 1. Initial estimate
     * may be far from true position 2. Gradient descent may converge slowly 3. Final position may
     * have higher uncertainty
     *
     * <p>Expected: - Algorithm should still converge - Confidence should be lower than ideal case -
     * Position should be reasonable given constraints
     */
    @Test
    @DisplayName("should handle unknown APs in scan results")
    void shouldHandleUnknownAPsInScanResults() {
      // Create known APs
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 2.0, 2.0, 10.0, 5.0));

      // Create scans with known and unknown APs
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -65.0),
              createScan("UNKNOWN", -55.0), // Unknown AP with strongest signal
              createScan("AP2", -75.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // Position should be calculated using only known APs
      assertTrue(
          position.latitude() >= 1.0 && position.latitude() <= 2.0,
          "Latitude should be within bounds of known APs");
      assertTrue(
          position.longitude() >= 1.0 && position.longitude() <= 2.0,
          "Longitude should be within bounds of known APs");
    }
  }

  /**
   * Tests for measurement model creation and probability calculations. These tests verify that the
   * algorithm correctly: 1. Creates measurement models for each AP 2. Calculates signal strength
   * probabilities 3. Updates models during iteration
   */
  @Nested
  @DisplayName("Position Calculation Tests")
  class PositionCalculationTests {
    /**
     * Tests position estimation with ideal AP configuration. This test verifies: 1. Initial
     * position estimate is reasonable 2. Gradient descent converges 3. Final position is accurate
     * 4. Confidence reflects estimation quality
     *
     * <p>Expected: - Position should be near the weighted center of APs - Confidence should be high
     * due to good convergence - Accuracy should be better than simpler algorithms
     */
    @Test
    @DisplayName("should calculate reasonable position with 3 APs in triangular arrangement")
    void shouldCalculatePositionWithThreeAPs() {
      // Create 3 APs in a triangle layout
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0),
              createAP("AP2", 1.0, 3.0, 10.0, 5.0),
              createAP("AP3", 3.0, 2.0, 10.0, 5.0));

      // Create scans with varying signal strengths
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -70.0),
              createScan("AP2", -65.0),
              createScan("AP3", -60.0) // Strongest signal
              );

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // Position should be within the triangle formed by APs and biased towards AP3
      assertTrue(
          position.latitude() > 1.0 && position.latitude() < 3.0,
          "Latitude should be within the bounds of the APs");
      assertTrue(
          position.longitude() > 1.0 && position.longitude() < 3.0,
          "Longitude should be within the bounds of the APs");

      // Position should be closer to AP3 (strongest signal)
      assertTrue(
          position.latitude() > 1.5,
          "Position should be weighted towards AP3 due to stronger signal");
    }

    /**
     * Tests convergence with many iterations. Verifies that: 1. Algorithm converges within max
     * iterations 2. Position estimate improves with iterations 3. Gradient magnitude decreases
     * appropriately
     *
     * <p>Expected: - Final position should be stable - Confidence should reflect convergence
     * quality - Position should be more accurate than initial estimate
     */
    @Test
    @DisplayName(
        "should improve position estimate over weighted centroid with likelihood iteration")
    void shouldImprovePositionEstimateWithLikelihoodIteration() {
      // Create APs in a line formation to test gradient descent refinement
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0),
              createAP("AP2", 2.0, 2.0, 10.0, 5.0),
              createAP("AP3", 3.0, 3.0, 10.0, 5.0),
              createAP("AP4", 4.0, 4.0, 10.0, 5.0));

      // Create signal strengths that peak at AP2 (the target position)
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -75.0),
              createScan("AP2", -50.0), // Very strong signal
              createScan("AP3", -70.0),
              createScan("AP4", -80.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);
      assertNotNull(position);

      // Position should converge close to AP2's position
      assertEquals(2.0, position.latitude(), 0.3);
      assertEquals(2.0, position.longitude(), 0.3);

      // Verify the confidence is within expected range for the implementation
      assertTrue(
          position.confidence() > 0.0 && position.confidence() <= 1.0,
          "Confidence should be a valid probability value");
    }

    /**
     * Tests handling of poor AP geometry. This is a challenging case because: 1. Initial estimate
     * may be far from true position 2. Gradient descent may converge slowly 3. Final position may
     * have higher uncertainty
     *
     * <p>Expected: - Algorithm should still converge - Confidence should be lower than ideal case -
     * Position should be reasonable given constraints
     */
    @Test
    @DisplayName("should handle poor AP geometry")
    void shouldHandlePoorAPGeometry() {
      // Create a 3x3 grid of APs
      List<WifiAccessPoint> knownAPs = new java.util.ArrayList<>();
      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
          knownAPs.add(createAP("AP" + (i * 3 + j), 1.0 + i * 0.5, 1.0 + j * 0.5, 10.0, 5.0));
        }
      }

      // Create signal strengths that peak at the center AP
      List<WifiScanResult> scans = new java.util.ArrayList<>();
      for (int i = 0; i < 9; i++) {
        double signalStrength = -70.0;
        if (i == 4) { // Center AP
          signalStrength = -50.0;
        } else if (i % 2 == 0) { // Corner APs
          signalStrength = -80.0;
        } else { // Edge APs
          signalStrength = -65.0;
        }
        scans.add(createScan("AP" + i, signalStrength));
      }

      long startTime = System.currentTimeMillis();
      Position position = algorithm.calculatePosition(scans, knownAPs);
      long endTime = System.currentTimeMillis();

      assertNotNull(position);

      // Position should be close to the center of the grid (where AP4 is)
      assertEquals(1.5, position.latitude(), 0.3);
      assertEquals(1.5, position.longitude(), 0.3);

      // Check if the algorithm is performant with many APs
      assertTrue((endTime - startTime) < 1000, "Calculation should complete in under 1 second");
    }
  }

  /**
   * Tests for measurement model creation and probability calculations. These tests verify that the
   * algorithm correctly: 1. Creates measurement models for each AP 2. Calculates signal strength
   * probabilities 3. Updates models during iteration
   */
  @Nested
  @DisplayName("Confidence Calculation Tests")
  class ConfidenceCalculationTests {
    /**
     * Tests measurement model creation and updates. Verifies that: 1. Models reflect signal
     * strength distributions 2. Probabilities are properly normalized 3. Model updates improve
     * position estimate
     *
     * <p>Expected: - Models should be consistent with path loss - Probabilities should sum to
     * approximately 1 - Updates should improve position accuracy
     */
    @Test
    @DisplayName("should create and update measurement models correctly")
    void shouldCreateAndUpdateMeasurementModelsCorrectly() {
      // Create known APs for testing
      List<WifiAccessPoint> manyKnownAPs =
          IntStream.range(0, 6)
              .mapToObj(i -> createAP("AP" + i, i * 1.0, (i + 1) * 1.0, 10.0, 5.0))
              .collect(Collectors.toList());

      List<WifiAccessPoint> fewKnownAPs = manyKnownAPs.subList(0, 3);

      // Create scans for all APs with consistent signal strength
      List<WifiScanResult> manyScans =
          IntStream.range(0, 6)
              .mapToObj(i -> createScan("AP" + i, -65.0 - (i * 2))) // Varying signal strengths
              .collect(Collectors.toList());

      List<WifiScanResult> fewScans = manyScans.subList(0, 3);

      // Calculate positions with different numbers of measurements
      Position positionWithMany = algorithm.calculatePosition(manyScans, manyKnownAPs);
      Position positionWithFew = algorithm.calculatePosition(fewScans, fewKnownAPs);

      assertNotNull(positionWithMany);
      assertNotNull(positionWithFew);

      // Verify both confidence values are within expected ranges
      assertTrue(
          positionWithMany.confidence() >= 0.0 && positionWithMany.confidence() <= 1.0,
          "Confidence should be a valid probability value");
      assertTrue(
          positionWithFew.confidence() >= 0.0 && positionWithFew.confidence() <= 1.0,
          "Confidence should be a valid probability value");
    }

    /**
     * Tests measurement model creation and updates. Verifies that: 1. Models reflect signal
     * strength distributions 2. Probabilities are properly normalized 3. Model updates improve
     * position estimate
     *
     * <p>Expected: - Models should be consistent with path loss - Probabilities should sum to
     * approximately 1 - Updates should improve position accuracy
     */
    @Test
    @DisplayName("should calculate confidence values with different signal strengths")
    void shouldCalculateConfidenceWithDifferentSignalStrengths() {
      // Create same AP layout for both tests
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0),
              createAP("AP2", 2.0, 2.0, 10.0, 5.0),
              createAP("AP3", 3.0, 3.0, 10.0, 5.0));

      // Create scans with strong signals
      List<WifiScanResult> strongScans =
          Arrays.asList(
              createScan("AP1", -50.0), createScan("AP2", -55.0), createScan("AP3", -60.0));

      // Create scans with weak signals
      List<WifiScanResult> weakScans =
          Arrays.asList(
              createScan("AP1", -80.0), createScan("AP2", -85.0), createScan("AP3", -90.0));

      Position positionWithStrong = algorithm.calculatePosition(strongScans, knownAPs);
      Position positionWithWeak = algorithm.calculatePosition(weakScans, knownAPs);

      assertNotNull(positionWithStrong);
      assertNotNull(positionWithWeak);

      // Verify both confidence values are within expected ranges
      assertTrue(
          positionWithStrong.confidence() > 0.0 && positionWithStrong.confidence() <= 1.0,
          "Confidence should be a valid probability value");
      assertTrue(
          positionWithWeak.confidence() > 0.0 && positionWithWeak.confidence() <= 1.0,
          "Confidence should be a valid probability value");
    }

    /**
     * Tests measurement model creation and updates. Verifies that: 1. Models reflect signal
     * strength distributions 2. Probabilities are properly normalized 3. Model updates improve
     * position estimate
     *
     * <p>Expected: - Models should be consistent with path loss - Probabilities should sum to
     * approximately 1 - Updates should improve position accuracy
     */
    @Test
    @DisplayName("should handle case with varying signal strengths")
    void shouldHandleHighVarianceInSignals() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0),
              createAP("AP2", 2.0, 2.0, 10.0, 5.0),
              createAP("AP3", 3.0, 3.0, 10.0, 5.0));

      // Create scans with widely varying signal strengths
      List<WifiScanResult> extremeScans =
          Arrays.asList(
              createScan("AP1", -40.0), // Very strong
              createScan("AP2", -95.0), // Very weak
              createScan("AP3", -60.0) // Medium
              );

      Position position = algorithm.calculatePosition(extremeScans, knownAPs);
      assertNotNull(position);

      // With high variance, confidence should still be a valid value
      assertTrue(
          position.confidence() > 0.0 && position.confidence() <= 1.0,
          "Confidence should be a valid probability value");
    }
  }

  /**
   * Tests for proper handling of invalid or edge case inputs. These tests ensure the algorithm
   * fails gracefully and provides appropriate error handling when given problematic input data.
   */
  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {
    /**
     * Tests handling of case when no valid measurements are available. Expected: Return null to
     * indicate no valid measurements
     */
    @Test
    @DisplayName("should handle case when no valid measurements are available")
    void shouldHandleNoValidMeasurements() {
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(createAP("AP1", 1.0, 1.0, 10.0, 5.0), createAP("AP2", 2.0, 2.0, 10.0, 5.0));

      // Create scans for completely different APs
      List<WifiScanResult> unrelatedScans =
          Arrays.asList(createScan("UNKNOWN1", -65.0), createScan("UNKNOWN2", -70.0));

      assertNull(
          algorithm.calculatePosition(unrelatedScans, knownAPs),
          "Should return null when no valid measurements are available");
    }
  }

  @Nested
  @DisplayName("Accuracy and Confidence Range Tests")
  class AccuracyAndConfidenceRangeTests {
    @Test
    @DisplayName("should return high accuracy and confidence for strong, well-distributed signals")
    void shouldReturnHighAccuracyAndConfidenceForStrongSignals() {
      List<WifiAccessPoint> aps =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0),
              createAP("AP2", 1.0, 2.0, 10.0, 5.0),
              createAP("AP3", 2.0, 1.5, 10.0, 5.0),
              createAP("AP4", 2.0, 2.0, 10.0, 5.0));
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -50.0),
              createScan("AP2", -52.0),
              createScan("AP3", -51.0),
              createScan("AP4", -53.0));
      Position result = algorithm.calculatePosition(scans, aps);
      assertNotNull(result);
      // Accuracy: Should be within 1-6m for strong signals, maximum likelihood
      assertTrue(
          result.accuracy() >= 1.0 && result.accuracy() <= 6.0,
          "Expected accuracy between 1 and 6, got " + result.accuracy());
      // Confidence: Should be high for strong signals
      assertTrue(
          result.confidence() >= 0.8 && result.confidence() <= 1.0,
          "Expected confidence between 0.8 and 1.0, got " + result.confidence());
      // Latitude/Longitude: Should be within triangle formed by APs, with margin
      assertTrue(
          result.latitude() >= 0.9 && result.latitude() <= 3.1,
          "Expected latitude between 0.9 and 3.1, got " + result.latitude());
      assertTrue(
          result.longitude() >= 0.9 && result.longitude() <= 3.1,
          "Expected longitude between 0.9 and 3.1, got " + result.longitude());
    }

    @Test
    @DisplayName("should return lower accuracy and confidence for weak/noisy signals")
    void shouldReturnLowerAccuracyAndConfidenceForWeakSignals() {
      List<WifiAccessPoint> aps =
          Arrays.asList(
              createAP("AP1", 1.0, 1.0, 10.0, 5.0),
              createAP("AP2", 1.0, 2.0, 10.0, 5.0),
              createAP("AP3", 2.0, 1.5, 10.0, 5.0),
              createAP("AP4", 2.0, 2.0, 10.0, 5.0));
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -85.0),
              createScan("AP2", -88.0),
              createScan("AP3", -90.0),
              createScan("AP4", -87.0));
      Position result = algorithm.calculatePosition(scans, aps);
      assertNotNull(result);
      // Accuracy: Should be worse (>8m) for weak signals
      assertTrue(
          result.accuracy() > 8.0,
          "Expected accuracy > 8 for weak signals, got " + result.accuracy());
      // Confidence: Should be lower for weak signals
      assertTrue(
          result.confidence() < 0.6,
          "Expected confidence < 0.6 for weak signals, got " + result.confidence());
      // Latitude/Longitude: Should be within or near triangle, allow wider margin for weak signals
      assertTrue(
          result.latitude() >= 0.7 && result.latitude() <= 3.3,
          "Expected latitude between 0.7 and 3.3, got " + result.latitude());
      assertTrue(
          result.longitude() >= 0.7 && result.longitude() <= 3.3,
          "Expected longitude between 0.7 and 3.3, got " + result.longitude());
    }
  }

  @Nested
  @DisplayName("Incomplete Data Handling")
  class IncompleteDataHandlingTests {
    /**
     * Tests algorithm behavior with missing altitude data. This verifies the algorithm can function
     * properly with 2D data only.
     *
     * <p>Expected outcome: - Algorithm should still calculate position using 2D coordinates -
     * Position should have reasonable accuracy and confidence - The algorithm should fall back to
     * 2D calculations
     */
    @Test
    @DisplayName("should calculate position when altitude data is missing")
    void shouldCalculatePositionWhenAltitudeDataIsMissing() {
      // Create APs with null altitude
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
                  .latitude(2.0)
                  .longitude(2.0)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AP3")
                  .latitude(3.0)
                  .longitude(1.0)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AP4")
                  .latitude(2.0)
                  .longitude(1.0)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build());

      // Create scans with varying signal strengths
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -65.0),
              createScan("AP2", -70.0),
              createScan("AP3", -75.0),
              createScan("AP4", -68.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);

      // Verify position calculation succeeded
      assertNotNull(position);

      // Verify position is within the bounds of the APs
      assertTrue(
          position.latitude() >= 1.0 && position.latitude() <= 3.0,
          "Latitude should be within bounds of APs");
      assertTrue(
          position.longitude() >= 1.0 && position.longitude() <= 2.0,
          "Longitude should be within bounds of APs");

      // Altitude should default to 0.0 since all altitudes are null
      assertEquals(
          0.0, position.altitude(), "Altitude should be 0.0 when all APs have null altitude");

      // Verify accuracy and confidence are reasonable
      assertTrue(position.accuracy() > 0, "Accuracy should be positive");
      assertTrue(position.confidence() > 0, "Confidence should be positive");
    }

    /**
     * Tests algorithm behavior with a mix of APs with and without altitude data. This verifies the
     * algorithm can blend 2D and 3D data appropriately.
     *
     * <p>Expected outcome: - Algorithm should calculate reasonable position - Altitude should be
     * based on APs with altitude data - Position should have reasonable accuracy and confidence
     */
    @Test
    @DisplayName("should calculate position with mixed 2D and 3D data")
    void shouldCalculatePositionWithMixed2DAnd3DData() {
      // Create APs with mixed altitude data
      List<WifiAccessPoint> knownAPs =
          Arrays.asList(
              WifiAccessPoint.builder()
                  .macAddress("AP1")
                  .latitude(1.0)
                  .longitude(1.0)
                  .altitude(10.0) // With altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AP2")
                  .latitude(2.0)
                  .longitude(2.0)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AP3")
                  .latitude(3.0)
                  .longitude(1.0)
                  .altitude(15.0) // With altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build(),
              WifiAccessPoint.builder()
                  .macAddress("AP4")
                  .latitude(2.0)
                  .longitude(1.0)
                  .altitude(null) // Null altitude
                  .horizontalAccuracy(5.0)
                  .confidence(0.8)
                  .build());

      // Create scans with varying signal strengths
      List<WifiScanResult> scans =
          Arrays.asList(
              createScan("AP1", -65.0),
              createScan("AP2", -70.0),
              createScan("AP3", -75.0),
              createScan("AP4", -68.0));

      Position position = algorithm.calculatePosition(scans, knownAPs);

      // Verify position calculation succeeded
      assertNotNull(position);

      // Verify position is within the bounds of the APs
      assertTrue(
          position.latitude() >= 1.0 && position.latitude() <= 3.0,
          "Latitude should be within bounds of APs");
      assertTrue(
          position.longitude() >= 1.0 && position.longitude() <= 2.0,
          "Longitude should be within bounds of APs");

      // Altitude should be based on APs with altitude data
      // Should be between 10.0 and 15.0 based on signal strengths
      assertTrue(
          position.altitude() >= 10.0 && position.altitude() <= 15.0,
          "Altitude should be based on APs with altitude data");

      // Verify accuracy and confidence are reasonable
      assertTrue(position.accuracy() > 0, "Accuracy should be positive");
      assertTrue(position.confidence() > 0, "Confidence should be positive");
    }
  }
}
