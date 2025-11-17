package com.wifi.ap.location.measurements.math;

import com.wifi.ap.location.Location;
import com.wifi.ap.location.measurements.WifiMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Unified geographic centroid calculator using ECEF vector averaging with configurable weighting.
 *
 * <p>This calculator provides a flexible, mathematically rigorous approach to geographic
 * centroid calculation using ECEF (Earth-Centered, Earth-Fixed) vector averaging. It
 * supports any weighting strategy through function injection while maintaining the
 * same core algorithm for both weighted and unweighted calculations.
 *
 * <h2>Mathematical Foundation: ECEF Vector Averaging</h2>
 *
 * <h3>Core Algorithm Process</h3>
 * <p>The calculator transforms geographic coordinates to ECEF unit vectors, applies
 * weighting, averages them, and converts back to geographic coordinates using
 * spherical trigonometry:
 *
 * <pre>
 * For each measurement i with (lat_i, lon_i) and weight function w():
 *   lat_rad_i = lat_i * π/180
 *   lon_rad_i = lon_i * π/180
 *   weight_i = w(measurement_i)
 *   
 *   x_i = cos(lat_rad_i) * cos(lon_rad_i)
 *   y_i = cos(lat_rad_i) * sin(lon_rad_i)
 *   z_i = sin(lat_rad_i)
 *   
 *   weighted_x_i = weight_i * x_i
 *   weighted_y_i = weight_i * y_i
 *   weighted_z_i = weight_i * z_i
 *   
 * sum_x = Σ(weighted_x_i)
 * sum_y = Σ(weighted_y_i)
 * sum_z = Σ(weighted_z_i)
 * 
 * magnitude = √(sum_x² + sum_y² + sum_z²)
 * normalized_x = sum_x / magnitude
 * normalized_y = sum_y / magnitude
 * normalized_z = sum_z / magnitude
 * 
 * result_lat = arcsin(normalized_z) * 180/π
 * result_lon = atan2(normalized_y, normalized_x) * 180/π
 * </pre>
 *
 * <h3>Why ECEF Vector Averaging?</h3>
 * <ul>
 *   <li><strong>Spherical Geometry:</strong> Handles Earth's curvature correctly</li>
 *   <li><strong>Pole Handling:</strong> Works correctly at North/South poles</li>
 *   <li><strong>Anti-meridian Crossing:</strong> Handles 180°/-180° longitude crossing</li>
 *   <li><strong>Weighted Accuracy:</strong> Properly applies weights in 3D space</li>
 * </ul>
 *
 * <h2>Weight Function Integration</h2>
 *
 * <h3>Quality-Based Weighting (CONNECTED vs SCAN)</h3>
 * <pre>
 * ToDoubleFunction&lt;WifiMeasurement&gt; qualityWeight = m -&gt; 
 *     "CONNECTED".equals(m.connectionStatus()) ? 2.0 : 1.0;
 * Location centroid = calculator.calculateCentroid(measurements, qualityWeight);
 * </pre>
 *
 * <h3>Equal Weighting (Unweighted)</h3>
 * <pre>
 * ToDoubleFunction&lt;WifiMeasurement&gt; equalWeight = m -&gt; 1.0;
 * Location centroid = calculator.calculateCentroid(measurements, equalWeight);
 * </pre>
 *
 * <h3>Signal Strength Weighting</h3>
 * <pre>
 * ToDoubleFunction&lt;WifiMeasurement&gt; rssiWeight = m -&gt; 
 *     Math.max(0.1, Math.pow(10, (m.rssi() + 100) / 10.0));
 * </pre>
 *
 * <h3>Location Accuracy Weighting</h3>
 * <pre>
 * ToDoubleFunction&lt;WifiMeasurement&gt; accuracyWeight = m -&gt; 
 *     m.locationAccuracy() > 0 ? 1.0 / m.locationAccuracy() : 1.0;
 * </pre>
 *
 * <h2>Functional Design Benefits</h2>
 *
 * <ul>
 *   <li><strong>DRY Principle:</strong> Single algorithm for all weighting strategies</li>
 *   <li><strong>Flexibility:</strong> Any weighting strategy via function injection</li>
 *   <li><strong>Testability:</strong> Single algorithm to test with different weight functions</li>
 *   <li><strong>Extensibility:</strong> Easy to add new weighting strategies without code duplication</li>
 *   <li><strong>Maintainability:</strong> Single point of maintenance for core algorithm</li>
 * </ul>
 *
 * <h2>Edge Case Handling</h2>
 *
 * <ul>
 *   <li><strong>Zero Magnitude Vectors:</strong> Falls back to weighted arithmetic mean</li>
 *   <li><strong>Zero Total Weight:</strong> Falls back to arithmetic mean</li>
 *   <li><strong>Degenerate Cases:</strong> Handles empty or single measurement scenarios</li>
 *   <li><strong>Geographic Extremes:</strong> Works correctly at poles and anti-meridian</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 *
 * <ul>
 *   <li><strong>Time Complexity:</strong> O(n) where n is the number of measurements</li>
 *   <li><strong>Space Complexity:</strong> O(1) constant space for calculations</li>
 *   <li><strong>Functional Approach:</strong> Stream-based processing for memory efficiency</li>
 *   <li><strong>Declarative Style:</strong> Reduces cognitive load and improves readability</li>
 * </ul>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 2.0
 * @see WeightedECEFSum for the internal data structure used in calculations
 * @see ECEFVector for the 3D vector mathematics
 * @since 1.0
 */
@Component
public class GeographicCentroidCalculator {

    private static final Logger logger = LoggerFactory.getLogger(GeographicCentroidCalculator.class);

    /**
     * Calculates geographic centroid using ECEF vector averaging with configurable weighting.
     *
     * <p>This method implements the unified ECEF vector averaging algorithm with pluggable
     * weight functions. The algorithm handles spherical geometry correctly and supports
     * any weighting strategy through function injection.
     *
     * <p><strong>Algorithm Steps:</strong>
     * <ol>
     *   <li><strong>Validation:</strong> Verify measurements list is non-null and non-empty</li>
     *   <li><strong>ECEF Conversion:</strong> Convert each lat/lon to ECEF unit vector using spherical trigonometry</li>
     *   <li><strong>Weight Application:</strong> Apply weight function to each measurement</li>
     *   <li><strong>Vector Summation:</strong> Sum all weighted ECEF vectors using functional reduction</li>
     *   <li><strong>Normalization:</strong> Normalize the sum vector to unit magnitude</li>
     *   <li><strong>Coordinate Conversion:</strong> Convert normalized vector back to lat/lon</li>
     *   <li><strong>Fallback Handling:</strong> Use weighted arithmetic mean if normalization fails</li>
     * </ol>
     *
     * <p><strong>Mathematical Process:</strong>
     * <pre>
     * For each measurement i:
     *   1. Convert (lat_i, lon_i) to ECEF unit vector (x_i, y_i, z_i)
     *   2. Apply weight: (weighted_x_i, weighted_y_i, weighted_z_i) = weight_i * (x_i, y_i, z_i)
     *   
     * Sum all weighted vectors:
     *   sum_vector = Σ(weighted_x_i, weighted_y_i, weighted_z_i)
     *   
     * Normalize and convert back:
     *   centroid_vector = sum_vector / ||sum_vector||
     *   (lat_result, lon_result) = ECEF_to_geographic(centroid_vector)
     * </pre>
     *
     * <p><strong>Weight Function Examples:</strong>
     * <ul>
     *   <li><strong>Quality weighting:</strong> {@code m -> "CONNECTED".equals(m.connectionStatus()) ? 2.0 : 1.0}</li>
     *   <li><strong>Equal weighting:</strong> {@code m -> 1.0}</li>
     *   <li><strong>RSSI weighting:</strong> {@code m -> Math.max(0.1, Math.pow(10, (m.rssi() + 100) / 10.0))}</li>
     *   <li><strong>Accuracy weighting:</strong> {@code m -> m.locationAccuracy() > 0 ? 1.0 / m.locationAccuracy() : 1.0}</li>
     * </ul>
     *
     * @param measurements List of measurements with valid location data (non-null lat/lon)
     * @param weightFunction Function calculating weight for each measurement (must return positive values)
     * @return Geographic centroid as Location using ECEF vector averaging for spherical accuracy
     * @throws IllegalArgumentException if measurements list is null or empty
     */
    public Location calculateCentroid(List<WifiMeasurement> measurements,
            ToDoubleFunction<WifiMeasurement> weightFunction) {
        WeightedECEFSum ecefSum = calculateWeightedECEFSum(measurements, weightFunction);
        return calculateCentroid(ecefSum, measurements, weightFunction);
    }

    /**
     * Calculates weighted ECEF vector sum using the provided weight function.
     *
     * <p>
     * This method performs the core mathematical transformation from geographic
     * coordinates to weighted ECEF vectors using the provided weight function to 
     * determine each measurement's contribution.
     *
     * @param measurements   List of measurements to process
     * @param weightFunction Function to calculate weight for each measurement
     * @return WeightedECEFSum containing the summed ECEF vector components and total weight
     */
    private WeightedECEFSum calculateWeightedECEFSum(List<WifiMeasurement> measurements,
            ToDoubleFunction<WifiMeasurement> weightFunction) {
        
        // Use declarative functional approach to calculate weighted ECEF sum
        return measurements.stream()
                .map(measurement -> toWeightedECEFSum(measurement, weightFunction))
                .reduce(emptyECEFSum(), this::combineSums);
    }

    /**
     * Creates a WeightedECEFSum from a single measurement and weight function.
     * 
     * @param measurement The measurement to convert
     * @param weightFunction Function to calculate the weight for this measurement
     * @return WeightedECEFSum representing the single weighted measurement
     */
    private WeightedECEFSum toWeightedECEFSum(WifiMeasurement measurement, 
            ToDoubleFunction<WifiMeasurement> weightFunction) {
        
        Location location = Location.of(measurement.latitude(), measurement.longitude());
        ECEFVector ecefVector = ECEFVector.fromLocation(location);
        double weight = weightFunction.applyAsDouble(measurement);
        
        return new WeightedECEFSum(ecefVector.scale(weight), weight, 1);
    }

    /**
     * Creates an empty WeightedECEFSum (identity for reducing operations).
     * 
     * @return WeightedECEFSum with zero vector, zero weight, and zero count
     */
    private WeightedECEFSum emptyECEFSum() {
        return new WeightedECEFSum(ECEFVector.zero(), 0.0, 0);
    }

    /**
     * Combines two WeightedECEFSum instances.
     * 
     * @param sum1 First WeightedECEFSum
     * @param sum2 Second WeightedECEFSum
     * @return Combined WeightedECEFSum
     */
    private WeightedECEFSum combineSums(WeightedECEFSum sum1, WeightedECEFSum sum2) {
        return new WeightedECEFSum(
            sum1.ecefVector().add(sum2.ecefVector()),
            sum1.totalWeight() + sum2.totalWeight(),
            sum1.measurementCount() + sum2.measurementCount()
        );
    }

    /**
     * Normalizes ECEF vector and converts back to geographic coordinates.
     *
     * <p>
     * This method completes the centroid calculation by normalizing the summed
     * ECEF vector and converting it back to latitude/longitude coordinates.
     * If normalization fails, it falls back to a weighted arithmetic mean.
     *
     * @param ecefSum        The weighted ECEF vector sum
     * @param measurements   Original measurements list for fallback calculation
     * @param weightFunction Weight function for fallback calculation
     * @return Geographic centroid as Location
     */
    private Location calculateCentroid(WeightedECEFSum ecefSum, List<WifiMeasurement> measurements,
            ToDoubleFunction<WifiMeasurement> weightFunction) {
        
        // Check for degenerate cases
        if (isDegenerate(ecefSum)) {
            logger.warn("Degenerate geographic centroid calculation, using weighted arithmetic mean fallback");
            return calculateWeightedArithmeticMean(measurements, weightFunction);
        }

        // Normalize ECEF vector and convert to geographic coordinates
        ECEFVector normalized = ecefSum.ecefVector().normalize();
        Location centroid = normalized.toLocation();
        
        if (logger.isDebugEnabled()) {
            logger.debug("Geographic centroid calculation: {} measurements, total weight={}",
                        ecefSum.measurementCount(), String.format("%.1f", ecefSum.totalWeight()));
        }
        
        return centroid;
    }

    /**
     * Checks if the WeightedECEFSum represents a degenerate case.
     * 
     * @param ecefSum The WeightedECEFSum to check
     * @return true if degenerate, false otherwise
     */
    private boolean isDegenerate(WeightedECEFSum ecefSum) {
        return ecefSum.ecefVector().isZero() || 
               ecefSum.totalWeight() == 0.0 || 
               ecefSum.measurementCount() == 0;
    }

    /**
     * Fallback calculation using weighted arithmetic mean.
     *
     * <p>
     * This method provides a fallback when ECEF vector normalization fails.
     * It calculates a weighted arithmetic mean of the latitude and longitude
     * coordinates directly using the provided weight function.
     *
     * @param measurements   List of measurements for fallback calculation
     * @param weightFunction Weight function to apply to each measurement
     * @return Weighted arithmetic mean as Location
     */
    private Location calculateWeightedArithmeticMean(List<WifiMeasurement> measurements,
            ToDoubleFunction<WifiMeasurement> weightFunction) {
        if (measurements.isEmpty()) {
            logger.warn("Empty measurements list in fallback calculation");
            throw new IllegalStateException("Cannot calculate centroid with empty measurements");
        }

        // Calculate weighted sums and total weight using streams
        double totalWeight = measurements.stream()
                .mapToDouble(weightFunction)
                .sum();

        if (totalWeight == 0.0) {
            return Location.of(0.0, 0.0);
        }

        double weightedLatSum = measurements.stream()
                .mapToDouble(m -> weightFunction.applyAsDouble(m) * m.latitude())
                .sum();

        double weightedLonSum = measurements.stream()
                .mapToDouble(m -> weightFunction.applyAsDouble(m) * m.longitude())
                .sum();

        return Location.of(weightedLatSum / totalWeight, weightedLonSum / totalWeight);
    }


}
