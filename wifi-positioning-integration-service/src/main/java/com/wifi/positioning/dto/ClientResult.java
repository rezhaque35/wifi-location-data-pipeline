// com/wifi/positioning/dto/ClientResult.java
package com.wifi.positioning.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Result of calling the positioning service client.
 * Contains HTTP status, response body, latency, and error information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientResult {
    
    /**
     * HTTP status code from the positioning service
     */
    private Integer httpStatus;
    
    /**
     * Response body from the positioning service (could be success or error response)
     */
    private Object responseBody;
    
    /**
     * Latency in milliseconds for the call
     */
    private Long latencyMs;
    
    /**
     * Whether the call was successful (2xx status code)
     */
    private Boolean success;
    
    /**
     * Error message if the call failed (timeout, connection error, etc.)
     */
    private String errorMessage;
    
    /**
     * Create a successful result
     */
    public static ClientResult success(int httpStatus, Object responseBody, long latencyMs) {
        return new ClientResult(httpStatus, responseBody, latencyMs, true, null);
    }
    
    /**
     * Create an error result
     */
    public static ClientResult error(Integer httpStatus, String errorMessage, long latencyMs) {
        return new ClientResult(httpStatus, null, latencyMs, false, errorMessage);
    }
    
    /**
     * Create an error result with response body (e.g., 4xx/5xx with error details)
     */
    public static ClientResult error(int httpStatus, Object responseBody, String errorMessage, long latencyMs) {
        return new ClientResult(httpStatus, responseBody, latencyMs, false, errorMessage);
    }
}
