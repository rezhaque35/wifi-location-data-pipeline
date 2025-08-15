// com/wifi/positioning/exception/AsyncExceptionTest.java
package com.wifi.positioning.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for async-related exceptions.
 */
class AsyncExceptionTest {

    @Test
    void asyncProcessingUnavailableExceptionShouldHaveMessage() {
        // Given
        String message = "Async processing is unavailable";
        
        // When
        AsyncProcessingUnavailableException exception = 
            new AsyncProcessingUnavailableException(message);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void asyncProcessingUnavailableExceptionShouldHaveMessageAndCause() {
        // Given
        String message = "Async processing failed";
        Throwable cause = new RuntimeException("Root cause");
        
        // When
        AsyncProcessingUnavailableException exception = 
            new AsyncProcessingUnavailableException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void integrationProcessingExceptionShouldHaveMessage() {
        // Given
        String message = "Integration processing failed";
        
        // When
        IntegrationProcessingException exception = 
            new IntegrationProcessingException(message);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void integrationProcessingExceptionShouldHaveMessageAndCause() {
        // Given
        String message = "Processing error occurred";
        Throwable cause = new IllegalStateException("Invalid state");
        
        // When
        IntegrationProcessingException exception = 
            new IntegrationProcessingException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void exceptionsShouldBeChainable() {
        // Given
        RuntimeException rootCause = new RuntimeException("Root issue");
        IntegrationProcessingException processingException = 
            new IntegrationProcessingException("Processing failed", rootCause);
        
        // When
        AsyncProcessingUnavailableException asyncException = 
            new AsyncProcessingUnavailableException("Async unavailable", processingException);
        
        // Then
        assertThat(asyncException.getCause()).isEqualTo(processingException);
        assertThat(asyncException.getCause().getCause()).isEqualTo(rootCause);
    }

    @Test
    void exceptionsShouldSupportNullMessages() {
        // When
        AsyncProcessingUnavailableException asyncException = 
            new AsyncProcessingUnavailableException(null);
        IntegrationProcessingException processingException = 
            new IntegrationProcessingException(null);
        
        // Then
        assertThat(asyncException.getMessage()).isNull();
        assertThat(processingException.getMessage()).isNull();
    }

    @Test
    void exceptionsShouldSupportNullCauses() {
        // Given
        String message = "Test message";
        
        // When
        AsyncProcessingUnavailableException asyncException = 
            new AsyncProcessingUnavailableException(message, null);
        IntegrationProcessingException processingException = 
            new IntegrationProcessingException(message, null);
        
        // Then
        assertThat(asyncException.getMessage()).isEqualTo(message);
        assertThat(asyncException.getCause()).isNull();
        assertThat(processingException.getMessage()).isEqualTo(message);
        assertThat(processingException.getCause()).isNull();
    }
}

