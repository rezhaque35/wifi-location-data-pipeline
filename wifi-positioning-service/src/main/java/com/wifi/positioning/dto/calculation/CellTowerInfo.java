// src/main/java/com/wifi/positioning/dto/calculation/CellTowerInfo.java
package com.wifi.positioning.dto.calculation;

import java.util.HashMap;
import java.util.Map;

/**
 * Cell tower reference information for calculation details.
 * Includes location, coverage range, and type used for WiFi access point filtering.
 */
public record CellTowerInfo(
    Long id,
    String cellType,
    Double latitude,
    Double longitude,
    Double range
) {
    /**
     * Converts CellTowerInfo to a Map for structured logging.
     * 
     * @return Map representation of the CellTowerInfo
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("cellType", cellType);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("range", range);
        return map;
    }
}

