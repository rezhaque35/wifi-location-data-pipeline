// com/wifi/positioning/dto/ComparisonScenario.java
package com.wifi.positioning.dto;

/**
 * Enumeration of cross-service comparison scenarios based on VLSS vs Frisco results.
 * Enhanced to support location type analysis and confidence assessment per requirements.
 */
public enum ComparisonScenario {
    
    /**
     * Both VLSS and Frisco services succeeded in positioning.
     * This indicates both services found WiFi APs in the shared database.
     * Standard WiFi positioning comparison scenario.
     * Location Type: WIFI
     */
    BOTH_WIFI_SUCCESS("BOTH_WIFI_SUCCESS", "Both services succeeded using WiFi positioning", LocationType.WIFI),
    
    /**
     * VLSS succeeded but Frisco failed due to insufficient WiFi data.
     * This strongly indicates VLSS used cell tower fallback positioning.
     * Key scenario for detecting cell tower usage.
     * Location Type: Determined by VLSS accuracy (WiFi if < 250m, CELL if >= 250m)
     */
    VLSS_CELL_FALLBACK_DETECTED("VLSS_CELL_FALLBACK_DETECTED", "VLSS succeeded (likely using cell towers), Frisco failed", LocationType.DYNAMIC),
    
    /**
     * Both services failed to determine position.
     * Indicates insufficient WiFi APs in database and insufficient cell tower data.
     * Location Type: No positioning successful
     */
    BOTH_INSUFFICIENT_DATA("BOTH_INSUFFICIENT_DATA", "Both services failed due to insufficient data", LocationType.NONE),
    
    /**
     * VLSS failed but Frisco succeeded.
     * Unexpected scenario since both use same AP database.
     * May indicate VLSS service issue or different filtering logic.
     * Location Type: Determined by Frisco only (WIFI)
     */
    VLSS_ERROR_FRISCO_SUCCESS("VLSS_ERROR_FRISCO_SUCCESS", "VLSS failed but Frisco succeeded (unexpected)", LocationType.WIFI),
    
    /**
     * VLSS was not provided (sourceResponse is null).
     * Only Frisco positioning was performed for analysis.
     * Location Type: Determined by Frisco result
     */
    FRISCO_ONLY_ANALYSIS("FRISCO_ONLY_ANALYSIS", "Only Frisco service result available for analysis", LocationType.WIFI),
    
    /**
     * VLSS succeeded but Frisco failed due to errors other than insufficient APs.
     * Location Type: Determined by VLSS accuracy (WiFi if < 250m, CELL if >= 250m)
     */
    VLSS_SUCCESS_FRISCO_ERROR("VLSS_SUCCESS_FRISCO_ERROR", "VLSS succeeded but Frisco failed with non-AP error", LocationType.DYNAMIC),
    
    /**
     * Both services provided responses but in unexpected combinations.
     * Requires further investigation.
     */
    UNKNOWN_SCENARIO("UNKNOWN_SCENARIO", "Unknown or unexpected response combination", LocationType.UNKNOWN);
    
    private final String code;
    private final String description;
    private final LocationType locationType;
    
    ComparisonScenario(String code, String description, LocationType locationType) {
        this.code = code;
        this.description = description;
        this.locationType = locationType;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public LocationType getLocationType() {
        return locationType;
    }
    
    /**
     * Determines the comparison scenario based on service results and error details.
     * Enhanced to differentiate between AP-related failures and other errors.
     * 
     * @param vlssSuccess Whether VLSS succeeded (null if no VLSS response)
     * @param friscoSuccess Whether Frisco succeeded
     * @param friscoErrorMessage Frisco error message for failure classification
     * @return The appropriate comparison scenario
     */
    public static ComparisonScenario determineScenario(Boolean vlssSuccess, Boolean friscoSuccess, String friscoErrorMessage) {
        // Handle case where VLSS response is not provided
        if (vlssSuccess == null) {
            return FRISCO_ONLY_ANALYSIS;
        }
        
        // Determine scenario based on success/failure combinations
        boolean vlssSucceeded = Boolean.TRUE.equals(vlssSuccess);
        boolean friscoSucceeded = Boolean.TRUE.equals(friscoSuccess);
        
        if (vlssSucceeded) {
            if (friscoSucceeded) {
                return BOTH_WIFI_SUCCESS;
            } else {
                // VLSS succeeded but Frisco failed - check error type
                if (isInsufficientApError(friscoErrorMessage)) {
                    return VLSS_CELL_FALLBACK_DETECTED;
                } else {
                    return VLSS_SUCCESS_FRISCO_ERROR;
                }
            }
        } else {
            if (friscoSucceeded) {
                return VLSS_ERROR_FRISCO_SUCCESS;
            } else {
                return BOTH_INSUFFICIENT_DATA;
            }
        }
    }
    
    /**
     * Legacy method for backwards compatibility.
     */
    public static ComparisonScenario determineScenario(Boolean vlssSuccess, Boolean friscoSuccess) {
        return determineScenario(vlssSuccess, friscoSuccess, null);
    }
    
    /**
     * Determines if a Frisco error is related to insufficient access points.
     * 
     * @param errorMessage The error message from Frisco service
     * @return true if the error indicates insufficient AP data
     */
    private static boolean isInsufficientApError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        
        String lowerMessage = errorMessage.toLowerCase();
        return lowerMessage.contains("no known access points found in database") ||
               lowerMessage.contains("no access points with valid status found") ||
               (lowerMessage.contains("insufficient") && lowerMessage.contains("access point"));
    }
    
    /**
     * Location type enumeration for positioning analysis.
     */
    public enum LocationType {
        WIFI("WIFI", "WiFi-based positioning"),
        CELL("CELL", "Cell tower-based positioning"), 
        NONE("NONE", "No positioning successful"),
        DYNAMIC("DYNAMIC", "Type determined dynamically based on accuracy"),
        UNKNOWN("UNKNOWN", "Unknown positioning type");
        
        private final String code;
        private final String description;
        
        LocationType(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
        
        /**
         * Determines location type based on VLSS accuracy for dynamic scenarios.
         * 
         * @param vlssAccuracy VLSS accuracy in meters
         * @return WiFi if accuracy < 250m, CELL if >= 250m
         */
        public static LocationType fromVlssAccuracy(Double vlssAccuracy) {
            if (vlssAccuracy == null) {
                return UNKNOWN;
            }
            return vlssAccuracy < 250.0 ? WIFI : CELL;
        }
    }
}
