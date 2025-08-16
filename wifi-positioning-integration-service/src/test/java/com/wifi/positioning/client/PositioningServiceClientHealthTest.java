// wifi-positioning-integration-service/src/test/java/com/wifi/positioning/client/PositioningServiceClientHealthTest.java
package com.wifi.positioning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.config.IntegrationProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PositioningServiceClientHealthTest {

    private MockWebServer mockWebServer;
    private PositioningServiceClient client;
    private IntegrationProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Setup properties
        properties = new IntegrationProperties();
        IntegrationProperties.Positioning positioningProps = 
            new IntegrationProperties.Positioning();
        positioningProps.setBaseUrl(mockWebServer.url("/").toString().replaceAll("/$", ""));
        positioningProps.setPath("/api/positioning/calculate");
        positioningProps.setReadTimeoutMs(5000);
        properties.setPositioning(positioningProps);

        // Create client
        WebClient webClient = WebClient.builder()
            .build();

        client = new PositioningServiceClient(webClient, properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void checkHealth_shouldReturnHealthyWhenServiceReportsUp() {
        // Given
        String healthResponse = "{\"status\":\"UP\",\"details\":{\"db\":\"UP\"}}";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(healthResponse));

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertTrue(result.isHealthy());
        assertEquals("UP", result.getHealthStatus());
        assertEquals(200, result.getHttpStatus());
        assertNotNull(result.getResponse());
        assertTrue(result.getLatencyMs() >= 0);
    }

    @Test
    void checkHealth_shouldReturnUnhealthyWhenServiceReportsDown() {
        // Given
        String healthResponse = "{\"status\":\"DOWN\",\"details\":{\"db\":\"DOWN\"}}";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(healthResponse));

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("DOWN", result.getHealthStatus());
        assertEquals(200, result.getHttpStatus());
        assertEquals("Service status: DOWN", result.getErrorMessage());
        assertNotNull(result.getResponse());
    }

    @Test
    void checkHealth_shouldHandleSimpleStringResponse() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/plain")
            .setBody("UP"));

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertTrue(result.isHealthy());
        assertEquals("UP", result.getHealthStatus());
        assertEquals(200, result.getHttpStatus());
        assertEquals("UP", result.getResponse());
    }

    @Test
    void checkHealth_shouldHandleUnknownStatusInJson() {
        // Given
        String healthResponse = "{\"status\":\"UNKNOWN\",\"details\":{}}";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(healthResponse));

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("UNKNOWN", result.getHealthStatus());
        assertEquals(200, result.getHttpStatus());
        assertEquals("Service status: UNKNOWN", result.getErrorMessage());
    }

    @Test
    void checkHealth_shouldHandleEmptyResponse() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(""));

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("UNKNOWN", result.getHealthStatus());
        assertEquals(200, result.getHttpStatus());
        assertEquals("Service status: UNKNOWN", result.getErrorMessage());
    }

    @Test
    void checkHealth_shouldHandleInvalidJson() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("invalid json {"));

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("UNKNOWN", result.getHealthStatus());
        assertEquals(200, result.getHttpStatus());
        assertTrue(result.getErrorMessage().contains("Service status: UNKNOWN"));
    }

    @Test
    void checkHealth_shouldHandleHttpError() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(503)
            .setBody("Service Unavailable"));

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("DOWN", result.getHealthStatus());
        assertEquals(503, result.getHttpStatus());
        assertTrue(result.getErrorMessage().contains("HTTP 503"));
    }

    @Test
    void checkHealth_shouldHandleConnectionError() {
        // Given - shutdown server to simulate connection error
        try {
            mockWebServer.shutdown();
        } catch (IOException e) {
            // Ignore
        }

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertFalse(result.isHealthy());
        assertEquals("DOWN", result.getHealthStatus());
        assertNull(result.getHttpStatus());
        assertTrue(result.getErrorMessage().contains("Connection failed"));
    }

    @Test
    void checkHealth_shouldParseComplexHealthResponse() {
        // Given
        String healthResponse = "{" +
            "\"status\":\"UP\"," +
            "\"components\":{" +
                "\"db\":{\"status\":\"UP\",\"details\":{\"database\":\"H2\"}}," +
                "\"diskSpace\":{\"status\":\"UP\",\"details\":{\"free\":123456}}" +
            "}" +
        "}";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(healthResponse));

        // When
        PositioningServiceClient.HealthResult result = client.checkHealth();

        // Then
        assertTrue(result.isHealthy());
        assertEquals("UP", result.getHealthStatus());
        assertEquals(200, result.getHttpStatus());
        assertNotNull(result.getResponse());
        // Verify it's parsed as JSON
        assertTrue(result.getResponse().toString().contains("components"));
    }
}
