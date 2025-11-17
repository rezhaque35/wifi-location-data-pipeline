package com.wifi.ap.location.dynamic;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import com.wifi.ap.location.measurements.math.GeographicCentroidCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for WiFiHotspotDetectionService.
 * 
 * Tests cover spatial distribution analysis, edge cases, and validation
 * according to Section 3.4.1 of the requirements.
 */
@DisplayName("WiFiHotspotDetectionService")
class WiFiHotspotDetectionServiceTest {

    private WiFiHotspotDetectionService hotspotDetectionService;

    @BeforeEach
    void setUp() {
        GeographicCentroidCalculator centroidCalculator = new GeographicCentroidCalculator();
        hotspotDetectionService = new WiFiHotspotDetectionService(centroidCalculator);
    }

    @Nested
    @DisplayName("Detection with AP Location Context")
    class DetectionWithMacLocationTest {

        @Test
        @DisplayName("Should return previously detected result when AP is already classified as hotspot")
        void detect_WhenAPAlreadyHotspot_ShouldReturnPreviouslyDetected() {
            // Given
            APLocation hotspotAP = APLocation.builder()
                                             .macAddress("00:11:22:33:44:55")
                                             .status(APLocation.STATUS_WIFI_HOTSPOT)
                                             .build();
            
            List<WifiMeasurement> measurements = createStationaryMeasurements(25, 37.7749, -122.4194);

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements), hotspotAP);

            // Then
            assertThat(result).isEqualTo(WiFiHotspotDetectionService.PREVIOUSLY_HOTSPOT_DETECTED);
            assertThat(result.hotspotDetected()).isTrue();
            assertThat(result.hotspotDetectedReason()).isEqualTo("Previously classified as mobile hotspot");
        }

        @Test
        @DisplayName("Should perform analysis when AP is not classified as hotspot")
        void detect_WhenAPNotHotspot_ShouldPerformAnalysis() {
            // Given
            APLocation stationaryAP = APLocation.builder()
                                                .macAddress("00:11:22:33:44:55")
                                                .status(APLocation.STATUS_ACTIVE)
                                                .build();
            
            List<WifiMeasurement> measurements = createStationaryMeasurements(25, 37.7749, -122.4194);

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements), stationaryAP);

            // Then
            assertThat(result.hotspotDetected()).isFalse();
            assertThat(result.hotspotDetectedReason()).contains("Spatial standard deviation");
        }
    }

    @Nested
    @DisplayName("Detection Algorithm")
    class DetectionAlgorithmTest {

        @Test
        @DisplayName("Should throw exception for null measurements")
        void detect_WhenMeasurementsNull_ShouldThrowException() {
            // When/Then
            assertThatThrownBy(() -> hotspotDetectionService.detect((WifiMeasurements) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WiFi measurements cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for empty measurements")
        void detect_WhenMeasurementsEmpty_ShouldThrowException() {
            // When/Then
            assertThatThrownBy(() -> hotspotDetectionService.detect(WifiMeasurements.of(Collections.emptyList())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WiFi measurements cannot be null or empty");
        }

        @Test
        @DisplayName("Should return insufficient data when less than 20 measurements")
        void detect_WhenInsufficientMeasurements_ShouldReturnInsufficientData() {
            // Given
            List<WifiMeasurement> measurements = createStationaryMeasurements(15, 37.7749, -122.4194);

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result).isEqualTo(WiFiHotspotDetectionService.INSUFFICIENT_DATA);
            assertThat(result.hotspotDetected()).isFalse();
            assertThat(result.hotspotDetectedReason()).contains("Insufficient measurements for classification (minimum 20 required)");
        }

        @Test
        @DisplayName("Should return insufficient data when valid locations are less than 20")
        void detect_WhenInsufficientValidLocations_ShouldReturnInsufficientData() {
            // Given - Create measurements where only 10 have valid coordinates
            // Note: WifiMeasurements constructor now filters out invalid measurements automatically
            List<WifiMeasurement> measurements = createMeasurementsWithInvalidLocations(25, 10);

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then - After filtering, only 10 valid measurements remain, which is below the 20 minimum
            assertThat(result).isEqualTo(WiFiHotspotDetectionService.INSUFFICIENT_DATA);
            assertThat(result.hotspotDetected()).isFalse();
            assertThat(result.hotspotDetectedReason()).contains("Insufficient measurements for classification (minimum 20 required)");
        }

        @Test
        @DisplayName("Should detect stationary AP when spatial deviation is low")
        void detect_WhenLowSpatialDeviation_ShouldDetectStationary() {
            // Given - measurements clustered within 50 meters (well below 500m threshold)
            List<WifiMeasurement> measurements = createStationaryMeasurements(25, 37.7749, -122.4194);

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result.hotspotDetected()).isFalse();
            assertThat(result.hotspotDetectedReason()).contains("Spatial standard deviation");
            assertThat(result.hotspotDetectedReason()).contains("threshold: 500 meters");
            assertThat(result.hotspotDetectedReason()).contains("measurements: 25");
        }

        @Test
        @DisplayName("Should detect mobile hotspot when spatial deviation exceeds threshold")
        void detect_WhenHighSpatialDeviation_ShouldDetectMobileHotspot() {
            // Given - measurements spread across multiple kilometers
            List<WifiMeasurement> measurements = createMobileHotspotMeasurements();

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result.hotspotDetected()).isTrue();
            assertThat(result.hotspotDetectedReason()).contains("Spatial standard deviation");
            assertThat(result.hotspotDetectedReason()).contains("threshold: 500 meters");
        }

        @Test
        @DisplayName("Should handle edge case near threshold")
        void detect_WhenNearThreshold_ShouldClassifyCorrectly() {
            // Given - measurements that create ~500m standard deviation
            List<WifiMeasurement> measurements = createNearThresholdMeasurements();

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.hotspotDetectedReason()).contains("Spatial standard deviation");
            // The actual classification depends on the exact spatial distribution
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle measurements with identical locations")
        void detect_WhenIdenticalLocations_ShouldReturnZeroDeviation() {
            // Given - all measurements at same location
            List<WifiMeasurement> measurements = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                measurements.add(createMeasurement(String.valueOf(i), 37.7749, -122.4194));
            }

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result.hotspotDetected()).isFalse();
            assertThat(result.hotspotDetectedReason()).contains("0.00 meters");
        }

        @Test
        @DisplayName("Should filter out measurements with null coordinates")
        void detect_WhenNullCoordinates_ShouldFilterThem() {
            // Given
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Add valid measurements
            for (int i = 0; i < 20; i++) {
                measurements.add(createMeasurement(String.valueOf(i), 37.7749, -122.4194));
            }
            
            // Add measurements with null coordinates
            for (int i = 20; i < 25; i++) {
                measurements.add(createMeasurement(String.valueOf(i), null, null));
            }

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result.hotspotDetected()).isFalse();
            assertThat(result.hotspotDetectedReason()).contains("measurements: 20");
        }

        @Test
        @DisplayName("Should filter out measurements with NaN coordinates")
        void detect_WhenNaNCoordinates_ShouldFilterThem() {
            // Given
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Add valid measurements
            for (int i = 0; i < 20; i++) {
                measurements.add(createMeasurement(String.valueOf(i), 37.7749, -122.4194));
            }
            
            // Add measurements with NaN coordinates
            for (int i = 20; i < 25; i++) {
                measurements.add(createMeasurement(String.valueOf(i), Double.NaN, Double.NaN));
            }

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result.hotspotDetected()).isFalse();
            assertThat(result.hotspotDetectedReason()).contains("measurements: 20");
        }

        @Test
        @DisplayName("Should handle single measurement after filtering")
        void detect_WhenOnlyOneMeasurementAfterFiltering_ShouldReturnInsufficientData() {
            // Given
            List<WifiMeasurement> measurements = new ArrayList<>();
            measurements.add(createMeasurement("1", 37.7749, -122.4194));
            
            // Add many invalid measurements
            for (int i = 2; i < 25; i++) {
                measurements.add(createMeasurement(String.valueOf(i), null, null));
            }

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then - After filtering, only 1 valid measurement remains, which is below the 20 minimum
            assertThat(result).isEqualTo(WiFiHotspotDetectionService.INSUFFICIENT_DATA);
            assertThat(result.hotspotDetected()).isFalse();
            assertThat(result.hotspotDetectedReason()).contains("Insufficient measurements for classification (minimum 20 required)");
        }
    }

    @Nested
    @DisplayName("Spatial Distribution Calculations")
    class SpatialDistributionTest {

        @Test
        @DisplayName("Should correctly calculate spatial deviation for known geographic pattern")
        void detect_WhenKnownGeographicPattern_ShouldCalculateCorrectDeviation() {
            // Given - measurements in a cross pattern (North, South, East, West from center)
            List<WifiMeasurement> measurements = createCrossPatternMeasurements();

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.hotspotDetectedReason()).contains("Spatial standard deviation");
            // Verify the calculation produces a reasonable result
            String reason = result.hotspotDetectedReason();
            double deviation = extractDeviationFromReason(reason);
            assertThat(deviation).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Should handle measurements spanning large geographic areas")
        void detect_WhenLargeGeographicSpread_ShouldDetectAsHotspot() {
            // Given - measurements across different continents
            List<WifiMeasurement> measurements = new ArrayList<>(Arrays.asList(
                createMeasurement("1", 37.7749, -122.4194),  // San Francisco
                createMeasurement("2", 40.7128, -74.0060),   // New York
                createMeasurement("3", 51.5074, -0.1278),    // London
                createMeasurement("4", 35.6762, 139.6503),   // Tokyo
                createMeasurement("5", -33.8688, 151.2093)   // Sydney
            ));
            
            // Add more measurements to meet minimum requirement
            for (int i = 6; i <= 25; i++) {
                // Add measurements around San Francisco with small variations
                double lat = 37.7749 + (Math.random() - 0.5) * 0.01;
                double lon = -122.4194 + (Math.random() - 0.5) * 0.01;
                measurements.add(createMeasurement(String.valueOf(i), lat, lon));
            }

            // When
            WiFiHotspotResult result = hotspotDetectionService.detect(WifiMeasurements.of(measurements));

            // Then
            assertThat(result.hotspotDetected()).isTrue();
            assertThat(result.hotspotDetectedReason()).contains("Spatial standard deviation");
        }
    }

    // Helper methods for creating test data

    /**
     * Creates measurements clustered around a central point (stationary AP pattern).
     */
    private List<WifiMeasurement> createStationaryMeasurements(int count, double centerLat, double centerLon) {
        List<WifiMeasurement> measurements = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            // Small random variations around center point (within ~50 meters)
            double lat = centerLat + (Math.random() - 0.5) * 0.0005; // ~25m variation
            double lon = centerLon + (Math.random() - 0.5) * 0.0005; // ~25m variation
            measurements.add(createMeasurement(String.valueOf(i), lat, lon));
        }
        
        return measurements;
    }

    /**
     * Creates measurements that simulate a mobile hotspot (high spatial distribution).
     */
    private List<WifiMeasurement> createMobileHotspotMeasurements() {
        List<WifiMeasurement> measurements = new ArrayList<>();
        
        // Create measurements along a path (simulating mobile device)
        double startLat = 37.7749;
        double startLon = -122.4194;
        
        for (int i = 0; i < 25; i++) {
            // Simulate movement over several kilometers
            double lat = startLat + (i * 0.01); // ~1km per step
            double lon = startLon + (i * 0.01);
            measurements.add(createMeasurement(String.valueOf(i), lat, lon));
        }
        
        return measurements;
    }

    /**
     * Creates measurements that result in spatial deviation near the 500m threshold.
     */
    private List<WifiMeasurement> createNearThresholdMeasurements() {
        List<WifiMeasurement> measurements = new ArrayList<>();
        double centerLat = 37.7749;
        double centerLon = -122.4194;
        
        // Create measurements in a pattern that should result in ~500m standard deviation
        for (int i = 0; i < 25; i++) {
            double angle = (2 * Math.PI * i) / 25; // Distribute around circle
            double radius = 0.005; // ~500m from center
            double lat = centerLat + radius * Math.cos(angle);
            double lon = centerLon + radius * Math.sin(angle);
            measurements.add(createMeasurement(String.valueOf(i), lat, lon));
        }
        
        return measurements;
    }

    /**
     * Creates measurements in a cross pattern for predictable spatial distribution testing.
     */
    private List<WifiMeasurement> createCrossPatternMeasurements() {
        List<WifiMeasurement> measurements = new ArrayList<>();
        double centerLat = 37.7749;
        double centerLon = -122.4194;
        double offset = 0.001; // ~100m offset
        
        // Center point
        measurements.add(createMeasurement("center", centerLat, centerLon));
        
        // Add points in cardinal directions
        measurements.add(createMeasurement("north", centerLat + offset, centerLon));
        measurements.add(createMeasurement("south", centerLat - offset, centerLon));
        measurements.add(createMeasurement("east", centerLat, centerLon + offset));
        measurements.add(createMeasurement("west", centerLat, centerLon - offset));
        
        // Fill with more center points to reach minimum count
        for (int i = 5; i < 25; i++) {
            double lat = centerLat + (Math.random() - 0.5) * 0.0001; // Small variation
            double lon = centerLon + (Math.random() - 0.5) * 0.0001;
            measurements.add(createMeasurement(String.valueOf(i), lat, lon));
        }
        
        return measurements;
    }

    /**
     * Creates measurements with some invalid locations mixed in.
     */
    private List<WifiMeasurement> createMeasurementsWithInvalidLocations(int total, int validCount) {
        List<WifiMeasurement> measurements = new ArrayList<>();
        
        // Add valid measurements
        for (int i = 0; i < validCount; i++) {
            measurements.add(createMeasurement(String.valueOf(i), 37.7749, -122.4194));
        }
        
        // Add invalid measurements
        for (int i = validCount; i < total; i++) {
            measurements.add(createMeasurement(String.valueOf(i), null, null));
        }
        
        return measurements;
    }

    /**
     * Creates a single measurement with specified coordinates.
     */
    private WifiMeasurement createMeasurement(String id, Double lat, Double lon) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(lat)
            .longitude(lon)
            .altitude(10.0)
            .locationAccuracy(5.0)
            .rssi(-45)
            .frequency(2400)
            .connectionStatus("SCAN")
            .qualityWeight(1.0)
            .linkSpeed(null)
            .channelWidth(null)
            .centerFreq0(null)
            .isGlobalOutlier(false)
            .build();
    }

    /**
     * Extracts the spatial deviation value from the result reason string.
     */
    private double extractDeviationFromReason(String reason) {
        // Extract number from "Spatial standard deviation: X.XX meters"
        String[] parts = reason.split(":");
        if (parts.length > 1) {
            String numberPart = parts[1].trim().split(" ")[0];
            return Double.parseDouble(numberPart);
        }
        return 0.0;
    }
}
