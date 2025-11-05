package com.wifi.positioning.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.WifiPositioningCalculator;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.dto.CalculationInfo;
import com.wifi.positioning.dto.CellInfo;
import com.wifi.positioning.dto.CellTower;
import com.wifi.positioning.dto.WifiAccessPoints;
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningResponse.WifiPosition;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.calculation.AlgorithmSelectionInfo;
import com.wifi.positioning.dto.calculation.CellTowerInfo;
import com.wifi.positioning.dto.calculation.SelectionContextInfo;
import com.wifi.positioning.repository.CellTowerRepository;
import com.wifi.positioning.repository.WifiAccessPointRepository;

import lombok.extern.slf4j.Slf4j;

import static net.logstash.logback.argument.StructuredArguments.entries;

/**
 * Service for WiFi-based positioning calculations.
 *
 * <p>This service orchestrates the complete positioning workflow:
 * <ol>
 *   <li>Validates incoming WiFi scan results from client devices</li>
 *   <li>Retrieves known access points from repository using optimized batch operations</li>
 *   <li>Filters access points to include only those with valid status (active/warning)</li>
 *   <li>Delegates position calculation to specialized positioning algorithms</li>
 *   <li>Processes results and builds comprehensive response with calculation details</li>
 * </ol>
 *
 * <p>The service provides detailed calculation information for debugging and monitoring,
 * including access point usage statistics, algorithm selection reasoning, and performance metrics.
 */
@Service
@Slf4j
public class PositioningService {

    private static final Logger logger = LoggerFactory.getLogger(PositioningService.class);

    /**
     * Error message when no WiFi scan results are provided in the request.
     */
    private static final String ERROR_NO_SCAN_RESULTS = "No WiFi scan results provided";

    /**
     * Error message for position calculation failures.
     */
    private static final String ERROR_POSITION_CALCULATION_FAILED = "Position calculation failed: no position could be determined";

    /**
     * Default vertical accuracy value when not provided by positioning algorithms.
     */
    private static final double DEFAULT_VERTICAL_ACCURACY = 0.0;

    /**
     * Error message for invalid coordinates in position result.
     */
    public static final String INVALID_COORDINATES_IN_POSITION_RESULT = "Invalid coordinates in position result";

    /**
     * Error message for unexpected calculation errors.
     */
    public static final String ERROR_DURING_CALCULATING_POSITION = "unexpected error during calculating position.";
    
    /**
     * Configuration: Top N strongest signals to use for positioning.
     */
    private static final int TOP_STRONGEST_SIGNALS_LIMIT = 20;
    
    /**
     * Configuration: Maximum reasonable WiFi distance from centroid for global outlier filtering.
     */
    private static final double MAX_WIFI_DISTANCE_METERS = 500.0;

    /**
     * Constant for no valid access point scenario in selection context.
     */
    private static final String NO_VALID_AP = "NO_VALID_AP";

    /**
     * Log key for request data.
     */
    private static final String LOG_KEY_REQUEST = "request";

    /**
     * Log key for response data.
     */
    private static final String LOG_KEY_RESPONSE = "response";

    /**
     * Log key for calculation info data.
     */
    private static final String LOG_KEY_CALCULATION_INFO = "calculationInfo";

    /**
     * Error log message for calculation failures.
     */
    private static final String LOG_ERROR_CALCULATING_POSITION = "Error calculating position";

    /**
     * Error log message format for error responses.
     */
    private static final String LOG_ERROR_RESPONSE_FORMAT = "Failure : Returning error response for requestId {}: {}";

    private final WifiPositioningCalculator calculator;
    private final WifiAccessPointRepository accessPointRepository;
    private final CellTowerRepository cellTowerRepository;

    // ===== INNER RECORDS FOR DATA TRANSFER =====

    /**
     * Result of request validation containing validation status and error message.
     */
    private record ValidationResult(boolean isValid, String errorMessage) {
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }

    /**
     * Result of position calculation including timing and success status.
     * Failed calculations may contain partial positioning result for debugging.
     */
    private record CalculationResult(
            WifiPositioningCalculator.PositioningResult positioningResult,
            long calculationTimeMs,
            boolean isSuccessful) {

        static CalculationResult successful(
                WifiPositioningCalculator.PositioningResult positioningResult,
                long calculationTimeMs) {
            return new CalculationResult(positioningResult, calculationTimeMs, true);
        }

        static CalculationResult failed(
                WifiPositioningCalculator.PositioningResult positioningResult,
                long calculationTimeMs) {
            return new CalculationResult(positioningResult, calculationTimeMs, false);
        }
    }

    @Autowired
    public PositioningService(
            WifiPositioningCalculator calculator,
            WifiAccessPointRepository accessPointRepository,
            CellTowerRepository cellTowerRepository) {
        this.calculator = calculator;
        this.accessPointRepository = accessPointRepository;
        this.cellTowerRepository = cellTowerRepository;
    }

    /**
     * Calculate position based on WiFi scan results asynchronously.
     * 
     * <p>This method performs all operations (data retrieval, filtering, calculation) 
     * asynchronously without blocking. The returned CompletableFuture completes when 
     * the positioning calculation is done.
     *
     * @param request The position request containing WiFi scan results
     * @return CompletableFuture that completes with positioning response
     */
    public CompletableFuture<WifiPositioningResponse> calculatePosition(WifiPositioningRequest request) {
        logIncomingRequest(request);

        try {
            ValidationResult validation = validateRequest(request);
            if (!validation.isValid()) {
                // No calculation data available at validation stage
                return CompletableFuture.completedFuture(
                    handleError(validation.errorMessage(), request, null)
                );
            }

            // Async pipeline: data preparation -> calculation -> response building
            return prepareWiFiAPData(request.wifiScanResults(), request.cellInfo())
                .thenApply(wifiAccessPoints -> {
                    if (!wifiAccessPoints.isViable()) {
                        // Build partial calculation info with available AP data
                        CalculationInfo partialInfo = buildPartialCalculationInfo(wifiAccessPoints);
                        return handleError(wifiAccessPoints.getErrorMessage(), request, partialInfo);
                    }

                    CalculationResult calculationResult = performPositionCalculation(wifiAccessPoints);

                    // Build calculation info with partial data from calculator even on failure
                    CalculationInfo calculationInfo = calculationResult.positioningResult() != null
                            ? buildCalculationInfo(calculationResult.positioningResult(), wifiAccessPoints)
                            : buildPartialCalculationInfo(wifiAccessPoints);

                    if (!calculationResult.isSuccessful()) {
                        // Return error with detailed calculation info from calculator
                        return handleCalculationError(request, calculationInfo);
                    }

                    WifiPositioningResponse response = buildSuccessResponse(
                            calculationResult, wifiAccessPoints, request, calculationInfo);
                    logSuccessResponse(request, response, calculationInfo);
                    return response;
                })
                .exceptionally(e -> handleUnexpectedException(e, request));

        } catch (Exception e) {
            return CompletableFuture.completedFuture(handleUnexpectedException(e, request));
        }
    }

    // ===== HIGH-LEVEL FLOW METHODS =====

    /**
     * Logs incoming positioning request using structured logging for monitoring and debugging.
     *
     * @param request the positioning request to log
     */
    private void logIncomingRequest(WifiPositioningRequest request) {
        logger.info("", entries(Map.of(LOG_KEY_REQUEST, toLogDataMap(request))));
    }

    /**
     * Validates incoming positioning request for required data.
     *
     * @param request the positioning request to validate
     * @return validation result with success status and error message if invalid
     */
    private ValidationResult validateRequest(WifiPositioningRequest request) {
        if (request.wifiScanResults()
                   .isEmpty()) {
            return ValidationResult.invalid(ERROR_NO_SCAN_RESULTS);
        }

        return ValidationResult.valid();
    }

    /**
     * Prepares positioning data asynchronously by retrieving and filtering access points.
     *
     * <p>This method delegates to WifiAccessPoints.FilteringBuilder which encapsulates:
     * <ul>
     *   <li>Cell tower and AP location lookups (parallel, non-blocking)</li>
     *   <li>Top N strongest signal selection</li>
     *   <li>Global and local outlier detection</li>
     *   <li>Distribution validation</li>
     * </ul>
     * 
     * <p>All operations are performed asynchronously without blocking any threads.
     *
     * @param scanResults list of WiFi scan results from client device
     * @param cellInfoList optional list of cell tower information for cell-based filtering
     * @return CompletableFuture that completes with prepared WiFi access point data
     */
    private CompletableFuture<WifiAccessPoints> prepareWiFiAPData(
            List<WifiScanResult> scanResults, 
            List<CellInfo> cellInfoList) {
        
        // Use filtering builder with native async DynamoDB client for optimal performance
        // All async operations (cell tower lookup, AP lookup) are composed without blocking
        return WifiAccessPoints.filteringBuilder()
                .scanResults(scanResults)
                .cellInfo(cellInfoList)
                .asyncApLookup(accessPointRepository::findByMacAddressesAsync)
                .asyncCellTowerLookup(cellTowerRepository::findBestCellAsync)
                .topSignalsLimit(TOP_STRONGEST_SIGNALS_LIMIT)
                .maxWifiDistance(MAX_WIFI_DISTANCE_METERS)
                .buildWithFiltering();
    }

    /**
     * Performs position calculation using the positioning calculator.
     *
     * @param wifiAccessPoints prepared WiFi access point data for calculation
     * @return calculation result with timing and success status
     */
    private CalculationResult performPositionCalculation(WifiAccessPoints wifiAccessPoints) {
        long startTime = System.currentTimeMillis();
        var positioningResult = calculator.calculatePosition(wifiAccessPoints);
        long calculationTime = System.currentTimeMillis() - startTime;

        // Calculator returns partial result even on failure, check if position was calculated
        boolean hasValidPosition = positioningResult != null && positioningResult.position() != null;

        if (hasValidPosition) {
            return CalculationResult.successful(positioningResult, calculationTime);
        } else {
            // Return failed result but keep the partial positioningResult for debugging
            return CalculationResult.failed(positioningResult, calculationTime);
        }
    }

    /**
     * Builds successful positioning response with calculated position data.
     *
     * @param calculationResult result from position calculation
     * @param wifiAccessPoints  prepared access point data
     * @param request           original positioning request
     * @param calculationInfo   detailed calculation information
     * @return successful positioning response
     */
    private WifiPositioningResponse buildSuccessResponse(
            CalculationResult calculationResult,
            WifiAccessPoints wifiAccessPoints,
            WifiPositioningRequest request,
            CalculationInfo calculationInfo) {

        return createSuccessResponse(
                calculationResult.positioningResult(),
                wifiAccessPoints.getOriginalScans().size(),
                calculationResult.calculationTimeMs(),
                request,
                calculationInfo);
    }

    // ===== ERROR HANDLING METHODS =====

    /**
     * Handles data preparation errors and returns appropriate error response.
     *
     * @param errorMessage error description
     * @param request      original positioning request
     * @param partialInfo  partial calculation information if available
     * @return error response with optional calculation details
     */
    private WifiPositioningResponse handleError(
            String errorMessage,
            WifiPositioningRequest request,
            CalculationInfo partialInfo) {

        // Include calculation info only if explicitly requested
        var responseCalcInfo =
                Boolean.TRUE.equals(request.calculationDetail()) ? partialInfo : null;

        var response =
                WifiPositioningResponse.error(errorMessage, request, responseCalcInfo);
        logErrorResponse(request, response, partialInfo);
        return response;
    }

    /**
     * Handles position calculation errors and returns error response.
     *
     * @param request     original positioning request
     * @param partialInfo partial calculation information from failed calculation
     * @return error response with calculation failure details
     */
    private WifiPositioningResponse handleCalculationError(
            WifiPositioningRequest request,
            CalculationInfo partialInfo) {
        var message = String.format("%s . Cause - %s",
                                       ERROR_POSITION_CALCULATION_FAILED, ERROR_DURING_CALCULATING_POSITION);
        return handleError(message, request, partialInfo);
    }

    /**
     * Handles unexpected exceptions during position calculation.
     *
     * @param e       the unexpected exception
     * @param request original positioning request
     * @return error response with exception details
     */
    private WifiPositioningResponse handleUnexpectedException(Throwable e, WifiPositioningRequest request) {
        logger.error(LOG_ERROR_CALCULATING_POSITION, e);
        var response = WifiPositioningResponse.error(e.getMessage(), request, null);
        logErrorResponse(request, response, null);
        return response;
    }

    // ===== LOGGING METHODS =====

    /**
     * Logs successful positioning response with calculation details for monitoring.
     *
     * @param request         original positioning request
     * @param response        successful positioning response
     * @param calculationInfo detailed calculation information
     */
    private void logSuccessResponse(
            WifiPositioningRequest request,
            WifiPositioningResponse response,
            CalculationInfo calculationInfo) {

        logger.info("", entries(Map.of(LOG_KEY_RESPONSE, toLogDataMap(response))));

        // Log calculation info for monitoring and debugging only if not included in response
        if (calculationInfo != null && !request.calculationDetail()) {
                logger.info("", entries(Map.of(LOG_KEY_CALCULATION_INFO, toLogDataMap(calculationInfo))));
        }
    }

    /**
     * Logs error response with available calculation details for debugging.
     *
     * @param request     original positioning request
     * @param response    error positioning response
     * @param partialInfo partial calculation information if available
     */
    private void logErrorResponse(
            WifiPositioningRequest request,
            WifiPositioningResponse response,
            CalculationInfo partialInfo) {
        logger.error(
                LOG_ERROR_RESPONSE_FORMAT,
                request.requestId(),
                response.message());

        logger.info("", entries(Map.of(LOG_KEY_RESPONSE, toLogDataMap(response))));
        // Log partial calculation info for debugging only if not included in response
        if (partialInfo != null && !request.calculationDetail()) {
            logger.info("", entries(Map.of(LOG_KEY_CALCULATION_INFO, toLogDataMap(partialInfo))));
        }
    }

    private Map<String, Object> toLogDataMap(WifiPositioningRequest request) {
        return request.toMap();
    }

    private Map<String, Object> toLogDataMap(WifiPositioningResponse response) {
        return response.toMap();
    }

    private Map<String, Object> toLogDataMap(CalculationInfo calculationInfo) {
        return calculationInfo.toMap();
    }


    // ===== DATA ACCESS AND FILTERING METHODS =====
    
    // Note: Filtering logic has been moved to WifiAccessPoints.FilteringBuilder
    // for better encapsulation and cohesion


    // ===== RESPONSE BUILDING METHODS =====

    /**
     * Creates successful positioning response with validation and optional calculation details.
     *
     * @param positioningResult result from positioning calculation
     * @param apCount           number of access points used in calculation
     * @param calculationTime   calculation duration in milliseconds
     * @param request           original positioning request
     * @param calculationInfo   pre-built calculation information
     * @return successful positioning response with calculated position
     */
    private WifiPositioningResponse createSuccessResponse(
            WifiPositioningCalculator.PositioningResult positioningResult,
            int apCount,
            long calculationTime,
            WifiPositioningRequest request,
            CalculationInfo calculationInfo) {

        // Validate calculated position coordinates
        var position = positioningResult.position();
        if (!position.isValid()) {
            logger.warn(INVALID_COORDINATES_IN_POSITION_RESULT);
            var message = String.format("%s . Cause - %s", ERROR_POSITION_CALCULATION_FAILED, INVALID_COORDINATES_IN_POSITION_RESULT);
            return WifiPositioningResponse.error(message, request);
        }

        // Extract methods used from positioning result
        var methodsUsed = positioningResult.getMethodsUsedNames();

        // Create WifiPosition from positioning result
        var wifiPosition =
                new WifiPosition(
                        position.latitude(),
                        position.longitude(),
                        position.altitude(),
                        position.accuracy(),
                        DEFAULT_VERTICAL_ACCURACY,
                        position.confidence(),
                        methodsUsed,
                        apCount,
                        calculationTime);

        // Include calculation info only if explicitly requested
        var responseCalculationInfo =
                Boolean.TRUE.equals(request.calculationDetail()) ? calculationInfo : null;

        return WifiPositioningResponse.success(request, wifiPosition, responseCalculationInfo);
    }

    // ===== CALCULATION INFO BUILDING METHODS =====

    /**
     * Builds comprehensive calculation information from positioning result and access point data.
     *
     * @param positioningResult result from positioning calculation
     * @param wifiAccessPoints  prepared access point data
     * @return structured calculation information with algorithm and access point details
     */
    private CalculationInfo buildCalculationInfo(
            WifiPositioningCalculator.PositioningResult positioningResult,
            WifiAccessPoints wifiAccessPoints) {

        // Build components from WifiAccessPoints
        var accessPoints = wifiAccessPoints.getAccessPointInfos();
        var accessPointSummary = wifiAccessPoints.calculateAccessPointSummary();
        var selectionContext = buildSelectionContextInfo(positioningResult.selectionContext());
        var algorithmSelection = buildAlgorithmSelectionInfo(
                positioningResult.algorithmWeights(), positioningResult.selectionReasons());
        var cellTowerInfo = buildCellTowerInfo(wifiAccessPoints.getReferenceCell());
        
        return new CalculationInfo(accessPoints, accessPointSummary, selectionContext, algorithmSelection, cellTowerInfo);
    }

    /**
     * Builds partial calculation information for error scenarios with access point summary.
     *
     * @param wifiAccessPoints WiFi access point data containing scan results and known APs
     * @return partial calculation information with access point categorization
     */
    private CalculationInfo buildPartialCalculationInfo(WifiAccessPoints wifiAccessPoints) {
        // Build components from WifiAccessPoints
        var accessPoints = wifiAccessPoints.getAccessPointInfos();
        var accessPointSummary = wifiAccessPoints.calculateAccessPointSummary();
        var cellTowerInfo = buildCellTowerInfo(wifiAccessPoints.getReferenceCell());
        
        // No selection context or algorithm selection for error scenarios
        var selectionContext =
                new SelectionContextInfo(NO_VALID_AP, NO_VALID_AP, NO_VALID_AP, NO_VALID_AP);
        
        return new CalculationInfo(accessPoints, accessPointSummary, selectionContext, List.of(), cellTowerInfo);
    }


    /**
     * Builds selection context information from positioning result.
     *
     * @param context selection context from positioning calculation
     * @return selection context information for calculation details
     */
    private SelectionContextInfo buildSelectionContextInfo(SelectionContext context) {
        if (context == null) {
            return new SelectionContextInfo(null, null, null, null);
        }

        return new SelectionContextInfo(
                context.getApCountFactor() != null ? context.getApCountFactor().toString() : null,
                context.getSignalQuality() != null ? context.getSignalQuality().toString() : null,
                context.getSignalDistribution() != null ? context.getSignalDistribution().toString() : null,
                context.getGeometricQuality() != null ? context.getGeometricQuality().toString() : null
        );
    }

    /**
     * Builds algorithm selection information from weights and selection reasons.
     *
     * @param algorithmWeights map of algorithms to their selection weights
     * @param selectionReasons map of algorithms to their selection reasons
     * @return list of algorithm selection information for calculation details
     */
    private List<AlgorithmSelectionInfo> buildAlgorithmSelectionInfo(
            Map<PositioningAlgorithm, Double> algorithmWeights,
            Map<PositioningAlgorithm, List<String>> selectionReasons) {

        // Collect all unique algorithms from both maps
        Set<PositioningAlgorithm> allAlgorithms = new HashSet<>();
        if (algorithmWeights != null) allAlgorithms.addAll(algorithmWeights.keySet());
        if (selectionReasons != null) allAlgorithms.addAll(selectionReasons.keySet());

        return allAlgorithms.stream()
                            .map(algorithm -> {
                                var selected = algorithmWeights != null && algorithmWeights.containsKey(algorithm);
                                var weight = algorithmWeights != null ? algorithmWeights.get(algorithm) : null;
                                List<String> reasons = selectionReasons != null ?
                                        selectionReasons.getOrDefault(algorithm, List.of()) : List.of();

                                return new AlgorithmSelectionInfo(
                                        algorithm.getName(), selected, reasons, weight);
                            })
                            .toList();
    }

    /**
     * Builds cell tower information from CellTower reference.
     * Extracts relevant cell tower location and range data for calculation details.
     *
     * @param cellTower the cell tower reference if available
     * @return cell tower information, or null if cell tower is not available
     */
    private CellTowerInfo buildCellTowerInfo(CellTower cellTower) {
        if (cellTower == null) {
            return null;
        }

        return new CellTowerInfo(
                cellTower.getId(),
                cellTower.getCellType(),
                cellTower.getLatitude(),
                cellTower.getLongitude(),
                cellTower.getRange()
        );
    }
}
