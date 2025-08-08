package com.wifi.scan.consume.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.wifi.scan.consume.config.FirehoseBatchConfiguration;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchResponse;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchResponseEntry;
import software.amazon.awssdk.services.firehose.model.Record;

/**
 * Service for delivering pre-compressed message batches to AWS Kinesis Data Firehose.
 *
 * <p>This service implements efficient batch delivery to AWS Kinesis Data Firehose with intelligent
 * sub-batching, rate limiting integration, and comprehensive metrics tracking. It handles the
 * complex requirements of Firehose delivery including size limits, batch constraints, and enhanced
 * error handling with intelligent retry strategies.
 *
 * <p>Key Functionality: - Intelligent sub-batch sizing based on message size and rate limits -
 * Firehose-compliant batch delivery with size and count limits - Enhanced exception classification
 * and retry strategies - Buffer full scenario handling with graduated backoff - Comprehensive
 * metrics collection and reporting - Connectivity testing and health monitoring - Error handling
 * and failed record logging
 *
 * <p>High-Level Processing Steps: 1. Receive pre-compressed messages from upstream services 2.
 * Calculate optimal sub-batch sizes based on constraints 3. Create sub-batches respecting Firehose
 * limits 4. Deliver sub-batches to Firehose with intelligent error handling 5. Apply
 * exception-specific retry strategies (Buffer Full, Rate Limit, Network, Generic) 6. Track metrics
 * and handle failed deliveries 7. Provide connectivity testing and health status
 *
 * <p>Firehose Constraints Handled: - Maximum batch size: 500 records per batch - Maximum batch
 * size: 4MB per batch - Maximum record size: 1MB per record - Rate limiting integration for
 * downstream protection - Buffer full scenarios with intelligent backoff
 *
 * <p>NOTE: Messages are expected to be pre-compressed and encoded by MessageCompressionService.
 * This service focuses solely on efficient delivery to Firehose.
 *
 * @see MessageCompressionService
 * @see FirehoseClient
 * @see FirehoseExceptionClassifier
 * @see EnhancedRetryStrategy
 */
@Slf4j
@Service
public class BatchFirehoseMessageService {

  /** AWS Kinesis Data Firehose client for batch delivery operations */
  private final FirehoseClient firehoseClient;

  /** Name of the Firehose delivery stream for message routing */
  private final String deliveryStreamName;

  /** Exception classifier for intelligent error handling */
  private final FirehoseExceptionClassifier exceptionClassifier;

  /** Enhanced retry strategy for different exception types */
  private final EnhancedRetryStrategy retryStrategy;

  /** Firehose batch configuration for constraints */
  private final FirehoseBatchConfiguration batchConfiguration;

  // Batch metrics tracking with thread-safe atomic counters

  /** Counter for successfully delivered batches */
  private final AtomicLong successfulBatches = new AtomicLong(0);

  /** Counter for failed batch deliveries */
  private final AtomicLong failedBatches = new AtomicLong(0);

  /** Counter for total individual messages processed */
  private final AtomicLong totalMessagesProcessed = new AtomicLong(0);

  /** Counter for total bytes processed across all batches */
  private final AtomicLong totalBytesProcessed = new AtomicLong(0);

  // Enhanced metrics for buffer management

  /** Counter for buffer full retry attempts */
  private final AtomicLong bufferFullRetries = new AtomicLong(0);

  /** Counter for successful buffer full recoveries */
  private final AtomicLong bufferFullRecoveries = new AtomicLong(0);

  /** Counter for messages held during buffer full retries */
  private final AtomicLong messagesHeldDuringRetries = new AtomicLong(0);

  // Maximum record size limit for AWS Firehose compatibility
  private static final long MAX_RECORD_SIZE_BYTES = 1024 * 1024; // 1MB per record

  // Note: Firehose constraints are now managed through FirehoseBatchConfiguration

  /**
   * Creates a new BatchFirehoseMessageService with required dependencies.
   *
   * @param firehoseClient AWS Firehose client for batch delivery operations
   * @param deliveryStreamName Name of the Firehose delivery stream
   * @param exceptionClassifier Exception classifier for intelligent error handling
   * @param retryStrategy Enhanced retry strategy for different exception types
   * @param batchConfiguration Configuration for Firehose batch constraints
   */
  @Autowired
  public BatchFirehoseMessageService(
      FirehoseClient firehoseClient,
      @Qualifier("deliveryStreamName") String deliveryStreamName,
      FirehoseExceptionClassifier exceptionClassifier,
      EnhancedRetryStrategy retryStrategy,
      FirehoseBatchConfiguration batchConfiguration) {
    this.firehoseClient = firehoseClient;
    this.deliveryStreamName = deliveryStreamName;
    this.exceptionClassifier = exceptionClassifier;
    this.retryStrategy = retryStrategy;
    this.batchConfiguration = batchConfiguration;
  }

  /**
   * Create sub-batches from messages with optimal sizing based on multiple constraints.
   *
   * <p>This method creates optimally-sized sub-batches from a list of messages, respecting ALL
   * Firehose limits including record count, batch size in bytes, and individual message sizes. It
   * uses intelligent partitioning to maximize throughput while staying within AWS Kinesis Data
   * Firehose constraints.
   *
   * <p>Processing Steps: 1. Validate input messages for null or empty conditions 2. Initialize
   * sub-batch tracking with size and count limits 3. For each message, calculate its byte size 4.
   * Check if adding the message would exceed either limit: - Record count limit (maxBatchSize
   * parameter) - Byte size limit (maxBatchSizeBytes parameter) 5. If either limit would be
   * exceeded, start a new sub-batch 6. Add message to current sub-batch and update counters 7.
   * Finalize and return all sub-batches
   *
   * <p>Sub-batch Creation Logic: - Each sub-batch respects both record count AND byte size limits -
   * Messages are processed sequentially to maintain order - Empty sub-batches are avoided through
   * proper validation - Single large messages get their own sub-batch if needed
   *
   * <p>Constraint Handling: - Record Count: Maximum specified by maxBatchSize parameter - Byte
   * Size: Maximum specified by maxBatchSizeBytes parameter - Individual Size: Should be
   * pre-validated by caller
   *
   * <p>Performance Characteristics: - O(n) time complexity where n is number of messages - Minimal
   * memory overhead with incremental size tracking - Optimal sub-batch utilization maximizing
   * Firehose throughput
   *
   * @param messages List of pre-compressed messages to be sub-batched
   * @param maxBatchSize Maximum number of records per sub-batch
   * @param maxBatchSizeBytes Maximum total size in bytes per sub-batch
   * @return List of sub-batches, each optimized for Firehose delivery
   */
  public List<List<String>> createSubBatches(
      List<String> messages, int maxBatchSize, long maxBatchSizeBytes) {
    if (messages == null || messages.isEmpty()) {
      return List.of();
    }

    List<List<String>> subBatches = new ArrayList<>();
    List<String> currentSubBatch = new ArrayList<>();
    long currentSubBatchSizeBytes = 0;

    log.debug(
        "Creating sub-batches from {} messages with dual constraints: {} records and {} bytes",
        messages.size(),
        maxBatchSize,
        maxBatchSizeBytes);

    for (String message : messages) {
      if (message == null || message.trim().isEmpty()) {
        log.warn("Skipping null or empty message during sub-batch creation");
        continue;
      }

      // Calculate message size in bytes (UTF-8 encoding)
      long messageSize;
      try {
        messageSize = message.getBytes("UTF-8").length;
      } catch (Exception e) {
        log.warn("Failed to calculate message size, skipping message: {}", e.getMessage());
        continue;
      }

      // Check if adding this message would exceed either limit
      boolean exceedsRecordLimit = currentSubBatch.size() >= maxBatchSize;
      boolean exceedsSizeLimit = (currentSubBatchSizeBytes + messageSize) > maxBatchSizeBytes;

      // Start new sub-batch if either limit would be exceeded
      if ((exceedsRecordLimit || exceedsSizeLimit) && !currentSubBatch.isEmpty()) {
        subBatches.add(new ArrayList<>(currentSubBatch));
        log.trace(
            "Created sub-batch: {} records, {} bytes (triggered by {} limit)",
            currentSubBatch.size(),
            currentSubBatchSizeBytes,
            exceedsRecordLimit ? "record count" : "size");

        currentSubBatch.clear();
        currentSubBatchSizeBytes = 0;
      }

      // Add message to current sub-batch
      currentSubBatch.add(message);
      currentSubBatchSizeBytes += messageSize;
    }

    // Add the final sub-batch if it contains messages
    if (!currentSubBatch.isEmpty()) {
      subBatches.add(currentSubBatch);
      log.trace(
          "Final sub-batch: {} records, {} bytes",
          currentSubBatch.size(),
          currentSubBatchSizeBytes);
    }

    // Log comprehensive sub-batch statistics
    if (log.isDebugEnabled()) {
      long totalMessages = subBatches.stream().mapToLong(List::size).sum();
      int maxRecordsInSubBatch = subBatches.stream().mapToInt(List::size).max().orElse(0);

      log.debug(
          "Sub-batch creation complete: {} sub-batches from {} messages, max {} records per sub-batch",
          subBatches.size(),
          totalMessages,
          maxRecordsInSubBatch);
    }

    return subBatches;
  }

  /**
   * Process and deliver a batch of pre-compressed messages to Firehose.
   *
   * <p>This method orchestrates the complete batch delivery process including sub-batch creation,
   * delivery execution, and metrics tracking. It handles the complex workflow of delivering large
   * message batches while respecting Firehose constraints.
   *
   * <p>Processing Steps: 1. Validate input messages and handle empty batches 2. Create
   * optimally-sized sub-batches using Firehose constraints 3. Deliver each sub-batch to Firehose
   * with error handling 4. Track delivery success/failure for all sub-batches 5. Update metrics
   * based on overall batch success 6. Log comprehensive delivery results
   *
   * <p>Error Handling: - Empty or null batches are considered successful - Individual sub-batch
   * failures are logged and tracked - Overall batch success requires all sub-batches to succeed -
   * Exceptions are caught and logged with appropriate metrics updates
   *
   * <p>Metrics Tracking: - Successful batches counter - Failed batches counter - Total messages
   * processed - Total bytes processed
   *
   * @param messages the batch of pre-compressed messages to deliver
   * @return true if all sub-batches were delivered successfully, false otherwise
   */
  public boolean deliverBatch(List<String> messages) {
    if (messages == null || messages.isEmpty()) {
      log.warn("Cannot deliver null or empty message batch to Firehose");
      return true; // Empty batch is considered successful
    }

    log.info(
        "Processing batch of {} pre-compressed messages for Firehose delivery", messages.size());

    try {
      // Step 1: Create sub-batches with simple, efficient algorithm
      List<List<String>> subBatches =
          createSubBatches(
              messages,
              batchConfiguration.getMaxBatchSize(),
              batchConfiguration.getMaxBatchSizeBytes());
      log.debug("Split into {} sub-batches", subBatches.size());

      // Step 2: Process all sub-batches
      boolean allSuccessful = true;
      for (int i = 0; i < subBatches.size(); i++) {
        List<String> subBatch = subBatches.get(i);
        boolean subBatchSuccess = deliverSubBatch(subBatch, i + 1, subBatches.size());
        allSuccessful = allSuccessful && subBatchSuccess;
      }

      // Step 3: Update metrics
      if (allSuccessful) {
        successfulBatches.incrementAndGet();
        totalMessagesProcessed.addAndGet(messages.size());
        log.info(
            "Successfully delivered entire batch: {} messages in {} sub-batches",
            messages.size(),
            subBatches.size());
      } else {
        failedBatches.incrementAndGet();
        log.error("Failed to deliver complete batch: {} messages", messages.size());
      }

      return allSuccessful;

    } catch (Exception e) {
      log.error("Failed to deliver message batch to Firehose: {}", e.getMessage(), e);
      failedBatches.incrementAndGet();
      return false;
    }
  }

  /**
   * Deliver a single sub-batch to Firehose with enhanced error handling and intelligent retry.
   *
   * <p>This method handles the delivery of a single sub-batch to AWS Kinesis Data Firehose,
   * including record creation, batch request building, response processing, and intelligent retry
   * strategies based on exception classification. It implements buffer full handling, rate limiting
   * awareness, and network issue recovery.
   *
   * <p>Processing Steps: 1. Convert pre-compressed messages to Firehose Record objects 2. Filter
   * out invalid or null records 3. Build PutRecordBatchRequest with delivery stream configuration
   * 4. Attempt delivery with intelligent retry strategy 5. Classify exceptions and apply
   * appropriate retry logic 6. Handle buffer full scenarios with graduated backoff 7. Track retry
   * metrics and recovery patterns 8. Log detailed results for monitoring and debugging
   *
   * <p>Enhanced Error Handling: - Exception classification (Buffer Full, Rate Limit, Network Issue,
   * Generic) - Graduated backoff for buffer full (5s → 15s → 45s → 2min → 5min) - Standard retry
   * for rate limiting and network issues - Comprehensive retry metrics and tracking
   *
   * <p>Pre-compressed Message Handling: - Messages are expected to be already compressed and
   * encoded - No additional compression is performed - Direct conversion to Firehose Record format
   *
   * @param compressedSubBatch List of pre-compressed messages to deliver
   * @param subBatchIndex Current sub-batch index for logging and tracking
   * @param totalSubBatches Total number of sub-batches for progress tracking
   * @return true if sub-batch was delivered successfully, false otherwise
   */
  private boolean deliverSubBatch(
      List<String> compressedSubBatch, int subBatchIndex, int totalSubBatches) {
    if (compressedSubBatch == null || compressedSubBatch.isEmpty()) {
      log.warn("Null or empty sub-batch {}/{}", subBatchIndex, totalSubBatches);
      return false;
    }

    log.debug(
        "Processing pre-compressed sub-batch {}/{}: {} messages",
        subBatchIndex,
        totalSubBatches,
        compressedSubBatch.size());

    // Step 1: Convert pre-compressed messages directly to Firehose records
    List<Record> records =
        compressedSubBatch.stream()
            .map(this::createFirehoseRecordFromCompressed)
            .filter(record -> record != null) // Remove failed transformations
            .toList();

    if (records.isEmpty()) {
      log.warn(
          "No valid records in pre-compressed sub-batch {}/{}", subBatchIndex, totalSubBatches);
      return false;
    }

    // Step 2: Attempt delivery with intelligent retry strategy
    return deliverSubBatchWithRetry(
        records, compressedSubBatch.size(), subBatchIndex, totalSubBatches);
  }

  /** Deliver sub-batch with intelligent retry strategy based on exception classification. */
  private boolean deliverSubBatchWithRetry(
      List<Record> records, int originalMessageCount, int subBatchIndex, int totalSubBatches) {
    int maxRetries = 0;
    FirehoseExceptionClassifier.ExceptionType lastExceptionType = null;

    for (int attempt = 0; attempt < 8; attempt++) {
      try {
        PutRecordBatchRequest request =
            PutRecordBatchRequest.builder()
                .deliveryStreamName(deliveryStreamName)
                .records(records)
                .build();

        PutRecordBatchResponse response = firehoseClient.putRecordBatch(request);

        if (handleSuccessfulResponse(
            response, subBatchIndex, totalSubBatches, records.size(), lastExceptionType, attempt)) {
          return true;
        }

        log.warn(
            "Sub-batch {}/{} delivery failed without exception - not retrying",
            subBatchIndex,
            totalSubBatches);
        return false;

      } catch (Exception e) {
        FirehoseExceptionClassifier.ExceptionType exceptionType =
            exceptionClassifier.classifyException(e);
        lastExceptionType = exceptionType;

        if (attempt == 0) {
          maxRetries = getMaxRetriesForExceptionType(exceptionType);
        }

        if (shouldStopRetrying(
            exceptionType,
            attempt,
            maxRetries,
            subBatchIndex,
            totalSubBatches,
            originalMessageCount)) {
          return false;
        }

        trackRetryMetrics(exceptionType, originalMessageCount);

        if (!waitForRetry(exceptionType, attempt, maxRetries, subBatchIndex, totalSubBatches)) {
          return false;
        }
      }
    }
    return false;
  }

  private boolean handleSuccessfulResponse(
      PutRecordBatchResponse response,
      int subBatchIndex,
      int totalSubBatches,
      int recordCount,
      FirehoseExceptionClassifier.ExceptionType lastExceptionType,
      int attempt) {
    boolean success = handleFirehoseResponse(response, subBatchIndex, totalSubBatches, recordCount);
    if (success
        && lastExceptionType == FirehoseExceptionClassifier.ExceptionType.BUFFER_FULL
        && attempt > 0) {
      bufferFullRecoveries.incrementAndGet();
      log.info(
          "Buffer full recovery successful for sub-batch {}/{} after {} attempts",
          subBatchIndex,
          totalSubBatches,
          attempt + 1);
    }
    return success;
  }

  private boolean shouldStopRetrying(
      FirehoseExceptionClassifier.ExceptionType exceptionType,
      int attempt,
      int maxRetries,
      int subBatchIndex,
      int totalSubBatches,
      int originalMessageCount) {
    log.warn(
        "Sub-batch {}/{} delivery attempt {} failed with {}",
        subBatchIndex,
        totalSubBatches,
        attempt + 1,
        exceptionType);

    if (!retryStrategy.shouldRetry(exceptionType, attempt) || attempt >= maxRetries - 1) {
      log.error(
          "Sub-batch {}/{} delivery failed after {} attempts with {}",
          subBatchIndex,
          totalSubBatches,
          attempt + 1,
          exceptionType);

      if (exceptionType == FirehoseExceptionClassifier.ExceptionType.BUFFER_FULL) {
        messagesHeldDuringRetries.addAndGet(originalMessageCount);
      }
      return true;
    }
    return false;
  }

  private void trackRetryMetrics(
      FirehoseExceptionClassifier.ExceptionType exceptionType, int originalMessageCount) {
    if (exceptionType == FirehoseExceptionClassifier.ExceptionType.BUFFER_FULL) {
      bufferFullRetries.incrementAndGet();
      messagesHeldDuringRetries.addAndGet(originalMessageCount);
    }
  }

  private boolean waitForRetry(
      FirehoseExceptionClassifier.ExceptionType exceptionType,
      int attempt,
      int maxRetries,
      int subBatchIndex,
      int totalSubBatches) {
    try {
      java.time.Duration retryDelay = retryStrategy.calculateRetryDelay(exceptionType, attempt);
      log.info(
          "Retrying sub-batch {}/{} in {} for {} (attempt {} of {})",
          subBatchIndex,
          totalSubBatches,
          retryDelay,
          exceptionType,
          attempt + 2,
          maxRetries);
      Thread.sleep(retryDelay.toMillis());
      return true;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      log.warn("Retry delay interrupted for sub-batch {}/{}", subBatchIndex, totalSubBatches);
      return false;
    }
  }

  /**
   * Get maximum retry attempts for different exception types.
   *
   * @param exceptionType The classified exception type
   * @return Maximum number of retry attempts for this exception type
   */
  private int getMaxRetriesForExceptionType(
      FirehoseExceptionClassifier.ExceptionType exceptionType) {
    switch (exceptionType) {
      case BUFFER_FULL:
        return 7; // 5s → 15s → 45s → 2min → 5min → 5min → 5min (total ~15 minutes)
      case RATE_LIMIT:
        return 6; // 1s → 2s → 4s → 8s → 16s → 30s
      case NETWORK_ISSUE:
        return 5; // 1s → 2s → 4s → 8s → 16s (quick recovery for transient issues)
      case GENERIC_FAILURE:
      default:
        return 5; // Standard retry attempts
    }
  }

  /**
   * Create Firehose record from pre-compressed message content.
   *
   * <p>This method converts pre-compressed and encoded message strings into AWS Kinesis Data
   * Firehose Record objects. It performs validation, size checking, and proper byte conversion for
   * Firehose delivery.
   *
   * <p>Processing Steps: 1. Validate input message for null or empty content 2. Convert compressed
   * string to UTF-8 bytes 3. Validate record size against Firehose limits (1MB) 4. Update total
   * bytes processed metrics 5. Create Firehose Record with SdkBytes wrapper 6. Log detailed
   * information for monitoring
   *
   * <p>Validation Logic: - Null or empty messages are rejected - Size validation against
   * MAX_RECORD_SIZE_BYTES (1MB) - UTF-8 encoding validation
   *
   * <p>Metrics Integration: - Updates totalBytesProcessed counter - Tracks individual record sizes
   * - Provides trace-level logging for debugging
   *
   * <p>Pre-compressed Message Assumptions: - Message is already compressed (GZIP) - Message is
   * already Base64 encoded - No additional compression or encoding needed
   *
   * @param compressedMessage Pre-compressed and encoded message string
   * @return Firehose Record object or null if validation fails
   */
  private Record createFirehoseRecordFromCompressed(String compressedMessage) {
    try {
      if (compressedMessage == null || compressedMessage.trim().isEmpty()) {
        log.warn("Skipping null or empty pre-compressed message");
        return null;
      }

      // Convert compressed string directly to bytes for Firehose
      byte[] finalData = compressedMessage.getBytes("UTF-8");

      // Validate size limit for Firehose compatibility
      if (finalData.length > MAX_RECORD_SIZE_BYTES) {
        log.error(
            "Pre-compressed message too large: {} bytes (limit: {} bytes)",
            finalData.length,
            MAX_RECORD_SIZE_BYTES);
        log.error("Dropping message content: {}", compressedMessage);
        return null;
      }

      totalBytesProcessed.addAndGet(finalData.length);

      log.trace("Pre-compressed message ready for Firehose: {} bytes", finalData.length);

      return Record.builder().data(SdkBytes.fromByteArray(finalData)).build();

    } catch (Exception e) {
      log.error("Failed to create Firehose record from pre-compressed message: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Handle Firehose batch response and check for failures.
   *
   * <p>This method processes the response from AWS Kinesis Data Firehose after a batch delivery
   * attempt. It analyzes the response for failures, logs detailed error information, and determines
   * overall success status.
   *
   * <p>Processing Steps: 1. Validate response object for null values 2. Check failedPutCount for
   * any delivery failures 3. Log detailed failure information if any records failed 4. Log success
   * information for monitoring 5. Return boolean success status
   *
   * <p>Failure Analysis: - Checks failedPutCount from Firehose response - Logs detailed error
   * information for failed records - Provides sub-batch context for debugging - Handles null
   * responses gracefully
   *
   * <p>Success Criteria: - All records in the sub-batch must be delivered successfully -
   * failedPutCount must be 0 for success - Response object must be valid
   *
   * <p>Logging Strategy: - Error level for failures with detailed context - Debug level for
   * successful deliveries - Detailed failure information for troubleshooting
   *
   * @param response Firehose PutRecordBatchResponse to analyze
   * @param subBatchIndex Current sub-batch index for logging context
   * @param totalSubBatches Total number of sub-batches for progress tracking
   * @param recordCount Number of records in the sub-batch
   * @return true if all records were delivered successfully, false otherwise
   */
  private boolean handleFirehoseResponse(
      PutRecordBatchResponse response, int subBatchIndex, int totalSubBatches, int recordCount) {
    if (response == null) {
      log.error("Null response from Firehose for sub-batch {}/{}", subBatchIndex, totalSubBatches);
      return false;
    }

    int failedRecordCount = response.failedPutCount();
    if (failedRecordCount > 0) {
      log.error(
          "Sub-batch {}/{} had {} failed records out of {}",
          subBatchIndex,
          totalSubBatches,
          failedRecordCount,
          recordCount);

      // Log details of failed records
      logFailedRecords(response.requestResponses(), subBatchIndex);
      return false;
    }

    log.debug(
        "Sub-batch {}/{} delivered successfully: {} records",
        subBatchIndex,
        totalSubBatches,
        recordCount);
    return true;
  }

  /** Log details of failed records for debugging. */
  private void logFailedRecords(List<PutRecordBatchResponseEntry> responses, int subBatchIndex) {
    if (log.isDebugEnabled()) {
      for (int i = 0; i < responses.size(); i++) {
        PutRecordBatchResponseEntry entry = responses.get(i);
        if (entry.errorCode() != null) {
          log.debug(
              "Failed record in sub-batch {}: index={}, error={}, message={}",
              subBatchIndex,
              i,
              entry.errorCode(),
              entry.errorMessage());
        }
      }
    }
  }

  /**
   * Get comprehensive metrics for monitoring and health checks. Includes enhanced buffer management
   * metrics for operational insights.
   */
  public BatchFirehoseMetrics getMetrics() {
    return BatchFirehoseMetrics.builder()
        .successfulBatches(successfulBatches.get())
        .failedBatches(failedBatches.get())
        .totalMessagesProcessed(totalMessagesProcessed.get())
        .totalBytesProcessed(totalBytesProcessed.get())
        .bufferFullRetries(bufferFullRetries.get())
        .bufferFullRecoveries(bufferFullRecoveries.get())
        .messagesHeldDuringRetries(messagesHeldDuringRetries.get())
        .deliveryStreamName(deliveryStreamName)
        .build();
  }

  /** Reset all metrics including enhanced buffer management metrics (useful for testing). */
  public void resetMetrics() {
    successfulBatches.set(0);
    failedBatches.set(0);
    totalMessagesProcessed.set(0);
    totalBytesProcessed.set(0);
    bufferFullRetries.set(0);
    bufferFullRecoveries.set(0);
    messagesHeldDuringRetries.set(0);
    log.info("Enhanced Batch Firehose metrics reset");
  }

  /** Test Firehose connectivity. */
  public boolean testConnectivity() {
    try {
      firehoseClient.describeDeliveryStream(
          builder -> builder.deliveryStreamName(deliveryStreamName));
      log.debug("Batch Firehose connectivity test successful for stream: {}", deliveryStreamName);
      return true;
    } catch (Exception e) {
      log.warn(
          "Batch Firehose connectivity test failed for stream {}: {}",
          deliveryStreamName,
          e.getMessage());
      return false;
    }
  }

  /**
   * Enhanced metrics data class for batch Firehose operations. Includes comprehensive buffer
   * management and retry tracking.
   */
  @lombok.Builder
  @lombok.Data
  public static class BatchFirehoseMetrics {
    private long successfulBatches;
    private long failedBatches;
    private long totalMessagesProcessed;
    private long totalBytesProcessed;
    private String deliveryStreamName;

    // Enhanced buffer management metrics
    private long bufferFullRetries;
    private long bufferFullRecoveries;
    private long messagesHeldDuringRetries;

    public double getBatchSuccessRate() {
      long total = successfulBatches + failedBatches;
      return total > 0 ? (double) successfulBatches / total : 0.0;
    }

    public double getAverageMessagesPerBatch() {
      return successfulBatches > 0 ? (double) totalMessagesProcessed / successfulBatches : 0.0;
    }

    public double getBufferFullRecoveryRate() {
      return bufferFullRetries > 0 ? (double) bufferFullRecoveries / bufferFullRetries : 0.0;
    }

    public double getAverageMessagesHeldPerRetry() {
      return bufferFullRetries > 0 ? (double) messagesHeldDuringRetries / bufferFullRetries : 0.0;
    }
  }
}
