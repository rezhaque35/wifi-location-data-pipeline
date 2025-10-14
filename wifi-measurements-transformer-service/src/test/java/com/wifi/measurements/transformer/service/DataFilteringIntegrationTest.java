// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/DataFilteringIntegrationTest.java
package com.wifi.measurements.transformer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.dto.LocationData;

/**
 * Integration tests for data filtering scenarios.
 *
 * <p>Tests the complete data validation pipeline.
 */
class DataFilteringIntegrationTest {

  private DataValidationService validationService;

  @BeforeEach
  void setUp() {

    // Configure filtering properties for testing
    DataFilteringConfigurationProperties.OuiDetectionConfiguration ouiDetection =
        new DataFilteringConfigurationProperties.OuiDetectionConfiguration(
            true, // enabled
            Set.of("00236c", "3c15c2", "5855ca")); // Apple OUIs for testing (normalized)

    DataFilteringConfigurationProperties.SsidDetectionConfiguration ssidDetection =
        new DataFilteringConfigurationProperties.SsidDetectionConfiguration(
            false, // disabled for this test
            Set.of()); // no patterns

    DataFilteringConfigurationProperties.MobileHotspotConfiguration mobileHotspot =
        new DataFilteringConfigurationProperties.MobileHotspotConfiguration(
            true, // enabled
            ouiDetection,
            ssidDetection);

    DataFilteringConfigurationProperties filteringConfig =
        new DataFilteringConfigurationProperties(
            150.0, // maxLocationAccuracy
            -100, // minRssi
            0, // maxRssi
            2.0, // connectedQualityWeight
            1.0, // scanQualityWeight
            1.5, // lowLinkSpeedQualityWeight
            mobileHotspot);

    validationService = new DataValidationService(filteringConfig);
  }

  @Test
  void testValidLocationPassesValidation() {
    // Given: Valid location data
    LocationData location =
        new LocationData(
            "fused",
            40.6768816,
            -74.416391,
            110.9,
            50.0,
            System.currentTimeMillis(),
            "fused",
            0.0,
            0.0);

    // When: Validate the location
    DataValidationService.ValidationResult result = validationService.validateLocation(location);

    // Then: Should pass validation
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testInvalidCoordinatesFailValidation() {
    // Given: Invalid location data (latitude > 90)
    LocationData invalidLocation =
        new LocationData(
            "fused",
            91.0,
            -74.416391,
            110.9,
            50.0, // Invalid latitude
            System.currentTimeMillis(),
            "fused",
            0.0,
            0.0);

    // When: Validate the location
    DataValidationService.ValidationResult result =
        validationService.validateLocation(invalidLocation);

    // Then: Should fail validation
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("Invalid coordinates");
  }

  @Test
  void testPoorGpsAccuracyFailsValidation() {
    // Given: Location with poor GPS accuracy (>150m)
    LocationData poorAccuracyLocation =
        new LocationData(
            "fused",
            40.6768816,
            -74.416391,
            110.9,
            200.0, // Poor accuracy
            System.currentTimeMillis(),
            "fused",
            0.0,
            0.0);

    // When: Validate the location
    DataValidationService.ValidationResult result =
        validationService.validateLocation(poorAccuracyLocation);

    // Then: Should fail validation
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("Location accuracy 200.0m exceeds threshold 150.0m");
  }

  @Test
  void testValidRssiPassesValidation() {
    // When: Validate valid RSSI
    DataValidationService.ValidationResult result = validationService.validateRssi(-58);

    // Then: Should pass validation
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testInvalidRssiFailsValidation() {
    // When: Validate invalid RSSI (-101 dBm, below threshold)
    DataValidationService.ValidationResult result = validationService.validateRssi(-101);

    // Then: Should fail validation
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("RSSI -101 dBm outside valid range [-100, 0]");
  }

  @Test
  void testValidBssidPassesValidation() {
    // When: Validate valid BSSID
    DataValidationService.ValidationResult result =
        validationService.validateBssid("b8:f8:53:c0:1e:ff");

    // Then: Should pass validation
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testInvalidBssidFailsValidation() {
    // When: Validate invalid BSSID
    DataValidationService.ValidationResult result =
        validationService.validateBssid("invalid-bssid");

    // Then: Should fail validation
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("Invalid BSSID format");
  }

  @Test
  void testValidTimestampPassesValidation() {
    // When: Validate current timestamp
    DataValidationService.ValidationResult result =
        validationService.validateTimestamp(System.currentTimeMillis());

    // Then: Should pass validation
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testFutureTimestampFailsValidation() {
    // When: Validate future timestamp
    DataValidationService.ValidationResult result =
        validationService.validateTimestamp(System.currentTimeMillis() + 100000);

    // Then: Should fail validation
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Timestamp is in the future");
  }


}
