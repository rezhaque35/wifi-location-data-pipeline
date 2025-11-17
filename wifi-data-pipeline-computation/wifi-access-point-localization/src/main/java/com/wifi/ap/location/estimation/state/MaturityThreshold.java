// src/main/java/com/wifi/ap/location/estimate/dto/MaturityThreshold.java
package com.wifi.ap.location.estimation.state;

import lombok.Getter;

/**
 * Enumeration defining the threshold values for data maturity tiers.
 * 
 * <p>This enum centralizes all threshold constants used by the DataMaturityTier
 * to determine tier boundaries based on measurement counts.
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
@Getter
public enum MaturityThreshold {
    
    /**
     * Threshold for transitioning from INSUFFICIENT to BOOTSTRAP tier.
     * Measurements below this count are considered insufficient.
     */
    INSUFFICIENT(20),
    
    /**
     * Threshold for transitioning from BOOTSTRAP to MATURE tier.
     * Measurements below this count but above INSUFFICIENT are bootstrap level.
     */
    BOOTSTRAP(50),
    
    /**
     * Threshold for transitioning from MATURE to HIGHLY_MATURE tier.
     * Measurements below this count but above BOOTSTRAP are mature level.
     */
    MATURE(100);

    /**
     * -- GETTER --
     *  Gets the threshold value.
     *
     * @return The threshold value
     */
    private final int value;
    
    MaturityThreshold(int value) {
        this.value = value;
    }

}
