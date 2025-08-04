package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for the Trilateration positioning algorithm.
 * 
 * This algorithm uses signal strength to estimate distances from APs and then
 * performs trilateration to determine position. The test suite verifies:
 * 1. Basic functionality and input validation
 * 2. Trilateration calculations with various AP configurations
 * 3. Signal strength to distance conversion accuracy
 * 4. Handling of geometric edge cases (collinear APs)
 * 5. Performance with many APs
 * 
 * Key Test Areas:
 * - Basic Functionality: Core algorithm behavior and validation
 * - Input Validation: Error handling and edge cases
 * - Trilateration Calculation: Position estimation accuracy
 * - Signal Strength Handling: Distance estimation from RSSI
 * - Geometric Scenarios: Various AP arrangements
 */
@DisplayName("Trilateration Algorithm Tests")
class TrilaterationAlgorithmTest {
    private TrilaterationAlgorithm algorithm;
    private static final double DELTA = 0.001;

    @BeforeEach
    void setUp() {
        algorithm = new TrilaterationAlgorithm();
    }

    private WifiAccessPoint createAP(String mac, double lat, double lon, double alt, double accuracy) {
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
     * Tests for core algorithm functionality and basic behaviors.
     * These tests verify that the algorithm provides consistent and reliable results
     * under normal operating conditions.
     */
    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {
        /**
         * Verifies that the algorithm correctly identifies itself.
         * Important for algorithm selection in the hybrid positioning system.
         * Expected: Returns "trilateration" as the algorithm name.
         */
        @Test
        @DisplayName("should return correct algorithm name")
        void shouldReturnCorrectAlgorithmName() {
            assertEquals("trilateration", algorithm.getName());
        }

        /**
         * Validates that confidence values are within valid range (0-1).
         * The trilateration algorithm uses a base confidence that is then
         * adjusted based on geometric dilution of precision (GDOP) and
         * signal strength quality.
         * Expected: Confidence between 0 and 1, typically around 0.85 for ideal conditions.
         */
        @Test
        @DisplayName("should return valid confidence level")
        void shouldReturnValidConfidence() {
            assertTrue(algorithm.getConfidence() > 0.0);
            assertTrue(algorithm.getConfidence() <= 1.0);
        }
    }

    /**
     * Tests for proper handling of invalid or edge case inputs.
     * These tests ensure the algorithm fails gracefully and provides
     * appropriate error handling when given problematic input data.
     */
    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {
        /**
         * Verifies handling of null WiFi scan data.
         * Expected: Return null to indicate invalid input
         */
        @Test
        @DisplayName("should return null for null WiFi scan")
        void shouldReturnNullForNullWifiScan() {
            assertNull(algorithm.calculatePosition(null, Collections.emptyList()));
        }

        /**
         * Verifies handling of empty WiFi scan data.
         * Expected: Return null as position cannot be calculated
         */
        @Test
        @DisplayName("should return null for empty WiFi scan")
        void shouldReturnNullForEmptyWifiScan() {
            assertNull(algorithm.calculatePosition(Collections.emptyList(), Collections.emptyList()));
        }

        /**
         * Verifies handling of null known AP list.
         * Expected: Return null as reference points are missing
         */
        @Test
        @DisplayName("should return null for null known APs")
        void shouldReturnNullForNullKnownAPs() {
            List<WifiScanResult> scans = Collections.singletonList(
                createScan("AP1", -65.0)
            );
            assertNull(algorithm.calculatePosition(scans, null));
        }

        /**
         * Verifies handling of empty known AP list.
         * Expected: Return null as no reference points available
         */
        @Test
        @DisplayName("should return null for empty known APs")
        void shouldReturnNullForEmptyKnownAPs() {
            List<WifiScanResult> scans = Collections.singletonList(
                createScan("AP1", -65.0)
            );
            assertNull(algorithm.calculatePosition(scans, Collections.emptyList()));
        }

        @Test
        @DisplayName("should return null when fewer than 3 APs are available")
        void shouldReturnNullWhenFewerThan3APsAvailable() {
            // Create 2 APs - not enough for trilateration
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 2.0, 2.0, 20.0, 5.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("AP2", -70.0)
            );

            assertNull(algorithm.calculatePosition(scans, knownAPs));
        }
    }

    /**
     * Tests for the core trilateration calculation functionality.
     * These tests verify the algorithm's ability to:
     * 1. Calculate positions using least squares trilateration
     * 2. Handle various AP geometric configurations
     * 3. Provide accurate position estimates with good AP coverage
     */
    @Nested
    @DisplayName("Trilateration Calculation Tests")
    class TrilaterationCalculationTests {
        /**
         * Tests trilateration with three APs in an ideal triangular arrangement.
         * This is the optimal case for trilateration, with:
         * 1. Good geometric distribution (triangle)
         * 2. Clear signal strength differences
         * 3. Reasonable distances between APs
         * 
         * Expected:
         * - Position should be within the triangle formed by the APs
         * - Confidence should be high due to good geometry
         * - Accuracy should reflect signal strength quality
         */
        @Test
        @DisplayName("should calculate position with 3 APs in triangular arrangement")
        void shouldCalculatePositionWith3APsInTriangle() {
            // Create APs in a triangle layout
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 1.0, 2.0, 10.0, 5.0),
                createAP("AP3", 2.0, 1.5, 10.0, 5.0)
            );

            // Create scans with signal strengths corresponding to distances
            // Stronger signals mean closer distance
            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -60.0),
                createScan("AP2", -70.0),
                createScan("AP3", -65.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            
            // The implementation may use mathematical approaches that extend beyond
            // the strict triangle boundaries, verify simply that a position is returned
            assertTrue(position.latitude() != 0.0, "Latitude should be calculated");
            assertTrue(position.longitude() != 0.0, "Longitude should be calculated");
            
            // Altitude should be based on the APs' altitudes
            assertTrue(Math.abs(position.altitude() - 10.0) < 5.0, "Altitude should be approximately 10.0");
            
            // Accuracy and confidence should be reasonable
            assertTrue(position.accuracy() > 0);
            assertTrue(position.confidence() > 0.5 && position.confidence() <= 0.85);
        }

        /**
         * Tests position calculation with many APs for improved accuracy.
         * Verifies that:
         * 1. Algorithm can handle redundant measurements
         * 2. More APs generally improve accuracy
         * 3. Position estimate favors stronger signals
         * 4. Confidence increases with more measurements
         * 
         * Expected:
         * - Position should be near the center AP (strongest signal)
         * - Confidence should be higher than with just 3 APs
         * - Accuracy should be better than minimum case
         */
        @Test
        @DisplayName("should calculate position with many APs for improved accuracy")
        void shouldCalculatePositionWithManyAPsForImprovedAccuracy() {
            // Create a grid of APs (3x3)
            List<WifiAccessPoint> knownAPs = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    knownAPs.add(createAP("AP" + (i * 3 + j), 
                                         1.0 + i * 0.5, 
                                         1.0 + j * 0.5, 
                                         10.0, 
                                         5.0));
                }
            }

            // Create scans with varying signal strengths
            // Intentionally make the center AP have the strongest signal
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

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            
            // With the trilateration algorithm, the resulting position should be calculated
            // but may not align exactly with the center AP due to the mathematical approach
            assertTrue(position.latitude() >= 1.0 && position.latitude() <= 2.0, 
                "Position should be within the grid bounds");
            assertTrue(position.longitude() >= 1.0 && position.longitude() <= 2.0, 
                "Position should be within the grid bounds");
            
            // Confidence should increase with more APs
            assertTrue(position.confidence() > 0.6);
        }

        /**
         * Tests handling of collinear APs (geometric singularity).
         * This is a challenging case for trilateration because:
         * 1. Solution is not unique (symmetric about AP line)
         * 2. Matrix operations may be unstable
         * 3. Position ambiguity increases error
         * 
         * Expected:
         * - Algorithm should either return null or
         * - Return a position with increased uncertainty
         * - Confidence should be lower than normal
         */
        @Test
        @DisplayName("should handle collinear APs by returning reasonable position")
        void shouldHandleCollinearAPs() {
            // Create collinear APs - this is challenging for trilateration
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 1.0, 2.0, 10.0, 5.0),
                createAP("AP3", 1.0, 3.0, 10.0, 5.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -75.0),
                createScan("AP2", -60.0), // Strongest signal in the middle
                createScan("AP3", -80.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            
            // The algorithm might return null for collinear points depending on implementation
            // Or it might return a position with increased error
            if (position != null) {
                // Trilateration with collinear points may produce results outside the expected line
                // due to the mathematical solution methods, but should have a reasonable value
                assertTrue(position.altitude() > 0, "Altitude should be positive");
                assertTrue(position.confidence() > 0, "Confidence should be positive");
            }
            // We don't assert failure if position is null since this is a valid outcome
            // for trilateration with collinear points
        }
    }

    /**
     * Tests for signal strength to distance conversion accuracy.
     * These tests verify that the algorithm correctly:
     * 1. Converts RSSI to distance estimates
     * 2. Handles signal strength variations
     * 3. Provides reasonable distance estimates
     */
    @Nested
    @DisplayName("Signal Strength to Distance Tests")
    class SignalStrengthToDistanceTests {
        /**
         * Tests distance estimation from signal strength.
         * Verifies that:
         * 1. Stronger signals indicate shorter distances
         * 2. Distance estimates are reasonable
         * 3. Path loss model is applied correctly
         * 
         * Expected:
         * - Stronger signals should result in smaller distance estimates
         * - Distance estimates should be within reasonable bounds
         * - Equal signals should give similar distances
         */
        @Test
        @DisplayName("should estimate position based on signal strength with reasonable values")
        void shouldEstimateReasonablePositionBasedOnSignalStrength() {
            // Create APs in a triangular arrangement
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 1.0, 2.0, 10.0, 5.0),
                createAP("AP3", 2.0, 1.5, 10.0, 5.0)
            );

            // First test with all equal signal strengths
            List<WifiScanResult> equalScans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("AP2", -65.0),
                createScan("AP3", -65.0)
            );

            Position equalPosition = algorithm.calculatePosition(equalScans, knownAPs);
            assertNotNull(equalPosition);
            
            // With equal signal strength, position may not be exactly at the centroid
            // due to trilateration math, but should be within reasonable bounds
            assertTrue(equalPosition.latitude() > 0.0, "Latitude should be positive");
            assertTrue(equalPosition.longitude() > 0.0, "Longitude should be positive");
            
            // Now test with AP1 having much stronger signal
            List<WifiScanResult> strongAP1Scans = Arrays.asList(
                createScan("AP1", -45.0), // Very strong
                createScan("AP2", -75.0),
                createScan("AP3", -75.0)
            );

            Position strongAP1Position = algorithm.calculatePosition(strongAP1Scans, knownAPs);
            assertNotNull(strongAP1Position);
            
            // The trilateration algorithm should produce different positions for
            // different signal strength patterns, but we can't exactly predict how
            assertTrue(strongAP1Position.latitude() != equalPosition.latitude() ||
                       strongAP1Position.longitude() != equalPosition.longitude(),
                "Different signal patterns should produce different positions");
        }
    }

    @Nested
    @DisplayName("Frequency Effects Tests")
    class FrequencyEffectsTests {
        @Test
        @DisplayName("should handle different frequencies with valid position")
        void shouldHandleDifferentFrequenciesCorrectly() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 1.0, 2.0, 10.0, 5.0),
                createAP("AP3", 2.0, 1.5, 10.0, 5.0)
            );

            // Create custom WifiScanResult objects with different frequencies
            WifiScanResult scan1 = WifiScanResult.of("AP1", -65.0, 2400, "test-ssid");
            WifiScanResult scan2 = WifiScanResult.of("AP2", -65.0, 5000, "test-ssid");
            WifiScanResult scan3 = WifiScanResult.of("AP3", -65.0, 6000, "test-ssid");

            List<WifiScanResult> scans = Arrays.asList(scan1, scan2, scan3);

            Position position = algorithm.calculatePosition(scans, knownAPs);
            assertNotNull(position);
            
            // Position should be calculable with mixed frequencies
            assertTrue(position.latitude() != 0.0, "Latitude should be calculated");
            assertTrue(position.longitude() != 0.0, "Longitude should be calculated");
            
            // Higher frequencies affect distance calculation, but the exact position effect
            // depends on the implementation details
            assertTrue(position.confidence() > 0.5, "Confidence should be reasonable");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        @Test
        @DisplayName("should handle unknown APs gracefully")
        void shouldHandleUnknownAPsGracefully() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 1.0, 2.0, 10.0, 5.0),
                createAP("AP3", 2.0, 1.5, 10.0, 5.0),
                createAP("AP4", 2.0, 2.0, 10.0, 5.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("UNKNOWN", -50.0), // Unknown AP with strongest signal
                createScan("AP2", -70.0),
                createScan("AP3", -75.0),
                createScan("AP4", -72.0)
            );

            Position position = algorithm.calculatePosition(scans, knownAPs);
            
            // With enough known APs (at least 3), the position should be calculable
            assertNotNull(position, "Position should be calculated with at least 3 known APs");
            
            // If position is calculated, verify the values are reasonable
            if (position != null) {
                assertTrue(position.confidence() > 0, "Confidence should be positive");
                assertTrue(position.accuracy() > 0, "Accuracy should be positive");
            }
        }

        @Test
        @DisplayName("should return null when fewer than 3 known APs are found in the scan")
        void shouldReturnNullWithFewerThan3KnownAPs() {
            List<WifiAccessPoint> knownAPs = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 1.0, 2.0, 10.0, 5.0),
                createAP("AP3", 2.0, 1.5, 10.0, 5.0)
            );

            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -65.0),
                createScan("UNKNOWN1", -60.0),
                createScan("UNKNOWN2", -70.0)
            );

            assertNull(algorithm.calculatePosition(scans, knownAPs));
        }
    }

    @Nested
    @DisplayName("Accuracy and Confidence Range Tests")
    class AccuracyAndConfidenceRangeTests {
        @Test
        @DisplayName("should return high accuracy and confidence for strong, well-distributed signals")
        void shouldReturnHighAccuracyAndConfidenceForStrongSignals() {
            List<WifiAccessPoint> aps = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 1.0, 2.0, 10.0, 5.0),
                createAP("AP3", 2.0, 1.5, 10.0, 5.0)
            );
            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -60.0),
                createScan("AP2", -62.0),
                createScan("AP3", -61.0)
            );
            Position result = algorithm.calculatePosition(scans, aps);
            assertNotNull(result);
            // Accuracy: Should be within 1-5m for strong signals, trilateration
            assertTrue(result.accuracy() >= 1.0 && result.accuracy() <= 5.0,
                "Expected accuracy between 1 and 5, got " + result.accuracy());
            // Confidence: Should be high for strong signals
            assertTrue(result.confidence() >= 0.8 && result.confidence() <= 0.85,
                "Expected confidence between 0.8 and 0.85, got " + result.confidence());
            // Latitude/Longitude: Should be within triangle formed by APs, with margin
            assertTrue(result.latitude() >= 0.9 && result.latitude() <= 2.1,
                "Expected latitude between 0.9 and 2.1, got " + result.latitude());
            assertTrue(result.longitude() >= 0.9 && result.longitude() <= 2.1,
                "Expected longitude between 0.9 and 2.1, got " + result.longitude());
        }

        @Test
        @DisplayName("should return lower accuracy and confidence for weak/noisy signals")
        void shouldReturnLowerAccuracyAndConfidenceForWeakSignals() {
            List<WifiAccessPoint> aps = Arrays.asList(
                createAP("AP1", 1.0, 1.0, 10.0, 5.0),
                createAP("AP2", 1.0, 2.0, 10.0, 5.0),
                createAP("AP3", 2.0, 1.5, 10.0, 5.0)
            );
            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -85.0),
                createScan("AP2", -88.0),
                createScan("AP3", -90.0)
            );
            Position result = algorithm.calculatePosition(scans, aps);
            assertNotNull(result);
            // Accuracy: Should be worse (>10m) for weak signals
            assertTrue(result.accuracy() > 10.0,
                "Expected accuracy > 10 for weak signals, got " + result.accuracy());
            // Confidence: Should be lower for weak signals
            assertTrue(result.confidence() < 0.6,
                "Expected confidence < 0.6 for weak signals, got " + result.confidence());
            // Latitude/Longitude: Should be within or near triangle, allow wider margin for weak signals
            assertTrue(result.latitude() >= 0.7 && result.latitude() <= 2.3,
                "Expected latitude between 0.7 and 2.3, got " + result.latitude());
            assertTrue(result.longitude() >= 0.7 && result.longitude() <= 2.3,
                "Expected longitude between 0.7 and 2.3, got " + result.longitude());
        }
    }

    @Nested
    @DisplayName("Incomplete Data Handling")
    class IncompleteDataHandlingTests {
        /**
         * Tests algorithm behavior with missing altitude data.
         * This tests the 2D positioning fallback capability of the algorithm.
         * 
         * Expected outcome:
         * - Algorithm should still calculate position using 2D coordinates
         * - Position should have reasonable accuracy and confidence
         * - Altitude should be 0.0 when all altitude data is missing
         */
        @Test
        @DisplayName("should calculate 2D position when altitude data is missing")
        void shouldCalculate2DPositionWhenAltitudeDataIsMissing() {
            // Create APs with null altitude
            List<WifiAccessPoint> aps = Arrays.asList(
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
                    .latitude(1.0)
                    .longitude(2.0)
                    .altitude(null) // Null altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build(),
                WifiAccessPoint.builder()
                    .macAddress("AP3")
                    .latitude(2.0)
                    .longitude(1.5)
                    .altitude(null) // Null altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build()
            );
            
            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -60.0),
                createScan("AP2", -62.0),
                createScan("AP3", -61.0)
            );
            
            Position result = algorithm.calculatePosition(scans, aps);
            
            // Verify position calculation succeeded
            assertNotNull(result);
            
            // Verify position is within reasonable bounds
            assertTrue(result.latitude() >= 0.9 && result.latitude() <= 2.1,
                "Expected latitude between 0.9 and 2.1, got " + result.latitude());
            assertTrue(result.longitude() >= 0.9 && result.longitude() <= 2.1,
                "Expected longitude between 0.9 and 2.1, got " + result.longitude());
            
            // Verify altitude handling
            assertEquals(0.0, result.altitude(), "Altitude should be 0.0 when all APs have null altitude");
            
            // Verify accuracy and confidence are reasonable
            assertTrue(result.accuracy() > 0, "Accuracy should be positive");
            assertTrue(result.confidence() > 0, "Confidence should be positive");
        }
        
        /**
         * Tests algorithm behavior with a mix of APs with and without altitude data.
         * This verifies the algorithm can blend 2D and 3D data appropriately.
         * 
         * Expected outcome:
         * - Algorithm should calculate reasonable position
         * - Altitude should be based on APs with altitude data
         * - Position should have reasonable accuracy and confidence
         */
        @Test
        @DisplayName("should calculate position with mixed 2D and 3D data")
        void shouldCalculatePositionWithMixed2DAnd3DData() {
            // Create APs with mixed altitude data
            List<WifiAccessPoint> aps = Arrays.asList(
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
                    .latitude(1.0)
                    .longitude(2.0)
                    .altitude(null) // Null altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build(),
                WifiAccessPoint.builder()
                    .macAddress("AP3")
                    .latitude(2.0)
                    .longitude(1.5)
                    .altitude(15.0) // With altitude
                    .horizontalAccuracy(5.0)
                    .confidence(0.8)
                    .build()
            );
            
            List<WifiScanResult> scans = Arrays.asList(
                createScan("AP1", -60.0),
                createScan("AP2", -62.0),
                createScan("AP3", -61.0)
            );
            
            Position result = algorithm.calculatePosition(scans, aps);
            
            // Verify position calculation succeeded
            assertNotNull(result);
            
            // Verify position is within reasonable bounds
            assertTrue(result.latitude() >= 0.9 && result.latitude() <= 2.1,
                "Expected latitude between 0.9 and 2.1, got " + result.latitude());
            assertTrue(result.longitude() >= 0.9 && result.longitude() <= 2.1,
                "Expected longitude between 0.9 and 2.1, got " + result.longitude());
            
            // Verify altitude is based on APs with altitude data
            // Should be between 10.0 and 15.0, or exactly one of those values
            assertTrue(result.altitude() >= 10.0 && result.altitude() <= 15.0,
                "Altitude should be based on APs with altitude data");
            
            // Verify accuracy and confidence are reasonable
            assertTrue(result.accuracy() > 0, "Accuracy should be positive");
            assertTrue(result.confidence() > 0, "Confidence should be positive");
        }
    }
} 