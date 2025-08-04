// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/dto/WifiMeasurement.java
package com.wifi.measurements.transformer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * DTO representing a WiFi measurement record that matches the wifi_measurements table schema.
 * 
 * This represents the final transformed data that will be written to storage.
 */
public record WifiMeasurement(
    // Primary Keys
    @JsonProperty("bssid")
    String bssid,
    @JsonProperty("measurement_timestamp")
    Long measurementTimestamp,
    @JsonProperty("event_id")
    String eventId,
    
    // Device Information
    @JsonProperty("device_id")
    String deviceId,
    @JsonProperty("device_model")
    String deviceModel,
    @JsonProperty("device_manufacturer")
    String deviceManufacturer,
    @JsonProperty("os_version")
    String osVersion,
    @JsonProperty("app_version")
    String appVersion,
    
    // Location Data (GNSS/GPS)
    @JsonProperty("latitude")
    Double latitude,
    @JsonProperty("longitude")
    Double longitude,
    @JsonProperty("altitude")
    Double altitude,
    @JsonProperty("location_accuracy")
    Double locationAccuracy,
    @JsonProperty("location_timestamp")
    Long locationTimestamp,
    @JsonProperty("location_provider")
    String locationProvider,
    @JsonProperty("location_source")
    String locationSource,
    @JsonProperty("speed")
    Double speed,
    @JsonProperty("bearing")
    Double bearing,
    
    // WiFi Signal Data
    @JsonProperty("ssid")
    String ssid,
    @JsonProperty("rssi")
    Integer rssi,
    @JsonProperty("frequency")
    Integer frequency,
    @JsonProperty("scan_timestamp")
    Long scanTimestamp,
    
    // Data Quality and Connection Tier
    @JsonProperty("connection_status")
    String connectionStatus,  // 'CONNECTED' or 'SCAN'
    @JsonProperty("quality_weight")
    Double qualityWeight,     // 2.0 for CONNECTED, 1.0 for SCAN
    
    // Connected-Only Enrichment Fields (NULL for SCAN records)
    @JsonProperty("link_speed")
    Integer linkSpeed,
    @JsonProperty("channel_width")
    Integer channelWidth,
    @JsonProperty("center_freq0")
    Integer centerFreq0,
    @JsonProperty("center_freq1")
    Integer centerFreq1,
    @JsonProperty("capabilities")
    String capabilities,
    @JsonProperty("is_80211mc_responder")
    Boolean is80211mcResponder,
    @JsonProperty("is_passpoint_network")
    Boolean isPasspointNetwork,
    @JsonProperty("operator_friendly_name")
    String operatorFriendlyName,
    @JsonProperty("venue_name")
    String venueName,
    @JsonProperty("is_captive")
    Boolean isCaptive,
    @JsonProperty("num_scan_results")
    Integer numScanResults,
    
    // Global Outlier Detection (stable, persistent flags)
    @JsonProperty("is_global_outlier")
    Boolean isGlobalOutlier,
    @JsonProperty("global_outlier_distance")
    Double globalOutlierDistance,
    @JsonProperty("global_outlier_threshold")
    Double globalOutlierThreshold,
    @JsonProperty("global_detection_algorithm")
    String globalDetectionAlgorithm,
    @JsonProperty("global_detection_timestamp")
    Instant globalDetectionTimestamp,
    @JsonProperty("global_detection_version")
    String globalDetectionVersion,
    
    // Ingestion and Processing Metadata
    @JsonProperty("ingestion_timestamp")
    Instant ingestionTimestamp,
    @JsonProperty("data_version")
    String dataVersion,
    @JsonProperty("processing_batch_id")
    String processingBatchId,
    @JsonProperty("quality_score")
    Double qualityScore
) {
    
    /**
     * Builder pattern for creating WiFi measurements.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for constructing WifiMeasurement instances.
     */
    public static class Builder {
        // Primary Keys
        private String bssid;
        private Long measurementTimestamp;
        private String eventId;
        
        // Device Information
        private String deviceId;
        private String deviceModel;
        private String deviceManufacturer;
        private String osVersion;
        private String appVersion;
        
        // Location Data
        private Double latitude;
        private Double longitude;
        private Double altitude;
        private Double locationAccuracy;
        private Long locationTimestamp;
        private String locationProvider;
        private String locationSource;
        private Double speed;
        private Double bearing;
        
        // WiFi Signal Data
        private String ssid;
        private Integer rssi;
        private Integer frequency;
        private Long scanTimestamp;
        
        // Data Quality and Connection Tier
        private String connectionStatus;
        private Double qualityWeight;
        
        // Connected-Only Enrichment Fields
        private Integer linkSpeed;
        private Integer channelWidth;
        private Integer centerFreq0;
        private Integer centerFreq1;
        private String capabilities;
        private Boolean is80211mcResponder;
        private Boolean isPasspointNetwork;
        private String operatorFriendlyName;
        private String venueName;
        private Boolean isCaptive;
        private Integer numScanResults;
        
        // Global Outlier Detection (set to null for now)
        private Boolean isGlobalOutlier = null;
        private Double globalOutlierDistance = null;
        private Double globalOutlierThreshold = null;
        private String globalDetectionAlgorithm = null;
        private Instant globalDetectionTimestamp = null;
        private String globalDetectionVersion = null;
        
        // Ingestion and Processing Metadata
        private Instant ingestionTimestamp;
        private String dataVersion;
        private String processingBatchId;
        private Double qualityScore;
        
        // Builder methods
        public Builder bssid(String bssid) {
            this.bssid = bssid;
            return this;
        }
        
        public Builder measurementTimestamp(Long measurementTimestamp) {
            this.measurementTimestamp = measurementTimestamp;
            return this;
        }
        
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        
        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }
        
        public Builder deviceModel(String deviceModel) {
            this.deviceModel = deviceModel;
            return this;
        }
        
        public Builder deviceManufacturer(String deviceManufacturer) {
            this.deviceManufacturer = deviceManufacturer;
            return this;
        }
        
        public Builder osVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }
        
        public Builder appVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }
        
        public Builder latitude(Double latitude) {
            this.latitude = latitude;
            return this;
        }
        
        public Builder longitude(Double longitude) {
            this.longitude = longitude;
            return this;
        }
        
        public Builder altitude(Double altitude) {
            this.altitude = altitude;
            return this;
        }
        
        public Builder locationAccuracy(Double locationAccuracy) {
            this.locationAccuracy = locationAccuracy;
            return this;
        }
        
        public Builder locationTimestamp(Long locationTimestamp) {
            this.locationTimestamp = locationTimestamp;
            return this;
        }
        
        public Builder locationProvider(String locationProvider) {
            this.locationProvider = locationProvider;
            return this;
        }
        
        public Builder locationSource(String locationSource) {
            this.locationSource = locationSource;
            return this;
        }
        
        public Builder speed(Double speed) {
            this.speed = speed;
            return this;
        }
        
        public Builder bearing(Double bearing) {
            this.bearing = bearing;
            return this;
        }
        
        public Builder ssid(String ssid) {
            this.ssid = ssid;
            return this;
        }
        
        public Builder rssi(Integer rssi) {
            this.rssi = rssi;
            return this;
        }
        
        public Builder frequency(Integer frequency) {
            this.frequency = frequency;
            return this;
        }
        
        public Builder scanTimestamp(Long scanTimestamp) {
            this.scanTimestamp = scanTimestamp;
            return this;
        }
        
        public Builder connectionStatus(String connectionStatus) {
            this.connectionStatus = connectionStatus;
            return this;
        }
        
        public Builder qualityWeight(Double qualityWeight) {
            this.qualityWeight = qualityWeight;
            return this;
        }
        
        public Builder linkSpeed(Integer linkSpeed) {
            this.linkSpeed = linkSpeed;
            return this;
        }
        
        public Builder channelWidth(Integer channelWidth) {
            this.channelWidth = channelWidth;
            return this;
        }
        
        public Builder centerFreq0(Integer centerFreq0) {
            this.centerFreq0 = centerFreq0;
            return this;
        }
        
        public Builder centerFreq1(Integer centerFreq1) {
            this.centerFreq1 = centerFreq1;
            return this;
        }
        
        public Builder capabilities(String capabilities) {
            this.capabilities = capabilities;
            return this;
        }
        
        public Builder is80211mcResponder(Boolean is80211mcResponder) {
            this.is80211mcResponder = is80211mcResponder;
            return this;
        }
        
        public Builder isPasspointNetwork(Boolean isPasspointNetwork) {
            this.isPasspointNetwork = isPasspointNetwork;
            return this;
        }
        
        public Builder operatorFriendlyName(String operatorFriendlyName) {
            this.operatorFriendlyName = operatorFriendlyName;
            return this;
        }
        
        public Builder venueName(String venueName) {
            this.venueName = venueName;
            return this;
        }
        
        public Builder isCaptive(Boolean isCaptive) {
            this.isCaptive = isCaptive;
            return this;
        }
        
        public Builder numScanResults(Integer numScanResults) {
            this.numScanResults = numScanResults;
            return this;
        }
        
        public Builder ingestionTimestamp(Instant ingestionTimestamp) {
            this.ingestionTimestamp = ingestionTimestamp;
            return this;
        }
        
        public Builder dataVersion(String dataVersion) {
            this.dataVersion = dataVersion;
            return this;
        }
        
        public Builder processingBatchId(String processingBatchId) {
            this.processingBatchId = processingBatchId;
            return this;
        }
        
        public Builder qualityScore(Double qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }
        
        public WifiMeasurement build() {
            return new WifiMeasurement(
                bssid, measurementTimestamp, eventId,
                deviceId, deviceModel, deviceManufacturer, osVersion, appVersion,
                latitude, longitude, altitude, locationAccuracy, locationTimestamp,
                locationProvider, locationSource, speed, bearing,
                ssid, rssi, frequency, scanTimestamp,
                connectionStatus, qualityWeight,
                linkSpeed, channelWidth, centerFreq0, centerFreq1, capabilities,
                is80211mcResponder, isPasspointNetwork, operatorFriendlyName, venueName,
                isCaptive, numScanResults,
                isGlobalOutlier, globalOutlierDistance, globalOutlierThreshold,
                globalDetectionAlgorithm, globalDetectionTimestamp, globalDetectionVersion,
                ingestionTimestamp, dataVersion, processingBatchId, qualityScore
            );
        }
    }
} 