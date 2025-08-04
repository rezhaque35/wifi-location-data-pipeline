package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.algorithm.util.GDOPCalculator;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Evaluates geometry, signal quality, and location certainty to build a selection context.
 * 
 * The builder includes methods to determine:
 * - Geometric Quality Factor: Based on AP geometry/GDOP calculation
 * - Signal Distribution Factor: Based on standard deviation of signal strengths
 * - Signal Quality Factor: Based on average signal strength
 */
@Component
public class SelectionContextBuilder {
    
    /**
     * Evaluate scenario characteristics and build a selection context.
     * 
     * @param validScans List of valid WiFi scan results
     * @param apMap Map of known access points by MAC address
     * @return The selection context with scenario characteristics
     */
    public SelectionContext buildContext(List<WifiScanResult> validScans, Map<String, WifiAccessPoint> apMap) {
        // Calculate selection factors
        GeometricQualityFactor geometricQuality = GeometricQualityFactor.determineGeometricQuality(validScans, apMap);
        SignalQualityFactor signalQualityFactor = determineSignalQuality(validScans);
        SignalDistributionFactor distributionFactor = determineSignalDistribution(validScans);
        
        // Calculate AP count factor
        long apCount = validScans.stream()
            .map(WifiScanResult::macAddress)
            .distinct()
            .count();
        
        // Build context
        return SelectionContext.builder()
                .geometricQuality(geometricQuality)
                .signalQuality(signalQualityFactor)
                .signalDistribution(distributionFactor)
                .apCountFactor(APCountFactor.fromCount((int)apCount))
                .build();
    }
    
    /**
     * Determines the signal distribution factor based on signal strength variation.
     * 
     * This method assesses how signal strengths vary across different APs by calculating
     * the standard deviation of signal strengths and categorizing:
     * - UNIFORM_SIGNALS: standard deviation < 3.0 dB (signals are consistent)
     * - MIXED_SIGNALS: 3.0 ≤ standard deviation < 10.0 dB (moderate variation)
     * - SIGNAL_OUTLIERS: standard deviation ≥ 10.0 dB (significant variation/outliers)
     * 
     * @param wifiScans List of WiFi scan results
     * @return The corresponding SignalDistributionFactor
     */
    public SignalDistributionFactor determineSignalDistribution(List<WifiScanResult> wifiScans) {
        return SignalDistributionFactor.fromWifiScans(wifiScans);
    }
    
    /**
     * Determines the signal quality factor based on average signal strength.
     * 
     * This method assesses the overall signal quality by averaging signal strengths
     * and categorizing:
     * - STRONG_SIGNAL: average > -70 dBm (good signal quality)
     * - MEDIUM_SIGNAL: -70 dBm ≥ average ≥ -85 dBm (moderate signal quality)
     * - WEAK_SIGNAL: average < -85 dBm (poor signal quality)
     * 
     * @param wifiScans List of WiFi scan results
     * @return The corresponding SignalQualityFactor
     */
    public SignalQualityFactor determineSignalQuality(List<WifiScanResult> wifiScans) {
        return SignalQualityFactor.fromWifiScans(wifiScans);
    }

    private double evaluateAPLocationCertainty(List<WifiScanResult> validScans, 
                                            Map<String, WifiAccessPoint> apMap) {
        double totalConfidence = 0;
        
        for (WifiScanResult scan : validScans) {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            if (ap != null && ap.getConfidence() != null) {
                totalConfidence += ap.getConfidence();
            }
        }
        
        return validScans.size() > 0 ? totalConfidence / validScans.size() : 0;
    }
} 