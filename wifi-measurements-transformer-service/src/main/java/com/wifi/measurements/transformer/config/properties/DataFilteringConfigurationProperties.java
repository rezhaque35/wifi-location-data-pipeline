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

  /** Configuration for optional mobile hotspot detection (OUI-based + SSID-based). */
  public record MobileHotspotConfiguration(
      @NotNull(message = "Mobile hotspot enabled flag is required") Boolean enabled,

      /** OUI-based detection configuration */
      @NestedConfigurationProperty @Valid OuiDetectionConfiguration ouiDetection,

      /** SSID-based detection configuration */
      @NestedConfigurationProperty @Valid SsidDetectionConfiguration ssidDetection) {

    // No default constructor - all properties must be explicitly configured
  }

  /** Configuration for OUI-based mobile hotspot detection. */
  public record OuiDetectionConfiguration(
      @NotNull(message = "OUI detection enabled flag is required") Boolean enabled,

      /**
       * Set of OUI prefixes (first 3 octets) for known mobile device manufacturers. Example:
       * "00:23:6C" for Apple devices.
       */
      Set<String> ouiBlacklist) {

    // No default constructor - all properties must be explicitly configured
  }

  /** Configuration for SSID-based mobile hotspot detection. */
  public record SsidDetectionConfiguration(
      @NotNull(message = "SSID detection enabled flag is required") Boolean enabled,

      /** List of SSID patterns to match (always case-insensitive) */
      Set<SsidPattern> patterns) {

    // No default constructor - all properties must be explicitly configured
  }

  /** SSID pattern configuration. */
  public record SsidPattern(
      @NotNull(message = "Pattern is required") String pattern,

      @NotNull(message = "Pattern type is required") PatternType type,

      String description) {

    // No default constructor - all properties must be explicitly configured
  }

  /** Pattern matching type. */
  public enum PatternType {
    CONTAINS, // Simple substring matching
    REGEX // Regular expression matching
  }

  // No default constructor - all properties must be explicitly configured
}
