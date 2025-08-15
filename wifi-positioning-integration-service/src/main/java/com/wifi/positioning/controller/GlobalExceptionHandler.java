// com/wifi/positioning/controller/GlobalExceptionHandler.java
package com.wifi.positioning.controller;

import com.wifi.positioning.exception.AsyncProcessingUnavailableException;
import com.wifi.positioning.exception.IntegrationProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the integration service.
 * Provides consistent error responses and logging.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String TIMESTAMP = "timestamp";
    private static final String STATUS = "status";
    private static final String ERROR = "error";
    private static final String MESSAGE = "message";

    /**
     * Handles validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        Map<String, String> fieldErrors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });
        
        errorResponse.put(TIMESTAMP, Instant.now());
        errorResponse.put(STATUS, HttpStatus.BAD_REQUEST.value());
        errorResponse.put(ERROR, "Validation Failed");
        errorResponse.put(MESSAGE, "Request validation failed");
        errorResponse.put("fieldErrors", fieldErrors);
        
        log.warn("Validation error: {}", fieldErrors);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handles illegal argument exceptions (business validation errors).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        errorResponse.put(TIMESTAMP, Instant.now());
        errorResponse.put(STATUS, HttpStatus.BAD_REQUEST.value());
        errorResponse.put(ERROR, "Bad Request");
        errorResponse.put(MESSAGE, ex.getMessage());
        
        log.warn("Business validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handles async processing unavailable exceptions (queue full, service overloaded).
     */
    @ExceptionHandler(AsyncProcessingUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAsyncProcessingUnavailableException(AsyncProcessingUnavailableException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        errorResponse.put(TIMESTAMP, Instant.now());
        errorResponse.put(STATUS, HttpStatus.SERVICE_UNAVAILABLE.value());
        errorResponse.put(ERROR, "Service Unavailable");
        errorResponse.put(MESSAGE, ex.getMessage());
        errorResponse.put("suggestedAction", "Try again later or use synchronous processing mode");
        
        log.warn("Async processing unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    /**
     * Handles integration processing exceptions (internal processing errors).
     */
    @ExceptionHandler(IntegrationProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrationProcessingException(IntegrationProcessingException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        errorResponse.put(TIMESTAMP, Instant.now());
        errorResponse.put(STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponse.put(ERROR, "Integration Processing Error");
        errorResponse.put(MESSAGE, ex.getMessage());
        
        log.error("Integration processing error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles all other unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        errorResponse.put(TIMESTAMP, Instant.now());
        errorResponse.put(STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponse.put(ERROR, "Internal Server Error");
        errorResponse.put(MESSAGE, "An unexpected error occurred");
        
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
