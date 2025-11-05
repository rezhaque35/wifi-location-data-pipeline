// src/main/java/com/wifi/positioning/dto/calculation/AccessPointInfo.java
package com.wifi.positioning.dto.calculation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Information about a specific access point used in positioning calculation.
 * 
 * @param bssid MAC address of the access point
 * @param location Geographic location of the access point
 * @param status AP status from database (active, warning, error, etc.)
 * @param usage How the AP was used in calculation (USED, DISCARDED_STATUS, DISCARDED_CELL_RANGE, etc.)
 * @param discardReason Detailed reason why AP was discarded (null if USED)
 */
public record AccessPointInfo(
    String bssid,
    LocationInfo location,
    String status,
    String usage,
    String discardReason
) {
    /**
     * Converts AccessPointInfo to a Map for structured logging.
     * 
     * @return Map representation of the AccessPointInfo
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("bssid", bssid);
        map.put("location", location != null ? location.toMap() : Collections.emptyMap());
        map.put("status", status);
        map.put("usage", usage);
        if (discardReason != null) {
            map.put("discardReason", discardReason);
        }
        return map;
    }
}

