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
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningResponse.WifiPosition;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.repository.WifiAccessPointRepository;

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
@Profile("!test")
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
   * Error message when signal physics validation fails. Rationale: Indicates that the signal
   * strength relationships between access points violate physical laws (e.g., signal strength
   * increases with distance), suggesting measurement errors or data corruption.
   */
  private static final String ERROR_INVALID_SIGNAL_PHYSICS =
      "Physically impossible signal strength relationships";

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
      "No access points with valid status (active or warning) found";

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

  /** Default high accuracy setting for backward compatibility */
  private static final boolean DEFAULT_HIGH_ACCURACY = false;

  /** Default return all methods setting for backward compatibility */
  private static final boolean DEFAULT_RETURN_ALL_METHODS = false;

  private final WifiPositioningCalculator calculator;
  private final WifiAccessPointRepository accessPointRepository;
  private final SignalPhysicsValidator signalPhysicsValidator;

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
   * Holds prepared positioning data including scan results and access points.
   */
  private record PositioningData(
      List<WifiScanResult> scanResults,
      List<WifiAccessPoint> knownAccessPoints,
      List<WifiAccessPoint> validAccessPoints,
      boolean isViable,
      String errorMessage) {
    
    static PositioningData viable(
        List<WifiScanResult> scanResults,
        List<WifiAccessPoint> knownAccessPoints,
        List<WifiAccessPoint> validAccessPoints) {
      return new PositioningData(scanResults, knownAccessPoints, validAccessPoints, true, null);
    }
    
    static PositioningData notViable(String errorMessage) {
      return new PositioningData(null, null, null, false, errorMessage);
    }
  }

  /**
   * Holds the result of position calculation.
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
    
    static CalculationResult failed() {
      return new CalculationResult(null, 0, false);
    }
  }

  @Autowired
  public PositioningServiceImpl(
      WifiPositioningCalculator calculator,
      WifiAccessPointRepository accessPointRepository,
      SignalPhysicsValidator signalPhysicsValidator) {
    this.calculator = calculator;
    this.accessPointRepository = accessPointRepository;
    this.signalPhysicsValidator = signalPhysicsValidator;
  }

  @Override
  public WifiPositioningResponse calculatePosition(WifiPositioningRequest request) {
    logIncomingRequest(request);
    
    try {
      ValidationResult validation = validateRequest(request);
      if (!validation.isValid()) {
        return handleValidationError(validation.errorMessage(), request);
      }

      PositioningData positioningData = preparePositioningData(request.wifiScanResults());
      if (!positioningData.isViable()) {
        return handleDataPreparationError(positioningData.errorMessage(), request);
      }

      CalculationResult calculationResult = performPositionCalculation(positioningData);
      if (!calculationResult.isSuccessful()) {
        return handleCalculationError(request);
      }

      WifiPositioningResponse response = buildSuccessResponse(
          calculationResult, positioningData, request);
      logSuccessResponse(request, response);
      return response;

    } catch (Exception e) {
      return handleUnexpectedException(e, request);
    }
  }

  // ===== HIGH-LEVEL FLOW METHODS =====

  /**
   * Logs the incoming positioning request.
   */
  private void logIncomingRequest(WifiPositioningRequest request) {
    logger.info(
        "Calculating position for {} WiFi scan results from client {} if application {} with requestId {}",
        request.wifiScanResults().size(),
        request.client(),
        request.application(),
        request.requestId());
    logger.debug("Full positioning request: {}", request);
  }

  /**
   * Validates the incoming positioning request.
   */
  private ValidationResult validateRequest(WifiPositioningRequest request) {
    if (request.wifiScanResults().isEmpty()) {
      return ValidationResult.invalid(ERROR_NO_SCAN_RESULTS);
    }

    if (!signalPhysicsValidator.isPhysicallyPossible(request.wifiScanResults())) {
      return ValidationResult.invalid(ERROR_INVALID_SIGNAL_PHYSICS);
    }

    return ValidationResult.valid();
  }

  /**
   * Prepares positioning data by looking up and filtering access points.
   */
  private PositioningData preparePositioningData(List<WifiScanResult> scanResults) {
    List<WifiAccessPoint> knownAccessPoints = lookupKnownAccessPoints(scanResults);
    
    if (knownAccessPoints.isEmpty()) {
      return PositioningData.notViable(ERROR_NO_KNOWN_ACCESS_POINTS);
    }

    List<WifiAccessPoint> validAccessPoints = filterAPsByStatus(knownAccessPoints);
    
    if (validAccessPoints.isEmpty()) {
      return PositioningData.notViable(ERROR_NO_VALID_STATUS_ACCESS_POINTS);
    }

    return PositioningData.viable(scanResults, knownAccessPoints, validAccessPoints);
  }

  /**
   * Performs the position calculation using the positioning calculator.
   */
  private CalculationResult performPositionCalculation(PositioningData data) {
    long startTime = System.currentTimeMillis();
    WifiPositioningCalculator.PositioningResult positioningResult =
        calculator.calculatePosition(data.scanResults(), data.validAccessPoints());
    long calculationTime = System.currentTimeMillis() - startTime;

    if (positioningResult == null || positioningResult.position() == null) {
      return CalculationResult.failed();
    }

    return CalculationResult.successful(positioningResult, calculationTime);
  }

  /**
   * Builds a successful positioning response.
   */
  private WifiPositioningResponse buildSuccessResponse(
      CalculationResult calculationResult,
      PositioningData positioningData,
      WifiPositioningRequest request) {
    
    return createSuccessResponse(
        calculationResult.positioningResult(),
        positioningData.scanResults().size(),
        calculationResult.calculationTimeMs(),
        request,
        positioningData.knownAccessPoints());
  }

  // ===== ERROR HANDLING METHODS =====

  /**
   * Handles validation errors and returns appropriate response.
   */
  private WifiPositioningResponse handleValidationError(String errorMessage, WifiPositioningRequest request) {
    logger.warn(errorMessage);
    WifiPositioningResponse response = WifiPositioningResponse.error(errorMessage, request);
    logErrorResponse(request, response);
    return response;
  }

  /**
   * Handles data preparation errors and returns appropriate response.
   */
  private WifiPositioningResponse handleDataPreparationError(String errorMessage, WifiPositioningRequest request) {
    logger.warn(errorMessage);
    WifiPositioningResponse response = createPositionNotFoundResponse(0, request);
    logErrorResponse(request, response);
    return response;
  }

  /**
   * Handles calculation errors and returns appropriate response.
   */
  private WifiPositioningResponse handleCalculationError(WifiPositioningRequest request) {
    logger.warn(ERROR_POSITION_CALCULATION_FAILED);
    WifiPositioningResponse response = createPositionNotFoundResponse(0, request);
    logErrorResponse(request, response);
    return response;
  }

  /**
   * Handles unexpected exceptions and returns appropriate response.
   */
  private WifiPositioningResponse handleUnexpectedException(Exception e, WifiPositioningRequest request) {
    logger.error("Error calculating position", e);
    WifiPositioningResponse response = WifiPositioningResponse.error(e.getMessage(), request);
    logErrorResponse(request, response);
    return response;
  }

  // ===== LOGGING METHODS =====

  /**
   * Logs successful positioning response.
   */
  private void logSuccessResponse(WifiPositioningRequest request, WifiPositioningResponse response) {
    logger.info(
        "Returning successful positioning response for requestId {}: {}",
        request.requestId(),
        response);
  }

  /**
   * Logs error response.
   */
  private void logErrorResponse(WifiPositioningRequest request, WifiPositioningResponse response) {
    logger.info(
        "Returning error response for requestId {}: {}",
        request.requestId(),
        response);
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
    // Extract all MAC addresses from scan results
    Set<String> macAddresses =
        scanResults.stream().map(WifiScanResult::macAddress).collect(Collectors.toSet());

    try {
      // Use batch operation to retrieve all access points in a single call
      Map<String, WifiAccessPoint> apMap = accessPointRepository.findByMacAddresses(macAddresses);

      List<WifiAccessPoint> knownAPs = new ArrayList<>();

      // Process the results with null safety
      if (apMap != null) {
        // Convert map values to list
        knownAPs.addAll(apMap.values());
      } else {
        logger.warn("Batch lookup returned null map");
      }

      logger.info(
          "Found {} known access points in database out of {} scan results",
          knownAPs.size(),
          scanResults.size());

      return knownAPs;

    } catch (Exception e) {
      logger.error("Error in batch lookup of access points: {}", e.getMessage(), e);

      // Fall back to individual lookups if batch operation fails
      logger.warn("Falling back to individual lookups due to batch operation failure");
      return fallbackIndividualLookups(macAddresses);
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

  /**
   * Create a response when position calculation fails.
   */
  private WifiPositioningResponse createPositionNotFoundResponse(
      int apCount, WifiPositioningRequest request) {
    String message = ERROR_POSITION_CALCULATION_FAILED;
    return WifiPositioningResponse.error(message, request);
  }

  // ===== RESPONSE BUILDING METHODS =====

  /**
   * Creates a success response from the positioning result. This method handles: - Position
   * validation - Converting position to WifiPosition - Adding calculation details if requested
   *
   * @param positioningResult The result from the positioning calculation
   * @param apCount Number of access points used in calculation
   * @param calculationTime Time taken for calculation in milliseconds
   * @param request The original position request
   * @param knownAPs List of all known access points (used for calculation info)
   * @return A success response with the calculated position
   */
  private WifiPositioningResponse createSuccessResponse(
      WifiPositioningCalculator.PositioningResult positioningResult,
      int apCount,
      long calculationTime,
      WifiPositioningRequest request,
      List<WifiAccessPoint> knownAPs) {

    // Validate position coordinates
    Position position = positioningResult.position();
    if (!position.isValid()) {
      logger.warn("Invalid coordinates in position result");
      return createPositionNotFoundResponse(apCount, request);
    }

    // Get methods used from the positioning result
    List<String> methodsUsed = positioningResult.getMethodsUsedNames();

    // Build structured calculation info
    CalculationInfo calculationInfo = buildCalculationInfo(positioningResult, knownAPs);

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

    return WifiPositioningResponse.success(request, wifiPosition, calculationInfo);
  }

  // ===== CALCULATION INFO BUILDING METHODS =====

  /**
   * Builds structured calculation information from positioning result and access points.
   *
   * @param positioningResult The result from the positioning calculation
   * @param knownAPs List of all known access points
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
   * Builds access points information for calculation details.
   */
  private List<CalculationInfo.AccessPointInfo> buildAccessPointsInfo(List<WifiAccessPoint> knownAPs) {
    return knownAPs.stream()
        .map(ap -> {
          String status = ap.getStatus() != null ? ap.getStatus() : "unknown";
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

    List<CalculationInfo.StatusCount> statusCountList = statusCounts.entrySet().stream()
        .map(entry -> new CalculationInfo.StatusCount(entry.getKey(), entry.getValue()))
        .sorted((a, b) -> a.status().compareTo(b.status())) // Sort for consistent output
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
        context.getApCountFactor() != null ? context.getApCountFactor().toString() : null,
        context.getSignalQuality() != null ? context.getSignalQuality().toString() : null,
        context.getSignalDistribution() != null ? context.getSignalDistribution().toString() : null,
        context.getGeometricQuality() != null ? context.getGeometricQuality().toString() : null
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
