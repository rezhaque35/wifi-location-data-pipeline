package com.wifi.scan.consume.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.scan.consume.service.KafkaMonitoringService;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator for Kafka topic accessibility. Verifies that the configured topics are
 * accessible and exist.
 */
@Slf4j
@Component("kafkaTopicAccessibility")
public class TopicAccessibilityHealthIndicator implements HealthIndicator {

  private static final String CHECK_TIMESTAMP_KEY = "checkTimestamp";

  private final KafkaMonitoringService kafkaMonitoringService;

  @Autowired
  public TopicAccessibilityHealthIndicator(KafkaMonitoringService kafkaMonitoringService) {
    this.kafkaMonitoringService = kafkaMonitoringService;
  }

  @Override
  public Health health() {
    try {
      log.debug("Checking Kafka topic accessibility");

      boolean areTopicsAccessible = kafkaMonitoringService.areTopicsAccessible();

      if (areTopicsAccessible) {
        return Health.up()
            .withDetail("topicsAccessible", true)
            .withDetail(CHECK_TIMESTAMP_KEY, System.currentTimeMillis())
            .build();
      } else {
        return Health.down()
            .withDetail("reason", "Configured topics are not accessible")
            .withDetail("topicsAccessible", false)
            .withDetail(CHECK_TIMESTAMP_KEY, System.currentTimeMillis())
            .build();
      }

    } catch (Exception e) {
      log.error("Error checking topic accessibility", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail(CHECK_TIMESTAMP_KEY, System.currentTimeMillis())
          .build();
    }
  }
}
