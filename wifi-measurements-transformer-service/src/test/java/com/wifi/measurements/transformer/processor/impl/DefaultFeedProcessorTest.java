// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/processor/impl/DefaultFeedProcessorTest.java
package com.wifi.measurements.transformer.processor.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.MobileHotspotConfiguration;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.OuiDetectionConfiguration;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.PatternType;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.SsidDetectionConfiguration;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.SsidPattern;
import com.wifi.measurements.transformer.dto.FeedUploadEvent;
import com.wifi.measurements.transformer.dto.WifiMeasurement;
import com.wifi.measurements.transformer.dto.WifiScanData;
import com.wifi.measurements.transformer.service.DataDecodingService;
import com.wifi.measurements.transformer.service.MobileHotspotDetectionService;
import com.wifi.measurements.transformer.service.S3FileProcessorService;
import com.wifi.measurements.transformer.service.WiFiMeasurementsPublisher;
import com.wifi.measurements.transformer.service.WifiDataTransformationService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for DefaultFeedProcessor with mobile hotspot detection integration.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Hotspot measurements are filtered when detection is enabled
 *   <li>Legitimate AP measurements are preserved
 *   <li>Mixed scans (hotspots + legitimate APs) are handled correctly
 *   <li>When detection is disabled, all measurements pass through
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DefaultFeedProcessorTest {

  @Mock private S3FileProcessorService s3FileProcessorService;
  @Mock private DataDecodingService dataDecodingService;
  @Mock private WifiDataTransformationService wifiDataTransformationService;
  @Mock private WiFiMeasurementsPublisher measurementsPublisher;
  @Mock private ObjectMapper objectMapper;

  private MobileHotspotDetectionService mobileHotspotDetectionService;
  private DefaultFeedProcessor processor;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  @DisplayName("Should process empty stream when transformation service filters out hotspots")
  void shouldHandleEmptyStreamWhenHotspotsFilteredByTransformationService() throws Exception {
    // Given - Transformation service filters hotspots early (returns empty stream)
    var config = createTestConfig(true, true, false);
    mobileHotspotDetectionService = new MobileHotspotDetectionService(config, meterRegistry);
    processor = createProcessor();

    var feedEvent = createTestFeedEvent();

    // Mock S3 file streaming
    when(s3FileProcessorService.streamFileLines(feedEvent))
        .thenReturn(Stream.of("encoded-line"));

    // Mock decoding
    when(dataDecodingService.decodeAndDecompress("encoded-line"))
        .thenReturn(java.util.Optional.of("decoded-json"));

    // Mock JSON parsing
    WifiScanData mockScanData = new WifiScanData(null, null, null, null, null, null, null, null, null, null, null);
    when(objectMapper.readValue(eq("decoded-json"), eq(WifiScanData.class)))
        .thenReturn(mockScanData);

    // Mock transformation to return empty stream (hotspots already filtered in transformation layer)
    when(wifiDataTransformationService.transformToMeasurements(any(WifiScanData.class), anyString()))
        .thenReturn(Stream.empty());

    // When
    boolean result = processor.process(feedEvent);

    // Then
    assertThat(result).isTrue();
    
    // Verify no measurements published (transformation service already filtered them out)
    verify(measurementsPublisher, never()).publishMeasurement(any(WifiMeasurement.class));
    verify(measurementsPublisher, times(1)).flushCurrentBatch();
  }

  @Test
  @DisplayName("Should publish legitimate AP measurements returned by transformation service")
  void shouldPublishLegitimateApMeasurements() throws Exception {
    // Given - Transformation service returns only legitimate APs (hotspots already filtered)
    var config = createTestConfig(true, true, false);
    mobileHotspotDetectionService = new MobileHotspotDetectionService(config, meterRegistry);
    processor = createProcessor();

    var feedEvent = createTestFeedEvent();

    when(s3FileProcessorService.streamFileLines(feedEvent))
        .thenReturn(Stream.of("encoded-line"));

    when(dataDecodingService.decodeAndDecompress("encoded-line"))
        .thenReturn(java.util.Optional.of("decoded-json"));

    // Mock JSON parsing
    WifiScanData mockScanData = new WifiScanData(null, null, null, null, null, null, null, null, null, null, null);
    when(objectMapper.readValue(eq("decoded-json"), eq(WifiScanData.class)))
        .thenReturn(mockScanData);

    // Mock transformation to return legitimate AP measurement (hotspots already filtered in transformation layer)
    var legitimateAp =
        WifiMeasurement.builder()
            .bssid("11:22:33:44:55:66")
            .ssid("CoffeeShopWiFi") // Legitimate AP SSID
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    when(wifiDataTransformationService.transformToMeasurements(any(WifiScanData.class), anyString()))
        .thenReturn(Stream.of(legitimateAp));

    // When
    boolean result = processor.process(feedEvent);

    // Then
    assertThat(result).isTrue();
    
    // Verify legitimate AP measurement was published
    verify(measurementsPublisher, times(1)).publishMeasurement(legitimateAp);
    verify(measurementsPublisher, times(1)).flushCurrentBatch();
  }

  @Test
  @DisplayName("Should publish multiple legitimate measurements from transformation service")
  void shouldPublishMultipleLegitimateMMeasurements() throws Exception {
    // Given - Transformation service returns only legitimate APs (hotspots already filtered)
    var config = createTestConfig(true, true, false);
    mobileHotspotDetectionService = new MobileHotspotDetectionService(config, meterRegistry);
    processor = createProcessor();

    var feedEvent = createTestFeedEvent();

    when(s3FileProcessorService.streamFileLines(feedEvent))
        .thenReturn(Stream.of("encoded-line"));

    when(dataDecodingService.decodeAndDecompress("encoded-line"))
        .thenReturn(java.util.Optional.of("decoded-json"));

    // Mock JSON parsing
    WifiScanData mockScanData = new WifiScanData(null, null, null, null, null, null, null, null, null, null, null);
    when(objectMapper.readValue(eq("decoded-json"), eq(WifiScanData.class)))
        .thenReturn(mockScanData);

    // Mock transformation to return multiple legitimate AP measurements (hotspots already filtered)
    var legitimateAp1 =
        WifiMeasurement.builder()
            .bssid("11:22:33:44:55:66")
            .ssid("CoffeeShopWiFi") // Legitimate AP
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    var legitimateAp2 =
        WifiMeasurement.builder()
            .bssid("22:33:44:55:66:77")
            .ssid("StarbucksWiFi") // Legitimate AP
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    when(wifiDataTransformationService.transformToMeasurements(any(WifiScanData.class), anyString()))
        .thenReturn(Stream.of(legitimateAp1, legitimateAp2));

    // When
    boolean result = processor.process(feedEvent);

    // Then
    assertThat(result).isTrue();

    // Capture all published measurements
    ArgumentCaptor<WifiMeasurement> measurementCaptor = ArgumentCaptor.forClass(WifiMeasurement.class);
    verify(measurementsPublisher, times(2)).publishMeasurement(measurementCaptor.capture());

    var publishedMeasurements = measurementCaptor.getAllValues();
    
    // Verify both legitimate APs were published
    assertThat(publishedMeasurements)
        .hasSize(2)
        .extracting(WifiMeasurement::ssid)
        .containsExactly("CoffeeShopWiFi", "StarbucksWiFi");
  }

  @Test
  @DisplayName("Should publish all measurements returned by transformation service when detection disabled")
  void shouldPublishAllMeasurementsWhenDetectionDisabled() throws Exception {
    // Given - Transformation service returns all measurements (detection disabled, no filtering)
    var config = createTestConfig(false, true, false);
    mobileHotspotDetectionService = new MobileHotspotDetectionService(config, meterRegistry);
    processor = createProcessor();

    var feedEvent = createTestFeedEvent();

    when(s3FileProcessorService.streamFileLines(feedEvent))
        .thenReturn(Stream.of("encoded-line"));

    when(dataDecodingService.decodeAndDecompress("encoded-line"))
        .thenReturn(java.util.Optional.of("decoded-json"));

    // Mock JSON parsing
    WifiScanData mockScanData = new WifiScanData(null, null, null, null, null, null, null, null, null, null, null);
    when(objectMapper.readValue(eq("decoded-json"), eq(WifiScanData.class)))
        .thenReturn(mockScanData);

    // Mock transformation to return all measurements (no filtering when detection disabled)
    var measurement1 =
        WifiMeasurement.builder()
            .bssid("aa:bb:cc:dd:ee:ff")
            .ssid("John's iPhone")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    var measurement2 =
        WifiMeasurement.builder()
            .bssid("11:22:33:44:55:66")
            .ssid("CoffeeShopWiFi")
            .latitude(37.7749)
            .longitude(-122.4194)
            .build();

    when(wifiDataTransformationService.transformToMeasurements(any(WifiScanData.class), anyString()))
        .thenReturn(Stream.of(measurement1, measurement2));

    // When
    boolean result = processor.process(feedEvent);

    // Then
    assertThat(result).isTrue();
    
    // Verify both measurements were published
    verify(measurementsPublisher, times(2)).publishMeasurement(any(WifiMeasurement.class));
  }

  @Test
  @DisplayName("Should handle empty stream when transformation service filters hotspot by OUI")
  void shouldHandleEmptyStreamWhenHotspotFilteredByOui() throws Exception {
    // Given - Transformation service filters hotspot by OUI detection (returns empty stream)
    var config = createTestConfig(true, false, true);
    mobileHotspotDetectionService = new MobileHotspotDetectionService(config, meterRegistry);
    processor = createProcessor();

    var feedEvent = createTestFeedEvent();

    when(s3FileProcessorService.streamFileLines(feedEvent))
        .thenReturn(Stream.of("encoded-line"));

    when(dataDecodingService.decodeAndDecompress("encoded-line"))
        .thenReturn(java.util.Optional.of("decoded-json"));

    // Mock JSON parsing
    WifiScanData mockScanData = new WifiScanData(null, null, null, null, null, null, null, null, null, null, null);
    when(objectMapper.readValue(eq("decoded-json"), eq(WifiScanData.class)))
        .thenReturn(mockScanData);

    // Mock transformation to return empty stream (hotspot with Apple OUI already filtered in transformation layer)
    when(wifiDataTransformationService.transformToMeasurements(any(WifiScanData.class), anyString()))
        .thenReturn(Stream.empty());

    // When
    boolean result = processor.process(feedEvent);

    // Then
    assertThat(result).isTrue();
    
    // Verify no measurements published (transformation service already filtered by OUI)
    verify(measurementsPublisher, never()).publishMeasurement(any(WifiMeasurement.class));
  }

  // Helper methods

  private DefaultFeedProcessor createProcessor() {
    return new DefaultFeedProcessor(
        s3FileProcessorService,
        dataDecodingService,
        wifiDataTransformationService,
        measurementsPublisher,
        objectMapper);
  }

  private FeedUploadEvent createTestFeedEvent() {
    return new FeedUploadEvent(
        "test-id",
        Instant.now(),
        "us-east-1",
        List.of("arn:aws:s3:::test-bucket"),
        "test-bucket",
        "test-stream/test-key",
        1000L,
        "test-etag",
        "test-version",
        "test-request-id",
        "test-stream");
  }

  private DataFilteringConfigurationProperties createTestConfig(
      boolean enabled,
      boolean ssidEnabled,
      boolean ouiEnabled) {

    // OUI detection config
    OuiDetectionConfiguration ouiConfig =
        new OuiDetectionConfiguration(ouiEnabled, Set.of("00236c", "3c15c2", "5855ca"));

    // SSID detection config (always case-insensitive)
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

