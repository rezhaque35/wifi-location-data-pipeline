package com.wifi.positioning.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;

/**
 * Integration tests for PositioningController to verify correct algorithm names in the HTTP
 * responses. Tests end-to-end flow from HTTP request to repository and back to response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PositioningControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Mock private WifiAccessPointRepository accessPointRepository;

  @Autowired private ObjectMapper objectMapper;

  private static final String TEST_CLIENT = "integration-test-client";
  private static final String TEST_APPLICATION = "integration-test-app";

  @BeforeEach
  void setUp() {
    // Set up mock responses
    WifiAccessPoint ap1 =
        createTestAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.5, "SingleAP_Test");
    WifiAccessPoint ap2 =
        createTestAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.5, "DualAP_Test");
    WifiAccessPoint ap3 = createTestAP("00:11:22:33:44:03", 37.7751, -122.4196, 15.0, "TriAP_Test");
    WifiAccessPoint ap4 =
        createTestAP("00:11:22:33:44:04", 37.7752, -122.4197, 18.0, "MultiAP_Test");

    // Mock individual lookups
    when(accessPointRepository.findByMacAddress("00:11:22:33:44:01")).thenReturn(Optional.of(ap1));
    when(accessPointRepository.findByMacAddress("00:11:22:33:44:02")).thenReturn(Optional.of(ap2));
    when(accessPointRepository.findByMacAddress("00:11:22:33:44:03")).thenReturn(Optional.of(ap3));
    when(accessPointRepository.findByMacAddress("00:11:22:33:44:04")).thenReturn(Optional.of(ap4));

    // Mock batch lookups - this is crucial for the integration test
    when(accessPointRepository.findByMacAddresses(org.mockito.ArgumentMatchers.anySet()))
        .thenAnswer(
            invocation -> {
              java.util.Set<String> macAddresses = invocation.getArgument(0);
              java.util.Map<String, WifiAccessPoint> result = new java.util.HashMap<>();

              if (macAddresses.contains("00:11:22:33:44:01")) {
                result.put("00:11:22:33:44:01", ap1);
              }
              if (macAddresses.contains("00:11:22:33:44:02")) {
                result.put("00:11:22:33:44:02", ap2);
              }
              if (macAddresses.contains("00:11:22:33:44:03")) {
                result.put("00:11:22:33:44:03", ap3);
              }
              if (macAddresses.contains("00:11:22:33:44:04")) {
                result.put("00:11:22:33:44:04", ap4);
              }

              return result;
            });
  }

  @Test
  @DisplayName("Single AP Test - HTTP Response should contain valid methods used")
  void singleAPHttpResponseShouldContainMethodsUsed() throws Exception {
    // Create test payload
    String requestBody =
        "{\n"
            + "  \"wifiScanResults\": [\n"
            + "    {\n"
            + "      \"macAddress\": \"00:11:22:33:44:01\",\n"
            + "      \"ssid\": \"SingleAP_Test\",\n"
            + "      \"signalStrength\": -65.0,\n"
            + "      \"frequency\": 2437\n"
            + "    }\n"
            + "  ],\n"
            + "  \"client\": \""
            + TEST_CLIENT
            + "\",\n"
            + "  \"requestId\": \""
            + UUID.randomUUID()
            + "\",\n"
            + "  \"application\": \""
            + TEST_APPLICATION
            + "\"\n"
            + "}";

    // Send request and verify response
    MvcResult result =
        mockMvc
            .perform(
                post("/api/positioning/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Parse response
    JsonNode jsonResponse = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode wifiPosition = jsonResponse.get("wifiPosition");

    // Verify methodsUsed is an array that contains at least one algorithm
    assertNotNull(wifiPosition);
    assertTrue(wifiPosition.get("methodsUsed").isArray());
    assertTrue(wifiPosition.get("methodsUsed").size() > 0);

    // Print the actual algorithms used for debugging purposes
    System.out.println("Methods used: " + wifiPosition.get("methodsUsed").toString());
  }

  @Test
  @DisplayName(
      "Multiple APs Test - HTTP Response should contain correct methods used based on count")
  void multipleAPsHttpResponseShouldUseCorrectMethods() throws Exception {
    // Create test payload with 3 APs that are collinear, leading to weighted_centroid algorithm
    // selection
    String requestBody =
        "{\n"
            + "  \"wifiScanResults\": [\n"
            + "    {\n"
            + "      \"macAddress\": \"00:11:22:33:44:02\",\n"
            + "      \"ssid\": \"TriAP_Test\",\n"
            + "      \"signalStrength\": -68.5,\n"
            + "      \"frequency\": 5180\n"
            + "    },\n"
            + "    {\n"
            + "      \"macAddress\": \"00:11:22:33:44:03\",\n"
            + "      \"ssid\": \"TriAP_Test\",\n"
            + "      \"signalStrength\": -62.3,\n"
            + "      \"frequency\": 2462\n"
            + "    },\n"
            + "    {\n"
            + "      \"macAddress\": \"00:11:22:33:44:04\",\n"
            + "      \"ssid\": \"TriAP_Test\",\n"
            + "      \"signalStrength\": -71.2,\n"
            + "      \"frequency\": 5240\n"
            + "    }\n"
            + "  ],\n"
            + "  \"client\": \""
            + TEST_CLIENT
            + "\",\n"
            + "  \"requestId\": \""
            + UUID.randomUUID()
            + "\",\n"
            + "  \"application\": \""
            + TEST_APPLICATION
            + "\"\n"
            + "}";

    // Send request and verify response
    MvcResult result =
        mockMvc
            .perform(
                post("/api/positioning/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Parse response
    JsonNode jsonResponse = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode wifiPosition = jsonResponse.get("wifiPosition");

    // Verify methods used array contains expected algorithm names
    assertNotNull(wifiPosition);
    assertTrue(wifiPosition.get("methodsUsed").isArray());
    String methodsUsed = wifiPosition.get("methodsUsed").toString();
    assertTrue(methodsUsed.contains("weighted_centroid") || methodsUsed.contains("rssiratio"));
  }

  /** Helper method to create a test access point */
  private WifiAccessPoint createTestAP(
      String macAddress, double lat, double lon, double alt, String ssid) {
    WifiAccessPoint ap = new WifiAccessPoint();
    ap.setMacAddress(macAddress);
    ap.setLatitude(lat);
    ap.setLongitude(lon);
    ap.setAltitude(alt);
    ap.setSsid(ssid);
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
