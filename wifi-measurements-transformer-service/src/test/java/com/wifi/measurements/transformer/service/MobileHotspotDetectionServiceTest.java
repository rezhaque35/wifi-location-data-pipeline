// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/MobileHotspotDetectionServiceTest.java
package com.wifi.measurements.transformer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.MobileHotspotConfiguration;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.OuiDetectionConfiguration;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.PatternType;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.SsidDetectionConfiguration;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.SsidPattern;
import com.wifi.measurements.transformer.dto.NetworkIdentifier;
import com.wifi.measurements.transformer.dto.WifiMeasurement;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for MobileHotspotDetectionService.
 *
 * <p>Tests OUI-based and SSID-based mobile hotspot detection with various configurations.
 */
class MobileHotspotDetectionServiceTest {

  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  @DisplayName("Should detect iPhone hotspot by SSID pattern")
  void shouldDetectIPhoneHotspotBySsid() {
    // Given
    var config = createTestConfig(true, true, false);
    var service = new MobileHotspotDetectionService(config, meterRegistry);

    var measurement =
        WifiMeasurement.builder()
            .bssid("aa:bb:cc:dd:ee:ff")
            .ssid("John's iPhone")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // When
    boolean isHotspot = service.isMobileHotspot(NetworkIdentifier.from(measurement.bssid(), measurement.ssid()));

    // Then
    assertThat(isHotspot).isTrue();
    assertThat(meterRegistry.counter("hotspot.detection.checked.total").count()).isEqualTo(1.0);
    assertThat(meterRegistry.counter("hotspot.detection.excluded.total").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Should detect Android hotspot by SSID regex pattern")
  void shouldDetectAndroidHotspotBySsid() {
    // Given
    var config = createTestConfig(true, true, false);
    var service = new MobileHotspotDetectionService(config, meterRegistry);

    var measurement =
        WifiMeasurement.builder()
            .bssid("aa:bb:cc:dd:ee:ff")
            .ssid("AndroidAP1234")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // When
    boolean isHotspot = service.isMobileHotspot(NetworkIdentifier.from(measurement.bssid(), measurement.ssid()));

    // Then
    assertThat(isHotspot).isTrue();
  }

  @Test
  @DisplayName("Should detect hotspot by OUI (BSSID)")
  void shouldDetectHotspotByOui() {
    // Given
    var config = createTestConfig(true, false, true);
    var service = new MobileHotspotDetectionService(config, meterRegistry);

    var measurement =
        WifiMeasurement.builder()
            .bssid("00:23:6C:AA:BB:CC") // Apple OUI
            .ssid("StarbucksWiFi")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // When
    boolean isHotspot = service.isMobileHotspot(NetworkIdentifier.from(measurement.bssid(), measurement.ssid()));

    // Then
    assertThat(isHotspot).isTrue();
    assertThat(meterRegistry.counter("hotspot.detection.checked.total").count()).isEqualTo(1.0);
    assertThat(meterRegistry.counter("hotspot.detection.excluded.total").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Should NOT detect legitimate AP as hotspot")
  void shouldNotDetectLegitimateAp() {
    // Given
    var config = createTestConfig(true, true, true);
    var service = new MobileHotspotDetectionService(config, meterRegistry);

    var measurement =
        WifiMeasurement.builder()
            .bssid("11:22:33:44:55:66") // Non-mobile OUI
            .ssid("CoffeeShopWiFi")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // When
    boolean isHotspot = service.isMobileHotspot(NetworkIdentifier.from(measurement.bssid(), measurement.ssid()));

    // Then
    assertThat(isHotspot).isFalse();
  }

  @Test
  @DisplayName("Should detect iPhone hotspot and return true")
  void shouldDetectAndReturnTrue() {
    // Given
    var config = createTestConfig(true, true, false);
    var service = new MobileHotspotDetectionService(config, meterRegistry);

    var measurement =
        WifiMeasurement.builder()
            .bssid("aa:bb:cc:dd:ee:ff")
            .ssid("John's iPhone")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // When
    boolean isHotspot = service.isMobileHotspot(NetworkIdentifier.from(measurement.bssid(), measurement.ssid()));

    // Then - detected as hotspot
    assertThat(isHotspot).isTrue();
  }

  @Test
  @DisplayName("Should be disabled when mobile hotspot detection is disabled")
  void shouldBeDisabledWhenConfigDisabled() {
    // Given
    var config = createTestConfig(false, true, true);
    var service = new MobileHotspotDetectionService(config, meterRegistry);

    var measurement =
        WifiMeasurement.builder()
            .bssid("00:23:6C:AA:BB:CC") // Apple OUI
            .ssid("John's iPhone")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // When
    boolean isHotspot = service.isMobileHotspot(NetworkIdentifier.from(measurement.bssid(), measurement.ssid()));

    // Then
    assertThat(isHotspot).isFalse();
  }

  @Test
  @DisplayName("Should detect case-insensitive SSID patterns")
  void shouldDetectCaseInsensitiveSsidPatterns() {
    // Given
    var config = createTestConfig(true, true, false);
    var service = new MobileHotspotDetectionService(config, meterRegistry);

    var measurement =
        WifiMeasurement.builder()
            .bssid("aa:bb:cc:dd:ee:ff")
            .ssid("sarah's IPHONE") // Mixed case
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // When
    boolean isHotspot = service.isMobileHotspot(NetworkIdentifier.from(measurement.bssid(), measurement.ssid()));

    // Then
    assertThat(isHotspot).isTrue();
  }

  @Test
  @DisplayName("Should handle mixed scan with both hotspot and legitimate AP")
  void shouldHandleMixedScan() {
    // Given
    var config = createTestConfig(true, true, false);
    var service = new MobileHotspotDetectionService(config, meterRegistry);

    // Hotspot measurement
    var hotspotMeasurement =
        WifiMeasurement.builder()
            .bssid("aa:bb:cc:dd:ee:ff")
            .ssid("Mike's Pixel")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // Legitimate AP
    var legitimateAp =
        WifiMeasurement.builder()
            .bssid("11:22:33:44:55:66")
            .ssid("CoffeeShopWiFi")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    // When
    boolean isHotspot1 = service.isMobileHotspot(NetworkIdentifier.from(hotspotMeasurement.bssid(), hotspotMeasurement.ssid()));
    boolean isHotspot2 = service.isMobileHotspot(NetworkIdentifier.from(legitimateAp.bssid(), legitimateAp.ssid()));

    // Then
    assertThat(isHotspot1).isTrue(); // Hotspot should be excluded
    assertThat(isHotspot2).isFalse(); // Legitimate AP should be kept
  }

  // Helper method to create test configuration
  private DataFilteringConfigurationProperties createTestConfig(
      boolean enabled,
      boolean ssidEnabled,
      boolean ouiEnabled) {

    // OUI detection config
    OuiDetectionConfiguration ouiConfig =
        new OuiDetectionConfiguration(ouiEnabled, Set.of("00236c", "3c15c2", "5855ca"));

    // SSID detection config
    Set<SsidPattern> patterns =
        Set.of(
            new SsidPattern("iPhone", PatternType.CONTAINS, "iPhone hotspot"),
            new SsidPattern("iPad", PatternType.CONTAINS, "iPad hotspot"),
            new SsidPattern("^AndroidAP.*", PatternType.REGEX, "Android hotspot"),
            new SsidPattern("Pixel", PatternType.CONTAINS, "Google Pixel hotspot"),
            new SsidPattern("Galaxy", PatternType.CONTAINS, "Samsung Galaxy hotspot"));

    SsidDetectionConfiguration ssidConfig =
        new SsidDetectionConfiguration(ssidEnabled, patterns);

    // Mobile hotspot config
    MobileHotspotConfiguration mobileHotspotConfig =
        new MobileHotspotConfiguration(enabled, ouiConfig, ssidConfig);

    // Full config
    return new DataFilteringConfigurationProperties(
        150.0, // maxLocationAccuracy
        -100, // minRssi
        0, // maxRssi
        2.0, // connectedQualityWeight
        1.0, // scanQualityWeight
        1.5, // lowLinkSpeedQualityWeight
        mobileHotspotConfig);
  }
}

