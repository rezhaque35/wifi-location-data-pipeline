package com.wifi.ap.location.measurements.outlier;

import java.util.Set;

/**
 * Result of outlier detection analysis containing detected outliers and metrics.
 * 
 * <p>This record captures the results of both global and local outlier detection
 * processes as specified in Section 3.5 of the requirements.
 */
public record OutlierDetectionResult(
    /**
     * Set of measurement IDs that were identified as global outliers.
     * These are measurements that deviate significantly from the overall centroid.
     */
    Set<String> globalOutlierIds,
    
    /**
     * Set of measurement IDs that were identified as local outliers.
     * These are measurements with low local density compared to neighbors.
     */
    Set<String> localOutlierIds,
    
    /**
     * Combined set of all outlier measurement IDs (global + local).
     * This is the set that should be excluded from localization calculations.
     */
    Set<String> allOutlierIds,
    
    /**
     * Number of measurements processed in the analysis.
     */
    int totalMeasurements,
    
    /**
     * Number of measurements with valid location data.
     */
    int validMeasurements,
    
    /**
     * Threshold used for global outlier detection (3x MAD).
     */
    double globalThreshold,
    
    /**
     * Threshold used for local outlier detection (LOF > 1.5).
     */
    double localThreshold,
    
    /**
     * Human-readable summary of detection results.
     */
    String summary
) {
    
    /**
     * Creates a result indicating no outliers were detected.
     */
    public static OutlierDetectionResult noOutliers(int totalMeasurements, int validMeasurements) {
        return new OutlierDetectionResult(
            Set.of(), Set.of(), Set.of(),
            totalMeasurements, validMeasurements,
            0.0, 1.5,
            String.format("No outliers detected in %d valid measurements", validMeasurements)
        );
    }
    
    /**
     * Creates a result for insufficient data scenarios.
     */
    public static OutlierDetectionResult insufficientData(int totalMeasurements, int validMeasurements, int minimumRequired) {
        return new OutlierDetectionResult(
            Set.of(), Set.of(), Set.of(),
            totalMeasurements, validMeasurements,
            0.0, 1.5,
            String.format("Insufficient data for outlier detection: %d valid measurements (minimum %d required)", 
                         validMeasurements, minimumRequired)
        );
    }
    
    /**
     * Gets the percentage of measurements identified as outliers.
     */
    public double getOutlierPercentage() {
        if (validMeasurements == 0) return 0.0;
        return (double) allOutlierIds.size() / validMeasurements * 100.0;
    }
    
    /**
     * Gets the number of measurements remaining after outlier removal.
     */
    public int getCleanMeasurementCount() {
        return validMeasurements - allOutlierIds.size();
    }
}
