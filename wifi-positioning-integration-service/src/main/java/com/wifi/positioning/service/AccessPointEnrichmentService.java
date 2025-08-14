// com/wifi/positioning/service/AccessPointEnrichmentService.java
package com.wifi.positioning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.AccessPointDetail;
import com.wifi.positioning.dto.AccessPointEnrichmentMetrics;
import com.wifi.positioning.dto.WifiInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for enriching access point information from positioning service responses.
 * Extracts AP details and computes usage metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessPointEnrichmentService {

    private static final Set<String> ELIGIBLE_STATUSES = Set.of("active", "warning");
    
    private final ObjectMapper objectMapper;

    /**
     * Enriches access point information using positioning service response.
     * 
     * @param originalWifiInfo The original WiFi scan results from the request
     * @param positioningServiceResponse Response from positioning service
     * @return Enrichment metrics with AP details and statistics
     */
    public AccessPointEnrichmentMetrics enrichAccessPoints(
            List<WifiInfo> originalWifiInfo, 
            Object positioningServiceResponse) {
        
        log.debug("Enriching access point information for {} APs", originalWifiInfo.size());
        
        AccessPointEnrichmentMetrics metrics = new AccessPointEnrichmentMetrics();
        
        // Extract AP information from positioning service response
        Map<String, PositioningApInfo> positioningApMap = extractPositioningApInfo(positioningServiceResponse);
        Integer usedApCount = extractUsedApCount(positioningServiceResponse);
        
        // Create detailed AP information
        List<AccessPointDetail> apDetails = new ArrayList<>();
        
        for (WifiInfo wifiInfo : originalWifiInfo) {
            AccessPointDetail detail = createAccessPointDetail(wifiInfo, positioningApMap);
            apDetails.add(detail);
        }
        
        metrics.setApDetails(apDetails);
        
        // Compute aggregate metrics
        computeAggregateMetrics(metrics, apDetails, usedApCount);
        
        log.debug("Enrichment completed - found: {}/{}, used: {}", 
            metrics.getFoundApCount(), originalWifiInfo.size(), metrics.getUsedApCount());
        
        return metrics;
    }

    /**
     * Extracts AP information from positioning service calculationInfo.
     */
    private Map<String, PositioningApInfo> extractPositioningApInfo(Object response) {
        Map<String, PositioningApInfo> apMap = new HashMap<>();
        
        if (response == null) {
            return apMap;
        }
        
        try {
            JsonNode node = objectMapper.valueToTree(response);
            JsonNode calculationInfo = node.get("calculationInfo");
            
            if (calculationInfo == null) {
                log.debug("No calculationInfo found in positioning response");
                return apMap;
            }
            
            JsonNode accessPoints = calculationInfo.get("accessPoints");
            if (accessPoints != null && accessPoints.isArray()) {
                for (JsonNode apNode : accessPoints) {
                    PositioningApInfo apInfo = parseAccessPointNode(apNode);
                    if (apInfo != null && apInfo.getBssid() != null) {
                        apMap.put(apInfo.getBssid(), apInfo);
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to extract AP information from positioning response: {}", e.getMessage());
        }
        
        return apMap;
    }

    /**
     * Parses a single access point node from calculationInfo.
     */
    private PositioningApInfo parseAccessPointNode(JsonNode apNode) {
        try {
            PositioningApInfo apInfo = new PositioningApInfo();
            
            apInfo.setBssid(getTextValue(apNode, "bssid"));
            apInfo.setStatus(getTextValue(apNode, "status"));
            apInfo.setUsage(getTextValue(apNode, "usage"));
            
            JsonNode location = apNode.get("location");
            if (location != null) {
                apInfo.setLatitude(getDoubleValue(location, "latitude"));
                apInfo.setLongitude(getDoubleValue(location, "longitude"));
                apInfo.setAltitude(getDoubleValue(location, "altitude"));
            }
            
            return apInfo;
            
        } catch (Exception e) {
            log.debug("Failed to parse access point node: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the number of APs used in positioning calculation.
     */
    private Integer extractUsedApCount(Object response) {
        if (response == null) {
            return null;
        }
        
        try {
            JsonNode node = objectMapper.valueToTree(response);
            JsonNode wifiPosition = node.get("wifiPosition");
            
            if (wifiPosition != null) {
                return getIntegerValue(wifiPosition, "apCount");
            }
            
        } catch (Exception e) {
            log.debug("Failed to extract used AP count: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Creates detailed AP information by combining original request data with positioning service data.
     */
    private AccessPointDetail createAccessPointDetail(
            WifiInfo wifiInfo, 
            Map<String, PositioningApInfo> positioningApMap) {
        
        AccessPointDetail detail = new AccessPointDetail();
        
        // Set provided information from original request
        detail.setMac(normalizeMacAddress(wifiInfo.getId()));
        detail.setProvidedSsid(wifiInfo.getSsid());
        detail.setProvidedRssi(wifiInfo.getSignalStrength());
        detail.setProvidedFrequency(wifiInfo.getFrequency());
        
        // Check if this AP was found in positioning service
        PositioningApInfo positioningInfo = positioningApMap.get(detail.getMac());
        detail.setFound(positioningInfo != null);
        
        if (positioningInfo != null) {
            // Set database information
            detail.setDbStatus(positioningInfo.getStatus());
            detail.setDbLatitude(positioningInfo.getLatitude());
            detail.setDbLongitude(positioningInfo.getLongitude());
            detail.setDbAltitude(positioningInfo.getAltitude());
            detail.setUsed("used".equals(positioningInfo.getUsage()));
            
            // Determine eligibility based on status
            detail.setEligible(ELIGIBLE_STATUSES.contains(positioningInfo.getStatus()));
        } else {
            detail.setDbStatus(null);
            detail.setDbLatitude(null);
            detail.setDbLongitude(null);
            detail.setDbAltitude(null);
            detail.setUsed(false);
            detail.setEligible(false);
        }
        
        return detail;
    }

    /**
     * Computes aggregate metrics from AP details.
     */
    private void computeAggregateMetrics(
            AccessPointEnrichmentMetrics metrics, 
            List<AccessPointDetail> apDetails, 
            Integer usedApCount) {
        
        int totalAps = apDetails.size();
        int foundCount = (int) apDetails.stream().filter(ap -> Boolean.TRUE.equals(ap.getFound())).count();
        int notFoundCount = totalAps - foundCount;
        int eligibleCount = (int) apDetails.stream().filter(ap -> Boolean.TRUE.equals(ap.getEligible())).count();
        
        metrics.setFoundApCount(foundCount);
        metrics.setNotFoundApCount(notFoundCount);
        metrics.setEligibleApCount(eligibleCount);
        metrics.setUsedApCount(usedApCount);
        
        // Calculate percentages
        if (totalAps > 0) {
            metrics.setPercentRequestFound((double) foundCount / totalAps * 100.0);
        }
        
        if (foundCount > 0 && usedApCount != null) {
            metrics.setPercentFoundUsed((double) usedApCount / foundCount * 100.0);
        }
        
        // Calculate unknown exclusions
        if (eligibleCount > 0 && usedApCount != null) {
            metrics.setUnknownExclusions(Math.max(0, eligibleCount - usedApCount));
        }
        
        // Count APs by status
        Map<String, Integer> statusCounts = apDetails.stream()
            .filter(ap -> Boolean.TRUE.equals(ap.getFound()) && ap.getDbStatus() != null)
            .collect(Collectors.groupingBy(
                AccessPointDetail::getDbStatus,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
        
        metrics.setFoundApStatusCounts(statusCounts);
    }

    /**
     * Normalizes MAC address format.
     */
    private String normalizeMacAddress(String macAddress) {
        if (macAddress == null) {
            return null;
        }
        
        // Remove separators and convert to uppercase
        String normalized = macAddress.replaceAll("[:-]", "").toUpperCase();
        
        // Add colon separators
        if (normalized.length() == 12) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < normalized.length(); i += 2) {
                if (i > 0) sb.append(':');
                sb.append(normalized, i, i + 2);
            }
            return sb.toString();
        }
        
        return macAddress; // Return as-is if invalid format
    }

    // Helper methods for JSON parsing
    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asText(null) : null;
    }
    
    private Double getDoubleValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asDouble() : null;
    }
    
    private Integer getIntegerValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asInt() : null;
    }

    /**
     * Internal class for positioning service AP information.
     */
    private static class PositioningApInfo {
        private String bssid;
        private String status;
        private String usage;
        private Double latitude;
        private Double longitude;
        private Double altitude;
        
        // Getters and setters
        public String getBssid() { return bssid; }
        public void setBssid(String bssid) { this.bssid = bssid; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getUsage() { return usage; }
        public void setUsage(String usage) { this.usage = usage; }
        
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        
        public Double getAltitude() { return altitude; }
        public void setAltitude(Double altitude) { this.altitude = altitude; }
    }
}
