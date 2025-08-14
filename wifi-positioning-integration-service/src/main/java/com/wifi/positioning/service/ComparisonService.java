// com/wifi/positioning/service/ComparisonService.java
package com.wifi.positioning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.AccessPointEnrichmentMetrics;
import com.wifi.positioning.dto.ComparisonMetrics;
import com.wifi.positioning.dto.SourceResponse;
import com.wifi.positioning.dto.WifiInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for comparing client source responses with positioning service results.
 * Computes distance metrics, accuracy deltas, and other comparison statistics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComparisonService {

    private static final double EARTH_RADIUS_METERS = 6371000.0; // Earth radius in meters
    
    private final ObjectMapper objectMapper;
    private final AccessPointEnrichmentService enrichmentService;

    /**
     * Compares a source response with positioning service results.
     * 
     * @param sourceResponse Client's original positioning result (optional)
     * @param positioningServiceResponse Response from positioning service
     * @return Comparison metrics including distances and deltas
     */
    public ComparisonMetrics compareResults(SourceResponse sourceResponse, Object positioningServiceResponse) {
        return compareResults(sourceResponse, positioningServiceResponse, null);
    }

    /**
     * Compares a source response with positioning service results, including AP enrichment.
     * 
     * @param sourceResponse Client's original positioning result (optional)
     * @param positioningServiceResponse Response from positioning service
     * @param originalWifiInfo Original WiFi scan info for enrichment analysis
     * @return Comparison metrics including distances, deltas, and AP enrichment
     */
    public ComparisonMetrics compareResults(SourceResponse sourceResponse, Object positioningServiceResponse, List<WifiInfo> originalWifiInfo) {
        log.debug("Computing comparison metrics");
        
        ComparisonMetrics metrics = new ComparisonMetrics();
        
        // Extract normalized data from positioning service response
        NormalizedPosition positioningResult = normalizePositioningServiceResponse(positioningServiceResponse);
        
        // Extract normalized data from source response
        NormalizedPosition sourceResult = normalizeSourceResponse(sourceResponse);
        
        // Set basic positioning service data
        if (positioningResult != null) {
            metrics.setMethodsUsed(positioningResult.getMethodsUsed());
            metrics.setApCount(positioningResult.getApCount());
            metrics.setCalculationTimeMs(positioningResult.getCalculationTimeMs());
        }
        
        // Compute comparison metrics if both positions are available
        if (sourceResult != null && positioningResult != null && 
            sourceResult.isValid() && positioningResult.isValid()) {
            
            metrics.setPositionsComparable(true);
            
            // Compute haversine distance
            double distance = calculateHaversineDistance(
                sourceResult.getLatitude(), sourceResult.getLongitude(),
                positioningResult.getLatitude(), positioningResult.getLongitude()
            );
            metrics.setHaversineDistanceMeters(distance);
            
            // Compute accuracy delta (positioning service - source)
            if (sourceResult.getAccuracy() != null && positioningResult.getAccuracy() != null) {
                double accuracyDelta = positioningResult.getAccuracy() - sourceResult.getAccuracy();
                metrics.setAccuracyDelta(accuracyDelta);
            }
            
            // Compute confidence delta (positioning service - source)
            if (sourceResult.getConfidence() != null && positioningResult.getConfidence() != null) {
                double confidenceDelta = positioningResult.getConfidence() - sourceResult.getConfidence();
                metrics.setConfidenceDelta(confidenceDelta);
            }
            
            log.debug("Computed comparison: distance={}m, accuracyDelta={}, confidenceDelta={}", 
                distance, metrics.getAccuracyDelta(), metrics.getConfidenceDelta());
            
        } else {
            metrics.setPositionsComparable(false);
            log.debug("Positions not comparable - missing or invalid data");
        }
        
        // Add access point enrichment if original WiFi info is available
        if (originalWifiInfo != null && !originalWifiInfo.isEmpty()) {
            try {
                AccessPointEnrichmentMetrics enrichmentMetrics = enrichmentService.enrichAccessPoints(
                    originalWifiInfo, positioningServiceResponse);
                metrics.setAccessPointEnrichment(enrichmentMetrics);
                
                log.debug("AP enrichment completed - found: {}/{}, used: {}", 
                    enrichmentMetrics.getFoundApCount(), 
                    originalWifiInfo.size(), 
                    enrichmentMetrics.getUsedApCount());
                    
            } catch (Exception e) {
                log.warn("Failed to enrich access point information: {}", e.getMessage());
                // Continue without enrichment rather than failing the whole comparison
            }
        }
        
        return metrics;
    }

    /**
     * Normalizes positioning service response to extract relevant positioning data.
     */
    private NormalizedPosition normalizePositioningServiceResponse(Object response) {
        if (response == null) {
            return null;
        }
        
        try {
            JsonNode node = objectMapper.valueToTree(response);
            
            // Check if this is a successful response
            String result = getTextValue(node, "result");
            if (!"SUCCESS".equals(result)) {
                log.debug("Positioning service response was not successful: {}", result);
                return null;
            }
            
            JsonNode wifiPosition = node.get("wifiPosition");
            if (wifiPosition == null) {
                log.debug("No wifiPosition found in positioning service response");
                return null;
            }
            
            NormalizedPosition position = new NormalizedPosition();
            position.setLatitude(getDoubleValue(wifiPosition, "latitude"));
            position.setLongitude(getDoubleValue(wifiPosition, "longitude"));
            position.setAccuracy(getDoubleValue(wifiPosition, "horizontalAccuracy"));
            position.setConfidence(getDoubleValue(wifiPosition, "confidence"));
            
            // Extract methods used
            JsonNode methodsNode = wifiPosition.get("methodsUsed");
            if (methodsNode != null && methodsNode.isArray()) {
                List<String> methods = new ArrayList<>();
                methodsNode.forEach(method -> methods.add(method.asText()));
                position.setMethodsUsed(methods);
            }
            
            // Extract AP count and calculation time
            position.setApCount(getIntegerValue(wifiPosition, "apCount"));
            position.setCalculationTimeMs(getLongValue(wifiPosition, "calculationTimeMs"));
            
            return position;
            
        } catch (Exception e) {
            log.warn("Failed to normalize positioning service response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalizes source response to extract relevant positioning data.
     */
    private NormalizedPosition normalizeSourceResponse(SourceResponse sourceResponse) {
        if (sourceResponse == null || !Boolean.TRUE.equals(sourceResponse.getSuccess())) {
            return null;
        }
        
        if (sourceResponse.getLocationInfo() == null) {
            return null;
        }
        
        NormalizedPosition position = new NormalizedPosition();
        position.setLatitude(sourceResponse.getLocationInfo().getLatitude());
        position.setLongitude(sourceResponse.getLocationInfo().getLongitude());
        position.setAccuracy(sourceResponse.getLocationInfo().getAccuracy());
        position.setConfidence(sourceResponse.getLocationInfo().getConfidence());
        
        return position;
    }

    /**
     * Calculates the haversine distance between two points on Earth.
     * 
     * @param lat1 Latitude of first point in decimal degrees
     * @param lon1 Longitude of first point in decimal degrees
     * @param lat2 Latitude of second point in decimal degrees
     * @param lon2 Longitude of second point in decimal degrees
     * @return Distance in meters
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        // Convert latitude and longitude from degrees to radians
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);
        
        // Haversine formula
        double dlat = lat2Rad - lat1Rad;
        double dlon = lon2Rad - lon1Rad;
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_METERS * c;
    }

    // Helper methods to safely extract values from JsonNode
    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asText(null) : null;
    }
    
    private Double getDoubleValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asDouble() : null;
    }
    
    private Integer getIntegerValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asInt() : null;
    }
    
    private Long getLongValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asLong() : null;
    }

    /**
     * Internal class for normalized position data.
     */
    private static class NormalizedPosition {
        private Double latitude;
        private Double longitude;
        private Double accuracy;
        private Double confidence;
        private List<String> methodsUsed;
        private Integer apCount;
        private Long calculationTimeMs;
        
        public boolean isValid() {
            return latitude != null && longitude != null;
        }
        
        // Getters and setters
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        
        public Double getAccuracy() { return accuracy; }
        public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
        
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        
        public List<String> getMethodsUsed() { return methodsUsed; }
        public void setMethodsUsed(List<String> methodsUsed) { this.methodsUsed = methodsUsed; }
        
        public Integer getApCount() { return apCount; }
        public void setApCount(Integer apCount) { this.apCount = apCount; }
        
        public Long getCalculationTimeMs() { return calculationTimeMs; }
        public void setCalculationTimeMs(Long calculationTimeMs) { this.calculationTimeMs = calculationTimeMs; }
    }
}
