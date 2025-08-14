// com/wifi/positioning/dto/AccessPointDetail.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Detailed information about an access point including both provided data
 * and information found in the positioning service database.
 */
@Data
public class AccessPointDetail {
    
    /**
     * MAC address (BSSID) of the access point
     */
    @JsonProperty("mac")
    private String mac;
    
    /**
     * SSID provided in the original request (if any)
     */
    @JsonProperty("providedSsid")
    private String providedSsid;
    
    /**
     * RSSI (signal strength) provided in the original request
     */
    @JsonProperty("providedRssi")
    private Double providedRssi;
    
    /**
     * Frequency provided in the original request
     */
    @JsonProperty("providedFrequency")
    private Integer providedFrequency;
    
    /**
     * Whether this AP was found in the positioning service database
     */
    @JsonProperty("found")
    private Boolean found;
    
    /**
     * Status of the AP in the database (active, inactive, etc.)
     */
    @JsonProperty("dbStatus")
    private String dbStatus;
    
    /**
     * Latitude of the AP from the database
     */
    @JsonProperty("dbLatitude")
    private Double dbLatitude;
    
    /**
     * Longitude of the AP from the database
     */
    @JsonProperty("dbLongitude")
    private Double dbLongitude;
    
    /**
     * Altitude of the AP from the database
     */
    @JsonProperty("dbAltitude")
    private Double dbAltitude;
    
    /**
     * Whether this AP is eligible for positioning calculations
     * (based on status being in valid set)
     */
    @JsonProperty("eligible")
    private Boolean eligible;
    
    /**
     * Whether this AP was actually used in the positioning calculation
     */
    @JsonProperty("used")
    private Boolean used;
}
