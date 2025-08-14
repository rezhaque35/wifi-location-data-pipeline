// com/wifi/positioning/dto/PositioningServiceResult.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Result of calling the WiFi positioning service including HTTP status,
 * latency, and the response body.
 */
@Data
public class PositioningServiceResult {
    
    /**
     * HTTP status code from positioning service (200, 400, 500, etc.)
     */
    @JsonProperty("httpStatus")
    private Integer httpStatus;
    
    /**
     * Latency in milliseconds for the positioning service call
     */
    @JsonProperty("latencyMs")
    private Long latencyMs;
    
    /**
     * Response body from positioning service (may contain result or error)
     */
    @JsonProperty("response")
    private Object response;
    
    /**
     * Whether the call was successful (2xx status code)
     */
    @JsonProperty("success")
    private Boolean success;
    
    /**
     * Error message if the call failed (timeout, connection error, etc.)
     */
    @JsonProperty("errorMessage")
    private String errorMessage;
}
