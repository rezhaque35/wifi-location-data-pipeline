// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/SqsMonitoringService.java
package com.wifi.measurements.transformer.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * Comprehensive SQS monitoring service for tracking message processing activity and health.
 *
 * <p>This service provides detailed monitoring capabilities for SQS message processing, including
 * connectivity tracking, message processing activity, queue status monitoring, and performance
 * metrics collection. It serves as the foundation for SQS-based health indicators in both readiness
 * and liveness probes.
 *
 * <p><strong>Monitoring Capabilities:</strong>
 *
 * <ul>
 *   <li><strong>Connectivity Monitoring:</strong> SQS queue accessibility and connection health
 *   <li><strong>Processing Activity:</strong> Message processing rate, success/failure tracking
 *   <li><strong>Queue Status:</strong> Queue attributes, message counts, visibility metrics
 *   <li><strong>Performance Metrics:</strong> Processing times, throughput, error rates
 * </ul>
 *
 * <p><strong>Health Check Support:</strong>
 *
 * <ul>
 *   <li><strong>Readiness:</strong> Queue connectivity, accessibility, configuration validation
 *   <li><strong>Liveness:</strong> Processing activity, message consumption tracking, stuck
 *       detection
 * </ul>
 *
 * <p><strong>Activity Tracking:</strong>
 *
 * <ul>
 *   <li>Last message received timestamp for idle period detection
 *   <li>Message processing rate calculation for performance monitoring
 *   <li>Success/failure ratio tracking for reliability assessment
 *   <li>Processing pipeline health status monitoring
 * </ul>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class SqsMonitoringService {

  private static final Logger logger = LoggerFactory.getLogger(SqsMonitoringService.class);

  private final SqsClient sqsClient;
  private final String queueUrl;

  // Activity tracking
  private final AtomicReference<Instant> lastMessageReceivedTime =
      new AtomicReference<>(Instant.now());
  private final AtomicReference<Instant> lastProcessingActivityTime =
      new AtomicReference<>(Instant.now());
  private final AtomicLong totalMessagesReceived = new AtomicLong(0);
  private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
  private final AtomicLong totalMessagesSucceeded = new AtomicLong(0);
  private final AtomicLong totalMessagesFailed = new AtomicLong(0);

  // Connection state tracking
  private final AtomicReference<Instant> lastSuccessfulConnection = new AtomicReference<>(null);
  private final AtomicLong consecutiveConnectionFailures = new AtomicLong(0);

  /**
   * Creates a new SqsMonitoringService with required dependencies.
   *
   * @param sqsClient AWS SQS client for queue operations
   * @param queueUrl Resolved SQS queue URL
   */
  public SqsMonitoringService(
      SqsClient sqsClient, 
      @Value("#{@resolvedQueueUrl}") String queueUrl) {
    this.sqsClient = sqsClient;
    this.queueUrl = queueUrl;
  }

  /**
   * Checks if the SQS client can successfully connect to the queue.
   *
   * @return true if connection is successful, false otherwise
   */
  public boolean isQueueConnected() {
    try {
      GetQueueAttributesRequest request =
          GetQueueAttributesRequest.builder()
              .queueUrl(queueUrl)
              .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
              .build();

      sqsClient.getQueueAttributes(request);
      lastSuccessfulConnection.set(Instant.now());
      consecutiveConnectionFailures.set(0);
      return true;

    } catch (SqsException e) {
      logger.warn("SQS connection check failed: {}", e.getMessage());
      consecutiveConnectionFailures.incrementAndGet();
      return false;
    } catch (Exception e) {
      logger.error("Unexpected error during SQS connection check", e);
      consecutiveConnectionFailures.incrementAndGet();
      return false;
    }
  }

  /**
   * Checks if the SQS queue is accessible and responding to requests.
   *
   * @return true if queue is accessible, false otherwise
   */
  public boolean isQueueAccessible() {
    return isQueueConnected();
  }

  /**
   * Gets the time in milliseconds since the last message was received.
   *
   * @return milliseconds since last message received
   */
  public long getTimeSinceLastMessageReceived() {
    Instant lastReceived = lastMessageReceivedTime.get();
    return lastReceived != null
        ? java.time.Duration.between(lastReceived, Instant.now()).toMillis()
        : Long.MAX_VALUE;
  }

  /**
   * Gets the time in milliseconds since the last processing activity.
   *
   * @return milliseconds since last processing activity
   */
  public long getTimeSinceLastProcessingActivity() {
    Instant lastActivity = lastProcessingActivityTime.get();
    return lastActivity != null
        ? java.time.Duration.between(lastActivity, Instant.now()).toMillis()
        : Long.MAX_VALUE;
  }

  /**
   * Calculates the current message processing rate in messages per minute.
   *
   * @return processing rate in messages per minute
   */
  public double getMessageProcessingRate() {
    long totalProcessed = totalMessagesProcessed.get();
    if (totalProcessed == 0) {
      return 0.0;
    }

    Instant now = Instant.now();
    Instant startTime = lastProcessingActivityTime.get();

    if (startTime == null) {
      return 0.0;
    }

    long elapsedMinutes = java.time.Duration.between(startTime, now).toMinutes();
    if (elapsedMinutes == 0) {
      elapsedMinutes = 1; // Avoid division by zero, treat as 1 minute minimum
    }

    return (double) totalProcessed / elapsedMinutes;
  }

  /**
   * Gets the success rate of message processing as a percentage.
   *
   * @return success rate as a decimal (0.0 to 1.0)
   */
  public double getSuccessRate() {
    long total = totalMessagesProcessed.get();
    if (total == 0) {
      return 1.0; // 100% if no messages processed yet
    }

    long succeeded = totalMessagesSucceeded.get();
    return (double) succeeded / total;
  }

  /**
   * Checks if message processing appears to be stuck or inactive.
   *
   * @return true if processing appears stuck, false otherwise
   */
  public boolean isProcessingStuck() {
    // Consider processing stuck if:
    // 1. We have received messages but haven't processed any recently
    // 2. Processing activity has been idle for an extended period
    long timeSinceLastActivity = getTimeSinceLastProcessingActivity();
    long totalReceived = totalMessagesReceived.get();
    long totalProcessed = totalMessagesProcessed.get();

    // If we have received messages but processing activity is stale
    if (totalReceived > totalProcessed && timeSinceLastActivity > 300000) { // 5 minutes
      return true;
    }

    // Check for consecutive connection failures
    return consecutiveConnectionFailures.get() > 5;
  }

  /** Records that a message was received from SQS. */
  public void recordMessageReceived() {
    lastMessageReceivedTime.set(Instant.now());
    totalMessagesReceived.incrementAndGet();
    logger.debug("Message received recorded - total: {}", totalMessagesReceived.get());
  }

  /**
   * Records that a message processing attempt was made.
   *
   * @param success whether the processing was successful
   */
  public void recordMessageProcessed(boolean success) {
    lastProcessingActivityTime.set(Instant.now());
    totalMessagesProcessed.incrementAndGet();

    if (success) {
      totalMessagesSucceeded.incrementAndGet();
    } else {
      totalMessagesFailed.incrementAndGet();
    }

    logger.debug(
        "Message processing recorded - success: {}, total: {}, succeeded: {}",
        success,
        totalMessagesProcessed.get(),
        totalMessagesSucceeded.get());
  }

  /**
   * Gets comprehensive metrics for monitoring and health checks.
   *
   * @return SQS monitoring metrics
   */
  public SqsMetrics getMetrics() {
    return new SqsMetrics(
        totalMessagesReceived.get(),
        totalMessagesProcessed.get(),
        totalMessagesSucceeded.get(),
        totalMessagesFailed.get(),
        getSuccessRate(),
        getMessageProcessingRate(),
        getTimeSinceLastMessageReceived(),
        getTimeSinceLastProcessingActivity(),
        lastSuccessfulConnection.get(),
        consecutiveConnectionFailures.get());
  }

  /** Data class containing SQS monitoring metrics. */
  public static class SqsMetrics {
    private final long totalMessagesReceived;
    private final long totalMessagesProcessed;
    private final long totalMessagesSucceeded;
    private final long totalMessagesFailed;
    private final double successRate;
    private final double processingRate;
    private final long timeSinceLastMessageReceived;
    private final long timeSinceLastProcessingActivity;
    private final Instant lastSuccessfulConnection;
    private final long consecutiveConnectionFailures;

    public SqsMetrics(
        long totalMessagesReceived,
        long totalMessagesProcessed,
        long totalMessagesSucceeded,
        long totalMessagesFailed,
        double successRate,
        double processingRate,
        long timeSinceLastMessageReceived,
        long timeSinceLastProcessingActivity,
        Instant lastSuccessfulConnection,
        long consecutiveConnectionFailures) {
      this.totalMessagesReceived = totalMessagesReceived;
      this.totalMessagesProcessed = totalMessagesProcessed;
      this.totalMessagesSucceeded = totalMessagesSucceeded;
      this.totalMessagesFailed = totalMessagesFailed;
      this.successRate = successRate;
      this.processingRate = processingRate;
      this.timeSinceLastMessageReceived = timeSinceLastMessageReceived;
      this.timeSinceLastProcessingActivity = timeSinceLastProcessingActivity;
      this.lastSuccessfulConnection = lastSuccessfulConnection;
      this.consecutiveConnectionFailures = consecutiveConnectionFailures;
    }

    // Getters
    public long getTotalMessagesReceived() {
      return totalMessagesReceived;
    }

    public long getTotalMessagesProcessed() {
      return totalMessagesProcessed;
    }

    public long getTotalMessagesSucceeded() {
      return totalMessagesSucceeded;
    }

    public long getTotalMessagesFailed() {
      return totalMessagesFailed;
    }

    public double getSuccessRate() {
      return successRate;
    }

    public double getProcessingRate() {
      return processingRate;
    }

    public long getTimeSinceLastMessageReceived() {
      return timeSinceLastMessageReceived;
    }

    public long getTimeSinceLastProcessingActivity() {
      return timeSinceLastProcessingActivity;
    }

    public Instant getLastSuccessfulConnection() {
      return lastSuccessfulConnection;
    }

    public long getConsecutiveConnectionFailures() {
      return consecutiveConnectionFailures;
    }
  }
}
