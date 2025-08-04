import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClient;
import software.amazon.awssdk.services.firehose.model.*;
import software.amazon.awssdk.core.exception.SdkClientException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class PracticalFirehoseHandler {
    
    private final FirehoseAsyncClient firehoseAsyncClient;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService retryExecutor;
    
    @Value("${firehose.delivery-stream-name}")
    private String deliveryStreamName;
    
    @Value("${firehose.max-retries:3}")
    private int maxRetries;
    
    @Value("${firehose.base-backoff-ms:1000}")
    private long baseBackoffMs;
    
    public PracticalFirehoseHandler(FirehoseAsyncClient firehoseAsyncClient, 
                                  MeterRegistry meterRegistry) {
        this.firehoseAsyncClient = firehoseAsyncClient;
        this.meterRegistry = meterRegistry;
        this.retryExecutor = Executors.newScheduledThreadPool(2, 
            r -> new Thread(r, "firehose-retry"));
    }
    
    /**
     * Write to Firehose with practical error handling
     */
    public CompletableFuture<Void> writeToFirehose(List<Record> records) {
        return writeToFirehose(records, 0, UUID.randomUUID().toString());
    }
    
    private CompletableFuture<Void> writeToFirehose(List<Record> records, int attempt, String correlationId) {
        PutRecordBatchRequest request = PutRecordBatchRequest.builder()
            .deliveryStreamName(deliveryStreamName)
            .records(records)
            .build();
        
        return firehoseAsyncClient.putRecordBatch(request)
            .thenAccept(response -> {
                // Success case
                log.info("Batch {} written successfully, {} records, {} failed", 
                    correlationId, response.requestResponses().size(), response.failedPutCount());
                
                meterRegistry.counter("firehose.batch.success").increment();
                
                // Handle partial failures - retry failed records
                if (response.failedPutCount() > 0) {
                    handlePartialFailures(response, records, correlationId);
                }
            })
            .exceptionally(throwable -> {
                return handleBatchError(throwable, records, attempt, correlationId);
            });
    }
    
    /**
     * Handle batch-level errors with smart categorization
     */
    private Void handleBatchError(Throwable throwable, List<Record> records, int attempt, String correlationId) {
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
     * Handle partial failures from successful batch response
     */
    private void handlePartialFailures(PutRecordBatchResponse response, List<Record> originalRecords, String correlationId) {
        List<Record> failedRecords = new ArrayList<>();
        List<PutRecordBatchResponseEntry> responseEntries = response.requestResponses();
        
        for (int i = 0; i < responseEntries.size(); i++) {
            PutRecordBatchResponseEntry entry = responseEntries.get(i);
            if (entry.errorCode() != null) {
                log.debug("Record {} in batch {} failed: {} - {}", 
                    i, correlationId, entry.errorCode(), entry.errorMessage());
                failedRecords.add(originalRecords.get(i));
            }
        }
        
        if (!failedRecords.isEmpty()) {
            log.warn("Batch {} had {} partial failures, retrying failed records", 
                correlationId, failedRecords.size());
            
            meterRegistry.counter("firehose.partial.failures").increment(failedRecords.size());
            
            // Retry failed records with short delay (they're usually different issues)
            retryAfterDelay(failedRecords, 0, correlationId + "-partial", 500);
        }
    }
    
    /**
     * Handle retriable errors (throttling, rate limiting, network issues)
     */
    private Void handleRetriableError(Throwable cause, List<Record> records, int attempt, String correlationId) {
        String errorType = getRetriableErrorType(cause);
        
        log.warn("Retriable {} error for batch {} (attempt {}): {}", 
            errorType, correlationId, attempt + 1, cause.getMessage());
        
        meterRegistry.counter("firehose.retriable.errors", "type", errorType).increment();
        
        if (attempt >= maxRetries) {
            log.error("Max retries ({}) exceeded for batch {} due to {}", 
                maxRetries, correlationId, errorType);
            meterRegistry.counter("firehose.max_retries_exceeded", "type", errorType).increment();
            return null;
        }
        
        // Retry with exponential backoff + jitter for ALL retriable errors
        long delay = calculateRetryDelayWithJitter(attempt);
        
        log.info("Retrying batch {} in {}ms due to {} (attempt {}/{})", 
            correlationId, delay, errorType, attempt + 1, maxRetries);
        
        meterRegistry.counter("firehose.retry.attempts", "type", errorType).increment();
        retryAfterDelay(records, attempt + 1, correlationId, delay);
        
        return null;
    }
    
    /**
     * Log permanent errors without retry
     */
    private void logPermanentError(Throwable cause, List<Record> records, String correlationId) {
        String errorType = getPermanentErrorType(cause);
        
        log.error("Permanent {} error for batch {} - NOT RETRYING: {}", 
            errorType, correlationId, cause.getMessage());
        log.error("Lost {} records due to permanent error", records.size());
        
        meterRegistry.counter("firehose.permanent.errors", "type", errorType).increment(records.size());
        
        // TODO: Could send to dead letter queue here if needed
        // deadLetterQueueService.send(records, correlationId, cause.getMessage());
    }
    
    /**
     * Log unknown errors without retry (conservative approach)
     */
    private void logUnknownError(Throwable cause, List<Record> records, String correlationId) {
        log.error("Unknown error type for batch {} - NOT RETRYING (being conservative): {} - {}", 
            correlationId, cause.getClass().getSimpleName(), cause.getMessage());
        log.error("Lost {} records due to unknown error", records.size());
        
        meterRegistry.counter("firehose.unknown.errors", "class", cause.getClass().getSimpleName()).increment(records.size());
        
        // TODO: Alert operations team for unknown errors
        // alertService.sendAlert("Unknown Firehose error", cause);
    }
    
    /**
     * Check if error is permanent (non-retriable)
     */
    private boolean isPermanentError(Throwable cause) {
        return cause instanceof ResourceNotFoundException ||     // Delivery stream doesn't exist
               cause instanceof InvalidArgumentException;       // Bad request format/data
    }
    
    /**
     * Check if error is retriable
     */
    private boolean isRetriableError(Throwable cause) {
        return cause instanceof ServiceUnavailableException ||  // Throttling/service issues
               cause instanceof LimitExceededException ||       // Rate limiting
               cause instanceof SdkClientException ||           // Network issues, timeouts
               (cause instanceof FirehoseException && 
                ((FirehoseException) cause).statusCode() >= 500); // Server errors (5xx)
    }
    
    /**
     * Get readable error type for retriable errors
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
     * Get readable error type for permanent errors
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
     * Schedule retry with delay
     */
    private void retryAfterDelay(List<Record> records, int attempt, String correlationId, long delayMs) {
        retryExecutor.schedule(() -> {
            writeToFirehose(records, attempt, correlationId)
                .exceptionally(ex -> {
                    log.error("Scheduled retry failed for batch {}: {}", correlationId, ex.getMessage());
                    return null;
                });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Calculate retry delay with exponential backoff + jitter
     */
    private long calculateRetryDelayWithJitter(int attempt) {
        // Exponential backoff: baseBackoff * 2^attempt
        long exponentialDelay = baseBackoffMs * (1L << attempt); // 1s, 2s, 4s, 8s...
        long cappedDelay = Math.min(exponentialDelay, 30000L);   // Cap at 30 seconds
        
        // Add jitter (±25% randomness to prevent thundering herd)
        double jitterFactor = 0.75 + (Math.random() * 0.5); // 0.75 to 1.25
        
        return (long) (cappedDelay * jitterFactor);
    }
    
    /**
     * Graceful shutdown
     */
    public void shutdown() {
        log.info("Shutting down Firehose retry executor...");
        retryExecutor.shutdown();
        try {
            if (!retryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Retry executor didn't terminate gracefully, forcing shutdown");
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Health check
     */
    public boolean isHealthy() {
        return !retryExecutor.isShutdown();
    }
}

/**
 * Configuration class for the handler
 */
@ConfigurationProperties(prefix = "firehose")
@Data
public class FirehoseRetryConfig {
    private String deliveryStreamName;
    private int maxRetries = 3;
    private long baseBackoffMs = 1000;
    private boolean logPartialFailures = true;
    private boolean alertOnUnknownErrors = true;
    private boolean sendPermanentFailuresToDlq = false;
}
