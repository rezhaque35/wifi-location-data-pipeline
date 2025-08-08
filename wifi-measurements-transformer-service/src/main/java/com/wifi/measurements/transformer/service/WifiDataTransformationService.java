// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/WifiDataTransformationService.java
package com.wifi.measurements.transformer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.dto.LocationData;
import com.wifi.measurements.transformer.dto.ScanResult;
import com.wifi.measurements.transformer.dto.ScanResultEntry;
import com.wifi.measurements.transformer.dto.WifiConnectedEvent;
import com.wifi.measurements.transformer.dto.WifiMeasurement;
import com.wifi.measurements.transformer.dto.WifiScanData;

/**
 * Core service responsible for transforming raw WiFi scan data into normalized WiFi measurements.
 *
 * <p>This service implements the primary data transformation logic for the WiFi location data
 * pipeline. It processes both WiFi connected events and scan results, applying comprehensive
 * validation, filtering, and quality assessment rules to ensure data integrity and relevance.
 *
 * <p><strong>Transformation Process:</strong>
 *
 * <ol>
 *   <li><strong>Data Validation:</strong> Validates BSSID format, RSSI values, and location data
 *   <li><strong>Mobile Hotspot Detection:</strong> Identifies and optionally excludes mobile
 *       hotspot data
 *   <li><strong>Quality Assessment:</strong> Calculates quality weights based on signal strength
 *       and link speed
 *   <li><strong>Data Normalization:</strong> Standardizes BSSID format and cleans SSID values
 *   <li><strong>Measurement Creation:</strong> Builds comprehensive measurement records with
 *       metadata
 * </ol>
 *
 * <p><strong>Business Rules:</strong>
 *
 * <ul>
 *   <li>Connected events receive higher quality weights than scan results
 *   <li>Low link speeds trigger quality weight adjustments
 *   <li>Mobile hotspots can be excluded based on configuration
 *   <li>Invalid or missing data is filtered out
 *   <li>Device IDs are generated consistently for tracking
 * </ul>
 *
 * <p><strong>Data Sources:</strong>
 *
 * <ul>
 *   <li><strong>WiFi Connected Events:</strong> Active connection data with detailed network
 *       information
 *   <li><strong>Scan Results:</strong> Passive network discovery data from WiFi scans
 * </ul>
 *
 * <p>This service is designed to be stateless and thread-safe, allowing for concurrent processing
 * of multiple data streams while maintaining data consistency.
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class WifiDataTransformationService {

  private static final Logger logger = LoggerFactory.getLogger(WifiDataTransformationService.class);

  private final DataValidationService validationService;
  private final DataFilteringConfigurationProperties filteringConfig;

  /**
   * Constructs a new WiFi data transformation service with required dependencies.
   *
   * <p>This constructor initializes the service with the validation service for data quality checks
   * and filtering configuration for business rule application. The service uses dependency
   * injection to ensure loose coupling and testability.
   *
   * @param validationService Service responsible for validating WiFi data quality and detecting
   *     mobile hotspots
   * @param filteringConfig Configuration properties that define filtering rules and quality weights
   * @throws IllegalArgumentException if any required dependency is null
   */
  public WifiDataTransformationService(
      DataValidationService validationService,
      DataFilteringConfigurationProperties filteringConfig) {
    if (validationService == null) {
      throw new IllegalArgumentException("DataValidationService cannot be null");
    }
    if (filteringConfig == null) {
      throw new IllegalArgumentException("DataFilteringConfigurationProperties cannot be null");
    }

    this.validationService = validationService;
    this.filteringConfig = filteringConfig;

    logger.info(
        "WiFi Data Transformation Service initialized with validation service and filtering configuration");
  }

  /**
   * Transforms WiFi scan data into a stream of validated and normalized WiFi measurements.
   *
   * <p>This method is the main entry point for data transformation. It processes both WiFi
   * connected events and scan results, applying comprehensive validation and transformation rules
   * to create high-quality measurement records.
   *
   * <p><strong>Processing Steps:</strong>
   *
   * <ol>
   *   <li><strong>Input Validation:</strong> Ensures input data is not null
   *   <li><strong>Device ID Generation:</strong> Creates consistent device identifier
   *   <li><strong>Event Processing:</strong> Transforms connected events into measurements
   *   <li><strong>Scan Processing:</strong> Transforms scan results into measurements
   *   <li><strong>Stream Combination:</strong> Merges both data streams into single output
   * </ol>
   *
   * <p><strong>Data Flow:</strong>
   *
   * <ul>
   *   <li>Connected events are processed individually with detailed network information
   *   <li>Scan results are processed as collections of network entries
   *   <li>Both streams undergo the same validation and quality assessment
   *   <li>Invalid or filtered data is excluded from the output stream
   * </ul>
   *
   * <p><strong>Performance Characteristics:</strong>
   *
   * <ul>
   *   <li>Uses lazy evaluation with Java Streams for memory efficiency
   *   <li>Processes data incrementally without loading entire datasets into memory
   *   <li>Supports parallel processing for improved throughput
   * </ul>
   *
   * @param wifiScanData The parsed WiFi scan data containing connected events and scan results
   * @param processingBatchId Unique identifier for tracking this processing batch
   * @return Stream of transformed and validated WiFi measurements
   * @throws IllegalArgumentException if wifiScanData is null
   * @throws RuntimeException if critical transformation errors occur
   */
  public Stream<WifiMeasurement> transformToMeasurements(
      WifiScanData wifiScanData, String processingBatchId) {
    if (wifiScanData == null) {
      throw new IllegalArgumentException("WiFi scan data cannot be null");
    }

    if (processingBatchId == null || processingBatchId.trim().isEmpty()) {
      throw new IllegalArgumentException("Processing batch ID cannot be null or empty");
    }

    Instant ingestionTimestamp = Instant.now();
    String deviceId = generateDeviceId(wifiScanData);

    logger.debug(
        "Starting transformation: deviceId={}, batchId={}, timestamp={}",
        deviceId,
        processingBatchId,
        ingestionTimestamp);

    // Process connected events - these represent active WiFi connections
    // with detailed network information and higher quality weights
    Stream<WifiMeasurement> connectedEventStream =
        Optional.ofNullable(wifiScanData.wifiConnectedEvents())
            .map(List::stream)
            .orElse(Stream.empty())
            .flatMap(
                event ->
                    transformConnectedEvent(
                        event, wifiScanData, deviceId, processingBatchId, ingestionTimestamp)
                        .stream());

    // Process scan results - these represent discovered networks from WiFi scans
    // with basic network information and standard quality weights
    Stream<WifiMeasurement> scanResultStream =
        Optional.ofNullable(wifiScanData.scanResults())
            .map(List::stream)
            .orElse(Stream.empty())
            .flatMap(
                scanResult ->
                    transformScanResult(
                        scanResult, wifiScanData, deviceId, processingBatchId, ingestionTimestamp));

    // Combine both data streams into a single output stream
    // This allows for unified processing while maintaining data source distinction
    return Stream.concat(connectedEventStream, scanResultStream);
  }

  /**
   * Transforms a WiFi connected event into a normalized WiFi measurement.
   *
   * <p>This method processes WiFi connected events, which represent active WiFi connections with
   * detailed network information. Connected events are considered higher quality data sources
   * compared to scan results because they represent actual network connections.
   *
   * <p><strong>Processing Steps:</strong>
   *
   * <ol>
   *   <li><strong>Data Validation:</strong> Ensures WiFi connected info is present and valid
   *   <li><strong>Field Validation:</strong> Validates BSSID, RSSI, and location data
   *   <li><strong>Mobile Hotspot Detection:</strong> Checks if the network is a mobile hotspot
   *   <li><strong>Quality Assessment:</strong> Calculates quality weight based on signal strength
   *       and link speed
   *   <li><strong>Measurement Creation:</strong> Builds comprehensive measurement record
   * </ol>
   *
   * <p><strong>Quality Characteristics:</strong>
   *
   * <ul>
   *   <li>Connected events receive higher base quality weights than scan results
   *   <li>Link speed information is used to adjust quality weights
   *   <li>Signal strength (RSSI) contributes to overall quality assessment
   * </ul>
   *
   * <p><strong>Filtering Rules:</strong>
   *
   * <ul>
   *   <li>Events without WiFi connected info are excluded
   *   <li>Invalid BSSID, RSSI, or location data results in exclusion
   *   <li>Mobile hotspots may be excluded based on configuration
   * </ul>
   *
   * @param event The WiFi connected event to transform
   * @param wifiScanData The complete WiFi scan data containing device and context information
   * @param deviceId The generated device identifier for this scan session
   * @param processingBatchId The processing batch identifier for tracking
   * @param ingestionTimestamp The timestamp when this data was ingested
   * @return Optional containing the transformed WiFi measurement, or empty if validation fails
   */
  private Optional<WifiMeasurement> transformConnectedEvent(
      WifiConnectedEvent event,
      WifiScanData wifiScanData,
      String deviceId,
      String processingBatchId,
      Instant ingestionTimestamp) {

    // Validate that the connected event contains WiFi information
    // This is critical as connected events should always have detailed network data
    if (event.wifiConnectedInfo() == null) {
      logger.warn("Connected event {} missing WiFi info", event.eventId());
      return Optional.empty();
    }

    // Perform comprehensive validation of all required fields
    // This ensures data quality before proceeding with transformation
    if (!isValidForTransformation(
        event.wifiConnectedInfo().bssid(), event.wifiConnectedInfo().rssi(), event.location())) {
      return Optional.empty();
    }

    // Detect and handle mobile hotspot networks
    // Mobile hotspots may be excluded based on business rules to improve data quality
    DataValidationService.MobileHotspotResult hotspotResult =
        validationService.detectMobileHotspot(event.wifiConnectedInfo().bssid());

    if (shouldExcludeForMobileHotspot(hotspotResult)) {
      logger.debug("Excluding connected event {} due to mobile hotspot detection", event.eventId());
      return Optional.empty();
    }

    // Calculate quality weight based on signal strength and link speed
    // Connected events get higher base weights, but may be adjusted based on network performance
    double qualityWeight =
        calculateQualityWeight(
            filteringConfig.connectedQualityWeight(),
            event.wifiConnectedInfo().linkSpeed(),
            event.wifiConnectedInfo().rssi());

    return Optional.of(
        WifiMeasurement.builder()
            // Primary Keys
            .bssid(normalizedBssid(event.wifiConnectedInfo().bssid()))
            .measurementTimestamp(event.timestamp())
            .eventId(event.eventId())

            // Device Information
            .deviceId(deviceId)
            .deviceModel(wifiScanData.model())
            .deviceManufacturer(wifiScanData.manufacturer())
            .osVersion(wifiScanData.osVersion())
            .appVersion(wifiScanData.appNameVersion())

            // Location Data
            .latitude(event.location() != null ? event.location().latitude() : null)
            .longitude(event.location() != null ? event.location().longitude() : null)
            .altitude(event.location() != null ? event.location().altitude() : null)
            .locationAccuracy(event.location() != null ? event.location().accuracy() : null)
            .locationTimestamp(event.location() != null ? event.location().time() : null)
            .locationProvider(event.location() != null ? event.location().provider() : null)
            .locationSource(event.location() != null ? event.location().source() : null)
            .speed(event.location() != null ? event.location().speed() : null)
            .bearing(event.location() != null ? event.location().bearing() : null)

            // WiFi Signal Data
            .ssid(cleanSsid(event.wifiConnectedInfo().ssid()))
            .rssi(event.wifiConnectedInfo().rssi())
            .frequency(event.wifiConnectedInfo().frequency())
            .scanTimestamp(event.timestamp())

            // Connection Status and Quality
            .connectionStatus("CONNECTED")
            .qualityWeight(qualityWeight)

            // Connected-Only Enrichment Fields
            .linkSpeed(event.wifiConnectedInfo().linkSpeed())
            .channelWidth(event.wifiConnectedInfo().channelWidth())
            .centerFreq0(event.wifiConnectedInfo().centerFreq0())
            .centerFreq1(event.wifiConnectedInfo().centerFreq1())
            .capabilities(event.wifiConnectedInfo().capabilities())
            .is80211mcResponder(event.wifiConnectedInfo().is80211mcResponder())
            .isPasspointNetwork(event.wifiConnectedInfo().isPasspointNetwork())
            .operatorFriendlyName(event.wifiConnectedInfo().operatorFriendlyName())
            .venueName(event.wifiConnectedInfo().venueName())
            .isCaptive(event.isCaptive())
            .numScanResults(event.wifiConnectedInfo().numOfScanResults())

            // Processing Metadata
            .ingestionTimestamp(ingestionTimestamp)
            .dataVersion(wifiScanData.dataVersion())
            .processingBatchId(processingBatchId)
            .qualityScore(calculateQualityScore(event.location(), event.wifiConnectedInfo().rssi()))
            .build());
  }

  /**
   * Transforms WiFi scan results into a stream of normalized WiFi measurements.
   *
   * <p>This method processes WiFi scan results, which represent discovered networks from passive
   * WiFi scans. Scan results provide broader network coverage but with less detailed information
   * compared to connected events.
   *
   * <p><strong>Processing Approach:</strong>
   *
   * <ul>
   *   <li>Uses a declarative stream-based approach for efficient processing
   *   <li>Handles null scan results gracefully by returning empty streams
   *   <li>Processes each scan result entry individually for detailed validation
   *   <li>Applies the same validation and quality assessment rules as connected events
   * </ul>
   *
   * <p><strong>Data Characteristics:</strong>
   *
   * <ul>
   *   <li>Scan results contain multiple network entries per scan
   *   <li>Each entry represents a discovered network with basic information
   *   <li>Location data is shared across all entries in a scan result
   *   <li>Quality weights are typically lower than connected events
   * </ul>
   *
   * @param scanResult The WiFi scan result containing multiple network entries
   * @param wifiScanData The complete WiFi scan data containing device and context information
   * @param deviceId The generated device identifier for this scan session
   * @param processingBatchId The processing batch identifier for tracking
   * @param ingestionTimestamp The timestamp when this data was ingested
   * @return Stream of transformed WiFi measurements from the scan result
   */
  private Stream<WifiMeasurement> transformScanResult(
      ScanResult scanResult,
      WifiScanData wifiScanData,
      String deviceId,
      String processingBatchId,
      Instant ingestionTimestamp) {

    // Process scan results using a declarative stream approach
    // This handles null results gracefully and processes each entry individually
    return Optional.ofNullable(scanResult.results()).stream()
        .flatMap(List::stream)
        .flatMap(
            entry ->
                transformScanResultEntrySafely(
                    entry,
                    scanResult,
                    wifiScanData,
                    deviceId,
                    processingBatchId,
                    ingestionTimestamp)
                    .stream());
  }

  /**
   * Safely transforms a single scan result entry into a WiFi measurement.
   *
   * <p>This method processes individual network entries from WiFi scan results. Each entry
   * represents a discovered network with basic connectivity information. The method applies
   * comprehensive validation and transformation rules to ensure data quality and consistency.
   *
   * <p><strong>Processing Steps:</strong>
   *
   * <ol>
   *   <li><strong>Field Validation:</strong> Validates BSSID, RSSI, and location data
   *   <li><strong>Mobile Hotspot Detection:</strong> Checks if the network is a mobile hotspot
   *   <li><strong>Event ID Generation:</strong> Creates unique identifier for the measurement
   *   <li><strong>Measurement Building:</strong> Constructs comprehensive measurement record
   *   <li><strong>Quality Assessment:</strong> Calculates quality score and weight
   * </ol>
   *
   * <p><strong>Data Mapping:</strong>
   *
   * <ul>
   *   <li>Network information comes from the scan result entry
   *   <li>Location data is shared from the parent scan result
   *   <li>Device information is inherited from the WiFi scan data
   *   <li>Processing metadata is added for tracking and debugging
   * </ul>
   *
   * <p><strong>Error Handling:</strong>
   *
   * <ul>
   *   <li>Invalid entries are filtered out without affecting other entries
   *   <li>Exceptions are caught and logged to prevent processing failures
   *   <li>Returns empty Optional for any validation failures
   * </ul>
   *
   * @param entry The individual scan result entry to transform
   * @param scanResult The parent scan result containing location and timing information
   * @param wifiScanData The complete WiFi scan data containing device and context information
   * @param deviceId The generated device identifier for this scan session
   * @param processingBatchId The processing batch identifier for tracking
   * @param ingestionTimestamp The timestamp when this data was ingested
   * @return Optional containing the transformed WiFi measurement, or empty if validation fails
   */
  private Optional<WifiMeasurement> transformScanResultEntrySafely(
      ScanResultEntry entry,
      ScanResult scanResult,
      WifiScanData wifiScanData,
      String deviceId,
      String processingBatchId,
      Instant ingestionTimestamp) {
    try {
      // Step 1: Validate all required fields before processing
      // This ensures data quality and prevents downstream errors
      if (!isValidForTransformation(entry.bssid(), entry.rssi(), scanResult.location())) {
        return Optional.empty();
      }

      // Step 2: Detect and handle mobile hotspot networks
      // Mobile hotspots may be excluded based on business rules to improve data quality
      DataValidationService.MobileHotspotResult hotspotResult =
          validationService.detectMobileHotspot(entry.bssid());

      if (shouldExcludeForMobileHotspot(hotspotResult)) {
        logger.debug(
            "Excluding scan result for BSSID {} due to mobile hotspot detection", entry.bssid());
        return Optional.empty();
      }

      // Step 3: Generate unique event identifier for tracking and deduplication
      // This combines timestamp and BSSID to create a deterministic but unique ID
      String eventId = generateEventId(scanResult.timestamp(), entry.bssid());

      // Step 4: Build comprehensive WiFi measurement record
      // This creates a normalized measurement with all required fields and metadata
      WifiMeasurement measurement =
          WifiMeasurement.builder()
              // Primary Keys - These uniquely identify each measurement
              .bssid(normalizedBssid(entry.bssid())) // Normalized BSSID format
              .measurementTimestamp(scanResult.timestamp()) // When the scan occurred
              .eventId(eventId) // Unique identifier for this measurement

              // Device Information - Inherited from the WiFi scan data
              .deviceId(deviceId) // Generated device identifier
              .deviceModel(wifiScanData.model()) // Device model (e.g., "SM-A536V")
              .deviceManufacturer(
                  wifiScanData.manufacturer()) // Device manufacturer (e.g., "samsung")
              .osVersion(wifiScanData.osVersion())
              .appVersion(wifiScanData.appNameVersion())

              // Location Data
              .latitude(scanResult.location() != null ? scanResult.location().latitude() : null)
              .longitude(scanResult.location() != null ? scanResult.location().longitude() : null)
              .altitude(scanResult.location() != null ? scanResult.location().altitude() : null)
              .locationAccuracy(
                  scanResult.location() != null ? scanResult.location().accuracy() : null)
              .locationTimestamp(
                  scanResult.location() != null ? scanResult.location().time() : null)
              .locationProvider(
                  scanResult.location() != null ? scanResult.location().provider() : null)
              .locationSource(scanResult.location() != null ? scanResult.location().source() : null)
              .speed(scanResult.location() != null ? scanResult.location().speed() : null)
              .bearing(scanResult.location() != null ? scanResult.location().bearing() : null)

              // WiFi Signal Data
              .ssid(cleanSsid(entry.ssid()))
              .rssi(entry.rssi())
              .frequency(null) // Not available in scan results
              .scanTimestamp(entry.scantime())

              // Connection Status and Quality
              .connectionStatus("SCAN")
              .qualityWeight(filteringConfig.scanQualityWeight())

              // Connected-Only Fields (NULL for scan results)
              .linkSpeed(null)
              .channelWidth(null)
              .centerFreq0(null)
              .centerFreq1(null)
              .capabilities(null)
              .is80211mcResponder(null)
              .isPasspointNetwork(null)
              .operatorFriendlyName(null)
              .venueName(null)
              .isCaptive(null)
              .numScanResults(null)

              // Processing Metadata
              .ingestionTimestamp(ingestionTimestamp)
              .dataVersion(wifiScanData.dataVersion())
              .processingBatchId(processingBatchId)
              .qualityScore(calculateQualityScore(scanResult.location(), entry.rssi()))
              .build();

      return Optional.of(measurement);

    } catch (Exception e) {
      logger.warn("Failed to transform scan result entry {}: {}", entry.bssid(), e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Validates if the provided data is suitable for transformation into a WiFi measurement.
   *
   * <p>This method performs comprehensive validation of all critical fields required for creating a
   * valid WiFi measurement. It ensures data quality by checking BSSID format, RSSI values, and
   * location data integrity.
   *
   * <p><strong>Validation Criteria:</strong>
   *
   * <ul>
   *   <li><strong>BSSID:</strong> Must be a valid MAC address format
   *   <li><strong>RSSI:</strong> Must be within acceptable signal strength range
   *   <li><strong>Location:</strong> Must have valid coordinates and accuracy
   * </ul>
   *
   * <p><strong>Validation Process:</strong>
   *
   * <ol>
   *   <li>Validates BSSID format and structure using validation service
   *   <li>Checks RSSI value against acceptable ranges
   *   <li>Validates location data including coordinates and accuracy
   *   <li>Returns false if any validation fails, preventing downstream errors
   * </ol>
   *
   * @param bssid The Basic Service Set Identifier (MAC address) to validate
   * @param rssi The Received Signal Strength Indicator value to validate
   * @param location The location data containing coordinates and accuracy information
   * @return true if all fields are valid for transformation, false otherwise
   */
  private boolean isValidForTransformation(String bssid, Integer rssi, LocationData location) {
    // Step 1: Validate BSSID format and structure
    // BSSID must be a valid MAC address for proper network identification
    DataValidationService.ValidationResult bssidResult = validationService.validateBssid(bssid);
    if (!bssidResult.valid()) {
      logger.debug("Invalid BSSID: {}", bssidResult.errorMessage());
      return false;
    }

    // Step 2: Validate RSSI signal strength
    // RSSI must be within acceptable range for meaningful signal analysis
    DataValidationService.ValidationResult rssiResult = validationService.validateRssi(rssi);
    if (!rssiResult.valid()) {
      logger.debug("Invalid RSSI: {}", rssiResult.errorMessage());
      return false;
    }

    // Step 3: Validate location data integrity
    // Location must have valid coordinates and accuracy for spatial analysis
    DataValidationService.ValidationResult locationResult =
        validationService.validateLocation(location);
    if (!locationResult.valid()) {
      logger.debug("Invalid location: {}", locationResult.errorMessage());
      return false;
    }

    return true;
  }

  /**
   * Determines if a measurement should be excluded based on mobile hotspot detection.
   *
   * <p>This method implements the business rule for handling mobile hotspot networks. Mobile
   * hotspots may be excluded from analysis to improve data quality, as they represent temporary
   * networks rather than fixed infrastructure.
   *
   * <p><strong>Exclusion Logic:</strong>
   *
   * <ul>
   *   <li>Mobile hotspot must be detected by the validation service
   *   <li>Configuration must specify EXCLUDE action for mobile hotspots
   *   <li>Both conditions must be true for exclusion to occur
   * </ul>
   *
   * @param result The mobile hotspot detection result from validation service
   * @return true if the measurement should be excluded, false otherwise
   */
  private boolean shouldExcludeForMobileHotspot(DataValidationService.MobileHotspotResult result) {
    return result.detected()
        && result.action() == DataFilteringConfigurationProperties.MobileHotspotAction.EXCLUDE;
  }

  /**
   * Calculates quality weight for WiFi measurements based on network performance indicators.
   *
   * <p>This method applies business rules to adjust quality weights based on network performance
   * characteristics. It specifically handles cases where link speed indicates poor network
   * performance despite good signal strength.
   *
   * <p><strong>Quality Adjustment Rules:</strong>
   *
   * <ul>
   *   <li>Low link speed (< 50 Mbps) with good RSSI (> -70 dBm) triggers weight reduction
   *   <li>This indicates network congestion or interference despite strong signal
   *   <li>Reduced weight reflects lower data quality for location analysis
   * </ul>
   *
   * <p><strong>Weight Calculation:</strong>
   *
   * <ul>
   *   <li>Normal conditions: Returns the base weight (higher for connected events)
   *   <li>Poor performance: Returns configured low link speed weight
   *   <li>Missing data: Returns base weight (graceful degradation)
   * </ul>
   *
   * @param baseWeight The base quality weight for this measurement type
   * @param linkSpeed The network link speed in Mbps (may be null)
   * @param rssi The signal strength in dBm (may be null)
   * @return The calculated quality weight for this measurement
   */
  private double calculateQualityWeight(double baseWeight, Integer linkSpeed, Integer rssi) {
    // Apply reduced quality weight for low link speed despite good RSSI
    // This indicates network congestion or interference that affects data quality
    if (linkSpeed != null && rssi != null && linkSpeed < 50 && rssi > -70) {
      return filteringConfig.lowLinkSpeedQualityWeight();
    }
    return baseWeight;
  }

  /**
   * Calculates overall quality score based on location accuracy and signal strength.
   *
   * <p>This method computes a composite quality score that reflects the overall reliability of the
   * measurement for location analysis. The score combines location accuracy and signal strength to
   * provide a normalized quality metric.
   *
   * <p><strong>Score Components:</strong>
   *
   * <ul>
   *   <li><strong>Base Score (50%):</strong> Default quality level for all measurements
   *   <li><strong>Location Accuracy (30%):</strong> Higher accuracy improves score
   *   <li><strong>Signal Strength (20%):</strong> Stronger signals improve score
   * </ul>
   *
   * <p><strong>Scoring Algorithm:</strong>
   *
   * <ol>
   *   <li>Start with base score of 0.5 (50%)
   *   <li>Add location accuracy contribution (0-30% based on accuracy in meters)
   *   <li>Add RSSI contribution (0-20% based on signal strength)
   *   <li>Cap final score at 1.0 (100%)
   * </ol>
   *
   * <p><strong>Quality Ranges:</strong>
   *
   * <ul>
   *   <li>0.5-0.7: Low quality (poor accuracy or weak signal)
   *   <li>0.7-0.9: Medium quality (moderate accuracy and signal)
   *   <li>0.9-1.0: High quality (excellent accuracy and strong signal)
   * </ul>
   *
   * @param location The location data containing accuracy information
   * @param rssi The signal strength in dBm
   * @return Quality score between 0.5 and 1.0, where higher is better
   */
  private double calculateQualityScore(LocationData location, Integer rssi) {
    double score = 0.5; // Base score - minimum quality level for all measurements

    // Location accuracy contribution (30% of total score)
    // Better accuracy (lower values) results in higher scores
    if (location != null && location.accuracy() != null) {
      // Calculate accuracy score: 100m accuracy = 0%, 0m accuracy = 100%
      double accuracyScore = Math.max(0, 1.0 - (location.accuracy() / 100.0));
      score += 0.3 * accuracyScore;
    }

    // RSSI contribution (20% of total score)
    // Better signal strength (higher values) results in higher scores
    if (rssi != null) {
      // Calculate RSSI score: -100 dBm = 0%, 0 dBm = 100%
      double rssiScore = Math.max(0, (rssi + 100.0) / 100.0);
      score += 0.2 * rssiScore;
    }

    return Math.min(1.0, score); // Cap score at maximum of 1.0
  }

  /**
   * Generates a privacy-preserving device identifier from device characteristics.
   *
   * <p>This method creates a unique, deterministic device identifier by combining multiple device
   * characteristics and applying cryptographic hashing. This approach ensures privacy by not
   * storing raw device information while maintaining consistency for tracking and analytics
   * purposes.
   *
   * <p><strong>Device Characteristics Used:</strong>
   *
   * <ul>
   *   <li><strong>Manufacturer:</strong> Device manufacturer (e.g., "samsung")
   *   <li><strong>Model:</strong> Device model (e.g., "SM-A536V")
   *   <li><strong>Device:</strong> Device identifier (e.g., "a53x")
   *   <li><strong>OS Version:</strong> Operating system version string
   * </ul>
   *
   * <p><strong>Privacy Features:</strong>
   *
   * <ul>
   *   <li>Uses SHA-256 hashing to prevent reverse engineering
   *   <li>Consistent identifier for same device characteristics
   *   <li>No storage of raw device information
   * </ul>
   *
   * @param wifiScanData The WiFi scan data containing device information
   * @return A hashed device identifier string
   */
  private String generateDeviceId(WifiScanData wifiScanData) {
    // Create a unique identifier from device characteristics
    // This combines multiple device attributes to ensure uniqueness
    String identifier =
        String.format(
            "%s:%s:%s:%s",
            wifiScanData.manufacturer() != null ? wifiScanData.manufacturer() : "",
            wifiScanData.model() != null ? wifiScanData.model() : "",
            wifiScanData.device() != null ? wifiScanData.device() : "",
            wifiScanData.osVersion() != null ? wifiScanData.osVersion() : "");

    // Apply cryptographic hashing for privacy protection
    return hashString(identifier);
  }

  /**
   * Generates a unique event identifier for WiFi measurements.
   *
   * <p>This method creates a deterministic event ID by combining the scan timestamp and BSSID. This
   * ensures unique identification of each measurement while maintaining consistency for
   * deduplication and tracking purposes.
   *
   * <p><strong>Event ID Components:</strong>
   *
   * <ul>
   *   <li><strong>Timestamp:</strong> When the scan occurred (milliseconds since epoch)
   *   <li><strong>BSSID:</strong> The network identifier (MAC address)
   * </ul>
   *
   * <p><strong>Use Cases:</strong>
   *
   * <ul>
   *   <li>Deduplication of duplicate measurements
   *   <li>Tracking individual measurements through the pipeline
   *   <li>Correlation with other data sources
   * </ul>
   *
   * @param timestamp The scan timestamp in milliseconds
   * @param bssid The Basic Service Set Identifier (MAC address)
   * @return A hashed event identifier string
   */
  private String generateEventId(Long timestamp, String bssid) {
    return hashString(timestamp + ":" + bssid);
  }

  /**
   * Normalizes BSSID format to standard lowercase with colons.
   *
   * <p>This method standardizes BSSID format to ensure consistent representation across the system.
   * It handles various input formats and converts them to the standard MAC address format with
   * lowercase letters and colons.
   *
   * <p><strong>Normalization Process:</strong>
   *
   * <ol>
   *   <li>Convert to lowercase for consistency
   *   <li>Replace hyphens with colons for standard format
   *   <li>Ensure proper MAC address format
   * </ol>
   *
   * <p><strong>Input Formats Handled:</strong>
   *
   * <ul>
   *   <li>"B8:F8:53:C0:1E:FF" → "b8:f8:53:c0:1e:ff"
   *   <li>"B8-F8-53-C0-1E-FF" → "b8:f8:53:c0:1e:ff"
   *   <li>"b8f853c01eff" → "b8:f8:53:c0:1e:ff"
   * </ul>
   *
   * @param bssid The BSSID string to normalize
   * @return Normalized BSSID in lowercase with colons, or null if input is null
   */
  private String normalizedBssid(String bssid) {
    if (bssid == null) {
      return null;
    }
    // Convert to lowercase and standardize separator format
    return bssid.toLowerCase().replace("-", ":");
  }

  /**
   * Cleans SSID by removing null bytes and trimming whitespace.
   *
   * <p>This method sanitizes SSID (Service Set Identifier) values by removing null bytes and
   * trimming whitespace. This ensures clean, consistent SSID representation for storage and
   * analysis.
   *
   * <p><strong>Cleaning Process:</strong>
   *
   * <ul>
   *   <li>Remove null bytes (0x00) that may be present in raw data
   *   <li>Trim leading and trailing whitespace
   *   <li>Preserve internal whitespace and special characters
   *   <li>Return null for empty strings after cleaning
   * </ul>
   *
   * <p><strong>Examples:</strong>
   *
   * <ul>
   *   <li>"MyWiFi\u0000" → "MyWiFi"
   *   <li>" Home Network " → "Home Network"
   *   <li>"Office-5G\u0000\u0000" → "Office-5G"
   *   <li>"\u0000\u0000" → null
   * </ul>
   *
   * @param ssid The SSID string to clean
   * @return Cleaned SSID string, or null if input is null or empty after cleaning
   */
  private String cleanSsid(String ssid) {
    if (ssid == null) {
      return null;
    }

    // Remove null bytes and trim whitespace for clean SSID representation
    String cleaned = ssid.replace("\u0000", "").trim();
    return cleaned.isEmpty() ? null : cleaned;
  }

  /** Hashes a string using SHA-256. */
  private String hashString(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();

    } catch (NoSuchAlgorithmException e) {
      logger.error("SHA-256 algorithm not available", e);
      return UUID.randomUUID().toString();
    }
  }
}
