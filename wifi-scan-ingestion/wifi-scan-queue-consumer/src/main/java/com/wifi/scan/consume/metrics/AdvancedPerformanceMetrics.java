package com.wifi.scan.consume.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Advanced performance metrics component for high-throughput batch processing monitoring.
 *
 * <p>This class provides comprehensive performance monitoring and analytics for high-throughput
 * Kafka consumer operations, including poll processing efficiency, thread utilization, buffer
 * performance optimization, Firehose batch composition analysis, consumer lag monitoring, batch
 * effectiveness correlation, and throttling event analysis.
 *
 * <p>Key Functionality: - Poll processing time monitoring and performance alerts - Thread
 * utilization tracking and optimization - Buffer fill time analysis for optimal performance -
 * Firehose batch composition and size distribution - Consumer lag monitoring during rate limiting -
 * Batch rejection rate analysis and reason tracking - Batch size effectiveness correlation analysis
 * - Throttling event monitoring and duration tracking - Performance threshold monitoring and
 * alerting
 *
 * <p>High-Level Processing Steps: 1. Monitor poll processing times and identify slow operations 2.
 * Track thread utilization for resource optimization 3. Analyze buffer fill times for optimal
 * performance ranges 4. Monitor Firehose batch composition and size distribution 5. Track consumer
 * lag during rate limiting operations 6. Analyze batch rejection rates and reasons 7. Correlate
 * batch sizes with success rates 8. Monitor throttling events and their durations 9. Provide
 * comprehensive performance analytics and alerts
 *
 * <p>Performance Categories: - Poll Processing: Time tracking, slow poll detection, performance
 * alerts - Thread Utilization: Current and average utilization, threshold monitoring - Buffer
 * Performance: Fill time analysis, optimal range monitoring - Firehose Batching: Size distribution,
 * composition analysis - Consumer Lag: Lag tracking, rate limiting impact analysis - Batch
 * Management: Rejection rates, effectiveness correlation - Throttling Analysis: Event counts,
 * duration tracking, performance impact
 *
 * <p>Alert Thresholds: - Poll processing: 1.2 seconds threshold for slow polls - Thread
 * utilization: 80% threshold for high utilization - Buffer fill time: 13-44 seconds optimal range -
 * Consumer lag: 1000 messages threshold - Batch rejection: 5% threshold for rejection rate
 *
 * <p>Thread Safety: - Uses AtomicLong and AtomicInteger for thread-safe counters - AtomicReference
 * for thread-safe reference updates - ConcurrentHashMap for thread-safe collections - Volatile
 * fields for thread-visible state updates
 *
 * @see KafkaConsumerMetrics for basic consumer metrics
 */
@Slf4j
@Component
public class AdvancedPerformanceMetrics {

  // Poll Processing Time Metrics

  /** Total number of poll operations performed */
  private final AtomicLong totalPollOperations = new AtomicLong(0);

  /** Total processing time for all poll operations in milliseconds */
  private final AtomicLong totalPollProcessingTimeMs = new AtomicLong(0);

  /** Count of poll operations that exceeded the performance threshold */
  private final AtomicInteger slowPollCount = new AtomicInteger(0);

  /** Current state of poll performance alert (true if performance issues detected) */
  private final AtomicReference<Boolean> currentPollPerformanceAlert = new AtomicReference<>(false);

  /** Threshold in milliseconds for identifying slow poll operations */
  private static final long POLL_PROCESSING_THRESHOLD_MS = 1200; // 1.2 seconds

  // Thread Utilization Metrics

  /** Total number of thread utilization records */
  private final AtomicLong threadUtilizationRecords = new AtomicLong(0);

  /** Cumulative thread utilization percentage for average calculation */
  private final AtomicReference<Double> totalThreadUtilization = new AtomicReference<>(0.0);

  /** Current thread utilization percentage */
  private final AtomicReference<Double> currentThreadUtilization = new AtomicReference<>(0.0);

  /** Threshold percentage for high thread utilization alerts */
  private static final double THREAD_UTILIZATION_THRESHOLD = 80.0;

  // Buffer Fill Time Metrics

  /** Total number of buffer fill operations */
  private final AtomicLong totalBufferFillOperations = new AtomicLong(0);

  /** Total time spent on buffer fill operations in milliseconds */
  private final AtomicLong totalBufferFillTimeMs = new AtomicLong(0);

  /** Count of buffer fill operations outside the optimal range */
  private final AtomicInteger bufferFillOutsideRangeCount = new AtomicInteger(0);

  /** Minimum optimal buffer fill time in milliseconds */
  private static final long BUFFER_FILL_MIN_MS = 13_000; // 13 seconds

  /** Maximum optimal buffer fill time in milliseconds */
  private static final long BUFFER_FILL_MAX_MS = 44_000; // 44 seconds

  // Firehose Batch Composition Metrics

  /** Total number of Firehose batches processed */
  private final AtomicLong totalFirehoseBatches = new AtomicLong(0);

  /** Total size of all Firehose batches */
  private final AtomicLong totalFirehoseBatchSize = new AtomicLong(0);

  /** Distribution of Firehose batch sizes by category */
  private final Map<String, AtomicInteger> firehoseBatchSizeDistribution =
      new ConcurrentHashMap<>();

  // Consumer Lag Metrics

  /** Total number of consumer lag records */
  private final AtomicLong totalConsumerLagRecords = new AtomicLong(0);

  /** Cumulative consumer lag for average calculation */
  private final AtomicLong totalConsumerLag = new AtomicLong(0);

  /** Current consumer lag in messages */
  private final AtomicLong currentConsumerLag = new AtomicLong(0);

  /** Count of high lag events during rate limiting */
  private final AtomicInteger highLagDuringRateLimitingCount = new AtomicInteger(0);

  /** Threshold for high consumer lag alerts */
  private static final long CONSUMER_LAG_THRESHOLD = 1000;

  // Batch Rejection Rate Metrics

  /** Total number of batch processing attempts */
  private final AtomicLong totalBatchAttempts = new AtomicLong(0);

  /** Total number of batch rejections */
  private final AtomicLong totalBatchRejections = new AtomicLong(0);

  /** Distribution of batch rejection reasons */
  private final Map<String, AtomicInteger> batchRejectionReasons = new ConcurrentHashMap<>();

  /** Threshold percentage for batch rejection rate alerts */
  private static final double BATCH_REJECTION_THRESHOLD = 5.0;

  // Batch Size Effectiveness Metrics

  /** Success count for each batch size category */
  private final Map<String, AtomicInteger> batchSizeSuccessCount = new ConcurrentHashMap<>();

  /** Total count for each batch size category */
  private final Map<String, AtomicInteger> batchSizeTotalCount = new ConcurrentHashMap<>();

  // Throttling Event Metrics

  /** Count of throttling events by type */
  private final Map<String, AtomicInteger> throttlingEventCounts = new ConcurrentHashMap<>();

  /** Total duration of throttling events by type */
  private final Map<String, AtomicLong> throttlingEventDurations = new ConcurrentHashMap<>();

  /** Total number of throttling events across all types */
  private final AtomicLong totalThrottlingEvents = new AtomicLong(0);

  /**
   * Creates a new AdvancedPerformanceMetrics instance.
   *
   * <p>Initializes the advanced performance monitoring system with batch size categories and
   * Firehose batch size distribution tracking for comprehensive performance analysis.
   */
  public AdvancedPerformanceMetrics() {
    initializeBatchSizeCategories();
    initializeFirehoseBatchSizeCategories();
    log.info("AdvancedPerformanceMetrics initialized for high-throughput monitoring");
  }

  /**
   * Initializes batch size categories for effectiveness tracking.
   *
   * <p>This method sets up the tracking categories for batch size effectiveness analysis, enabling
   * correlation between batch sizes and success rates.
   *
   * <p>Categories: - Small: ≤60 messages - Medium: 61-100 messages - Large: 101-150 messages
   */
  private void initializeBatchSizeCategories() {
    batchSizeSuccessCount.put("small(<=60)", new AtomicInteger(0));
    batchSizeSuccessCount.put("medium(61-100)", new AtomicInteger(0));
    batchSizeSuccessCount.put("large(101-150)", new AtomicInteger(0));

    batchSizeTotalCount.put("small(<=60)", new AtomicInteger(0));
    batchSizeTotalCount.put("medium(61-100)", new AtomicInteger(0));
    batchSizeTotalCount.put("large(101-150)", new AtomicInteger(0));
  }

  /**
   * Initializes Firehose batch size distribution categories.
   *
   * <p>This method sets up the tracking categories for Firehose batch size distribution analysis,
   * enabling optimization of batch composition.
   *
   * <p>Categories: - 40-60: Small batches - 61-100: Medium batches - 101-136: Large batches
   */
  private void initializeFirehoseBatchSizeCategories() {
    firehoseBatchSizeDistribution.put("40-60", new AtomicInteger(0));
    firehoseBatchSizeDistribution.put("61-100", new AtomicInteger(0));
    firehoseBatchSizeDistribution.put("101-136", new AtomicInteger(0));
  }

  /**
   * Records poll processing time and tracks performance alerts.
   *
   * <p>This method tracks the time taken for poll operations and identifies slow polls that may
   * indicate performance issues or bottlenecks.
   *
   * <p>Processing Steps: 1. Increment total poll operations counter 2. Add processing time to total
   * processing time 3. Check if processing time exceeds threshold 4. Increment slow poll counter if
   * threshold exceeded 5. Update performance alert status 6. Log performance details for monitoring
   *
   * <p>Performance Analysis: - Tracks average poll processing time - Identifies slow poll
   * operations (>1.2 seconds) - Provides performance alerting capabilities - Enables bottleneck
   * identification
   *
   * <p>Use Cases: - Performance bottleneck identification - System optimization and tuning - Alert
   * generation for slow operations - Capacity planning and scaling decisions
   *
   * @param processingTimeMs Processing time for the poll operation in milliseconds
   */
  public void recordPollProcessingTime(long processingTimeMs) {
    totalPollOperations.incrementAndGet();
    totalPollProcessingTimeMs.addAndGet(processingTimeMs);

    if (processingTimeMs > POLL_PROCESSING_THRESHOLD_MS) {
      slowPollCount.incrementAndGet();
      currentPollPerformanceAlert.set(true);
      log.warn(
          "Slow poll processing detected: {} ms (threshold: {} ms)",
          processingTimeMs,
          POLL_PROCESSING_THRESHOLD_MS);
    } else {
      currentPollPerformanceAlert.set(false);
    }

    log.debug("Poll processing time recorded: {} ms", processingTimeMs);
  }

  /** Records thread utilization percentage. */
  public void recordThreadUtilization(double utilizationPercent) {
    threadUtilizationRecords.incrementAndGet();
    currentThreadUtilization.set(utilizationPercent);

    // Update cumulative average
    double currentTotal = totalThreadUtilization.get();
    long recordCount = threadUtilizationRecords.get();
    double newTotal = currentTotal + utilizationPercent;
    totalThreadUtilization.set(newTotal);

    if (utilizationPercent > THREAD_UTILIZATION_THRESHOLD) {
      log.warn(
          "High thread utilization detected: {}% (threshold: {}%)",
          utilizationPercent, THREAD_UTILIZATION_THRESHOLD);
    }

    log.debug("Thread utilization recorded: {}%", utilizationPercent);
  }

  /** Records buffer fill time and tracks performance against expected range. */
  public void recordBufferFillTime(long fillTimeMs) {
    totalBufferFillOperations.incrementAndGet();
    totalBufferFillTimeMs.addAndGet(fillTimeMs);

    if (fillTimeMs < BUFFER_FILL_MIN_MS || fillTimeMs > BUFFER_FILL_MAX_MS) {
      bufferFillOutsideRangeCount.incrementAndGet();
      log.warn(
          "Buffer fill time outside optimal range: {} ms (expected: {}-{} ms)",
          fillTimeMs,
          BUFFER_FILL_MIN_MS,
          BUFFER_FILL_MAX_MS);
    }

    log.debug("Buffer fill time recorded: {} ms", fillTimeMs);
  }

  /** Records Firehose batch size for composition analysis. */
  public void recordFirehoseBatchSize(int batchSize) {
    totalFirehoseBatches.incrementAndGet();
    totalFirehoseBatchSize.addAndGet(batchSize);

    String category = categorizeFirehoseBatchSize(batchSize);
    firehoseBatchSizeDistribution.get(category).incrementAndGet();

    log.debug("Firehose batch size recorded: {} (category: {})", batchSize, category);
  }

  private String categorizeFirehoseBatchSize(int batchSize) {
    if (batchSize <= 60) {
      return "40-60";
    } else if (batchSize <= 100) {
      return "61-100";
    } else {
      return "101-136";
    }
  }

  /** Records consumer lag with rate limiting context. */
  public void recordConsumerLag(long lagMessages, boolean rateLimitingActive) {
    totalConsumerLagRecords.incrementAndGet();
    totalConsumerLag.addAndGet(lagMessages);
    currentConsumerLag.set(lagMessages);

    if (lagMessages > CONSUMER_LAG_THRESHOLD && rateLimitingActive) {
      highLagDuringRateLimitingCount.incrementAndGet();
      log.warn("High consumer lag during rate limiting: {} messages", lagMessages);
    }

    log.debug(
        "Consumer lag recorded: {} messages (rate limiting: {})", lagMessages, rateLimitingActive);
  }

  /** Records a batch that was accepted for processing. */
  public void recordBatchAccepted() {
    totalBatchAttempts.incrementAndGet();
    log.debug("Batch accepted. Total attempts: {}", totalBatchAttempts.get());
  }

  /** Records a batch that was rejected with reason. */
  public void recordBatchRejected(String reason) {
    totalBatchAttempts.incrementAndGet();
    totalBatchRejections.incrementAndGet();

    batchRejectionReasons.computeIfAbsent(reason, k -> new AtomicInteger(0)).incrementAndGet();

    log.debug("Batch rejected ({}). Total rejections: {}", reason, totalBatchRejections.get());
  }

  /** Records batch size effectiveness correlation data. */
  public void recordBatchSizeEffectiveness(int batchSize, boolean successful) {
    String category = categorizeBatchSize(batchSize);

    batchSizeTotalCount.get(category).incrementAndGet();
    if (successful) {
      batchSizeSuccessCount.get(category).incrementAndGet();
    }

    log.debug(
        "Batch size effectiveness recorded: {} (category: {}, successful: {})",
        batchSize,
        category,
        successful);
  }

  private String categorizeBatchSize(int batchSize) {
    if (batchSize <= 60) {
      return "small(<=60)";
    } else if (batchSize <= 100) {
      return "medium(61-100)";
    } else {
      return "large(101-150)";
    }
  }

  /** Records throttling events with type and duration. */
  public void recordThrottlingEvent(String throttlingType, long durationMs) {
    totalThrottlingEvents.incrementAndGet();

    throttlingEventCounts
        .computeIfAbsent(throttlingType, k -> new AtomicInteger(0))
        .incrementAndGet();
    throttlingEventDurations
        .computeIfAbsent(throttlingType, k -> new AtomicLong(0))
        .addAndGet(durationMs);

    log.debug("Throttling event recorded: {} (duration: {} ms)", throttlingType, durationMs);
  }

  // Getter methods for metrics access

  public long getTotalPollOperations() {
    return totalPollOperations.get();
  }

  public double getAveragePollProcessingTimeMs() {
    long operations = totalPollOperations.get();
    return operations == 0 ? 0.0 : (double) totalPollProcessingTimeMs.get() / operations;
  }

  public int getSlowPollCount() {
    return slowPollCount.get();
  }

  public boolean isCurrentPollPerformanceAlert() {
    return currentPollPerformanceAlert.get();
  }

  public double getAverageThreadUtilization() {
    long records = threadUtilizationRecords.get();
    return records == 0 ? 0.0 : totalThreadUtilization.get() / records;
  }

  public double getCurrentThreadUtilization() {
    return currentThreadUtilization.get();
  }

  public boolean isHighThreadUtilizationAlert() {
    return getCurrentThreadUtilization() > THREAD_UTILIZATION_THRESHOLD;
  }

  public double getAverageBufferFillTimeMs() {
    long operations = totalBufferFillOperations.get();
    return operations == 0 ? 0.0 : (double) totalBufferFillTimeMs.get() / operations;
  }

  public long getTotalBufferFillOperations() {
    return totalBufferFillOperations.get();
  }

  public int getBufferFillOutsideRangeCount() {
    return bufferFillOutsideRangeCount.get();
  }

  public boolean isOptimalBufferFillPerformance() {
    return getBufferFillOutsideRangeCount() == 0;
  }

  public Map<String, Integer> getFirehoseBatchSizeDistribution() {
    Map<String, Integer> distribution = new HashMap<>();
    firehoseBatchSizeDistribution.forEach((key, value) -> distribution.put(key, value.get()));
    return distribution;
  }

  public double getAverageFirehoseBatchSize() {
    long batches = totalFirehoseBatches.get();
    return batches == 0 ? 0.0 : (double) totalFirehoseBatchSize.get() / batches;
  }

  public long getTotalFirehoseBatches() {
    return totalFirehoseBatches.get();
  }

  public double getAverageConsumerLag() {
    long records = totalConsumerLagRecords.get();
    return records == 0 ? 0.0 : (double) totalConsumerLag.get() / records;
  }

  public long getCurrentConsumerLag() {
    return currentConsumerLag.get();
  }

  public int getHighLagDuringRateLimitingCount() {
    return highLagDuringRateLimitingCount.get();
  }

  public boolean isConsumerLagAlert() {
    return getCurrentConsumerLag() > CONSUMER_LAG_THRESHOLD;
  }

  public double getBatchRejectionRate() {
    long total = totalBatchAttempts.get();
    return total == 0 ? 0.0 : (totalBatchRejections.get() * 100.0) / total;
  }

  public long getTotalBatchAttempts() {
    return totalBatchAttempts.get();
  }

  public long getTotalBatchRejections() {
    return totalBatchRejections.get();
  }

  public boolean isBatchRejectionRateAlert() {
    return getBatchRejectionRate() > BATCH_REJECTION_THRESHOLD;
  }

  public Map<String, Integer> getBatchRejectionReasons() {
    Map<String, Integer> reasons = new HashMap<>();
    batchRejectionReasons.forEach((key, value) -> reasons.put(key, value.get()));
    return reasons;
  }

  public Map<String, Double> getBatchSizeEffectivenessCorrelation() {
    Map<String, Double> effectiveness = new HashMap<>();

    batchSizeTotalCount.forEach(
        (category, totalCount) -> {
          int total = totalCount.get();
          int successful = batchSizeSuccessCount.get(category).get();
          double successRate = total == 0 ? 0.0 : (successful * 100.0) / total;
          effectiveness.put(category, successRate);
        });

    return effectiveness;
  }

  public double getOverallBatchSuccessRate() {
    int totalSuccess = batchSizeSuccessCount.values().stream().mapToInt(AtomicInteger::get).sum();
    int totalAttempts = batchSizeTotalCount.values().stream().mapToInt(AtomicInteger::get).sum();
    return totalAttempts == 0 ? 0.0 : (totalSuccess * 100.0) / totalAttempts;
  }

  public Map<String, Integer> getThrottlingEventCounts() {
    Map<String, Integer> counts = new HashMap<>();
    throttlingEventCounts.forEach((key, value) -> counts.put(key, value.get()));
    return counts;
  }

  public Map<String, Double> getAverageThrottlingDurations() {
    Map<String, Double> durations = new HashMap<>();

    throttlingEventCounts.forEach(
        (type, count) -> {
          long totalDuration = throttlingEventDurations.get(type).get();
          double averageDuration = count.get() == 0 ? 0.0 : (double) totalDuration / count.get();
          durations.put(type, averageDuration);
        });

    return durations;
  }

  public long getTotalThrottlingEvents() {
    return totalThrottlingEvents.get();
  }

  /** Provides comprehensive summary of all advanced metrics. */
  public String getAdvancedMetricsSummary() {
    return """
                Advanced Performance Metrics Summary:

                Poll Processing Performance:
                  Total Poll Operations: %d
                  Average Poll Time: %.2f ms
                  Slow Polls (>1.2s): %d
                  Performance Alert: %s

                Thread Utilization:
                  Current Utilization: %.2f%%
                  Average Utilization: %.2f%%
                  High Utilization Alert: %s

                Buffer Fill Performance:
                  Total Operations: %d
                  Average Fill Time: %.2f ms
                  Outside Range Count: %d
                  Optimal Performance: %s

                Firehose Batch Composition:
                  Total Batches: %d
                  Average Batch Size: %.2f
                  Size Distribution: %s

                Consumer Lag Analysis:
                  Current Lag: %d messages
                  Average Lag: %.2f messages
                  High Lag During Rate Limiting: %d
                  Lag Alert: %s

                Batch Processing Effectiveness:
                  Total Attempts: %d
                  Rejection Rate: %.2f%%
                  Overall Success Rate: %.2f%%
                  Rejection Alert: %s

                Throttling Events:
                  Total Events: %d
                  Event Counts: %s
                  Average Durations: %s"""
        .formatted(
            getTotalPollOperations(),
            getAveragePollProcessingTimeMs(),
            getSlowPollCount(),
            isCurrentPollPerformanceAlert(),
            getCurrentThreadUtilization(),
            getAverageThreadUtilization(),
            isHighThreadUtilizationAlert(),
            getTotalBufferFillOperations(),
            getAverageBufferFillTimeMs(),
            getBufferFillOutsideRangeCount(),
            isOptimalBufferFillPerformance(),
            getTotalFirehoseBatches(),
            getAverageFirehoseBatchSize(),
            getFirehoseBatchSizeDistribution(),
            getCurrentConsumerLag(),
            getAverageConsumerLag(),
            getHighLagDuringRateLimitingCount(),
            isConsumerLagAlert(),
            getTotalBatchAttempts(),
            getBatchRejectionRate(),
            getOverallBatchSuccessRate(),
            isBatchRejectionRateAlert(),
            getTotalThrottlingEvents(),
            getThrottlingEventCounts(),
            getAverageThrottlingDurations());
  }

  /** Resets all advanced metrics to initial state. */
  public void resetAdvancedMetrics() {
    // Reset poll processing metrics
    totalPollOperations.set(0);
    totalPollProcessingTimeMs.set(0);
    slowPollCount.set(0);
    currentPollPerformanceAlert.set(false);

    // Reset thread utilization metrics
    threadUtilizationRecords.set(0);
    totalThreadUtilization.set(0.0);
    currentThreadUtilization.set(0.0);

    // Reset buffer fill metrics
    totalBufferFillOperations.set(0);
    totalBufferFillTimeMs.set(0);
    bufferFillOutsideRangeCount.set(0);

    // Reset Firehose metrics
    totalFirehoseBatches.set(0);
    totalFirehoseBatchSize.set(0);
    firehoseBatchSizeDistribution.values().forEach(counter -> counter.set(0));

    // Reset consumer lag metrics
    totalConsumerLagRecords.set(0);
    totalConsumerLag.set(0);
    currentConsumerLag.set(0);
    highLagDuringRateLimitingCount.set(0);

    // Reset batch rejection metrics
    totalBatchAttempts.set(0);
    totalBatchRejections.set(0);
    batchRejectionReasons.clear();

    // Reset effectiveness metrics
    batchSizeSuccessCount.values().forEach(counter -> counter.set(0));
    batchSizeTotalCount.values().forEach(counter -> counter.set(0));

    // Reset throttling metrics
    throttlingEventCounts.clear();
    throttlingEventDurations.clear();
    totalThrottlingEvents.set(0);

    log.info("Advanced performance metrics reset");
  }
}
