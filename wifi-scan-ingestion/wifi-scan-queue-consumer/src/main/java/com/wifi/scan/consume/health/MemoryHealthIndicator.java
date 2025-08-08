package com.wifi.scan.consume.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.scan.consume.config.HealthIndicatorConfiguration;
import com.wifi.scan.consume.service.KafkaMonitoringService;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator for JVM memory usage. Monitors heap memory usage and triggers alerts when usage
 * exceeds thresholds.
 */
@Slf4j
@Component("jvmMemory")
public class MemoryHealthIndicator implements HealthIndicator {

  private final KafkaMonitoringService kafkaMonitoringService;
  private final HealthIndicatorConfiguration config;

  @Autowired
  public MemoryHealthIndicator(
      KafkaMonitoringService kafkaMonitoringService, HealthIndicatorConfiguration config) {
    this.kafkaMonitoringService = kafkaMonitoringService;
    this.config = config;
  }

  @Override
  public Health health() {
    try {
      log.debug("Checking JVM memory health");

      double memoryUsagePercentage = kafkaMonitoringService.getMemoryUsagePercentage();
      boolean isMemoryHealthy =
          kafkaMonitoringService.isMemoryHealthy(config.getMemoryThresholdPercentage());

      Runtime runtime = Runtime.getRuntime();
      long totalMemory = runtime.totalMemory();
      long freeMemory = runtime.freeMemory();
      long maxMemory = runtime.maxMemory();
      long usedMemory = totalMemory - freeMemory;

      Health.Builder healthBuilder = isMemoryHealthy ? Health.up() : Health.down();

      if (!isMemoryHealthy) {
        healthBuilder.withDetail(
            "reason",
            String.format(
                "Memory usage (%.1f%%) exceeds threshold (%d%%)",
                memoryUsagePercentage, config.getMemoryThresholdPercentage()));
      }

      return healthBuilder
          .withDetail("memoryHealthy", isMemoryHealthy)
          .withDetail("memoryUsagePercentage", Math.round(memoryUsagePercentage * 10.0) / 10.0)
          .withDetail("usedMemoryMB", usedMemory / (1024 * 1024))
          .withDetail("totalMemoryMB", totalMemory / (1024 * 1024))
          .withDetail("maxMemoryMB", maxMemory / (1024 * 1024))
          .withDetail("freeMemoryMB", freeMemory / (1024 * 1024))
          .withDetail("threshold", config.getMemoryThresholdPercentage())
          .withDetail("checkTimestamp", System.currentTimeMillis())
          .build();

    } catch (Exception e) {
      log.error("Error checking memory health", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("checkTimestamp", System.currentTimeMillis())
          .build();
    }
  }
}
