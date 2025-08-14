// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/MemoryUsageReportingIndicator.java
package com.wifi.measurements.transformer.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Memory usage reporting indicator that never marks service as DOWN.
 *
 * <p>This indicator provides comprehensive monitoring and reporting of JVM memory usage,
 * memory trends, and memory-related metrics. Unlike traditional memory health indicators,
 * this component always returns UP status and focuses on providing detailed memory
 * visibility for operational monitoring rather than failing health checks.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>Always UP:</strong> Never marks service as DOWN regardless of memory usage</li>
 *   <li><strong>Detailed Reporting:</strong> Provides comprehensive memory metrics</li>
 *   <li><strong>Trend Analysis:</strong> Reports memory usage patterns and trends</li>
 *   <li><strong>Operational Insights:</strong> Includes warnings and recommendations</li>
 * </ul>
 *
 * <p><strong>Reported Metrics:</strong>
 * <ul>
 *   <li><strong>Memory Usage:</strong> Current heap memory usage percentage</li>
 *   <li><strong>Memory Distribution:</strong> Used, free, total, and max memory</li>
 *   <li><strong>Memory Status:</strong> Whether usage is within normal ranges</li>
 *   <li><strong>Recommendations:</strong> Operational guidance for memory management</li>
 * </ul>
 */
@Component("memoryUsageReporting")
public class MemoryUsageReportingIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(MemoryUsageReportingIndicator.class);

    /** Memory usage threshold percentage for reporting warnings */
    @Value("${health.indicator.memory-threshold-percentage:90}")
    private int memoryThresholdPercentage;

    @Override
    public Health health() {
        try {
            logger.debug("Generating memory usage report");
            long checkTimestamp = System.currentTimeMillis();

            // Get current memory statistics
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            long usedMemory = totalMemory - freeMemory;

            // Calculate memory usage percentage
            double memoryUsagePercentage = (double) usedMemory / maxMemory * 100;
            
            // Determine memory status for reporting
            String memoryStatus = determineMemoryStatus(memoryUsagePercentage);
            String operationalRecommendation = generateMemoryRecommendation(memoryUsagePercentage);
            
            // Calculate additional useful metrics
            long availableMemory = maxMemory - usedMemory;
            double memoryUtilization = (double) totalMemory / maxMemory * 100;

            // Always return UP - this is a reporting indicator
            return Health.up()
                .withDetail("memoryStatus", memoryStatus)
                .withDetail("operationalRecommendation", operationalRecommendation)
                // Core memory metrics
                .withDetail("memoryUsagePercentage", Math.round(memoryUsagePercentage * 10.0) / 10.0)
                .withDetail("memoryUtilizationPercentage", Math.round(memoryUtilization * 10.0) / 10.0)
                .withDetail("usedMemoryMB", usedMemory / (1024 * 1024))
                .withDetail("freeMemoryMB", freeMemory / (1024 * 1024))
                .withDetail("totalMemoryMB", totalMemory / (1024 * 1024))
                .withDetail("maxMemoryMB", maxMemory / (1024 * 1024))
                .withDetail("availableMemoryMB", availableMemory / (1024 * 1024))
                // Status flags
                .withDetail("withinNormalLimits", memoryUsagePercentage < memoryThresholdPercentage)
                .withDetail("approachingLimit", memoryUsagePercentage > (memoryThresholdPercentage * 0.8))
                .withDetail("exceedsThreshold", memoryUsagePercentage >= memoryThresholdPercentage)
                .withDetail("threshold", memoryThresholdPercentage)
                // Metadata
                .withDetail("checkTimestamp", checkTimestamp)
                .withDetail("reason", "Memory usage reporting - always UP for monitoring purposes")
                .build();

        } catch (Exception e) {
            logger.warn("Error generating memory usage report", e);
            // Even on error, return UP with error details for monitoring
            return Health.up()
                .withDetail("memoryStatus", "ERROR")
                .withDetail("operationalRecommendation", "Check service logs for error details")
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail("checkTimestamp", System.currentTimeMillis())
                .withDetail("reason", "Memory usage reporting error - returning UP for monitoring")
                .build();
        }
    }

    /**
     * Determines the current memory status for reporting.
     */
    private String determineMemoryStatus(double memoryUsagePercentage) {
        if (memoryUsagePercentage >= memoryThresholdPercentage) {
            return "HIGH_USAGE";
        } else if (memoryUsagePercentage > (memoryThresholdPercentage * 0.8)) {
            return "APPROACHING_LIMIT";
        } else if (memoryUsagePercentage > (memoryThresholdPercentage * 0.6)) {
            return "MODERATE_USAGE";
        } else {
            return "NORMAL";
        }
    }

    /**
     * Generates memory management recommendations based on current usage.
     */
    private String generateMemoryRecommendation(double memoryUsagePercentage) {
        if (memoryUsagePercentage >= memoryThresholdPercentage) {
            return String.format(
                "Memory usage (%.1f%%) exceeds threshold (%d%%) - consider increasing heap size (-Xmx) or optimizing memory usage",
                memoryUsagePercentage, memoryThresholdPercentage);
        } else if (memoryUsagePercentage > (memoryThresholdPercentage * 0.9)) {
            return String.format(
                "Memory usage (%.1f%%) is very close to threshold (%d%%) - monitor closely and prepare for memory optimization",
                memoryUsagePercentage, memoryThresholdPercentage);
        } else if (memoryUsagePercentage > (memoryThresholdPercentage * 0.8)) {
            return String.format(
                "Memory usage (%.1f%%) is approaching threshold (%d%%) - consider memory optimization or heap size adjustment",
                memoryUsagePercentage, memoryThresholdPercentage);
        } else if (memoryUsagePercentage < 20) {
            return String.format(
                "Memory usage (%.1f%%) is very low - heap size may be over-allocated",
                memoryUsagePercentage);
        } else {
            return String.format(
                "Memory usage (%.1f%%) is within normal range - no action required",
                memoryUsagePercentage);
        }
    }
}
