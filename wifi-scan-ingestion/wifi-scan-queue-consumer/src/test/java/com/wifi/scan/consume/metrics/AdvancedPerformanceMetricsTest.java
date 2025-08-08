package com.wifi.scan.consume.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for AdvancedPerformanceMetrics following TDD principles. Tests advanced performance
 * monitoring capabilities for high-throughput batch processing.
 */
@ExtendWith(MockitoExtension.class)
class AdvancedPerformanceMetricsTest {

  private AdvancedPerformanceMetrics performanceMetrics;

  @BeforeEach
  void setUp() {
    performanceMetrics = new AdvancedPerformanceMetrics();
  }

  @Test
  @DisplayName("Should track poll processing time and alert when exceeding 1.2 seconds")
  void shouldTrackPollProcessingTimeAndAlert() {
    // Given - Normal poll processing time
    long normalPollTime = 800; // 800ms - within threshold
    long slowPollTime = 1300; // 1.3s - exceeds 1.2s threshold

    // When
    performanceMetrics.recordPollProcessingTime(normalPollTime);
    performanceMetrics.recordPollProcessingTime(slowPollTime);

    // Then
    assertEquals(
        2, performanceMetrics.getTotalPollOperations(), "Should track total poll operations");
    assertEquals(
        1050.0,
        performanceMetrics.getAveragePollProcessingTimeMs(),
        0.1,
        "Should calculate average poll processing time");
    assertEquals(
        1, performanceMetrics.getSlowPollCount(), "Should count polls exceeding 1.2s threshold");
    assertTrue(
        performanceMetrics.isCurrentPollPerformanceAlert(),
        "Should alert when recent polls exceed threshold");
  }

  @Test
  @DisplayName("Should monitor thread utilization percentage and alert when exceeding 80%")
  void shouldMonitorThreadUtilizationAndAlert() {
    // Given - Various thread utilization levels
    double normalUtilization = 65.0; // Normal level
    double highUtilization = 85.0; // Above 80% threshold

    // When
    performanceMetrics.recordThreadUtilization(normalUtilization);
    performanceMetrics.recordThreadUtilization(highUtilization);

    // Then
    assertEquals(
        75.0,
        performanceMetrics.getAverageThreadUtilization(),
        0.1,
        "Should calculate average thread utilization");
    assertEquals(
        85.0,
        performanceMetrics.getCurrentThreadUtilization(),
        0.1,
        "Should track current thread utilization");
    assertTrue(
        performanceMetrics.isHighThreadUtilizationAlert(),
        "Should alert when thread utilization exceeds 80%");
  }

  @Test
  @DisplayName("Should track buffer fill time and validate against expected range 13-44 seconds")
  void shouldTrackBufferFillTime() {
    // Given - Buffer fill times within and outside expected range
    long optimalFillTime = 25_000; // 25s - within 13-44s range
    long slowFillTime = 50_000; // 50s - outside expected range

    // When
    performanceMetrics.recordBufferFillTime(optimalFillTime);
    performanceMetrics.recordBufferFillTime(slowFillTime);

    // Then
    assertEquals(
        37_500.0,
        performanceMetrics.getAverageBufferFillTimeMs(),
        0.1,
        "Should calculate average buffer fill time");
    assertEquals(
        2,
        performanceMetrics.getTotalBufferFillOperations(),
        "Should track total buffer fill operations");
    assertEquals(
        1,
        performanceMetrics.getBufferFillOutsideRangeCount(),
        "Should count buffer fills outside 13-44s range");
    assertFalse(
        performanceMetrics.isOptimalBufferFillPerformance(),
        "Should indicate suboptimal performance when fills outside range");
  }

  @Test
  @DisplayName("Should monitor Firehose batch composition and track size distribution")
  void shouldMonitorFirehoseBatchComposition() {
    // Given - Various Firehose batch sizes
    List<Integer> batchSizes = List.of(40, 75, 100, 136, 45, 120, 80);

    // When
    for (Integer batchSize : batchSizes) {
      performanceMetrics.recordFirehoseBatchSize(batchSize);
    }

    // Then
    Map<String, Integer> distribution = performanceMetrics.getFirehoseBatchSizeDistribution();
    assertTrue(distribution.containsKey("40-60"), "Should track small batch range");
    assertTrue(distribution.containsKey("61-100"), "Should track medium batch range");
    assertTrue(distribution.containsKey("101-136"), "Should track large batch range");

    assertEquals(2, distribution.get("40-60"), "Should count small batches correctly");
    assertEquals(3, distribution.get("61-100"), "Should count medium batches correctly");
    assertEquals(2, distribution.get("101-136"), "Should count large batches correctly");

    assertEquals(
        85.14,
        performanceMetrics.getAverageFirehoseBatchSize(),
        0.1,
        "Should calculate average batch size");
    assertEquals(
        7, performanceMetrics.getTotalFirehoseBatches(), "Should track total Firehose batches");
  }

  @Test
  @DisplayName("Should track consumer lag with rate limiting context")
  void shouldTrackConsumerLagWithRateLimitingContext() {
    // Given - Consumer lag with different contexts
    long normalLag = 150; // Normal lag - processing rate keeping up
    long highLag = 1500; // High lag - falling behind
    boolean rateLimitActive = true;

    // When
    performanceMetrics.recordConsumerLag(normalLag, false);
    performanceMetrics.recordConsumerLag(highLag, rateLimitActive);

    // Then
    assertEquals(
        825.0,
        performanceMetrics.getAverageConsumerLag(),
        0.1,
        "Should calculate average consumer lag");
    assertEquals(
        1500, performanceMetrics.getCurrentConsumerLag(), "Should track current consumer lag");
    assertEquals(
        1,
        performanceMetrics.getHighLagDuringRateLimitingCount(),
        "Should count high lag events during rate limiting");
    assertTrue(
        performanceMetrics.isConsumerLagAlert(),
        "Should alert when consumer lag exceeds threshold");
  }

  @Test
  @DisplayName("Should track batch rejection rate and alert when exceeding 5%")
  void shouldTrackBatchRejectionRateAndAlert() {
    // Given - Batch processing with some rejections
    int totalBatches = 100;
    int rejectedBatches = 7; // 7% rejection rate - above 5% threshold

    // When
    for (int i = 0; i < totalBatches - rejectedBatches; i++) {
      performanceMetrics.recordBatchAccepted();
    }
    for (int i = 0; i < rejectedBatches; i++) {
      performanceMetrics.recordBatchRejected("Rate limiting");
    }

    // Then
    assertEquals(
        7.0,
        performanceMetrics.getBatchRejectionRate(),
        0.1,
        "Should calculate batch rejection rate");
    assertEquals(
        100, performanceMetrics.getTotalBatchAttempts(), "Should track total batch attempts");
    assertEquals(
        7, performanceMetrics.getTotalBatchRejections(), "Should track total batch rejections");
    assertTrue(
        performanceMetrics.isBatchRejectionRateAlert(),
        "Should alert when rejection rate exceeds 5%");

    Map<String, Integer> rejectionReasons = performanceMetrics.getBatchRejectionReasons();
    assertEquals(7, rejectionReasons.get("Rate limiting"), "Should categorize rejection reasons");
  }

  @Test
  @DisplayName("Should monitor dynamic batch size effectiveness correlation")
  void shouldMonitorDynamicBatchSizeEffectiveness() {
    // Given - Various batch sizes with success rates
    performanceMetrics.recordBatchSizeEffectiveness(50, true); // Small batch, success
    performanceMetrics.recordBatchSizeEffectiveness(100, true); // Medium batch, success
    performanceMetrics.recordBatchSizeEffectiveness(150, false); // Large batch, failure
    performanceMetrics.recordBatchSizeEffectiveness(75, true); // Medium batch, success
    performanceMetrics.recordBatchSizeEffectiveness(125, false); // Large batch, failure

    // When
    Map<String, Double> effectiveness = performanceMetrics.getBatchSizeEffectivenessCorrelation();

    // Then
    assertTrue(effectiveness.containsKey("small(<=60)"), "Should track small batch effectiveness");
    assertTrue(
        effectiveness.containsKey("medium(61-100)"), "Should track medium batch effectiveness");
    assertTrue(
        effectiveness.containsKey("large(101-150)"), "Should track large batch effectiveness");

    assertEquals(
        100.0,
        effectiveness.get("small(<=60)"),
        0.1,
        "Small batches should have 100% success rate");
    assertEquals(
        100.0,
        effectiveness.get("medium(61-100)"),
        0.1,
        "Medium batches should have 100% success rate");
    assertEquals(
        0.0,
        effectiveness.get("large(101-150)"),
        0.1,
        "Large batches should have 0% success rate in this scenario");

    assertEquals(
        60.0,
        performanceMetrics.getOverallBatchSuccessRate(),
        0.1,
        "Should calculate overall success rate");
  }

  @Test
  @DisplayName("Should distinguish Firehose throttling from application rate limiting")
  void shouldDistinguishThrottlingTypes() {
    // Given - Different types of throttling events
    String firehoseThrottling = "Firehose throttling";
    String appRateLimiting = "Application rate limiting";

    // When
    performanceMetrics.recordThrottlingEvent(firehoseThrottling, 1500);
    performanceMetrics.recordThrottlingEvent(appRateLimiting, 800);
    performanceMetrics.recordThrottlingEvent(firehoseThrottling, 2000);

    // Then
    Map<String, Integer> throttlingCounts = performanceMetrics.getThrottlingEventCounts();
    assertEquals(
        2, throttlingCounts.get(firehoseThrottling), "Should count Firehose throttling events");
    assertEquals(
        1, throttlingCounts.get(appRateLimiting), "Should count application rate limiting events");

    Map<String, Double> throttlingDurations = performanceMetrics.getAverageThrottlingDurations();
    assertEquals(
        1750.0,
        throttlingDurations.get(firehoseThrottling),
        0.1,
        "Should calculate average Firehose throttling duration");
    assertEquals(
        800.0,
        throttlingDurations.get(appRateLimiting),
        0.1,
        "Should calculate average app rate limiting duration");

    assertEquals(
        3, performanceMetrics.getTotalThrottlingEvents(), "Should track total throttling events");
  }

  @Test
  @DisplayName("Should provide comprehensive performance metrics summary")
  void shouldProvideComprehensiveMetricsSummary() {
    // Given - Various performance metrics recorded
    performanceMetrics.recordPollProcessingTime(1000);
    performanceMetrics.recordThreadUtilization(70.0);
    performanceMetrics.recordBufferFillTime(30_000);
    performanceMetrics.recordFirehoseBatchSize(100);
    performanceMetrics.recordConsumerLag(500, false);
    performanceMetrics.recordBatchAccepted();

    // When
    String summary = performanceMetrics.getAdvancedMetricsSummary();

    // Then
    assertNotNull(summary, "Should provide metrics summary");
    assertTrue(
        summary.contains("Poll Processing Performance"), "Should include poll processing metrics");
    assertTrue(summary.contains("Thread Utilization"), "Should include thread utilization metrics");
    assertTrue(summary.contains("Buffer Fill Performance"), "Should include buffer fill metrics");
    assertTrue(
        summary.contains("Firehose Batch Composition"), "Should include Firehose batch metrics");
    assertTrue(summary.contains("Consumer Lag Analysis"), "Should include consumer lag metrics");
    assertTrue(
        summary.contains("Batch Processing Effectiveness"),
        "Should include batch effectiveness metrics");
  }

  @Test
  @DisplayName("Should reset all advanced metrics")
  void shouldResetAllAdvancedMetrics() {
    // Given - Metrics with recorded data
    performanceMetrics.recordPollProcessingTime(1000);
    performanceMetrics.recordThreadUtilization(70.0);
    performanceMetrics.recordBatchAccepted();

    // When
    performanceMetrics.resetAdvancedMetrics();

    // Then
    assertEquals(
        0, performanceMetrics.getTotalPollOperations(), "Should reset poll operations count");
    assertEquals(
        0.0, performanceMetrics.getCurrentThreadUtilization(), "Should reset thread utilization");
    assertEquals(
        0, performanceMetrics.getTotalBatchAttempts(), "Should reset batch attempts count");
    assertFalse(performanceMetrics.isCurrentPollPerformanceAlert(), "Should reset alert states");
  }
}
