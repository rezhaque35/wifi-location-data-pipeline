// src/main/java/com/wifi/ap/location/config/StatisticalAlgorithmConfig.java
package com.wifi.ap.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Statistical Anomaly Detection algorithm.
 * 
 * <p>Handles statistical algorithm parameters for post-maturity outlier detection
 * as defined in Section 3.5.2 of the requirements.
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "wifi.outlier.local.statistical")
@Getter
@Setter
public class StatisticalAlgorithmConfig {
    
    /**
     * Mahalanobis distance threshold for outlier detection.
     * Values above this threshold are considered outliers.
     * Higher values = fewer outliers detected.
     * Default: 3.0 (3-sigma rule for normal distribution)
     */
    private double mahalanobisThreshold = 3.0;
    
    /**
     * Number of representative samples to maintain for virtual neighbor calculations.
     * Balances computational efficiency with detection accuracy.
     * Default: 50 (sufficient for statistical validity)
     */
    private int representativeSampleSize = 50;
    
    /**
     * Spatial boundary threshold for outlier detection.
     * Used in conjunction with Mahalanobis distance.
     * Default: 0.95 (95% confidence interval)
     */
    private double spatialBoundaryThreshold = 0.95;
    
    /**
     * Maximum number of virtual neighbors for representative sample calculations.
     * Prevents excessive computation in dense measurement areas.
     * Default: 30 (reasonable upper bound)
     */
    private int maxVirtualNeighbors = 30;
}
