// com/wifi/positioning/dto/WifiInfo.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * WiFi access point information from the sample interface.
 * Represents a single WiFi scan result with MAC address and signal strength.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WifiInfo {
    
    /**
     * MAC address of the WiFi access point (BSSID)
     */
    @NotBlank(message = "WiFi MAC address (id) is required")
    @JsonProperty("id")
    private String id;
    
    /**
     * Signal strength in dBm (typically negative values like -53)
     */
    @NotNull(message = "Signal strength is required")
    @JsonProperty("signalStrength")
    private Double signalStrength;
    
    /**
     * Optional: Frequency in MHz (e.g., 2437 for 2.4GHz, 5180 for 5GHz)
     * If missing, will be dropped or use default based on configuration
     */
    @JsonProperty("frequency")
    private Integer frequency;
    
    /**
     * Optional: SSID (network name) of the access point
     */
    @JsonProperty("ssid")
    private String ssid;
}
