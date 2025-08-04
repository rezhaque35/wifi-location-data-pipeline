package com.wifi.scan.consume.metrics;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection component for Kafka consumer operations.
 * 
 * This component provides comprehensive metrics tracking for Kafka consumer
 * operations, including both individual message processing and batch processing
 * statistics. It uses thread-safe atomic counters and volatile variables to
 * ensure accurate metrics collection in multi-threaded environments.
 * 
 * Key Functionality:
 * - Tracks individual message consumption, processing, and failure rates
 * - Monitors batch processing performance and success rates
 * - Records processing time statistics (min, max, average)
 * - Maintains timestamp tracking for first and last message processing
 * - Provides rate limiting and partial processing metrics
 * - Offers comprehensive metrics summary generation
 * - Supports metrics reset for testing and monitoring periods
 * 
 * High-Level Steps:
 * 1. Record message consumption events with timestamps
 * 2. Track message processing success/failure with timing
 * 3. Monitor batch processing performance and outcomes
 * 4. Calculate success rates and performance statistics
 * 5. Maintain processing time min/max/average calculations
 * 6. Generate comprehensive metrics summaries
 * 7. Support metrics reset for operational purposes
 * 
 * The component is designed for production monitoring and provides the
 * detailed metrics needed for performance analysis, alerting, and
 * operational dashboards.
 * 
 * @see com.wifi.scan.consume.service.KafkaMonitoringService
 * @see com.wifi.scan.consume.controller.MetricsController
 */
@Slf4j
@Component("customKafkaConsumerMetrics")
@Getter
public class KafkaConsumerMetrics {

    // Individual message processing metrics using atomic counters for thread safety
    private final AtomicLong totalMessagesConsumed = new AtomicLong(0);
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final AtomicLong totalMessagesFailed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    
    // Timestamp tracking for operational monitoring
    private volatile LocalDateTime lastMessageTimestamp;
    private volatile LocalDateTime firstMessageTimestamp;
    
    // Processing time statistics for performance monitoring
    private volatile long minProcessingTimeMs = Long.MAX_VALUE;
    private volatile long maxProcessingTimeMs = 0;

    /**
     * Records a successfully consumed message.
     * 
     * This method increments the total messages consumed counter and updates
     * the timestamp tracking for operational monitoring. It's called when
     * a message is successfully received from Kafka.
     * 
     * High-Level Steps:
     * 1. Increment total messages consumed counter atomically
     * 2. Update timestamp tracking for operational monitoring
     * 3. Log debug information for monitoring and debugging
     */
    public void recordMessageConsumed() {
        // Increment consumed message counter atomically
        totalMessagesConsumed.incrementAndGet();
        
        // Update timestamp tracking for operational monitoring
        updateTimestamp();
        
        log.debug("Message consumed. Total: {}", totalMessagesConsumed.get());
    }

    /**
     * Records a successfully processed message with processing time.
     * 
     * This method tracks successful message processing with timing information
     * for performance monitoring. It updates processing statistics and maintains
     * min/max processing time tracking.
     * 
     * High-Level Steps:
     * 1. Increment total messages processed counter atomically
     * 2. Add processing time to total processing time accumulator
     * 3. Update min/max processing time statistics
     * 4. Log debug information for monitoring and debugging
     * 
     * @param processingTimeMs the time taken to process the message in milliseconds
     */
    public void recordMessageProcessed(long processingTimeMs) {
        // Increment processed message counter atomically
        totalMessagesProcessed.incrementAndGet();
        
        // Add processing time to total accumulator
        totalProcessingTimeMs.addAndGet(processingTimeMs);
        
        // Update processing time statistics (min/max)
        updateProcessingTimeStats(processingTimeMs);
        
        log.debug("Message processed in {} ms. Total processed: {}", processingTimeMs, totalMessagesProcessed.get());
    }

    /**
     * Records a failed message processing attempt.
     * 
     * This method tracks message processing failures for error rate calculation
     * and operational monitoring. It's called when message processing fails
     * due to errors or exceptions.
     * 
     * High-Level Steps:
     * 1. Increment total messages failed counter atomically
     * 2. Log debug information for monitoring and debugging
     */
    public void recordMessageFailed() {
        // Increment failed message counter atomically
        totalMessagesFailed.incrementAndGet();
        
        log.debug("Message processing failed. Total failures: {}", totalMessagesFailed.get());
    }

    /**
     * Gets the current success rate as a percentage.
     * 
     * This method calculates the success rate based on consumed vs processed
     * messages. It returns 100% if no messages have been consumed yet.
     * 
     * High-Level Steps:
     * 1. Get total messages consumed count
     * 2. Return 100% if no messages consumed (avoid division by zero)
     * 3. Calculate success rate as (processed / consumed) * 100
     * 4. Return success rate percentage
     * 
     * @return success rate percentage (0-100)
     */
    public double getSuccessRate() {
        long total = totalMessagesConsumed.get();
        if (total == 0) {
            return 100.0; // Return 100% if no messages consumed yet
        }
        
        long successful = totalMessagesProcessed.get();
        return (successful * 100.0) / total;
    }

    /**
     * Gets the current error rate as a percentage.
     * 
     * This method calculates the error rate based on failed vs consumed
     * messages. It returns 0% if no messages have been consumed yet.
     * 
     * High-Level Steps:
     * 1. Get total messages consumed count
     * 2. Return 0% if no messages consumed (avoid division by zero)
     * 3. Calculate error rate as (failed / consumed) * 100
     * 4. Return error rate percentage
     * 
     * @return error rate percentage (0-100)
     */
    public double getErrorRate() {
        long total = totalMessagesConsumed.get();
        if (total == 0) {
            return 0.0; // Return 0% if no messages consumed yet
        }
        
        long failed = totalMessagesFailed.get();
        return (failed * 100.0) / total;
    }

    /**
     * Gets the average processing time in milliseconds.
     * 
     * This method calculates the average processing time across all
     * successfully processed messages. It returns 0 if no messages
     * have been processed yet.
     * 
     * High-Level Steps:
     * 1. Get total messages processed count
     * 2. Return 0 if no messages processed (avoid division by zero)
     * 3. Calculate average as total processing time / total processed
     * 4. Return average processing time
     * 
     * @return average processing time or 0 if no messages processed
     */
    public double getAverageProcessingTimeMs() {
        long totalProcessed = totalMessagesProcessed.get();
        if (totalProcessed == 0) {
            return 0.0; // Return 0 if no messages processed yet
        }
        
        return (double) totalProcessingTimeMs.get() / totalProcessed;
    }

    /**
     * Gets the minimum processing time recorded.
     * 
     * @return minimum processing time in milliseconds
     */
    public long getMinProcessingTimeMs() {
        return minProcessingTimeMs == Long.MAX_VALUE ? 0 : minProcessingTimeMs;
    }

    /**
     * Gets the maximum processing time recorded.
     * 
     * @return maximum processing time in milliseconds
     */
    public long getMaxProcessingTimeMs() {
        return maxProcessingTimeMs;
    }

    /**
     * Gets the timestamp of the last processed message.
     * 
     * @return formatted timestamp string or null if no messages processed
     */
    public String getLastMessageTimestamp() {
        if (lastMessageTimestamp == null) {
            return null;
        }
        return lastMessageTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Gets the timestamp of the first processed message.
     * 
     * @return formatted timestamp string or null if no messages processed
     */
    public String getFirstMessageTimestamp() {
        if (firstMessageTimestamp == null) {
            return null;
        }
        return firstMessageTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Resets all metrics to their initial state.
     * Useful for testing or metrics refresh.
     */
    public void resetMetrics() {
        totalMessagesConsumed.set(0);
        totalMessagesProcessed.set(0);
        totalMessagesFailed.set(0);
        totalProcessingTimeMs.set(0);
        lastMessageTimestamp = null;
        firstMessageTimestamp = null;
        minProcessingTimeMs = Long.MAX_VALUE;
        maxProcessingTimeMs = 0;
        log.info("Kafka consumer metrics reset");
    }

    /**
     * Updates the message timestamp tracking.
     */
    private void updateTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        this.lastMessageTimestamp = now;
        
        if (this.firstMessageTimestamp == null) {
            this.firstMessageTimestamp = now;
        }
    }

    /**
     * Updates processing time statistics.
     * 
     * @param processingTimeMs the processing time to record
     */
    private void updateProcessingTimeStats(long processingTimeMs) {
        // Update minimum processing time
        long currentMin = this.minProcessingTimeMs;
        while (processingTimeMs < currentMin) {
            if (compareAndSetMinProcessingTime(currentMin, processingTimeMs)) {
                break;
            }
            currentMin = this.minProcessingTimeMs;
        }
        
        // Update maximum processing time
        long currentMax = this.maxProcessingTimeMs;
        while (processingTimeMs > currentMax) {
            if (compareAndSetMaxProcessingTime(currentMax, processingTimeMs)) {
                break;
            }
            currentMax = this.maxProcessingTimeMs;
        }
    }

    /**
     * Atomic compare and set for minimum processing time.
     */
    private synchronized boolean compareAndSetMinProcessingTime(long expected, long newValue) {
        if (this.minProcessingTimeMs == expected) {
            this.minProcessingTimeMs = newValue;
            return true;
        }
        return false;
    }

    /**
     * Atomic compare and set for maximum processing time.
     */
    private synchronized boolean compareAndSetMaxProcessingTime(long expected, long newValue) {
        if (this.maxProcessingTimeMs == expected) {
            this.maxProcessingTimeMs = newValue;
            return true;
        }
        return false;
    }

    /**
     * Gets a summary of all metrics as a formatted string.
     * 
     * @return metrics summary
     */
    public String getMetricsSummary() {
        return """
                Kafka Consumer Metrics Summary:
                  Total Messages Consumed: %d
                  Total Messages Processed: %d
                  Total Messages Failed: %d
                  Success Rate: %.2f%%
                  Error Rate: %.2f%%
                  Average Processing Time: %.2f ms
                  Min Processing Time: %d ms
                  Max Processing Time: %d ms
                  First Message: %s
                  Last Message: %s""".formatted(
                totalMessagesConsumed.get(),
                totalMessagesProcessed.get(),
                totalMessagesFailed.get(),
                getSuccessRate(),
                getErrorRate(),
                getAverageProcessingTimeMs(),
                getMinProcessingTimeMs(),
                getMaxProcessingTimeMs(),
                getFirstMessageTimestamp(),
                getLastMessageTimestamp()
        );
    }

    // Additional batch metrics for Phase 5
    private final AtomicLong totalBatchesProcessed = new AtomicLong(0);
    private final AtomicLong totalBatchesFailed = new AtomicLong(0);
    private final AtomicLong totalBatchesRateLimited = new AtomicLong(0);
    private final AtomicLong totalBatchProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong totalBatchesConsumed = new AtomicLong(0);
    private final AtomicLong totalPartiallyProcessedBatches = new AtomicLong(0);

    /**
     * Records a batch that was successfully consumed from Kafka.
     * 
     * @param batchSize the number of messages in the consumed batch
     */
    public void recordBatchConsumed(int batchSize) {
        totalBatchesConsumed.incrementAndGet();
        totalMessagesConsumed.addAndGet(batchSize);
        updateTimestamp();
        log.debug("Batch consumed with {} messages. Total batches: {}", batchSize, totalBatchesConsumed.get());
    }

    /**
     * Records a successfully processed batch with processing time.
     * 
     * @param batchSize the number of messages in the batch
     * @param processingTimeMs the time taken to process the batch in milliseconds
     */
    public void recordBatchProcessed(int batchSize, long processingTimeMs) {
        totalBatchesProcessed.incrementAndGet();
        totalBatchProcessingTimeMs.addAndGet(processingTimeMs);
        totalMessagesProcessed.addAndGet(batchSize);
        log.debug("Batch processed in {} ms with {} messages. Total batches: {}", 
                 processingTimeMs, batchSize, totalBatchesProcessed.get());
    }

    /**
     * Records a batch that was rate limited.
     * 
     * @param batchSize the number of messages in the rate limited batch
     */
    public void recordBatchRateLimited(int batchSize) {
        totalBatchesRateLimited.incrementAndGet();
        log.debug("Batch rate limited with {} messages. Total rate limited batches: {}", 
                 batchSize, totalBatchesRateLimited.get());
    }

    /**
     * Records a failed batch processing attempt.
     * 
     * @param batchSize the number of messages in the failed batch
     */
    public void recordBatchFailed(int batchSize) {
        totalBatchesFailed.incrementAndGet();
        totalMessagesFailed.addAndGet(batchSize);
        log.debug("Batch processing failed with {} messages. Total batch failures: {}", 
                 batchSize, totalBatchesFailed.get());
    }

    /**
     * Records a batch that was partially processed due to rate limiting or other constraints.
     * 
     * @param totalBatchSize the original size of the batch
     * @param processedCount the number of messages actually processed
     */
    public void recordBatchPartiallyProcessed(int totalBatchSize, int processedCount) {
        totalPartiallyProcessedBatches.incrementAndGet();
        totalMessagesProcessed.addAndGet(processedCount);
        log.debug("Batch partially processed: {}/{} messages. Total partial batches: {}", 
                 processedCount, totalBatchSize, totalPartiallyProcessedBatches.get());
    }

    /**
     * Records a successfully processed batch with processing time (legacy method).
     * 
     * @param processingTimeMs the time taken to process the batch in milliseconds
     * @param batchSize the number of messages in the batch
     */
    public void recordBatchProcessed(long processingTimeMs, int batchSize) {
        recordBatchProcessed(batchSize, processingTimeMs);
    }

    /**
     * Records a failed batch processing attempt (legacy method).
     */
    public void recordBatchFailed() {
        recordBatchFailed(1); // Default to 1 message for legacy compatibility
    }

    /**
     * Records a batch that was rate limited (legacy method).
     */
    public void recordBatchRateLimited() {
        recordBatchRateLimited(1); // Default to 1 message for legacy compatibility
    }

    /**
     * Gets the batch success rate as a percentage.
     */
    public double getBatchSuccessRate() {
        long total = totalBatchesProcessed.get() + totalBatchesFailed.get();
        if (total == 0) {
            return 100.0;
        }
        return (totalBatchesProcessed.get() * 100.0) / total;
    }

    /**
     * Gets the average batch processing time in milliseconds.
     */
    public double getAverageBatchProcessingTimeMs() {
        long totalBatches = totalBatchesProcessed.get();
        if (totalBatches == 0) {
            return 0.0;
        }
        return (double) totalBatchProcessingTimeMs.get() / totalBatches;
    }

    /**
     * Gets the total number of batches consumed.
     */
    public long getTotalBatchesConsumed() {
        return totalBatchesConsumed.get();
    }

    /**
     * Gets the total number of batches processed.
     */
    public long getTotalBatchesProcessed() {
        return totalBatchesProcessed.get();
    }

    /**
     * Gets the total number of failed batches.
     */
    public long getTotalBatchesFailed() {
        return totalBatchesFailed.get();
    }

    /**
     * Gets the total number of rate limited batches.
     */
    public long getTotalBatchesRateLimited() {
        return totalBatchesRateLimited.get();
    }

    /**
     * Gets the total number of partially processed batches.
     */
    public long getTotalPartiallyProcessedBatches() {
        return totalPartiallyProcessedBatches.get();
    }
} 