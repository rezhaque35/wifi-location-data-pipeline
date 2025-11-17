// wifi-data-pipeline-computation/wifi-access-point-localization/src/main/java/com/wifi/ap/location/estimation/algorithm/impl/WeightedCentroidLocalization.java
package com.wifi.ap.location.estimation.algorithm.impl;

import com.wifi.ap.location.estimation.algorithm.LocalizationAlgorithm;
import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.Location;
import com.wifi.ap.location.measurements.WifiMeasurements;
import com.wifi.ap.location.measurements.math.GeographicCentroidCalculator;
import com.wifi.ap.location.measurements.WeightingStrategies;
import com.wifi.ap.location.estimation.accuracy.WclAccuracyCalculator;
import com.wifi.ap.location.estimation.GpsQualityTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Weighted Centroid Localization (WCL) algorithm implementation with research-based accuracy estimation.
 *
 * <p>This implementation provides a robust, geometrically-sound approach to Access Point
 * localization using weighted centroid calculation with comprehensive quality assessment.
 * It leverages the refactored GeographicCentroidCalculator and WclAccuracyCalculator
 * for optimal performance and accuracy estimation.
 *
 * <h2>Algorithm Overview</h2>
 *
 * <p>WCL calculates a weighted centroid of measurement locations using a sophisticated
 * weighting strategy that combines multiple quality factors:
 * <ul>
 *   <li><strong>Connection Status:</strong> CONNECTED measurements get 2× weight vs SCAN</li>
 *   <li><strong>Signal Strength:</strong> Exponential weighting based on RSSI values</li>
 *   <li><strong>Location Accuracy:</strong> Inverse weighting based on GPS accuracy</li>
 * </ul>
 *
 * <h2>Mathematical Foundation</h2>
 *
 * <h3>Weighting Strategy Formula</h3>
 * <pre>
 * For each measurement i:
 *   connection_weight_i = "CONNECTED".equals(connectionStatus) ? 2.0 : 1.0
 *   signal_weight_i = 10^((RSSI_i + 100) / 10)  // Exponential RSSI weighting
 *   accuracy_weight_i = locationAccuracy_i > 0 ? 1.0 / locationAccuracy_i : 1.0
 *   
 *   total_weight_i = connection_weight_i × signal_weight_i × accuracy_weight_i
 * </pre>
 *
 * <h3>Centroid Calculation</h3>
 * <p>Uses ECEF vector averaging through GeographicCentroidCalculator for spherical accuracy:
 * <pre>
 * centroid = ECEF_weighted_average(measurements, total_weights)
 * </pre>
 *
 * <h2>Usage in Algorithm Selection Framework</h2>
 *
 * <p>WCL is used during <strong>Phase 1 build-up stage</strong> for:
 * <ul>
 *   <li><strong>20-49 measurements:</strong> Bootstrap AP location estimates</li>
 *   <li><strong>Quick localization:</strong> Simple weighted average approach</li>
 *   <li><strong>Geometric robustness:</strong> Handles poor AP geometry better than trilateration</li>
 *   <li><strong>Quality assessment:</strong> Provides comprehensive accuracy estimation</li>
 * </ul>
 *
 * <h2>Research-Based Accuracy Estimation</h2>
 *
 * <p>The algorithm leverages WclAccuracyCalculator for scientifically-grounded accuracy estimates:
 * <ul>
 *   <li><strong>Spatial Consistency (60% weight):</strong> 95% confidence interval (spatialStdDev × 1.96) - primary factor for WCL reliability</li>
 *   <li><strong>GPS Quality Impact (25% weight):</strong> PMC 7763701 research-based quality multipliers from 168,286+ GPS measurements</li>
 *   <li><strong>Sample Size Effects (15% weight):</strong> Representative sample study findings on overestimation with small samples</li>
 *   <li><strong>Environmental Factors:</strong> Connection quality, RSSI consistency, and geometric quality multipliers</li>
 * </ul>
 *
 * <p><strong>Research Sources:</strong>
 * <ul>
 *   <li><strong>PMC 7763701:</strong> Specht et al. (2020) - Statistical Distribution Analysis of Navigation Positioning System Errors</li>
 *   <li><strong>Statistical Theory:</strong> 95% confidence interval (1.96 multiplier) - universally accepted mathematical principle</li>
 *   <li><strong>WiFi Localization Studies:</strong> RSSI consistency thresholds (2-15 dBm range) from indoor positioning research</li>
 *   <li><strong>Geometric Studies:</strong> GDOP and angular coverage requirements from WiFi positioning literature</li>
 *   <li><strong>Framework Specification:</strong> CONNECTED vs SCAN quality differentiation (2× weight)</li>
 * </ul>
 *
 * <h2>Confidence Calculation</h2>
 *
 * <p>Implements geometric mean-based confidence calculation:
 * <pre>
 * spatial_consistency = 1.0 - (spatial_std_dev / 50.0)  // Normalized to [0.2, 1.0]
 * gps_quality = threshold_based_mapping(avg_gps_accuracy)  // Based on GPS quality tiers
 * confidence = sqrt(spatial_consistency × gps_quality)  // Geometric mean
 * </pre>
 *
 * <h2>Performance Optimizations</h2>
 *
 * <ul>
 *   <li><strong>Cached Calculations:</strong> Leverages WifiMeasurements caching for spatial statistics</li>
 *   <li><strong>Pre-validated Data:</strong> Works with pre-filtered valid measurements</li>
 *   <li><strong>ECEF Efficiency:</strong> Uses optimized ECEF vector averaging</li>
 *   <li><strong>Research Constants:</strong> Embedded research-based constants for accuracy estimation</li>
 * </ul>
 *
 * <h2>Quality Assurance Integration</h2>
 *
 * <p>The algorithm integrates with the broader quality assessment framework:
 * <ul>
 *   <li><strong>Spatial Spread Analysis:</strong> Validates minimum spatial distribution</li>
 *   <li><strong>Angular Coverage Assessment:</strong> Ensures minimum geometric coverage</li>
 *   <li><strong>Signal Quality Evaluation:</strong> Assesses RSSI consistency and strength</li>
 *   <li><strong>GPS Accuracy Integration:</strong> Factors in location accuracy quality</li>
 * </ul>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 2.0
 * @see GeographicCentroidCalculator for ECEF-based centroid calculation
 * @see WclAccuracyCalculator for research-based accuracy estimation
 * @see WifiMeasurements for cached spatial statistics and quality metrics
 * @see wcl_accuracy_research_sources_summary.md for complete research citations
 * @see wcl_accuracy_implementation_summary.md for implementation details
 * @since 1.0
 */
@Service
public class WeightedCentroidLocalization implements LocalizationAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger(WeightedCentroidLocalization.class);


    private final GeographicCentroidCalculator centroidCalculator;
    private final WclAccuracyCalculator accuracyCalculator;

    public WeightedCentroidLocalization(GeographicCentroidCalculator centroidCalculator,
                                      WclAccuracyCalculator accuracyCalculator) {
        this.centroidCalculator = centroidCalculator;
        this.accuracyCalculator = accuracyCalculator;
    }

    @Override
    public APLocation estimateLocation(WifiMeasurements measurements) {

        try {
            // Use quality-and-signal-based weighting strategy which combines:
            // - Quality weights (CONNECTED=2.0, SCAN=1.0)
            // - Exponential signal strength weights (10^(RSSI/10))
            // This implements the WCL formula: quality_weight × 10^(RSSI/10)
            // Calculate geographic centroid using ECEF vector averaging

            Location centroid = centroidCalculator.calculateCentroid(measurements.measurements(),
                    WeightingStrategies.qualitySignalAndAccuracyBased());


            // Calculate final confidence and accuracy based on quality assessment
            double confidence = calculateConfidence(measurements, centroid);
            double accuracy = accuracyCalculator.calculateAccuracy(measurements, centroid);

            // Build APLocation result
            APLocation result = APLocation.builder()
                    .latitude(centroid.latitude())
                    .longitude(centroid.longitude())
                    .altitude(0.0) // WCL doesn't estimate altitude
                    .confidence(confidence)
                    .horizontalAccuracy(accuracy)
                    .build();

            if (logger.isDebugEnabled()) {
                logger.debug("WCL estimation completed: lat={}, lon={}, confidence={}, accuracy={}",
                        String.format("%.6f", result.getLatitude()),
                        String.format("%.6f", result.getLongitude()),
                        String.format("%.2f", confidence),
                        String.format("%.1f", accuracy));
            }

            return result;

        } catch (Exception e) {
            logger.error("Error during WCL estimation", e);
            return null;
        }
    }

    @Override
    public APLocation estimateLocation(WifiMeasurements locationMeasurements, APLocation currentEstimation) {
        // WCL doesn't use current estimation - it's a fresh calculation each time
        logger.debug(
                "WCL called with current estimation, ignoring current estimation and performing fresh calculation");
        return estimateLocation(locationMeasurements);
    }



    /**
     * Calculates WCL confidence based on spatial consistency and GPS quality metrics.
     *
     * <p>This method implements a geometric mean-based confidence calculation that combines
     * spatial consistency and GPS quality factors. It uses cached calculations from
     * WifiMeasurements for optimal performance.
     *
     * <p><strong>Confidence Formula:</strong>
     * <pre>
     * spatial_consistency = 1.0 - (spatial_std_dev / 50.0)  // Normalized to [0.2, 1.0]
     * gps_quality = threshold_based_mapping(avg_gps_accuracy)  // Quality tier mapping
     * confidence = sqrt(spatial_consistency × gps_quality)  // Geometric mean
     * </pre>
     *
     * <p><strong>Spatial Consistency Calculation:</strong>
     * <ul>
     *   <li><strong>Input:</strong> Spatial standard deviation from WCL estimate</li>
     *   <li><strong>Normalization:</strong> 5m std dev = 0.9 confidence, 25m std dev = 0.5 confidence</li>
     *   <li><strong>Bounds:</strong> Clamped to [0.2, 1.0] range</li>
     * </ul>
     *
     * <p><strong>GPS Quality Mapping:</strong>
     * <ul>
     *   <li><strong>Excellent (≤5m):</strong> 0.9 confidence</li>
     *   <li><strong>Very Good (≤15m):</strong> 0.8 confidence</li>
     *   <li><strong>Good (≤30m):</strong> 0.7 confidence</li>
     *   <li><strong>Acceptable (≤50m):</strong> 0.6 confidence</li>
     *   <li><strong>Poor (≤75m):</strong> 0.4 confidence</li>
     *   <li><strong>Very Poor (≤100m):</strong> 0.3 confidence</li>
     *   <li><strong>Terrible (>100m):</strong> 0.2 confidence</li>
     * </ul>
     *
     * <p><strong>Performance Benefits:</strong>
     * <ul>
     *   <li><strong>Cached Spatial Stats:</strong> Uses WifiMeasurements.spatialStandardDeviationFrom() cache</li>
     *   <li><strong>Cached GPS Accuracy:</strong> Leverages WifiMeasurements.getAverageGpsAccuracy() cache</li>
     * </ul>
     *
     * @param measurements WifiMeasurements collection with cached spatial statistics and GPS accuracy
     * @param wclEstimate The WCL location estimate for spatial consistency calculation
     * @return Confidence level between 0.2 and 0.9 based on geometric mean of spatial and GPS quality
     */
    private double calculateConfidence(WifiMeasurements measurements,
                                       Location wclEstimate) {

        // Factor 1: Spatial Consistency (0.2 to 1.0)
        double spatialConsistency = calculateSpatialConsistency(measurements, wclEstimate);

        // Factor 2: GPS Quality (0.2 to 0.9)
        double gpsQuality = calculateGpsQuality(measurements);

        // Geometric mean combination for balanced contribution
        double confidence = Math.sqrt(spatialConsistency * gpsQuality);

        // Apply final bounds
        return Math.clamp(confidence, 0.2, 0.9);
    }

    private double calculateSpatialConsistency(WifiMeasurements measurements,
            Location wclEstimate) {

        // Use existing centralized spatial standard deviation calculation
        double spatialStdDev = measurements.spatialStandardDeviationFrom(wclEstimate);

        // Convert to consistency score: lower std dev = higher consistency
        // 5m std dev = 0.9 consistency, 25m std dev = 0.5 consistency, 50m std dev =
        // 0.2 consistency
        return Math.clamp(1.0 - (spatialStdDev / 50.0), 0.2, 1.0);
    }

    private double calculateGpsQuality(WifiMeasurements measurements) {
        // Use centralized GPS accuracy calculation
        double avgAccuracy = measurements.getAverageGpsAccuracy();

        // Use unified GPS quality assessment with WCL quality scores
        // These scores are designed for geometric mean calculations in confidence assessment
        GpsQualityTier qualityTier = GpsQualityTier.fromAccuracy(avgAccuracy);
        double qualityScore = qualityTier.getWclQualityScore();
        
        // Handle extended range beyond standard tiers (for very poor GPS >50m)
        // Maintain original logic for GPS accuracy >50m with additional granularity
        if (avgAccuracy > 50.0) {
            if (avgAccuracy <= 75.0)
                return 0.4; // Poor GPS (between POOR and VERY_POOR)
            if (avgAccuracy <= 100.0)
                return 0.3; // Very poor GPS
            return 0.2; // Terrible GPS (>100m)
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("GPS accuracy {}m → {} → quality score {}", 
                        String.format("%.1f", avgAccuracy), qualityTier.name(), 
                        String.format("%.1f", qualityScore));
        }
        
        return qualityScore;
    }


}
