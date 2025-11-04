// src/main/java/com/wifi/positioning/dto/calculation/LocationInfo.java
package com.wifi.positioning.dto.calculation;

import java.util.HashMap;
import java.util.Map;

/**
 * Location information for an access point.
 */
public record LocationInfo(
    double latitude,
    double longitude,
    Double altitude
) {
    /**
     * Converts LocationInfo to a Map for structured logging.
     * 
     * @return Map representation of the LocationInfo
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("altitude", altitude);
        return map;
    }
}

