package com.wifi.positioning.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wifi.positioning.dto.WifiAccessPoint;

import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;

/**
 * Unit tests for WifiAccessPointRepositoryImpl health check methods. Tests follow TDD principles
 * with comprehensive coverage of success and error scenarios.
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

  @Mock private DynamoDbEnhancedAsyncClient mockAsyncEnhancedClient;

  @Mock private DynamoDbAsyncTable<WifiAccessPoint> mockAsyncTable;

  @Mock private DescribeTableEnhancedResponse mockDescribeResponse;

  @Mock private TableDescription mockTableDescription;

  @Mock private BatchGetResultPage mockBatchResponse;
  
  @Mock private TableMetadata mockTableMetadata;

  private WifiAccessPointRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    // Mock table setup with proper schema support
    // Use lenient to avoid UnnecessaryStubbing errors for tests that don't use all mocks
    TableSchema<WifiAccessPoint> tableSchema = TableSchema.fromBean(WifiAccessPoint.class);
    lenient().when(mockAsyncEnhancedClient.table(eq(TEST_TABLE_NAME), any(TableSchema.class)))
        .thenReturn(mockAsyncTable);
    lenient().when(mockAsyncTable.tableSchema()).thenReturn(tableSchema);
    repository = new WifiAccessPointRepositoryImpl(mockAsyncEnhancedClient, TEST_TABLE_NAME);
  }

  // === BATCH RETRIEVAL TESTS ===

  @Test
  @DisplayName("should_ReturnAccessPoints_When_AllKeysProcessedSuccessfully")
  void should_ReturnAccessPoints_When_AllKeysProcessedSuccessfully() {
    // Arrange
    Set<String> macAddresses = Set.of("00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF");
    
    WifiAccessPoint ap1 = createTestAccessPoint("00:11:22:33:44:55");
    WifiAccessPoint ap2 = createTestAccessPoint("AA:BB:CC:DD:EE:FF");
    
    List<WifiAccessPoint> results = List.of(ap1, ap2);
    
    // Mock Publisher that calls onNext and onComplete
    when(mockAsyncEnhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
        .thenAnswer(invocation -> createPublisher(mockBatchResponse));
    when(mockBatchResponse.resultsForTable(mockAsyncTable))
        .thenReturn(results);
    when(mockBatchResponse.unprocessedKeysForTable(mockAsyncTable))
        .thenReturn(Collections.emptyList());

    // Act
    Map<String, WifiAccessPoint> result = repository.findByMacAddressesAsync(macAddresses).join();

    // Assert
    assertEquals(2, result.size());
    assertTrue(result.containsKey("00:11:22:33:44:55"));
    assertTrue(result.containsKey("AA:BB:CC:DD:EE:FF"));
    assertEquals(ap1, result.get("00:11:22:33:44:55"));
    assertEquals(ap2, result.get("AA:BB:CC:DD:EE:FF"));
    
    verify(mockAsyncEnhancedClient).batchGetItem(any(BatchGetItemEnhancedRequest.class));
  }

  @Test
  @DisplayName("should_ThrowRuntimeException_When_UnprocessedKeysRemainAfterRetries")
  void should_ThrowRuntimeException_When_UnprocessedKeysRemainAfterRetries() {
    // Arrange
    Set<String> macAddresses = Set.of("00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF");
    
    WifiAccessPoint ap1 = createTestAccessPoint("00:11:22:33:44:55");
    List<WifiAccessPoint> partialResults = List.of(ap1);
    
    // Mock unprocessed key
    Key unprocessedKey = Key.builder()
        .partitionValue("AA:BB:CC:DD:EE:FF")
        .build();
    
    // Mock Publisher that calls onNext and onComplete
    when(mockAsyncEnhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
        .thenAnswer(invocation -> createPublisher(mockBatchResponse));
    when(mockBatchResponse.resultsForTable(mockAsyncTable))
        .thenReturn(partialResults);
    when(mockBatchResponse.unprocessedKeysForTable(mockAsyncTable))
        .thenReturn(List.of(unprocessedKey));

    // Act & Assert
    CompletionException exception = assertThrows(
        CompletionException.class,
        () -> repository.findByMacAddressesAsync(macAddresses).join()
    );
    
    // Unwrap the actual exception from CompletionException
    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof RuntimeException);
    String message = cause.getMessage();
    
    // Verify the message contains key information about unprocessed keys
    assertTrue(message.contains("unprocessed keys"));
    assertTrue(message.contains("SDK retries exhausted"));
    assertTrue(message.contains("Requested=2"));
    assertTrue(message.contains("Retrieved=1"));
    assertTrue(message.contains("AA:BB:CC:DD:EE:FF"));
    
    verify(mockAsyncEnhancedClient).batchGetItem(any(BatchGetItemEnhancedRequest.class));
  }

  @Test
  @DisplayName("should_ReturnEmptyMap_When_EmptySetProvided")
  void should_ReturnEmptyMap_When_EmptySetProvided() {
    // Act
    Map<String, WifiAccessPoint> result = 
        repository.findByMacAddressesAsync(Collections.emptySet()).join();

    // Assert
    assertTrue(result.isEmpty());
    verify(mockAsyncEnhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
  }

  @Test
  @DisplayName("should_ReturnEmptyMap_When_NullSetProvided")
  void should_ReturnEmptyMap_When_NullSetProvided() {
    // Act
    Map<String, WifiAccessPoint> result = 
        repository.findByMacAddressesAsync(null).join();

    // Assert
    assertTrue(result.isEmpty());
    verify(mockAsyncEnhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
  }

  @Test
  @DisplayName("should_ThrowIllegalArgumentException_When_RequestSizeExceedsMaxBatchSize")
  void should_ThrowIllegalArgumentException_When_RequestSizeExceedsMaxBatchSize() {
    // Arrange
    Set<String> oversizedRequest = new HashSet<>();
    for (int i = 0; i <= 100; i++) {
      oversizedRequest.add(String.format("%02d:11:22:33:44:55", i));
    }

    // Act & Assert
    CompletionException exception = assertThrows(
        CompletionException.class,
        () -> repository.findByMacAddressesAsync(oversizedRequest).join()
    );
    
    // Unwrap the actual exception from CompletionException
    Throwable cause = exception.getCause();
    assertNotNull(cause);
    assertTrue(cause instanceof IllegalArgumentException);
    String message = cause.getMessage();
    assertTrue(message.contains("Request size exceeds maximum batch size"));
    assertTrue(message.contains("requested=101"));
    assertTrue(message.contains("max=100"));
  }

  // === HEALTH CHECK TESTS ===

  @Test
  @DisplayName("should_ReturnHealthyResult_When_TableAccessibleWithGoodLatency")
  void should_ReturnHealthyResult_When_TableAccessibleWithGoodLatency() throws Exception {
    // Arrange
    when(mockAsyncTable.describeTable())
        .thenReturn(CompletableFuture.completedFuture(mockDescribeResponse));
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

    verify(mockAsyncTable).describeTable();
  }

  @Test
  @DisplayName("should_ReturnUnhealthyResult_When_TableAccessibleButSlowLatency")
  void should_ReturnUnhealthyResult_When_TableAccessibleButSlowLatency() throws Exception {
    // Arrange - simulate slow response by adding delay
    CompletableFuture<DescribeTableEnhancedResponse> delayedFuture = 
        CompletableFuture.supplyAsync(() -> {
          try {
            Thread.sleep(UNHEALTHY_RESPONSE_TIME_MS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return mockDescribeResponse;
        });
    
    when(mockAsyncTable.describeTable()).thenReturn(delayedFuture);
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

    verify(mockAsyncTable).describeTable();
  }

  @Test
  @DisplayName("should_ThrowResourceNotFoundException_When_TableNotFound")
  void should_ThrowResourceNotFoundException_When_TableNotFound() {
    // Arrange
    ResourceNotFoundException exception =
        ResourceNotFoundException.builder().message("Table not found").build();
    when(mockAsyncTable.describeTable())
        .thenReturn(CompletableFuture.failedFuture(exception));

    // Act & Assert
    // The exception is unwrapped in the repository implementation
    ResourceNotFoundException thrown =
        assertThrows(ResourceNotFoundException.class, () -> repository.validateTableHealth());

    assertEquals("Table not found", thrown.getMessage());
    verify(mockAsyncTable).describeTable();
  }

  @Test
  @DisplayName("should_ThrowDynamoDbException_When_ConnectionError")
  void should_ThrowDynamoDbException_When_ConnectionError() {
    // Arrange
    InternalServerErrorException exception =
        InternalServerErrorException.builder().message("Connection error").build();
    when(mockAsyncTable.describeTable())
        .thenReturn(CompletableFuture.failedFuture(exception));

    // Act & Assert
    // The exception is unwrapped in the repository implementation
    DynamoDbException thrown =
        assertThrows(DynamoDbException.class, () -> repository.validateTableHealth());

    assertEquals("Connection error", thrown.getMessage());
    verify(mockAsyncTable).describeTable();
  }

  @Test
  @DisplayName("should_ThrowException_When_UnexpectedError")
  void should_ThrowException_When_UnexpectedError() {
    // Arrange
    RuntimeException exception = new RuntimeException("Unexpected error");
    when(mockAsyncTable.describeTable())
        .thenReturn(CompletableFuture.failedFuture(exception));

    // Act & Assert
    // The exception is unwrapped in the repository implementation
    Exception thrown = assertThrows(Exception.class, () -> repository.validateTableHealth());

    assertEquals("Unexpected error", thrown.getMessage());
    verify(mockAsyncTable).describeTable();
  }

  @Test
  @DisplayName("should_ReturnItemCount_When_GetApproximateItemCountCalled")
  void should_ReturnItemCount_When_GetApproximateItemCountCalled() throws Exception {
    // Arrange
    when(mockAsyncTable.describeTable())
        .thenReturn(CompletableFuture.completedFuture(mockDescribeResponse));
    when(mockDescribeResponse.table()).thenReturn(mockTableDescription);
    when(mockTableDescription.itemCount()).thenReturn(TEST_ITEM_COUNT);

    // Act
    long itemCount = repository.getApproximateItemCount();

    // Assert
    assertEquals(TEST_ITEM_COUNT, itemCount);
    verify(mockAsyncTable).describeTable();
  }

  @Test
  @DisplayName("should_ThrowResourceNotFoundException_When_GetItemCountAndTableNotFound")
  void should_ThrowResourceNotFoundException_When_GetItemCountAndTableNotFound() {
    // Arrange
    ResourceNotFoundException exception =
        ResourceNotFoundException.builder().message("Table not found").build();
    when(mockAsyncTable.describeTable())
        .thenReturn(CompletableFuture.failedFuture(exception));

    // Act & Assert
    // The exception is unwrapped in the repository implementation
    ResourceNotFoundException thrown =
        assertThrows(ResourceNotFoundException.class, () -> repository.getApproximateItemCount());

    assertEquals("Table not found", thrown.getMessage());
    verify(mockAsyncTable).describeTable();
  }

  @Test
  @DisplayName("should_ThrowDynamoDbException_When_GetItemCountAndConnectionError")
  void should_ThrowDynamoDbException_When_GetItemCountAndConnectionError() {
    // Arrange
    InternalServerErrorException exception =
        InternalServerErrorException.builder().message("Connection error").build();
    when(mockAsyncTable.describeTable())
        .thenReturn(CompletableFuture.failedFuture(exception));

    // Act & Assert
    // The exception is unwrapped in the repository implementation
    DynamoDbException thrown =
        assertThrows(DynamoDbException.class, () -> repository.getApproximateItemCount());

    assertEquals("Connection error", thrown.getMessage());
    verify(mockAsyncTable).describeTable();
  }

  @Test
  @DisplayName("should_MeasureAccurateResponseTime_When_ValidatingTableHealth")
  void should_MeasureAccurateResponseTime_When_ValidatingTableHealth() throws Exception {
    // Arrange
    long simulatedDelayMs = 100L;
    CompletableFuture<DescribeTableEnhancedResponse> delayedFuture = 
        CompletableFuture.supplyAsync(() -> {
          try {
            Thread.sleep(simulatedDelayMs);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return mockDescribeResponse;
        });
    
    when(mockAsyncTable.describeTable()).thenReturn(delayedFuture);
    when(mockDescribeResponse.table()).thenReturn(mockTableDescription);
    when(mockTableDescription.itemCount()).thenReturn(TEST_ITEM_COUNT);

    // Act
    WifiAccessPointRepository.HealthCheckResult result = repository.validateTableHealth();

    // Assert
    assertTrue(result.responseTimeMs() >= simulatedDelayMs);
    assertTrue(result.responseTimeMs() < simulatedDelayMs + 50); // Allow for small variance
    verify(mockAsyncTable).describeTable();
  }

  // === HELPER METHODS ===

  private WifiAccessPoint createTestAccessPoint(String macAddress) {
    return WifiAccessPoint.builder()
        .macAddress(macAddress)
        .version("test-1.0")
        .latitude(37.7749)
        .longitude(-122.4194)
        .altitude(10.0)
        .horizontalAccuracy(5.0)
        .verticalAccuracy(2.0)
        .confidence(0.85)
        .ssid("test-ssid")
        .frequency(2437)
        .vendor("test-vendor")
        .geohash("abcdef")
        .status(WifiAccessPoint.STATUS_ACTIVE)
        .build();
  }

  /**
   * Creates a mock BatchGetResultPagePublisher that properly invokes the reactive streams lifecycle.
   * This simulates DynamoDB's async behavior by calling onSubscribe, onNext, and onComplete.
   *
   * @param page The BatchGetResultPage to emit
   * @return A BatchGetResultPagePublisher that emits the provided page
   */
  private software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPagePublisher createPublisher(BatchGetResultPage page) {
    // Create a mock BatchGetResultPagePublisher
    software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPagePublisher publisher = 
        mock(software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPagePublisher.class);
    
    // Mock the subscribe method to properly handle reactive streams lifecycle
    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      org.reactivestreams.Subscriber<? super BatchGetResultPage> subscriber = 
          (org.reactivestreams.Subscriber<? super BatchGetResultPage>) invocation.getArgument(0);
      // Call onSubscribe first (reactive streams contract)
      subscriber.onSubscribe(new org.reactivestreams.Subscription() {
        @Override
        public void request(long n) {
          // Immediately emit the page and complete
          subscriber.onNext(page);
          subscriber.onComplete();
        }

        @Override
        public void cancel() {
          // No-op for tests
        }
      });
      return null;
    }).when(publisher).subscribe(any(org.reactivestreams.Subscriber.class));
    
    return publisher;
  }
}
