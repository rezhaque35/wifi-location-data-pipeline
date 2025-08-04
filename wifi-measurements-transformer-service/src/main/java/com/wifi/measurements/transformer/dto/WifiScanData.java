// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/dto/WifiScanData.java
package com.wifi.measurements.transformer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Root DTO representing the complete WiFi scan data structure from JSON.
 * 
 * This matches the structure of the sample WiFi scan JSON file and provides
 * a typed interface for processing the raw data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WifiScanData(
    @JsonProperty("osVersion") String osVersion,
    @JsonProperty("model") String model,
    @JsonProperty("device") String device,
    @JsonProperty("manufacturer") String manufacturer,
    @JsonProperty("osName") String osName,
    @JsonProperty("sdkInt") String sdkInt,
    @JsonProperty("appNameVersion") String appNameVersion,
    @JsonProperty("dataVersion") String dataVersion,
    @JsonProperty("wifiConnectedEvents") List<WifiConnectedEvent> wifiConnectedEvents,
    @JsonProperty("wifiDisconnectedEvents") List<WifiDisconnectedEvent> wifiDisconnectedEvents,
    @JsonProperty("scanResults") List<ScanResult> scanResults
) {
    /**
     * Gets the device ID from the data, preferring explicit deviceId field or using SHA256 hash.
     * Returns null if no device identifier is available.
     */
    public String getDeviceId() {
        // For now, return null as device ID hashing will be handled in transformation
        return null;
    }
}

/**
 * Represents a WiFi disconnected event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WifiDisconnectedEvent(
    @JsonProperty("timestamp") Long timestamp,
    @JsonProperty("eventId") String eventId,
    @JsonProperty("eventType") String eventType,
    @JsonProperty("did") String did,
    @JsonProperty("mode") String mode,
    @JsonProperty("wifiDisconnectedInfo") WifiDisconnectedInfo wifiDisconnectedInfo,
    @JsonProperty("location") LocationData location
) {}

/**
 * Represents WiFi disconnection information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WifiDisconnectedInfo(
    @JsonProperty("bssid") String bssid,
    @JsonProperty("ssid") String ssid,
    @JsonProperty("sessionWifiTx") Long sessionWifiTx,
    @JsonProperty("sessionWifiRx") Long sessionWifiRx,
    @JsonProperty("totalSessionTime") Long totalSessionTime
) {} 