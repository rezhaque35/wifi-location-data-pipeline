// com/wifi/positioning/dto/SvcBody.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Service body containing the main request payload from the sample interface.
 */
@Data
public class SvcBody {
    
    /**
     * Service request containing positioning data
     */
    @NotNull(message = "Service request (svcReq) is required")
    @Valid
    @JsonProperty("svcReq")
    private SvcReq svcReq;
}
