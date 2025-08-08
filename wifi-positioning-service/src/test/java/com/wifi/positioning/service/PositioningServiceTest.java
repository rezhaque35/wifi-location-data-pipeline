package com.wifi.positioning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.WifiPositioningCalculator;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.repository.WifiAccessPointRepository;

@ExtendWith(MockitoExtension.class)
public class PositioningServiceTest {

  @Mock private WifiAccessPointRepository accessPointRepository;

  @Mock private WifiPositioningCalculator calculator;

  @Mock private SignalPhysicsValidator signalPhysicsValidator;

  @InjectMocks private PositioningServiceImpl positioningService;

  private WifiPositioningRequest request;
  private List<WifiScanResult> scanResults;
  private List<WifiAccessPoint> accessPoints;

  @BeforeEach
  void setUp() {
    // Create test data
    scanResults =
        List.of(
            WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "Test"),
            WifiScanResult.of("AA:BB:CC:DD:EE:FF", -70.0, 5180, "Test"));

    request =
        new WifiPositioningRequest(
            scanResults, "test-client", "test-request-id", "test-app", false // calculationDetail
            );

    accessPoints =
        Arrays.asList(
            createAccessPoint(
                "00:11:22:33:44:55", 37.7749, -122.4194, 10.0, WifiAccessPoint.STATUS_ACTIVE),
            createAccessPoint(
                "AA:BB:CC:DD:EE:FF", 37.7751, -122.4196, 12.0, WifiAccessPoint.STATUS_WARNING));

    // Set up calculator behavior to return a valid result
    PositioningAlgorithm mockAlgorithm = mock(PositioningAlgorithm.class);
    lenient().when(mockAlgorithm.getName()).thenReturn("Test Algorithm");

    WifiPositioningCalculator.PositioningResult positioningResult =
        new WifiPositioningCalculator.PositioningResult(
            new Position(37.7749, -122.4194, 10.0, 5.0, 0.85), Map.of(mockAlgorithm, 1.0));

    lenient()
        .when(calculator.calculatePosition(anyList(), anyList()))
        .thenReturn(positioningResult);

    // Mock repository to return test access points
    Map<String, WifiAccessPoint> apMap = new HashMap<>();
    for (WifiAccessPoint ap : accessPoints) {
      apMap.put(ap.getMacAddress(), ap);
    }

    lenient().when(accessPointRepository.findByMacAddresses(anySet())).thenReturn(apMap);

    // Mock signal physics validator to pass validation
    lenient().when(signalPhysicsValidator.isPhysicallyPossible(anyList())).thenReturn(true);
  }

  private WifiAccessPoint createAccessPoint(
      String mac, double lat, double lon, double alt, String status) {
    return WifiAccessPoint.builder()
        .macAddress(mac)
        .latitude(lat)
        .longitude(lon)
        .altitude(alt)
        .horizontalAccuracy(10.0)
        .verticalAccuracy(5.0)
        .confidence(0.85)
        .status(status)
        .build();
  }

  @Nested
  @DisplayName("Happy Path Tests")
  class HappyPathTests {

    @Test
    @DisplayName("Should calculate position when given valid input")
    void should_CalculatePosition_When_GivenValidInput() {
      // Act
      WifiPositioningResponse response = positioningService.calculatePosition(request);

      // Assert
      assertNotNull(response);
      assertEquals("SUCCESS", response.result());
      assertNotNull(response.wifiPosition());
      assertNotNull(response.wifiPosition().latitude());
      assertNotNull(response.wifiPosition().longitude());

      // Verify repository and calculator were called
      verify(accessPointRepository).findByMacAddresses(anySet());
      verify(calculator).calculatePosition(anyList(), anyList());
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should return error response when scan results are empty")
    void should_ReturnErrorResponse_When_ScanResultsAreEmpty() {
      // Arrange
      WifiPositioningRequest emptyRequest =
          new WifiPositioningRequest(
              Collections.emptyList(), "test-client", "test-request-id", "test-app", false);

      // Act
      WifiPositioningResponse response = positioningService.calculatePosition(emptyRequest);

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
    @DisplayName("Should return error when no known APs are found")
    void should_ReturnError_When_NoKnownAPsAreFound() {
      // Arrange
      when(accessPointRepository.findByMacAddresses(anySet())).thenReturn(Map.of());

      // Act
      WifiPositioningResponse response = positioningService.calculatePosition(request);

      // Assert
      assertNotNull(response);
      assertEquals("ERROR", response.result());
      assertTrue(response.message().contains("no position could be determined"));
    }

    @Test
    @DisplayName("Should return error when calculator returns null")
    void should_ReturnError_When_CalculatorReturnsNull() {
      // Arrange
      when(calculator.calculatePosition(anyList(), anyList())).thenReturn(null);

      // Act
      WifiPositioningResponse response = positioningService.calculatePosition(request);

      // Assert
      assertNotNull(response);
      assertEquals("ERROR", response.result());
    }

    @Test
    @DisplayName("Should return error when signals are physically impossible")
    void should_ReturnError_When_SignalsArePhysicallyImpossible() {
      // Arrange
      when(signalPhysicsValidator.isPhysicallyPossible(anyList())).thenReturn(false);

      // Act
      WifiPositioningResponse response = positioningService.calculatePosition(request);

      // Assert
      assertNotNull(response);
      assertEquals("ERROR", response.result());
      assertTrue(response.message().contains("Physically impossible"));
    }
  }

  @Nested
  @DisplayName("Access Point Status Filtering Tests")
  class AccessPointFilteringTests {

    @Test
    @DisplayName("Should filter out APs with invalid status")
    void should_FilterOutAPs_With_InvalidStatus() {
      // Arrange
      List<WifiAccessPoint> mixedStatusAPs =
          Arrays.asList(
              createAccessPoint(
                  "00:11:22:33:44:55", 37.7749, -122.4194, 10.0, WifiAccessPoint.STATUS_ACTIVE),
              createAccessPoint(
                  "AA:BB:CC:DD:EE:FF", 37.7751, -122.4196, 12.0, WifiAccessPoint.STATUS_ERROR),
              createAccessPoint(
                  "11:22:33:44:55:66", 37.7753, -122.4198, 14.0, WifiAccessPoint.STATUS_EXPIRED));

      Map<String, WifiAccessPoint> apMap = new HashMap<>();
      for (WifiAccessPoint ap : mixedStatusAPs) {
        apMap.put(ap.getMacAddress(), ap);
      }

      when(accessPointRepository.findByMacAddresses(anySet())).thenReturn(apMap);

      // Reset the calculator mock to verify it gets called with filtered APs
      reset(calculator);

      // Setup calculator to return valid result
      PositioningAlgorithm mockAlgorithm = mock(PositioningAlgorithm.class);
      when(mockAlgorithm.getName()).thenReturn("Test Algorithm");

      WifiPositioningCalculator.PositioningResult positioningResult =
          new WifiPositioningCalculator.PositioningResult(
              new Position(37.7749, -122.4194, 10.0, 5.0, 0.85), Map.of(mockAlgorithm, 1.0));

      when(calculator.calculatePosition(anyList(), anyList())).thenReturn(positioningResult);

      // Act
      WifiPositioningResponse response = positioningService.calculatePosition(request);

      // Assert
      assertNotNull(response);
      assertEquals("SUCCESS", response.result());

      // Verify calculator was called with only the valid status AP
      verify(calculator)
          .calculatePosition(
              anyList(),
              argThat(
                  aps ->
                      aps.size() == 1
                          && aps.get(0).getMacAddress().equals("00:11:22:33:44:55")
                          && aps.get(0).getStatus().equals(WifiAccessPoint.STATUS_ACTIVE)));
    }
  }
}
