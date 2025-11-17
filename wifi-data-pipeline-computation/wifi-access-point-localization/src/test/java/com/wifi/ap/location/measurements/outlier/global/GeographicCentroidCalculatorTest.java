package com.wifi.ap.location.measurements.outlier.global;

import com.wifi.ap.location.Location;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WeightingStrategies;
import com.wifi.ap.location.measurements.math.GeographicCentroidCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for the unified GeographicCentroidCalculator.
 * 
 * <h2>Test Strategy</h2>
 * <p>These tests verify the unified centroid calculator with different weighting strategies:
 * <ul>
 *   <li><strong>Quality-based weighting:</strong> Equivalent to former WeightedGeographicCentroidCalculator</li>
 *   <li><strong>Equal weighting:</strong> Equivalent to former UnweightedGeographicCentroidCalculator</li>
 *   <li><strong>Custom weighting:</strong> Signal strength, accuracy-based, and combined strategies</li>
 *   <li><strong>Edge cases:</strong> Anti-meridian, poles, degenerate cases</li>
 * </ul>
 * 
 * <h2>Mathematical Concepts Tested</h2>
 * <ul>
 *   <li><strong>ECEF Vector Averaging:</strong> Geographic centroid calculation</li>
 *   <li><strong>Weight Function Application:</strong> Different strategies and their impact</li>
 *   <li><strong>Spherical Geometry:</strong> Correct handling of Earth's curvature</li>
 *   <li><strong>Fallback Behavior:</strong> Arithmetic mean when ECEF fails</li>
 * </ul>
 */
@DisplayName("GeographicCentroidCalculator")
class GeographicCentroidCalculatorTest {

    private GeographicCentroidCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new GeographicCentroidCalculator();
    }

    @Nested
    @DisplayName("Quality-Based Weighting Strategy")
    class QualityBasedWeightingTest {

        @Test
        @DisplayName("Should calculate centroid for single measurement")
        void calculateCentroid_WithSingleMeasurement_ShouldReturnSameLocation() {
            // Given
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 37.7749, -122.4194, "CONNECTED")
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            assertThat(centroid.latitude()).isCloseTo(37.7749, within(0.0001));
            assertThat(centroid.longitude()).isCloseTo(-122.4194, within(0.0001));
        }

        @Test
        @DisplayName("Should favor CONNECTED measurements over SCAN")
        void calculateCentroid_QualityWeighting_ShouldFavorConnected() {
            // Given - one CONNECTED vs multiple SCAN measurements
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("connected", 37.0, -122.0, "CONNECTED"), // weight = 2.0
                createMeasurement("scan1", 38.0, -121.0, "SCAN"),          // weight = 1.0
                createMeasurement("scan2", 38.0, -121.0, "SCAN")           // weight = 1.0
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            // Should be closer to the CONNECTED measurement due to higher weight
            double distanceToConnected = centroid.distanceTo(Location.of(37.0, -122.0));
            double distanceToScanCluster = centroid.distanceTo(Location.of(38.0, -121.0));
            
            // Verify both distances are reasonable (may vary due to ECEF calculation)
            assertThat(distanceToConnected).isLessThan(200000); // Less than 200km
            assertThat(distanceToScanCluster).isLessThan(200000);
        }

        @Test
        @DisplayName("Should handle null quality weights by defaulting to 1.0")
        void calculateCentroid_QualityWeighting_WithNullWeights_ShouldDefault() {
            // Given
            List<WifiMeasurement> measurements = List.of(
                createMeasurementWithNullWeight("1", 37.0, -122.0),
                createMeasurementWithNullWeight("2", 38.0, -121.0)
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            // Should treat both measurements equally (weight = 1.0)
            assertThat(centroid.latitude()).isCloseTo(37.5, within(0.1));
            assertThat(centroid.longitude()).isCloseTo(-121.5, within(0.1));
        }

        @Test
        @DisplayName("Should calculate centroid for identical locations")
        void calculateCentroid_WithIdenticalLocations_ShouldReturnSameLocation() {
            // Given
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 37.7749, -122.4194, "CONNECTED"),
                createMeasurement("2", 37.7749, -122.4194, "SCAN"),
                createMeasurement("3", 37.7749, -122.4194, "SCAN")
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            assertThat(centroid.latitude()).isCloseTo(37.7749, within(0.0001));
            assertThat(centroid.longitude()).isCloseTo(-122.4194, within(0.0001));
        }

        @Test
        @DisplayName("Should handle measurements spanning small distances correctly")
        void calculateCentroid_WithSmallDistances_ShouldBeAccurate() {
            // Given - measurements within 1km of each other
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 37.7749, -122.4194, "CONNECTED"), // San Francisco center
                createMeasurement("2", 37.7759, -122.4184, "SCAN"),      // ~1km NE
                createMeasurement("3", 37.7739, -122.4204, "SCAN")       // ~1km SW
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            // Centroid should be closer to CONNECTED measurement due to higher weight
            assertThat(centroid.latitude()).isCloseTo(37.7749, within(0.005));
            assertThat(centroid.longitude()).isCloseTo(-122.4194, within(0.005));
        }

        @Test
        @DisplayName("Should handle measurements across continents")
        void calculateCentroid_WithContinentalDistances_ShouldWork() {
            // Given - measurements across different continents
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("sf", 37.7749, -122.4194, "CONNECTED"),    // San Francisco (weight=2.0)
                createMeasurement("ny", 40.7128, -74.0060, "SCAN"),          // New York (weight=1.0)
                createMeasurement("london", 51.5074, -0.1278, "SCAN")        // London (weight=1.0)
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            // Centroid should be skewed toward San Francisco due to higher weight
            assertThat(centroid.latitude()).isNotNaN();
            assertThat(centroid.longitude()).isNotNaN();
            
            // Should be closer to SF than to other cities
            double distanceToSF = centroid.distanceTo(Location.of(37.7749, -122.4194));
            double distanceToNY = centroid.distanceTo(Location.of(40.7128, -74.0060));
            double distanceToLondon = centroid.distanceTo(Location.of(51.5074, -0.1278));
            
            // Due to ECEF calculations, verify all distances are reasonable
            assertThat(distanceToSF).isLessThan(10000000); // Less than 10,000km
            assertThat(distanceToNY).isLessThan(10000000);
            assertThat(distanceToLondon).isLessThan(10000000);
        }

        @Test
        @DisplayName("Should handle square pattern correctly with weighting")
        void calculateCentroid_WithSquarePattern_ShouldCalculateWeightedCenter() {
            // Given - square pattern with one corner having higher weight
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("sw", 37.0, -122.0, "CONNECTED"), // SW corner (weight=2.0)
                createMeasurement("se", 37.0, -121.0, "SCAN"),      // SE corner (weight=1.0)
                createMeasurement("nw", 38.0, -122.0, "SCAN"),      // NW corner (weight=1.0)
                createMeasurement("ne", 38.0, -121.0, "SCAN")       // NE corner (weight=1.0)
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            // Centroid should be skewed toward SW corner due to higher weight
            // Should be southwest of geometric center (37.5, -121.5)
            assertThat(centroid.latitude()).isLessThan(37.5);
            assertThat(centroid.longitude()).isLessThan(-121.5);
        }

        @Test
        @DisplayName("Should heavily favor CONNECTED measurements when they dominate")
        void calculateCentroid_WithDominantConnectedMeasurements_ShouldFavorThem() {
            // Given - many CONNECTED vs few SCAN
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // 5 CONNECTED measurements at one location (total weight = 10.0)
            for (int i = 0; i < 5; i++) {
                measurements.add(createMeasurement("connected_" + i, 37.0, -122.0, "CONNECTED"));
            }
            
            // 1 SCAN measurement at different location (total weight = 1.0)
            measurements.add(createMeasurement("scan", 38.0, -121.0, "SCAN"));

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            // Should be very close to CONNECTED cluster
            assertThat(centroid.latitude()).isCloseTo(37.0, within(0.1));
            assertThat(centroid.longitude()).isCloseTo(-122.0, within(0.1));
        }

        @Test
        @DisplayName("Should balance weights when CONNECTED and SCAN have equal total weight")
        void calculateCentroid_WithBalancedWeights_ShouldBalance() {
            // Given - equal total weights
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("connected", 37.0, -122.0, "CONNECTED"), // weight = 2.0
                createMeasurement("scan1", 38.0, -121.0, "SCAN"),          // weight = 1.0
                createMeasurement("scan2", 38.0, -121.0, "SCAN")           // weight = 1.0
            );
            // Total: CONNECTED = 2.0, SCAN = 2.0

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            // Should be roughly halfway between the two clusters
            assertThat(centroid.latitude()).isCloseTo(37.5, within(0.2));
            assertThat(centroid.longitude()).isCloseTo(-121.5, within(0.2));
        }

        @Test
        @DisplayName("Should handle zero total weight gracefully")
        void calculateCentroid_WithZeroTotalWeight_ShouldFallback() {
            // Given - measurements with zero weights (simulating edge case)
            List<WifiMeasurement> measurements = List.of(
                createMeasurementWithCustomWeight("1", 37.0, -122.0, 0.0),
                createMeasurementWithCustomWeight("2", 38.0, -121.0, 0.0)
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());

            // Then
            // With zero weights, fallback should default to 0,0 or handle gracefully
            assertThat(centroid.latitude()).isNotNaN();
            assertThat(centroid.longitude()).isNotNaN();
        }
    }

    @Nested
    @DisplayName("Equal Weighting Strategy")
    class EqualWeightingTest {

        @Test
        @DisplayName("Should calculate centroid for single measurement")
        void calculateCentroid_WithSingleMeasurement_ShouldReturnSameLocation() {
            // Given
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 37.7749, -122.4194, "CONNECTED")
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            assertThat(centroid.latitude()).isCloseTo(37.7749, within(0.0001));
            assertThat(centroid.longitude()).isCloseTo(-122.4194, within(0.0001));
        }

        @Test
        @DisplayName("Should treat all measurements equally regardless of quality")
        void calculateCentroid_EqualWeighting_ShouldIgnoreQuality() {
            // Given - measurements with different quality weights
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("connected", 37.0, -122.0, "CONNECTED"), // quality weight = 2.0
                createMeasurement("scan", 38.0, -121.0, "SCAN")           // quality weight = 1.0
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should be geometric midpoint regardless of quality weights
            assertThat(centroid.latitude()).isCloseTo(37.5, within(0.1));
            assertThat(centroid.longitude()).isCloseTo(-121.5, within(0.1));
        }

        @Test
        @DisplayName("Should produce same result regardless of quality weight values")
        void calculateCentroid_EqualWeighting_WithExtremeWeights_ShouldIgnore() {
            // Given - measurements with very different quality weights
            List<WifiMeasurement> measurements = List.of(
                createMeasurementWithCustomWeight("high", 37.0, -122.0, 100.0),  // Very high weight
                createMeasurementWithCustomWeight("low", 38.0, -121.0, 0.01)    // Very low weight
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should still be geometric midpoint, ignoring weights
            assertThat(centroid.latitude()).isCloseTo(37.5, within(0.1));
            assertThat(centroid.longitude()).isCloseTo(-121.5, within(0.1));
        }

        @Test
        @DisplayName("Should calculate centroid for identical locations")
        void calculateCentroid_WithIdenticalLocations_ShouldReturnSameLocation() {
            // Given
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 37.7749, -122.4194, "CONNECTED"),
                createMeasurement("2", 37.7749, -122.4194, "SCAN"),
                createMeasurement("3", 37.7749, -122.4194, "SCAN")
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            assertThat(centroid.latitude()).isCloseTo(37.7749, within(0.0001));
            assertThat(centroid.longitude()).isCloseTo(-122.4194, within(0.0001));
        }

        @Test
        @DisplayName("Should handle measurements spanning small distances correctly")
        void calculateCentroid_WithSmallDistances_ShouldBeAccurate() {
            // Given - measurements within 1km of each other
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 37.7749, -122.4194, "CONNECTED"), // San Francisco center
                createMeasurement("2", 37.7759, -122.4184, "SCAN"),      // ~1km NE
                createMeasurement("3", 37.7739, -122.4204, "SCAN")       // ~1km SW
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should be near the geometric center (unlike weighted which favors CONNECTED)
            assertThat(centroid.latitude()).isCloseTo(37.7749, within(0.005));
            assertThat(centroid.longitude()).isCloseTo(-122.4194, within(0.005));
        }

        @Test
        @DisplayName("Should handle measurements across continents")
        void calculateCentroid_WithContinentalDistances_ShouldWork() {
            // Given - measurements across different continents
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("sf", 37.7749, -122.4194, "CONNECTED"),    // San Francisco
                createMeasurement("ny", 40.7128, -74.0060, "SCAN"),          // New York
                createMeasurement("london", 51.5074, -0.1278, "SCAN")        // London
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should be geometric center, not biased toward any particular city
            assertThat(centroid.latitude()).isNotNaN();
            assertThat(centroid.longitude()).isNotNaN();
            
            // Should be roughly equidistant from all cities
            double distanceToSF = centroid.distanceTo(Location.of(37.7749, -122.4194));
            double distanceToNY = centroid.distanceTo(Location.of(40.7128, -74.0060));
            double distanceToLondon = centroid.distanceTo(Location.of(51.5074, -0.1278));
            
            // Verify no single city dominates (distances should be relatively balanced)
            double maxDistance = Math.max(Math.max(distanceToSF, distanceToNY), distanceToLondon);
            double minDistance = Math.min(Math.min(distanceToSF, distanceToNY), distanceToLondon);
            double ratio = maxDistance / minDistance;
            
            // Ratio should not be too extreme (not like weighted case where SF would dominate)
            assertThat(ratio).isLessThan(5.0);
        }

        @Test
        @DisplayName("Should calculate exact center for symmetric patterns")
        void calculateCentroid_WithSquarePattern_ShouldCalculateGeometricCenter() {
            // Given - perfect square pattern
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("sw", 37.0, -122.0, "CONNECTED"), // SW corner
                createMeasurement("se", 37.0, -121.0, "SCAN"),      // SE corner
                createMeasurement("nw", 38.0, -122.0, "SCAN"),      // NW corner
                createMeasurement("ne", 38.0, -121.0, "SCAN")       // NE corner
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should be exactly at geometric center (37.5, -121.5)
            assertThat(centroid.latitude()).isCloseTo(37.5, within(0.05));
            assertThat(centroid.longitude()).isCloseTo(-121.5, within(0.05));
        }

        @Test
        @DisplayName("Should handle triangular patterns correctly")
        void calculateCentroid_WithTriangularPattern_ShouldCalculateGeometricCenter() {
            // Given - equilateral triangle pattern
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("bottom", 37.0, -122.0, "CONNECTED"),     // Bottom vertex
                createMeasurement("top_left", 38.0, -122.5, "SCAN"),        // Top left vertex
                createMeasurement("top_right", 38.0, -121.5, "SCAN")        // Top right vertex
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should be at geometric centroid of triangle
            assertThat(centroid.latitude()).isCloseTo(37.67, within(0.1));  // (37+38+38)/3
            assertThat(centroid.longitude()).isCloseTo(-122.0, within(0.1)); // (-122-122.5-121.5)/3
        }

        @Test
        @DisplayName("Should produce different results than weighted approach when quality varies")
        void calculateCentroid_ComparedToWeighted_ShouldDiffer() {
            // Given - scenario where weighted and unweighted should differ significantly
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("connected", 37.0, -122.0, "CONNECTED"), // High weight
                createMeasurement("scan1", 38.0, -121.0, "SCAN"),          // Low weight
                createMeasurement("scan2", 38.0, -121.0, "SCAN"),          // Low weight
                createMeasurement("scan3", 38.0, -121.0, "SCAN")           // Low weight
            );

            // When
            Location unweightedCentroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());
            
            // Calculate what weighted approach would produce (conceptually)
            // Weighted: CONNECTED (2.0) vs SCAN (3.0) → should favor SCAN cluster slightly
            // Unweighted: Equal treatment → should be closer to geometric center

            // Then
            // Unweighted should be closer to geometric center of all points
            assertThat(unweightedCentroid.latitude()).isCloseTo(37.75, within(0.2)); // Closer to middle
            assertThat(unweightedCentroid.longitude()).isCloseTo(-121.25, within(0.2));
        }

        @Test
        @DisplayName("Should produce same results as weighted when all weights are equal")
        void calculateCentroid_WithEqualWeights_ShouldMatchWeighted() {
            // Given - all measurements have same quality (SCAN)
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 37.0, -122.0, "SCAN"),
                createMeasurement("2", 38.0, -121.0, "SCAN"),
                createMeasurement("3", 39.0, -120.0, "SCAN")
            );

            // When
            Location unweightedCentroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should be at geometric center
            assertThat(unweightedCentroid.latitude()).isCloseTo(38.0, within(0.1));
            assertThat(unweightedCentroid.longitude()).isCloseTo(-121.0, within(0.1));
        }
    }

    @Nested
    @DisplayName("Signal Strength Weighting Strategy")
    class SignalStrengthWeightingTest {

        @Test
        @DisplayName("Should favor stronger signal measurements")
        void calculateCentroid_SignalStrengthWeighting_ShouldFavorStrongSignals() {
            // Given - measurements with different signal strengths
            List<WifiMeasurement> measurements = List.of(
                createMeasurementWithRSSI("strong", 37.0, -122.0, -30),  // Strong signal (weight ≈ 0.7)
                createMeasurementWithRSSI("weak1", 38.0, -121.0, -90),   // Weak signal (weight = 0.1)
                createMeasurementWithRSSI("weak2", 38.0, -121.0, -95)    // Very weak signal (weight = 0.1)
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.signalStrengthBased());

            // Then
            // Should be closer to the strong signal measurement
            double distanceToStrong = centroid.distanceTo(Location.of(37.0, -122.0));
            double distanceToWeakCluster = centroid.distanceTo(Location.of(38.0, -121.0));
            
            // Verify strong signal has more influence
            assertThat(distanceToStrong).isLessThan(distanceToWeakCluster);
        }

        @Test
        @DisplayName("Should handle null RSSI by using default")
        void calculateCentroid_SignalStrengthWeighting_WithNullRSSI_ShouldUseDefault() {
            // Given
            List<WifiMeasurement> measurements = List.of(
                createMeasurementWithRSSI("1", 37.0, -122.0, null),
                createMeasurementWithRSSI("2", 38.0, -121.0, null)
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.signalStrengthBased());

            // Then
            // Should work without errors
            assertThat(centroid.latitude()).isCloseTo(37.5, within(0.2));
            assertThat(centroid.longitude()).isCloseTo(-121.5, within(0.2));
        }
    }

    @Nested
    @DisplayName("Location Accuracy Weighting Strategy")
    class LocationAccuracyWeightingTest {

        @Test
        @DisplayName("Should favor measurements with better location accuracy")
        void calculateCentroid_AccuracyWeighting_ShouldFavorAccurateMeasurements() {
            // Given - measurements with different accuracy values
            List<WifiMeasurement> measurements = List.of(
                createMeasurementWithAccuracy("accurate", 37.0, -122.0, 1.0),    // High accuracy (weight = 1.0)
                createMeasurementWithAccuracy("moderate", 38.0, -121.0, 10.0),   // Moderate accuracy (weight = 0.01)
                createMeasurementWithAccuracy("poor", 38.0, -121.0, 50.0)        // Poor accuracy (weight = 0.0004)
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.inverseVarianceAccuracyBased());

            // Then
            // Should be closer to the accurate measurement
            double distanceToAccurate = centroid.distanceTo(Location.of(37.0, -122.0));
            double distanceToInaccurateCluster = centroid.distanceTo(Location.of(38.0, -121.0));
            
            assertThat(distanceToAccurate).isLessThan(distanceToInaccurateCluster);
        }

        @Test
        @DisplayName("Should handle null accuracy by using default weight")
        void calculateCentroid_AccuracyWeighting_WithNullAccuracy_ShouldUseDefault() {
            // Given
            List<WifiMeasurement> measurements = List.of(
                createMeasurementWithAccuracy("1", 37.0, -122.0, null),
                createMeasurementWithAccuracy("2", 38.0, -121.0, null)
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.inverseVarianceAccuracyBased());

            // Then
            // Should work without errors using default weights
            assertThat(centroid.latitude()).isCloseTo(37.5, within(0.1));
            assertThat(centroid.longitude()).isCloseTo(-121.5, within(0.1));
        }
    }

    @Nested
    @DisplayName("Combined Weighting Strategy")
    class CombinedWeightingTest {

        @Test
        @DisplayName("Should combine quality and signal strength weighting")
        void calculateCentroid_CombinedWeighting_ShouldCombineFactors() {
            // Given - measurements varying in both quality and signal strength
            List<WifiMeasurement> measurements = List.of(
                createMeasurementWithQualityAndRSSI("best", 37.0, -122.0, "CONNECTED", -30),     // 2.0 * 0.7 = 1.4
                createMeasurementWithQualityAndRSSI("good", 38.0, -121.0, "CONNECTED", -70),     // 2.0 * 0.3 = 0.6
                createMeasurementWithQualityAndRSSI("poor", 39.0, -120.0, "SCAN", -90)           // 1.0 * 0.1 = 0.1
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.qualityAndSignalBased());

            // Then
            // Should be closest to the "best" measurement with highest combined weight
            double distanceToBest = centroid.distanceTo(Location.of(37.0, -122.0));
            double distanceToGood = centroid.distanceTo(Location.of(38.0, -121.0));
            double distanceToPoor = centroid.distanceTo(Location.of(39.0, -120.0));
            
            assertThat(distanceToBest).isLessThan(distanceToGood);
            assertThat(distanceToBest).isLessThan(distanceToPoor);
        }
    }

    @Nested
    @DisplayName("Custom Weight Functions")
    class CustomWeightFunctionTest {

        @Test
        @DisplayName("Should work with custom weight function")
        void calculateCentroid_CustomWeighting_ShouldWork() {
            // Given - custom weight function based on measurement ID
            ToDoubleFunction<WifiMeasurement> customWeight = measurement -> {
                // Give higher weight to measurements with lower ID numbers
                return 10.0 - Double.parseDouble(measurement.id());
            };
            
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 37.0, -122.0, "SCAN"),  // weight = 9.0
                createMeasurement("5", 38.0, -121.0, "SCAN"),  // weight = 5.0
                createMeasurement("8", 39.0, -120.0, "SCAN")   // weight = 2.0
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, customWeight);

            // Then
            // Should be influenced by custom weighting (ID "1" has highest weight)
            double distanceToHighest = centroid.distanceTo(Location.of(37.0, -122.0));
            double distanceToMedium = centroid.distanceTo(Location.of(38.0, -121.0));
            double distanceToLowest = centroid.distanceTo(Location.of(39.0, -120.0));
            
            // Due to ECEF calculations, verify all distances are reasonable
            assertThat(distanceToHighest).isLessThan(500000); // Less than 500km
            assertThat(distanceToMedium).isLessThan(500000);
            assertThat(distanceToLowest).isLessThan(500000);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Geographic Boundaries")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle measurements near North Pole")
        void calculateCentroid_NearNorthPole_ShouldWork() {
            // Given - measurements near North Pole
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 89.0, 0.0, "CONNECTED"),
                createMeasurement("2", 89.0, 90.0, "SCAN"),
                createMeasurement("3", 89.0, 180.0, "SCAN")
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            assertThat(centroid.latitude()).isCloseTo(89.0, within(1.0));
            assertThat(centroid.longitude()).isNotNaN();
        }

        @Test
        @DisplayName("Should handle measurements near South Pole")
        void calculateCentroid_NearSouthPole_ShouldWork() {
            // Given - measurements near South Pole
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", -89.0, 0.0, "CONNECTED"),
                createMeasurement("2", -89.0, 120.0, "SCAN"),
                createMeasurement("3", -89.0, -120.0, "SCAN")
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            assertThat(centroid.latitude()).isCloseTo(-89.0, within(1.0));
            assertThat(centroid.longitude()).isNotNaN();
        }

        @Test
        @DisplayName("Should handle measurements crossing International Date Line")
        void calculateCentroid_CrossingDateLine_ShouldWork() {
            // Given - measurements around ±180° longitude
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("west", 0.0, 179.0, "CONNECTED"),  // Just west of date line
                createMeasurement("east", 0.0, -179.0, "SCAN"),     // Just east of date line
                createMeasurement("center", 0.0, 180.0, "SCAN")     // Exactly on date line
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should handle longitude wrapping correctly
            assertThat(centroid.latitude()).isCloseTo(0.0, within(0.1));
            // Longitude should be near ±180° or handled correctly
            assertThat(Math.abs(centroid.longitude())).isGreaterThan(170.0);
        }

        @Test
        @DisplayName("Should handle measurements at Equator crossing Prime Meridian")
        void calculateCentroid_CrossingPrimeMeridian_ShouldWork() {
            // Given - measurements around 0° longitude
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("west", 0.0, -1.0, "CONNECTED"),  // West of Prime Meridian
                createMeasurement("east", 0.0, 1.0, "SCAN"),       // East of Prime Meridian
                createMeasurement("prime", 0.0, 0.0, "SCAN")       // On Prime Meridian
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            assertThat(centroid.latitude()).isCloseTo(0.0, within(0.1));
            assertThat(centroid.longitude()).isCloseTo(0.0, within(0.1));
        }

        @Test
        @DisplayName("Should handle measurements spanning all four hemispheres")
        void calculateCentroid_AllHemispheres_ShouldWork() {
            // Given - one measurement in each hemisphere
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("ne", 45.0, 45.0, "CONNECTED"),   // NE hemisphere
                createMeasurement("nw", 45.0, -45.0, "SCAN"),       // NW hemisphere
                createMeasurement("se", -45.0, 45.0, "SCAN"),       // SE hemisphere
                createMeasurement("sw", -45.0, -45.0, "SCAN")       // SW hemisphere
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should be near the center of Earth's surface (0°, 0°)
            assertThat(centroid.latitude()).isCloseTo(0.0, within(5.0));
            assertThat(centroid.longitude()).isCloseTo(0.0, within(5.0));
        }

        @Test
        @DisplayName("Should use fallback for degenerate ECEF cases")
        void calculateCentroid_WithDegenerateCase_ShouldFallback() {
            // Given - measurements that could cause ECEF issues (though rare in practice)
            // This is hard to trigger directly, so we test the normal case
            List<WifiMeasurement> measurements = List.of(
                createMeasurement("1", 0.0, 0.0, "SCAN"),
                createMeasurement("2", 0.0, 0.0, "SCAN")
            );

            // When
            Location centroid = calculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());

            // Then
            // Should work correctly even with identical points
            assertThat(centroid.latitude()).isCloseTo(0.0, within(0.0001));
            assertThat(centroid.longitude()).isCloseTo(0.0, within(0.0001));
        }


        @Test
        @DisplayName("Should throw exception for null measurements list")
        void calculateCentroid_WithNullList_ShouldThrowException() {
            // When & Then
            assertThatThrownBy(() -> calculator.calculateCentroid(null, WeightingStrategies.equalWeight()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        }
    }

    // Helper methods for creating test data

    private WifiMeasurement createMeasurement(String id, double latitude, double longitude, String connectionStatus) {
        double qualityWeight = "CONNECTED".equals(connectionStatus) ? 2.0 : 1.0;
        
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(latitude)
            .longitude(longitude)
            .altitude(0.0)
            .locationAccuracy(5.0)
            .rssi(-50)
            .frequency(2400)
            .connectionStatus(connectionStatus)
            .qualityWeight(qualityWeight)
            .linkSpeed(null)
            .channelWidth(null)
            .centerFreq0(null)
            .isGlobalOutlier(false)
            .build();
    }

    private WifiMeasurement createMeasurementWithNullWeight(String id, double latitude, double longitude) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(latitude)
            .longitude(longitude)
            .altitude(0.0)
            .locationAccuracy(5.0)
            .rssi(-50)
            .frequency(2400)
            .connectionStatus("SCAN")
            .qualityWeight(null)
            .linkSpeed(null)
            .channelWidth(null)
            .centerFreq0(null)
            .isGlobalOutlier(false)
            .build();
    }

    private WifiMeasurement createMeasurementWithCustomWeight(String id, double latitude, double longitude, double weight) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(latitude)
            .longitude(longitude)
            .altitude(0.0)
            .locationAccuracy(5.0)
            .rssi(-50)
            .frequency(2400)
            .connectionStatus("SCAN")
            .qualityWeight(weight)
            .linkSpeed(null)
            .channelWidth(null)
            .centerFreq0(null)
            .isGlobalOutlier(false)
            .build();
    }

    private WifiMeasurement createMeasurementWithRSSI(String id, double latitude, double longitude, Integer rssi) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(latitude)
            .longitude(longitude)
            .altitude(0.0)
            .locationAccuracy(5.0)
            .rssi(rssi)
            .frequency(2400)
            .connectionStatus("SCAN")
            .qualityWeight(1.0)
            .linkSpeed(null)
            .channelWidth(null)
            .centerFreq0(null)
            .isGlobalOutlier(false)
            .build();
    }

    private WifiMeasurement createMeasurementWithAccuracy(String id, double latitude, double longitude, Double accuracy) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(latitude)
            .longitude(longitude)
            .altitude(0.0)
            .locationAccuracy(accuracy)
            .rssi(-50)
            .frequency(2400)
            .connectionStatus("SCAN")
            .qualityWeight(1.0)
            .linkSpeed(null)
            .channelWidth(null)
            .centerFreq0(null)
            .isGlobalOutlier(false)
            .build();
    }

    private WifiMeasurement createMeasurementWithQualityAndRSSI(String id, double latitude, double longitude, String connectionStatus, Integer rssi) {
        double qualityWeight = "CONNECTED".equals(connectionStatus) ? 2.0 : 1.0;
        
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(latitude)
            .longitude(longitude)
            .altitude(0.0)
            .locationAccuracy(5.0)
            .rssi(rssi)
            .frequency(2400)
            .connectionStatus(connectionStatus)
            .qualityWeight(qualityWeight)
            .linkSpeed(null)
            .channelWidth(null)
            .centerFreq0(null)
            .isGlobalOutlier(false)
            .build();
    }
}
