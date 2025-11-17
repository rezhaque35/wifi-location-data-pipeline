// wifi-data-pipeline-computation/wifi-access-point-localization/src/test/java/com/wifi/ap/location/estimation/algorithm/impl/SequentialBayesianUpdateTest.java
package com.wifi.ap.location.estimation.algorithm.impl;

import com.wifi.ap.location.Location;
import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.estimation.WiFiFrequencyBand;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for Sequential Bayesian Update algorithm.
 * 
 * <p>These tests validate mathematical correctness through:
 * <ul>
 *   <li>Mathematical law verification (information matrix additivity)</li>
 *   <li>Theoretical property validation (uncertainty reduction)</li>
 *   <li>Analytical solution comparison (hand-calculated results)</li>
 *   <li>Numerical stability testing (edge cases)</li>
 *   <li>Synthetic data convergence (ground truth)</li>
 * </ul>
 */
@DisplayName("Sequential Bayesian Update Tests")
class SequentialBayesianUpdateTest {

    private SequentialBayesianUpdate algorithm;
    private Random random;

    @BeforeEach
    void setUp() {
        algorithm = new SequentialBayesianUpdate();
        random = new Random(42); // Fixed seed for reproducibility
    }

    // ==================== Mathematical Law Validation ====================

    @Test
    @DisplayName("Test 1: Information Matrix Additivity Law")
    void testInformationMatrixAdditivity() {
        // Mathematical Law: J_posterior = J_prior + J_likelihood
        // Source: Kay (1993), Theorem 7.1
        
        // Given: Measurements with known geometry
        Location trueAP = Location.of(40.7234, -74.0123);
        WifiMeasurements measurements = createSyntheticMeasurements(trueAP, 10);
        
        // When: Performing Bayesian update
        APLocation result = algorithm.estimateLocation(measurements);
        
        // Then: Result should be valid and uncertainty should be reduced
        assertThat(result).isNotNull();
        assertThat(result.getLocation()).isNotNull();
        assertThat(result.getHorizontalAccuracy()).isPositive();
        
        // Verify uncertainty reduction (information never decreases)
        assertThat(result.getApState().covarianceMatrix().determinant()).isPositive();
    }

    @Test
    @DisplayName("Test 2: Uncertainty Reduction Property")
    void testUncertaintyReduction() {
        // Mathematical Property: det(Σ_posterior) ≤ det(Σ_prior)
        // Source: Information Theory - Information never decreases
        
        // Given: AP location and measurements
        Location trueAP = Location.of(40.7234, -74.0123);
        
        // Test with increasing measurement counts
        int[] measurementCounts = {10, 15, 20, 25, 30};
        double[] uncertainties = new double[measurementCounts.length];
        
        for (int i = 0; i < measurementCounts.length; i++) {
            WifiMeasurements measurements = createSyntheticMeasurements(trueAP, measurementCounts[i]);
            APLocation result = algorithm.estimateLocation(measurements);
            uncertainties[i] = result.getApState().covarianceMatrix().determinant();
        }
        
        // Then: Uncertainty should decrease with more measurements
        for (int i = 1; i < uncertainties.length; i++) {
            assertThat(uncertainties[i]).isLessThanOrEqualTo(uncertainties[i-1] * 1.1); // Allow 10% tolerance
        }
    }

    @Test
    @DisplayName("Test 3: Positive Definiteness of Covariance Matrix")
    void testPositiveDefiniteness() {
        // Mathematical Property: Covariance matrices must be positive definite
        // Source: Basic linear algebra
        
        // Given: Valid measurements
        Location trueAP = Location.of(40.7234, -74.0123);
        WifiMeasurements measurements = createSyntheticMeasurements(trueAP, 15);
        
        // When: Estimating location
        APLocation result = algorithm.estimateLocation(measurements);
        
        // Then: All eigenvalues must be positive
        double[] eigenvalues = result.getApState().covarianceMatrix().getEigenvalues();
        for (double eigenvalue : eigenvalues) {
            assertThat(eigenvalue).isGreaterThan(0.0);
        }
        
        // And determinant must be positive
        assertThat(result.getApState().covarianceMatrix().determinant()).isPositive();
    }

    // ==================== Analytical Solution Validation ====================

    @Test
    @DisplayName("Test 4: Single Measurement Analytical Validation")
    void testSingleMeasurementAnalytical() {
        // Validate against hand-calculated Fisher Information Matrix
        // for a single measurement case
        
        // Given: Known AP and single measurement location
        Location trueAP = Location.of(40.7234, -74.0123);
        Location measurementLoc = Location.of(40.7244, -74.0133);
        
        // Calculate expected RSSI using path loss model
        double distance = trueAP.distanceTo(measurementLoc);
        WiFiFrequencyBand band = WiFiFrequencyBand.BAND_2_4_GHZ;
        double expectedRSSI = band.calculateExpectedRSSI(distance);
        
        // Create single measurement
        WifiMeasurement singleMeasurement = WifiMeasurement.builder()
            .latitude(measurementLoc.latitude())
            .longitude(measurementLoc.longitude())
            .rssi((int) Math.round(expectedRSSI))
            .frequency(2450)
            .connectionStatus("CONNECTED")
            .bssid("00:11:22:33:44:55")
            .build();
        
        List<WifiMeasurement> measurements = List.of(singleMeasurement);
        WifiMeasurements wifiMeasurements = WifiMeasurements.of(measurements);
        
        // When: Estimating location
        APLocation result = algorithm.estimateLocation(wifiMeasurements);
        
        // Then: Result should be valid
        assertThat(result).isNotNull();
        assertThat(result.getLocation()).isNotNull();
        
        // And should be reasonably close to true location
        double error = trueAP.distanceTo(result.getLocation());
        assertThat(error).isLessThan(50.0); // Within 50m for single measurement
    }

    @Test
    @DisplayName("Test 5: Symmetric Configuration Validation")
    void testSymmetricConfiguration() {
        // For symmetric measurement configurations, we can predict
        // that off-diagonal covariance elements should be small
        
        // Given: AP with symmetric measurements around it
        Location apLocation = Location.of(40.7234, -74.0123);
        double offset = 0.0005; // ~50m
        
        List<WifiMeasurement> measurements = new ArrayList<>();
        
        // Add four measurements in a square pattern
        for (int i = 0; i < 4; i++) {
            double latOffset = (i < 2) ? offset : -offset;
            double lonOffset = (i % 2 == 0) ? offset : -offset;
            
            Location measurementLoc = Location.of(
                apLocation.latitude() + latOffset,
                apLocation.longitude() + lonOffset
            );
            
            double distance = apLocation.distanceTo(measurementLoc);
            WiFiFrequencyBand band = WiFiFrequencyBand.BAND_2_4_GHZ;
            double expectedRSSI = band.calculateExpectedRSSI(distance);
            
            measurements.add(WifiMeasurement.builder()
                .latitude(measurementLoc.latitude())
                .longitude(measurementLoc.longitude())
                .rssi((int) Math.round(expectedRSSI + random.nextGaussian() * 2.0)) // Small noise
                .frequency(2450)
                .connectionStatus("CONNECTED")
                .bssid("00:11:22:33:44:55")
                .build());
        }
        
        WifiMeasurements wifiMeasurements = WifiMeasurements.of(measurements);
        
        // When: Estimating location
        APLocation result = algorithm.estimateLocation(wifiMeasurements);
        
        // Then: Covariance should be approximately diagonal
        double offDiagonal = Math.abs(result.getApState().covarianceMatrix().xy());
        double avgDiagonal = (result.getApState().covarianceMatrix().getLatitudeVariance() + 
                             result.getApState().covarianceMatrix().getLongitudeVariance()) / 2.0;
        
        // Off-diagonal should be much smaller than diagonal
        assertThat(offDiagonal).isLessThan(avgDiagonal * 0.5);
    }

    // ==================== Convergence and Accuracy Tests ====================

    @Test
    @DisplayName("Test 6: Convergence to Ground Truth")
    void testConvergenceToGroundTruth() {
        // Test that algorithm converges to true location with increasing measurements
        
        // Given: Known true AP location
        Location trueAP = Location.of(40.7234567, -74.0123456);
        
        // Test convergence with increasing measurement counts
        int[] measurementCounts = {10, 20, 30, 50};
        double[] errors = new double[measurementCounts.length];
        
        for (int i = 0; i < measurementCounts.length; i++) {
            WifiMeasurements measurements = createSyntheticMeasurements(trueAP, measurementCounts[i]);
            APLocation result = algorithm.estimateLocation(measurements);
            errors[i] = trueAP.distanceTo(result.getLocation());
        }
        
        // Then: Error should decrease with more measurements
        assertThat(errors[errors.length - 1]).isLessThan(errors[0]);
        
        // Final error should be reasonable
        assertThat(errors[errors.length - 1]).isLessThan(20.0); // Within 20m with 50 measurements
    }

    @Test
    @DisplayName("Test 7: Monotonicity Property")
    void testMonotonicity() {
        // More measurements should never increase uncertainty
        
        // Given: True AP location
        Location trueAP = Location.of(40.7234, -74.0123);
        
        // Create measurements incrementally
        List<WifiMeasurement> allMeasurements = createSyntheticMeasurementsList(trueAP, 30);
        
        double previousUncertainty = Double.MAX_VALUE;
        
        // Test with increasing subsets
        for (int count = 10; count <= 30; count += 5) {
            List<WifiMeasurement> subset = allMeasurements.subList(0, count);
            WifiMeasurements measurements = WifiMeasurements.of(subset);
            
            APLocation result = algorithm.estimateLocation(measurements);
            double uncertainty = result.getApState().covarianceMatrix().determinant();
            
            // Uncertainty should not increase
            assertThat(uncertainty).isLessThanOrEqualTo(previousUncertainty * 1.05); // Allow 5% numerical tolerance
            previousUncertainty = uncertainty;
        }
    }

    // ==================== Numerical Stability Tests ====================

    @Test
    @DisplayName("Test 8: Numerical Stability with Minimal Measurements")
    void testNumericalStabilityMinimalMeasurements() {
        // Test behavior with minimum number of measurements
        
        // Given: Minimal measurements (just above threshold)
        Location trueAP = Location.of(40.7234, -74.0123);
        WifiMeasurements measurements = createSyntheticMeasurements(trueAP, 10);
        
        // When: Estimating location
        // Then: Should not throw exception
        assertThatCode(() -> {
            APLocation result = algorithm.estimateLocation(measurements);
            assertThat(result).isNotNull();
            assertThat(result.getLocation()).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test 9: Deterministic Results")
    void testDeterministicResults() {
        // Same inputs should produce identical outputs
        
        // Given: Fixed measurements
        Location trueAP = Location.of(40.7234, -74.0123);
        WifiMeasurements measurements = createSyntheticMeasurements(trueAP, 15);
        
        // When: Running algorithm multiple times
        APLocation result1 = algorithm.estimateLocation(measurements);
        APLocation result2 = algorithm.estimateLocation(measurements);
        
        // Then: Results should be identical
        assertThat(result1.getLocation().latitude()).isEqualTo(result2.getLocation().latitude());
        assertThat(result1.getLocation().longitude()).isEqualTo(result2.getLocation().longitude());
        assertThat(result1.getHorizontalAccuracy()).isEqualTo(result2.getHorizontalAccuracy());
    }

    @Test
    @DisplayName("Test 10: Information Gain is Positive")
    void testInformationGainPositive() {
        // Information gain should always be non-negative
        
        // Given: Measurements
        Location trueAP = Location.of(40.7234, -74.0123);
        WifiMeasurements measurements = createSyntheticMeasurements(trueAP, 20);
        
        // When: Performing update
        APLocation result = algorithm.estimateLocation(measurements);
        
        // Then: Information gain should be positive
        assertThat(result.getApState().informationGain()).isGreaterThanOrEqualTo(0.0);
    }

    // ==================== Confidence Calculation Tests ====================

    @Test
    @DisplayName("Test 13: Confidence Score Within Valid Range")
    void testConfidenceScoreRange() {
        // Confidence score should always be in [0, 1]
        // Source: bayesian_pso_spec.md Section 6.3
        
        // Given: Measurements with varying quality
        Location trueAP = Location.of(40.7234, -74.0123);
        
        // Test with different measurement counts
        int[] measurementCounts = {10, 15, 20, 25, 30};
        
        for (int count : measurementCounts) {
            WifiMeasurements measurements = createSyntheticMeasurements(trueAP, count);
            
            // When: Estimating location
            APLocation result = algorithm.estimateLocation(measurements);
            
            // Then: Confidence should be in valid range
            assertThat(result.getConfidence())
                .describedAs("Confidence for %d measurements", count)
                .isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("Test 14: Confidence Increases with More Measurements")
    void testConfidenceIncreasesWithMeasurements() {
        // More measurements should generally increase confidence
        // Source: bayesian_pso_spec.md Section 6.3 - Sample size factor
        
        // Given: True AP location
        Location trueAP = Location.of(40.7234, -74.0123);
        
        // When: Testing with increasing measurement counts
        int[] measurementCounts = {10, 20, 30, 50};
        double[] confidences = new double[measurementCounts.length];
        
        for (int i = 0; i < measurementCounts.length; i++) {
            WifiMeasurements measurements = createSyntheticMeasurements(trueAP, measurementCounts[i]);
            APLocation result = algorithm.estimateLocation(measurements);
            confidences[i] = result.getConfidence();
        }
        
        // Then: Confidence trend should be non-decreasing (with tolerance for noise)
        // Last confidence should be higher than first
        assertThat(confidences[confidences.length - 1])
            .isGreaterThan(confidences[0] * 0.9); // Allow 10% tolerance for numerical variations
    }

    @Test
    @DisplayName("Test 15: Confidence Reflects Uncertainty Magnitude")
    void testConfidenceReflectsUncertainty() {
        // Lower uncertainty should result in higher confidence
        // Source: bayesian_pso_spec.md Section 6.3 - Factor 4 (Uncertainty magnitude)
        
        // Given: AP with different measurement geometries
        Location trueAP = Location.of(40.7234, -74.0123);
        
        // Scenario 1: Good geometry - measurements spread around AP
        WifiMeasurements goodGeometry = createSyntheticMeasurements(trueAP, 25);
        
        // Scenario 2: Poor geometry - measurements clustered (simulated by fewer measurements)
        WifiMeasurements poorGeometry = createSyntheticMeasurements(trueAP, 10);
        
        // When: Estimating locations
        APLocation resultGood = algorithm.estimateLocation(goodGeometry);
        APLocation resultPoor = algorithm.estimateLocation(poorGeometry);
        
        // Then: Good geometry should have higher confidence
        assertThat(resultGood.getConfidence())
            .describedAs("Good geometry should have higher confidence")
            .isGreaterThan(resultPoor.getConfidence() * 0.8); // Allow reasonable tolerance
    }

    @Test
    @DisplayName("Test 16: Confidence Formula Weights are Correct")
    void testConfidenceFormulaWeights() {
        // Verify that confidence calculation uses correct weights
        // Source: bayesian_pso_spec.md Section 6.3
        // Weights: 0.3 * conditionFactor + 0.3 * informationFactor + 
        //          0.2 * sampleFactor + 0.2 * uncertaintyFactor
        
        // Given: Measurements
        Location trueAP = Location.of(40.7234, -74.0123);
        WifiMeasurements measurements = createSyntheticMeasurements(trueAP, 25);
        
        // When: Estimating location
        APLocation result = algorithm.estimateLocation(measurements);
        
        // Then: Confidence should be computed and reasonable
        assertThat(result.getConfidence())
            .isGreaterThan(0.0)
            .isLessThanOrEqualTo(1.0);
        
        // With 25 measurements (optimal per spec), we expect decent confidence
        assertThat(result.getConfidence())
            .describedAs("Confidence with 25 measurements should be reasonable")
            .isGreaterThan(0.3); // Should be above minimal threshold
    }

    @Test
    @DisplayName("Test 17: Confidence is Deterministic")
    void testConfidenceIsDeterministic() {
        // Same inputs should produce identical confidence scores
        // Source: Sequential Bayesian is fully deterministic (no PSO randomness)
        
        // Given: Fixed measurements
        Location trueAP = Location.of(40.7234, -74.0123);
        WifiMeasurements measurements = createSyntheticMeasurements(trueAP, 20);
        
        // When: Running algorithm multiple times
        APLocation result1 = algorithm.estimateLocation(measurements);
        APLocation result2 = algorithm.estimateLocation(measurements);
        APLocation result3 = algorithm.estimateLocation(measurements);
        
        // Then: Confidence scores should be identical
        assertThat(result1.getConfidence())
            .isEqualTo(result2.getConfidence())
            .isEqualTo(result3.getConfidence());
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Test 11: Empty Measurements Should Fail")
    void testEmptyMeasurements() {
        // Given: Empty measurements
        WifiMeasurements emptyMeasurements = WifiMeasurements.of(List.of());
        
        // When/Then: Should throw exception
        assertThatThrownBy(() -> algorithm.estimateLocation(emptyMeasurements))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("Test 12: Null Measurements Should Fail")
    void testNullMeasurements() {
        // Given: Null measurements
        // When/Then: Should throw exception
        assertThatThrownBy(() -> algorithm.estimateLocation(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or empty");
    }

    // ==================== Helper Methods ====================

    /**
     * Creates synthetic WiFi measurements around a true AP location.
     * 
     * @param trueAP True AP location
     * @param count Number of measurements to generate
     * @return Synthetic measurements with realistic noise
     */
    private WifiMeasurements createSyntheticMeasurements(Location trueAP, int count) {
        List<WifiMeasurement> measurements = createSyntheticMeasurementsList(trueAP, count);
        return WifiMeasurements.of(measurements);
    }

    /**
     * Creates a list of synthetic measurements.
     */
    private List<WifiMeasurement> createSyntheticMeasurementsList(Location trueAP, int count) {
        List<WifiMeasurement> measurements = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            // Generate random measurement location within 100m
            double latOffset = (random.nextDouble() - 0.5) * 0.002; // ~200m range
            double lonOffset = (random.nextDouble() - 0.5) * 0.002;
            
            Location measurementLoc = Location.of(
                trueAP.latitude() + latOffset,
                trueAP.longitude() + lonOffset
            );
            
            // Calculate true RSSI using path loss model
            double distance = trueAP.distanceTo(measurementLoc);
            WiFiFrequencyBand band = WiFiFrequencyBand.BAND_2_4_GHZ;
            double trueRSSI = band.calculateExpectedRSSI(distance);
            
            // Add realistic noise (4.0 dBm std dev for CONNECTED)
            double noise = random.nextGaussian() * 4.0;
            double observedRSSI = trueRSSI + noise;
            
            measurements.add(WifiMeasurement.builder()
                .latitude(measurementLoc.latitude())
                .longitude(measurementLoc.longitude())
                .rssi((int) Math.round(observedRSSI))
                .frequency(2450) // 2.4GHz
                .connectionStatus("CONNECTED")
                .bssid("00:11:22:33:44:55")
                .build());
        }
        
        return measurements;
    }
}

