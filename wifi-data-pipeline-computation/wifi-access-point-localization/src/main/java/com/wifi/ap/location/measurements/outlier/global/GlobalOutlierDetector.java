// wifi-data-pipeline-computation/wifi-access-point-localization/src/main/java/com/wifi/ap/location/estimate/service/outlier/GlobalOutlierDetector.java
package com.wifi.ap.location.measurements.outlier.global;

import com.wifi.ap.location.Location;
import com.wifi.ap.location.measurements.WeightingStrategies;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import com.wifi.ap.location.measurements.math.GeographicCentroidCalculator;
import com.wifi.ap.location.measurements.math.WiFiMeasurementDistances;
import com.wifi.ap.location.measurements.outlier.Outliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.wifi.ap.location.config.GlobalOutlierDetectionConfig;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global outlier detector using MAD (Median Absolute Deviation) with cached centroid calculations.
 *
 * <p>This detector identifies measurements that are significantly distant from the geographic
 * centroid using robust statistical methods. It leverages the refactored WifiMeasurements
 * class for cached centroid calculations and distance computations, eliminating duplicate
 * calculations across multiple services.
 *
 * <h2>Refactored Architecture</h2>
 *
 * <p>The detector now delegates to specialized classes following the Single Responsibility Principle:
 * <ul>
 *   <li><strong>WifiMeasurements:</strong> Provides cached centroid calculation and distance computation</li>
 *   <li><strong>WiFiMeasurementDistances:</strong> Handles MAD calculation and outlier identification</li>
 *   <li><strong>GlobalOutlierDetector:</strong> Orchestrates the complete detection pipeline</li>
 * </ul>
 *
 * <p>This refactoring eliminates code duplication with WiFiHotspotDetectionService and ensures
 * consistent behavior through shared caching mechanisms.
 *
 * <h2>Mathematical Algorithm</h2>
 *
 * <h3>1. Geographic Centroid Calculation</h3>
 * <p><strong>Purpose:</strong> Establish reference point for distance-based outlier detection
 * <p><strong>Method:</strong> Uses cached WifiMeasurements.getCentroidLocation() with smart weighting:
 * <ul>
 *   <li><strong>Quality-based weighting:</strong> CONNECTED measurements get 2× weight vs SCAN</li>
 *   <li><strong>ECEF vector averaging:</strong> Handles spherical geometry correctly</li>
 *   <li><strong>Automatic strategy selection:</strong> Chooses weighted vs unweighted based on data quality</li>
 * </ul>
 *
 * <h3>2. Haversine Distance Calculation</h3>
 * <p><strong>Formula:</strong>
 * <pre>
 * a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
 * c = 2 * atan2(√a, √(1-a))
 * distance = R * c
 * Where: R = 6,371 km (Earth's radius)
 * </pre>
 * <p><strong>Why Haversine:</strong> Provides accurate great-circle distances on Earth's curved surface
 *
 * <h3>3. MAD (Median Absolute Deviation) Threshold</h3>
 * <p><strong>Mathematical Definition:</strong>
 * <pre>
 * MAD = median(|distance_i - median(distances)|)
 * threshold = median_distance + (mad_multiplier × MAD)
 * </pre>
 * <p><strong>Advantages over Standard Deviation:</strong>
 * <ul>
 *   <li><strong>Robustness:</strong> Resistant to outliers in the calculation itself</li>
 *   <li><strong>Non-parametric:</strong> No distribution assumptions</li>
 *   <li><strong>Stability:</strong> Works well with small sample sizes</li>
 * </ul>
 *
 * <h3>4. Complete Detection Process</h3>
 * <ol>
 *   <li><strong>Validation:</strong> Check minimum sample size (default: 20 measurements)</li>
 *   <li><strong>Centroid:</strong> Calculate geographic centroid using cached WifiMeasurements method</li>
 *   <li><strong>Distances:</strong> Compute Haversine distances from centroid to all measurements</li>
 *   <li><strong>MAD Threshold:</strong> Calculate outlier threshold using robust MAD statistics</li>
 *   <li><strong>Identification:</strong> Flag measurements exceeding the threshold as outliers</li>
 * </ol>
 *
 * <h2>Performance Optimizations</h2>
 *
 * <ul>
 *   <li><strong>Cached Centroid:</strong> Leverages WifiMeasurements caching to avoid duplicate calculations</li>
 *   <li><strong>Pre-validated Data:</strong> Works with pre-filtered valid measurements</li>
 *   <li><strong>Efficient Statistics:</strong> Uses optimized MAD calculation in WiFiMeasurementDistances</li>
 *   <li><strong>Thread Safety:</strong> All operations are thread-safe through immutable data structures</li>
 * </ul>
 *
 * <h2>Configuration Parameters</h2>
 *
 * <ul>
 *   <li><strong>mad-multiplier:</strong> Threshold sensitivity (default: 3.0, based on 3-sigma rule)</li>
 *   <li><strong>min-sample-size:</strong> Minimum measurements for reliable statistics (default: 20)</li>
 *   <li><strong>use-weighted-centroid:</strong> Enable quality-based centroid weighting (default: true)</li>
 *   <li><strong>min-connected-percentage:</strong> Minimum CONNECTED % for quality weighting (default: 0.1)</li>
 *   <li><strong>min-connected-count:</strong> Minimum CONNECTED count for quality weighting (default: 2)</li>
 * </ul>
 *
 * <h2>Research Foundation</h2>
 *
 * <ul>
 *   <li><strong>MAD Methodology:</strong> Based on robust statistics research for outlier detection</li>
 *   <li><strong>3-Sigma Rule:</strong> MAD multiplier of 3.0 captures ~99.7% of normal data points</li>
 *   <li><strong>Geographic Accuracy:</strong> ECEF vector averaging ensures spherical geometry handling</li>
 *   <li><strong>Bootstrap Alignment:</strong> Minimum 20 measurements aligns with statistical reliability standards</li>
 * </ul>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 2.0
 * @see WifiMeasurements for cached centroid and distance calculations
 * @see WiFiMeasurementDistances for MAD-based outlier identification
 * @see GeographicCentroidCalculator for ECEF-based centroid calculation
 * @see <a href="https://en.wikipedia.org/wiki/Median_absolute_deviation">MAD on Wikipedia</a>
 * @see <a href="https://en.wikipedia.org/wiki/Haversine_formula">Haversine Formula on Wikipedia</a>
 * @since 1.0
 */
@Component
public class GlobalOutlierDetector {

    private static final Logger logger = LoggerFactory.getLogger(GlobalOutlierDetector.class);
    public static final boolean DETECTION_NOT_POSSIBLE = true;
    public static final boolean DETECTION_POSSIBLE = false;
    public static final Outliers EMPTY_OUTLIERS = new Outliers(Set.of());

    private final GeographicCentroidCalculator centroidCalculator;
    private final GlobalOutlierDetectionConfig config;

    public GlobalOutlierDetector(GeographicCentroidCalculator centroidCalculator, GlobalOutlierDetectionConfig config) {
        this.centroidCalculator = centroidCalculator;
        this.config = config;
    }

    /**
     * Detects global outliers using MAD-based statistical analysis with cached centroid calculations.
     *
     * <p>This method orchestrates the complete outlier detection pipeline, leveraging cached
     * calculations from WifiMeasurements to eliminate duplicate computations. It implements
     * a robust statistical approach using Median Absolute Deviation (MAD) for outlier identification.
     *
     * <p><strong>Mathematical Algorithm:</strong>
     * <pre>
     * 1. Validate sample size ≥ minimum threshold (default: 20)
     * 2. Calculate geographic centroid using cached WifiMeasurements method
     * 3. Compute Haversine distances: distances[i] = haversine(centroid, measurements[i])
     * 4. Calculate MAD threshold: threshold = median(distances) + (mad_multiplier × MAD)
     * 5. Identify outliers: outliers = {i : distances[i] > threshold}
     * </pre>
     *
     * <p><strong>MAD Calculation Formula:</strong>
     * <pre>
     * median_distance = median(distances)
     * absolute_deviations = |distances[i] - median_distance|
     * MAD = median(absolute_deviations)
     * threshold = median_distance + (mad_multiplier × MAD)
     * </pre>
     *
     * <p><strong>Performance Benefits:</strong>
     * <ul>
     *   <li><strong>Cached Centroid:</strong> Reuses centroid calculation if already computed</li>
     *   <li><strong>Cached Distances:</strong> Leverages location-based distance caching</li>
     *   <li><strong>Pre-validated Data:</strong> Works with pre-filtered valid measurements</li>
     *   <li><strong>Efficient Statistics:</strong> Optimized MAD calculation in WiFiMeasurementDistances</li>
     * </ul>
     *
     * <p><strong>Statistical Robustness:</strong>
     * <ul>
     *   <li><strong>MAD-based:</strong> Resistant to outliers in threshold calculation itself</li>
     *   <li><strong>Non-parametric:</strong> No assumptions about data distribution</li>
     *   <li><strong>Bootstrap-aligned:</strong> Minimum 20 measurements ensures statistical reliability</li>
     * </ul>
     *
     * @param measurements WifiMeasurements collection with pre-validated location data
     * @return Outliers object containing set of measurement IDs identified as global outliers
     * @see WifiMeasurements#getCentroidLocation for cached centroid calculation
     * @see WifiMeasurements#calculateDistancesTo for cached distance computation
     * @see WiFiMeasurementDistances#madThreshold for MAD-based threshold calculation
     */
    public Outliers detectGlobalOutliers(WifiMeasurements measurements) {
        if (isDetectionNotPossible(measurements)) {
            return EMPTY_OUTLIERS;
        }

        Location centroid = calculateCentroid(measurements);

        // Calculate Haversine distances from geographic centroid for all measurements
        WiFiMeasurementDistances distancesToCentroid = measurements.calculateDistancesTo(centroid);

        // Calculate MAD threshold
        double madThreshold = distancesToCentroid.madThreshold(config.getMadMultiplier());


        // Identify outliers
        Set<String> globalOutliers = distancesToCentroid.getBeyond(madThreshold, WifiMeasurement::id);
        logger.info("Global outlier detection completed: {} outliers found out of {} measurements",
                    globalOutliers.size(), measurements.size());

        return new Outliers(globalOutliers);
    }


    private boolean isDetectionNotPossible(WifiMeasurements measurements) {
        if (measurements == null || measurements.isEmpty()) {
            logger.debug("Empty or null measurements list provided");
            return DETECTION_NOT_POSSIBLE;
        }

        if (measurements.size() < config.getMinSampleSize()) {
            logger.debug("Insufficient measurements for global outlier detection: {} < {}",
                         measurements.size(), config.getMinSampleSize());
            return DETECTION_NOT_POSSIBLE;
        }
        return DETECTION_POSSIBLE;
    }

    /**
     * Calculates the geographic centroid using the smart auto-detection logic.
     *
     * <p>This method delegates to the WifiMeasurements smart centroid calculation
     * which automatically chooses between weighted and unweighted approaches based
     * on the characteristics of the measurement data.
     *
     * @param measurements List of measurements for centroid calculation
     * @return Geographic centroid as Location
     * @see WifiMeasurements#getCentroidLocation(GeographicCentroidCalculator, boolean, int, double)
     */
    private Location calculateCentroid(WifiMeasurements measurements) {
        return measurements.getCentroidLocation(
            centroidCalculator,
            config.isUseWeightedCentroid(),
            config.getMinConnectedCount(),
            config.getMinConnectedPercentage()
        );
    }

}