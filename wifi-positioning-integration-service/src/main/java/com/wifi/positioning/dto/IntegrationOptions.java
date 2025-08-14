// com/wifi/positioning/dto/IntegrationOptions.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Options for configuring integration behavior.
 */
@Data
public class IntegrationOptions {
    
    /**
     * Whether to request detailed calculation information from positioning service
     */
    @JsonProperty("calculationDetail")
    private Boolean calculationDetail = true;
    
    /**
     * Processing mode: "sync" (default) or "async"
     */
    @JsonProperty("processingMode")
    private String processingMode = "sync";
}
