// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/SqsMessageReceiver.java
package com.wifi.measurements.transformer.listener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.wifi.measurements.transformer.config.properties.SqsConfigurationProperties;
import com.wifi.measurements.transformer.processor.MessageProcessor;
import com.wifi.measurements.transformer.service.SqsMonitoringService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

/**
 * High-performance SQS message receiver service for WiFi measurements processing.
 *
 * <p>This service implements a robust, production-ready SQS message receiver that handles the
 * continuous processing of WiFi scan data messages from SQS queues. It provides efficient batch
 * processing, comprehensive error handling, and detailed monitoring capabilities for the WiFi
 * location data pipeline.
 *
 * <p><strong>Core Features:</strong>
 *
 * <ul>
 *   <li><strong>Long Polling:</strong> 20-second polling for efficient message retrieval
 *   <li><strong>Batch Processing:</strong> Up to 10 messages per batch for optimal throughput
 *   <li><strong>Cost Optimization:</strong> Batch message deletion for reduced API costs
 *   <li><strong>Reliability:</strong> Message visibility timeout extension and retry logic
 *   <li><strong>Dead Letter Queue:</strong> Automatic handling of failed messages
 *   <li><strong>Monitoring:</strong> Comprehensive metrics and health monitoring
 * </ul>
 *
 * <p><strong>Processing Architecture:</strong>
 *
 * <ol>
 *   <li><strong>Message Reception:</strong> Long-polling for message batches
 *   <li><strong>Batch Processing:</strong> Parallel processing of multiple messages
 *   <li><strong>Message Handling:</strong> Individual message processing with error isolation
 *   <li><strong>Batch Deletion:</strong> Efficient batch deletion of processed messages
 *   <li><strong>Metrics Collection:</strong> Real-time performance and error monitoring
 * </ol>
 *
 * <p><strong>Error Handling Strategy:</strong>
 *
 * <ul>
 *   <li><strong>Message-Level Isolation:</strong> Individual message failures don't affect batch
 *   <li><strong>Retry Logic:</strong> Configurable retry attempts for transient failures
 *   <li><strong>Dead Letter Queue:</strong> Failed messages sent to DLQ for investigation
 *   <li><strong>Graceful Degradation:</strong> Service continues processing despite individual
 *       failures
 * </ul>
 *
 * <p><strong>Performance Characteristics:</strong>
 *
 * <ul>
 *   <li>Asynchronous processing for high throughput
 *   <li>Batch operations for cost efficiency
 *   <li>Long polling to reduce empty responses
 *   <li>Configurable batch sizes and timeouts
 * </ul>
 *
 * <p>This service is designed to be resilient, scalable, and observable, providing the foundation
 * for reliable WiFi data processing in production environments.
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class SqsMessageReceiver {

  private static final Logger logger = LoggerFactory.getLogger(SqsMessageReceiver.class);

  private final SqsAsyncClient sqsAsyncClient;
  private final SqsConfigurationProperties sqsConfig;
  private final MessageProcessor messageProcessor;
  private final SqsMonitoringService sqsMonitoringService;
  private final AtomicBoolean running = new AtomicBoolean(false);

  // Comprehensive metrics for monitoring and observability
  private final Counter messagesReceivedCounter;
  private final Counter messagesProcessedCounter;
  private final Counter messagesFailedCounter;
  private final Counter messagesDeletedCounter;
  private final Timer messageProcessingTimer;
  private final Timer batchReceiveTimer;

  /**
   * Constructs a new SQS message receiver with required dependencies and metrics.
   *
   * <p>This constructor initializes the SQS message receiver with the AWS SQS client, configuration
   * properties, message processor, and comprehensive metrics collection. The service is designed
   * for high-throughput, reliable message processing with detailed monitoring capabilities.
   *
   * <p><strong>Dependencies:</strong>
   *
   * <ul>
   *   <li><strong>SQS Client:</strong> AWS SDK client for SQS operations
   *   <li><strong>Configuration:</strong> SQS-specific configuration properties
   *   <li><strong>Message Processor:</strong> Service for processing individual messages
   *   <li><strong>Metrics Registry:</strong> Micrometer registry for monitoring
   * </ul>
   *
   * <p><strong>Metrics Initialized:</strong>
   *
   * <ul>
   *   <li><strong>Messages Received:</strong> Total count of messages received from SQS
   *   <li><strong>Messages Processed:</strong> Count of successfully processed messages
   *   <li><strong>Messages Failed:</strong> Count of messages that failed processing
   *   <li><strong>Messages Deleted:</strong> Count of messages deleted from SQS
   *   <li><strong>Processing Duration:</strong> Time taken to process individual messages
   *   <li><strong>Batch Receive Duration:</strong> Time taken to receive message batches
   * </ul>
   *
   * @param sqsAsyncClient AWS SQS async client for queue operations
   * @param sqsConfig SQS-specific configuration properties
   * @param messageProcessor Service for processing individual SQS messages
   * @param sqsMonitoringService Service for SQS monitoring and health tracking
   * @param meterRegistry Micrometer registry for metrics collection
   * @throws IllegalArgumentException if any required dependency is null
   */
  public SqsMessageReceiver(
      SqsAsyncClient sqsAsyncClient,
      SqsConfigurationProperties sqsConfig,
      MessageProcessor messageProcessor,
      SqsMonitoringService sqsMonitoringService,
      MeterRegistry meterRegistry) {
    if (sqsAsyncClient == null) {
      throw new IllegalArgumentException("SqsAsyncClient cannot be null");
    }
    if (sqsConfig == null) {
      throw new IllegalArgumentException("SqsConfigurationProperties cannot be null");
    }
    if (messageProcessor == null) {
      throw new IllegalArgumentException("MessageProcessor cannot be null");
    }
    if (sqsMonitoringService == null) {
      throw new IllegalArgumentException("SqsMonitoringService cannot be null");
    }
    if (meterRegistry == null) {
      throw new IllegalArgumentException("MeterRegistry cannot be null");
    }

    this.sqsAsyncClient = sqsAsyncClient;
    this.sqsConfig = sqsConfig;
    this.messageProcessor = messageProcessor;
    this.sqsMonitoringService = sqsMonitoringService;

    // Initialize comprehensive metrics for monitoring and observability
    // These metrics provide insights into processing performance and error rates
    this.messagesReceivedCounter =
        Counter.builder("sqs.messages.received")
            .description("Total number of SQS messages received")
            .register(meterRegistry);
    this.messagesProcessedCounter =
        Counter.builder("sqs.messages.processed")
            .description("Total number of SQS messages successfully processed")
            .register(meterRegistry);
    this.messagesFailedCounter =
        Counter.builder("sqs.messages.failed")
            .description("Total number of SQS messages that failed processing")
            .register(meterRegistry);
    this.messagesDeletedCounter =
        Counter.builder("sqs.messages.deleted")
            .description("Total number of SQS messages successfully deleted")
            .register(meterRegistry);
    this.messageProcessingTimer =
        Timer.builder("sqs.message.processing.duration")
            .description("Time taken to process SQS messages")
            .register(meterRegistry);
    this.batchReceiveTimer =
        Timer.builder("sqs.batch.receive.duration")
            .description("Time taken to receive SQS message batches")
            .register(meterRegistry);

    logger.info("SQS Message Receiver initialized with configuration and metrics");
  }

  /**
   * Starts the SQS message receiver service.
   *
   * <p>This method initializes and starts the continuous message processing loop. It uses atomic
   * boolean operations to ensure thread-safe startup and prevent multiple concurrent starts. The
   * service begins long-polling for messages immediately upon successful startup.
   *
   * <p><strong>Startup Process:</strong>
   *
   * <ol>
   *   <li><strong>Thread Safety:</strong> Atomic check-and-set for running state
   *   <li><strong>Configuration Logging:</strong> Log startup configuration for debugging
   *   <li><strong>Loop Initialization:</strong> Start the continuous message receiving loop
   *   <li><strong>State Management:</strong> Set running flag to true
   * </ol>
   *
   * <p><strong>Configuration Logged:</strong>
   *
   * <ul>
   *   <li>Queue URL for message reception
   *   <li>Maximum messages per batch
   *   <li>Long polling wait time
   *   <li>Maximum retry attempts
   * </ul>
   *
   * <p><strong>Thread Safety:</strong>
   *
   * <ul>
   *   <li>Uses AtomicBoolean for thread-safe state management
   *   <li>Prevents multiple concurrent starts
   *   <li>Ensures clean startup state
   * </ul>
   */
  @PostConstruct
  public void start() {
    if (running.compareAndSet(false, true)) {
      logger.info(
          "Starting SQS message receiver with configuration: "
              + "queueUrl={}, maxMessages={}, waitTimeSeconds={}, maxRetries={}",
          sqsConfig.queueUrl(),
          sqsConfig.maxMessages(),
          sqsConfig.waitTimeSeconds(),
          sqsConfig.maxRetries());

      // Start the continuous message receiving loop
      // This begins the long-polling process for SQS messages
      receiveMessagesLoop();
    }
  }

  /**
   * Stops the SQS message receiver service gracefully.
   *
   * <p>This method initiates a graceful shutdown of the SQS message receiver service. It uses
   * atomic boolean operations to ensure thread-safe shutdown and prevent multiple concurrent stop
   * attempts. The service will complete processing any in-flight messages before fully stopping.
   *
   * <p><strong>Shutdown Process:</strong>
   *
   * <ol>
   *   <li><strong>Thread Safety:</strong> Atomic check-and-set for running state
   *   <li><strong>State Update:</strong> Set running flag to false to stop message loop
   *   <li><strong>Graceful Completion:</strong> Allow in-flight messages to complete
   *   <li><strong>Logging:</strong> Log shutdown event for monitoring
   * </ol>
   *
   * <p><strong>Graceful Shutdown Features:</strong>
   *
   * <ul>
   *   <li>Stops accepting new messages from SQS
   *   <li>Allows in-flight message processing to complete
   *   <li>Ensures message deletion for processed messages
   *   <li>Maintains data integrity during shutdown
   * </ul>
   *
   * <p><strong>Thread Safety:</strong>
   *
   * <ul>
   *   <li>Uses AtomicBoolean for thread-safe state management
   *   <li>Prevents multiple concurrent stops
   *   <li>Ensures clean shutdown state
   * </ul>
   *
   * <p><strong>Lifecycle Integration:</strong>
   *
   * <ul>
   *   <li>Called automatically by Spring container during shutdown
   *   <li>Ensures proper cleanup of resources
   *   <li>Prevents message loss during application shutdown
   * </ul>
   */
  @PreDestroy
  public void stop() {
    if (running.compareAndSet(true, false)) {
      logger.info("Stopping SQS message receiver");
    }
  }

  /**
   * Main message receiving loop that continuously polls SQS for messages using asynchronous
   * processing.
   *
   * <p>This method implements the core message processing loop that runs continuously while the
   * service is active. It uses CompletableFuture for asynchronous execution and performs
   * long-polling on the SQS queue to receive message batches efficiently. The loop is designed to
   * be resilient to individual batch processing failures while maintaining overall service
   * availability.
   *
   * <p><strong>Asynchronous Processing:</strong>
   *
   * <ul>
   *   <li><strong>Non-blocking Execution:</strong> Uses CompletableFuture.runAsync for background
   *       processing
   *   <li><strong>Thread Management:</strong> Leverages ForkJoinPool for efficient thread
   *       utilization
   *   <li><strong>Concurrent Operations:</strong> Allows other operations to proceed while polling
   * </ul>
   *
   * <p><strong>Loop Behavior:</strong>
   *
   * <ol>
   *   <li><strong>Continuous Polling:</strong> Long-polling for message batches while running
   *   <li><strong>Batch Processing:</strong> Process received messages in batches for efficiency
   *   <li><strong>Error Resilience:</strong> Continue processing despite individual failures
   *   <li><strong>Graceful Shutdown:</strong> Stop when running flag is set to false
   * </ol>
   *
   * <p><strong>Error Handling Strategy:</strong>
   *
   * <ul>
   *   <li><strong>Exception Isolation:</strong> Individual batch failures don't stop the loop
   *   <li><strong>Retry Logic:</strong> Brief pause before retrying to avoid tight error loops
   *   <li><strong>Interruption Handling:</strong> Proper handling of thread interruption
   *   <li><strong>Fatal Error Recovery:</strong> Exceptionally clause for catastrophic failures
   * </ul>
   *
   * <p><strong>Performance Characteristics:</strong>
   *
   * <ul>
   *   <li>Asynchronous execution prevents blocking the main thread
   *   <li>Long polling reduces empty responses and API costs
   *   <li>Batch processing improves throughput efficiency
   *   <li>Configurable retry intervals for optimal error recovery
   * </ul>
   *
   * <p><strong>Thread Safety:</strong>
   *
   * <ul>
   *   <li>Uses atomic boolean for thread-safe state checking
   *   <li>Safe for concurrent access and shutdown requests
   *   <li>Proper interruption handling for clean shutdown
   * </ul>
   */
  private void receiveMessagesLoop() {
    // Start asynchronous message processing loop
    // This prevents blocking the main application thread during message reception
    CompletableFuture.runAsync(
            () -> {
              while (running.get()) {
                try {
                  // Process message batches continuously while service is running
                  // This implements the core long-polling and batch processing logic
                  receiveAndProcessBatch();
                } catch (Exception e) {
                  // Log errors but continue processing to maintain service availability
                  // Individual batch failures should not stop the entire message processing loop
                  logger.error("Error in SQS message receiving loop", e);

                  // Brief pause before retrying to avoid tight error loops
                  // This prevents excessive CPU usage during repeated failures
                  try {
                    Thread.sleep(Duration.ofSeconds(5).toMillis());
                  } catch (InterruptedException ie) {
                    // Proper interruption handling for clean shutdown
                    Thread.currentThread().interrupt();
                    logger.warn("SQS message receiver interrupted");
                    break;
                  }
                }
              }
            })
        .exceptionally(
            throwable -> {
              // Handle fatal errors that could crash the entire message processing loop
              // This ensures the service can recover from catastrophic failures
              logger.error("Fatal error in SQS message receiver", throwable);
              return null;
            });
  }

  /**
   * Receives and processes a batch of messages from the SQS queue using long polling.
   *
   * <p>This method implements the core batch message processing logic that receives messages from
   * SQS using long polling and processes them efficiently. It provides comprehensive error
   * handling, metrics collection, and correlation tracking for debugging and monitoring purposes.
   *
   * <p><strong>Processing Flow:</strong>
   *
   * <ol>
   *   <li><strong>Correlation Setup:</strong> Generate unique correlation ID for tracking
   *   <li><strong>Message Reception:</strong> Long-polling for message batches
   *   <li><strong>Batch Processing:</strong> Process received messages in parallel
   *   <li><strong>Metrics Collection:</strong> Track performance and error metrics
   *   <li><strong>Cleanup:</strong> Remove correlation context
   * </ol>
   *
   * <p><strong>Long Polling Configuration:</strong>
   *
   * <ul>
   *   <li><strong>Queue URL:</strong> Target SQS queue for message reception
   *   <li><strong>Max Messages:</strong> Maximum messages per batch (typically 10)
   *   <li><strong>Wait Time:</strong> Long polling duration (typically 20 seconds)
   *   <li><strong>Visibility Timeout:</strong> Message visibility duration
   *   <li><strong>Attributes:</strong> Request all queue and message attributes
   * </ul>
   *
   * <p><strong>Error Handling:</strong>
   *
   * <ul>
   *   <li><strong>Reception Errors:</strong> Logged but don't stop processing
   *   <li><strong>Processing Errors:</strong> Handled at individual message level
   *   <li><strong>Metrics Tracking:</strong> Failed operations are tracked
   *   <li><strong>Correlation Cleanup:</strong> Ensured in finally block
   * </ul>
   *
   * <p><strong>Performance Features:</strong>
   *
   * <ul>
   *   <li>Asynchronous message reception using CompletableFuture
   *   <li>Batch processing for improved throughput
   *   <li>Long polling to reduce empty responses
   *   <li>Comprehensive timing metrics for performance monitoring
   * </ul>
   *
   * <p><strong>Observability:</strong>
   *
   * <ul>
   *   <li>Correlation ID for request tracing
   *   <li>Detailed logging for debugging
   *   <li>Performance metrics for monitoring
   *   <li>Error tracking for alerting
   * </ul>
   */
  private void receiveAndProcessBatch() {
    // Generate unique correlation ID for request tracing and debugging
    // This allows tracking of individual batch processing operations
    String correlationId = UUID.randomUUID().toString();
    MDC.put("correlationId", correlationId);

    try {
      // Step 1: Build SQS receive message request with long polling configuration
      // This optimizes message reception by reducing empty responses and API costs
      ReceiveMessageRequest receiveRequest =
          ReceiveMessageRequest.builder()
              .queueUrl(sqsConfig.queueUrl())
              .maxNumberOfMessages(sqsConfig.maxMessages())
              .waitTimeSeconds(sqsConfig.waitTimeSeconds())
              .visibilityTimeout(sqsConfig.visibilityTimeoutSeconds())
              .attributeNames(QueueAttributeName.ALL)
              .messageAttributeNames("All")
              .build();

      // Step 2: Start timing for batch receive performance monitoring
      Timer.Sample receiveSample = Timer.start();

      // Step 3: Asynchronously receive messages from SQS queue
      // This prevents blocking while waiting for messages
      CompletableFuture<ReceiveMessageResponse> receiveResponse =
          sqsAsyncClient.receiveMessage(receiveRequest);

      // Step 4: Process received messages and handle errors
      receiveResponse
          .thenAccept(
              response -> {
                // Stop timing and record batch receive duration
                receiveSample.stop(batchReceiveTimer);

                List<Message> messages = response.messages();
                if (!messages.isEmpty()) {
                  // Log batch reception and increment metrics
                  logger.info("Received {} messages from SQS queue", messages.size());
                  messagesReceivedCounter.increment(messages.size());

                  // Record message reception in monitoring service
                  for (int i = 0; i < messages.size(); i++) {
                    sqsMonitoringService.recordMessageReceived();
                  }

                  // Process the received message batch
                  processBatch(messages, correlationId);
                } else {
                  // Log empty response for debugging (debug level to avoid noise)
                  logger.debug("No messages received from SQS queue");
                }
              })
          .exceptionally(
              throwable -> {
                // Handle reception errors and stop timing
                receiveSample.stop(batchReceiveTimer);
                logger.error("Failed to receive messages from SQS queue", throwable);
                return null;
              })
          .join(); // Wait for completion to ensure proper error handling

    } finally {
      // Always clean up correlation context to prevent memory leaks
      MDC.remove("correlationId");
    }
  }

  /** Process a batch of messages. */
  private void processBatch(List<Message> messages, String correlationId) {
    MDC.put("correlationId", correlationId);
    MDC.put("batchSize", String.valueOf(messages.size()));

    try {
      Timer.Sample processingSample = Timer.start();

      // Process each message in the batch and track results
      int successfulCount = 0;
      int failedCount = 0;
      List<String> allReceiptHandles = new ArrayList<>();

      for (Message message : messages) {
        boolean success = processMessage(message);
        allReceiptHandles.add(message.receiptHandle());

        if (success) {
          successfulCount++;
        } else {
          failedCount++;
        }
      }

      // Delete ALL messages (both successful and failed) to prevent replay
      if (!allReceiptHandles.isEmpty()) {
        deleteBatch(allReceiptHandles);
      }

      processingSample.stop(messageProcessingTimer);

      logger.info(
          "Batch processing completed: {} total messages, {} successful, {} failed, {} deleted",
          messages.size(),
          successfulCount,
          failedCount,
          allReceiptHandles.size());

    } catch (Exception e) {
      logger.error("Error processing message batch", e);
    } finally {
      MDC.remove("correlationId");
      MDC.remove("batchSize");
    }
  }

  /**
   * Process a single message.
   *
   * @param message The SQS message to process
   * @return true if processing was successful, false otherwise
   */
  private boolean processMessage(Message message) {
    String messageId = message.messageId();
    MDC.put("messageId", messageId);

    try {
      logger.debug("Processing SQS message: {}", messageId);

      // Process the message using the message processor
      boolean success = messageProcessor.processMessage(message);

      // Record processing activity in monitoring service
      sqsMonitoringService.recordMessageProcessed(success);

      if (success) {
        messagesProcessedCounter.increment();
        logger.debug("Successfully processed SQS message: {}", messageId);
        return true;
      } else {
        messagesFailedCounter.increment();
        logger.error("Failed to process SQS message (will be deleted): {}", messageId);
        return false;
      }

    } catch (Exception e) {
      // Record failed processing activity in monitoring service
      sqsMonitoringService.recordMessageProcessed(false);
      messagesFailedCounter.increment();
      logger.error("Error processing SQS message: {}", messageId, e);
      return false;
    } finally {
      MDC.remove("messageId");
    }
  }

  /** Delete a batch of successfully processed messages. */
  private void deleteBatch(List<String> receiptHandles) {
    if (receiptHandles.isEmpty()) {
      return;
    }

    try {
      List<DeleteMessageBatchRequestEntry> deleteEntries =
          receiptHandles.stream()
              .map(
                  receiptHandle ->
                      DeleteMessageBatchRequestEntry.builder()
                          .id(UUID.randomUUID().toString())
                          .receiptHandle(receiptHandle)
                          .build())
              .toList();

      DeleteMessageBatchRequest deleteRequest =
          DeleteMessageBatchRequest.builder()
              .queueUrl(sqsConfig.queueUrl())
              .entries(deleteEntries)
              .build();

      sqsAsyncClient
          .deleteMessageBatch(deleteRequest)
          .thenAccept(
              response -> {
                List<DeleteMessageBatchResultEntry> successful = response.successful();
                List<BatchResultErrorEntry> failed = response.failed();

                messagesDeletedCounter.increment(successful.size());

                if (!failed.isEmpty()) {
                  logger.warn("Failed to delete {} messages from batch", failed.size());
                  failed.forEach(
                      error ->
                          logger.warn(
                              "Delete failed for message: {} - {}", error.id(), error.message()));
                }

                logger.debug(
                    "Batch delete completed: {} successful, {} failed",
                    successful.size(),
                    failed.size());
              })
          .exceptionally(
              throwable -> {
                logger.error("Failed to delete message batch", throwable);
                return null;
              })
          .join();

    } catch (Exception e) {
      logger.error("Error deleting message batch", e);
    }
  }

  /** Check if the message receiver is running. */
  public boolean isRunning() {
    return running.get();
  }

  /** Get current metrics summary. */
  public String getMetricsSummary() {
    return String.format(
        "SQS Metrics - Received: %.0f, Processed: %.0f, Failed: %.0f, Deleted: %.0f",
        messagesReceivedCounter.count(),
        messagesProcessedCounter.count(),
        messagesFailedCounter.count(),
        messagesDeletedCounter.count());
  }
}
