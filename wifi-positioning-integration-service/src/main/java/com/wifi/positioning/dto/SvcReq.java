// com/wifi/positioning/dto/SvcReq.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Service request payload from the sample interface containing positioning data.
 */
@Data
public class SvcReq {
    
    /**
     * Client identifier - maps to 'client' field in positioning service
     */
    @NotBlank(message = "Client ID is required")
    @JsonProperty("clientId")
    private String clientId;
    
    /**
     * Request identifier for tracking
     */
    @NotBlank(message = "Request ID is required")
    @JsonProperty("requestId")
    private String requestId;
    
    /**
     * WiFi access point scan results - must contain at least one entry
     */
    @NotEmpty(message = "WiFi info must contain at least one access point")
    @Valid
    @JsonProperty("wifiInfo")
    private List<WifiInfo> wifiInfo;
    
    /**
     * Cell tower information (optional, not used for WiFi positioning)
     */
    @JsonProperty("cellInfo")
    private List<CellInfo> cellInfo;
}
