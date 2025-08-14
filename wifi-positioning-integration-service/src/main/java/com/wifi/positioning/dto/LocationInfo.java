// com/wifi/positioning/dto/LocationInfo.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Location information from client's source response.
 * Used for comparison against positioning service results.
 */
@Data
public class LocationInfo {
    
    /**
     * Latitude in decimal degrees
     */
    @JsonProperty("latitude")
    private Double latitude;
    
    /**
     * Longitude in decimal degrees
     */
    @JsonProperty("longitude")
    private Double longitude;
    
    /**
     * Accuracy estimate in meters
     */
    @JsonProperty("accuracy")
    private Double accuracy;
    
    /**
     * Optional: Altitude in meters
     */
    @JsonProperty("altitude")
    private Double altitude;
    
    /**
     * Optional: Confidence level (0.0 to 1.0)
     */
    @JsonProperty("confidence")
    private Double confidence;
}
