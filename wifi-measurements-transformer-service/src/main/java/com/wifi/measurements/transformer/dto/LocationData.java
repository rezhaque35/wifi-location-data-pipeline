package com.wifi.measurements.transformer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents location data from GPS/GNSS. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationData(
    @JsonProperty("source") String source,
    @JsonProperty("latitude") Double latitude,
    @JsonProperty("longitude") Double longitude,
    @JsonProperty("altitude") Double altitude,
    @JsonProperty("accuracy") Double accuracy,
    @JsonProperty("time") Long time,
    @JsonProperty("provider") String provider,
    @JsonProperty("speed") Double speed,
    @JsonProperty("bearing") Double bearing) {
  /** Validates that this location data has required coordinates. */
  public boolean hasValidCoordinates() {
    return latitude != null
        && longitude != null
        && latitude >= -90.0
        && latitude <= 90.0
        && longitude >= -180.0
        && longitude <= 180.0;
  }
}
