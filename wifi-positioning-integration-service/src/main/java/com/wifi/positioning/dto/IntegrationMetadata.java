// com/wifi/positioning/dto/IntegrationMetadata.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata for integration request tracking and auditing.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrationMetadata {
    
    /**
     * Correlation ID for request tracking across systems
     */
    @JsonProperty("correlationId")
    private String correlationId;
    
    /**
     * Timestamp when request was received by client
     * Using custom Jackson configuration for flexible parsing
     */
    @JsonProperty("receivedAt")
    private String receivedAt;
    
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
    
    /**
     * Bridge script identifier
     */
    @JsonProperty("bridgeScript")
    private String bridgeScript;
    
    /**
     * VLSS-specific metadata
     */
    @JsonProperty("vlls")
    private Map<String, Object> vlls;
}
