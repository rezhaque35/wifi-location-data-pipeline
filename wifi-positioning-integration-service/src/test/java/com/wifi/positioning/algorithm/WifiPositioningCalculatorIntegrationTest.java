package com.wifi.positioning.algorithm;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WifiPositioningCalculator.
 * Tests that the calculator works correctly with real implementation of dependencies.
 */
@SpringBootTest
@ActiveProfiles("test")
class WifiPositioningCalculatorIntegrationTest {

    @Autowired
    private WifiPositioningCalculator calculator;
    
    @Test
    void should_CalculatePosition_When_GivenValidInput() {
        // Create test access points
        WifiAccessPoint ap1 = WifiAccessPoint.builder()
            .macAddress("00:11:22:33:44:55")
            .latitude(37.7749)
            .longitude(-122.4194)
            .altitude(10.0)
            .horizontalAccuracy(5.0)
            .verticalAccuracy(2.0)
            .confidence(0.85)
            .status(WifiAccessPoint.STATUS_ACTIVE)
            .build();
            
        WifiAccessPoint ap2 = WifiAccessPoint.builder()
            .macAddress("66:77:88:99:AA:BB")
            .latitude(37.7751)
            .longitude(-122.4196)
            .altitude(12.0)
            .horizontalAccuracy(6.0)
            .verticalAccuracy(3.0)
            .confidence(0.82)
            .status(WifiAccessPoint.STATUS_ACTIVE)
            .build();
            
        // Create test scan results
        List<WifiScanResult> scanResults = Arrays.asList(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "Test"),
            WifiScanResult.of("66:77:88:99:AA:BB", -70.0, 5180, "Test")
        );
        
        List<WifiAccessPoint> accessPoints = Arrays.asList(ap1, ap2);
        
        // Calculate position
        WifiPositioningCalculator.PositioningResult result = calculator.calculatePosition(scanResults, accessPoints);
        
        // Check results
        assertNotNull(result);
        assertNotNull(result.position());
        assertTrue(result.position().isValid());
        
        // Position should be between the two APs or nearby
        double avgLat = (ap1.getLatitude() + ap2.getLatitude()) / 2;
        double avgLon = (ap1.getLongitude() + ap2.getLongitude()) / 2;
        
        // Allow for some variance but it should be close to the average
        assertEquals(avgLat, result.position().latitude(), 0.01);
        assertEquals(avgLon, result.position().longitude(), 0.01);
        
        // Verify algorithm weights and reasons
        assertNotNull(result.algorithmWeights());
        assertFalse(result.algorithmWeights().isEmpty());
    }
    
    @Test
    void should_ReturnNull_When_NoAccessPointsProvided() {
        List<WifiScanResult> scanResults = Arrays.asList(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "Test")
        );
        
        WifiPositioningCalculator.PositioningResult result = calculator.calculatePosition(scanResults, List.of());
        
        assertNull(result);
    }
    
    @Test
    void should_ReturnNull_When_NoScanResultsProvided() {
        WifiAccessPoint ap = WifiAccessPoint.builder()
            .macAddress("00:11:22:33:44:55")
            .latitude(37.7749)
            .longitude(-122.4194)
            .altitude(10.0)
            .build();
            
        WifiPositioningCalculator.PositioningResult result = calculator.calculatePosition(List.of(), List.of(ap));
        
        assertNull(result);
    }
} 