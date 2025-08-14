// com/wifi/positioning/dto/IntegrationMetadata.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Metadata for integration request tracking and auditing.
 */
@Data
public class IntegrationMetadata {
    
    /**
     * Correlation ID for request tracking across systems
     */
    @JsonProperty("correlationId")
    private String correlationId;
    
    /**
     * Timestamp when request was received by client
     */
    @JsonProperty("receivedAt")
    private Instant receivedAt;
    
    /**
     * Client IP address for logging
     */
    @JsonProperty("clientIp")
    private String clientIp;
    
    /**
     * User agent string for logging
     */
    @JsonProperty("userAgent")
    private String userAgent;
}
