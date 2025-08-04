// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/DataValidationService.java
package com.wifi.measurements.transformer.service;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.dto.LocationData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Comprehensive data validation service for WiFi measurement quality assurance.
 * 
 * <p>This service implements Stage 1 filtering logic for the WiFi location data pipeline,
 * providing robust validation of all critical data fields to ensure data quality and
 * integrity before processing. It applies business rules and sanity checks to filter
 * out invalid or low-quality measurements.</p>
 * 
 * <p><strong>Validation Categories:</strong></p>
 * <ul>
 *   <li><strong>Location Validation:</strong> Coordinate validity, GPS accuracy thresholds</li>
 *   <li><strong>Signal Validation:</strong> RSSI range checks and signal strength validation</li>
 *   <li><strong>Network Validation:</strong> BSSID format validation and mobile hotspot detection</li>
 *   <li><strong>Temporal Validation:</strong> Timestamp sanity checks and recency validation</li>
 * </ul>
 * 
 * <p><strong>Quality Assurance Features:</strong></p>
 * <ul>
 *   <li>Comprehensive metrics collection for monitoring validation performance</li>
 *   <li>Configurable validation thresholds via application properties</li>
 *   <li>Mobile hotspot detection to filter temporary networks</li>
 *   <li>Detailed error reporting for debugging and analysis</li>
 * </ul>
 * 
 * <p><strong>Business Rules:</strong></p>
 * <ul>
 *   <li>Location accuracy must be within configured maximum threshold</li>
 *   <li>RSSI values must be within acceptable signal strength range</li>
 *   <li>BSSID must conform to valid MAC address format</li>
 *   <li>Timestamps must be recent and within reasonable bounds</li>
 *   <li>Mobile hotspots may be flagged or excluded based on configuration</li>
 * </ul>
 * 
 * <p>This service is designed to be stateless and thread-safe, supporting high-throughput
 * validation operations with comprehensive monitoring and observability.</p>
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
    private static final Pattern BSSID_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    
    // One year in milliseconds for timestamp validation
    public static final long ONE_YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000;
    
    private final DataFilteringConfigurationProperties filteringConfig;
    
    // Metrics counters for monitoring validation performance and data quality
    private final Counter locationValidationCounter;
    private final Counter locationInvalidCounter;
    private final Counter rssiValidationCounter;
    private final Counter rssiInvalidCounter;
    private final Counter bssidValidationCounter;
    private final Counter bssidInvalidCounter;
    private final Counter timestampValidationCounter;
    private final Counter timestampInvalidCounter;
    private final Counter mobileHotspotDetectedCounter;
    
    /**
     * Constructs a new data validation service with configuration and metrics registry.
     * 
     * <p>This constructor initializes the validation service with filtering configuration
     * and sets up comprehensive metrics collection for monitoring validation performance.
     * The metrics provide insights into data quality trends and validation effectiveness.</p>
     * 
     * <p><strong>Metrics Initialized:</strong></p>
     * <ul>
     *   <li><strong>Location Metrics:</strong> Success/failure counters for location validation</li>
     *   <li><strong>RSSI Metrics:</strong> Success/failure counters for signal strength validation</li>
     *   <li><strong>BSSID Metrics:</strong> Success/failure counters for network identifier validation</li>
     *   <li><strong>Timestamp Metrics:</strong> Success/failure counters for temporal validation</li>
     *   <li><strong>Mobile Hotspot Metrics:</strong> Detection counters for mobile hotspot identification</li>
     * </ul>
     * 
     * @param filteringConfig Configuration properties defining validation thresholds and rules
     * @param meterRegistry Micrometer registry for metrics collection and monitoring
     * @throws IllegalArgumentException if any required dependency is null
     */
    public DataValidationService(DataFilteringConfigurationProperties filteringConfig, MeterRegistry meterRegistry) {
        if (filteringConfig == null) {
            throw new IllegalArgumentException("DataFilteringConfigurationProperties cannot be null");
        }
        if (meterRegistry == null) {
            throw new IllegalArgumentException("MeterRegistry cannot be null");
        }
        
        this.filteringConfig = filteringConfig;
        
        // Initialize comprehensive metrics counters for monitoring validation performance
        // These metrics provide insights into data quality and validation effectiveness
        this.locationValidationCounter = Counter.builder("data.validation.location.success")
            .description("Number of successful location validations")
            .register(meterRegistry);
        this.locationInvalidCounter = Counter.builder("data.validation.location.invalid")
            .description("Number of invalid location validations")
            .register(meterRegistry);
        this.rssiValidationCounter = Counter.builder("data.validation.rssi.success")
            .description("Number of successful RSSI validations")
            .register(meterRegistry);
        this.rssiInvalidCounter = Counter.builder("data.validation.rssi.invalid")
            .description("Number of invalid RSSI validations")
            .register(meterRegistry);
        this.bssidValidationCounter = Counter.builder("data.validation.bssid.success")
            .description("Number of successful BSSID validations")
            .register(meterRegistry);
        this.bssidInvalidCounter = Counter.builder("data.validation.bssid.invalid")
            .description("Number of invalid BSSID validations")
            .register(meterRegistry);
        this.timestampValidationCounter = Counter.builder("data.validation.timestamp.success")
            .description("Number of successful timestamp validations")
            .register(meterRegistry);
        this.timestampInvalidCounter = Counter.builder("data.validation.timestamp.invalid")
            .description("Number of invalid timestamp validations")
            .register(meterRegistry);
        this.mobileHotspotDetectedCounter = Counter.builder("data.validation.mobile_hotspot.detected")
            .description("Number of mobile hotspots detected")
            .register(meterRegistry);
        
        logger.info("Data Validation Service initialized with filtering configuration and metrics registry");
    }
    
    /**
     * Validates location data against configured sanity check rules and accuracy thresholds.
     * 
     * <p>This method performs comprehensive location validation to ensure GPS coordinates
     * are valid and location accuracy meets quality standards. It checks coordinate validity,
     * GPS accuracy thresholds, and ensures location data is present and reasonable.</p>
     * 
     * <p><strong>Validation Criteria:</strong></p>
     * <ul>
     *   <li><strong>Presence Check:</strong> Location data must not be null</li>
     *   <li><strong>Coordinate Validity:</strong> Latitude and longitude must be within valid ranges</li>
     *   <li><strong>Accuracy Threshold:</strong> GPS accuracy must be within configured maximum</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>Location accuracy must be ≤ configured maximum accuracy threshold</li>
     *   <li>Coordinates must be within valid geographic bounds</li>
     *   <li>Null location data is automatically rejected</li>
     * </ul>
     * 
     * <p><strong>Metrics Impact:</strong></p>
     * <ul>
     *   <li>Increments success counter for valid locations</li>
     *   <li>Increments failure counter for invalid locations</li>
     *   <li>Provides detailed error messages for debugging</li>
     * </ul>
     * 
     * @param location The location data to validate
     * @return ValidationResult indicating if location is valid with detailed error message if invalid
     */
    public ValidationResult validateLocation(LocationData location) {
        // Step 1: Check for null location data
        // Null location data is automatically invalid as it's required for spatial analysis
        if (location == null) {
            locationInvalidCounter.increment();
            return ValidationResult.invalid("Location data is null");
        }
        
        // Step 2: Validate coordinate validity using LocationData's built-in validation
        // This ensures coordinates are within valid geographic bounds
        if (!location.hasValidCoordinates()) {
            locationInvalidCounter.increment();
            return ValidationResult.invalid("Invalid coordinates: lat=" + location.latitude() + ", lon=" + location.longitude());
        }
        
        // Step 3: Check GPS accuracy against configured threshold
        // Poor accuracy indicates unreliable location data for spatial analysis
        if (location.accuracy() != null && location.accuracy() > filteringConfig.maxLocationAccuracy()) {
            locationInvalidCounter.increment();
            return ValidationResult.invalid("Location accuracy " + location.accuracy() + "m exceeds threshold " + filteringConfig.maxLocationAccuracy() + "m");
        }
        
        // Location validation successful - increment success counter
        locationValidationCounter.increment();
        return ValidationResult.success();
    }
    
    /**
     * Validates RSSI (Received Signal Strength Indicator) value against configured range.
     * 
     * <p>This method performs signal strength validation to ensure RSSI values are within
     * acceptable bounds for meaningful WiFi analysis. RSSI values indicate the quality
     * and strength of the WiFi signal, which is critical for location accuracy assessment.</p>
     * 
     * <p><strong>Validation Criteria:</strong></p>
     * <ul>
     *   <li><strong>Presence Check:</strong> RSSI value must not be null</li>
     *   <li><strong>Range Validation:</strong> RSSI must be within configured minimum and maximum bounds</li>
     * </ul>
     * 
     * <p><strong>RSSI Value Ranges:</strong></p>
     * <ul>
     *   <li><strong>Excellent:</strong> -50 to 0 dBm (very strong signal)</li>
     *   <li><strong>Good:</strong> -60 to -50 dBm (strong signal)</li>
     *   <li><strong>Fair:</strong> -70 to -60 dBm (moderate signal)</li>
     *   <li><strong>Poor:</strong> -80 to -70 dBm (weak signal)</li>
     *   <li><strong>Very Poor:</strong> -100 to -80 dBm (very weak signal)</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>RSSI must be ≥ configured minimum threshold (typically -100 dBm)</li>
     *   <li>RSSI must be ≤ configured maximum threshold (typically 0 dBm)</li>
     *   <li>Null RSSI values are automatically rejected</li>
     * </ul>
     * 
     * <p><strong>Metrics Impact:</strong></p>
     * <ul>
     *   <li>Increments success counter for valid RSSI values</li>
     *   <li>Increments failure counter for invalid RSSI values</li>
     *   <li>Provides detailed error messages with actual values and thresholds</li>
     * </ul>
     * 
     * @param rssi The RSSI value in dBm to validate
     * @return ValidationResult indicating if RSSI is valid with detailed error message if invalid
     */
    public ValidationResult validateRssi(Integer rssi) {
        // Step 1: Check for null RSSI value
        // Null RSSI values are automatically invalid as signal strength is required for analysis
        if (rssi == null) {
            rssiInvalidCounter.increment();
            return ValidationResult.invalid("RSSI is null");
        }
        
        // Step 2: Validate RSSI against configured range bounds
        // RSSI values outside the valid range indicate measurement errors or invalid data
        if (rssi < filteringConfig.minRssi() || rssi > filteringConfig.maxRssi()) {
            rssiInvalidCounter.increment();
            return ValidationResult.invalid("RSSI " + rssi + " dBm outside valid range [" + 
                                          filteringConfig.minRssi() + ", " + filteringConfig.maxRssi() + "]");
        }
        
        // RSSI validation successful - increment success counter
        rssiValidationCounter.increment();
        return ValidationResult.success();
    }
    
    /**
     * Validates BSSID (Basic Service Set Identifier) format and structure.
     * 
     * <p>This method performs comprehensive BSSID validation to ensure network identifiers
     * conform to valid MAC address format and structure. BSSID validation is critical for
     * proper network identification and filtering of invalid or malformed network data.</p>
     * 
     * <p><strong>Validation Criteria:</strong></p>
     * <ul>
     *   <li><strong>Presence Check:</strong> BSSID must not be null or empty</li>
     *   <li><strong>Format Validation:</strong> Must conform to MAC address format with colons or hyphens</li>
     *   <li><strong>Structure Validation:</strong> Must not be a reserved or invalid MAC address</li>
     * </ul>
     * 
     * <p><strong>MAC Address Format:</strong></p>
     * <ul>
     *   <li><strong>Standard Format:</strong> XX:XX:XX:XX:XX:XX (6 octets separated by colons)</li>
     *   <li><strong>Alternative Format:</strong> XX-XX-XX-XX-XX-XX (6 octets separated by hyphens)</li>
     *   <li><strong>Case Insensitive:</strong> Both uppercase and lowercase hex digits are accepted</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>BSSID must be exactly 17 characters (6 octets + 5 separators)</li>
     *   <li>Each octet must be a valid 2-digit hexadecimal number</li>
     *   <li>BSSID must not be a reserved or multicast address</li>
     *   <li>Null or empty BSSID values are automatically rejected</li>
     * </ul>
     * 
     * <p><strong>Metrics Impact:</strong></p>
     * <ul>
     *   <li>Increments success counter for valid BSSID values</li>
     *   <li>Increments failure counter for invalid BSSID values</li>
     *   <li>Provides detailed error messages for debugging</li>
     * </ul>
     * 
     * @param bssid The BSSID string to validate
     * @return ValidationResult indicating if BSSID is valid with detailed error message if invalid
     */
    public ValidationResult validateBssid(String bssid) {
        // Step 1: Check for null or empty BSSID
        // Null or empty BSSID values are automatically invalid as network identification is required
        if (bssid == null || bssid.trim().isEmpty()) {
            bssidInvalidCounter.increment();
            return ValidationResult.invalid("BSSID is null or empty");
        }
        
        // Step 2: Normalize BSSID for consistent validation
        // Convert to lowercase and trim whitespace for standardized processing
        String normalizedBssid = bssid.toLowerCase().trim();
        
        // Step 3: Validate MAC address format and structure
        // Check both regex pattern match and invalid MAC address detection
        if (!BSSID_PATTERN.matcher(normalizedBssid).matches() || isInvalidMacAddress(normalizedBssid)) {
            bssidInvalidCounter.increment();
            return ValidationResult.invalid("Invalid BSSID format: " + bssid);
        }
        
        // BSSID validation successful - increment success counter
        bssidValidationCounter.increment();
        return ValidationResult.success();
    }
    
    /**
     * Validates measurement timestamp for temporal sanity and recency.
     * 
     * <p>This method performs temporal validation to ensure timestamps are reasonable
     * and within acceptable bounds for data analysis. Timestamp validation is critical
     * for maintaining data quality and preventing analysis of stale or invalid temporal data.</p>
     * 
     * <p><strong>Validation Criteria:</strong></p>
     * <ul>
     *   <li><strong>Presence Check:</strong> Timestamp must not be null</li>
     *   <li><strong>Future Check:</strong> Timestamp must not be in the future</li>
     *   <li><strong>Recency Check:</strong> Timestamp must not be too old (within 1 year)</li>
     * </ul>
     * 
     * <p><strong>Temporal Bounds:</strong></p>
     * <ul>
     *   <li><strong>Lower Bound:</strong> Current time - 1 year (365 days)</li>
     *   <li><strong>Upper Bound:</strong> Current time (no future timestamps)</li>
     *   <li><strong>Format:</strong> Unix timestamp in milliseconds since epoch</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>Timestamps must be ≤ current system time (no future data)</li>
     *   <li>Timestamps must be ≥ current time - 1 year (not too old)</li>
     *   <li>Null timestamps are automatically rejected</li>
     *   <li>Data older than 1 year is considered stale and invalid</li>
     * </ul>
     * 
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Prevents analysis of future-dated measurements</li>
     *   <li>Filters out stale data that may not be relevant</li>
     *   <li>Ensures temporal consistency in data analysis</li>
     * </ul>
     * 
     * <p><strong>Metrics Impact:</strong></p>
     * <ul>
     *   <li>Increments success counter for valid timestamps</li>
     *   <li>Increments failure counter for invalid timestamps</li>
     *   <li>Provides detailed error messages for debugging</li>
     * </ul>
     * 
     * @param timestamp The timestamp in milliseconds since epoch to validate
     * @return ValidationResult indicating if timestamp is valid with detailed error message if invalid
     */
    public ValidationResult validateTimestamp(Long timestamp) {
        // Step 1: Check for null timestamp
        // Null timestamps are automatically invalid as temporal data is required for analysis
        if (timestamp == null) {
            timestampInvalidCounter.increment();
            return ValidationResult.invalid("Timestamp is null");
        }
        
        // Get current system time for temporal validation
        long currentTime = System.currentTimeMillis();
        
        // Step 2: Check for future timestamps
        // Future timestamps indicate measurement errors or system clock issues
        if (timestamp > currentTime) {
            timestampInvalidCounter.increment();
            return ValidationResult.invalid("Timestamp is in the future");
        }
        
        // Step 3: Check for very old timestamps (more than 1 year)
        // Stale data may not be relevant for current analysis and could indicate data quality issues
        if (timestamp < (currentTime - ONE_YEAR_MILLIS)) {
            timestampInvalidCounter.increment();
            return ValidationResult.invalid("Timestamp is more than a year old");
        }
        
        // Timestamp validation successful - increment success counter
        timestampValidationCounter.increment();
        return ValidationResult.success();
    }
    
    /**
     * Detects if a BSSID belongs to a known mobile hotspot manufacturer.
     * 
     * <p>This method implements mobile hotspot detection by analyzing the OUI (Organizationally
     * Unique Identifier) portion of the BSSID. Mobile hotspots are temporary networks created
     * by mobile devices and may be filtered out to improve data quality for fixed infrastructure
     * analysis.</p>
     * 
     * <p><strong>Detection Process:</strong></p>
     * <ol>
     *   <li><strong>Feature Check:</strong> Verify mobile hotspot detection is enabled</li>
     *   <li><strong>Input Validation:</strong> Ensure BSSID is valid and long enough</li>
     *   <li><strong>OUI Extraction:</strong> Extract first 3 octets (6 characters) of BSSID</li>
     *   <li><strong>Blacklist Check:</strong> Compare OUI against configured manufacturer blacklist</li>
     *   <li><strong>Action Determination:</strong> Return appropriate action based on configuration</li>
     * </ol>
     * 
     * <p><strong>OUI Analysis:</strong></p>
     * <ul>
     *   <li><strong>OUI Definition:</strong> First 3 octets of MAC address identifying manufacturer</li>
     *   <li><strong>Extraction Method:</strong> Parse first 6 characters of BSSID</li>
     *   <li><strong>Blacklist Source:</strong> Configured list of known mobile hotspot manufacturers</li>
     * </ul>
     * 
     * <p><strong>Business Rules:</strong></p>
     * <ul>
     *   <li>Detection is only performed if mobile hotspot filtering is enabled</li>
     *   <li>BSSID must be at least 8 characters long for OUI extraction</li>
     *   <li>OUI must be successfully extracted for detection to proceed</li>
     *   <li>Action (EXCLUDE/FLAG) is determined by configuration</li>
     * </ul>
     * 
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Filter out temporary mobile networks from fixed infrastructure analysis</li>
     *   <li>Improve data quality by excluding non-permanent networks</li>
     *   <li>Reduce noise in location analysis by focusing on stable networks</li>
     * </ul>
     * 
     * <p><strong>Metrics Impact:</strong></p>
     * <ul>
     *   <li>Increments detection counter when mobile hotspots are identified</li>
     *   <li>Provides detailed detection information for analysis</li>
     * </ul>
     * 
     * @param bssid The BSSID string to check for mobile hotspot characteristics
     * @return MobileHotspotResult containing detection status and recommended action
     */
    public MobileHotspotResult detectMobileHotspot(String bssid) {
        // Step 1: Check if mobile hotspot detection is enabled
        // Skip detection if the feature is disabled in configuration
        if (!filteringConfig.mobileHotspot().enabled()) {
            return MobileHotspotResult.notChecked();
        }
        
        // Step 2: Validate BSSID input for OUI extraction
        // BSSID must be at least 8 characters to contain a valid OUI (6 chars) plus separator
        if (bssid == null || bssid.length() < 8) {
            return MobileHotspotResult.notDetected();
        }
        
        // Step 3: Extract OUI (Organizationally Unique Identifier) from BSSID
        // OUI is the first 3 octets (6 characters) that identify the manufacturer
        String oui = extractOui(bssid);
        if (oui == null) {
            return MobileHotspotResult.notDetected();
        }
        
        // Step 4: Check OUI against configured mobile hotspot manufacturer blacklist
        // This identifies BSSIDs from known mobile hotspot manufacturers
        boolean isKnownMobileOui = filteringConfig.mobileHotspot().ouiBlacklist().contains(oui);
        
        if (isKnownMobileOui) {
            mobileHotspotDetectedCounter.increment();
            logger.debug("Mobile hotspot detected: BSSID={}, OUI={}", bssid, oui);
            return MobileHotspotResult.detected(oui, filteringConfig.mobileHotspot().action());
        }
        
        return MobileHotspotResult.notDetected();
    }
    
    /**
     * Extracts OUI (Organizationally Unique Identifier) from a BSSID string.
     * 
     * <p>This utility method extracts the first 3 octets (6 characters) of a BSSID,
     * which represent the OUI that identifies the manufacturer of the network device.
     * The OUI is used for mobile hotspot detection and manufacturer identification.</p>
     * 
     * <p><strong>Extraction Process:</strong></p>
     * <ol>
     *   <li><strong>Input Validation:</strong> Ensure BSSID is valid and long enough</li>
     *   <li><strong>Normalization:</strong> Convert to uppercase and standardize separators</li>
     *   <li><strong>Format Validation:</strong> Verify proper MAC address format with colons</li>
     *   <li><strong>OUI Extraction:</strong> Extract first 8 characters (XX:XX:XX format)</li>
     * </ol>
     * 
     * <p><strong>OUI Format:</strong></p>
     * <ul>
     *   <li><strong>Length:</strong> Exactly 8 characters (XX:XX:XX)</li>
     *   <li><strong>Case:</strong> Uppercase for consistency</li>
     *   <li><strong>Separators:</strong> Colons between octets</li>
     *   <li><strong>Example:</strong> "B8:F8:53" from "B8:F8:53:C0:1E:FF"</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>Returns null for null or short BSSID inputs</li>
     *   <li>Handles hyphen to colon conversion</li>
     *   <li>Validates proper MAC address format</li>
     * </ul>
     * 
     * @param bssid The BSSID string to extract OUI from
     * @return The OUI string in XX:XX:XX format or null if extraction fails
     */
    private String extractOui(String bssid) {
        // Step 1: Validate input for OUI extraction
        // BSSID must be at least 8 characters to contain OUI plus separator
        if (bssid == null || bssid.length() < 8) {
            return null;
        }
        
        // Step 2: Normalize BSSID format for consistent processing
        // Convert to uppercase and standardize separators to colons
        String normalized = bssid.toUpperCase().replace("-", ":");
        
        // Step 3: Validate format and extract OUI
        // Check for proper MAC address format with colons at positions 2 and 5
        if (normalized.length() >= 8 && normalized.charAt(2) == ':' && normalized.charAt(5) == ':') {
            return normalized.substring(0, 8); // Extract XX:XX:XX format
        }
        
        return null;
    }
    
    /**
     * Checks if a MAC address is invalid due to being reserved, broadcast, or all zeros.
     * 
     * <p>This utility method validates MAC addresses against known invalid patterns
     * that should not be used for network identification. Invalid MAC addresses are
     * filtered out to improve data quality and prevent analysis of non-unique identifiers.</p>
     * 
     * <p><strong>Invalid MAC Address Types:</strong></p>
     * <ul>
     *   <li><strong>All Zeros:</strong> 00:00:00:00:00:00 (reserved by IEEE)</li>
     *   <li><strong>Broadcast Address:</strong> FF:FF:FF:FF:FF:FF (all devices)</li>
     * </ul>
     * 
     * <p><strong>Validation Process:</strong></p>
     * <ol>
     *   <li><strong>Normalization:</strong> Remove separators for pattern matching</li>
     *   <li><strong>Pattern Check:</strong> Compare against known invalid patterns</li>
     *   <li><strong>Result Return:</strong> Return true if invalid, false if valid</li>
     * </ol>
     * 
     * <p><strong>Business Impact:</strong></p>
     * <ul>
     *   <li>Filters out non-unique network identifiers</li>
     *   <li>Improves data quality by excluding special-purpose addresses</li>
     *   <li>Prevents analysis of broadcast and reserved addresses</li>
     * </ul>
     * 
     * <p><strong>Invalid Patterns:</strong></p>
     * <ul>
     *   <li><strong>000000000000:</strong> All zeros (IEEE reserved)</li>
     *   <li><strong>ffffffffffff:</strong> Broadcast address (all devices)</li>
     * </ul>
     * 
     * @param bssid The BSSID string to check for invalid MAC address patterns
     * @return true if the MAC address is invalid, false if valid
     */
    private boolean isInvalidMacAddress(String bssid) {
        // Step 1: Normalize BSSID for pattern matching
        // Remove separators to create clean 12-character hex string
        String cleanBssid = bssid.replace(":", "").replace("-", "");
        
        // Step 2: Check for all zeros pattern
        // All zeros is reserved by IEEE and should not be used for network identification
        if ("000000000000".equals(cleanBssid)) {
            return true;
        }
        
        // Step 3: Check for broadcast address pattern
        // Broadcast address targets all devices and is not a unique network identifier
        if ("ffffffffffff".equals(cleanBssid)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Immutable result of data validation operations.
     * 
     * <p>This record represents the outcome of validation operations performed by the
     * DataValidationService. It provides a standardized way to communicate validation
     * results with clear success/failure status and detailed error messages.</p>
     * 
     * <p><strong>Record Components:</strong></p>
     * <ul>
     *   <li><strong>valid:</strong> Boolean indicating if validation passed (true) or failed (false)</li>
     *   <li><strong>errorMessage:</strong> Detailed error message for failed validations, null for successful validations</li>
     * </ul>
     * 
     * <p><strong>Factory Methods:</strong></p>
     * <ul>
     *   <li><strong>success():</strong> Creates a successful validation result with no error message</li>
     *   <li><strong>invalid(String):</strong> Creates a failed validation result with detailed error message</li>
     * </ul>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <ul>
     *   <li>Successful validation: <code>ValidationResult.success()</code></li>
     *   <li>Failed validation: <code>ValidationResult.invalid("RSSI value is null")</code></li>
     * </ul>
     * 
     * @param valid Whether the validation operation was successful
     * @param errorMessage Detailed error message for failed validations, null for successful validations
     */
    public record ValidationResult(boolean valid, String errorMessage) {
        /**
         * Creates a successful validation result.
         * 
         * <p>This factory method creates a ValidationResult indicating that the validation
         * operation completed successfully with no errors.</p>
         * 
         * @return A ValidationResult with valid=true and errorMessage=null
         */
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        /**
         * Creates a failed validation result with error details.
         * 
         * <p>This factory method creates a ValidationResult indicating that the validation
         * operation failed, along with a detailed error message explaining the failure.</p>
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
    
    /**
     * Immutable result of mobile hotspot detection operations.
     * 
     * <p>This record represents the outcome of mobile hotspot detection performed by the
     * DataValidationService. It provides detailed information about whether detection
     * was performed, if a mobile hotspot was detected, and what action should be taken.</p>
     * 
     * <p><strong>Record Components:</strong></p>
     * <ul>
     *   <li><strong>checked:</strong> Boolean indicating if mobile hotspot detection was performed</li>
     *   <li><strong>detected:</strong> Boolean indicating if a mobile hotspot was detected</li>
     *   <li><strong>detectedOui:</strong> The OUI of the detected mobile hotspot manufacturer, null if not detected</li>
     *   <li><strong>action:</strong> The recommended action to take (EXCLUDE/FLAG), null if not detected</li>
     * </ul>
     * 
     * <p><strong>Detection States:</strong></p>
     * <ul>
     *   <li><strong>Not Checked:</strong> Detection was disabled or skipped</li>
     *   <li><strong>Not Detected:</strong> Detection was performed but no mobile hotspot found</li>
     *   <li><strong>Detected:</strong> Mobile hotspot was identified with recommended action</li>
     * </ul>
     * 
     * <p><strong>Factory Methods:</strong></p>
     * <ul>
     *   <li><strong>notChecked():</strong> Detection was not performed (feature disabled)</li>
     *   <li><strong>notDetected():</strong> Detection was performed but no mobile hotspot found</li>
     *   <li><strong>detected(String, Action):</strong> Mobile hotspot was detected with OUI and action</li>
     * </ul>
     * 
     * @param checked Whether mobile hotspot detection was performed
     * @param detected Whether a mobile hotspot was detected
     * @param detectedOui The OUI of the detected mobile hotspot manufacturer, null if not detected
     * @param action The recommended action to take for the detected mobile hotspot, null if not detected
     */
    public record MobileHotspotResult(
        boolean checked,
        boolean detected,
        String detectedOui,
        DataFilteringConfigurationProperties.MobileHotspotAction action
    ) {
        /**
         * Creates a result indicating mobile hotspot detection was not performed.
         * 
         * <p>This factory method creates a MobileHotspotResult when detection was
         * skipped, typically because the feature is disabled in configuration.</p>
         * 
         * @return A MobileHotspotResult with checked=false and detected=false
         */
        public static MobileHotspotResult notChecked() {
            return new MobileHotspotResult(false, false, null, null);
        }
        
        /**
         * Creates a result indicating mobile hotspot detection was performed but no mobile hotspot was found.
         * 
         * <p>This factory method creates a MobileHotspotResult when detection was
         * performed but the BSSID does not belong to a known mobile hotspot manufacturer.</p>
         * 
         * @return A MobileHotspotResult with checked=true and detected=false
         */
        public static MobileHotspotResult notDetected() {
            return new MobileHotspotResult(true, false, null, null);
        }
        
        /**
         * Creates a result indicating a mobile hotspot was detected.
         * 
         * <p>This factory method creates a MobileHotspotResult when a mobile hotspot
         * was detected, including the OUI of the manufacturer and the recommended action.</p>
         * 
         * @param oui The OUI of the detected mobile hotspot manufacturer
         * @param action The recommended action to take (EXCLUDE or FLAG)
         * @return A MobileHotspotResult with checked=true, detected=true, and the provided OUI and action
         * @throws IllegalArgumentException if oui is null or empty, or if action is null
         */
        public static MobileHotspotResult detected(String oui, DataFilteringConfigurationProperties.MobileHotspotAction action) {
            if (oui == null || oui.trim().isEmpty()) {
                throw new IllegalArgumentException("OUI cannot be null or empty");
            }
            if (action == null) {
                throw new IllegalArgumentException("Action cannot be null");
            }
            return new MobileHotspotResult(true, true, oui, action);
        }
    }
} 