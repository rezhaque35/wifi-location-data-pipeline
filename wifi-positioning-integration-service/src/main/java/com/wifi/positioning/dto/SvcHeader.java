// com/wifi/positioning/dto/SvcHeader.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Header section of the sample interface request containing authentication information.
 * This data is logged but not forwarded to the positioning service.
 */
@Data
public class SvcHeader {
    
    @JsonProperty("authToken")
    private String authToken;
}
