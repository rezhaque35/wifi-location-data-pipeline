package com.wifi.positioning.dto;

import java.util.List;

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
    ) {}

    /**
     * Location information for an access point.
     */
    public record LocationInfo(
        double latitude,
        double longitude,
        Double altitude
    ) {}

    /**
     * Summary of access point usage in the calculation.
     */
    public record AccessPointSummary(
        int total,
        int used,
        List<StatusCount> statusCounts
    ) {}

    /**
     * Count of access points by status.
     */
    public record StatusCount(
        String status,
        int count
    ) {}

    /**
     * Selection context information used for algorithm selection.
     */
    public record SelectionContextInfo(
        String apCountFactor,
        String signalQuality,
        String signalDistribution,
        String geometricQuality
    ) {}

    /**
     * Information about algorithm selection and weighting.
     */
    public record AlgorithmSelectionInfo(
        String algorithm,
        boolean selected,
        List<String> reasons,
        Double weight
    ) {}
}
