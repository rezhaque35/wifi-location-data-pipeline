// src/main/java/com/wifi/positioning/dto/calculation/AccessPointInfo.java
package com.wifi.positioning.dto.calculation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Information about a specific access point used in positioning calculation.
 */
public record AccessPointInfo(
    String bssid,
    LocationInfo location,
    String status,
    String usage
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
        return map;
    }
}

