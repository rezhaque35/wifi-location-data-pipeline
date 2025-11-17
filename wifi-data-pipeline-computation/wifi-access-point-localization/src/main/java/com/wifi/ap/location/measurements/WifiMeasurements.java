// wifi-data-pipeline-computation/wifi-access-point-localization/src/main/java/com/wifi/ap/location/estimate/dto/WifiMeasurements.java
package com.wifi.ap.location.measurements;

import com.wifi.ap.location.Location;
import com.wifi.ap.location.estimation.state.DataMaturityTier;
import com.wifi.ap.location.measurements.math.GeographicCentroidCalculator;
import com.wifi.ap.location.measurements.math.WiFiMeasurementDistance;
import com.wifi.ap.location.measurements.math.WiFiMeasurementDistances;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;

/**
 * Centralized WiFi measurements container with intelligent caching and validation.
 *
 * <p>This class serves as the core data structure for WiFi measurement collections, providing
 * thread-safe operations with built-in caching to eliminate duplicate calculations across
 * multiple services. It automatically filters invalid measurements during construction and
 * caches expensive computational results for optimal performance.
 *
 * <h2>Refactored Architecture</h2>
 *
 * <p>This implementation consolidates measurement operations that were previously duplicated
 * across multiple services, implementing the DRY principle and ensuring consistent behavior:
 * <ul>
 *   <li><strong>GlobalOutlierDetector:</strong> Uses cached centroid calculations and distance computations</li>
 *   <li><strong>WiFiHotspotDetectionService:</strong> Leverages cached spatial distribution analysis</li>
 *   <li><strong>WclAccuracyCalculator:</strong> Utilizes cached statistical metrics for accuracy estimation</li>
 *   <li><strong>WeightedCentroidLocalization:</strong> Benefits from pre-validated measurement data</li>
 * </ul>
 *
 * <h2>Intelligent Caching Strategy</h2>
 *
 * <p>The class implements multiple caching layers to prevent duplicate calculations:
 * <ul>
 *   <li><strong>Centroid Cache:</strong> Caches centroid calculations by actual weighting strategy used</li>
 *   <li><strong>Location-Based Caches:</strong> Caches distance calculations and statistics per reference location</li>
 *   <li><strong>Statistical Caches:</strong> Caches expensive O(n²) calculations like distance matrices</li>
 *   <li><strong>Quality Metrics Cache:</strong> Caches CONNECTED measurement statistics and RSSI analysis</li>
 * </ul>
 *
 * <h2>Smart Centroid Calculation with Caching</h2>
 *
 * <p>The {@link #getCentroidLocation(GeographicCentroidCalculator, boolean, int, double)} method
 * implements intelligent decision-making with caching based on actual computation strategy:
 *
 * <p><strong>Decision Logic:</strong>
 * <ol>
 *   <li><strong>Configuration Override:</strong> If useWeightedCentroid is false, use unweighted</li>
 *   <li><strong>Percentage Check:</strong> CONNECTED measurements must be ≥ minConnectedPercentage</li>
 *   <li><strong>Count Check:</strong> CONNECTED measurements must be ≥ minConnectedCount</li>
 *   <li><strong>Decision:</strong> Use weighted only if both conditions are met</li>
 *   <li><strong>Caching:</strong> Cache result by actual weighting strategy (qualityBased vs equalWeight)</li>
 * </ol>
 *
 * <p><strong>Cache Key Strategy:</strong>
 * The cache key reflects the actual computation method used, not the input parameters.
 * This enables efficient reuse when the same WifiMeasurements instance is used by
 * multiple services with different threshold configurations.
 *
 * <h2>Mathematical Operations</h2>
 *
 * <h3>Distance Calculations</h3>
 * <p>The {@link #calculateDistancesTo(Location)} method uses the Haversine formula:
 * <pre>
 * a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
 * c = 2 * atan2(√a, √(1-a))
 * distance = R * c (where R = 6,371 km)
 * </pre>
 *
 * <h3>Spatial Standard Deviation</h3>
 * <p>The {@link #spatialStandardDeviationFrom(Location)} method calculates:
 * <pre>
 * σ = √(Σ(distance_i - μ)² / N)
 * where μ = mean distance, N = number of measurements
 * </pre>
 *
 * <h3>Distance Matrix Computation</h3>
 * <p>The {@link #computeDistanceMatrix()} method creates an O(n²) symmetric matrix:
 * <pre>
 * matrix[i][j] = Haversine distance between measurements i and j
 * Leverages symmetry: distance[i][j] = distance[j][i]
 * </pre>
 *
 * <h2>Performance Optimizations</h2>
 *
 * <ul>
 *   <li><strong>Pre-Filtered Data:</strong> Invalid measurements filtered once at construction</li>
 *   <li><strong>Thread-Safe Caching:</strong> ConcurrentHashMap for lock-free cache operations</li>
 *   <li><strong>Lazy Computation:</strong> Expensive calculations only performed when needed</li>
 *   <li><strong>Memory Efficiency:</strong> Single measurement list instead of duplicate storage</li>
 *   <li><strong>Stream Processing:</strong> Functional approach for optimal memory usage</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Create measurements (automatically validates and filters)
 * WifiMeasurements measurements = WifiMeasurements.of(measurementList);
 *
 * // Calculate cached centroid (reuses cache across multiple calls)
 * Location centroid = measurements.getCentroidLocation(
 *     centroidCalculator, true, 2, 0.1);
 *
 * // Calculate cached distances for outlier detection
 * WiFiMeasurementDistances distances = measurements.calculateDistancesTo(centroid);
 *
 * // Get cached spatial statistics
 * double stdDev = measurements.spatialStandardDeviationFrom(centroid);
 * double maxSpread = measurements.getMaxSpatialSpread();
 * 
 * // All operations work on pre-validated measurements with intelligent caching
 * int validCount = measurements.size(); // Only counts valid measurements
 * }</pre>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 2.0
 * @see WiFiMeasurementDistances for distance-based statistical operations
 * @see ConnectedMeasurementStats for CONNECTED measurement analysis
 * @see GeographicCentroidCalculator for ECEF-based centroid calculations
 * @since 1.0
 */
public final class WifiMeasurements {

    private static final Logger logger = LoggerFactory.getLogger(WifiMeasurements.class);

    /**
     * Cache key for centroid calculations based on the actual weighting strategy used.
     *
     * <p>This record represents the actual weighting strategy that gets applied during
     * centroid calculation, enabling efficient caching of expensive centroid computations.
     * The key is based on the outcome of the decision logic, not the input parameters.
     *
     * @param isQualityWeighted Whether quality-based weighting was actually used (true for qualityBased, false for equalWeight)
     */
    private record CentroidCacheKey(
        boolean isQualityWeighted
    ) {}

    // Immutable list of valid measurements for spatial operations
    private final List<WifiMeasurement> measurements;

    @SuppressWarnings("java:S3077") // Volatile is appropriate here with double-checked locking pattern
    private volatile ConnectedMeasurementStats cachedConnectedStats;
    @SuppressWarnings("java:S3077") // Complex 2D array with synchronized access pattern
    private volatile double[][] cachedDistanceMatrix;
    private volatile Double cachedMaxSpatialSpread;
    private volatile Double cachedAngularCoverage;
    private volatile Double cachedAverageGpsAccuracy;
    private volatile Double cachedRssiStandardDeviation;
    private volatile Double cachedAverageRssi;

    // Location-based cache for distance calculations - thread-safe maps
    private final ConcurrentHashMap<Location, SummaryStatistics> locationDistanceStatsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Location, WiFiMeasurementDistances> locationDistancesCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Location, Double> locationStdDeviationCache = new ConcurrentHashMap<>();

    // Centroid calculation cache - thread-safe map
    private final ConcurrentHashMap<CentroidCacheKey, Location> centroidCalculationCache = new ConcurrentHashMap<>();

    /**
     * Constructs a WifiMeasurements instance with automatic validation and filtering.
     * 
     * <p>This constructor performs one-time filtering of invalid measurements during construction,
     * ensuring that only measurements suitable for spatial operations are stored. This eliminates
     * the need for repeated validation throughout the object's lifecycle and provides consistent
     * behavior across all spatial analysis methods.
     * 
     * <p><strong>Validation Criteria:</strong>
     * <ul>
     *   <li><strong>Non-null coordinates:</strong> Both latitude and longitude must not be null</li>
     *   <li><strong>Valid numeric values:</strong> Coordinates must not be NaN (Not a Number)</li>
     *   <li><strong>Latitude range:</strong> Must be within [-90°, 90°] (valid Earth latitude)</li>
     *   <li><strong>Longitude range:</strong> Must be within [-180°, 180°] (valid Earth longitude)</li>
     * </ul>
     * 
     * <p><strong>Performance Impact:</strong>
     * This one-time filtering operation eliminates the need for repeated validation checks
     * in spatial analysis methods, improving performance for large measurement collections.
     * 
     * @param measurements List of WiFi measurements to validate and filter
     * @throws IllegalArgumentException if measurements is null
     */
    public WifiMeasurements(List<WifiMeasurement> measurements) {
        // Filter and store only valid measurements - no need for shallow copy or duplicate lists
        this.measurements = measurements.stream()
            .filter(this::isValidForSpatialOperations)
            .toList();
    }
    
    /**
     * Validates if a measurement has valid location data for spatial operations.
     * 
     * <p>This method centralizes the validation logic that was previously duplicated
     * across multiple methods in this class. It ensures consistent validation criteria
     * throughout all spatial analysis operations.
     * 
     * @param measurement The measurement to validate
     * @return true if the measurement has valid location data for spatial operations
     */
    private boolean isValidForSpatialOperations(WifiMeasurement measurement) {
        return measurement.latitude() != null 
            && measurement.longitude() != null
            && !Double.isNaN(measurement.latitude()) 
            && !Double.isNaN(measurement.longitude())
            && measurement.latitude() >= -90 
            && measurement.latitude() <= 90 
            && measurement.longitude() >= -180 
            && measurement.longitude() <= 180;
    }

    /**
     * Returns the immutable list of measurements.
     *
     * @return Immutable list of WiFi measurements
     */
    public List<WifiMeasurement> measurements() {
        return measurements;
    }

    /**
     * Factory method to create WifiMeasurements from a list.
     *
     * @param measurements List of WiFi measurements
     * @return New WifiMeasurements instance
     */
    public static WifiMeasurements of(List<WifiMeasurement> measurements) {
        return new WifiMeasurements(measurements);
    }

    /**
     * Returns the number of valid measurements in this collection.
     * 
     * <p>Only measurements with valid location data are stored in this collection.
     * Invalid measurements are filtered out during construction.
     *
     * @return Count of valid measurements suitable for spatial operations
     */
    public int size() {
        return measurements.size();
    }

    /**
     * Checks if this collection is empty.
     *
     * @return true if no valid measurements are present
     */
    public boolean isEmpty() {
        return measurements.isEmpty();
    }
    



    /**
     * Calculates distances from each measurement to the centroid location.
     *
     * <p>All measurements in the collection are guaranteed to be valid for spatial operations.
     *
     * @return DoubleStream of distances in meters
     * @param centroid
     */
    private DoubleStream distancesTo(Location centroid) {
        return measurements.stream()
            .mapToDouble(measurement -> centroid.distanceTo(Location.fromMeasurement(measurement)));
    }

    /**
     * Calculates comprehensive distance statistics from all measurements to the location.
     * Uses location-based caching to avoid repeated expensive calculations.
     *
     * <p>This includes mean distance, standard deviation, minimum, and maximum distances
     * from each measurement to the location location.
     *
     * @return SummaryStatistics containing distance distribution metrics
     * @param location
     */
    private SummaryStatistics distanceStatistics(Location location) {
        return locationDistanceStatsCache.computeIfAbsent(location, loc ->
            distancesTo(loc)
                .boxed()
                .collect(Collector.of(
                    SummaryStatistics::new,
                    SummaryStatistics::addValue,
                    (stats1, stats2) -> stats1 // Sequential stream combiner
                ))
        );
    }


    /**
     * Calculates spatial standard deviation with comprehensive logging for debugging.
     * Uses location-based caching to avoid repeated expensive calculations.
     *
     * <p>This method provides the same calculation as spatialStandardDeviation()
     * but includes detailed logging of all distance statistics for debugging purposes.
     *
     * @param location
     * @return Standard deviation of distances from location in meters
     */
    public double spatialStandardDeviationFrom(Location location) {
        if (isEmpty()) {
            return 0.0;
        }

        return locationStdDeviationCache.computeIfAbsent(location, loc -> {
            SummaryStatistics distanceStats = distanceStatistics(loc);
            double standardDeviation = distanceStats.getStandardDeviation();

            if (logger.isDebugEnabled()) {
                logger.debug("Distance statistics - Mean: {}m, StdDev: {}m, Min: {}m, Max: {}m",
                             String.format("%.2f", distanceStats.getMean()),
                             String.format("%.2f", standardDeviation),
                             String.format("%.2f", distanceStats.getMin()),
                             String.format("%.2f", distanceStats.getMax()));
            }

            return standardDeviation;
        });
    }



    /**
     * Calculates statistics about CONNECTED measurements in this collection.
     * Uses caching to avoid repeated expensive filtering operations.
     *
     * <p>This method analyzes the connection status distribution and provides
     * both absolute count and percentage of CONNECTED measurements. This information
     * is commonly used for determining weighting strategies in centroid calculations
     * and quality assessments.
     *
     * @return ConnectedMeasurementStats containing count, total, and percentage
     */
    public ConnectedMeasurementStats getConnectedMeasurementStats() {
        if (cachedConnectedStats == null) {
            synchronized (this) {
                if (cachedConnectedStats == null) {
                    cachedConnectedStats = calculateConnectedMeasurementStats();
                }
            }
        }
        return cachedConnectedStats;
    }

    /**
     * Internal method to calculate connected measurement statistics.
     */
    private ConnectedMeasurementStats calculateConnectedMeasurementStats() {
        long connectedCount = measurements.stream()
                                          .filter(m -> "CONNECTED".equals(m.connectionStatus()))
                                          .count();

        int totalCount = measurements.size();
        double connectedPercentage = totalCount > 0 ? (double) connectedCount / totalCount : 0.0;

        return new ConnectedMeasurementStats(connectedCount, totalCount, connectedPercentage);
    }

    /**
     * Calculates geographic centroid with intelligent weighting strategy and caching.
     *
     * <p>This method implements smart decision-making for centroid calculation, automatically
     * choosing between quality-weighted and equal-weighted approaches based on measurement
     * characteristics. Results are cached by actual computation strategy to eliminate
     * duplicate calculations across multiple services.
     *
     * <p><strong>Mathematical Process:</strong>
     * <ol>
     *   <li><strong>Strategy Decision:</strong> Determine weighting approach based on quality thresholds</li>
     *   <li><strong>ECEF Conversion:</strong> Transform lat/lon coordinates to ECEF unit vectors</li>
     *   <li><strong>Weight Application:</strong> Apply chosen weighting strategy (quality-based or equal)</li>
     *   <li><strong>Vector Averaging:</strong> Sum weighted vectors and normalize to unit magnitude</li>
     *   <li><strong>Coordinate Conversion:</strong> Transform normalized vector back to lat/lon</li>
     * </ol>
     *
     * <p><strong>Quality-Based Weighting Formula:</strong>
     * <pre>
     * For each measurement i:
     *   weight_i = quality_weight_i (CONNECTED=2.0, SCAN=1.0)
     *   
     * ECEF vector calculation:
     *   x_i = cos(lat_i) * cos(lon_i) * weight_i
     *   y_i = cos(lat_i) * sin(lon_i) * weight_i  
     *   z_i = sin(lat_i) * weight_i
     * </pre>
     *
     * <p><strong>Caching Strategy:</strong>
     * Results are cached using {@link CentroidCacheKey} based on actual weighting strategy:
     * <ul>
     *   <li><strong>qualityBased:</strong> Uses CONNECTED measurement weighting (2x weight)</li>
     *   <li><strong>equalWeight:</strong> Treats all measurements equally (1x weight)</li>
     * </ul>
     *
     * <p><strong>Decision Logic:</strong>
     * <ol>
     *   <li><strong>Configuration Override:</strong> If useWeightedCentroid=false, force equal weighting</li>
     *   <li><strong>Quality Threshold Check:</strong> CONNECTED percentage ≥ minConnectedPercentage</li>
     *   <li><strong>Count Threshold Check:</strong> CONNECTED count ≥ minConnectedCount</li>
     *   <li><strong>Final Decision:</strong> Use quality weighting only if both thresholds met</li>
     * </ol>
     *
     * <p><strong>Performance Benefits:</strong>
     * <ul>
     *   <li><strong>Eliminates Duplicate Calculations:</strong> Same WifiMeasurements instance used by multiple services</li>
     *   <li><strong>Thread-Safe Caching:</strong> ConcurrentHashMap ensures safe concurrent access</li>
     *   <li><strong>Strategy-Based Keys:</strong> Cache hits regardless of threshold parameter variations</li>
     * </ul>
     *
     * @param centroidCalculator The geographic centroid calculator using ECEF vector averaging
     * @param useWeightedCentroid Whether to enable quality-based weighting consideration
     * @param minConnectedCount Minimum absolute count of CONNECTED measurements for quality weighting
     * @param minConnectedPercentage Minimum percentage of CONNECTED measurements (0.0 to 1.0)
     * @return Geographic centroid as Location using ECEF vector averaging for spherical accuracy
     * @throws IllegalStateException if no valid measurements are available
     */
    public Location getCentroidLocation(GeographicCentroidCalculator centroidCalculator,
                                        boolean useWeightedCentroid,
                                        int minConnectedCount,
                                        double minConnectedPercentage) {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot calculate centroid: no measurements available");
        }

        // Determine which weighting strategy will actually be used
        boolean willUseQualityWeighting = useWeightedCentroid &&
            getConnectedMeasurementStats().meetsThresholds(minConnectedCount, minConnectedPercentage);

        // Create cache key based on actual weighting strategy that will be used
        CentroidCacheKey cacheKey = new CentroidCacheKey(willUseQualityWeighting);

        // Use cached result if available, otherwise compute and cache
        return centroidCalculationCache.computeIfAbsent(cacheKey, key ->
            computeCentroidLocation(centroidCalculator, key.isQualityWeighted())
        );
    }

    /**
     * Internal method to compute centroid location without caching.
     *
     * <p>This method contains the actual centroid calculation logic that was previously
     * in getCentroidLocation. It's separated to enable clean caching without code duplication.
     * The decision logic has already been applied by the caller, so this method directly
     * uses the specified weighting strategy.
     *
     * <p>All measurements in the collection are guaranteed to be valid for spatial operations.
     *
     * @param centroidCalculator The geographic centroid calculator to use
     * @param useQualityWeighting Whether to use quality-based weighting (true) or equal weighting (false)
     * @return Geographic centroid as Location
     */
    private Location computeCentroidLocation(GeographicCentroidCalculator centroidCalculator,
                                           boolean useQualityWeighting) {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot calculate centroid: no measurements with valid location data");
        }

        // Use the predetermined weighting strategy
        if (useQualityWeighting) {
            return centroidCalculator.calculateCentroid(measurements, WeightingStrategies.qualityBased());
        } else {
            return centroidCalculator.calculateCentroid(measurements, WeightingStrategies.equalWeight());
        }
    }

    /**
     * Calculates Haversine distances from a given centroid to all measurements.
     * Uses location-based caching to avoid repeated expensive calculations.
     *
     * <p>This method computes the great-circle distances between each measurement
     * and the provided centroid using the Haversine formula. The distances are
     * returned as a WiFiMeasurementDistances object that provides convenient
     * access to statistical operations like MAD calculation.
     *
     * <p><strong>Mathematical Process:</strong>
     * <ol>
     *   <li>For each measurement, calculate Haversine distance to centroid</li>
     *   <li>Create WiFiMeasurementDistance objects with measurement and distance</li>
     *   <li>Wrap in WiFiMeasurementDistances for statistical operations</li>
     * </ol>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Outlier Detection:</strong> Calculate distances for MAD-based outlier detection</li>
     *   <li><strong>Quality Assessment:</strong> Analyze spatial distribution of measurements</li>
     *   <li><strong>Statistical Analysis:</strong> Perform various statistical operations on distances</li>
     * </ul>
     *
     * @param centroid The reference point for distance calculations
     * @return WiFiMeasurementDistances containing all measurement-distance pairs
     * @throws IllegalArgumentException if centroid is null
     */
    public WiFiMeasurementDistances calculateDistancesTo(Location centroid) {
        if (centroid == null) {
            throw new IllegalArgumentException("Centroid cannot be null");
        }

        return locationDistancesCache.computeIfAbsent(centroid, loc -> {
            List<WiFiMeasurementDistance> distances = measurements.stream()
                .map(measurement -> new WiFiMeasurementDistance(
                    measurement,
                    loc.distanceTo(Location.fromMeasurement(measurement))
                ))
                .toList();

            return new WiFiMeasurementDistances(distances);
        });
    }

    /**
     * Filters measurements using the provided predicate and returns a new WifiMeasurements instance.
     *
     * <p>This method provides a functional approach to filtering measurements based on any criteria.
     * It creates a new immutable WifiMeasurements instance containing only the measurements
     * that satisfy the given predicate. This is commonly used for outlier filtering, quality
     * filtering, and other measurement selection operations.
     *
     * <p><strong>Functional Design:</strong>
     * <ul>
     *   <li><strong>Immutable:</strong> Returns new instance, original remains unchanged</li>
     *   <li><strong>Composable:</strong> Can be chained with other filtering operations</li>
     *   <li><strong>Type-Safe:</strong> Compile-time safety for predicate operations</li>
     *   <li><strong>Efficient:</strong> Stream-based processing for optimal performance</li>
     * </ul>
     *
     * <p><strong>Common Use Cases:</strong>
     * <ul>
     *   <li><strong>Outlier Filtering:</strong> Remove global and local outliers</li>
     *   <li><strong>Quality Filtering:</strong> Filter by connection status or signal strength</li>
     *   <li><strong>Location Filtering:</strong> Filter by geographic bounds or accuracy</li>
     *   <li><strong>Time Filtering:</strong> Filter by measurement timestamp ranges</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     *
     * <pre>{@code
     * // Filter out global outliers
     * WifiMeasurements filtered = measurements.filter(m -> !globalOutliers.contains(m.id()));
     *
     * // Filter by connection status
     * WifiMeasurements connectedOnly = measurements.filter(m -> "CONNECTED".equals(m.connectionStatus()));
     *
     * // Filter by signal strength
     * WifiMeasurements strongSignals = measurements.filter(m -> m.rssi() != null && m.rssi() > -70);
     *
     * // Chain multiple filters
     * WifiMeasurements result = measurements
     *     .filter(m -> !globalOutliers.contains(m.id()))
     *     .filter(m -> !localOutliers.contains(m.id()))
     *     .filter(m -> m.latitude() != null && m.longitude() != null);
     * }</pre>
     *
     * @param predicate The filtering criteria to apply to measurements
     * @return New WifiMeasurements instance containing only measurements that satisfy the predicate
     * @throws IllegalArgumentException if predicate is null
     */
    public WifiMeasurements filter(Predicate<WifiMeasurement> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Predicate cannot be null");
        }

        List<WifiMeasurement> filteredMeasurements = measurements.stream()
            .filter(predicate)
            .toList();

        return new WifiMeasurements(filteredMeasurements);
    }


    public DataMaturityTier getMaturity() { return DataMaturityTier.fromMeasurementCount(measurements.size()); }

    /**
     * Pre-computes distance matrix for all measurement pairs using Haversine distance.
     * Uses caching to avoid repeated expensive O(n²) calculations.
     *
     * <p>This method provides a fundamental spatial analysis capability for measurement collections.
     * It computes all pairwise distances between measurements using the Haversine formula,
     * which accounts for Earth's curvature and provides accurate great-circle distances.
     *
     * <p><strong>Performance Characteristics:</strong>
     * <ul>
     *   <li><strong>Time Complexity:</strong> O(n²) where n is the number of measurements</li>
     *   <li><strong>Space Complexity:</strong> O(n²) for the distance matrix storage</li>
     *   <li><strong>Optimization:</strong> Leverages matrix symmetry to reduce calculations by ~50%</li>
     *   <li><strong>Geographic Accuracy:</strong> Uses Haversine formula for spherical geometry</li>
     *   <li><strong>Caching:</strong> Results are cached to avoid repeated expensive calculations</li>
     * </ul>
     *
     * <p><strong>Haversine Distance Formula:</strong>
     * <pre>
     * a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
     * c = 2 * atan2(√a, √(1-a))
     * distance = R * c
     *
     * Where:
     * - Δlat = lat2 - lat1
     * - Δlon = lon2 - lon1
     * - R = Earth's radius (6,371 km)
     * </pre>
     *
     * <p><strong>Matrix Properties:</strong>
     * <ul>
     *   <li><strong>Symmetry:</strong> distance[i][j] = distance[j][i]</li>
     *   <li><strong>Zero Diagonal:</strong> distance[i][i] = 0 (point to itself)</li>
     *   <li><strong>Immutable Result:</strong> Returns defensive copy of matrix</li>
     * </ul>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>LOF Algorithm:</strong> Pre-computed distances for k-nearest neighbor calculations</li>
     *   <li><strong>Clustering:</strong> Distance-based clustering algorithms (DBSCAN, etc.)</li>
     *   <li><strong>Spatial Analysis:</strong> Measurement density and distribution analysis</li>
     *   <li><strong>Quality Assessment:</strong> Identifying measurement clusters and outliers</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     *
     * <pre>{@code
     * // Compute distance matrix for LOF algorithm
     * double[][] distances = measurements.computeDistanceMatrix();
     *
     * // Use in clustering analysis
     * List<Cluster> clusters = dbscan.cluster(measurements.measurements(), distances);
     *
     * // Analyze spatial distribution
     * double maxDistance = Arrays.stream(distances)
     *     .flatMapToDouble(Arrays::stream)
     *     .max().orElse(0.0);
     * }</pre>
     *
     * @return Symmetric distance matrix where matrix[i][j] = Haversine distance between measurements i and j
     * @throws IllegalStateException if no measurements have valid location data
     */
    public double[][] computeDistanceMatrix() {
        if (cachedDistanceMatrix == null) {
            synchronized (this) {
                if (cachedDistanceMatrix == null) {
                    cachedDistanceMatrix = calculateDistanceMatrix();
                }
            }
        }
        // Return defensive copy to maintain immutability
        return cloneMatrix(cachedDistanceMatrix);
    }

    /**
     * Internal method to calculate distance matrix.
     * All measurements in the collection are guaranteed to be valid for spatial operations.
     */
    private double[][] calculateDistanceMatrix() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot compute distance matrix: no measurements available");
        }

        // Convert measurements to Location objects for proper distance calculations
        List<Location> locations = measurements.stream()
                                                   .map(Location::fromMeasurement)
                                                   .toList();
        int n = locations.size();
        double[][] distances = new double[n][n];

        // Compute pairwise distances, leveraging matrix symmetry
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dist = locations.get(i).distanceTo(locations.get(j));
                distances[i][j] = distances[j][i] = dist;
            }
        }

        return distances;
    }

    /**
     * Creates a defensive copy of a 2D matrix.
     */
    private double[][] cloneMatrix(double[][] matrix) {
        if (matrix == null) return new double[0][0];
        double[][] clone = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            clone[i] = matrix[i].clone();
        }
        return clone;
    }


    @Override
    public String toString() {
        return String.format("WifiMeasurements[size=%d]",
                           size());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        WifiMeasurements that = (WifiMeasurements) obj;
        return measurements.equals(that.measurements);
    }

    @Override
    public int hashCode() {
        return measurements.hashCode();
    }

    public WifiMeasurement get(int measurementIndex) {
        return measurements.get(measurementIndex);
    }

    /**
     * Calculates the maximum spatial spread of measurements in this collection.
     * Uses caching to avoid repeated expensive O(n²) calculations.
     *
     * <p>This method computes the maximum distance between any two measurement points,
     * providing a measure of the spatial distribution and coverage area of the measurements.
     * This metric is commonly used for geometric quality assessment in localization algorithms.
     *
     * <p><strong>Performance Characteristics:</strong>
     * <ul>
     *   <li><strong>Time Complexity:</strong> O(n²) where n is the number of measurements</li>
     *   <li><strong>Space Complexity:</strong> O(1) constant space usage</li>
     *   <li><strong>Optimization:</strong> Leverages triangular matrix iteration to reduce comparisons by ~50%</li>
     *   <li><strong>Caching:</strong> Results are cached to avoid repeated expensive calculations</li>
     * </ul>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Quality Assessment:</strong> Validate minimum spatial spread requirements</li>
     *   <li><strong>Geometric Analysis:</strong> Assess measurement distribution quality</li>
     *   <li><strong>Algorithm Selection:</strong> Determine suitability for different localization methods</li>
     *   <li><strong>Confidence Calculation:</strong> Factor into location estimation confidence</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     *
     * <pre>{@code
     * // Check if measurements meet minimum spread requirement
     * double spread = measurements.getMaxSpatialSpread();
     * boolean meetsRequirement = spread >= MINIMUM_SPATIAL_SPREAD_METERS;
     *
     * // Use in quality assessment
     * if (measurements.getMaxSpatialSpread() < 10.0) {
     *     logger.warn("Poor spatial coverage: {} meters", spread);
     * }
     *
     * // Factor into confidence calculation
     * double spatialFactor = Math.min(1.0, spread / 50.0);
     * }</pre>
     *
     * @return Maximum distance between any two measurements in meters, or 0.0 if insufficient measurements
     * @throws IllegalStateException if no measurements have valid location data
     */
    public double getMaxSpatialSpread() {
        if (cachedMaxSpatialSpread == null) {
            synchronized (this) {
                if (cachedMaxSpatialSpread == null) {
                    cachedMaxSpatialSpread = calculateMaxSpatialSpread();
                }
            }
        }
        return cachedMaxSpatialSpread;
    }

    /**
     * Internal method to calculate maximum spatial spread.
     * All measurements in the collection are guaranteed to be valid for spatial operations.
     */
    private double calculateMaxSpatialSpread() {
        if (size() < 2) {
            return 0.0;
        }

        double maxDistance = 0.0;

        // Calculate maximum distance between any two measurements using triangular iteration
        for (int i = 0; i < measurements.size(); i++) {
            for (int j = i + 1; j < measurements.size(); j++) {
                WifiMeasurement m1 = measurements.get(i);
                WifiMeasurement m2 = measurements.get(j);

                Location loc1 = Location.fromMeasurement(m1);
                Location loc2 = Location.fromMeasurement(m2);

                double distance = loc1.distanceTo(loc2);
                maxDistance = Math.max(maxDistance, distance);
            }
        }

        return maxDistance;
    }

    /**
     * Estimates the angular coverage of measurements around their centroid.
     * Uses caching to avoid repeated expensive trigonometric calculations.
     *
     * <p>This method calculates the angular span of measurements around their geometric centroid,
     * providing a measure of how well the measurements are distributed directionally. This is
     * particularly important for localization algorithms that rely on geometric diversity.
     *
     * <p><strong>Algorithm:</strong>
     * <ol>
     *   <li>Calculate the geometric centroid of all valid measurements</li>
     *   <li>Compute the angle from centroid to each measurement using atan2</li>
     *   <li>Find the angular span between min and max angles</li>
     *   <li>Handle wraparound cases (measurements spanning across 0°)</li>
     * </ol>
     *
     * <p><strong>Performance Characteristics:</strong>
     * <ul>
     *   <li><strong>Time Complexity:</strong> O(n) where n is the number of measurements</li>
     *   <li><strong>Space Complexity:</strong> O(1) constant space usage</li>
     *   <li><strong>Accuracy:</strong> Uses atan2 for precise angle calculations</li>
     *   <li><strong>Caching:</strong> Results are cached to avoid repeated expensive calculations</li>
     * </ul>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Geometric Quality:</strong> Assess directional distribution of measurements</li>
     *   <li><strong>Algorithm Suitability:</strong> Determine if measurements provide good angular coverage</li>
     *   <li><strong>Confidence Factors:</strong> Higher angular coverage increases localization confidence</li>
     *   <li><strong>Quality Gates:</strong> Validate minimum angular requirements for algorithms</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     *
     * <pre>{@code
     * // Check angular coverage requirement
     * double coverage = measurements.getAngularCoverage();
     * boolean meetsRequirement = coverage >= MINIMUM_ANGULAR_COVERAGE_DEGREES;
     *
     * // Quality assessment
     * if (coverage < 90.0) {
     *     logger.warn("Poor angular coverage: {} degrees", coverage);
     * }
     *
     * // Factor into geometric quality
     * double angularFactor = Math.min(1.0, coverage / 180.0);
     * }</pre>
     *
     * @return Angular coverage in degrees (0.0 to 360.0), or 0.0 if insufficient measurements
     */
    public double getAngularCoverage() {
        if (cachedAngularCoverage == null) {
            synchronized (this) {
                if (cachedAngularCoverage == null) {
                    cachedAngularCoverage = calculateAngularCoverage();
                }
            }
        }
        return cachedAngularCoverage;
    }

    /**
     * Internal method to calculate angular coverage.
     * All measurements in the collection are guaranteed to be valid for spatial operations.
     */
    private double calculateAngularCoverage() {
        if (size() < 2) {
            return 0.0;
        }

        // Calculate centroid for angle calculations
        double avgLat = measurements.stream()
            .mapToDouble(WifiMeasurement::latitude)
            .average()
            .orElse(0.0);
        double avgLon = measurements.stream()
            .mapToDouble(WifiMeasurement::longitude)
            .average()
            .orElse(0.0);

        // Calculate angles from centroid to each measurement
        double minAngle = Double.MAX_VALUE;
        double maxAngle = Double.MIN_VALUE;

        for (WifiMeasurement measurement : measurements) {
            double angle = Math.atan2(
                measurement.longitude() - avgLon,
                measurement.latitude() - avgLat
            );

            // Convert to degrees and normalize to [0, 360)
            angle = Math.toDegrees(angle);
            if (angle < 0) {
                angle += 360.0;
            }

            minAngle = Math.min(minAngle, angle);
            maxAngle = Math.max(maxAngle, angle);
        }

        // Calculate angular span
        double angularSpan = maxAngle - minAngle;

        // Handle wraparound case (measurements span across 0 degrees)
        if (angularSpan > 180.0) {
            angularSpan = 360.0 - angularSpan;
        }

        return angularSpan;
    }

    /**
     * Calculates the average GPS/GNSS location accuracy for measurements in this collection.
     * Uses caching to avoid repeated expensive filtering and averaging operations.
     *
     * <p>This method computes the average location accuracy from all measurements that have
     * valid accuracy data. GPS accuracy is a critical factor in determining the overall quality
     * and reliability of location-based measurements for WiFi localization algorithms.
     *
     * <p><strong>Quality Interpretation:</strong>
     * <ul>
     *   <li><strong>Excellent:</strong> ≤ 5 meters (high-precision GPS/GNSS)</li>
     *   <li><strong>Very Good:</strong> ≤ 15 meters (good GPS conditions)</li>
     *   <li><strong>Good:</strong> ≤ 30 meters (acceptable for most applications)</li>
     *   <li><strong>Acceptable:</strong> ≤ 50 meters (usable but with caution)</li>
     *   <li><strong>Poor:</strong> > 50 meters (low confidence in location data)</li>
     * </ul>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Quality Gates:</strong> Filter measurements based on accuracy thresholds</li>
     *   <li><strong>Confidence Factors:</strong> Weight localization results by GPS quality</li>
     *   <li><strong>Algorithm Selection:</strong> Choose algorithms suitable for accuracy level</li>
     *   <li><strong>Quality Reporting:</strong> Provide accuracy metrics in results</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     *
     * <pre>{@code
     * // Assess GPS quality
     * double avgAccuracy = measurements.getAverageGpsAccuracy();
     *
     * // Quality-based filtering
     * if (avgAccuracy > 50.0) {
     *     logger.warn("Poor GPS accuracy: {} meters", avgAccuracy);
     * }
     *
     * // Confidence calculation
     * double gpsQuality = avgAccuracy <= 15.0 ? 0.9 : 0.6;
     * }</pre>
     *
     * @return Average GPS accuracy in meters, or 50.0 as conservative default if no accuracy data available
     */
    public double getAverageGpsAccuracy() {
        if (cachedAverageGpsAccuracy == null) {
            synchronized (this) {
                if (cachedAverageGpsAccuracy == null) {
                    cachedAverageGpsAccuracy = calculateAverageGpsAccuracy();
                }
            }
        }
        return cachedAverageGpsAccuracy;
    }

    /**
     * Internal method to calculate average GPS accuracy.
     */
    private double calculateAverageGpsAccuracy() {
        return measurements.stream()
            .filter(m -> m.locationAccuracy() != null)
            .mapToDouble(WifiMeasurement::locationAccuracy)
            .filter(acc -> acc > 0) // Filter out invalid/zero accuracy values
            .average()
            .orElse(50.0); // Conservative default for unknown accuracy
    }


    /**
     * Calculates the standard deviation of a list of double values.
     *
     * <p>This is a general-purpose statistical utility method used by various measurement
     * analysis operations. It computes the population standard deviation using the
     * standard mathematical formula.
     *
     * <p><strong>Mathematical Formula:</strong>
     * <pre>
     * σ = √(Σ(xi - μ)² / N)
     *
     * Where:
     * - σ = standard deviation
     * - xi = each individual value
     * - μ = mean of all values
     * - N = number of values
     * </pre>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Spatial Consistency:</strong> Measure spread of distances from centroid</li>
     *   <li><strong>Quality Assessment:</strong> Analyze variability in measurements</li>
     *   <li><strong>Confidence Calculation:</strong> Factor variability into confidence scores</li>
     *   <li><strong>Statistical Analysis:</strong> General purpose statistical calculations</li>
     * </ul>
     *
     * @param values List of numerical values to calculate standard deviation for
     * @return Standard deviation of the values, or 0.0 if insufficient data (≤1 value)
     */
    public static double calculateStandardDeviation(List<Double> values) {
        if (values == null || values.size() <= 1) {
            return 0.0;
        }

        double mean = values.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * Calculates the RSSI (Received Signal Strength Indicator) standard deviation for measurements.
     * Uses caching to avoid repeated expensive statistical calculations.
     *
     * <p>This method computes the standard deviation of RSSI values across all measurements
     * that have valid RSSI data. RSSI consistency is an important indicator of measurement
     * quality and environmental stability for WiFi-based localization.
     *
     * <p><strong>RSSI Consistency Interpretation:</strong>
     * <ul>
     *   <li><strong>Very Consistent:</strong> ≤ 5 dBm std dev (stable indoor environment)</li>
     *   <li><strong>Consistent:</strong> ≤ 10 dBm std dev (good indoor conditions)</li>
     *   <li><strong>Acceptable:</strong> ≤ 15 dBm std dev (typical indoor environment)</li>
     *   <li><strong>Inconsistent:</strong> > 15 dBm std dev (poor/unstable conditions)</li>
     * </ul>
     *
     * <p><strong>Research Foundation:</strong>
     * Indoor WiFi localization studies show that RSSI standard deviations typically
     * range from 2-15 dBm in stable indoor environments. Higher standard deviations
     * indicate environmental factors affecting signal propagation.
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Quality Assessment:</strong> Evaluate signal environment stability</li>
     *   <li><strong>Confidence Factors:</strong> Adjust localization confidence based on RSSI consistency</li>
     *   <li><strong>Algorithm Selection:</strong> Choose algorithms suitable for signal conditions</li>
     *   <li><strong>Environmental Analysis:</strong> Identify interference or multipath effects</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     *
     * <pre>{@code
     * // Assess RSSI consistency
     * double rssiStdDev = measurements.getRssiStandardDeviation();
     *
     * // Quality-based algorithm selection
     * if (rssiStdDev <= 5.0) {
     *     logger.info("Very consistent RSSI environment");
     *     algorithm = PRECISION_ALGORITHM;
     * } else if (rssiStdDev <= 15.0) {
     *     logger.info("Acceptable RSSI environment");
     *     algorithm = STANDARD_ALGORITHM;
     * } else {
     *     logger.warn("Inconsistent RSSI environment: {} dBm std dev", rssiStdDev);
     *     algorithm = ROBUST_ALGORITHM;
     * }
     * }</pre>
     *
     * @return RSSI standard deviation in dBm, or 0.0 if insufficient RSSI data (≤1 valid value)
     */
    public double getRssiStandardDeviation() {
        if (cachedRssiStandardDeviation == null) {
            synchronized (this) {
                if (cachedRssiStandardDeviation == null) {
                    cachedRssiStandardDeviation = calculateRssiStandardDeviation();
                }
            }
        }
        return cachedRssiStandardDeviation;
    }

    /**
     * Internal method to calculate RSSI standard deviation.
     */
    private double calculateRssiStandardDeviation() {
        // Extract RSSI values (filter nulls)
        List<Double> rssiValues = measurements.stream()
            .filter(m -> m.rssi() != null)
            .mapToDouble(m -> m.rssi().doubleValue())
            .boxed()
            .toList();

        return calculateStandardDeviation(rssiValues);
    }

    /**
     * Calculates the average RSSI (Received Signal Strength Indicator) for measurements in this collection.
     * Uses caching to avoid repeated expensive filtering and averaging operations.
     * 
     * <p>This method computes the average RSSI value across all measurements that have
     * valid RSSI data. Average RSSI is commonly used for quality metrics in APState
     * and for signal strength analysis.
     * 
     * @return Average RSSI in dBm, or -80.0 as conservative default if no RSSI data available
     */
    public double getAverageRssi() {
        if (cachedAverageRssi == null) {
            synchronized (this) {
                if (cachedAverageRssi == null) {
                    cachedAverageRssi = calculateAverageRssi();
                }
            }
        }
        return cachedAverageRssi;
    }

    /**
     * Internal method to calculate average RSSI.
     */
    private double calculateAverageRssi() {
        return measurements.stream()
            .filter(m -> m.rssi() != null)
            .mapToDouble(m -> m.rssi().doubleValue())
            .average()
            .orElse(-80.0); // Conservative default for unknown RSSI
    }
    
    /**
     * Calculates the average altitude from measurements in this collection.
     * Uses caching to avoid repeated expensive filtering and averaging operations.
     * 
     * <p>This method computes the average altitude value across all measurements that have
     * valid altitude data. Average altitude is commonly used for location estimation
     * and geographic analysis in WiFi localization algorithms.
     * 
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Location Estimation:</strong> Provide altitude context for 3D localization</li>
     *   <li><strong>Geographic Analysis:</strong> Analyze elevation patterns in measurement data</li>
     *   <li><strong>Quality Assessment:</strong> Validate altitude consistency across measurements</li>
     *   <li><strong>APState Metrics:</strong> Include altitude information in AP state</li>
     * </ul>
     * 
     * @return Average altitude in meters, or null if no altitude data available
     */
    public Double getAverageAltitude() {
        return measurements.stream()
            .filter(m -> m.altitude() != null)
            .mapToDouble(WifiMeasurement::altitude)
            .average()
            .orElse(Double.NaN);
    }
}


