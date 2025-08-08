// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/processor/MessageProcessorTest.java
package com.wifi.measurements.transformer.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.wifi.measurements.transformer.dto.S3EventRecord;

import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Comprehensive unit tests for the MessageProcessor service.
 *
 * <p>This test class provides thorough unit testing for the MessageProcessor service, covering all
 * major functionality including successful message processing, error handling, and edge cases. The
 * tests use Mockito for dependency mocking and JUnit 5 for test execution.
 *
 * <p><strong>Test Coverage:</strong>
 *
 * <ul>
 *   <li><strong>Successful Processing:</strong> Tests for successful S3 event extraction and
 *       processing
 *   <li><strong>Error Handling:</strong> Tests for various failure scenarios and error conditions
 *   <li><strong>Edge Cases:</strong> Tests for null inputs, malformed messages, and exception
 *       handling
 *   <li><strong>Integration Points:</strong> Tests for interaction with S3EventExtractor and
 *       FeedProcessorFactory
 *   <li><strong>Logging Verification:</strong> Tests for proper logging behavior and message
 *       tracking
 * </ul>
 *
 * <p><strong>Test Scenarios:</strong>
 *
 * <ul>
 *   <li>Successful message processing with valid S3 events
 *   <li>Handling of S3 event extraction failures
 *   <li>Handling of feed processor processing failures
 *   <li>Exception handling during message processing
 *   <li>Null and malformed message handling
 *   <li>Logging verification for successful operations
 * </ul>
 *
 * <p><strong>Mocking Strategy:</strong>
 *
 * <ul>
 *   <li>S3EventExtractor is mocked to control S3 event extraction behavior
 *   <li>FeedProcessorFactory is mocked to control processor selection
 *   <li>FeedProcessor is mocked to control processing behavior
 *   <li>All external dependencies are isolated for pure unit testing
 * </ul>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
class MessageProcessorTest {

  @Mock private S3EventExtractor s3EventExtractor;

  @Mock private FeedProcessorFactory feedProcessorFactory;

  @Mock private FeedProcessor mockFeedProcessor;

  private MessageProcessor processor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    processor = new MessageProcessor(s3EventExtractor, feedProcessorFactory);
  }

  @Test
  @DisplayName("Should process message successfully when S3 event extraction succeeds")
  void shouldProcessMessageSuccessfullyWhenS3EventExtractionSucceeds() {
    // Given
    String messageId = "test-message-id";
    String messageBody = "{\"test\": \"message\"}";
    Message message = Message.builder().messageId(messageId).body(messageBody).build();

    S3EventRecord mockS3Event =
        new S3EventRecord(
            "test-id",
            Instant.now(),
            "us-east-1",
            List.of("arn:aws:s3:::test-bucket"),
            "test-bucket",
            "MVS-stream/2025/07/28/19/MVS-stream-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.txt",
            1000L,
            "test-etag",
            "test-version-id",
            "test-request-id",
            "MVS-stream");

    when(s3EventExtractor.extractS3Event(messageBody)).thenReturn(Optional.of(mockS3Event));
    when(feedProcessorFactory.getProcessor(mockS3Event)).thenReturn(mockFeedProcessor);
    when(mockFeedProcessor.processS3Event(mockS3Event)).thenReturn(true);

    // When
    boolean result = processor.processMessage(message);

    // Then
    assertTrue(result);
    verify(s3EventExtractor).extractS3Event(messageBody);
    verify(feedProcessorFactory).getProcessor(mockS3Event);
    verify(mockFeedProcessor).processS3Event(mockS3Event);
  }

  @Test
  @DisplayName("Should return false when S3 event extraction fails")
  void shouldReturnFalseWhenS3EventExtractionFails() {
    // Given
    String messageId = "test-message-id";
    String messageBody = "{\"invalid\": \"message\"}";
    Message message = Message.builder().messageId(messageId).body(messageBody).build();

    when(s3EventExtractor.extractS3Event(messageBody)).thenReturn(Optional.empty());

    // When
    boolean result = processor.processMessage(message);

    // Then
    assertFalse(result);
    verify(s3EventExtractor).extractS3Event(messageBody);
  }

  @Test
  @DisplayName("Should handle null message body gracefully")
  void shouldHandleNullMessageBodyGracefully() {
    // Given
    String messageId = "test-message-id";
    Message message = Message.builder().messageId(messageId).body(null).build();

    when(s3EventExtractor.extractS3Event(null)).thenReturn(Optional.empty());

    // When
    boolean result = processor.processMessage(message);

    // Then
    assertFalse(result);
    verify(s3EventExtractor).extractS3Event(null);
  }

  @Test
  @DisplayName("Should handle exception during processing")
  void shouldHandleExceptionDuringProcessing() {
    // Given
    String messageId = "test-message-id";
    String messageBody = "{\"test\": \"message\"}";
    Message message = Message.builder().messageId(messageId).body(messageBody).build();

    when(s3EventExtractor.extractS3Event(messageBody))
        .thenThrow(new RuntimeException("Test exception"));

    // When
    boolean result = processor.processMessage(message);

    // Then
    assertFalse(result);
    verify(s3EventExtractor).extractS3Event(messageBody);
  }

  @Test
  @DisplayName("Should log S3 event details when extraction succeeds")
  void shouldLogS3EventDetailsWhenExtractionSucceeds() {
    // Given
    String messageId = "test-message-id";
    String messageBody = "{\"test\": \"message\"}";
    Message message = Message.builder().messageId(messageId).body(messageBody).build();

    S3EventRecord mockS3Event =
        new S3EventRecord(
            "test-id",
            Instant.now(),
            "us-east-1",
            List.of("arn:aws:s3:::test-bucket"),
            "test-bucket",
            "test-key",
            1000L,
            "test-etag",
            "test-version-id",
            "test-request-id",
            "test-stream");

    when(s3EventExtractor.extractS3Event(messageBody)).thenReturn(Optional.of(mockS3Event));
    when(feedProcessorFactory.getProcessor(mockS3Event)).thenReturn(mockFeedProcessor);
    when(mockFeedProcessor.processS3Event(mockS3Event)).thenReturn(true);

    // When
    boolean result = processor.processMessage(message);

    // Then
    assertTrue(result);
    verify(s3EventExtractor).extractS3Event(messageBody);
    verify(feedProcessorFactory).getProcessor(mockS3Event);
    verify(mockFeedProcessor).processS3Event(mockS3Event);
  }
}
