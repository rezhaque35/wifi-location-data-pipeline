// com/wifi/positioning/exception/IntegrationProcessingException.java
package com.wifi.positioning.exception;

/**
 * Exception thrown when there are internal processing errors in the integration service.
 */
public class IntegrationProcessingException extends RuntimeException {
    
    public IntegrationProcessingException(String message) {
        super(message);
    }
    
    public IntegrationProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

