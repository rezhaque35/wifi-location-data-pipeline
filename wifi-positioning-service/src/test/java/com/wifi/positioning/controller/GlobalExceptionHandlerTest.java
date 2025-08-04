package com.wifi.positioning.controller;

import com.wifi.positioning.dto.WifiPositioningResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the GlobalExceptionHandler.
 * Verifies that exceptions are handled correctly and appropriate WifiPositioningResponse objects
 * are returned with the correct error messages and status codes.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    
    @Test
    void should_ReturnBadRequest_When_HandlingIllegalArgumentException() {
        // Arrange
        IllegalArgumentException exception = new IllegalArgumentException("Invalid argument");
        
        // Act
        ResponseEntity<WifiPositioningResponse> response = handler.handleIllegalArgumentException(exception);
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ERROR", response.getBody().result());
        assertEquals("Error 400: Invalid argument", response.getBody().message());
        assertNull(response.getBody().wifiPosition());
    }
    

    
    @Test
    void should_ReturnValidationErrors_When_HandlingMethodArgumentNotValidException() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError1 = new FieldError("object", "field1", "Field 1 error");
        FieldError fieldError2 = new FieldError("object", "field2", "Field 2 error");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(java.util.Arrays.asList(fieldError1, fieldError2));
        
        // Act
        ResponseEntity<WifiPositioningResponse> response = handler.handleValidationExceptions(exception);
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ERROR", response.getBody().result());
        assertTrue(response.getBody().message().contains("Validation failed"));
        assertTrue(response.getBody().message().contains("field1 - Field 1 error"));
        assertTrue(response.getBody().message().contains("field2 - Field 2 error"));
        assertNull(response.getBody().wifiPosition());
    }
    
    @Test
    void should_ReturnInternalServerError_When_HandlingGenericException() {
        // Arrange
        Exception exception = new RuntimeException("Something went wrong");
        WebRequest request = mock(WebRequest.class);
        
        // Act
        ResponseEntity<WifiPositioningResponse> response = handler.handleGlobalException(exception, request);
        
        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("ERROR", response.getBody().result());
        assertEquals("Error 500: An unexpected error occurred: Something went wrong", response.getBody().message());
        assertNull(response.getBody().wifiPosition());
        assertNotNull(response.getBody().requestId());
        assertEquals("system", response.getBody().client());
        assertEquals("global-exception-handler", response.getBody().application());
    }
    
    @Test
    void should_CreateGenericError_When_CallingErrorResponseEntityMethod() {
        // Act
        ResponseEntity<WifiPositioningResponse> response = 
            GlobalExceptionHandler.errorResponseEntity("Test error", HttpStatus.BAD_GATEWAY);
        
        // Assert
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("ERROR", response.getBody().result());
        assertEquals("Error 502: Test error", response.getBody().message());
    }
} 