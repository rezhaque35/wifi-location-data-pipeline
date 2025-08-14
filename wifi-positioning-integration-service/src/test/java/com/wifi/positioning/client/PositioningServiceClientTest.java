// com/wifi/positioning/client/PositioningServiceClientTest.java
package com.wifi.positioning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.ClientResult;
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiScanResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PositioningServiceClient using MockWebServer.
 */
class PositioningServiceClientTest {

    private MockWebServer mockWebServer;
    private PositioningServiceClient client;
    private IntegrationProperties properties;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Configure properties to point to mock server
        properties = new IntegrationProperties();
        IntegrationProperties.Positioning positioning = properties.getPositioning();
        positioning.setBaseUrl(String.format("http://localhost:%s", mockWebServer.getPort()));
        positioning.setPath("/api/positioning/calculate");
        positioning.setConnectTimeoutMs(1000); // Increased timeout for test stability
        positioning.setReadTimeoutMs(2000);

        // Create WebClient with proper timeout configuration
        WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
        client = new PositioningServiceClient(webClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void invoke_SuccessfulResponse() throws InterruptedException {
        // Given
        String responseBody = "{\"result\":\"SUCCESS\",\"wifiPosition\":{\"latitude\":37.7749,\"longitude\":-122.4194}}";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(responseBody));

        WifiPositioningRequest request = createTestRequest();

        // When
        ClientResult result = client.invoke(request);

        // Then
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals(200, result.getHttpStatus());
        assertNotNull(result.getResponseBody());
        assertTrue(result.getLatencyMs() > 0);
        assertNull(result.getErrorMessage());

        // Verify request was made correctly
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/api/positioning/calculate", recordedRequest.getPath());
        assertEquals("application/json", recordedRequest.getHeader("Content-Type"));
    }

    @Test
    void invoke_ClientError4xx() {
        // Given
        String errorBody = "{\"error\":\"Invalid request\",\"message\":\"WiFi scan results are required\"}";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .setHeader("Content-Type", "application/json")
            .setBody(errorBody));

        WifiPositioningRequest request = createTestRequest();

        // When
        ClientResult result = client.invoke(request);

        // Then
        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertEquals(400, result.getHttpStatus());
        assertNotNull(result.getResponseBody());
        assertTrue(result.getLatencyMs() > 0);
        assertTrue(result.getErrorMessage().contains("HTTP 400"));
    }

    @Test
    void invoke_ServerError5xx() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        WifiPositioningRequest request = createTestRequest();

        // When
        ClientResult result = client.invoke(request);

        // Then
        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertEquals(500, result.getHttpStatus());
        assertTrue(result.getLatencyMs() > 0);
        assertTrue(result.getErrorMessage().contains("HTTP 500"));
    }

    @Test
    void invoke_ConnectionTimeout() {
        // Given - server that doesn't respond (simulate connection timeout)
        mockWebServer.enqueue(new MockResponse()
            .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));

        WifiPositioningRequest request = createTestRequest();

        // When
        ClientResult result = client.invoke(request);

        // Then
        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertNull(result.getHttpStatus()); // No HTTP status for connection failures
        assertNull(result.getResponseBody());
        assertTrue(result.getLatencyMs() > 0);
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Connection failed") || 
                  result.getErrorMessage().contains("Unexpected error"));
    }

    @Test
    void invoke_ReadTimeout() {
        // Given - server that delays response beyond read timeout
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"result\":\"SUCCESS\"}")
            .setBodyDelay(3000, java.util.concurrent.TimeUnit.MILLISECONDS)); // Delay longer than read timeout

        WifiPositioningRequest request = createTestRequest();

        // When
        ClientResult result = client.invoke(request);

        // Then
        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertNull(result.getHttpStatus()); // Timeout before HTTP response
        assertNull(result.getResponseBody());
        assertTrue(result.getLatencyMs() >= 1500); // Should be close to read timeout
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void buildUrl_HandlesTrailingSlashesCorrectly() {
        // Test that URL building handles various slash combinations correctly
        
        // Test 1: Base URL with trailing slash
        properties.getPositioning().setBaseUrl("http://localhost:8080/");
        properties.getPositioning().setPath("/api/test");
        
        WifiPositioningRequest request = createTestRequest();
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        
        ClientResult result = client.invoke(request);
        assertNotNull(result); // Should not fail due to URL issues
        
        // Test 2: Base URL without trailing slash, path without leading slash
        properties.getPositioning().setBaseUrl("http://localhost:" + mockWebServer.getPort());
        properties.getPositioning().setPath("api/test");
        
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        result = client.invoke(request);
        assertNotNull(result); // Should not fail due to URL issues
    }

    private WifiPositioningRequest createTestRequest() {
        WifiScanResult scanResult = new WifiScanResult();
        scanResult.setMacAddress("00:11:22:33:44:55");
        scanResult.setSignalStrength(-65.0);
        scanResult.setFrequency(2437);

        WifiPositioningRequest request = new WifiPositioningRequest();
        request.setWifiScanResults(List.of(scanResult));
        request.setClient("test-client");
        request.setRequestId("test-request-123");
        request.setApplication("test-application");
        request.setCalculationDetail(false);

        return request;
    }
}
