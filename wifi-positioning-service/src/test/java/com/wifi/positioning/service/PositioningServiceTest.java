// src/test/java/com/wifi/positioning/service/PositioningServiceTest.java
package com.wifi.positioning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.WifiPositioningCalculator;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.dto.*;
import com.wifi.positioning.repository.CellTowerRepository;
import com.wifi.positioning.repository.WifiAccessPointRepository;

/**
 * Focused unit tests for PositioningService.
 * 
 * These tests complement the integration tests by focusing on:
 * - Complex error scenarios
 * - Async exception handling
 * - Edge cases in internal logic
 * - Precise verification of service behavior with mocked dependencies
 * 
 * For end-to-end flows, see WifiPositioningIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PositioningService Unit Tests")
class PositioningServiceTest {

    @Mock
    private WifiPositioningCalculator calculator;

    @Mock
    private WifiAccessPointRepository accessPointRepository;

    @Mock
    private CellTowerRepository cellTowerRepository;

    @Mock
    private PositioningAlgorithm mockAlgorithm;

    private PositioningService service;

    @BeforeEach
    void setUp() {
        service = new PositioningService(calculator, accessPointRepository, cellTowerRepository);
        
        // Default mock behavior for cell tower repository (lenient as not all tests use it)
        lenient().when(cellTowerRepository.findBestCellAsync(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        
        // Default mock behavior for algorithm (lenient as not all tests use it)
        lenient().when(mockAlgorithm.getName()).thenReturn("TestAlgorithm");
    }

    // ======================================================================================
    // TEST 1: Successful Position Calculation
    // ======================================================================================

    @Test
    @DisplayName("Test 1: Should calculate position successfully with valid data")
    void testSuccessfulPositionCalculation() {
        // Arrange
        List<WifiScanResult> scanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "TestAP")
        );
        WifiPositioningRequest request = new WifiPositioningRequest(
            scanResults, "test-client", "req-1", "test-app", null, false
        );

        WifiAccessPoint ap = createMockAP("00:11:22:33:44:55", 37.7749, -122.4194, "active");
        Map<String, WifiAccessPoint> apMap = Map.of(ap.getMacAddress(), ap);

        when(accessPointRepository.findByMacAddressesAsync(any()))
            .thenReturn(CompletableFuture.completedFuture(apMap));

        Position position = new Position(37.7749, -122.4194, 10.0, 15.0, 0.75);
        WifiPositioningCalculator.PositioningResult positioningResult = createPositioningResult(position);
        when(calculator.calculatePosition(any())).thenReturn(positioningResult);

        // Act
        WifiPositioningResponse response = service.calculatePosition(request).join();

        // Assert
        assertNotNull(response);
        assertEquals("SUCCESS", response.result());
        assertNotNull(response.wifiPosition());
        assertEquals(37.7749, response.wifiPosition().latitude());
        assertEquals(-122.4194, response.wifiPosition().longitude());
        verify(accessPointRepository).findByMacAddressesAsync(any());
        verify(calculator).calculatePosition(any());
    }

    // ======================================================================================
    // TEST 2: Empty Scan Results Validation
    // ======================================================================================

    @Test
    @DisplayName("Test 2: Should return error for empty scan results")
    void testEmptyScanResultsValidation() {
        // Arrange
        WifiPositioningRequest request = new WifiPositioningRequest(
            Collections.emptyList(), "test-client", "req-2", "test-app", null, false
        );

        // Act
        WifiPositioningResponse response = service.calculatePosition(request).join();

        // Assert
        assertEquals("ERROR", response.result());
        assertEquals("No WiFi scan results provided", response.message());
        assertNull(response.wifiPosition());
        
        // Verify no repository or calculator calls were made
        verify(accessPointRepository, never()).findByMacAddressesAsync(any());
        verify(calculator, never()).calculatePosition(any());
    }

    // ======================================================================================
    // TEST 3: Unknown APs Error Handling
    // ======================================================================================

    @Test
    @DisplayName("Test 3: Should return error when no known APs found")
    void testUnknownAPsErrorHandling() {
        // Arrange
        List<WifiScanResult> scanResults = List.of(
            WifiScanResult.of("FF:FF:FF:FF:FF:FF", -65.0, 2437, "UnknownAP")
        );
        WifiPositioningRequest request = new WifiPositioningRequest(
            scanResults, "test-client", "req-3", "test-app", null, false
        );

        // Repository returns empty map (no known APs)
        when(accessPointRepository.findByMacAddressesAsync(any()))
            .thenReturn(CompletableFuture.completedFuture(Collections.emptyMap()));

        // Act
        WifiPositioningResponse response = service.calculatePosition(request).join();

        // Assert
        assertEquals("ERROR", response.result());
        assertTrue(response.message().contains("No known access points found"));
        assertNull(response.wifiPosition());
        verify(accessPointRepository).findByMacAddressesAsync(any());
        verify(calculator, never()).calculatePosition(any());
    }

    // ======================================================================================
    // TEST 4: Status Filtering Logic
    // ======================================================================================

    @Test
    @DisplayName("Test 4: Should filter out APs with invalid status")
    void testStatusFilteringLogic() {
        // Arrange
        List<WifiScanResult> scanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "ActiveAP"),
            WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 5180, "ErrorAP"),
            WifiScanResult.of("11:22:33:44:55:66", -75.0, 2412, "WarningAP")
        );
        WifiPositioningRequest request = new WifiPositioningRequest(
            scanResults, "test-client", "req-4", "test-app", null, false
        );

        // Create APs with different statuses
        WifiAccessPoint activeAP = createMockAP("00:11:22:33:44:55", 37.7749, -122.4194, "active");
        WifiAccessPoint errorAP = createMockAP("AA:BB:CC:DD:EE:FF", 37.7750, -122.4195, "error");
        WifiAccessPoint warningAP = createMockAP("11:22:33:44:55:66", 37.7751, -122.4196, "warning");

        Map<String, WifiAccessPoint> apMap = Map.of(
            activeAP.getMacAddress(), activeAP,
            errorAP.getMacAddress(), errorAP,
            warningAP.getMacAddress(), warningAP
        );

        when(accessPointRepository.findByMacAddressesAsync(any()))
            .thenReturn(CompletableFuture.completedFuture(apMap));

        Position position = new Position(37.7749, -122.4194, 10.0, 15.0, 0.75);
        WifiPositioningCalculator.PositioningResult positioningResult = createPositioningResult(position);
        when(calculator.calculatePosition(any())).thenReturn(positioningResult);

        // Act
        service.calculatePosition(request).join();

        // Assert - Capture what was passed to calculator
        ArgumentCaptor<WifiAccessPoints> captor = ArgumentCaptor.forClass(WifiAccessPoints.class);
        verify(calculator).calculatePosition(captor.capture());

        WifiAccessPoints capturedData = captor.getValue();
        List<WifiAPWithScan> validAPs = capturedData.getValidAccessPoints();

        // Only active and warning APs should be used
        assertEquals(2, validAPs.size(), "Only active and warning APs should pass filtering");
        
        List<String> statuses = validAPs.stream()
            .map(ap -> ap.wifiAccessPoint().getStatus())
            .toList();
        assertTrue(statuses.contains("active"));
        assertTrue(statuses.contains("warning"));
        assertFalse(statuses.contains("error"), "Error status APs should be filtered out");
    }

    // ======================================================================================
    // TEST 5: Async Exception Handling
    // ======================================================================================

    @Test
    @DisplayName("Test 5: Should handle async repository exceptions gracefully")
    void testAsyncExceptionHandling() {
        // Arrange
        List<WifiScanResult> scanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "TestAP")
        );
        WifiPositioningRequest request = new WifiPositioningRequest(
            scanResults, "test-client", "req-5", "test-app", null, false
        );

        // Repository throws exception
        CompletableFuture<Map<String, WifiAccessPoint>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Database connection failed"));
        when(accessPointRepository.findByMacAddressesAsync(any())).thenReturn(failedFuture);

        // Act
        WifiPositioningResponse response = service.calculatePosition(request).join();

        // Assert
        assertEquals("ERROR", response.result());
        assertTrue(response.message().contains("Database connection failed") || 
                   response.message().contains("unexpected error"),
                   "Error message should indicate the exception");
        assertNull(response.wifiPosition());
    }

    // ======================================================================================
    // TEST 6: Calculation Info Building
    // ======================================================================================

    @Test
    @DisplayName("Test 6: Should include calculation info when requested")
    void testCalculationInfoBuilding() {
        // Arrange
        List<WifiScanResult> scanResults = List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "TestAP"),
            WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 5180, "TestAP2")
        );
        WifiPositioningRequest request = new WifiPositioningRequest(
            scanResults, "test-client", "req-6", "test-app", null, true // calculationDetail = true
        );

        WifiAccessPoint ap1 = createMockAP("00:11:22:33:44:55", 37.7749, -122.4194, "active");
        WifiAccessPoint ap2 = createMockAP("AA:BB:CC:DD:EE:FF", 37.7750, -122.4195, "active");
        Map<String, WifiAccessPoint> apMap = Map.of(
            ap1.getMacAddress(), ap1,
            ap2.getMacAddress(), ap2
        );

        when(accessPointRepository.findByMacAddressesAsync(any()))
            .thenReturn(CompletableFuture.completedFuture(apMap));

        Position position = new Position(37.7749, -122.4194, 10.0, 15.0, 0.75);
        WifiPositioningCalculator.PositioningResult positioningResult = createPositioningResult(position);
        when(calculator.calculatePosition(any())).thenReturn(positioningResult);

        // Act
        WifiPositioningResponse response = service.calculatePosition(request).join();

        // Assert
        assertEquals("SUCCESS", response.result());
        assertNotNull(response.calculationInfo(), "CalculationInfo should be included when requested");
        assertNotNull(response.calculationInfo().accessPoints());
        assertNotNull(response.calculationInfo().accessPointSummary());
        assertNotNull(response.calculationInfo().algorithmSelection());
        
        // Verify summary data
        assertTrue(response.calculationInfo().accessPointSummary().total() > 0);
        assertTrue(response.calculationInfo().accessPointSummary().used() > 0);
    }

    // ======================================================================================
    // Helper Methods
    // ======================================================================================

    private WifiAccessPoint createMockAP(String mac, double lat, double lon, String status) {
        WifiAccessPoint ap = mock(WifiAccessPoint.class);
        lenient().when(ap.getMacAddress()).thenReturn(mac);
        lenient().when(ap.getLatitude()).thenReturn(lat);
        lenient().when(ap.getLongitude()).thenReturn(lon);
        lenient().when(ap.getAltitude()).thenReturn(10.0);
        lenient().when(ap.getStatus()).thenReturn(status);
        lenient().when(ap.getHorizontalAccuracy()).thenReturn(10.0);
        lenient().when(ap.getVerticalAccuracy()).thenReturn(5.0);
        lenient().when(ap.getConfidence()).thenReturn(0.85);
        return ap;
    }

    private WifiPositioningCalculator.PositioningResult createPositioningResult(Position position) {
        Map<PositioningAlgorithm, Double> algorithmWeights = Map.of(mockAlgorithm, 1.0);
        Map<PositioningAlgorithm, List<String>> selectionReasons = Map.of(
            mockAlgorithm, List.of("Test reason")
        );
        SelectionContext context = mock(SelectionContext.class);
        
        return new WifiPositioningCalculator.PositioningResult(
            position, algorithmWeights, selectionReasons, context
        );
    }
}
