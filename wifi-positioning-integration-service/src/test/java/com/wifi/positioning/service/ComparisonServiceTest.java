// com/wifi/positioning/service/ComparisonServiceTest.java
package com.wifi.positioning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.ComparisonMetrics;
import com.wifi.positioning.dto.LocationInfo;
import com.wifi.positioning.dto.SourceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ComparisonService.
 */
class ComparisonServiceTest {

    private ComparisonService comparisonService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AccessPointEnrichmentService enrichmentService = new AccessPointEnrichmentService(objectMapper);
        comparisonService = new ComparisonService(objectMapper, enrichmentService);
    }

    @Test
    void compareResults_BothValidPositions() {
        // Given
        SourceResponse sourceResponse = createSourceResponse(37.7749, -122.4194, 50.0, 0.8);
        Object positioningResponse = createPositioningServiceResponse(37.7750, -122.4195, 45.0, 0.85);

        // When
        ComparisonMetrics result = comparisonService.compareResults(sourceResponse, positioningResponse);

        // Then
        assertNotNull(result);
        assertTrue(result.getPositionsComparable());
        assertNotNull(result.getHaversineDistanceMeters());
        assertTrue(result.getHaversineDistanceMeters() > 0);
        assertTrue(result.getHaversineDistanceMeters() < 200); // Should be a small distance
        
        assertEquals(-5.0, result.getAccuracyDelta(), 0.01); // 45 - 50 = -5
        assertEquals(0.05, result.getConfidenceDelta(), 0.01); // 0.85 - 0.8 = 0.05
        
        // Check echoed positioning service data
        assertEquals(List.of("weighted_centroid", "rssiratio"), result.getMethodsUsed());
        assertEquals(2, result.getApCount());
        assertEquals(15L, result.getCalculationTimeMs());
    }

    @Test
    void compareResults_NullSourceResponse() {
        // Given
        Object positioningResponse = createPositioningServiceResponse(37.7749, -122.4194, 50.0, 0.8);

        // When
        ComparisonMetrics result = comparisonService.compareResults(null, positioningResponse);

        // Then
        assertNotNull(result);
        assertNull(result.getPositionsComparable()); // Cannot compare when source response is null
        assertNull(result.getHaversineDistanceMeters());
        assertNull(result.getAccuracyDelta());
        assertNull(result.getConfidenceDelta());
        
        // Should still echo positioning service data
        assertEquals(List.of("weighted_centroid", "rssiratio"), result.getMethodsUsed());
        assertEquals(2, result.getApCount());
        assertEquals(15L, result.getCalculationTimeMs());
    }

    @Test
    void compareResults_UnsuccessfulSourceResponse() {
        // Given
        SourceResponse sourceResponse = new SourceResponse();
        sourceResponse.setSuccess(false);
        sourceResponse.setErrorMessage("Positioning failed");
        
        Object positioningResponse = createPositioningServiceResponse(37.7749, -122.4194, 50.0, 0.8);

        // When
        ComparisonMetrics result = comparisonService.compareResults(sourceResponse, positioningResponse);

        // Then
        assertNotNull(result);
        assertNull(result.getPositionsComparable()); // Cannot compare when VLSS failed
        assertNull(result.getHaversineDistanceMeters());
    }

    @Test
    void compareResults_FailedPositioningServiceResponse() {
        // Given
        SourceResponse sourceResponse = createSourceResponse(37.7749, -122.4194, 50.0, 0.8);
        Object positioningResponse = Map.of(
            "result", "FAILED",
            "message", "Insufficient access points"
        );

        // When
        ComparisonMetrics result = comparisonService.compareResults(sourceResponse, positioningResponse);

        // Then
        assertNotNull(result);
        assertNull(result.getPositionsComparable()); // Cannot compare when Frisco failed
        assertNull(result.getHaversineDistanceMeters());
        assertNull(result.getMethodsUsed());
        assertNull(result.getApCount());
    }

    @Test
    void compareResults_SameLocation() {
        // Given - exact same coordinates
        double lat = 37.7749;
        double lon = -122.4194;
        SourceResponse sourceResponse = createSourceResponse(lat, lon, 50.0, 0.8);
        Object positioningResponse = createPositioningServiceResponse(lat, lon, 50.0, 0.8);

        // When
        ComparisonMetrics result = comparisonService.compareResults(sourceResponse, positioningResponse);

        // Then
        assertNotNull(result);
        assertTrue(result.getPositionsComparable());
        assertEquals(0.0, result.getHaversineDistanceMeters(), 0.01); // Should be ~0 meters
        assertEquals(0.0, result.getAccuracyDelta(), 0.01);
        assertEquals(0.0, result.getConfidenceDelta(), 0.01);
    }

    @Test
    void compareResults_LargeDistance() {
        // Given - San Francisco vs New York
        SourceResponse sourceResponse = createSourceResponse(37.7749, -122.4194, 50.0, 0.8); // SF
        Object positioningResponse = createPositioningServiceResponse(40.7589, -73.9851, 45.0, 0.85); // NYC

        // When
        ComparisonMetrics result = comparisonService.compareResults(sourceResponse, positioningResponse);

        // Then
        assertNotNull(result);
        assertTrue(result.getPositionsComparable());
        assertNotNull(result.getHaversineDistanceMeters());
        assertTrue(result.getHaversineDistanceMeters() > 4000000); // Should be ~4000km
        assertTrue(result.getHaversineDistanceMeters() < 5000000);
    }

    @Test
    void compareResults_MissingAccuracyAndConfidence() {
        // Given
        SourceResponse sourceResponse = createSourceResponse(37.7749, -122.4194, null, null);
        Object positioningResponse = createPositioningServiceResponseMinimal(37.7750, -122.4195);

        // When
        ComparisonMetrics result = comparisonService.compareResults(sourceResponse, positioningResponse);

        // Then
        assertNotNull(result);
        assertTrue(result.getPositionsComparable());
        assertNotNull(result.getHaversineDistanceMeters());
        assertNull(result.getAccuracyDelta()); // Missing accuracy data
        assertNull(result.getConfidenceDelta()); // Missing confidence data
    }

    @Test
    void compareResults_InvalidJsonResponse() {
        // Given
        SourceResponse sourceResponse = createSourceResponse(37.7749, -122.4194, 50.0, 0.8);
        Object positioningResponse = "invalid json response";

        // When
        ComparisonMetrics result = comparisonService.compareResults(sourceResponse, positioningResponse);

        // Then
        assertNotNull(result);
        assertNull(result.getPositionsComparable()); // Cannot compare when Frisco returns invalid response
        assertNull(result.getHaversineDistanceMeters());
    }

    @Test
    void haversineDistance_KnownDistances() {
        // Test known distance calculations
        
        // Distance between two points 1 degree apart at equator should be ~111km
        SourceResponse source = createSourceResponse(0.0, 0.0, 50.0, 0.8);
        Object positioning = createPositioningServiceResponse(1.0, 0.0, 50.0, 0.8);
        
        ComparisonMetrics result = comparisonService.compareResults(source, positioning);
        
        assertNotNull(result.getHaversineDistanceMeters());
        assertTrue(result.getHaversineDistanceMeters() > 110000); // ~111km
        assertTrue(result.getHaversineDistanceMeters() < 112000);
    }

    private SourceResponse createSourceResponse(Double lat, Double lon, Double accuracy, Double confidence) {
        SourceResponse response = new SourceResponse();
        response.setSuccess(true);
        response.setRequestId("test-request-123");
        
        LocationInfo locationInfo = new LocationInfo();
        locationInfo.setLatitude(lat);
        locationInfo.setLongitude(lon);
        locationInfo.setAccuracy(accuracy);
        locationInfo.setConfidence(confidence);
        
        response.setLocationInfo(locationInfo);
        return response;
    }

    private Object createPositioningServiceResponse(double lat, double lon, Double accuracy, Double confidence) {
        return Map.of(
            "result", "SUCCESS",
            "message", "Request processed successfully",
            "wifiPosition", Map.of(
                "latitude", lat,
                "longitude", lon,
                "horizontalAccuracy", accuracy,
                "confidence", confidence,
                "methodsUsed", List.of("weighted_centroid", "rssiratio"),
                "apCount", 2,
                "calculationTimeMs", 15
            )
        );
    }

    private Object createPositioningServiceResponseMinimal(double lat, double lon) {
        return Map.of(
            "result", "SUCCESS",
            "wifiPosition", Map.of(
                "latitude", lat,
                "longitude", lon
                // Missing accuracy, confidence, etc.
            )
        );
    }
}
