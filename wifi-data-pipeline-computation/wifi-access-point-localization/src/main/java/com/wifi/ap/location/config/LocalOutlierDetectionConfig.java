// src/main/java/com/wifi/ap/location/config/LocalOutlierDetectionConfig.java
package com.wifi.ap.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for local outlier detection general settings.
 * 
 * <p>Handles shared parameters for local outlier detection algorithms
 * as defined in Section 3.5.2 of the requirements.
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
@Component
@ConfigurationProperties(prefix = "wifi.outlier.local")
@Getter
@Setter
public class LocalOutlierDetectionConfig {
    
    /**
     * Minimum measurements required for any local outlier detection.
     * Below this threshold, outlier detection is skipped entirely.
     * Default: 5 (ensures basic statistical validity)
     */
    private int minMeasurementsForDetection = 5;
}
