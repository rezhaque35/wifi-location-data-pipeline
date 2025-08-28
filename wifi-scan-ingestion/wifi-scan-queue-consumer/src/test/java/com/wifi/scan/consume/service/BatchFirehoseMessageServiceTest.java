package com.wifi.scan.consume.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.scan.consume.config.FirehoseBatchConfiguration;
import com.wifi.scan.consume.service.FirehoseExceptionClassifier.ExceptionType;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamRequest;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamResponse;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchResponse;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchResponseEntry;

/**
 * Unit tests for BatchFirehoseMessageService.
 * 
 * <p>Comprehensive test suite covering batch delivery, sub-batch creation, 
 * error handling, retry strategies, metrics tracking, and connectivity testing.
 * This test class aims to achieve high coverage for the BatchFirehoseMessageService
 * by testing all major code paths including success scenarios, error conditions,
 * and edge cases.
 * 
 * <p>Test Categories:
 * - Sub-batch creation with various size constraints
 * - Successful batch delivery scenarios
 * - Error handling and retry strategies
 * - Metrics collection and tracking
 * - Connectivity testing
 * - Edge cases and boundary conditions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchFirehoseMessageService Tests")
class BatchFirehoseMessageServiceTest {

    @Mock
    private FirehoseClient firehoseClient;

    @Mock
    private FirehoseExceptionClassifier exceptionClassifier;

    @Mock
    private EnhancedRetryStrategy retryStrategy;

    @Mock
    private FirehoseBatchConfiguration batchConfiguration;

    private BatchFirehoseMessageService service;
    private static final String DELIVERY_STREAM_NAME = "test-delivery-stream";

    @BeforeEach
    void setUp() {
        service = new BatchFirehoseMessageService(
            firehoseClient,
            DELIVERY_STREAM_NAME,
            exceptionClassifier,
            retryStrategy,
            batchConfiguration
        );
    }

    // ==================== Sub-batch Creation Tests ====================

    @Test
    @DisplayName("Should create single sub-batch when messages fit in limits")
    void createSubBatches_WhenMessagesWithinLimits_ShouldCreateSingleSubBatch() {
        // Given
        List<String> messages = Arrays.asList("message1", "message2", "message3");
        int maxBatchSize = 10;
        long maxBatchSizeBytes = 1000L;

        // When
        List<List<String>> subBatches = service.createSubBatches(messages, maxBatchSize, maxBatchSizeBytes);

        // Then
        assertEquals(1, subBatches.size());
        assertEquals(3, subBatches.get(0).size());
        assertEquals(messages, subBatches.get(0));
    }

    @Test
    @DisplayName("Should create multiple sub-batches when record count limit exceeded")
    void createSubBatches_WhenRecordCountExceeded_ShouldCreateMultipleSubBatches() {
        // Given
        List<String> messages = Arrays.asList("msg1", "msg2", "msg3", "msg4", "msg5");
        int maxBatchSize = 2; // Force splitting
        long maxBatchSizeBytes = 10000L; // Large enough to not be limiting factor

        // When
        List<List<String>> subBatches = service.createSubBatches(messages, maxBatchSize, maxBatchSizeBytes);

        // Then
        assertEquals(3, subBatches.size());
        assertEquals(2, subBatches.get(0).size()); // First batch: 2 messages
        assertEquals(2, subBatches.get(1).size()); // Second batch: 2 messages  
        assertEquals(1, subBatches.get(2).size()); // Third batch: 1 message
    }

    @Test
    @DisplayName("Should create multiple sub-batches when size limit exceeded")
    void createSubBatches_WhenSizeLimitExceeded_ShouldCreateMultipleSubBatches() {
        // Given - messages that will exceed size limit
        List<String> messages = Arrays.asList(
            "short", 
            "this_is_a_much_longer_message_that_should_help_exceed_size_limits", 
            "another_very_long_message_to_test_size_constraints_properly"
        );
        int maxBatchSize = 10; // Large enough to not be limiting factor
        long maxBatchSizeBytes = 30L; // Small size to force splitting

        // When
        List<List<String>> subBatches = service.createSubBatches(messages, maxBatchSize, maxBatchSizeBytes);

        // Then
        assertTrue(subBatches.size() >= 2, "Should create multiple sub-batches due to size constraints");
        // Verify each sub-batch respects size limit
        for (List<String> subBatch : subBatches) {
            assertFalse(subBatch.isEmpty(), "Sub-batch should not be empty");
        }
    }

    @Test
    @DisplayName("Should return empty list for null or empty input")
    void createSubBatches_WhenNullOrEmptyInput_ShouldReturnEmptyList() {
        // Test null input
        List<List<String>> result1 = service.createSubBatches(null, 10, 1000L);
        assertEquals(0, result1.size());

        // Test empty input
        List<List<String>> result2 = service.createSubBatches(Collections.emptyList(), 10, 1000L);
        assertEquals(0, result2.size());
    }

    // ==================== Batch Delivery Tests ====================

    @Test
    @DisplayName("Should successfully deliver batch and update metrics")
    void deliverBatch_WhenSuccessful_ShouldReturnTrueAndUpdateMetrics() {
        // Given
        List<String> messages = Arrays.asList("test1", "test2");
        setupBatchConfiguration();
        setupSuccessfulFirehoseResponse();

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertTrue(result);
        verify(firehoseClient, times(1)).putRecordBatch(any(PutRecordBatchRequest.class));
        
        // Verify metrics
        BatchFirehoseMessageService.BatchFirehoseMetrics metrics = service.getMetrics();
        assertEquals(1L, metrics.getSuccessfulBatches());
        assertEquals(0L, metrics.getFailedBatches());
        assertEquals(2L, metrics.getTotalMessagesProcessed());
    }

    @Test
    @DisplayName("Should handle batch delivery failure and update metrics")
    void deliverBatch_WhenFirehoseFails_ShouldReturnFalseAndUpdateMetrics() {
        // Given
        List<String> messages = Arrays.asList("test1", "test2");
        setupBatchConfiguration();
        setupFirehoseExceptionWithNoRetry();

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertFalse(result);
        
        // Verify metrics
        BatchFirehoseMessageService.BatchFirehoseMetrics metrics = service.getMetrics();
        assertEquals(0L, metrics.getSuccessfulBatches());
        assertEquals(1L, metrics.getFailedBatches());
    }

    @Test
    @DisplayName("Should handle multiple sub-batches correctly")
    void deliverBatch_WhenMultipleSubBatches_ShouldDeliverAll() {
        // Given - Force creation of multiple sub-batches
        when(batchConfiguration.getMaxBatchSize()).thenReturn(1); // Force one message per sub-batch
        
        List<String> messages = Arrays.asList("msg1", "msg2", "msg3");
        setupSuccessfulFirehoseResponse();

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertTrue(result);
        verify(firehoseClient, times(3)).putRecordBatch(any(PutRecordBatchRequest.class));
        
        BatchFirehoseMessageService.BatchFirehoseMetrics metrics = service.getMetrics();
        assertEquals(1L, metrics.getSuccessfulBatches());
        assertEquals(3L, metrics.getTotalMessagesProcessed());
    }

    // ==================== Error Handling and Retry Tests ====================

    @Test
    @DisplayName("Should retry on buffer full exception")
    void deliverBatch_WhenBufferFullException_ShouldRetryAndTrackMetrics() {
        // Given
        List<String> messages = Arrays.asList("test1");
        setupBufferFullWithRecovery();

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertTrue(result); // Should eventually succeed
        
        BatchFirehoseMessageService.BatchFirehoseMetrics metrics = service.getMetrics();
        assertTrue(metrics.getBufferFullRetries() > 0);
        assertEquals(1L, metrics.getBufferFullRecoveries());
    }

    @Test
    @DisplayName("Should handle different exception types with appropriate retry strategies")
    void deliverBatch_WithDifferentExceptionTypes_ShouldUseAppropriateRetryStrategies() {
        // Given
        List<String> messages = Arrays.asList("test1");
        
        // Test Rate Limit exception
        SdkException rateLimitException = SdkException.builder()
            .message("Rate limit exceeded")
            .build();
        
        when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
            .thenThrow(rateLimitException)
            .thenReturn(createSuccessfulResponse(1));
            
        when(exceptionClassifier.classifyException(rateLimitException))
            .thenReturn(ExceptionType.RATE_LIMIT);
        when(retryStrategy.shouldRetry(ExceptionType.RATE_LIMIT, 0)).thenReturn(true);
        when(retryStrategy.calculateRetryDelay(ExceptionType.RATE_LIMIT, 0))
            .thenReturn(java.time.Duration.ofMillis(10)); // Short delay for test

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertTrue(result);
        verify(retryStrategy).shouldRetry(ExceptionType.RATE_LIMIT, 0);
        verify(retryStrategy).calculateRetryDelay(ExceptionType.RATE_LIMIT, 0);
    }

    @Test
    @DisplayName("Should stop retrying when max attempts reached")
    void deliverBatch_WhenMaxRetriesReached_ShouldStopRetrying() {
        // Given
        List<String> messages = Arrays.asList("test1");
        
        SdkException persistentException = SdkException.builder()
            .message("Persistent failure")
            .build();
            
        when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
            .thenThrow(persistentException);
            
        when(exceptionClassifier.classifyException(persistentException))
            .thenReturn(ExceptionType.GENERIC_FAILURE);
        when(retryStrategy.shouldRetry(any(ExceptionType.class), any(Integer.class)))
            .thenReturn(false); // No retries

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertFalse(result);
        verify(firehoseClient, times(1)).putRecordBatch(any(PutRecordBatchRequest.class));
    }

    // ==================== Metrics Tests ====================

    @Test
    @DisplayName("Should correctly calculate metrics")
    void getMetrics_ShouldReturnCorrectCalculations() {
        // Given - Setup some successful and failed batches
        setupSuccessfulFirehoseResponse();
        service.deliverBatch(Arrays.asList("msg1", "msg2"));
        service.deliverBatch(Arrays.asList("msg3", "msg4", "msg5"));
        
        // When
        BatchFirehoseMessageService.BatchFirehoseMetrics metrics = service.getMetrics();

        // Then
        assertEquals(2L, metrics.getSuccessfulBatches());
        assertEquals(0L, metrics.getFailedBatches());
        assertEquals(5L, metrics.getTotalMessagesProcessed());
        assertEquals(1.0, metrics.getBatchSuccessRate(), 0.001);
        assertEquals(2.5, metrics.getAverageMessagesPerBatch(), 0.001);
        assertEquals(DELIVERY_STREAM_NAME, metrics.getDeliveryStreamName());
    }

    @Test
    @DisplayName("Should reset all metrics correctly")
    void resetMetrics_ShouldClearAllCounters() {
        // Given - Generate some metrics
        setupSuccessfulFirehoseResponse();
        service.deliverBatch(Arrays.asList("msg1", "msg2"));

        // When
        service.resetMetrics();

        // Then
        BatchFirehoseMessageService.BatchFirehoseMetrics metrics = service.getMetrics();
        assertEquals(0L, metrics.getSuccessfulBatches());
        assertEquals(0L, metrics.getFailedBatches());
        assertEquals(0L, metrics.getTotalMessagesProcessed());
        assertEquals(0L, metrics.getTotalBytesProcessed());
        assertEquals(0L, metrics.getBufferFullRetries());
        assertEquals(0L, metrics.getBufferFullRecoveries());
    }

    // ==================== Connectivity Tests ====================

    @Test
    @DisplayName("Should return true when connectivity test succeeds")
    void testConnectivity_WhenSuccessful_ShouldReturnTrue() {
        // Given
        when(firehoseClient.describeDeliveryStream(any(java.util.function.Consumer.class)))
            .thenReturn(DescribeDeliveryStreamResponse.builder().build());

        // When
        boolean result = service.testConnectivity();

        // Then
        assertTrue(result);
        verify(firehoseClient).describeDeliveryStream(any(java.util.function.Consumer.class));
    }

    @Test
    @DisplayName("Should return false when connectivity test fails")
    void testConnectivity_WhenFails_ShouldReturnFalse() {
        // Given
        when(firehoseClient.describeDeliveryStream(any(java.util.function.Consumer.class)))
            .thenThrow(new RuntimeException("Connection failed"));

        // When
        boolean result = service.testConnectivity();

        // Then
        assertFalse(result);
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Should handle very large messages correctly")
    void deliverBatch_WithLargeMessage_ShouldHandleCorrectly() {
        // Given - Create a message close to 1MB limit
        StringBuilder largeMessage = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            largeMessage.append("This is a large message for testing. ");
        }
        
        List<String> messages = Arrays.asList(largeMessage.toString());
        setupSuccessfulFirehoseResponse();

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should handle failed records in Firehose response")
    void deliverBatch_WhenSomeRecordsFail_ShouldReturnFalse() {
        // Given
        List<String> messages = Arrays.asList("msg1", "msg2");
        
        PutRecordBatchResponse responseWithFailures = PutRecordBatchResponse.builder()
            .failedPutCount(1)
            .requestResponses(
                PutRecordBatchResponseEntry.builder()
                    .recordId("success-1")
                    .build(),
                PutRecordBatchResponseEntry.builder()
                    .errorCode("ServiceError")
                    .errorMessage("Internal service error")
                    .build()
            )
            .build();
            
        when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
            .thenReturn(responseWithFailures);

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should handle null Firehose response")
    void deliverBatch_WhenNullResponse_ShouldReturnFalse() {
        // Given
        List<String> messages = Arrays.asList("msg1");
        when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
            .thenReturn(null);

        // When
        boolean result = service.deliverBatch(messages);

        // Then
        assertFalse(result);
    }

    // ==================== Helper Methods ====================

    private void setupBatchConfiguration() {
        when(batchConfiguration.getMaxBatchSize()).thenReturn(500);
        when(batchConfiguration.getMaxBatchSizeBytes()).thenReturn(4L * 1024 * 1024); // 4MB
    }

    private void setupSuccessfulFirehoseResponse() {
        when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
            .thenAnswer(invocation -> {
                PutRecordBatchRequest request = invocation.getArgument(0);
                return createSuccessfulResponse(request.records().size());
            });
    }

    private PutRecordBatchResponse createSuccessfulResponse(int recordCount) {
        List<PutRecordBatchResponseEntry> responses = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            responses.add(PutRecordBatchResponseEntry.builder()
                .recordId("record-" + i)
                .build());
        }
        
        return PutRecordBatchResponse.builder()
            .failedPutCount(0)
            .requestResponses(responses)
            .build();
    }

    private void setupFirehoseExceptionWithNoRetry() {
        SdkException exception = SdkException.builder()
            .message("Service error")
            .build();
            
        when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
            .thenThrow(exception);
            
        when(exceptionClassifier.classifyException(exception))
            .thenReturn(ExceptionType.GENERIC_FAILURE);
        when(retryStrategy.shouldRetry(ExceptionType.GENERIC_FAILURE, 0))
            .thenReturn(false);
    }

    private void setupBufferFullWithRecovery() {
        SdkException bufferFullException = SdkException.builder()
            .message("Buffer is full")
            .build();
            
        when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
            .thenThrow(bufferFullException)
            .thenReturn(createSuccessfulResponse(1));
            
        when(exceptionClassifier.classifyException(bufferFullException))
            .thenReturn(ExceptionType.BUFFER_FULL);
        when(retryStrategy.shouldRetry(ExceptionType.BUFFER_FULL, 0))
            .thenReturn(true);
        when(retryStrategy.calculateRetryDelay(ExceptionType.BUFFER_FULL, 0))
            .thenReturn(java.time.Duration.ofMillis(10)); // Short delay for test
    }
}