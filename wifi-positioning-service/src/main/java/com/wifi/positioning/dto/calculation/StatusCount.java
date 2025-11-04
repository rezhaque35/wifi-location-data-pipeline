// src/main/java/com/wifi/positioning/dto/calculation/StatusCount.java
package com.wifi.positioning.dto.calculation;

import java.util.HashMap;
import java.util.Map;

/**
 * Count of access points by status.
 */
public record StatusCount(
    String status,
    int count
) {
    /**
     * Converts StatusCount to a Map for structured logging.
     * 
     * @return Map representation of the StatusCount
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", status);
        map.put("count", count);
        return map;
    }
}

