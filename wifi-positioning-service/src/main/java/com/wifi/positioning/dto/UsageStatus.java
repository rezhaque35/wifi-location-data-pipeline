// src/main/java/com/wifi/positioning/dto/UsageStatus.java
package com.wifi.positioning.dto;

/**
 * Usage status enumeration for access points in positioning calculation.
 * Used as map keys to categorize discarded access points by discard reason.
 */
public enum UsageStatus {
    /** Access point is used in position calculation */
    USED,
    
    /** Access point discarded due to weak signal (not in top 20 strongest) */
    DISCARDED_WEAK_SIGNAL,
    
    /** Access point found but has no location data */
    DISCARDED_NO_LOCATION,
    
    /** Access point discarded due to invalid status (hotspot, expired, etc.) */
    DISCARDED_STATUS,
    
    /** Access point discarded because it's outside cell tower range (global outlier) */
    DISCARDED_CELL_RANGE,
    
    /** Access point discarded - too far from geographic centroid (global outlier) */
    DISCARDED_GLOBAL_OUTLIER_CENTROID,
    
    /** Access point discarded as LOF-based local outlier */
    DISCARDED_LOCAL_OUTLIER,
    
    /** Access point not yet processed or status unknown */
    UNKNOWN
}

