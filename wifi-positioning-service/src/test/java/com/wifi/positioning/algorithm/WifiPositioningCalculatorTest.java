// src/test/java/com/wifi/positioning/algorithm/WifiPositioningCalculatorTest.java
package com.wifi.positioning.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.positioning.algorithm.impl.*;
import com.wifi.positioning.algorithm.selection.AlgorithmSelector;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.algorithm.selection.SelectionContextBuilder;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiAccessPoints;
import com.wifi.positioning.dto.WifiAPWithScan;
import com.wifi.positioning.dto.WifiScanResult;

/**
 * Focused unit tests for WifiPositioningCalculator.
 * 
 * Tests core calculator logic in isolation:
 * - Algorithm selection for different AP scenarios
 * - Collinear geometry handling
 * - Algorithm failure/timeout graceful degradation
 * - Error handling when all algorithms fail
 * 
 * For end-to-end calculator behavior, see WifiPositioningIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WifiPositioningCalculator Unit Tests")
class WifiPositioningCalculatorTest {

    @Mock private ProximityDetectionAlgorithm proximityAlgorithm;
    @Mock private RSSIRatioAlgorithm rssiRatioAlgorithm;
    @Mock private WeightedCentroidAlgorithm weightedCentroidAlgorithm;
    @Mock private LogDistancePathLossAlgorithm logDistanceAlgorithm;
    @Mock private MaximumLikelihoodAlgorithm maximumLikelihoodAlgorithm;
    @Mock private TrilaterationAlgorithm trilaterationAlgorithm;
    @Mock private AlgorithmSelector algorithmSelector;
    @Mock private SelectionContextBuilder contextBuilder;
    @Mock private PositionCombiner positionCombiner;

    @InjectMocks
    private WifiPositioningCalculator calculator;

    @BeforeEach
    void setUp() {
        // Set up algorithm names
        lenient().when(proximityAlgorithm.getName()).thenReturn("proximity");
        lenient().when(rssiRatioAlgorithm.getName()).thenReturn("rssi_ratio");
        lenient().when(weightedCentroidAlgorithm.getName()).thenReturn("weighted_centroid");
        lenient().when(logDistanceAlgorithm.getName()).thenReturn("log_distance");
        lenient().when(maximumLikelihoodAlgorithm.getName()).thenReturn("maximum_likelihood");
        lenient().when(trilaterationAlgorithm.getName()).thenReturn("trilateration");

        // Set up base confidence values
        lenient().when(proximityAlgorithm.getConfidence()).thenReturn(0.65);
        lenient().when(rssiRatioAlgorithm.getConfidence()).thenReturn(0.75);
        lenient().when(weightedCentroidAlgorithm.getConfidence()).thenReturn(0.80);
        lenient().when(logDistanceAlgorithm.getConfidence()).thenReturn(0.85);
        lenient().when(maximumLikelihoodAlgorithm.getConfidence()).thenReturn(0.90);
        lenient().when(trilaterationAlgorithm.getConfidence()).thenReturn(0.85);

        // Default context builder behavior
        lenient().when(contextBuilder.buildContext(any(), any()))
            .thenReturn(SelectionContext.builder().build());

        // Default position combiner - returns highest weighted position
        lenient().when(positionCombiner.combinePositions(any())).thenAnswer(invocation -> {
            List<PositionCombiner.WeightedPosition> positions = invocation.getArgument(0);
            if (positions.isEmpty()) return null;
            return positions.stream()
                .max(Comparator.comparingDouble(PositionCombiner.WeightedPosition::weight))
                .map(PositionCombiner.WeightedPosition::position)
                .orElse(null);
        });
    }

    // ======================================================================================
    // TEST 1: Single AP - Proximity Algorithm Selection
    // ======================================================================================

    @Test
    @DisplayName("Test 1: Single AP - Should Use Proximity Detection")
    void testSingleAPProximitySelection() {
        // Arrange
        WifiAccessPoints wifiAccessPoints = createWifiAccessPoints(
            List.of(WifiScanResult.of("00:11:22:33:44:01", -65.0, 2437, "test")),
            List.of(createAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.5))
        );

        Position expectedPosition = new Position(37.7749, -122.4194, 10.5, 50.0, 0.65);
        Map<PositioningAlgorithm, Double> selectedAlgorithms = Map.of(proximityAlgorithm, 1.0);
        Map<PositioningAlgorithm, List<String>> selectionReasons = Map.of(
            proximityAlgorithm, List.of("Single AP - proximity detection required")
        );

        when(algorithmSelector.selectAlgorithmsWithReasons(any()))
            .thenReturn(new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));
        when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

        // Act
        WifiPositioningCalculator.PositioningResult result = calculator.calculatePosition(wifiAccessPoints);

        // Assert
        assertNotNull(result);
        assertNotNull(result.position());
        assertEquals(expectedPosition.latitude(), result.position().latitude(), 0.0001);
        assertEquals(expectedPosition.longitude(), result.position().longitude(), 0.0001);
        assertTrue(result.algorithmWeights().containsKey(proximityAlgorithm));
        verify(proximityAlgorithm).calculatePosition(any(), any());
    }

    // ======================================================================================
    // TEST 2: Multiple APs - Advanced Algorithm Selection
    // ======================================================================================

    @Test
    @DisplayName("Test 2: Multiple APs - Should Use Advanced Algorithms")
    void testMultipleAPsAdvancedSelection() {
        // Arrange
        WifiAccessPoints wifiAccessPoints = createWifiAccessPoints(
            List.of(
                WifiScanResult.of("00:11:22:33:44:01", -65.0, 2437, "test"),
                WifiScanResult.of("00:11:22:33:44:02", -68.0, 5180, "test"),
                WifiScanResult.of("00:11:22:33:44:03", -70.0, 2462, "test"),
                WifiScanResult.of("00:11:22:33:44:04", -72.0, 5240, "test")
            ),
            List.of(
                createAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.0),
                createAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.0),
                createAP("00:11:22:33:44:03", 37.7751, -122.4196, 15.0),
                createAP("00:11:22:33:44:04", 37.7752, -122.4197, 18.0)
            )
        );

        Position mlPosition = new Position(37.7750, -122.4195, 12.0, 10.0, 0.88);
        Position trilatPosition = new Position(37.7751, -122.4196, 13.0, 12.0, 0.85);

        Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
        selectedAlgorithms.put(maximumLikelihoodAlgorithm, 1.0);
        selectedAlgorithms.put(trilaterationAlgorithm, 0.9);

        Map<PositioningAlgorithm, List<String>> selectionReasons = Map.of(
            maximumLikelihoodAlgorithm, List.of("Multiple APs with good signal quality"),
            trilaterationAlgorithm, List.of("Good geometric distribution")
        );

        when(algorithmSelector.selectAlgorithmsWithReasons(any()))
            .thenReturn(new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));
        when(maximumLikelihoodAlgorithm.calculatePosition(any(), any())).thenReturn(mlPosition);
        when(trilaterationAlgorithm.calculatePosition(any(), any())).thenReturn(trilatPosition);

        // Act
        WifiPositioningCalculator.PositioningResult result = calculator.calculatePosition(wifiAccessPoints);

        // Assert
        assertNotNull(result);
        assertNotNull(result.position());
        assertTrue(result.algorithmWeights().size() >= 1, "Should have at least one algorithm selected");
        assertTrue(result.algorithmWeights().containsKey(maximumLikelihoodAlgorithm) ||
                   result.algorithmWeights().containsKey(trilaterationAlgorithm));
        verify(algorithmSelector).selectAlgorithmsWithReasons(any());
    }

    // ======================================================================================
    // TEST 3: Collinear Geometry - Proper Handling
    // ======================================================================================

    @Test
    @DisplayName("Test 3: Collinear APs - Should Handle Poor Geometry Gracefully")
    void testCollinearGeometryHandling() {
        // Arrange - APs in a straight line (poor geometry for trilateration)
        WifiAccessPoints wifiAccessPoints = createWifiAccessPoints(
            List.of(
                WifiScanResult.of("00:11:22:33:44:01", -70.0, 2437, "test"),
                WifiScanResult.of("00:11:22:33:44:02", -72.0, 2437, "test"),
                WifiScanResult.of("00:11:22:33:44:03", -74.0, 2437, "test")
            ),
            List.of(
                createAP("00:11:22:33:44:01", 37.7750, -122.4194, 10.0), // Collinear
                createAP("00:11:22:33:44:02", 37.7755, -122.4194, 12.0), // Same longitude
                createAP("00:11:22:33:44:03", 37.7760, -122.4194, 15.0)  // Same longitude
            )
        );

        Position centroidPosition = new Position(37.7755, -122.4194, 12.0, 35.0, 0.65);

        // Collinear geometry should favor weighted centroid over trilateration
        Map<PositioningAlgorithm, Double> selectedAlgorithms = Map.of(
            weightedCentroidAlgorithm, 1.0,
            logDistanceAlgorithm, 0.7  // Lower weight due to poor geometry
        );

        Map<PositioningAlgorithm, List<String>> selectionReasons = Map.of(
            weightedCentroidAlgorithm, List.of("Collinear geometry - centroid approach safer"),
            logDistanceAlgorithm, List.of("Secondary algorithm for collinear points")
        );

        when(algorithmSelector.selectAlgorithmsWithReasons(any()))
            .thenReturn(new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));
        when(weightedCentroidAlgorithm.calculatePosition(any(), any())).thenReturn(centroidPosition);

        // Act
        WifiPositioningCalculator.PositioningResult result = calculator.calculatePosition(wifiAccessPoints);

        // Assert
        assertNotNull(result);
        assertNotNull(result.position());
        assertEquals(centroidPosition.latitude(), result.position().latitude(), 0.0001);
        // Should use centroid algorithm for collinear geometry
        assertTrue(result.algorithmWeights().containsKey(weightedCentroidAlgorithm));
        verify(weightedCentroidAlgorithm).calculatePosition(any(), any());
    }

    // ======================================================================================
    // TEST 4: Algorithm Timeout - Graceful Degradation
    // ======================================================================================

    @Test
    @DisplayName("Test 4: Algorithm Timeout - Should Use Successful Algorithms")
    void testAlgorithmTimeoutGracefulDegradation() {
        // Arrange
        WifiAccessPoints wifiAccessPoints = createWifiAccessPoints(
            List.of(
                WifiScanResult.of("00:11:22:33:44:01", -65.0, 2437, "test"),
                WifiScanResult.of("00:11:22:33:44:02", -68.0, 5180, "test")
            ),
            List.of(
                createAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.0),
                createAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.0)
            )
        );

        Position validPosition = new Position(37.7749, -122.4194, 10.0, 20.0, 0.75);

        Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
        selectedAlgorithms.put(proximityAlgorithm, 1.0);
        selectedAlgorithms.put(rssiRatioAlgorithm, 0.8);

        when(algorithmSelector.selectAlgorithmsWithReasons(any()))
            .thenReturn(new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, Map.of()));

        // One algorithm succeeds
        when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(validPosition);
        
        // Another algorithm times out (simulated by long sleep)
        when(rssiRatioAlgorithm.calculatePosition(any(), any())).thenAnswer(invocation -> {
            try {
                Thread.sleep(6000); // Longer than timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        // Act
        WifiPositioningCalculator.PositioningResult result = calculator.calculatePosition(wifiAccessPoints);

        // Assert - Should still return result from successful algorithm
        assertNotNull(result, "Result should not be null even when one algorithm times out");
        assertNotNull(result.position(), "Position should be calculated from successful algorithm");
        assertEquals(validPosition.latitude(), result.position().latitude(), 0.001);
        assertEquals(validPosition.longitude(), result.position().longitude(), 0.001);
    }

    // ======================================================================================
    // TEST 5: All Algorithms Fail - Proper Error Handling
    // ======================================================================================

    @Test
    @DisplayName("Test 5: All Algorithms Fail - Should Return Null Position")
    void testAllAlgorithmsFailErrorHandling() {
        // Arrange
        WifiAccessPoints wifiAccessPoints = createWifiAccessPoints(
            List.of(
                WifiScanResult.of("00:11:22:33:44:01", -65.0, 2437, "test"),
                WifiScanResult.of("00:11:22:33:44:02", -68.0, 5180, "test")
            ),
            List.of(
                createAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.0),
                createAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.0)
            )
        );

        Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
        selectedAlgorithms.put(proximityAlgorithm, 1.0);
        selectedAlgorithms.put(rssiRatioAlgorithm, 0.8);

        when(algorithmSelector.selectAlgorithmsWithReasons(any()))
            .thenReturn(new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, Map.of()));

        // All algorithms fail or return null
        when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(null);
        when(rssiRatioAlgorithm.calculatePosition(any(), any()))
            .thenThrow(new RuntimeException("Algorithm computation failed"));

        // Act
        WifiPositioningCalculator.PositioningResult result = calculator.calculatePosition(wifiAccessPoints);

        // Assert
        assertNotNull(result, "Result should contain partial info even when all algorithms fail");
        assertNull(result.position(), "Position should be null when all algorithms fail");
        assertNotNull(result.algorithmWeights(), "Algorithm weights should still be present");
    }

    // ======================================================================================
    // Helper Methods
    // ======================================================================================

    private WifiAccessPoints createWifiAccessPoints(List<WifiScanResult> scans, List<WifiAccessPoint> aps) {
        List<WifiAPWithScan> validAPs = new ArrayList<>();
        for (int i = 0; i < Math.min(scans.size(), aps.size()); i++) {
            validAPs.add(new WifiAPWithScan(aps.get(i), scans.get(i)));
        }
        return WifiAccessPoints.builder()
            .validAccessPoints(validAPs)
            .originalScans(scans)
            .viable(true)
            .build();
    }

    private WifiAccessPoint createAP(String mac, double lat, double lon, double alt) {
        return WifiAccessPoint.builder()
            .macAddress(mac)
            .latitude(lat)
            .longitude(lon)
            .altitude(alt)
            .horizontalAccuracy(10.0)
            .verticalAccuracy(5.0)
            .confidence(0.85)
            .status(WifiAccessPoint.STATUS_ACTIVE)
            .build();
    }
}
