package com.wifi.ap.location.estimate.service;

import com.wifi.ap.location.estimate.dto.WiFiHotspotResult;
import com.wifi.ap.location.estimate.WifiAccessPointLocation;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for detecting mobile WiFi hotspots based on spatial distribution analysis.
 * 
 * <p>Implements the hotspot detection algorithm from Section 3.4.1 of the requirements:
 * - Calculates standard deviation of measurement locations
 * - Flags as mobile if exceeds 500-meter threshold  
 * - Requires minimum 20 measurements for classification
 * 
 * <p>Note: SSID pattern matching and OUI filtering occur upstream in the data pipeline,
 * so MAC addresses for obvious mobile hotspots will not reach this service.
 */
@Service
public class WiFiHotspotDetectionService {

    private static final String HOTSPOT_DETECTED_BASED_ON_SPATIAL_STANDARD_DEVIATION = "Spatial standard deviation: %.2f meters (threshold: %.0f meters, measurements: %d)";

    private static final Logger logger = LoggerFactory.getLogger(WiFiHotspotDetectionService.class);
    
    // Constants from requirements
    private static final int MINIMUM_MEASUREMENTS_FOR_CLASSIFICATION = 20;
    private static final double MOBILE_HOTSPOT_THRESHOLD_METERS = 500.0;
    
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
    public WiFiHotspotResult detect(List<WifiMeasurement> wifiMeasurements, WifiAccessPointLocation accessPointLocation) {
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
     * 2. Filters valid location measurements (non-null lat/lon)
     * 3. Calculates spatial standard deviation using spherical distance
     * 4. Compares against 500-meter threshold
     * 
     * @param wifiMeasurements List of WiFi measurements for analysis
     * @return WiFiHotspotResult indicating detection outcome and reasoning
     * @throws IllegalArgumentException if measurements list is null or empty
     */
    public WiFiHotspotResult detect(List<WifiMeasurement> wifiMeasurements) {


        // Check minimum measurement count requirement
        if (wifiMeasurements.size() < MINIMUM_MEASUREMENTS_FOR_CLASSIFICATION) {
            return INSUFFICIENT_DATA;
        }

        // Calculate spatial distribution standard deviation
        double spatialStdDev = calculateSpatialStandardDeviation(wifiMeasurements);
        
        // Determine if AP is mobile based on threshold
        boolean isMobileHotspot = spatialStdDev > MOBILE_HOTSPOT_THRESHOLD_METERS;
        
        String reason = String.format(HOTSPOT_DETECTED_BASED_ON_SPATIAL_STANDARD_DEVIATION, 
                                     spatialStdDev, MOBILE_HOTSPOT_THRESHOLD_METERS, wifiMeasurements.size());
        
        return new WiFiHotspotResult(isMobileHotspot, reason);
    }

    /**
     * Calculates the spatial standard deviation of measurement locations.
     * 
     * <p>Delegates to the domain object for calculation with comprehensive logging.
     * 
     * @param measurements List of measurements with valid location data
     * @return Standard deviation of distances from centroid in meters
     */
    private double calculateSpatialStandardDeviation(List<WifiMeasurement> measurements) {
        WifiMeasurements wifiMeasurements = WifiMeasurements.of(measurements);
        return wifiMeasurements.spatialStandardDeviationWithLogging(logger);
    }



}
