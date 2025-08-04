package com.wifi.positioning.repository;

import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the batch operations in WifiAccessPointRepository.
 * Uses the InMemoryWifiAccessPointRepository for testing.
 */
@DisplayName("WifiAccessPointRepository Batch Operations Tests")
class WifiAccessPointRepositoryBatchTest {

    private WifiAccessPointRepository repository;
    private TestWifiAccessPointRepository testRepository;
    
    // Test constants
    private static final String MAC_1 = "00:11:22:33:44:55";
    private static final String MAC_2 = "AA:BB:CC:DD:EE:FF";
    private static final String MAC_3 = "11:22:33:44:55:66";
    private static final String VERSION_1 = "1.0";
    private static final String VERSION_2 = "2.0";
    private static final String NONEXISTENT_MAC = "99:99:99:99:99:99";

    @BeforeEach
    void setUp() {
        InMemoryWifiAccessPointRepository repo = new InMemoryWifiAccessPointRepository();
        repository = repo;
        testRepository = repo;
        
        // Add test access points
        WifiAccessPoint ap1 = WifiAccessPoint.builder()
                .macAddress(MAC_1)
                .version(VERSION_1)
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();
                
        WifiAccessPoint ap2 = WifiAccessPoint.builder()
                .macAddress(MAC_2)
                .version(VERSION_1)
                .latitude(37.7750)
                .longitude(-122.4195)
                .build();
                
        WifiAccessPoint ap3 = WifiAccessPoint.builder()
                .macAddress(MAC_3)
                .version(VERSION_1)
                .latitude(37.7751)
                .longitude(-122.4196)
                .build();
                
        testRepository.save(ap1);
        testRepository.save(ap2);
        testRepository.save(ap3);
    }
    
    @Test
    @DisplayName("Batch find by MAC addresses returns correct results")
    void batchFindByMacAddressesReturnsCorrectResults() {
        // Prepare test data
        Set<String> macAddresses = new HashSet<>(Arrays.asList(MAC_1, MAC_2, NONEXISTENT_MAC));
        
        // Execute the batch find
        Map<String, WifiAccessPoint> results = repository.findByMacAddresses(macAddresses);
        
        // Verify
        assertNotNull(results);
        assertEquals(2, results.size()); // Only the existing MAC addresses should be in the result
        
        // MAC_1 should have one access point
        assertTrue(results.containsKey(MAC_1));
        assertEquals(MAC_1, results.get(MAC_1).getMacAddress());
        
        // MAC_2 should have one access point
        assertTrue(results.containsKey(MAC_2));
        assertEquals(MAC_2, results.get(MAC_2).getMacAddress());
        
        // NONEXISTENT_MAC should not be in the results
        assertFalse(results.containsKey(NONEXISTENT_MAC));
    }
    
    @Test
    @DisplayName("Batch find with empty set returns empty map")
    void batchFindWithEmptySetReturnsEmptyMap() {
        // Execute the batch find with empty set
        Map<String, WifiAccessPoint> results = repository.findByMacAddresses(Collections.emptySet());
        
        // Verify
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
    
    @Test
    @DisplayName("Batch find with null returns empty map")
    void batchFindWithNullReturnsEmptyMap() {
        // Execute the batch find with null
        Map<String, WifiAccessPoint> results = repository.findByMacAddresses(null);
        
        // Verify
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
    
    @Test
    @DisplayName("Batch find after update returns the updated version")
    void batchFindAfterUpdateReturnsUpdatedVersion() {
        // Update the first AP with new coordinates
        WifiAccessPoint updatedAp1 = WifiAccessPoint.builder()
                .macAddress(MAC_1)
                .version(VERSION_2)
                .latitude(37.7752)
                .longitude(-122.4197)
                .build();
        testRepository.save(updatedAp1);
        
        // Execute the batch find
        Map<String, WifiAccessPoint> results = repository.findByMacAddresses(
                new HashSet<>(Arrays.asList(MAC_1)));
        
        // Verify
        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.containsKey(MAC_1));
        
        // Verify the updated version is returned (since table only has partition key)
        WifiAccessPoint ap = results.get(MAC_1);
        assertEquals(MAC_1, ap.getMacAddress());
        assertEquals(VERSION_2, ap.getVersion());
        assertEquals(37.7752, ap.getLatitude(), 0.0001);
        assertEquals(-122.4197, ap.getLongitude(), 0.0001);
    }
} 