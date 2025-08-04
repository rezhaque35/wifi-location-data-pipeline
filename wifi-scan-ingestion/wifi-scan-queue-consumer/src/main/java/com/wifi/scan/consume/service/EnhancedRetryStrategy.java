package com.wifi.scan.consume.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service implementing intelligent retry strategies for different types of Firehose failures.
 * 
 * This service provides exception-specific retry logic with graduated backoff strategies
 * optimized for different failure scenarios. It implements sophisticated retry timing
 * that aligns with Firehose behavior patterns and system recovery characteristics.
 * 
 * Retry Strategies by Exception Type:
 * - BUFFER_FULL: Graduated backoff (5s → 15s → 45s → 2min → 5min) over ~15 minutes
 * - RATE_LIMIT: Standard exponential backoff (1s → 2s → 4s → 8s → 16s → 30s)
 * - NETWORK_ISSUE: Quick retries (1s → 2s → 4s) with limited attempts (3-5)
 * - GENERIC_FAILURE: Standard backoff with moderate attempts (5 max)
 * 
 * Key Features:
 * - Exception-specific retry logic and timing
 * - Jitter to prevent thundering herd problems
 * - Comprehensive retry attempt tracking and metrics
 * - Configurable retry limits and timing parameters
 * - Integration with Firehose buffer flush intervals (5-minute alignment)
 * 
 * @see FirehoseExceptionClassifier
 * @see BatchFirehoseMessageService
 */
@Slf4j
@Service
public class EnhancedRetryStrategy {

    // Configurable retry parameters
    private final int bufferFullMaxRetries;
    private final int rateLimitMaxRetries;
    private final int networkIssueMaxRetries;
    private final int genericFailureMaxRetries;
    private final Duration bufferFullBaseDelay;
    private final Duration rateLimitBaseDelay;
    private final Duration networkIssueBaseDelay;
    private final Duration genericFailureBaseDelay;

    // Retry attempt tracking metrics
    private final AtomicLong totalRetryAttempts = new AtomicLong(0);
    private final AtomicLong successfulRetries = new AtomicLong(0);
    private final AtomicLong failedRetries = new AtomicLong(0);
    private final AtomicLong bufferFullRetries = new AtomicLong(0);
    private final AtomicLong rateLimitRetries = new AtomicLong(0);
    private final AtomicLong networkIssueRetries = new AtomicLong(0);
    private final AtomicLong genericFailureRetries = new AtomicLong(0);

    /**
     * Creates EnhancedRetryStrategy with configurable parameters.
     */
    public EnhancedRetryStrategy(
            @Value("${app.firehose.retry.buffer-full.max-attempts:7}") int bufferFullMaxRetries,
            @Value("${app.firehose.retry.rate-limit.max-attempts:5}") int rateLimitMaxRetries,
            @Value("${app.firehose.retry.network-issue.max-attempts:3}") int networkIssueMaxRetries,
            @Value("${app.firehose.retry.generic-failure.max-attempts:5}") int genericFailureMaxRetries,
            @Value("${app.firehose.retry.buffer-full.base-delay:PT5S}") Duration bufferFullBaseDelay,
            @Value("${app.firehose.retry.rate-limit.base-delay:PT1S}") Duration rateLimitBaseDelay,
            @Value("${app.firehose.retry.network-issue.base-delay:PT1S}") Duration networkIssueBaseDelay,
            @Value("${app.firehose.retry.generic-failure.base-delay:PT2S}") Duration genericFailureBaseDelay) {
        
        this.bufferFullMaxRetries = bufferFullMaxRetries;
        this.rateLimitMaxRetries = rateLimitMaxRetries;
        this.networkIssueMaxRetries = networkIssueMaxRetries;
        this.genericFailureMaxRetries = genericFailureMaxRetries;
        this.bufferFullBaseDelay = bufferFullBaseDelay;
        this.rateLimitBaseDelay = rateLimitBaseDelay;
        this.networkIssueBaseDelay = networkIssueBaseDelay;
        this.genericFailureBaseDelay = genericFailureBaseDelay;

        log.info("EnhancedRetryStrategy initialized: bufferFull={} retries, rateLimit={} retries, " +
                "network={} retries, generic={} retries", 
                bufferFullMaxRetries, rateLimitMaxRetries, networkIssueMaxRetries, genericFailureMaxRetries);
    }

    /**
     * Determines if a retry should be attempted based on exception type and current attempt count.
     * 
     * @param exceptionType The type of exception that occurred
     * @param currentAttempt Current retry attempt number (0-based)
     * @return true if retry should be attempted, false if max retries exceeded
     */
    public boolean shouldRetry(FirehoseExceptionClassifier.ExceptionType exceptionType, int currentAttempt) {
        int maxRetries = getMaxRetries(exceptionType);
        boolean shouldRetry = currentAttempt < maxRetries;
        
        if (shouldRetry) {
            totalRetryAttempts.incrementAndGet();
            incrementRetryCounter(exceptionType);
        }
        
        log.debug("Retry decision for {}: attempt {} of {}, shouldRetry={}", 
                exceptionType, currentAttempt + 1, maxRetries, shouldRetry);
        
        return shouldRetry;
    }

    /**
     * Calculates the appropriate delay before the next retry attempt.
     * 
     * This method implements exception-specific retry timing strategies:
     * - Buffer Full: Graduated backoff aligned with Firehose 5-minute buffer flush
     * - Rate Limit: Standard exponential backoff
     * - Network Issue: Quick retries for transient issues
     * - Generic Failure: Standard backoff with jitter
     * 
     * @param exceptionType The type of exception that occurred
     * @param attemptNumber Current retry attempt number (0-based)
     * @return Duration to wait before next retry attempt
     */
    public Duration calculateRetryDelay(FirehoseExceptionClassifier.ExceptionType exceptionType, int attemptNumber) {
        Duration baseDelay = getBaseDelay(exceptionType);
        Duration calculatedDelay;

        switch (exceptionType) {
            case BUFFER_FULL:
                calculatedDelay = calculateBufferFullDelay(attemptNumber);
                break;
            case RATE_LIMIT:
                calculatedDelay = calculateExponentialBackoff(baseDelay, attemptNumber);
                break;
            case NETWORK_ISSUE:
                calculatedDelay = calculateNetworkIssueDelay(attemptNumber);
                break;
            case GENERIC_FAILURE:
            default:
                calculatedDelay = calculateExponentialBackoff(baseDelay, attemptNumber);
                break;
        }

        // Add jitter to prevent thundering herd
        Duration jitteredDelay = addJitter(calculatedDelay);
        
        log.debug("Calculated retry delay for {} attempt {}: {} (with jitter: {})", 
                exceptionType, attemptNumber + 1, calculatedDelay, jitteredDelay);
        
        return jitteredDelay;
    }

    /**
     * Calculates graduated backoff delay specifically for buffer full scenarios.
     * 
     * This strategy implements increasing delays that align with Firehose's 5-minute
     * buffer flush interval, providing optimal recovery timing for buffer capacity issues.
     * 
     * Delay Schedule: 5s → 15s → 45s → 2min → 5min → 5min → 5min
     * 
     * @param attemptNumber Current retry attempt number (0-based)
     * @return Duration for buffer full retry delay
     */
    private Duration calculateBufferFullDelay(int attemptNumber) {
        switch (attemptNumber) {
            case 0: return Duration.ofSeconds(5);   // 5 seconds
            case 1: return Duration.ofSeconds(15);  // 15 seconds  
            case 2: return Duration.ofSeconds(45);  // 45 seconds
            case 3: return Duration.ofMinutes(2);   // 2 minutes
            default: return Duration.ofMinutes(5);  // 5 minutes (aligned with buffer flush)
        }
    }

    /**
     * Calculates quick retry delays for network issues.
     * 
     * Network issues are often transient, so this strategy uses shorter delays
     * to quickly recover from temporary connectivity problems.
     * 
     * Delay Schedule: 1s → 2s → 4s
     * 
     * @param attemptNumber Current retry attempt number (0-based)
     * @return Duration for network issue retry delay
     */
    private Duration calculateNetworkIssueDelay(int attemptNumber) {
        switch (attemptNumber) {
            case 0: return Duration.ofSeconds(1);   // 1 second
            case 1: return Duration.ofSeconds(2);   // 2 seconds
            default: return Duration.ofSeconds(4);  // 4 seconds
        }
    }

    /**
     * Calculates standard exponential backoff with maximum cap.
     * 
     * @param baseDelay Base delay for first retry
     * @param attemptNumber Current retry attempt number (0-based)
     * @return Duration for exponential backoff delay
     */
    private Duration calculateExponentialBackoff(Duration baseDelay, int attemptNumber) {
        long baseMillis = baseDelay.toMillis();
        long exponentialDelay = baseMillis * (1L << Math.min(attemptNumber, 5)); // Cap at 2^5 = 32x
        long cappedDelay = Math.min(exponentialDelay, Duration.ofSeconds(30).toMillis()); // 30s max
        return Duration.ofMillis(cappedDelay);
    }

    /**
     * Adds jitter to prevent thundering herd problems.
     * 
     * @param delay Base delay duration
     * @return Delay with added jitter (±25%)
     */
    private Duration addJitter(Duration delay) {
        long baseMillis = delay.toMillis();
        long jitter = (long) (baseMillis * 0.25 * (ThreadLocalRandom.current().nextDouble() - 0.5));
        return Duration.ofMillis(Math.max(0, baseMillis + jitter));
    }

    /**
     * Gets maximum retry attempts for the specified exception type.
     */
    private int getMaxRetries(FirehoseExceptionClassifier.ExceptionType exceptionType) {
        switch (exceptionType) {
            case BUFFER_FULL: return bufferFullMaxRetries;
            case RATE_LIMIT: return rateLimitMaxRetries;
            case NETWORK_ISSUE: return networkIssueMaxRetries;
            case GENERIC_FAILURE: return genericFailureMaxRetries;
            default: return genericFailureMaxRetries;
        }
    }

    /**
     * Gets base delay for the specified exception type.
     */
    private Duration getBaseDelay(FirehoseExceptionClassifier.ExceptionType exceptionType) {
        switch (exceptionType) {
            case BUFFER_FULL: return bufferFullBaseDelay;
            case RATE_LIMIT: return rateLimitBaseDelay;
            case NETWORK_ISSUE: return networkIssueBaseDelay;
            case GENERIC_FAILURE: return genericFailureBaseDelay;
            default: return genericFailureBaseDelay;
        }
    }

    /**
     * Increments retry counter for the specific exception type.
     */
    private void incrementRetryCounter(FirehoseExceptionClassifier.ExceptionType exceptionType) {
        switch (exceptionType) {
            case BUFFER_FULL: bufferFullRetries.incrementAndGet(); break;
            case RATE_LIMIT: rateLimitRetries.incrementAndGet(); break;
            case NETWORK_ISSUE: networkIssueRetries.incrementAndGet(); break;
            case GENERIC_FAILURE: genericFailureRetries.incrementAndGet(); break;
        }
    }

    /**
     * Records a successful retry for metrics tracking.
     */
    public void recordSuccessfulRetry() {
        successfulRetries.incrementAndGet();
        log.debug("Recorded successful retry. Total successful: {}", successfulRetries.get());
    }

    /**
     * Records a failed retry (exhausted all attempts) for metrics tracking.
     */
    public void recordFailedRetry() {
        failedRetries.incrementAndGet();
        log.debug("Recorded failed retry. Total failed: {}", failedRetries.get());
    }

    /**
     * Gets comprehensive retry metrics.
     */
    public RetryMetrics getRetryMetrics() {
        return new RetryMetrics(
                totalRetryAttempts.get(),
                successfulRetries.get(),
                failedRetries.get(),
                bufferFullRetries.get(),
                rateLimitRetries.get(),
                networkIssueRetries.get(),
                genericFailureRetries.get()
        );
    }

    /**
     * Resets all retry metrics.
     */
    public void resetMetrics() {
        totalRetryAttempts.set(0);
        successfulRetries.set(0);
        failedRetries.set(0);
        bufferFullRetries.set(0);
        rateLimitRetries.set(0);
        networkIssueRetries.set(0);
        genericFailureRetries.set(0);
        log.info("Enhanced retry strategy metrics reset");
    }

    /**
     * Data class for retry metrics.
     */
    public static class RetryMetrics {
        private final long totalAttempts;
        private final long successfulRetries;
        private final long failedRetries;
        private final long bufferFullRetries;
        private final long rateLimitRetries;
        private final long networkIssueRetries;
        private final long genericFailureRetries;

        public RetryMetrics(long totalAttempts, long successfulRetries, long failedRetries,
                          long bufferFullRetries, long rateLimitRetries, long networkIssueRetries, 
                          long genericFailureRetries) {
            this.totalAttempts = totalAttempts;
            this.successfulRetries = successfulRetries;
            this.failedRetries = failedRetries;
            this.bufferFullRetries = bufferFullRetries;
            this.rateLimitRetries = rateLimitRetries;
            this.networkIssueRetries = networkIssueRetries;
            this.genericFailureRetries = genericFailureRetries;
        }

        // Getters
        public long getTotalAttempts() { return totalAttempts; }
        public long getSuccessfulRetries() { return successfulRetries; }
        public long getFailedRetries() { return failedRetries; }
        public long getBufferFullRetries() { return bufferFullRetries; }
        public long getRateLimitRetries() { return rateLimitRetries; }
        public long getNetworkIssueRetries() { return networkIssueRetries; }
        public long getGenericFailureRetries() { return genericFailureRetries; }

        public double getSuccessRate() {
            long completed = successfulRetries + failedRetries;
            return completed > 0 ? (double) successfulRetries / completed : 0.0;
        }

        @Override
        public String toString() {
            return String.format("RetryMetrics{total=%d, successful=%d, failed=%d, " +
                    "bufferFull=%d, rateLimit=%d, network=%d, generic=%d, successRate=%.2f%%}",
                    totalAttempts, successfulRetries, failedRetries, bufferFullRetries,
                    rateLimitRetries, networkIssueRetries, genericFailureRetries, getSuccessRate() * 100);
        }
    }
} 