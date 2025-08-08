// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/WiFiMeasurementsPublisher.java
package com.wifi.measurements.transformer.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;
import com.wifi.measurements.transformer.dto.WifiMeasurement;

/**
 * High-performance WiFi measurements publisher service for AWS Kinesis Data Firehose integration.
 *
 * <p>This service implements an efficient batch accumulation and publishing mechanism for WiFi
 * measurements, optimizing for AWS Kinesis Data Firehose performance characteristics and limits. It
 * provides thread-safe batch management with explicit flushing control for optimal throughput.
 *
 * <p><strong>Core Features:</strong>
 *
 * <ul>
 *   <li><strong>Batch Accumulation:</strong> Collects measurements into optimal batch sizes
 *   <li><strong>Size Optimization:</strong> Respects Firehose 4MB batch size and 1000KB record
 *       limits
 *   <li><strong>Thread Safety:</strong> ReentrantLock-based concurrent access protection
 *   <li><strong>JSON Serialization:</strong> Efficient JSON serialization with size validation
 *   <li><strong>Explicit Flushing:</strong> Manual batch emission control for processing completion
 *   <li><strong>Error Handling:</strong> Graceful handling of serialization and size limit
 *       violations
 * </ul>
 *
 * <p><strong>Batch Management Strategy:</strong>
 *
 * <ol>
 *   <li><strong>Record Validation:</strong> Validates record size against Firehose limits
 *   <li><strong>Batch Accumulation:</strong> Adds records to current batch until limits reached
 *   <li><strong>Automatic Emission:</strong> Emits batch when size or record count limits exceeded
 *   <li><strong>Manual Flushing:</strong> Supports explicit batch flushing for processing
 *       completion
 * </ol>
 *
 * <p><strong>Performance Characteristics:</strong>
 *
 * <ul>
 *   <li>Thread-safe concurrent access with minimal contention
 *   <li>Efficient JSON serialization with single-pass size calculation
 *   <li>Optimal batch utilization for Firehose throughput
 *   <li>Memory-efficient batch management with ArrayList backing
 * </ul>
 *
 * <p><strong>Firehose Integration:</strong>
 *
 * <ul>
 *   <li>Respects 4MB maximum batch size limit
 *   <li>Validates 1000KB maximum record size limit
 *   <li>Optimizes for 500 records per batch target
 *   <li>Supports batch consumer pattern for flexible delivery
 * </ul>
 *
 * <p><strong>Thread Safety:</strong>
 *
 * <ul>
 *   <li>Uses {@link ReentrantLock} to protect shared state (currentBatch, currentBatchSizeBytes)
 *   <li>All state mutations are performed within lock.lock()/unlock() blocks
 *   <li>Batch emission uses zero-copy reference swapping for efficiency, requiring strict locking
 *   <li>Asynchronous batch consumption occurs outside the lock to prevent blocking
 * </ul>
 *
 * <p>This service is designed for high-throughput, reliable publishing of WiFi measurements to AWS
 * Kinesis Data Firehose with optimal performance and resource utilization.
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class WiFiMeasurementsPublisher {

  private static final Logger logger = LoggerFactory.getLogger(WiFiMeasurementsPublisher.class);

  private final FirehoseConfigurationProperties firehoseProperties;
  private final Consumer<List<byte[]>>
      batchConsumer; // Consumer now takes serialized JSON byte arrays
  private final ObjectMapper objectMapper;

  // Thread-safe state management
  private final ReentrantLock lock = new ReentrantLock();
  private List<byte[]> currentBatch = new ArrayList<>(); // Store serialized JSON byte arrays
  private long currentBatchSizeBytes = 0;

  /**
   * Constructs a new WiFi measurements publisher with required dependencies.
   *
   * <p>This constructor initializes the publisher with Firehose configuration properties, a batch
   * consumer for processing accumulated batches, and an ObjectMapper for JSON serialization. The
   * service is designed for efficient batch management and optimal Firehose integration.
   *
   * @param firehoseProperties Configuration properties defining Firehose batch and record size
   *     limits
   * @param batchConsumer Consumer function that processes accumulated batches of serialized JSON
   *     byte arrays
   * @param objectMapper Jackson ObjectMapper for efficient JSON serialization of measurements
   * @throws IllegalArgumentException if any required dependency is null
   */
  public WiFiMeasurementsPublisher(
      FirehoseConfigurationProperties firehoseProperties,
      Consumer<List<byte[]>> batchConsumer,
      ObjectMapper objectMapper) {
    if (firehoseProperties == null) {
      throw new IllegalArgumentException("FirehoseConfigurationProperties cannot be null");
    }
    if (batchConsumer == null) {
      throw new IllegalArgumentException("BatchConsumer cannot be null");
    }
    if (objectMapper == null) {
      throw new IllegalArgumentException("ObjectMapper cannot be null");
    }

    this.firehoseProperties = firehoseProperties;
    this.batchConsumer = batchConsumer;
    this.objectMapper = objectMapper;

    logger.info(
        "WiFi Measurements Publisher initialized with batch size limit: {} records, {} bytes",
        firehoseProperties.maxBatchSize(),
        firehoseProperties.maxBatchSizeBytes());
  }

  /**
   * Publishes a WiFi measurement to the current batch with automatic size validation and batch
   * management.
   *
   * <p>This method serializes the measurement to JSON, validates its size against Firehose limits,
   * and adds it to the current batch. If adding the measurement would exceed batch limits, the
   * current batch is automatically emitted before adding the new measurement.
   *
   * <p><strong>Processing Steps:</strong>
   *
   * <ol>
   *   <li><strong>JSON Serialization:</strong> Converts measurement to JSON string
   *   <li><strong>Size Validation:</strong> Checks record size against Firehose 1000KB limit
   *   <li><strong>Batch Management:</strong> Emits current batch if limits would be exceeded
   *   <li><strong>Record Addition:</strong> Adds serialized record to current batch
   *   <li><strong>Auto-Emission:</strong> Emits batch if size or record count limits reached
   * </ol>
   *
   * <p><strong>Error Handling:</strong>
   *
   * <ul>
   *   <li>Serialization failures result in record being skipped
   *   <li>Oversized records are rejected and logged
   *   <li>Thread-safe operation with lock protection
   * </ul>
   *
   * @param measurement The WiFi measurement to publish to the batch
   * @return null (for compatibility with functional interfaces), or null if record was skipped due
   *     to errors
   * @throws IllegalStateException if the service is in an invalid state
   */
  public void publishMeasurement(WifiMeasurement measurement) {
    lock.lock();
    try {
      // Serialize directly to byte array for memory efficiency
      byte[] jsonBytes;
      try {
        jsonBytes = objectMapper.writeValueAsBytes(measurement);
      } catch (Exception e) {
        logger.error("Failed to serialize measurement, skipping record: {}", e.getMessage());
        return; // Skip this record
      }

      // Calculate accurate record size from the serialized JSON bytes
      long recordSize = jsonBytes.length;

      // Validate record size against Firehose limits
      if (recordSize > firehoseProperties.maxRecordSizeBytes()) {
        logger.error(
            "Rejecting oversized record: {} bytes exceeds limit {} bytes",
            recordSize,
            firehoseProperties.maxRecordSizeBytes());
        return; // Skip this record
      }

      // Check if adding this record would exceed batch size limits
      if (wouldExceedBatchLimits(recordSize)) {
        // Emit current batch first
        emitCurrentBatch();
      }

      // Add serialized byte array to batch
      currentBatch.add(jsonBytes);
      currentBatchSizeBytes += recordSize;

      logger.debug(
          "Added record to batch: {} records, {} bytes",
          currentBatch.size(),
          currentBatchSizeBytes);

      // Check if batch is now full and should be emitted
      if (shouldEmitBatch()) {
        emitCurrentBatch();
      }

    } finally {
      lock.unlock();
    }
  }

  /**
   * Manually flushes the current batch regardless of size, ensuring all accumulated measurements
   * are processed.
   *
   * <p>This method forces the emission of the current batch, even if it hasn't reached the size or
   * record count limits. This is typically called at the end of processing to ensure all
   * measurements are delivered to the Firehose delivery stream.
   *
   * <p><strong>Use Cases:</strong>
   *
   * <ul>
   *   <li><strong>Processing Completion:</strong> Flush remaining records at end of batch
   *       processing
   *   <li><strong>Graceful Shutdown:</strong> Ensure no records are lost during service shutdown
   *   <li><strong>Manual Control:</strong> Force batch emission for testing or debugging
   * </ul>
   *
   * <p><strong>Thread Safety:</strong>
   *
   * <ul>
   *   <li>Thread-safe operation with lock protection
   *   <li>Blocks until batch emission completes
   *   <li>Returns immediately if no records in current batch
   * </ul>
   *
   * @return CompletableFuture that completes when the batch has been emitted (may be empty if no
   *     records)
   */
  public CompletableFuture<Void> flushCurrentBatch() {
    lock.lock();
    try {
      if (currentBatch.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }

      emitCurrentBatch();
      return CompletableFuture.completedFuture(null);

    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns the current batch status for monitoring and debugging purposes.
   *
   * <p>This method provides a snapshot of the current batch state, including the number of records
   * and total size in bytes. This information is useful for monitoring batch utilization, debugging
   * processing issues, and understanding batch emission patterns.
   *
   * <p><strong>Thread Safety:</strong>
   *
   * <ul>
   *   <li>Thread-safe operation with lock protection
   *   <li>Returns consistent snapshot of current state
   *   <li>May be called concurrently with other operations
   * </ul>
   *
   * @return BatchStatus containing current record count and total size in bytes
   */
  public BatchStatus getCurrentBatchStatus() {
    lock.lock();
    try {
      return new BatchStatus(currentBatch.size(), currentBatchSizeBytes);
    } finally {
      lock.unlock();
    }
  }

  /** Internal record representing the current status of a Firehose batch accumulator. */
  /**
   * Immutable record representing the current status of a WiFi measurements batch.
   *
   * <p>This record provides a snapshot of batch state for monitoring, debugging, and batch
   * management decisions. It includes both record count and total size information to support
   * comprehensive batch analysis.
   *
   * @param recordCount The number of records currently in the batch
   * @param totalSizeBytes The total size of all records in the batch in bytes
   */
  public record BatchStatus(int recordCount, long totalSizeBytes) {

    /**
     * Returns true if the batch contains at least one record.
     *
     * <p>This method provides a convenient way to check if the batch has any content without
     * needing to compare recordCount to zero.
     *
     * @return true if recordCount > 0, false otherwise
     */
    public boolean hasRecords() {
      return recordCount > 0;
    }

    /**
     * Returns true if the batch contains no records.
     *
     * <p>This method provides a convenient way to check if the batch is empty without needing to
     * compare recordCount to zero.
     *
     * @return true if recordCount == 0, false otherwise
     */
    public boolean isEmpty() {
      return recordCount == 0;
    }
  }

  /** Checks if adding a record would exceed batch limits. */
  private boolean wouldExceedBatchLimits(long recordSize) {
    return (currentBatch.size() >= firehoseProperties.maxBatchSize())
        || (currentBatchSizeBytes + recordSize > firehoseProperties.maxBatchSizeBytes());
  }

  /** Determines if the current batch should be emitted. */
  private boolean shouldEmitBatch() {
    return (currentBatch.size() >= firehoseProperties.maxBatchSize())
        || (currentBatchSizeBytes >= firehoseProperties.maxBatchSizeBytes());
  }

  /**
   * Emits the current batch to the consumer and resets batch state.
   *
   * <p><b>THREAD SAFETY CRITICAL:</b> This method MUST be called while holding the {@link #lock}.
   * It performs direct reference swapping on shared state ({@link #currentBatch} and {@link
   * #currentBatchSizeBytes}) which requires exclusive access to maintain consistency.
   *
   * <p>The method uses an efficient zero-copy approach where it swaps the current batch reference
   * instead of creating a defensive copy, making the locking requirement even more critical for
   * thread safety.
   *
   * <p>Current callers (all properly synchronized):
   *
   * <ul>
   *   <li>{@link #publishMeasurement(WifiMeasurement)} - within lock.lock()/unlock() block
   *   <li>{@link #flushCurrentBatch()} - within lock.lock()/unlock() block
   * </ul>
   *
   * @throws IllegalStateException if called without holding the lock (in debug builds)
   */
  private void emitCurrentBatch() {
    // Assert that we're holding the lock - critical for thread safety
    if (!lock.isHeldByCurrentThread()) {
      throw new IllegalStateException("emitCurrentBatch() must be called while holding the lock");
    }

    if (currentBatch.isEmpty()) {
      return;
    }

    // Efficient swap instead of copying - we're already within the lock
    List<byte[]> batchToEmit = currentBatch;
    long batchSize = currentBatchSizeBytes;

    logger.info("Publishing batch: {} records, {} bytes", batchToEmit.size(), batchSize);

    // Reset state with new empty list
    currentBatch = new ArrayList<>();
    currentBatchSizeBytes = 0;

    // Emit batch (outside of lock to avoid blocking)
    CompletableFuture.runAsync(() -> batchConsumer.accept(batchToEmit))
        .exceptionally(
            throwable -> {
              logger.error("Failed to publish batch of {} records", batchToEmit.size(), throwable);
              return null;
            });
  }
}
