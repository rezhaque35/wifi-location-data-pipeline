// src/main/java/com/wifi/positioning/dto/calculation/SelectionContextInfo.java
package com.wifi.positioning.dto.calculation;

import java.util.HashMap;
import java.util.Map;

/**
 * Selection context information used for algorithm selection.
 */
public record SelectionContextInfo(
    String apCountFactor,
    String signalQuality,
    String signalDistribution,
    String geometricQuality
) {
    /**
     * Converts SelectionContextInfo to a Map for structured logging.
     * 
     * @return Map representation of the SelectionContextInfo
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("apCountFactor", apCountFactor);
        map.put("signalQuality", signalQuality);
        map.put("signalDistribution", signalDistribution);
        map.put("geometricQuality", geometricQuality);
        return map;
    }
}

