package com.wifi.positioning.dto;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data Transfer Object that holds prepared WiFi positioning data.
 * Contains scan results and access points that have been looked up and filtered.
 * 
 * <p>This record encapsulates the result of data preparation before position calculation,
 * including validation status, error messages, pre-filtered valid scans that match
 * known access points, and detailed filtering information with usage tracking.
 */
public record WifiAPData(
    List<WifiScanResult> scanResults,
    List<WifiAccessPoint> knownAccessPoints,
    List<WifiAccessPoint> validAccessPoints,
    List<WifiScanResult> validScans,
    WifiAccessPoints wifiAccessPoints,
    boolean isViable,
    String errorMessage) {
  
  /**
   * Creates a viable WifiAPData with all required data for position calculation.
   * Automatically filters scan results to only include those matching valid access points.
   *
   * @param scanResults Original WiFi scan results from the client
   * @param knownAccessPoints All access points found in the database
   * @param validAccessPoints Access points with valid status (active or warning)
   * @param wifiAccessPoints WifiAccessPoints with detailed filtering and usage tracking
   * @return A viable WifiAPData instance with pre-filtered valid scans
   */
  public static WifiAPData viable(
      List<WifiScanResult> scanResults,
      List<WifiAccessPoint> knownAccessPoints,
      List<WifiAccessPoint> validAccessPoints,
      WifiAccessPoints wifiAccessPoints) {
    
    List<WifiScanResult> validScans = filterValidScans(scanResults, validAccessPoints);
    
    return new WifiAPData(
        scanResults, 
        knownAccessPoints, 
        validAccessPoints, 
        validScans,
        wifiAccessPoints,
        true, 
        null);
  }
  
  /**
   * Creates a non-viable WifiAPData with an error message.
   * Preserves scan results, known access points, and WifiAccessPoints for diagnostic purposes.
   *
   * @param scanResults Original WiFi scan results from the client (may be null)
   * @param knownAccessPoints Access points found in database (may be null)
   * @param wifiAccessPoints WifiAccessPoints with detailed filtering information (may be null)
   * @param errorMessage The error message explaining why the data is not viable
   * @return A non-viable WifiAPData instance
   */
  public static WifiAPData notViable(
      List<WifiScanResult> scanResults,
      List<WifiAccessPoint> knownAccessPoints,
      WifiAccessPoints wifiAccessPoints,
      String errorMessage) {
    return new WifiAPData(
        scanResults, 
        knownAccessPoints, 
        null, 
        Collections.emptyList(),
        wifiAccessPoints,
        false, 
        errorMessage);
  }
  
  /**
   * Filters scan results to only include those with MAC addresses matching valid access points.
   * This filtering ensures that only scans with corresponding database entries are used for
   * position calculation.
   *
   * @param scanResults The original WiFi scan results
   * @param validAccessPoints The access points with valid status
   * @return Filtered list of scan results matching valid access points
   */
  private static List<WifiScanResult> filterValidScans(
      List<WifiScanResult> scanResults,
      List<WifiAccessPoint> validAccessPoints) {
    
    Set<String> validMacAddresses = validAccessPoints.stream()
        .map(WifiAccessPoint::getMacAddress)
        .collect(Collectors.toSet());
    
    return scanResults.stream()
        .filter(scan -> validMacAddresses.contains(scan.macAddress()))
        .toList();
  }
}
