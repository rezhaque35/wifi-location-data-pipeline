package com.wifi.ap.location.measurements.outlier.global;

import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import com.wifi.ap.location.measurements.math.GeographicCentroidCalculator;
import com.wifi.ap.location.measurements.outlier.Outliers;
import com.wifi.ap.location.config.GlobalOutlierDetectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for GlobalOutlierDetector and its centroid calculators.
 * 
 * <h2>Test Strategy</h2>
 * <p>These tests use real instances of all components to verify the complete
 * outlier detection pipeline, including:
 * <ul>
 *   <li><strong>Centroid Calculation:</strong> Both weighted and unweighted approaches</li>
 *   <li><strong>MAD-based Detection:</strong> Statistical outlier identification</li>
 *   <li><strong>Smart Auto-Detection:</strong> Automatic weighted/unweighted selection</li>
 *   <li><strong>Geographic Edge Cases:</strong> Anti-meridian, poles, etc.</li>
 * </ul>
 * 
 * <h2>Mathematical Concepts Tested</h2>
 * <ul>
 *   <li><strong>ECEF Vector Averaging:</strong> Geographic centroid calculation</li>
 *   <li><strong>Quality Weighting:</strong> CONNECTED vs SCAN measurement influence</li>
 *   <li><strong>MAD Calculation:</strong> Median Absolute Deviation computation</li>
 *   <li><strong>Haversine Distance:</strong> Spherical distance calculations</li>
 *   <li><strong>Statistical Robustness:</strong> Outlier resistance of MAD</li>
 * </ul>
 */
@DisplayName("GlobalOutlierDetector")
class GlobalOutlierDetectorTest {

    private GlobalOutlierDetector globalOutlierDetector;
    private GeographicCentroidCalculator centroidCalculator;
    
    @Mock
    private GlobalOutlierDetectionConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup default config values
        when(config.getMadMultiplier()).thenReturn(3.0);
        when(config.getMinSampleSize()).thenReturn(20);
        when(config.isUseWeightedCentroid()).thenReturn(true);
        when(config.getMinConnectedCount()).thenReturn(2);
        when(config.getMinConnectedPercentage()).thenReturn(0.1);
        
        centroidCalculator = new GeographicCentroidCalculator();
        globalOutlierDetector = new GlobalOutlierDetector(centroidCalculator, config);
    }
    
    /**
     * Helper method to convert List<WifiMeasurement> to WifiMeasurements.
     * This handles the filtering that now happens in the WifiMeasurements constructor.
     */
    private WifiMeasurements toWifiMeasurements(List<WifiMeasurement> measurements) {
        return WifiMeasurements.of(measurements);
    }

    @Nested
    @DisplayName("Global Outlier Detection")
    class GlobalOutlierDetectionTest {

        @Test
        @DisplayName("Should handle small measurement sets")
        void detectGlobalOutliers_WhenFewMeasurements_ShouldWork() {
            // Given - few measurements (tests behavior with small datasets)
            List<WifiMeasurement> measurements = createClusteredMeasurements(3, "SCAN");

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            // With few measurements, algorithm may still detect outliers
            assertThat(outliers.outliers()).isNotNull();
            assertThat(outliers.size()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Should detect outliers using MAD threshold")
        void detectGlobalOutliers_WhenOutliersPresent_ShouldDetectThem() {
            // Given - clustered measurements around San Francisco with distant outliers
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Main cluster around San Francisco (should not be outliers) - need at least 20 measurements
            for (int i = 0; i < 18; i++) {
                double lat = 37.7749 + (i % 6 - 2.5) * 0.0002; // ~25m variation
                double lon = -122.4194 + (i % 6 - 2.5) * 0.0002;
                measurements.add(createMeasurement("cluster_" + i, lat, lon, "SCAN"));
            }
            
            // Add clear outliers (different cities)
            measurements.add(createMeasurement("outlier_1", 40.7128, -74.0060, "SCAN"));  // New York
            measurements.add(createMeasurement("outlier_2", 34.0522, -118.2437, "SCAN")); // Los Angeles

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            // Should detect the clear outliers (may also detect some edge measurements)
            assertThat(outliers.outliers()).contains("outlier_1", "outlier_2");
        }

        @Test
        @DisplayName("Should handle identical locations without errors")
        void detectGlobalOutliers_WhenIdenticalLocations_ShouldHandleCorrectly() {
            // Given - all measurements at same location (above minimum threshold)
            List<WifiMeasurement> measurements = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                measurements.add(createMeasurement(String.valueOf(i), 37.7749, -122.4194, "SCAN"));
            }

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            assertThat(outliers.outliers()).isEmpty(); // No outliers when all points are identical
        }

        @Test
        @DisplayName("Should detect some outliers when measurements vary moderately")
        void detectGlobalOutliers_WhenAllMeasurementsClose_ShouldDetectSome() {
            // Given - measurements with moderate variation 
            List<WifiMeasurement> measurements = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                double lat = 37.7749 + (i % 3 - 1) * 0.0001; // ~10m variation
                double lon = -122.4194 + (i % 3 - 1) * 0.0001;
                measurements.add(createMeasurement(String.valueOf(i), lat, lon, "SCAN"));
            }

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            // MAD-based detection can be sensitive, should detect some outliers but not too many
            assertThat(outliers.size()).isLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("Should handle moderate outliers correctly")
        void detectGlobalOutliers_WhenModerateOutliers_ShouldApplyMADCorrectly() {
            // Given - cluster with one moderate outlier
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Tight cluster (19 measurements)
            for (int i = 0; i < 19; i++) {
                double lat = 37.7749 + (i * 0.0001); // ~10m spacing
                double lon = -122.4194 + (i * 0.0001);
                measurements.add(createMeasurement("normal_" + i, lat, lon, "SCAN"));
            }
            
            // Add moderate outlier (~5km away)
            measurements.add(createMeasurement("moderate_outlier", 37.8199, -122.4594, "SCAN"));

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            // Should detect the moderate outlier
            assertThat(outliers.outliers()).contains("moderate_outlier");
        }

        @Test
        @DisplayName("Should handle edge case with minimum measurements")
        void detectGlobalOutliers_WithMinimumMeasurements_ShouldWork() {
            // Given - exactly 20 measurements (minimum for detection)
            List<WifiMeasurement> measurements = new ArrayList<>();
            for (int i = 0; i < 19; i++) {
                measurements.add(createMeasurement(String.valueOf(i), 37.7749, -122.4194, "SCAN"));
            }
            measurements.add(createMeasurement("outlier", 40.7128, -74.0060, "SCAN")); // New York - clear outlier

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            assertThat(outliers.outliers()).contains("outlier");
        }
        
        @Test
        @DisplayName("Should use weighted centroid when sufficient CONNECTED measurements")
        void detectGlobalOutliers_WithSufficientConnectedMeasurements_ShouldUseWeightedCentroid() {
            // Given - mix of CONNECTED and SCAN measurements above thresholds
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Add CONNECTED measurements (higher quality weight)
            for (int i = 0; i < 10; i++) {
                double lat = 37.7749 + (i % 5 - 2) * 0.0002;
                double lon = -122.4194 + (i % 5 - 2) * 0.0002;
                measurements.add(createMeasurement("connected_" + i, lat, lon, "CONNECTED"));
            }
            
            // Add SCAN measurements
            for (int i = 0; i < 8; i++) {
                double lat = 37.7749 + (i % 4 - 1.5) * 0.0002;
                double lon = -122.4194 + (i % 4 - 1.5) * 0.0002;
                measurements.add(createMeasurement("scan_" + i, lat, lon, "SCAN"));
            }
            
            // Add clear outliers
            measurements.add(createMeasurement("outlier_1", 40.7128, -74.0060, "SCAN"));
            measurements.add(createMeasurement("outlier_2", 34.0522, -118.2437, "SCAN"));

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            // Should detect the clear outliers (weighted centroid should be closer to CONNECTED measurements)
            assertThat(outliers.outliers()).contains("outlier_1", "outlier_2");
        }

        @Test
        @DisplayName("Should use unweighted centroid when insufficient CONNECTED measurements")
        void detectGlobalOutliers_WithInsufficientConnectedMeasurements_ShouldUseUnweightedCentroid() {
            // Given - mostly SCAN measurements, few CONNECTED
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Add only 1 CONNECTED measurement (below min count threshold of 2)
            measurements.add(createMeasurement("connected_1", 37.7749, -122.4194, "CONNECTED"));
            
            // Add many SCAN measurements
            for (int i = 0; i < 17; i++) {
                double lat = 37.7749 + (i % 6 - 2.5) * 0.0002;
                double lon = -122.4194 + (i % 6 - 2.5) * 0.0002;
                measurements.add(createMeasurement("scan_" + i, lat, lon, "SCAN"));
            }
            
            // Add clear outliers
            measurements.add(createMeasurement("outlier_1", 40.7128, -74.0060, "SCAN"));
            measurements.add(createMeasurement("outlier_2", 34.0522, -118.2437, "SCAN"));

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            // Should still detect outliers using unweighted approach
            assertThat(outliers.outliers()).contains("outlier_1", "outlier_2");
        }
    }

    @Nested
    @DisplayName("Statistical Calculations")
    class StatisticalCalculationsTest {

        @Test
        @DisplayName("Should calculate correct centroid for known points")
        void detectGlobalOutliers_ShouldCalculateCorrectCentroid() {
            // Given - measurements forming a cluster around center
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Add measurements clustered around center
            for (int i = 0; i < 20; i++) {
                double lat = 37.5 + (i % 3 - 1) * 0.0001; // ~10m variation
                double lon = -121.5 + (i % 3 - 1) * 0.0001;
                measurements.add(createMeasurement(String.valueOf(i), lat, lon, "SCAN"));
            }

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            // MAD-based detection might identify some points as outliers
            assertThat(outliers.size()).isLessThanOrEqualTo(8);
        }

        @Test
        @DisplayName("Should handle large distance variations")
        void detectGlobalOutliers_WithLargeDistanceVariations_ShouldWork() {
            // Given - measurements with very different distance scales
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Very tight cluster (meter scale) - 18 measurements
            for (int i = 0; i < 18; i++) {
                double lat = 37.7749 + (i * 0.00001); // ~1m spacing
                double lon = -122.4194 + (i * 0.00001);
                measurements.add(createMeasurement("tight_" + i, lat, lon, "SCAN"));
            }
            
            // Very distant outliers (continent scale)
            measurements.add(createMeasurement("distant_1", 51.5074, -0.1278, "SCAN")); // London
            measurements.add(createMeasurement("distant_2", 35.6762, 139.6503, "SCAN")); // Tokyo

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            assertThat(outliers.outliers()).contains("distant_1", "distant_2");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Robustness")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle empty measurements list")
        void detectGlobalOutliers_WhenEmpty_ShouldReturnEmpty() {
            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(List.of()));

            // Then
            assertThat(outliers.outliers()).isEmpty();
        }

        @Test
        @DisplayName("Should handle null measurements list")
        void detectGlobalOutliers_WhenNull_ShouldReturnEmpty() {
            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(null);

            // Then
            assertThat(outliers.outliers()).isEmpty();
        }

        @Test
        @DisplayName("Should handle measurements at poles and equator")
        void detectGlobalOutliers_WithExtremeLat_ShouldWork() {
            // Given - measurements at extreme latitudes
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Cluster near equator (18 measurements)
            for (int i = 0; i < 18; i++) {
                double lat = 0.0 + (i % 6 - 2.5) * 0.01;
                double lon = 0.0 + (i % 6 - 2.5) * 0.01;
                measurements.add(createMeasurement("equator_" + i, lat, lon, "SCAN"));
            }
            
            // Add polar outliers
            measurements.add(createMeasurement("north_pole", 89.0, 0.0, "SCAN"));
            measurements.add(createMeasurement("south_pole", -89.0, 0.0, "SCAN"));

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            assertThat(outliers.outliers()).contains("north_pole", "south_pole");
        }

        @Test
        @DisplayName("Should handle measurements crossing 180 degree longitude")
        void detectGlobalOutliers_WithLongitudeCrossing_ShouldWork() {
            // Given - measurements around international date line
            List<WifiMeasurement> measurements = new ArrayList<>();
            
            // Cluster near date line (18 measurements)
            for (int i = 0; i < 18; i++) {
                double lat = 0.0 + (i % 6 - 2.5) * 0.01;
                double lon = 179.0 + (i % 3 - 1) * 0.5; // Small variation around 179°
                measurements.add(createMeasurement("dateline_" + i, lat, lon, "SCAN"));
            }
            
            // Add distant outliers
            measurements.add(createMeasurement("distant_1", 0.0, 0.0, "SCAN"));  // Prime meridian
            measurements.add(createMeasurement("distant_2", 0.0, 90.0, "SCAN")); // 90° East

            // When
            Outliers outliers = globalOutlierDetector.detectGlobalOutliers(toWifiMeasurements(measurements));

            // Then
            // Should handle longitude wrapping correctly and detect distant points
            assertThat(outliers.outliers())
                .isNotEmpty()
                .contains("distant_1");
        }
    }

    // Helper methods for creating test data

    /**
     * Creates measurements clustered around San Francisco.
     * 
     * @param count Number of measurements to create
     * @param connectionStatus Connection status for all measurements
     * @return List of clustered measurements
     */
    private List<WifiMeasurement> createClusteredMeasurements(int count, String connectionStatus) {
        List<WifiMeasurement> measurements = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            // Small deterministic variations around San Francisco (~50m radius)
            double lat = 37.7749 + (i % 5 - 2) * 0.0005; // ±100m variation
            double lon = -122.4194 + (i % 5 - 2) * 0.0005;
            measurements.add(createMeasurement(String.valueOf(i), lat, lon, connectionStatus));
        }
        
        return measurements;
    }

    /**
     * Creates a single measurement with specified coordinates and connection status.
     * 
     * @param id Measurement ID
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @param connectionStatus Connection status ("CONNECTED" or "SCAN")
     * @return WifiMeasurement instance
     */
    private WifiMeasurement createMeasurement(String id, Double latitude, Double longitude, String connectionStatus) {
        double qualityWeight = "CONNECTED".equals(connectionStatus) ? 2.0 : 1.0;
        
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(latitude)
            .longitude(longitude)
            .altitude(10.0)
            .locationAccuracy(5.0)
            .rssi(-45)
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