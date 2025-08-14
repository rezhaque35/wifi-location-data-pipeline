// com/wifi/positioning/dto/WifiPositioningRequest.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Request DTO for the WiFi positioning service.
 * This matches the positioning service's WifiPositioningRequest structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WifiPositioningRequest {
    
    /**
     * List of WiFi scan results for positioning calculation
     */
    @NotEmpty(message = "At least one WiFi scan result is required")
    @Size(min = 1, max = 20, message = "Between 1 and 20 WiFi scan results must be provided")
    @Valid
    @JsonProperty("wifiScanResults")
    private List<WifiScanResult> wifiScanResults;
    
    /**
     * Client identifier
     */
    @NotBlank(message = "Client is required")
    @Size(max = 50, message = "Client must be at most 50 characters")
    @JsonProperty("client")
    private String client;
    
    /**
     * Request ID for tracking
     */
    @NotBlank(message = "Request ID is required")
    @Size(max = 64, message = "Request ID must be at most 64 characters")
    @JsonProperty("requestId")
    private String requestId;
    
    /**
     * Application name
     */
    @Size(max = 100, message = "Application must be at most 100 characters")
    @JsonProperty("application")
    private String application;
    
    /**
     * Whether to include detailed calculation information in the response
     */
    @JsonProperty("calculationDetail")
    private Boolean calculationDetail;
    

}
