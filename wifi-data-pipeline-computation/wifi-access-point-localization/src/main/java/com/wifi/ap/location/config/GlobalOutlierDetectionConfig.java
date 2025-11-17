// src/main/java/com/wifi/ap/location/config/GlobalOutlierDetectionConfig.java
package com.wifi.ap.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for global outlier detection algorithms.
 * 
 * <p>Handles MAD-based outlier detection and weighted centroid calculation
 * parameters as defined in Section 3.5.1 of the requirements.
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "wifi.outlier.global")
@Getter
@Setter
public class GlobalOutlierDetectionConfig {
    
    /**
     * MAD (Median Absolute Deviation) multiplier for outlier threshold calculation.
     * Higher values result in more permissive outlier detection (fewer outliers).
     * Lower values result in stricter outlier detection (more outliers).
     * Default: 3.0 (standard statistical practice for robust outlier detection)
     */
    private double madMultiplier = 3.0;
    
    /**
     * Use weighted centroid calculation based on measurement quality.
     * CONNECTED measurements (quality_weight=2.0) get twice the influence of SCAN measurements (quality_weight=1.0).
     * This aligns with the framework requirement that CONNECTED measurements are more trustworthy.
     * Default: true (enable quality-weighted centroid calculation)
     */
    private boolean useWeightedCentroid = true;
    
    /**
     * Minimum percentage of CONNECTED measurements required to enable weighting.
     * If CONNECTED measurements are less than this percentage, unweighted centroid is used.
     * Default: 0.1 (10% minimum)
     */
    private double minConnectedPercentage = 0.1;
    
    /**
     * Minimum absolute count of CONNECTED measurements required to enable weighting.
     * If CONNECTED measurements are fewer than this count, unweighted centroid is used.
     * Default: 2 (at least 2 CONNECTED measurements)
     */
    private int minConnectedCount = 2;
    
    /**
     * Minimum sample size aligned with bootstrap requirements.
     * Below this threshold, global outlier detection is skipped entirely.
     * Default: 20 (ensures statistical reliability)
     */
    private int minSampleSize = 20;
}
