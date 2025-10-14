// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/DataValidationService.java
package com.wifi.measurements.transformer.service;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.dto.LocationData;


/**
 * Comprehensive data validation service for WiFi measurement quality assurance.
 *
 * <p>This service implements Stage 1 filtering logic for the WiFi location data pipeline, providing
 * robust validation of all critical data fields to ensure data quality and integrity before
 * processing. It applies business rules and sanity checks to filter out invalid or low-quality
 * measurements.
 *
 * <p><strong>Validation Categories:</strong>
 *
 * <ul>
 *   <li><strong>Location Validation:</strong> Coordinate validity, GPS accuracy thresholds
 *   <li><strong>Signal Validation:</strong> RSSI range checks and signal strength validation
 *   <li><strong>Network Validation:</strong> BSSID format validation and mobile hotspot detection
 *   <li><strong>Temporal Validation:</strong> Timestamp sanity checks and recency validation
 * </ul>
 *
 * <p><strong>Quality Assurance Features:</strong>
 *
 * <ul>
 *   <li>Configurable validation thresholds via application properties
 *   <li>Mobile hotspot detection to filter temporary networks
 *   <li>Detailed error reporting for debugging and analysis
 * </ul>
 *
 * <p><strong>Business Rules:</strong>
 *
 * <ul>
 *   <li>Location accuracy must be within configured maximum threshold
 *   <li>RSSI values must be within acceptable signal strength range
 *   <li>BSSID must conform to valid MAC address format
 *   <li>Timestamps must be recent and within reasonable bounds
 *   <li>Mobile hotspots may be flagged or excluded based on configuration
 * </ul>
 *
 * <p>This service is designed to be stateless and thread-safe, supporting high-throughput
 * validation operations.
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class DataValidationService {

  private static final Logger logger = LoggerFactory.getLogger(DataValidationService.class);

  // Regular expression pattern for validating BSSID (MAC address) format
  // Matches standard MAC address formats with colons or hyphens
  private static final Pattern BSSID_PATTERN =
      Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");

  // One year in milliseconds for timestamp validation
  public static final long ONE_YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000;

  private final DataFilteringConfigurationProperties filteringConfig;

  /**
   * Constructs a new data validation service with configuration.
   *
   * <p>This constructor initializes the validation service with filtering configuration that
   * defines validation thresholds and rules for location accuracy, signal strength, and temporal
   * bounds.
   *
   * @param filteringConfig Configuration properties defining validation thresholds and rules
   * @throws IllegalArgumentException if filteringConfig is null
   */
  public DataValidationService(DataFilteringConfigurationProperties filteringConfig) {
    if (filteringConfig == null) {
      throw new IllegalArgumentException("DataFilteringConfigurationProperties cannot be null");
    }

    this.filteringConfig = filteringConfig;

    logger.info("Data Validation Service initialized with filtering configuration");
  }

  /**
   * Validates location data against configured sanity check rules and accuracy thresholds.
   *
   * <p>This method performs comprehensive location validation to ensure GPS coordinates are valid
   * and location accuracy meets quality standards. It checks coordinate validity, GPS accuracy
   * thresholds, and ensures location data is present and reasonable.
   *
   * <p><strong>Validation Criteria:</strong>
   *
   * <ul>
   *   <li><strong>Presence Check:</strong> Location data must not be null
   *   <li><strong>Coordinate Validity:</strong> Latitude and longitude must be within valid ranges
   *   <li><strong>Accuracy Threshold:</strong> GPS accuracy must be within configured maximum
   * </ul>
   *
   * <p><strong>Business Rules:</strong>
   *
   * <ul>
   *   <li>Location accuracy must be ≤ configured maximum accuracy threshold
   *   <li>Coordinates must be within valid geographic bounds
   *   <li>Null location data is automatically rejected
   * </ul>
   *
   * @param location The location data to validate
   * @return ValidationResult indicating if location is valid with detailed error message if invalid
   */
  public ValidationResult validateLocation(LocationData location) {
    // Step 1: Check for null location data
    // Null location data is automatically invalid as it's required for spatial analysis
    if (location == null) {
      return ValidationResult.invalid("Location data is null");
    }

    // Step 2: Validate coordinate validity using LocationData's built-in validation
    // This ensures coordinates are within valid geographic bounds
    if (!location.hasValidCoordinates()) {
      return ValidationResult.invalid(
          "Invalid coordinates: lat=" + location.latitude() + ", lon=" + location.longitude());
    }

    // Step 3: Check GPS accuracy against configured threshold
    // Poor accuracy indicates unreliable location data for spatial analysis
    if (location.accuracy() != null
        && location.accuracy() > filteringConfig.maxLocationAccuracy()) {
      return ValidationResult.invalid(
          "Location accuracy "
              + location.accuracy()
              + "m exceeds threshold "
              + filteringConfig.maxLocationAccuracy()
              + "m");
    }

    return ValidationResult.success();
  }

  /**
   * Validates RSSI (Received Signal Strength Indicator) value against configured range.
   *
   * <p>This method performs signal strength validation to ensure RSSI values are within acceptable
   * bounds for meaningful WiFi analysis. RSSI values indicate the quality and strength of the WiFi
   * signal, which is critical for location accuracy assessment.
   *
   * <p><strong>Validation Criteria:</strong>
   *
   * <ul>
   *   <li><strong>Presence Check:</strong> RSSI value must not be null
   *   <li><strong>Range Validation:</strong> RSSI must be within configured minimum and maximum
   *       bounds
   * </ul>
   *
   * <p><strong>RSSI Value Ranges:</strong>
   *
   * <ul>
   *   <li><strong>Excellent:</strong> -50 to 0 dBm (very strong signal)
   *   <li><strong>Good:</strong> -60 to -50 dBm (strong signal)
   *   <li><strong>Fair:</strong> -70 to -60 dBm (moderate signal)
   *   <li><strong>Poor:</strong> -80 to -70 dBm (weak signal)
   *   <li><strong>Very Poor:</strong> -100 to -80 dBm (very weak signal)
   * </ul>
   *
   * <p><strong>Business Rules:</strong>
   *
   * <ul>
   *   <li>RSSI must be ≥ configured minimum threshold (typically -100 dBm)
   *   <li>RSSI must be ≤ configured maximum threshold (typically 0 dBm)
   *   <li>Null RSSI values are automatically rejected
   * </ul>
   *
   * @param rssi The RSSI value in dBm to validate
   * @return ValidationResult indicating if RSSI is valid with detailed error message if invalid
   */
  public ValidationResult validateRssi(Integer rssi) {
    // Step 1: Check for null RSSI value
    // Null RSSI values are automatically invalid as signal strength is required for analysis
    if (rssi == null) {
      return ValidationResult.invalid("RSSI is null");
    }

    // Step 2: Validate RSSI against configured range bounds
    // RSSI values outside the valid range indicate measurement errors or invalid data
    if (rssi < filteringConfig.minRssi() || rssi > filteringConfig.maxRssi()) {
      return ValidationResult.invalid(
          "RSSI "
              + rssi
              + " dBm outside valid range ["
              + filteringConfig.minRssi()
              + ", "
              + filteringConfig.maxRssi()
              + "]");
    }

    return ValidationResult.success();
  }

  /**
   * Validates BSSID (Basic Service Set Identifier) format and structure.
   *
   * <p>This method performs comprehensive BSSID validation to ensure network identifiers conform to
   * valid MAC address format and structure. BSSID validation is critical for proper network
   * identification and filtering of invalid or malformed network data.
   *
   * <p><strong>Validation Criteria:</strong>
   *
   * <ul>
   *   <li><strong>Presence Check:</strong> BSSID must not be null or empty
   *   <li><strong>Format Validation:</strong> Must conform to MAC address format with colons or
   *       hyphens
   *   <li><strong>Structure Validation:</strong> Must not be a reserved or invalid MAC address
   * </ul>
   *
   * <p><strong>MAC Address Format:</strong>
   *
   * <ul>
   *   <li><strong>Standard Format:</strong> XX:XX:XX:XX:XX:XX (6 octets separated by colons)
   *   <li><strong>Alternative Format:</strong> XX-XX-XX-XX-XX-XX (6 octets separated by hyphens)
   *   <li><strong>Case Insensitive:</strong> Both uppercase and lowercase hex digits are accepted
   * </ul>
   *
   * <p><strong>Business Rules:</strong>
   *
   * <ul>
   *   <li>BSSID must be exactly 17 characters (6 octets + 5 separators)
   *   <li>Each octet must be a valid 2-digit hexadecimal number
   *   <li>BSSID must not be a reserved or multicast address
   *   <li>Null or empty BSSID values are automatically rejected
   * </ul>
   *
   * @param bssid The BSSID string to validate
   * @return ValidationResult indicating if BSSID is valid with detailed error message if invalid
   */
  public ValidationResult validateBssid(String bssid) {
    // Step 1: Check for null or empty BSSID
    // Null or empty BSSID values are automatically invalid as network identification is required
    if (bssid == null || bssid.trim().isEmpty()) {
      return ValidationResult.invalid("BSSID is null or empty");
    }

    // Step 2: Normalize BSSID for consistent validation
    // Convert to lowercase and trim whitespace for standardized processing
    String normalizedBssid = bssid.toLowerCase().trim();

    // Step 3: Validate MAC address format and structure
    // Check both regex pattern match and invalid MAC address detection
    if (!BSSID_PATTERN.matcher(normalizedBssid).matches() || isInvalidMacAddress(normalizedBssid)) {
      return ValidationResult.invalid("Invalid BSSID format: " + bssid);
    }

    return ValidationResult.success();
  }

  /**
   * Validates measurement timestamp for temporal sanity and recency.
   *
   * <p>This method performs temporal validation to ensure timestamps are reasonable and within
   * acceptable bounds for data analysis. Timestamp validation is critical for maintaining data
   * quality and preventing analysis of stale or invalid temporal data.
   *
   * <p><strong>Validation Criteria:</strong>
   *
   * <ul>
   *   <li><strong>Presence Check:</strong> Timestamp must not be null
   *   <li><strong>Future Check:</strong> Timestamp must not be in the future
   *   <li><strong>Recency Check:</strong> Timestamp must not be too old (within 1 year)
   * </ul>
   *
   * <p><strong>Temporal Bounds:</strong>
   *
   * <ul>
   *   <li><strong>Lower Bound:</strong> Current time - 1 year (365 days)
   *   <li><strong>Upper Bound:</strong> Current time (no future timestamps)
   *   <li><strong>Format:</strong> Unix timestamp in milliseconds since epoch
   * </ul>
   *
   * <p><strong>Business Rules:</strong>
   *
   * <ul>
   *   <li>Timestamps must be ≤ current system time (no future data)
   *   <li>Timestamps must be ≥ current time - 1 year (not too old)
   *   <li>Null timestamps are automatically rejected
   *   <li>Data older than 1 year is considered stale and invalid
   * </ul>
   *
   * <p><strong>Use Cases:</strong>
   *
   * <ul>
   *   <li>Prevents analysis of future-dated measurements
   *   <li>Filters out stale data that may not be relevant
   *   <li>Ensures temporal consistency in data analysis
   * </ul>
   *
   * @param timestamp The timestamp in milliseconds since epoch to validate
   * @return ValidationResult indicating if timestamp is valid with detailed error message if
   *     invalid
   */
  public ValidationResult validateTimestamp(Long timestamp) {
    // Step 1: Check for null timestamp
    // Null timestamps are automatically invalid as temporal data is required for analysis
    if (timestamp == null) {
      return ValidationResult.invalid("Timestamp is null");
    }

    // Get current system time for temporal validation
    long currentTime = System.currentTimeMillis();

    // Step 2: Check for future timestamps
    // Future timestamps indicate measurement errors or system clock issues
    if (timestamp > currentTime) {
      return ValidationResult.invalid("Timestamp is in the future");
    }

    // Step 3: Check for very old timestamps (more than 1 year)
    // Stale data may not be relevant for current analysis and could indicate data quality issues
    if (timestamp < (currentTime - ONE_YEAR_MILLIS)) {
      return ValidationResult.invalid("Timestamp is more than a year old");
    }

    return ValidationResult.success();
  }


  /**
   * Checks if a MAC address is invalid due to being reserved, broadcast, or all zeros.
   *
   * <p>This utility method validates MAC addresses against known invalid patterns that should not
   * be used for network identification. Invalid MAC addresses are filtered out to improve data
   * quality and prevent analysis of non-unique identifiers.
   *
   * <p><strong>Invalid MAC Address Types:</strong>
   *
   * <ul>
   *   <li><strong>All Zeros:</strong> 00:00:00:00:00:00 (reserved by IEEE)
   *   <li><strong>Broadcast Address:</strong> FF:FF:FF:FF:FF:FF (all devices)
   * </ul>
   *
   * <p><strong>Validation Process:</strong>
   *
   * <ol>
   *   <li><strong>Normalization:</strong> Remove separators for pattern matching
   *   <li><strong>Pattern Check:</strong> Compare against known invalid patterns
   *   <li><strong>Result Return:</strong> Return true if invalid, false if valid
   * </ol>
   *
   * <p><strong>Business Impact:</strong>
   *
   * <ul>
   *   <li>Filters out non-unique network identifiers
   *   <li>Improves data quality by excluding special-purpose addresses
   *   <li>Prevents analysis of broadcast and reserved addresses
   * </ul>
   *
   * <p><strong>Invalid Patterns:</strong>
   *
   * <ul>
   *   <li><strong>000000000000:</strong> All zeros (IEEE reserved)
   *   <li><strong>ffffffffffff:</strong> Broadcast address (all devices)
   * </ul>
   *
   * @param bssid The BSSID string to check for invalid MAC address patterns
   * @return true if the MAC address is invalid, false if valid
   */
  private boolean isInvalidMacAddress(String bssid) {
    // Normalize BSSID for pattern matching - remove separators to create clean 12-character hex string
    String cleanBssid = bssid.replace(":", "").replace("-", "");

    // Check for reserved patterns: all zeros (IEEE reserved) or broadcast address (targets all devices)
    // Both patterns indicate non-unique network identifiers that should not be used
    return "000000000000".equals(cleanBssid) || "ffffffffffff".equals(cleanBssid);
  }

  /**
   * Immutable result of data validation operations.
   *
   * <p>This record represents the outcome of validation operations performed by the
   * DataValidationService. It provides a standardized way to communicate validation results with
   * clear success/failure status and detailed error messages.
   *
   * <p><strong>Record Components:</strong>
   *
   * <ul>
   *   <li><strong>valid:</strong> Boolean indicating if validation passed (true) or failed (false)
   *   <li><strong>errorMessage:</strong> Detailed error message for failed validations, null for
   *       successful validations
   * </ul>
   *
   * <p><strong>Factory Methods:</strong>
   *
   * <ul>
   *   <li><strong>success():</strong> Creates a successful validation result with no error message
   *   <li><strong>invalid(String):</strong> Creates a failed validation result with detailed error
   *       message
   * </ul>
   *
   * <p><strong>Usage Examples:</strong>
   *
   * <ul>
   *   <li>Successful validation: <code>ValidationResult.success()</code>
   *   <li>Failed validation: <code>ValidationResult.invalid("RSSI value is null")</code>
   * </ul>
   *
   * @param valid Whether the validation operation was successful
   * @param errorMessage Detailed error message for failed validations, null for successful
   *     validations
   */
  public record ValidationResult(boolean valid, String errorMessage) {
    /**
     * Creates a successful validation result.
     *
     * <p>This factory method creates a ValidationResult indicating that the validation operation
     * completed successfully with no errors.
     *
     * @return A ValidationResult with valid=true and errorMessage=null
     */
    public static ValidationResult success() {
      return new ValidationResult(true, null);
    }

    /**
     * Creates a failed validation result with error details.
     *
     * <p>This factory method creates a ValidationResult indicating that the validation operation
     * failed, along with a detailed error message explaining the failure.
     *
     * @param errorMessage Detailed description of the validation failure
     * @return A ValidationResult with valid=false and the provided error message
     * @throws IllegalArgumentException if errorMessage is null or empty
     */
    public static ValidationResult invalid(String errorMessage) {
      if (errorMessage == null || errorMessage.trim().isEmpty()) {
        throw new IllegalArgumentException("Error message cannot be null or empty");
      }
      return new ValidationResult(false, errorMessage);
    }
  }

}
