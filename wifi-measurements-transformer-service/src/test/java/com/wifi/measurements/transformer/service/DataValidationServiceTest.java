// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/DataValidationServiceTest.java
package com.wifi.measurements.transformer.service;

import static com.wifi.measurements.transformer.service.DataValidationService.ONE_YEAR_MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.dto.LocationData;

@ExtendWith(MockitoExtension.class)
class DataValidationServiceTest {

  @Mock private DataFilteringConfigurationProperties filteringConfig;

  private DataValidationService validationService;

  @BeforeEach
  void setUp() {
    // Configure default filtering properties
    lenient().when(filteringConfig.maxLocationAccuracy()).thenReturn(150.0);
    lenient().when(filteringConfig.minRssi()).thenReturn(-100);
    lenient().when(filteringConfig.maxRssi()).thenReturn(0);

    validationService = new DataValidationService(filteringConfig);
  }

  @Test
  void validateLocation_ValidLocation_ReturnsSuccess() {
    // Given
    LocationData location =
        new LocationData(
            "fused",
            40.6768816,
            -74.416391,
            110.9,
            100.0,
            System.currentTimeMillis(),
            "fused",
            0.0,
            0.0);

    // When
    DataValidationService.ValidationResult result = validationService.validateLocation(location);

    // Then
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void validateLocation_NullLocation_ReturnsInvalid() {
    // When
    DataValidationService.ValidationResult result = validationService.validateLocation(null);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Location data is null");
  }

  @Test
  void validateLocation_InvalidCoordinates_ReturnsInvalid() {
    // Given
    LocationData location =
        new LocationData(
            "fused", 91.0, -74.416391, 110.9, 100.0, System.currentTimeMillis(), "fused", 0.0, 0.0);

    // When
    DataValidationService.ValidationResult result = validationService.validateLocation(location);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("Invalid coordinates");
  }

  @Test
  void validateLocation_AccuracyExceedsThreshold_ReturnsInvalid() {
    // Given
    LocationData location =
        new LocationData(
            "fused",
            40.6768816,
            -74.416391,
            110.9,
            200.0,
            System.currentTimeMillis(),
            "fused",
            0.0,
            0.0);

    // When
    DataValidationService.ValidationResult result = validationService.validateLocation(location);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("Location accuracy 200.0m exceeds threshold 150.0m");
  }

  @Test
  void validateRssi_ValidRssi_ReturnsSuccess() {
    // When
    DataValidationService.ValidationResult result = validationService.validateRssi(-58);

    // Then
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void validateRssi_NullRssi_ReturnsInvalid() {
    // When
    DataValidationService.ValidationResult result = validationService.validateRssi(null);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("RSSI is null");
  }

  @Test
  void validateRssi_TooLowRssi_ReturnsInvalid() {
    // When
    DataValidationService.ValidationResult result = validationService.validateRssi(-101);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("RSSI -101 dBm outside valid range [-100, 0]");
  }

  @Test
  void validateBssid_ValidBssid_ReturnsSuccess() {
    // When
    DataValidationService.ValidationResult result =
        validationService.validateBssid("b8:f8:53:c0:1e:ff");

    // Then
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void validateBssid_ValidBssidWithHyphens_ReturnsSuccess() {
    // When
    DataValidationService.ValidationResult result =
        validationService.validateBssid("b8-f8-53-c0-1e-ff");

    // Then
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void validateBssid_NullBssid_ReturnsInvalid() {
    // When
    DataValidationService.ValidationResult result = validationService.validateBssid(null);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("BSSID is null or empty");
  }

  @Test
  void validateBssid_InvalidFormat_ReturnsInvalid() {
    // When
    DataValidationService.ValidationResult result =
        validationService.validateBssid("invalid-bssid");

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("Invalid BSSID format");
  }

  @Test
  void validateBssid_BroadcastAddress_ReturnsInvalid() {
    // When
    DataValidationService.ValidationResult result =
        validationService.validateBssid("ff:ff:ff:ff:ff:ff");

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).contains("Invalid BSSID format");
  }

  @Test
  void validateTimestamp_ValidTimestamp_ReturnsSuccess() {
    // When
    DataValidationService.ValidationResult result =
        validationService.validateTimestamp(System.currentTimeMillis());

    // Then
    assertThat(result.valid()).isTrue();
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void validateTimestamp_NullTimestamp_ReturnsInvalid() {
    // When
    DataValidationService.ValidationResult result = validationService.validateTimestamp(null);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Timestamp is null");
  }

  @Test
  void validateTimestamp_FutureTimestamp_ReturnsInvalid() {
    // When
    DataValidationService.ValidationResult result =
        validationService.validateTimestamp(System.currentTimeMillis() + 100000);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Timestamp is in the future");
  }

  @Test
  void validateTimestamp_TooOldTimestamp_ReturnsInvalid() {
    // Given
    long oneYearAndAMillisecondAgo = System.currentTimeMillis() - (ONE_YEAR_MILLIS + 1);

    // When
    DataValidationService.ValidationResult result =
        validationService.validateTimestamp(oneYearAndAMillisecondAgo);

    // Then
    assertThat(result.valid()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("Timestamp is more than a year old");
  }
}
