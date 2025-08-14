// com/wifi/positioning/dto/SourceResponse.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Client's source response containing their positioning result.
 * Used for comparison against our positioning service results.
 */
@Data
public class SourceResponse {
    
    /**
     * Whether the client's positioning attempt was successful
     */
    @JsonProperty("success")
    private Boolean success;
    
    /**
     * Location information from client's positioning result
     */
    @JsonProperty("locationInfo")
    private LocationInfo locationInfo;
    
    /**
     * Request ID that matches the original request
     */
    @JsonProperty("requestId")
    private String requestId;
    
    /**
     * Optional: Transaction ID from client
     */
    @JsonProperty("transactionId")
    private String transactionId;
    
    /**
     * Optional: Error message if success=false
     */
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    /**
     * Optional: Error code if success=false
     */
    @JsonProperty("errorCode")
    private String errorCode;
}
