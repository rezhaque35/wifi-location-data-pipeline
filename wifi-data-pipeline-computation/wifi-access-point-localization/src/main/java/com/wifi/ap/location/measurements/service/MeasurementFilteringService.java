package com.wifi.ap.location.measurements.service;

import com.wifi.ap.location.estimation.state.APState;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.outlier.OutlierDetectionResult;
import com.wifi.ap.location.estimation.WifiAccessPointLocation;
import com.wifi.ap.location.measurements.outlier.global.GlobalOutlierDetector;
import com.wifi.ap.location.measurements.outlier.local.LocalOutlierDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for filtering WiFi measurements by removing global and local outliers.
 * 
 * <p>Implements Section 3.5 of the requirements with sequential outlier processing:
 * 1. Apply global outlier detection (centroid-based with MAD)
 * 2. Apply local outlier detection (LOF algorithm) 
 * 3. Combine results and filter measurements
 * 4. Log outlier patterns for analysis
 */
@Service
public class APLocationMeasurementsFilterService {
    
    private static final Logger logger = LoggerFactory.getLogger(APLocationMeasurementsFilterService.class);
    
    // Minimum measurements required for outlier detection
    private static final int MIN_MEASUREMENTS_FOR_OUTLIER_DETECTION = 10;
    
    private final GlobalOutlierDetector globalOutlierDetector;
    private final LocalOutlierDetector localOutlierDetector;
    
    public APLocationMeasurementsFilterService(
            GlobalOutlierDetector globalOutlierDetector,
            LocalOutlierDetector localOutlierDetector) {
        this.globalOutlierDetector = globalOutlierDetector;
        this.localOutlierDetector = localOutlierDetector;
    }
    
    /**
     * Filters measurements by removing detected outliers.
     * 
     * @param apLocationMeasurements List of measurements to filter
     * @param currentEstimation Current AP location estimate (unused for now, future enhancement)
     * @return Filtered list of measurements with outliers removed
     */
    public List<WifiMeasurement> filter(List<WifiMeasurement> apLocationMeasurements,
                                        Optional<WifiAccessPointLocation> currentEstimation) {
        
  
            
  
        // Perform outlier detection
        OutlierDetectionResult detectionResult = detectOutliers(apLocationMeasurements);
        
        // Filter out detected outliers
        List<WifiMeasurement> filteredMeasurements = apLocationMeasurements.stream()
            .filter(m -> !detectionResult.allOutlierIds().contains(m.id()))
            .toList();
            
        logger.info("Outlier filtering completed: {} measurements filtered from {} valid measurements", 
                   detectionResult.allOutlierIds().size(), apLocationMeasurements.size());
        logger.debug("Detection summary: {}", detectionResult.summary());
        
        return filteredMeasurements;
    }
    
    /**
     * Performs comprehensive outlier detection using both global and local methods.
     * 
     * @param measurements List of measurements with valid location data
     * @return OutlierDetectionResult containing detected outliers and metrics
     */
    public OutlierDetectionResult detectOutliers(List<WifiMeasurement> measurements) {
        if (measurements.size() < MIN_MEASUREMENTS_FOR_OUTLIER_DETECTION) {
            return OutlierDetectionResult.insufficientData(
                measurements.size(), measurements.size(), MIN_MEASUREMENTS_FOR_OUTLIER_DETECTION);
        }
        
        logger.debug("Performing outlier detection on {} measurements", measurements.size());
        
        // Step 1: Global outlier detection
        Set<String> globalOutliers = globalOutlierDetector.detectGlobalOutliers(measurements);
        
        // Step 2: Local outlier detection  
        // Create a basic APState for local outlier detection
        APState apState = APState.createBasic();
        Set<String> localOutliers = localOutlierDetector.detectLocalOutliers(measurements, apState);
        
        // Step 3: Combine results
        Set<String> allOutliers = new HashSet<>();
        allOutliers.addAll(globalOutliers);
        allOutliers.addAll(localOutliers);
        
        // Create summary
        String summary = String.format(
            "Outlier detection: %d global, %d local, %d total outliers from %d measurements (%.1f%% filtered)",
            globalOutliers.size(), localOutliers.size(), allOutliers.size(), 
            measurements.size(), (double) allOutliers.size() / measurements.size() * 100.0
        );
        
        OutlierDetectionResult result = new OutlierDetectionResult(
            globalOutliers, localOutliers, allOutliers,
            measurements.size(), measurements.size(),
            3.0, // Global threshold (3x MAD)
            1.5, // Local threshold (LOF)
            summary
        );
        
        logger.info("Outlier detection completed: {}", summary);
        
        // Log patterns for analysis
        logOutlierPatterns(result, measurements);
        
        return result;
    }
    
    /**
     * Logs outlier patterns for analysis and monitoring.
     */
    private void logOutlierPatterns(OutlierDetectionResult result, List<WifiMeasurement> measurements) {
        if (result.allOutlierIds().isEmpty()) {
            return;
        }
        
        // Analyze outlier characteristics
        List<WifiMeasurement> outliers = measurements.stream()
            .filter(m -> result.allOutlierIds().contains(m.id()))
            .toList();
            
        // Log statistical patterns
        double avgRssi = outliers.stream().mapToInt(WifiMeasurement::rssi).average().orElse(0.0);
        double avgAccuracy = outliers.stream()
            .filter(m -> m.locationAccuracy() != null)
            .mapToDouble(WifiMeasurement::locationAccuracy)
            .average()
            .orElse(0.0);
            
        if (logger.isDebugEnabled()) {
            logger.debug("Outlier characteristics - Avg RSSI: {} dBm, Avg accuracy: {}m", 
                        String.format("%.1f", avgRssi), String.format("%.1f", avgAccuracy));
        }
        
        // Log connection status distribution
        long connectedOutliers = outliers.stream()
            .filter(m -> "CONNECTED".equals(m.connectionStatus()))
            .count();
        long scanOutliers = outliers.size() - connectedOutliers;
        
        logger.debug("Outlier connection distribution - CONNECTED: {}, SCAN: {}", 
                    connectedOutliers, scanOutliers);
    }
    

}
