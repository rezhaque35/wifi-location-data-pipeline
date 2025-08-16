// com/wifi/positioning/integration/IntegrationTest.java
package com.wifi.positioning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.client.PositioningServiceClient;
import com.wifi.positioning.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the WiFi Positioning Integration Service.
 * Tests the full flow from request to response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PositioningServiceClient positioningServiceClient;

    @Test
    void testIntegrationReportEndpoint_ValidRequest() throws Exception {
        // Given
        IntegrationReportRequest request = createValidIntegrationRequest();
        String requestJson = objectMapper.writeValueAsString(request);
        
        // Mock successful positioning service response
        ClientResult mockResult = ClientResult.success(200, 
            createMockPositioningResponse(), 100L);
        when(positioningServiceClient.invoke(any())).thenReturn(mockResult);

        // When & Then
        mockMvc.perform(post("/api/integration/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.receivedAt").exists())
                .andExpect(jsonPath("$.processingMode").value("sync"))
                .andExpect(jsonPath("$.derivedRequest").exists())
                .andExpect(jsonPath("$.positioningService").exists())
                .andExpect(jsonPath("$.comparison").exists());
    }

    @Test
    void testIntegrationReportEndpoint_InvalidRequest() throws Exception {
        // Given - request with empty WiFi info
        IntegrationReportRequest request = createInvalidIntegrationRequest();
        String requestJson = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/integration/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void testIntegrationReportEndpoint_WithSourceResponse() throws Exception {
        // Given
        IntegrationReportRequest request = createValidIntegrationRequestWithSourceResponse();
        String requestJson = objectMapper.writeValueAsString(request);
        
        // Mock successful positioning service response
        ClientResult mockResult = ClientResult.success(200, 
            createMockPositioningResponse(), 100L);
        when(positioningServiceClient.invoke(any())).thenReturn(mockResult);

        // When & Then
        mockMvc.perform(post("/api/integration/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison").exists()) // Comparison object should exist
                .andExpect(jsonPath("$.comparison.scenario").exists()) // Enhanced scenario detection
                .andExpect(jsonPath("$.sourceResponse").exists())
                .andExpect(jsonPath("$.sourceResponse.success").value(true));
    }

    /**
     * Creates a valid integration request using test data MAC addresses.
     */
    private IntegrationReportRequest createValidIntegrationRequest() {
        // Create WiFi scan results using test data MAC addresses
        WifiInfo wifi1 = new WifiInfo();
        wifi1.setId("00:11:22:33:44:01");
        wifi1.setSignalStrength(-65.0);
        wifi1.setFrequency(2437);
        wifi1.setSsid("SingleAP_Test");

        WifiInfo wifi2 = new WifiInfo();
        wifi2.setId("00:11:22:33:44:02");
        wifi2.setSignalStrength(-70.0);
        wifi2.setFrequency(5180);
        wifi2.setSsid("DualAP_Test");

        // Create service request
        SvcReq svcReq = new SvcReq();
        svcReq.setClientId("integration-test-client");
        svcReq.setRequestId("integration-test-" + System.currentTimeMillis());
        svcReq.setWifiInfo(Arrays.asList(wifi1, wifi2));

        // Create service body
        SvcBody svcBody = new SvcBody();
        svcBody.setSvcReq(svcReq);

        // Create service header
        SvcHeader svcHeader = new SvcHeader();
        svcHeader.setAuthToken("test-token");

        // Create source request
        SampleInterfaceSourceRequest sourceRequest = new SampleInterfaceSourceRequest();
        sourceRequest.setSvcHeader(svcHeader);
        sourceRequest.setSvcBody(svcBody);

        // Create options
        IntegrationOptions options = new IntegrationOptions();
        options.setCalculationDetail(true);
        options.setProcessingMode("sync");

        // Create metadata
        IntegrationMetadata metadata = new IntegrationMetadata();
        metadata.setCorrelationId("integration-test-correlation");

        // Create integration request
        IntegrationReportRequest request = new IntegrationReportRequest();
        request.setSourceRequest(sourceRequest);
        request.setOptions(options);
        request.setMetadata(metadata);

        return request;
    }

    /**
     * Creates a valid integration request with source response for comparison.
     */
    private IntegrationReportRequest createValidIntegrationRequestWithSourceResponse() {
        IntegrationReportRequest request = createValidIntegrationRequest();

        // Add source response for comparison
        LocationInfo locationInfo = new LocationInfo();
        locationInfo.setLatitude(37.7749);
        locationInfo.setLongitude(-122.4194);
        locationInfo.setAccuracy(50.0);
        locationInfo.setConfidence(0.8);

        SourceResponse sourceResponse = new SourceResponse();
        sourceResponse.setSuccess(true);
        sourceResponse.setLocationInfo(locationInfo);
        sourceResponse.setRequestId(request.getSourceRequest().getSvcBody().getSvcReq().getRequestId());

        request.setSourceResponse(sourceResponse);

        return request;
    }

    /**
     * Creates an invalid integration request for testing validation.
     */
    private IntegrationReportRequest createInvalidIntegrationRequest() {
        // Create service request with empty WiFi info (should fail validation)
        SvcReq svcReq = new SvcReq();
        svcReq.setClientId("test-client");
        svcReq.setRequestId("test-request");
        svcReq.setWifiInfo(List.of()); // Empty list should fail validation

        SvcBody svcBody = new SvcBody();
        svcBody.setSvcReq(svcReq);

        SampleInterfaceSourceRequest sourceRequest = new SampleInterfaceSourceRequest();
        sourceRequest.setSvcBody(svcBody);

        IntegrationReportRequest request = new IntegrationReportRequest();
        request.setSourceRequest(sourceRequest);

        return request;
    }

    /**
     * Creates a mock positioning service response for testing.
     */
    private Object createMockPositioningResponse() {
        // Return a simple JSON object representing a successful positioning response
        return "{\"result\":\"SUCCESS\",\"wifiPosition\":{\"latitude\":37.7749,\"longitude\":-122.4194,\"accuracy\":50.0}}";
    }
    
    /**
     * Test configuration that provides a mock PositioningServiceClient bean.
     * This replaces the deprecated @MockBean annotation.
     */
    @TestConfiguration
    static class MockPositioningServiceConfiguration {
        
        @Bean
        @Primary
        public PositioningServiceClient positioningServiceClient() {
            return mock(PositioningServiceClient.class);
        }
    }
}
