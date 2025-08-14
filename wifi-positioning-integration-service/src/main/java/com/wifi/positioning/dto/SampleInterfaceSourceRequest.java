// com/wifi/positioning/dto/SampleInterfaceSourceRequest.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Complete sample interface source request structure.
 * Represents the original request format that clients may use.
 */
@Data
public class SampleInterfaceSourceRequest {
    
    /**
     * Service header containing authentication information
     */
    @JsonProperty("svcHeader")
    private SvcHeader svcHeader;
    
    /**
     * Service body containing the main positioning request
     */
    @NotNull(message = "Service body (svcBody) is required")
    @Valid
    @JsonProperty("svcBody")
    private SvcBody svcBody;
}
