// com/wifi/positioning/dto/ComparisonMetrics.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Streamlined metrics for VLSS vs Frisco service comparison based on requirements.
 * Focuses only on the 5 core requirements for Splunk dashboard.
 */
@Data
public class ComparisonMetrics {
    
    // === Core Cross-Service Analysis ===
    
    @JsonProperty("scenario")
    private ComparisonScenario scenario;
    
    @JsonProperty("positioningMethod")
    private PositioningMethod positioningMethod;
    
    @JsonProperty("locationType")
    private ComparisonScenario.LocationType locationType;
    
    @JsonProperty("vlssSuccess")
    private Boolean vlssSuccess;
    
    @JsonProperty("friscoSuccess")
    private Boolean friscoSuccess;
    
    // === 1. Input Data Quality ===
    
    /**
     * Number of APs in original request
     */
    @JsonProperty("requestApCount")
    private Integer requestApCount;
    
    /**
     * Selection context from Frisco calculationInfo
     */
    @JsonProperty("selectionContextInfo")
    private Map<String, Object> selectionContextInfo;
    
    // === 2. AP Data Quality ===
    
    /**
     * Access points details from Frisco calculationInfo
     */
    @JsonProperty("calculationAccessPoints")
    private List<Map<String, Object>> calculationAccessPoints;
    
    /**
     * Access point summary from Frisco calculationInfo
     */
    @JsonProperty("calculationAccessPointSummary")
    private Map<String, Object> calculationAccessPointSummary;
    
    /**
     * Status ratio: used count / total count
     */
    @JsonProperty("statusRatio")
    private Double statusRatio;
    
    /**
     * Quality factors from Frisco selectionContext
     */
    @JsonProperty("geometricQualityFactor")
    private String geometricQualityFactor;
    
    @JsonProperty("signalQualityFactor")
    private String signalQualityFactor;
    
    @JsonProperty("signalDistributionFactor")
    private String signalDistributionFactor;
    
    // === 3. Algorithm Usage ===
    
    /**
     * Algorithms used by Frisco service
     */
    @JsonProperty("friscoMethodsUsed")
    private List<String> friscoMethodsUsed;
    
    // === 4. Frisco Service Performance ===
    
    @JsonProperty("friscoAccuracy")
    private Double friscoAccuracy;
    
    @JsonProperty("friscoConfidence")
    private Double friscoConfidence;
    
    @JsonProperty("friscoErrorDetails")
    private String friscoErrorDetails;
    
    /**
     * Frisco service response time in milliseconds
     */
    @JsonProperty("friscoResponseTimeMs")
    private Long friscoResponseTimeMs;
    
    /**
     * Frisco calculation time in milliseconds
     */
    @JsonProperty("friscoCalculationTimeMs")
    private Long friscoCalculationTimeMs;
    
    // === 5. VLSS vs Frisco Service Performance ===
    
    @JsonProperty("vlssAccuracy")
    private Double vlssAccuracy;
    
    @JsonProperty("vlssConfidence")
    private Double vlssConfidence;
    
    /**
     * Haversine distance between positions (when both succeed)
     */
    @JsonProperty("haversineDistanceMeters")
    private Double haversineDistanceMeters;
    
    /**
     * Expected uncertainty = √(VLSS_accuracy² + Frisco_accuracy²)
     */
    @JsonProperty("expectedUncertaintyMeters")
    private Double expectedUncertaintyMeters;
    
    /**
     * Agreement analysis result (GOOD AGREEMENT, FRISCO WITHIN BOUNDS, etc.)
     */
    @JsonProperty("agreementAnalysis")
    private String agreementAnalysis;
    
    /**
     * Confidence ratio = Distance / Reported_Accuracy
     */
    @JsonProperty("confidenceRatio")
    private Double confidenceRatio;
    

    
    // === Basic Service Info (for context) ===
    
    @JsonProperty("requestCellCount")
    private Integer requestCellCount;
    
    @JsonProperty("vlssErrorDetails")
    private String vlssErrorDetails;
    
    // === Legacy Fields (backwards compatibility) ===
    
    /**
     * @deprecated Use friscoMethodsUsed instead
     */
    @JsonProperty("methodsUsed")
    @Deprecated(since = "2.0", forRemoval = true)
    @SuppressWarnings("java:S1133")
    private List<String> methodsUsed;
    
    /**
     * @deprecated Use friscoApCount instead
     */
    @JsonProperty("apCount")
    @Deprecated(since = "2.0", forRemoval = true)
    @SuppressWarnings("java:S1133")
    private Integer apCount;
    
    /**
     * @deprecated Use friscoCalculationTimeMs instead
     */
    @JsonProperty("calculationTimeMs")
    @Deprecated(since = "2.0", forRemoval = true)
    @SuppressWarnings("java:S1133")
    private Long calculationTimeMs;
}