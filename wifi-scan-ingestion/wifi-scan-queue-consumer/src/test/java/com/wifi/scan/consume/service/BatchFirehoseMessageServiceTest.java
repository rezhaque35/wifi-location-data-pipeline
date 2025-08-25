package com.wifi.scan.consume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.wifi.scan.consume.config.FirehoseBatchConfiguration;

import software.amazon.awssdk.services.firehose.FirehoseClient;

/**
 * Comprehensive unit tests for BatchFirehoseMessageService.
 *
 * <p>Tests the refactored createSubBatches method that accepts configurable parameters for maximum
 * batch size and maximum batch size in bytes. Uses smaller test values for easier testing and
 * validation.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class BatchFirehoseMessageServiceTest {

  private BatchFirehoseMessageService batchFirehoseService;

  @Mock private FirehoseClient firehoseClient;

  @Mock private FirehoseExceptionClassifier exceptionClassifier;

  @Mock private EnhancedRetryStrategy retryStrategy;

  @Mock private FirehoseBatchConfiguration batchConfiguration;

  // Test-friendly constraint values - much smaller for easier testing
  private static final int TEST_MAX_BATCH_SIZE = 5; // Small batch size
  private static final long TEST_MAX_BATCH_SIZE_BYTES = 100; // 100 bytes max
  private static final long TEST_MAX_RECORD_SIZE_BYTES = 50; // 50 bytes per record

  @BeforeEach
  void setUp() {
    // Configure mock batch configuration with test-friendly values
    lenient().when(batchConfiguration.getMaxBatchSize()).thenReturn(TEST_MAX_BATCH_SIZE);
    lenient().when(batchConfiguration.getMaxBatchSizeBytes()).thenReturn(TEST_MAX_BATCH_SIZE_BYTES);
    lenient()
        .when(batchConfiguration.getMaxRecordSizeBytes())
        .thenReturn(TEST_MAX_RECORD_SIZE_BYTES);

    // Create service with mocked dependencies
    batchFirehoseService =
        new BatchFirehoseMessageService(
            firehoseClient, "test-stream", exceptionClassifier, retryStrategy, batchConfiguration);
  }

  @Nested
  @DisplayName("Basic Functionality Tests")
  class BasicFunctionalityTests {

    @Test
    @DisplayName("Should handle empty message list")
    void shouldHandleEmptyMessageList() {
      // Given
      List<String> messages = List.of();

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then
      assertThat(subBatches).isEmpty();
    }

    @Test
    @DisplayName("Should handle null message list")
    void shouldHandleNullMessageList() {
      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              null, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then
      assertThat(subBatches).isEmpty();
    }

    @Test
    @DisplayName("Should create single sub-batch for small message list")
    void shouldCreateSingleSubBatchForSmallMessageList() {
      // Given - 3 small messages (under both limits)
      List<String> messages = List.of("msg1", "msg2", "msg3");

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then
      assertThat(subBatches).hasSize(1);
      assertThat(subBatches.get(0)).hasSize(3);
      assertThat(subBatches.get(0)).containsExactly("msg1", "msg2", "msg3");
    }
  }

  @Nested
  @DisplayName("Record Count Constraint Tests")
  class RecordCountConstraintTests {

    @Test
    @DisplayName("Should split when record count limit is exceeded")
    void shouldSplitWhenRecordCountLimitExceeded() {
      // Given - 7 small messages (exceeds count limit of 5)
      List<String> messages = createTestMessages(7, 5); // 7 messages, 5 bytes each

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then - Should split into 2 sub-batches: 5 + 2
      assertThat(subBatches).hasSize(2);
      assertThat(subBatches.get(0)).hasSize(5);
      assertThat(subBatches.get(1)).hasSize(2);
    }

    @Test
    @DisplayName("Should handle exact record count limit")
    void shouldHandleExactRecordCountLimit() {
      // Given - exactly 5 small messages (at limit)
      List<String> messages = createTestMessages(5, 5);

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then - Should create single sub-batch
      assertThat(subBatches).hasSize(1);
      assertThat(subBatches.get(0)).hasSize(5);
    }
  }

  @Nested
  @DisplayName("Byte Size Constraint Tests")
  class ByteSizeConstraintTests {

    @Test
    @DisplayName("Should split when byte size limit is exceeded")
    void shouldSplitWhenByteSizeLimitExceeded() {
      // Given - 3 messages that together exceed 100 bytes
      List<String> messages = createTestMessages(3, 40); // 3 * 40 = 120 bytes > 100

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then - Should split into multiple sub-batches
      assertThat(subBatches).hasSizeGreaterThan(1);

      // Verify each sub-batch respects byte limit
      for (List<String> subBatch : subBatches) {
        long totalSize = calculateTotalSize(subBatch);
        assertThat(totalSize).isLessThanOrEqualTo(TEST_MAX_BATCH_SIZE_BYTES);
      }
    }

    @Test
    @DisplayName("Should handle exact byte size limit")
    void shouldHandleExactByteSizeLimit() {
      // Given - messages that exactly reach 100 bytes
      List<String> messages = createTestMessages(5, 20); // 5 * 20 = 100 bytes

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then - Should create single sub-batch
      assertThat(subBatches).hasSize(1);
      assertThat(calculateTotalSize(subBatches.get(0))).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("Mixed Constraint Tests")
  class MixedConstraintTests {

    @Test
    @DisplayName("Should handle both constraints simultaneously")
    void shouldHandleBothConstraintsSimultaneously() {
      // Given - large number of messages that hit both limits
      List<String> messages = createTestMessages(10, 15); // 10 messages * 15 bytes

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then - Should split into multiple sub-batches
      assertThat(subBatches).hasSizeGreaterThan(1);

      // Verify all constraints are respected
      for (List<String> subBatch : subBatches) {
        assertThat(subBatch.size()).isLessThanOrEqualTo(TEST_MAX_BATCH_SIZE);
        assertThat(calculateTotalSize(subBatch)).isLessThanOrEqualTo(TEST_MAX_BATCH_SIZE_BYTES);
      }
    }

    @Test
    @DisplayName("Should optimize sub-batch utilization")
    void shouldOptimizeSubBatchUtilization() {
      // Given - mix of different size messages
      List<String> messages = new ArrayList<>();
      messages.addAll(createTestMessages(2, 30)); // 2 * 30 = 60 bytes
      messages.addAll(createTestMessages(3, 10)); // 3 * 10 = 30 bytes
      // Total: 90 bytes in 5 messages (under both limits)

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then - Should fit in single optimized sub-batch
      assertThat(subBatches).hasSize(1);
      assertThat(subBatches.get(0)).hasSize(5);
      assertThat(calculateTotalSize(subBatches.get(0))).isEqualTo(90);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {


    @Test
    @DisplayName("Should handle single large message")
    void shouldHandleSingleLargeMessage() {
      // Given - single message that approaches byte limit
      List<String> messages = createTestMessages(1, 90); // 90 bytes (under 100 limit)

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then
      assertThat(subBatches).hasSize(1);
      assertThat(subBatches.get(0)).hasSize(1);
      assertThat(calculateTotalSize(subBatches.get(0))).isEqualTo(90);
    }

    @Test
    @DisplayName("Should handle alternating message sizes")
    void shouldHandleAlternatingMessageSizes() {
      // Given - alternating large and small messages
      List<String> messages = new ArrayList<>();
      messages.addAll(createTestMessages(2, 45)); // 2 large messages
      messages.addAll(createTestMessages(3, 5)); // 3 small messages

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, TEST_MAX_BATCH_SIZE_BYTES);

      // Then - Should create multiple optimized sub-batches
      assertThat(subBatches).hasSizeGreaterThan(1);

      // Verify constraints
      for (List<String> subBatch : subBatches) {
        assertThat(subBatch.size()).isLessThanOrEqualTo(TEST_MAX_BATCH_SIZE);
        assertThat(calculateTotalSize(subBatch)).isLessThanOrEqualTo(TEST_MAX_BATCH_SIZE_BYTES);
      }
    }
  }

  @Nested
  @DisplayName("Configuration Parameter Tests")
  class ConfigurationParameterTests {

    @Test
    @DisplayName("Should respect custom batch size parameter")
    void shouldRespectCustomBatchSizeParameter() {
      // Given
      List<String> messages = createTestMessages(8, 5);
      int customMaxBatchSize = 3;

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, customMaxBatchSize, TEST_MAX_BATCH_SIZE_BYTES);

      // Then - Should split into 3 sub-batches: 3 + 3 + 2
      assertThat(subBatches).hasSize(3);
      assertThat(subBatches.get(0)).hasSize(3);
      assertThat(subBatches.get(1)).hasSize(3);
      assertThat(subBatches.get(2)).hasSize(2);
    }

    @Test
    @DisplayName("Should respect custom byte size parameter")
    void shouldRespectCustomByteSizeParameter() {
      // Given
      List<String> messages = createTestMessages(4, 20);
      long customMaxBatchSizeBytes = 50; // Smaller than default 100

      // When
      List<List<String>> subBatches =
          batchFirehoseService.createSubBatches(
              messages, TEST_MAX_BATCH_SIZE, customMaxBatchSizeBytes);

      // Then - Should split based on the smaller byte limit
      assertThat(subBatches).hasSizeGreaterThan(1);

      for (List<String> subBatch : subBatches) {
        assertThat(calculateTotalSize(subBatch)).isLessThanOrEqualTo(customMaxBatchSizeBytes);
      }
    }
  }

  /** Helper method to create test messages of specified count and size. */
  private List<String> createTestMessages(int count, int sizePerMessage) {
    return IntStream.range(0, count).mapToObj(i -> createTestMessage(i, sizePerMessage)).toList();
  }

  /** Helper method to create a single test message of specified size. */
  private String createTestMessage(int index, int targetSize) {
    String prefix = "msg_" + index + "_";
    int remainingSize = targetSize - prefix.length();

    if (remainingSize <= 0) {
      return prefix;
    }

    StringBuilder sb = new StringBuilder(prefix);
    String padding = "x";
    for (int i = 0; i < remainingSize; i++) {
      sb.append(padding);
    }

    return sb.toString();
  }

  /** Helper method to calculate total size of messages in bytes. */
  private long calculateTotalSize(List<String> messages) {
    return messages.stream()
        .mapToLong(
            msg -> {
              try {
                return msg.getBytes("UTF-8").length;
              } catch (Exception e) {
                return 0;
              }
            })
        .sum();
  }
}
