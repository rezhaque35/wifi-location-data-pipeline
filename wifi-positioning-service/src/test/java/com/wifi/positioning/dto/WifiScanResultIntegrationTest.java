package com.wifi.positioning.dto;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.repository.WifiAccessPointRepository;

/**
 * Integration tests for WifiScanResult validation through the HTTP API. Tests end-to-end behavior
 * including validation, default values, and error handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "stub-service"})
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WifiScanResult Integration Tests")
class WifiScanResultIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Mock private WifiAccessPointRepository accessPointRepository;

  @Autowired private ObjectMapper objectMapper;

  private static final String POSITIONING_ENDPOINT = "/v1/wifi/position";
  private static final String TEST_CLIENT = "integration-test-client";
  private static final String VALID_MAC = "00:11:22:33:44:55";

  @BeforeEach
  void setUp() {
    // Mock repository to return valid access points for successful positioning
    WifiAccessPoint testAp = createTestAccessPoint(VALID_MAC);
    when(accessPointRepository.findByMacAddress(VALID_MAC)).thenReturn(Optional.of(testAp));
    when(accessPointRepository.findByMacAddresses(anySet()))
        .thenAnswer(
            invocation -> {
              Map<String, WifiAccessPoint> result = new HashMap<>();
              result.put(VALID_MAC, testAp);
              return result;
            });
  }

  @Nested
  @DisplayName("Mandatory Field Validation Tests")
  class MandatoryFieldValidationTests {

    @Test
    @DisplayName("Should accept request with only mandatory fields (macAddress and signalStrength)")
    void shouldAcceptRequest_WithOnlyMandatoryFields() throws Exception {
      // Given
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      MvcResult result =
          mockMvc
              .perform(
                  post(POSITIONING_ENDPOINT)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.result").value("SUCCESS"))
              .andReturn();

      // Verify response contains position data
      String responseContent = result.getResponse().getContentAsString();
      JsonNode jsonResponse = objectMapper.readTree(responseContent);
      JsonNode wifiPosition = jsonResponse.get("wifiPosition");
      
      // Verify position calculation succeeded with mandatory fields only
      assert wifiPosition != null : "WifiPosition should not be null";
      assert wifiPosition.has("latitude") : "Should have latitude";
      assert wifiPosition.has("longitude") : "Should have longitude";
    }

    @Test
    @DisplayName("Should reject request when macAddress is missing")
    void shouldRejectRequest_WhenMacAddressIsMissing() throws Exception {
      // Given - Request without macAddress
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 2437\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then - Jakarta validation catches missing mandatory field
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("MAC address")));
    }

    @Test
    @DisplayName("Should reject request when macAddress is blank")
    void shouldRejectRequest_WhenMacAddressIsBlank() throws Exception {
      // Given - Request with blank macAddress
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \"\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 2437\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then - Jakarta validation catches blank mandatory field
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("MAC address")));
    }

    @Test
    @DisplayName("Should reject request when macAddress format is invalid")
    void shouldRejectRequest_WhenMacAddressFormatIsInvalid() throws Exception {
      // Given - Request with invalid MAC format
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \"invalid-mac-format\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 2437\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("Invalid MAC address format")));
    }

    @Test
    @DisplayName("Should reject request when signalStrength is missing")
    void shouldRejectRequest_WhenSignalStrengthIsMissing() throws Exception {
      // Given - Request without signalStrength
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"frequency\": 2437\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then - Jakarta validation catches missing mandatory field
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("Signal strength")));
    }

    @Test
    @DisplayName("Should reject request when signalStrength is below minimum")
    void shouldRejectRequest_WhenSignalStrengthBelowMinimum() throws Exception {
      // Given - Request with signal strength < -100 dBm
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -101.0,\n"
              + "      \"frequency\": 2437\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("at least -100 dBm")));
    }

    @Test
    @DisplayName("Should reject request when signalStrength is above maximum")
    void shouldRejectRequest_WhenSignalStrengthAboveMaximum() throws Exception {
      // Given - Request with signal strength > 0 dBm
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": 1.0,\n"
              + "      \"frequency\": 2437\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("at most 0 dBm")));
    }
  }

  @Nested
  @DisplayName("Default Value Tests")
  class DefaultValueTests {

    @Test
    @DisplayName("Should apply default frequency of 2400 MHz when frequency is not provided")
    void shouldApplyDefaultFrequency_WhenFrequencyNotProvided() throws Exception {
      // Given - Request without frequency field
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\",\n"
              + "  \"calculationDetail\": true\n"
              + "}";

      // When/Then - Request should succeed with default frequency
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result").value("SUCCESS"));
    }

    @Test
    @DisplayName("Should preserve provided frequency when explicitly set")
    void shouldPreserveProvidedFrequency_WhenExplicitlySet() throws Exception {
      // Given - Request with explicit frequency
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 5180\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then - Request should succeed with provided frequency
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result").value("SUCCESS"));
    }
  }

  @Nested
  @DisplayName("Optional Field Validation Tests")
  class OptionalFieldValidationTests {

    @Test
    @DisplayName("Should accept request without optional fields (frequency, ssid, linkSpeed, channelWidth)")
    void shouldAcceptRequest_WithoutOptionalFields() throws Exception {
      // Given - Minimal request
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result").value("SUCCESS"));
    }

    @Test
    @DisplayName("Should accept request with all optional fields")
    void shouldAcceptRequest_WithAllOptionalFields() throws Exception {
      // Given - Request with all optional fields
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 5180,\n"
              + "      \"ssid\": \"TestNetwork\",\n"
              + "      \"linkSpeed\": 866,\n"
              + "      \"channelWidth\": 80\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result").value("SUCCESS"));
    }

    @Test
    @DisplayName("Should reject request when frequency is below minimum")
    void shouldRejectRequest_WhenFrequencyBelowMinimum() throws Exception {
      // Given - Request with frequency < 2400 MHz
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 2399\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("at least 2400 MHz")));
    }

    @Test
    @DisplayName("Should reject request when frequency is above maximum")
    void shouldRejectRequest_WhenFrequencyAboveMaximum() throws Exception {
      // Given - Request with frequency > 6000 MHz
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 6001\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("at most 6000 MHz")));
    }

    @Test
    @DisplayName("Should reject request when channelWidth is below minimum")
    void shouldRejectRequest_WhenChannelWidthBelowMinimum() throws Exception {
      // Given - Request with channel width < 20 MHz
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 2437,\n"
              + "      \"channelWidth\": 19\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("at least 20 MHz")));
    }

    @Test
    @DisplayName("Should reject request when channelWidth is above maximum")
    void shouldRejectRequest_WhenChannelWidthAboveMaximum() throws Exception {
      // Given - Request with channel width > 160 MHz
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 2437,\n"
              + "      \"channelWidth\": 161\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("at most 160 MHz")));
    }

    @Test
    @DisplayName("Should reject request when linkSpeed is negative")
    void shouldRejectRequest_WhenLinkSpeedIsNegative() throws Exception {
      // Given - Request with negative link speed
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 2437,\n"
              + "      \"linkSpeed\": -1\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(containsString("non-negative")));
    }

    @Test
    @DisplayName("Should accept valid SSID with special characters")
    void shouldAcceptValidSsid_WithSpecialCharacters() throws Exception {
      // Given - Request with SSID containing special characters
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -65.0,\n"
              + "      \"frequency\": 2437,\n"
              + "      \"ssid\": \"Test-Network_2.4GHz (Main)\"\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result").value("SUCCESS"));
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should accept boundary value for signal strength at -100 dBm")
    void shouldAcceptBoundaryValue_ForSignalStrengthAtMinimum() throws Exception {
      // Given - Request with minimum signal strength
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": -100.0,\n"
              + "      \"frequency\": 2437\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should accept boundary value for signal strength at 0 dBm")
    void shouldAcceptBoundaryValue_ForSignalStrengthAtMaximum() throws Exception {
      // Given - Request with maximum signal strength
      String requestBody =
          "{\n"
              + "  \"wifiScanResults\": [\n"
              + "    {\n"
              + "      \"macAddress\": \""
              + VALID_MAC
              + "\",\n"
              + "      \"signalStrength\": 0.0,\n"
              + "      \"frequency\": 2437\n"
              + "    }\n"
              + "  ],\n"
              + "  \"client\": \""
              + TEST_CLIENT
              + "\",\n"
              + "  \"requestId\": \""
              + UUID.randomUUID()
              + "\"\n"
              + "}";

      // When/Then
      mockMvc
          .perform(
              post(POSITIONING_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should accept common channel width values (20, 40, 80, 160)")
    void shouldAcceptCommonChannelWidthValues() throws Exception {
      int[] commonWidths = {20, 40, 80, 160};

      for (int width : commonWidths) {
        // Given - Request with each common channel width
        String requestBody =
            "{\n"
                + "  \"wifiScanResults\": [\n"
                + "    {\n"
                + "      \"macAddress\": \""
                + VALID_MAC
                + "\",\n"
                + "      \"signalStrength\": -65.0,\n"
                + "      \"frequency\": 5180,\n"
                + "      \"channelWidth\": "
                + width
                + "\n"
                + "    }\n"
                + "  ],\n"
                + "  \"client\": \""
                + TEST_CLIENT
                + "\",\n"
                + "  \"requestId\": \""
                + UUID.randomUUID()
                + "\"\n"
                + "}";

        // When/Then - Each common width should be accepted
        mockMvc
            .perform(
                post(POSITIONING_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("SUCCESS"));
      }
    }
  }

  /** Helper method to create a test access point */
  private WifiAccessPoint createTestAccessPoint(String macAddress) {
    WifiAccessPoint ap = new WifiAccessPoint();
    ap.setMacAddress(macAddress);
    ap.setLatitude(37.7749);
    ap.setLongitude(-122.4194);
    ap.setAltitude(10.0);
    ap.setSsid("TestNetwork");
    ap.setConfidence(0.85);
    ap.setHorizontalAccuracy(10.0);
    ap.setVerticalAccuracy(5.0);
    ap.setFrequency(2437);
    ap.setVendor("Test-Vendor");
    ap.setGeohash("9q8yyk");
    ap.setStatus(WifiAccessPoint.STATUS_ACTIVE);
    return ap;
  }
}
