package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.algorithm.util.GDOPCalculator;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.springframework.stereotype.Component;
import org.apache.commons.math3.linear.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * Implementation of the Trilateration positioning algorithm.
 * 
 * This algorithm implements geometric positioning using the intersection of distance spheres
 * from multiple access points. It uses least squares optimization to solve overdetermined
 * systems and provides high accuracy when AP geometry is favorable.
 * 
 * ALGORITHM OVERVIEW:
 * 1. Validates input and filters valid access points
 * 2. Calculates distances from RSSI using frequency-dependent path loss models
 * 3. Converts geographic coordinates to local Cartesian system
 * 4. Applies least squares trilateration for position estimation
 * 5. Calculates accuracy and confidence with GDOP analysis
 * 
 * MATHEMATICAL FOUNDATION:
 * 
 * 1. Distance Estimation from RSSI:
 *    Free-Space Path Loss: FSPL(f,d) = 20×log₁₀(4πdf/c)
 *    Log-Distance Model: RSSI(d) = RSSI₀ - 10×n×log₁₀(d/d₀)
 *    Where: f = frequency, d = distance, c = speed of light, n = path loss exponent
 * 
 * 2. Trilateration Mathematics:
 *    For each AP i: ||p - pᵢ||² = dᵢ²
 *    Linearized system: Ax = b
 *    Where A = 2[(p₁-pₙ)ᵀ, (p₂-pₙ)ᵀ, ..., (pₙ₋₁-pₙ)ᵀ]
 *    And b = [||p₁||² - ||pₙ||² - d₁² + dₙ², ...]
 *    Solution: x = (AᵀA)⁻¹Aᵀb using QR decomposition
 * 
 * 3. Geometric Dilution of Precision (GDOP):
 *    GDOP = √(trace((HᵀH)⁻¹))
 *    Where H is the geometry matrix containing unit vectors from position to each AP
 *    Lower GDOP values indicate better geometric distribution of access points
 * 
 * ACADEMIC REFERENCES:
 * - Foy, W. "Position-location solutions by Taylor-series estimation" (1976)
 * - Torrieri, D. "Statistical Theory of Passive Location Systems" (1984)
 * - Rappaport, T. "Wireless Communications: Principles and Practice" (2002)
 * - Kaplan, E. "Understanding GPS: Principles and Applications" (2006)
 * 
 * USE CASES:
 * - Ideal for 3+ APs with good geometric distribution
 * - Performs well with strong to medium signal strengths (-40 to -80 dBm)
 * - Effective in environments where signal propagation follows predictable patterns
 * - Best accuracy achieved when APs form a well-distributed triangle or polygon
 * 
 * STRENGTHS:
 * - Mathematically rigorous approach based on geometric principles
 * - Handles overdetermined systems (4+ APs) robustly
 * - Provides realistic accuracy estimates through GDOP analysis
 * - Uses frequency-dependent path loss modeling for better distance estimates
 * - Robust numerical implementation using QR decomposition
 * 
 * LIMITATIONS:
 * - Requires minimum 3 APs for calculation
 * - Sensitive to geometric distribution of APs (poor for collinear arrangements)
 * - Performance degrades significantly with very weak signals (< -90 dBm)
 * - Assumes relatively stable signal propagation environment
 * - Less effective in environments with significant multipath interference
 */
@Component
public class TrilaterationAlgorithm implements PositioningAlgorithm {

    // ============================================================================
    // FUNDAMENTAL PHYSICAL CONSTANTS
    // ============================================================================
    
    /**
     * Earth's radius in meters for geographic distance calculations.
     * Used in Haversine formula and coordinate conversions.
     * Value: WGS84 mean radius = 6,371,000 meters
     * Source: International Earth Rotation and Reference Systems Service (IERS)
     */
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    
    /**
     * Conversion factor from degrees latitude to meters.
     * Approximation valid for small geographic areas (< 100 km).
     * Value: 1 degree latitude ≈ 111,000 meters (111.32 km at equator)
     * Derivation: Earth circumference (40,075 km) ÷ 360 degrees
     */
    private static final double LATITUDE_TO_METERS = 111000.0;
    
    /**
     * Base conversion factor from degrees longitude to meters at equator.
     * Actual conversion varies with latitude: lonToMeters = BASE × cos(latitude)
     * Value: 1 degree longitude ≈ 111,000 meters at equator
     * Note: This is adjusted by latitude cosine for accurate local conversions
     */
    private static final double LONGITUDE_TO_METERS_BASE = 111000.0;
    
    /**
     * Speed of light in vacuum (meters per second).
     * Fundamental physical constant used in electromagnetic wave propagation.
     * Value: c = 299,792,458 m/s (exact by definition)
     * Used in free-space path loss calculations: FSPL = 20×log₁₀(4πdf/c)
     */
    private static final double SPEED_OF_LIGHT_MPS = 299792458.0;

    // ============================================================================
    // SIGNAL PROPAGATION MODEL CONSTANTS
    // ============================================================================
    
    /**
     * Reference distance for path loss measurements (meters).
     * Standard reference point for signal strength calibration.
     * Value: 1.0 meter - industry standard for WiFi propagation models
     * Usage: All distance calculations are relative to this reference point
     */
    private static final double REFERENCE_DISTANCE_METERS = 1.0;
    
    /**
     * Path loss exponent for typical indoor environments.
     * Describes how signal strength decreases with distance in dB/decade.
     * Value: 3.0 - representative of indoor environments with obstacles
     * Range: Free space = 2.0, Indoor = 2.5-4.0, Dense urban = 3.5-5.0
     * Source: ITU-R P.1238 indoor propagation model recommendations
     */
    private static final double PATH_LOSS_EXPONENT = 3.0;
    
    /**
     * Reduced path loss exponent for strong signal scenarios.
     * Strong signals experience less environmental attenuation variation.
     * Value: 2.5 - closer to free-space propagation for high SNR scenarios
     * Rationale: Strong signals indicate line-of-sight or minimal obstruction
     */
    private static final double STRONG_SIGNAL_PATH_LOSS_EXPONENT = 2.5;
    
    /**
     * Frequency conversion factor from MHz to Hz.
     * Used to convert frequency from WiFi standard units (MHz) to SI units (Hz).
     * Value: 1 MHz = 10⁶ Hz
     * Required for accurate free-space path loss calculations
     */
    private static final double MHZ_TO_HZ_CONVERSION = 1e6;
    
    /**
     * Free-space path loss calculation constant.
     * Mathematical constant: 4π used in Friis transmission equation.
     * Value: 4π ≈ 12.566 (exact: 4 × 3.14159265...)
     * Formula context: FSPL = 20×log₁₀(4πdf/c)
     */
    private static final double FOUR_PI = 4.0 * Math.PI;
    
    /**
     * Logarithmic scaling factor for decibel calculations.
     * Converts power ratios to decibel scale: dB = 10×log₁₀(P₁/P₂)
     * Value: 10.0 - standard decibel conversion factor for power
     * Used in: Path loss = 10×n×log₁₀(d/d₀) calculations
     */
    private static final double DECIBEL_CONVERSION_FACTOR = 10.0;
    
    /**
     * Power-to-dB conversion factor for voltage/field strength.
     * For electromagnetic field calculations: dB = 20×log₁₀(E₁/E₂)
     * Value: 20.0 - used in free-space path loss formula
     * Rationale: Power ∝ voltage², so voltage dB = 2 × power dB = 20×log₁₀
     */
    private static final double FIELD_STRENGTH_DB_FACTOR = 20.0;

    // ============================================================================
    // SIGNAL QUALITY THRESHOLDS
    // ============================================================================
    
    /**
     * Signal strength threshold defining "strong" signals (dBm).
     * Signals above this threshold have high reliability for distance estimation.
     * Value: -65 dBm - typical for close proximity WiFi with good SNR
     * Impact: Uses reduced path loss exponent for better accuracy
     * Source: IEEE 802.11 signal quality classifications
     */
    private static final double STRONG_SIGNAL_THRESHOLD_DBM = -65.0;
    
    /**
     * Signal strength threshold defining "weak" signals (dBm).
     * Signals below this threshold have reduced reliability and accuracy.
     * Value: -80 dBm - typical boundary for reliable WiFi communication
     * Impact: Affects confidence calculation and accuracy estimation
     * Context: Weak signals experience higher measurement uncertainty
     */
    private static final double WEAK_SIGNAL_THRESHOLD_DBM = -80.0;
    
    /**
     * Signal strength threshold for confidence calculation adjustments (dBm).
     * Intermediate threshold between strong and weak signal categories.
     * Value: -75 dBm - midpoint for confidence scaling algorithms
     * Usage: Determines confidence calculation methodology
     */
    private static final double CONFIDENCE_THRESHOLD_DBM = -75.0;

    // ============================================================================
    // DISTANCE ESTIMATION CONSTRAINTS
    // ============================================================================
    
    /**
     * Minimum allowable distance estimate (meters).
     * Physical constraint preventing unrealistic close-range estimates.
     * Value: 1.0 meter - practical minimum for WiFi positioning
     * Rationale: Accounts for AP antenna patterns and near-field effects
     */
    private static final double MIN_DISTANCE_METERS = 1.0;
    
    /**
     * Maximum allowable distance estimate (meters).
     * Constraint preventing unrealistic long-range estimates from weak signals.
     * Value: 100.0 meters - typical maximum effective range for indoor WiFi
     * Rationale: Beyond this range, signal-to-noise ratio becomes unreliable
     */
    private static final double MAX_DISTANCE_METERS = 100.0;

    // ============================================================================
    // ACCURACY ESTIMATION CONSTANTS
    // ============================================================================
    
    /**
     * Minimum achievable positioning accuracy (meters).
     * Physical limit based on WiFi signal characteristics and AP precision.
     * Value: 1.0 meter - represents best-case trilateration accuracy
     * Rationale: Signal wavelength (~12 cm at 2.4 GHz) and measurement precision
     */
    private static final double MIN_ACCURACY_METERS = 1.0;
    
    /**
     * Maximum accuracy estimate for strong signal scenarios (meters).
     * Upper bound for positioning accuracy with high-quality signals.
     * Value: 5.0 meters - typical indoor positioning performance ceiling
     * Source: IEEE 802.11 indoor positioning accuracy standards
     */
    private static final double MAX_ACCURACY_STRONG_SIGNALS_METERS = 5.0;
    
    /**
     * Base accuracy estimate for strong signal calculations (meters).
     * Starting point for accuracy estimation with high SNR measurements.
     * Value: 3.0 meters - empirical midpoint for strong signal performance
     * Usage: Foundation for GDOP-adjusted accuracy calculations
     */
    private static final double BASE_ACCURACY_STRONG_SIGNALS_METERS = 3.0;
    
    /**
     * Maximum accuracy estimate for any positioning scenario (meters).
     * Global upper bound preventing unrealistic accuracy claims.
     * Value: 50.0 meters - conservative limit for indoor WiFi positioning
     * Rationale: Beyond this accuracy, positioning becomes less useful
     */
    private static final double MAX_ACCURACY_ANY_SCENARIO_METERS = 50.0;

    // ============================================================================
    // CONFIDENCE CALCULATION CONSTANTS
    // ============================================================================
    
    /**
     * Minimum confidence value for positioning results.
     * Lower bound preventing unrealistically pessimistic estimates.
     * Value: 0.55 - represents minimum useful confidence for applications
     * Academic principle: Confidence should reflect realistic uncertainty
     */
    private static final double MIN_CONFIDENCE = 0.55;
    
    /**
     * Maximum confidence value for positioning results.
     * Upper bound preventing overconfident estimates even in ideal conditions.
     * Value: 0.85 - leaves room for inherent measurement uncertainty
     * Rationale: Perfect confidence (1.0) is theoretically impossible
     */
    private static final double MAX_CONFIDENCE = 0.85;
    
    /**
     * High confidence threshold for strong signal scenarios.
     * Target confidence level for optimal positioning conditions.
     * Value: 0.8 - represents high reliability for location-based applications
     * Usage: Confidence scaling reference for algorithm selection
     */
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;
    
    /**
     * Confidence cap for weak signal scenarios.
     * Maximum confidence allowed when signal quality is poor.
     * Value: 0.58 - reflects increased uncertainty with weak signals
     * Academic principle: Confidence should scale with measurement quality
     */
    private static final double WEAK_SIGNAL_CONFIDENCE_CAP = 0.58;

    // ============================================================================
    // WEIGHTING AND CALCULATION PARAMETERS
    // ============================================================================
    
    /**
     * Signal strength divisor for exponential weighting calculations.
     * Used in distance-based weighting: weight = 1/distance^WEIGHTING_EXPONENT
     * Value: 20.0 - provides appropriate dynamic range for WiFi signals
     * Mathematical context: Balances strong vs. weak signal contributions
     */
    private static final double SIGNAL_WEIGHTING_DIVISOR = 20.0;
    
    /**
     * Exponent for inverse distance weighting in altitude calculations.
     * Controls how strongly distance affects altitude weight contribution.
     * Value: 1.0 - linear inverse relationship (weight = 1/distance)
     * Academic basis: Standard inverse distance weighting (IDW) methodology
     */
    private static final double INVERSE_DISTANCE_WEIGHT_EXPONENT = 1.0;
    
    /**
     * Geographic coordinate validation: maximum latitude (degrees).
     * Northern hemisphere boundary for coordinate validation.
     * Value: 90.0 degrees - North Pole (geographic maximum)
     * Usage: Prevents invalid coordinate calculations
     */
    private static final double MAX_LATITUDE_DEGREES = 90.0;
    
    /**
     * Geographic coordinate validation: minimum latitude (degrees).
     * Southern hemisphere boundary for coordinate validation.
     * Value: -90.0 degrees - South Pole (geographic minimum)
     * Usage: Prevents invalid coordinate calculations
     */
    private static final double MIN_LATITUDE_DEGREES = -90.0;
    
    /**
     * Geographic coordinate validation: maximum longitude (degrees).
     * Eastern hemisphere boundary for coordinate validation.
     * Value: 180.0 degrees - International Date Line (geographic maximum)
     * Usage: Prevents invalid coordinate calculations
     */
    private static final double MAX_LONGITUDE_DEGREES = 180.0;
    
    /**
     * Geographic coordinate validation: minimum longitude (degrees).
     * Western hemisphere boundary for coordinate validation.  
     * Value: -180.0 degrees - International Date Line (geographic minimum)
     * Usage: Prevents invalid coordinate calculations
     */
    private static final double MIN_LONGITUDE_DEGREES = -180.0;
    
    /**
     * Fallback coordinate: equatorial latitude (degrees).
     * Used when latitude calculation fails due to numerical issues.
     * Value: 0.0 degrees - Equator (neutral geographic reference)
     * Rationale: Minimizes geographic bias in error cases
     */
    private static final double EQUATOR_LATITUDE_DEGREES = 0.0;
    
    /**
     * Fallback coordinate: prime meridian longitude (degrees).
     * Used when longitude calculation fails due to numerical issues.
     * Value: 0.0 degrees - Prime Meridian (neutral geographic reference)
     * Rationale: Minimizes geographic bias in error cases
     */
    private static final double PRIME_MERIDIAN_LONGITUDE_DEGREES = 0.0;
    
    /**
     * Weight factor for signal quality in confidence calculations.
     * Determines relative importance of signal strength vs. geometric factors.
     * Value: 0.7 - emphasizes signal quality as primary confidence driver
     * Academic basis: Signal quality most directly correlates with accuracy
     */
    private static final double CONFIDENCE_SIGNAL_WEIGHT = 0.7;
    
    /**
     * Weight factor for AP count in confidence calculations.
     * Balances signal quality with geometric redundancy benefits.
     * Value: 0.3 - secondary importance to signal quality
     * Rationale: More APs provide redundancy but signal quality dominates
     */
    private static final double CONFIDENCE_AP_COUNT_WEIGHT = 0.3;

    // ============================================================================
    // ALGORITHM CONFIGURATION CONSTANTS  
    // ============================================================================
    
    /**
     * Minimum number of access points required for trilateration.
     * Mathematical requirement: trilateration needs 3+ APs for 2D positioning.
     * Value: 3 - geometric minimum for solving 2D position (x,y)
     * Academic basis: System of equations requires n ≥ dimension + 1
     */
    private static final int MIN_AP_COUNT_FOR_TRILATERATION = 3;
    
    /**
     * Maximum AP count for scaling calculations.
     * Beyond this count, additional APs provide diminishing returns.
     * Value: 8 - empirical point where geometric redundancy plateaus
     * Usage: Confidence and accuracy scaling normalization
     */
    private static final int MAX_AP_COUNT_FOR_SCALING = 8;
    
    /**
     * Algorithm identifier for the positioning framework.
     * Used in algorithm selection, logging, and result reporting.
     * Value: "trilateration" - indicates geometric positioning method
     */
    private static final String ALGORITHM_NAME = "trilateration";

    /**
     * Helper class to store coordinate calculations for each AP.
     * Used to cache intermediate results and improve performance.
     */
    private static class CachedCoordinates {
        final double x;
        final double y;
        final double distance;

        CachedCoordinates(double x, double y, double distance) {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }

    /**
     * Calculates position using trilateration with least squares optimization.
     * 
     * This method orchestrates the complete trilateration positioning process:
     * 1. Input validation and preprocessing
     * 2. Distance calculation and coordinate conversion
     * 3. Trilateration computation with fallback strategies
     * 4. GDOP analysis and accuracy estimation
     * 5. Confidence calculation based on multiple factors
     *
     * @param wifiScan List of WiFi scan results containing signal strengths
     * @param knownAPs List of known access points with their locations
     * @return Calculated position with confidence metrics, or null if calculation fails
     */
    @Override
    public Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        // Validate input parameters
        if (!isValidInput(wifiScan, knownAPs)) {
            return null;
        }

        // Create AP lookup map and filter valid scans
        Map<String, WifiAccessPoint> apMap = createApLookupMap(knownAPs);
        List<WifiScanResult> validScans = filterValidScans(wifiScan, apMap);
        
        if (validScans.size() < 3) {
            return null; // Need at least 3 APs for trilateration
        }

        // Find reference point and calculate conversion factors
        ReferencePoint referencePoint = determineReferencePoint(validScans, apMap);
        CoordinateConverter converter = createCoordinateConverter(referencePoint);

        // Calculate distances and convert to local coordinates
        CoordinateCache coordinateCache = calculateCoordinatesAndDistances(validScans, apMap, referencePoint, converter);
        
        // Calculate weighted center for fallback positioning
        WeightedCenter weightedCenter = calculateWeightedCenter(validScans, apMap);
        
        // Get average signal strength for accuracy and confidence calculations
        double avgSignalStrength = coordinateCache.getAverageSignalStrength();

        // Apply trilateration algorithm
        double[] localPosition = performTrilateration(validScans, coordinateCache, weightedCenter, referencePoint, converter);

        // Calculate GDOP for geometry quality assessment
        double gdop = calculateGDOP(coordinateCache, localPosition);
        double gdopFactor = GDOPCalculator.calculateGDOPFactor(gdop);

        // Convert back to geographic coordinates
        GeographicPosition geographicPosition = convertToGeographicCoordinates(localPosition, referencePoint, converter);
        
        // Validate position for numerical correctness
        GeographicPosition validatedPosition = validatePositionForNumericalCorrectness(geographicPosition);

        // Calculate altitude using weighted average
        double altitude = calculateWeightedAltitude(validScans, apMap, coordinateCache);

        // Calculate final accuracy and confidence
        double accuracy = calculateAccuracy(avgSignalStrength, gdopFactor, coordinateCache.getAverageDistance());
        double confidence = calculateConfidence(avgSignalStrength, validScans.size(), gdopFactor);

        return new Position(
            validatedPosition.latitude,
            validatedPosition.longitude,
            altitude,
            accuracy,
            confidence
        );
    }

    /**
     * Validates input parameters for trilateration.
     * Ensures that both WiFi scan results and known AP lists are valid and non-empty.
     * 
     * @param wifiScan List of WiFi scan results to validate
     * @param knownAPs List of known access points to validate
     * @return true if inputs are valid, false otherwise
     */
    private boolean isValidInput(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        return wifiScan != null && !wifiScan.isEmpty() && 
               knownAPs != null && !knownAPs.isEmpty() &&
               wifiScan.size() >= 3 && knownAPs.size() >= 3;
    }

    /**
     * Creates a lookup map from known access points for efficient access during calculations.
     * Uses concurrent map for thread-safe operations during parallel processing.
     * 
     * @param knownAPs List of known access points
     * @return Map with MAC addresses as keys and WifiAccessPoint objects as values
     */
    private Map<String, WifiAccessPoint> createApLookupMap(List<WifiAccessPoint> knownAPs) {
        return knownAPs.parallelStream()
            .collect(Collectors.toConcurrentMap(
                WifiAccessPoint::getMacAddress,
                ap -> ap,
                (ap1, ap2) -> ap1 // In case of duplicates, keep the first one
            ));
    }

    /**
     * Filters scan results to only include APs with known locations.
     * 
     * @param wifiScan List of WiFi scan results
     * @param apMap Map of known AP locations
     * @return List of valid scan results that have corresponding AP locations
     */
    private List<WifiScanResult> filterValidScans(List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        return wifiScan.parallelStream()
            .filter(scan -> apMap.containsKey(scan.macAddress()))
            .collect(Collectors.toList());
    }

    /**
     * Determines the reference point for coordinate conversion.
     * Uses the AP with the strongest signal as the reference to minimize conversion errors.
     * 
     * @param validScans List of valid WiFi scan results
     * @param apMap Map of known AP locations
     * @return ReferencePoint containing reference AP information
     */
    private ReferencePoint determineReferencePoint(List<WifiScanResult> validScans, Map<String, WifiAccessPoint> apMap) {
        WifiScanResult strongestSignalScan = validScans.stream()
            .max(java.util.Comparator.comparingDouble(WifiScanResult::signalStrength))
            .orElse(validScans.get(0));
        
        WifiAccessPoint refAP = apMap.get(strongestSignalScan.macAddress());
        return new ReferencePoint(refAP.getLatitude(), refAP.getLongitude());
    }

    /**
     * Creates coordinate converter for geographic to Cartesian conversion.
     * 
     * @param referencePoint Reference point for coordinate system origin
     * @return CoordinateConverter with appropriate scaling factors
     */
    private CoordinateConverter createCoordinateConverter(ReferencePoint referencePoint) {
        double latToMeters = LATITUDE_TO_METERS;
        double lonToMeters = LONGITUDE_TO_METERS_BASE * Math.cos(Math.toRadians(referencePoint.latitude));
        return new CoordinateConverter(latToMeters, lonToMeters);
    }

    /**
     * Calculates distances and converts coordinates to local Cartesian system.
     * 
     * @param validScans List of valid WiFi scan results
     * @param apMap Map of known AP locations
     * @param referencePoint Reference point for coordinate conversion
     * @param converter Coordinate conversion utility
     * @return CoordinateCache containing all calculated coordinates and distances
     */
    private CoordinateCache calculateCoordinatesAndDistances(List<WifiScanResult> validScans, 
                                                           Map<String, WifiAccessPoint> apMap,
                                                           ReferencePoint referencePoint,
                                                           CoordinateConverter converter) {
        Map<String, CachedCoordinates> coordinateMap = new ConcurrentHashMap<>();
        DoubleAdder totalSignalStrength = new DoubleAdder();
        DoubleAdder totalDistance = new DoubleAdder();

        // Process scans in parallel for efficiency
        validScans.parallelStream().forEach(scan -> {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            double distance = calculateDistanceFromRSSI(scan.signalStrength(), scan.frequency());
            
            // Convert to local coordinates
            double x = (ap.getLatitude() - referencePoint.latitude) * converter.latToMeters;
            double y = (ap.getLongitude() - referencePoint.longitude) * converter.lonToMeters;
            
            coordinateMap.put(scan.macAddress(), new CachedCoordinates(x, y, distance));
            totalSignalStrength.add(scan.signalStrength());
            totalDistance.add(distance);
        });

        return new CoordinateCache(coordinateMap, totalSignalStrength.doubleValue(), 
                                 totalDistance.doubleValue(), validScans.size());
    }

    /**
     * Calculates weighted center of access points for fallback positioning.
     * 
     * @param validScans List of valid WiFi scan results
     * @param apMap Map of known AP locations
     * @return WeightedCenter containing center coordinates and bounds
     */
    private WeightedCenter calculateWeightedCenter(List<WifiScanResult> validScans, Map<String, WifiAccessPoint> apMap) {
        double minLat = Double.MAX_VALUE;
        double maxLat = Double.MIN_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = Double.MIN_VALUE;
        double centerLat = 0;
        double centerLon = 0;
        double totalWeight = 0;
        
        // Calculate center of gravity of APs, weighted by signal strength
        for (WifiScanResult scan : validScans) {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            minLat = Math.min(minLat, ap.getLatitude());
            maxLat = Math.max(maxLat, ap.getLatitude());
            minLon = Math.min(minLon, ap.getLongitude());
            maxLon = Math.max(maxLon, ap.getLongitude());
            
            // Use exponential weighting for stronger signals
            double weight = Math.pow(10, scan.signalStrength() / SIGNAL_WEIGHTING_DIVISOR);
            centerLat += ap.getLatitude() * weight;
            centerLon += ap.getLongitude() * weight;
            totalWeight += weight;
        }
        
        centerLat /= totalWeight;
        centerLon /= totalWeight;
        
        return new WeightedCenter(centerLat, centerLon, minLat, maxLat, minLon, maxLon);
    }

    /**
     * Performs trilateration calculation with fallback to weighted centroid.
     * 
     * @param validScans List of valid WiFi scan results
     * @param coordinateCache Cached coordinates and distances
     * @param weightedCenter Weighted center for fallback
     * @param referencePoint Reference point for coordinate conversion
     * @param converter Coordinate conversion utility
     * @return Local position coordinates [x, y]
     */
    private double[] performTrilateration(List<WifiScanResult> validScans,
                                        CoordinateCache coordinateCache,
                                        WeightedCenter weightedCenter,
                                        ReferencePoint referencePoint,
                                        CoordinateConverter converter) {
        // Apply trilateration using least squares method
        double[] position = leastSquaresTrilateration(validScans, coordinateCache.coordinateMap);
        
        // If trilateration fails, fall back to weighted centroid method
        if (position == null || Double.isNaN(position[0]) || Double.isNaN(position[1]) ||
            Double.isInfinite(position[0]) || Double.isInfinite(position[1])) {
            position = new double[]{
                (weightedCenter.centerLat - referencePoint.latitude) * converter.latToMeters,
                (weightedCenter.centerLon - referencePoint.longitude) * converter.lonToMeters
            };
        }
        
        return position;
    }

    /**
     * Calculates GDOP (Geometric Dilution of Precision) for the estimated position.
     * 
     * GDOP quantifies how the geometric arrangement of access points affects 
     * positioning accuracy. It's derived from satellite positioning theory and
     * applied to WiFi trilateration systems.
     * 
     * Mathematical Foundation:
     * 1. Geometry Matrix H: Contains unit vectors from estimated position to each AP
     *    H = [u₁ᵀ; u₂ᵀ; ...; uₙᵀ] where uᵢ = (pᵢ - p̂) / ||pᵢ - p̂||
     *    - pᵢ = position of AP i
     *    - p̂ = estimated user position
     *    - uᵢ = unit vector from position to AP i
     * 
     * 2. Covariance Matrix: Q = (HᵀH)⁻¹
     *    Represents positioning uncertainty in each coordinate direction
     * 
     * 3. GDOP Calculation: GDOP = √(trace(Q)) = √(Qₓₓ + Qᵧᵧ)
     *    Where Qₓₓ and Qᵧᵧ are diagonal elements representing x,y variances
     * 
     * GDOP Interpretation:
     * - GDOP < 2.0: Excellent geometry (strong triangulation)
     * - 2.0 ≤ GDOP < 4.0: Good geometry (adequate triangulation)  
     * - 4.0 ≤ GDOP < 6.0: Fair geometry (moderate triangulation)
     * - GDOP ≥ 6.0: Poor geometry (weak triangulation)
     * - Infinite GDOP: Singular geometry (collinear APs)
     * 
     * Physical Meaning:
     * GDOP reflects how small measurement errors propagate to position errors.
     * Lower GDOP means better geometric distribution of reference points.
     * 
     * Academic References:
     * - Kaplan, E. "Understanding GPS: Principles and Applications" (2006)
     * - Langley, R. "Dilution of Precision" GPS World (1999)
     * - Hofmann-Wellenhof et al. "GNSS - Global Navigation Satellite Systems" (2008)
     * 
     * @param coordinateCache Cached coordinates for GDOP calculation
     * @param localPosition Estimated position in local coordinate system (meters)
     * @return GDOP value indicating geometric quality (lower is better)
     */
    private double calculateGDOP(CoordinateCache coordinateCache, double[] localPosition) {
        // Prepare coordinates for GDOP calculation using 2D trilateration
        double[][] coordinates = new double[coordinateCache.coordinateMap.size()][2];
        int i = 0;
        for (CachedCoordinates coord : coordinateCache.coordinateMap.values()) {
            coordinates[i][0] = coord.x;
            coordinates[i][1] = coord.y;
            i++;
        }
        
        // Calculate GDOP using academic-standard algorithm
        // Note: Using 2D mode as trilateration primarily operates in horizontal plane
        return GDOPCalculator.calculateGDOP(coordinates, localPosition, false);
    }

    /**
     * Converts local coordinates back to geographic coordinates.
     * 
     * @param localPosition Position in local Cartesian coordinates
     * @param referencePoint Reference point for coordinate conversion
     * @param converter Coordinate conversion utility
     * @return GeographicPosition with latitude and longitude
     */
    private GeographicPosition convertToGeographicCoordinates(double[] localPosition,
                                                            ReferencePoint referencePoint,
                                                            CoordinateConverter converter) {
        double latitude = referencePoint.latitude + localPosition[0] / converter.latToMeters;
        double longitude = referencePoint.longitude + localPosition[1] / converter.lonToMeters;
        return new GeographicPosition(latitude, longitude);
    }

    /**
     * Validates position for numerical correctness without artificial constraints.
     * Only checks for NaN, infinity, and other numerical issues.
     * Does NOT impose artificial geographic bounds or mix with target positions.
     * 
     * This method ensures mathematical validity while preserving the computed position's
     * geographic accuracy. It only intervenes in cases of numerical failure.
     * 
     * Academic Principle: Position validation should not artificially constrain
     * results to predefined boundaries, as this corrupts the mathematical solution.
     * 
     * @param position Original geographic position from trilateration
     * @return Position with numerical issues corrected, or original if valid
     */
    private GeographicPosition validatePositionForNumericalCorrectness(GeographicPosition position) {
        double latitude = position.latitude;
        double longitude = position.longitude;
        
        // Only check for numerical validity, not arbitrary bounds
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)) {
            latitude = EQUATOR_LATITUDE_DEGREES; // Fallback to equator if calculation failed
        }
        
        if (Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            longitude = PRIME_MERIDIAN_LONGITUDE_DEGREES; // Fallback to prime meridian if calculation failed
        }
        
        // Ensure coordinates are within valid geographic ranges (physical Earth limits)
        latitude = Math.max(MIN_LATITUDE_DEGREES, Math.min(MAX_LATITUDE_DEGREES, latitude));
        longitude = Math.max(MIN_LONGITUDE_DEGREES, Math.min(MAX_LONGITUDE_DEGREES, longitude));
        
        return new GeographicPosition(latitude, longitude);
    }

    /**
     * Calculates weighted altitude using inverse distance weighting methodology.
     * 
     * This method applies the Inverse Distance Weighting (IDW) algorithm
     * commonly used in spatial interpolation and geographic information systems.
     * 
     * Mathematical Foundation:
     * - Weight(i) = 1 / distance(i)^p, where p is the power parameter
     * - Weighted Average = Σ(value(i) × weight(i)) / Σ(weight(i))
     * - Uses p = 1.0 for linear inverse relationship
     * 
     * Academic References:
     * - Shepard, D. "A two-dimensional interpolation function..." (1968)
     * - Li, J. & Heap, A.D. "Spatial interpolation methods applied..." (2014)
     * 
     * @param validScans List of valid WiFi scan results
     * @param apMap Map of known AP locations with altitude data
     * @param coordinateCache Cached coordinates for efficient distance lookup
     * @return Weighted average altitude in meters, or 0.0 if no altitude data available
     */
    private double calculateWeightedAltitude(List<WifiScanResult> validScans,
                                           Map<String, WifiAccessPoint> apMap,
                                           CoordinateCache coordinateCache) {
        DoubleAdder weightedAltitude = new DoubleAdder();
        DoubleAdder weightSum = new DoubleAdder();
        
        // Process altitude calculation in parallel for efficiency
        validScans.parallelStream().forEach(scan -> {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            CachedCoordinates coords = coordinateCache.coordinateMap.get(scan.macAddress());
            
            // Only contribute to altitude calculation if the AP has altitude data
            if (ap.getAltitude() != null && coords != null) {
                // Apply inverse distance weighting: weight = 1/distance^p
                double weight = 1.0 / Math.pow(coords.distance, INVERSE_DISTANCE_WEIGHT_EXPONENT);
                weightedAltitude.add(ap.getAltitude() * weight);
                weightSum.add(weight);
            }
        });
        
        return weightSum.doubleValue() > 0 ? 
            weightedAltitude.doubleValue() / weightSum.doubleValue() : 0.0;
    }

    /**
     * Calculates distance from RSSI using the log-distance path loss model.
     * 
     * This method implements the academic standard for RF signal propagation:
     * 1. Free-space path loss: FSPL(f,d) = 20×log₁₀(4πdf/c)
     * 2. Log-distance model: RSSI(d) = RSSI₀ - 10×n×log₁₀(d/d₀)
     * 3. Combined model: d = d₀ × 10^((RSSI₀ - RSSI)/(10×n))
     * 
     * Mathematical Foundation:
     * - FSPL at reference distance provides frequency-dependent calibration
     * - Path loss exponent accounts for environmental characteristics
     * - Distance constraints ensure realistic estimates within WiFi range
     * 
     * Academic References:
     * - Rappaport, T. "Wireless Communications: Principles and Practice"
     * - ITU-R P.1238 indoor propagation model
     * - IEEE 802.11 working group propagation studies
     *
     * @param rssi Signal strength in dBm
     * @param frequency Frequency in MHz (WiFi standard units)
     * @return Estimated distance in meters
     */
    private double calculateDistanceFromRSSI(double rssi, int frequency) {
        // Convert frequency from MHz to Hz for proper physical calculations
        double frequencyHz = frequency * MHZ_TO_HZ_CONVERSION;
        
        // Calculate wavelength using fundamental physics: λ = c/f
        double wavelength = SPEED_OF_LIGHT_MPS / frequencyHz;
        
        // Calculate reference RSSI at 1m using free-space path loss formula
        // FSPL(f,1m) = 20×log₁₀(4π×1m×f/c) = 20×log₁₀(4π/λ)
        double referenceRSSI = -FIELD_STRENGTH_DB_FACTOR * Math.log10(FOUR_PI * REFERENCE_DISTANCE_METERS / wavelength);
        
        // Select appropriate path loss exponent based on signal strength
        // Strong signals typically experience less environmental variation
        double pathLossExponent = (rssi >= STRONG_SIGNAL_THRESHOLD_DBM) ? 
                                 STRONG_SIGNAL_PATH_LOSS_EXPONENT : PATH_LOSS_EXPONENT;
        
        // Apply log-distance path loss model: d = d₀ × 10^((RSSI₀ - RSSI)/(10×n))
        double pathLoss = referenceRSSI - rssi;
        double distance = REFERENCE_DISTANCE_METERS * Math.pow(10, pathLoss / (DECIBEL_CONVERSION_FACTOR * pathLossExponent));
        
        // Apply physical constraints to prevent unrealistic distance estimates
        return Math.min(MAX_DISTANCE_METERS, Math.max(MIN_DISTANCE_METERS, distance));
    }

    /**
     * Performs least squares trilateration using linear algebra.
     * 
     * Mathematical foundation:
     * For each AP i (relative to reference AP 0), we have:
     * 2*(x_i - x_0)*x + 2*(y_i - y_0)*y = (x_i² + y_i²) - (x_0² + y_0²) + (r_0² - r_i²)
     * 
     * This creates a system of linear equations Ax = b where:
     * - A is the coefficient matrix [2*(x_i - x_0), 2*(y_i - y_0)]
     * - x is the unknown position [x, y]
     * - b is the constants vector [(x_i² + y_i²) - (x_0² + y_0²) + (r_0² - r_i²)]
     * 
     * Academic references:
     * - Foy, W. "Position-location solutions by Taylor-series estimation"
     * - Torrieri, D. "Statistical Theory of Passive Location Systems"
     * 
     * @param validScans List of WiFi scan results with corresponding coordinates
     * @param coordinateCache Map of cached coordinates for each AP
     * @return Position [x, y] in local coordinate system, or null if calculation fails
     */
    private double[] leastSquaresTrilateration(List<WifiScanResult> validScans, Map<String, CachedCoordinates> coordinateCache) {
        int n = validScans.size();
        
        if (n < 3) {
            return null;
        }
        
        // Reference point is the first AP in the scan list
        CachedCoordinates ref = coordinateCache.get(validScans.get(0).macAddress());
        
        // Create matrices for least squares calculation: Ax = b
        double[][] matrixData = new double[n-1][2];
        double[] constants = new double[n-1];
        
        // Build the linear system relative to the reference AP
        for (int i = 1; i < n; i++) {
            CachedCoordinates coords = coordinateCache.get(validScans.get(i).macAddress());
            
            // Coefficient matrix A: [2*(x_i - x_0), 2*(y_i - y_0)]
            matrixData[i-1][0] = 2.0 * (coords.x - ref.x);
            matrixData[i-1][1] = 2.0 * (coords.y - ref.y);
            
            // Constants vector b: (x_i² + y_i²) - (x_0² + y_0²) + (r_0² - r_i²)
            constants[i-1] = (coords.x * coords.x + coords.y * coords.y) - 
                            (ref.x * ref.x + ref.y * ref.y) + 
                            (ref.distance * ref.distance - coords.distance * coords.distance);
        }
        
        // Use Apache Commons Math for robust matrix operations
        RealMatrix A = new Array2DRowRealMatrix(matrixData);
        RealVector b = new ArrayRealVector(constants);
        
        try {
            // Use QR decomposition for numerical stability with overdetermined systems
            DecompositionSolver solver = new QRDecomposition(A).getSolver();
            
            if (!solver.isNonSingular()) {
                return null; // Matrix is singular, cannot solve
            }
            
            RealVector solution = solver.solve(b);
            return solution.toArray();
        } catch (Exception e) {
            // Matrix operations failed (numerical issues, etc.)
            return null;
        }
    }

    @Override
    public double getConfidence() {
        return MAX_CONFIDENCE;
    }

    @Override
    public String getName() {
        return ALGORITHM_NAME;
    }
    
    /**
     * Weight constants from the algorithm selection framework.
     * These reflect the strengths and weaknesses of the Trilateration algorithm:
     * - Only effective with 3+ APs (optimized for this scenario)
     * - Highly dependent on signal quality (works best with strong signals)
     * - Very sensitive to geometric quality (GDOP)
     * - Modest performance with varying signal distributions
     */
    // AP Count weights from framework document
    private static final double TRILATERATION_SINGLE_AP_WEIGHT = 0.0;      // Not applicable for single AP
    private static final double TRILATERATION_TWO_APS_WEIGHT = 0.0;        // Not applicable for two APs
    private static final double TRILATERATION_THREE_APS_WEIGHT = 1.0;      // Optimal for three APs (exact solution)
    private static final double TRILATERATION_FOUR_PLUS_APS_WEIGHT = 0.8;  // Good for overdetermined systems
    
    // Signal quality multipliers from framework document
    private static final double TRILATERATION_STRONG_SIGNAL_MULTIPLIER = 1.1;  // Better with strong signals
    private static final double TRILATERATION_MEDIUM_SIGNAL_MULTIPLIER = 0.8;  // Reduced with medium signals
    private static final double TRILATERATION_WEAK_SIGNAL_MULTIPLIER = 0.3;    // Major reduction for weak signals
    private static final double TRILATERATION_VERY_WEAK_SIGNAL_MULTIPLIER = 0.0; // ×0.0 for very weak signals
    
    // Geometric quality multipliers from framework document
    private static final double TRILATERATION_EXCELLENT_GDOP_MULTIPLIER = 1.3; // Significant boost for excellent geometry
    private static final double TRILATERATION_GOOD_GDOP_MULTIPLIER = 0.9;      // Slight reduction for good geometry
    private static final double TRILATERATION_FAIR_GDOP_MULTIPLIER = 0.6;      // Significant reduction for fair geometry
    private static final double TRILATERATION_POOR_GDOP_MULTIPLIER = 0.3;      // Major reduction for poor geometry
    private static final double TRILATERATION_COLLINEAR_MULTIPLIER = 0.0;      // Zero weight for collinear APs - trilateration is impossible
    
    // Signal distribution multipliers from framework document
    private static final double TRILATERATION_UNIFORM_SIGNALS_MULTIPLIER = 1.1;  // Better with uniform signals
    private static final double TRILATERATION_MIXED_SIGNALS_MULTIPLIER = 0.8;    // Reduced with mixed signals
    private static final double TRILATERATION_SIGNAL_OUTLIERS_MULTIPLIER = 0.5;  // Significant reduction with outliers
    
    @Override
    public double getBaseWeight(APCountFactor factor) {
        switch (factor) {
            case SINGLE_AP:
                return TRILATERATION_SINGLE_AP_WEIGHT;      // Not applicable for single AP
            case TWO_APS:
                return TRILATERATION_TWO_APS_WEIGHT;        // Not applicable for two APs
            case THREE_APS:
                return TRILATERATION_THREE_APS_WEIGHT;      // Optimal for three APs (exact solution)
            case FOUR_PLUS_APS:
                return TRILATERATION_FOUR_PLUS_APS_WEIGHT;  // Good for overdetermined systems
            default:
                return 0.0;
        }
    }
    
    @Override
    public double getSignalQualityMultiplier(SignalQualityFactor factor) {
        switch (factor) {
            case STRONG_SIGNAL:
                return TRILATERATION_STRONG_SIGNAL_MULTIPLIER;
            case MEDIUM_SIGNAL:
                return TRILATERATION_MEDIUM_SIGNAL_MULTIPLIER;
            case WEAK_SIGNAL:
                return TRILATERATION_WEAK_SIGNAL_MULTIPLIER;
            case VERY_WEAK_SIGNAL:
                return TRILATERATION_VERY_WEAK_SIGNAL_MULTIPLIER;
            default:
                return TRILATERATION_MEDIUM_SIGNAL_MULTIPLIER;
        }
    }
    
    @Override
    public double getGeometricQualityMultiplier(GeometricQualityFactor factor) {
        switch (factor) {
            case EXCELLENT_GDOP:
                return TRILATERATION_EXCELLENT_GDOP_MULTIPLIER;
            case GOOD_GDOP:
                return TRILATERATION_GOOD_GDOP_MULTIPLIER;
            case FAIR_GDOP:
                return TRILATERATION_FAIR_GDOP_MULTIPLIER;
            case POOR_GDOP:
                return TRILATERATION_POOR_GDOP_MULTIPLIER;
            case COLLINEAR:
                return TRILATERATION_COLLINEAR_MULTIPLIER;
            default:
                return TRILATERATION_GOOD_GDOP_MULTIPLIER;
        }
    }
    
    @Override
    public double getSignalDistributionMultiplier(SignalDistributionFactor factor) {
        switch (factor) {
            case UNIFORM_SIGNALS:
                return TRILATERATION_UNIFORM_SIGNALS_MULTIPLIER;
            case MIXED_SIGNALS:
                return TRILATERATION_MIXED_SIGNALS_MULTIPLIER;
            case SIGNAL_OUTLIERS:
                return TRILATERATION_SIGNAL_OUTLIERS_MULTIPLIER;
            default:
                return TRILATERATION_MIXED_SIGNALS_MULTIPLIER;
        }
    }

    /**
     * Calculates 3D distance between two points using Haversine formula.
     * Accounts for Earth's curvature in horizontal distance.
     * When altitude data is missing, falls back to 2D distance calculation.
     * 
     * @param lat1 First point latitude
     * @param lon1 First point longitude
     * @param alt1 First point altitude (can be 0.0 if missing)
     * @param lat2 Second point latitude
     * @param lon2 Second point longitude
     * @param alt2 Second point altitude (can be 0.0 if missing)
     * @param use3D Whether to include altitude in distance calculation
     * @return 3D or 2D distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double alt1, 
                                  double lat2, double lon2, double alt2,
                                  boolean use3D) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                  Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                  Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double horizontalDist = EARTH_RADIUS_METERS * c;
        
        // Only include vertical component if we have 3D data and use3D is true
        if (use3D) {
            double verticalDist = alt2 - alt1;
            return Math.sqrt(horizontalDist * horizontalDist + verticalDist * verticalDist);
        } else {
            // 2D distance only (horizontal)
            return horizontalDist;
        }
    }
    
    /**
     * Convenience method for calculateDistance that uses currentUse3D value.
     */
    private double calculateDistance(double lat1, double lon1, double alt1, 
                                  double lat2, double lon2, double alt2) {
        return calculateDistance(lat1, lon1, alt1, lat2, lon2, alt2, false);
    }

    /**
     * Calculates accuracy based on signal strength, GDOP, and distance uncertainty.
     * Uses academic principles for error propagation and geometric quality assessment.
     * 
     * Mathematical foundation:
     * - Strong signals: Base accuracy scaled by GDOP with controlled adjustment
     * - Weak signals: Distance-based accuracy fully scaled by GDOP
     * - GDOP reflects how AP geometry affects position uncertainty
     * 
     * References:
     * - Caffery, J. "Wireless Location in CDMA Cellular Radio Systems"
     * - Patwari, N., et al. "Locating the nodes" IEEE Signal Processing Magazine
     * 
     * @param avgSignalStrength Average signal strength across measurements
     * @param gdopFactor GDOP factor indicating geometric quality (1.0 = excellent, >2.0 = poor)
     * @param avgDistance Average distance to access points
     * @return Calculated accuracy in meters
     */
    private double calculateAccuracy(double avgSignalStrength, double gdopFactor, double avgDistance) {
        double baseAccuracy;
        
        // Determine base accuracy based on signal quality
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            // Strong signals: Use fixed base accuracy representing optimal trilateration performance
            baseAccuracy = BASE_ACCURACY_STRONG_SIGNALS_METERS;
            
            // Apply controlled GDOP adjustment for strong signals
            // Formula follows academic practice: accuracy = base × (1 + (gdop_factor - 1) × sensitivity)
            double accuracy = baseAccuracy * (1.0 + (gdopFactor - 1.0) * GDOPCalculator.GDOP_ACCURACY_MULTIPLIER);
            
            // Constrain within expected range for strong signals
            return Math.max(MIN_ACCURACY_METERS, Math.min(MAX_ACCURACY_STRONG_SIGNALS_METERS, accuracy));
            
        } else {
            // Weak/medium signals: Use distance-based accuracy reflecting increased uncertainty
            baseAccuracy = Math.min(avgDistance * 0.3, MAX_ACCURACY_ANY_SCENARIO_METERS);
            
            // Apply full GDOP scaling for weaker signals (more sensitive to geometry)
            double accuracy = baseAccuracy * gdopFactor;
            
            // Ensure reasonable bounds
            return Math.max(MIN_ACCURACY_METERS, Math.min(MAX_ACCURACY_ANY_SCENARIO_METERS, accuracy));
        }
    }

    /**
     * Calculates confidence based on signal strength, AP count, and geometric quality.
     * Uses academic principles for reliability assessment in trilateration systems.
     * 
     * Mathematical foundation:
     * - Signal factor: Normalized signal quality assessment
     * - AP count factor: Geometric redundancy benefit
     * - GDOP adjustment: Geometric quality impact on reliability
     * 
     * References:
     * - IEEE 802.11 positioning accuracy standards
     * - Patwari, N., et al. positioning confidence metrics
     * 
     * @param avgSignalStrength Average signal strength across measurements (dBm)
     * @param apCount Number of access points used in trilateration
     * @param gdopFactor GDOP factor indicating geometric quality
     * @return Calculated confidence value between 0 and 1
     */
    private double calculateConfidence(double avgSignalStrength, int apCount, double gdopFactor) {
        // Calculate signal quality factor (0-1 scale)
        double signalFactor;
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            // Strong signals: Linear scaling from threshold to excellent
            signalFactor = Math.min(1.0, Math.max(0.0, 
                          (avgSignalStrength - WEAK_SIGNAL_THRESHOLD_DBM) / 
                          (STRONG_SIGNAL_THRESHOLD_DBM - WEAK_SIGNAL_THRESHOLD_DBM)));
        } else {
            // Weak signals: Linear scaling from very weak to threshold
            signalFactor = Math.min(1.0, Math.max(0.0, 
                          (avgSignalStrength - (-100.0)) / 
                          (WEAK_SIGNAL_THRESHOLD_DBM - (-100.0))));
        }
        
        // Calculate AP count factor (0-1 scale) - redundancy improves confidence
        double apCountFactor = Math.min(1.0, Math.max(0.0, 
                              (apCount - MIN_AP_COUNT_FOR_TRILATERATION) / 
                              (double)(MAX_AP_COUNT_FOR_SCALING - MIN_AP_COUNT_FOR_TRILATERATION)));
        
        // Calculate base confidence using weighted combination
        double baseConfidence = MIN_CONFIDENCE + (MAX_CONFIDENCE - MIN_CONFIDENCE) * 
                               (CONFIDENCE_SIGNAL_WEIGHT * signalFactor + 
                                CONFIDENCE_AP_COUNT_WEIGHT * apCountFactor);
        
        // Apply GDOP adjustment - poor geometry reduces confidence
        // Academic approach: confidence decreases with poor geometric distribution
        double confidence = baseConfidence * (1.0 - GDOPCalculator.GDOP_CONFIDENCE_WEIGHT * 
                                            (1.0 - 1.0/Math.max(1.0, gdopFactor)));
        
        // Apply signal-dependent bounds to maintain realistic expectations
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            // Strong signals: Allow high confidence with good geometry
            confidence = Math.max(HIGH_CONFIDENCE_THRESHOLD, Math.min(MAX_CONFIDENCE, confidence));
        } else if (avgSignalStrength < WEAK_SIGNAL_THRESHOLD_DBM) {
            // Weak signals: Cap confidence to reflect uncertainty
            confidence = Math.min(WEAK_SIGNAL_CONFIDENCE_CAP, confidence);
        }
        // Medium signals: Use calculated value within global bounds
        
        return Math.max(MIN_CONFIDENCE, Math.min(MAX_CONFIDENCE, confidence));
    }

    // ============================================================================
    // HELPER CLASSES FOR DATA ENCAPSULATION
    // ============================================================================

    /**
     * Encapsulates reference point information for coordinate conversion.
     */
    private static class ReferencePoint {
        final double latitude;
        final double longitude;

        ReferencePoint(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * Encapsulates coordinate conversion factors.
     */
    private static class CoordinateConverter {
        final double latToMeters;
        final double lonToMeters;

        CoordinateConverter(double latToMeters, double lonToMeters) {
            this.latToMeters = latToMeters;
            this.lonToMeters = lonToMeters;
        }
    }

    /**
     * Encapsulates coordinate cache with aggregated statistics.
     */
    private static class CoordinateCache {
        final Map<String, CachedCoordinates> coordinateMap;
        private final double totalSignalStrength;
        private final double totalDistance;
        private final int scanCount;

        CoordinateCache(Map<String, CachedCoordinates> coordinateMap, 
                       double totalSignalStrength, double totalDistance, int scanCount) {
            this.coordinateMap = coordinateMap;
            this.totalSignalStrength = totalSignalStrength;
            this.totalDistance = totalDistance;
            this.scanCount = scanCount;
        }

        double getAverageSignalStrength() {
            return totalSignalStrength / scanCount;
        }

        double getAverageDistance() {
            return totalDistance / scanCount;
        }
    }

    /**
     * Encapsulates weighted center calculation results.
     */
    private static class WeightedCenter {
        final double centerLat;
        final double centerLon;
        final double minLat;
        final double maxLat;
        final double minLon;
        final double maxLon;

        WeightedCenter(double centerLat, double centerLon, 
                      double minLat, double maxLat, 
                      double minLon, double maxLon) {
            this.centerLat = centerLat;
            this.centerLon = centerLon;
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }
    }

    /**
     * Encapsulates geographic position coordinates.
     */
    private static class GeographicPosition {
        final double latitude;
        final double longitude;

        GeographicPosition(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
} 