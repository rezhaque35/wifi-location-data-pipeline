// com/wifi/positioning/controller/IntegrationReportController.java
package com.wifi.positioning.controller;

import com.wifi.positioning.client.PositioningServiceClient;
import com.wifi.positioning.config.AsyncConfig;
import com.wifi.positioning.dto.*;
import com.wifi.positioning.exception.AsyncProcessingUnavailableException;
import com.wifi.positioning.exception.IntegrationProcessingException;
import com.wifi.positioning.mapper.SampleInterfaceMapper;
import com.wifi.positioning.service.AsyncIntegrationService;
import com.wifi.positioning.service.IntegrationProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for WiFi positioning integration reports.
 * Provides endpoints for comparing client positioning results with the
 * positioning service.
 */
@RestController
@RequestMapping("/v1/wifi/position")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Integration Reports", description = "Endpoints for WiFi positioning integration and comparison")
public class IntegrationReportController {

    private final IntegrationProcessingService integrationProcessingService;
    private final AsyncIntegrationService asyncIntegrationService;

    @Operation(summary = "Submit integration report", description = "Submit a WiFi positioning integration report for comparison with the positioning service. "
            +
            "Supports both synchronous and asynchronous processing modes.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Integration report processed successfully (sync mode)", content = @Content(schema = @Schema(implementation = IntegrationReportResponse.class))),
            @ApiResponse(responseCode = "202", description = "Integration report accepted for processing (async mode)", content = @Content(schema = @Schema(implementation = IntegrationReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request format or validation errors"),
            @ApiResponse(responseCode = "502", description = "Positioning service unavailable or returned error")
    })
    @PostMapping("/report")
    public ResponseEntity<IntegrationReportResponse> submitIntegrationReport(
            @Valid @RequestBody IntegrationReportRequest request) {

        // Generate correlation ID if not provided
        String correlationId = extractCorrelationId(request);
        Instant receivedAt = Instant.now();

        log.info("Processing integration report - correlationId: {}, requestId: {}",
                correlationId, extractRequestId(request));

        try {
            // Determine processing mode
            String processingMode = extractProcessingMode(request);

            if ("async".equals(processingMode)) {
                return handleAsyncProcessing(request, correlationId, receivedAt);
            } else {
                return handleSyncProcessing(request, correlationId, receivedAt);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Validation error for correlationId {}: {}", correlationId, e.getMessage());
            throw e; // Let global exception handler convert to 400

        } catch (AsyncProcessingUnavailableException e) {
            log.warn("Async processing unavailable for correlationId {}: {}", correlationId, e.getMessage());
            throw e; // Re-throw async processing exceptions

        } catch (Exception e) {
            log.error("Unexpected error processing integration report - correlationId: {}",
                    correlationId, e);
            throw new IntegrationProcessingException("Internal processing error", e);
        }
    }

    /**
     * Handles synchronous processing of integration reports.
     */
    private ResponseEntity<IntegrationReportResponse> handleSyncProcessing(
            IntegrationReportRequest request, String correlationId, Instant receivedAt) {

        try {
            String requestId = extractRequestId(request);
            
            // Create processing context
            IntegrationProcessingService.ProcessingContext context = IntegrationProcessingService.ProcessingContext.builder()
                .correlationId(correlationId)
                .requestId(requestId)
                .processingMode("sync")
                .receivedAt(receivedAt)
                .request(request)
                .build();
            
            // Process using the shared service
            IntegrationProcessingService.ProcessingResult result = integrationProcessingService.processIntegrationReport(context);
            
            if (!result.isSuccess()) {
                // Handle processing errors
                if ("VALIDATION_ERROR".equals(result.getErrorType())) {
                    throw new IllegalArgumentException(result.getErrorMessage());
                } else {
                    throw new IntegrationProcessingException("Processing failed: " + result.getErrorMessage());
                }
            }
            
            // Always return 200 OK for successful processing, even if positioning service failed
            // The positioning service status is included in the response body for client analysis
            IntegrationReportResponse response = buildSyncResponse(
                    correlationId, receivedAt, result.getPositioningRequest(), result.getPositioningResult(),
                    request.getSourceResponse(), result.getComparison());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Validation/mapping errors should return 400
            throw e;
        } catch (Exception e) {
            log.error("Error in sync processing for correlationId {}", correlationId, e);
            throw new IntegrationProcessingException("Processing failed", e);
        }
    }

    /**
     * Handles asynchronous processing of integration reports.
     * Immediately returns 202 Accepted while processing continues in background.
     */
    private ResponseEntity<IntegrationReportResponse> handleAsyncProcessing(
            IntegrationReportRequest request, String correlationId, Instant receivedAt) {

        String requestId = extractRequestId(request);

        log.info("Starting async processing - correlationId: {}, requestId: {}", correlationId, requestId);

        try {
            // Check if async processing is available
            if (!asyncIntegrationService.isAsyncProcessingAvailable()) {
                log.warn("Async processing is disabled - falling back to sync for correlationId: {}", correlationId);
                return handleSyncProcessing(request, correlationId, receivedAt);
            }

            // Submit task for async processing (this may throw AsyncQueueFullException)
            asyncIntegrationService.processIntegrationReportAsync(correlationId, requestId, receivedAt, request);

            // Build immediate response for async mode
            IntegrationReportResponse response = buildAsyncResponse(correlationId, receivedAt);

            log.info("Async processing initiated successfully - correlationId: {}, requestId: {}",
                    correlationId, requestId);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (AsyncConfig.AsyncQueueFullException e) {
            log.warn("Async queue is full - rejecting request, correlationId: {}, requestId: {}, error: {}",
                    correlationId, requestId, e.getMessage());

            // Return 503 Service Unavailable when queue is full
            throw new AsyncProcessingUnavailableException(
                    "Async processing queue is full. Please try again later or use sync mode.");

        } catch (IllegalStateException e) {
            log.warn("Async processing configuration error - falling back to sync, correlationId: {}, error: {}",
                    correlationId, e.getMessage());

            // Fall back to sync processing if async is misconfigured
            return handleSyncProcessing(request, correlationId, receivedAt);

        } catch (Exception e) {
            log.error("Unexpected error in async processing setup - correlationId: {}, requestId: {}",
                    correlationId, requestId, e);

            // For unexpected errors, fall back to sync processing
            log.warn("Falling back to sync processing due to async setup error - correlationId: {}", correlationId);
            return handleSyncProcessing(request, correlationId, receivedAt);
        }
    }

    /**
     * Builds the synchronous response with all comparison data.
     */
    private IntegrationReportResponse buildSyncResponse(
            String correlationId, Instant receivedAt, WifiPositioningRequest derivedRequest,
            ClientResult positioningResult, SourceResponse sourceResponse, ComparisonMetrics comparison) {

        IntegrationReportResponse response = new IntegrationReportResponse();
        response.setCorrelationId(correlationId);
        response.setReceivedAt(receivedAt);
        response.setProcessingMode("sync");
        response.setDerivedRequest(derivedRequest);
        response.setSourceResponse(sourceResponse);
        response.setComparison(comparison);

        // Build positioning service result
        PositioningServiceResult serviceResult = new PositioningServiceResult();
        serviceResult.setHttpStatus(positioningResult.getHttpStatus());
        serviceResult.setLatencyMs(positioningResult.getLatencyMs());
        serviceResult.setResponse(positioningResult.getResponseBody());
        serviceResult.setSuccess(positioningResult.getSuccess());
        serviceResult.setErrorMessage(positioningResult.getErrorMessage());

        response.setPositioningService(serviceResult);

        return response;
    }

    /**
     * Builds the immediate response for asynchronous processing.
     * Returns minimal data since processing continues in background.
     */
    private IntegrationReportResponse buildAsyncResponse(String correlationId, Instant receivedAt) {
        IntegrationReportResponse response = new IntegrationReportResponse();
        response.setCorrelationId(correlationId);
        response.setReceivedAt(receivedAt);
        response.setProcessingMode("async");

        // For async mode, we don't include processing results since they're not
        // available yet
        // Results will be available in logs only
        return response;
    }



    /**
     * Extracts correlation ID from request metadata or generates one.
     */
    private String extractCorrelationId(IntegrationReportRequest request) {
        if (request.getMetadata() != null && request.getMetadata().getCorrelationId() != null) {
            return request.getMetadata().getCorrelationId();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Extracts request ID from the source request.
     */
    private String extractRequestId(IntegrationReportRequest request) {
        if (request.getSourceRequest() != null &&
                request.getSourceRequest().getSvcBody() != null &&
                request.getSourceRequest().getSvcBody().getSvcReq() != null) {
            return request.getSourceRequest().getSvcBody().getSvcReq().getRequestId();
        }
        return "unknown";
    }

    /**
     * Extracts processing mode from options or returns default.
     */
    private String extractProcessingMode(IntegrationReportRequest request) {
        if (request.getOptions() != null && request.getOptions().getProcessingMode() != null) {
            return request.getOptions().getProcessingMode();
        }
        return "sync"; // Default to synchronous
    }
}
