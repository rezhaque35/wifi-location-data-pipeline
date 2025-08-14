// com/wifi/positioning/dto/IntegrationReportResponse.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Response from the integration service containing comparison results
 * and metadata about the processing.
 */
@Data
public class IntegrationReportResponse {
    
    /**
     * Correlation ID for request tracking
     */
    @JsonProperty("correlationId")
    private String correlationId;
    
    /**
     * Timestamp when request was received by integration service
     */
    @JsonProperty("receivedAt")
    private Instant receivedAt;
    
    /**
     * Processing mode used (sync or async)
     */
    @JsonProperty("processingMode")
    private String processingMode;
    
    /**
     * The derived request that was sent to positioning service
     */
    @JsonProperty("derivedRequest")
    private Object derivedRequest;
    
    /**
     * Result from positioning service call
     */
    @JsonProperty("positioningService")
    private PositioningServiceResult positioningService;
    
    /**
     * Echo of the source response provided by client (if any)
     */
    @JsonProperty("sourceResponse")
    private SourceResponse sourceResponse;
    
    /**
     * Comparison metrics between source and positioning service results
     */
    @JsonProperty("comparison")
    private ComparisonMetrics comparison;
}
