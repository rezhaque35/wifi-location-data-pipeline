// src/main/java/com/wifi/ap/location/config/WifiLocalizationConfig.java
package com.wifi.ap.location.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

/**
 * Top-level configuration class for WiFi Access Point Localization.
 * 
 * <p>This configuration class enables scanning of all WiFi-related configuration properties
 * and ensures proper Spring bean registration for dependency injection.
 * 
 * <p>Configuration is organized into separate, focused classes:
 * <ul>
 *   <li>{@link GlobalOutlierDetectionConfig} - Global outlier detection settings</li>
 *   <li>{@link LocalOutlierDetectionConfig} - Local outlier detection general settings</li>
 *   <li>{@link LofAlgorithmConfig} - LOF algorithm specific parameters</li>
 *   <li>{@link StatisticalAlgorithmConfig} - Statistical algorithm specific parameters</li>
 * </ul>
 * 
 * <p>WCL accuracy calculation uses embedded research-based constants in {@link com.wifi.ap.location.estimation.accuracy.WclAccuracyCalculator}.</p>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
@ConfigurationPropertiesScan(basePackages = "com.wifi.ap.location.config")
public class WifiLocalizationConfig {
    // This class serves as a configuration entry point and enables
    // automatic scanning of @ConfigurationProperties classes in this package
}
