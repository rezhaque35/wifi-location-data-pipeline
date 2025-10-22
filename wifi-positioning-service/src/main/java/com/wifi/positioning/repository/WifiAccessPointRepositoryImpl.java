package com.wifi.positioning.repository;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.wifi.positioning.dto.WifiAccessPoint;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
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

  /**
   * Maximum number of retry attempts for handling unprocessed keys in batch operations.
   *
   * <p>Rationale: DynamoDB may return unprocessed keys due to: - Provisioned throughput limits -
   * Internal service throttling - Temporary unavailability
   *
   * <p>Strategy: Exponential backoff with limited retries - Attempt 1: Immediate retry - Attempt 2:
   * Short delay (handled by AWS SDK) - Attempt 3: Final attempt before graceful degradation
   *
   * <p>Mathematical Model: retry_count ∈ {1, 2, 3} Total attempts = initial_attempt +
   * retry_attempts = 1 + 3 = 4
   */
  private static final int MAX_BATCH_RETRIES = 3;

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

  private static final Logger logger = LoggerFactory.getLogger(WifiAccessPointRepositoryImpl.class);
  private final DynamoDbTable<WifiAccessPoint> accessPointTable;
  private final DynamoDbEnhancedClient enhancedClient;
  private final String tableName;

  public WifiAccessPointRepositoryImpl(
      DynamoDbEnhancedClient enhancedClient,
      @Value("${aws.dynamodb.table-name}") String tableName) {
    this.enhancedClient = enhancedClient;
    this.tableName = tableName;
    this.accessPointTable =
        enhancedClient.table(tableName, TableSchema.fromBean(WifiAccessPoint.class));
    logger.info("Initialized WifiAccessPointRepository with table: {}", tableName);
  }

  // === PUBLIC API LAYER ===

  @Override
  public Optional<WifiAccessPoint> findByMacAddress(String macAddress) {
    validateMacAddress(macAddress);

    try {
      WifiAccessPoint result = retrieveSingleAccessPoint(macAddress);
      return handleSingleResult(result);
    } catch (Exception e) {
      logger.error("Error retrieving access point by MAC address: {}", macAddress, e);
      throw new RuntimeException("Failed to retrieve access point", e);
    }
  }

  @Override
  public Map<String, WifiAccessPoint> findByMacAddresses(Set<String> macAddresses) {
    if (isEmptyOrNull(macAddresses)) {
      return Collections.emptyMap();
    }

    try {
      return orchestrateBatchRetrieval(macAddresses);
    } catch (DynamoDBThrottlingException e) {
      logger.error(
          "DynamoDB throughput limit reached: {} keys unprocessed after {} retries",
          e.getUnprocessedKeys().size(),
          e.getAttemptedRetries());
      throw e;
    } catch (Exception e) {
      logger.error("Unexpected error in batch retrieval of access points", e);
      throw new RuntimeException("Failed to retrieve access points in batch", e);
    }
  }

  // === ORCHESTRATION LAYER ===

  /**
   * Orchestrates the complete batch retrieval process by coordinating multiple operations. This
   * method operates at a high level of abstraction, delegating specific tasks to specialized
   * methods that handle individual concerns.
   *
   * <p>Process Flow: 1. Partition MAC addresses into DynamoDB batch size limits 2. Process each
   * batch independently using functional style 3. Aggregate results from all batches 4. Track
   * performance metrics 5. Return consolidated result map
   *
   * @param macAddresses Set of MAC addresses to retrieve
   * @return Map of MAC addresses to matching access points
   * @throws DynamoDBThrottlingException if any batch has unprocessed keys after max retries
   */
  private Map<String, WifiAccessPoint> orchestrateBatchRetrieval(Set<String> macAddresses) {
    long startTime = System.nanoTime();
    int requestedCount = macAddresses.size();

    Map<String, WifiAccessPoint> consolidatedResults =
        partitionIntoBatches(macAddresses).stream()
            .map(this::processSingleBatch)
            .flatMap(map -> map.entrySet().stream())
            .collect(
                HashMap::new,
                (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                HashMap::putAll);

    long durationMs = (System.nanoTime() - startTime) / NANOS_TO_MILLIS;
    logger.info(
        "Batch retrieval completed: requested={}, found={}, duration={}ms",
        requestedCount,
        consolidatedResults.size(),
        durationMs);

    return consolidatedResults;
  }

  /**
   * Processes a single batch of MAC addresses with retry logic for unprocessed keys. This method
   * coordinates the execution and retry logic while delegating the actual DynamoDB operations to
   * implementation-layer methods.
   *
   * <p>Retry Strategy: 1. Execute initial batch request with all keys 2. Accumulate successful
   * results 3. Identify unprocessed keys (throttled by DynamoDB) 4. Retry with ONLY unprocessed
   * keys 5. Continue until all keys processed or max retries reached 6. Throw exception if keys
   * remain unprocessed after max retries
   *
   * <p>Important: This method only retries keys that DynamoDB explicitly returns as "unprocessed"
   * due to throughput limits. Keys that don't exist in the database are not retried.
   *
   * @param macAddressBatch List of MAC addresses to process
   * @return Map of MAC addresses to matching access points
   * @throws DynamoDBThrottlingException if keys remain unprocessed after max retries
   */
  private Map<String, WifiAccessPoint> processSingleBatch(List<String> macAddressBatch) {
    Map<String, WifiAccessPoint> accumulatedResults = new HashMap<>();
    List<String> remainingKeys = new ArrayList<>(macAddressBatch);
    int retryCount = 0;

    while (!remainingKeys.isEmpty() && retryCount <= MAX_BATCH_RETRIES) {
      BatchGetItemEnhancedRequest batchRequest = buildBatchRequest(remainingKeys);
      BatchOperationResult result = executeBatchOperation(batchRequest);

      accumulatedResults.putAll(result.results());
      remainingKeys = result.unprocessedKeys();
      retryCount++;

      if (!remainingKeys.isEmpty()) {
        logger.warn(
            "Attempt {}: {} keys remain unprocessed due to throughput limits",
            retryCount, remainingKeys.size());
      }
    }

    if (!remainingKeys.isEmpty()) {
      throw new DynamoDBThrottlingException(
          "Failed to process keys after "
              + MAX_BATCH_RETRIES
              + " retries due to DynamoDB throughput limits",
          new HashSet<>(remainingKeys),
          MAX_BATCH_RETRIES);
    }

    return accumulatedResults;
  }

  // === IMPLEMENTATION LAYER ===

  /**
   * Validates MAC address input according to business rules.
   *
   * <p>Validation Rules: - Must not be null - Must not be empty or whitespace-only
   *
   * @param macAddress MAC address to validate
   * @throws IllegalArgumentException if validation fails
   */
  private void validateMacAddress(String macAddress) {
    if (macAddress == null || macAddress.trim().isEmpty()) {
      logger.error("MAC address cannot be null or empty");
      throw new IllegalArgumentException("MAC address cannot be null or empty");
    }
  }

  /**
   * Retrieves a single access point from DynamoDB using partition key lookup.
   *
   * <p>DynamoDB Operation: GetItem Key Structure: Partition key only (mac_addr)
   *
   * @param macAddress MAC address serving as partition key
   * @return WifiAccessPoint or null if not found
   */
  private WifiAccessPoint retrieveSingleAccessPoint(String macAddress) {
    Key partitionKey = Key.builder().partitionValue(macAddress).build();

    return accessPointTable.getItem(GetItemEnhancedRequest.builder().key(partitionKey).build());
  }

  /**
   * Handles the result of a single access point retrieval operation.
   *
   * @param result Retrieved access point (may be null)
   * @return Optional containing the result
   */
  private Optional<WifiAccessPoint> handleSingleResult(WifiAccessPoint result) {
    return Optional.ofNullable(result);
  }

  /**
   * Builds a DynamoDB BatchGetItem request for the specified MAC addresses.
   *
   * <p>Request Structure: - ReadBatch for WifiAccessPoint table - GetItem requests for each MAC
   * address - Partition key only (no sort key)
   *
   * @param macAddresses List of MAC addresses to include in batch
   * @return Configured BatchGetItemEnhancedRequest
   */
  private BatchGetItemEnhancedRequest buildBatchRequest(List<String> macAddresses) {
    ReadBatch.Builder<WifiAccessPoint> readBatchBuilder =
        ReadBatch.builder(WifiAccessPoint.class).mappedTableResource(accessPointTable);

    for (String macAddress : macAddresses) {
      Key key = Key.builder().partitionValue(macAddress).build();
      readBatchBuilder.addGetItem(key);
    }

    return BatchGetItemEnhancedRequest.builder().readBatches(readBatchBuilder.build()).build();
  }

  /**
   * Executes a single DynamoDB batch operation and processes the results.
   *
   * <p>Processing Logic: 1. Execute BatchGetItem request 2. Extract results for our table 3. Map
   * each result to MAC address key 4. Extract unprocessed keys (throttled keys only)
   *
   * <p>Unprocessed keys are those that DynamoDB could not process due to throughput limits. These
   * are distinct from keys that don't exist in the database (which simply don't appear in results).
   *
   * @param batchRequest Configured batch request
   * @return BatchOperationResult containing results and list of unprocessed MAC addresses
   */
  private BatchOperationResult executeBatchOperation(BatchGetItemEnhancedRequest batchRequest) {
    Map<String, WifiAccessPoint> results = new HashMap<>();
    List<String> unprocessedMacAddresses = new ArrayList<>();

    BatchGetResultPageIterable resultPages = enhancedClient.batchGetItem(batchRequest);

    for (BatchGetResultPage page : resultPages) {
      // Extract successful results using functional style
      page.resultsForTable(accessPointTable)
          .forEach(ap -> results.put(ap.getMacAddress(), ap));

      // Extract unprocessed keys (throttled keys only)
      page.unprocessedKeysForTable(accessPointTable).stream()
          .map(key -> key.partitionKeyValue().s())
          .forEach(unprocessedMacAddresses::add);
    }

    return new BatchOperationResult(results, unprocessedMacAddresses);
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
   * Partitions a set of MAC addresses into batches respecting DynamoDB size limits.
   *
   * <p>Partitioning Algorithm using functional style: - Convert Set to immutable List - Use
   * IntStream to generate batch indices - Map each index to a sublist of appropriate size - Collect
   * into list of batches
   *
   * <p>Mathematical Formula: number_of_batches = ⌈total_items / MAX_BATCH_SIZE⌉
   *
   * <p>Where ⌈⌉ represents the ceiling function.
   *
   * <p>Time Complexity: O(n) where n is the number of items Space Complexity: O(1) additional space
   * (sublists are views, not copies)
   *
   * @param macAddresses Set of MAC addresses to partition
   * @return List of batches, each containing at most MAX_BATCH_SIZE items
   */
  private List<List<String>> partitionIntoBatches(Set<String> macAddresses) {
    List<String> macList = List.copyOf(macAddresses);
    int totalBatches = (macList.size() + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;

    return java.util.stream.IntStream.range(0, totalBatches)
        .mapToObj(
            i ->
                macList.subList(
                    i * MAX_BATCH_SIZE, Math.min((i + 1) * MAX_BATCH_SIZE, macList.size())))
        .toList();
  }

  /**
   * Record representing the result of a batch operation. Encapsulates both the successful results
   * and the list of unprocessed keys that need to be retried.
   *
   * @param results Map of MAC addresses to retrieved access points
   * @param unprocessedKeys List of MAC addresses that were not processed due to throttling
   */
  private record BatchOperationResult(
      Map<String, WifiAccessPoint> results, List<String> unprocessedKeys) {}

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
      DescribeTableEnhancedResponse response = accessPointTable.describeTable();
      long itemCount = response.table().itemCount();

      long responseTimeMs = calculateResponseTime(startTime);
      boolean isHealthy = evaluateHealthStatus(responseTimeMs);
      String statusMessage = determineStatusMessage(isHealthy);

      return new HealthCheckResult(isHealthy, responseTimeMs, tableName, itemCount, statusMessage);

    } catch (ResourceNotFoundException e) {
      handleHealthCheckException(e, startTime, "Table not found during health check");
      throw e;
    } catch (DynamoDbException e) {
      handleHealthCheckException(e, startTime, "DynamoDB error during health check");
      throw e;
    } catch (Exception e) {
      handleHealthCheckException(e, startTime, "Unexpected error during health check");
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
      DescribeTableEnhancedResponse response = accessPointTable.describeTable();
      return response.table().itemCount();

    } catch (ResourceNotFoundException e) {
      logger.error("Table not found when retrieving item count: {}", tableName);
      throw e;
    } catch (DynamoDbException e) {
      logger.error("DynamoDB error when retrieving item count for table: {}", tableName, e);
      throw e;
    } catch (Exception e) {
      logger.error("Unexpected error when retrieving item count for table: {}", tableName, e);
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
}
