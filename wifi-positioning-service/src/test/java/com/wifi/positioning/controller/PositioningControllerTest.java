package com.wifi.positioning.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.service.PositioningService;

/**
 * Unit tests for the PositioningController class. Tests focus on controller-specific logic like
 * error handling, status codes, and response formatting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Positioning Controller Tests")
class PositioningControllerTest {

  // Test constants
  private static final String TEST_CLIENT = "test-client";
  private static final String TEST_REQUEST_ID = "test-request-id";
  private static final String TEST_APPLICATION = "test-app";
  private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
  private static final double TEST_SIGNAL_STRENGTH = -65.0;
  private static final int TEST_FREQUENCY = 2437;
  private static final String TEST_SSID = "TestAP";

  private static final double TEST_LATITUDE = 37.7749;
  private static final double TEST_LONGITUDE = -122.4194;
  private static final double TEST_ALTITUDE = 10.0;
  private static final double TEST_ACCURACY = 25.0;
  private static final double TEST_CONFIDENCE = 0.5;
  private static final long TEST_CALCULATION_TIME = 100L;

  @Mock private PositioningService positioningService;

  @InjectMocks private PositioningController controller;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private WifiPositioningRequest testRequest;
  private WifiPositioningResponse successResponse;
  private WifiPositioningResponse errorResponse;
  private List<WifiScanResult> wifiScanResults;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();

    // Create test data
    wifiScanResults = new ArrayList<>();
    wifiScanResults.add(
        WifiScanResult.of(TEST_MAC_ADDRESS, TEST_SIGNAL_STRENGTH, TEST_FREQUENCY, TEST_SSID));

    testRequest =
        new WifiPositioningRequest(
            wifiScanResults, TEST_CLIENT, TEST_REQUEST_ID, TEST_APPLICATION, false);

    // Create a success response for testing
    WifiPositioningResponse.WifiPosition wifiPosition =
        new WifiPositioningResponse.WifiPosition(
            TEST_LATITUDE,
            TEST_LONGITUDE,
            TEST_ALTITUDE,
            TEST_ACCURACY,
            0.0,
            TEST_CONFIDENCE,
            Arrays.asList("weighted_centroid"),
            1,
            TEST_CALCULATION_TIME);

    successResponse =
        new WifiPositioningResponse(
            "SUCCESS",
            "Request processed successfully",
            TEST_REQUEST_ID,
            TEST_CLIENT,
            TEST_APPLICATION,
            Instant.now().toEpochMilli(),
            wifiPosition,
            null); // Using null for test since we're not testing calculationInfo structure

    // Create an error response for testing
    errorResponse = WifiPositioningResponse.error("Error message", testRequest);
  }

  @Nested
  @DisplayName("Successful Response Tests")
  class SuccessfulResponseTests {

    @Test
    @DisplayName("Should return successful response with HTTP 200 when calculation succeeds")
    void should_ReturnSuccessfulResponse_When_CalculationSucceeds() throws Exception {
      // Arrange
      when(positioningService.calculatePosition(any(WifiPositioningRequest.class)))
          .thenReturn(successResponse);

      // Act & Assert
      mockMvc
          .perform(
              post("/api/positioning/calculate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(testRequest)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.result", is("SUCCESS")))
          .andExpect(jsonPath("$.requestId", is(TEST_REQUEST_ID)));

      // Verify service interaction
      verify(positioningService).calculatePosition(any(WifiPositioningRequest.class));
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should return error response with HTTP 200 when service returns error response")
    void should_ReturnErrorResponseWithOk_When_ServiceReturnsErrorResponse() throws Exception {
      // Arrange
      when(positioningService.calculatePosition(any(WifiPositioningRequest.class)))
          .thenReturn(errorResponse);

      // Act & Assert
      mockMvc
          .perform(
              post("/api/positioning/calculate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(testRequest)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.result", is("ERROR")))
          .andExpect(jsonPath("$.message", is("Error message")));

      // Verify service interaction
      verify(positioningService).calculatePosition(any(WifiPositioningRequest.class));
    }

    @Test
    @DisplayName("Should return error with generic message when NullPointerException is thrown")
    void should_ReturnErrorWithGenericMessage_When_NullPointerExceptionIsThrown() throws Exception {
      // Arrange
      when(positioningService.calculatePosition(any(WifiPositioningRequest.class)))
          .thenThrow(new NullPointerException("Null pointer error"));

      // Act & Assert
      mockMvc
          .perform(
              post("/api/positioning/calculate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(testRequest)))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.result", is("ERROR")))
          .andExpect(jsonPath("$.message", is("Null pointer error")));

      // Verify service interaction
      verify(positioningService).calculatePosition(any(WifiPositioningRequest.class));
    }

    @Test
    @DisplayName(
        "Should return internal server error status when generic RuntimeException is thrown")
    void should_ReturnInternalServerErrorStatus_When_GenericRuntimeExceptionIsThrown()
        throws Exception {
      // Arrange
      when(positioningService.calculatePosition(any(WifiPositioningRequest.class)))
          .thenThrow(new RuntimeException("Generic error"));

      // Act & Assert
      mockMvc
          .perform(
              post("/api/positioning/calculate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(testRequest)))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.result", is("ERROR")))
          .andExpect(jsonPath("$.message", is("Generic error")));

      // Verify service interaction
      verify(positioningService).calculatePosition(any(WifiPositioningRequest.class));
    }
  }
}
