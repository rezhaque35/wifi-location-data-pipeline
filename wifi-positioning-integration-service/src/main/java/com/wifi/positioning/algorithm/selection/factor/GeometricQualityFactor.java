package com.wifi.positioning.algorithm.selection.factor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.wifi.positioning.algorithm.util.GDOPCalculator;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

/**
 * Enum representing different geometric quality scenarios based on GDOP (Geometric Dilution of Precision)
 * that affect algorithm weights.
 * 
 * GEOMETRIC DILUTION OF PRECISION (GDOP) METHODOLOGY:
 * 
 * This classification system is based on the industry-standard GDOP ratings used in satellite
 * navigation and adapted for WiFi-based indoor positioning systems. GDOP quantifies how the
 * geometric arrangement of reference points (access points) affects positioning accuracy.
 * 
 * INDUSTRY STANDARD REFERENCE:
 * [1] Wikipedia: "Dilution of precision (navigation)"
 *     https://en.wikipedia.org/wiki/Dilution_of_precision_(navigation)
 *     - Comprehensive overview of GDOP theory and standard classification thresholds
 * 
 * STANDARD GDOP CLASSIFICATION (from satellite navigation):
 * 
 * | DOP Value | Rating    | Description |
 * |-----------|-----------|-------------|
 * | < 1       | Ideal     | Highest possible confidence, most sensitive applications |
 * | 1-2       | Excellent | Accurate enough for all but most sensitive applications |
 * | 2-5       | Good      | Minimum appropriate for business decisions, reliable navigation |
 * | 5-10      | Moderate  | Usable for calculations but fix quality could be improved |
 * | 10-20     | Fair      | Low confidence level, very rough estimates only |
 * | > 20      | Poor      | Inaccurate measurements, should be discarded |
 * 
 * WIFI POSITIONING ADAPTATION:
 * Our implementation adapts these standards for indoor WiFi positioning constraints:
 * 
 * EXCELLENT_GDOP (< 2.0):
 * - Equivalent to "Ideal" to "Excellent" satellite navigation ratings
 * - Optimal access point geometric distribution
 * - Enables all positioning algorithms with maximum confidence
 * - Typical scenario: APs well-distributed around user position
 * - Algorithm Impact: Enhances trilateration and maximum likelihood weights (×1.3, ×1.2)
 * 
 * GOOD_GDOP (2.0-4.0):
 * - Equivalent to "Good" satellite navigation rating
 * - Good geometric distribution suitable for reliable positioning
 * - Most algorithms perform well with standard weights
 * - Typical scenario: APs reasonably spaced with minor clustering
 * - Algorithm Impact: Standard weights with slight trilateration reduction (×0.9)
 * 
 * FAIR_GDOP (4.0-6.0):
 * - Bridging "Good" to "Moderate" satellite navigation ratings
 * - Acceptable geometry but with reduced precision expectations
 * - Favors robust algorithms over precision methods
 * - Typical scenario: APs somewhat clustered or irregularly distributed
 * - Algorithm Impact: Reduces trilateration weight (×0.6), increases centroid weight (×1.2)
 * 
 * POOR_GDOP (> 6.0):
 * - Equivalent to "Moderate" and worse satellite navigation ratings
 * - Poor geometric distribution significantly affects accuracy
 * - Only robust algorithms should be used
 * - Typical scenario: APs highly clustered or forming poor geometric patterns
 * - Algorithm Impact: Severely reduces trilateration (×0.3), emphasizes centroid methods (×1.3)
 * 
 * COLLINEAR (Special Case):
 * - Not applicable to satellite navigation (satellites never collinear from Earth)
 * - Unique to terrestrial positioning where reference points can align linearly
 * - Makes trilateration mathematically impossible (×0.0 weight)
 * - Requires alternative positioning approaches
 * - Algorithm Impact: Disables trilateration, maximizes weighted centroid (×1.4)
 * 
 * THRESHOLD ADAPTATION RATIONALE:
 * - Indoor WiFi positioning has tighter geometric constraints than satellite systems
 * - Lower thresholds (2.0, 4.0, 6.0) vs. satellite standards (2.0, 5.0, 10.0) reflect:
 *   * Limited 2D positioning (no altitude diversity like satellites)
 *   * Constrained indoor environments with potential obstructions
 *   * Need for more conservative quality assessment in confined spaces
 * 
 * MATHEMATICAL FOUNDATION:
 * GDOP calculation follows standard navigation principles:
 * - GDOP = √(trace(Q)) where Q = (H^T × H)^(-1)
 * - H is the geometry matrix with unit vectors from position to each AP
 * - Lower GDOP values indicate better geometric distribution
 * - Values scale with position uncertainty magnification factor
 * 
 * Based on the algorithm selection framework documentation and adapted from
 * satellite navigation standards for WiFi indoor positioning applications.
 */
public enum GeometricQualityFactor {
    /** Excellent geometric distribution (GDOP < 2) */
    EXCELLENT_GDOP(0.0, 2.0),
    
    /** Good geometric distribution (GDOP 2-4) */
    GOOD_GDOP(2.0, 4.0),
    
    /** Fair geometric distribution (GDOP 4-6) */
    FAIR_GDOP(4.0, 6.0),
    
    /** Poor geometric distribution (GDOP > 6) */
    POOR_GDOP(6.0, Double.POSITIVE_INFINITY),
    
    /** Collinear AP arrangement (special case that makes trilateration impossible) */
    COLLINEAR(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    
    private static final Logger logger = LoggerFactory.getLogger(GeometricQualityFactor.class);
    private final double lowerBound;
    private final double upperBound;
    
    // Constants for geometric quality determination
    private static final double SIGNAL_WEIGHT_FACTOR = 10.0; // Base for signal weight (10^(dBm/10))
    private static final int MIN_AP_COUNT_FOR_GEOMETRY = 3; // Minimum APs needed for valid geometry
    
    GeometricQualityFactor(double lowerBound, double upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }
    
    /**
     * Get the lower bound GDOP value for this factor.
     * 
     * @return The lower bound GDOP value
     */
    public double getLowerBound() {
        return lowerBound;
    }
    
    /**
     * Get the upper bound GDOP value for this factor.
     * 
     * @return The upper bound GDOP value
     */
    public double getUpperBound() {
        return upperBound;
    }
    
    /**
     * Determine the appropriate geometric quality factor based on the GDOP value.
     * 
     * @param gdop The Geometric Dilution of Precision value
     * @return The corresponding GeometricQualityFactor
     */
    public static GeometricQualityFactor fromGDOP(double gdop) {
        if (gdop < 2.0) {
            return EXCELLENT_GDOP;
        } else if (gdop < 4.0) {
            return GOOD_GDOP;
        } else if (gdop < 6.0) {
            return FAIR_GDOP;
        } else {
            return POOR_GDOP;
        }
    }

    private static final double COLLINEARITY_THRESHOLD = 0.0002; // Maximum allowed deviation from line of best fit
    private static final double AREA_THRESHOLD = 0.0001; // Threshold for area-based check
    private static final double SINGULARITY_THRESHOLD = 1e-10; // Threshold for near-zero values

    /**
     * Checks if a list of positions are collinear (lie on the same line).
     * Uses the maximum deviation from the line of best fit to determine collinearity.
     * The method is designed to be robust to small deviations from perfect collinearity.
     *
     * @param positions List of positions to check
     * @return true if positions are collinear, false otherwise
     */
    public static boolean isCollinear(List<Position> positions) {
        if (positions == null || positions.size() < 3) {
            return false;
        }

        // Calculate mean position
        double meanLat = positions.stream().mapToDouble(Position::latitude).average().orElse(0);
        double meanLon = positions.stream().mapToDouble(Position::longitude).average().orElse(0);

        // Calculate covariance matrix elements
        double covLatLat = 0, covLonLon = 0, covLatLon = 0;
        for (Position p : positions) {
            double dLat = p.latitude() - meanLat;
            double dLon = p.longitude() - meanLon;
            covLatLat += dLat * dLat;
            covLonLon += dLon * dLon;
            covLatLon += dLat * dLon;
        }
        int n = positions.size();
        covLatLat /= n;
        covLonLon /= n;
        covLatLon /= n;

        // Check for perfect horizontal or vertical lines
        if (covLatLat < SINGULARITY_THRESHOLD || covLonLon < SINGULARITY_THRESHOLD) {
            return true;
        }

        // Calculate line of best fit parameters
        // Additional explicit check to satisfy static analysis tools
        if (Math.abs(covLonLon) < SINGULARITY_THRESHOLD) {
            // This should never happen due to the check above, but satisfies SonarQube
            return true;
        }
        
        double slope = covLatLon / covLonLon;
        double intercept = meanLat - slope * meanLon;

        // Calculate maximum deviation from line
        double maxDeviation = 0;
        for (Position p : positions) {
            double expectedLat = slope * p.longitude() + intercept;
            double deviation = Math.abs(p.latitude() - expectedLat);
            maxDeviation = Math.max(maxDeviation, deviation);
        }

        logger.debug("Max deviation: {}, Slope: {}", maxDeviation, slope);
        return maxDeviation <= COLLINEARITY_THRESHOLD;
    }
    
    /**
     * Checks if the access points in the valid scans are collinear.
     * 
     * @param validScans List of valid WiFi scan results
     * @param apMap Map of known access points by MAC address
     * @return true if the access points are collinear, false otherwise
     */
    public static boolean checkCollinearity(List<WifiScanResult> validScans, Map<String, WifiAccessPoint> apMap) {
        if (validScans == null || validScans.size() < 3 || apMap == null) {
            return false;
        }
        
        return isCollinear(
            validScans.stream()
                .map(scan -> apMap.get(scan.macAddress()))
                .filter(ap -> ap != null && ap.getLatitude() != null && ap.getLongitude() != null)
                .map(ap -> new Position(ap.getLatitude(), ap.getLongitude(), 0.0, 0.0, 0.0))
                .collect(Collectors.toList())
        );
    }

    /**
     * Determines the geometric quality factor based on AP positions.
     * 
     * This method uses GDOP (Geometric Dilution of Precision) to assess how the
     * geometric configuration of access points affects position accuracy:
     * - EXCELLENT_GDOP: GDOP < 2.0 (optimal AP geometry)
     * - GOOD_GDOP: 2.0 ≤ GDOP < 4.0 (good AP geometry)
     * - FAIR_GDOP: 4.0 ≤ GDOP < 6.0 (acceptable AP geometry)
     * - POOR_GDOP: GDOP ≥ 6.0 (poor AP geometry)
     * - COLLINEAR: Special case where APs lie approximately on a straight line
     * 
     * The calculation includes:
     * 1. Checking if APs are collinear (arranged in a line)
     * 2. Estimating user position using weighted centroid (weights based on signal strength)
     * 3. Creating an array of AP coordinates
     * 4. Computing GDOP using the GDOPCalculator
     * 5. Mapping the GDOP value to the appropriate GeometricQualityFactor
     * 
     * @param wifiScans List of WiFi scan results
     * @param apMap Map of known access points by MAC address
     * @return The corresponding GeometricQualityFactor
     */
    public static GeometricQualityFactor determineGeometricQuality(List<WifiScanResult> wifiScans, 
                                                          Map<String, WifiAccessPoint> apMap) {
        // Check if we have enough APs for a meaningful geometry calculation
        if (wifiScans == null || wifiScans.size() < MIN_AP_COUNT_FOR_GEOMETRY || apMap == null) {
            return GeometricQualityFactor.POOR_GDOP;
        }
        
        // First check for collinearity as a special case
        if (checkCollinearity(wifiScans, apMap)) {
            return GeometricQualityFactor.COLLINEAR;
        }
        
        // Extract APs with known positions
        List<WifiAccessPoint> validAPs = wifiScans.stream()
            .map(scan -> apMap.get(scan.macAddress()))
            .filter(ap -> ap != null && ap.getLatitude() != null && ap.getLongitude() != null)
            .collect(Collectors.toList());
        
        // Create a map of MAC address to signal strength for weighting
        Map<String, Double> signalMap = wifiScans.stream()
            .collect(Collectors.toMap(
                WifiScanResult::macAddress,
                WifiScanResult::signalStrength,
                (a, b) -> a  // If duplicate keys, take the first one
            ));
        
        // Check if we have enough valid APs
        if (validAPs.size() < MIN_AP_COUNT_FOR_GEOMETRY) {
            return GeometricQualityFactor.POOR_GDOP;
        }

        // Calculate weighted centroid based on signal strength as an estimate for user position
        double totalWeight = 0, weightedLat = 0, weightedLon = 0;
        
        for (WifiAccessPoint ap : validAPs) {
            // Signal strength is negative, so we need to take power(10, signal/10) for proper weighting
            // Stronger signals (less negative) will have higher weights
            double signalStrength = signalMap.getOrDefault(ap.getMacAddress(), -80.0);
            double weight = Math.pow(SIGNAL_WEIGHT_FACTOR, signalStrength / 10.0);
            
            weightedLat += ap.getLatitude() * weight;
            weightedLon += ap.getLongitude() * weight;
            totalWeight += weight;
        }
        
        // Normalize weighted coordinates
        double[] estimatedPosition = new double[2];
        if (totalWeight > 0) {
            estimatedPosition[0] = weightedLat / totalWeight;
            estimatedPosition[1] = weightedLon / totalWeight;
        } else {
            // If weighting fails, use simple average
            estimatedPosition[0] = validAPs.stream().mapToDouble(WifiAccessPoint::getLatitude).average().orElse(0);
            estimatedPosition[1] = validAPs.stream().mapToDouble(WifiAccessPoint::getLongitude).average().orElse(0);
        }
        
        // Create array of AP coordinates for GDOP calculation
        double[][] apCoordinates = validAPs.stream()
            .map(ap -> new double[] { ap.getLatitude(), ap.getLongitude() })
            .toArray(double[][]::new);
        
        // Calculate GDOP (include bias term for 2D positioning)
        double gdop = GDOPCalculator.calculateGDOP(apCoordinates, estimatedPosition, true);
        
        // Map GDOP to GeometricQualityFactor
        return fromGDOP(gdop);
    }
} 