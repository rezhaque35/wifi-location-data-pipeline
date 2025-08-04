package com.wifi.measurements.transformer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents WiFi connection information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WifiConnectedInfo(
    @JsonProperty("bssid") String bssid,
    @JsonProperty("ssid") String ssid,
    @JsonProperty("numOfScanResults") Integer numOfScanResults,
    @JsonProperty("linkSpeed") Integer linkSpeed,
    @JsonProperty("frequency") Integer frequency,
    @JsonProperty("rssi") Integer rssi,
    @JsonProperty("capabilities") String capabilities,
    @JsonProperty("centerFreq0") Integer centerFreq0,
    @JsonProperty("centerFreq1") Integer centerFreq1,
    @JsonProperty("channelWidth") Integer channelWidth,
    @JsonProperty("operatorFriendlyName") String operatorFriendlyName,
    @JsonProperty("venueName") String venueName,
    @JsonProperty("is80211mcResponder") Boolean is80211mcResponder,
    @JsonProperty("isPasspointNetwork") Boolean isPasspointNetwork
) {} 