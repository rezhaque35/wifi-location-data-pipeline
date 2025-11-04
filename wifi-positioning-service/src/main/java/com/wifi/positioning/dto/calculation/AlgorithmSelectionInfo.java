// src/main/java/com/wifi/positioning/dto/calculation/AlgorithmSelectionInfo.java
package com.wifi.positioning.dto.calculation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information about algorithm selection and weighting.
 */
public record AlgorithmSelectionInfo(
    String algorithm,
    boolean selected,
    List<String> reasons,
    Double weight
) {
    /**
     * Converts AlgorithmSelectionInfo to a Map for structured logging.
     * 
     * @return Map representation of the AlgorithmSelectionInfo
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("algorithm", algorithm);
        map.put("selected", selected);
        map.put("reasons", reasons != null ? reasons : Collections.emptyList());
        map.put("weight", weight);
        return map;
    }
}

