// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/WiFiMeasurementsPublisherTest.java
package com.wifi.measurements.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;

import com.wifi.measurements.transformer.dto.WifiMeasurement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WiFiMeasurementsPublisher.
 */
@ExtendWith(MockitoExtension.class)
class WiFiMeasurementsPublisherTest {

    @Mock
    private Consumer<List<byte[]>> batchConsumer; // Now consumes JSON byte arrays

    @Mock
    private ObjectMapper objectMapper;

    private FirehoseConfigurationProperties firehoseProperties;
    private WiFiMeasurementsPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        firehoseProperties = new FirehoseConfigurationProperties(
            true,   // enabled
            "test-stream",
            3,      // max batch size
            1000L,  // max batch size bytes
            1000L,  // batch timeout ms
            512L,   // max record size bytes
            3,      // max retries
            100L    // retry backoff ms
        );

        // Mock JSON serialization (lenient to avoid unnecessary stubbing exceptions)
        lenient().when(objectMapper.writeValueAsBytes(any(WifiMeasurement.class)))
            .thenReturn("{\"bssid\":\"00:11:22:33:44:55\",\"deviceId\":\"test-device\"}".getBytes());

        publisher = new WiFiMeasurementsPublisher(
            firehoseProperties, batchConsumer, objectMapper);
    }

    @Test
    void shouldAccumulateRecordsUntilBatchSizeLimit() {
        // Given
        WifiMeasurement measurement = createTestMeasurement();

        // When - publish 4 measurements (batch size is 3, so should trigger emission)
        publisher.publishMeasurement(measurement);
        publisher.publishMeasurement(measurement);
        publisher.publishMeasurement(measurement);
        publisher.publishMeasurement(measurement); // This should trigger a new batch

        // Then - verify that batch consumer was called at least once (with timeout for async emission)
        verify(batchConsumer, timeout(2000).atLeastOnce()).accept(any());
        
        // Verify batch status shows remaining records
        WiFiMeasurementsPublisher.BatchStatus status = publisher.getCurrentBatchStatus();
        assertThat(status.recordCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void shouldAccumulateRecordsUntilBatchSizeBytesLimit() throws Exception {
        // Given - configure moderately sized JSON response to trigger byte limit but not record limit
        when(objectMapper.writeValueAsBytes(any(WifiMeasurement.class)))
            .thenReturn(("{\"bssid\":\"00:11:22:33:44:55\",\"data\":\"" + "x".repeat(300) + "\"}").getBytes()); // ~340 byte JSON string

        WifiMeasurement measurement = createTestMeasurement();

        // When - publish measurements that exceed byte limit (1000L bytes configured) 
        // 340 * 3 = 1020 bytes > 1000 bytes limit, should trigger batch emission
        publisher.publishMeasurement(measurement);
        publisher.publishMeasurement(measurement);
        publisher.publishMeasurement(measurement); // This should trigger emission due to size

        // Then - account for asynchronous batch emission with timeout
        // The batch emission uses CompletableFuture.runAsync(), so we need to wait for completion
        verify(batchConsumer, timeout(2000).atLeastOnce()).accept(any());
    }

    @Test
    void shouldEmitBatchOnFlush() {
        // Given - use the default JSON serialization
        WifiMeasurement measurement = createTestMeasurement();
        publisher.publishMeasurement(measurement);

        // When
        CompletableFuture<Void> result = publisher.flushCurrentBatch();

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        verify(batchConsumer).accept(any());
        
        // Verify batch is cleared after flush
        WiFiMeasurementsPublisher.BatchStatus status = publisher.getCurrentBatchStatus();
        assertThat(status.recordCount()).isZero();
        assertThat(status.totalSizeBytes()).isZero();
    }

    @Test
    void shouldHandleSerializationError() throws Exception {
        // Given
        when(objectMapper.writeValueAsBytes(any(WifiMeasurement.class)))
            .thenThrow(new RuntimeException("Serialization failed"));

        WifiMeasurement measurement = createTestMeasurement();

        // When
        publisher.publishMeasurement(measurement);

        // Then - should not call batch consumer due to serialization error
        verify(batchConsumer, never()).accept(any());
        
        // Batch should remain empty
        WiFiMeasurementsPublisher.BatchStatus status = publisher.getCurrentBatchStatus();
        assertThat(status.recordCount()).isZero();
    }

    @Test
    void shouldRejectOversizedRecords() throws Exception {
        // Given - configure JSON response that exceeds record size limit (512L bytes)
        when(objectMapper.writeValueAsBytes(any(WifiMeasurement.class)))
            .thenReturn(("{\"data\":\"" + "x".repeat(600) + "\"}").getBytes()); // 600+ byte JSON string

        WifiMeasurement measurement = createTestMeasurement();

        // When
        publisher.publishMeasurement(measurement);

        // Then - should not add to batch due to size limit
        verify(batchConsumer, never()).accept(any());
        
        WiFiMeasurementsPublisher.BatchStatus status = publisher.getCurrentBatchStatus();
        assertThat(status.recordCount()).isZero();
    }

    @Test
    void shouldCaptureBatchedJsonRecords() {
        // Given
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<byte[]>> batchCaptor = ArgumentCaptor.forClass(List.class);
        WifiMeasurement measurement = createTestMeasurement();

        // When - fill batch to trigger emission
        publisher.publishMeasurement(measurement);
        publisher.publishMeasurement(measurement);
        publisher.publishMeasurement(measurement);

        // Then - use timeout to account for asynchronous batch emission
        verify(batchConsumer, timeout(2000)).accept(batchCaptor.capture());
        List<byte[]> capturedBatch = batchCaptor.getValue();
        
        assertThat(capturedBatch).hasSize(3);
        assertThat(new String(capturedBatch.get(0))).contains("bssid");
        assertThat(new String(capturedBatch.get(0))).contains("deviceId");
    }

    @Test
    void shouldReturnCorrectBatchStatus() {
        // Given
        WifiMeasurement measurement = createTestMeasurement();

        // When
        publisher.publishMeasurement(measurement);
        publisher.publishMeasurement(measurement);

        // Then
        WiFiMeasurementsPublisher.BatchStatus status = publisher.getCurrentBatchStatus();
        assertThat(status.recordCount()).isEqualTo(2);
        assertThat(status.totalSizeBytes()).isGreaterThan(0);
    }

    @Test
    void shouldHandleEmptyFlush() {
        // Given - empty publisher

        // When
        CompletableFuture<Void> result = publisher.flushCurrentBatch();

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        verify(batchConsumer, never()).accept(any());
    }

    private WifiMeasurement createTestMeasurement() {
        return new WifiMeasurement(
            "00:11:22:33:44:55",
            Instant.now().toEpochMilli(),
            "test-event-" + UUID.randomUUID(),
            "test-device",
            "Test Model",
            "Test Manufacturer",
            "1.0.0",
            "1.0.0",
            40.7128,
            -74.0060,
            10.0,
            5.0,
            Instant.now().toEpochMilli(),
            "gps",
            "network",
            0.0,
            0.0,
            "TestNetwork",
            -50,
            2400,
            Instant.now().toEpochMilli(),
            "CONNECTED",
            2.0,
            100, 80, null, null, "WPA2", true, null, null, null, null, 0,
            null, null, null, null, null, null,
            Instant.now(),
            "1.0",
            UUID.randomUUID().toString(),
            0.95
        );
    }
}