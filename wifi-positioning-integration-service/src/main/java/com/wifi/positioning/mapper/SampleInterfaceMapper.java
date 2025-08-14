// com/wifi/positioning/mapper/SampleInterfaceMapper.java
package com.wifi.positioning.mapper;

import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for mapping sample interface requests to WiFi positioning service requests.
 * Handles frequency defaults, validation, and format conversion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SampleInterfaceMapper {

    private static final String APPLICATION_NAME = "wifi-positioning-integration-service";
    
    private final IntegrationProperties properties;

    /**
     * Maps a sample interface source request to a WiFi positioning request.
     * 
     * @param sourceRequest The original sample interface request
     * @param options Integration options (e.g., calculationDetail)
     * @return Mapped WiFi positioning request
     * @throws IllegalArgumentException if mapping fails due to invalid data
     */
    public WifiPositioningRequest mapToPositioningRequest(
            SampleInterfaceSourceRequest sourceRequest, 
            IntegrationOptions options) {
        
        log.debug("Mapping sample interface request to positioning request");
        
        SvcReq svcReq = sourceRequest.getSvcBody().getSvcReq();
        
        // Map WiFi scan results
        List<WifiScanResult> wifiScanResults = mapWifiInfo(svcReq.getWifiInfo());
        
        if (wifiScanResults.isEmpty()) {
            throw new IllegalArgumentException("No valid WiFi scan results after mapping and frequency filtering");
        }
        
        // Create positioning request
        WifiPositioningRequest positioningRequest = new WifiPositioningRequest();
        positioningRequest.setWifiScanResults(wifiScanResults);
        positioningRequest.setClient(svcReq.getClientId());
        positioningRequest.setRequestId(svcReq.getRequestId());
        positioningRequest.setApplication(APPLICATION_NAME);
        positioningRequest.setCalculationDetail(
            options != null && options.getCalculationDetail() != null 
                ? options.getCalculationDetail() 
                : false
        );
        
        log.debug("Successfully mapped {} WiFi scan results", wifiScanResults.size());
        return positioningRequest;
    }

    /**
     * Maps WiFi info from sample interface to WiFi scan results.
     * Handles frequency validation and default assignment based on configuration.
     * 
     * @param wifiInfoList List of WiFi info from sample interface
     * @return List of mapped WiFi scan results
     */
    private List<WifiScanResult> mapWifiInfo(List<WifiInfo> wifiInfoList) {
        List<WifiScanResult> results = new ArrayList<>();
        int droppedCount = 0;
        int defaultFrequencyCount = 0;
        
        for (WifiInfo wifiInfo : wifiInfoList) {
            try {
                WifiScanResult scanResult = mapSingleWifiInfo(wifiInfo);
                if (scanResult != null) {
                    results.add(scanResult);
                    if (wifiInfo.getFrequency() == null) {
                        defaultFrequencyCount++;
                    }
                } else {
                    droppedCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to map WiFi info for MAC {}: {}", wifiInfo.getId(), e.getMessage());
                droppedCount++;
            }
        }
        
        if (droppedCount > 0) {
            log.info("Dropped {} WiFi scan results due to missing frequency", droppedCount);
        }
        if (defaultFrequencyCount > 0) {
            log.info("Applied default frequency to {} WiFi scan results", defaultFrequencyCount);
        }
        
        return results;
    }

    /**
     * Maps a single WiFi info entry to a WiFi scan result.
     * 
     * @param wifiInfo Single WiFi info entry
     * @return Mapped WiFi scan result, or null if dropped due to missing frequency
     */
    private WifiScanResult mapSingleWifiInfo(WifiInfo wifiInfo) {
        if (wifiInfo.getId() == null || wifiInfo.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("WiFi MAC address (id) is required");
        }
        
        if (wifiInfo.getSignalStrength() == null) {
            throw new IllegalArgumentException("Signal strength is required");
        }
        
        // Handle frequency validation and defaults
        Integer frequency = wifiInfo.getFrequency();
        if (frequency == null) {
            if (properties.getMapping().isDropMissingFrequency()) {
                log.debug("Dropping WiFi scan result for MAC {} due to missing frequency", wifiInfo.getId());
                return null;
            } else {
                frequency = properties.getMapping().getDefaultFrequencyMhz();
                log.debug("Using default frequency {} for MAC {}", frequency, wifiInfo.getId());
            }
        }
        
        // Validate frequency range
        if (frequency < 2400 || frequency > 6000) {
            throw new IllegalArgumentException(
                String.format("Frequency %d MHz is outside valid range (2400-6000 MHz)", frequency)
            );
        }
        
        // Create WiFi scan result
        WifiScanResult scanResult = new WifiScanResult();
        scanResult.setMacAddress(normalizeMacAddress(wifiInfo.getId()));
        scanResult.setSignalStrength(wifiInfo.getSignalStrength());
        scanResult.setFrequency(frequency);
        scanResult.setSsid(wifiInfo.getSsid());
        
        return scanResult;
    }

    /**
     * Normalizes MAC address format to ensure consistency.
     * Converts to uppercase and uses colon separators.
     * 
     * @param macAddress MAC address in various formats
     * @return Normalized MAC address in XX:XX:XX:XX:XX:XX format
     */
    private String normalizeMacAddress(String macAddress) {
        if (macAddress == null) {
            return null;
        }
        
        // Remove any existing separators and convert to uppercase
        String normalized = macAddress.replaceAll("[:-]", "").toUpperCase();
        
        // Validate length
        if (normalized.length() != 12) {
            throw new IllegalArgumentException("Invalid MAC address length: " + macAddress);
        }
        
        // Add colon separators
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalized.length(); i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(normalized, i, i + 2);
        }
        
        return sb.toString();
    }
}
