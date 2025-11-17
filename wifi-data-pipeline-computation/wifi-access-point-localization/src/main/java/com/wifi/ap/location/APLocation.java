package com.wifi.ap.location;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wifi.ap.location.estimation.state.APState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.Set;

/**
 * WiFi access point model with DynamoDB mapping annotations. This class serves as the unified model
 * for WiFi access points in the system, with the necessary annotations for DynamoDB persistence.
 * Contains all the fields required for positioning calculations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class APLocation {
  private static final Logger logger = LoggerFactory.getLogger(APLocation.class);
  
  /** ObjectMapper for JSON serialization/deserialization of APState */
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());
  
  /** Status constants for access point operational states */
  public static final String STATUS_ACTIVE = "active";

  public static final String STATUS_ERROR = "error";
  public static final String STATUS_EXPIRED = "expired";
  public static final String STATUS_WARNING = "warning";
  public static final String STATUS_WIFI_HOTSPOT = "wifi-hotspot";
  public static final String STATUS_VERIFIED = "verified";
  public static final String STATUS_TEST = "test";
  public static final String STATUS_IMPORTED = "imported";

  /**
   * Set of valid access point statuses for fast lookup during positioning calculations. Access
   * points with these statuses are considered reliable enough for positioning.
   */
  public static final Set<String> VALID_AP_STATUSES =
      Set.of(STATUS_ACTIVE, STATUS_WARNING, STATUS_VERIFIED, STATUS_TEST, STATUS_IMPORTED);

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
  private String stateJson;
  
  // Field for APState object (not persisted directly, used for caching)
  private APState apState;

  @DynamoDbPartitionKey
  @DynamoDbAttribute("mac_addr")
  public String getMacAddress() {
    return macAddress;
  }

    @DynamoDbAttribute("version")
  public String getVersion() {
    return version;
  }

    @DynamoDbSecondaryPartitionKey(indexNames = "GeohashIndex")
  @DynamoDbSecondarySortKey(indexNames = "SSIDIndex")
  @DynamoDbAttribute("geohash")
  public String getGeohash() {
    return geohash;
  }

    @DynamoDbSecondaryPartitionKey(indexNames = "SSIDIndex")
  @DynamoDbSecondarySortKey(indexNames = "GeohashIndex")
  @DynamoDbAttribute("ssid")
  public String getSsid() {
    return ssid;
  }

    @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")
  @DynamoDbAttribute("status")
  public String getStatus() {
    return status;
  }

    @DynamoDbAttribute("latitude")
  public Double getLatitude() {
    return latitude;
  }

    @DynamoDbAttribute("longitude")
  public Double getLongitude() {
    return longitude;
  }

    @DynamoDbAttribute("altitude")
  public Double getAltitude() {
    return altitude;
  }

    @DynamoDbAttribute("horizontal_accuracy")
  public Double getHorizontalAccuracy() {
    return horizontalAccuracy;
  }

    @DynamoDbAttribute("vertical_accuracy")
  public Double getVerticalAccuracy() {
    return verticalAccuracy;
  }

    @DynamoDbAttribute("confidence")
  public Double getConfidence() {
    return confidence;
  }

    @DynamoDbAttribute("frequency")
  public Integer getFrequency() {
    return frequency;
  }

    @DynamoDbAttribute("vendor")
  public String getVendor() {
    return vendor;
  }

    /**
   * Checks if this access point is a WiFi hotspot.
   *
   * @return true if this is a hotspot, false otherwise
   */
  public boolean isHotspot() {
    return STATUS_WIFI_HOTSPOT.equals(status);
  }

  @DynamoDbAttribute("state")
  public String getStateJson() {
    // If apState is set but stateJson is null, serialize it
    if (apState != null && stateJson == null) {
      try {
        stateJson = objectMapper.writeValueAsString(apState);
      } catch (JsonProcessingException e) {
        logger.error("Failed to serialize APState to JSON for macAddress: {}", macAddress, e);
        return null;
      }
    }
    return stateJson;
  }

  public void setStateJson(String stateJson) {
    this.stateJson = stateJson;
    // Clear the transient apState so it will be deserialized fresh next time
    this.apState = null;
  }

  /**
   * Gets the APState object, deserializing from JSON if necessary.
   * 
   * @return APState object or null if no state is stored or deserialization fails
   */
  public APState getApState() {
    if (apState == null && stateJson != null && !stateJson.trim().isEmpty()) {
      try {
        apState = objectMapper.readValue(stateJson, APState.class);
      } catch (JsonProcessingException e) {
        logger.error("Failed to deserialize APState from JSON for macAddress: {}", macAddress, e);
        return null;
      }
    }
    return apState;
  }

  /**
   * Sets the APState object and clears the JSON cache.
   * The JSON will be serialized when needed (on getStateJson() or DynamoDB save).
   * 
   * @param apState APState object to set
   */
  public void setApState(APState apState) {
    this.apState = apState;
    // Clear the JSON cache so it will be serialized fresh next time
    this.stateJson = null;
  }

  public Location getLocation() {
    return Location.of(latitude, longitude);
  }
  public void setLocation(Location location) {
    this.latitude = location.latitude();
    this.longitude = location.longitude();
  }

}
