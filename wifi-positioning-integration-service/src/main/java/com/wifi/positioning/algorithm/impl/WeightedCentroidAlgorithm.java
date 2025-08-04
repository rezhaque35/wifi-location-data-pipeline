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
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * Implementation of the Weighted Centroid positioning algorithm.
 * 
 * USE CASES:
 * - Best suited for environments with many APs (4+ APs)
 * - Effective when AP geometry is poor for trilateration
 * - Useful in areas with high AP density but variable signal quality
 * - Good fallback when more precise methods fail
 * 
 * STRENGTHS:
 * - Simple and computationally efficient
 * - Robust to individual AP failures or signal anomalies
 * - Works well with non-uniform AP distributions
 * - Handles overlapping AP coverage effectively
 * 
 * WEAKNESSES:
 * - Less accurate than trilateration in ideal conditions
 * - Sensitive to AP distribution geometry
 * - May be biased toward areas of high AP density
 * - Accuracy depends heavily on signal strength quality
 * 
 * MATHEMATICAL MODEL:
 * The algorithm uses a weighted average of AP positions based on normalized signal strengths:
 * 
 * 1. Signal Strength Normalization:
 *    normalized = (RSSI - RSSI_MAX) / (RSSI_MIN - RSSI_MAX)
 *    where:
 *    - RSSI is the received signal strength indicator
 *    - RSSI_MAX = -30 dBm (theoretical maximum WiFi signal strength)
 *    - RSSI_MIN = -100 dBm (practical minimum usable WiFi signal strength)
 *    - Normalized value ranges from 0 (weakest) to 1 (strongest)
 * 
 * 2. Exponential Weight Calculation:
 *    weight = WEIGHT_BASE^normalized
 *    where:
 *    - WEIGHT_BASE = 10 (chosen to provide exponential weighting favoring stronger signals)
 *    - Stronger signals (normalized closer to 1) get exponentially higher weights
 *    - This creates a preference for closer APs with stronger signals
 * 
 * 3. Weighted Position Calculation:
 *    P = Σ(Pi × wi) / Σ(wi)
 *    where:
 *    - P is the final calculated position (latitude, longitude, altitude)
 *    - Pi is the position of access point i
 *    - wi is the calculated weight for access point i
 *    - This formula ensures positions are biased toward stronger signal sources
 * 
 * 4. Confidence Calculation:
 *    confidence = min(MAX_CONFIDENCE, coverage × BASE_CONFIDENCE)
 *    where:
 *    - coverage = number_of_scanned_APs / number_of_known_APs
 *    - MAX_CONFIDENCE = 0.8 (prevents overconfidence even with perfect coverage)
 *    - BASE_CONFIDENCE = 0.7 (algorithm's inherent confidence level)
 * 
 * 5. Accuracy Estimation:
 *    Uses parallel stream to calculate average horizontal accuracy of participating APs
 *    Falls back to DEFAULT_ACCURACY = 15.0 meters if no accuracy data available
 */
@Component
public class WeightedCentroidAlgorithm implements PositioningAlgorithm {

    // Signal strength normalization constants
    /**
     * Maximum theoretical WiFi signal strength in dBm.
     * Rationale: -30 dBm represents very close proximity (< 1 meter) to an AP.
     * This value is rarely achieved in practice but serves as the upper bound
     * for signal strength normalization calculations.
     */
    private static final double RSSI_MAX = -30.0;
    
    /**
     * Minimum practical WiFi signal strength in dBm for positioning.
     * Rationale: -100 dBm represents the lower threshold where WiFi signals
     * become too weak for reliable positioning. Below this threshold, 
     * signal strength variations are dominated by noise rather than distance.
     */
    private static final double RSSI_MIN = -100.0;
    
    // Weight calculation constants
    /**
     * Base value for exponential weight calculation.
     * Rationale: Base 10 provides strong exponential scaling that heavily favors
     * stronger signals while still giving some weight to weaker ones.
     * This creates a natural bias toward closer APs with stronger signals.
     */
    private static final double WEIGHT_BASE = 10.0;
    
    // Confidence and accuracy constants
    /**
     * Base confidence level for the weighted centroid algorithm.
     * Rationale: 0.7 reflects the algorithm's inherent reliability.
     * It's lower than trilateration (0.8-0.9) but higher than simple proximity (0.5)
     * because it uses multiple APs but doesn't solve geometric constraints.
     */
    private static final double BASE_CONFIDENCE = 0.7;
    
    /**
     * Maximum achievable confidence level for the algorithm.
     * Rationale: 0.8 prevents overconfidence even with perfect AP coverage.
     * Weighted centroid is inherently less precise than trilateration due to
     * geometric ambiguity, so confidence is capped below maximum levels.
     */
    private static final double MAX_CONFIDENCE = 0.8;
    
    /**
     * Default horizontal accuracy in meters when AP accuracy data is unavailable.
     * Rationale: 15 meters represents typical indoor positioning accuracy for
     * weighted centroid methods based on empirical studies. This conservative
     * estimate accounts for the algorithm's geometric limitations.
     */
    private static final double DEFAULT_ACCURACY_METERS = 15.0;
    
    /**
     * Zero threshold for floating point comparisons.
     * Rationale: Used to safely compare weighted sums to zero, accounting for
     * floating point precision limitations in calculations.
     */
    private static final double ZERO_THRESHOLD = 1e-10;
    
    /**
     * Weight constants from the algorithm selection framework.
     * These reflect the strengths and weaknesses of the Weighted Centroid algorithm:
     * - Works well with 2+ APs, optimal with 3-4+ APs
     * - Robust to signal quality variations
     * - Improved performance with poor geometry (unlike trilateration)
     * - Very effective with mixed signals and outliers
     */
    // AP Count weights from framework document
    private static final double WEIGHTED_CENTROID_SINGLE_AP_WEIGHT = 0.0;     // Not applicable for single AP
    private static final double WEIGHTED_CENTROID_TWO_APS_WEIGHT = 0.8;       // Good for two APs
    private static final double WEIGHTED_CENTROID_THREE_APS_WEIGHT = 0.8;     // Good for three APs
    private static final double WEIGHTED_CENTROID_FOUR_PLUS_APS_WEIGHT = 0.7; // Good for 4+ APs
    
    // Signal quality multipliers from framework document
    private static final double WEIGHTED_CENTROID_STRONG_SIGNAL_MULTIPLIER = 1.0;  // No change with strong signals
    private static final double WEIGHTED_CENTROID_MEDIUM_SIGNAL_MULTIPLIER = 1.0;  // No change with medium signals
    private static final double WEIGHTED_CENTROID_WEAK_SIGNAL_MULTIPLIER = 0.8;    // Moderate reduction with weak signals
    private static final double WEIGHTED_CENTROID_VERY_WEAK_SIGNAL_MULTIPLIER = 0.0; // ×0.0 for very weak signals
    
    // Geometric quality multipliers from framework document
    private static final double WEIGHTED_CENTROID_EXCELLENT_GDOP_MULTIPLIER = 1.0; // No change for excellent geometry
    private static final double WEIGHTED_CENTROID_GOOD_GDOP_MULTIPLIER = 1.1;      // Slight boost with good geometry
    private static final double WEIGHTED_CENTROID_FAIR_GDOP_MULTIPLIER = 1.2;      // Better with fair geometry
    private static final double WEIGHTED_CENTROID_POOR_GDOP_MULTIPLIER = 1.3;      // Best with poor geometry
    
    // Signal distribution multipliers from framework document
    private static final double WEIGHTED_CENTROID_UNIFORM_SIGNALS_MULTIPLIER = 1.0;  // No change for uniform signals
    private static final double WEIGHTED_CENTROID_MIXED_SIGNALS_MULTIPLIER = 1.8;    // Better with mixed signals
    private static final double WEIGHTED_CENTROID_SIGNAL_OUTLIERS_MULTIPLIER = 1.4;  // Best with signal outliers
    
    /**
     * Immutable data class to store weighted position calculation results.
     * 
     * This class encapsulates the results of signal strength to weight conversion
     * and position weighting calculations for a single access point.
     * 
     * Mathematical Representation:
     * For each AP_i with position (lat_i, lon_i, alt_i) and weight w_i:
     * - weightedLat = lat_i × w_i
     * - weightedLon = lon_i × w_i  
     * - weightedAlt = alt_i × w_i (if altitude exists, 0.0 otherwise)
     * - weight = w_i
     */
    private static final class WeightedPositionResult {
        final double weightedLat;
        final double weightedLon;
        final double weightedAlt;
        final double weight;

        WeightedPositionResult(double weightedLat, double weightedLon, double weightedAlt, double weight) {
            this.weightedLat = weightedLat;
            this.weightedLon = weightedLon;
            this.weightedAlt = weightedAlt;
            this.weight = weight;
        }
    }

    /**
     * Immutable data class to store aggregated calculation results.
     * 
     * This class holds the final aggregated weighted sums and total weights
     * before computing the final position coordinates.
     */
    private static final class AggregatedResults {
        final double totalWeightedLat;
        final double totalWeightedLon;
        final double totalWeightedAlt;
        final double totalWeight;
        final double altitudeWeightSum;

        AggregatedResults(double totalWeightedLat, double totalWeightedLon, double totalWeightedAlt, 
                         double totalWeight, double altitudeWeightSum) {
            this.totalWeightedLat = totalWeightedLat;
            this.totalWeightedLon = totalWeightedLon;
            this.totalWeightedAlt = totalWeightedAlt;
            this.totalWeight = totalWeight;
            this.altitudeWeightSum = altitudeWeightSum;
        }
    }

    @Override
    public Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        // Phase 1: Input validation
        if (!isValidInput(wifiScan, knownAPs)) {
            return null;
        }

        // Phase 2: Create AP lookup map for efficient access
        Map<String, WifiAccessPoint> apMap = createAccessPointMap(knownAPs);

        // Phase 3: Calculate weighted positions for each detected AP
        List<WeightedPositionResult> weightedResults = calculateWeightedPositions(wifiScan, apMap);

        // Phase 4: Aggregate all weighted position results
        AggregatedResults aggregated = aggregateWeightedResults(weightedResults);
        
        // Phase 5: Validate that we have meaningful results
        if (aggregated.totalWeight < ZERO_THRESHOLD) {
            return null;
        }

        // Phase 6: Calculate final position, accuracy, and confidence
        return assemblePosition(aggregated, knownAPs, wifiScan);
    }

    /**
     * Validates input parameters for the position calculation.
     * 
     * @param wifiScan List of WiFi scan results to validate
     * @param knownAPs List of known access points to validate
     * @return true if inputs are valid, false otherwise
     */
    private boolean isValidInput(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        return wifiScan != null && !wifiScan.isEmpty() && knownAPs != null && !knownAPs.isEmpty();
    }

    /**
     * Creates a thread-safe map of MAC addresses to known access points.
     * 
     * Mathematical Purpose: O(1) lookup optimization
     * This creates an efficient lookup structure to convert O(n×m) nested loop
     * complexity to O(n+m) for matching scan results to known APs.
     * 
     * @param knownAPs List of known access points
     * @return Thread-safe map from MAC address to WifiAccessPoint
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
     * Calculates weighted position contributions for each detected access point.
     * 
     * Mathematical Process:
     * For each scan result with signal strength RSSI_i:
     * 1. normalized_i = (RSSI_i - RSSI_MAX) / (RSSI_MIN - RSSI_MAX)
     * 2. weight_i = WEIGHT_BASE^normalized_i  
     * 3. weightedPosition_i = position_i × weight_i
     * 
     * The normalization ensures stronger signals (closer to RSSI_MAX) get
     * higher normalized values and thus exponentially higher weights.
     * 
     * @param wifiScan List of WiFi scan results
     * @param apMap Map from MAC address to access point data
     * @return List of weighted position results
     */
    private List<WeightedPositionResult> calculateWeightedPositions(List<WifiScanResult> wifiScan, 
                                                                   Map<String, WifiAccessPoint> apMap) {
        return wifiScan.parallelStream()
            .map(scan -> calculateSingleWeightedPosition(scan, apMap.get(scan.macAddress())))
            .filter(result -> result != null)
            .collect(Collectors.toList());
    }

    /**
     * Calculates the weighted position contribution for a single access point.
     * 
     * Signal Strength to Weight Conversion:
     * The formula weight = WEIGHT_BASE^normalized creates exponential weighting:
     * - Signal at -30 dBm: normalized = 1.0, weight = 10^1.0 = 10.0
     * - Signal at -65 dBm: normalized = 0.5, weight = 10^0.5 ≈ 3.16  
     * - Signal at -100 dBm: normalized = 0.0, weight = 10^0.0 = 1.0
     * 
     * This exponential relationship ensures that small improvements in signal
     * strength (indicating closer proximity) result in disproportionately higher
     * influence on the final position calculation.
     * 
     * @param scan WiFi scan result containing signal strength
     * @param ap Access point data containing position information
     * @return WeightedPositionResult or null if AP is unknown
     */
    private WeightedPositionResult calculateSingleWeightedPosition(WifiScanResult scan, WifiAccessPoint ap) {
        if (ap == null) {
            return null;
        }

        // Calculate normalized signal strength (0.0 to 1.0)
        double normalizedSignal = normalizeSignalStrength(scan.signalStrength());
        
        // Apply exponential weighting to favor stronger signals
        double weight = Math.pow(WEIGHT_BASE, normalizedSignal);

        // Calculate weighted position components
        double weightedLatitude = ap.getLatitude() * weight;
        double weightedLongitude = ap.getLongitude() * weight;
        
        // Handle altitude - only include if available, otherwise contribute 0.0
        double weightedAltitude = (ap.getAltitude() != null) ? ap.getAltitude() * weight : 0.0;

        return new WeightedPositionResult(weightedLatitude, weightedLongitude, weightedAltitude, weight);
    }

    /**
     * Normalizes signal strength to a 0.0-1.0 range for weight calculation.
     * 
     * Mathematical Formula:
     * normalized = (RSSI - RSSI_MAX) / (RSSI_MIN - RSSI_MAX)
     * 
     * Boundary Behavior:
     * - RSSI = -30 dBm → normalized = 1.0 (strongest possible signal)
     * - RSSI = -65 dBm → normalized = 0.5 (medium signal strength)  
     * - RSSI = -100 dBm → normalized = 0.0 (weakest usable signal)
     * 
     * Values outside the range are clamped to [0.0, 1.0] to prevent
     * invalid weight calculations.
     * 
     * @param signalStrength Signal strength in dBm
     * @return Normalized value between 0.0 and 1.0
     */
    private double normalizeSignalStrength(double signalStrength) {
        double normalized = (signalStrength - RSSI_MAX) / (RSSI_MIN - RSSI_MAX);
        return Math.max(0.0, Math.min(1.0, normalized)); // Clamp to [0.0, 1.0]
    }

    /**
     * Aggregates weighted position results into final coordinate sums.
     * 
     * Mathematical Process:
     * Σ(weightedLat_i), Σ(weightedLon_i), Σ(weightedAlt_i), Σ(weight_i)
     * 
     * Uses thread-safe DoubleAdder for parallel aggregation to ensure
     * accurate summation even with concurrent calculations.
     * 
     * Special handling for altitude: Only APs with valid altitude data
     * contribute to the altitude calculation and altitudeWeightSum.
     * 
     * @param weightedResults List of weighted position contributions
     * @return AggregatedResults containing all coordinate and weight sums
     */
    private AggregatedResults aggregateWeightedResults(List<WeightedPositionResult> weightedResults) {
        DoubleAdder totalWeightedLat = new DoubleAdder();
        DoubleAdder totalWeightedLon = new DoubleAdder();
        DoubleAdder totalWeightedAlt = new DoubleAdder();
        DoubleAdder totalWeight = new DoubleAdder();
        DoubleAdder altitudeWeightSum = new DoubleAdder();

        weightedResults.forEach(result -> {
            totalWeightedLat.add(result.weightedLat);
            totalWeightedLon.add(result.weightedLon);
            totalWeightedAlt.add(result.weightedAlt);
            totalWeight.add(result.weight);
            
            // Only count weights for altitude if the AP contributed altitude data
            if (Math.abs(result.weightedAlt) > ZERO_THRESHOLD) {
                altitudeWeightSum.add(result.weight);
            }
        });

        return new AggregatedResults(
            totalWeightedLat.doubleValue(),
            totalWeightedLon.doubleValue(), 
            totalWeightedAlt.doubleValue(),
            totalWeight.doubleValue(),
            altitudeWeightSum.doubleValue()
        );
    }

    /**
     * Assembles the final position from aggregated results and calculates metrics.
     * 
     * Final Position Calculation:
     * - latitude = Σ(weightedLat_i) / Σ(weight_i)
     * - longitude = Σ(weightedLon_i) / Σ(weight_i)  
     * - altitude = Σ(weightedAlt_i) / Σ(altitudeWeight_i) or 0.0 if no altitude data
     * 
     * Confidence Calculation:
     * confidence = min(MAX_CONFIDENCE, coverage × BASE_CONFIDENCE)
     * where coverage = scanned_APs / known_APs (measures AP visibility)
     * 
     * @param aggregated Aggregated weighted results
     * @param knownAPs List of known access points (for confidence calculation)
     * @param wifiScan List of scan results (for coverage calculation)
     * @return Final Position with calculated coordinates, accuracy, and confidence
     */
    private Position assemblePosition(AggregatedResults aggregated, List<WifiAccessPoint> knownAPs, 
                                    List<WifiScanResult> wifiScan) {
        // Calculate final coordinates using weighted averages
        double finalLatitude = aggregated.totalWeightedLat / aggregated.totalWeight;
        double finalLongitude = aggregated.totalWeightedLon / aggregated.totalWeight;
        
        // Calculate altitude only if we have valid altitude contributions
        double finalAltitude = (aggregated.altitudeWeightSum > ZERO_THRESHOLD) 
            ? aggregated.totalWeightedAlt / aggregated.altitudeWeightSum 
            : 0.0;

        // Calculate accuracy as average of contributing APs' horizontal accuracy
        double accuracy = calculateAverageAccuracy(knownAPs);
        
        // Calculate confidence based on AP coverage and algorithm reliability
        double confidence = calculateConfidence(wifiScan.size(), knownAPs.size());

        return new Position(finalLatitude, finalLongitude, finalAltitude, accuracy, confidence);
    }

    /**
     * Calculates average horizontal accuracy from known access points.
     * 
     * Uses parallel stream for efficiency with large AP datasets.
     * Falls back to DEFAULT_ACCURACY_METERS if no accuracy data is available.
     * 
     * @param knownAPs List of known access points
     * @return Average horizontal accuracy in meters
     */
    private double calculateAverageAccuracy(List<WifiAccessPoint> knownAPs) {
        return knownAPs.parallelStream()
            .mapToDouble(WifiAccessPoint::getHorizontalAccuracy)
            .average()
            .orElse(DEFAULT_ACCURACY_METERS);
    }

    /**
     * Calculates algorithm confidence based on AP coverage.
     * 
     * Coverage Ratio Impact:
     * - Higher coverage (more visible APs) increases confidence
     * - Perfect coverage (ratio = 1.0) yields maximum possible confidence
     * - Confidence is capped at MAX_CONFIDENCE to reflect algorithm limitations
     * 
     * Mathematical Formula:
     * confidence = min(MAX_CONFIDENCE, (scannedCount / knownCount) × BASE_CONFIDENCE)
     * 
     * @param scannedCount Number of APs detected in scan
     * @param knownCount Number of APs in reference database
     * @return Confidence level between 0.0 and MAX_CONFIDENCE
     */
    private double calculateConfidence(int scannedCount, int knownCount) {
        double coverage = (double) scannedCount / knownCount;
        return Math.min(MAX_CONFIDENCE, coverage * BASE_CONFIDENCE);
    }

    @Override
    public double getConfidence() {
        return BASE_CONFIDENCE;
    }

    @Override
    public String getName() {
        return "weighted_centroid";
    }
    
    @Override
    public double getBaseWeight(APCountFactor factor) {
        switch (factor) {
            case SINGLE_AP:
                return WEIGHTED_CENTROID_SINGLE_AP_WEIGHT;      // Not applicable for single AP
            case TWO_APS:
                return WEIGHTED_CENTROID_TWO_APS_WEIGHT;        // Good for two APs
            case THREE_APS:
                return WEIGHTED_CENTROID_THREE_APS_WEIGHT;      // Good for three APs
            case FOUR_PLUS_APS:
                return WEIGHTED_CENTROID_FOUR_PLUS_APS_WEIGHT;  // Good for 4+ APs
            default:
                return 0.0;
        }
    }
    
    @Override
    public double getSignalQualityMultiplier(SignalQualityFactor factor) {
        switch (factor) {
            case STRONG_SIGNAL:
                return WEIGHTED_CENTROID_STRONG_SIGNAL_MULTIPLIER;
            case MEDIUM_SIGNAL:
                return WEIGHTED_CENTROID_MEDIUM_SIGNAL_MULTIPLIER;
            case WEAK_SIGNAL:
                return WEIGHTED_CENTROID_WEAK_SIGNAL_MULTIPLIER;
            case VERY_WEAK_SIGNAL:
                return WEIGHTED_CENTROID_VERY_WEAK_SIGNAL_MULTIPLIER;
            default:
                return WEIGHTED_CENTROID_MEDIUM_SIGNAL_MULTIPLIER;
        }
    }
    
    @Override
    public double getGeometricQualityMultiplier(GeometricQualityFactor factor) {
        switch (factor) {
            case EXCELLENT_GDOP:
                return WEIGHTED_CENTROID_EXCELLENT_GDOP_MULTIPLIER;
            case GOOD_GDOP:
                return WEIGHTED_CENTROID_GOOD_GDOP_MULTIPLIER;
            case FAIR_GDOP:
                return WEIGHTED_CENTROID_FAIR_GDOP_MULTIPLIER;
            case POOR_GDOP:
                return WEIGHTED_CENTROID_POOR_GDOP_MULTIPLIER;
            default:
                return WEIGHTED_CENTROID_GOOD_GDOP_MULTIPLIER;
        }
    }
    
    @Override
    public double getSignalDistributionMultiplier(SignalDistributionFactor factor) {
        switch (factor) {
            case UNIFORM_SIGNALS:
                return WEIGHTED_CENTROID_UNIFORM_SIGNALS_MULTIPLIER;
            case MIXED_SIGNALS:
                return WEIGHTED_CENTROID_MIXED_SIGNALS_MULTIPLIER;
            case SIGNAL_OUTLIERS:
                return WEIGHTED_CENTROID_SIGNAL_OUTLIERS_MULTIPLIER;
            default:
                return WEIGHTED_CENTROID_MIXED_SIGNALS_MULTIPLIER;
        }
    }
} 