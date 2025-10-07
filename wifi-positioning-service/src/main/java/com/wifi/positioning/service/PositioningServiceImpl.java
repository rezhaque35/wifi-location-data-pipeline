package com.wifi.positioning.service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.WifiPositioningCalculator;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.dto.CalculationInfo;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiAPData;
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningResponse.WifiPosition;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.repository.WifiAccessPointRepository;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toSet;
import static net.logstash.logback.argument.StructuredArguments.*;

/**
 * Implementation of the PositioningService interface. Provides functionality for calculating
 * positions based on WiFi scan results.
 *
 * <p>This service: 1. Receives WiFi scan results from client devices 2. Performs validation on
 * input data 3. Looks up known access points from the repository using optimized batch operations
 * 4. Filters access points to only use those with active or warning status 5. Delegates positioning
 * calculation to WifiPositioningCalculator 6. Processes the PositioningResult to extract algorithm
 * information 7. Formats the response with algorithm names and positioning data
 */
@Service
public class PositioningServiceImpl implements PositioningService {

    private static final Logger logger = LoggerFactory.getLogger(PositioningServiceImpl.class);

    /**
     * Error message constants for consistent error reporting. These constants ensure standardized
     * error messages across the application and make them easier to maintain and localize if needed.
     */

    /**
     * Error message when no WiFi scan results are provided in the request. Rationale: This is a
     * client-side error indicating missing required data. The message is clear and actionable for API
     * consumers.
     */
    private static final String ERROR_NO_SCAN_RESULTS = "No WiFi scan results provided";

    /**
     * Error message when no known access points are found in the database. Rationale: Indicates that
     * none of the scanned access points exist in our reference database, making position calculation
     * impossible.
     */
    private static final String ERROR_NO_KNOWN_ACCESS_POINTS =
            "No known access points found in database";

    /**
     * Error message when no access points with valid status are available. Rationale: While access
     * points were found, none have "active" or "warning" status, so they cannot be used for reliable
     * positioning calculations.
     */
    private static final String ERROR_NO_VALID_STATUS_ACCESS_POINTS =
            "No access points with valid status found";

    /**
     * Base error message for position calculation failures. Rationale: Generic message for when the
     * positioning algorithms cannot determine a location despite having valid input data.
     */
    private static final String ERROR_POSITION_CALCULATION_FAILED =
            "Position calculation failed: no position could be determined";

    /**
     * Default value for vertical accuracy when not provided by the positioning algorithms. Set to 0.0
     * as most algorithms in this system only calculate horizontal accuracy.
     */
    private static final double DEFAULT_VERTICAL_ACCURACY = 0.0;

    /**
     * Default high accuracy setting for backward compatibility
     */
    private static final boolean DEFAULT_HIGH_ACCURACY = false;

    /**
     * Default return all methods setting for backward compatibility
     */
    private static final boolean DEFAULT_RETURN_ALL_METHODS = false;
    public static final String INVALID_COORDINATES_IN_POSITION_RESULT = "Invalid coordinates in position result";
    public static final String ERROR_DURING_CALCULATING_POSITION = "unexpected error during calculating position.";

    /**
     * Status value for unknown or unrecognized access points
     */
    private static final String STATUS_UNKNOWN = "unknown";

    private final WifiPositioningCalculator calculator;
    private final WifiAccessPointRepository accessPointRepository;

    // ===== INNER RECORDS FOR DATA TRANSFER =====

    /**
     * Holds the result of request validation.
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
     * Holds the result of position calculation.
     * Even failed calculations may contain a partial positioningResult with debugging information.
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
    public PositioningServiceImpl(
            WifiPositioningCalculator calculator,
            WifiAccessPointRepository accessPointRepository) {
        this.calculator = calculator;
        this.accessPointRepository = accessPointRepository;
    }

    @Override
    public WifiPositioningResponse calculatePosition(WifiPositioningRequest request) {
        logIncomingRequest(request);

        try {
            ValidationResult validation = validateRequest(request);
            if (!validation.isValid()) {
                // No data available for calculationInfo at this stage
                return handleError(validation.errorMessage(), request, null);
            }

            WifiAPData wifiAPData = prepareWiFiAPData(request.wifiScanResults());
            if (!wifiAPData.isViable()) {
                // Build partial calculationInfo with whatever AP data is available
                CalculationInfo partialInfo = buildPartialCalculationInfo(wifiAPData);
                return handleError(wifiAPData.errorMessage(), request, partialInfo);
            }

            CalculationResult calculationResult = performPositionCalculation(wifiAPData);

            // Always build calculation info (now includes partial data from calculator even on failure)
            CalculationInfo calculationInfo = calculationResult.positioningResult() != null
                    ? buildCalculationInfo(calculationResult.positioningResult(), wifiAPData.knownAccessPoints())
                    : buildPartialCalculationInfo(wifiAPData);

            if (!calculationResult.isSuccessful()) {
                // Return error with detailed calculation info from the calculator
                return handleCalculationError(request, calculationInfo);
            }

            WifiPositioningResponse response = buildSuccessResponse(
                    calculationResult, wifiAPData, request, calculationInfo);
            logSuccessResponse(request, response, calculationInfo);
            return response;

        } catch (Exception e) {
            return handleUnexpectedException(e, request);
        }
    }

    // ===== HIGH-LEVEL FLOW METHODS =====

    /**
     * Logs the incoming positioning request using structured logging.
     */
    private void logIncomingRequest(WifiPositioningRequest request) {
        logger.info(
                "Calculating position for {} WiFi scan results from client {} if application {} with requestId {}",
                request.wifiScanResults()
                       .size(),
                request.client(),
                request.application(),
                request.requestId());
        
        logger.info("{}", entries(Map.of("request", toLogDataMap(request))));
    }

    /**
     * Validates the incoming positioning request.
     */
    private ValidationResult validateRequest(WifiPositioningRequest request) {
        if (request.wifiScanResults()
                   .isEmpty()) {
            return ValidationResult.invalid(ERROR_NO_SCAN_RESULTS);
        }

        return ValidationResult.valid();
    }

    /**
     * Prepares positioning data by looking up and filtering access points.
     */
    private WifiAPData prepareWiFiAPData(List<WifiScanResult> scanResults) {
        List<WifiAccessPoint> knownAccessPoints = lookupKnownAccessPoints(scanResults);

        if (knownAccessPoints.isEmpty()) {
            return WifiAPData.notViable(scanResults, knownAccessPoints, ERROR_NO_KNOWN_ACCESS_POINTS);
        }

        List<WifiAccessPoint> validAccessPoints = filterAPsByStatus(knownAccessPoints);

        if (validAccessPoints.isEmpty()) {
            return WifiAPData.notViable(scanResults, knownAccessPoints, ERROR_NO_VALID_STATUS_ACCESS_POINTS);
        }

        return WifiAPData.viable(scanResults, knownAccessPoints, validAccessPoints);
    }

    /**
     * Performs the position calculation using the positioning calculator.
     * The calculator always returns a PositioningResult with partial data even on failure.
     */
    private CalculationResult performPositionCalculation(WifiAPData data) {
        long startTime = System.currentTimeMillis();
        var positioningResult = calculator.calculatePosition(data);
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
     * Builds a successful positioning response.
     * Conditionally includes calculation info based on request flag.
     */
    private WifiPositioningResponse buildSuccessResponse(
            CalculationResult calculationResult,
            WifiAPData wifiAPData,
            WifiPositioningRequest request,
            CalculationInfo calculationInfo) {

        return createSuccessResponse(
                calculationResult.positioningResult(),
                wifiAPData.scanResults()
                          .size(),
                calculationResult.calculationTimeMs(),
                request,
                calculationInfo);
    }

    // ===== ERROR HANDLING METHODS =====

    /**
     * Handles data preparation errors and returns appropriate response.
     * Includes partial calculationInfo when available and requested.
     */
    private WifiPositioningResponse handleError(
            String errorMessage,
            WifiPositioningRequest request,
            CalculationInfo partialInfo) {

        // Only include calculationInfo in response if explicitly requested
        CalculationInfo responseCalcInfo =
                Boolean.TRUE.equals(request.calculationDetail()) ? partialInfo : null;

        WifiPositioningResponse response =
                WifiPositioningResponse.error(errorMessage, request, responseCalcInfo);
        logErrorResponse(request, response, partialInfo);
        return response;
    }

    /**
     * Handles calculation errors and returns appropriate response.
     */
    private WifiPositioningResponse handleCalculationError(
            WifiPositioningRequest request,
            CalculationInfo partialInfo) {
        String message = String.format("%s . Cause - %s",
                                       ERROR_POSITION_CALCULATION_FAILED, ERROR_DURING_CALCULATING_POSITION);
        return handleError(message, request, partialInfo);
    }

    /**
     * Handles unexpected exceptions and returns appropriate response.
     */
    private WifiPositioningResponse handleUnexpectedException(Exception e, WifiPositioningRequest request) {
        logger.error("Error calculating position", e);
        WifiPositioningResponse response = WifiPositioningResponse.error(e.getMessage(), request, null);
        logErrorResponse(request, response, null);
        return response;
    }

    // ===== LOGGING METHODS =====

    /**
     * Logs successful positioning response along with calculation details.
     * Only logs calculation info separately if it's not already included in the response
     * (i.e., when calculationDetail flag is false).
     */
    private void logSuccessResponse(
            WifiPositioningRequest request,
            WifiPositioningResponse response,
            CalculationInfo calculationInfo) {
        logger.info(
                "Returning successful positioning response for requestId {}: {}",
                request.requestId(),
                response.message());

        logger.info("{}", entries(Map.of("response", toLogDataMap(response))));

        // Log calculation info for monitoring and debugging only if not included in response
        if (calculationInfo != null && !request.calculationDetail()) {
            logger.info("{}", entries(Map.of("calculationInfo", toLogDataMap(calculationInfo))));
        }
    }

    /**
     * Logs error response along with any available calculation details.
     * Only logs partial calculation info separately if it's not already included in the response
     * (i.e., when calculationDetail flag is false).
     */
    private void logErrorResponse(
            WifiPositioningRequest request,
            WifiPositioningResponse response,
            CalculationInfo partialInfo) {
        logger.error(
                "Failure : Returning error response for requestId {}: {}",
                request.requestId(),
                response.message());

        logger.error("{}", entries(Map.of("response", toLogDataMap(response))));
        // Log partial calculation info for debugging only if not included in response
        if (partialInfo != null && !request.calculationDetail()) {
            logger.info("{}", entries(Map.of("calculationInfo", toLogDataMap(partialInfo))));
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

    /**
     * Filter access points by status. Only APs with active or warning status should be used.
     *
     * @param allAPs List of all known access points retrieved from the database
     * @return List of access points with valid status (active or warning)
     */
    private List<WifiAccessPoint> filterAPsByStatus(List<WifiAccessPoint> allAPs) {
        return allAPs.stream()
                     .filter(
                             ap ->
                                     ap.getStatus() != null
                                             && WifiAccessPoint.VALID_AP_STATUSES.contains(ap.getStatus()))
                     .toList();
    }

    /**
     * Lookup known access points from the repository based on MAC addresses from scan results.
     * Uses batch operation to optimize DynamoDB access.
     */
    private List<WifiAccessPoint> lookupKnownAccessPoints(List<WifiScanResult> scanResults) {

        try {
            // Use batch operation to retrieve all access points in a single call
            Map<String, WifiAccessPoint> apMap =
                    scanResults.stream()
                               .map(WifiScanResult::macAddress)
                               .collect(collectingAndThen(toSet(), accessPointRepository::findByMacAddresses));

            // Process the results with null safety
            if (apMap != null && !apMap.isEmpty()) {

                logger.info(
                        "Found {} known access points in database out of {} scan results",
                        apMap.size(),
                        scanResults.size());

                return apMap.values()
                            .stream()
                            .toList();
            } else {
                logger.warn("No access point found for {}",
                            scanResults.stream()
                                       .map(WifiScanResult::macAddress)
                                       .collect(toSet()));
                return Collections.emptyList();
            }

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(),e);
        }
    }

    /**
     * Fallback method to look up access points individually if batch operation fails. This ensures
     * the system continues to function even if the batch operation encounters an error.
     */
    private List<WifiAccessPoint> fallbackIndividualLookups(Set<String> macAddresses) {
        List<WifiAccessPoint> knownAPs = new ArrayList<>();

        // Look up each MAC address individually
        for (String macAddress : macAddresses) {
            try {
                Optional<WifiAccessPoint> ap = accessPointRepository.findByMacAddress(macAddress);
                ap.ifPresent(knownAPs::add);
            } catch (Exception e) {
                logger.warn("Error looking up access point with MAC {}: {}", macAddress, e.getMessage());
            }
        }

        return knownAPs;
    }

    // ===== RESPONSE BUILDING METHODS =====

    /**
     * Creates a success response from the positioning result. This method handles: - Position
     * validation - Converting position to WifiPosition - Conditionally including calculation details
     *
     * @param positioningResult The result from the positioning calculation
     * @param apCount           Number of access points used in calculation
     * @param calculationTime   Time taken for calculation in milliseconds
     * @param request           The original position request
     * @param calculationInfo   Pre-built calculation info (included only if request.calculationDetail is true)
     * @return A success response with the calculated position
     */
    private WifiPositioningResponse createSuccessResponse(
            WifiPositioningCalculator.PositioningResult positioningResult,
            int apCount,
            long calculationTime,
            WifiPositioningRequest request,
            CalculationInfo calculationInfo) {

        // Validate position coordinates
        Position position = positioningResult.position();
        if (!position.isValid()) {
            logger.warn(INVALID_COORDINATES_IN_POSITION_RESULT);
            String message = String.format("%s . Cause - %s", ERROR_POSITION_CALCULATION_FAILED, INVALID_COORDINATES_IN_POSITION_RESULT);
            return WifiPositioningResponse.error(message, request);
        }

        // Get methods used from the positioning result
        List<String> methodsUsed = positioningResult.getMethodsUsedNames();

        // Create the WifiPosition from the positioning result
        WifiPosition wifiPosition =
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

        // Only include calculation info in response if explicitly requested
        CalculationInfo responseCalculationInfo =
                Boolean.TRUE.equals(request.calculationDetail()) ? calculationInfo : null;

        return WifiPositioningResponse.success(request, wifiPosition, responseCalculationInfo);
    }

    // ===== CALCULATION INFO BUILDING METHODS =====

    /**
     * Builds structured calculation information from positioning result and access points.
     *
     * @param positioningResult The result from the positioning calculation
     * @param knownAPs          List of all known access points
     * @return Structured calculation information
     */
    private CalculationInfo buildCalculationInfo(
            WifiPositioningCalculator.PositioningResult positioningResult,
            List<WifiAccessPoint> knownAPs) {

        // Build access points information
        List<CalculationInfo.AccessPointInfo> accessPoints = buildAccessPointsInfo(knownAPs);

        // Build access point summary
        CalculationInfo.AccessPointSummary accessPointSummary = buildAccessPointSummary(accessPoints);

        // Build selection context information
        CalculationInfo.SelectionContextInfo selectionContext = buildSelectionContextInfo(positioningResult.selectionContext());

        // Build algorithm selection information
        List<CalculationInfo.AlgorithmSelectionInfo> algorithmSelection = buildAlgorithmSelectionInfo(
                positioningResult.algorithmWeights(), positioningResult.selectionReasons());

        return new CalculationInfo(accessPoints, accessPointSummary, selectionContext, algorithmSelection);
    }

    /**
     * Builds partial calculation information for error scenarios.
     * Includes access point information and summary showing known vs unknown APs.
     * Summary shows all scanned APs with proper categorization by status.
     *
     * @param wifiAPData WiFi access point data containing scan results and known APs
     * @return Partial calculation information with complete summary
     */
    private CalculationInfo buildPartialCalculationInfo(WifiAPData wifiAPData) {
        // Build access points information from known APs (if any)
        List<CalculationInfo.AccessPointInfo> accessPoints = (wifiAPData.knownAccessPoints() != null)
                ? buildAccessPointsInfo(wifiAPData.knownAccessPoints())
                : List.of();

        // Build comprehensive access point summary including unknown APs
        CalculationInfo.AccessPointSummary accessPointSummary =
                buildPartialAccessPointSummary(wifiAPData);

        // No selection context or algorithm selection for error scenarios
        CalculationInfo.SelectionContextInfo selectionContext =
                new CalculationInfo.SelectionContextInfo(null, null, null, null);

        List<CalculationInfo.AlgorithmSelectionInfo> algorithmSelection = List.of();

        return new CalculationInfo(accessPoints, accessPointSummary, selectionContext, algorithmSelection);
    }

    /**
     * Builds access point summary for partial calculation info.
     * Shows complete picture: total scanned APs, known APs by status, and unknown APs.
     *
     * @param wifiAPData WiFi access point data containing scan results and known APs
     * @return Summary with counts of all scanned, known, and unknown access points
     */
    private CalculationInfo.AccessPointSummary buildPartialAccessPointSummary(WifiAPData wifiAPData) {
        // Calculate total scanned APs from request
        int totalScanned = (wifiAPData.scanResults() != null)
                ? wifiAPData.scanResults()
                            .size()
                : 0;

        // Process known APs and their statuses
        Map<String, Integer> statusCounts = new HashMap<>();
        int knownAPCount = 0;

        if (wifiAPData.knownAccessPoints() != null && !wifiAPData.knownAccessPoints()
                                                                 .isEmpty()) {
            for (WifiAccessPoint ap : wifiAPData.knownAccessPoints()) {
                String status = ap.getStatus() != null ? ap.getStatus() : STATUS_UNKNOWN;
                statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
                knownAPCount++;
            }
        }

        // Calculate unknown APs (scanned but not in database)
        int unknownAPCount = totalScanned - knownAPCount;
        if (unknownAPCount > 0) {
            statusCounts.put(STATUS_UNKNOWN, statusCounts.getOrDefault(STATUS_UNKNOWN, 0) + unknownAPCount);
        }

        // Convert to sorted list for consistent output
        List<CalculationInfo.StatusCount> statusCountList = statusCounts.entrySet()
                                                                        .stream()
                                                                        .map(entry -> new CalculationInfo.StatusCount(entry.getKey(), entry.getValue()))
                                                                        .sorted((a, b) -> a.status()
                                                                                           .compareTo(b.status()))
                                                                        .toList();

        // Used count is 0 for partial info (error scenarios)
        return new CalculationInfo.AccessPointSummary(totalScanned, 0, statusCountList);
    }

    /**
     * Builds access points information for calculation details.
     */
    private List<CalculationInfo.AccessPointInfo> buildAccessPointsInfo(List<WifiAccessPoint> knownAPs) {
        return knownAPs.stream()
                       .map(ap -> {
                           String status = ap.getStatus() != null ? ap.getStatus() : STATUS_UNKNOWN;
                           boolean used = WifiAccessPoint.VALID_AP_STATUSES.contains(status);
                           String usage = used ? "used" : "filtered";

                           CalculationInfo.LocationInfo location = new CalculationInfo.LocationInfo(
                                   ap.getLatitude(), ap.getLongitude(), ap.getAltitude());

                           return new CalculationInfo.AccessPointInfo(ap.getMacAddress(), location, status, usage);
                       })
                       .toList();
    }

    /**
     * Builds access point summary with counts and usage statistics from the processed access points.
     */
    private CalculationInfo.AccessPointSummary buildAccessPointSummary(List<CalculationInfo.AccessPointInfo> accessPoints) {
        Map<String, Integer> statusCounts = new HashMap<>();
        int usedCount = 0;

        for (CalculationInfo.AccessPointInfo ap : accessPoints) {
            String status = ap.status();
            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);

            // Count APs that are marked as "used" in their usage field
            if ("used".equals(ap.usage())) {
                usedCount++;
            }
        }

        List<CalculationInfo.StatusCount> statusCountList = statusCounts.entrySet()
                                                                        .stream()
                                                                        .map(entry -> new CalculationInfo.StatusCount(entry.getKey(), entry.getValue()))
                                                                        .sorted((a, b) -> a.status()
                                                                                           .compareTo(b.status())) // Sort for consistent output
                                                                        .toList();

        return new CalculationInfo.AccessPointSummary(accessPoints.size(), usedCount, statusCountList);
    }

    /**
     * Builds selection context information from the positioning result.
     */
    private CalculationInfo.SelectionContextInfo buildSelectionContextInfo(SelectionContext context) {
        if (context == null) {
            return new CalculationInfo.SelectionContextInfo(null, null, null, null);
        }

        return new CalculationInfo.SelectionContextInfo(
                context.getApCountFactor() != null ? context.getApCountFactor()
                                                            .toString() : null,
                context.getSignalQuality() != null ? context.getSignalQuality()
                                                            .toString() : null,
                context.getSignalDistribution() != null ? context.getSignalDistribution()
                                                                 .toString() : null,
                context.getGeometricQuality() != null ? context.getGeometricQuality()
                                                               .toString() : null
        );
    }

    /**
     * Builds algorithm selection information from weights and selection reasons.
     */
    private List<CalculationInfo.AlgorithmSelectionInfo> buildAlgorithmSelectionInfo(
            Map<PositioningAlgorithm, Double> algorithmWeights,
            Map<PositioningAlgorithm, List<String>> selectionReasons) {

        // Get all unique algorithms from both maps
        Set<PositioningAlgorithm> allAlgorithms = new HashSet<>();
        if (algorithmWeights != null) allAlgorithms.addAll(algorithmWeights.keySet());
        if (selectionReasons != null) allAlgorithms.addAll(selectionReasons.keySet());

        return allAlgorithms.stream()
                            .map(algorithm -> {
                                boolean selected = algorithmWeights != null && algorithmWeights.containsKey(algorithm);
                                Double weight = algorithmWeights != null ? algorithmWeights.get(algorithm) : null;
                                List<String> reasons = selectionReasons != null ?
                                        selectionReasons.getOrDefault(algorithm, List.of()) : List.of();

                                return new CalculationInfo.AlgorithmSelectionInfo(
                                        algorithm.getName(), selected, reasons, weight);
                            })
                            .toList();
    }
}
