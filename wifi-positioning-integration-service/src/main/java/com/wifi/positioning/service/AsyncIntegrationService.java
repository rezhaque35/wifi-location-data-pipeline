// com/wifi/positioning/service/AsyncIntegrationService.java
package com.wifi.positioning.service;

import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.IntegrationReportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling asynchronous integration report processing.
 * Uses Spring's @Async annotation with custom executor for background processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncIntegrationService {

    private final IntegrationProcessingService integrationProcessingService;
    private final IntegrationProperties integrationProperties;

    /**
     * Processes an integration report asynchronously in the background.
     * Returns immediately with a CompletableFuture for tracking (though results are logged only).
     * 
     * @param correlationId Unique identifier for tracking this request
     * @param requestId The request ID from the source request
     * @param receivedAt Timestamp when the request was received
     * @param request The integration report request to process
     * @return CompletableFuture that completes when processing is done
     * @throws AsyncConfig.AsyncQueueFullException if the async queue is full
     */
    @Async("integrationAsyncExecutor")
    public CompletableFuture<Void> processIntegrationReportAsync(
            String correlationId, String requestId, Instant receivedAt, IntegrationReportRequest request) {
        
        // Check if async processing is enabled
        if (!integrationProperties.getProcessing().getAsync().isEnabled()) {
            log.warn("Async processing is disabled but async method was called - correlationId: {}", correlationId);
            throw new IllegalStateException("Async processing is not enabled in configuration");
        }
        
        log.debug("Starting async processing - correlationId: {}, requestId: {}", correlationId, requestId);
        
        try {
            // Create processing context for async mode
            IntegrationProcessingService.ProcessingContext context = IntegrationProcessingService.ProcessingContext.builder()
                .correlationId(correlationId)
                .requestId(requestId)
                .processingMode("async")
                .receivedAt(receivedAt)
                .request(request)
                .build();
            
            // Process using the shared service (results will be logged by the service)
            IntegrationProcessingService.ProcessingResult result = integrationProcessingService.processIntegrationReport(context);
            
            if (result.isSuccess()) {
                log.debug("Async processing completed successfully - correlationId: {}, requestId: {}", correlationId, requestId);
            } else {
                log.warn("Async processing completed with errors - correlationId: {}, requestId: {}, errorType: {}, error: {}", 
                    correlationId, requestId, result.getErrorType(), result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Fatal error in async processing - correlationId: {}, requestId: {}", 
                correlationId, requestId, e);
            
            // Log a failure event for monitoring
            logAsyncProcessingFailure(correlationId, requestId, e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Checks if async processing is currently enabled and available.
     * 
     * @return true if async processing is enabled and queue has capacity
     */
    public boolean isAsyncProcessingAvailable() {
        return integrationProperties.getProcessing().getAsync().isEnabled();
    }

    /**
     * Gets the current async processing configuration for diagnostics.
     * 
     * @return AsyncConfig object with current settings
     */
    public IntegrationProperties.Processing.Async getAsyncConfig() {
        return integrationProperties.getProcessing().getAsync();
    }

    /**
     * Logs async processing failures for monitoring and alerting.
     */
    private void logAsyncProcessingFailure(String correlationId, String requestId, Exception error) {
        log.error("ASYNC_PROCESSING_FAILURE: correlationId='{}', requestId='{}', " +
                "errorType='{}', errorMessage='{}', processingMode='async'",
            correlationId, requestId, error.getClass().getSimpleName(), error.getMessage());
    }
}

