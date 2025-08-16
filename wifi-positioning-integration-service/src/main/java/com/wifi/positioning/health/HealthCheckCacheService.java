// wifi-positioning-integration-service/src/main/java/com/wifi/positioning/health/HealthCheckCacheService.java
package com.wifi.positioning.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Service for caching health check results to avoid excessive health endpoint calls.
 * 
 * <p>This service provides cached access to expensive health check operations like
 * positioning service connectivity checks. It implements a simple time-based cache
 * with configurable TTL to balance between performance and freshness of health data.
 * 
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>Time-based Caching:</strong> Results are cached for a configurable TTL period</li>
 *   <li><strong>Thread Safety:</strong> Concurrent access is safe with minimal locking</li>
 *   <li><strong>Cache Miss Handling:</strong> Automatically refreshes expired entries</li>
 *   <li><strong>Error Resilience:</strong> Handles exceptions during cache refresh gracefully</li>
 * </ul>
 */
@Service
public class HealthCheckCacheService {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckCacheService.class);

    /** Cache TTL in seconds from configuration */
    @Value("${health.indicator.cache-ttl-seconds:30}")
    private long cacheTtlSeconds;

    /** Whether caching is enabled */
    @Value("${health.indicator.enable-caching:true}")
    private boolean enableCaching;

    /** Cache for storing health check results */
    private final ConcurrentHashMap<String, CachedResult> cache = new ConcurrentHashMap<>();

    /** Lock for preventing multiple threads from refreshing the same cache entry */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Gets a cached result or refreshes it if expired.
     *
     * @param cacheKey unique key for the cached operation
     * @param supplier function to execute if cache miss or expired
     * @param <T> type of the cached result
     * @return cached or fresh result
     */
    public <T> T getCachedOrRefresh(String cacheKey, Supplier<T> supplier) {
        if (!enableCaching) {
            // If caching is disabled, always execute the supplier
            return supplier.get();
        }

        CachedResult cachedResult = cache.get(cacheKey);
        long currentTime = System.currentTimeMillis();
        
        // Check if we have a valid cached result
        if (cachedResult != null && !isExpired(cachedResult, currentTime)) {
            logger.debug("Cache hit for key: {}", cacheKey);
            @SuppressWarnings("unchecked")
            T result = (T) cachedResult.value;
            return result;
        }

        // Need to refresh - use lock to prevent multiple refreshes
        ReentrantLock lock = locks.computeIfAbsent(cacheKey, k -> new ReentrantLock());
        
        try {
            lock.lock();
            
            // Double-check pattern - another thread might have refreshed while we waited
            cachedResult = cache.get(cacheKey);
            if (cachedResult != null && !isExpired(cachedResult, currentTime)) {
                logger.debug("Cache hit after lock for key: {}", cacheKey);
                @SuppressWarnings("unchecked")
                T result = (T) cachedResult.value;
                return result;
            }

            // Execute the supplier and cache the result
            logger.debug("Cache miss for key: {}, refreshing", cacheKey);
            T result = supplier.get();
            cache.put(cacheKey, new CachedResult(result, currentTime));
            return result;
            
        } catch (Exception e) {
            logger.warn("Error refreshing cache for key: {}, attempting fallback to stale data", cacheKey, e);
            
            // If we have a stale cached result, return it rather than failing
            if (cachedResult != null) {
                logger.info("Returning stale cached result for key: {} due to refresh error: {}", cacheKey, e.getMessage());
                @SuppressWarnings("unchecked")
                T result = (T) cachedResult.value;
                return result;
            }
            
            // No cached result available, re-throw with contextual information
            logger.error("No cached result available for key: {} and refresh failed", cacheKey, e);
            throw new HealthCheckCacheException("Failed to refresh cache for key: " + cacheKey + 
                                             " and no cached result available", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears the cache entry for a specific key.
     *
     * @param cacheKey key to clear
     */
    public void clearCache(String cacheKey) {
        cache.remove(cacheKey);
        logger.debug("Cleared cache for key: {}", cacheKey);
    }

    /**
     * Clears all cache entries.
     */
    public void clearAllCache() {
        cache.clear();
        logger.debug("Cleared all cache entries");
    }

    /**
     * Gets the current cache size.
     *
     * @return number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Checks if a cached result is expired.
     *
     * @param cachedResult the cached result to check
     * @param currentTime current timestamp
     * @return true if expired, false otherwise
     */
    private boolean isExpired(CachedResult cachedResult, long currentTime) {
        long ageMs = currentTime - cachedResult.timestamp;
        long ttlMs = cacheTtlSeconds * 1000;
        return ageMs > ttlMs;
    }

    /**
     * Container for cached results with timestamp.
     */
    private static class CachedResult {
        final Object value;
        final long timestamp;

        CachedResult(Object value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    /**
     * Dedicated exception for health check cache operations.
     */
    public static class HealthCheckCacheException extends RuntimeException {
        public HealthCheckCacheException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
