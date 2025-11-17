// src/main/java/com/wifi/ap/location/config/LofAlgorithmConfig.java
package com.wifi.ap.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for LOF (Local Outlier Factor) algorithm.
 * 
 * <p>Handles LOF-specific parameters for pre-maturity outlier detection
 * as defined in Section 3.5.2 of the requirements.
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "wifi.outlier.local.lof")
@Getter
@Setter
public class LofAlgorithmConfig {

    /**
     * Minimum number of measurements for LOF calculation.
     * Higher values provide more stable results but require more computation.
     * Default: 5 (balanced between stability and performance)
     */
    private int minMeasurements = 20;

    /**
     * Minimum number of neighbors for LOF calculation.
     * Higher values provide more stable results but require more computation.
     * Default: 5 (balanced between stability and performance)
     */
    private int minNeighbors = 5;
    
    /**
     * Maximum number of neighbors to consider for LOF calculation.
     * Prevents excessive computation with large datasets.
     * Default: 15 (reasonable upper bound)
     */
    private int maxNeighbors = 15;
    
    /**
     * LOF threshold for outlier classification.
     * Values above this threshold are considered outliers.
     * Higher values = fewer outliers detected.
     * Default: 1.7 (standard statistical practice)
     */
    private double outlierThreshold = 1.7;
}
