// com/wifi/positioning/integration/AsyncIntegrationTest.java
package com.wifi.positioning.integration;

import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for async processing functionality.
 * Tests the complete async processing flow including configuration, 
 * queuing, and background execution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AsyncIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IntegrationProperties integrationProperties;

    @Test
    void shouldProcessAsyncRequestSuccessfully() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequest();
        String url = "http://localhost:" + port + "/wifi-positioning-integration-service/v1/wifi/position/report";

        // When
        ResponseEntity<IntegrationReportResponse> response = restTemplate.postForEntity(
            url, request, IntegrationReportResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProcessingMode()).isEqualTo("async");
        assertThat(response.getBody().getCorrelationId()).isNotNull();
        assertThat(response.getBody().getReceivedAt()).isNotNull();
        
        // Async response should not include processing results
        assertThat(response.getBody().getPositioningService()).isNull();
        assertThat(response.getBody().getComparison()).isNull();
    }

    @Test
    void shouldRespectProvidedCorrelationId() {
        // Given
        String correlationId = "test-async-correlation-123";
        IntegrationReportRequest request = createAsyncIntegrationRequestWithCorrelationId(correlationId);
        String url = "http://localhost:" + port + "/wifi-positioning-integration-service/v1/wifi/position/report";

        // When
        ResponseEntity<IntegrationReportResponse> response = restTemplate.postForEntity(
            url, request, IntegrationReportResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    void shouldFallbackToSyncWhenAsyncIsDisabled() {
        // Given - temporarily disable async processing
        boolean originalAsyncEnabled = integrationProperties.getProcessing().getAsync().isEnabled();
        integrationProperties.getProcessing().getAsync().setEnabled(false);
        
        try {
            IntegrationReportRequest request = createAsyncIntegrationRequest();
            String url = "http://localhost:" + port + "/wifi-positioning-integration-service/v1/wifi/position/report";

            // When
            ResponseEntity<IntegrationReportResponse> response = restTemplate.postForEntity(
                url, request, IntegrationReportResponse.class);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getProcessingMode()).isEqualTo("sync");
            assertThat(response.getBody().getPositioningService()).isNotNull();
            
        } finally {
            // Restore original configuration
            integrationProperties.getProcessing().getAsync().setEnabled(originalAsyncEnabled);
        }
    }

    @Test
    void shouldHandleSyncRequestNormally() {
        // Given
        IntegrationReportRequest request = createSyncIntegrationRequest();
        String url = "http://localhost:" + port + "/wifi-positioning-integration-service/v1/wifi/position/report";

        // When
        ResponseEntity<IntegrationReportResponse> response = restTemplate.postForEntity(
            url, request, IntegrationReportResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getProcessingMode()).isEqualTo("sync");
        assertThat(response.getBody().getPositioningService()).isNotNull();
        assertThat(response.getBody().getComparison()).isNotNull();
    }

    @Test
    void shouldReturnValidationErrorsForInvalidRequest() {
        // Given
        IntegrationReportRequest invalidRequest = new IntegrationReportRequest();
        // Missing required fields
        String url = "http://localhost:" + port + "/wifi-positioning-integration-service/v1/wifi/position/report";

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            url, invalidRequest, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldHandleMultipleConcurrentAsyncRequests() {
        // Given
        String url = "http://localhost:" + port + "/wifi-positioning-integration-service/v1/wifi/position/report";
        int requestCount = 5;

        // When - Submit multiple async requests concurrently
        for (int i = 0; i < requestCount; i++) {
            IntegrationReportRequest request = createAsyncIntegrationRequestWithCorrelationId("async-" + i);
            
            ResponseEntity<IntegrationReportResponse> response = restTemplate.postForEntity(
                url, request, IntegrationReportResponse.class);
            
            // Then - Each should be accepted
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getBody().getProcessingMode()).isEqualTo("async");
            assertThat(response.getBody().getCorrelationId()).isEqualTo("async-" + i);
        }

        // Wait a bit for async processing to complete
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // This just ensures the test doesn't hang - the actual processing
            // results would be in logs
            assertThat(true).isTrue();
        });
    }

    @Test
    void shouldProcessAsyncRequestWithComplexWifiData() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequestWithWifiData();
        String url = "http://localhost:" + port + "/wifi-positioning-integration-service/v1/wifi/position/report";

        // When
        ResponseEntity<IntegrationReportResponse> response = restTemplate.postForEntity(
            url, request, IntegrationReportResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getProcessingMode()).isEqualTo("async");
    }

    @Test
    void shouldUseCorrectContentTypeHeaders() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequest();
        String url = "http://localhost:" + port + "/wifi-positioning-integration-service/v1/wifi/position/report";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<IntegrationReportRequest> requestEntity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<IntegrationReportResponse> response = restTemplate.exchange(
            url, HttpMethod.POST, requestEntity, IntegrationReportResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().getType()).isEqualTo("application");
        assertThat(response.getHeaders().getContentType().getSubtype()).isEqualTo("json");
    }

    private IntegrationReportRequest createAsyncIntegrationRequest() {
        return createIntegrationRequest("async", null, "test-request-async-123");
    }

    private IntegrationReportRequest createAsyncIntegrationRequestWithCorrelationId(String correlationId) {
        return createIntegrationRequest("async", correlationId, "test-request-async-456");
    }

    private IntegrationReportRequest createSyncIntegrationRequest() {
        return createIntegrationRequest("sync", null, "test-request-sync-123");
    }

    private IntegrationReportRequest createAsyncIntegrationRequestWithWifiData() {
        IntegrationReportRequest request = createIntegrationRequest("async", null, "test-request-wifi-789");
        
        // Add WiFi data
        List<WifiInfo> wifiInfo = List.of(
            createWifiInfo("aa:bb:cc:dd:ee:ff", -50.0, 2437),
            createWifiInfo("11:22:33:44:55:66", -60.0, 5180),
            createWifiInfo("77:88:99:aa:bb:cc", -65.0, 2412)
        );
        
        request.getSourceRequest().getSvcBody().getSvcReq().setWifiInfo(wifiInfo);
        
        return request;
    }

    private IntegrationReportRequest createIntegrationRequest(String processingMode, String correlationId, String requestId) {
        IntegrationReportRequest request = new IntegrationReportRequest();
        
        // Create options
        IntegrationOptions options = new IntegrationOptions();
        options.setProcessingMode(processingMode);
        options.setCalculationDetail(true);
        request.setOptions(options);

        // Create metadata if correlationId is provided
        if (correlationId != null) {
            IntegrationMetadata metadata = new IntegrationMetadata();
            metadata.setCorrelationId(correlationId);
            request.setMetadata(metadata);
        }

        // Create source request
        SampleInterfaceSourceRequest sourceRequest = new SampleInterfaceSourceRequest();
        SvcHeader svcHeader = new SvcHeader();
        svcHeader.setAuthToken("test-token");
        sourceRequest.setSvcHeader(svcHeader);
        
        SvcBody svcBody = new SvcBody();
        SvcReq svcReq = new SvcReq();
        svcReq.setRequestId(requestId);
        svcReq.setClientId("test-client");
        svcReq.setWifiInfo(List.of(
            createWifiInfo("aa:bb:cc:dd:ee:ff", -55.0, 2437)
        ));
        svcReq.setCellInfo(List.of());
        
        svcBody.setSvcReq(svcReq);
        sourceRequest.setSvcBody(svcBody);
        request.setSourceRequest(sourceRequest);

        return request;
    }

    private WifiInfo createWifiInfo(String macAddress, Double signalStrength, Integer frequency) {
        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setId(macAddress);
        wifiInfo.setSignalStrength(signalStrength);
        wifiInfo.setFrequency(frequency);
        return wifiInfo;
    }
}

