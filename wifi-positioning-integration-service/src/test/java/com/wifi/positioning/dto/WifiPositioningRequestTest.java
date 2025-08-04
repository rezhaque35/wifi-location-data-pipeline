package com.wifi.positioning.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WifiPositioningRequestTest {

    private Validator validator;
    private WifiScanResult validScanResult;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        validScanResult = new WifiScanResult(
                "00:11:22:33:44:55", 
                -65.0, 
                2437, 
                "test-ssid", 
                54, 
                40);
    }

    @Test
    void validRequest() {
        // Given
        WifiPositioningRequest request = new WifiPositioningRequest(
                List.of(validScanResult),
                "mobile-app",
                UUID.randomUUID().toString(),
                "my-test-app",
                false);
        
        // When
        Set<ConstraintViolation<WifiPositioningRequest>> violations = validator.validate(request);
        
        // Then
        assertTrue(violations.isEmpty());
    }
    
    @Test
    void requestWithEmptyScanResults() {
        // Given
        WifiPositioningRequest request = new WifiPositioningRequest(
                List.of(),
                "mobile-app",
                UUID.randomUUID().toString(),
                "my-test-app",
                false);
        
        // When
        Set<ConstraintViolation<WifiPositioningRequest>> violations = validator.validate(request);
        
        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("wifiScanResults")));
    }
    
    @Test
    void requestWithNullClient() {
        // Given
        WifiPositioningRequest request = new WifiPositioningRequest(
                List.of(validScanResult),
                null,
                UUID.randomUUID().toString(),
                "my-test-app",
                false);
        
        // When
        Set<ConstraintViolation<WifiPositioningRequest>> violations = validator.validate(request);
        
        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("client")));
    }
    
    @Test
    void requestWithLongRequestId() {
        // Given
        String longRequestId = "a".repeat(65);
        WifiPositioningRequest request = new WifiPositioningRequest(
                List.of(validScanResult),
                "mobile-app",
                longRequestId,
                "my-test-app",
                false);
        
        // When
        Set<ConstraintViolation<WifiPositioningRequest>> violations = validator.validate(request);
        
        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("requestId")));
    }
    
    @Test
    void defaultValues() {
        // Given
        String requestId = UUID.randomUUID().toString();
        WifiPositioningRequest request = new WifiPositioningRequest(
                List.of(validScanResult),
                "mobile-app",
                requestId,
                null,
                null);
        
        // Then
        assertEquals("mobile-app", request.client());
        assertEquals(requestId, request.requestId());
        assertNull(request.application());
        assertFalse(request.calculationDetail());
    }

    @Test
    void calculationDetailDefault() {
        // Given
        WifiPositioningRequest request = new WifiPositioningRequest(
                List.of(validScanResult),
                "mobile-app",
                UUID.randomUUID().toString(),
                "my-test-app",
                null);
        
        // Then
        assertFalse(request.calculationDetail());
    }
} 