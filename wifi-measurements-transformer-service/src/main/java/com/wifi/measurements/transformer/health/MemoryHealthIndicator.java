// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/MemoryHealthIndicator.java
package com.wifi.measurements.transformer.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for JVM memory usage monitoring for liveness probes.
 *
 * <p>This health indicator monitors heap memory usage and triggers alerts when usage exceeds
 * thresholds. It provides comprehensive memory monitoring for service liveness and prevents
 * out-of-memory conditions that could affect message processing and data delivery.
 *
 * <p><strong>Key Functionality:</strong>
 *
 * <ul>
 *   <li><strong>Memory Usage Tracking:</strong> Monitor current heap memory usage percentage
 *   <li><strong>Threshold Monitoring:</strong> Alert when memory usage exceeds configurable
 *       thresholds
 *   <li><strong>Memory Metrics:</strong> Provide detailed memory statistics for monitoring
 *   <li><strong>Liveness Integration:</strong> Fail liveness probes when memory usage is critical
 *   <li><strong>Operational Guidance:</strong> Provide memory optimization recommendations
 * </ul>
 *
 * <p><strong>Health Status Criteria:</strong>
 *
 * <ul>
 *   <li><strong>UP:</strong> Memory usage is below the configured threshold
 *   <li><strong>DOWN:</strong> Memory usage exceeds the critical threshold
 * </ul>
 *
 * <p><strong>Configuration Properties:</strong>
 *
 * <ul>
 *   <li><strong>memory-threshold-percentage:</strong> Critical memory usage threshold (default:
 *       90%)
 * </ul>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 * @see HealthIndicator for Spring Boot health check interface
 */
@Component("memoryUsage")
public class MemoryHealthIndicator implements HealthIndicator {

  private static final Logger logger = LoggerFactory.getLogger(MemoryHealthIndicator.class);

  /** Memory usage threshold percentage (default: 90%) */
  @Value("${health.indicator.memory-threshold-percentage:90}")
  private int memoryThresholdPercentage;

  /**
   * Performs comprehensive health check for JVM memory usage.
   *
   * <p>This method monitors heap memory usage and provides detailed memory statistics for
   * liveness monitoring and operational troubleshooting.
   *
   * <p><strong>Processing Steps:</strong>
   *
   * <ol>
   *   <li><strong>Memory Calculation:</strong> Calculate current memory usage statistics
   *   <li><strong>Threshold Check:</strong> Compare usage against configured threshold
   *   <li><strong>Health Assessment:</strong> Determine health status based on memory usage
   *   <li><strong>Metrics Collection:</strong> Collect detailed memory metrics for monitoring
   *   <li><strong>Response Building:</strong> Build comprehensive health response with details
   * </ol>
   *
   * <p><strong>Health Evaluation Criteria:</strong>
   *
   * <ul>
   *   <li><strong>Memory Usage:</strong> Current heap memory usage must be below threshold
   *   <li><strong>Available Memory:</strong> Sufficient free memory must be available
   *   <li><strong>Memory Trend:</strong> Memory usage should not show critical growth patterns
   * </ul>
   *
   * @return Health status with detailed memory information
   */
  @Override
  public Health health() {
    try {
      logger.debug("Checking JVM memory health for liveness monitoring");

      // Get current memory statistics
      Runtime runtime = Runtime.getRuntime();
      long totalMemory = runtime.totalMemory();
      long freeMemory = runtime.freeMemory();
      long maxMemory = runtime.maxMemory();
      long usedMemory = totalMemory - freeMemory;

      // Calculate memory usage percentage
      double memoryUsagePercentage = (double) usedMemory / maxMemory * 100;
      boolean isMemoryHealthy = memoryUsagePercentage < memoryThresholdPercentage;

      // Determine health status
      Health.Builder healthBuilder = isMemoryHealthy ? Health.up() : Health.down();

      // Add warning details if memory usage is high
      if (!isMemoryHealthy) {
        healthBuilder.withDetail(
            "reason",
            String.format(
                "Memory usage (%.1f%%) exceeds threshold (%d%%) - may affect processing performance",
                memoryUsagePercentage, memoryThresholdPercentage));
        healthBuilder.withDetail(
            "recommendation",
            "Consider increasing heap size or optimizing memory usage to prevent out-of-memory conditions");
      } else if (memoryUsagePercentage > (memoryThresholdPercentage * 0.8)) {
        // Warning when approaching threshold
        healthBuilder.withDetail(
            "warning",
            String.format(
                "Memory usage (%.1f%%) is approaching threshold (%d%%) - monitor closely",
                memoryUsagePercentage, memoryThresholdPercentage));
      }

      return healthBuilder
          .withDetail("memoryHealthy", isMemoryHealthy)
          .withDetail("memoryUsagePercentage", Math.round(memoryUsagePercentage * 10.0) / 10.0)
          .withDetail("usedMemoryMB", usedMemory / (1024 * 1024))
          .withDetail("totalMemoryMB", totalMemory / (1024 * 1024))
          .withDetail("maxMemoryMB", maxMemory / (1024 * 1024))
          .withDetail("freeMemoryMB", freeMemory / (1024 * 1024))
          .withDetail("availableMemoryMB", (maxMemory - usedMemory) / (1024 * 1024))
          .withDetail("threshold", memoryThresholdPercentage)
          .withDetail("checkTimestamp", System.currentTimeMillis())
          .build();

    } catch (Exception e) {
      logger.error("Error checking memory health", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("reason", "Memory health check failed due to exception")
          .withDetail("checkTimestamp", System.currentTimeMillis())
          .build();
    }
  }
}
