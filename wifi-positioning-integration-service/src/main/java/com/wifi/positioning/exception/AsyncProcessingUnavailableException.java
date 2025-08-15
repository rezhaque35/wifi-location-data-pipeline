// com/wifi/positioning/exception/AsyncProcessingUnavailableException.java
package com.wifi.positioning.exception;

/**
 * Exception thrown when async processing is unavailable due to queue being full
 * or other async processing constraints.
 */
public class AsyncProcessingUnavailableException extends RuntimeException {
    
    public AsyncProcessingUnavailableException(String message) {
        super(message);
    }
    
    public AsyncProcessingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

