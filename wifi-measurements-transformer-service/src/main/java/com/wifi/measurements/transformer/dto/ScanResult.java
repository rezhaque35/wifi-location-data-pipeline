package com.wifi.measurements.transformer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents scan results data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanResult(
    @JsonProperty("timestamp") Long timestamp,
    @JsonProperty("mode") String mode,
    @JsonProperty("location") LocationData location,
    @JsonProperty("results") List<ScanResultEntry> results
) {} 