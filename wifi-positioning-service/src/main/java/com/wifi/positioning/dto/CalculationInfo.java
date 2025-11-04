package com.wifi.positioning.dto;

import com.wifi.positioning.dto.calculation.AccessPointInfo;
import com.wifi.positioning.dto.calculation.AccessPointSummary;
import com.wifi.positioning.dto.calculation.AlgorithmSelectionInfo;
import com.wifi.positioning.dto.calculation.SelectionContextInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured calculation information for WiFi positioning responses.
 * Contains detailed information about access points, algorithm selection, and calculation context.
 */
public record CalculationInfo(
    List<AccessPointInfo> accessPoints,
    AccessPointSummary accessPointSummary,
    SelectionContextInfo selectionContext,
    List<AlgorithmSelectionInfo> algorithmSelection
) {

    /**
     * Converts CalculationInfo to a Map for structured logging.
     * Includes all calculation details and nested objects.
     * 
     * @return Map representation of the CalculationInfo
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("accessPoints", accessPoints != null 
            ? accessPoints.stream().map(AccessPointInfo::toMap).toList()
            : Collections.emptyList());
        map.put("accessPointSummary", accessPointSummary != null ? accessPointSummary.toMap() : Collections.emptyMap());
        map.put("selectionContext", selectionContext != null ? selectionContext.toMap() : Collections.emptyMap());
        map.put("algorithmSelection", algorithmSelection != null 
            ? algorithmSelection.stream().map(AlgorithmSelectionInfo::toMap).toList()
            : Collections.emptyList());
        return map;
    }
}
