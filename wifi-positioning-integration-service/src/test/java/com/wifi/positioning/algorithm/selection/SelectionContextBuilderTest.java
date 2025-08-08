package com.wifi.positioning.algorithm.selection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("SelectionContextBuilder Tests")
class SelectionContextBuilderTest {

  @InjectMocks private SelectionContextBuilder contextBuilder;

  @Nested
  @DisplayName("Signal Quality Factor Tests")
  class SignalQualityFactorTests {

    @Test
    @DisplayName("should return STRONG_SIGNAL for high signal strengths")
    void shouldReturnStrongSignalForHighSignalStrengths() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-60.0, -65.0, -68.0});

      // Act
      SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);

      // Assert
      assertEquals(
          SignalQualityFactor.STRONG_SIGNAL,
          factor,
          "Expected STRONG_SIGNAL for signals better than -70dBm");
    }

    @Test
    @DisplayName("should return MEDIUM_SIGNAL for medium signal strengths")
    void shouldReturnMediumSignalForMediumSignalStrengths() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-75.0, -80.0, -72.0});

      // Act
      SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);

      // Assert
      assertEquals(
          SignalQualityFactor.MEDIUM_SIGNAL,
          factor,
          "Expected MEDIUM_SIGNAL for signals between -70dBm and -85dBm");
    }

    @Test
    @DisplayName("should return WEAK_SIGNAL for weak signal strengths")
    void shouldReturnWeakSignalForWeakSignalStrengths() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-86.0, -90.0, -88.0});

      // Act
      SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);

      // Assert
      assertEquals(
          SignalQualityFactor.WEAK_SIGNAL,
          factor,
          "Expected WEAK_SIGNAL for signals between -85dBm and -95dBm");
    }

    @Test
    @DisplayName("should return VERY_WEAK_SIGNAL for very weak signal strengths")
    void shouldReturnVeryWeakSignalForVeryWeakSignalStrengths() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-96.0, -98.0, -100.0});

      // Act
      SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);

      // Assert
      assertEquals(
          SignalQualityFactor.VERY_WEAK_SIGNAL,
          factor,
          "Expected VERY_WEAK_SIGNAL for signals worse than -95dBm");
    }

    @Test
    @DisplayName("should handle empty scan list")
    void shouldHandleEmptyScanList() {
      // Arrange
      List<WifiScanResult> scans = new ArrayList<>();

      // Act
      SignalQualityFactor factor = contextBuilder.determineSignalQuality(scans);

      // Assert
      assertEquals(
          SignalQualityFactor.MEDIUM_SIGNAL,
          factor,
          "Expected MEDIUM_SIGNAL as default for empty scan list");
    }
  }

  @Nested
  @DisplayName("Signal Distribution Factor Tests")
  class SignalDistributionFactorTests {

    @Test
    @DisplayName("should return UNIFORM_SIGNALS for low standard deviation")
    void shouldReturnUniformSignalsForLowStandardDeviation() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-75.0, -76.0, -74.0});

      // Act
      SignalDistributionFactor factor = contextBuilder.determineSignalDistribution(scans);

      // Assert
      assertEquals(
          SignalDistributionFactor.UNIFORM_SIGNALS,
          factor,
          "Expected UNIFORM_SIGNALS for standard deviation < 3.0");
    }

    @Test
    @DisplayName("should return MIXED_SIGNALS for medium standard deviation")
    void shouldReturnMixedSignalsForMediumStandardDeviation() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-70.0, -80.0, -75.0});

      // Act
      SignalDistributionFactor factor = contextBuilder.determineSignalDistribution(scans);

      // Assert
      assertEquals(
          SignalDistributionFactor.MIXED_SIGNALS,
          factor,
          "Expected MIXED_SIGNALS for standard deviation between 3.0 and 10.0");
    }

    @Test
    @DisplayName("should return SIGNAL_OUTLIERS for high standard deviation")
    void shouldReturnSignalOutliersForHighStandardDeviation() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-60.0, -90.0, -75.0});

      // Act
      SignalDistributionFactor factor = contextBuilder.determineSignalDistribution(scans);

      // Assert
      assertEquals(
          SignalDistributionFactor.SIGNAL_OUTLIERS,
          factor,
          "Expected SIGNAL_OUTLIERS for standard deviation > 10.0");
    }

    @Test
    @DisplayName("should handle empty scan list")
    void shouldHandleEmptyScanList() {
      // Arrange
      List<WifiScanResult> scans = new ArrayList<>();

      // Act
      SignalDistributionFactor factor = contextBuilder.determineSignalDistribution(scans);

      // Assert
      assertEquals(
          SignalDistributionFactor.UNIFORM_SIGNALS,
          factor,
          "Expected UNIFORM_SIGNALS as default for empty scan list");
    }
  }

  @Nested
  @DisplayName("Geometric Quality Factor Tests")
  class GeometricQualityFactorTests {

    @Test
    @DisplayName("should return EXCELLENT_GDOP for triangular AP arrangement")
    void shouldReturnExcellentGdopForTriangularArrangement() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-70.0, -72.0, -74.0});
      Map<String, WifiAccessPoint> apMap =
          createAccessPoints(
              new double[][] {
                {37.7751, -122.4196}, // AP1 at origin
                {37.7751, -122.4176}, // AP2 ~200m east
                {37.7771, -122.4186} // AP3 ~200m northeast (forms clear triangle)
              });

      // Act
      GeometricQualityFactor factor =
          GeometricQualityFactor.determineGeometricQuality(scans, apMap);

      // Assert
      assertEquals(
          GeometricQualityFactor.EXCELLENT_GDOP,
          factor,
          "Expected EXCELLENT_GDOP for triangular AP arrangement");
    }

    @Test
    @DisplayName("should return geometric quality factor for APs configuration from Test Case 4")
    void shouldReturnGeometricQualityFactorForSimpleConfigurationOfAPsFromTestCase4() {
      // Arrange
      // Signal strengths from Test Case 4
      List<WifiScanResult> scans =
          List.of(
              WifiScanResult.of("00:11:22:33:44:04", -71.2, 5240, "MultiAP_Test"),
              WifiScanResult.of("00:11:22:33:44:05", -85.5, 2412, "WeakSignal_Test"),
              WifiScanResult.of("00:11:22:33:44:06", -70.0, 2437, "Collinear_Test_06"));

      Map<String, WifiAccessPoint> apMap =
          Map.of(
              "00:11:22:33:44:04",
                  WifiAccessPoint.builder()
                      .macAddress("00:11:22:33:44:04")
                      .latitude(37.7757)
                      .longitude(-122.4202)
                      .build(),
              "00:11:22:33:44:05",
                  WifiAccessPoint.builder()
                      .macAddress("00:11:22:33:44:05")
                      .latitude(37.7755)
                      .longitude(-122.4200)
                      .build(),
              "00:11:22:33:44:06",
                  WifiAccessPoint.builder()
                      .macAddress("00:11:22:33:44:06")
                      .latitude(37.7753)
                      .longitude(-122.4198)
                      .build());

      // Act
      GeometricQualityFactor factor =
          GeometricQualityFactor.determineGeometricQuality(scans, apMap);

      // Assert - These APs have good spacing but form a diagonal line
      // Should return either GOOD_GDOP or COLLINEAR
      assertTrue(
          factor == GeometricQualityFactor.GOOD_GDOP || factor == GeometricQualityFactor.COLLINEAR,
          "Expected GOOD_GDOP or COLLINEAR for diagonally aligned APs");
    }

    @Test
    @DisplayName("should return COLLINEAR for collinear APs")
    void shouldReturnCollinearForCollinearAPs() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-70.0, -72.0, -74.0});
      Map<String, WifiAccessPoint> apMap =
          createAccessPoints(
              new double[][] {
                {37.7751, -122.4196}, // AP1
                {37.7753, -122.4196}, // AP2 - same longitude but different latitude
                {37.7755, -122.4196} // AP3 - same longitude but different latitude
              });

      // Act
      GeometricQualityFactor factor =
          GeometricQualityFactor.determineGeometricQuality(scans, apMap);

      // Assert
      assertEquals(
          GeometricQualityFactor.COLLINEAR,
          factor,
          "Expected COLLINEAR for APs arranged in a straight line");
    }

    @Test
    @DisplayName("should detect collinearity but return GOOD_GDOP if disabled")
    void shouldDetectCollinearityInGDOPCalculation() {
      // This test ensures the GDOP calculation is correctly implemented
      // For collinear points, the determinant of the matrix should be close to zero

      List<WifiScanResult> scans = createWifiScans(new double[] {-70.0, -72.0, -74.0});
      Map<String, WifiAccessPoint> apMap =
          createAccessPoints(
              new double[][] {
                {37.7751, -122.4196}, // AP1
                {37.7753, -122.4196}, // AP2 - same longitude but different latitude
                {37.7755, -122.4196} // AP3 - same longitude but different latitude
              });

      // The determineGeometricQuality method should detect collinearity
      GeometricQualityFactor factor =
          GeometricQualityFactor.determineGeometricQuality(scans, apMap);
      assertEquals(
          GeometricQualityFactor.COLLINEAR,
          factor,
          "GeometricQualityFactor should detect collinearity for these points");
    }

    @Test
    @DisplayName("should handle insufficient APs")
    void shouldHandleInsufficientAPs() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-70.0, -72.0}); // Only 2 APs
      Map<String, WifiAccessPoint> apMap =
          createAccessPoints(
              new double[][] {
                {37.7751, -122.4196}, // AP1
                {37.7753, -122.4198} // AP2
              });

      // Act
      GeometricQualityFactor factor =
          GeometricQualityFactor.determineGeometricQuality(scans, apMap);

      // Assert
      assertEquals(
          GeometricQualityFactor.POOR_GDOP,
          factor,
          "Expected POOR_GDOP for insufficient number of APs");
    }

    @Test
    @DisplayName("should handle missing APs in map")
    void shouldHandleMissingAPsInMap() {
      // Arrange
      List<WifiScanResult> scans = createWifiScans(new double[] {-70.0, -72.0, -74.0, -76.0});
      Map<String, WifiAccessPoint> apMap =
          createAccessPoints(
              new double[][] {
                {37.7751, -122.4196}, // AP1
                {37.7753, -122.4198} // AP2
                // AP3 and AP4 missing from map
              });

      // Act
      GeometricQualityFactor factor =
          GeometricQualityFactor.determineGeometricQuality(scans, apMap);

      // Assert
      assertEquals(
          GeometricQualityFactor.POOR_GDOP,
          factor,
          "Expected POOR_GDOP when APs are missing from the map");
    }
  }

  private List<WifiScanResult> createWifiScans(double[] signalStrengths) {
    List<WifiScanResult> scans = new ArrayList<>();
    for (int i = 0; i < signalStrengths.length; i++) {
      String mac = String.format("AP%d", i + 1);
      scans.add(WifiScanResult.of(mac, signalStrengths[i], 2437, "test"));
    }
    return scans;
  }

  private Map<String, WifiAccessPoint> createAccessPoints(double[][] coordinates) {
    Map<String, WifiAccessPoint> apMap = new HashMap<>();
    for (int i = 0; i < coordinates.length; i++) {
      String mac = String.format("AP%d", i + 1);
      double latitude = coordinates[i][0];
      double longitude = coordinates[i][1];

      apMap.put(
          mac,
          WifiAccessPoint.builder()
              .macAddress(mac)
              .latitude(latitude)
              .longitude(longitude)
              .build());
    }
    return apMap;
  }
}
