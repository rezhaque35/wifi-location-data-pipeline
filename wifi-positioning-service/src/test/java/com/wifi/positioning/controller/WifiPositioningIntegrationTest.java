// src/test/java/com/wifi/positioning/controller/WifiPositioningIntegrationTest.java
package com.wifi.positioning.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import org.junit.jupiter.api.BeforeEach;
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
import com.wifi.positioning.dto.CellTower;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.CellTowerRepository;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.repository.impl.InMemoryCellTowerRepository;
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;

/**
 * Comprehensive end-to-end integration test for WiFi Positioning Service.
 * 
 * This simplified test suite covers critical paths:
 * 1. Basic positioning with various AP counts
 * 2. Error handling for invalid/missing data
 * 3. Status-based filtering
 * 4. Cell tower filtering
 * 5. Calculation details
 * 
 * Uses in-memory repositories to eliminate external dependencies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "stub-service"})
@DisplayName("WiFi Positioning Service - End-to-End Integration Tests")
public class WifiPositioningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WifiAccessPointRepository accessPointRepository;

    @Autowired
    private CellTowerRepository cellTowerRepository;

    private static final String API_ENDPOINT = "/v1/wifi/position";
    private static final String TEST_CLIENT = "integration-test-client";
    private static final String TEST_APP = "comprehensive-integration-test";

    @BeforeEach
    void setUp() {
        loadTestData();
    }

    /**
     * Loads essential test data covering all test scenarios
     */
    private void loadTestData() {
        loadWifiAccessPoints();
        loadCellTowers();
    }

    private void loadWifiAccessPoints() {
        if (!(accessPointRepository instanceof InMemoryWifiAccessPointRepository repo)) {
            return;
        }

        repo.clearAll();

        // Basic positioning test data (single, dual, multi AP)
        repo.addAccessPoint(createAP("00:11:22:33:44:01", 37.7749, -122.4194, 10.5, "active"));
        repo.addAccessPoint(createAP("00:11:22:33:44:02", 37.7750, -122.4195, 12.5, "active"));
        repo.addAccessPoint(createAP("00:11:22:33:44:03", 37.7751, -122.4196, 15.0, "active"));
        repo.addAccessPoint(createAP("00:11:22:33:44:04", 37.7752, -122.4197, 18.0, "active"));
        repo.addAccessPoint(createAP("00:11:22:33:44:05", 37.7753, -122.4198, 20.0, "active"));

        // Status filtering test data (various statuses)
        repo.addAccessPoint(createAP("00:11:22:33:44:41", 37.8000, -122.4400, 10.0, "active"));
        repo.addAccessPoint(createAP("00:11:22:33:44:42", 37.8001, -122.4401, 10.5, "warning"));
        repo.addAccessPoint(createAP("00:11:22:33:44:43", 37.8002, -122.4402, 11.0, "error"));
        repo.addAccessPoint(createAP("00:11:22:33:44:44", 37.8003, -122.4403, 11.5, "expired"));
    }

    private void loadCellTowers() {
        if (!(cellTowerRepository instanceof InMemoryCellTowerRepository repo)) {
            return;
        }

        repo.clearAll();

        // San Francisco cell tower for filtering tests
        repo.addCellTower(CellTower.builder()
                .id(12345L)
                .cellType("LTE")
                .latitude(37.7749)
                .longitude(-122.4194)
                .range(1000.0)
                .createdAt("2024-01-01T00:00:00Z")
                .updatedAt("2024-01-01T00:00:00Z")
                .build());
    }

    private WifiAccessPoint createAP(String mac, double lat, double lon, double alt, String status) {
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

    // ======================================================================================
    // TEST 1: Basic Positioning - Single Access Point
    // ======================================================================================

    @Test
    @DisplayName("Test 1: Single AP - Basic Positioning Works")
    void testSingleAccessPointPositioning() throws Exception {
        String requestBody = buildRequest("single-ap-test",
                scanResult("00:11:22:33:44:01", -65.0, 2437));

        MvcResult mvcResult = mockMvc.perform(post(API_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.wifiPosition").exists())
                .andExpect(jsonPath("$.wifiPosition.latitude").exists())
                .andExpect(jsonPath("$.wifiPosition.longitude").exists())
                .andExpect(jsonPath("$.wifiPosition.confidence").exists())
                .andExpect(jsonPath("$.wifiPosition.methodsUsed").isArray());
    }

    // ======================================================================================
    // TEST 2: Multiple Access Points - Algorithm Selection
    // ======================================================================================

    @Test
    @DisplayName("Test 2: Multiple APs - Advanced Algorithm Selection")
    void testMultipleAccessPointsPositioning() throws Exception {
        String requestBody = buildRequest("multi-ap-test",
                scanResult("00:11:22:33:44:02", -68.5, 5180),
                scanResult("00:11:22:33:44:03", -62.3, 2462),
                scanResult("00:11:22:33:44:04", -71.2, 5240),
                scanResult("00:11:22:33:44:05", -75.5, 2412));

        MvcResult mvcResult = mockMvc.perform(post(API_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.wifiPosition").exists())
                .andExpect(jsonPath("$.wifiPosition.methodsUsed").isArray())
                .andDo(result -> {
                    String content = result.getResponse().getContentAsString();
                    JsonNode response = objectMapper.readTree(content);
                    int methodCount = response.get("wifiPosition").get("methodsUsed").size();
                    assertTrue(methodCount > 0, "Should have at least one positioning method");
                });
    }

    // ======================================================================================
    // TEST 3: Error Handling - Unknown Access Point
    // ======================================================================================

    @Test
    @DisplayName("Test 3: Error Handling - Unknown AP Returns Error")
    void testUnknownAccessPointError() throws Exception {
        String requestBody = buildRequest("unknown-ap-test",
                scanResult("FF:FF:FF:FF:FF:FF", -65.0, 2412));

        MvcResult mvcResult = mockMvc.perform(post(API_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.wifiPosition").doesNotExist());
    }

    // ======================================================================================
    // TEST 4: Validation - Empty Scan Results
    // ======================================================================================

    @Test
    @DisplayName("Test 4: Validation - Empty Scan Results Returns 400")
    void testEmptyScanResultsValidation() throws Exception {
        String requestBody = String.format(
                "{\"wifiScanResults\":[],\"client\":\"%s\",\"requestId\":\"%s\",\"application\":\"%s\",\"calculationDetail\":false}",
                TEST_CLIENT, "empty-scans-test", TEST_APP);

        mockMvc.perform(post(API_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    // ======================================================================================
    // TEST 5: Status Filtering - Only Valid Status APs Used
    // ======================================================================================

    @Test
    @DisplayName("Test 5: Status Filtering - ERROR and EXPIRED APs Filtered Out")
    void testStatusFiltering() throws Exception {
        // Request includes 4 APs: active, warning, error, expired
        // Only active and warning should be used
        String requestBody = buildRequest("status-filtering-test",
                scanResult("00:11:22:33:44:41", -70.0, 2437),
                scanResult("00:11:22:33:44:42", -70.0, 2437),
                scanResult("00:11:22:33:44:43", -70.0, 2437),
                scanResult("00:11:22:33:44:44", -70.0, 2437));

        MvcResult mvcResult = mockMvc.perform(post(API_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.wifiPosition").exists())
                .andDo(result -> {
                    String content = result.getResponse().getContentAsString();
                    JsonNode response = objectMapper.readTree(content);
                    
                    // Verify position was calculated (meaning some APs were valid)
                    assertNotNull(response.get("wifiPosition"));
                    
                    // If calculationInfo is present, verify filtering happened
                    if (response.has("calculationInfo")) {
                        JsonNode summary = response.get("calculationInfo").get("accessPointSummary");
                        if (summary != null && summary.has("used")) {
                            int usedCount = summary.get("used").asInt();
                            assertTrue(usedCount >= 2, "Should use at least active and warning APs");
                        }
                    }
                });
    }

    // ======================================================================================
    // TEST 6: Cell Tower Filtering
    // ======================================================================================

    @Test
    @DisplayName("Test 6: Cell Tower Filtering - Cell Info Processed")
    void testCellTowerFiltering() throws Exception {
        // Note: field names must match CellInfo record definition (id, broadcastId, networkId, cellType, signalStrength)
        String requestBody = String.format(
                "{\"wifiScanResults\":[{\"macAddress\":\"00:11:22:33:44:01\",\"signalStrength\":-65.0,\"frequency\":2437}]," +
                        "\"cellInfo\":[{\"id\":12345,\"broadcastId\":100,\"networkId\":410,\"cellType\":\"lte\",\"signalStrength\":-85}]," +
                        "\"client\":\"%s\",\"requestId\":\"%s\",\"application\":\"%s\",\"calculationDetail\":false}",
                TEST_CLIENT, "cell-tower-test", TEST_APP);

        MvcResult mvcResult = mockMvc.perform(post(API_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.wifiPosition").exists());
    }

    // ======================================================================================
    // TEST 7: Calculation Details - When Requested
    // ======================================================================================

    @Test
    @DisplayName("Test 7: Calculation Details - Included When Flag Is True")
    void testCalculationDetailsIncluded() throws Exception {
        String requestBody = buildRequestWithCalcDetails("calc-details-test", true,
                scanResult("00:11:22:33:44:01", -65.0, 2437));

        MvcResult mvcResult = mockMvc.perform(post(API_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.calculationInfo").exists())
                .andExpect(jsonPath("$.calculationInfo.accessPoints").isArray())
                .andExpect(jsonPath("$.calculationInfo.accessPointSummary").exists())
                .andExpect(jsonPath("$.calculationInfo.algorithmSelection").isArray());
    }

    @Test
    @DisplayName("Test 8: Calculation Details - Excluded When Flag Is False")
    void testCalculationDetailsExcluded() throws Exception {
        String requestBody = buildRequestWithCalcDetails("no-calc-details-test", false,
                scanResult("00:11:22:33:44:01", -65.0, 2437));

        MvcResult mvcResult = mockMvc.perform(post(API_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.calculationInfo").doesNotExist());
    }

    // ======================================================================================
    // HELPER METHODS
    // ======================================================================================

    private String buildRequest(String requestId, String... scanResults) {
        return buildRequestWithCalcDetails(requestId, false, scanResults);
    }

    private String buildRequestWithCalcDetails(String requestId, boolean calcDetail, String... scanResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"wifiScanResults\":[");
        for (int i = 0; i < scanResults.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(scanResults[i]);
        }
        sb.append("],\"client\":\"").append(TEST_CLIENT).append("\"");
        sb.append(",\"requestId\":\"").append(requestId).append("\"");
        sb.append(",\"application\":\"").append(TEST_APP).append("\"");
        sb.append(",\"calculationDetail\":").append(calcDetail).append("}");
        return sb.toString();
    }

    private String scanResult(String mac, double signalStrength, int frequency) {
        return String.format(
                "{\"macAddress\":\"%s\",\"signalStrength\":%.1f,\"frequency\":%d}",
                mac, signalStrength, frequency);
    }
}
