// src/main/java/com/wifi/positioning/dto/calculation/AccessPointSummary.java
package com.wifi.positioning.dto.calculation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Summary of access point usage in the calculation.
 */
public record AccessPointSummary(
    int total,
    int known,
    int used,
    List<StatusCount> statusCounts
) {
    /**
     * Converts AccessPointSummary to a Map for structured logging.
     * 
     * @return Map representation of the AccessPointSummary
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("known", known);
        map.put("used", used);
        map.put("statusCounts", statusCounts != null 
            ? statusCounts.stream().map(StatusCount::toMap).toList()
            : Collections.emptyList());
        return map;
    }
}

