// src/main/java/com/wifi/ap/location/estimate/dto/DataMaturityTier.java
package com.wifi.ap.location.estimation.state;

import lombok.Getter;

import java.util.function.IntPredicate;

/**
 * Data maturity tier enumeration based on measurement count with hardcoded thresholds.
 * 
 * <p>Each tier represents a different level of data reliability and algorithm capability:
 * <ul>
 *   <li><strong>INSUFFICIENT:</strong> Very few measurements, unreliable for most algorithms</li>
 *   <li><strong>BOOTSTRAP:</strong> Initial data collection phase, basic algorithms applicable</li>
 *   <li><strong>MATURE:</strong> Stable data, most algorithms can be applied effectively</li>
 *   <li><strong>HIGHLY_MATURE:</strong> Extensive data, enables advanced statistical algorithms</li>
 * </ul>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
public enum DataMaturityTier {
    
    INSUFFICIENT(
        count -> count < MaturityThreshold.INSUFFICIENT.getValue(),
        "Very few measurements, unreliable for most algorithms"
    ),
    
    BOOTSTRAP(
        count -> count >= MaturityThreshold.INSUFFICIENT.getValue() && count < MaturityThreshold.BOOTSTRAP.getValue(),
        "Initial data collection phase, basic algorithms applicable"
    ),
    
    MATURE(
        count -> count >= MaturityThreshold.BOOTSTRAP.getValue() && count < MaturityThreshold.MATURE.getValue(),
        "Stable data, most algorithms can be applied effectively"
    ),
    
    HIGHLY_MATURE(
        count -> count >= MaturityThreshold.MATURE.getValue(),
        "Extensive data, enables advanced statistical algorithms"
    );

    private final IntPredicate isInRange;
    /**
     * -- GETTER --
     *  Gets a human-readable description of this maturity tier.
     *
     * @return Description of the tier's characteristics and applicability
     */
    @Getter
    private final String description;

    DataMaturityTier(IntPredicate isInRange, String description) {
        this.isInRange = isInRange;
        this.description = description;
    }

    /**
     * Checks if the given measurement count falls within this tier's range.
     *
     * @param measurementCount Number of measurements to check
     * @return true if the measurement count is within this tier's range
     */
    public boolean isInRange(int measurementCount) {
        return isInRange.test(measurementCount);
    }

    /**
     * Gets the minimum measurement count for this tier.
     *
     * @return Minimum measurement count for this tier
     */
    public int getMinCount() {
        return switch (this) {
            case INSUFFICIENT -> 0;
            case BOOTSTRAP -> MaturityThreshold.INSUFFICIENT.getValue();
            case MATURE -> MaturityThreshold.BOOTSTRAP.getValue();
            case HIGHLY_MATURE -> MaturityThreshold.MATURE.getValue();
        };
    }

    /**
     * Gets the maximum measurement count for this tier (exclusive).
     *
     * @return Maximum measurement count for this tier (exclusive), or Integer.MAX_VALUE for unbounded tiers
     */
    public int getMaxCount() {
        return switch (this) {
            case INSUFFICIENT -> MaturityThreshold.INSUFFICIENT.getValue();
            case BOOTSTRAP -> MaturityThreshold.BOOTSTRAP.getValue();
            case MATURE -> MaturityThreshold.MATURE.getValue();
            case HIGHLY_MATURE -> Integer.MAX_VALUE;
        };
    }

    /**
     * Determines the appropriate DataMaturityTier for the given measurement count.
     * 
     * @param measurementCount Number of measurements
     * @return The appropriate DataMaturityTier
     */
    public static DataMaturityTier fromMeasurementCount(int measurementCount) {
        for (DataMaturityTier tier : values()) {
            if (tier.isInRange(measurementCount)) {
                return tier;
            }
        }
        // This should never happen given the tier definitions, but fallback to INSUFFICIENT
        return INSUFFICIENT;
    }

    /**
     * Gets the insufficient threshold value.
     * 
     * @return Threshold for insufficient tier
     */
    public static int getInsufficientThreshold() {
        return MaturityThreshold.INSUFFICIENT.getValue();
    }

    /**
     * Gets the bootstrap threshold value.
     * 
     * @return Threshold for bootstrap tier
     */
    public static int getBootstrapThreshold() {
        return MaturityThreshold.BOOTSTRAP.getValue();
    }

    /**
     * Gets the mature threshold value.
     * 
     * @return Threshold for mature tier
     */
    public static int getMatureThreshold() {
        return MaturityThreshold.MATURE.getValue();
    }
}
