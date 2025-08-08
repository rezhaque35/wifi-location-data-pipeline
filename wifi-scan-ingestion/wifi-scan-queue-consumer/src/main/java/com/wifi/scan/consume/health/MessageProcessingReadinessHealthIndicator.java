package com.wifi.scan.consume.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.scan.consume.config.HealthIndicatorConfiguration;
import com.wifi.scan.consume.service.KafkaMonitoringService;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator for message processing readiness.
 *
 * <p>Readiness focuses on whether the service is ready to handle requests/traffic. This indicator
 * checks: - Consumer connectivity and group membership - Topic accessibility - Recent processing
 * activity (with tolerance for idle periods) - Processing performance and error rates
 *
 * <p>Unlike liveness, readiness can be more strict about operational health but should still be
 * tolerant of normal idle periods.
 */
@Slf4j
@Component("messageProcessingReadiness")
public class MessageProcessingReadinessHealthIndicator implements HealthIndicator {

  private static final String REASON_KEY = "reason";

  private final KafkaMonitoringService kafkaMonitoringService;
  private final HealthIndicatorConfiguration config;

  @Autowired
  public MessageProcessingReadinessHealthIndicator(
      KafkaMonitoringService kafkaMonitoringService, HealthIndicatorConfiguration config) {
    this.kafkaMonitoringService = kafkaMonitoringService;
    this.config = config;
  }

  @Override
  public Health health() {
    try {
      log.debug("Checking message processing readiness");

      // Basic connectivity checks (required for readiness)
      boolean isConsumerConnected = kafkaMonitoringService.isConsumerConnected();
      boolean isConsumerGroupActive = kafkaMonitoringService.isConsumerGroupActive();
      boolean areTopicsAccessible = kafkaMonitoringService.areTopicsAccessible();

      // Processing health checks (with tolerance for idle periods)
      boolean isConsumptionHealthy =
          kafkaMonitoringService.isMessageConsumptionHealthy(
              config.getConsumptionTimeoutMinutes(), config.getMinimumConsumptionRate());

      // Get metrics for detailed reporting
      double consumptionRate = kafkaMonitoringService.getMessageConsumptionRate();
      double successRate = kafkaMonitoringService.getMetrics().getSuccessRate();
      long totalConsumed = kafkaMonitoringService.getMetrics().getTotalMessagesConsumed().get();
      long totalProcessed = kafkaMonitoringService.getMetrics().getTotalMessagesProcessed().get();

      // Readiness criteria: basic connectivity + topic access + reasonable processing health
      // Note: We're more lenient on consumption health to avoid false negatives during idle periods
      boolean isReady =
          isConsumerConnected
              && isConsumerGroupActive
              && areTopicsAccessible
              && (isConsumptionHealthy
                  || totalConsumed == 0); // Allow healthy status if no messages consumed yet

      Health.Builder healthBuilder = isReady ? Health.up() : Health.down();

      // Provide detailed reason for failures
      if (!isConsumerConnected) {
        healthBuilder.withDetail(REASON_KEY, "Consumer cannot connect to Kafka cluster");
      } else if (!isConsumerGroupActive) {
        healthBuilder.withDetail(REASON_KEY, "Consumer is not active in consumer group");
      } else if (!areTopicsAccessible) {
        healthBuilder.withDetail(REASON_KEY, "Configured topics are not accessible");
      } else if (!isConsumptionHealthy && totalConsumed > 0) {
        healthBuilder.withDetail(REASON_KEY, "Message processing is degraded");
      } else {
        healthBuilder.withDetail(REASON_KEY, "Service is ready to process messages");
      }

      return healthBuilder
          .withDetail("consumerConnected", isConsumerConnected)
          .withDetail("consumerGroupActive", isConsumerGroupActive)
          .withDetail("topicsAccessible", areTopicsAccessible)
          .withDetail("consumptionHealthy", isConsumptionHealthy)
          .withDetail("consumptionRate", consumptionRate)
          .withDetail("successRate", successRate)
          .withDetail("totalMessagesConsumed", totalConsumed)
          .withDetail("totalMessagesProcessed", totalProcessed)
          .withDetail("readinessNote", "This probe indicates service readiness for traffic")
          .withDetail("checkTimestamp", System.currentTimeMillis())
          .build();

    } catch (Exception e) {
      log.error("Error checking message processing readiness", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail(REASON_KEY, "Readiness check failed due to exception")
          .withDetail("checkTimestamp", System.currentTimeMillis())
          .build();
    }
  }
}
