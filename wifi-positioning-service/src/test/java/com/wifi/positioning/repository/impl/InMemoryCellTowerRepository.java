// src/test/java/com/wifi/positioning/repository/impl/InMemoryCellTowerRepository.java
package com.wifi.positioning.repository.impl;

import com.wifi.positioning.dto.CellInfo;
import com.wifi.positioning.dto.CellTower;
import com.wifi.positioning.repository.CellTowerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of CellTowerRepository for unit testing.
 * Provides LRU caching and asynchronous cell tower lookups without external dependencies.
 * 
 * Design:
 * - Uses LinkedHashMap for LRU cache implementation
 * - Composite key: cellId + cellType (normalized to uppercase)
 * - All methods return CompletableFuture for consistent async interface
 * - No external dependencies required
 */
@Repository
@Profile("test")
public class InMemoryCellTowerRepository implements CellTowerRepository {
    
    private static final int CACHE_SIZE = 1000;
    private static final int MAX_CACHE_SIZE = 1000;
    
    // In-memory data store: composite key (cellId:cellType) -> CellTower
    private final Map<String, CellTower> dataStore = new ConcurrentHashMap<>();
    
    // LRU Cache for recent lookups
    private final Map<String, CellTower> lruCache = Collections.synchronizedMap(
        new LinkedHashMap<String, CellTower>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        }
    );
    
    /**
     * Asynchronously finds a cell tower by ID and type using composite key lookup.
     * Results are cached using LRU eviction.
     *
     * @param cellId the cell tower ID
     * @param cellType the cell tower type (normalized to uppercase)
     * @return CompletableFuture containing Optional with the cell tower if found
     */
    @Override
    public CompletableFuture<Optional<CellTower>> findByIdAsync(Long cellId, String cellType) {
        // Validate input
        if (cellId == null || cellType == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        String normalizedType = cellType.toUpperCase();
        String cacheKey = buildCacheKey(cellId, normalizedType);
        
        // Check LRU cache first
        if (lruCache.containsKey(cacheKey)) {
            CellTower cached = lruCache.get(cacheKey);
            return CompletableFuture.completedFuture(Optional.ofNullable(cached));
        }
        
        // Look up from data store
        CellTower tower = dataStore.get(cacheKey);
        
        // Cache the result (including null to avoid repeated lookups)
        if (tower != null) {
            lruCache.put(cacheKey, tower);
        } else {
            // Store null marker to avoid repeated lookups for non-existent cells
            lruCache.put(cacheKey, null);
        }
        
        return CompletableFuture.completedFuture(Optional.ofNullable(tower));
    }
    
    /**
     * Asynchronously selects the best cell tower from a list of cell info.
     * Prioritizes cell with strongest signal strength.
     *
     * @param cellInfoList list of cell information from client
     * @return CompletableFuture containing Optional with the best cell tower
     */
    @Override
    public CompletableFuture<Optional<CellTower>> findBestCellAsync(List<CellInfo> cellInfoList) {
        if (cellInfoList == null || cellInfoList.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        // Select best cell: first try cell with strongest signal, else first cell
        CellInfo bestCell = cellInfoList.stream()
            .max(Comparator.comparingInt((CellInfo c) -> c.signalStrength() != null ? c.signalStrength() : Integer.MIN_VALUE)
                .thenComparingLong(CellInfo::id))
            .orElse(cellInfoList.get(0));
        
        // Look up the best cell tower
        return findByIdAsync(bestCell.id(), bestCell.cellType());
    }
    
    /**
     * Validates repository health. In-memory implementation always returns healthy.
     *
     * @return HealthCheckResult with healthy status
     */
    @Override
    public HealthCheckResult validateHealth() {
        long startTime = System.nanoTime();
        long endTime = System.nanoTime();
        long responseTimeMs = (endTime - startTime) / 1_000_000L;
        
        return new HealthCheckResult(
            true,
            responseTimeMs,
            "In-memory cell tower repository is accessible"
        );
    }
    
    // ===== TEST HELPER METHODS =====
    
    /**
     * Adds a cell tower to the repository.
     *
     * @param cellTower the cell tower to add
     */
    public void addCellTower(CellTower cellTower) {
        if (cellTower == null || cellTower.getId() == null || cellTower.getCellType() == null) {
            return;
        }
        
        String cacheKey = buildCacheKey(cellTower.getId(), cellTower.getCellType());
        dataStore.put(cacheKey, cellTower);
    }
    
    /**
     * Clears all cell towers from the repository and cache.
     */
    public void clearAll() {
        dataStore.clear();
        lruCache.clear();
    }
    
    /**
     * Gets the current cache size.
     *
     * @return number of entries in LRU cache
     */
    public int getCacheSize() {
        return lruCache.size();
    }
    
    /**
     * Clears the LRU cache without affecting data store.
     */
    public void clearCache() {
        lruCache.clear();
    }
    
    /**
     * Loads test data for San Francisco area (single cell).
     */
    public void loadSanFranciscoCell() {
        CellTower sf = CellTower.builder()
            .id(12345L)
            .cellType("LTE")
            .latitude(37.7749)
            .longitude(-122.4194)
            .range(1000.0)
            .createdAt("2024-01-01T00:00:00Z")
            .updatedAt("2024-01-01T00:00:00Z")
            .build();
        
        addCellTower(sf);
    }
    
    /**
     * Loads test data for New York area (single cell).
     */
    public void loadNewYorkCell() {
        CellTower ny = CellTower.builder()
            .id(54321L)
            .cellType("LTE")
            .latitude(40.7128)
            .longitude(-74.0060)
            .range(1200.0)
            .createdAt("2024-01-01T00:00:00Z")
            .updatedAt("2024-01-01T00:00:00Z")
            .build();
        
        addCellTower(ny);
    }
    
    /**
     * Loads test data for multiple cell types.
     */
    public void loadMultipleCellTypes() {
        // LTE cell in SF
        CellTower lteSf = CellTower.builder()
            .id(12345L)
            .cellType("LTE")
            .latitude(37.7749)
            .longitude(-122.4194)
            .range(1000.0)
            .createdAt("2024-01-01T00:00:00Z")
            .updatedAt("2024-01-01T00:00:00Z")
            .build();
        
        // GSM cell in SF
        CellTower gsmSf = CellTower.builder()
            .id(12345L)
            .cellType("GSM")
            .latitude(37.7749)
            .longitude(-122.4194)
            .range(800.0)
            .createdAt("2024-01-01T00:00:00Z")
            .updatedAt("2024-01-01T00:00:00Z")
            .build();
        
        // 5G cell in SF
        CellTower fiveGSf = CellTower.builder()
            .id(12345L)
            .cellType("5G")
            .latitude(37.7749)
            .longitude(-122.4194)
            .range(1500.0)
            .createdAt("2024-01-01T00:00:00Z")
            .updatedAt("2024-01-01T00:00:00Z")
            .build();
        
        addCellTower(lteSf);
        addCellTower(gsmSf);
        addCellTower(fiveGSf);
    }
    
    /**
     * Loads test data for all common test scenarios.
     */
    public void loadAllTestScenarios() {
        loadSanFranciscoCell();
        loadNewYorkCell();
        loadMultipleCellTypes();
    }
    
    // ===== PRIVATE HELPER METHODS =====
    
    /**
     * Builds a cache key from cell ID and type.
     *
     * @param cellId the cell tower ID
     * @param cellType the normalized cell type
     * @return composite cache key
     */
    private String buildCacheKey(Long cellId, String cellType) {
        return cellId + ":" + cellType.toUpperCase();
    }
}
