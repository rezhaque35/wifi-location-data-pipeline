// wifi-positioning-integration-service/src/test/java/com/wifi/positioning/health/WifiPositioningServiceHealthIndicatorTest.java
package com.wifi.positioning.health;

import com.wifi.positioning.client.PositioningServiceClient;
import com.wifi.positioning.config.IntegrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WifiPositioningServiceHealthIndicatorTest {

    @Mock
    private PositioningServiceClient positioningServiceClient;

    @Mock
    private HealthCheckCacheService cacheService;

    @Mock
    private IntegrationProperties properties;

    @Mock
    private IntegrationProperties.Positioning positioningProperties;

    private WifiPositioningServiceHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        when(properties.getPositioning()).thenReturn(positioningProperties);
        when(positioningProperties.getBaseUrl()).thenReturn("http://localhost:8080/wifi-positioning-service");

        healthIndicator = new WifiPositioningServiceHealthIndicator(
            positioningServiceClient, cacheService, properties);
    }

    @Test
    void health_shouldReturnUpWhenPositioningServiceIsHealthy() {
        // Given
        PositioningServiceClient.HealthResult healthyResult = 
            PositioningServiceClient.HealthResult.healthy("health response", 100L, "UP");
        
        when(cacheService.getCachedOrRefresh(anyString(), any()))
            .thenReturn(healthyResult);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertTrue((Boolean) health.getDetails().get("connected"));
        assertEquals("UP", health.getDetails().get("positioningServiceStatus"));
        assertEquals("Positioning service is accessible and reports status: UP", 
            health.getDetails().get("connectivityReason"));
        assertEquals("WiFi positioning service is up and running (status: UP)", 
            health.getDetails().get("reason"));
        assertEquals(100L, health.getDetails().get("responseTimeMs"));
        assertEquals(200, health.getDetails().get("httpStatus"));
    }

    @Test
    void health_shouldReturnDownWhenPositioningServiceIsUnhealthy() {
        // Given
        PositioningServiceClient.HealthResult unhealthyResult = 
            PositioningServiceClient.HealthResult.unhealthy("Connection failed", 500, 200L, "DOWN");
        
        when(cacheService.getCachedOrRefresh(anyString(), any()))
            .thenReturn(unhealthyResult);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertFalse((Boolean) health.getDetails().get("connected"));
        assertEquals("DOWN", health.getDetails().get("positioningServiceStatus"));
        assertEquals("Positioning service is not accessible or reports unhealthy status: DOWN - Connection failed", 
            health.getDetails().get("connectivityReason"));
        assertEquals("WiFi positioning service is down (status: DOWN) - Connection failed", 
            health.getDetails().get("reason"));
        assertEquals(200L, health.getDetails().get("responseTimeMs"));
        assertEquals(500, health.getDetails().get("httpStatus"));
    }

    @Test
    void health_shouldReturnDownWhenCacheServiceThrowsException() {
        // Given
        when(cacheService.getCachedOrRefresh(anyString(), any()))
            .thenThrow(new RuntimeException("Cache service failed"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertFalse((Boolean) health.getDetails().get("connected"));
        assertEquals("Exception during health check", 
            health.getDetails().get("connectivityReason"));
        assertEquals("WiFi positioning service health check failed: Cache service failed", 
            health.getDetails().get("reason"));
        assertEquals("Cache service failed", health.getDetails().get("error"));
        assertEquals("RuntimeException", health.getDetails().get("errorType"));
    }

    @Test
    void health_shouldIncludeCorrectUrl() {
        // Given
        PositioningServiceClient.HealthResult healthyResult = 
            PositioningServiceClient.HealthResult.healthy("health response", 100L, "UP");
        
        when(cacheService.getCachedOrRefresh(anyString(), any()))
            .thenReturn(healthyResult);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals("http://localhost:8080/wifi-positioning-service", 
            health.getDetails().get("positioningServiceUrl"));
    }

    @Test
    void health_shouldIncludeTimestamp() {
        // Given
        PositioningServiceClient.HealthResult healthyResult = 
            PositioningServiceClient.HealthResult.healthy("health response", 100L, "UP");
        
        when(cacheService.getCachedOrRefresh(anyString(), any()))
            .thenReturn(healthyResult);

        long beforeCall = System.currentTimeMillis();

        // When
        Health health = healthIndicator.health();

        // Then
        long afterCall = System.currentTimeMillis();
        Long checkTimestamp = (Long) health.getDetails().get("checkTimestamp");
        
        assertNotNull(checkTimestamp);
        assertTrue(checkTimestamp >= beforeCall);
        assertTrue(checkTimestamp <= afterCall);
    }

    @Test
    void health_shouldReturnDownWhenPositioningServiceReportsDownStatus() {
        // Given - HTTP 200 but service reports DOWN status
        PositioningServiceClient.HealthResult downResult = 
            PositioningServiceClient.HealthResult.unhealthy("Service status: DOWN", 200, 150L, "DOWN", "{\"status\":\"DOWN\"}");
        
        when(cacheService.getCachedOrRefresh(anyString(), any()))
            .thenReturn(downResult);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertFalse((Boolean) health.getDetails().get("connected"));
        assertEquals("DOWN", health.getDetails().get("positioningServiceStatus"));
        assertEquals("Positioning service is not accessible or reports unhealthy status: DOWN - Service status: DOWN", 
            health.getDetails().get("connectivityReason"));
        assertEquals("WiFi positioning service is down (status: DOWN) - Service status: DOWN", 
            health.getDetails().get("reason"));
        assertEquals(150L, health.getDetails().get("responseTimeMs"));
        assertEquals(200, health.getDetails().get("httpStatus"));
        assertEquals("{\"status\":\"DOWN\"}", health.getDetails().get("positioningServiceResponse"));
    }

    @Test
    void health_shouldHandleNullErrorMessage() {
        // Given
        PositioningServiceClient.HealthResult resultWithNullError = 
            PositioningServiceClient.HealthResult.unhealthy(null, 503, 100L, "DOWN");
        
        when(cacheService.getCachedOrRefresh(anyString(), any()))
            .thenReturn(resultWithNullError);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertFalse((Boolean) health.getDetails().get("connected"));
        assertEquals("DOWN", health.getDetails().get("positioningServiceStatus"));
        assertEquals("Positioning service is not accessible or reports unhealthy status: DOWN", 
            health.getDetails().get("connectivityReason"));
        assertEquals("WiFi positioning service is down (status: DOWN)", 
            health.getDetails().get("reason"));
    }
}
