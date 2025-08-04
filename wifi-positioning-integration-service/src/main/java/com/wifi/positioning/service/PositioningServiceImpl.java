package com.wifi.positioning.service;

import com.wifi.positioning.algorithm.WifiPositioningCalculator;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningResponse.WifiPosition;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.wifi.positioning.dto.WifiPositioningRequest;

/**
 * Implementation of the PositioningService interface.
 * Provides functionality for calculating positions based on WiFi scan results.
 * 
 * This service:
 * 1. Receives WiFi scan results from client devices
 * 2. Performs validation on input data
 * 3. Looks up known access points from the repository using optimized batch operations
 * 4. Filters access points to only use those with active or warning status
 * 5. Delegates positioning calculation to WifiPositioningCalculator
 * 6. Processes the PositioningResult to extract algorithm information
 * 7. Formats the response with algorithm names and positioning data
 */
@Service
@Profile("!test")
public class PositioningServiceImpl implements PositioningService {

    private static final Logger logger = LoggerFactory.getLogger(PositioningServiceImpl.class);
    
    /**
     * Error message constants for consistent error reporting.
     * These constants ensure standardized error messages across the application
     * and make them easier to maintain and localize if needed.
     */
    
    /**
     * Error message when no WiFi scan results are provided in the request.
     * Rationale: This is a client-side error indicating missing required data.
     * The message is clear and actionable for API consumers.
     */
    private static final String ERROR_NO_SCAN_RESULTS = "No WiFi scan results provided";
    
    /**
     * Error message when signal physics validation fails.
     * Rationale: Indicates that the signal strength relationships between access points
     * violate physical laws (e.g., signal strength increases with distance), suggesting
     * measurement errors or data corruption.
     */
    private static final String ERROR_INVALID_SIGNAL_PHYSICS = "Physically impossible signal strength relationships";
    
    /**
     * Error message when no known access points are found in the database.
     * Rationale: Indicates that none of the scanned access points exist in our reference
     * database, making position calculation impossible.
     */
    private static final String ERROR_NO_KNOWN_ACCESS_POINTS = "No known access points found in database";
    
    /**
     * Error message when no access points with valid status are available.
     * Rationale: While access points were found, none have "active" or "warning" status,
     * so they cannot be used for reliable positioning calculations.
     */
    private static final String ERROR_NO_VALID_STATUS_ACCESS_POINTS = "No access points with valid status (active or warning) found";
    
    /**
     * Base error message for position calculation failures.
     * Rationale: Generic message for when the positioning algorithms cannot determine
     * a location despite having valid input data.
     */
    private static final String ERROR_POSITION_CALCULATION_FAILED = "Position calculation failed: no position could be determined";
    
    /**
     * Default value for vertical accuracy when not provided by the positioning algorithms.
     * Set to 0.0 as most algorithms in this system only calculate horizontal accuracy.
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
    
    private final WifiPositioningCalculator calculator;
    private final WifiAccessPointRepository accessPointRepository;
    private final SignalPhysicsValidator signalPhysicsValidator;
    
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
        logger.info("Calculating position for {} WiFi scan results from client {} if application {} with requestId {}", 
                request.wifiScanResults().size(), request.client(), request.application(), request.requestId());
        
        // Log full request details
        logger.debug("Full positioning request: {}", request);
        
        // Check if we have any scan results
        if (request.wifiScanResults().isEmpty()) {
            logger.warn(ERROR_NO_SCAN_RESULTS);
            WifiPositioningResponse response = WifiPositioningResponse.error(ERROR_NO_SCAN_RESULTS, request);
            logger.info("Returning error response for requestId {}: {}", request.requestId(), response);
            return response;
        }
        
        try {
            List<WifiScanResult> scanResults = request.wifiScanResults();
            
            // Check if the signal physics is valid
            if (!signalPhysicsValidator.isPhysicallyPossible(scanResults)) {
                logger.warn(ERROR_INVALID_SIGNAL_PHYSICS);
                WifiPositioningResponse response = WifiPositioningResponse.error(
                    ERROR_INVALID_SIGNAL_PHYSICS, 
                    request
                );
                logger.info("Returning error response for requestId {}: {}", request.requestId(), response);
                return response;
            }
            
            // Lookup known APs using their MAC addresses
            List<WifiAccessPoint> knownAPs = lookupKnownAccessPoints(scanResults);
            
            if (knownAPs.isEmpty()) {
                logger.warn(ERROR_NO_KNOWN_ACCESS_POINTS);
                WifiPositioningResponse response = createPositionNotFoundResponse(scanResults.size(), request);
                logger.info("Returning position not found response for requestId {}: {}", request.requestId(), response);
                return response;
            }
            
            // Filter access points by status (only "active" or "warning" status should be used)
            List<WifiAccessPoint> validStatusAPs = filterAPsByStatus(knownAPs);
            
            if (validStatusAPs.isEmpty()) {
                logger.warn(ERROR_NO_VALID_STATUS_ACCESS_POINTS);
                WifiPositioningResponse response = createPositionNotFoundResponse(scanResults.size(), request);
                logger.info("Returning position not found response for requestId {}: {}", request.requestId(), response);
                return response;
            }
            
            // Calculate position
            long startTime = System.currentTimeMillis();
            WifiPositioningCalculator.PositioningResult positioningResult = calculator.calculatePosition(scanResults, validStatusAPs);
            long calculationTime = System.currentTimeMillis() - startTime;
            
            if (positioningResult == null || positioningResult.position() == null) {
                logger.warn(ERROR_POSITION_CALCULATION_FAILED);
                WifiPositioningResponse response = createPositionNotFoundResponse(scanResults.size(), request);
                logger.info("Returning position not found response for requestId {}: {}", request.requestId(), response);
                return response;
            }

            WifiPositioningResponse response = createSuccessResponse(positioningResult, scanResults.size(), calculationTime, request, knownAPs);
            logger.info("Returning successful positioning response for requestId {}: {}", request.requestId(), response);
            return response;
            
        } catch (Exception e) {
            // For unhandled exceptions, return an error response
            logger.error("Error calculating position", e);
            WifiPositioningResponse response = WifiPositioningResponse.error(e.getMessage(), request);
            logger.info("Returning exception error response for requestId {}: {}", request.requestId(), response);
            return response;
        }
    }
    
    /**
     * Filter access points by status. Only APs with active or warning status should be used.
     * 
     * @param allAPs List of all known access points retrieved from the database
     * @return List of access points with valid status (active or warning)
     */
    private List<WifiAccessPoint> filterAPsByStatus(List<WifiAccessPoint> allAPs) {
        return allAPs.stream()
                .filter(ap -> ap.getStatus() != null && WifiAccessPoint.VALID_AP_STATUSES.contains(ap.getStatus()))
                .collect(Collectors.toList());
    }
    
    /**
     * Create a response when position calculation fails
     */
    private WifiPositioningResponse createPositionNotFoundResponse(int apCount, WifiPositioningRequest request) {
        String message = ERROR_POSITION_CALCULATION_FAILED;
        return WifiPositioningResponse.error(message, request);
    }
    
    /**
     * Lookup known access points from the repository based on MAC addresses from scan results.
     * Uses batch operation to optimize DynamoDB access.
     */
    private List<WifiAccessPoint> lookupKnownAccessPoints(List<WifiScanResult> scanResults) {
        // Extract all MAC addresses from scan results
        Set<String> macAddresses = scanResults.stream()
                .map(WifiScanResult::macAddress)
                .collect(Collectors.toSet());
        
        
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
            
            logger.info("Found {} known access points in database out of {} scan results", 
                    knownAPs.size(), scanResults.size());
            
            return knownAPs;
            
        } catch (Exception e) {
            logger.error("Error in batch lookup of access points: {}", e.getMessage(), e);
            
            // Fall back to individual lookups if batch operation fails
            logger.warn("Falling back to individual lookups due to batch operation failure");
            return fallbackIndividualLookups(macAddresses);
        }
    }
    
    /**
     * Fallback method to look up access points individually if batch operation fails.
     * This ensures the system continues to function even if the batch operation encounters an error.
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
     * Creates a success response from the positioning result.
     * This method handles:
     * - Position validation
     * - Converting position to WifiPosition
     * - Adding calculation details if requested
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
        
        // Create AP filtering information
        StringBuilder apFilteringInfo = new StringBuilder();
        Map<String, Integer> statusCounts = new HashMap<>();
        
        for (WifiAccessPoint ap : knownAPs) {
            String status = ap.getStatus() != null ? ap.getStatus() : "unknown";
            boolean used = WifiAccessPoint.VALID_AP_STATUSES.contains(status);
            
            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
            
            apFilteringInfo.append(ap.getMacAddress())
                       .append("(")
                       .append(status)
                       .append(":")
                       .append(used ? "used" : "filtered")
                       .append(") ");
        }
        
        // Build calculation info string
        StringBuilder calculationInfo = new StringBuilder();
        calculationInfo.append(positioningResult.getCalculationInfo());
        
        // Add AP filtering information
        calculationInfo.append("AP Filtering:\n");
        for (Map.Entry<String, Integer> entry : statusCounts.entrySet()) {
            calculationInfo.append("  ").append(entry.getKey())
                           .append(": ").append(entry.getValue())
                           .append(" APs, ");
            
            if (WifiAccessPoint.VALID_AP_STATUSES.contains(entry.getKey())) {
                calculationInfo.append("used in calculation");
            } else {
                calculationInfo.append("filtered out");
            }
            calculationInfo.append("\n");
        }
        
        // Add detailed AP list if requested
        calculationInfo.append("AP List: ").append(apFilteringInfo.toString()).append("\n");
        
        // Create the WifiPosition from the positioning result
        WifiPosition wifiPosition = new WifiPosition(
            position.latitude(),
            position.longitude(),
            position.altitude(),
            position.accuracy(),
            DEFAULT_VERTICAL_ACCURACY,
            position.confidence(),
            methodsUsed,
            apCount,
            calculationTime
        );
        
        return WifiPositioningResponse.success(
            request,
            wifiPosition,
            calculationInfo.toString()
        );
    }

    private WifiPositioningResponse createErrorResponse(String message, WifiPositioningRequest request) {
        // Implementation of createErrorResponse method
        throw new UnsupportedOperationException("Method not implemented");
    }
} 