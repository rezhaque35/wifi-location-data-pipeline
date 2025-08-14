// com/wifi/positioning/dto/CellInfo.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Cell tower information from the sample interface.
 * This data is preserved but not used for WiFi positioning.
 */
@Data
public class CellInfo {
    
    @JsonProperty("id")
    private Integer id;
    
    @JsonProperty("broadcastId")
    private Integer broadcastId;
    
    @JsonProperty("networkId")
    private Integer networkId;
    
    @JsonProperty("cellType")
    private String cellType;
    
    @JsonProperty("signalStrength")
    private Double signalStrength;
}
