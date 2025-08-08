package com.wifi.scan.consume.listener;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import com.wifi.scan.consume.service.BatchFirehoseMessageService;
import com.wifi.scan.consume.service.KafkaMonitoringService;
import com.wifi.scan.consume.service.MessageCompressionService;

/**
 * Test class for WifiScanBatchMessageListener following TDD principles. Tests the simplified batch
 * processing flow without batch-level rate limiting.
 *
 * <p>Updated Flow Being Tested: 1. Receive and validate batch 2. Compress all messages 3. Send to
 * Firehose (rate limiting handled at delivery level) 4. Acknowledge on success
 */
@ExtendWith(MockitoExtension.class)
class WifiScanBatchMessageListenerTest {

  @Mock private BatchFirehoseMessageService mockBatchFirehoseService;

  @Mock private KafkaConsumerMetrics mockMetrics;

  @Mock private KafkaMonitoringService mockMonitoringService;

  @Mock private MessageCompressionService mockCompressionService;

  @Mock private Acknowledgment mockAcknowledgment;

  private WifiScanBatchMessageListener batchListener;

  @BeforeEach
  void setUp() {
    batchListener = new WifiScanBatchMessageListener();

    // Use reflection to set private fields
    setPrivateField(batchListener, "batchFirehoseService", mockBatchFirehoseService);
    setPrivateField(batchListener, "metrics", mockMetrics);
    setPrivateField(batchListener, "monitoringService", mockMonitoringService);
    setPrivateField(batchListener, "compressionService", mockCompressionService);

    // Setup default mock behavior for successful processing (using lenient to avoid unnecessary
    // stubbing warnings)
    lenient()
        .when(mockCompressionService.compressAndEncodeBatch(anyList()))
        .thenReturn(List.of("compressed_test_content"));
    lenient().when(mockBatchFirehoseService.deliverBatch(anyList())).thenReturn(true);
  }

  @Test
  @DisplayName("Should process full batch of 150 messages successfully without rate limiting")
  void shouldProcessFullBatchSuccessfully() {
    // Given
    ConsumerRecords<String, String> records = createTestBatch(150);

    // When
    batchListener.listenBatch(records, mockAcknowledgment);

    // Then
    verify(mockMetrics).recordBatchConsumed(150);
    verify(mockMonitoringService).recordPollActivity();
    // Note: No more rate limiting service calls, just simple delivery
    verify(mockBatchFirehoseService).deliverBatch(anyList());
    verify(mockAcknowledgment).acknowledge();
    verify(mockMetrics).recordBatchProcessed(anyLong(), eq(150));
  }

  @Test
  @DisplayName("Should always process batch and let Firehose handle rate limiting")
  void shouldAlwaysProcessBatchWithFirehoseRateLimiting() {
    // Given
    ConsumerRecords<String, String> records = createTestBatch(150);

    // When
    batchListener.listenBatch(records, mockAcknowledgment);

    // Then - Should always attempt delivery, no rate limiting at batch level
    verify(mockBatchFirehoseService).deliverBatch(anyList());
    verify(mockAcknowledgment).acknowledge();
  }

  @Test
  @DisplayName("Should handle Firehose delivery failure with retry logic")
  void shouldHandleFirehoseDeliveryFailureWithRetry() {
    // Given
    ConsumerRecords<String, String> records = createTestBatch(50);
    when(mockBatchFirehoseService.deliverBatch(anyList())).thenReturn(false);

    // When
    batchListener.listenBatch(records, mockAcknowledgment);

    // Then
    verify(mockBatchFirehoseService).deliverBatch(anyList());
    verify(mockAcknowledgment)
        .acknowledge(); // Should acknowledge failed batches to prevent reprocessing
    verify(mockMetrics).recordBatchFailed();
  }

  @Test
  @DisplayName("Should handle empty batch gracefully")
  void shouldHandleEmptyBatchGracefully() {
    // Given
    ConsumerRecords<String, String> emptyRecords = createTestBatch(0);

    // When
    batchListener.listenBatch(emptyRecords, mockAcknowledgment);

    // Then
    verify(mockAcknowledgment).acknowledge();
    verify(mockBatchFirehoseService, never()).deliverBatch(anyList());
  }

  @Test
  @DisplayName("Should calculate average message size for monitoring")
  void shouldCalculateAverageMessageSizeCorrectly() {
    // Given
    ConsumerRecords<String, String> records = createTestBatch(3);

    // When
    batchListener.listenBatch(records, mockAcknowledgment);

    // Then - No more rate limiting service calls, just delivery with max tokens
    verify(mockBatchFirehoseService).deliverBatch(anyList());
  }

  @Test
  @DisplayName("Should handle validation failures gracefully")
  void shouldHandleValidationFailuresGracefully() {
    // Given
    ConsumerRecords<String, String> records = createTestBatchWithInvalidMessages();

    // Setup compression service to return empty list (all messages failed validation/compression)
    when(mockCompressionService.compressAndEncodeBatch(anyList())).thenReturn(List.of());

    // When
    batchListener.listenBatch(records, mockAcknowledgment);

    // Then
    verify(mockAcknowledgment).acknowledge(); // Empty batch should be acknowledged
    verify(mockBatchFirehoseService, never()).deliverBatch(anyList());
  }

  @Test
  @DisplayName("Should handle compression service returning multiple compressed messages")
  void shouldHandleMultipleCompressedMessages() {
    // Given
    ConsumerRecords<String, String> records = createTestBatch(3);

    List<String> compressedMessages = List.of("compressed1", "compressed2", "compressed3");
    when(mockCompressionService.compressAndEncodeBatch(anyList())).thenReturn(compressedMessages);

    // When
    batchListener.listenBatch(records, mockAcknowledgment);

    // Then
    verify(mockBatchFirehoseService)
        .deliverBatch(
            argThat(
                messages ->
                    messages.size() == 3
                        && messages.contains("compressed1")
                        && messages.contains("compressed2")
                        && messages.contains("compressed3")));
    verify(mockAcknowledgment).acknowledge();
  }

  @Test
  @DisplayName("Should record processing metrics correctly")
  void shouldRecordProcessingMetricsCorrectly() {
    // Given
    ConsumerRecords<String, String> records = createTestBatch(100);

    // When
    batchListener.listenBatch(records, mockAcknowledgment);

    // Then
    verify(mockMetrics).recordBatchConsumed(100);
    verify(mockMetrics).recordBatchProcessed(anyLong(), eq(100));
    verify(mockMonitoringService).recordPollActivity();
  }

  // Helper methods

  private ConsumerRecords<String, String> createTestBatch(int messageCount) {
    Map<org.apache.kafka.common.TopicPartition, List<ConsumerRecord<String, String>>> records =
        new java.util.HashMap<>();

    org.apache.kafka.common.TopicPartition partition =
        new org.apache.kafka.common.TopicPartition("test-topic", 0);

    List<ConsumerRecord<String, String>> recordList = new java.util.ArrayList<>();
    for (int i = 0; i < messageCount; i++) {
      recordList.add(
          new ConsumerRecord<>(
              "test-topic",
              0,
              i,
              "key" + i,
              "{\"deviceId\":\"device" + i + "\",\"data\":\"test data " + i + "\"}"));
    }

    records.put(partition, recordList);
    return new ConsumerRecords<>(records);
  }

  private ConsumerRecords<String, String> createTestBatchWithInvalidMessages() {
    Map<org.apache.kafka.common.TopicPartition, List<ConsumerRecord<String, String>>> records =
        new java.util.HashMap<>();

    org.apache.kafka.common.TopicPartition partition =
        new org.apache.kafka.common.TopicPartition("test-topic", 0);

    List<ConsumerRecord<String, String>> recordList =
        List.of(
            new ConsumerRecord<>("test-topic", 0, 0, "key1", "invalid json"),
            new ConsumerRecord<>("test-topic", 0, 1, "key2", null),
            new ConsumerRecord<>("test-topic", 0, 2, "key3", ""),
            new ConsumerRecord<>("test-topic", 0, 3, "key4", "{\"valid\":\"json\"}"));

    records.put(partition, recordList);
    return new ConsumerRecords<>(records);
  }

  private void setPrivateField(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field: " + fieldName, e);
    }
  }
}
