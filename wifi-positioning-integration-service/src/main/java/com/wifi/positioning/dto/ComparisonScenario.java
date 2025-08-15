// com/wifi/positioning/dto/ComparisonScenario.java
package com.wifi.positioning.dto;

/**
 * Enumeration of cross-service comparison scenarios based on VLSS vs Frisco results.
 * These scenarios provide insights into when different positioning methods are used.
 */
public enum ComparisonScenario {
    
    /**
     * Both VLSS and Frisco services succeeded in positioning.
     * This indicates both services found WiFi APs in the shared database.
     * Standard WiFi positioning comparison scenario.
     */
    BOTH_WIFI_SUCCESS("BOTH_WIFI_SUCCESS", "Both services succeeded using WiFi positioning"),
    
    /**
     * VLSS succeeded but Frisco failed due to insufficient WiFi data.
     * This strongly indicates VLSS used cell tower fallback positioning.
     * Key scenario for detecting cell tower usage.
     */
    VLSS_CELL_FALLBACK_DETECTED("VLSS_CELL_FALLBACK_DETECTED", "VLSS succeeded (likely using cell towers), Frisco failed"),
    
    /**
     * Both services failed to determine position.
     * Indicates insufficient WiFi APs in database and insufficient cell tower data.
     */
    BOTH_INSUFFICIENT_DATA("BOTH_INSUFFICIENT_DATA", "Both services failed due to insufficient data"),
    
    /**
     * VLSS failed but Frisco succeeded.
     * Unexpected scenario since both use same AP database.
     * May indicate VLSS service issue or different filtering logic.
     */
    VLSS_ERROR_FRISCO_SUCCESS("VLSS_ERROR_FRISCO_SUCCESS", "VLSS failed but Frisco succeeded (unexpected)"),
    
    /**
     * VLSS was not provided (sourceResponse is null).
     * Only Frisco positioning was performed for analysis.
     */
    FRISCO_ONLY_ANALYSIS("FRISCO_ONLY_ANALYSIS", "Only Frisco service result available for analysis"),
    
    /**
     * Both services provided responses but in unexpected combinations.
     * Requires further investigation.
     */
    UNKNOWN_SCENARIO("UNKNOWN_SCENARIO", "Unknown or unexpected response combination");
    
    private final String code;
    private final String description;
    
    ComparisonScenario(String code, String description) {
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
     * Determines the comparison scenario based on service results.
     * 
     * @param vlssSuccess Whether VLSS succeeded (null if no VLSS response)
     * @param friscoSuccess Whether Frisco succeeded
     * @return The appropriate comparison scenario
     */
    public static ComparisonScenario determineScenario(Boolean vlssSuccess, Boolean friscoSuccess) {
        // Handle case where VLSS response is not provided
        if (vlssSuccess == null) {
            return FRISCO_ONLY_ANALYSIS;
        }
        
        // Determine scenario based on success/failure combinations
        boolean vlssSucceeded = Boolean.TRUE.equals(vlssSuccess);
        boolean friscoSucceeded = Boolean.TRUE.equals(friscoSuccess);
        
        if (vlssSucceeded && friscoSucceeded) {
            return BOTH_WIFI_SUCCESS;
        } else if (vlssSucceeded && !friscoSucceeded) {
            return VLSS_CELL_FALLBACK_DETECTED;
        } else if (!vlssSucceeded && !friscoSucceeded) {
            return BOTH_INSUFFICIENT_DATA;
        } else if (!vlssSucceeded && friscoSucceeded) {
            return VLSS_ERROR_FRISCO_SUCCESS;
        }
        
        return UNKNOWN_SCENARIO;
    }
}
