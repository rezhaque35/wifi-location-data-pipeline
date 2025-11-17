package com.wifi.ap.location.measurements.math;

import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a collection of WiFi measurement distances with comprehensive statistical operations.
 *
 * <p>This record encapsulates a list of WiFiMeasurementDistance objects and provides
 * centralized methods for statistical analysis commonly needed in outlier detection
 * and spatial distribution analysis. It serves as the primary interface for
 * distance-based calculations across the WiFi localization system.
 *
 * <h2>Architectural Role</h2>
 *
 * <p>This class implements the <strong>Single Responsibility Principle</strong> by centralizing
 * all distance-related statistical operations that were previously scattered across
 * multiple services. It eliminates code duplication and ensures consistent behavior across:
 * <ul>
 *   <li><strong>GlobalOutlierDetector:</strong> MAD calculation and outlier identification</li>
 *   <li><strong>WiFiHotspotDetectionService:</strong> Spatial distribution analysis</li>
 *   <li><strong>Quality Assessment Services:</strong> Distance-based filtering and validation</li>
 * </ul>
 *
 * <h2>Mathematical Foundation</h2>
 *
 * <h3>1. Median Absolute Deviation (MAD)</h3>
 * <p><strong>What MAD is:</strong>
 * <p>MAD is a robust measure of statistical dispersion that quantifies how "spread out" a dataset is.
 * Unlike standard deviation, MAD is resistant to outliers, making it ideal for outlier detection.
 *
 * <p><strong>Mathematical Definition:</strong>
 * <pre>
 * MAD = fromDistanceMedian(|x_i - fromDistanceMedian(x)|)
 *
 * Where:
 * - x_i = individual data points (distances from centroid)
 * - fromDistanceMedian(x) = fromDistanceMedian of all data points
 * - |x_i - fromDistanceMedian(x)| = absolute deviation of each point from the fromDistanceMedian
 * - fromDistanceMedian(|...|) = fromDistanceMedian of all absolute deviations
 * </pre>
 *
 * <p><strong>Why MAD is Superior to Standard Deviation for Outlier Detection:</strong>
 * <ul>
 *   <li><strong>Robustness:</strong> Not influenced by extreme values (outliers themselves)</li>
 *   <li><strong>Stability:</strong> More stable with small sample sizes</li>
 *   <li><strong>Interpretability:</strong> Represents the "typical" deviation from the center</li>
 *   <li><strong>Non-parametric:</strong> No assumptions about data distribution</li>
 * </ul>
 *
 * <h3>2. Outlier Identification Process</h3>
 * <p><strong>Mathematical Process:</strong>
 * <ol>
 *   <li>Filter measurements where distance > threshold</li>
 *   <li>Extract measurement objects from filtered results</li>
 *   <li>Return as Stream for flexible downstream processing</li>
 * </ol>
 *
 * <h2>Key Features</h2>
 *
 * <ul>
 *   <li><strong>Robust Statistics:</strong> MAD-based calculations resistant to outliers</li>
 *   <li><strong>Flexible Outlier Detection:</strong> Stream-based filtering for various use cases</li>
 *   <li><strong>Statistical Operations:</strong> Median, MAD, and threshold-based analysis</li>
 *   <li><strong>Type Safety:</strong> Compile-time safety for distance-based operations</li>
 *   <li><strong>Performance Optimized:</strong> Efficient stream processing for large datasets</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 *
 * <ul>
 *   <li><strong>Time Complexity:</strong> O(n log n) for fromDistanceMedian calculations, O(n) for filtering</li>
 *   <li><strong>Space Complexity:</strong> O(n) for distance storage and statistical operations</li>
 *   <li><strong>Stream Processing:</strong> Lazy evaluation for memory efficiency</li>
 *   <li><strong>Statistical Operations:</strong> Optimized Apache Commons Math integration</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Calculate distances from centroid
 * WiFiMeasurementDistances distances = measurements.calculateDistancesTo(centroid);
 *
 * // Calculate MAD for outlier detection
 * WiFiMeasurementDistances.MAD mad = distances.medianAbsoluteDeviation();
 * double threshold = mad.fromDistanceMedian() + (3.0 * mad.value());
 *
 * // Identify outliers
 * Set<String> outlierIds = distances.getMeasurementsBeyond(threshold)
 *     .map(WifiMeasurement::id)
 *     .collect(Collectors.toSet());
 * }</pre>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 2.0
 * @see WiFiMeasurementDistance for individual measurement-distance pairs
 * @see WifiMeasurements#calculateDistancesTo(Location) for distance calculation
 * @see <a href="https://en.wikipedia.org/wiki/Median_absolute_deviation">MAD on Wikipedia</a>
 * @since 1.0
 */
public record WiFiMeasurementDistances(List<WiFiMeasurementDistance> distances) {


    public double median() {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (WiFiMeasurementDistance distance : distances) {
            stats.addValue(distance.distance());
        }

        return stats.getPercentile(50.0);
    }

    private MAD medianAbsoluteDeviation() {
        double median = median();
        // Calculate absolute deviations from fromDistanceMedian
        DescriptiveStatistics deviations = new DescriptiveStatistics();
        for (WiFiMeasurementDistance distance : distances) {
            deviations.addValue(Math.abs(distance.distance() - median));
        }

        return new MAD(median, deviations.getPercentile(50.0));
    }

    public double madThreshold(double multiplier) {
        MAD mad = medianAbsoluteDeviation();
        return mad.fromDistanceMedian() + (multiplier * mad.value());
    }

    /**
     * Identifies outlier measurements based on distance threshold.
     *
     * <p>This method filters measurements whose distances exceed the given threshold
     * and returns their IDs as a set. This is commonly used in outlier detection
     * algorithms where measurements beyond a certain distance from the centroid
     * are considered outliers.
     *
     * <p><strong>Mathematical Process:</strong>
     * <ol>
     *   <li>Filter measurements where distance > threshold</li>
     *   <li>Extract measurement IDs from filtered results</li>
     *   <li>Return as a Set for efficient lookup operations</li>
     * </ol>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Global Outlier Detection:</strong> Identify measurements beyond MAD threshold</li>
     *   <li><strong>Quality Filtering:</strong> Remove measurements that are too far from centroid</li>
     *   <li><strong>Statistical Analysis:</strong> Analyze spatial distribution patterns</li>
     * </ul>
     *
     * @param threshold The distance threshold beyond which measurements are considered outliers
     * @return Set of measurement IDs that exceed the threshold distance
     */
    public <T> Set<T> getBeyond(double threshold, Function<WifiMeasurement, T> mappingFunction) {
        return distances.stream()
                        .filter(distance -> distance.distance() > threshold)
                        .map(WiFiMeasurementDistance::measurement)
                        .map(mappingFunction)
                        .collect(Collectors.toUnmodifiableSet());
    }

    private record MAD(double fromDistanceMedian, double value) {
    }
}
