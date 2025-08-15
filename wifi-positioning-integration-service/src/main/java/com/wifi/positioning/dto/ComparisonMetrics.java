// com/wifi/positioning/dto/ComparisonMetrics.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive metrics comparing VLSS source response with Frisco positioning service results.
 * Enhanced to support cross-service analysis and cell tower fallback detection.
 */
@Data
public class ComparisonMetrics {
    
    // === Cross-Service Analysis ===
    
    /**
     * Classification of the comparison scenario (e.g., BOTH_WIFI_SUCCESS, VLSS_CELL_FALLBACK_DETECTED)
     */
    @JsonProperty("scenario")
    private ComparisonScenario scenario;
    
    /**
     * Detected positioning method based on scenario analysis
     */
    @JsonProperty("positioningMethod")
    private PositioningMethod positioningMethod;
    
    /**
     * Whether VLSS service succeeded
     */
    @JsonProperty("vlssSuccess")
    private Boolean vlssSuccess;
    
    /**
     * Whether Frisco service succeeded
     */
    @JsonProperty("friscoSuccess")
    private Boolean friscoSuccess;
    
    // === Position Comparison (when both services succeed) ===
    
    /**
     * Haversine distance between the two positions in meters
     * (null if either position is missing)
     */
    @JsonProperty("haversineDistanceMeters")
    private Double haversineDistanceMeters;
    
    /**
     * Latitude difference (Frisco - VLSS) in decimal degrees
     */
    @JsonProperty("latitudeDelta")
    private Double latitudeDelta;
    
    /**
     * Longitude difference (Frisco - VLSS) in decimal degrees
     */
    @JsonProperty("longitudeDelta")
    private Double longitudeDelta;
    
    /**
     * Altitude difference (Frisco - VLSS) in meters (if available)
     */
    @JsonProperty("altitudeDelta")
    private Double altitudeDelta;
    
    /**
     * Difference in accuracy estimates (Frisco - VLSS)
     * Positive means Frisco reported higher accuracy
     */
    @JsonProperty("accuracyDelta")
    private Double accuracyDelta;
    
    /**
     * Difference in confidence values (Frisco - VLSS)
     * Positive means Frisco reported higher confidence
     */
    @JsonProperty("confidenceDelta")
    private Double confidenceDelta;
    
    /**
     * Whether both services produced valid positions for comparison
     */
    @JsonProperty("positionsComparable")
    private Boolean positionsComparable;
    
    // === Method Analysis ===
    
    /**
     * Positioning methods used by the Frisco service
     */
    @JsonProperty("friscoMethodsUsed")
    private List<String> friscoMethodsUsed;
    
    /**
     * VLSS positioning method used (if indicated in response)
     */
    @JsonProperty("vlssMethodUsed")
    private String vlssMethodUsed;
    
    /**
     * Comparison of methods used by both services
     */
    @JsonProperty("methodComparison")
    private String methodComparison;
    
    // === Access Point Analysis ===
    
    /**
     * Number of access points used by Frisco in positioning calculation
     */
    @JsonProperty("friscoApCount")
    private Integer friscoApCount;
    
    /**
     * Number of WiFi APs in the original request
     */
    @JsonProperty("requestApCount")
    private Integer requestApCount;
    
    /**
     * Number of cell towers in the original request (if any)
     */
    @JsonProperty("requestCellCount")
    private Integer requestCellCount;
    
    /**
     * Access point enrichment metrics from Frisco service response
     */
    @JsonProperty("accessPointEnrichment")
    private AccessPointEnrichmentMetrics accessPointEnrichment;
    
    // === Performance Metrics ===
    
    /**
     * Frisco service response time (wall time) in milliseconds
     */
    @JsonProperty("friscoResponseTimeMs")
    private Long friscoResponseTimeMs;
    
    /**
     * Frisco internal calculation time in milliseconds
     */
    @JsonProperty("friscoCalculationTimeMs")
    private Long friscoCalculationTimeMs;
    
    /**
     * Request transformation time in milliseconds
     */
    @JsonProperty("transformationTimeMs")
    private Long transformationTimeMs;
    
    /**
     * Total integration processing time in milliseconds
     */
    @JsonProperty("totalProcessingTimeMs")
    private Long totalProcessingTimeMs;
    
    /**
     * Performance breakdown (network vs computation time)
     */
    @JsonProperty("performanceBreakdown")
    private Map<String, Long> performanceBreakdown;
    
    // === Data Quality Analysis ===
    
    /**
     * Signal strength statistics from original request
     */
    @JsonProperty("signalStrengthStats")
    private SignalStrengthStats signalStrengthStats;
    
    /**
     * Number of APs with valid frequency data
     */
    @JsonProperty("validFrequencyApCount")
    private Integer validFrequencyApCount;
    
    /**
     * Whether frequency defaults were used during transformation
     */
    @JsonProperty("frequencyDefaultsUsed")
    private Boolean frequencyDefaultsUsed;
    
    /**
     * Data quality flags and warnings
     */
    @JsonProperty("dataQualityFlags")
    private List<String> dataQualityFlags;
    
    // === Error Analysis (when services fail) ===
    
    /**
     * Frisco service error details (if failed)
     */
    @JsonProperty("friscoErrorDetails")
    private String friscoErrorDetails;
    
    /**
     * VLSS service error details (if failed)
     */
    @JsonProperty("vlssErrorDetails")
    private String vlssErrorDetails;
    
    /**
     * Analysis of why positioning failed
     */
    @JsonProperty("failureAnalysis")
    private String failureAnalysis;
    
    // === Legacy Fields (for backwards compatibility) ===
    
    /**
     * @deprecated Use friscoMethodsUsed instead
     */
    @JsonProperty("methodsUsed")
    @Deprecated(since = "2.0", forRemoval = true)
    private List<String> methodsUsed;
    
    /**
     * @deprecated Use friscoApCount instead
     */
    @JsonProperty("apCount")
    @Deprecated(since = "2.0", forRemoval = true)
    private Integer apCount;
    
    /**
     * @deprecated Use friscoCalculationTimeMs instead
     */
    @JsonProperty("calculationTimeMs")
    @Deprecated(since = "2.0", forRemoval = true)
    private Long calculationTimeMs;
    
    // === Helper Inner Class ===
    
    /**
     * Signal strength statistics for data quality analysis
     */
    @Data
    public static class SignalStrengthStats {
        @JsonProperty("minSignalStrength")
        private Double minSignalStrength;
        
        @JsonProperty("maxSignalStrength")
        private Double maxSignalStrength;
        
        @JsonProperty("avgSignalStrength")
        private Double avgSignalStrength;
        
        @JsonProperty("signalRange")
        private Double signalRange;
        
        @JsonProperty("weakSignalCount")
        private Integer weakSignalCount; // Count of signals < -80 dBm
    }
}
