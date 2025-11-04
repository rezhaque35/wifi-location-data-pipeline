// src/main/java/com/wifi/positioning/dto/CellInfo.java
package com.wifi.positioning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents cell tower information from client device.
 * Used to validate WiFi access point locations by filtering out APs outside cell tower range.
 */
public record CellInfo(
    @NotNull(message = "Cell ID is required")
    Long id,
    
    Integer broadcastId,
    
    Integer networkId,
    
    @NotBlank(message = "Cell type is required")
    String cellType,
    
    @Min(value = -140, message = "Signal strength must be at least -140 dBm")
    @Max(value = -40, message = "Signal strength must be at most -40 dBm")
    Integer signalStrength
) {
    
    /**
     * Converts CellInfo to a Map for structured logging.
     * 
     * @return Map representation of the CellInfo
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("broadcastId", broadcastId);
        map.put("networkId", networkId);
        map.put("cellType", cellType);
        map.put("signalStrength", signalStrength);
        return map;
    }
}


