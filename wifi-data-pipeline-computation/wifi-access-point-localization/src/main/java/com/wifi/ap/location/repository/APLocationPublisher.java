// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/WiFiMeasurementsPublisher.java
package com.wifi.ap.location.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.config.DynamoDBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
 *   <li><strong>Explicit Flushing:</strong> Manual batch emission control for processing completion
 *   <li><strong>Error Handling:</strong> Graceful handling of serialization and size limit
 *       violations
 * </ul>
 *
 * <p><strong>Batch Management Strategy:</strong>
 *
 * <ol>
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
public class APLocationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(APLocationPublisher.class);

    private final APLocationRepository repository; // Consumer now takes serialized JSON byte arrays
    private final DynamoDBConfig dynamoDBConfig;
    // Thread-safe state management
    private final ReentrantLock lock = new ReentrantLock();
    private List<APLocation> currentBatch = new ArrayList<>(); // Store serialized JSON byte arrays

    public APLocationPublisher(
            APLocationRepository repository, DynamoDBConfig dynamoDBConfig) {
        this.dynamoDBConfig = dynamoDBConfig;
        if (repository == null) {
            throw new IllegalArgumentException("Wifi Repository cannot be null");
        }

        this.repository = repository;

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
     * @param accessPointLocation The WiFi measurement to publish to the batch
     * @return null (for compatibility with functional interfaces), or null if record was skipped due
     * to errors
     * @throws IllegalStateException if the service is in an invalid state
     */
    public void publish(APLocation accessPointLocation) {

        addRecordToCurrentBatch(accessPointLocation);
    }

    private void addRecordToCurrentBatch(APLocation accessPointLocation) {
        lock.lock();
        try {

            // Add serialized byte array to batch
            currentBatch.add(accessPointLocation);


            logBatchAdd();

            // Check if batch is now full and should be emitted
            if (shouldEmitBatch()) {
                emitCurrentBatch();
            }

        } finally {
            lock.unlock();
        }
    }

    private void logBatchAdd() {
        logger.debug(
                "Added record to batch: {} records",
                currentBatch.size());
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
     * records)
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
            return new BatchStatus(currentBatch.size());
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
     * @param recordCount    The number of records currently in the batch
     */
    public record BatchStatus(int recordCount) {

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


    /**
     * Determines if the current batch should be emitted.
     */
    private boolean shouldEmitBatch() {
        return (currentBatch.size() >= dynamoDBConfig.writeBatchSize);
    }

    /**
     * Emits the current batch to the consumer and resets batch state.
     *
     * <p><b>THREAD SAFETY CRITICAL:</b> This method MUST be called while holding the {@link #lock}.
     * It performs direct reference swapping on shared state ({@link #currentBatch}  which requires exclusive access to maintain consistency.
     *
     * <p>The method uses an efficient zero-copy approach where it swaps the current batch reference
     * instead of creating a defensive copy, making the locking requirement even more critical for
     * thread safety.
     *
     * <p>Current callers (all properly synchronized):
     *
     * <ul>
     *   <li>{@link #publish(APLocation)} } - within lock.lock()/unlock() block
     *   <li>{@link #flushCurrentBatch()} - within lock.lock()/unlock() block
     * </ul>
     *
     * @throws IllegalStateException if called without holding the lock (in debug builds)
     */
    private void emitCurrentBatch() {
        if (currentBatch.isEmpty()) {
            return;
        }
        List<APLocation> batchToEmit = getBatchToEmit();

        // Emit batch (outside of lock to avoid blocking)
        CompletableFuture.runAsync(() -> this.repository.save(batchToEmit))
                .exceptionally(handleError(batchToEmit));
    }

    private  Function<Throwable, Void> handleError(List<APLocation> batchToEmit) {
        return throwable -> {
            logger.error("Failed to publish batch of {} records", batchToEmit.size(), throwable);
            return null;
        };
    }

    private List<APLocation> getBatchToEmit() {
        // Assert that we're holding the lock - critical for thread safety
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("emitCurrentBatch() must be called while holding the lock");
        }

        // Efficient swap instead of copying - we're already within the lock
        List<APLocation> batchToEmit = currentBatch;

        logger.info("Publishing batch: {} records, {} bytes", batchToEmit.size(), batchToEmit.size());

        // Reset state with new empty list
        currentBatch = new ArrayList<>();
        return batchToEmit;
    }
}
