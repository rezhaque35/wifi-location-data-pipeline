package com.wifi.scan.consume.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.firehose.model.FirehoseException;
import software.amazon.awssdk.services.firehose.model.ServiceUnavailableException;
import software.amazon.awssdk.core.exception.SdkException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for classifying AWS Firehose exceptions into specific failure types.
 * 
 * This service implements intelligent exception classification to enable appropriate
 * retry strategies and error handling for different types of Firehose failures.
 * It distinguishes between buffer full scenarios, rate limiting, network issues,
 * and generic failures to enable targeted response strategies.
 * 
 * Exception Classifications:
 * - BUFFER_FULL: Firehose internal buffer is at capacity (123MB+)
 * - RATE_LIMIT: Firehose rate limiting exceeded (5MB/sec or other limits)
 * - NETWORK_ISSUE: Network connectivity or timeout problems
 * - GENERIC_FAILURE: Other failures requiring standard error handling
 * 
 * Key Features:
 * - AWS SDK exception analysis with error code parsing
 * - Exception pattern recognition and classification
 * - Metrics tracking for different exception types
 * - Comprehensive logging for operational insights
 * 
 * @see BatchFirehoseMessageService
 * @see EnhancedRetryStrategy
 */
@Slf4j
@Service
public class FirehoseExceptionClassifier {

    // Exception classification metrics
    private final AtomicLong bufferFullExceptions = new AtomicLong(0);
    private final AtomicLong rateLimitExceptions = new AtomicLong(0);
    private final AtomicLong networkIssueExceptions = new AtomicLong(0);
    private final AtomicLong genericFailureExceptions = new AtomicLong(0);

    /**
     * Enumeration of Firehose exception types for targeted handling.
     */
    public enum ExceptionType {
        BUFFER_FULL("Buffer Full - Firehose internal buffer at capacity"),
        RATE_LIMIT("Rate Limit - Firehose throughput limits exceeded"),
        NETWORK_ISSUE("Network Issue - Connectivity or timeout problems"),
        GENERIC_FAILURE("Generic Failure - Other error requiring standard handling");

        private final String description;

        ExceptionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Classifies an exception into a specific failure type for targeted handling.
     * 
     * This method analyzes AWS SDK exceptions to determine the most appropriate
     * classification based on error codes, exception types, and message content.
     * The classification enables targeted retry strategies and error handling.
     * 
     * Classification Logic:
     * 1. Buffer Full: ServiceUnavailableException with buffer-related indicators
     * 2. Rate Limit: Throttling exceptions and rate limit error codes
     * 3. Network Issue: Connection timeouts, DNS issues, network failures
     * 4. Generic Failure: All other exceptions requiring standard handling
     * 
     * @param exception The exception to classify
     * @return The classified exception type for appropriate handling
     */
    public ExceptionType classifyException(Exception exception) {
        if (exception == null) {
            return ExceptionType.GENERIC_FAILURE;
        }

        try {
            // Check for buffer full scenarios
            if (isBufferFullException(exception)) {
                bufferFullExceptions.incrementAndGet();
                log.debug("Classified as BUFFER_FULL: {}", exception.getMessage());
                return ExceptionType.BUFFER_FULL;
            }

            // Check for rate limiting scenarios
            if (isRateLimitException(exception)) {
                rateLimitExceptions.incrementAndGet();
                log.debug("Classified as RATE_LIMIT: {}", exception.getMessage());
                return ExceptionType.RATE_LIMIT;
            }

            // Check for network issues
            if (isNetworkIssueException(exception)) {
                networkIssueExceptions.incrementAndGet();
                log.debug("Classified as NETWORK_ISSUE: {}", exception.getMessage());
                return ExceptionType.NETWORK_ISSUE;
            }

            // Default to generic failure
            genericFailureExceptions.incrementAndGet();
            log.debug("Classified as GENERIC_FAILURE: {}", exception.getMessage());
            return ExceptionType.GENERIC_FAILURE;

        } catch (Exception e) {
            log.warn("Error during exception classification, defaulting to GENERIC_FAILURE: {}", e.getMessage());
            genericFailureExceptions.incrementAndGet();
            return ExceptionType.GENERIC_FAILURE;
        }
    }

    /**
     * Determines if an exception indicates a Firehose buffer full scenario.
     * 
     * Buffer full scenarios occur when the Firehose internal buffer (typically 128MB)
     * is at capacity and cannot accept additional data. This is indicated by specific
     * ServiceUnavailableException patterns and error messages.
     * 
     * Detection Patterns:
     * - ServiceUnavailableException from Firehose
     * - Error messages containing "buffer", "capacity", "full"
     * - Specific AWS error codes indicating buffer issues
     * 
     * @param exception The exception to analyze
     * @return true if the exception indicates buffer full, false otherwise
     */
    private boolean isBufferFullException(Exception exception) {
        // Check for ServiceUnavailableException (primary indicator of buffer full)
        if (exception instanceof ServiceUnavailableException) {
            return true;
        }

        // Check for Firehose-specific exceptions with buffer indicators
        if (exception instanceof FirehoseException) {
            FirehoseException firehoseEx = (FirehoseException) exception;
            String errorCode = firehoseEx.awsErrorDetails() != null ? 
                              firehoseEx.awsErrorDetails().errorCode() : "";
            String message = exception.getMessage().toLowerCase();

            // Look for buffer-related error codes and messages
            return errorCode.contains("ServiceUnavailable") ||
                   errorCode.contains("BufferFull") ||
                   message.contains("buffer") ||
                   message.contains("capacity") ||
                   message.contains("full") ||
                   message.contains("unavailable");
        }

        // Check exception message for buffer-related keywords
        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("service unavailable") ||
                   lowerMessage.contains("buffer full") ||
                   lowerMessage.contains("capacity exceeded");
        }

        return false;
    }

    /**
     * Determines if an exception indicates Firehose rate limiting.
     * 
     * Rate limiting occurs when throughput exceeds Firehose limits (5MB/sec, etc.).
     * This is typically indicated by throttling exceptions and specific error codes.
     * 
     * Detection Patterns:
     * - Throttling-related error codes
     * - Rate limit error messages
     * - HTTP 429 status codes
     * - Specific AWS throttling exceptions
     * 
     * @param exception The exception to analyze
     * @return true if the exception indicates rate limiting, false otherwise
     */
    private boolean isRateLimitException(Exception exception) {
        if (exception instanceof FirehoseException) {
            FirehoseException firehoseEx = (FirehoseException) exception;
            String errorCode = firehoseEx.awsErrorDetails() != null ? 
                              firehoseEx.awsErrorDetails().errorCode() : "";
            String message = exception.getMessage().toLowerCase();

            // Check for throttling and rate limit indicators
            return errorCode.contains("Throttling") ||
                   errorCode.contains("TooManyRequests") ||
                   errorCode.contains("RateLimit") ||
                   message.contains("throttling") ||
                   message.contains("rate limit") ||
                   message.contains("too many requests") ||
                   message.contains("exceeded throughput");
        }

        // Check for SDK-level throttling exceptions
        if (exception instanceof SdkException) {
            String message = exception.getMessage();
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                return lowerMessage.contains("throttling") ||
                       lowerMessage.contains("rate limit") ||
                       lowerMessage.contains("429");
            }
        }

        return false;
    }

    /**
     * Determines if an exception indicates a network connectivity issue.
     * 
     * Network issues include connection timeouts, DNS resolution failures,
     * and other network-level problems that may be temporary.
     * 
     * Detection Patterns:
     * - Connection timeout exceptions
     * - DNS resolution failures
     * - Network unreachable errors
     * - Socket exceptions
     * 
     * @param exception The exception to analyze
     * @return true if the exception indicates network issues, false otherwise
     */
    private boolean isNetworkIssueException(Exception exception) {
        // Check for common network exception types
        if (exception instanceof ConnectException ||
            exception instanceof SocketTimeoutException ||
            exception instanceof UnknownHostException) {
            return true;
        }

        // Check for network-related error messages
        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("connection timeout") ||
                   lowerMessage.contains("connect timed out") ||
                   lowerMessage.contains("network unreachable") ||
                   lowerMessage.contains("no route to host") ||
                   lowerMessage.contains("connection refused") ||
                   lowerMessage.contains("dns resolution failed");
        }

        // Check for SDK network exceptions
        if (exception instanceof SdkException) {
            Throwable cause = exception.getCause();
            return cause instanceof ConnectException ||
                   cause instanceof SocketTimeoutException ||
                   cause instanceof UnknownHostException;
        }

        return false;
    }

    /**
     * Gets comprehensive metrics for all exception types.
     * 
     * @return ExceptionMetrics object containing counts for all exception types
     */
    public ExceptionMetrics getExceptionMetrics() {
        return new ExceptionMetrics(
            bufferFullExceptions.get(),
            rateLimitExceptions.get(),
            networkIssueExceptions.get(),
            genericFailureExceptions.get()
        );
    }

    /**
     * Resets all exception metrics counters.
     * Used for testing and periodic metrics reset.
     */
    public void resetMetrics() {
        bufferFullExceptions.set(0);
        rateLimitExceptions.set(0);
        networkIssueExceptions.set(0);
        genericFailureExceptions.set(0);
        log.info("Firehose exception classification metrics reset");
    }

    /**
     * Data class for exception metrics.
     */
    public static class ExceptionMetrics {
        private final long bufferFullCount;
        private final long rateLimitCount;
        private final long networkIssueCount;
        private final long genericFailureCount;

        public ExceptionMetrics(long bufferFullCount, long rateLimitCount, 
                              long networkIssueCount, long genericFailureCount) {
            this.bufferFullCount = bufferFullCount;
            this.rateLimitCount = rateLimitCount;
            this.networkIssueCount = networkIssueCount;
            this.genericFailureCount = genericFailureCount;
        }

        public long getBufferFullCount() { return bufferFullCount; }
        public long getRateLimitCount() { return rateLimitCount; }
        public long getNetworkIssueCount() { return networkIssueCount; }
        public long getGenericFailureCount() { return genericFailureCount; }
        public long getTotalCount() { 
            return bufferFullCount + rateLimitCount + networkIssueCount + genericFailureCount; 
        }

        @Override
        public String toString() {
            return String.format("ExceptionMetrics{bufferFull=%d, rateLimit=%d, network=%d, generic=%d, total=%d}",
                    bufferFullCount, rateLimitCount, networkIssueCount, genericFailureCount, getTotalCount());
        }
    }
} 