package com.wifi.ap.location.dynamic;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.Location;
import com.wifi.ap.location.measurements.WifiMeasurements;
import com.wifi.ap.location.measurements.math.GeographicCentroidCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for detecting mobile WiFi hotspots based on spatial distribution analysis.
 * 
 * <h2>Architectural Evolution</h2>
 * 
 * <p>This service has been refactored to eliminate code duplication and centralize measurement-related
 * operations. It now delegates core calculations to specialized classes that implement the
 * <strong>Single Responsibility Principle</strong>:
 * 
 * <ul>
 *   <li><strong>WifiMeasurements:</strong> Handles smart centroid calculation and spatial analysis</li>
 *   <li><strong>WiFiHotspotDetectionService:</strong> Orchestrates the hotspot detection pipeline</li>
 * </ul>
 * 
 * <p>This refactoring eliminates the previous code duplication with GlobalOutlierDetector
 * and ensures consistent behavior across all measurement-based services.
 * 
 * <h2>Algorithm Implementation</h2>
 * 
 * <p>Implements the hotspot detection algorithm from Section 3.4.1 of the requirements:
 * <ol>
 *   <li><strong>Minimum Sample Validation:</strong> Requires minimum 20 measurements for classification</li>
 *   <li><strong>Smart Centroid Calculation:</strong> Uses same intelligent logic as GlobalOutlierDetector</li>
 *   <li><strong>Spatial Distribution Analysis:</strong> Calculates standard deviation from smart centroid</li>
 *   <li><strong>Threshold Comparison:</strong> Flags as mobile if exceeds 500-meter threshold</li>
 * </ol>
 * 
 * <h2>Smart Centroid Integration</h2>
 * 
 * <p>The service now uses the same smart centroid calculation logic as GlobalOutlierDetector:
 * <ul>
 *   <li><strong>Quality-Based Weighting:</strong> CONNECTED measurements get 2× influence</li>
 *   <li><strong>Dual Thresholds:</strong> Both percentage (10%) and count (2) requirements</li>
 *   <li><strong>ECEF Vector Averaging:</strong> Geographic accuracy for spherical Earth</li>
 *   <li><strong>Automatic Fallback:</strong> Unweighted centroid when thresholds not met</li>
 * </ul>
 * 
 * <h2>Performance Characteristics</h2>
 * 
 * <ul>
 *   <li><strong>Time Complexity:</strong> O(n) for centroid calculation and spatial analysis</li>
 *   <li><strong>Space Complexity:</strong> O(n) for measurement storage and distance calculations</li>
 *   <li><strong>Fast Path Optimization:</strong> Immediate return for previously classified hotspots</li>
 *   <li><strong>Stream Processing:</strong> Efficient spatial standard deviation calculation</li>
 * </ul>
 * 
 * <h2>Configuration Parameters</h2>
 * 
 * <ul>
 *   <li><strong>MINIMUM_MEASUREMENTS_FOR_CLASSIFICATION:</strong> 20 (bootstrap-aligned)</li>
 *   <li><strong>MOBILE_HOTSPOT_THRESHOLD_METERS:</strong> 500.0 (requirements-specified)</li>
 *   <li><strong>USE_WEIGHTED_CENTROID:</strong> true (quality-based weighting)</li>
 *   <li><strong>MIN_CONNECTED_COUNT:</strong> 2 (absolute minimum)</li>
 *   <li><strong>MIN_CONNECTED_PERCENTAGE:</strong> 0.1 (10% threshold)</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <pre>{@code
 * // Detect hotspot with existing AP state
 * WiFiHotspotResult result = service.detect(measurements, accessPointLocation);
 * 
 * // Detect hotspot from measurements only
 * WiFiHotspotResult result = service.detect(measurements);
 * 
 * // Check result
 * if (result.isHotspot()) {
 *     logger.info("Mobile hotspot detected: {}", result.getReason());
 * }
 * }</pre>
 * 
 * <p><strong>Note:</strong> SSID pattern matching and OUI filtering occur upstream in the data pipeline,
 * so MAC addresses for obvious mobile hotspots will not reach this service.
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 2.0
 * @see WifiMeasurements for smart centroid calculation and spatial analysis
 * @see GeographicCentroidCalculator for ECEF-based centroid calculations
 * @since 1.0
 */
@Service
public class WiFiHotspotDetectionService {

    private static final String HOTSPOT_DETECTED_BASED_ON_SPATIAL_STANDARD_DEVIATION = "Spatial standard deviation: %.2f meters (threshold: %.0f meters, measurements: %d)";

    private static final Logger logger = LoggerFactory.getLogger(WiFiHotspotDetectionService.class);
    
    // Constants from requirements
    private static final int MINIMUM_MEASUREMENTS_FOR_CLASSIFICATION = 20;
    private static final double MOBILE_HOTSPOT_THRESHOLD_METERS = 500.0;
    
    // Smart centroid calculation parameters (same logic as GlobalOutlierDetector)
    private static final boolean USE_WEIGHTED_CENTROID = true;
    private static final int MIN_CONNECTED_COUNT = 2;
    private static final double MIN_CONNECTED_PERCENTAGE = 0.1;
    
    private final GeographicCentroidCalculator centroidCalculator;
    
    public WiFiHotspotDetectionService(GeographicCentroidCalculator centroidCalculator) {
        this.centroidCalculator = centroidCalculator;
    }
    
    // Pre-computed results for optimization
    public static final WiFiHotspotResult PREVIOUSLY_HOTSPOT_DETECTED = 
        new WiFiHotspotResult(true, "Previously classified as mobile hotspot");
    
    public static final WiFiHotspotResult INSUFFICIENT_DATA = 
        new WiFiHotspotResult(false, String.format("Insufficient measurements for classification (minimum %d required)", 
                                                   MINIMUM_MEASUREMENTS_FOR_CLASSIFICATION));

    /**
     * Detects if an access point is a mobile hotspot based on measurement data and existing state.
     * 
     * @param wifiMeasurements List of WiFi measurements for spatial analysis
     * @param accessPointLocation Current AP state from DynamoDB  
     * @return WiFiHotspotResult indicating detection outcome and reasoning
     */
    public WiFiHotspotResult detect(WifiMeasurements wifiMeasurements, APLocation accessPointLocation) {
        logger.debug("Starting hotspot detection for AP: {} with {} measurements", 
                     accessPointLocation.getMacAddress(), wifiMeasurements.size());

        // Fast path: if already classified as hotspot, return immediately
        if (accessPointLocation.isHotspot()) {
            logger.debug("AP {} previously classified as hotspot, skipping analysis", accessPointLocation.getMacAddress());
            return PREVIOUSLY_HOTSPOT_DETECTED;
        }
        
        return detect(wifiMeasurements);
    }

    /**
     * Detects if an access point is a mobile hotspot based on spatial distribution analysis.
     * 
     * <p>Algorithm:
     * 1. Validates minimum measurement count (20)
     * 2. Calculates smart centroid using same logic as GlobalOutlierDetector
     * 3. Calculates spatial standard deviation using spherical distance
     * 4. Compares against 500-meter threshold
     * 
     * <p><strong>Smart Centroid Calculation:</strong>
     * Uses the same intelligent decision-making logic as GlobalOutlierDetector:
     * - Considers measurement quality (CONNECTED vs SCAN)
     * - Uses ECEF vector averaging for geographic accuracy
     * - Applies dual thresholds (count and percentage) for weighting decisions
     * 
     * @param wifiMeasurements List of WiFi measurements for analysis
     * @return WiFiHotspotResult indicating detection outcome and reasoning
     * @throws IllegalArgumentException if measurements list is null or empty
     */
    public WiFiHotspotResult detect(WifiMeasurements wifiMeasurements) {
        // Validate input parameters
        if (wifiMeasurements == null) {
            throw new IllegalArgumentException("WiFi measurements cannot be null or empty");
        }
        
        if (wifiMeasurements.isEmpty()) {
            throw new IllegalArgumentException("WiFi measurements cannot be null or empty");
        }
        
        // Check minimum measurement count requirement
        if (wifiMeasurements.size() < MINIMUM_MEASUREMENTS_FOR_CLASSIFICATION) {
            return INSUFFICIENT_DATA;
        }

        // Calculate smart centroid using same logic as GlobalOutlierDetector
        Location centroid = wifiMeasurements.getCentroidLocation(
            centroidCalculator, 
            USE_WEIGHTED_CENTROID, 
            MIN_CONNECTED_COUNT, 
            MIN_CONNECTED_PERCENTAGE
        );
        
        // Calculate spatial distribution standard deviation from smart centroid
        double spatialStdDev = wifiMeasurements.spatialStandardDeviationFrom(centroid);
        
        // Determine if AP is mobile based on threshold
        boolean isMobileHotspot = spatialStdDev > MOBILE_HOTSPOT_THRESHOLD_METERS;
        
        String reason = String.format(HOTSPOT_DETECTED_BASED_ON_SPATIAL_STANDARD_DEVIATION, 
                                     spatialStdDev, MOBILE_HOTSPOT_THRESHOLD_METERS, wifiMeasurements.size());
        
        return new WiFiHotspotResult(isMobileHotspot, reason);
    }


}
