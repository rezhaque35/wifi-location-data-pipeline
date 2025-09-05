// com/wifi/positioning/service/IntegrationProcessingService.java
package com.wifi.positioning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.client.PositioningServiceClient;
import com.wifi.positioning.dto.*;
import com.wifi.positioning.mapper.VLSSInterfaceMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service that encapsulates the core integration processing logic shared between
 * synchronous and asynchronous processing modes.
 * Follows DRY principle by eliminating code duplication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationProcessingService {

    private static final String ASYNC_MODE = "async";

    private final VLSSInterfaceMapper mapper;
    private final PositioningServiceClient positioningClient;
    private final ComparisonService comparisonService;
    private final ObjectMapper objectMapper;

    /**
     * Processes an integration report request and returns comprehensive results.
     * This method contains the core business logic used by both sync and async processing.
     * 
     * @param context Processing context containing request data and identifiers
     * @return ProcessingResult containing all processing outcomes and metrics
     */
    public ProcessingResult processIntegrationReport(ProcessingContext context) {
        logProcessingStart(context);
        
        try {
            WifiPositioningRequest wifiPositioningRequest = performRequestTransformation(context);
            ClientResult positioning = positioningClient.invoke(wifiPositioningRequest);
            ComparisonMetrics comparison = performResultsComparison(context, positioning);
            

            logProcessingSuccess(context, positioning, comparison);
            
            return buildSuccessResult(wifiPositioningRequest, positioning, comparison);
            
        } catch (IllegalArgumentException e) {
            return handleValidationError(context, e);
        } catch (Exception e) {
            return handleProcessingError(context, e);
        }
    }

    /**
     * Logs the start of integration processing.
     */
    private void logProcessingStart(ProcessingContext context) {
        log.info("Starting integration processing - correlationId: {}, requestId: {}, mode: {}", 
            context.getCorrelationId(), context.getRequestId(), context.getProcessingMode());
    }

    /**
     * Performs request transformation from source format to positioning service format.
     */
    private WifiPositioningRequest performRequestTransformation(ProcessingContext context) {
        
        return mapper.mapToPositioningRequest(context.getRequest().getSourceRequest(), context.getRequest().getOptions());
        
    }



    /**
     * Performs comprehensive comparison between source and positioning service results.
     */
    private ComparisonMetrics performResultsComparison(ProcessingContext context,
                                                      ClientResult positioning) {
        
        ComparisonMetrics comparison = comparisonService.compareResults(
            context.getRequest().getSourceResponse(), 
            positioning.getResponseBody(),
            context.getRequest().getSourceRequest().getSvcBody().getSvcReq().getWifiInfo());
        
        // Set the measured response time from the positioning service call
        comparison.setFriscoResponseTimeMs(positioning.getLatencyMs());
        
        return comparison;
    }

    /**
     * Logs successful processing completion with comprehensive metrics in JSON format.
     */
    private void logProcessingSuccess(ProcessingContext context,
                                      ClientResult positioning, ComparisonMetrics comparison) {
        
        logIntegrationEvent(context, comparison);
        
        log.info("Integration processing completed successfully - correlationId: {}, requestId: {}, mode: {}, " +
                " positioning Latency Time: {}ms",
            context.getCorrelationId(), context.getRequestId(), context.getProcessingMode(),
             positioning.getLatencyMs());
    }

    /**
     * Builds successful processing result.
     */
    private ProcessingResult buildSuccessResult(WifiPositioningRequest request,
            ClientResult positioning, ComparisonMetrics comparison) {
        
        return ProcessingResult.builder()
            .success(true)
            .positioningRequest(request)
            .positioningResult(positioning)
            .comparison(comparison)
            .build();
    }

    /**
     * Handles validation errors during processing.
     */
    private ProcessingResult handleValidationError(ProcessingContext context, IllegalArgumentException e) {
        log.warn("Validation error - correlationId: {}, requestId: {}, mode: {}, error: {}", 
            context.getCorrelationId(), context.getRequestId(), context.getProcessingMode(), e.getMessage());
        logProcessingError(context, "VALIDATION_ERROR", e.getMessage());
        
        return ProcessingResult.builder()
            .success(false)
            .errorType("VALIDATION_ERROR")
            .errorMessage(e.getMessage())
            .build();
    }

    /**
     * Handles general processing errors.
     */
    private ProcessingResult handleProcessingError(ProcessingContext context, Exception e) {
        log.error("Processing error - correlationId: {}, requestId: {}, mode: {}", 
            context.getCorrelationId(), context.getRequestId(), context.getProcessingMode(), e);
        logProcessingError(context, "PROCESSING_ERROR", e.getMessage());
        
        return ProcessingResult.builder()
            .success(false)
            .errorType("PROCESSING_ERROR")
            .errorMessage(e.getMessage())
            .build();
    }

    /**
     * Logs comprehensive integration event metrics as a single JSON structure.
     * Consolidates all integration data for easier parsing in log aggregation systems.
     */
    private void logIntegrationEvent(ProcessingContext context, ComparisonMetrics comparison) {
        try {
            String eventType = ASYNC_MODE.equals(context.getProcessingMode()) ? 
                "ASYNC_INTEGRATION_COMPARISON_EVENT" : "INTEGRATION_COMPARISON_EVENT";
            
            Map<String, Object> logData = createIntegrationLogData(context, comparison, eventType);
            String jsonLog = objectMapper.writeValueAsString(logData);
            
            log.info("{}: {}", eventType, jsonLog);
            
        } catch (Exception e) {
            // Fallback to basic logging if JSON serialization fails
            log.error("Failed to serialize integration log data - correlationId: {}, requestId: {}, error: {}", 
                context.getCorrelationId(), context.getRequestId(), e.getMessage());
            logBasicIntegrationEvent(context, comparison);
        }
    }

    /**
     * Creates comprehensive log data structure containing all integration metrics.
     */
    private Map<String, Object> createIntegrationLogData(ProcessingContext context, 
                                                        ComparisonMetrics comparison, 
                                                        String eventType) {
        Map<String, Object> logData = new LinkedHashMap<>();
        
        // Core context information
        logData.put("eventType", eventType);
        logData.put("correlationId", context.getCorrelationId());
        logData.put("requestId", context.getRequestId());
        logData.put("processingMode", context.getProcessingMode());
        logData.put("scenario", comparison.getScenario());
        logData.put("timestamp", Instant.now().toString());
        
        // Input data quality metrics
        Map<String, Object> inputDataQuality = new LinkedHashMap<>();
        inputDataQuality.put("totalApCount", comparison.getRequestApCount());
        inputDataQuality.put("selectionContextInfo", comparison.getSelectionContextInfo());
        logData.put("inputDataQuality", inputDataQuality);
        
        // AP data quality metrics
        Map<String, Object> apDataQuality = new LinkedHashMap<>();
        apDataQuality.put("requestApCount", comparison.getRequestApCount());
        apDataQuality.put("friscoSuccess", comparison.getFriscoSuccess());
        apDataQuality.put("calculationAccessPoints", comparison.getCalculationAccessPoints());
        apDataQuality.put("accessPointSummary", comparison.getCalculationAccessPointSummary());
        apDataQuality.put("statusRatio", comparison.getStatusRatio());
        apDataQuality.put("geometricQualityFactor", comparison.getGeometricQualityFactor());
        apDataQuality.put("signalQualityFactor", comparison.getSignalQualityFactor());
        apDataQuality.put("signalDistributionFactor", comparison.getSignalDistributionFactor());
        logData.put("apDataQuality", apDataQuality);
        
        // Algorithm usage metrics
        if (comparison.getFriscoMethodsUsed() != null && !comparison.getFriscoMethodsUsed().isEmpty()) {
            Map<String, Object> algorithmUsage = new LinkedHashMap<>();
            algorithmUsage.put("usedAlgorithms", comparison.getFriscoMethodsUsed());
            logData.put("algorithmUsage", algorithmUsage);
        }
        
        // Frisco service performance metrics
        Map<String, Object> friscoPerformance = new LinkedHashMap<>();
        friscoPerformance.put("friscoSuccess", comparison.getFriscoSuccess());
        friscoPerformance.put("friscoAccuracy", comparison.getFriscoAccuracy());
        friscoPerformance.put("friscoConfidence", comparison.getFriscoConfidence());
        friscoPerformance.put("friscoErrorDetails", comparison.getFriscoErrorDetails());
        friscoPerformance.put("friscoResponseTimeMs", comparison.getFriscoResponseTimeMs());
        friscoPerformance.put("friscoCalculationTimeMs", comparison.getFriscoCalculationTimeMs());
        logData.put("friscoPerformance", friscoPerformance);
        
        // VLSS vs Frisco comparison metrics (only if location data available)
        if (comparison.getLocationType() != null) {
            Map<String, Object> vlssFriscoComparison = new LinkedHashMap<>();
            vlssFriscoComparison.put("locationType", comparison.getLocationType());
            vlssFriscoComparison.put("distance", comparison.getHaversineDistanceMeters());
            vlssFriscoComparison.put("expectedUncertainty", comparison.getExpectedUncertaintyMeters());
            vlssFriscoComparison.put("agreementAnalysis", comparison.getAgreementAnalysis());
            vlssFriscoComparison.put("vlssAccuracy", comparison.getVlssAccuracy());
            vlssFriscoComparison.put("friscoAccuracy", comparison.getFriscoAccuracy());
            vlssFriscoComparison.put("confidenceRatio", comparison.getConfidenceRatio());
            logData.put("vlssFriscoComparison", vlssFriscoComparison);
        }
        
        return logData;
    }

    /**
     * Fallback logging method if JSON serialization fails.
     */
    private void logBasicIntegrationEvent(ProcessingContext context, ComparisonMetrics comparison) {
        String eventType = ASYNC_MODE.equals(context.getProcessingMode()) ? 
            "ASYNC_INTEGRATION_COMPARISON_EVENT" : "INTEGRATION_COMPARISON_EVENT";
        
        log.info("{}: correlationId='{}', requestId='{}', processingMode='{}', scenario='{}', " +
                "friscoSuccess={}, vlssSuccess={}", 
            eventType, context.getCorrelationId(), context.getRequestId(), 
            context.getProcessingMode(), comparison.getScenario(), 
            comparison.getFriscoSuccess(), comparison.getVlssSuccess());
    }


    /**
     * Logs processing errors for monitoring and debugging.
     */
    private void logProcessingError(ProcessingContext context, String errorType, String errorMessage) {
        log.error("INTEGRATION_PROCESSING_ERROR: correlationId='{}', requestId='{}', " +
                "processingMode='{}', errorType='{}', errorMessage='{}'",
            context.getCorrelationId(), context.getRequestId(), context.getProcessingMode(), 
            errorType, errorMessage);
    }

    /**
     * Processing context containing all necessary data for integration processing.
     */
    @Data
    @Builder
    public static class ProcessingContext {
        private final String correlationId;
        private final String requestId;
        private final String processingMode; // "sync" or "async"
        private final Instant receivedAt;
        private final IntegrationReportRequest request;
    }

    /**
     * Result of integration processing containing all outcomes and metrics.
     */
    @Data
    @Builder
    public static class ProcessingResult {
        private final boolean success;
        private final WifiPositioningRequest positioningRequest;
        private final ClientResult positioningResult;
        private final ComparisonMetrics comparison;
        private final String errorType;
        private final String errorMessage;
    }
}
