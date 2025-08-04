package com.wifi.positioning.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.util.List;
import java.util.Set;

/**
 * WiFi access point model with DynamoDB mapping annotations.
 * This class serves as the unified model for WiFi access points in the system,
 * with the necessary annotations for DynamoDB persistence.
 * Contains all the fields required for positioning calculations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class WifiAccessPoint {
    /**
     * Status constants for access point operational states
     */
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_WARNING = "warning";
    public static final String STATUS_WIFI_HOTSPOT = "wifi-hotspot";
    public static final String STATUS_VERIFIED = "verified";
    public static final String STATUS_TEST = "test";
    public static final String STATUS_IMPORTED= "imported";
    
    /**
     * Set of valid access point statuses for fast lookup during positioning calculations.
     * Access points with these statuses are considered reliable enough for positioning.
     */
    public static final Set<String> VALID_AP_STATUSES = Set.of(
        STATUS_ACTIVE,
        STATUS_WARNING,
        STATUS_VERIFIED,
        STATUS_TEST,
        STATUS_IMPORTED
    );

    private String macAddress;
    private String version;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double horizontalAccuracy;
    private Double verticalAccuracy;
    private Double confidence;
    private String ssid;
    private Integer frequency;
    private String vendor;
    private String status;
    private String geohash;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("mac_addr")
    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    @DynamoDbAttribute("version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "GeohashIndex")
    @DynamoDbSecondarySortKey(indexNames = "SSIDIndex")
    @DynamoDbAttribute("geohash")
    public String getGeohash() {
        return geohash;
    }

    public void setGeohash(String geohash) {
        this.geohash = geohash;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "SSIDIndex")
    @DynamoDbSecondarySortKey(indexNames = "GeohashIndex")
    @DynamoDbAttribute("ssid")
    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbAttribute("latitude")
    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    @DynamoDbAttribute("longitude")
    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    @DynamoDbAttribute("altitude")
    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    @DynamoDbAttribute("horizontal_accuracy")
    public Double getHorizontalAccuracy() {
        return horizontalAccuracy;
    }

    public void setHorizontalAccuracy(Double horizontalAccuracy) {
        this.horizontalAccuracy = horizontalAccuracy;
    }

    @DynamoDbAttribute("vertical_accuracy")
    public Double getVerticalAccuracy() {
        return verticalAccuracy;
    }

    public void setVerticalAccuracy(Double verticalAccuracy) {
        this.verticalAccuracy = verticalAccuracy;
    }

    @DynamoDbAttribute("confidence")
    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    @DynamoDbAttribute("frequency")
    public Integer getFrequency() {
        return frequency;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }

    @DynamoDbAttribute("vendor")
    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    /**
     * Checks if this access point is a WiFi hotspot.
     * 
     * @return true if this is a hotspot, false otherwise
     */
    public boolean isHotspot() {
        return STATUS_WIFI_HOTSPOT.equals(status);
    }
} 