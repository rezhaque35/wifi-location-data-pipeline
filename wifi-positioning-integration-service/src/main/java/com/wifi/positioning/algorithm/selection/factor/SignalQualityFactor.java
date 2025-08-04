package com.wifi.positioning.algorithm.selection.factor;

import com.wifi.positioning.dto.WifiScanResult;
import java.util.List;

/**
 * Enum representing different signal quality scenarios that affect algorithm weights.
 * Based on the algorithm selection framework documentation.
 * 
 * SIGNAL QUALITY CLASSIFICATION METHODOLOGY:
 * 
 * This classification is based on industry-standard RSSI threshold definitions that
 * directly correlate with signal reliability and positioning algorithm performance.
 * 
 * INDUSTRY STANDARD REFERENCE:
 * [1] Teltonika Networks: "RSSI (Received Signal Strength Indicator)"
 *     https://wiki.teltonika-networks.com/view/RSSI
 *     - Industry-standard RSSI classification for cellular and WiFi signals
 * 
 * THRESHOLD CLASSIFICATION (Based on Industry Standards):
 * 
 * STRONG_SIGNAL (> -70 dBm):
 * - Industry Classification: "Excellent" signal strength
 * - Characteristics: Strong signal with maximum data speeds
 * - Positioning Impact: Optimal for all algorithms, especially precision methods
 * - Distance Estimation: Highly reliable for trilateration and log-distance modeling
 * - Algorithm Preference: Maximum Likelihood, Trilateration (enhanced weights)
 * 
 * MEDIUM_SIGNAL (-70 to -85 dBm):
 * - Industry Classification: "Good" to "Fair" signal strength
 * - Characteristics: Strong signal with good data speeds, reliable connectivity
 * - Positioning Impact: Good for most algorithms with moderate confidence
 * - Distance Estimation: Reliable for centroid and ratio-based methods
 * - Algorithm Preference: Weighted Centroid, RSSI Ratio (standard weights)
 * 
 * WEAK_SIGNAL (-85 to -95 dBm):
 * - Industry Classification: "Fair" to "Poor" signal strength
 * - Characteristics: Marginal data with potential drop-outs possible
 * - Positioning Impact: Limited algorithm reliability, reduced confidence
 * - Distance Estimation: Less reliable, subject to multipath and noise
 * - Algorithm Preference: Proximity, Weighted Centroid (reduced precision weights)
 * 
 * VERY_WEAK_SIGNAL (< -95 dBm):
 * - Industry Classification: "Poor" to "No signal"
 * - Characteristics: Performance drops drastically, approaching disconnection
 * - Positioning Impact: Only proximity-based methods viable
 * - Distance Estimation: Unreliable for distance-based algorithms
 * - Algorithm Preference: Proximity only (all other algorithms disabled)
 * 
 * THRESHOLD RATIONALE:
 * - -70 dBm threshold: Industry boundary between "excellent" and "good" signal
 * - -85 dBm threshold: Industry boundary between "good" and "fair" signal
 * - -95 dBm threshold: Industry boundary between "fair" and "poor" signal
 * - These thresholds are validated across multiple cellular and WiFi technologies
 * 
 * ALGORITHM SELECTION IMPACT:
 * Signal quality directly influences algorithm weight multipliers:
 * - Strong signals: Enable all algorithms with enhanced precision weights
 * - Medium signals: Standard algorithm weights with good reliability
 * - Weak signals: Reduced weights for precision algorithms, favor robust methods
 * - Very weak signals: Only proximity algorithm enabled for safety
 * 
 * CROSS-TECHNOLOGY VALIDATION:
 * These thresholds are consistent across WiFi (802.11) and cellular technologies,
 * providing a standardized approach to signal quality assessment that aligns
 * with industry best practices and equipment manufacturer specifications.
 */
public enum SignalQualityFactor {
    /** Strong signals (better than -70 dBm) */
    STRONG_SIGNAL(Double.NEGATIVE_INFINITY, -70.0),
    
    /** Medium signals (between -70 and -85 dBm) */
    MEDIUM_SIGNAL(-70.0, -85.0),
    
    /** Weak signals (between -85 and -95 dBm) */
    WEAK_SIGNAL(-85.0, -95.0),
    
    /** Very weak signals (worse than -95 dBm) */
    VERY_WEAK_SIGNAL(-95.0, Double.POSITIVE_INFINITY);
    
    private final double upperBound;
    private final double lowerBound;
    
    SignalQualityFactor(double upperBound, double lowerBound) {
        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
    }
    
    /**
     * Determine the appropriate signal quality factor based on the average signal strength.
     * 
     * @param signalStrength The signal strength in dBm
     * @return The corresponding SignalQualityFactor
     */
    public static SignalQualityFactor fromSignalStrength(double signalStrength) {
        if (signalStrength > -70.0) {
            return STRONG_SIGNAL;
        } else if (signalStrength > -85.0) {
            return MEDIUM_SIGNAL;
        } else if (signalStrength > -95.0) {
            return WEAK_SIGNAL;
        } else {
            return VERY_WEAK_SIGNAL;
        }
    }
    
    /**
     * Calculate the average signal quality from a list of WiFi scan results.
     * 
     * @param wifiScans List of WiFi scan results
     * @return The corresponding SignalQualityFactor based on average signal strength
     */
    public static SignalQualityFactor fromWifiScans(List<WifiScanResult> wifiScans) {
        if (wifiScans == null || wifiScans.isEmpty()) {
            return MEDIUM_SIGNAL;
        }
        
        double avgSignalStrength = wifiScans.stream()
                .mapToDouble(WifiScanResult::signalStrength)
                .average()
                .orElse(-80.0); // Default to medium if no signal
                
        return fromSignalStrength(avgSignalStrength);
    }
} 