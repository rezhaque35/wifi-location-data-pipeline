// com/wifi/positioning/dto/VlssServiceError.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Represents the VLSS service svcError structure.
 */
@Data
public class VlssServiceError {
    
    /**
     * Array of errors from VLSS service
     */
    @JsonProperty("errors")
    private List<VlssError> errors;
}
