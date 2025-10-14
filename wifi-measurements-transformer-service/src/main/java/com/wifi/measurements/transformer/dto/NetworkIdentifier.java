// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/dto/NetworkIdentifier.java
package com.wifi.measurements.transformer.dto;

/**
 * Lightweight record containing network identification data for validation and filtering.
 *
 * <p>This record serves as a data transfer object for services that need to validate or
 * classify network data without requiring a full {@link WifiMeasurement} object. It contains
 * the minimal set of fields needed for:
 *
 * <ul>
 *   <li><strong>Mobile Hotspot Detection:</strong> BSSID (OUI) and SSID pattern matching
 *   <li><strong>Network Validation:</strong> BSSID format and SSID sanity checks
 *   <li><strong>Early Filtering:</strong> Pre-measurement creation filtering to improve efficiency
 * </ul>
 *
 * <h2>Use Cases</h2>
 *
 * <p><strong>Transformation Services:</strong> Filter out mobile hotspots before creating
 * expensive {@link WifiMeasurement} objects
 *
 * <p><strong>Validation Services:</strong> Validate network identifiers without full context
 *
 * <p><strong>Detection Services:</strong> Classify networks based on BSSID and SSID patterns
 *
 * <h2>Design Rationale</h2>
 *
 * <p>This record enables early filtering in the transformation pipeline, preventing the creation
 * of measurement objects that would be immediately discarded. This improves performance by:
 *
 * <ul>
 *   <li>Reducing object allocation for filtered data
 *   <li>Avoiding expensive builder operations
 *   <li>Filtering before JSON serialization overhead
 * </ul>
 *
 * @param bssid The Basic Service Set Identifier (MAC address) of the WiFi network
 * @param ssid The Service Set Identifier (network name) of the WiFi network
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @see MobileHotspotDetectionService
 * @see WifiMeasurement
 * @since 2024
 */
public record NetworkIdentifier(String bssid, String ssid) {

  /**
   * Creates a NetworkIdentifier with only BSSID (no SSID).
   *
   * <p>Useful for OUI-based detection where SSID is not available or needed.
   *
   * @param bssid The BSSID to create identifier from
   * @return A NetworkIdentifier with the given BSSID and null SSID
   */
  public static NetworkIdentifier fromBssid(String bssid) {
    return new NetworkIdentifier(bssid, null);
  }

  /**
   * Checks if this network identifier has a valid BSSID.
   *
   * @return true if BSSID is non-null and non-empty
   */
  public boolean hasBssid() {
    return bssid != null && !bssid.isEmpty();
  }

  /**
   * Checks if this network identifier has a valid SSID.
   *
   * @return true if SSID is non-null and non-empty
   */
  public boolean hasSsid() {
    return ssid != null && !ssid.isEmpty();
  }

  public static NetworkIdentifier from(String bssid2, String ssid2) {
    return new NetworkIdentifier(bssid2, ssid2);
}


}

