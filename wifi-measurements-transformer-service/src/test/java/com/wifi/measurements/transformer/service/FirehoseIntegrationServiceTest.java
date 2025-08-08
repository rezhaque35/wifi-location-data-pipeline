// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/FirehoseBatchWriterTest.java

package com.wifi.measurements.transformer.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClient;
import software.amazon.awssdk.services.firehose.model.*;

/** Unit tests for FirehoseBatchWriter service. */
@ExtendWith(MockitoExtension.class)
class FirehoseIntegrationServiceTest {

  @Mock private FirehoseAsyncClient firehoseClient;

  @Mock private MeterRegistry meterRegistry;

  @Mock private ScheduledExecutorService retryExecutor;

  @Mock private FirehoseMonitoringService firehoseMonitoringService;

  @Mock private Counter counter;

  private FirehoseConfigurationProperties firehoseProperties;
  private FirehoseIntegrationService writer;

  @BeforeEach
  void setUp() {
    firehoseProperties =
        new FirehoseConfigurationProperties(
            true, "test-stream", 500, 4194304L, 5000L, 1024000L, 3, 1000L);

    // Configure the meter registry to return the mock counter for any counter call
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    lenient()
        .when(meterRegistry.counter(anyString(), anyString(), anyString()))
        .thenReturn(counter);

    writer =
        new FirehoseIntegrationService(
            firehoseClient,
            firehoseProperties,
            meterRegistry,
            retryExecutor,
            firehoseMonitoringService);
  }

  @Test
  void shouldWriteBatchSuccessfully() throws Exception {
    // Given
    List<byte[]> jsonRecords =
        List.of(
            "{\"bssid\":\"00:11:22:33:44:55\",\"deviceId\":\"test-device\"}".getBytes(),
            "{\"bssid\":\"AA:BB:CC:DD:EE:FF\",\"deviceId\":\"test-device-2\"}".getBytes());

    PutRecordBatchResponse response =
        PutRecordBatchResponse.builder()
            .failedPutCount(0)
            .requestResponses(
                PutRecordBatchResponseEntry.builder().build(),
                PutRecordBatchResponseEntry.builder().build())
            .build();

    when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // When
    CompletableFuture<Void> result = writer.writeBatch(jsonRecords);

    // Then
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(firehoseClient).putRecordBatch(any(PutRecordBatchRequest.class));
    verify(meterRegistry).counter("firehose.batch.success");
    verify(counter).increment();
  }

  @Test
  void shouldCreateCorrectFirehoseRecords() throws Exception {
    // Given
    List<byte[]> jsonRecords =
        List.of(
            "{\"bssid\":\"00:11:22:33:44:55\"}".getBytes(),
            "{\"bssid\":\"AA:BB:CC:DD:EE:FF\"}".getBytes());

    PutRecordBatchResponse response =
        PutRecordBatchResponse.builder()
            .failedPutCount(0)
            .requestResponses(
                PutRecordBatchResponseEntry.builder().build(),
                PutRecordBatchResponseEntry.builder().build())
            .build();

    when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // When
    CompletableFuture<Void> result = writer.writeBatch(jsonRecords);

    // Then
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));

    verify(firehoseClient)
        .putRecordBatch(
            argThat(
                (PutRecordBatchRequest request) -> {
                  List<software.amazon.awssdk.services.firehose.model.Record> records =
                      request.records();
                  assertThat(records).hasSize(2);

                  // Verify first record
                  String firstRecordData = records.get(0).data().asUtf8String();
                  assertThat(firstRecordData).isEqualTo("{\"bssid\":\"00:11:22:33:44:55\"}");

                  // Verify second record
                  String secondRecordData = records.get(1).data().asUtf8String();
                  assertThat(secondRecordData).isEqualTo("{\"bssid\":\"AA:BB:CC:DD:EE:FF\"}");

                  return true;
                }));
  }

  @Test
  void shouldHandlePartialFailures() throws Exception {
    // Given
    List<byte[]> jsonRecords =
        List.of(
            "{\"bssid\":\"00:11:22:33:44:55\"}".getBytes(),
            "{\"bssid\":\"AA:BB:CC:DD:EE:FF\"}".getBytes());

    PutRecordBatchResponse response =
        PutRecordBatchResponse.builder()
            .failedPutCount(1)
            .requestResponses(
                PutRecordBatchResponseEntry.builder().build(),
                PutRecordBatchResponseEntry.builder()
                    .errorCode("ServiceUnavailable")
                    .errorMessage("Service temporarily unavailable")
                    .build())
            .build();

    when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // When
    CompletableFuture<Void> result = writer.writeBatch(jsonRecords);

    // Then
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(meterRegistry).counter("firehose.batch.success");
    verify(meterRegistry).counter("firehose.partial.failures");
    verify(counter, times(1)).increment(); // batch success
    verify(counter, times(1)).increment(1); // partial failures count
  }

  @Test
  void shouldRetryOnRetriableErrors() throws Exception {
    // Given
    List<byte[]> jsonRecords = List.of("{\"bssid\":\"00:11:22:33:44:55\"}".getBytes());

    when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                FirehoseException.builder()
                    .statusCode(503)
                    .message("Service Unavailable")
                    .build()));

    // When
    CompletableFuture<Void> result = writer.writeBatch(jsonRecords);

    // Then
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(meterRegistry).counter(eq("firehose.retriable.errors"), eq("type"), anyString());
    verify(retryExecutor).schedule(any(Runnable.class), anyLong(), any());
  }

  @Test
  void shouldNotRetryOnPermanentErrors() throws Exception {
    // Given
    List<byte[]> jsonRecords = List.of("{\"bssid\":\"00:11:22:33:44:55\"}".getBytes());

    when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                InvalidArgumentException.builder()
                    .message("Invalid delivery stream name")
                    .build()));

    // When
    CompletableFuture<Void> result = writer.writeBatch(jsonRecords);

    // Then
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(meterRegistry).counter(eq("firehose.permanent.errors"), eq("type"), anyString());
    verify(retryExecutor, never()).schedule(any(Runnable.class), anyLong(), any());
  }

  @Test
  void shouldHandleEmptyBatch() throws Exception {
    // Given
    List<byte[]> emptyRecords = List.of();

    // When
    CompletableFuture<Void> result = writer.writeBatch(emptyRecords);

    // Then
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(firehoseClient, never()).putRecordBatch(any(PutRecordBatchRequest.class));
  }

  @Test
  void shouldHandleNullBatch() throws Exception {
    // Given
    List<byte[]> nullRecords = null;

    // When
    CompletableFuture<Void> result = writer.writeBatch(nullRecords);

    // Then
    assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(firehoseClient, never()).putRecordBatch(any(PutRecordBatchRequest.class));
  }
}
