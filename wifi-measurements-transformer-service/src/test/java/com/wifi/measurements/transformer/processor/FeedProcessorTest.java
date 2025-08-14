// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/processor/FeedProcessorTest.java
package com.wifi.measurements.transformer.processor;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wifi.measurements.transformer.dto.S3EventRecord;

/**
 * Unit tests for FeedProcessor implementations.
 *
 * <p>Tests the basic contract and behavior of feed processors including feed type detection,
 * processing capabilities, and priority handling.
 */
class FeedProcessorTest {

  @Test
  @DisplayName("Should identify supported feed type correctly")
  void shouldIdentifySupportedFeedType() {
    // Given
    TestFeedProcessor processor = new TestFeedProcessor("MVS-stream");

    // When & Then
    assertEquals("MVS-stream", processor.getSupportedFeedType());
    assertTrue(processor.canProcess("MVS-stream"));
    assertFalse(processor.canProcess("GPS-data"));
    assertFalse(processor.canProcess("unknown"));
  }

  @Test
  @DisplayName("Should process S3 event successfully")
  void shouldProcessS3EventSuccessfully() {
    // Given
    TestFeedProcessor processor = new TestFeedProcessor("MVS-stream");
    S3EventRecord eventRecord = createTestS3EventRecord("MVS-stream");

    // When
    boolean result = processor.processS3Event(eventRecord);

    // Then
    assertTrue(result);
    assertEquals("MVS-stream", eventRecord.streamName());
  }

  @Test
  @DisplayName("Should handle unsupported feed type gracefully")
  void shouldHandleUnsupportedFeedTypeGracefully() {
    // Given
    TestFeedProcessor processor = new TestFeedProcessor("MVS-stream");
    S3EventRecord eventRecord = createTestS3EventRecord("GPS-data");

    // When
    boolean canProcess = processor.canProcess(eventRecord.streamName());

    // Then
    assertFalse(canProcess);
  }

  @Test
  @DisplayName("Should return default priority")
  void shouldReturnDefaultPriority() {
    // Given
    TestFeedProcessor processor = new TestFeedProcessor("MVS-stream");

    // When & Then
    assertEquals(0, processor.getPriority());
  }

  @Test
  @DisplayName("Should provide processing metrics")
  void shouldProvideProcessingMetrics() {
    // Given
    TestFeedProcessor processor = new TestFeedProcessor("MVS-stream");

    // When
    String metrics = processor.getProcessingMetrics();

    // Then
    assertNotNull(metrics);
    assertFalse(metrics.trim().isEmpty());
  }

  private S3EventRecord createTestS3EventRecord(String streamName) {
    // Use the simplified format where stream name is immediately before the filename
    String objectKey = "2025/07/28/19/" + streamName + "/" + streamName + "-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.txt";

    return S3EventRecord.of(
        "test-id",
        Instant.now(),
        "us-east-1",
        List.of("arn:aws:s3:::test-bucket"),
        "test-bucket",
        objectKey,
        1024L,
        "test-etag",
        "test-version",
        "test-request-id");
  }

  /** Test implementation of FeedProcessor for testing purposes. */
  private static class TestFeedProcessor implements FeedProcessor {
    private final String supportedFeedType;

    public TestFeedProcessor(String supportedFeedType) {
      this.supportedFeedType = supportedFeedType;
    }

    @Override
    public String getSupportedFeedType() {
      return supportedFeedType;
    }

    @Override
    public boolean canProcess(String feedType) {
      return supportedFeedType.equals(feedType);
    }

    @Override
    public boolean processS3Event(S3EventRecord s3EventRecord) {
      // Simple test implementation - just check if we can process the feed type
      return canProcess(s3EventRecord.streamName());
    }

    @Override
    public String getProcessingMetrics() {
      return "Test metrics for " + supportedFeedType;
    }
  }
}
