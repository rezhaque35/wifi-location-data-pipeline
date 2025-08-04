// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/DataFilteringIntegrationTest.java
package com.wifi.measurements.transformer.service;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.dto.LocationData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for data filtering scenarios.
 * 
 * Tests the complete data validation pipeline including metrics collection.
 */
class DataFilteringIntegrationTest {

    private DataValidationService validationService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        // Configure filtering properties for testing
        DataFilteringConfigurationProperties.MobileHotspotConfiguration mobileHotspot = 
            new DataFilteringConfigurationProperties.MobileHotspotConfiguration(
                true, // enabled
                Set.of("00:23:6C", "3C:15:C2", "58:55:CA"), // Apple OUIs for testing
                DataFilteringConfigurationProperties.MobileHotspotAction.EXCLUDE
            );
        
        DataFilteringConfigurationProperties filteringConfig = new DataFilteringConfigurationProperties(
            150.0, // maxLocationAccuracy
            -100,  // minRssi
            0,     // maxRssi
            2.0,   // connectedQualityWeight
            1.0,   // scanQualityWeight
            1.5,   // lowLinkSpeedQualityWeight
            mobileHotspot
        );
        
        validationService = new DataValidationService(filteringConfig, meterRegistry);
    }

    @Test
    void testValidLocationPassesValidation() {
        // Given: Valid location data
        LocationData location = new LocationData(
            "fused", 40.6768816, -74.416391, 110.9, 50.0,
            System.currentTimeMillis(), "fused", 0.0, 0.0
        );
        
        // When: Validate the location
        DataValidationService.ValidationResult result = validationService.validateLocation(location);
        
        // Then: Should pass validation
        assertThat(result.valid()).isTrue();
        assertThat(result.errorMessage()).isNull();
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.location.success").count()).isEqualTo(1.0);
    }

    @Test
    void testInvalidCoordinatesFailValidation() {
        // Given: Invalid location data (latitude > 90)
        LocationData invalidLocation = new LocationData(
            "fused", 91.0, -74.416391, 110.9, 50.0, // Invalid latitude
            System.currentTimeMillis(), "fused", 0.0, 0.0
        );
        
        // When: Validate the location
        DataValidationService.ValidationResult result = validationService.validateLocation(invalidLocation);
        
        // Then: Should fail validation
        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid coordinates");
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.location.invalid").count()).isEqualTo(1.0);
    }

    @Test
    void testPoorGpsAccuracyFailsValidation() {
        // Given: Location with poor GPS accuracy (>150m)
        LocationData poorAccuracyLocation = new LocationData(
            "fused", 40.6768816, -74.416391, 110.9, 200.0, // Poor accuracy
            System.currentTimeMillis(), "fused", 0.0, 0.0
        );
        
        // When: Validate the location
        DataValidationService.ValidationResult result = validationService.validateLocation(poorAccuracyLocation);
        
        // Then: Should fail validation
        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("Location accuracy 200.0m exceeds threshold 150.0m");
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.location.invalid").count()).isEqualTo(1.0);
    }

    @Test
    void testValidRssiPassesValidation() {
        // When: Validate valid RSSI
        DataValidationService.ValidationResult result = validationService.validateRssi(-58);
        
        // Then: Should pass validation
        assertThat(result.valid()).isTrue();
        assertThat(result.errorMessage()).isNull();
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.rssi.success").count()).isEqualTo(1.0);
    }

    @Test
    void testInvalidRssiFailsValidation() {
        // When: Validate invalid RSSI (-101 dBm, below threshold)
        DataValidationService.ValidationResult result = validationService.validateRssi(-101);
        
        // Then: Should fail validation
        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("RSSI -101 dBm outside valid range [-100, 0]");
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.rssi.invalid").count()).isEqualTo(1.0);
    }

    @Test
    void testValidBssidPassesValidation() {
        // When: Validate valid BSSID
        DataValidationService.ValidationResult result = validationService.validateBssid("b8:f8:53:c0:1e:ff");
        
        // Then: Should pass validation
        assertThat(result.valid()).isTrue();
        assertThat(result.errorMessage()).isNull();
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.bssid.success").count()).isEqualTo(1.0);
    }

    @Test
    void testInvalidBssidFailsValidation() {
        // When: Validate invalid BSSID
        DataValidationService.ValidationResult result = validationService.validateBssid("invalid-bssid");
        
        // Then: Should fail validation
        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid BSSID format");
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.bssid.invalid").count()).isEqualTo(1.0);
    }

    @Test
    void testValidTimestampPassesValidation() {
        // When: Validate current timestamp
        DataValidationService.ValidationResult result = validationService.validateTimestamp(System.currentTimeMillis());
        
        // Then: Should pass validation
        assertThat(result.valid()).isTrue();
        assertThat(result.errorMessage()).isNull();
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.timestamp.success").count()).isEqualTo(1.0);
    }

    @Test
    void testFutureTimestampFailsValidation() {
        // When: Validate future timestamp
        DataValidationService.ValidationResult result = validationService.validateTimestamp(System.currentTimeMillis() + 100000);
        
        // Then: Should fail validation
        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Timestamp is in the future");
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.timestamp.invalid").count()).isEqualTo(1.0);
    }

    @Test
    void testMobileHotspotDetection() {
        // When: Check known Apple OUI (mobile hotspot)
        DataValidationService.MobileHotspotResult result = validationService.detectMobileHotspot("00:23:6C:aa:bb:cc");
        
        // Then: Should detect mobile hotspot
        assertThat(result.checked()).isTrue();
        assertThat(result.detected()).isTrue();
        assertThat(result.detectedOui()).isEqualTo("00:23:6C");
        assertThat(result.action()).isEqualTo(DataFilteringConfigurationProperties.MobileHotspotAction.EXCLUDE);
        
        // Verify metrics were recorded
        assertThat(meterRegistry.counter("data.validation.mobile_hotspot.detected").count()).isEqualTo(1.0);
    }

    @Test
    void testNonMobileHotspotNotDetected() {
        // When: Check regular router BSSID (not mobile hotspot)
        DataValidationService.MobileHotspotResult result = validationService.detectMobileHotspot("b8:f8:53:c0:1e:ff");
        
        // Then: Should not detect mobile hotspot
        assertThat(result.checked()).isTrue();
        assertThat(result.detected()).isFalse();
        assertThat(result.detectedOui()).isNull();
        assertThat(result.action()).isNull();
        
        // Verify no mobile hotspot detection metric was recorded
        assertThat(meterRegistry.counter("data.validation.mobile_hotspot.detected").count()).isEqualTo(0.0);
    }

    @Test
    void testMetricsAccumulation() {
        // Given: Multiple validation calls
        validationService.validateLocation(new LocationData("fused", 40.6768816, -74.416391, 110.9, 50.0, System.currentTimeMillis(), "fused", 0.0, 0.0));
        validationService.validateLocation(new LocationData("fused", 91.0, -74.416391, 110.9, 50.0, System.currentTimeMillis(), "fused", 0.0, 0.0)); // Invalid
        validationService.validateRssi(-58);
        validationService.validateRssi(-101); // Invalid
        validationService.validateBssid("b8:f8:53:c0:1e:ff");
        validationService.validateBssid("invalid"); // Invalid
        
        // Then: Verify metrics accumulation
        assertThat(meterRegistry.counter("data.validation.location.success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("data.validation.location.invalid").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("data.validation.rssi.success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("data.validation.rssi.invalid").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("data.validation.bssid.success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("data.validation.bssid.invalid").count()).isEqualTo(1.0);
    }
}