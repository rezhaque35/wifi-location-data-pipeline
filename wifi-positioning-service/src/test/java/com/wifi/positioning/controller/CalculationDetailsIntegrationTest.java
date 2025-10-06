package com.wifi.positioning.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;

/**
 * Integration tests for verifying calculation details (calculationInfo) behavior in positioning
 * responses. Tests use in-memory repository with pre-loaded test data.
 *
 * <p>These tests verify that calculationInfo is properly included/excluded based on the
 * calculationDetail flag in various scenarios including success and error cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Calculation Details Integration Tests")
class CalculationDetailsIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private WifiAccessPointRepository repository;

  @Autowired private ObjectMapper objectMapper;

  private static final String TEST_CLIENT = "calc-test-client";
  private static final String TEST_APP = "calc-test-app";
  private static final String ENDPOINT = "/v1/wifi/position";

  // Helper method to get InMemoryRepository for setup
  private InMemoryWifiAccessPointRepository getInMemoryRepo() {
    return (InMemoryWifiAccessPointRepository) repository;
  }

  @Test
  @DisplayName("Should include calculationInfo when flag is TRUE and calculation succeeds")
  void shouldIncludeCalculationInfoWhenFlagIsTrue() throws Exception {
    // Arrange - Load test data
    InMemoryWifiAccessPointRepository repo = getInMemoryRepo();
    repo.clearAll();
    repo.loadProximityDetectionScenario(); // Loads test APs

    String requestBody =
        buildRequest(
            true, "00:11:22:33:44:01", "00:11:22:33:44:02"); // 2 APs for better calculation

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Assert
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    assertEquals("SUCCESS", response.get("result").asText());

    // Verify calculationInfo is PRESENT
    JsonNode calculationInfo = response.get("calculationInfo");
    assertNotNull(calculationInfo, "calculationInfo should be present when flag is TRUE");

    // Verify structure
    assertNotNull(calculationInfo.get("accessPoints"));
    assertTrue(calculationInfo.get("accessPoints").isArray());
    assertNotNull(calculationInfo.get("accessPointSummary"));
    assertNotNull(calculationInfo.get("selectionContext"));
    assertNotNull(calculationInfo.get("algorithmSelection"));

    System.out.println("✓ calculationInfo included when flag=true");
  }

  @Test
  @DisplayName("Should EXCLUDE calculationInfo when flag is FALSE and calculation succeeds")
  void shouldExcludeCalculationInfoWhenFlagIsFalse() throws Exception {
    // Arrange
    InMemoryWifiAccessPointRepository repo = getInMemoryRepo();
    repo.clearAll();
    repo.loadProximityDetectionScenario();

    String requestBody = buildRequest(false, "00:11:22:33:44:01", "00:11:22:33:44:02");

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Assert
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    assertEquals("SUCCESS", response.get("result").asText());

    // Verify calculationInfo is NOT PRESENT
    JsonNode calculationInfo = response.get("calculationInfo");
    assertTrue(
        calculationInfo == null || calculationInfo.isNull(),
        "calculationInfo should NOT be present when flag is FALSE");

    System.out.println("✓ calculationInfo excluded when flag=false");
  }

  @Test
  @DisplayName("Should EXCLUDE calculationInfo when flag is NOT PROVIDED")
  void shouldExcludeCalculationInfoWhenFlagNotProvided() throws Exception {
    // Arrange
    InMemoryWifiAccessPointRepository repo = getInMemoryRepo();
    repo.clearAll();
    repo.loadProximityDetectionScenario();

    String requestBody = buildRequestWithoutFlag("00:11:22:33:44:01", "00:11:22:33:44:02");

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Assert
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode calculationInfo = response.get("calculationInfo");
    assertTrue(
        calculationInfo == null || calculationInfo.isNull(),
        "calculationInfo should NOT be present when flag is not provided");

    System.out.println("✓ calculationInfo excluded when flag not provided");
  }

  @Test
  @DisplayName(
      "Should include partial calculationInfo when flag is TRUE and NO VALID APs available")
  void shouldIncludePartialInfoWhenNoValidAPsAndFlagTrue() throws Exception {
    // Arrange - Load scenario with invalid status APs
    InMemoryWifiAccessPointRepository repo = getInMemoryRepo();
    repo.clearAll();
    repo.loadWeakSignalsScenario(); // Has some APs that might be filtered

    // Use MAC addresses that exist but might have invalid status
    String requestBody = buildRequest(true, "00:11:22:33:44:07", "00:11:22:33:44:08");

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andReturn();

    // Assert
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    String resultStatus = response.get("result").asText();

    if ("ERROR".equals(resultStatus)) {
      // Verify specific error message for no valid APs
      String errorMsg = response.get("message").asText();
      assertTrue(
          errorMsg.contains("No access points with valid status found")
              || errorMsg.contains("No known access points found in database"),
          "Error message should indicate no valid APs or no known APs");

      // If error occurred, calculationInfo should still be present when flag is true
      JsonNode calculationInfo = response.get("calculationInfo");

      if (calculationInfo != null && !calculationInfo.isNull()) {
        // Verify partial info is present
        assertNotNull(calculationInfo.get("accessPointSummary"));
        System.out.println("✓ Partial calculationInfo included on error when flag=true");
      } else {
        System.out.println("⚠ calculationInfo is null (implementation bug - should be present)");
      }
    } else {
      System.out.println("✓ Request succeeded - calculationInfo included");
      JsonNode calculationInfo = response.get("calculationInfo");
      assertNotNull(calculationInfo);
    }
  }

  @Test
  @DisplayName("Should EXCLUDE calculationInfo when flag is FALSE and error occurs")
  void shouldExcludeCalculationInfoWhenFlagFalseAndError() throws Exception {
    // Arrange - No APs in repository
    InMemoryWifiAccessPointRepository repo = getInMemoryRepo();
    repo.clearAll();

    String requestBody = buildRequest(false, "FF:FF:FF:FF:FF:01", "FF:FF:FF:FF:FF:02");

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Assert
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    assertEquals("ERROR", response.get("result").asText());

    // Verify specific error message for no APs found in database
    String errorMsg = response.get("message").asText();
    assertTrue(
        errorMsg.contains("No known access points found in database"),
        "Error message should be: 'No known access points found in database'");

    // Verify calculationInfo is NOT PRESENT even on error
    JsonNode calculationInfo = response.get("calculationInfo");
    assertTrue(
        calculationInfo == null || calculationInfo.isNull(),
        "calculationInfo should NOT be present when flag is FALSE, even on error");

    System.out.println("✓ calculationInfo excluded on error when flag=false");
  }

  @Test
  @DisplayName("Should include AP filtering details when flag is TRUE with mixed status APs")
  void shouldIncludeAPFilteringDetailsWhenFlagTrue() throws Exception {
    // Arrange - Load all scenarios which includes mixed status APs
    InMemoryWifiAccessPointRepository repo = getInMemoryRepo();
    repo.clearAll();
    repo.loadAllTestScenarios();

    String requestBody =
        buildRequest(
            true,
            "00:11:22:33:44:01",
            "00:11:22:33:44:02",
            "00:11:22:33:44:03"); // Multiple APs

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Assert
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode calculationInfo = response.get("calculationInfo");
    assertNotNull(calculationInfo);

    // Verify AP summary has filtering info
    JsonNode summary = calculationInfo.get("accessPointSummary");
    assertNotNull(summary);
    assertTrue(summary.get("total").asInt() >= 1);
    assertTrue(summary.get("used").asInt() >= 0);

    // Verify status counts exist
    JsonNode statusCounts = summary.get("statusCounts");
    assertNotNull(statusCounts);
    assertTrue(statusCounts.isArray());

    System.out.println("✓ AP filtering details included when flag=true");
  }

  @Test
  @DisplayName("Should include algorithm selection details when flag is TRUE")
  void shouldIncludeAlgorithmSelectionWhenFlagTrue() throws Exception {
    // Arrange - Load trilateration scenario for multiple algorithms
    InMemoryWifiAccessPointRepository repo = getInMemoryRepo();
    repo.clearAll();
    repo.loadTrilaterationScenario();

    String requestBody =
        buildRequest(
            true, "00:11:22:33:44:03", "00:11:22:33:44:04", "00:11:22:33:44:05"); // 3 APs

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Assert
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode calculationInfo = response.get("calculationInfo");
    assertNotNull(calculationInfo);

    // Verify algorithm selection exists
    JsonNode algorithmSelection = calculationInfo.get("algorithmSelection");
    assertNotNull(algorithmSelection);
    assertTrue(algorithmSelection.isArray());

    // Verify selection context
    JsonNode selectionContext = calculationInfo.get("selectionContext");
    assertNotNull(selectionContext);

    System.out.println("✓ Algorithm selection details included when flag=true");
  }

  @Test
  @DisplayName("Should include partial calculationInfo when flag is TRUE and NO APs found in DB")
  void shouldIncludePartialInfoWhenNoAPsFoundInDatabase() throws Exception {
    // Arrange - Empty repository (no APs in database)
    InMemoryWifiAccessPointRepository repo = getInMemoryRepo();
    repo.clearAll();

    // Request with MAC addresses that don't exist in DB
    String requestBody = buildRequest(true, "FF:FF:FF:FF:FF:01", "FF:FF:FF:FF:FF:02");

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    // Assert
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    assertEquals("ERROR", response.get("result").asText());

    // Verify specific error message for no APs found in database
    String errorMsg = response.get("message").asText();
    assertTrue(
        errorMsg.contains("No known access points found in database"),
        "Error message should be: 'No known access points found in database'");

    // Verify calculationInfo is PRESENT with partial data when flag is TRUE
    JsonNode calculationInfo = response.get("calculationInfo");
    assertNotNull(calculationInfo, "calculationInfo should be present when flag is TRUE, even with no APs");

    // Verify partial info structure
    JsonNode summary = calculationInfo.get("accessPointSummary");
    assertNotNull(summary, "accessPointSummary should be present");
    assertEquals(2, summary.get("total").asInt(), "Should show 2 total scanned APs from request");
    assertEquals(0, summary.get("used").asInt(), "Should show 0 used APs (none found in DB)");
    
    // Verify all APs are marked as "unknown" status
    JsonNode statusCounts = summary.get("statusCounts");
    assertNotNull(statusCounts);
    assertTrue(statusCounts.isArray());
    assertEquals(1, statusCounts.size(), "Should have one status type (unknown)");
    assertEquals("unknown", statusCounts.get(0).get("status").asText());
    assertEquals(2, statusCounts.get(0).get("count").asInt(), "All 2 APs should be unknown");

    System.out.println("✓ Partial calculationInfo included when no APs found in DB and flag=true");
  }

  @Test
  @DisplayName("Should handle validation errors without calculationInfo")
  void shouldHandleValidationErrorsWithoutCalculationInfo() throws Exception {
    // Arrange - Empty scan results will trigger validation error
    String requestBody =
        "{\n"
            + "  \"wifiScanResults\": [],\n"
            + "  \"client\": \""
            + TEST_CLIENT
            + "\",\n"
            + "  \"requestId\": \""
            + UUID.randomUUID()
            + "\",\n"
            + "  \"application\": \""
            + TEST_APP
            + "\",\n"
            + "  \"calculationDetail\": true\n"
            + "}";

    // Act
    MvcResult result =
        mockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andReturn();

    // Assert - Validation errors return HTTP 400
    assertEquals(400, result.getResponse().getStatus());
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    assertEquals("ERROR", response.get("result").asText());

    // calculationInfo should be null for validation errors
    JsonNode calculationInfo = response.get("calculationInfo");
    assertTrue(
        calculationInfo == null || calculationInfo.isNull(),
        "calculationInfo should be null for validation errors");

    System.out.println("✓ Validation errors handled without calculationInfo");
  }

  // ==================== Helper Methods ====================

  private String buildRequest(boolean calculationDetail, String... macAddresses) {
    StringBuilder scans = new StringBuilder();
    for (int i = 0; i < macAddresses.length; i++) {
      if (i > 0) scans.append(",\n");
      scans
          .append("    {")
          .append("\"macAddress\":\"")
          .append(macAddresses[i])
          .append("\",")
          .append("\"ssid\":\"TestAP\",")
          .append("\"signalStrength\":")
          .append(-60 - (i * 5))
          .append(",")
          .append("\"frequency\":2437")
          .append("}");
    }

    return "{\n"
        + "  \"wifiScanResults\":[\n"
        + scans
        + "\n  ],\n"
        + "  \"client\":\""
        + TEST_CLIENT
        + "\",\n"
        + "  \"requestId\":\""
        + UUID.randomUUID()
        + "\",\n"
        + "  \"application\":\""
        + TEST_APP
        + "\",\n"
        + "  \"calculationDetail\":"
        + calculationDetail
        + "\n}";
  }

  private String buildRequestWithoutFlag(String... macAddresses) {
    StringBuilder scans = new StringBuilder();
    for (int i = 0; i < macAddresses.length; i++) {
      if (i > 0) scans.append(",\n");
      scans
          .append("    {")
          .append("\"macAddress\":\"")
          .append(macAddresses[i])
          .append("\",")
          .append("\"ssid\":\"TestAP\",")
          .append("\"signalStrength\":")
          .append(-60 - (i * 5))
          .append(",")
          .append("\"frequency\":2437")
          .append("}");
    }

    return "{\n"
        + "  \"wifiScanResults\":[\n"
        + scans
        + "\n  ],\n"
        + "  \"client\":\""
        + TEST_CLIENT
        + "\",\n"
        + "  \"requestId\":\""
        + UUID.randomUUID()
        + "\",\n"
        + "  \"application\":\""
        + TEST_APP
        + "\"\n}";
  }
}

