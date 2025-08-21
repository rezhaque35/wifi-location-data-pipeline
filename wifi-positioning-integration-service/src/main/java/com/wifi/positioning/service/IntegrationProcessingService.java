// com/wifi/positioning/service/IntegrationProcessingService.java
package com.wifi.positioning.service;

import com.wifi.positioning.client.PositioningServiceClient;
import com.wifi.positioning.dto.*;
import com.wifi.positioning.mapper.VLSSInterfaceMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
     * Logs successful processing completion with metrics.
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
     * Logs a simple summary of the integration event.
     * Detailed metrics are logged separately in logSpecificRequirements().
     */
    private void logIntegrationEvent(ProcessingContext context, ComparisonMetrics comparison) {
        
        String logPrefix = ASYNC_MODE.equals(context.getProcessingMode()) ? "ASYNC_INTEGRATION_COMPARISON_EVENT" : "INTEGRATION_COMPARISON_EVENT";
        
        // Simple summary log - detailed metrics are in specific requirement logs below
        log.info("{}: correlationId='{}', requestId='{}', processingMode='{}', " +
                "scenario='{}' ",
            logPrefix,
            context.getCorrelationId(), context.getRequestId(), context.getProcessingMode(),
            comparison.getScenario());
        
        // Log detailed requirements for Splunk dashboard
        logSpecificRequirements(context, comparison);

    }
    

    
    /**
     * Logs specific requirements for Splunk dashboard analysis.
     */
    private void logSpecificRequirements(ProcessingContext context, ComparisonMetrics comparison) {
        
        String logPrefix = ASYNC_MODE.equals(context.getProcessingMode()) ? "ASYNC_" : "";
        
        // 1. Input data quality - print total AP count and selection context
        log.info("{}INPUT_DATA_QUALITY: correlationId='{}', requestId='{}', " +
                "totalApCount={}, selectionContextInfo={}",
            logPrefix, context.getCorrelationId(), context.getRequestId(),
            comparison.getRequestApCount(), comparison.getSelectionContextInfo());
        
        // 2. AP Data Quality - comprehensive metrics for all scenarios
        log.info("{}AP_DATA_QUALITY: correlationId='{}', requestId='{}', " +
                "requestApCount={}, friscoSuccess={}, " +
                "calculationAccessPoints={}, accessPointSummary={}, statusRatio={}, " +
                "geometricQualityFactor={}, signalQualityFactor={}, signalDistributionFactor={}",
            logPrefix, context.getCorrelationId(), context.getRequestId(),
            comparison.getRequestApCount(), comparison.getFriscoSuccess(),
            comparison.getCalculationAccessPoints(), comparison.getCalculationAccessPointSummary(), 
            comparison.getStatusRatio(), comparison.getGeometricQualityFactor(),
            comparison.getSignalQualityFactor(), comparison.getSignalDistributionFactor());
        
        // 3. Algorithm Usage - print used algorithms
        if (comparison.getFriscoMethodsUsed() != null && !comparison.getFriscoMethodsUsed().isEmpty()) {
            log.info("{}ALGORITHM_USAGE: correlationId='{}', requestId='{}', usedAlgorithms={}",
                logPrefix, context.getCorrelationId(), context.getRequestId(),
                comparison.getFriscoMethodsUsed());
        }
        
        // 4. Frisco Service Performance - unified success/failure metrics
        log.info("{}FRISCO_PERFORMANCE: correlationId='{}', requestId='{}', " +
                "friscoSuccess={}, friscoAccuracy={}, friscoConfidence={}, friscoErrorDetails={}, " +
                "friscoResponseTimeMs={}, friscoCalculationTimeMs={}",
            logPrefix, context.getCorrelationId(), context.getRequestId(),
            comparison.getFriscoSuccess(), comparison.getFriscoAccuracy(), comparison.getFriscoConfidence(),
            comparison.getFriscoErrorDetails(), comparison.getFriscoResponseTimeMs(), comparison.getFriscoCalculationTimeMs());
        
        // 5. VLSS VS Frisco Service Performance Analysis
        if (comparison.getLocationType() != null) {
            log.info("{}VLSS_FRISCO_COMPARISON: correlationId='{}', requestId='{}', " +
                    "locationType='{}', distance={}, expectedUncertainty={}, agreementAnalysis='{}', " +
                    "vlssAccuracy={}, friscoAccuracy={}, confidenceRatio={}",
                logPrefix, context.getCorrelationId(), context.getRequestId(),
                comparison.getLocationType(), comparison.getHaversineDistanceMeters(), 
                comparison.getExpectedUncertaintyMeters(), comparison.getAgreementAnalysis(),
                comparison.getVlssAccuracy(), comparison.getFriscoAccuracy(), comparison.getConfidenceRatio());
        }
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
