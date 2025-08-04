// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/FirehoseBatchWriter.java
package com.wifi.measurements.transformer.service;


import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClient;
import software.amazon.awssdk.services.firehose.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for writing WiFi measurement batches to Kinesis Data Firehose.
 * Implements comprehensive error handling, retry logic, and monitoring based on AWS best practices.
 */
@Service
public class FirehoseIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(FirehoseIntegrationService.class);

    private final FirehoseAsyncClient firehoseClient;
    private final FirehoseConfigurationProperties firehoseProperties;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService retryExecutor;
    private final FirehoseMonitoringService firehoseMonitoringService;

    public FirehoseIntegrationService(
            FirehoseAsyncClient firehoseClient,
            FirehoseConfigurationProperties firehoseProperties,
            MeterRegistry meterRegistry,
            ScheduledExecutorService retryExecutor,
            FirehoseMonitoringService firehoseMonitoringService) {
        this.firehoseClient = firehoseClient;
        this.firehoseProperties = firehoseProperties;
        this.meterRegistry = meterRegistry;
        this.retryExecutor = retryExecutor;
        this.firehoseMonitoringService = firehoseMonitoringService;
    }

    /**
     * Writes a batch of pre-serialized JSON records to Firehose with error handling and monitoring.
     * 
     * @param jsonRecords list of pre-serialized JSON byte arrays to write
     * @return CompletableFuture that completes when the write operation finishes
     */
    public CompletableFuture<Void> writeBatch(List<byte[]> jsonRecords) {
        return writeBatchWithAttempt(jsonRecords, 0, UUID.randomUUID().toString());
    }

    /**
     * Internal method for writing batches with retry tracking.
     */
    CompletableFuture<Void> writeBatchWithAttempt(List<byte[]> jsonRecords, int attempt, String correlationId) {
        if (jsonRecords == null || jsonRecords.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            // Convert JSON byte arrays to Firehose records
            List<software.amazon.awssdk.services.firehose.model.Record> records = createFirehoseRecords(jsonRecords);
            
            PutRecordBatchRequest request = PutRecordBatchRequest.builder()
                .deliveryStreamName(firehoseProperties.deliveryStreamName())
                .records(records)
                .build();

            logger.debug("Writing batch {} to Firehose: {} records, attempt {}", 
                correlationId, jsonRecords.size(), attempt + 1);

            // Record delivery attempt in monitoring service
            firehoseMonitoringService.recordDeliveryAttempt(jsonRecords.size());

            return firehoseClient.putRecordBatch(request)
                .thenAccept(response -> handleSuccessfulResponse(response, jsonRecords, correlationId))
                .exceptionally(throwable -> handleBatchError(throwable, jsonRecords, attempt, correlationId));

        } catch (Exception e) {
            logger.error("Failed to prepare batch {} for Firehose: {}", correlationId, e.getMessage());
            meterRegistry.counter("firehose.preparation.errors").increment();
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Creates Firehose records from pre-serialized JSON byte arrays.
     */
    private List<software.amazon.awssdk.services.firehose.model.Record> createFirehoseRecords(List<byte[]> jsonRecords) {
        List<software.amazon.awssdk.services.firehose.model.Record> records = new ArrayList<>();
        
        for (byte[] jsonRecordBytes : jsonRecords) {
            software.amazon.awssdk.services.firehose.model.Record record = software.amazon.awssdk.services.firehose.model.Record.builder()
                .data(SdkBytes.fromByteArray(jsonRecordBytes))
                .build();
            records.add(record);
        }
        
        return records;
    }

    /**
     * Handles successful Firehose response, including partial failures.
     */
    private void handleSuccessfulResponse(PutRecordBatchResponse response, List<byte[]> originalRecords, String correlationId) {
        int totalRecords = response.requestResponses().size();
        int failedRecords = response.failedPutCount();
        int successfulRecords = totalRecords - failedRecords;
        
        logger.info("Batch {} written to Firehose: {} records, {} failed", 
            correlationId, totalRecords, failedRecords);

        meterRegistry.counter("firehose.batch.success").increment();

        // Record delivery results in monitoring service
        boolean isFullSuccess = failedRecords == 0;
        firehoseMonitoringService.recordDeliveryResult(isFullSuccess, successfulRecords, failedRecords);

        // Handle partial failures
        if (response.failedPutCount() > 0) {
            handlePartialFailures(response, originalRecords, correlationId);
        }
    }

    /**
     * Handles partial failures from successful batch response.
     */
    private void handlePartialFailures(PutRecordBatchResponse response, List<byte[]> originalRecords, String correlationId) {
        List<byte[]> failedRecords = new ArrayList<>();
        List<PutRecordBatchResponseEntry> responseEntries = response.requestResponses();

        for (int i = 0; i < responseEntries.size(); i++) {
            PutRecordBatchResponseEntry entry = responseEntries.get(i);
            if (entry.errorCode() != null) {
                logger.debug("Record {} in batch {} failed: {} - {}", 
                    i, correlationId, entry.errorCode(), entry.errorMessage());
                if (i < originalRecords.size()) {
                    failedRecords.add(originalRecords.get(i));
                }
            }
        }

        if (!failedRecords.isEmpty()) {
            logger.warn("Batch {} had {} partial failures, retrying failed records", 
                correlationId, failedRecords.size());

            meterRegistry.counter("firehose.partial.failures").increment(failedRecords.size());

            // Retry failed records with short delay
            retryAfterDelay(failedRecords, 0, correlationId + "-partial", 500);
        }
    }

    /**
     * Handles batch-level errors with smart categorization.
     */
    private Void handleBatchError(Throwable throwable, List<byte[]> records, int attempt, String correlationId) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

        // Category 1: Permanent errors - log and don't retry
        if (isPermanentError(cause)) {
            logPermanentError(cause, records, correlationId);
            return null;
        }

        // Category 2: Retriable errors - retry with backoff and jitter
        if (isRetriableError(cause)) {
            return handleRetriableError(cause, records, attempt, correlationId);
        }

        // Category 3: Unknown errors - log and don't retry (be conservative)
        logUnknownError(cause, records, correlationId);
        return null;
    }

    /**
     * Handles retriable errors with exponential backoff.
     */
    private Void handleRetriableError(Throwable cause, List<byte[]> records, int attempt, String correlationId) {
        String errorType = getRetriableErrorType(cause);

        logger.warn("Retriable {} error for batch {} (attempt {}): {}", 
            errorType, correlationId, attempt + 1, cause.getMessage());

        meterRegistry.counter("firehose.retriable.errors", "type", errorType).increment();

        if (attempt >= firehoseProperties.maxRetries()) {
            logger.error("Max retries ({}) exceeded for batch {} due to {}", 
                firehoseProperties.maxRetries(), correlationId, errorType);
            meterRegistry.counter("firehose.max_retries_exceeded", "type", errorType).increment();
            
            // Record failure in monitoring service when max retries exceeded
            firehoseMonitoringService.recordDeliveryResult(false, 0, records.size());
            return null;
        }

        // Retry with exponential backoff + jitter
        long delay = calculateRetryDelayWithJitter(attempt);

        logger.info("Retrying batch {} in {}ms due to {} (attempt {}/{})", 
            correlationId, delay, errorType, attempt + 1, firehoseProperties.maxRetries());

        meterRegistry.counter("firehose.retry.attempts", "type", errorType).increment();
        retryAfterDelay(records, attempt + 1, correlationId, delay);

        return null;
    }

    /**
     * Logs permanent errors without retry.
     */
    private void logPermanentError(Throwable cause, List<byte[]> records, String correlationId) {
        String errorType = getPermanentErrorType(cause);

        logger.error("Permanent {} error for batch {} - NOT RETRYING: {}", 
            errorType, correlationId, cause.getMessage());
        logger.error("Lost {} records due to permanent error", records.size());

        meterRegistry.counter("firehose.permanent.errors", "type", errorType).increment(records.size());
        
        // Record failure in monitoring service
        firehoseMonitoringService.recordDeliveryResult(false, 0, records.size());
    }

    /**
     * Logs unknown errors without retry.
     */
    private void logUnknownError(Throwable cause, List<byte[]> records, String correlationId) {
        logger.error("Unknown error type for batch {} - NOT RETRYING (being conservative): {} - {}", 
            correlationId, cause.getClass().getSimpleName(), cause.getMessage());
        logger.error("Lost {} records due to unknown error", records.size());

        meterRegistry.counter("firehose.unknown.errors", "class", cause.getClass().getSimpleName()).increment(records.size());
        
        // Record failure in monitoring service
        firehoseMonitoringService.recordDeliveryResult(false, 0, records.size());
    }

    /**
     * Checks if error is permanent (non-retriable).
     */
    private boolean isPermanentError(Throwable cause) {
        return cause instanceof ResourceNotFoundException ||     // Delivery stream doesn't exist
               cause instanceof InvalidArgumentException;       // Bad request format/data
    }

    /**
     * Checks if error is retriable.
     */
    private boolean isRetriableError(Throwable cause) {
        return cause instanceof ServiceUnavailableException ||  // Throttling/service issues
               cause instanceof LimitExceededException ||       // Rate limiting
               cause instanceof SdkClientException ||           // Network issues, timeouts
               (cause instanceof FirehoseException && 
                ((FirehoseException) cause).statusCode() >= 500); // Server errors (5xx)
    }

    /**
     * Gets readable error type for retriable errors.
     */
    private String getRetriableErrorType(Throwable cause) {
        if (cause instanceof ServiceUnavailableException) {
            return "throttling";
        } else if (cause instanceof LimitExceededException) {
            return "rate_limiting";
        } else if (cause instanceof SdkClientException) {
            return "network";
        } else if (cause instanceof FirehoseException) {
            return "server_error";
        } else {
            return "retriable_unknown";
        }
    }

    /**
     * Gets readable error type for permanent errors.
     */
    private String getPermanentErrorType(Throwable cause) {
        if (cause instanceof ResourceNotFoundException) {
            return "resource_not_found";
        } else if (cause instanceof InvalidArgumentException) {
            return "invalid_argument";
        } else {
            return "permanent_unknown";
        }
    }

    /**
     * Schedules retry with delay.
     */
    private void retryAfterDelay(List<byte[]> records, int attempt, String correlationId, long delayMs) {
        retryExecutor.schedule(() -> {
            writeBatchWithAttempt(records, attempt, correlationId)
                .exceptionally(ex -> {
                    logger.error("Scheduled retry failed for batch {}: {}", correlationId, ex.getMessage());
                    return null;
                });
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculates retry delay with exponential backoff + jitter.
     */
    private long calculateRetryDelayWithJitter(int attempt) {
        // Exponential backoff: baseBackoff * 2^attempt
        long exponentialDelay = firehoseProperties.retryBackoffMs() * (1L << attempt); // 1s, 2s, 4s, 8s...
        long cappedDelay = Math.min(exponentialDelay, 30000L);   // Cap at 30 seconds

        // Add jitter (±25% randomness to prevent thundering herd)
        double jitterFactor = 0.75 + (Math.random() * 0.5); // 0.75 to 1.25

        return (long) (cappedDelay * jitterFactor);
    }
} 