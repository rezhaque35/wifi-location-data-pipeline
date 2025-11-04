// src/main/java/com/wifi/positioning/dto/DiscardedAccessPoint.java
package com.wifi.positioning.dto;

/**
 * Immutable record combining WifiAPWithScan with discard reason.
 * Used for access points that were filtered out during the positioning pipeline.
 * The usage status (discard reason category) is stored as the map key in WifiAccessPoints.
 */
public record DiscardedAccessPoint(
    WifiAPWithScan pair,  // May have null wifiAccessPoint if AP not found in database
    String discardReason  // Detailed reason why this AP was discarded
) {
    
    /**
     * Gets the WiFi access point from the pair.
     * 
     * @return WifiAccessPoint or null if not found in database
     */
    public WifiAccessPoint wifiAccessPoint() {
        return pair != null ? pair.wifiAccessPoint() : null;
    }
    
    /**
     * Gets the scan result from the pair.
     * 
     * @return WifiScanResult
     */
    public WifiScanResult wifiScanResult() {
        return pair != null ? pair.wifiScanResult() : null;
    }
    
    /**
     * Gets the signal strength from the scan result.
     * 
     * @return signal strength in dBm, or null if scan result is not available
     */
    public Double getSignalStrength() {
        return pair != null && pair.wifiScanResult() != null 
            ? pair.wifiScanResult().signalStrength() 
            : null;
    }
}

