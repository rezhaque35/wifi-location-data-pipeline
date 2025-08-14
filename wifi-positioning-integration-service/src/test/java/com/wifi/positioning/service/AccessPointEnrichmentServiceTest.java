// com/wifi/positioning/service/AccessPointEnrichmentServiceTest.java
package com.wifi.positioning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.AccessPointEnrichmentMetrics;
import com.wifi.positioning.dto.WifiInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AccessPointEnrichmentService.
 */
class AccessPointEnrichmentServiceTest {

    private AccessPointEnrichmentService enrichmentService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        enrichmentService = new AccessPointEnrichmentService(objectMapper);
    }

    @Test
    void enrichAccessPoints_ValidResponse() {
        // Given
        List<WifiInfo> originalWifiInfo = createOriginalWifiInfo();
        Object positioningResponse = createPositioningServiceResponse();

        // When
        AccessPointEnrichmentMetrics result = enrichmentService.enrichAccessPoints(originalWifiInfo, positioningResponse);

        // Then
        assertNotNull(result);
        assertEquals(3, result.getApDetails().size());
        
        // Check found/not found counts
        assertEquals(2, result.getFoundApCount());
        assertEquals(1, result.getNotFoundApCount());
        assertEquals(66.67, result.getPercentRequestFound(), 0.1);
        
        // Check used AP count
        assertEquals(2, result.getUsedApCount());
        assertEquals(100.0, result.getPercentFoundUsed(), 0.1);
        
        // Check eligible count
        assertEquals(2, result.getEligibleApCount());
        assertEquals(0, result.getUnknownExclusions());
        
        // Check status counts
        Map<String, Integer> statusCounts = result.getFoundApStatusCounts();
        assertEquals(2, statusCounts.get("active"));
    }

    @Test
    void enrichAccessPoints_NoPositioningResponse() {
        // Given
        List<WifiInfo> originalWifiInfo = createOriginalWifiInfo();

        // When
        AccessPointEnrichmentMetrics result = enrichmentService.enrichAccessPoints(originalWifiInfo, null);

        // Then
        assertNotNull(result);
        assertEquals(3, result.getApDetails().size());
        assertEquals(0, result.getFoundApCount());
        assertEquals(3, result.getNotFoundApCount());
        assertEquals(0.0, result.getPercentRequestFound(), 0.1);
        
        // All APs should be marked as not found
        result.getApDetails().forEach(ap -> {
            assertFalse(ap.getFound());
            assertFalse(ap.getEligible());
            assertFalse(ap.getUsed());
        });
    }

    @Test
    void enrichAccessPoints_MixedStatuses() {
        // Given
        List<WifiInfo> originalWifiInfo = createOriginalWifiInfo();
        Object positioningResponse = createPositioningResponseWithMixedStatuses();

        // When
        AccessPointEnrichmentMetrics result = enrichmentService.enrichAccessPoints(originalWifiInfo, positioningResponse);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getFoundApCount());
        assertEquals(1, result.getEligibleApCount()); // Only 'active' status is eligible
        
        // Check status distribution
        Map<String, Integer> statusCounts = result.getFoundApStatusCounts();
        assertEquals(1, statusCounts.get("active"));
        assertEquals(1, statusCounts.get("inactive"));
    }

    @Test
    void enrichAccessPoints_EmptyWifiInfo() {
        // Given
        List<WifiInfo> originalWifiInfo = List.of();
        Object positioningResponse = createPositioningServiceResponse();

        // When
        AccessPointEnrichmentMetrics result = enrichmentService.enrichAccessPoints(originalWifiInfo, positioningResponse);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getApDetails().size());
        assertEquals(0, result.getFoundApCount());
        assertEquals(0, result.getNotFoundApCount());
    }

    private List<WifiInfo> createOriginalWifiInfo() {
        WifiInfo wifi1 = new WifiInfo();
        wifi1.setId("00:11:22:33:44:01");
        wifi1.setSignalStrength(-65.0);
        wifi1.setFrequency(2437);
        wifi1.setSsid("Test_AP_1");

        WifiInfo wifi2 = new WifiInfo();
        wifi2.setId("00:11:22:33:44:02");
        wifi2.setSignalStrength(-70.0);
        wifi2.setFrequency(5180);
        wifi2.setSsid("Test_AP_2");

        WifiInfo wifi3 = new WifiInfo();
        wifi3.setId("00:11:22:33:44:03"); // This one won't be found in positioning response
        wifi3.setSignalStrength(-75.0);
        wifi3.setFrequency(2462);

        return Arrays.asList(wifi1, wifi2, wifi3);
    }

    private Object createPositioningServiceResponse() {
        return Map.of(
            "result", "SUCCESS",
            "wifiPosition", Map.of(
                "apCount", 2
            ),
            "calculationInfo", Map.of(
                "accessPoints", Arrays.asList(
                    Map.of(
                        "bssid", "00:11:22:33:44:01",
                        "status", "active",
                        "usage", "used",
                        "location", Map.of(
                            "latitude", 37.7749,
                            "longitude", -122.4194,
                            "altitude", 10.5
                        )
                    ),
                    Map.of(
                        "bssid", "00:11:22:33:44:02",
                        "status", "active",
                        "usage", "used",
                        "location", Map.of(
                            "latitude", 37.7750,
                            "longitude", -122.4195,
                            "altitude", 12.0
                        )
                    )
                )
            )
        );
    }

    private Object createPositioningResponseWithMixedStatuses() {
        return Map.of(
            "result", "SUCCESS",
            "wifiPosition", Map.of(
                "apCount", 1
            ),
            "calculationInfo", Map.of(
                "accessPoints", Arrays.asList(
                    Map.of(
                        "bssid", "00:11:22:33:44:01",
                        "status", "active",
                        "usage", "used",
                        "location", Map.of(
                            "latitude", 37.7749,
                            "longitude", -122.4194
                        )
                    ),
                    Map.of(
                        "bssid", "00:11:22:33:44:02",
                        "status", "inactive", // Not eligible
                        "usage", "unused",
                        "location", Map.of(
                            "latitude", 37.7750,
                            "longitude", -122.4195
                        )
                    )
                )
            )
        );
    }
}
