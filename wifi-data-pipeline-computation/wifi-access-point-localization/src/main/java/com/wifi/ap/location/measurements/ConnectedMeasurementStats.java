// wifi-data-pipeline-computation/wifi-access-point-localization/src/main/java/com/wifi/ap/location/measurements/ConnectedMeasurementStats.java
package com.wifi.ap.location.measurements;

/**
 * Statistics about CONNECTED measurements within a collection.
 * 
 * <p>This record encapsulates the key metrics needed for determining
 * whether to use weighted centroid calculations and other quality-based
 * decision making in WiFi access point localization algorithms.
 * 
 * @param connectedCount Number of measurements with "CONNECTED" status
 * @param totalCount Total number of measurements in the collection
 * @param connectedPercentage Ratio of connected to total measurements (0.0 to 1.0)
 */
public record ConnectedMeasurementStats(
    long connectedCount,
    int totalCount, 
    double connectedPercentage
) {
    
    /**
     * Checks if the connected measurements meet both count and percentage thresholds.
     * 
     * @param minCount Minimum absolute count of connected measurements required
     * @param minPercentage Minimum percentage of connected measurements required (0.0 to 1.0)
     * @return true if both thresholds are met
     */
    public boolean meetsThresholds(int minCount, double minPercentage) {
        return connectedCount >= minCount && connectedPercentage >= minPercentage;
    }


    @Override
    public String toString() {
        return String.format("ConnectedStats[count=%d, total=%d, percentage=%.1f%%]", 
                           connectedCount, totalCount, connectedPercentage * 100);
    }
}
