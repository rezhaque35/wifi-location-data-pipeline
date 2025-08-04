package com.wifi.positioning.repository;

import com.wifi.positioning.dto.WifiAccessPoint;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for accessing WiFi access point data.
 * Includes methods necessary for efficient position calculation and health monitoring.
 */
public interface WifiAccessPointRepository {
    
    /**
     * Find an access point by its MAC address.
     * This is the primary method used by the positioning service.
     * 
     * @param macAddress MAC address of the access point
     * @return Optional containing the access point if found, empty otherwise
     */
    Optional<WifiAccessPoint> findByMacAddress(String macAddress);
    
    /**
     * Find multiple access points by their MAC addresses in a single batch operation.
     * This method optimizes DynamoDB access by reducing the number of API calls.
     * 
     * @param macAddresses Set of MAC addresses to look up
     * @return Map of MAC addresses to matching access points
     */
    Map<String, WifiAccessPoint> findByMacAddresses(Set<String> macAddresses);
    
    /**
     * Validates table accessibility and measures response time for health checks.
     * This method performs a lightweight operation to verify:
     * - Table exists and is accessible
     * - Service has read permissions
     * - Response time is within acceptable limits
     * 
     * @return HealthCheckResult containing validation results and metrics
     * @throws ResourceNotFoundException if the table does not exist
     * @throws DynamoDbException if there are connectivity or permission issues
     * @throws Exception for unexpected errors during validation
     */
    HealthCheckResult validateTableHealth() throws ResourceNotFoundException, DynamoDbException, Exception;
    
    /**
     * Gets the approximate item count from the table for health validation.
     * This method verifies read permissions by attempting to retrieve table statistics.
     * 
     * @return Approximate number of items in the table
     * @throws ResourceNotFoundException if the table does not exist
     * @throws DynamoDbException if there are connectivity or permission issues
     * @throws Exception for unexpected errors during count retrieval
     */
    long getApproximateItemCount() throws ResourceNotFoundException, DynamoDbException, Exception;
    
    /**
     * Result object for health check operations containing metrics and validation results.
     */
    record HealthCheckResult(
            boolean isHealthy,
            long responseTimeMs,
            String tableName,
            long itemCount,
            String statusMessage
    ) {}
} 