package com.wifi.positioning.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.wifi.positioning.algorithm.impl.*;
import com.wifi.positioning.algorithm.impl.PositionCombiner;
import com.wifi.positioning.algorithm.selection.AlgorithmSelector;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.algorithm.selection.SelectionContextBuilder;
import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.service.SignalPhysicsValidator;

/**
 * Comprehensive test suite for WifiPositioningCalculator covering all scenarios from test data.
 * Test cases are organized into categories matching the test data structure: 1. Basic Scenarios
 * (Cases 1-5) 2. Collinear APs (Cases 6-10) 3. High Density Cluster (Cases 11-15) 4. Mixed Signal
 * Quality (Cases 16-20) 5. Time Series Data (Cases 21-25)
 */
@ExtendWith(MockitoExtension.class)
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

  @Mock private SignalPhysicsValidator signalPhysicsValidator;

  @InjectMocks private WifiPositioningCalculator wifiPositioningCalculator;

  private TestDataLoader testData;

  @BeforeEach
  void setUp() {
    testData = new TestDataLoader();

    // Mock the signal physics validator to always return true for test cases
    lenient().when(signalPhysicsValidator.isPhysicallyPossible(any())).thenReturn(true);

    // Set up algorithm names with lenient mode to avoid unused stubbing errors
    lenient().when(proximityAlgorithm.getName()).thenReturn("proximity");
    lenient().when(rssiRatioAlgorithm.getName()).thenReturn("rssi_ratio");
    lenient().when(weightedCentroidAlgorithm.getName()).thenReturn("weighted_centroid");
    lenient().when(logDistanceAlgorithm.getName()).thenReturn("log_distance");
    lenient().when(maximumLikelihoodAlgorithm.getName()).thenReturn("maximum_likelihood");
    lenient().when(trilaterationAlgorithm.getName()).thenReturn("trilateration");

    // Set up base confidence values for algorithms
    lenient().when(proximityAlgorithm.getConfidence()).thenReturn(0.65);
    lenient().when(rssiRatioAlgorithm.getConfidence()).thenReturn(0.75);
    lenient().when(weightedCentroidAlgorithm.getConfidence()).thenReturn(0.80);
    lenient().when(logDistanceAlgorithm.getConfidence()).thenReturn(0.85);
    lenient().when(maximumLikelihoodAlgorithm.getConfidence()).thenReturn(0.90);
    lenient().when(trilaterationAlgorithm.getConfidence()).thenReturn(0.85);

    // Initialize the algorithms list in the WifiPositioningCalculator
    List<PositioningAlgorithm> algorithms =
        Arrays.asList(
            proximityAlgorithm,
            rssiRatioAlgorithm,
            weightedCentroidAlgorithm,
            logDistanceAlgorithm,
            maximumLikelihoodAlgorithm,
            trilaterationAlgorithm);

    ReflectionTestUtils.setField(wifiPositioningCalculator, "algorithms", algorithms);

    // Default behavior for context builder and position combiner
    lenient()
        .when(contextBuilder.buildContext(any(), any()))
        .thenReturn(SelectionContext.builder().build());

    // Set up position combiner to return the input position with the highest weight
    lenient()
        .when(positionCombiner.combinePositions(any()))
        .thenAnswer(
            invocation -> {
              List<PositionCombiner.WeightedPosition> positions = invocation.getArgument(0);
              if (positions.isEmpty()) {
                return null;
              }

              PositionCombiner.WeightedPosition result = positions.get(0);
              for (PositionCombiner.WeightedPosition wp : positions) {
                if (wp.weight() > result.weight()) {
                  result = wp;
                }
              }
              return result.position();
            });

    // Mock the AlgorithmSelector.selectAlgorithmsWithReasons method
    lenient()
        .when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              // The first parameter (algorithms) has been removed, so we don't need to extract it
              // Get scan results and apMap from the remaining parameters
              List<WifiScanResult> scanResults = invocation.getArgument(0);

              // Create default selection that includes all algorithms
              Map<PositioningAlgorithm, Double> algorithmWeights = new HashMap<>();
              Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();

              // Add all algorithms with default weights
              algorithmWeights.put(proximityAlgorithm, 1.0);
              algorithmWeights.put(rssiRatioAlgorithm, 1.0);
              algorithmWeights.put(weightedCentroidAlgorithm, 1.0);
              algorithmWeights.put(logDistanceAlgorithm, 1.0);
              algorithmWeights.put(maximumLikelihoodAlgorithm, 1.0);
              algorithmWeights.put(trilaterationAlgorithm, 1.0);

              // Add default reasons
              selectionReasons.put(proximityAlgorithm, List.of("Default selection for testing"));
              selectionReasons.put(rssiRatioAlgorithm, List.of("Default selection for testing"));
              selectionReasons.put(
                  weightedCentroidAlgorithm, List.of("Default selection for testing"));
              selectionReasons.put(logDistanceAlgorithm, List.of("Default selection for testing"));
              selectionReasons.put(
                  maximumLikelihoodAlgorithm, List.of("Default selection for testing"));
              selectionReasons.put(
                  trilaterationAlgorithm, List.of("Default selection for testing"));

              return new AlgorithmSelector.AlgorithmSelectionInfo(
                  algorithmWeights, selectionReasons);
            });
  }

  @Nested
  @DisplayName("Basic Scenarios (Cases 1-5)")
  class BasicScenarios {

    @Test
    @DisplayName("Case 1: Single AP - Should use Proximity Detection")
    void singleAPScenario() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("SingleAP_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("SingleAP_Test");
      Position expectedPosition = new Position(37.7749, -122.4194, 10.5, 50.0, 0.65);

      // Mock the algorithm selector to return only the proximity algorithm for this specific test
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(proximityAlgorithm, 1.0);

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(
          proximityAlgorithm, List.of("Single AP scenario requires proximity detection"));

      // Override the default mock for this specific test
      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result);
      assertEquals(expectedPosition.latitude(), result.position().latitude(), 0.0001);
      assertEquals(expectedPosition.longitude(), result.position().longitude(), 0.0001);
      assertEquals(expectedPosition.confidence(), result.position().confidence(), 0.0001);
    }

    @Test
    @DisplayName("Case 2: Two APs - Should use RSSI Ratio")
    void twoAPScenario() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("DualAP_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("DualAP_Test");
      Position expectedPosition = new Position(37.7750, -122.4195, 12.5, 25.0, 0.78);

      // Mock the algorithm selector to return only the RSSI ratio algorithm for this specific test
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(rssiRatioAlgorithm, 1.0);

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(rssiRatioAlgorithm, List.of("Dual AP scenario is best for RSSI ratio"));

      // Override the default mock for this specific test
      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      when(rssiRatioAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result);
      assertEquals(expectedPosition.latitude(), result.position().latitude(), 0.0001);
      assertEquals(expectedPosition.confidence(), result.position().confidence(), 0.0001);
    }

    @Test
    @DisplayName("Case 3: Three APs - Should use Algorithm Selection")
    void threeAPScenario() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("TriAP_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("TriAP_Test");

      Position position1 = new Position(37.7751, -122.4196, 15.0, 8.5, 0.85);

      // Mock the algorithm selector to return selected algorithm
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(weightedCentroidAlgorithm, 1.0);

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(
          weightedCentroidAlgorithm, List.of("Three APs work well with weighted centroid"));

      // Override the default mock for this specific test
      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      when(weightedCentroidAlgorithm.calculatePosition(any(), any())).thenReturn(position1);

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result);
      assertNotNull(result.position());

      // Check that at least one algorithm was used
      assertNotNull(result.algorithmWeights());
      assertTrue(result.algorithmWeights().size() >= 1);
    }

    @Test
    @DisplayName("Case 4: Multiple APs - Should use Maximum Likelihood")
    void multipleAPScenario() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("MultiAP_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("MultiAP_Test");
      Position expectedPosition = new Position(37.7752, -122.4197, 18.0, 15.5, 0.85);

      // Mock the algorithm selector to return only maximum likelihood algorithm
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(maximumLikelihoodAlgorithm, 1.0);

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(
          maximumLikelihoodAlgorithm, List.of("Multiple APs work best with maximum likelihood"));

      // Override the default mock for this specific test
      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      when(maximumLikelihoodAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result);
      assertEquals(expectedPosition.latitude(), result.position().latitude(), 0.0001);
      assertTrue(result.position().confidence() >= 0.85);
    }

    @Test
    @DisplayName("Case 5: Weak Signals - Should Handle Poor Quality")
    void weakSignalsScenario() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("WeakSignal_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("WeakSignal_Test");
      Position expectedPosition = new Position(37.7753, -122.4198, 20.0, 35.0, 0.45);

      // Mock the algorithm selector to return only proximity algorithm for weak signals
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(proximityAlgorithm, 0.9); // Lower weight due to weak signal

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(proximityAlgorithm, List.of("Weak signals favor proximity algorithm"));

      // Override the default mock for this specific test
      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result);
      assertEquals(expectedPosition.latitude(), result.position().latitude(), 0.0001);
      assertEquals(expectedPosition.confidence(), result.position().confidence(), 0.0001);
    }
  }

  @Nested
  @DisplayName("Collinear APs (Cases 6-10)")
  class CollinearScenarios {

    @Test
    @DisplayName("Case 6-10: Collinear APs - Should Handle Poor Geometry")
    void collinearAPsScenario() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("Collinear_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("Collinear_Test");
      Position expectedPosition = new Position(37.7755, -122.4200, 25.0, 40.0, 0.55);

      // Mock the algorithm selector to return multiple algorithms
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(weightedCentroidAlgorithm, 1.0);
      selectedAlgorithms.put(logDistanceAlgorithm, 0.8); // Lower weight due to collinearity

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(
          weightedCentroidAlgorithm,
          List.of("Collinear geometry works better with weighted centroid"));
      selectionReasons.put(
          logDistanceAlgorithm, List.of("Secondary algorithm for collinear points"));

      // Override the default mock for this specific test
      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      when(weightedCentroidAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result);
      assertEquals(expectedPosition.latitude(), result.position().latitude(), 0.0001);
      assertEquals(expectedPosition.confidence(), result.position().confidence(), 0.0001);
    }
  }

  @Nested
  @DisplayName("High Density Cluster (Cases 11-15)")
  class HighDensityScenarios {

    @Test
    @DisplayName("Case 11-15: Dense AP Cluster - Should Use Maximum Likelihood")
    void highDensityScenario() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("HighDensity_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("HighDensity_Test");
      Position expectedPosition = new Position(37.7760, -122.4200, 25.0, 15.0, 0.85);

      // Mock the algorithm selector to return only maximum likelihood for density
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(maximumLikelihoodAlgorithm, 1.0);

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(
          maximumLikelihoodAlgorithm,
          List.of("High density clusters work best with maximum likelihood"));

      // Override the default mock for this specific test
      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      when(maximumLikelihoodAlgorithm.calculatePosition(any(), any())).thenReturn(expectedPosition);

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result);
      assertNotNull(result.position());
      assertTrue(result.position().confidence() >= 0.85);
      assertTrue(result.position().accuracy() <= 15.0);
    }
  }

  @Nested
  @DisplayName("Mixed Signal Quality (Cases 16-20)")
  class MixedSignalScenarios {

    @Test
    @DisplayName("Case 16-20: Mixed Signal Quality - Should Adapt Algorithm Selection")
    void mixedSignalQualityScenario() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("MixedSignal_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("MixedSignal_Test");

      // Set up different algorithm responses
      Position position1 = new Position(37.7770, -122.4210, 30.0, 15.0, 0.90);
      Position position2 = new Position(37.7770, -122.4210, 30.0, 15.0, 0.85);

      // Mock the algorithm selector to return multiple algorithms for mixed signals
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(maximumLikelihoodAlgorithm, 1.0);
      selectedAlgorithms.put(logDistanceAlgorithm, 0.9);

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(maximumLikelihoodAlgorithm, List.of("Mixed signals - high confidence"));
      selectionReasons.put(logDistanceAlgorithm, List.of("Mixed signals - good distance modeling"));

      // Override the default mock for this specific test
      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      when(maximumLikelihoodAlgorithm.calculatePosition(any(), any())).thenReturn(position1);
      when(logDistanceAlgorithm.calculatePosition(any(), any())).thenReturn(position2);

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result);
      assertNotNull(result.position());
      // Confidence should reflect signal quality variation
      assertTrue(result.position().confidence() >= 0.5 && result.position().confidence() <= 0.9);

      // Verify that an algorithm was selected as the best algorithm
      assertFalse(result.algorithmWeights().isEmpty());
    }
  }

  @Nested
  @DisplayName("Time Series Data (Cases 21-25)")
  class TimeSeriesScenarios {
    @Test
    @DisplayName("Case 21-25: Temporal Variations - Should Maintain Stability")
    void timeSeriesScenario() {
      // Arrange
      List<WifiPositioningCalculator.PositioningResult> positioningResults = new ArrayList<>();
      List<Position> positions = new ArrayList<>();

      // Simulate 5 positions over time with the same APs but varying signal strengths
      List<WifiScanResult> scans1 =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:24", -65.0, 2437, "test"),
              WifiScanResult.of("00:11:22:33:44:25", -75.0, 5180, "test"),
              WifiScanResult.of("00:11:22:33:44:26", -85.0, 2437, "test"));
      List<WifiScanResult> scans2 =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:24", -64.0, 2437, "test"),
              WifiScanResult.of("00:11:22:33:44:25", -76.0, 5180, "test"),
              WifiScanResult.of("00:11:22:33:44:26", -84.0, 2437, "test"));
      List<WifiScanResult> scans3 =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:24", -66.0, 2437, "test"),
              WifiScanResult.of("00:11:22:33:44:25", -74.0, 5180, "test"),
              WifiScanResult.of("00:11:22:33:44:26", -86.0, 2437, "test"));

      List<WifiAccessPoint> aps = testData.getAccessPoints("MixedSignal_Test");

      // Set up different algorithm responses for each time point
      Position position1 = new Position(37.7770, -122.4210, 30.0, 8.0, 0.85);
      Position position2 = new Position(37.7771, -122.4211, 30.5, 8.2, 0.84);
      Position position3 = new Position(37.7769, -122.4209, 29.5, 8.5, 0.83);

      // Mock the algorithm selector for time series data
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(logDistanceAlgorithm, 1.0);
      selectedAlgorithms.put(weightedCentroidAlgorithm, 0.8);

      Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
      selectionReasons.put(logDistanceAlgorithm, List.of("Time series data favors log distance"));
      selectionReasons.put(
          weightedCentroidAlgorithm, List.of("Alternative algorithm for time series"));

      // Use lenient() to avoid UnnecessaryStubbingException
      lenient()
          .when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(
              new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, selectionReasons));

      // Use lenient() for mock stubs that may not be used
      lenient().when(logDistanceAlgorithm.calculatePosition(any(), any())).thenReturn(position1);
      lenient()
          .when(weightedCentroidAlgorithm.calculatePosition(any(), any()))
          .thenReturn(position1);
      lenient().when(trilaterationAlgorithm.calculatePosition(any(), any())).thenReturn(position1);

      // Act - get positions at 3 different time points
      positioningResults.add(wifiPositioningCalculator.calculatePosition(scans1, aps));
      positioningResults.add(wifiPositioningCalculator.calculatePosition(scans2, aps));
      positioningResults.add(wifiPositioningCalculator.calculatePosition(scans3, aps));

      // Extract positions from positioning results
      for (WifiPositioningCalculator.PositioningResult result : positioningResults) {
        assertNotNull(result);
        assertNotNull(result.position());
        positions.add(result.position());
      }

      // Assert
      // All positions should be non-null
      for (Position position : positions) {
        assertNotNull(position);
      }

      // Calculate position stability metrics
      double maxLatDiff = 0;
      double maxLonDiff = 0;

      for (int i = 0; i < positions.size() - 1; i++) {
        Position p1 = positions.get(i);
        Position p2 = positions.get(i + 1);
        maxLatDiff = Math.max(maxLatDiff, Math.abs(p1.latitude() - p2.latitude()));
        maxLonDiff = Math.max(maxLonDiff, Math.abs(p1.longitude() - p2.longitude()));
      }

      // Ensure position is stable over time (doesn't jump around)
      assertTrue(maxLatDiff < 0.01, "Latitude shouldn't change drastically between measurements");
      assertTrue(maxLonDiff < 0.01, "Longitude shouldn't change drastically between measurements");
    }
  }

  @Test
  @DisplayName("getCalculationInfo should return detailed calculation information")
  void getCalculationInfoShouldReturnDetailedInfo() {
    // Create a simple selection context
    SelectionContext context =
        SelectionContext.builder()
            .apCountFactor(APCountFactor.TWO_APS)
            .signalQuality(SignalQualityFactor.STRONG_SIGNAL)
            .signalDistribution(SignalDistributionFactor.UNIFORM_SIGNALS)
            .geometricQuality(GeometricQualityFactor.GOOD_GDOP)
            .build();

    // Create weighted algorithms map
    Map<PositioningAlgorithm, Double> weightedAlgorithms = new HashMap<>();
    weightedAlgorithms.put(proximityAlgorithm, 0.8);
    weightedAlgorithms.put(rssiRatioAlgorithm, 0.6);

    // Create selection reasons map
    Map<PositioningAlgorithm, List<String>> selectionReasons = new HashMap<>();
    selectionReasons.put(
        proximityAlgorithm, List.of("Primary algorithm for this scenario", "Good signal strength"));
    selectionReasons.put(rssiRatioAlgorithm, List.of("Secondary algorithm as backup"));

    // Create a position
    Position position = new Position(37.7749, -122.4194, 10.0, 5.0, 0.85);

    // Create positioning result
    WifiPositioningCalculator.PositioningResult result =
        new WifiPositioningCalculator.PositioningResult(
            position, weightedAlgorithms, selectionReasons, context);

    // Get calculation info
    String calculationInfo = result.getCalculationInfo();

    // Verify that the calculation info includes all required information
    assertNotNull(calculationInfo);
    assertTrue(calculationInfo.contains("Selection Context:"));
    assertTrue(calculationInfo.contains("AP Count: TWO_APS"));
    assertTrue(calculationInfo.contains("Signal Quality: STRONG_SIGNAL"));
    assertTrue(calculationInfo.contains("Signal Distribution: UNIFORM_SIGNALS"));
    assertTrue(calculationInfo.contains("Geometric Quality: GOOD_GDOP"));

    assertTrue(calculationInfo.contains("Algorithm Selection Reasons:"));
    assertTrue(calculationInfo.contains("Primary algorithm for this scenario"));
    assertTrue(calculationInfo.contains("Good signal strength"));
    assertTrue(calculationInfo.contains("Secondary algorithm as backup"));

    // Check algorithm weights are included
    assertTrue(calculationInfo.contains("proximity (weight: 0.80)"));
    assertTrue(calculationInfo.contains("rssi_ratio (weight: 0.60)"));
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("Should handle algorithm execution timeout gracefully")
    void shouldHandleAlgorithmTimeoutGracefully() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("DualAP_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("DualAP_Test");

      // Mock one algorithm to return normally, another to simulate timeout/failure
      Position validPosition = new Position(37.7750, -122.4195, 12.0, 25.0, 0.70);
      when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(validPosition);

      // Simulate an algorithm that takes too long (this will be handled by the timeout mechanism)
      when(rssiRatioAlgorithm.calculatePosition(any(), any()))
          .thenAnswer(
              invocation -> {
                try {
                  Thread.sleep(6000); // Sleep longer than timeout
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return null;
              });

      // Configure algorithm selector to use both algorithms
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(proximityAlgorithm, 1.0);
      selectedAlgorithms.put(rssiRatioAlgorithm, 0.8);

      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, Map.of()));

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      // Should still return a result based on the algorithm that completed successfully
      assertNotNull(result, "Result should not be null even when one algorithm times out");
      assertNotNull(result.position(), "Position should be calculated from successful algorithms");
      assertEquals(validPosition.latitude(), result.position().latitude(), 0.001);
      assertEquals(validPosition.longitude(), result.position().longitude(), 0.001);
    }

    @Test
    @DisplayName("Should handle all algorithms failing")
    void shouldHandleAllAlgorithmsFailing() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("DualAP_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("DualAP_Test");

      // Mock all algorithms to return null or throw exceptions
      when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(null);
      when(rssiRatioAlgorithm.calculatePosition(any(), any()))
          .thenThrow(new RuntimeException("Algorithm failure"));

      // Configure algorithm selector
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(proximityAlgorithm, 1.0);
      selectedAlgorithms.put(rssiRatioAlgorithm, 0.8);

      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, Map.of()));

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNull(result, "Result should be null when all algorithms fail");
    }

    @Test
    @DisplayName("Should handle mixed algorithm success and failure")
    void shouldHandleMixedAlgorithmResults() {
      // Arrange
      List<WifiScanResult> scans = testData.getWifiScans("TriAP_Test");
      List<WifiAccessPoint> aps = testData.getAccessPoints("TriAP_Test");

      // Mock algorithms with mixed results
      Position position1 = new Position(37.7751, -122.4196, 15.0, 20.0, 0.75);
      Position position2 = new Position(37.7752, -122.4197, 16.0, 22.0, 0.72);

      when(proximityAlgorithm.calculatePosition(any(), any())).thenReturn(position1);
      when(rssiRatioAlgorithm.calculatePosition(any(), any())).thenReturn(position2);
      when(weightedCentroidAlgorithm.calculatePosition(any(), any()))
          .thenReturn(null); // Simulate failure

      // Configure algorithm selector
      Map<PositioningAlgorithm, Double> selectedAlgorithms = new HashMap<>();
      selectedAlgorithms.put(proximityAlgorithm, 1.0);
      selectedAlgorithms.put(rssiRatioAlgorithm, 0.8);
      selectedAlgorithms.put(weightedCentroidAlgorithm, 0.6);

      when(algorithmSelector.selectAlgorithmsWithReasons(any(), any(), any()))
          .thenReturn(new AlgorithmSelector.AlgorithmSelectionInfo(selectedAlgorithms, Map.of()));

      // Act
      WifiPositioningCalculator.PositioningResult result =
          wifiPositioningCalculator.calculatePosition(scans, aps);

      // Assert
      assertNotNull(result, "Result should not be null when some algorithms succeed");
      assertNotNull(result.position(), "Position should be calculated from successful algorithms");
      // Should get a position that's a combination of the successful results
    }
  }

  /** Helper class to load test data from resources. */
  private static class TestDataLoader {
    private final Map<String, List<WifiScanResult>> wifiScans;
    private final Map<String, List<WifiAccessPoint>> accessPoints;

    TestDataLoader() {
      this.wifiScans = loadWifiScans();
      this.accessPoints = loadAccessPoints();
    }

    private Map<String, List<WifiScanResult>> loadWifiScans() {
      return Map.of(
          "SingleAP_Test",
              Arrays.asList(WifiScanResult.of("00:11:22:33:44:01", -65.0, 2437, "test")),
          "DualAP_Test",
              Arrays.asList(
                  WifiScanResult.of("00:11:22:33:44:02", -68.5, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:03", -70.0, 5180, "test")),
          "TriAP_Test",
              Arrays.asList(
                  WifiScanResult.of("00:11:22:33:44:04", -72.0, 2437, "test"),
                  WifiScanResult.of("00:11:22:33:44:05", -74.0, 2437, "test"),
                  WifiScanResult.of("00:11:22:33:44:06", -76.0, 2437, "test")),
          "MultiAP_Test",
              Arrays.asList(
                  WifiScanResult.of("00:11:22:33:44:07", -65.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:08", -67.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:09", -69.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:10", -71.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:11", -73.0, 5180, "test")),
          "WeakSignal_Test",
              Arrays.asList(
                  WifiScanResult.of("00:11:22:33:44:12", -85.0, 2437, "test"),
                  WifiScanResult.of("00:11:22:33:44:13", -87.0, 2437, "test"),
                  WifiScanResult.of("00:11:22:33:44:14", -89.0, 2437, "test")),
          "Collinear_Test",
              Arrays.asList(
                  WifiScanResult.of("00:11:22:33:44:15", -75.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:16", -77.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:17", -79.0, 5180, "test")),
          "HighDensity_Test",
              Arrays.asList(
                  WifiScanResult.of("00:11:22:33:44:18", -62.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:19", -63.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:20", -64.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:21", -65.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:22", -66.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:23", -67.0, 5180, "test")),
          "MixedSignal_Test",
              Arrays.asList(
                  WifiScanResult.of("00:11:22:33:44:24", -65.0, 2437, "test"),
                  WifiScanResult.of("00:11:22:33:44:25", -75.0, 5180, "test"),
                  WifiScanResult.of("00:11:22:33:44:26", -85.0, 2437, "test"),
                  WifiScanResult.of("00:11:22:33:44:27", -70.0, 5180, "test")));
    }

    private Map<String, List<WifiAccessPoint>> loadAccessPoints() {
      return Map.of(
          "SingleAP_Test", Arrays.asList(createAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.5)),
          "DualAP_Test",
              Arrays.asList(
                  createAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.0),
                  createAP("00:11:22:33:44:03", 37.7751, -122.4196, 13.0)),
          "TriAP_Test",
              Arrays.asList(
                  createAP("00:11:22:33:44:04", 37.7751, -122.4196, 15.0),
                  createAP("00:11:22:33:44:05", 37.7752, -122.4197, 16.0),
                  createAP("00:11:22:33:44:06", 37.7753, -122.4198, 17.0)),
          "MultiAP_Test",
              Arrays.asList(
                  createAP("00:11:22:33:44:07", 37.7754, -122.4199, 18.0),
                  createAP("00:11:22:33:44:08", 37.7755, -122.4200, 19.0),
                  createAP("00:11:22:33:44:09", 37.7756, -122.4201, 20.0),
                  createAP("00:11:22:33:44:10", 37.7757, -122.4202, 21.0),
                  createAP("00:11:22:33:44:11", 37.7758, -122.4203, 22.0)),
          "WeakSignal_Test",
              Arrays.asList(
                  createAP("00:11:22:33:44:12", 37.7753, -122.4198, 20.0),
                  createAP("00:11:22:33:44:13", 37.7754, -122.4199, 21.0),
                  createAP("00:11:22:33:44:14", 37.7755, -122.4200, 22.0)),
          "Collinear_Test",
              Arrays.asList(
                  createAP("00:11:22:33:44:15", 37.7754, -122.4194, 15.0),
                  createAP("00:11:22:33:44:16", 37.7759, -122.4194, 16.0),
                  createAP("00:11:22:33:44:17", 37.7764, -122.4194, 17.0)),
          "HighDensity_Test",
              Arrays.asList(
                  createAP("00:11:22:33:44:18", 37.7760, -122.4200, 25.0),
                  createAP("00:11:22:33:44:19", 37.7761, -122.4201, 25.2),
                  createAP("00:11:22:33:44:20", 37.7759, -122.4199, 24.8),
                  createAP("00:11:22:33:44:21", 37.7762, -122.4202, 25.5),
                  createAP("00:11:22:33:44:22", 37.7758, -122.4198, 24.5),
                  createAP("00:11:22:33:44:23", 37.7763, -122.4203, 26.0)),
          "MixedSignal_Test",
              Arrays.asList(
                  createAP("00:11:22:33:44:24", 37.7770, -122.4210, 30.0),
                  createAP("00:11:22:33:44:25", 37.7771, -122.4211, 31.0),
                  createAP("00:11:22:33:44:26", 37.7772, -122.4212, 32.0),
                  createAP("00:11:22:33:44:27", 37.7773, -122.4213, 33.0)));
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

    List<WifiScanResult> getWifiScans(String testCase) {
      return wifiScans.getOrDefault(testCase, new ArrayList<>());
    }

    List<WifiAccessPoint> getAccessPoints(String testCase) {
      return accessPoints.getOrDefault(testCase, new ArrayList<>());
    }
  }
}
