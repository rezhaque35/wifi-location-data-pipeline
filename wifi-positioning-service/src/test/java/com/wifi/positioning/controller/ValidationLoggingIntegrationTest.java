// src/test/java/com/wifi/positioning/controller/ValidationLoggingIntegrationTest.java
package com.wifi.positioning.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test to verify validation failure logging in real HTTP request scenarios. This test
 * validates that when Jakarta validation fails, the service properly logs: - HTTP status 400 -
 * Request ID from the request body - Client from the request body - Validation error details -
 * Structured format suitable for Splunk indexing
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ValidationLoggingIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void should_LogValidationFailureWithRequestIdAndStatus400_When_InvalidSignalStrength(
      CapturedOutput output) throws Exception {
    // Arrange - JSON with invalid signal strength (-150 is below minimum of -100)
    String invalidRequest =
        """
                {
                    "wifiScanResults": [
                        {
                            "macAddress": "AA:BB:CC:DD:EE:FF",
                            "signalStrength": -150,
                            "frequency": 2400,
                            "ssid": "TestNetwork"
                        }
                    ],
                    "client": "integration-test-client",
                    "requestId": "req-validation-test-001",
                    "application": "test-app"
                }
                """;

    // Act & Assert
    mockMvc
        .perform(
            post("/v1/wifi/position").contentType(MediaType.APPLICATION_JSON).content(invalidRequest))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.result").value("ERROR"))
        .andExpect(jsonPath("$.message").exists());

    // Verify logging output
    String logOutput = output.toString();
    assertTrue(
        logOutput.contains("req-validation-test-001"),
        "Log should contain the requestId from the request");
    assertTrue(
        logOutput.contains("integration-test-client"),
        "Log should contain the client from the request");
    assertTrue(logOutput.contains("HTTP_STATUS=400"), "Log should contain HTTP status 400");
    assertTrue(
        logOutput.contains("Validation failure"), "Log should contain validation failure message");
    assertTrue(
        logOutput.contains("signalStrength"), "Log should mention the invalid field");
  }

  @Test
  void should_LogValidationFailureForMultipleFields_When_MissingRequiredFields(
      CapturedOutput output) throws Exception {
    // Arrange - JSON with missing required fields (macAddress and signalStrength)
    String invalidRequest =
        """
                {
                    "wifiScanResults": [
                        {
                            "frequency": 2400
                        }
                    ],
                    "client": "mobile-client",
                    "requestId": "req-missing-fields-002"
                }
                """;

    // Act & Assert
    mockMvc
        .perform(
            post("/v1/wifi/position").contentType(MediaType.APPLICATION_JSON).content(invalidRequest))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.result").value("ERROR"));

    // Verify logging output contains multiple validation errors
    String logOutput = output.toString();
    assertTrue(
        logOutput.contains("req-missing-fields-002"), "Log should contain the requestId");
    assertTrue(logOutput.contains("mobile-client"), "Log should contain the client");
    assertTrue(logOutput.contains("HTTP_STATUS=400"), "Log should contain HTTP status 400");
    assertTrue(
        logOutput.contains("Validation failure"), "Log should contain validation failure message");
  }

  @Test
  void should_LogValidationFailureForInvalidMacAddress_When_InvalidFormat(CapturedOutput output)
      throws Exception {
    // Arrange - JSON with invalid MAC address format
    String invalidRequest =
        """
                {
                    "wifiScanResults": [
                        {
                            "macAddress": "INVALID-MAC",
                            "signalStrength": -50,
                            "frequency": 2400
                        }
                    ],
                    "client": "web-client",
                    "requestId": "req-invalid-mac-003"
                }
                """;

    // Act & Assert
    mockMvc
        .perform(
            post("/v1/wifi/position").contentType(MediaType.APPLICATION_JSON).content(invalidRequest))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.result").value("ERROR"));

    // Verify logging output
    String logOutput = output.toString();
    assertTrue(logOutput.contains("req-invalid-mac-003"), "Log should contain the requestId");
    assertTrue(logOutput.contains("web-client"), "Log should contain the client");
    assertTrue(
        logOutput.contains("macAddress") || logOutput.contains("MAC address"),
        "Log should mention MAC address validation failure");
    assertTrue(logOutput.contains("HTTP_STATUS=400"), "Log should contain HTTP status 400");
  }

  @Test
  void should_LogValidationFailureDetails_When_EmptyWifiScanResults(CapturedOutput output)
      throws Exception {
    // Arrange
    String invalidRequest =
        """
                {
                    "wifiScanResults": [],
                    "client": "iot-device",
                    "requestId": "req-splunk-test-004"
                }
                """;

    // Act & Assert
    mockMvc
        .perform(
            post("/v1/wifi/position").contentType(MediaType.APPLICATION_JSON).content(invalidRequest))
        .andExpect(status().isBadRequest());

    // Verify ERROR log format
    String logOutput = output.toString();

    // Verify ERROR log contains all required information
    assertTrue(
        logOutput.contains("REQUEST_ID='req-splunk-test-004'"),
        "Log should contain requestId in quotes");
    assertTrue(
        logOutput.contains("CLIENT='iot-device'"), "Log should contain client in quotes");
    assertTrue(logOutput.contains("HTTP_STATUS=400"), "Log should contain HTTP status 400");
    assertTrue(
        logOutput.contains("Validation failure"), "Log should contain validation failure message");
    assertTrue(
        logOutput.contains("PATH='/v1/wifi/position'"), "Log should contain request path");
    assertTrue(
        logOutput.contains("VALIDATION_ERRORS="), "Log should contain validation errors section");
  }
}

