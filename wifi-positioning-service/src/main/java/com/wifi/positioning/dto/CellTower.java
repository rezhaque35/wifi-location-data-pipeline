// src/main/java/com/wifi/positioning/dto/CellTower.java
package com.wifi.positioning.dto;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Cell tower model with DynamoDB mapping annotations.
 * Represents cell tower location and coverage data from repository.
 * Used as reference point for filtering WiFi access points.
 * 
 * Serves as both DTO and DynamoDB entity to avoid unnecessary object mapping.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class CellTower {
    
    private Long id;
    private String cellType;
    private Double latitude;
    private Double longitude;
    private Double range;
    private String createdAt;
    private String updatedAt;
    
    /**
     * Partition key for DynamoDB table.
     * Stored as String in DynamoDB but exposed as Long in the API.
     * 
     * @return cell tower ID as String for DynamoDB
     */
    @DynamoDbPartitionKey
    @DynamoDbAttribute("cellId")
    public String getCellId() {
        return id != null ? id.toString() : null;
    }
    
    public void setCellId(String cellId) {
        this.id = cellId != null ? Long.parseLong(cellId) : null;
    }
    
    /**
     * Sort key for DynamoDB table.
     * Stored in uppercase for consistent lookups.
     * 
     * @return cell tower type (e.g., "LTE", "GSM")
     */
    @DynamoDbSortKey
    @DynamoDbAttribute("cellType")
    public String getCellType() {
        return cellType;
    }
    
    public void setCellType(String cellType) {
        this.cellType = cellType;
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
    
    /**
     * Range/radius of the cell tower in meters.
     * 
     * @return cell tower range
     */
    @DynamoDbAttribute("radius")
    public Double getRange() {
        return range;
    }
    
    public void setRange(Double range) {
        this.range = range;
    }
    
    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    @DynamoDbAttribute("updatedAt")
    public String getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Validates that the cell tower has valid coordinates.
     * 
     * @return true if latitude and longitude are valid, false otherwise
     */
    public boolean isValid() {
        return latitude != null && longitude != null && range != null
            && latitude >= -90.0 && latitude <= 90.0 
            && longitude >= -180.0 && longitude <= 180.0
            && range > 0;
    }
    
    /**
     * Converts CellTower to a Map for structured logging.
     * 
     * @return Map representation of the CellTower
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("cellType", cellType);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("range", range);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}


