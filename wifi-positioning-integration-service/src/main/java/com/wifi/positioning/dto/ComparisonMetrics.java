// com/wifi/positioning/dto/ComparisonMetrics.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Metrics comparing client's source response with positioning service results.
 */
@Data
public class ComparisonMetrics {
    
    /**
     * Haversine distance between the two positions in meters
     * (null if either position is missing)
     */
    @JsonProperty("haversineDistanceMeters")
    private Double haversineDistanceMeters;
    
    /**
     * Difference in accuracy estimates (positioning service - source)
     * Positive means positioning service reported higher accuracy
     */
    @JsonProperty("accuracyDelta")
    private Double accuracyDelta;
    
    /**
     * Difference in confidence values (positioning service - source)
     * Positive means positioning service reported higher confidence
     */
    @JsonProperty("confidenceDelta")
    private Double confidenceDelta;
    
    /**
     * Positioning methods used by the positioning service
     */
    @JsonProperty("methodsUsed")
    private List<String> methodsUsed;
    
    /**
     * Number of access points used in positioning calculation
     */
    @JsonProperty("apCount")
    private Integer apCount;
    
    /**
     * Time taken for positioning calculation in milliseconds
     */
    @JsonProperty("calculationTimeMs")
    private Long calculationTimeMs;
    
    /**
     * Whether both services produced valid positions for comparison
     */
    @JsonProperty("positionsComparable")
    private Boolean positionsComparable;
    
    /**
     * Access point enrichment metrics from positioning service response
     */
    @JsonProperty("accessPointEnrichment")
    private AccessPointEnrichmentMetrics accessPointEnrichment;
}
