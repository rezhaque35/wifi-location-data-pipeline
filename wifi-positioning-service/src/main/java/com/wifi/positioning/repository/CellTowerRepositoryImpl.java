// src/main/java/com/wifi/positioning/repository/CellTowerRepositoryImpl.java
package com.wifi.positioning.repository;

import com.wifi.positioning.dto.CellInfo;
import com.wifi.positioning.dto.CellTower;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * DynamoDB implementation of CellTowerRepository with LRU caching.
 * 
 * This implementation follows the Single Level of Abstraction Principle (SLAP)
 * and uses an in-memory LRU cache for performance optimization.
 * 
 * Cache Strategy:
 * - Fixed-size cache (1000 entries) using LinkedHashMap
 * - Access-order mode tracks most recently used entries
 * - Least recently used entries are automatically evicted when cache is full
 * - Thread-safe operations via synchronized methods
 * - Caches both hits (found records) and misses (empty Optional) to reduce DynamoDB calls
 */
@Repository
@Profile("!test") // Only active when not in test profile
public class CellTowerRepositoryImpl implements CellTowerRepository {
    
    private static final Logger log = LoggerFactory.getLogger(CellTowerRepositoryImpl.class);
    private static final int CACHE_SIZE = 1000;
    private static final long LATENCY_THRESHOLD_MS = 1_000L;
    
    private final DynamoDbAsyncTable<CellTower> asyncCellTowerTable;
    private final String tableName;
    
    /**
     * Cache key for composite lookup (cellId + cellType).
     * Implements proper equals/hashCode for use as HashMap key.
     */
    private record CellTowerCacheKey(Long cellId, String cellType) {
        CellTowerCacheKey {
            if (cellId == null || cellType == null) {
                throw new IllegalArgumentException("CellId and cellType must not be null");
            }
        }
    }
    
    // Thread-safe LRU cache using ConcurrentHashMap + access order tracking
    private final Map<CellTowerCacheKey, Optional<CellTower>> cache = new ConcurrentHashMap<>(CACHE_SIZE);
    private final ConcurrentLinkedDeque<CellTowerCacheKey> accessOrder = new ConcurrentLinkedDeque<>();
    
    public CellTowerRepositoryImpl(
            DynamoDbEnhancedAsyncClient asyncEnhancedClient,
            @Value("${aws.dynamodb.cell-tower-table-name}") String tableName) {
        this.tableName = tableName;
        this.asyncCellTowerTable = asyncEnhancedClient.table(tableName, TableSchema.fromBean(CellTower.class));
        log.info("Initialized CellTowerRepository with async table: {} and LRU cache size: {}", tableName, CACHE_SIZE);
    }
    
    @Override
    public CompletableFuture<Optional<CellTower>> findBestCellAsync(List<CellInfo> cellInfoList) {
        if (cellInfoList == null || cellInfoList.isEmpty()) {
            log.debug("Empty or null cell info list provided for async lookup");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        var startTimeNanos = System.nanoTime();
        
        // Select cell with strongest signal (highest value, closest to 0)
        var bestCell = cellInfoList.stream()
            .max(Comparator.comparing(
                cell -> cell.signalStrength() != null ? cell.signalStrength() : Integer.MIN_VALUE
            ))
            .orElse(cellInfoList.get(0));
        
        log.info("CellTowerRepository.findBestCellAsync [SELECTION] candidateCells={} selectedCellId={} cellType={} signalStrengthDbm={}",
            cellInfoList.size(),
            bestCell.id(), 
            bestCell.cellType(),
            bestCell.signalStrength() != null ? bestCell.signalStrength() : "unknown");
        
        return findByIdAsync(bestCell.id(), bestCell.cellType())
            .thenApply(result -> {
                var durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000L;
                var status = result.isPresent() ? "SUCCESS" : "NOT_FOUND";
                log.info("CellTowerRepository.findBestCellAsync [COMPLETE] [{}] totalDurationMs={} candidateCells={} selectedCellId={}",
                    status, durationMs, cellInfoList.size(), bestCell.id());
                return result;
            });
    }
    
    /**
     * Asynchronously finds a cell tower by its ID and type.
     * Checks cache first, then performs non-blocking DynamoDB lookup.
     * Uses ConcurrentHashMap for thread-safe cache operations.
     * 
     * @param cellId the cell tower ID
     * @param cellType the cell tower type
     * @return CompletableFuture containing Optional with the cell tower if found
     */
    @Override
    public CompletableFuture<Optional<CellTower>> findByIdAsync(Long cellId, String cellType) {
        if (cellId == null || cellType == null) {
            log.warn("Null cellId or cellType provided for async lookup: cellId={}, cellType={}", cellId, cellType);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        var normalizedCellType = cellType.trim().toUpperCase();
        var cacheKey = new CellTowerCacheKey(cellId, normalizedCellType);
        var startTimeNanos = System.nanoTime();
        
        // Check cache first (thread-safe, fast)
        var cached = cache.get(cacheKey);
        if (cached != null) {
            updateAccessOrder(cacheKey);
            var cacheHitTimeMs = (System.nanoTime() - startTimeNanos) / 1_000_000L;
            log.info("CellTowerRepository.findByIdAsync [CACHE_HIT] cellId={} cellType={} responseTimeMs={} cacheSize={}", 
                cellId, normalizedCellType, cacheHitTimeMs, cache.size());
            return CompletableFuture.completedFuture(cached);
        }
        
        // Query DynamoDB asynchronously
        var key = Key.builder()
            .partitionValue(cellId.toString())
            .sortValue(normalizedCellType)
            .build();
        
        return asyncCellTowerTable.getItem(key)
            .thenApply(result -> {
                var optionalResult = Optional.ofNullable(result);
                
                // Cache the result (including misses) with LRU eviction
                putInCache(cacheKey, optionalResult);
                
                logDbQueryPerformance(startTimeNanos);
                
                return optionalResult;
            })
            .exceptionally(e -> {
                var durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000L;
                log.error("CellTowerRepository.findByIdAsync [DB_ERROR] cellId={} cellType={} responseTimeMs={} error={}", 
                    cellId, normalizedCellType, durationMs, e.getMessage(), e);
                return Optional.empty();
            });
    }
    
    /**
     * Logs performance metrics for database query operations.
     * 
     * @param startTimeNanos start time in nanoseconds
     */
    private void logDbQueryPerformance(long startTimeNanos) {
        var durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000L;
        log.info("CellTowerRepository.findByIdAsync [DB_QUERY] responseTimeMs={}", durationMs);
    }

    /**
     * Updates access order for LRU tracking (thread-safe).
     */
    private void updateAccessOrder(CellTowerCacheKey key) {
        accessOrder.remove(key);
        accessOrder.addLast(key);
    }
    
    /**
     * Puts an entry in cache with LRU eviction (thread-safe).
     */
    private void putInCache(CellTowerCacheKey key, Optional<CellTower> value) {
        cache.put(key, value);
        updateAccessOrder(key);
        
        // Evict oldest entries if cache is too large
        while (cache.size() > CACHE_SIZE) {
            var oldest = accessOrder.pollFirst();
            if (oldest != null) {
                cache.remove(oldest);
                log.debug("LRU cache evicting eldest entry: cellId={}, cellType={}", 
                    oldest.cellId(), oldest.cellType());
            }
        }
    }
    
    @Override
    public HealthCheckResult validateHealth() {
        var startTime = System.currentTimeMillis();
        
        try {
            // Perform a lightweight check by describing the table (async)
            asyncCellTowerTable.describeTable().join();
            
            var responseTime = System.currentTimeMillis() - startTime;
            var isHealthy = responseTime < LATENCY_THRESHOLD_MS;
            
            var statusMessage = isHealthy 
                ? "Cell tower repository is accessible and healthy"
                : String.format("Cell tower repository response time (%d ms) exceeds threshold (%d ms)", 
                    responseTime, LATENCY_THRESHOLD_MS);
            
            log.debug("Health check completed: healthy={}, responseTime={}ms, cacheSize={}", 
                isHealthy, responseTime, cache.size());
            
            return new HealthCheckResult(isHealthy, responseTime, statusMessage);
            
        } catch (ResourceNotFoundException e) {
            var responseTime = System.currentTimeMillis() - startTime;
            log.error("Cell tower table not found: {}", tableName, e);
            return new HealthCheckResult(
                false,
                responseTime,
                "Cell tower table not found: " + tableName
            );
        } catch (DynamoDbException e) {
            var responseTime = System.currentTimeMillis() - startTime;
            log.error("DynamoDB error during health check", e);
            return new HealthCheckResult(
                false,
                responseTime,
                "DynamoDB error: " + e.getMessage()
            );
        } catch (Exception e) {
            var responseTime = System.currentTimeMillis() - startTime;
            log.error("Health check failed with unexpected error", e);
            return new HealthCheckResult(
                false,
                responseTime,
                "Health check failed: " + e.getMessage()
            );
        }
    }
    
    /**
     * Returns current cache size for monitoring.
     * 
     * @return number of entries in cache
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Clears the cache. Useful for testing or forced refresh.
     */
    public void clearCache() {
        cache.clear();
        accessOrder.clear();
        log.info("Cell tower cache cleared");
    }
}
