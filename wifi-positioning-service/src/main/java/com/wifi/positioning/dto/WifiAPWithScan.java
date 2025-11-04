// src/main/java/com/wifi/positioning/dto/WifiAPWithScan.java
package com.wifi.positioning.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable record combining WiFi access point location data with scan result.
 * Used for access points that passed all filtering and are ready for positioning calculation.
 * No status tracking needed - being in the valid list means it's used.
 */
public record WifiAPWithScan(
    WifiAccessPoint wifiAccessPoint,
    WifiScanResult wifiScanResult
) {
    
    /**
     * Gets the location of the access point.
     * 
     * @return Position object with latitude, longitude, and optional altitude
     */
    public Position getLocation() {
        if (wifiAccessPoint == null) {
            return null;
        }
        return new Position(
            wifiAccessPoint.getLatitude(),
            wifiAccessPoint.getLongitude(),
            wifiAccessPoint.getAltitude(),
            0.0, // accuracy not available from stored AP
            0.0  // confidence not available from stored AP
        );
    }
    
    /**
     * Gets the signal strength from the scan result.
     * 
     * @return signal strength in dBm, or null if scan result is not available
     */
    public Double getSignalStrength() {
        return wifiScanResult != null ? wifiScanResult.signalStrength() : null;
    }
    
    /**
     * Converts WifiAPWithScan to a Map for structured logging.
     * 
     * @return Map representation of the WifiAPWithScan
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (wifiAccessPoint != null) {
            map.put("macAddress", wifiAccessPoint.getMacAddress());
            map.put("latitude", wifiAccessPoint.getLatitude());
            map.put("longitude", wifiAccessPoint.getLongitude());
            map.put("status", wifiAccessPoint.getStatus());
        }
        if (wifiScanResult != null) {
            map.put("signalStrength", wifiScanResult.signalStrength());
        }
        return map;
    }
}


