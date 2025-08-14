// com/wifi/positioning/dto/AccessPointEnrichmentMetrics.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Metrics about access point enrichment and usage in positioning calculation.
 */
@Data
public class AccessPointEnrichmentMetrics {
    
    /**
     * List of detailed information for each access point
     */
    @JsonProperty("apDetails")
    private List<AccessPointDetail> apDetails;
    
    /**
     * Number of APs from the request that were found in the database
     */
    @JsonProperty("foundApCount")
    private Integer foundApCount;
    
    /**
     * Number of APs from the request that were not found in the database
     */
    @JsonProperty("notFoundApCount")
    private Integer notFoundApCount;
    
    /**
     * Percentage of requested APs that were found in the database
     */
    @JsonProperty("percentRequestFound")
    private Double percentRequestFound;
    
    /**
     * Count of found APs by status (e.g., {"active": 3, "inactive": 1})
     */
    @JsonProperty("foundApStatusCounts")
    private Map<String, Integer> foundApStatusCounts;
    
    /**
     * Number of found APs that are eligible for positioning
     * (have valid status for positioning calculations)
     */
    @JsonProperty("eligibleApCount")
    private Integer eligibleApCount;
    
    /**
     * Number of APs actually used in the positioning calculation
     * (from positioning service response)
     */
    @JsonProperty("usedApCount")
    private Integer usedApCount;
    
    /**
     * Percentage of found APs that were actually used
     */
    @JsonProperty("percentFoundUsed")
    private Double percentFoundUsed;
    
    /**
     * Number of eligible APs that were excluded for unknown reasons
     * (eligible - used, but capped at 0)
     */
    @JsonProperty("unknownExclusions")
    private Integer unknownExclusions;
}
