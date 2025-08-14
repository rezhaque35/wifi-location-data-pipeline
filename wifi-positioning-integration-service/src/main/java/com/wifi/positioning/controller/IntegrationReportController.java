// com/wifi/positioning/controller/IntegrationReportController.java
package com.wifi.positioning.controller;

import com.wifi.positioning.client.PositioningServiceClient;
import com.wifi.positioning.dto.*;
import com.wifi.positioning.mapper.SampleInterfaceMapper;
import com.wifi.positioning.service.ComparisonService;
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
 * Provides endpoints for comparing client positioning results with the positioning service.
 */
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Integration Reports", description = "Endpoints for WiFi positioning integration and comparison")
public class IntegrationReportController {

    private final SampleInterfaceMapper mapper;
    private final PositioningServiceClient positioningClient;
    private final ComparisonService comparisonService;

    @Operation(
        summary = "Submit integration report",
        description = "Submit a WiFi positioning integration report for comparison with the positioning service. " +
                     "Supports both synchronous and asynchronous processing modes."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Integration report processed successfully (sync mode)",
            content = @Content(schema = @Schema(implementation = IntegrationReportResponse.class))
        ),
        @ApiResponse(
            responseCode = "202", 
            description = "Integration report accepted for processing (async mode)",
            content = @Content(schema = @Schema(implementation = IntegrationReportResponse.class))
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid request format or validation errors"
        ),
        @ApiResponse(
            responseCode = "502", 
            description = "Positioning service unavailable or returned error"
        )
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
            
        } catch (Exception e) {
            log.error("Unexpected error processing integration report - correlationId: {}", 
                correlationId, e);
            throw new RuntimeException("Internal processing error", e);
        }
    }

    /**
     * Handles synchronous processing of integration reports.
     */
    private ResponseEntity<IntegrationReportResponse> handleSyncProcessing(
            IntegrationReportRequest request, String correlationId, Instant receivedAt) {
        
        try {
            // Map request to positioning service format
            WifiPositioningRequest positioningRequest = mapper.mapToPositioningRequest(
                request.getSourceRequest(), request.getOptions());
            
            log.debug("Mapped to positioning request with {} WiFi scan results", 
                positioningRequest.getWifiScanResults().size());
            
            // Call positioning service
            ClientResult positioningResult = positioningClient.invoke(positioningRequest);
            
            log.debug("Positioning service call completed - httpStatus: {}, latency: {}ms", 
                positioningResult.getHttpStatus(), positioningResult.getLatencyMs());
            
            // Compute comparison metrics including AP enrichment
            ComparisonMetrics comparison = comparisonService.compareResults(
                request.getSourceResponse(), 
                positioningResult.getResponseBody(),
                request.getSourceRequest().getSvcBody().getSvcReq().getWifiInfo());
            
            // Build response
            IntegrationReportResponse response = buildSyncResponse(
                correlationId, receivedAt, positioningRequest, positioningResult, 
                request.getSourceResponse(), comparison);
            
            // Log structured event for observability
            logIntegrationEvent(correlationId, request, positioningResult, comparison);
            
            // Return 502 if positioning service failed, otherwise 200
            if (!positioningResult.getSuccess()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            // Validation/mapping errors should return 400
            throw e;
        } catch (Exception e) {
            log.error("Error in sync processing for correlationId {}", correlationId, e);
            throw new RuntimeException("Processing failed", e);
        }
    }

    /**
     * Handles asynchronous processing of integration reports.
     * TODO: Implement async processing with queue and worker threads (Milestone 5)
     */
    private ResponseEntity<IntegrationReportResponse> handleAsyncProcessing(
            IntegrationReportRequest request, String correlationId, Instant receivedAt) {
        
        log.warn("Async processing not yet implemented - falling back to sync for correlationId: {}", 
            correlationId);
        
        // For now, fall back to sync processing
        // TODO: Implement proper async processing in future iteration
        return handleSyncProcessing(request, correlationId, receivedAt);
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
     * Logs structured integration event for observability.
     */
    private void logIntegrationEvent(String correlationId, IntegrationReportRequest request, 
                                   ClientResult positioningResult, ComparisonMetrics comparison) {
        
        String requestId = extractRequestId(request);
        
        log.info("Integration event - correlationId: {}, requestId: {}, " +
                "positioningStatus: {}, latencyMs: {}, comparable: {}, distanceM: {}", 
            correlationId, requestId,
            positioningResult.getSuccess() ? "SUCCESS" : "FAILED",
            positioningResult.getLatencyMs(),
            comparison.getPositionsComparable(),
            comparison.getHaversineDistanceMeters());
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
