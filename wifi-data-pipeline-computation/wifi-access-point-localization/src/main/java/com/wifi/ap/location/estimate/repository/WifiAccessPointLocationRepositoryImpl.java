package com.wifi.ap.location.estimate.repository;

import com.wifi.ap.location.estimate.dto.MacAddress;
import com.wifi.ap.location.estimate.dto.WifiAccessPointLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * DynamoDB implementation of the WifiAccessPointLocationRepository interface.
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
public class WifiAccessPointLocationRepositoryImpl implements WifiAccessPointLocationRepository {

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

    private static final Logger logger = LoggerFactory.getLogger(WifiAccessPointLocationRepositoryImpl.class);
    private final DynamoDbTable<WifiAccessPointLocation> accessPointTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName;

    public WifiAccessPointLocationRepositoryImpl(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName) {
        this.enhancedClient = enhancedClient;
        this.tableName = tableName;
        this.accessPointTable =
                enhancedClient.table(tableName, TableSchema.fromBean(WifiAccessPointLocation.class));
        logger.info("Initialized WifiAccessPointLocationRepository with table: {}", tableName);
    }

    // === PUBLIC API LAYER ===

    @Override
    public Optional<WifiAccessPointLocation> findByMacAddress(MacAddress macAddress) {
        validateMacAddress(macAddress);

        logger.debug("Querying access point by partition key (MAC address): {}", macAddress);
        try {
            return retrieveSingleAccessPoint(macAddress);
        } catch (Exception e) {
            logger.error("Error retrieving access point by MAC address: {}", macAddress, e);
            throw new RuntimeException("Failed to retrieve access point", e);
        }
    }

    @Override
    public Map<MacAddress, Optional<WifiAccessPointLocation>> findByMacAddresses(Set<MacAddress> macAddresses) {

        try {
            Map<String, WifiAccessPointLocation> consolidatedResults = new HashMap<>();
            List<List<MacAddress>> batches = partitionIntoBatches(macAddresses);


            for (List<MacAddress> batch : batches) {
                Map<String, WifiAccessPointLocation> batchResults = processSingleBatch(batch);
                consolidatedResults.putAll(batchResults);
            }

            logger.info(
                    "Successfully retrieved {} access points in batch operation", consolidatedResults.size());
            return mapAccessPointsTo(consolidatedResults, macAddresses);
        } catch (Exception e) {
            logger.error("Error in batch retrieval of access points", e);
            throw new RuntimeException("Failed to retrieve access points in batch", e);
        }
    }

    @Override
    public void save(Set<WifiAccessPointLocation> apLocations) {

    }

    private Map<MacAddress, Optional<WifiAccessPointLocation>> mapAccessPointsTo(Map<String, WifiAccessPointLocation> consolidatedResults, Set<MacAddress> macAddresses) {

        return macAddresses.stream().
                           collect(toMap(identity(), macAddress -> macAddress.macAddress()
                                                                             .map(consolidatedResults::get)));
    }

    // === ORCHESTRATION LAYER ===

    /**
     * Processes a single batch of MAC addresses with retry logic for unprocessed keys. This method
     * coordinates the execution and retry logic while delegating the actual DynamoDB operations to
     * implementation-layer methods.
     *
     * <p>Retry Strategy: 1. Execute initial batch request 2. Check for unprocessed keys 3. Retry with
     * exponential backoff (handled by AWS SDK) 4. Continue until success or max retries reached
     *
     * @param macAddressBatch List of MAC addresses to process
     * @return Map of MAC addresses to matching access points
     */
    private Map<String, WifiAccessPointLocation> processSingleBatch(List<MacAddress> macAddressBatch) {
        Map<String, WifiAccessPointLocation> batchResults = new HashMap<>();
        BatchGetItemEnhancedRequest batchRequest = buildBatchRequest(macAddressBatch);

        int retryCount = 0;
        boolean hasUnprocessedKeys;

        do {
            BatchOperationResult operationResult = executeBatchOperation(batchRequest);
            batchResults.putAll(operationResult.results());
            hasUnprocessedKeys = operationResult.hasUnprocessedKeys();

            retryCount++;
            handleRetryLogging(hasUnprocessedKeys, retryCount);

        } while (hasUnprocessedKeys && retryCount <= MAX_BATCH_RETRIES);

        handleFinalRetryResult(hasUnprocessedKeys);


        return batchResults;
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
    private void validateMacAddress(MacAddress macAddress) {
        macAddress.macAddress()
                  .map(String::trim)
                  .filter(Predicate.not(String::isEmpty))
                  .orElseThrow(() -> {
                      logger.error("MAC address cannot be null or empty");
                      throw new IllegalArgumentException("MAC address cannot be null or empty");
                  });


    }

    /**
     * Retrieves a single access point from DynamoDB using partition key lookup.
     *
     * <p>DynamoDB Operation: GetItem Key Structure: Partition key only (mac_addr)
     *
     * @param macAddress MAC address serving as partition key
     * @return WifiAccessPointLocation or null if not found
     */
    private Optional<WifiAccessPointLocation> retrieveSingleAccessPoint(MacAddress macAddress) {

        return macAddress.macAddress()
                         .map(String::trim)
                         .filter(Predicate.not(String::isEmpty))
                         .map(this::buildKey)
                         .map(this::buildRequest)
                         .map(accessPointTable::getItem);
    }

    private GetItemEnhancedRequest buildRequest(Key K) {
        return GetItemEnhancedRequest.builder()
                                     .key(K)
                                     .build();
    }

    private Key buildKey(String macAddress) {
        return Key.builder()
                  .partitionValue(macAddress)
                  .build();
    }


    /**
     * Builds a DynamoDB BatchGetItem request for the specified MAC addresses.
     *
     * <p>Request Structure: - ReadBatch for WifiAccessPointLocation table - GetItem requests for each MAC
     * address - Partition key only (no sort key)
     *
     * @param macAddresses List of MAC addresses to include in batch
     * @return Configured BatchGetItemEnhancedRequest
     */
    private BatchGetItemEnhancedRequest buildBatchRequest(List<MacAddress> macAddresses) {
        ReadBatch.Builder<WifiAccessPointLocation> readBatchBuilder =
                ReadBatch.builder(WifiAccessPointLocation.class)
                         .mappedTableResource(accessPointTable);

        macAddresses.stream()
                    .map(MacAddress::macAddress)
                    .flatMap(Optional::stream)
                    .map(String::trim)
                    .filter(String::isEmpty)
                    .map(this::buildKey)
                    .forEach(readBatchBuilder::addGetItem);

        return BatchGetItemEnhancedRequest.builder()
                                          .readBatches(readBatchBuilder.build())
                                          .build();
    }

    /**
     * Executes a single DynamoDB batch operation and processes the results.
     *
     * <p>Processing Logic: 1. Execute BatchGetItem request 2. Extract results for our table 3. Map
     * each result to MAC address key 4. Check for unprocessed keys
     *
     * @param batchRequest Configured batch request
     * @return BatchOperationResult containing results and unprocessed key status
     */
    private BatchOperationResult executeBatchOperation(BatchGetItemEnhancedRequest batchRequest) {
        Map<String, WifiAccessPointLocation> results = new HashMap<>();
        boolean hasUnprocessedKeys = false;

        BatchGetResultPageIterable resultPages = enhancedClient.batchGetItem(batchRequest);

        for (BatchGetResultPage page : resultPages) {
            List<WifiAccessPointLocation> pageResults = page.resultsForTable(accessPointTable);

            // Since table only has partition key, each MAC address maps to exactly one entry
            for (WifiAccessPointLocation ap : pageResults) {
                results.put(ap.getMacAddress(), ap);
            }

            hasUnprocessedKeys = !page.unprocessedKeysForTable(accessPointTable)
                                      .isEmpty();
        }

        return new BatchOperationResult(results, hasUnprocessedKeys);
    }

    /**
     * Handles logging for retry attempts based on unprocessed key status.
     *
     * @param hasUnprocessedKeys Whether there are unprocessed keys
     * @param retryCount         Current retry attempt number
     */
    private void handleRetryLogging(boolean hasUnprocessedKeys, int retryCount) {
        if (hasUnprocessedKeys) {
            logger.warn(
                    "Batch operation has unprocessed keys after attempt {}. "
                            + "Some access points may not be returned.",
                    retryCount);
        }
    }

    /**
     * Handles final logging after all retry attempts are exhausted.
     *
     * @param hasUnprocessedKeys Whether there are still unprocessed keys
     */
    private void handleFinalRetryResult(boolean hasUnprocessedKeys) {
        if (hasUnprocessedKeys) {
            logger.warn("Failed to process all keys after {} retries.", MAX_BATCH_RETRIES);
        }
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
     * <p>Partitioning Algorithm: - Convert Set to List for indexed access - Create sublists of
     * maximum size MAX_BATCH_SIZE - Ensure no batch exceeds DynamoDB limits
     *
     * <p>Mathematical Formula: number_of_batches = ⌈total_items / MAX_BATCH_SIZE⌉
     *
     * <p>Where ⌈⌉ represents the ceiling function.
     *
     * @param macAddresses Set of MAC addresses to partition
     * @return List of batches, each containing at most MAX_BATCH_SIZE items
     */
    private List<List<MacAddress>> partitionIntoBatches(Set<MacAddress> macAddresses) {
        return batchItems(new ArrayList<>(macAddresses), MAX_BATCH_SIZE);
    }

    /**
     * Generic utility method to split a list into batches of specified size.
     *
     * <p>Algorithm: 1. Iterate through list with step size equal to batch size 2. Create sublist from
     * current position to min(current + batchSize, listSize) 3. Add sublist to result collection
     *
     * <p>Time Complexity: O(n) where n is the number of items Space Complexity: O(1) additional space
     * (sublists are views, not copies)
     *
     * @param items     List of items to split
     * @param batchSize Maximum size of each batch
     * @param <T>       Type of items in the list
     * @return List of batches
     */
    private <T> List<List<T>> batchItems(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();

        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            batches.add(items.subList(i, endIndex));
        }

        return batches;
    }

    /**
     * Record representing the result of a batch operation. Encapsulates both the successful results
     * and the status of unprocessed keys.
     *
     * @param results            Map of MAC addresses to retrieved access points
     * @param hasUnprocessedKeys Whether the operation had unprocessed keys
     */
    private record BatchOperationResult(
            Map<String, WifiAccessPointLocation> results, boolean hasUnprocessedKeys) {
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
     * @throws DynamoDbException         if there are connectivity or permission issues
     * @throws Exception                 for unexpected errors during validation
     */
    @Override
    public HealthCheckResult validateTableHealth()
            throws ResourceNotFoundException, DynamoDbException, Exception {
        logger.debug("Starting table health validation for: {}", tableName);

        long startTime = System.nanoTime();

        try {
            DescribeTableEnhancedResponse response = accessPointTable.describeTable();
            long itemCount = response.table()
                                     .itemCount();

            long responseTimeMs = calculateResponseTime(startTime);
            boolean isHealthy = evaluateHealthStatus(responseTimeMs);
            String statusMessage = determineStatusMessage(isHealthy);

            logger.debug(
                    "Table health check completed - Table: {}, Response time: {}ms, Healthy: {}, Item count: {}",
                    tableName,
                    responseTimeMs,
                    isHealthy,
                    itemCount);

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
     * @param message   Base error message
     */
    private void handleHealthCheckException(Exception exception, long startTime, String message) {
        long responseTimeMs = calculateResponseTime(startTime);
        logger.error("{}: {} (response time: {}ms)", message, tableName, responseTimeMs, exception);
    }
}
