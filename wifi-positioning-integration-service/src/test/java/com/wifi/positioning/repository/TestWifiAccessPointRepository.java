package com.wifi.positioning.repository;

import com.wifi.positioning.dto.WifiAccessPoint;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extended repository interface for testing purposes.
 * Adds methods needed for tests without modifying the main repository interface.
 */
public interface TestWifiAccessPointRepository extends WifiAccessPointRepository {

    /**
     * Find an access point by MAC address and version
     * 
     * @param macAddress MAC address of the access point
     * @param version Version identifier
     * @return Optional containing the access point if found
     */
    Optional<WifiAccessPoint> findByMacAddressAndVersion(String macAddress, String version);
    
    /**
     * Save an access point to the repository
     * 
     * @param accessPoint The access point to save
     */
    void save(WifiAccessPoint accessPoint);
    
    /**
     * Batch save multiple access points
     * 
     * @param accessPoints List of access points to save
     */
    <T> void batchSave(List<T> accessPoints);
    
    /**
     * Delete an access point by MAC address and version
     * 
     * @param macAddress MAC address of the access point
     * @param version Version identifier
     */
    void delete(String macAddress, String version);
    
    /**
     * Find access points by geohash prefix
     * 
     * @param geohash The geohash prefix to search for
     * @return List of access points matching the geohash prefix
     */
    List<WifiAccessPoint> findByGeohashStartingWith(String geohash);
    
    /**
     * Find access points near a given coordinate, within a given distance
     * 
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @param limit Maximum number of access points to return
     * @return List of access points near the coordinate
     */
    List<WifiAccessPoint> findNearestByCoordinates(double latitude, double longitude, int limit);
} 