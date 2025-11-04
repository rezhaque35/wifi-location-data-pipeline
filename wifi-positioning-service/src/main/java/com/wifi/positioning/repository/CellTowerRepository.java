// src/main/java/com/wifi/positioning/repository/CellTowerRepository.java
package com.wifi.positioning.repository;

import com.wifi.positioning.dto.CellInfo;
import com.wifi.positioning.dto.CellTower;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for accessing cell tower location and range data.
 * Supports caching for performance optimization with LRU eviction.
 * 
 * All methods return CompletableFuture for non-blocking I/O operations.
 */
public interface CellTowerRepository {
    
    /**
     * Asynchronously finds a cell tower by its ID and type using composite key lookup.
     * Results are cached in an LRU cache for performance optimization.
     * Uses non-blocking I/O for optimal performance.
     * 
     * @param cellId the cell tower ID
     * @param cellType the cell tower type (e.g., "LTE", "GSM")
     * @return CompletableFuture containing Optional with the cell tower if found, empty otherwise
     */
    CompletableFuture<Optional<CellTower>> findByIdAsync(Long cellId, String cellType);
    
    /**
     * Asynchronously selects the best cell tower from a list of cell info.
     * Prioritizes cell with strongest signal strength, or first cell if no signal info.
     * Uses non-blocking I/O for optimal performance.
     * 
     * @param cellInfoList list of cell information from client
     * @return CompletableFuture containing Optional with the best cell tower, or empty if none found
     */
    CompletableFuture<Optional<CellTower>> findBestCellAsync(List<CellInfo> cellInfoList);
    
    /**
     * Validates repository health and accessibility.
     * 
     * @return HealthCheckResult containing validation results
     * @throws Exception if health check fails
     */
    HealthCheckResult validateHealth() throws Exception;
    
    /**
     * Result object for health check operations.
     */
    record HealthCheckResult(
        boolean isHealthy,
        long responseTimeMs,
        String statusMessage
    ) {}
}

