package com.wifi.positioning.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for positioning requests. This record captures all necessary data to perform
 * a positioning calculation.
 */
public record WifiPositioningRequest(
        @NotEmpty(message = "At least one WiFi scan result is required")
        @Size(min = 1, max = 35, message = "Between 1 and 30 WiFi scan results must be provided")
        @Valid
        List<WifiScanResult> wifiScanResults,
        @NotBlank(message = "Client is required")
        @Size(max = 50, message = "Client must be at most 50 characters")
        String client,
        @NotBlank(message = "Request ID is required")
        @Size(max = 64, message = "Request ID must be at most 64 characters")
        String requestId,
        @Size(max = 100, message = "Application must be at most 100 characters")
        String application,

        /**
         * Optional cell tower information for validating WiFi access point locations.
         * When provided, APs outside cell tower range will be filtered out.
         */
        @Size(max = 10, message = "Maximum 10 cell towers allowed")
        @Valid
        List<CellInfo> cellInfo,

        /**
         * When set to true, detailed calculation information will be included in the response. This
         * includes the selection context and algorithm selection reasoning.
         */
        Boolean calculationDetail) {
    /**
     * Compact constructor for WifiPositioningRequest. Sets default values for optional fields.
     */
    public WifiPositioningRequest {
        // Set default value for calculationDetail if null
        if (calculationDetail == null) {
            calculationDetail = false;
        }
    }

    /**
     * Converts WifiPositioningRequest to a Map for structured logging.
     * Includes all request fields with summarized WiFi scan results.
     * 
     * @return Map representation of the WifiPositioningRequest
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("client", client);
        map.put("requestId", requestId);
        map.put("application", application);
        map.put("calculationDetail", calculationDetail);
        map.put("wifiScanResultsCount", wifiScanResults != null ? wifiScanResults.size() : 0);
        map.put("cellInfoCount", cellInfo != null ? cellInfo.size() : 0);
        
        // Include summarized scan results (MAC addresses and signal strengths)
        if (wifiScanResults != null && !wifiScanResults.isEmpty()) {
            List<Map<String, Object>> scanResultsSummary = wifiScanResults.stream()
                .map(scanResult -> {
                    Map<String, Object> srMap = new HashMap<>();
                    srMap.put("macAddress", scanResult.macAddress());
                    srMap.put("signalStrength", scanResult.signalStrength());
                    return srMap;
                })
                .toList();
            map.put("wifiScanResults", scanResultsSummary);
        }
        
        // Include cell tower information summary
        if (cellInfo != null && !cellInfo.isEmpty()) {
            List<Map<String, Object>> cellInfoSummary = cellInfo.stream()
                .map(CellInfo::toMap)
                .toList();
            map.put("cellInfo", cellInfoSummary);
        }
        
        return map;
    }
}
