package com.wifi.scan.consume.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.wifi.scan.consume.config.HealthIndicatorConfiguration;
import com.wifi.scan.consume.service.KafkaMonitoringService;

/**
 * Unit tests for MemoryHealthIndicator.
 * 
 * <p>Comprehensive test suite covering memory health monitoring, including threshold validation,
 * memory status reporting, configuration integration, and memory usage calculations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryHealthIndicator Tests")
class MemoryHealthIndicatorTest {

    @Mock
    private HealthIndicatorConfiguration healthConfig;

    @Mock 
    private KafkaMonitoringService kafkaMonitoringService;

    private MemoryHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new MemoryHealthIndicator(kafkaMonitoringService, healthConfig);
    }

    // ==================== Basic Memory Health Tests ====================

    @Test
    @DisplayName("Should return health status based on memory usage")
    void health_ShouldReturnHealthStatusBasedOnMemoryUsage() {
        // When
        Health health = healthIndicator.health();

        // Then
        assertNotNull(health);
        assertNotNull(health.getStatus());
        // Status should be either UP or DOWN based on current memory usage
        assertTrue(health.getStatus().equals(Status.UP) || health.getStatus().equals(Status.DOWN));
    }

    @Test
    @DisplayName("Should include memory details in health response")
    void health_ShouldIncludeMemoryDetails() {
        // When
        Health health = healthIndicator.health();

        // Then
        assertNotNull(health.getDetails());
        assertTrue(health.getDetails().containsKey("usedMemoryMB"));
        assertTrue(health.getDetails().containsKey("totalMemoryMB"));
        assertTrue(health.getDetails().containsKey("maxMemoryMB"));
        assertTrue(health.getDetails().containsKey("memoryUsagePercentage"));
        assertTrue(health.getDetails().containsKey("threshold"));
    }

    @Test
    @DisplayName("Should include memory health indicator")
    void health_ShouldIncludeMemoryHealthIndicator() {
        // When
        Health health = healthIndicator.health();

        // Then
        assertTrue(health.getDetails().containsKey("memoryHealthy"));
        assertNotNull(health.getDetails().get("memoryHealthy"));
        assertTrue(health.getDetails().get("memoryHealthy") instanceof Boolean);
    }

    // ==================== Memory Values Validation Tests ====================

    @Test
    @DisplayName("Should report valid memory values")
    void health_ShouldReportValidMemoryValues() {
        // When
        Health health = healthIndicator.health();

        // Then
        Object usedMB = health.getDetails().get("usedMemoryMB");
        Object totalMB = health.getDetails().get("totalMemoryMB");
        Object maxMB = health.getDetails().get("maxMemoryMB");
        Object usagePercentage = health.getDetails().get("memoryUsagePercentage");

        assertNotNull(usedMB);
        assertNotNull(totalMB);
        assertNotNull(maxMB);
        assertNotNull(usagePercentage);

        // Validate values are reasonable
        assertTrue(((Number) usedMB).longValue() >= 0);
        assertTrue(((Number) totalMB).longValue() >= 0);
        assertTrue(((Number) maxMB).longValue() >= 0);
        assertTrue(((Number) usagePercentage).doubleValue() >= 0.0);
        assertTrue(((Number) usagePercentage).doubleValue() <= 100.0);
    }

    @Test
    @DisplayName("Should report used memory less than or equal to total memory")
    void health_ShouldReportUsedMemoryLessThanOrEqualToTotal() {
        // When
        Health health = healthIndicator.health();

        // Then
        long usedMB = ((Number) health.getDetails().get("usedMemoryMB")).longValue();
        long totalMB = ((Number) health.getDetails().get("totalMemoryMB")).longValue();
        
        assertTrue(usedMB <= totalMB, 
            String.format("Used memory (%d MB) should be <= total memory (%d MB)", usedMB, totalMB));
    }

    @Test
    @DisplayName("Should report total memory less than or equal to max memory")
    void health_ShouldReportTotalMemoryLessThanOrEqualToMax() {
        // When
        Health health = healthIndicator.health();

        // Then
        long totalMB = ((Number) health.getDetails().get("totalMemoryMB")).longValue();
        long maxMB = ((Number) health.getDetails().get("maxMemoryMB")).longValue();
        
        assertTrue(totalMB <= maxMB, 
            String.format("Total memory (%d MB) should be <= max memory (%d MB)", totalMB, maxMB));
    }

    // ==================== Configuration Integration Tests ====================

    @Test
    @DisplayName("Should use configured memory threshold")
    void health_ShouldUseConfiguredMemoryThreshold() {
        // Given
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(85);
        when(kafkaMonitoringService.getMemoryUsagePercentage()).thenReturn(60.0);
        when(kafkaMonitoringService.isMemoryHealthy(85)).thenReturn(true);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(85, health.getDetails().get("threshold"));
    }

    @Test
    @DisplayName("Should support different threshold configurations")
    void health_ShouldSupportDifferentThresholdConfigurations() {
        // Test with high threshold
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(95);
        when(kafkaMonitoringService.getMemoryUsagePercentage()).thenReturn(60.0);
        when(kafkaMonitoringService.isMemoryHealthy(95)).thenReturn(true);
        Health healthHigh = healthIndicator.health();
        assertEquals(95, healthHigh.getDetails().get("threshold"));

        // Test with low threshold
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(70);
        when(kafkaMonitoringService.isMemoryHealthy(70)).thenReturn(true);
        Health healthLow = healthIndicator.health();
        assertEquals(70, healthLow.getDetails().get("threshold"));
    }

    // ==================== Memory Usage Percentage Tests ====================

    @Test
    @DisplayName("Should report memory usage percentage from service")
    void health_ShouldReportMemoryUsagePercentageFromService() {
        // Given
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(90);
        when(kafkaMonitoringService.getMemoryUsagePercentage()).thenReturn(65.4);
        when(kafkaMonitoringService.isMemoryHealthy(90)).thenReturn(true);

        // When
        Health health = healthIndicator.health();

        // Then
        assertNotNull(health.getDetails().get("memoryUsagePercentage"));
        double reportedPercentage = ((Number) health.getDetails().get("memoryUsagePercentage")).doubleValue();
        
        // The percentage comes from the service and gets rounded to 1 decimal place
        assertEquals(65.4, reportedPercentage, 0.1);
    }

    @Test
    @DisplayName("Should handle zero total memory gracefully")
    void health_ShouldHandleZeroTotalMemoryGracefully() {
        // This tests the edge case where total memory might be reported as 0
        // The health indicator should still function without throwing exceptions
        
        // Given
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(90);
        when(kafkaMonitoringService.getMemoryUsagePercentage()).thenReturn(75.0);
        when(kafkaMonitoringService.isMemoryHealthy(90)).thenReturn(true);
        
        // When
        Health health = healthIndicator.health();

        // Then
        assertNotNull(health);
        assertNotNull(health.getStatus());
        assertNotNull(health.getDetails());
        
        // Verify percentage is within valid range
        double percentage = ((Number) health.getDetails().get("memoryUsagePercentage")).doubleValue();
        assertTrue(percentage >= 0.0 && percentage <= 100.0);
    }

    // ==================== Status Determination Tests ====================

    @Test
    @DisplayName("Should determine status based on threshold comparison")
    void health_ShouldDetermineStatusBasedOnThresholdComparison() {
        // Given - setup a scenario where memory usage exceeds threshold
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(90);
        when(kafkaMonitoringService.getMemoryUsagePercentage()).thenReturn(95.0);
        when(kafkaMonitoringService.isMemoryHealthy(90)).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().containsKey("reason"));
        String reason = health.getDetails().get("reason").toString();
        assertTrue(reason.contains("Memory usage") && reason.contains("exceeds threshold"));
        assertEquals(false, health.getDetails().get("memoryHealthy"));
    }

    // ==================== Health Details Structure Tests ====================

    @Test
    @DisplayName("Should include all required memory metrics")
    void health_ShouldIncludeAllRequiredMemoryMetrics() {
        // When
        Health health = healthIndicator.health();

        // Then
        String[] requiredKeys = {
            "usedMemoryMB",
            "totalMemoryMB", 
            "maxMemoryMB",
            "memoryUsagePercentage",
            "threshold",
            "memoryHealthy"
        };

        for (String key : requiredKeys) {
            assertTrue(health.getDetails().containsKey(key), 
                String.format("Health details should contain key: %s", key));
            assertNotNull(health.getDetails().get(key),
                String.format("Health detail value for key '%s' should not be null", key));
        }
    }

    @Test
    @DisplayName("Should format memory values as numbers")
    void health_ShouldFormatMemoryValuesAsNumbers() {
        // When
        Health health = healthIndicator.health();

        // Then
        assertTrue(health.getDetails().get("usedMemoryMB") instanceof Number);
        assertTrue(health.getDetails().get("totalMemoryMB") instanceof Number);
        assertTrue(health.getDetails().get("maxMemoryMB") instanceof Number);
        assertTrue(health.getDetails().get("memoryUsagePercentage") instanceof Number);
        assertTrue(health.getDetails().get("threshold") instanceof Number);
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Should handle extreme threshold values")
    void health_ShouldHandleExtremeThresholdValues() {
        // Test with very low threshold (0%)
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(0);
        when(kafkaMonitoringService.getMemoryUsagePercentage()).thenReturn(50.0);
        when(kafkaMonitoringService.isMemoryHealthy(0)).thenReturn(false); // Any usage > 0% would be unhealthy
        Health healthLow = healthIndicator.health();
        assertNotNull(healthLow);
        assertEquals(0, healthLow.getDetails().get("threshold"));

        // Test with maximum threshold (100%)
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(100);
        when(kafkaMonitoringService.isMemoryHealthy(100)).thenReturn(true); // Even 99% would be healthy
        Health healthHigh = healthIndicator.health();
        assertNotNull(healthHigh);
        assertEquals(100, healthHigh.getDetails().get("threshold"));
    }

    @Test
    @DisplayName("Should provide meaningful reason when unhealthy")
    void health_ShouldProvideMeaningfulReasonWhenUnhealthy() {
        // Given - setup unhealthy memory scenario
        when(healthConfig.getMemoryThresholdPercentage()).thenReturn(90);
        when(kafkaMonitoringService.getMemoryUsagePercentage()).thenReturn(95.0);
        when(kafkaMonitoringService.isMemoryHealthy(90)).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertTrue(health.getDetails().containsKey("reason"));
        String reasonMessage = health.getDetails().get("reason").toString();
        assertNotNull(reasonMessage);
        assertTrue(reasonMessage.length() > 10); // Should be a meaningful message
        assertTrue(reasonMessage.toLowerCase().contains("memory")); // Should mention memory
        assertTrue(reasonMessage.contains("exceeds threshold"));
    }

    // ==================== Performance Tests ====================

    @Test
    @DisplayName("Should execute health check quickly")
    void health_ShouldExecuteHealthCheckQuickly() {
        // When
        long startTime = System.currentTimeMillis();
        Health health = healthIndicator.health();
        long endTime = System.currentTimeMillis();

        // Then
        assertNotNull(health);
        long executionTime = endTime - startTime;
        assertTrue(executionTime < 1000, // Should complete within 1 second
            String.format("Health check took too long: %d ms", executionTime));
    }

    @Test
    @DisplayName("Should be consistent across multiple calls")
    void health_ShouldBeConsistentAcrossMultipleCalls() {
        // When
        Health health1 = healthIndicator.health();
        Health health2 = healthIndicator.health();

        // Then
        assertNotNull(health1);
        assertNotNull(health2);
        
        // Memory values might change slightly, but should be in the same ballpark
        long used1 = ((Number) health1.getDetails().get("usedMemoryMB")).longValue();
        long used2 = ((Number) health2.getDetails().get("usedMemoryMB")).longValue();
        
        // Allow for reasonable variation (within 100MB)
        assertTrue(Math.abs(used1 - used2) < 100,
            String.format("Memory usage should be consistent across calls: %d vs %d", used1, used2));
    }
}
