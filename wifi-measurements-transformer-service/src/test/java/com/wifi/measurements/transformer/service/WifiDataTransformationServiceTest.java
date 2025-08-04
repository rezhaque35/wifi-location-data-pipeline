/**
 * Comprehensive unit tests for the WifiDataTransformationService.
 * 
 * <p>This test class validates the core data transformation logic for WiFi measurements,
 * covering all major transformation scenarios, validation rules, and business logic
 * implemented in the WifiDataTransformationService.</p>
 * 
 * <p><strong>Test Coverage Areas:</strong></p>
 * <ul>
 *   <li><strong>Input Validation:</strong> Null data handling and parameter validation</li>
 *   <li><strong>Connected Events:</strong> WiFi connected event transformation</li>
 *   <li><strong>Scan Results:</strong> WiFi scan result transformation</li>
 *   <li><strong>Data Filtering:</strong> Invalid data exclusion and filtering</li>
 *   <li><strong>Mobile Hotspot Detection:</strong> Mobile hotspot handling</li>
 *   <li><strong>Quality Assessment:</strong> Quality weight and score calculation</li>
 *   <li><strong>Edge Cases:</strong> Missing data, empty results, boundary conditions</li>
 * </ul>
 * 
 * <p><strong>Test Strategy:</strong></p>
 * <ul>
 *   <li>Uses Mockito for dependency mocking and isolation</li>
 *   <li>Comprehensive assertion coverage with AssertJ</li>
 *   <li>Test data builders for consistent test scenarios</li>
 *   <li>Clear test naming convention for easy understanding</li>
 * </ul>
 * 
 * <p><strong>Mock Configuration:</strong></p>
 * <ul>
 *   <li>DataValidationService: Mocked for validation result control</li>
 *   <li>DataFilteringConfigurationProperties: Mocked for configuration testing</li>
 *   <li>MobileHotspotConfiguration: Mocked for hotspot detection testing</li>
 * </ul>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/WifiDataTransformationServiceTest.java
package com.wifi.measurements.transformer.service;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WifiDataTransformationServiceTest {

    @Mock
    private DataValidationService validationService;
    
    @Mock
    private DataFilteringConfigurationProperties filteringConfig;
    
    @Mock
    private DataFilteringConfigurationProperties.MobileHotspotConfiguration mobileHotspot;
    
    private WifiDataTransformationService transformationService;

    /**
     * Sets up test environment before each test method execution.
     * 
     * <p>This method configures the test environment by setting up mock behaviors
     * for all dependencies and creating a fresh instance of the service under test.
     * It ensures consistent test behavior and isolated test execution.</p>
     * 
     * <p><strong>Setup Steps:</strong></p>
     * <ol>
     *   <li><strong>Filtering Configuration:</strong> Set default quality weights and mobile hotspot settings</li>
     *   <li><strong>Validation Service:</strong> Configure default validation success responses</li>
     *   <li><strong>Service Instantiation:</strong> Create fresh service instance with mocked dependencies</li>
     * </ol>
     * 
     * <p><strong>Default Configurations:</strong></p>
     * <ul>
     *   <li>Connected quality weight: 2.0 (higher than scan results)</li>
     *   <li>Scan quality weight: 1.0 (base weight for scan results)</li>
     *   <li>Low link speed quality weight: 1.5 (adjustment for poor performance)</li>
     *   <li>Mobile hotspot detection: Enabled with EXCLUDE action</li>
     *   <li>All validations: Return success by default</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        // Configure default filtering properties for consistent test behavior
        // These represent typical production configuration values
        lenient().when(filteringConfig.connectedQualityWeight()).thenReturn(2.0);
        lenient().when(filteringConfig.scanQualityWeight()).thenReturn(1.0);
        lenient().when(filteringConfig.lowLinkSpeedQualityWeight()).thenReturn(1.5);
        lenient().when(filteringConfig.mobileHotspot()).thenReturn(mobileHotspot);
        lenient().when(mobileHotspot.enabled()).thenReturn(true);
        lenient().when(mobileHotspot.action()).thenReturn(DataFilteringConfigurationProperties.MobileHotspotAction.EXCLUDE);
        
        // Configure validation service to return success by default
        // Individual tests can override these behaviors as needed
        lenient().when(validationService.validateBssid(anyString())).thenReturn(DataValidationService.ValidationResult.success());
        lenient().when(validationService.validateRssi(any())).thenReturn(DataValidationService.ValidationResult.success());
        lenient().when(validationService.validateLocation(any())).thenReturn(DataValidationService.ValidationResult.success());
        lenient().when(validationService.detectMobileHotspot(anyString())).thenReturn(DataValidationService.MobileHotspotResult.notDetected());
        
        // Create fresh service instance with mocked dependencies for each test
        transformationService = new WifiDataTransformationService(validationService, filteringConfig);
    }

    /**
     * Tests that null input data throws IllegalArgumentException.
     * 
     * <p><strong>Test Scenario:</strong></p>
     * <ul>
     *   <li><strong>Input:</strong> null WiFi scan data</li>
     *   <li><strong>Expected:</strong> IllegalArgumentException with specific error message</li>
     *   <li><strong>Purpose:</strong> Validates input parameter validation</li>
     * </ul>
     * 
     * <p><strong>Business Rule:</strong> The service must reject null input data to prevent
     * downstream processing errors and ensure data integrity.</p>
     */
    @Test
    void transformToMeasurements_NullData_ThrowsException() {
        // When & Then: Verify that null input throws appropriate exception
        assertThatThrownBy(() -> transformationService.transformToMeasurements(null, "batch-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("WiFi scan data cannot be null");
    }

    /**
     * Tests successful transformation of valid WiFi connected event data.
     * 
     * <p><strong>Test Scenario:</strong></p>
     * <ul>
     *   <li><strong>Input:</strong> Valid WiFi scan data with connected event</li>
     *   <li><strong>Expected:</strong> Single WiFi measurement with all fields correctly mapped</li>
     *   <li><strong>Purpose:</strong> Validates core transformation logic for connected events</li>
     * </ul>
     * 
     * <p><strong>Validation Points:</strong></p>
     * <ul>
     *   <li>BSSID normalization and mapping</li>
     *   <li>Device information inheritance</li>
     *   <li>Location data mapping</li>
     *   <li>WiFi signal data preservation</li>
     *   <li>Quality weight calculation (higher for connected events)</li>
     *   <li>Connected-specific fields (link speed, channel width, etc.)</li>
     * </ul>
     * 
     * <p><strong>Business Rule:</strong> Connected events should receive higher quality weights
     * than scan results due to their more reliable nature.</p>
     */
    @Test
    void transformToMeasurements_ValidConnectedEvent_ReturnsMeasurement() {
        // Given: Create valid WiFi scan data with connected event
        WifiScanData scanData = createValidWifiScanData();
        String batchId = "batch-123";
        
        // When: Transform the scan data to measurements
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, batchId).toList();
        
        // Then: Verify transformation produces exactly one measurement with correct data
        assertThat(measurements).hasSize(1);
        
        // Validate all measurement fields are correctly mapped from source data
        WifiMeasurement measurement = measurements.get(0);
        assertThat(measurement.bssid()).isEqualTo("b8:f8:53:c0:1e:ff");
        assertThat(measurement.measurementTimestamp()).isEqualTo(1731091615562L);
        assertThat(measurement.eventId()).isEqualTo("9a930a02-f0cc-4e6d-9b95-c18b4d5a542a");
        assertThat(measurement.deviceId()).isNotNull();
        assertThat(measurement.deviceModel()).isEqualTo("SM-A536V");
        assertThat(measurement.deviceManufacturer()).isEqualTo("samsung");
        assertThat(measurement.osVersion()).isEqualTo("14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys");
        assertThat(measurement.appVersion()).isEqualTo("com.verizon.wifiloc.app/0.1.0.10000");
        assertThat(measurement.latitude()).isEqualTo(40.6768816);
        assertThat(measurement.longitude()).isEqualTo(-74.416391);
        assertThat(measurement.altitude()).isEqualTo(110.9);
        assertThat(measurement.locationAccuracy()).isEqualTo(100.0);
        assertThat(measurement.locationTimestamp()).isEqualTo(1731091614415L);
        assertThat(measurement.locationProvider()).isEqualTo("fused");
        assertThat(measurement.locationSource()).isEqualTo("fused");
        assertThat(measurement.ssid()).isEqualTo("Sweethome");
        assertThat(measurement.rssi()).isEqualTo(-58);
        assertThat(measurement.frequency()).isEqualTo(5660);
        assertThat(measurement.connectionStatus()).isEqualTo("CONNECTED");
        assertThat(measurement.qualityWeight()).isEqualTo(2.0);
        assertThat(measurement.linkSpeed()).isEqualTo(351);
        assertThat(measurement.channelWidth()).isEqualTo(2);
        assertThat(measurement.centerFreq0()).isEqualTo(5690);
        assertThat(measurement.centerFreq1()).isEqualTo(0);
        assertThat(measurement.capabilities()).isEqualTo("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]");
        assertThat(measurement.is80211mcResponder()).isFalse();
        assertThat(measurement.isPasspointNetwork()).isFalse();
        assertThat(measurement.isCaptive()).isFalse();
        assertThat(measurement.numScanResults()).isEqualTo(19);
        assertThat(measurement.dataVersion()).isEqualTo("15");
        assertThat(measurement.processingBatchId()).isEqualTo(batchId);
        assertThat(measurement.ingestionTimestamp()).isNotNull();
        assertThat(measurement.qualityScore()).isBetween(0.0, 1.0);
    }

    @Test
    void transformToMeasurements_ValidScanResult_ReturnsMeasurement() {
        // Given
        WifiScanData scanData = createWifiScanDataWithScanResults();
        String batchId = "batch-456";
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, batchId).toList();
        
        // Then
        assertThat(measurements).hasSize(1);
        
        WifiMeasurement measurement = measurements.get(0);
        assertThat(measurement.bssid()).isEqualTo("aa:bb:cc:dd:ee:ff");
        assertThat(measurement.measurementTimestamp()).isEqualTo(1731091616000L);
        assertThat(measurement.eventId()).isNotNull();
        assertThat(measurement.deviceId()).isNotNull();
        assertThat(measurement.deviceModel()).isEqualTo("SM-A536V");
        assertThat(measurement.deviceManufacturer()).isEqualTo("samsung");
        assertThat(measurement.latitude()).isEqualTo(40.6768816);
        assertThat(measurement.longitude()).isEqualTo(-74.416391);
        assertThat(measurement.ssid()).isEqualTo("TestNetwork");
        assertThat(measurement.rssi()).isEqualTo(-65);
        assertThat(measurement.frequency()).isNull(); // Not available in scan results
        assertThat(measurement.connectionStatus()).isEqualTo("SCAN");
        assertThat(measurement.qualityWeight()).isEqualTo(1.0);
        assertThat(measurement.linkSpeed()).isNull(); // Connected-only field
        assertThat(measurement.channelWidth()).isNull(); // Connected-only field
        assertThat(measurement.centerFreq0()).isNull(); // Connected-only field
        assertThat(measurement.centerFreq1()).isNull(); // Connected-only field
        assertThat(measurement.capabilities()).isNull(); // Connected-only field
        assertThat(measurement.is80211mcResponder()).isNull(); // Connected-only field
        assertThat(measurement.isPasspointNetwork()).isNull(); // Connected-only field
        assertThat(measurement.isCaptive()).isNull(); // Connected-only field
        assertThat(measurement.numScanResults()).isNull(); // Connected-only field
        assertThat(measurement.processingBatchId()).isEqualTo(batchId);
    }

    @Test
    void transformToMeasurements_InvalidBssid_ExcludesMeasurement() {
        // Given
        WifiScanData scanData = createValidWifiScanData();
        when(validationService.validateBssid("b8:f8:53:c0:1e:ff"))
            .thenReturn(DataValidationService.ValidationResult.invalid("Invalid BSSID"));
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, "batch-123").toList();
        
        // Then
        assertThat(measurements).isEmpty();
    }

    @Test
    void transformToMeasurements_InvalidRssi_ExcludesMeasurement() {
        // Given
        WifiScanData scanData = createValidWifiScanData();
        when(validationService.validateRssi(-58))
            .thenReturn(DataValidationService.ValidationResult.invalid("Invalid RSSI"));
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, "batch-123").toList();
        
        // Then
        assertThat(measurements).isEmpty();
    }

    @Test
    void transformToMeasurements_InvalidLocation_ExcludesMeasurement() {
        // Given
        WifiScanData scanData = createValidWifiScanData();
        when(validationService.validateLocation(any()))
            .thenReturn(DataValidationService.ValidationResult.invalid("Invalid location"));
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, "batch-123").toList();
        
        // Then
        assertThat(measurements).isEmpty();
    }

    @Test
    void transformToMeasurements_MobileHotspotDetected_ExcludesMeasurement() {
        // Given
        WifiScanData scanData = createValidWifiScanData();
        when(validationService.detectMobileHotspot("b8:f8:53:c0:1e:ff"))
            .thenReturn(DataValidationService.MobileHotspotResult.detected("00:11:22", 
                DataFilteringConfigurationProperties.MobileHotspotAction.EXCLUDE));
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, "batch-123").toList();
        
        // Then
        assertThat(measurements).isEmpty();
    }

    @Test
    void transformToMeasurements_MobileHotspotFlagged_IncludesMeasurement() {
        // Given
        WifiScanData scanData = createValidWifiScanData();
        lenient().when(mobileHotspot.action()).thenReturn(DataFilteringConfigurationProperties.MobileHotspotAction.FLAG);
        lenient().when(validationService.detectMobileHotspot("b8:f8:53:c0:1e:ff"))
            .thenReturn(DataValidationService.MobileHotspotResult.detected("00:11:22", 
                DataFilteringConfigurationProperties.MobileHotspotAction.FLAG));
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, "batch-123").toList();
        
        // Then
        assertThat(measurements).hasSize(1);
    }

    @Test
    void transformToMeasurements_LowLinkSpeed_AdjustsQualityWeight() {
        // Given
        WifiScanData scanData = createWifiScanDataWithLowLinkSpeed();
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, "batch-123").toList();
        
        // Then
        assertThat(measurements).hasSize(1);
        assertThat(measurements.get(0).qualityWeight()).isEqualTo(1.5); // Low link speed weight
    }

    @Test
    void transformToMeasurements_MissingWifiInfo_ExcludesMeasurement() {
        // Given
        WifiScanData scanData = createWifiScanDataWithMissingWifiInfo();
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, "batch-123").toList();
        
        // Then
        assertThat(measurements).isEmpty();
    }

    @Test
    void transformToMeasurements_EmptyScanResults_ReturnsEmptyList() {
        // Given
        WifiScanData scanData = createWifiScanDataWithEmptyScanResults();
        
        // When
        List<WifiMeasurement> measurements = transformationService.transformToMeasurements(scanData, "batch-123").toList();
        
        // Then
        assertThat(measurements).isEmpty();
    }

    // Helper methods to create test data
    private WifiScanData createValidWifiScanData() {
        LocationData location = new LocationData(
            "fused", 40.6768816, -74.416391, 110.9, 100.0, 
            1731091614415L, "fused", 0.0, 0.0
        );
        
        WifiConnectedInfo wifiInfo = new WifiConnectedInfo(
            "b8:f8:53:c0:1e:ff", "Sweethome", 19, 351, 5660, -58,
            "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]", 5690, 0, 2,
            "", "", false, false
        );
        
        WifiConnectedEvent connectedEvent = new WifiConnectedEvent(
            1731091615562L, "9a930a02-f0cc-4e6d-9b95-c18b4d5a542a", "CONNECTED", 
            "device-123", false, "NOT RETRIEVABLE", wifiInfo, location
        );
        
        return new WifiScanData(
            "14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys",
            "SM-A536V", "a53x", "samsung", "Android", "34",
            "com.verizon.wifiloc.app/0.1.0.10000", "15",
            List.of(connectedEvent), List.of(), List.of()
        );
    }

    private WifiScanData createWifiScanDataWithScanResults() {
        LocationData location = new LocationData(
            "fused", 40.6768816, -74.416391, 110.9, 100.0, 
            1731091614415L, "fused", 0.0, 0.0
        );
        
        ScanResultEntry scanEntry = new ScanResultEntry(
            "TestNetwork", "aa:bb:cc:dd:ee:ff", 1731091616000L, -65, null
        );
        
        ScanResult scanResult = new ScanResult(
            1731091616000L, "wifi", location, List.of(scanEntry)
        );
        
        return new WifiScanData(
            "14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys",
            "SM-A536V", "a53x", "samsung", "Android", "34",
            "com.verizon.wifiloc.app/0.1.0.10000", "15",
            List.of(), List.of(), List.of(scanResult)
        );
    }

    private WifiScanData createWifiScanDataWithLowLinkSpeed() {
        LocationData location = new LocationData(
            "fused", 40.6768816, -74.416391, 110.9, 100.0, 
            1731091614415L, "fused", 0.0, 0.0
        );
        
        WifiConnectedInfo wifiInfo = new WifiConnectedInfo(
            "b8:f8:53:c0:1e:ff", "Sweethome", 19, 25, 5660, -58, // Low link speed (25)
            "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]", 5690, 0, 2,
            "", "", false, false
        );
        
        WifiConnectedEvent connectedEvent = new WifiConnectedEvent(
            1731091615562L, "9a930a02-f0cc-4e6d-9b95-c18b4d5a542a", "CONNECTED", 
            "device-123", false, "NOT RETRIEVABLE", wifiInfo, location
        );
        
        return new WifiScanData(
            "14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys",
            "SM-A536V", "a53x", "samsung", "Android", "34",
            "com.verizon.wifiloc.app/0.1.0.10000", "15",
            List.of(connectedEvent), List.of(), List.of()
        );
    }

    private WifiScanData createWifiScanDataWithMissingWifiInfo() {
        LocationData location = new LocationData(
            "fused", 40.6768816, -74.416391, 110.9, 100.0, 
            1731091614415L, "fused", 0.0, 0.0
        );
        
        WifiConnectedEvent connectedEvent = new WifiConnectedEvent(
            1731091615562L, "9a930a02-f0cc-4e6d-9b95-c18b4d5a542a", "CONNECTED", 
            "device-123", false, "NOT RETRIEVABLE", null, location // null wifiInfo
        );
        
        return new WifiScanData(
            "14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys",
            "SM-A536V", "a53x", "samsung", "Android", "34",
            "com.verizon.wifiloc.app/0.1.0.10000", "15",
            List.of(connectedEvent), List.of(), List.of()
        );
    }

    private WifiScanData createWifiScanDataWithEmptyScanResults() {
        return new WifiScanData(
            "14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys",
            "SM-A536V", "a53x", "samsung", "Android", "34",
            "com.verizon.wifiloc.app/0.1.0.10000", "15",
            List.of(), List.of(), List.of()
        );
    }
} 