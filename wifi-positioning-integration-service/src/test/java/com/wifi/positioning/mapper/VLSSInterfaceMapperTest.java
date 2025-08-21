// com/wifi/positioning/mapper/SampleInterfaceMapperTest.java
package com.wifi.positioning.mapper;

import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for VLSSInterfaceMapper.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VLSSInterfaceMapperTest {

    @Mock
    private IntegrationProperties properties;

    @Mock
    private IntegrationProperties.Mapping mappingConfig;

    private VLSSInterfaceMapper mapper;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getMapping()).thenReturn(mappingConfig);
        lenient().when(mappingConfig.isDropMissingFrequency()).thenReturn(true);
        lenient().when(mappingConfig.getDefaultFrequencyMhz()).thenReturn(2412);

        mapper = new VLSSInterfaceMapper(properties);
    }

    @Test
    void mapToPositioningRequest_ValidInput() {
        // Given
        SampleInterfaceSourceRequest sourceRequest = createValidSourceRequest();
        IntegrationOptions options = new IntegrationOptions();
        options.setCalculationDetail(true);

        // When
        WifiPositioningRequest result = mapper.mapToPositioningRequest(sourceRequest, options);

        // Then
        assertNotNull(result);
        assertEquals("test-client", result.getClient());
        assertEquals("test-request-123", result.getRequestId());
        assertEquals("GIZMO", result.getApplication());
        assertTrue(result.getCalculationDetail());
        assertEquals(2, result.getWifiScanResults().size());

        WifiScanResult scanResult = result.getWifiScanResults().get(0);
        assertEquals("00:11:22:33:44:55", scanResult.getMacAddress());
        assertEquals(-65.0, scanResult.getSignalStrength());
        assertEquals(2437, scanResult.getFrequency());
    }

    @Test
    void mapToPositioningRequest_NullOptions() {
        // Given
        SampleInterfaceSourceRequest sourceRequest = createValidSourceRequest();

        // When
        WifiPositioningRequest result = mapper.mapToPositioningRequest(sourceRequest, null);

        // Then
        assertNotNull(result);
        assertTrue(result.getCalculationDetail()); // Always true in VLSSInterfaceMapper
    }

    @Test
    void mapToPositioningRequest_MissingFrequencyWithDefault() {
        // Given
        SampleInterfaceSourceRequest sourceRequest = createSourceRequestWithMissingFrequency();
        lenient().when(mappingConfig.getDefaultFrequencyMhz()).thenReturn(2412);

        // When
        WifiPositioningRequest result = mapper.mapToPositioningRequest(sourceRequest, null);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getWifiScanResults().size()); // Both should be kept

        // Find the one that had missing frequency
        WifiScanResult withDefault = result.getWifiScanResults().stream()
                .filter(r -> r.getMacAddress().equals("AA:BB:CC:DD:EE:FF"))
                .findFirst()
                .orElse(null);
        assertNotNull(withDefault);
        assertEquals(2412, withDefault.getFrequency()); // Should have default frequency
    }



    @Test
    void mapToPositioningRequest_NoValidWifiInfo() {
        // Given
        SampleInterfaceSourceRequest sourceRequest = createSourceRequestWithInvalidWifi();

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.mapToPositioningRequest(sourceRequest, null)
        );
        assertTrue(exception.getMessage().contains("No valid WiFi scan results"));
    }

    @Test
    void macAddressNormalization() {
        // Given
        WifiInfo wifiInfo1 = new WifiInfo();
        wifiInfo1.setId("00-11-22-33-44-55"); // Dash separators
        wifiInfo1.setSignalStrength(-65.0);
        wifiInfo1.setFrequency(2437);

        WifiInfo wifiInfo2 = new WifiInfo();
        wifiInfo2.setId("aabbccddeeff"); // No separators, lowercase
        wifiInfo2.setSignalStrength(-70.0);
        wifiInfo2.setFrequency(5180);

        SampleInterfaceSourceRequest sourceRequest = createSourceRequestWithWifiInfo(Arrays.asList(wifiInfo1, wifiInfo2));

        // When
        WifiPositioningRequest result = mapper.mapToPositioningRequest(sourceRequest, null);

        // Then
        assertEquals(2, result.getWifiScanResults().size());
        assertEquals("00:11:22:33:44:55", result.getWifiScanResults().get(0).getMacAddress());
        assertEquals("AA:BB:CC:DD:EE:FF", result.getWifiScanResults().get(1).getMacAddress());
    }

    private SampleInterfaceSourceRequest createValidSourceRequest() {
        WifiInfo wifiInfo1 = new WifiInfo();
        wifiInfo1.setId("00:11:22:33:44:55");
        wifiInfo1.setSignalStrength(-65.0);
        wifiInfo1.setFrequency(2437);

        WifiInfo wifiInfo2 = new WifiInfo();
        wifiInfo2.setId("aa:bb:cc:dd:ee:ff");
        wifiInfo2.setSignalStrength(-70.0);
        wifiInfo2.setFrequency(5180);

        return createSourceRequestWithWifiInfo(Arrays.asList(wifiInfo1, wifiInfo2));
    }

    private SampleInterfaceSourceRequest createSourceRequestWithMissingFrequency() {
        WifiInfo wifiInfo1 = new WifiInfo();
        wifiInfo1.setId("00:11:22:33:44:55");
        wifiInfo1.setSignalStrength(-65.0);
        wifiInfo1.setFrequency(2437); // Has frequency

        WifiInfo wifiInfo2 = new WifiInfo();
        wifiInfo2.setId("aa:bb:cc:dd:ee:ff");
        wifiInfo2.setSignalStrength(-70.0);
        // Missing frequency

        return createSourceRequestWithWifiInfo(Arrays.asList(wifiInfo1, wifiInfo2));
    }

    private SampleInterfaceSourceRequest createSourceRequestWithInvalidWifi() {
        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setId(""); // Invalid: empty MAC
        wifiInfo.setSignalStrength(-65.0);
        wifiInfo.setFrequency(2437);

        return createSourceRequestWithWifiInfo(List.of(wifiInfo));
    }

    private SampleInterfaceSourceRequest createSourceRequestWithWifiInfo(List<WifiInfo> wifiInfoList) {
        SvcReq svcReq = new SvcReq();
        svcReq.setClientId("test-client");
        svcReq.setRequestId("test-request-123");
        svcReq.setWifiInfo(wifiInfoList);

        SvcBody svcBody = new SvcBody();
        svcBody.setSvcReq(svcReq);

        SampleInterfaceSourceRequest sourceRequest = new SampleInterfaceSourceRequest();
        sourceRequest.setSvcBody(svcBody);

        return sourceRequest;
    }
}
