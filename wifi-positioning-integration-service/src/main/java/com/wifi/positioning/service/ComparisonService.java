// com/wifi/positioning/service/ComparisonService.java
package com.wifi.positioning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced service for comprehensive comparison between VLSS and Frisco positioning services.
 * Supports cross-service analysis, cell tower fallback detection, and detailed metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComparisonService {

    private static final double EARTH_RADIUS_METERS = 6371000.0; // Earth radius in meters
    private static final double WEAK_SIGNAL_THRESHOLD = -80.0; // dBm
    private static final double HYBRID_DISTANCE_THRESHOLD = 1000.0; // meters
    private static final String WIFI_POSITION_FIELD = "wifiPosition";
    private static final String RESULT_FIELD = "result";
    private static final String SUCCESS_VALUE = "SUCCESS";
    
    private final ObjectMapper objectMapper;
    private final AccessPointEnrichmentService enrichmentService;

    /**
     * Compares a source response with positioning service results.
     */
    public ComparisonMetrics compareResults(SourceResponse sourceResponse, Object positioningServiceResponse) {
        return compareResults(sourceResponse, positioningServiceResponse, null, null, null);
    }

    /**
     * Compares a source response with positioning service results, including AP enrichment.
     */
    public ComparisonMetrics compareResults(SourceResponse sourceResponse, Object positioningServiceResponse, List<WifiInfo> originalWifiInfo) {
        return compareResults(sourceResponse, positioningServiceResponse, originalWifiInfo, null, null);
    }

    /**
     * Comprehensive comparison between VLSS and Frisco positioning services with performance tracking.
     */
    public ComparisonMetrics compareResults(SourceResponse sourceResponse, Object positioningServiceResponse, 
                                          List<WifiInfo> originalWifiInfo, List<CellInfo> cellInfo, 
                                          PerformanceData performanceData) {
        
        long comparisonStartTime = System.nanoTime();
        log.debug("Starting comprehensive comparison analysis");
        
        ComparisonMetrics metrics = new ComparisonMetrics();
        
        // Extract normalized data from both services
        NormalizedPosition friscoResult = normalizeFriscoResponse(positioningServiceResponse);
        NormalizedPosition vlssResult = normalizeVlssResponse(sourceResponse);
        
        // === Cross-Service Analysis ===
        analyzeServiceResults(metrics, vlssResult, friscoResult, sourceResponse, positioningServiceResponse);
        
        // === Position Comparison ===
        if (vlssResult != null && friscoResult != null && vlssResult.isValid() && friscoResult.isValid()) {
            computePositionComparison(metrics, vlssResult, friscoResult);
        }
        
        // === Method Analysis ===
        analyzePositioningMethods(metrics, friscoResult);
        
        // === Access Point Analysis ===
        analyzeAccessPoints(metrics, originalWifiInfo, cellInfo, positioningServiceResponse);
        
        // === Performance Analysis ===
        if (performanceData != null) {
            analyzePerformance(metrics, performanceData, comparisonStartTime);
        }
        
        // === Data Quality Analysis ===
        analyzeDataQuality(metrics, originalWifiInfo);
        
        // === Error Analysis ===
        analyzeErrors(metrics, positioningServiceResponse);
        
        // Set legacy fields for backwards compatibility
        setLegacyFields(metrics, friscoResult);
        
        long comparisonEndTime = System.nanoTime();
        long comparisonTimeMs = (comparisonEndTime - comparisonStartTime) / 1_000_000;
        
        log.debug("Comparison analysis completed in {}ms - scenario: {}, method: {}", 
            comparisonTimeMs, metrics.getScenario(), metrics.getPositioningMethod());
        
        return metrics;
    }
    
    // === Cross-Service Analysis Methods ===
    
    /**
     * Analyzes the results from both services to determine scenario and positioning method.
     */
    private void analyzeServiceResults(ComparisonMetrics metrics, NormalizedPosition vlssResult, 
                                     NormalizedPosition friscoResult, SourceResponse sourceResponse, 
                                     Object positioningServiceResponse) {
        
        Boolean vlssSuccess = determineVlssSuccess(sourceResponse);
        Boolean friscoSuccess = determineFriscoSuccess(positioningServiceResponse);
        
        metrics.setVlssSuccess(vlssSuccess);
        metrics.setFriscoSuccess(friscoSuccess);
        
        // Determine comparison scenario
        ComparisonScenario scenario = ComparisonScenario.determineScenario(vlssSuccess, friscoSuccess);
        metrics.setScenario(scenario);
        
        // Determine positioning method based on scenario
        Double distance = null;
        if (vlssResult != null && friscoResult != null && vlssResult.isValid() && friscoResult.isValid()) {
            distance = calculateHaversineDistance(
                vlssResult.getLatitude(), vlssResult.getLongitude(),
                friscoResult.getLatitude(), friscoResult.getLongitude()
            );
        }
        
        PositioningMethod method = PositioningMethod.determineMethod(scenario, distance);
        metrics.setPositioningMethod(method);
        
        log.debug("Service analysis: VLSS={}, Frisco={}, Scenario={}, Method={}", 
            vlssSuccess, friscoSuccess, scenario, method);
    }
    
    /**
     * Computes detailed position comparison when both services succeed.
     */
    private void computePositionComparison(ComparisonMetrics metrics, NormalizedPosition vlssResult, 
                                         NormalizedPosition friscoResult) {
        
        metrics.setPositionsComparable(true);
        
        // Compute haversine distance
        double distance = calculateHaversineDistance(
            vlssResult.getLatitude(), vlssResult.getLongitude(),
            friscoResult.getLatitude(), friscoResult.getLongitude()
        );
        metrics.setHaversineDistanceMeters(distance);
        
        // Compute coordinate deltas (Frisco - VLSS)
        metrics.setLatitudeDelta(friscoResult.getLatitude() - vlssResult.getLatitude());
        metrics.setLongitudeDelta(friscoResult.getLongitude() - vlssResult.getLongitude());
        
        // Compute altitude delta if available
        if (vlssResult.getAltitude() != null && friscoResult.getAltitude() != null) {
            metrics.setAltitudeDelta(friscoResult.getAltitude() - vlssResult.getAltitude());
        }
        
        // Compute accuracy and confidence deltas
        if (vlssResult.getAccuracy() != null && friscoResult.getAccuracy() != null) {
            metrics.setAccuracyDelta(friscoResult.getAccuracy() - vlssResult.getAccuracy());
        }
        
        if (vlssResult.getConfidence() != null && friscoResult.getConfidence() != null) {
            metrics.setConfidenceDelta(friscoResult.getConfidence() - vlssResult.getConfidence());
        }
        
        log.debug("Position comparison: distance={}m, latDelta={}, lonDelta={}, accuracyDelta={}", 
            distance, metrics.getLatitudeDelta(), metrics.getLongitudeDelta(), metrics.getAccuracyDelta());
    }
    
    /**
     * Analyzes positioning methods used by both services.
     */
    private void analyzePositioningMethods(ComparisonMetrics metrics, 
                                         NormalizedPosition friscoResult) {
        
        // Set Frisco methods
        if (friscoResult != null && friscoResult.getMethodsUsed() != null) {
            metrics.setFriscoMethodsUsed(friscoResult.getMethodsUsed());
        }
        
        // VLSS method detection (basic heuristic based on scenario)
        if (metrics.getScenario() == ComparisonScenario.VLSS_CELL_FALLBACK_DETECTED) {
            metrics.setVlssMethodUsed("cell_tower_fallback");
        } else if (metrics.getScenario() == ComparisonScenario.BOTH_WIFI_SUCCESS) {
            metrics.setVlssMethodUsed("wifi_positioning");
        }
        
        // Compare methods
        if (metrics.getFriscoMethodsUsed() != null && metrics.getVlssMethodUsed() != null) {
            if ("wifi_positioning".equals(metrics.getVlssMethodUsed())) {
                metrics.setMethodComparison("Both used WiFi positioning");
            } else {
                metrics.setMethodComparison("VLSS used " + metrics.getVlssMethodUsed() + 
                    ", Frisco used " + String.join(", ", metrics.getFriscoMethodsUsed()));
            }
        }
    }
    
    /**
     * Analyzes access point usage and enrichment.
     */
    private void analyzeAccessPoints(ComparisonMetrics metrics, List<WifiInfo> originalWifiInfo, 
                                   List<CellInfo> cellInfo, Object positioningServiceResponse) {
        
        // Set basic counts
        if (originalWifiInfo != null) {
            metrics.setRequestApCount(originalWifiInfo.size());
            
            // Count valid frequency APs
            long validFreqCount = originalWifiInfo.stream()
                .filter(ap -> ap.getFrequency() != null && ap.getFrequency() > 0)
                .count();
            metrics.setValidFrequencyApCount((int) validFreqCount);
            
            // Check if frequency defaults were needed
            metrics.setFrequencyDefaultsUsed(validFreqCount < originalWifiInfo.size());
        }
        
        if (cellInfo != null) {
            metrics.setRequestCellCount(cellInfo.size());
        }
        
        // Extract Frisco AP count from response
        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            JsonNode wifiPosition = node.get(WIFI_POSITION_FIELD);
            if (wifiPosition != null) {
                metrics.setFriscoApCount(getIntegerValue(wifiPosition, "apCount"));
            }
        } catch (Exception e) {
            log.debug("Could not extract Frisco AP count: {}", e.getMessage());
        }
        
        // Add AP enrichment if original WiFi info is available
        if (originalWifiInfo != null && !originalWifiInfo.isEmpty()) {
            try {
                AccessPointEnrichmentMetrics enrichmentMetrics = enrichmentService.enrichAccessPoints(
                    originalWifiInfo, positioningServiceResponse);
                metrics.setAccessPointEnrichment(enrichmentMetrics);
                
                log.debug("AP enrichment: found={}/{}, used={}", 
                    enrichmentMetrics.getFoundApCount(), originalWifiInfo.size(), 
                    enrichmentMetrics.getUsedApCount());
                    
            } catch (Exception e) {
                log.warn("Failed to enrich access point information: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Analyzes performance metrics and timing breakdown.
     */
    private void analyzePerformance(ComparisonMetrics metrics, PerformanceData performanceData, 
                                  long comparisonStartTime) {
        
        metrics.setFriscoResponseTimeMs(performanceData.getFriscoResponseTimeMs());
        metrics.setTransformationTimeMs(performanceData.getTransformationTimeMs());
        
        // Extract Frisco internal calculation time
        try {
            if (performanceData.getFriscoResponse() != null) {
                JsonNode node = objectMapper.valueToTree(performanceData.getFriscoResponse());
                JsonNode wifiPosition = node.get(WIFI_POSITION_FIELD);
                if (wifiPosition != null) {
                    Long calcTime = getLongValue(wifiPosition, "calculationTimeMs");
                    metrics.setFriscoCalculationTimeMs(calcTime);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract Frisco calculation time: {}", e.getMessage());
        }
        
        // Performance breakdown
        Map<String, Long> breakdown = new HashMap<>();
        breakdown.put("transformation", performanceData.getTransformationTimeMs());
        breakdown.put("frisco_total", performanceData.getFriscoResponseTimeMs());
        if (metrics.getFriscoCalculationTimeMs() != null) {
            breakdown.put("frisco_calculation", metrics.getFriscoCalculationTimeMs());
            breakdown.put("frisco_network", performanceData.getFriscoResponseTimeMs() - 
                metrics.getFriscoCalculationTimeMs());
        }
        
        long currentTime = System.nanoTime();
        long totalTime = (currentTime - comparisonStartTime) / 1_000_000;
        breakdown.put("comparison_analysis", totalTime);
        
        metrics.setPerformanceBreakdown(breakdown);
        metrics.setTotalProcessingTimeMs(performanceData.getTransformationTimeMs() + 
            performanceData.getFriscoResponseTimeMs() + totalTime);
    }
    
    /**
     * Analyzes data quality from the original request.
     */
    private void analyzeDataQuality(ComparisonMetrics metrics, List<WifiInfo> originalWifiInfo) {
        if (originalWifiInfo == null || originalWifiInfo.isEmpty()) {
            return;
        }
        
        List<String> qualityFlags = new ArrayList<>();
        
        // Signal strength analysis
        ComparisonMetrics.SignalStrengthStats stats = new ComparisonMetrics.SignalStrengthStats();
        List<Double> signals = originalWifiInfo.stream()
            .filter(ap -> ap.getSignalStrength() != null)
            .map(WifiInfo::getSignalStrength)
            .toList();
        
        if (!signals.isEmpty()) {
            stats.setMinSignalStrength(signals.stream().mapToDouble(Double::doubleValue).min().orElse(0.0));
            stats.setMaxSignalStrength(signals.stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
            stats.setAvgSignalStrength(signals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
            stats.setSignalRange(stats.getMaxSignalStrength() - stats.getMinSignalStrength());
            
            long weakSignals = signals.stream()
                .filter(signal -> signal < WEAK_SIGNAL_THRESHOLD)
                .count();
            stats.setWeakSignalCount((int) weakSignals);
            
            if (weakSignals > 0) {
                qualityFlags.add("WEAK_SIGNALS_DETECTED");
            }
            if (stats.getSignalRange() > 40.0) { // Large signal range
                qualityFlags.add("HIGH_SIGNAL_VARIANCE");
            }
        }
        
        metrics.setSignalStrengthStats(stats);
        
        // Other quality checks
        if (metrics.getValidFrequencyApCount() != null && 
            metrics.getRequestApCount() != null &&
            metrics.getValidFrequencyApCount() < metrics.getRequestApCount()) {
            qualityFlags.add("MISSING_FREQUENCY_DATA");
        }
        
        if (originalWifiInfo.size() < 3) {
            qualityFlags.add("INSUFFICIENT_AP_COUNT");
        }
        
        metrics.setDataQualityFlags(qualityFlags);
    }
    
    /**
     * Analyzes errors from both services when they fail.
     */
    private void analyzeErrors(ComparisonMetrics metrics, Object positioningServiceResponse) {
        
        // Analyze Frisco errors
        if (Boolean.FALSE.equals(metrics.getFriscoSuccess())) {
            analyzeFriscoErrors(metrics, positioningServiceResponse);
        }
        
        // Analyze VLSS errors
        if (Boolean.FALSE.equals(metrics.getVlssSuccess())) {
            metrics.setVlssErrorDetails("VLSS positioning failed");
        }
        
        // Cross-service failure analysis
        analyzeScenarioFailures(metrics);
    }
    
    /**
     * Analyzes Frisco-specific error details.
     */
    private void analyzeFriscoErrors(ComparisonMetrics metrics, Object positioningServiceResponse) {
        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            String message = getTextValue(node, "message");
            String result = getTextValue(node, RESULT_FIELD);
            
            setFriscoErrorDetails(metrics, message, result);
            setFailureAnalysisFromMessage(metrics, message);
            
        } catch (Exception e) {
            log.debug("Could not extract Frisco error details: {}", e.getMessage());
        }
    }
    
    /**
     * Sets Frisco error details based on message and result.
     */
    private void setFriscoErrorDetails(ComparisonMetrics metrics, String message, String result) {
        if (message != null) {
            metrics.setFriscoErrorDetails(message);
        } else if (result != null) {
            metrics.setFriscoErrorDetails("Result: " + result);
        }
    }
    
    /**
     * Sets failure analysis based on error message patterns.
     */
    private void setFailureAnalysisFromMessage(ComparisonMetrics metrics, String message) {
        if (message != null) {
            if (message.contains("access point") || message.contains("insufficient")) {
                metrics.setFailureAnalysis("Frisco failed due to insufficient WiFi access points in database");
            } else if (message.contains("timeout")) {
                metrics.setFailureAnalysis("Frisco failed due to timeout");
            }
        }
    }
    
    /**
     * Analyzes failure scenarios based on comparison scenario.
     */
    private void analyzeScenarioFailures(ComparisonMetrics metrics) {
        if (metrics.getScenario() == ComparisonScenario.VLSS_CELL_FALLBACK_DETECTED) {
            metrics.setFailureAnalysis("VLSS succeeded using cell tower fallback while Frisco failed due to insufficient WiFi APs");
        } else if (metrics.getScenario() == ComparisonScenario.BOTH_INSUFFICIENT_DATA) {
            metrics.setFailureAnalysis("Both services failed due to insufficient positioning data");
        }
    }
    
    /**
     * Sets legacy fields for backwards compatibility.
     */
    private void setLegacyFields(ComparisonMetrics metrics, NormalizedPosition friscoResult) {
        if (friscoResult != null) {
            metrics.setMethodsUsed(friscoResult.getMethodsUsed());
            metrics.setApCount(friscoResult.getApCount());
            metrics.setCalculationTimeMs(friscoResult.getCalculationTimeMs());
        }
    }
    
    // === Normalization Methods ===
    
    /**
     * Normalizes Frisco positioning service response.
     */
    private NormalizedPosition normalizeFriscoResponse(Object response) {
        if (response == null) {
            return null;
        }
        
        try {
            JsonNode node = objectMapper.valueToTree(response);
            
            // Check if this is a successful response
            String result = getTextValue(node, RESULT_FIELD);
            if (!SUCCESS_VALUE.equals(result)) {
                log.debug("Frisco response was not successful: {}", result);
                return null;
            }
            
            JsonNode wifiPosition = node.get(WIFI_POSITION_FIELD);
            if (wifiPosition == null) {
                log.debug("No wifiPosition found in Frisco response");
                return null;
            }
            
            NormalizedPosition position = new NormalizedPosition();
            position.setLatitude(getDoubleValue(wifiPosition, "latitude"));
            position.setLongitude(getDoubleValue(wifiPosition, "longitude"));
            position.setAltitude(getDoubleValue(wifiPosition, "altitude"));
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
            log.warn("Failed to normalize Frisco response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalizes VLSS source response.
     */
    private NormalizedPosition normalizeVlssResponse(SourceResponse sourceResponse) {
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
    
    // === Helper Methods ===
    
    /**
     * Determines if VLSS succeeded based on source response.
     */
    private Boolean determineVlssSuccess(SourceResponse sourceResponse) {
        if (sourceResponse == null) {
            return null; // No VLSS response provided
        }
        return Boolean.TRUE.equals(sourceResponse.getSuccess());
    }
    
    /**
     * Determines if Frisco succeeded based on positioning service response.
     */
    private Boolean determineFriscoSuccess(Object positioningServiceResponse) {
        if (positioningServiceResponse == null) {
            return false;
        }
        
        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            String result = getTextValue(node, RESULT_FIELD);
            return SUCCESS_VALUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Calculates the haversine distance between two points on Earth.
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);
        
        double dlat = lat2Rad - lat1Rad;
        double dlon = lon2Rad - lon1Rad;
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_METERS * c;
    }

    // JSON helper methods
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

    // === Helper Classes ===
    
    /**
     * Performance data container for timing analysis.
     */
    public static class PerformanceData {
        private Long friscoResponseTimeMs;
        private Long transformationTimeMs;
        private Object friscoResponse;
        
        public PerformanceData(Long friscoResponseTimeMs, Long transformationTimeMs, Object friscoResponse) {
            this.friscoResponseTimeMs = friscoResponseTimeMs;
            this.transformationTimeMs = transformationTimeMs;
            this.friscoResponse = friscoResponse;
        }
        
        // Getters
        public Long getFriscoResponseTimeMs() { return friscoResponseTimeMs; }
        public Long getTransformationTimeMs() { return transformationTimeMs; }
        public Object getFriscoResponse() { return friscoResponse; }
    }
    
    /**
     * Internal class for normalized position data.
     */
    private static class NormalizedPosition {
        private Double latitude;
        private Double longitude;
        private Double altitude;
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
        
        public Double getAltitude() { return altitude; }
        public void setAltitude(Double altitude) { this.altitude = altitude; }
        
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