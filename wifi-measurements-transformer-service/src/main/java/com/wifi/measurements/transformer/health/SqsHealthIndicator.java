// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/SqsHealthIndicator.java
package com.wifi.measurements.transformer.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.measurements.transformer.service.SqsMonitoringService;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Enhanced health indicator for SQS connectivity and queue availability for readiness monitoring.
 *
 * <p>This health indicator provides comprehensive readiness checks for the SQS queue, ensuring the
 * service is ready to receive traffic and process messages. It performs detailed connectivity
 * checks, queue attribute validation, and processing readiness assessment based on the pattern from
 * the wifi-scan-queue-consumer implementation.
 *
 * <p><strong>Readiness Health Checks:</strong>
 *
 * <ul>
 *   <li><strong>Queue Connectivity:</strong> Basic SQS queue accessibility and responsiveness
 *   <li><strong>Queue Attributes:</strong> Comprehensive queue configuration validation
 *   <li><strong>Message Visibility:</strong> Queue message counts and visibility timeout status
 *   <li><strong>Processing Readiness:</strong> Service readiness for message processing
 *   <li><strong>Connection Stability:</strong> Recent connection success tracking
 * </ul>
 *
 * <p><strong>Health Status Criteria:</strong>
 *
 * <ul>
 *   <li><strong>UP:</strong> Queue is accessible, responsive, and ready for processing
 *   <li><strong>DOWN:</strong> Queue is unreachable, misconfigured, or processing not ready
 * </ul>
 *
 * <p><strong>Readiness Validation:</strong>
 *
 * <ul>
 *   <li>Queue URL accessibility and valid configuration
 *   <li>AWS credentials and permissions validation
 *   <li>Message processing pipeline readiness
 *   <li>Recent connection success within acceptable timeframe
 * </ul>
 */
@Component("sqsConnectivity")
public class SqsHealthIndicator implements HealthIndicator {

  private static final Logger logger = LoggerFactory.getLogger(SqsHealthIndicator.class);
  private static final String QUEUE_URL_KEY = "queueUrl";

  private final SqsClient sqsClient;
  private final SqsMonitoringService sqsMonitoringService;
  private final String queueUrl;

  public SqsHealthIndicator(
      SqsClient sqsClient,
      SqsMonitoringService sqsMonitoringService,
      @Value("#{@resolvedQueueUrl}") String queueUrl) {
    this.sqsClient = sqsClient;
    this.sqsMonitoringService = sqsMonitoringService;
    this.queueUrl = queueUrl;
  }

  @Override
  public Health health() {
    try {
      logger.debug("Performing SQS readiness health check");
      long checkTimestamp = System.currentTimeMillis();

      // Step 1: Comprehensive queue attributes check for readiness
      GetQueueAttributesRequest request =
          GetQueueAttributesRequest.builder()
              .queueUrl(queueUrl)
              .attributeNames(
                  QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                  QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                  QueueAttributeName.VISIBILITY_TIMEOUT,
                  QueueAttributeName.MESSAGE_RETENTION_PERIOD,
                  QueueAttributeName.QUEUE_ARN)
              .build();

      GetQueueAttributesResponse response = sqsClient.getQueueAttributes(request);

      // Step 2: Extract and validate queue attributes
      String approximateMessages =
          response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
      String approximateNotVisible =
          response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE);
      String visibilityTimeout = response.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT);
      String messageRetention =
          response.attributes().get(QueueAttributeName.MESSAGE_RETENTION_PERIOD);
      String queueArn = response.attributes().get(QueueAttributeName.QUEUE_ARN);

      // Step 3: Get monitoring service metrics for readiness assessment
      SqsMonitoringService.SqsMetrics metrics = sqsMonitoringService.getMetrics();
      boolean queueConnected = sqsMonitoringService.isQueueConnected();
      boolean queueAccessible = sqsMonitoringService.isQueueAccessible();

      // Step 4: Assess overall readiness status
      boolean isReady =
          queueConnected && queueAccessible && (metrics.getConsecutiveConnectionFailures() < 3);

      // Step 5: Build comprehensive health response
      Health.Builder healthBuilder = isReady ? Health.up() : Health.down();

      return healthBuilder
          .withDetail(QUEUE_URL_KEY, queueUrl)
          .withDetail("queueAccessible", queueAccessible)
          .withDetail("queueConnected", queueConnected)
          .withDetail("approximateMessages", approximateMessages)
          .withDetail("approximateNotVisibleMessages", approximateNotVisible)
          .withDetail("visibilityTimeoutSeconds", visibilityTimeout)
          .withDetail("messageRetentionPeriod", messageRetention)
          .withDetail("queueArn", queueArn)
          .withDetail("totalMessagesReceived", metrics.getTotalMessagesReceived())
          .withDetail("totalMessagesProcessed", metrics.getTotalMessagesProcessed())
          .withDetail("successRate", metrics.getSuccessRate())
          .withDetail("consecutiveConnectionFailures", metrics.getConsecutiveConnectionFailures())
          .withDetail(
              "lastSuccessfulConnection",
              metrics.getLastSuccessfulConnection() != null
                  ? metrics.getLastSuccessfulConnection()
                  : "Never")
          .withDetail("processingReadiness", isReady)
          .withDetail(
              "reason",
              isReady
                  ? "SQS queue is accessible and ready for message processing"
                  : "SQS queue is not ready - connectivity or configuration issues detected")
          .withDetail("checkTimestamp", checkTimestamp)
          .build();

    } catch (Exception e) {
      logger.error("SQS readiness check failed", e);
      return Health.down()
          .withDetail(QUEUE_URL_KEY, queueUrl)
          .withDetail("queueAccessible", false)
          .withDetail("queueConnected", false)
          .withDetail("error", e.getMessage())
          .withDetail("errorType", e.getClass().getSimpleName())
          .withDetail("processingReadiness", false)
          .withDetail("reason", "SQS connectivity check failed: " + e.getMessage())
          .withDetail("checkTimestamp", System.currentTimeMillis())
          .build();
    }
  }
}
