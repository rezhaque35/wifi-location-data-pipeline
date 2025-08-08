package com.wifi.measurements.transformer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents a WiFi connected event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WifiConnectedEvent(
    @JsonProperty("timestamp") Long timestamp,
    @JsonProperty("eventId") String eventId,
    @JsonProperty("eventType") String eventType,
    @JsonProperty("did") String did,
    @JsonProperty("isCaptive") Boolean isCaptive,
    @JsonProperty("returnedIP") String returnedIP,
    @JsonProperty("wifiConnectedInfo") WifiConnectedInfo wifiConnectedInfo,
    @JsonProperty("location") LocationData location) {}
