package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Implementation of the RSSI Ratio positioning algorithm.
 * 
 * This algorithm estimates position using relative signal strength ratios between access points,
 * eliminating the need for absolute signal strength calibration. It's particularly effective
 * in environments where hardware characteristics are similar across access points.
 * 
 * MATHEMATICAL FOUNDATION:
 * ========================
 * The algorithm is based on the principle that signal strength ratios correspond to distance ratios
 * in free space propagation. For two access points with signal strengths RSSI₁ and RSSI₂:
 * 
 * Distance Ratio = 10^((RSSI₂ - RSSI₁) / PATH_LOSS_COEFFICIENT)
 * 
 * Where PATH_LOSS_COEFFICIENT = 20.0 dB per decade for free space propagation.
 * 
 * Position Interpolation:
 * For each AP pair (AP₁, AP₂), the position is interpolated as:
 * P = (P₁ + ratio × P₂) / (1 + ratio)
 * 
 * Where:
 * - P₁, P₂ are the geographic positions of AP₁ and AP₂
 * - ratio is the calculated distance ratio from signal strengths
 * - P is the interpolated position weighted by the ratio
 * 
 * ALGORITHM CHARACTERISTICS:
 * =========================
 * USE CASES:
 * - Ideal for scenarios with 2-3 access points
 * - Effective when absolute signal calibration is difficult
 * - Works well when APs have similar hardware characteristics
 * - Suitable for dynamic transmit power environments
 * 
 * STRENGTHS:
 * - No need for absolute signal strength calibration
 * - Resistant to environmental changes affecting all signals equally
 * - Computationally efficient for small numbers of APs
 * - Handles dynamic transmit power changes effectively
 * 
 * WEAKNESSES:
 * - Accuracy decreases with dissimilar AP hardware
 * - Performance degrades with more than 4-5 APs
 * - Sensitive to individual signal fluctuations
 * - Less accurate than trilateration in ideal conditions
 * 
 * THREAD SAFETY:
 * ==============
 * This implementation is thread-safe through the use of:
 * - Immutable constants and method parameters
 * - ConcurrentHashMap for AP lookups
 * - AtomicDouble accumulators for parallel calculations
 * - Stream-based parallel processing with proper isolation
 */
@Component
public class RSSIRatioAlgorithm implements PositioningAlgorithm {

    // ========================================================================================
    // CORE ALGORITHM CONSTANTS
    // ========================================================================================
    
    /**
     * Algorithm identification string used by the positioning framework.
     */
    private static final String ALGORITHM_NAME = "RSSI Ratio";
    
    /**
     * Base confidence level returned by getConfidence() method.
     * Rationale: 0.75 represents high confidence in the algorithm's general capability,
     * while individual position calculations may have lower confidence based on signal quality.
     */
    private static final double BASE_CONFIDENCE = 0.75;
    
    /**
     * Minimum number of access points required for RSSI ratio calculations.
     * Rationale: At least 2 APs are needed to calculate signal strength ratios.
     * With only 1 AP, no ratio comparison is possible.
     */
    private static final int MIN_REQUIRED_APS = 2;

    // ========================================================================================
    // MATHEMATICAL CONSTANTS FOR RSSI RATIO CALCULATIONS
    // ========================================================================================
    
    /**
     * Path loss coefficient for free space propagation (dB per decade).
     * 
     * Mathematical Foundation:
     * In free space, signal strength decreases by 20 dB per decade of distance.
     * This constant is used in the formula: ratio = 10^((RSSI₁ - RSSI₂) / 20.0)
     * 
     * Rationale: 20.0 dB represents the theoretical free space path loss,
     * providing a baseline for signal-to-distance ratio calculations.
     * 
     * Reference: Friis transmission equation for free space propagation
     */
    private static final double PATH_LOSS_COEFFICIENT = 20.0;
    
    /**
     * Weight normalization factor for signal strength differences.
     * 
     * Mathematical Purpose:
     * Converts signal strength differences (in dBm) to normalized weights [0,1].
     * Formula: weight = |RSSI₁ - RSSI₂| / WEIGHT_NORMALIZATION_FACTOR
     * 
     * Rationale: 30.0 dB represents a significant signal difference that would
     * receive maximum weight (1.0). Smaller differences receive proportionally
     * lower weights, reducing their influence on the final position calculation.
     * 
     * Typical signal differences range from 2-30 dB in real environments.
     */
    private static final double WEIGHT_NORMALIZATION_FACTOR = 30.0;

    // ========================================================================================
    // ACCURACY CALCULATION CONSTANTS
    // ========================================================================================
    
    /**
     * Default fallback value for average signal strength when calculation fails.
     * 
     * Rationale: -80.0 dBm represents a moderate signal strength typical in indoor
     * environments. Used as a safe fallback to prevent accuracy calculation failures.
     */
    private static final double DEFAULT_AVERAGE_SIGNAL_STRENGTH = -80.0;
    
    /**
     * Default base accuracy when AP accuracy data is unavailable.
     * 
     * Rationale: 15.0 meters represents a reasonable baseline accuracy for
     * RSSI-based positioning systems in typical indoor environments.
     */
    private static final double DEFAULT_BASE_ACCURACY = 15.0;
    
    /**
     * Signal strength threshold for accuracy scaling calculations.
     * 
     * Mathematical Purpose:
     * Used in formula: signalFactor = (-avgSignalStrength - SIGNAL_THRESHOLD) / SCALING_DIVISOR
     * 
     * Rationale: -50.0 dBm represents a very strong signal. Signals weaker than this
     * will result in degraded accuracy through the scaling factor calculation.
     */
    private static final double SIGNAL_THRESHOLD = -50.0;
    
    /**
     * Divisor for signal strength to accuracy scaling factor conversion.
     * 
     * Mathematical Purpose:
     * Converts signal strength differences to scaling multipliers.
     * Formula: signalFactor = (-avgSignalStrength - 50) / 10.0
     * 
     * Rationale: 10.0 dB increments provide reasonable granularity for accuracy scaling.
     * Each 10 dB of signal degradation increases the accuracy scaling factor.
     */
    private static final double SCALING_DIVISOR = 10.0;
    
    /**
     * Minimum scaling factor for signal-based accuracy adjustment.
     * 
     * Rationale: 1.0 ensures that even the strongest signals don't improve accuracy
     * beyond the base accuracy. Prevents unrealistic accuracy improvements.
     */
    private static final double MIN_SCALING_FACTOR = 1.0;
    
    /**
     * Maximum scaling factor for signal-based accuracy adjustment.
     * 
     * Rationale: 3.0 limits accuracy degradation to 3x the base accuracy even for
     * very weak signals. Prevents extremely pessimistic accuracy estimates.
     */
    private static final double MAX_SCALING_FACTOR = 3.0;

    // ========================================================================================
    // CONFIDENCE CALCULATION CONSTANTS
    // ========================================================================================
    
    /**
     * Signal strength offset for quality normalization (converting dBm to [0,1] range).
     * 
     * Mathematical Purpose:
     * Used in formula: signalQuality = (signalStrength + SIGNAL_OFFSET) / SIGNAL_RANGE
     * 
     * Rationale: 95.0 dBm offset maps typical WiFi signal range [-95, -50] dBm
     * to a normalized range starting from 0. Very weak signals (-95 dBm) become 0.
     */
    private static final double SIGNAL_OFFSET = 95.0;
    
    /**
     * Signal strength range for quality normalization (dB span).
     * 
     * Mathematical Purpose:
     * Completes the normalization: signalQuality = (signalStrength + 95.0) / 45.0
     * 
     * Rationale: 45.0 dB span covers typical WiFi range [-95, -50] dBm.
     * Maps to [0,1] range where 0 = very weak signal, 1 = very strong signal.
     */
    private static final double SIGNAL_RANGE = 45.0;
    
    /**
     * Default signal quality when normalization calculation fails.
     * 
     * Rationale: 0.5 represents moderate signal quality, providing a neutral
     * baseline that neither penalizes nor rewards the confidence calculation.
     */
    private static final double DEFAULT_SIGNAL_QUALITY = 0.5;
    
    /**
     * Maximum confidence level achievable by the algorithm.
     * 
     * Rationale: 0.85 represents high but not perfect confidence, acknowledging
     * the inherent limitations of RSSI-based positioning. Prevents overconfidence.
     */
    private static final double MAX_CONFIDENCE = 0.85;
    
    /**
     * Signal quality multiplier for confidence boost calculation.
     * 
     * Mathematical Purpose:
     * Used in formula: confidence = baseConfidence + (signalQuality × CONFIDENCE_BOOST)
     * 
     * Rationale: 1.0 provides a direct linear relationship between signal quality
     * and confidence improvement, up to the maximum confidence limit.
     */
    private static final double CONFIDENCE_BOOST = 1.0;
    
    /**
     * Strong signal threshold for confidence floor calculation.
     * 
     * Rationale: -70.0 dBm represents a strong signal boundary. Signals stronger
     * than this receive a minimum confidence floor of HIGH_CONFIDENCE_FLOOR.
     */
    private static final double STRONG_SIGNAL_THRESHOLD = -70.0;
    
    /**
     * Minimum confidence floor for strong signals.
     * 
     * Rationale: 0.7 ensures that strong signals always maintain high confidence,
     * even if other factors (like geometry) might reduce the calculated confidence.
     */
    private static final double HIGH_CONFIDENCE_FLOOR = 0.7;

    // ========================================================================================
    // ALGORITHM SELECTION FRAMEWORK CONSTANTS
    // ========================================================================================
    // These constants define how the algorithm integrates with the hybrid selection framework
    
    /**
     * Weight constants from the algorithm selection framework.
     * These reflect the strengths and weaknesses of the RSSI Ratio algorithm:
     * - Works optimally with 2 APs
     * - Good with 3 APs but less effective with more APs
     * - Medium signal quality sensitivity
     * - Moderate impact from geometric quality
     * - Good performance with uniform signals, worse with outliers
     */
    // AP Count weights from framework document
    private static final double RSSI_RATIO_SINGLE_AP_WEIGHT = 0.0;    // Not applicable for single AP
    private static final double RSSI_RATIO_TWO_APS_WEIGHT = 1.0;      // Optimal for two APs
    private static final double RSSI_RATIO_THREE_APS_WEIGHT = 0.7;    // Good for three APs
    private static final double RSSI_RATIO_FOUR_PLUS_APS_WEIGHT = 0.5;// Useful but not optimal for 4+ APs
    
    // Signal quality multipliers from framework document
    private static final double RSSI_RATIO_STRONG_SIGNAL_MULTIPLIER = 1.0;  // No change with strong signals
    private static final double RSSI_RATIO_MEDIUM_SIGNAL_MULTIPLIER = 0.9;  // Slight reduction with medium signals
    private static final double RSSI_RATIO_WEAK_SIGNAL_MULTIPLIER = 0.6;    // Significant reduction with weak signals
    private static final double RSSI_RATIO_VERY_WEAK_SIGNAL_MULTIPLIER = 0.0; // ×0.0 for very weak signals
    
    // Geometric quality multipliers from framework document
    private static final double RSSI_RATIO_EXCELLENT_GDOP_MULTIPLIER = 1.0; // No change for excellent geometry
    private static final double RSSI_RATIO_GOOD_GDOP_MULTIPLIER = 1.0;      // No change for good geometry
    private static final double RSSI_RATIO_FAIR_GDOP_MULTIPLIER = 0.9;      // Slight reduction for fair geometry
    private static final double RSSI_RATIO_POOR_GDOP_MULTIPLIER = 0.8;      // More reduction for poor geometry
    
    // Signal distribution multipliers from framework document
    private static final double RSSI_RATIO_UNIFORM_SIGNALS_MULTIPLIER = 1.2; // Significant improvement for uniform signals
    private static final double RSSI_RATIO_MIXED_SIGNALS_MULTIPLIER = 0.9;   // Slight reduction for mixed signals
    private static final double RSSI_RATIO_SIGNAL_OUTLIERS_MULTIPLIER = 0.7; // Significant reduction for outliers

    // ========================================================================================
    // HELPER CLASSES
    // ========================================================================================
    
    /**
     * Immutable data class for storing weighted position calculation results.
     * 
     * This class encapsulates the result of processing a single AP pair,
     * including the weighted coordinates and metadata about altitude availability.
     * 
     * Thread Safety: This class is immutable and thread-safe.
     */
    private static final class WeightedPositionResult {
        private final double weightedLat;
        private final double weightedLon;
        private final double weightedAlt;
        private final double weight;
        private final boolean hasAltitudeData;

        /**
         * Creates a weighted position result with all parameters.
         * 
         * @param weightedLat latitude weighted by signal strength ratio
         * @param weightedLon longitude weighted by signal strength ratio  
         * @param weightedAlt altitude weighted by signal strength ratio
         * @param weight the calculated weight for this AP pair
         * @param hasAltitudeData whether both APs in the pair had altitude data
         */
        WeightedPositionResult(double weightedLat, double weightedLon, double weightedAlt, 
                             double weight, boolean hasAltitudeData) {
            this.weightedLat = weightedLat;
            this.weightedLon = weightedLon;
            this.weightedAlt = weightedAlt;
            this.weight = weight;
            this.hasAltitudeData = hasAltitudeData;
        }
        
        /**
         * Creates a weighted position result assuming altitude data is available.
         * 
         * @param weightedLat latitude weighted by signal strength ratio
         * @param weightedLon longitude weighted by signal strength ratio
         * @param weightedAlt altitude weighted by signal strength ratio
         * @param weight the calculated weight for this AP pair
         */
        WeightedPositionResult(double weightedLat, double weightedLon, double weightedAlt, double weight) {
            this(weightedLat, weightedLon, weightedAlt, weight, true);
        }
    }

    // ========================================================================================
    // MAIN ALGORITHM IMPLEMENTATION
    // ========================================================================================

    @Override
    public Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        validateInputs(wifiScan, knownAPs);
        
        // Create thread-safe AP lookup map
        Map<String, WifiAccessPoint> apMap = createAccessPointMap(knownAPs);
        
        // Calculate weighted positions from all AP pairs
        List<WeightedPositionResult> results = calculateWeightedPositions(wifiScan, apMap);
        
        // Aggregate all weighted position results
        PositionAggregation aggregation = aggregatePositionResults(results);
        
        if (aggregation.totalWeight() == 0) {
            throw new IllegalArgumentException("No valid AP pairs found for position calculation");
        }

        // Calculate final position components
        double finalLatitude = aggregation.weightedLat() / aggregation.totalWeight();
        double finalLongitude = aggregation.weightedLon() / aggregation.totalWeight();
        double finalAltitude = calculateFinalAltitude(aggregation);
        
        // Calculate accuracy and confidence metrics
        double accuracy = calculateAccuracy(wifiScan, knownAPs);
        double confidence = calculateConfidence(wifiScan, aggregation.totalWeight());

        return new Position(finalLatitude, finalLongitude, finalAltitude, accuracy, confidence);
    }

    /**
     * Validates input parameters for the position calculation.
     * 
     * @param wifiScan list of WiFi scan results
     * @param knownAPs list of known access points
     * @throws IllegalArgumentException if inputs are invalid
     */
    private void validateInputs(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        if (wifiScan == null || knownAPs == null) {
            throw new IllegalArgumentException("WiFi scan and known APs cannot be null");
        }
        if (wifiScan.isEmpty() || knownAPs.isEmpty()) {
            throw new IllegalArgumentException("WiFi scan and known APs cannot be empty");
        }
        if (wifiScan.size() < MIN_REQUIRED_APS) {
            throw new IllegalArgumentException("At least " + MIN_REQUIRED_APS + 
                " APs are required for RSSI ratio calculation");
        }
        }

    /**
     * Creates a thread-safe map for AP lookups by MAC address.
     * 
     * @param knownAPs list of known access points
     * @return concurrent map from MAC address to access point
     */
    private Map<String, WifiAccessPoint> createAccessPointMap(List<WifiAccessPoint> knownAPs) {
        return knownAPs.stream()
            .collect(Collectors.toConcurrentMap(
                WifiAccessPoint::getMacAddress, 
                ap -> ap,
                (ap1, ap2) -> ap1 // In case of duplicate keys, keep the first one
            ));
    }

    /**
     * Calculates weighted positions from all AP pairs using parallel processing.
     * 
     * Mathematical Process:
     * For each AP pair (APᵢ, APⱼ):
     * 1. Calculate signal ratio: r = 10^((RSSIᵢ - RSSIⱼ) / 20.0)
     * 2. Calculate position weight: w = |RSSIᵢ - RSSIⱼ| / 30.0  
     * 3. Interpolate position: P = (Pᵢ + r × Pⱼ) / (1 + r)
     * 4. Apply weight: weightedP = P × w
     * 
     * @param wifiScan list of WiFi scan results
     * @param apMap map from MAC address to access point
     * @return list of weighted position results
     */
    private List<WeightedPositionResult> calculateWeightedPositions(
            List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        
        return IntStream.range(0, wifiScan.size())
            .parallel()
            .boxed()
            .flatMap(i -> createAccessPointPairsForIndex(i, wifiScan, apMap))
            .filter(result -> result != null)
            .collect(Collectors.toList());
    }

    /**
     * Creates WeightedPositionResult stream for all AP pairs involving the given index.
     * 
     * This method processes all AP pairs where the first AP is at the given index
     * and the second AP is at any subsequent index. This ensures each pair is
     * processed exactly once without duplication.
     * 
     * @param firstApIndex index of the first AP in the pair
     * @param wifiScan list of WiFi scan results
     * @param apMap map from MAC address to access point
     * @return stream of weighted position results for this index
     */
    private Stream<WeightedPositionResult> createAccessPointPairsForIndex(
            int firstApIndex, List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        
        return IntStream.range(firstApIndex + 1, wifiScan.size())
                .parallel()
            .mapToObj(secondApIndex -> processAccessPointPair(
                wifiScan.get(firstApIndex), 
                wifiScan.get(secondApIndex), 
                apMap
            ));
    }

    /**
     * Processes a single AP pair to calculate weighted position contribution.
     * 
     * Mathematical Implementation:
     * - Signal Ratio: ratio = 10^((RSSI₁ - RSSI₂) / PATH_LOSS_COEFFICIENT)
     * - Weight: weight = |RSSI₁ - RSSI₂| / WEIGHT_NORMALIZATION_FACTOR
     * - Position: P = (P₁ + ratio × P₂) / (1 + ratio)
     * 
     * @param scan1 first WiFi scan result
     * @param scan2 second WiFi scan result
     * @param apMap map from MAC address to access point
     * @return weighted position result or null if APs not found
     */
    private WeightedPositionResult processAccessPointPair(
            WifiScanResult scan1, WifiScanResult scan2, Map<String, WifiAccessPoint> apMap) {

                    WifiAccessPoint ap1 = apMap.get(scan1.macAddress());
                    WifiAccessPoint ap2 = apMap.get(scan2.macAddress());

                    if (ap1 == null || ap2 == null) {
                        return null;
                    }

        // Calculate signal strength ratio using path loss model
        double signalDifference = scan1.signalStrength() - scan2.signalStrength();
        double ratio = Math.pow(10, signalDifference / PATH_LOSS_COEFFICIENT);
        
        // Calculate weight based on signal strength difference magnitude
        double weight = Math.abs(signalDifference) / WEIGHT_NORMALIZATION_FACTOR;

        // Interpolate position using weighted ratio
        double lat = interpolateCoordinate(ap1.getLatitude(), ap2.getLatitude(), ratio);
        double lon = interpolateCoordinate(ap1.getLongitude(), ap2.getLongitude(), ratio);
                    
        // Handle altitude calculation with null safety
                    double alt = 0.0;
                    boolean hasAltitudeData = false;
                    
                    if (ap1.getAltitude() != null && ap2.getAltitude() != null) {
            alt = interpolateCoordinate(ap1.getAltitude(), ap2.getAltitude(), ratio);
                        hasAltitudeData = true;
                    }

                    return new WeightedPositionResult(lat * weight, lon * weight, alt * weight, weight, hasAltitudeData);
    }

    /**
     * Interpolates a single coordinate using the signal strength ratio.
     * 
     * Mathematical Formula:
     * coordinate = (coord₁ + ratio × coord₂) / (1 + ratio)
     * 
     * This formula provides a weighted interpolation where stronger signals
     * (higher ratio) bias the result toward the second coordinate.
     * 
     * @param coord1 first coordinate value
     * @param coord2 second coordinate value
     * @param ratio signal strength ratio
     * @return interpolated coordinate
     */
    private double interpolateCoordinate(double coord1, double coord2, double ratio) {
        return (coord1 + ratio * coord2) / (1 + ratio);
    }

    /**
     * Aggregates all weighted position results using thread-safe accumulators.
     * 
     * @param results list of weighted position results
     * @return aggregated position data
     */
    private PositionAggregation aggregatePositionResults(List<WeightedPositionResult> results) {
        DoubleAdder totalWeight = new DoubleAdder();
        DoubleAdder weightedLat = new DoubleAdder();
        DoubleAdder weightedLon = new DoubleAdder();
        DoubleAdder weightedAlt = new DoubleAdder();
        DoubleAdder altitudeWeightSum = new DoubleAdder();

        results.forEach(result -> {
            weightedLat.add(result.weightedLat);
            weightedLon.add(result.weightedLon);
            weightedAlt.add(result.weightedAlt);
            totalWeight.add(result.weight);
            
            if (result.hasAltitudeData) {
                altitudeWeightSum.add(result.weight);
            }
        });

        return new PositionAggregation(
            totalWeight.doubleValue(),
            weightedLat.doubleValue(),
            weightedLon.doubleValue(),
            weightedAlt.doubleValue(),
            altitudeWeightSum.doubleValue()
        );
    }

    /**
     * Record for aggregated position calculation results.
     * 
     * @param totalWeight sum of all position weights
     * @param weightedLat sum of all weighted latitudes
     * @param weightedLon sum of all weighted longitudes
     * @param weightedAlt sum of all weighted altitudes
     * @param altitudeWeightSum sum of weights for positions with altitude data
     */
    private record PositionAggregation(
        double totalWeight,
        double weightedLat,
        double weightedLon,
        double weightedAlt,
        double altitudeWeightSum
    ) {}

    /**
     * Calculates the final altitude, handling cases where not all APs have altitude data.
     * 
     * @param aggregation aggregated position results
     * @return final altitude value (0.0 if no altitude data available)
     */
    private double calculateFinalAltitude(PositionAggregation aggregation) {
        if (aggregation.altitudeWeightSum() > 0) {
            return aggregation.weightedAlt() / aggregation.altitudeWeightSum();
        }
        return 0.0;
    }

    /**
     * Calculates position accuracy based on signal strength and AP accuracy.
     * 
     * Mathematical Model:
     * 1. Calculate average signal strength
     * 2. Calculate base accuracy from AP horizontal accuracy values
     * 3. Scale accuracy based on signal strength:
     *    signalFactor = max(1, min(3, (-avgSignal - 50) / 10))
     * 4. finalAccuracy = baseAccuracy × signalFactor
     * 
     * Rationale: Weaker signals lead to less reliable distance estimates,
     * degrading overall position accuracy. The scaling is capped between
     * 1x (no degradation) and 3x (maximum degradation).
     * 
     * @param wifiScan list of WiFi scan results
     * @param knownAPs list of known access points
     * @return estimated position accuracy in meters
     */
    private double calculateAccuracy(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        double avgSignalStrength = wifiScan.stream()
            .mapToDouble(WifiScanResult::signalStrength)
            .average()
            .orElse(DEFAULT_AVERAGE_SIGNAL_STRENGTH);

        double baseAccuracy = knownAPs.parallelStream()
            .mapToDouble(WifiAccessPoint::getHorizontalAccuracy)
            .average()
            .orElse(DEFAULT_BASE_ACCURACY);

        // Scale accuracy based on signal strength - weak signals get worse accuracy
        double signalFactor = Math.max(MIN_SCALING_FACTOR, 
            Math.min(MAX_SCALING_FACTOR, (-avgSignalStrength + SIGNAL_THRESHOLD) / SCALING_DIVISOR));
        
        return baseAccuracy * signalFactor;
    }

    /**
     * Calculates position confidence based on signal quality and weight distribution.
     * 
     * Mathematical Model:
     * 1. Signal Quality: normalized signal strength in range [0,1]
     *    signalQuality = (signalStrength + SIGNAL_OFFSET) / SIGNAL_RANGE
     * 2. Base Confidence: ratio of actual weights to maximum possible weights
     *    baseConfidence = min(0.85, totalWeight / maxPossibleWeight)
     * 3. Enhanced Confidence: baseConfidence + signalQuality
     * 4. Strong Signal Floor: if avgSignal ≥ -70dBm, confidence ≥ 0.7
     * 
     * Rationale: Confidence increases with signal quality and weight coverage.
     * Strong signals receive a confidence floor to ensure reliable reporting.
     * 
     * @param wifiScan list of WiFi scan results
     * @param totalWeight sum of all calculated weights
     * @return confidence level between 0.0 and 1.0
     */
    private double calculateConfidence(List<WifiScanResult> wifiScan, double totalWeight) {
        double avgSignalStrength = wifiScan.stream()
            .mapToDouble(WifiScanResult::signalStrength)
            .average()
            .orElse(DEFAULT_AVERAGE_SIGNAL_STRENGTH);

        double signalQuality = wifiScan.stream()
            .mapToDouble(scan -> Math.min(1.0, Math.max(0.0, 
                (scan.signalStrength() + SIGNAL_OFFSET) / SIGNAL_RANGE)))
            .average()
            .orElse(DEFAULT_SIGNAL_QUALITY);

        // Calculate maximum possible weight for n APs: n*(n-1)/2 pairs
        int apCount = wifiScan.size();
        double maxPossibleWeight = apCount * (apCount - 1) / 2.0;

        double baseConfidence = Math.min(MAX_CONFIDENCE, totalWeight / maxPossibleWeight);
        double computedConfidence = Math.min(MAX_CONFIDENCE, 
            baseConfidence + (signalQuality * CONFIDENCE_BOOST));
        
        // Apply confidence floor for strong signals
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD) {
            return Math.max(HIGH_CONFIDENCE_FLOOR, computedConfidence);
        }
        
        return computedConfidence;
    }

    // ========================================================================================
    // ALGORITHM FRAMEWORK INTEGRATION METHODS
    // ========================================================================================

    @Override
    public double getConfidence() {
        return BASE_CONFIDENCE;
    }

    @Override
    public String getName() {
        return ALGORITHM_NAME;
    }
    
    @Override
    public double getBaseWeight(APCountFactor factor) {
        return switch (factor) {
            case SINGLE_AP -> RSSI_RATIO_SINGLE_AP_WEIGHT;      // Not applicable for single AP
            case TWO_APS -> RSSI_RATIO_TWO_APS_WEIGHT;          // Optimal for two APs
            case THREE_APS -> RSSI_RATIO_THREE_APS_WEIGHT;      // Good for three APs
            case FOUR_PLUS_APS -> RSSI_RATIO_FOUR_PLUS_APS_WEIGHT; // Useful but not optimal for 4+ APs
            default -> 0.0;
        };
    }
    
    @Override
    public double getSignalQualityMultiplier(SignalQualityFactor factor) {
        return switch (factor) {
            case STRONG_SIGNAL -> RSSI_RATIO_STRONG_SIGNAL_MULTIPLIER;
            case MEDIUM_SIGNAL -> RSSI_RATIO_MEDIUM_SIGNAL_MULTIPLIER;
            case WEAK_SIGNAL -> RSSI_RATIO_WEAK_SIGNAL_MULTIPLIER;
            case VERY_WEAK_SIGNAL -> RSSI_RATIO_VERY_WEAK_SIGNAL_MULTIPLIER;
            default -> RSSI_RATIO_MEDIUM_SIGNAL_MULTIPLIER;
        };
    }
    
    @Override
    public double getGeometricQualityMultiplier(GeometricQualityFactor factor) {
        return switch (factor) {
            case EXCELLENT_GDOP -> RSSI_RATIO_EXCELLENT_GDOP_MULTIPLIER;
            case GOOD_GDOP -> RSSI_RATIO_GOOD_GDOP_MULTIPLIER;
            case FAIR_GDOP -> RSSI_RATIO_FAIR_GDOP_MULTIPLIER;
            case POOR_GDOP -> RSSI_RATIO_POOR_GDOP_MULTIPLIER;
            default -> RSSI_RATIO_GOOD_GDOP_MULTIPLIER;
        };
    }
    
    @Override
    public double getSignalDistributionMultiplier(SignalDistributionFactor factor) {
        return switch (factor) {
            case UNIFORM_SIGNALS -> RSSI_RATIO_UNIFORM_SIGNALS_MULTIPLIER;
            case MIXED_SIGNALS -> RSSI_RATIO_MIXED_SIGNALS_MULTIPLIER;
            case SIGNAL_OUTLIERS -> RSSI_RATIO_SIGNAL_OUTLIERS_MULTIPLIER;
            default -> RSSI_RATIO_MIXED_SIGNALS_MULTIPLIER;
        };
    }
} 