package com.wifi.positioning.algorithm.selection.factor;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GeometricQualityFactorTest {

    @Test
    @DisplayName("fromGDOP should return correct factor based on GDOP value")
    public void testFromGDOP() {
        assertEquals(GeometricQualityFactor.EXCELLENT_GDOP, GeometricQualityFactor.fromGDOP(1.5));
        assertEquals(GeometricQualityFactor.GOOD_GDOP, GeometricQualityFactor.fromGDOP(3.0));
        assertEquals(GeometricQualityFactor.FAIR_GDOP, GeometricQualityFactor.fromGDOP(5.0));
        assertEquals(GeometricQualityFactor.POOR_GDOP, GeometricQualityFactor.fromGDOP(7.0));
    }

    @Test
    @DisplayName("isCollinear should return false when there are fewer than 3 positions")
    public void testIsCollinearWithFewerThanThreePositions() {
        // Single position
        List<Position> singlePosition = Arrays.asList(
            Position.of(51.5074, -0.1278)
        );
        assertFalse(GeometricQualityFactor.isCollinear(singlePosition));
        
        // Two positions
        List<Position> twoPositions = Arrays.asList(
            Position.of(51.5074, -0.1278),
            Position.of(48.8566, 2.3522)
        );
        assertFalse(GeometricQualityFactor.isCollinear(twoPositions));
    }
    
    @Test
    @DisplayName("isCollinear should return true for positions in a horizontal line")
    public void testIsCollinearWithHorizontalLine() {
        List<Position> horizontalLine = Arrays.asList(
            Position.of(40.0, -74.0),
            Position.of(40.0, -75.0),
            Position.of(40.0, -76.0),
            Position.of(40.0, -77.0)
        );
        
        assertTrue(GeometricQualityFactor.isCollinear(horizontalLine));
    }
    
    @Test
    @DisplayName("isCollinear should return true for positions in a vertical line")
    public void testIsCollinearWithVerticalLine() {
        List<Position> verticalLine = Arrays.asList(
            Position.of(40.0, -74.0),
            Position.of(41.0, -74.0),
            Position.of(42.0, -74.0),
            Position.of(43.0, -74.0)
        );
        
        assertTrue(GeometricQualityFactor.isCollinear(verticalLine));
    }
    
    @Test
    @DisplayName("isCollinear should return true for positions in a diagonal line")
    public void testIsCollinearWithDiagonalLine() {
        // Create a more precise diagonal line
        List<Position> diagonalLine = Arrays.asList(
            Position.of(40.0, -74.0),
            Position.of(40.1, -73.9),
            Position.of(40.2, -73.8),
            Position.of(40.3, -73.7)
        );
        
        assertTrue(GeometricQualityFactor.isCollinear(diagonalLine));
    }
    
    @Test
    @DisplayName("isCollinear should return false for positions not in a line")
    public void testIsCollinearWithNonCollinearPositions() {
        List<Position> nonCollinearPositions = Arrays.asList(
            Position.of(40.0, -74.0),  // New York
            Position.of(34.0, -118.0), // Los Angeles
            Position.of(41.9, -87.6),  // Chicago
            Position.of(39.1, -94.6)   // Kansas City
        );
        
        assertFalse(GeometricQualityFactor.isCollinear(nonCollinearPositions));
    }
    
    @Test
    @DisplayName("isCollinear should return false for positions forming a triangle")
    public void testIsCollinearWithTrianglePositions() {
        List<Position> trianglePositions = Arrays.asList(
            Position.of(0.0, 0.0),
            Position.of(0.0, 1.0),
            Position.of(1.0, 0.0)
        );
        
        assertFalse(GeometricQualityFactor.isCollinear(trianglePositions));
    }
    
    @Test
    @DisplayName("isCollinear should return true for nearly collinear positions")
    public void testIsCollinearWithNearlyCollinearPositions() {
        // Small variations from a perfect line should still be detected as collinear
        List<Position> nearlyCollinearPositions = Arrays.asList(
            Position.of(40.0, -74.0),
            Position.of(40.0001, -75.0), // Tiny deviation
            Position.of(40.0002, -76.0), // Tiny deviation
            Position.of(40.0, -77.0)
        );
        
        assertTrue(GeometricQualityFactor.isCollinear(nearlyCollinearPositions));
    }
    
    @Test
    @DisplayName("checkCollinearity should detect collinear APs in scan results")
    public void testCheckCollinearityWithCollinearAPs() {
        // Create scan results
        List<WifiScanResult> scans = List.of(
            WifiScanResult.of("00:11:22:33:44:01", -70.0, 2400, "SSID1"),
            WifiScanResult.of("00:11:22:33:44:02", -72.0, 2400, "SSID2"),
            WifiScanResult.of("00:11:22:33:44:03", -74.0, 2400, "SSID3")
        );
        
        // Create AP map with collinear positions
        Map<String, WifiAccessPoint> apMap = new HashMap<>();
        apMap.put("00:11:22:33:44:01", createAP("00:11:22:33:44:01", 0.0, 0.0));
        apMap.put("00:11:22:33:44:02", createAP("00:11:22:33:44:02", 0.001, 0.0));
        apMap.put("00:11:22:33:44:03", createAP("00:11:22:33:44:03", 0.002, 0.0));
        
        // Test collinearity detection
        boolean result = GeometricQualityFactor.checkCollinearity(scans, apMap);
        assertTrue(result, "Should detect collinear APs");
    }
    
    @Test
    @DisplayName("checkCollinearity should not detect collinearity in triangular arrangement")
    public void testCheckCollinearityWithTriangularArrangement() {
        // Create scan results
        List<WifiScanResult> scans = List.of(
            WifiScanResult.of("00:11:22:33:44:01", -70.0, 2400, "SSID1"),
            WifiScanResult.of("00:11:22:33:44:02", -72.0, 2400, "SSID2"),
            WifiScanResult.of("00:11:22:33:44:03", -74.0, 2400, "SSID3")
        );
        
        // Create AP map with distinct triangular arrangement
        Map<String, WifiAccessPoint> apMap = new HashMap<>();
        apMap.put("00:11:22:33:44:01", createAP("00:11:22:33:44:01", 0.0, 0.0));
        apMap.put("00:11:22:33:44:02", createAP("00:11:22:33:44:02", 0.002, 0.0));
        apMap.put("00:11:22:33:44:03", createAP("00:11:22:33:44:03", 0.001, 0.002));
        
        // Test collinearity detection
        boolean result = GeometricQualityFactor.checkCollinearity(scans, apMap);
        assertFalse(result, "Should not detect collinearity in triangular arrangement");
    }
    
    @Test
    @DisplayName("determineGeometricQuality should return COLLINEAR for collinear APs")
    public void testDetermineGeometricQualityWithCollinearAPs() {
        // Create scan results
        List<WifiScanResult> scans = List.of(
            WifiScanResult.of("00:11:22:33:44:01", -70.0, 2400, "SSID1"),
            WifiScanResult.of("00:11:22:33:44:02", -72.0, 2400, "SSID2"),
            WifiScanResult.of("00:11:22:33:44:03", -74.0, 2400, "SSID3")
        );
        
        // Create AP map with collinear positions
        Map<String, WifiAccessPoint> apMap = new HashMap<>();
        apMap.put("00:11:22:33:44:01", createAP("00:11:22:33:44:01", 0.0, 0.0));
        apMap.put("00:11:22:33:44:02", createAP("00:11:22:33:44:02", 0.001, 0.0));
        apMap.put("00:11:22:33:44:03", createAP("00:11:22:33:44:03", 0.002, 0.0));
        
        // Test geometric quality determination
        GeometricQualityFactor factor = GeometricQualityFactor.determineGeometricQuality(scans, apMap);
        assertEquals(GeometricQualityFactor.COLLINEAR, factor, 
                "Should return COLLINEAR for collinear APs");
    }
    
    @Test
    @DisplayName("determineGeometricQuality should return EXCELLENT_GDOP for triangle arrangement")
    public void testDetermineGeometricQualityWithTriangularArrangement() {
        // Create scan results
        List<WifiScanResult> scans = List.of(
            WifiScanResult.of("00:11:22:33:44:01", -70.0, 2400, "SSID1"),
            WifiScanResult.of("00:11:22:33:44:02", -72.0, 2400, "SSID2"),
            WifiScanResult.of("00:11:22:33:44:03", -74.0, 2400, "SSID3")
        );
        
        // Create AP map with equilateral triangle arrangement
        Map<String, WifiAccessPoint> apMap = new HashMap<>();
        apMap.put("00:11:22:33:44:01", createAP("00:11:22:33:44:01", 37.7751, -122.4196));
        apMap.put("00:11:22:33:44:02", createAP("00:11:22:33:44:02", 37.7751, -122.4176)); // 200m east
        apMap.put("00:11:22:33:44:03", createAP("00:11:22:33:44:03", 37.7771, -122.4186)); // ~200m northeast
        
        // Test geometric quality determination
        GeometricQualityFactor factor = GeometricQualityFactor.determineGeometricQuality(scans, apMap);
        assertEquals(GeometricQualityFactor.EXCELLENT_GDOP, factor, 
                "Should return EXCELLENT_GDOP for triangular AP arrangement");
    }
    
    @Test
    @DisplayName("determineGeometricQuality should return POOR_GDOP for insufficient APs")
    public void testDetermineGeometricQualityWithInsufficientAPs() {
        // Create scan results with only 2 APs
        List<WifiScanResult> scans = List.of(
            WifiScanResult.of("00:11:22:33:44:01", -70.0, 2400, "SSID1"),
            WifiScanResult.of("00:11:22:33:44:02", -72.0, 2400, "SSID2")
        );
        
        // Create AP map 
        Map<String, WifiAccessPoint> apMap = new HashMap<>();
        apMap.put("00:11:22:33:44:01", createAP("00:11:22:33:44:01", 0.0, 0.0));
        apMap.put("00:11:22:33:44:02", createAP("00:11:22:33:44:02", 0.001, 0.0));
        
        // Test geometric quality determination
        GeometricQualityFactor factor = GeometricQualityFactor.determineGeometricQuality(scans, apMap);
        assertEquals(GeometricQualityFactor.POOR_GDOP, factor, 
                "Should return POOR_GDOP for insufficient APs");
    }
    
    @Test
    @DisplayName("determineGeometricQuality should handle null inputs gracefully")
    public void testDetermineGeometricQualityWithNullInputs() {
        assertEquals(GeometricQualityFactor.POOR_GDOP, 
                GeometricQualityFactor.determineGeometricQuality(null, null), 
                "Should return POOR_GDOP for null inputs");
        
        List<WifiScanResult> scans = List.of(
            WifiScanResult.of("00:11:22:33:44:01", -70.0, 2400, "SSID1"),
            WifiScanResult.of("00:11:22:33:44:02", -72.0, 2400, "SSID2"),
            WifiScanResult.of("00:11:22:33:44:03", -74.0, 2400, "SSID3")
        );
        
        assertEquals(GeometricQualityFactor.POOR_GDOP, 
                GeometricQualityFactor.determineGeometricQuality(scans, null), 
                "Should return POOR_GDOP for null AP map");
    }
    
    private WifiAccessPoint createAP(String macAddress, double latitude, double longitude) {
        return WifiAccessPoint.builder()
                .macAddress(macAddress)
                .latitude(latitude)
                .longitude(longitude)
                .confidence(0.9)
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
    }
} 