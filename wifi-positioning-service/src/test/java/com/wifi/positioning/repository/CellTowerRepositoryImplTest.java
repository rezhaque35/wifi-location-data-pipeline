// src/test/java/com/wifi/positioning/repository/CellTowerRepositoryImplTest.java
package com.wifi.positioning.repository;

import com.wifi.positioning.dto.CellInfo;
import com.wifi.positioning.dto.CellTower;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DescribeTableEnhancedResponse;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CellTowerRepositoryImpl.
 * Tests cache behavior, DynamoDB integration, and cell selection logic.
 */
@ExtendWith(MockitoExtension.class)
class CellTowerRepositoryImplTest {
    
    @Mock
    private DynamoDbEnhancedAsyncClient asyncEnhancedClient;
    
    @Mock
    private DynamoDbAsyncTable<CellTower> asyncCellTowerTable;
    
    private CellTowerRepositoryImpl repository;
    
    private static final String TABLE_NAME = "wifi-cell-tower-location";
    
    @BeforeEach
    void setUp() {
        when(asyncEnhancedClient.table(eq(TABLE_NAME), any(TableSchema.class)))
            .thenReturn(asyncCellTowerTable);
        
        repository = new CellTowerRepositoryImpl(asyncEnhancedClient, TABLE_NAME);
    }
    
    @Test
    void testFindByIdReturnsEmpty() {
        // Given: DynamoDB returns null
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When: finding by ID and type (async)
        Optional<CellTower> result = repository.findByIdAsync(12345L, "LTE").join();
        
        // Then: returns empty
        assertTrue(result.isEmpty(), "Should return empty for non-existent cell tower");
        verify(asyncCellTowerTable).getItem(any(Key.class));
    }
    
    @Test
    void testFindByIdWithNullCellIdReturnsEmpty() {
        // When: finding with null cell ID
        Optional<CellTower> result = repository.findByIdAsync(null, "LTE").join();
        
        // Then: returns empty without querying DynamoDB
        assertTrue(result.isEmpty(), "Should return empty for null cell ID");
        verify(asyncCellTowerTable, never()).getItem(any(Key.class));
    }
    
    @Test
    void testFindByIdWithNullCellTypeReturnsEmpty() {
        // When: finding with null cell type
        Optional<CellTower> result = repository.findByIdAsync(12345L, null).join();
        
        // Then: returns empty without querying DynamoDB
        assertTrue(result.isEmpty(), "Should return empty for null cell type");
        verify(asyncCellTowerTable, never()).getItem(any(Key.class));
    }
    
    @Test
    void testFindByIdSuccessful() {
        // Given: DynamoDB returns a cell tower
        CellTower mockTower = CellTower.builder()
            .id(12345L)
            .cellType("LTE")
            .latitude(40.7130)
            .longitude(-74.0058)
            .range(2500.0)
            .createdAt("2024-01-15T10:30:00Z")
            .updatedAt("2024-01-15T10:30:00Z")
            .build();
        
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(mockTower));
        
        // When: finding by ID and type (async)
        Optional<CellTower> result = repository.findByIdAsync(12345L, "lte").join();
        
        // Then: returns the cell tower
        assertTrue(result.isPresent(), "Should return cell tower");
        assertEquals(12345L, result.get().getId());
        assertEquals("LTE", result.get().getCellType());
        assertEquals(40.7130, result.get().getLatitude());
        assertEquals(-74.0058, result.get().getLongitude());
        assertEquals(2500.0, result.get().getRange());
        verify(asyncCellTowerTable).getItem(any(Key.class));
    }
    
    @Test
    void testCacheBehavior() {
        // Given: DynamoDB returns a cell tower
        CellTower mockTower = CellTower.builder()
            .id(12345L)
            .cellType("LTE")
            .latitude(40.7130)
            .longitude(-74.0058)
            .range(2500.0)
            .build();
        
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(mockTower));
        
        // When: finding same ID and type multiple times (async)
        repository.findByIdAsync(12345L, "LTE").join();
        repository.findByIdAsync(12345L, "LTE").join();
        repository.findByIdAsync(12345L, "LTE").join();
        
        // Then: DynamoDB is queried only once (cached after first call)
        verify(asyncCellTowerTable, times(1)).getItem(any(Key.class));
        assertEquals(1, repository.getCacheSize(), "Should have 1 entry in cache");
    }
    
    @Test
    void testCacheNormalizesCellType() {
        // Given: DynamoDB returns null
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When: finding with different case variations of cell type (async)
        repository.findByIdAsync(12345L, "lte").join();
        repository.findByIdAsync(12345L, "LTE").join();
        repository.findByIdAsync(12345L, "Lte").join();
        
        // Then: all should hit the same cache entry
        verify(asyncCellTowerTable, times(1)).getItem(any(Key.class));
        assertEquals(1, repository.getCacheSize(), "Should have 1 entry for normalized cell type");
    }
    
    @Test
    void testCacheDifferentCellTypes() {
        // Given: DynamoDB returns null
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When: finding same ID with different cell types (async)
        repository.findByIdAsync(12345L, "LTE").join();
        repository.findByIdAsync(12345L, "GSM").join();
        
        // Then: should have separate cache entries
        verify(asyncCellTowerTable, times(2)).getItem(any(Key.class));
        assertEquals(2, repository.getCacheSize(), "Should have 2 entries for different cell types");
    }
    
    @Test
    void testFindBestCellWithEmptyList() {
        // When: finding best cell with empty list (async)
        Optional<CellTower> result = repository.findBestCellAsync(List.of()).join();
        
        // Then: returns empty
        assertTrue(result.isEmpty(), "Should return empty for empty list");
        verify(asyncCellTowerTable, never()).getItem(any(Key.class));
    }
    
    @Test
    void testFindBestCellWithNullList() {
        // When: finding best cell with null list (async)
        Optional<CellTower> result = repository.findBestCellAsync(null).join();
        
        // Then: returns empty
        assertTrue(result.isEmpty(), "Should return empty for null list");
        verify(asyncCellTowerTable, never()).getItem(any(Key.class));
    }
    
    @Test
    void testFindBestCellSelectsStrongestSignal() {
        // Given: multiple cells with different signal strengths
        CellInfo weakCell = new CellInfo(1L, 100, 200, "lte", -110);
        CellInfo strongCell = new CellInfo(2L, 101, 200, "lte", -85);
        CellInfo mediumCell = new CellInfo(3L, 102, 200, "lte", -95);
        
        List<CellInfo> cells = List.of(weakCell, strongCell, mediumCell);
        
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When: finding best cell (async)
        Optional<CellTower> result = repository.findBestCellAsync(cells).join();
        
        // Then: should query for the strongest signal cell (ID: 2)
        assertTrue(result.isEmpty(), "Should return empty as no towers in DB");
        verify(asyncCellTowerTable).getItem(any(Key.class));
        assertEquals(1, repository.getCacheSize(), "Should have cached the lookup for strongest signal");
    }
    
    @Test
    void testFindBestCellWithNoSignalStrength() {
        // Given: cells without signal strength (null)
        CellInfo cell1 = new CellInfo(1L, 100, 200, "lte", null);
        CellInfo cell2 = new CellInfo(2L, 101, 200, "lte", null);
        
        List<CellInfo> cells = List.of(cell1, cell2);
        
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When: finding best cell (async)
        Optional<CellTower> result = repository.findBestCellAsync(cells).join();
        
        // Then: should select first cell
        assertTrue(result.isEmpty(), "Should return empty as no towers in DB");
        assertEquals(1, repository.getCacheSize(), "Should have cached the lookup for first cell");
    }
    
    @Test
    void testFindBestCellWithMixedSignalStrength() {
        // Given: cells with mixed signal strengths (some null)
        CellInfo withSignal = new CellInfo(1L, 100, 200, "lte", -90);
        CellInfo withoutSignal = new CellInfo(2L, 101, 200, "lte", null);
        
        List<CellInfo> cells = List.of(withoutSignal, withSignal);
        
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When: finding best cell (async)
        Optional<CellTower> result = repository.findBestCellAsync(cells).join();
        
        // Then: should select cell with signal
        assertTrue(result.isEmpty(), "Should return empty as no towers in DB");
        assertEquals(1, repository.getCacheSize(), "Should have cached the lookup");
    }
    
    @Test
    void testClearCache() {
        // Given: cache with entries
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        repository.findByIdAsync(1L, "LTE").join();
        repository.findByIdAsync(2L, "GSM").join();
        repository.findByIdAsync(3L, "LTE").join();
        
        assertEquals(3, repository.getCacheSize(), "Should have 3 entries before clear");
        
        // When: clearing cache
        repository.clearCache();
        
        // Then: cache is empty
        assertEquals(0, repository.getCacheSize(), "Cache should be empty after clear");
    }
    
    @Test
    void testValidateHealth() throws Exception {
        // Given: DynamoDB is accessible
        when(asyncCellTowerTable.describeTable())
            .thenReturn(CompletableFuture.completedFuture(mock(DescribeTableEnhancedResponse.class)));
        
        // When: validating health
        CellTowerRepository.HealthCheckResult result = repository.validateHealth();
        
        // Then: returns healthy status
        assertTrue(result.isHealthy(), "Should return healthy status");
        assertTrue(result.responseTimeMs() >= 0, "Response time should be non-negative");
        assertNotNull(result.statusMessage(), "Status message should not be null");
        verify(asyncCellTowerTable).describeTable();
    }
    
    @Test
    void testLRUCacheEviction() {
        // Given: DynamoDB returns null and cache size is 1000
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When: adding more than cache size entries (async)
        for (long i = 1; i <= 1001; i++) {
            repository.findByIdAsync(i, "LTE").join();
        }
        
        // Then: cache size should not exceed limit
        assertEquals(1000, repository.getCacheSize(), 
            "Cache size should be limited to 1000 entries");
        
        // And: first entry should be evicted (LRU)
        // Query for entry 1 should hit DynamoDB again
        reset(asyncCellTowerTable);
        when(asyncCellTowerTable.getItem(any(Key.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        repository.findByIdAsync(1L, "LTE").join();
        verify(asyncCellTowerTable).getItem(any(Key.class));
    }
}
