package com.wifi.positioning.service;

import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningResponse.WifiPosition;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test implementation of PositioningService that returns predictable 
 * responses for integration tests.
 */
@Service
@Profile("test")
public class TestPositioningService implements PositioningService {

    // Default test coordinates
    private static final double DEFAULT_LATITUDE = 37.7749;
    private static final double DEFAULT_LONGITUDE = -122.4194;
    private static final double DEFAULT_ALTITUDE = 15.0;
    private static final double DEFAULT_HORIZONTAL_ACCURACY = 20.0;
    private static final double DEFAULT_CONFIDENCE = 0.75;
    
    private static final Map<String, WifiPositioningResponse> MAC_TO_RESPONSE = new HashMap<>();
    
    private WifiPositioningResponse response;
    private boolean shouldThrowException;
    private RuntimeException exceptionToThrow;

    /**
     * Set the response that should be returned by this service.
     */
    public void setResponse(WifiPositioningResponse response) {
        this.response = response;
    }

    /**
     * Configure the service to throw an exception when called.
     */
    public void setShouldThrowException(boolean shouldThrowException, RuntimeException exception) {
        this.shouldThrowException = shouldThrowException;
        this.exceptionToThrow = exception;
    }

    @Override
    public WifiPositioningResponse calculatePosition(WifiPositioningRequest request) {
        if (shouldThrowException) {
            throw exceptionToThrow;
        }
        
        if (response != null) {
            return response;
        }
        
        // Create a default response based on the first AP in the request
        WifiPosition wifiPosition = new WifiPosition(
            DEFAULT_LATITUDE,
            DEFAULT_LONGITUDE,
            DEFAULT_ALTITUDE,
            DEFAULT_HORIZONTAL_ACCURACY,
            0.0,
            DEFAULT_CONFIDENCE,
            Arrays.asList("proximity", "weighted_centroid"),
            request.wifiScanResults().size(),
            50L
        );
        
        return WifiPositioningResponse.success(
            request,
            wifiPosition,
            "Test positioning calculation"
        );
    }
    
    private boolean containsMacAddress(List<WifiScanResult> scanResults, String macToFind) {
        return scanResults.stream()
            .anyMatch(result -> result.macAddress().equalsIgnoreCase(macToFind));
    }
} 