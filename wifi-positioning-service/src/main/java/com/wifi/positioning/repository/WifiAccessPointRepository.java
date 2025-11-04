package com.wifi.positioning.repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.wifi.positioning.dto.WifiAccessPoint;

import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

/**
 * Repository interface for accessing WiFi access point data. Includes methods necessary for
 * efficient position calculation and health monitoring.
 * 
 * All data access methods return CompletableFuture for non-blocking I/O operations.
 */
public interface WifiAccessPointRepository {

  /**
   * Asynchronously find multiple access points by their MAC addresses in a single batch operation.
   *
   * <p>This method uses non-blocking I/O to retrieve access points for the provided MAC addresses.
   * The CompletableFuture completes when the DynamoDB operation finishes.
   *
   * @param macAddresses Set of MAC addresses to look up (must not exceed 100 items)
   * @return CompletableFuture containing a Map of MAC addresses to matching access points
   * @throws IllegalArgumentException if macAddresses size exceeds maximum batch size
   */
  CompletableFuture<Map<String, WifiAccessPoint>> findByMacAddressesAsync(Set<String> macAddresses);

  /**
   * Validates table accessibility and measures response time for health checks. This method
   * performs a lightweight operation to verify: - Table exists and is accessible - Service has read
   * permissions - Response time is within acceptable limits
   *
   * @return HealthCheckResult containing validation results and metrics
   * @throws ResourceNotFoundException if the table does not exist
   * @throws DynamoDbException if there are connectivity or permission issues
   * @throws Exception for unexpected errors during validation
   */
  HealthCheckResult validateTableHealth()
      throws ResourceNotFoundException, DynamoDbException, Exception;

  /**
   * Gets the approximate item count from the table for health validation. This method verifies read
   * permissions by attempting to retrieve table statistics.
   *
   * @return Approximate number of items in the table
   * @throws ResourceNotFoundException if the table does not exist
   * @throws DynamoDbException if there are connectivity or permission issues
   * @throws Exception for unexpected errors during count retrieval
   */
  long getApproximateItemCount() throws ResourceNotFoundException, DynamoDbException, Exception;

  /** Result object for health check operations containing metrics and validation results. */
  record HealthCheckResult(
      boolean isHealthy,
      long responseTimeMs,
      String tableName,
      long itemCount,
      String statusMessage) {}
}
