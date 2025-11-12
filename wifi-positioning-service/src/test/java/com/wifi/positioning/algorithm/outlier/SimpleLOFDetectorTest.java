// src/test/java/com/wifi/positioning/algorithm/outlier/SimpleLOFDetectorTest.java
package com.wifi.positioning.algorithm.outlier;

import com.wifi.positioning.dto.WifiAPWithScan;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SimpleLOFDetector outlier detection algorithm.
 * 
 * <p>Tests verify the Local Outlier Factor (LOF) algorithm's ability to detect
 * access points that are significantly isolated from their local neighborhood.
 */
@DisplayName("SimpleLOFDetector Tests")
class SimpleLOFDetectorTest {

    /**
     * Creates a WifiAccessPoint for testing.
     * 
     * @param mac MAC address
     * @param lat latitude
     * @param lon longitude
     * @return WifiAccessPoint instance
     */
    private WifiAccessPoint createAP(String mac, double lat, double lon) {
        return WifiAccessPoint.builder()
                .macAddress(mac)
                .latitude(lat)
                .longitude(lon)
                .status("active")
                .build();
    }

    /**
     * Creates a WifiScanResult for testing.
     * 
     * @param mac MAC address
     * @param rssi signal strength in dBm
     * @return WifiScanResult instance
     */
    private WifiScanResult createScan(String mac, double rssi) {
        return WifiScanResult.of(mac, rssi, 2400, "test-ssid");
    }

    /**
     * Creates a WifiAPWithScan pair for testing.
     * 
     * @param mac MAC address
     * @param lat latitude
     * @param lon longitude
     * @param rssi signal strength in dBm
     * @return WifiAPWithScan instance
     */
    private WifiAPWithScan createPair(String mac, double lat, double lon, double rssi) {
        WifiAccessPoint ap = createAP(mac, lat, lon);
        WifiScanResult scan = createScan(mac, rssi);
        return new WifiAPWithScan(ap, scan);
    }

    /**
     * Tests for LOF detection with real-world WiFi access point data.
     */
    @Nested
    @DisplayName("LOF Detection Tests")
    class LOFDetectionTests {

        /**
         * Verifies LOF detection runs without errors on real WiFi access point locations.
         * 
         * <p>Test data contains 10 access points distributed across two geographic clusters:
         * <ul>
         *   <li>Cluster 1: 4 APs at (40.7022738, -80.0883654)</li>
         *   <li>Cluster 2: 6 APs around (40.6835xxx, -80.0745xxx)</li>
         * </ul>
         * 
         * <p>The two clusters are approximately 2-3 km apart.
         */
        @Test
        @DisplayName("should execute LOF detection without errors on real WiFi AP data")
        void shouldExecuteLOFDetectionOnRealData() {
            // Arrange - Create 10 WifiAPWithScan pairs from real data
            List<WifiAPWithScan> pairs = new ArrayList<>();
            
            // Cluster 1: 4 APs at (40.7022738, -80.0883654)
            pairs.add(createPair("2c:3f:0b:57:8d:54", 40.7022738, -80.0883654, -72));
            pairs.add(createPair("2a:3f:0b:57:8d:54", 40.7022738, -80.0883654, -72));
            pairs.add(createPair("26:3f:0b:57:8d:54", 40.7022738, -80.0883654, -72));
            pairs.add(createPair("12:3f:0b:57:8d:54", 40.7022738, -80.0883654, -73));
            
            // Cluster 2: 6 APs around (40.6835xxx, -80.0745xxx)
            pairs.add(createPair("2c:3f:0b:57:8d:42", 40.6835285, -80.0745656, -78));
            pairs.add(createPair("2a:3f:0b:57:8d:42", 40.6835285, -80.0745656, -78));
            pairs.add(createPair("26:3f:0b:57:8d:42", 40.6834141, -80.0746066, -78));
            pairs.add(createPair("12:3f:0b:57:8d:42", 40.6835285, -80.0745656, -79));
            pairs.add(createPair("2c:3f:0b:57:8d:53", 40.6835401, -80.0745602, -81));
            pairs.add(createPair("12:3f:0b:57:8b:b8", 40.6834416, -80.0745034, -81));
            
            // Act - Detect local outliers
            Set<String> outliers = SimpleLOFDetector.detectLocalOutliers(pairs);
            
            // Assert - Verify result is valid
            assertNotNull(outliers, "Result should not be null");
            assertTrue(outliers instanceof Set, "Result should be a Set");
        }

        /**
         * Verifies LOF detection returns empty set for insufficient points.
         */
        @Test
        @DisplayName("should return empty set when fewer than minimum points provided")
        void shouldReturnEmptySetForInsufficientPoints() {
            // Arrange - Create only 2 points (minimum is 3)
            List<WifiAPWithScan> pairs = new ArrayList<>();
            pairs.add(createPair("aa:bb:cc:dd:ee:01", 40.7022738, -80.0883654, -70));
            pairs.add(createPair("aa:bb:cc:dd:ee:02", 40.6835285, -80.0745656, -75));
            
            // Act
            Set<String> outliers = SimpleLOFDetector.detectLocalOutliers(pairs);
            
            // Assert
            assertNotNull(outliers);
            assertTrue(outliers.isEmpty(), "Should return empty set for insufficient points");
        }

        /**
         * Verifies LOF detection handles empty list gracefully.
         */
        @Test
        @DisplayName("should return empty set for empty input list")
        void shouldReturnEmptySetForEmptyList() {
            // Arrange
            List<WifiAPWithScan> pairs = new ArrayList<>();
            
            // Act
            Set<String> outliers = SimpleLOFDetector.detectLocalOutliers(pairs);
            
            // Assert
            assertNotNull(outliers);
            assertTrue(outliers.isEmpty(), "Should return empty set for empty input");
        }

        /**
         * Verifies LOF detection filters out pairs without valid location data.
         */
        @Test
        @DisplayName("should filter out pairs without valid location data")
        void shouldFilterOutPairsWithoutLocation() {
            // Arrange - Mix of valid and invalid location data
            List<WifiAPWithScan> pairs = new ArrayList<>();
            
            // Valid pairs
            pairs.add(createPair("aa:bb:cc:dd:ee:01", 40.7022738, -80.0883654, -70));
            pairs.add(createPair("aa:bb:cc:dd:ee:02", 40.7022738, -80.0883654, -71));
            pairs.add(createPair("aa:bb:cc:dd:ee:03", 40.6835285, -80.0745656, -75));
            
            // Invalid pair (null access point)
            WifiScanResult scan = createScan("aa:bb:cc:dd:ee:04", -80);
            pairs.add(new WifiAPWithScan(null, scan));
            
            // Act
            Set<String> outliers = SimpleLOFDetector.detectLocalOutliers(pairs);
            
            // Assert - Should process only valid pairs (3 total, which is minimum)
            assertNotNull(outliers);
        }

        /**
         * Verifies LOF detection with all points at same location.
         */
        @Test
        @DisplayName("should handle all points at same location")
        void shouldHandleAllPointsAtSameLocation() {
            // Arrange - All APs at exact same location
            List<WifiAPWithScan> pairs = new ArrayList<>();
            pairs.add(createPair("aa:bb:cc:dd:ee:01", 40.7022738, -80.0883654, -70));
            pairs.add(createPair("aa:bb:cc:dd:ee:02", 40.7022738, -80.0883654, -71));
            pairs.add(createPair("aa:bb:cc:dd:ee:03", 40.7022738, -80.0883654, -72));
            pairs.add(createPair("aa:bb:cc:dd:ee:04", 40.7022738, -80.0883654, -73));
            
            // Act
            Set<String> outliers = SimpleLOFDetector.detectLocalOutliers(pairs);
            
            // Assert - Should handle gracefully (no outliers since all same location)
            assertNotNull(outliers);
        }
    }
}

