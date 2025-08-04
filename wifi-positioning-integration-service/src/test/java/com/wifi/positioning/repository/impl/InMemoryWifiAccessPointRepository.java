package com.wifi.positioning.repository.impl;

import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.TestWifiAccessPointRepository;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the WifiAccessPointRepository interface for testing.
 * This implementation does not require any external dependencies like DynamoDB.
 * Since the real DynamoDB table only has a partition key (mac_addr), each MAC address
 * can only have one entry, which is reflected in this implementation.
 */
@Profile("test")
public class InMemoryWifiAccessPointRepository implements WifiAccessPointRepository, TestWifiAccessPointRepository {
    
    // Map of MAC address to access point (single entry per MAC since only partition key exists)
    private final Map<String, WifiAccessPoint> dataStore = new ConcurrentHashMap<>();
    
    @Override
    public Optional<WifiAccessPoint> findByMacAddress(String macAddress) {
        WifiAccessPoint accessPoint = dataStore.get(macAddress);
        return Optional.ofNullable(accessPoint);
    }
    
    /**
     * Find multiple access points by their MAC addresses in a single batch operation.
     * This in-memory implementation maps each MAC address to its corresponding access point.
     * 
     * @param macAddresses Set of MAC addresses to look up
     * @return Map of MAC addresses to matching access points
     */
    @Override
    public Map<String, WifiAccessPoint> findByMacAddresses(Set<String> macAddresses) {
        if (macAddresses == null || macAddresses.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, WifiAccessPoint> result = new HashMap<>();
        
        // Lookup each MAC address and add to the result map
        for (String macAddress : macAddresses) {
            WifiAccessPoint accessPoint = dataStore.get(macAddress);
            if (accessPoint != null) {
                result.put(macAddress, accessPoint);
            }
        }
        
        return result;
    }
    
    /**
     * Find an access point by MAC address and version.
     * Since the table only has a partition key, this method checks if the stored
     * access point matches the requested version.
     * 
     * @param macAddress MAC address of the access point
     * @param version Version identifier
     * @return Optional containing the access point if found and version matches
     */
    @Override
    public Optional<WifiAccessPoint> findByMacAddressAndVersion(String macAddress, String version) {
        if (macAddress == null || version == null) {
            return Optional.empty();
        }
        
        WifiAccessPoint accessPoint = dataStore.get(macAddress);
        if (accessPoint != null && version.equals(accessPoint.getVersion())) {
            return Optional.of(accessPoint);
        }
        return Optional.empty();
    }
    
    /**
     * Save an access point to the repository.
     * Since the table only has a partition key, this replaces any existing entry
     * for the same MAC address.
     * 
     * @param accessPoint The access point to save
     */
    @Override
    public void save(WifiAccessPoint accessPoint) {
        if (accessPoint == null || accessPoint.getMacAddress() == null) {
            return;
        }
        
        // Simply store the access point, replacing any existing entry
        dataStore.put(accessPoint.getMacAddress(), accessPoint);
    }
    
    /**
     * Batch save multiple access points
     * 
     * @param accessPoints List of access points to save
     */
    @Override
    public <T> void batchSave(List<T> accessPoints) {
        if (accessPoints == null) {
            return;
        }
        
        for (Object obj : accessPoints) {
            if (obj instanceof WifiAccessPoint) {
                save((WifiAccessPoint) obj);
            }
        }
    }
    
    /**
     * Delete an access point by MAC address and version.
     * Since the table only has a partition key, this deletes the entry
     * only if the version matches.
     * 
     * @param macAddress MAC address of the access point
     * @param version Version identifier
     */
    @Override
    public void delete(String macAddress, String version) {
        if (macAddress == null || version == null) {
            return;
        }
        
        WifiAccessPoint existing = dataStore.get(macAddress);
        if (existing != null && version.equals(existing.getVersion())) {
            dataStore.remove(macAddress);
        }
    }
    
    /**
     * Find access points by geohash prefix
     * 
     * @param geohash The geohash prefix to search for
     * @return List of access points matching the geohash prefix
     */
    @Override
    public List<WifiAccessPoint> findByGeohashStartingWith(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            return Collections.emptyList();
        }
        
        return dataStore.values().stream()
                .filter(ap -> ap.getGeohash() != null && ap.getGeohash().startsWith(geohash))
                .collect(Collectors.toList());
    }
    
    /**
     * Find access points near a given coordinate, within a given distance
     * 
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @param limit Maximum number of access points to return
     * @return List of access points near the coordinate
     */
    @Override
    public List<WifiAccessPoint> findNearestByCoordinates(double latitude, double longitude, int limit) {
        // This is a simplified implementation that just sorts by distance
        return dataStore.values().stream()
                .filter(ap -> ap.getLatitude() != null && ap.getLongitude() != null)
                .sorted(Comparator.comparingDouble(ap -> 
                    calculateDistance(latitude, longitude, ap.getLatitude(), ap.getLongitude())))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Simple distance calculation using the Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // convert to meters
    }
    
    /**
     * Helper method for tests to add access points to the in-memory store.
     * Since table only has partition key, this replaces any existing entry for the MAC address.
     */
    public void addAccessPoint(WifiAccessPoint accessPoint) {
        if (accessPoint == null || accessPoint.getMacAddress() == null) {
            return;
        }
        
        dataStore.put(accessPoint.getMacAddress(), accessPoint);
    }
    
    /**
     * Helper method for tests to remove all access points
     */
    public void clearAll() {
        dataStore.clear();
    }
    
    /**
     * Loads test data for the "Single AP - Proximity Detection" scenario
     */
    public void loadProximityDetectionScenario() {
        WifiAccessPoint ap = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:01")
                .version("v1.0")
                .latitude(37.7749)
                .longitude(-122.4194)
                .altitude(10.5)
                .horizontalAccuracy(50.0)
                .verticalAccuracy(8.0)
                .confidence(0.65)
                .ssid("SingleAP_Test")
                .frequency(2437)
                .vendor("Cisco")
                .geohash("9q8yyk")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
        
        addAccessPoint(ap);
    }
    
    /**
     * Loads test data for the "Two APs - RSSI Ratio Method" scenario
     */
    public void loadRssiRatioScenario() {
        WifiAccessPoint ap1 = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:02")
                .version("v1.0")
                .latitude(37.7750)
                .longitude(-122.4195)
                .altitude(12.5)
                .horizontalAccuracy(25.0)
                .verticalAccuracy(5.0)
                .confidence(0.78)
                .ssid("DualAP_Test")
                .frequency(5180)
                .vendor("Aruba")
                .geohash("9q8yyk")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
        
        WifiAccessPoint ap2 = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:03")
                .version("v1.0")
                .latitude(37.7752)
                .longitude(-122.4198)
                .altitude(15.0)
                .horizontalAccuracy(30.0)
                .verticalAccuracy(7.0)
                .confidence(0.75)
                .ssid("DualAP_Test")
                .frequency(2437)
                .vendor("Cisco")
                .geohash("9q8yyk")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
        
        addAccessPoint(ap1);
        addAccessPoint(ap2);
    }
    
    /**
     * Loads test data for the "Three APs - Trilateration" scenario
     */
    public void loadTrilaterationScenario() {
        WifiAccessPoint ap1 = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:04")
                .version("v1.0")
                .latitude(37.7753)
                .longitude(-122.4190)
                .altitude(18.0)
                .horizontalAccuracy(20.0)
                .verticalAccuracy(5.0)
                .confidence(0.92)
                .ssid("TriAP_Test")
                .frequency(2437)
                .vendor("Meraki")
                .geohash("9q8yyk")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
        
        WifiAccessPoint ap2 = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:05")
                .version("v1.0")
                .latitude(37.7755)
                .longitude(-122.4192)
                .altitude(19.0)
                .horizontalAccuracy(18.0)
                .verticalAccuracy(4.0)
                .confidence(0.94)
                .ssid("TriAP_Test")
                .frequency(2437)
                .vendor("Meraki")
                .geohash("9q8yyk")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
                
        WifiAccessPoint ap3 = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:06")
                .version("v1.0")
                .latitude(37.7758)
                .longitude(-122.4195)
                .altitude(20.0)
                .horizontalAccuracy(15.0)
                .verticalAccuracy(3.0)
                .confidence(0.95)
                .ssid("TriAP_Test")
                .frequency(2437)
                .vendor("Meraki")
                .geohash("9q8yyk")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
                
        addAccessPoint(ap1);
        addAccessPoint(ap2);
        addAccessPoint(ap3);
    }
    
    /**
     * Loads test data for weak signals scenario
     */
    public void loadWeakSignalsScenario() {
        WifiAccessPoint ap1 = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:07")
                .version("v1.0")
                .latitude(37.7760)
                .longitude(-122.4200)
                .altitude(25.0)
                .horizontalAccuracy(40.0)
                .verticalAccuracy(10.0)
                .confidence(0.65)
                .ssid("WeakTest")
                .frequency(2437)
                .vendor("Ruckus")
                .geohash("9q8yyk")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
                
        WifiAccessPoint ap2 = WifiAccessPoint.builder()
                .macAddress("00:11:22:33:44:08")
                .version("v1.0")
                .latitude(37.7762)
                .longitude(-122.4203)
                .altitude(26.0)
                .horizontalAccuracy(45.0)
                .verticalAccuracy(12.0)
                .confidence(0.60)
                .ssid("WeakTest")
                .frequency(2437)
                .vendor("Ruckus")
                .geohash("9q8yyk")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
                
        addAccessPoint(ap1);
        addAccessPoint(ap2);
    }
    
    /**
     * Loads all test scenarios data
     */
    public void loadAllTestScenarios() {
        loadProximityDetectionScenario();
        loadRssiRatioScenario();
        loadTrilaterationScenario();
        loadWeakSignalsScenario();
    }

    /**
     * Validates table accessibility and measures response time for health checks.
     * In-memory implementation always returns healthy status with minimal latency.
     * 
     * @return HealthCheckResult containing validation results and metrics
     * @throws ResourceNotFoundException if simulating table not found scenario
     * @throws DynamoDbException if simulating connectivity issues
     * @throws Exception for simulating unexpected errors
     */
    @Override
    public HealthCheckResult validateTableHealth() throws ResourceNotFoundException, DynamoDbException, Exception {
        // Simulate response time measurement
        long startTime = System.nanoTime();
        
        // Simulate minimal processing time
        Thread.sleep(1); // 1ms delay to simulate processing
        
        long endTime = System.nanoTime();
        long responseTimeMs = (endTime - startTime) / 1_000_000L;
        
        // In-memory implementation is always healthy
        return new HealthCheckResult(
                true,
                responseTimeMs,
                "in-memory-table",
                getApproximateItemCount(),
                "In-memory table is accessible and healthy"
        );
    }

    /**
     * Gets the approximate item count from the in-memory store.
     * 
     * @return Total number of items in the in-memory store
     * @throws ResourceNotFoundException if simulating table not found scenario
     * @throws DynamoDbException if simulating connectivity issues
     * @throws Exception for simulating unexpected errors
     */
    @Override
    public long getApproximateItemCount() throws ResourceNotFoundException, DynamoDbException, Exception {
        return dataStore.size();
    }
} 