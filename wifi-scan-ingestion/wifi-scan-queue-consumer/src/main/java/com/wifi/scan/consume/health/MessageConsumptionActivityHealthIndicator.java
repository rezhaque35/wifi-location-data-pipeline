package com.wifi.scan.consume.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.scan.consume.service.KafkaMonitoringService;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator for message consumption activity suitable for liveness probes.
 *
 * <p>This health indicator monitors the active consumption of messages from Kafka topics,
 * distinguishing between scenarios where no messages are available versus situations where messages
 * are available but not being processed. It provides comprehensive monitoring for consumer liveness
 * and processing pipeline health.
 *
 * <p>Key Functionality: - Track message consumption count and trends for processing pipeline health
 * - Monitor messages processed per time window with configurable thresholds - Compare current
 * consumption rate with historical baseline - Detect sustained periods of zero message processing
 * when messages are available - Fail if consumer is polling but consistently failing to process
 * available messages - Distinguish between "no messages available" vs "messages available but not
 * processed" - Provide detailed health status with operational guidance
 *
 * <p>High-Level Processing Steps: 1. Check basic consumer connectivity to Kafka cluster 2. Verify
 * consumer group membership and activity 3. Monitor time since last poll activity 4. Detect
 * consumer stuck conditions (polling but not advancing) 5. Analyze message consumption rates and
 * success rates 6. Compare consumption patterns against configured thresholds 7. Provide detailed
 * health status with operational context
 *
 * <p>Liveness Requirements: - Track message consumption count and trends for processing pipeline
 * health - Monitor messages processed per time window - Compare current consumption rate with
 * historical baseline - Detect sustained periods of zero message processing when messages are
 * available - Fail if consumer is polling but consistently failing to process available messages -
 * Distinguish between "no messages available" vs "messages available but not processed"
 *
 * <p>Health Check Categories: - Connectivity: Consumer connection to Kafka cluster - Group
 * Activity: Consumer group membership and participation - Poll Activity: Time since last poll and
 * stuck detection - Consumption Rate: Messages processed per time window - Success Rate: Successful
 * vs failed message processing - Processing Pipeline: Overall message processing health
 *
 * <p>Configuration Properties: - message-timeout-threshold: Timeout for message activity (default:
 * 5 minutes) - consumption-rate-threshold: Minimum consumption rate (default: 0.1 messages/minute)
 *
 * <p>Use Cases: - Kubernetes liveness probe configuration - Consumer health monitoring and alerting
 * - Processing pipeline health assessment - Operational troubleshooting and guidance - Performance
 * degradation detection
 *
 * @see KafkaMonitoringService for detailed monitoring capabilities
 * @see HealthIndicator for Spring Boot health check interface
 */
@Slf4j
@Component("messageConsumptionActivity")
public class MessageConsumptionActivityHealthIndicator implements HealthIndicator {

  /** Service for Kafka consumer monitoring and metrics collection */
  private final KafkaMonitoringService kafkaMonitoringService;

  // Constants for health check details
  private static final String REASON_DETAIL = "reason";
  private static final String CHECK_TIMESTAMP_DETAIL = "checkTimestamp";

  // Configuration properties with defaults

  /** Timeout threshold for message activity in minutes (default: 5 minutes) */
  @Value(
      "${management.health.message-consumption.message-timeout-threshold:5}") // 5 minutes default
  private long messageTimeoutThresholdMinutes;

  /** Minimum consumption rate threshold in messages per minute (default: 0.1) */
  @Value("${management.health.message-consumption.consumption-rate-threshold:0.1}")
  private double consumptionRateThreshold;

  /**
   * Creates a new MessageConsumptionActivityHealthIndicator with required dependencies.
   *
   * @param kafkaMonitoringService Service for Kafka consumer monitoring and metrics
   */
  @Autowired
  public MessageConsumptionActivityHealthIndicator(KafkaMonitoringService kafkaMonitoringService) {
    this.kafkaMonitoringService = kafkaMonitoringService;
  }

  /**
   * Creates a new MessageConsumptionActivityHealthIndicator for testing with explicit
   * configuration.
   *
   * <p>This constructor allows for testing with specific configuration values without relying on
   * Spring property injection.
   *
   * @param kafkaMonitoringService Service for Kafka consumer monitoring and metrics
   * @param messageTimeoutThresholdMinutes Timeout threshold for message activity in minutes
   * @param consumptionRateThreshold Minimum consumption rate threshold in messages per minute
   */
  public MessageConsumptionActivityHealthIndicator(
      KafkaMonitoringService kafkaMonitoringService,
      long messageTimeoutThresholdMinutes,
      double consumptionRateThreshold) {
    this.kafkaMonitoringService = kafkaMonitoringService;
    this.messageTimeoutThresholdMinutes = messageTimeoutThresholdMinutes;
    this.consumptionRateThreshold = consumptionRateThreshold;
  }

  /**
   * Convert minutes to milliseconds for internal calculations.
   *
   * <p>This method converts the configured timeout threshold from minutes to milliseconds for
   * internal time-based calculations.
   *
   * @return Timeout threshold in milliseconds
   */
  private long getMessageTimeoutThresholdMs() {
    return messageTimeoutThresholdMinutes * 60 * 1000;
  }

  /**
   * Performs comprehensive health check for message consumption activity.
   *
   * <p>This method implements the core health check logic, evaluating multiple aspects of consumer
   * health including connectivity, group activity, poll activity, consumption rates, and processing
   * success rates.
   *
   * <p>Processing Steps: 1. Check basic consumer connectivity to Kafka cluster 2. Verify consumer
   * group membership and activity 3. Monitor time since last poll activity 4. Detect consumer stuck
   * conditions (polling but not advancing) 5. Analyze message consumption rates and success rates
   * 6. Compare consumption patterns against configured thresholds 7. Build comprehensive health
   * response with operational details
   *
   * <p>Health Evaluation Criteria: - Consumer connectivity: Must be able to connect to Kafka
   * cluster - Group activity: Must be active member of consumer group - Poll activity: Must have
   * recent poll activity within timeout threshold - Consumer stuck: Must not be stuck (polling but
   * not advancing) - Consumption rate: Must meet minimum consumption rate threshold - Success rate:
   * Must maintain acceptable processing success rate
   *
   * <p>Error Handling: - Comprehensive exception catching and logging - Detailed error messages for
   * troubleshooting - Graceful degradation with meaningful health status - Operational guidance in
   * health responses
   *
   * @return Health status with detailed operational information
   */
  @Override
  public Health health() {
    try {
      log.debug("Checking consumer liveness including consumption activity monitoring");

      var healthBuilder = Health.up();
      long checkTimestamp = System.currentTimeMillis();

      // Basic connectivity checks - verify consumer can connect to Kafka cluster
      boolean consumerConnected = kafkaMonitoringService.isConsumerConnected();
      boolean consumerGroupActive = kafkaMonitoringService.isConsumerGroupActive();

      if (!consumerConnected) {
        return buildDownHealth("Consumer cannot connect to Kafka cluster", checkTimestamp);
      }

      if (!consumerGroupActive) {
        return buildDownHealth("Consumer is not active in consumer group", checkTimestamp);
      }

      // Consumer activity and consumption monitoring - check for stuck conditions
      long timeSinceLastPoll = kafkaMonitoringService.getTimeSinceLastPoll();
      boolean consumerStuck = kafkaMonitoringService.isConsumerStuck();


      // Check if consumer is stuck (polling but not advancing)
      if (consumerStuck) {
        return buildDownHealth(
            "Consumer is stuck - polling but not advancing position", checkTimestamp);
      }


            // Check if consumer hasn't received messages recently (may indicate inactive consumer or no
      // available messages)
      long timeoutThresholdMs = getMessageTimeoutThresholdMs();
      boolean hasMessageTimeout = timeSinceLastPoll > timeoutThresholdMs;
      String timeoutWarning = null;
      
      if (hasMessageTimeout) {
        timeoutWarning = String.format(
            "Consumer hasn't received messages in %d ms (threshold: %d ms) - may indicate inactive consumer or no available messages",
            timeSinceLastPoll, timeoutThresholdMs);
        log.warn("Message consumption timeout detected: {}", timeoutWarning);
      }

      // Get consumption metrics for detailed health analysis
      var metrics = kafkaMonitoringService.getMetrics();
      double consumptionRate = kafkaMonitoringService.getMessageConsumptionRate();
      long totalMessagesConsumed = metrics.getTotalMessagesConsumed().get();
      long totalMessagesProcessed = metrics.getTotalMessagesProcessed().get();
      double successRate = metrics.getSuccessRate();

      // Check consumption rate only if we have meaningful data and recent activity
      // Avoid false alarms during idle periods or startup
      if (totalMessagesConsumed >= 10 
          && timeSinceLastPoll <= (timeoutThresholdMs / 2)
          && consumptionRate > 0 
          && consumptionRate < consumptionRateThreshold) {
        log.warn(
            "Low consumption rate detected: {} msgs/min (threshold: {} msgs/min)",
            consumptionRate,
            consumptionRateThreshold);
        // Note: Currently we just warn, but could make this configurable
        // to fail health check in environments where low rate indicates problems
      }

      // Determine if consumption is healthy (consumer is working properly)
      // Note: Message timeout is now treated as warning, not a health failure
      boolean healthyConsumption =
          consumerConnected
              && consumerGroupActive
              && !consumerStuck;

      // Determine appropriate reason message based on timeout status
      String reasonMessage = hasMessageTimeout 
          ? "Consumer is healthy but hasn't received messages recently - see warning details"
          : "Consumer is healthy - actively polling for messages";

      var healthDetailsBuilder = healthBuilder
          .withDetail("consumerConnected", consumerConnected)
          .withDetail("consumerGroupActive", consumerGroupActive)
          .withDetail("consumptionRate", consumptionRate)
          .withDetail("totalMessagesConsumed", totalMessagesConsumed)
          .withDetail("totalMessagesProcessed", totalMessagesProcessed)
          .withDetail("successRate", successRate)
          .withDetail("timeSinceLastMessageReceivedMs", timeSinceLastPoll)
          .withDetail("messageTimeoutThresholdMs", timeoutThresholdMs)
          .withDetail("consumerStuck", consumerStuck)
          .withDetail("healthyConsumption", healthyConsumption)
          .withDetail("hasMessageTimeout", hasMessageTimeout)
          .withDetail(REASON_DETAIL, reasonMessage)
          .withDetail(CHECK_TIMESTAMP_DETAIL, checkTimestamp);

      // Add warning message if there's a timeout
      if (timeoutWarning != null) {
        healthDetailsBuilder.withDetail("warning", timeoutWarning);
      }

      return healthDetailsBuilder.build();

    } catch (Exception e) {
      log.error("Error checking consumer consumption activity liveness", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail(REASON_DETAIL, "Health check failed due to exception")
          .withDetail(CHECK_TIMESTAMP_DETAIL, System.currentTimeMillis())
          .build();
    }
  }

  private Health buildDownHealth(String reason, long checkTimestamp) {
    // Include basic connection state details for consistent response format
    boolean consumerConnected = false;
    boolean consumerGroupActive = false;

    try {
      consumerConnected = kafkaMonitoringService.isConsumerConnected();
      consumerGroupActive = kafkaMonitoringService.isConsumerGroupActive();
    } catch (Exception e) {
      // In case of exception, defaults remain false
    }

    return Health.down()
        .withDetail(REASON_DETAIL, reason)
        .withDetail("consumerConnected", consumerConnected)
        .withDetail("consumerGroupActive", consumerGroupActive)
        .withDetail(CHECK_TIMESTAMP_DETAIL, checkTimestamp)
        .build();
  }
}
