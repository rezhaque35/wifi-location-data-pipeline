package com.wifi.scan.consume.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.scan.consume.service.FirehoseExceptionClassifier.ExceptionType;

/**
 * Unit tests for EnhancedRetryStrategy.
 *
 * <p>Tests comprehensive retry logic including retry decision making, delay calculations,
 * metrics tracking, and exception-specific strategies.
 */
@ExtendWith(MockitoExtension.class)
class EnhancedRetryStrategyTest {

  private EnhancedRetryStrategy retryStrategy;

  @BeforeEach
  void setUp() {
    // Initialize with default test values
    retryStrategy = new EnhancedRetryStrategy(
        7, // bufferFullMaxRetries
        5, // rateLimitMaxRetries  
        3, // networkIssueMaxRetries
        5, // genericFailureMaxRetries
        Duration.ofSeconds(5), // bufferFullBaseDelay
        Duration.ofSeconds(1), // rateLimitBaseDelay
        Duration.ofSeconds(1), // networkIssueBaseDelay
        Duration.ofSeconds(2)  // genericFailureBaseDelay
    );
  }

  @Test
  @DisplayName("Should allow retry when attempts are below maximum")
  void shouldRetry_WhenBelowMaxAttempts_ShouldReturnTrue() {
    // Given
    ExceptionType exceptionType = ExceptionType.BUFFER_FULL;
    int currentAttempt = 3;

    // When
    boolean shouldRetry = retryStrategy.shouldRetry(exceptionType, currentAttempt);

    // Then
    assertTrue(shouldRetry);
  }

  @Test
  @DisplayName("Should not allow retry when attempts reach maximum")
  void shouldRetry_WhenAtMaxAttempts_ShouldReturnFalse() {
    // Given
    ExceptionType exceptionType = ExceptionType.BUFFER_FULL;
    int currentAttempt = 7; // Max is 7

    // When
    boolean shouldRetry = retryStrategy.shouldRetry(exceptionType, currentAttempt);

    // Then
    assertFalse(shouldRetry);
  }

  @Test
  @DisplayName("Should calculate buffer full delay with graduated backoff")
  void calculateRetryDelay_ForBufferFull_ShouldUseGraduatedBackoff() {
    // Given
    ExceptionType exceptionType = ExceptionType.BUFFER_FULL;

    // When & Then
    Duration delay0 = retryStrategy.calculateRetryDelay(exceptionType, 0);
    Duration delay1 = retryStrategy.calculateRetryDelay(exceptionType, 1);
    Duration delay2 = retryStrategy.calculateRetryDelay(exceptionType, 2);
    Duration delay3 = retryStrategy.calculateRetryDelay(exceptionType, 3);
    Duration delay4 = retryStrategy.calculateRetryDelay(exceptionType, 4);

    // Verify graduated delays (allowing for jitter)
    assertTrue(delay0.toMillis() >= 3750 && delay0.toMillis() <= 6250); // 5s ±25%
    assertTrue(delay1.toMillis() >= 11250 && delay1.toMillis() <= 18750); // 15s ±25%
    assertTrue(delay2.toMillis() >= 33750 && delay2.toMillis() <= 56250); // 45s ±25%
    assertTrue(delay3.toMillis() >= 90000 && delay3.toMillis() <= 150000); // 2min ±25%
    assertTrue(delay4.toMillis() >= 225000 && delay4.toMillis() <= 375000); // 5min ±25%
  }

  @Test
  @DisplayName("Should calculate rate limit delay with exponential backoff")
  void calculateRetryDelay_ForRateLimit_ShouldUseExponentialBackoff() {
    // Given
    ExceptionType exceptionType = ExceptionType.RATE_LIMIT;

    // When
    Duration delay0 = retryStrategy.calculateRetryDelay(exceptionType, 0);
    Duration delay1 = retryStrategy.calculateRetryDelay(exceptionType, 1);
    Duration delay2 = retryStrategy.calculateRetryDelay(exceptionType, 2);

    // Then - exponential backoff with jitter (1s → 2s → 4s)
    assertTrue(delay0.toMillis() >= 750 && delay0.toMillis() <= 1250); // 1s ±25%
    assertTrue(delay1.toMillis() >= 1500 && delay1.toMillis() <= 2500); // 2s ±25%
    assertTrue(delay2.toMillis() >= 3000 && delay2.toMillis() <= 5000); // 4s ±25%
  }

  @Test
  @DisplayName("Should calculate network issue delay with quick retries")
  void calculateRetryDelay_ForNetworkIssue_ShouldUseQuickRetries() {
    // Given
    ExceptionType exceptionType = ExceptionType.NETWORK_ISSUE;

    // When
    Duration delay0 = retryStrategy.calculateRetryDelay(exceptionType, 0);
    Duration delay1 = retryStrategy.calculateRetryDelay(exceptionType, 1);
    Duration delay2 = retryStrategy.calculateRetryDelay(exceptionType, 2);

    // Then - quick retries with jitter (1s → 2s → 4s)
    assertTrue(delay0.toMillis() >= 750 && delay0.toMillis() <= 1250); // 1s ±25%
    assertTrue(delay1.toMillis() >= 1500 && delay1.toMillis() <= 2500); // 2s ±25%
    assertTrue(delay2.toMillis() >= 3000 && delay2.toMillis() <= 5000); // 4s ±25%
  }

  @Test
  @DisplayName("Should calculate generic failure delay with exponential backoff")
  void calculateRetryDelay_ForGenericFailure_ShouldUseExponentialBackoff() {
    // Given
    ExceptionType exceptionType = ExceptionType.GENERIC_FAILURE;

    // When
    Duration delay0 = retryStrategy.calculateRetryDelay(exceptionType, 0);
    Duration delay1 = retryStrategy.calculateRetryDelay(exceptionType, 1);

    // Then - exponential backoff with base delay of 2s
    assertTrue(delay0.toMillis() >= 1500 && delay0.toMillis() <= 2500); // 2s ±25%
    assertTrue(delay1.toMillis() >= 3000 && delay1.toMillis() <= 5000); // 4s ±25%
  }

  @Test
  @DisplayName("Should respect maximum retry limits for different exception types")
  void shouldRetry_ShouldRespectMaxRetryLimits() {
    // Test BUFFER_FULL (max 7)
    assertTrue(retryStrategy.shouldRetry(ExceptionType.BUFFER_FULL, 6));
    assertFalse(retryStrategy.shouldRetry(ExceptionType.BUFFER_FULL, 7));

    // Test RATE_LIMIT (max 5)
    assertTrue(retryStrategy.shouldRetry(ExceptionType.RATE_LIMIT, 4));
    assertFalse(retryStrategy.shouldRetry(ExceptionType.RATE_LIMIT, 5));

    // Test NETWORK_ISSUE (max 3)
    assertTrue(retryStrategy.shouldRetry(ExceptionType.NETWORK_ISSUE, 2));
    assertFalse(retryStrategy.shouldRetry(ExceptionType.NETWORK_ISSUE, 3));

    // Test GENERIC_FAILURE (max 5)
    assertTrue(retryStrategy.shouldRetry(ExceptionType.GENERIC_FAILURE, 4));
    assertFalse(retryStrategy.shouldRetry(ExceptionType.GENERIC_FAILURE, 5));
  }

  @Test
  @DisplayName("Should track retry metrics correctly")
  void retryMetrics_ShouldTrackCorrectly() {
    // Given
    retryStrategy.shouldRetry(ExceptionType.BUFFER_FULL, 0);
    retryStrategy.shouldRetry(ExceptionType.RATE_LIMIT, 0);
    retryStrategy.shouldRetry(ExceptionType.NETWORK_ISSUE, 0);
    retryStrategy.recordSuccessfulRetry();
    retryStrategy.recordFailedRetry();

    // When
    EnhancedRetryStrategy.RetryMetrics metrics = retryStrategy.getRetryMetrics();

    // Then
    assertEquals(3, metrics.getTotalAttempts());
    assertEquals(1, metrics.getSuccessfulRetries());
    assertEquals(1, metrics.getFailedRetries());
    assertEquals(1, metrics.getBufferFullRetries());
    assertEquals(1, metrics.getRateLimitRetries());
    assertEquals(1, metrics.getNetworkIssueRetries());
    assertEquals(0, metrics.getGenericFailureRetries());
  }

  @Test
  @DisplayName("Should calculate success rate correctly")
  void retryMetrics_ShouldCalculateSuccessRateCorrectly() {
    // Given
    retryStrategy.recordSuccessfulRetry();
    retryStrategy.recordSuccessfulRetry();
    retryStrategy.recordSuccessfulRetry();
    retryStrategy.recordFailedRetry();

    // When
    EnhancedRetryStrategy.RetryMetrics metrics = retryStrategy.getRetryMetrics();

    // Then
    assertEquals(0.75, metrics.getSuccessRate(), 0.01); // 3/4 = 75%
  }

  @Test
  @DisplayName("Should handle zero completed retries in success rate calculation")
  void retryMetrics_WithZeroCompletedRetries_ShouldReturnZeroSuccessRate() {
    // Given - no completed retries recorded

    // When
    EnhancedRetryStrategy.RetryMetrics metrics = retryStrategy.getRetryMetrics();

    // Then
    assertEquals(0.0, metrics.getSuccessRate(), 0.01);
  }

  @Test
  @DisplayName("Should reset metrics correctly")
  void resetMetrics_ShouldClearAllCounters() {
    // Given
    retryStrategy.shouldRetry(ExceptionType.BUFFER_FULL, 0);
    retryStrategy.recordSuccessfulRetry();
    retryStrategy.recordFailedRetry();

    // When
    retryStrategy.resetMetrics();

    // Then
    EnhancedRetryStrategy.RetryMetrics metrics = retryStrategy.getRetryMetrics();
    assertEquals(0, metrics.getTotalAttempts());
    assertEquals(0, metrics.getSuccessfulRetries());
    assertEquals(0, metrics.getFailedRetries());
    assertEquals(0, metrics.getBufferFullRetries());
    assertEquals(0, metrics.getRateLimitRetries());
    assertEquals(0, metrics.getNetworkIssueRetries());
    assertEquals(0, metrics.getGenericFailureRetries());
  }

  @Test
  @DisplayName("Should return valid retry metrics object")
  void getRetryMetrics_ShouldReturnValidMetricsObject() {
    // When
    EnhancedRetryStrategy.RetryMetrics metrics = retryStrategy.getRetryMetrics();

    // Then
    assertNotNull(metrics);
    assertTrue(metrics.getTotalAttempts() >= 0);
    assertTrue(metrics.getSuccessfulRetries() >= 0);
    assertTrue(metrics.getFailedRetries() >= 0);
    assertNotNull(metrics.toString());
  }

  @Test
  @DisplayName("Should cap exponential backoff at maximum duration")
  void calculateRetryDelay_ShouldCapExponentialBackoff() {
    // Given
    ExceptionType exceptionType = ExceptionType.RATE_LIMIT;
    int highAttemptNumber = 10; // Should be capped

    // When
    Duration delay = retryStrategy.calculateRetryDelay(exceptionType, highAttemptNumber);

    // Then - should be capped at 30 seconds + jitter
    assertTrue(delay.toMillis() <= 37500); // 30s + 25% jitter
  }

  @Test
  @DisplayName("Should increment correct retry counters based on exception type")
  void shouldRetry_ShouldIncrementCorrectCounters() {
    // Given
    EnhancedRetryStrategy.RetryMetrics initialMetrics = retryStrategy.getRetryMetrics();

    // When
    retryStrategy.shouldRetry(ExceptionType.BUFFER_FULL, 0);
    retryStrategy.shouldRetry(ExceptionType.BUFFER_FULL, 1);
    retryStrategy.shouldRetry(ExceptionType.RATE_LIMIT, 0);

    // Then
    EnhancedRetryStrategy.RetryMetrics metrics = retryStrategy.getRetryMetrics();
    assertEquals(initialMetrics.getBufferFullRetries() + 2, metrics.getBufferFullRetries());
    assertEquals(initialMetrics.getRateLimitRetries() + 1, metrics.getRateLimitRetries());
    assertEquals(initialMetrics.getTotalAttempts() + 3, metrics.getTotalAttempts());
  }
}
