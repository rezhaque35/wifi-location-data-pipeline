// com/wifi/positioning/mapper/VLSSInterfaceMapper.java
package com.wifi.positioning.mapper;

import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for mapping VLSS interface requests to WiFi positioning service requests.
 * Handles frequency defaults, validation, and format conversion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VLSSInterfaceMapper {

    private static final String APPLICATION_NAME = "GIZMO";

    private final IntegrationProperties properties;

    /**
     * Maps a VLSS interface source request to a WiFi positioning request.
     *
     * @param sourceRequest The original VLSS interface request
     * @param options       Integration options (e.g., calculationDetail)
     * @return Mapped WiFi positioning request
     * @throws IllegalArgumentException if mapping fails due to invalid data
     */
    public WifiPositioningRequest mapToPositioningRequest(
            SampleInterfaceSourceRequest sourceRequest,
            IntegrationOptions options) {

        SvcReq svcReq = sourceRequest.getSvcBody().getSvcReq();

        // Map WiFi scan results
        List<WifiScanResult> wifiScanResults = mapWifiInfo(svcReq.getWifiInfo());

        if (wifiScanResults.isEmpty()) {
            throw new IllegalArgumentException("No valid WiFi scan results after mapping");
        }

        // Create positioning request
        WifiPositioningRequest positioningRequest = new WifiPositioningRequest();
        positioningRequest.setWifiScanResults(wifiScanResults);
        positioningRequest.setClient(svcReq.getClientId());
        positioningRequest.setRequestId(svcReq.getRequestId());
        positioningRequest.setApplication(APPLICATION_NAME);
        positioningRequest.setCalculationDetail(true);

        log.debug("Successfully mapped {} WiFi scan results", wifiScanResults.size());
        return positioningRequest;
    }

    /**
     * Maps WiFi info from VLSS interface to WiFi scan results.
     * Always processes all WiFi info, applying defaults where needed.
     *
     * @param wifiInfoList List of WiFi info from VLSS interface
     * @return List of mapped WiFi scan results
     */
    private List<WifiScanResult> mapWifiInfo(List<WifiInfo> wifiInfoList) {
        return wifiInfoList.stream()
                .map(this::mapSingleWifiInfo)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    /**
     * Maps a single WiFi info entry to a WiFi scan result.
     * Always returns a valid result, applying defaults where needed.
     *
     * @param wifiInfo Single WiFi info entry
     * @return Mapped WiFi scan result
     */
    private Optional<WifiScanResult> mapSingleWifiInfo(WifiInfo wifiInfo) {
        // Validate required fields
        if (wifiInfo.getId() == null || wifiInfo.getId().trim().isEmpty()) {
            log.warn("Failed to map WiFi info for MAC {}: WiFi MAC address (id) is required", wifiInfo.getId());
            return Optional.empty();
        }
        if (wifiInfo.getSignalStrength() == null) {
            log.warn("Failed to map WiFi info for MAC {}: Signal strength is required", wifiInfo.getId());
            return Optional.empty();
        }

        // Apply frequency with fallback to default
        Integer frequency = Optional.ofNullable(wifiInfo.getFrequency())
                .orElse(properties.getMapping().getDefaultFrequencyMhz());

        // Create and return WiFi scan result
        WifiScanResult scanResult = new WifiScanResult();
        scanResult.setMacAddress(normalizeMacAddress(wifiInfo.getId()));
        scanResult.setSignalStrength(wifiInfo.getSignalStrength());
        scanResult.setFrequency(frequency);
        scanResult.setSsid(wifiInfo.getSsid());
        return Optional.of(scanResult);
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
