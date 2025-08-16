// wifi-positioning-integration-service/src/test/java/com/wifi/positioning/health/HealthCheckCacheServiceTest.java
package com.wifi.positioning.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckCacheServiceTest {

    private HealthCheckCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new HealthCheckCacheService();
        // Set short TTL for testing
        ReflectionTestUtils.setField(cacheService, "cacheTtlSeconds", 1L);
        ReflectionTestUtils.setField(cacheService, "enableCaching", true);
    }

    @Test
    void getCachedOrRefresh_shouldCacheResult() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            callCount.incrementAndGet();
            return "test-result";
        };

        // When
        String result1 = cacheService.getCachedOrRefresh("test-key", supplier);
        String result2 = cacheService.getCachedOrRefresh("test-key", supplier);

        // Then
        assertEquals("test-result", result1);
        assertEquals("test-result", result2);
        assertEquals(1, callCount.get(), "Supplier should only be called once due to caching");
    }

    @Test
    void getCachedOrRefresh_shouldRefreshExpiredCache() throws InterruptedException {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            callCount.incrementAndGet();
            return "result-" + callCount.get();
        };

        // When
        String result1 = cacheService.getCachedOrRefresh("test-key", supplier);
        Thread.sleep(1100); // Wait for cache to expire
        String result2 = cacheService.getCachedOrRefresh("test-key", supplier);

        // Then
        assertEquals("result-1", result1);
        assertEquals("result-2", result2);
        assertEquals(2, callCount.get(), "Supplier should be called twice due to cache expiration");
    }

    @Test
    void getCachedOrRefresh_shouldBypassCacheWhenDisabled() {
        // Given
        ReflectionTestUtils.setField(cacheService, "enableCaching", false);
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            callCount.incrementAndGet();
            return "result-" + callCount.get();
        };

        // When
        String result1 = cacheService.getCachedOrRefresh("test-key", supplier);
        String result2 = cacheService.getCachedOrRefresh("test-key", supplier);

        // Then
        assertEquals("result-1", result1);
        assertEquals("result-2", result2);
        assertEquals(2, callCount.get(), "Supplier should be called twice when caching is disabled");
    }

    @Test
    void getCachedOrRefresh_shouldReturnStaleDataOnException() throws InterruptedException {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            callCount.incrementAndGet();
            if (callCount.get() == 1) {
                return "good-result";
            } else {
                throw new RuntimeException("Supplier failed");
            }
        };

        // When
        String result1 = cacheService.getCachedOrRefresh("test-key", supplier);
        // Wait for cache to expire (TTL is 1 second)
        Thread.sleep(1100);
        String result2 = cacheService.getCachedOrRefresh("test-key", supplier);

        // Then
        assertEquals("good-result", result1);
        assertEquals("good-result", result2); // Should return stale data
        assertEquals(2, callCount.get());
    }

    @Test
    void getCachedOrRefresh_shouldThrowExceptionWhenNoStaleData() {
        // Given
        Supplier<String> supplier = () -> {
            throw new RuntimeException("Supplier failed");
        };

        // When/Then
        assertThrows(HealthCheckCacheService.HealthCheckCacheException.class, () -> {
            cacheService.getCachedOrRefresh("test-key", supplier);
        });
    }

    @Test
    void clearCache_shouldRemoveSpecificEntry() {
        // Given
        cacheService.getCachedOrRefresh("key1", () -> "value1");
        cacheService.getCachedOrRefresh("key2", () -> "value2");
        assertEquals(2, cacheService.getCacheSize());

        // When
        cacheService.clearCache("key1");

        // Then
        assertEquals(1, cacheService.getCacheSize());
    }

    @Test
    void clearAllCache_shouldRemoveAllEntries() {
        // Given
        cacheService.getCachedOrRefresh("key1", () -> "value1");
        cacheService.getCachedOrRefresh("key2", () -> "value2");
        assertEquals(2, cacheService.getCacheSize());

        // When
        cacheService.clearAllCache();

        // Then
        assertEquals(0, cacheService.getCacheSize());
    }
}
