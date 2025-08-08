package com.wifi.measurements.transformer.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents scan results data. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanResult(
    @JsonProperty("timestamp") Long timestamp,
    @JsonProperty("mode") String mode,
    @JsonProperty("location") LocationData location,
    @JsonProperty("results") List<ScanResultEntry> results) {}
