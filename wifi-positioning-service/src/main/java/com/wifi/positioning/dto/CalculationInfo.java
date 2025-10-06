package com.wifi.positioning.dto;

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

    /**
     * Summary of access point usage in the calculation.
     */
    public record AccessPointSummary(
        int total,
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
            map.put("used", used);
            map.put("statusCounts", statusCounts != null 
                ? statusCounts.stream().map(StatusCount::toMap).toList()
                : Collections.emptyList());
            return map;
        }
    }

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
