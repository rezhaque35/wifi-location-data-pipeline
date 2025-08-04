package com.wifi.measurements.transformer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents individual scan result entry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanResultEntry(
    @JsonProperty("ssid") String ssid,
    @JsonProperty("bssid") String bssid,
    @JsonProperty("scantime") Long scantime,
    @JsonProperty("rssi") Integer rssi,
    @JsonProperty("level") Integer level
) {} 