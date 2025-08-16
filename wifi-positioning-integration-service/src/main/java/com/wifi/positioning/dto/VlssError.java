// com/wifi/positioning/dto/VlssError.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a single error from VLSS service svcError.errors array.
 */
@Data
public class VlssError {
    
    /**
     * Error code from VLSS service
     */
    @JsonProperty("code")
    private Integer code;
    
    /**
     * Error message from VLSS service
     */
    @JsonProperty("message")
    private String message;
    
    /**
     * Error description with guidance from VLSS service
     */
    @JsonProperty("description")
    private String description;
}
