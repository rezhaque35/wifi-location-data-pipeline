package com.wifi.positioning.repository;

import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DescribeTableEnhancedResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WifiAccessPointRepositoryImpl health check methods.
 * Tests follow TDD principles with comprehensive coverage of success and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WifiAccessPointRepository Health Check Tests")
class WifiAccessPointRepositoryImplTest {

    // Test Constants
    private static final String TEST_TABLE_NAME = "test_wifi_access_points";
    private static final long HEALTHY_RESPONSE_TIME_MS = 500L;
    private static final long UNHEALTHY_RESPONSE_TIME_MS = 1500L;
    private static final long TEST_ITEM_COUNT = 1000L;
    private static final long LATENCY_THRESHOLD_MS = 1000L;

    @Mock
    private DynamoDbEnhancedClient mockEnhancedClient;

    @Mock
    private DynamoDbTable<WifiAccessPoint> mockTable;

    @Mock
    private DescribeTableEnhancedResponse mockDescribeResponse;

    @Mock
    private TableDescription mockTableDescription;

    private WifiAccessPointRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        // Fix the mock setup to use proper generics
        when(mockEnhancedClient.table(eq(TEST_TABLE_NAME), any(TableSchema.class))).thenReturn(mockTable);
        repository = new WifiAccessPointRepositoryImpl(mockEnhancedClient, TEST_TABLE_NAME);
    }

    @Test
    @DisplayName("should_ReturnHealthyResult_When_TableAccessibleWithGoodLatency")
    void should_ReturnHealthyResult_When_TableAccessibleWithGoodLatency() throws Exception {
        // Arrange
        when(mockTable.describeTable()).thenReturn(mockDescribeResponse);
        when(mockDescribeResponse.table()).thenReturn(mockTableDescription);
        when(mockTableDescription.itemCount()).thenReturn(TEST_ITEM_COUNT);

        // Act
        WifiAccessPointRepository.HealthCheckResult result = repository.validateTableHealth();

        // Assert
        assertTrue(result.isHealthy());
        assertEquals(TEST_TABLE_NAME, result.tableName());
        assertEquals(TEST_ITEM_COUNT, result.itemCount());
        assertTrue(result.responseTimeMs() >= 0);
        assertTrue(result.responseTimeMs() < LATENCY_THRESHOLD_MS);
        assertEquals("Table is accessible and healthy", result.statusMessage());
        
        verify(mockTable).describeTable();
    }

    @Test
    @DisplayName("should_ReturnUnhealthyResult_When_TableAccessibleButSlowLatency")
    void should_ReturnUnhealthyResult_When_TableAccessibleButSlowLatency() throws Exception {
        // Arrange - simulate slow response by adding delay
        when(mockTable.describeTable()).thenAnswer(invocation -> {
            Thread.sleep(UNHEALTHY_RESPONSE_TIME_MS);
            return mockDescribeResponse;
        });
        when(mockDescribeResponse.table()).thenReturn(mockTableDescription);
        when(mockTableDescription.itemCount()).thenReturn(TEST_ITEM_COUNT);

        // Act
        WifiAccessPointRepository.HealthCheckResult result = repository.validateTableHealth();

        // Assert
        assertFalse(result.isHealthy());
        assertEquals(TEST_TABLE_NAME, result.tableName());
        assertEquals(TEST_ITEM_COUNT, result.itemCount());
        assertTrue(result.responseTimeMs() >= LATENCY_THRESHOLD_MS);
        assertEquals("Table response time exceeds threshold", result.statusMessage());
        
        verify(mockTable).describeTable();
    }

    @Test
    @DisplayName("should_ThrowResourceNotFoundException_When_TableNotFound")
    void should_ThrowResourceNotFoundException_When_TableNotFound() {
        // Arrange - Fix the exception creation
        ResourceNotFoundException exception = ResourceNotFoundException.builder()
                .message("Table not found")
                .build();
        when(mockTable.describeTable()).thenThrow(exception);

        // Act & Assert
        ResourceNotFoundException thrown = assertThrows(
                ResourceNotFoundException.class,
                () -> repository.validateTableHealth()
        );
        
        assertEquals("Table not found", thrown.getMessage());
        verify(mockTable).describeTable();
    }

    @Test
    @DisplayName("should_ThrowDynamoDbException_When_ConnectionError")
    void should_ThrowDynamoDbException_When_ConnectionError() {
        // Arrange - Use InternalServerErrorException which extends DynamoDbException
        InternalServerErrorException exception = InternalServerErrorException.builder()
                .message("Connection error")
                .build();
        when(mockTable.describeTable()).thenThrow(exception);

        // Act & Assert
        DynamoDbException thrown = assertThrows(
                DynamoDbException.class,
                () -> repository.validateTableHealth()
        );
        
        assertEquals("Connection error", thrown.getMessage());
        verify(mockTable).describeTable();
    }

    @Test
    @DisplayName("should_ThrowException_When_UnexpectedError")
    void should_ThrowException_When_UnexpectedError() {
        // Arrange
        RuntimeException exception = new RuntimeException("Unexpected error");
        when(mockTable.describeTable()).thenThrow(exception);

        // Act & Assert
        Exception thrown = assertThrows(
                Exception.class,
                () -> repository.validateTableHealth()
        );
        
        assertEquals("Unexpected error", thrown.getMessage());
        verify(mockTable).describeTable();
    }

    @Test
    @DisplayName("should_ReturnItemCount_When_GetApproximateItemCountCalled")
    void should_ReturnItemCount_When_GetApproximateItemCountCalled() throws Exception {
        // Arrange
        when(mockTable.describeTable()).thenReturn(mockDescribeResponse);
        when(mockDescribeResponse.table()).thenReturn(mockTableDescription);
        when(mockTableDescription.itemCount()).thenReturn(TEST_ITEM_COUNT);

        // Act
        long itemCount = repository.getApproximateItemCount();

        // Assert
        assertEquals(TEST_ITEM_COUNT, itemCount);
        verify(mockTable).describeTable();
    }

    @Test
    @DisplayName("should_ThrowResourceNotFoundException_When_GetItemCountAndTableNotFound")
    void should_ThrowResourceNotFoundException_When_GetItemCountAndTableNotFound() {
        // Arrange
        ResourceNotFoundException exception = ResourceNotFoundException.builder()
                .message("Table not found")
                .build();
        when(mockTable.describeTable()).thenThrow(exception);

        // Act & Assert
        ResourceNotFoundException thrown = assertThrows(
                ResourceNotFoundException.class,
                () -> repository.getApproximateItemCount()
        );
        
        assertEquals("Table not found", thrown.getMessage());
        verify(mockTable).describeTable();
    }

    @Test
    @DisplayName("should_ThrowDynamoDbException_When_GetItemCountAndConnectionError")
    void should_ThrowDynamoDbException_When_GetItemCountAndConnectionError() {
        // Arrange - Use InternalServerErrorException which extends DynamoDbException
        InternalServerErrorException exception = InternalServerErrorException.builder()
                .message("Connection error")
                .build();
        when(mockTable.describeTable()).thenThrow(exception);

        // Act & Assert
        DynamoDbException thrown = assertThrows(
                DynamoDbException.class,
                () -> repository.getApproximateItemCount()
        );
        
        assertEquals("Connection error", thrown.getMessage());
        verify(mockTable).describeTable();
    }

    @Test
    @DisplayName("should_MeasureAccurateResponseTime_When_ValidatingTableHealth")
    void should_MeasureAccurateResponseTime_When_ValidatingTableHealth() throws Exception {
        // Arrange
        long simulatedDelayMs = 100L;
        when(mockTable.describeTable()).thenAnswer(invocation -> {
            Thread.sleep(simulatedDelayMs);
            return mockDescribeResponse;
        });
        when(mockDescribeResponse.table()).thenReturn(mockTableDescription);
        when(mockTableDescription.itemCount()).thenReturn(TEST_ITEM_COUNT);

        // Act
        WifiAccessPointRepository.HealthCheckResult result = repository.validateTableHealth();

        // Assert
        assertTrue(result.responseTimeMs() >= simulatedDelayMs);
        assertTrue(result.responseTimeMs() < simulatedDelayMs + 50); // Allow for small variance
        verify(mockTable).describeTable();
    }
} 