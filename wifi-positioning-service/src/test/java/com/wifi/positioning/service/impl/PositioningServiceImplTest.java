package com.wifi.positioning.service.impl;

import com.wifi.positioning.algorithm.WifiPositioningCalculator;
import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiScanResult;

import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.service.PositioningServiceImpl;
import com.wifi.positioning.service.SignalPhysicsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PositioningServiceImpl without the adapter.
 * These tests verify that the service correctly calculates positions
 * after merging with the adapter's functionality.
 * 
 * Test scenarios include:
 * - Successful position calculation
 * - Empty scan results
 * - Physics validation failures
 * - No known access points
 * - Calculation failures
 * - Batch lookup failures
 * - Invalid coordinates
 * - Status-based access point filtering
 */
@ExtendWith(MockitoExtension.class)
public class PositioningServiceImplTest {

    @Mock
    private WifiPositioningCalculator calculator;
    
    @Mock
    private WifiAccessPointRepository accessPointRepository;
    
    @Mock
    private SignalPhysicsValidator signalPhysicsValidator;
    
    @Mock
    private PositioningAlgorithm algorithm;
    
    @InjectMocks
    private PositioningServiceImpl service;
    
    private List<WifiScanResult> scanResults;
    private List<WifiAccessPoint> knownAPs;
    private Position position;
    private WifiPositioningCalculator.PositioningResult positioningResult;
    private WifiPositioningRequest request;
    
    private static final double VALID_LATITUDE = 37.7749;
    private static final double VALID_LONGITUDE = -122.4194;
    private static final double VALID_ALTITUDE = 10.0;
    private static final double VALID_ACCURACY = 25.0;
    private static final double VALID_CONFIDENCE = 0.5;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PositioningServiceImpl(calculator, accessPointRepository, signalPhysicsValidator);
        
        // Set up test data
        scanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "TestAP"),
            WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 5180, "TestAP2")
        );
        
        WifiAccessPoint ap1 = mock(WifiAccessPoint.class);
        lenient().when(ap1.getMacAddress()).thenReturn("00:11:22:33:44:55");
        lenient().when(ap1.getLatitude()).thenReturn(37.7749);
        lenient().when(ap1.getLongitude()).thenReturn(-122.4194);
        lenient().when(ap1.getAltitude()).thenReturn(10.0);
        lenient().when(ap1.getStatus()).thenReturn(WifiAccessPoint.STATUS_ACTIVE);
        
        WifiAccessPoint ap2 = mock(WifiAccessPoint.class);
        lenient().when(ap2.getMacAddress()).thenReturn("AA:BB:CC:DD:EE:FF");
        lenient().when(ap2.getLatitude()).thenReturn(37.7748);
        lenient().when(ap2.getLongitude()).thenReturn(-122.4192);
        lenient().when(ap2.getAltitude()).thenReturn(12.0);
        lenient().when(ap2.getStatus()).thenReturn(WifiAccessPoint.STATUS_ACTIVE);
        
        knownAPs = List.of(ap1, ap2);
        
        // Create a position
        position = new Position(37.7749, -122.4194, 10.0, 25.0, 0.5);
        
        // Create a positioning result
        Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
        algorithmWeights.put(algorithm, 1.0);
        
        Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
        selectionReasons.put(algorithm, List.of("Strong signal", "Good geometry"));
        
        SelectionContext context = mock(SelectionContext.class);
        
        positioningResult = new WifiPositioningCalculator.PositioningResult(
            position, algorithmWeights, selectionReasons, context
        );
        
        // Set up algorithm mock
        lenient().when(algorithm.getName()).thenReturn("Weighted Centroid");
        
        // Create request
        request = new WifiPositioningRequest(
            scanResults, 
            "test-client", 
            "test-request-1",
            "test-app",
            true
        );
        
        // Set up signals validator mock
        lenient().when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);
    }
    
    @Test
    void should_CalculatePosition_When_ValidRequest() {
        // Arrange
        Map<String, WifiAccessPoint> apMap = new HashMap<>();
        apMap.put("00:11:22:33:44:55", knownAPs.get(0));
        apMap.put("AA:BB:CC:DD:EE:FF", knownAPs.get(1));
        
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(apMap);
        when(calculator.calculatePosition(anyList(), anyList())).thenReturn(positioningResult);
        
        // Act
        WifiPositioningResponse response = service.calculatePosition(request);
        
        // Assert
        assertNotNull(response);
        assertEquals("SUCCESS", response.result());
        assertNotNull(response.wifiPosition());
        assertEquals(VALID_LATITUDE, response.wifiPosition().latitude());
        assertEquals(VALID_LONGITUDE, response.wifiPosition().longitude());
        assertEquals(VALID_ALTITUDE, response.wifiPosition().altitude());
        assertEquals(VALID_ACCURACY, response.wifiPosition().horizontalAccuracy());
        assertEquals(VALID_CONFIDENCE, response.wifiPosition().confidence());
        
        assertEquals(Integer.valueOf(scanResults.size()), response.wifiPosition().apCount());
        assertNotNull(response.wifiPosition().calculationTimeMs());
        assertNotNull(response.calculationInfo());
        
        // Verify interactions
        verify(signalPhysicsValidator).isPhysicallyPossible(scanResults);
        verify(accessPointRepository).findByMacAddresses(any());
        verify(calculator).calculatePosition(anyList(), anyList());
    }
    
    @Test
    void should_ReturnErrorResponse_When_NoScanResults() {
        // Arrange
        WifiPositioningRequest emptyRequest = new WifiPositioningRequest(
            Collections.emptyList(), 
            "test-client", 
            "test-request-id", 
            "test-app",
            false
        );
        
        // Act
        WifiPositioningResponse response = service.calculatePosition(emptyRequest);
        
        // Assert
        assertNotNull(response);
        assertEquals("ERROR", response.result());
        assertEquals("No WiFi scan results provided", response.message());
        assertEquals("test-request-id", response.requestId());
        assertEquals("test-client", response.client());
        assertEquals("test-app", response.application());
        assertNull(response.wifiPosition());
    }
    
    @Test
    void should_FilterAPsByStatus_When_MixedStatusAPs() {
        // Arrange
        // Create APs with different statuses
        WifiAccessPoint activeAP = mock(WifiAccessPoint.class);
        when(activeAP.getMacAddress()).thenReturn("00:11:22:33:44:55");
        when(activeAP.getStatus()).thenReturn(WifiAccessPoint.STATUS_ACTIVE);
        
        WifiAccessPoint warningAP = mock(WifiAccessPoint.class);
        when(warningAP.getMacAddress()).thenReturn("AA:BB:CC:DD:EE:FF");
        when(warningAP.getStatus()).thenReturn(WifiAccessPoint.STATUS_WARNING);
        
        WifiAccessPoint errorAP = mock(WifiAccessPoint.class);
        when(errorAP.getMacAddress()).thenReturn("11:22:33:44:55:66");
        when(errorAP.getStatus()).thenReturn(WifiAccessPoint.STATUS_ERROR);
        
        WifiAccessPoint expiredAP = mock(WifiAccessPoint.class);
        when(expiredAP.getMacAddress()).thenReturn("22:33:44:55:66:77");
        when(expiredAP.getStatus()).thenReturn(WifiAccessPoint.STATUS_EXPIRED);
        
        WifiAccessPoint hotspotAP = mock(WifiAccessPoint.class);
        when(hotspotAP.getMacAddress()).thenReturn("33:44:55:66:77:88");
        when(hotspotAP.getStatus()).thenReturn(WifiAccessPoint.STATUS_WIFI_HOTSPOT);
        
        // Add scan results for all APs
        List<WifiScanResult> mixedScanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "ActiveAP"),
            WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 5180, "WarningAP"),
            WifiScanResult.of("11:22:33:44:55:66", -75.0, 2412, "ErrorAP"),
            WifiScanResult.of("22:33:44:55:66:77", -80.0, 5240, "ExpiredAP"),
            WifiScanResult.of("33:44:55:66:77:88", -85.0, 2437, "HotspotAP")
        );
        
        // Create request with mixed scan results
        WifiPositioningRequest mixedRequest = new WifiPositioningRequest(
            mixedScanResults,
            "test-client",
            "test-request-mixed",
            "test-app",
            true
        );
        
        // Set up repository mock to return all APs
        Map<String, WifiAccessPoint> mixedAPMap = new HashMap<>();
        mixedAPMap.put(activeAP.getMacAddress(), activeAP);
        mixedAPMap.put(warningAP.getMacAddress(), warningAP);
        mixedAPMap.put(errorAP.getMacAddress(), errorAP);
        mixedAPMap.put(expiredAP.getMacAddress(), expiredAP);
        mixedAPMap.put(hotspotAP.getMacAddress(), hotspotAP);
        
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(mixedAPMap);
        when(calculator.calculatePosition(anyList(), anyList())).thenReturn(positioningResult);
        
        // Act
        service.calculatePosition(mixedRequest);
        
        // Assert - Use ArgumentCaptor to verify that only active and warning APs are passed to calculator
        ArgumentCaptor<List<WifiAccessPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(calculator).calculatePosition(anyList(), captor.capture());
        
        List<WifiAccessPoint> filteredAPs = captor.getValue();
        assertEquals(2, filteredAPs.size(), "Only active and warning APs should be used");
        
        List<String> statuses = filteredAPs.stream()
            .map(WifiAccessPoint::getStatus)
            .collect(Collectors.toList());
        assertTrue(statuses.contains(WifiAccessPoint.STATUS_ACTIVE), "Should include active APs");
        assertTrue(statuses.contains(WifiAccessPoint.STATUS_WARNING), "Should include warning APs");
        assertFalse(statuses.contains(WifiAccessPoint.STATUS_ERROR), "Should not include error APs");
        assertFalse(statuses.contains(WifiAccessPoint.STATUS_EXPIRED), "Should not include expired APs");
        assertFalse(statuses.contains(WifiAccessPoint.STATUS_WIFI_HOTSPOT), "Should not include hotspot APs");
    }
    
    @Test
    void should_IncludeAPInfo_In_CalculationInfo() {
        // Arrange
        // Create APs with different statuses
        WifiAccessPoint activeAP = mock(WifiAccessPoint.class);
        when(activeAP.getMacAddress()).thenReturn("00:11:22:33:44:55");
        when(activeAP.getStatus()).thenReturn(WifiAccessPoint.STATUS_ACTIVE);
        
        WifiAccessPoint errorAP = mock(WifiAccessPoint.class);
        when(errorAP.getMacAddress()).thenReturn("11:22:33:44:55:66");
        when(errorAP.getStatus()).thenReturn(WifiAccessPoint.STATUS_ERROR);
        
        // Add scan results for all APs
        List<WifiScanResult> mixedScanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "ActiveAP"),
            WifiScanResult.of("11:22:33:44:55:66", -75.0, 2412, "ErrorAP")
        );
        
        // Create request with mixed scan results
        WifiPositioningRequest mixedRequest = new WifiPositioningRequest(
            mixedScanResults,
            "test-client",
            "test-request-mixed",
            "test-app",
            true  // Set calculationDetail to true
        );
        
        // Set up repository mock to return all APs
        Map<String, WifiAccessPoint> mixedAPMap = new HashMap<>();
        mixedAPMap.put(activeAP.getMacAddress(), activeAP);
        mixedAPMap.put(errorAP.getMacAddress(), errorAP);
        
        when(accessPointRepository.findByMacAddresses(any())).thenReturn(mixedAPMap);
        when(calculator.calculatePosition(anyList(), anyList())).thenReturn(positioningResult);
        when(positioningResult.getCalculationInfo()).thenReturn("Algorithm calculation info here");
        
        // Act
        WifiPositioningResponse response = service.calculatePosition(mixedRequest);
        
        // Assert
        assertNotNull(response.calculationInfo());
        // Check for AP Filtering section
        assertTrue(response.calculationInfo().contains("AP Filtering:"));
        // Check for active AP info
        assertTrue(response.calculationInfo().contains("active: 1 APs, used in calculation"));
        // Check for error AP info
        assertTrue(response.calculationInfo().contains("error: 1 APs, filtered out"));
        // Check for AP list
        assertTrue(response.calculationInfo().contains("AP List:"));
        // Check for algorithm calculation info
        assertTrue(response.calculationInfo().contains("Algorithm calculation info here"));
    }

    @Test
    void should_ReturnErrorResponse_When_SignalPhysicsInvalid() {
        // Arrange
        // Create request with physically impossible signal strengths
        List<WifiScanResult> impossibleScanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -40.0, 2437, "ImpossibleAP_1"), // Very strong signal
            WifiScanResult.of("AA:BB:CC:DD:EE:FF", -45.0, 5180, "ImpossibleAP_2")  // Also strong signal from same location
        );
        
        WifiPositioningRequest impossibleRequest = new WifiPositioningRequest(
            impossibleScanResults,
            "test-client",
            "test-request-impossible",
            "test-app",
            true
        );
        
        // Mock the validator to return false for physically impossible signals
        when(signalPhysicsValidator.isPhysicallyPossible(impossibleScanResults)).thenReturn(false);
        
        // Act
        WifiPositioningResponse response = service.calculatePosition(impossibleRequest);
        
        // Assert
        assertNotNull(response);
        assertEquals("ERROR", response.result());
        assertEquals("Physically impossible signal strength relationships", response.message());
        assertNull(response.wifiPosition());
        
        // Verify the validator was called but repository and calculator were not
        verify(signalPhysicsValidator).isPhysicallyPossible(impossibleScanResults);
        verify(accessPointRepository, never()).findByMacAddresses(any());
        verify(calculator, never()).calculatePosition(any(), any());
    }
} 