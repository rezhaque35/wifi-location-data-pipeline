// com/wifi/positioning/dto/IntegrationReportRequest.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Main integration report request containing client's original request,
 * optional response, processing options, and metadata.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrationReportRequest {
    
    /**
     * Client's original request in sample interface format.
     * This will be mapped to WifiPositioningRequest for the positioning service.
     */
    @NotNull(message = "Source request is required")
    @Valid
    @JsonProperty("sourceRequest")
    private SampleInterfaceSourceRequest sourceRequest;
    
    /**
     * Optional: Client's computed response for comparison
     */
    @JsonProperty("sourceResponse")
    private SourceResponse sourceResponse;
    
    /**
     * Processing options for this request
     */
    @JsonProperty("options")
    private IntegrationOptions options;
    
    /**
     * Request metadata for tracking and auditing
     */
    @JsonProperty("metadata")
    private IntegrationMetadata metadata;
}
