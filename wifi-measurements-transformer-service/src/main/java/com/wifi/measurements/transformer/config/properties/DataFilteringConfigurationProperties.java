package com.wifi.measurements.transformer.config.properties;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for data filtering and quality assessment.
 *
 * <p>Configures Stage 1 sanity checks, quality weighting, and optional mobile hotspot detection
 * using OUI-based MAC address filtering.
 */
@ConfigurationProperties(prefix = "filtering")
@Validated
public record DataFilteringConfigurationProperties(

    // Stage 1: Sanity Checks
    @DecimalMin(value = "1.0", message = "Max location accuracy must be at least 1 meter")
        @DecimalMax(value = "1000.0", message = "Max location accuracy cannot exceed 1000 meters")
        @NotNull(message = "Max location accuracy is required")
        Double maxLocationAccuracy,
    @Min(value = -100, message = "Min RSSI must be at least -100 dBm")
        @Max(value = -10, message = "Min RSSI cannot exceed -10 dBm")
        @NotNull(message = "Min RSSI is required")
        Integer minRssi,
    @Min(value = -10, message = "Max RSSI must be at least -10 dBm")
        @Max(value = 0, message = "Max RSSI cannot exceed 0 dBm")
        @NotNull(message = "Max RSSI is required")
        Integer maxRssi,

    // Quality Weighting
    @DecimalMin(value = "0.1", message = "Connected quality weight must be at least 0.1")
        @DecimalMax(value = "10.0", message = "Connected quality weight cannot exceed 10.0")
        @NotNull(message = "Connected quality weight is required")
        Double connectedQualityWeight,
    @DecimalMin(value = "0.1", message = "Scan quality weight must be at least 0.1")
        @DecimalMax(value = "10.0", message = "Scan quality weight cannot exceed 10.0")
        @NotNull(message = "Scan quality weight is required")
        Double scanQualityWeight,
    @DecimalMin(value = "0.1", message = "Low link speed quality weight must be at least 0.1")
        @DecimalMax(value = "10.0", message = "Low link speed quality weight cannot exceed 10.0")
        @NotNull(message = "Low link speed quality weight is required")
        Double lowLinkSpeedQualityWeight,

    // Optional: Mobile Hotspot Detection
    @NestedConfigurationProperty @Valid MobileHotspotConfiguration mobileHotspot) {

  /** Configuration for optional OUI-based mobile hotspot detection. */
  public record MobileHotspotConfiguration(
      @NotNull(message = "Mobile hotspot enabled flag is required") Boolean enabled,

      /**
       * Set of OUI prefixes (first 3 octets) for known mobile device manufacturers. Example:
       * "00:23:6C" for Apple devices.
       */
      Set<String> ouiBlacklist,

      /** Action to take when mobile hotspot is detected. Options: FLAG, EXCLUDE, LOG_ONLY */
      @NotNull(message = "Mobile hotspot action is required") MobileHotspotAction action) {

    // No default constructor - all properties must be explicitly configured
  }

  /** Actions that can be taken when a mobile hotspot is detected. */
  public enum MobileHotspotAction {
    FLAG, // Flag the record but include it
    EXCLUDE, // Exclude the record completely
    LOG_ONLY // Only log the detection, no other action
  }

  // No default constructor - all properties must be explicitly configured
}
