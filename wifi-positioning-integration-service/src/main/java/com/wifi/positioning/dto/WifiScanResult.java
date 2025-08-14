// com/wifi/positioning/dto/WifiScanResult.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * WiFi scan result for internal mapping to positioning service.
 * This matches the positioning service's WifiScanResult structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WifiScanResult {
    
    /**
     * MAC address of the WiFi access point in XX:XX:XX:XX:XX:XX format
     */
    @NotBlank(message = "MAC address is required")
    @Pattern(
        regexp = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})",
        message = "Invalid MAC address format"
    )
    @JsonProperty("macAddress")
    private String macAddress;
    
    /**
     * Signal strength in dBm (typically -30 to -100)
     */
    @Min(value = -100, message = "Signal strength must be at least -100 dBm")
    @Max(value = 0, message = "Signal strength must be at most 0 dBm")
    @JsonProperty("signalStrength")
    private Double signalStrength;
    
    /**
     * Operating frequency in MHz (2400-6000)
     */
    @Min(value = 2400, message = "Frequency must be at least 2400 MHz")
    @Max(value = 6000, message = "Frequency must be at most 6000 MHz")
    @JsonProperty("frequency")
    private Integer frequency;
    
    /**
     * Network name (SSID) - optional
     */
    @JsonProperty("ssid")
    private String ssid;
    
    /**
     * Link speed in Mbps - optional
     */
    @Min(value = 0, message = "Link speed must be non-negative")
    @JsonProperty("linkSpeed")
    private Integer linkSpeed;
    
    /**
     * Channel width in MHz - optional
     */
    @Min(value = 20, message = "Channel width must be at least 20 MHz")
    @Max(value = 160, message = "Channel width must be at most 160 MHz")
    @JsonProperty("channelWidth")
    private Integer channelWidth;
}
