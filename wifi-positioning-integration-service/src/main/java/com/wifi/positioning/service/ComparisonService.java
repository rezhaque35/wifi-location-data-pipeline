// com/wifi/positioning/service/ComparisonService.java
package com.wifi.positioning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Streamlined service for VLSS vs Frisco comparison based on requirements.
 * Focuses only on the 5 core requirements for Splunk dashboard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComparisonService {

    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final String WIFI_POSITION_FIELD = "wifiPosition";
    private static final String RESULT_FIELD = "result";
    private static final String SUCCESS_VALUE = "SUCCESS";

    private final ObjectMapper objectMapper;

    /**
     * Main comparison method - streamlined for requirements only.
     */
    public ComparisonMetrics compareResults(SourceResponse sourceResponse, Object positioningServiceResponse,
            List<WifiInfo> originalWifiInfo, List<CellInfo> cellInfo) {

        ComparisonMetrics metrics = new ComparisonMetrics();

        // Determine service success/failure
        Boolean vlssSuccess = determineVlssSuccess(sourceResponse);
        Boolean friscoSuccess = determineFriscoSuccess(positioningServiceResponse);

        metrics.setVlssSuccess(vlssSuccess);
        metrics.setFriscoSuccess(friscoSuccess);

        // Determine scenario and location type
        String friscoErrorMessage = extractFriscoErrorMessage(positioningServiceResponse);
        ComparisonScenario scenario = ComparisonScenario.determineScenario(vlssSuccess, friscoSuccess,
                friscoErrorMessage);
        metrics.setScenario(scenario);

        // Set location type
        setLocationType(metrics, scenario, sourceResponse);

        // Set positioning method
        PositioningMethod method = PositioningMethod.determineMethod(scenario, null);
        metrics.setPositioningMethod(method);

        // === 1. Input Data Quality ===
        setInputDataQuality(metrics, originalWifiInfo, positioningServiceResponse);

        // === 2. AP Data Quality ===
        setApDataQuality(metrics, positioningServiceResponse);

        // === 3. Algorithm Usage ===
        setAlgorithmUsage(metrics, positioningServiceResponse);

        // === 4. Frisco Service Performance ===
        setFriscoPerformance(metrics, positioningServiceResponse);

        // === 5. VLSS vs Frisco Performance ===
        setVlssFriscoComparison(metrics, sourceResponse, positioningServiceResponse);

        // Basic service info
        if (cellInfo != null) {
            metrics.setRequestCellCount(cellInfo.size());
        }

        // Set legacy fields for backwards compatibility
        setLegacyFields(metrics, positioningServiceResponse);

        return metrics;
    }

    // === Requirement 1: Input Data Quality ===

    private void setInputDataQuality(ComparisonMetrics metrics, List<WifiInfo> originalWifiInfo,
            Object positioningServiceResponse) {
        // Set request AP count
        if (originalWifiInfo != null) {
            metrics.setRequestApCount(originalWifiInfo.size());
        }

        // Extract selection context from Frisco calculationInfo
        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            JsonNode calculationInfo = node.get("calculationInfo");
            if (calculationInfo != null) {
                JsonNode selectionContext = calculationInfo.get("selectionContext");
                if (selectionContext != null) {
                    metrics.setSelectionContextInfo(objectMapper.convertValue(selectionContext, Map.class));
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract selection context: {}", e.getMessage());
        }
    }

    // === Requirement 2: AP Data Quality ===

    private void setApDataQuality(ComparisonMetrics metrics, Object positioningServiceResponse) {
        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            JsonNode calculationInfo = node.get("calculationInfo");
            if (calculationInfo != null) {

                // Extract access points
                JsonNode accessPoints = calculationInfo.get("accessPoints");
                if (accessPoints != null && accessPoints.isArray()) {
                    metrics.setCalculationAccessPoints(objectMapper.convertValue(accessPoints, List.class));
                }

                // Extract access point summary
                JsonNode accessPointSummary = calculationInfo.get("accessPointSummary");
                if (accessPointSummary != null) {
                    metrics.setCalculationAccessPointSummary(objectMapper.convertValue(accessPointSummary, Map.class));

                    // Calculate status ratio
                    calculateStatusRatio(metrics, accessPointSummary);
                }

                // Extract quality factors from selectionContext
                JsonNode selectionContext = calculationInfo.get("selectionContext");
                if (selectionContext != null) {
                    extractQualityFactors(metrics, selectionContext);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract AP data quality info: {}", e.getMessage());
        }
    }

    private void calculateStatusRatio(ComparisonMetrics metrics, JsonNode accessPointSummary) {
        try {
            JsonNode totalNode = accessPointSummary.get("total");
            JsonNode usedNode = accessPointSummary.get("used");

            if (totalNode != null && usedNode != null && totalNode.asInt() > 0) {
                double ratio = (double) usedNode.asInt() / totalNode.asInt();
                metrics.setStatusRatio(ratio);
            }
        } catch (Exception e) {
            log.debug("Could not calculate status ratio: {}", e.getMessage());
        }
    }

    private void extractQualityFactors(ComparisonMetrics metrics, JsonNode selectionContext) {
        try {
            JsonNode geometricFactor = selectionContext.get("geometricQuality");
            if (geometricFactor != null) {
                metrics.setGeometricQualityFactor(geometricFactor.asText());
            }

            JsonNode signalFactor = selectionContext.get("signalQuality");
            if (signalFactor != null) {
                metrics.setSignalQualityFactor(signalFactor.asText());
            }

            JsonNode distributionFactor = selectionContext.get("signalDistribution");
            if (distributionFactor != null) {
                metrics.setSignalDistributionFactor(distributionFactor.asText());
            }
        } catch (Exception e) {
            log.debug("Could not extract quality factors: {}", e.getMessage());
        }
    }

    // === Requirement 3: Algorithm Usage ===

    private void setAlgorithmUsage(ComparisonMetrics metrics, Object positioningServiceResponse) {
        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            JsonNode wifiPosition = node.get(WIFI_POSITION_FIELD);
            if (wifiPosition != null) {
                JsonNode methodsNode = wifiPosition.get("methodsUsed");
                if (methodsNode != null && methodsNode.isArray()) {
                    List<String> methods = new ArrayList<>();
                    methodsNode.forEach(method -> methods.add(method.asText()));
                    metrics.setFriscoMethodsUsed(methods);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract algorithm usage: {}", e.getMessage());
        }
    }

    // === Requirement 4: Frisco Service Performance ===

    private void setFriscoPerformance(ComparisonMetrics metrics, Object positioningServiceResponse) {
        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);

            // Check if successful
            String result = getTextValue(node, RESULT_FIELD);
            if (SUCCESS_VALUE.equals(result)) {
                JsonNode wifiPosition = node.get(WIFI_POSITION_FIELD);
                if (wifiPosition != null) {
                    metrics.setFriscoAccuracy(getDoubleValue(wifiPosition, "horizontalAccuracy"));
                    metrics.setFriscoConfidence(getDoubleValue(wifiPosition, "confidence"));
                    
                    // Extract calculation time from Frisco response
                    metrics.setFriscoCalculationTimeMs(getLongValue(wifiPosition, "calculationTimeMs"));
                }
            } else {
                // Set error details for failed cases
                String message = getTextValue(node, "message");
                if (message != null) {
                    metrics.setFriscoErrorDetails(message);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract Frisco performance: {}", e.getMessage());
        }
    }

    // === Requirement 5: VLSS vs Frisco Service Performance ===

    private void setVlssFriscoComparison(ComparisonMetrics metrics, SourceResponse sourceResponse,
            Object positioningServiceResponse) {
        // Extract VLSS accuracy and confidence
        if (sourceResponse != null && sourceResponse.getLocationInfo() != null) {
            metrics.setVlssAccuracy(sourceResponse.getLocationInfo().getAccuracy());
            metrics.setVlssConfidence(sourceResponse.getLocationInfo().getConfidence());
        }

        // Extract VLSS error details if failed
        if (Boolean.FALSE.equals(metrics.getVlssSuccess()) && sourceResponse != null) {
            metrics.setVlssErrorDetails(sourceResponse.getErrorMessage());
        }

        // Perform comparison analysis when VLSS succeeds (regardless of Frisco status)
        if (Boolean.TRUE.equals(metrics.getVlssSuccess())) {
            performComparisonAnalysis(metrics, sourceResponse, positioningServiceResponse);
        }
    }

    private void performComparisonAnalysis(ComparisonMetrics metrics, SourceResponse sourceResponse,
            Object positioningServiceResponse) {
        try {
            // Extract VLSS accuracy first (needed for special scenario handling)
            Double vlssAcc = null;
            if (sourceResponse != null && sourceResponse.getLocationInfo() != null) {
                vlssAcc = sourceResponse.getLocationInfo().getAccuracy();
            }
            
            // Handle special scenarios first
            if (metrics.getScenario() == ComparisonScenario.VLSS_CELL_FALLBACK_DETECTED) {
                // VLSS succeeded with cell fallback, Frisco failed due to no APs found
                metrics.setAgreementAnalysis("NO WIFI COVERAGE");
                return;
            } else if (metrics.getScenario() == ComparisonScenario.VLSS_SUCCESS_FRISCO_ERROR) {
                // VLSS succeeded but Frisco had other errors (not no AP found)
                if (vlssAcc != null && vlssAcc < 250.0) {
                    metrics.setAgreementAnalysis("FRISCO FAILURE");
                } else {
                    metrics.setAgreementAnalysis("NO WIFI COVERAGE");
                }
                return;
            }
            
            // Get positions
            Double vlssLat = sourceResponse.getLocationInfo().getLatitude();
            Double vlssLon = sourceResponse.getLocationInfo().getLongitude();

            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            JsonNode wifiPosition = node.get(WIFI_POSITION_FIELD);

            Double friscoLat = getDoubleValue(wifiPosition, "latitude");
            Double friscoLon = getDoubleValue(wifiPosition, "longitude");
            Double friscoAcc = getDoubleValue(wifiPosition, "horizontalAccuracy");

            if (vlssLat != null && vlssLon != null && friscoLat != null && friscoLon != null) {
                // Calculate haversine distance
                double distance = calculateHaversineDistance(vlssLat, vlssLon, friscoLat, friscoLon);
                metrics.setHaversineDistanceMeters(distance);

                // Calculate expected uncertainty
                if (vlssAcc != null && friscoAcc != null) {
                    double expectedUncertainty = Math.sqrt(vlssAcc * vlssAcc + friscoAcc * friscoAcc);
                    metrics.setExpectedUncertaintyMeters(expectedUncertainty);

                    // Perform confidence analysis
                    performConfidenceAnalysis(metrics, distance, expectedUncertainty, vlssAcc, friscoAcc);
                }
            }
        } catch (Exception e) {
            log.debug("Could not perform comparison analysis: {}", e.getMessage());
        }
    }

        private void performConfidenceAnalysis(ComparisonMetrics metrics, double distance, double expectedUncertainty, 
            Double vlssAcc, Double friscoAcc) {

        // Handle perfect agreement first
        if (distance == 0.0) {
            metrics.setAgreementAnalysis("PERFECT AGREEMENT");
            if (friscoAcc != null && friscoAcc > 0) {
                metrics.setConfidenceRatio(0.0);
            }
            return;
        }

        // Check for VLSS cell vs WiFi disagreement
        if (vlssAcc != null && vlssAcc >= 250.0) {
            metrics.setAgreementAnalysis("WIFI VS CELL DISAGREEMENT");
            return;
        }

        // Calculate Frisco confidence ratio for all cases (always useful for analysis)
        if (friscoAcc != null && friscoAcc > 0) {
            double confidenceRatio = distance / friscoAcc;
            metrics.setConfidenceRatio(confidenceRatio);
        }

        // Check if within expected uncertainty (good agreement)
        if (distance < expectedUncertainty) {
            metrics.setAgreementAnalysis("GOOD AGREEMENT");
            return;
        }

        // Determine overconfidence scenarios
        if (friscoAcc != null && friscoAcc > 0) {
            double confidenceRatio = distance / friscoAcc;
            if (confidenceRatio > 0 && confidenceRatio <= 1.0) {
                metrics.setAgreementAnalysis("FRISCO WITHIN BOUNDS");
            } else if (confidenceRatio > 1.0 && confidenceRatio <= 1.5) {
                metrics.setAgreementAnalysis("FRISCO MODERATELY OVERCONFIDENT");
            } else if (confidenceRatio > 1.5 && confidenceRatio <= 2.5) {
                metrics.setAgreementAnalysis("FRISCO OVERCONFIDENT");
            } else if (confidenceRatio > 2.5) {
                metrics.setAgreementAnalysis("FRISCO EXTREMELY OVERCONFIDENT");
            }
        }
    }





    // === Helper Methods ===

    @SuppressWarnings("java:S2447") // Null return is intentional to distinguish "not provided" from "failed"
    private Boolean determineVlssSuccess(SourceResponse sourceResponse) {
        if (sourceResponse == null) {
            return null; // No VLSS response provided
        }
        return Boolean.TRUE.equals(sourceResponse.getSuccess());
    }

    private Boolean determineFriscoSuccess(Object positioningServiceResponse) {
        if (positioningServiceResponse == null) {
            return false;
        }

        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            String result = getTextValue(node, RESULT_FIELD);
            return SUCCESS_VALUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractFriscoErrorMessage(Object positioningServiceResponse) {
        if (positioningServiceResponse == null) {
            return null;
        }

        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            return getTextValue(node, "message");
        } catch (Exception e) {
            return null;
        }
    }

    private void setLocationType(ComparisonMetrics metrics, ComparisonScenario scenario,
            SourceResponse sourceResponse) {
        ComparisonScenario.LocationType locationType = scenario.getLocationType();

        // Handle dynamic location type based on VLSS accuracy
        if (locationType == ComparisonScenario.LocationType.DYNAMIC && sourceResponse != null &&
                sourceResponse.getLocationInfo() != null) {
            locationType = ComparisonScenario.LocationType
                    .fromVlssAccuracy(sourceResponse.getLocationInfo().getAccuracy());
        }

        metrics.setLocationType(locationType);
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        double dlat = lat2Rad - lat1Rad;
        double dlon = lon2Rad - lon1Rad;
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    @SuppressWarnings("deprecation") // Intentionally using deprecated methods for backwards compatibility
    private void setLegacyFields(ComparisonMetrics metrics, Object positioningServiceResponse) {
        try {
            JsonNode node = objectMapper.valueToTree(positioningServiceResponse);
            JsonNode wifiPosition = node.get(WIFI_POSITION_FIELD);
            if (wifiPosition != null) {
                metrics.setMethodsUsed(metrics.getFriscoMethodsUsed());
                metrics.setApCount(getIntegerValue(wifiPosition, "apCount"));
                metrics.setCalculationTimeMs(getLongValue(wifiPosition, "calculationTimeMs"));
            }
        } catch (Exception e) {
            log.debug("Could not set legacy fields: {}", e.getMessage());
        }
    }

    // JSON helper methods
    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asText(null) : null;
    }

    private Double getDoubleValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asDouble() : null;
    }

    private Integer getIntegerValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asInt() : null;
    }

    private Long getLongValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asLong() : null;
    }

    // === Legacy Method Overloads ===

    public ComparisonMetrics compareResults(SourceResponse sourceResponse, Object positioningServiceResponse) {
        return compareResults(sourceResponse, positioningServiceResponse, null, null);
    }

    public ComparisonMetrics compareResults(SourceResponse sourceResponse, Object positioningServiceResponse,
            List<WifiInfo> originalWifiInfo) {
        return compareResults(sourceResponse, positioningServiceResponse, originalWifiInfo, null);
    }
}
