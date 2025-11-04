package com.wifi.positioning.repository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.wifi.positioning.dto.WifiAccessPoint;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

/**
 * DynamoDB implementation of the WifiAccessPointRepository interface.
 *
 * <p>This implementation follows the Single Level of Abstraction Principle (SLAP) by organizing
 * methods into clear abstraction layers:
 *
 * <p>1. Public API Layer - Interface implementation methods that provide high-level operations 2.
 * Orchestration Layer - Methods that coordinate multiple lower-level operations 3. Implementation
 * Layer - Methods that handle specific DynamoDB operations 4. Utility Layer - Helper methods for
 * data transformation and validation
 *
 * <p>The class is optimized for batch operations and includes comprehensive health monitoring
 * capabilities with detailed performance metrics and error handling.
 */
@Repository
@Profile("!test") // Only active when not in test profile
public class WifiAccessPointRepositoryImpl implements WifiAccessPointRepository {

  // === BATCH OPERATION CONSTANTS ===

  /**
   * Maximum number of items in a single DynamoDB BatchGetItem request.
   *
   * <p>Rationale: DynamoDB service limit is 100 items per batch operation. This constraint is
   * imposed by AWS to ensure consistent performance and prevent resource exhaustion on DynamoDB
   * infrastructure.
   *
   * <p>Mathematical Constraint: batch_size ≤ 100 items
   *
   * <p>Reference: AWS DynamoDB BatchGetItem API documentation
   * https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchGetItem.html
   */
  private static final int MAX_BATCH_SIZE = 100;


  // === HEALTH CHECK CONSTANTS ===

  /**
   * Response time threshold for health check evaluation in milliseconds.
   *
   * <p>Rationale: Based on DynamoDB performance characteristics and SLA requirements: - Normal
   * single-digit millisecond responses: < 10ms (excellent) - Acceptable responses under load: 10ms
   * - 100ms (good) - Warning threshold for degraded performance: 100ms - 1000ms (degraded) -
   * Critical threshold indicating problems: > 1000ms (critical)
   *
   * <p>Mathematical Formula for Health Classification: health_status = { EXCELLENT if response_time
   * < 10ms GOOD if 10ms ≤ response_time < 100ms DEGRADED if 100ms ≤ response_time < 1000ms CRITICAL
   * if response_time ≥ 1000ms }
   *
   * <p>This threshold aligns with microservice architecture best practices where downstream service
   * calls should complete within 1 second to prevent cascading failures and maintain acceptable
   * user experience.
   */
  private static final long LATENCY_THRESHOLD_MS = 1_000L;

  /**
   * Conversion factor from nanoseconds to milliseconds for high-precision timing.
   *
   * <p>Mathematical Formula: milliseconds = nanoseconds / 1,000,000
   *
   * <p>Derivation: - 1 second = 1,000 milliseconds = 1,000,000,000 nanoseconds - 1 millisecond =
   * 1,000,000 nanoseconds - Therefore: nanoseconds_to_milliseconds = nanoseconds / 1,000,000
   *
   * <p>Rationale: System.nanoTime() provides the highest precision timing available in the JVM
   * (typically nanosecond resolution), which is essential for accurate performance monitoring of
   * fast operations.
   */
  private static final long NANOS_TO_MILLIS = 1_000_000L;

  // === STATUS MESSAGE CONSTANTS ===

  /**
   * Standard status message for healthy table state. Used when table accessibility and response
   * time meet all health criteria.
   */
  private static final String HEALTHY_STATUS_MESSAGE = "Table is accessible and healthy";

  /**
   * Status message indicating performance degradation. Used when table is accessible but response
   * time exceeds acceptable thresholds.
   */
  private static final String SLOW_RESPONSE_STATUS_MESSAGE =
      "Table response time exceeds threshold";

  // ===== LOG MESSAGE CONSTANTS =====

  private static final String LOG_INITIALIZED = "Initialized WifiAccessPointRepository with async table: {}";
  private static final String LOG_ERROR_REQUEST_SIZE_EXCEEDS = "Request size exceeds maximum batch size: requested=%d, max=%d";
  private static final String LOG_INFO_BATCH_RETRIEVAL_COMPLETED = "Async batch retrieval completed: requested={}, found={}, duration={}ms";
  private static final String LOG_ERROR_UNEXPECTED_BATCH_RETRIEVAL = "Unexpected error in async batch retrieval of access points";
  private static final String LOG_ERROR_FAILED_RETRIEVE_BATCH = "Failed to retrieve access points in batch (async)";
  private static final String LOG_ERROR_BATCH_UNPROCESSED_KEYS = "DynamoDB batch request failed with %d unprocessed keys after SDK retries exhausted. "
      + "This indicates persistent throughput capacity issues. "
      + "Requested=%d, Retrieved=%d, Unprocessed=%s";
  private static final String LOG_ERROR_TABLE_NOT_FOUND_HEALTH_CHECK = "Table not found during health check";
  private static final String LOG_ERROR_DYNAMODB_ERROR_HEALTH_CHECK = "DynamoDB error during health check";
  private static final String LOG_ERROR_UNEXPECTED_ERROR_HEALTH_CHECK = "Unexpected error during health check";
  private static final String LOG_ERROR_TABLE_NOT_FOUND_ITEM_COUNT = "Table not found when retrieving item count: {}";
  private static final String LOG_ERROR_DYNAMODB_ERROR_ITEM_COUNT = "DynamoDB error when retrieving item count for table: {}";
  private static final String LOG_ERROR_UNEXPECTED_ERROR_ITEM_COUNT = "Unexpected error when retrieving item count for table: {}";
  private static final String EXCEPTION_MESSAGE_UNEXPECTED_ERROR_ITEM_COUNT = "Unexpected error when retrieving item count";

  private static final Logger logger = LoggerFactory.getLogger(WifiAccessPointRepositoryImpl.class);
  private final DynamoDbAsyncTable<WifiAccessPoint> asyncAccessPointTable;
  private final DynamoDbEnhancedAsyncClient asyncEnhancedClient;
  private final String tableName;

  public WifiAccessPointRepositoryImpl(
      DynamoDbEnhancedAsyncClient asyncEnhancedClient,
      @Value("${aws.dynamodb.table-name}") String tableName) {
    this.asyncEnhancedClient = asyncEnhancedClient;
    this.tableName = tableName;
    this.asyncAccessPointTable =
        asyncEnhancedClient.table(tableName, TableSchema.fromBean(WifiAccessPoint.class));
    logger.info(LOG_INITIALIZED, tableName);
  }

  // === PUBLIC API LAYER ===

  @Override
  public CompletableFuture<Map<String, WifiAccessPoint>> findByMacAddressesAsync(Set<String> macAddresses) {
    if (isEmptyOrNull(macAddresses)) {
      return CompletableFuture.completedFuture(Collections.emptyMap());
    }

    if (macAddresses.size() > MAX_BATCH_SIZE) {
      String errorMessage =
          String.format(
              LOG_ERROR_REQUEST_SIZE_EXCEEDS,
              macAddresses.size(), MAX_BATCH_SIZE);
      logger.error(errorMessage);
      return CompletableFuture.failedFuture(new IllegalArgumentException(errorMessage));
    }

    var startTime = System.nanoTime();
    
    return queryDBAsync(macAddresses)
        .thenApply(results -> {
          var durationMs = (System.nanoTime() - startTime) / NANOS_TO_MILLIS;
          
          logger.info(
              LOG_INFO_BATCH_RETRIEVAL_COMPLETED,
              macAddresses.size(),
              results.size(),
              durationMs);
          
          return results;
        })
        .exceptionally(e -> {
          logger.error(LOG_ERROR_UNEXPECTED_BATCH_RETRIEVAL, e);
          // Preserve the original exception if it's already a RuntimeException with a meaningful message
          if (e instanceof RuntimeException && e.getMessage() != null && !e.getMessage().isEmpty()) {
            throw (RuntimeException) e;
          }
          throw new RuntimeException(LOG_ERROR_FAILED_RETRIEVE_BATCH, e);
        });
  }

  // === IMPLEMENTATION LAYER ===


  /**
   * Asynchronously executes batch retrieval of access points from DynamoDB using a declarative,
   * functional style with proper reactive streams handling.
   *
   * <p>This method performs a non-blocking BatchGetItem operation using DynamoDB async client.
   * Returns immediately with a CompletableFuture that completes when the operation finishes.
   * The AWS SDK handles retries with exponential backoff and uses Netty for non-blocking I/O.
   *
   * <p>Implementation Details:
   * - Uses Reactive Streams Publisher/Subscriber pattern
   * - Accumulates results across multiple pages (if any) in thread-safe manner
   * - Completes future only after all pages are processed in onComplete()
   * - Validates unprocessed keys after all data is collected
   *
   * <p>DynamoDB Pagination Context:
   * For batch requests ≤100 items (our MAX_BATCH_SIZE), typically returns single page.
   * However, this implementation properly handles multiple pages for robustness and
   * future-proofing.
   *
   * <p>If unprocessed keys remain after SDK retries are exhausted, this method throws a
   * RuntimeException to indicate a persistent capacity or throttling issue that requires
   * attention. Returning partial results would compromise positioning accuracy.
   *
   * @param macAddresses Set of MAC addresses to retrieve
   * @return CompletableFuture containing a Map of MAC addresses to matching access points
   * @throws RuntimeException if unprocessed keys remain after all retry attempts
   */
  private CompletableFuture<Map<String, WifiAccessPoint>> queryDBAsync(Set<String> macAddresses) {
    var macAddressList = new ArrayList<>(macAddresses);
    var batchRequest = buildAsyncBatchRequest(macAddressList);
    var future = new CompletableFuture<Map<String, WifiAccessPoint>>();
    
    var subscriber = new BatchGetSubscriber(
        asyncAccessPointTable,
        macAddressList.size(),
        future,
        this::validateNoUnprocessedKeys
    );
    
    asyncEnhancedClient.batchGetItem(batchRequest).subscribe(subscriber);
    
    return future;
  }

  /**
   * Builds a DynamoDB async BatchGetItem request for the specified MAC addresses.
   *
   * <p>Request Structure: - ReadBatch for WifiAccessPoint table - GetItem requests for each MAC
   * address - Partition key only (no sort key)
   *
   * @param macAddresses List of MAC addresses to include in batch
   * @return Configured BatchGetItemEnhancedRequest for async operations
   */
  private BatchGetItemEnhancedRequest buildAsyncBatchRequest(List<String> macAddresses) {
    var readBatchBuilder =
        ReadBatch.builder(WifiAccessPoint.class).mappedTableResource(asyncAccessPointTable);

    for (String macAddress : macAddresses) {
      var key = Key.builder().partitionValue(macAddress).build();
      readBatchBuilder.addGetItem(key);
    }

    return BatchGetItemEnhancedRequest.builder().readBatches(readBatchBuilder.build()).build();
  }

  // === UTILITY LAYER ===

  /**
   * Checks if a collection is null or empty.
   *
   * @param collection Collection to check
   * @return true if null or empty, false otherwise
   */
  private boolean isEmptyOrNull(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  /**
   * Validates that no unprocessed keys remain after DynamoDB SDK retries.
   *
   * <p>The AWS SDK handles automatic retries with exponential backoff. If unprocessed keys
   * still remain, it indicates a persistent capacity or throttling issue that requires
   * immediate attention. Returning partial results would compromise positioning accuracy.
   *
   * @param response BatchGetResultPage from DynamoDB
   * @param requestedCount Number of MAC addresses originally requested
   * @param retrievedCount Number of access points successfully retrieved
   * @throws RuntimeException if unprocessed keys are detected after retry exhaustion
   */
  private void validateNoUnprocessedKeys(
      BatchGetResultPage response, 
      int requestedCount, 
      int retrievedCount) {
    
    List<String> unprocessedKeys = 
        response.unprocessedKeysForTable(asyncAccessPointTable).stream()
            .map(key -> key.partitionKeyValue().s())
            .toList();

    if (!unprocessedKeys.isEmpty()) {
      String errorMessage = String.format(
          LOG_ERROR_BATCH_UNPROCESSED_KEYS,
          unprocessedKeys.size(),
          requestedCount,
          retrievedCount,
          unprocessedKeys);
      
      logger.error(errorMessage);
      throw new RuntimeException(errorMessage);
    }
  }

  // === HEALTH CHECK METHODS ===

  /**
   * Validates table accessibility and measures response time for health checks.
   *
   * <p>This method performs a comprehensive health check by: 1. Measuring response time using
   * high-precision timing 2. Verifying table existence and accessibility 3. Retrieving item count
   * to validate read permissions 4. Evaluating response time against performance thresholds
   *
   * <p>Mathematical Formula for Response Time: response_time_ms = (end_time_nanos -
   * start_time_nanos) / NANOS_TO_MILLIS
   *
   * <p>Where: - start_time_nanos: System.nanoTime() before DynamoDB operation - end_time_nanos:
   * System.nanoTime() after DynamoDB operation - response_time_ms: Latency in milliseconds for
   * performance evaluation
   *
   * <p>Health Evaluation Logic: health_status = response_time_ms < LATENCY_THRESHOLD_MS
   *
   * @return HealthCheckResult containing validation results and metrics
   * @throws ResourceNotFoundException if the table does not exist
   * @throws DynamoDbException if there are connectivity or permission issues
   * @throws Exception for unexpected errors during validation
   */
  @Override
  public HealthCheckResult validateTableHealth()
      throws ResourceNotFoundException, DynamoDbException, Exception {
    long startTime = System.nanoTime();

    try {
      var response = asyncAccessPointTable.describeTable().join();
      long itemCount = response.table().itemCount();

      long responseTimeMs = calculateResponseTime(startTime);
      boolean isHealthy = evaluateHealthStatus(responseTimeMs);
      String statusMessage = determineStatusMessage(isHealthy);

      return new HealthCheckResult(isHealthy, responseTimeMs, tableName, itemCount, statusMessage);

    } catch (CompletionException e) {
      // Unwrap CompletionException to get the actual cause
      Throwable cause = e.getCause();
      if (cause instanceof ResourceNotFoundException rne) {
        handleHealthCheckException(rne, startTime, LOG_ERROR_TABLE_NOT_FOUND_HEALTH_CHECK);
        throw rne;
      } else if (cause instanceof DynamoDbException dbe) {
        handleHealthCheckException(dbe, startTime, LOG_ERROR_DYNAMODB_ERROR_HEALTH_CHECK);
        throw dbe;
      } else if (cause instanceof Exception ex) {
        handleHealthCheckException(ex, startTime, LOG_ERROR_UNEXPECTED_ERROR_HEALTH_CHECK);
        throw ex;
      } else {
        // Rethrow if it's an Error or unknown Throwable
        handleHealthCheckException(e, startTime, LOG_ERROR_UNEXPECTED_ERROR_HEALTH_CHECK);
        throw new Exception(LOG_ERROR_UNEXPECTED_ERROR_HEALTH_CHECK, cause);
      }
    } catch (ResourceNotFoundException e) {
      handleHealthCheckException(e, startTime, LOG_ERROR_TABLE_NOT_FOUND_HEALTH_CHECK);
      throw e;
    } catch (DynamoDbException e) {
      handleHealthCheckException(e, startTime, LOG_ERROR_DYNAMODB_ERROR_HEALTH_CHECK);
      throw e;
    } catch (Exception e) {
      handleHealthCheckException(e, startTime, LOG_ERROR_UNEXPECTED_ERROR_HEALTH_CHECK);
      throw e;
    }
  }

  /**
   * Gets the approximate item count from the table for health validation.
   *
   * <p>This method verifies read permissions by retrieving table statistics. The item count is
   * approximate and may not reflect real-time values due to DynamoDB's eventually consistent
   * nature.
   *
   * <p>Use Cases: - Validate that the service has read permissions on the table - Monitor table
   * growth for capacity planning - Detect empty tables that might indicate data loading issues
   *
   * @return Approximate number of items in the table
   * @throws ResourceNotFoundException if the table does not exist
   * @throws DynamoDbException if there are connectivity or permission issues
   * @throws Exception for unexpected errors during count retrieval
   */
  @Override
  public long getApproximateItemCount()
      throws ResourceNotFoundException, DynamoDbException, Exception {
    try {
      var response = asyncAccessPointTable.describeTable().join();
      return response.table().itemCount();

    } catch (CompletionException e) {
      // Unwrap CompletionException to get the actual cause
      Throwable cause = e.getCause();
      if (cause instanceof ResourceNotFoundException rne) {
        logger.error(LOG_ERROR_TABLE_NOT_FOUND_ITEM_COUNT, tableName);
        throw rne;
      } else if (cause instanceof DynamoDbException dbe) {
        logger.error(LOG_ERROR_DYNAMODB_ERROR_ITEM_COUNT, tableName, dbe);
        throw dbe;
      } else if (cause instanceof Exception ex) {
        logger.error(LOG_ERROR_UNEXPECTED_ERROR_ITEM_COUNT, tableName, ex);
        throw ex;
      } else {
        logger.error(LOG_ERROR_UNEXPECTED_ERROR_ITEM_COUNT, tableName, e);
        throw new Exception(EXCEPTION_MESSAGE_UNEXPECTED_ERROR_ITEM_COUNT, cause);
      }
    } catch (ResourceNotFoundException e) {
      logger.error(LOG_ERROR_TABLE_NOT_FOUND_ITEM_COUNT, tableName);
      throw e;
    } catch (DynamoDbException e) {
      logger.error(LOG_ERROR_DYNAMODB_ERROR_ITEM_COUNT, tableName, e);
      throw e;
    } catch (Exception e) {
      logger.error(LOG_ERROR_UNEXPECTED_ERROR_ITEM_COUNT, tableName, e);
      throw e;
    }
  }

  /**
   * Calculates response time in milliseconds from start time.
   *
   * @param startTime Start time in nanoseconds from System.nanoTime()
   * @return Response time in milliseconds
   */
  private long calculateResponseTime(long startTime) {
    long endTime = System.nanoTime();
    return (endTime - startTime) / NANOS_TO_MILLIS;
  }

  /**
   * Evaluates health status based on response time threshold.
   *
   * @param responseTimeMs Response time in milliseconds
   * @return true if healthy (below threshold), false otherwise
   */
  private boolean evaluateHealthStatus(long responseTimeMs) {
    return responseTimeMs < LATENCY_THRESHOLD_MS;
  }

  /**
   * Determines appropriate status message based on health evaluation.
   *
   * @param isHealthy Health status from evaluation
   * @return Appropriate status message
   */
  private String determineStatusMessage(boolean isHealthy) {
    return isHealthy ? HEALTHY_STATUS_MESSAGE : SLOW_RESPONSE_STATUS_MESSAGE;
  }

  /**
   * Handles exceptions during health check operations with consistent logging.
   *
   * @param exception Exception that occurred
   * @param startTime Start time for response time calculation
   * @param message Base error message
   */
  private void handleHealthCheckException(Exception exception, long startTime, String message) {
    long responseTimeMs = calculateResponseTime(startTime);
    logger.error("{}: {} (response time: {}ms)", message, tableName, responseTimeMs, exception);
  }

  // === REACTIVE STREAMS HELPER CLASSES ===

  /**
   * Thread-safe accumulator for batch retrieval results across multiple DynamoDB pages.
   *
   * <p>This class encapsulates the mutable state required for accumulating results from
   * a reactive stream of pages. It uses ConcurrentHashMap for thread-safe accumulation
   * and volatile for the last page reference to ensure visibility across threads.
   *
   * <p>Responsibilities:
   * - Accumulate access points from multiple pages into a single map
   * - Track the last received page for unprocessed key validation
   * - Provide immutable copy of results for safe publishing
   */
  private static class BatchResultAccumulator {
    private final Map<String, WifiAccessPoint> results = new ConcurrentHashMap<>();
    private final DynamoDbAsyncTable<WifiAccessPoint> table;
    private volatile BatchGetResultPage lastPage;

    BatchResultAccumulator(DynamoDbAsyncTable<WifiAccessPoint> table) {
      this.table = table;
    }

    /**
     * Adds all access points from a page to the accumulated results.
     *
     * @param page The page containing access points to add
     */
    void accumulatePage(BatchGetResultPage page) {
      page.resultsForTable(table)
          .forEach(ap -> results.put(ap.getMacAddress(), ap));
      lastPage = page;
    }

    /**
     * Returns an immutable copy of all accumulated results.
     *
     * @return Immutable map of MAC addresses to access points
     */
    Map<String, WifiAccessPoint> getImmutableResults() {
      return Map.copyOf(results);
    }

    /**
     * Returns the last page received, or null if no pages received.
     *
     * @return The last BatchGetResultPage or null
     */
    BatchGetResultPage getLastPage() {
      return lastPage;
    }

    /**
     * Returns the number of access points accumulated so far.
     *
     * @return Count of accumulated access points
     */
    int getResultCount() {
      return results.size();
    }
  }

  /**
   * Reactive Streams Subscriber implementation for DynamoDB batch get operations.
   *
   * <p>This subscriber bridges the reactive streaming paradigm with the batch processing
   * requirement of WiFi positioning calculations. It accumulates all results from potentially
   * multiple pages before completing the CompletableFuture, ensuring all data is available
   * for positioning accuracy.
   *
   * <p>Lifecycle:
   * 1. onSubscribe: Request all available pages (Long.MAX_VALUE)
   * 2. onNext: Accumulate results from each page as it arrives
   * 3. onComplete: Validate completeness and publish final results
   * 4. onError: Propagate errors to the CompletableFuture
   *
   * <p>Thread Safety: This class is designed to be used by a single reactive streams
   * Publisher, which guarantees sequential invocation of lifecycle methods.
   */
  private static class BatchGetSubscriber implements org.reactivestreams.Subscriber<BatchGetResultPage> {
    private final BatchResultAccumulator accumulator;
    private final int requestedCount;
    private final CompletableFuture<Map<String, WifiAccessPoint>> resultFuture;
    private final UnprocessedKeyValidator validator;

    /**
     * Functional interface for unprocessed key validation.
     * Allows dependency injection of validation logic for better testability.
     */
    @FunctionalInterface
    interface UnprocessedKeyValidator {
      void validate(BatchGetResultPage page, int requestedCount, int retrievedCount);
    }

    BatchGetSubscriber(
        DynamoDbAsyncTable<WifiAccessPoint> table,
        int requestedCount,
        CompletableFuture<Map<String, WifiAccessPoint>> resultFuture,
        UnprocessedKeyValidator validator) {
      this.accumulator = new BatchResultAccumulator(table);
      this.requestedCount = requestedCount;
      this.resultFuture = resultFuture;
      this.validator = validator;
    }

    @Override
    public void onSubscribe(org.reactivestreams.Subscription subscription) {
      // Request all available pages upfront (non-blocking)
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(BatchGetResultPage page) {
      try {
        accumulator.accumulatePage(page);
      } catch (Exception e) {
        resultFuture.completeExceptionally(e);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      resultFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
      try {
        publishResults();
      } catch (Exception e) {
        resultFuture.completeExceptionally(e);
      }
    }

    /**
     * Publishes final results after validating completeness.
     * Called when all pages have been processed.
     */
    private void publishResults() {
      var lastPage = accumulator.getLastPage();

      if (lastPage != null) {
        // Validate no unprocessed keys remain after SDK retries
        validator.validate(lastPage, requestedCount, accumulator.getResultCount());
        resultFuture.complete(accumulator.getImmutableResults());
      } else {
        // No pages received - return empty results
        resultFuture.complete(Collections.emptyMap());
      }
    }
  }
}
