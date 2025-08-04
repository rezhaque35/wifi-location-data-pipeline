// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/processor/FeedProcessorFactoryTest.java
package com.wifi.measurements.transformer.processor;

import com.wifi.measurements.transformer.dto.S3EventRecord;
import com.wifi.measurements.transformer.processor.FeedProcessor;
import com.wifi.measurements.transformer.processor.FeedProcessorFactory;
import com.wifi.measurements.transformer.processor.impl.DefaultFeedProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for the FeedProcessorFactory service.
 * 
 * <p>This test class provides thorough unit testing for the FeedProcessorFactory service,
 * covering processor selection logic, fallback behavior, and factory configuration.
 * The tests verify the simplified factory implementation that uses stream-based
 * processor selection with DefaultFeedProcessor fallback.</p>
 * 
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li><strong>Processor Selection:</strong> Tests for correct processor selection based on stream types</li>
 *   <li><strong>Fallback Behavior:</strong> Tests for default processor fallback when no custom processors match</li>
 *   <li><strong>Factory Configuration:</strong> Tests for correct processor counts and supported feed types</li>
 *   <li><strong>Edge Cases:</strong> Tests for null inputs and boundary conditions</li>
 *   <li><strong>Stream Handling:</strong> Tests for various stream name patterns and types</li>
 * </ul>
 * 
 * <p><strong>Test Scenarios:</strong></p>
 * <ul>
 *   <li>Default processor selection for known stream types</li>
 *   <li>Default processor fallback for unknown stream types</li>
 *   <li>Processor selection for specific stream names</li>
 *   <li>Factory configuration and processor count validation</li>
 *   <li>Supported feed types when no custom processors are configured</li>
 *   <li>Null S3 event record handling</li>
 * </ul>
 * 
 * <p><strong>Mocking Strategy:</strong></p>
 * <ul>
 *   <li>DefaultFeedProcessor is mocked to control processor behavior</li>
 *   <li>Stream name responses are controlled for testing different scenarios</li>
 *   <li>Processor capabilities are mocked for testing selection logic</li>
 * </ul>
 * 
 * <p><strong>Current Implementation Testing:</strong></p>
 * <ul>
 *   <li>Tests the simplified factory with no custom processors</li>
 *   <li>Verifies default processor is always returned</li>
 *   <li>Validates factory configuration methods</li>
 * </ul>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
class FeedProcessorFactoryTest {

    private FeedProcessorFactory factory;
    
    @Mock
    private DefaultFeedProcessor mockDefaultProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockDefaultProcessor.getSupportedFeedType()).thenReturn("unknown");
        when(mockDefaultProcessor.canProcess("MVS-stream")).thenReturn(true);
        when(mockDefaultProcessor.canProcess("unknown-stream")).thenReturn(true);
        
        factory = new FeedProcessorFactory(mockDefaultProcessor);
    }

    @Test
    @DisplayName("Should return default processor for any stream")
    void shouldReturnDefaultProcessorForAnyStream() {
        // Given
        S3EventRecord s3Event = createS3EventRecord("MVS-stream");
        
        // When
        FeedProcessor result = factory.getProcessor(s3Event);
        
        // Then
        assertNotNull(result);
        assertEquals("unknown", result.getSupportedFeedType());
    }
    
    @Test
    @DisplayName("Should return default processor for unknown stream")
    void shouldReturnDefaultProcessorForUnknownStream() {
        // Given
        S3EventRecord s3Event = createS3EventRecord("unknown-stream");
        
        // When
        FeedProcessor result = factory.getProcessor(s3Event);
        
        // Then
        assertNotNull(result);
        assertEquals("unknown", result.getSupportedFeedType());
    }
    
    @Test
    @DisplayName("Should return default processor even for specific stream names")
    void shouldReturnDefaultProcessorForSpecificStreamName() {
        // Given
        S3EventRecord s3Event = createS3EventRecord("GPS-data");
        when(mockDefaultProcessor.canProcess("GPS-data")).thenReturn(true);
        
        // When
        FeedProcessor result = factory.getProcessor(s3Event);
        
        // Then
        assertNotNull(result);
        assertEquals("unknown", result.getSupportedFeedType());
    }

    @Test
    @DisplayName("Should return correct total processor count")
    void shouldReturnCorrectTotalProcessorCount() {
        // When
        int count = factory.getTotalProcessorCount();
        
        // Then
        assertEquals(1, count); // Only default processor
    }
    
    @Test
    @DisplayName("Should return empty list for supported feed types when no custom processors")
    void shouldReturnEmptyListForSupportedFeedTypes() {
        // When
        List<String> supportedTypes = factory.getSupportedFeedTypes();
        
        // Then
        assertTrue(supportedTypes.isEmpty());
    }
    
    @Test
    @DisplayName("Should handle null S3 event record gracefully")
    void shouldHandleNullS3EventRecordGracefully() {
        // When & Then
        assertThrows(NullPointerException.class, () -> factory.getProcessor(null));
    }

    private S3EventRecord createS3EventRecord(String streamName) {
        return new S3EventRecord(
            "test-id",
            Instant.now(),
            "us-east-1",
            List.of("arn:aws:s3:::test-bucket"),
            "test-bucket",
            "test/object/key.txt",
            1024L,
            "test-etag",
            "test-version",
            "test-request-id",
            streamName
        );
    }
} 