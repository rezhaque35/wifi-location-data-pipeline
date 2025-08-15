// com/wifi/positioning/controller/GlobalExceptionHandlerAsyncTest.java
package com.wifi.positioning.controller;

import com.wifi.positioning.exception.AsyncProcessingUnavailableException;
import com.wifi.positioning.exception.IntegrationProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler async-related exception handling.
 */
class GlobalExceptionHandlerAsyncTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleAsyncProcessingUnavailableException() {
        // Given
        String errorMessage = "Async processing queue is full. Please try again later.";
        AsyncProcessingUnavailableException exception = 
            new AsyncProcessingUnavailableException(errorMessage);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleAsyncProcessingUnavailableException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(503);
        assertThat(body.get("error")).isEqualTo("Service Unavailable");
        assertThat(body.get("message")).isEqualTo(errorMessage);
        assertThat(body.get("suggestedAction")).isEqualTo("Try again later or use synchronous processing mode");
        assertThat(body.get("timestamp")).isInstanceOf(Instant.class);
    }

    @Test
    void shouldHandleIntegrationProcessingException() {
        // Given
        String errorMessage = "Internal processing error occurred";
        RuntimeException cause = new RuntimeException("Root cause");
        IntegrationProcessingException exception = 
            new IntegrationProcessingException(errorMessage, cause);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleIntegrationProcessingException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(500);
        assertThat(body.get("error")).isEqualTo("Integration Processing Error");
        assertThat(body.get("message")).isEqualTo(errorMessage);
        assertThat(body.get("timestamp")).isInstanceOf(Instant.class);
    }

    @Test
    void shouldHandleAsyncProcessingUnavailableExceptionWithNullMessage() {
        // Given
        AsyncProcessingUnavailableException exception = 
            new AsyncProcessingUnavailableException(null);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleAsyncProcessingUnavailableException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isNull();
        assertThat(body.get("suggestedAction")).isNotNull();
    }

    @Test
    void shouldHandleIntegrationProcessingExceptionWithNullMessage() {
        // Given
        IntegrationProcessingException exception = 
            new IntegrationProcessingException(null);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleIntegrationProcessingException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isNull();
        assertThat(body.get("error")).isEqualTo("Integration Processing Error");
    }

    @Test
    void shouldHandleAsyncProcessingUnavailableExceptionWithCause() {
        // Given
        RuntimeException cause = new RuntimeException("Queue overflow");
        AsyncProcessingUnavailableException exception = 
            new AsyncProcessingUnavailableException("Async processing failed", cause);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleAsyncProcessingUnavailableException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo("Async processing failed");
        assertThat(body.get("status")).isEqualTo(503);
    }

    @Test
    void shouldHandleIntegrationProcessingExceptionWithCause() {
        // Given
        IllegalStateException cause = new IllegalStateException("Invalid configuration");
        IntegrationProcessingException exception = 
            new IntegrationProcessingException("Processing failed due to configuration", cause);

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleIntegrationProcessingException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo("Processing failed due to configuration");
        assertThat(body.get("status")).isEqualTo(500);
    }

    @Test
    void shouldUseConsistentErrorResponseFormat() {
        // Given
        AsyncProcessingUnavailableException asyncException = 
            new AsyncProcessingUnavailableException("Async error");
        IntegrationProcessingException processingException = 
            new IntegrationProcessingException("Processing error");

        // When
        ResponseEntity<Map<String, Object>> asyncResponse = 
            exceptionHandler.handleAsyncProcessingUnavailableException(asyncException);
        ResponseEntity<Map<String, Object>> processingResponse = 
            exceptionHandler.handleIntegrationProcessingException(processingException);

        // Then
        Map<String, Object> asyncBody = asyncResponse.getBody();
        Map<String, Object> processingBody = processingResponse.getBody();

        // Both should have consistent structure
        assertThat(asyncBody.keySet()).contains("timestamp", "status", "error", "message");
        assertThat(processingBody.keySet()).contains("timestamp", "status", "error", "message");
        
        // Timestamps should be close to current time
        Instant asyncTimestamp = (Instant) asyncBody.get("timestamp");
        Instant processingTimestamp = (Instant) processingBody.get("timestamp");
        Instant now = Instant.now();
        
        assertThat(asyncTimestamp).isBefore(now.plusSeconds(1));
        assertThat(processingTimestamp).isBefore(now.plusSeconds(1));
    }

    @Test
    void shouldProvideHelpfulSuggestedActionForAsyncUnavailable() {
        // Given
        AsyncProcessingUnavailableException exception = 
            new AsyncProcessingUnavailableException("Queue full");

        // When
        ResponseEntity<Map<String, Object>> response = 
            exceptionHandler.handleAsyncProcessingUnavailableException(exception);

        // Then
        Map<String, Object> body = response.getBody();
        String suggestedAction = (String) body.get("suggestedAction");
        
        assertThat(suggestedAction).isNotNull();
        assertThat(suggestedAction).contains("Try again later");
        assertThat(suggestedAction).contains("synchronous processing");
    }

    @Test
    void shouldReturnCorrectHttpStatusCodes() {
        // Given
        AsyncProcessingUnavailableException asyncException = 
            new AsyncProcessingUnavailableException("Async unavailable");
        IntegrationProcessingException processingException = 
            new IntegrationProcessingException("Processing error");

        // When
        ResponseEntity<Map<String, Object>> asyncResponse = 
            exceptionHandler.handleAsyncProcessingUnavailableException(asyncException);
        ResponseEntity<Map<String, Object>> processingResponse = 
            exceptionHandler.handleIntegrationProcessingException(processingException);

        // Then
        assertThat(asyncResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(processingResponse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        assertThat((Integer) asyncResponse.getBody().get("status")).isEqualTo(503);
        assertThat((Integer) processingResponse.getBody().get("status")).isEqualTo(500);
    }
}

