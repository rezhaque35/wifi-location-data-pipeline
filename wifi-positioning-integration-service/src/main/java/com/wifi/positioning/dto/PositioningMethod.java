// com/wifi/positioning/dto/PositioningMethod.java
package com.wifi.positioning.dto;

/**
 * Enumeration of positioning methods detected during comparison analysis.
 */
public enum PositioningMethod {
    
    /**
     * Both services used WiFi access point positioning.
     */
    WIFI_ONLY("WIFI_ONLY", "WiFi access point positioning"),
    
    /**
     * VLSS used cell tower fallback positioning while Frisco used WiFi.
     * Detected when VLSS succeeds but Frisco fails due to insufficient WiFi APs.
     */
    CELL_FALLBACK("CELL_FALLBACK", "Cell tower fallback positioning (VLSS)"),
    
    /**
     * VLSS potentially used hybrid positioning (WiFi + Cell towers).
     * Detected when both succeed but with significantly different results.
     */
    HYBRID_SUSPECTED("HYBRID_SUSPECTED", "Suspected hybrid WiFi + cell tower positioning"),
    
    /**
     * Positioning method could not be determined due to insufficient data.
     */
    UNDETERMINED("UNDETERMINED", "Positioning method could not be determined"),
    
    /**
     * No positioning was successful by either service.
     */
    NONE("NONE", "No successful positioning");
    
    private final String code;
    private final String description;
    
    PositioningMethod(String code, String description) {
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
     * Determines the positioning method based on comparison scenario and metrics.
     * 
     * @param scenario The comparison scenario
     * @param haversineDistance Distance between positions (if both succeeded)
     * @return The detected positioning method
     */
    public static PositioningMethod determineMethod(ComparisonScenario scenario, Double haversineDistance) {
        switch (scenario) {
            case BOTH_WIFI_SUCCESS:
                // If distance is very large, might indicate hybrid vs pure WiFi
                if (haversineDistance != null && haversineDistance > 1000.0) { // 1km threshold
                    return HYBRID_SUSPECTED;
                }
                return WIFI_ONLY;
                
            case VLSS_CELL_FALLBACK_DETECTED:
                return CELL_FALLBACK;
                
            case BOTH_INSUFFICIENT_DATA:
                return NONE;
                
            case VLSS_ERROR_FRISCO_SUCCESS:
            case FRISCO_ONLY_ANALYSIS:
            case UNKNOWN_SCENARIO:
            default:
                return UNDETERMINED;
        }
    }
}
