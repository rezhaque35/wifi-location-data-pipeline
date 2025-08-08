package com.wifi.positioning.algorithm;

import java.util.List;

import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;

/**
 * Interface for positioning algorithms that calculate location based on WiFi access points.
 * Includes methods for obtaining algorithm-specific weights based on various factors that affect
 * positioning accuracy.
 */
public interface PositioningAlgorithm {
  /**
   * Calculates the position based on the provided WiFi scan results and known access points.
   *
   * @param wifiScan List of WiFi scan results
   * @param knownAPs List of known WiFi access points
   * @return The calculated position
   */
  Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs);

  /**
   * Returns the confidence level of the algorithm's calculation.
   *
   * @return Confidence value between 0 and 1
   */
  double getConfidence();

  /**
   * Returns the name of the algorithm.
   *
   * @return Algorithm name
   */
  String getName();

  /**
   * Returns the base weight of the algorithm for a given AP count scenario.
   *
   * <p>Implementations should provide weights based on AP count: - Single AP: moderate weight (0.6)
   * - Two APs: increased weight (0.7) - Three APs: good weight (0.8) - Four+ APs: high weight (0.9)
   *
   * @param apCountFactor The AP count factor
   * @return The base weight value for this algorithm
   */
  double getBaseWeight(APCountFactor apCountFactor);

  /**
   * Returns the weight multiplier for this algorithm based on signal quality.
   *
   * <p>Implementations should provide multipliers based on signal quality: - Strong signals (> -70
   * dBm): typically values around 0.9-1.2 - Medium signals (-70 to -85 dBm): typically values
   * around 0.7-1.0 - Weak signals (< -85 dBm): typically values around 0.3-0.8 - Very weak signals
   * (< -95 dBm): typically values around 0.0-0.5
   *
   * @param factor The signal quality factor
   * @return The weight multiplier value (e.g., 0.9, 1.1)
   */
  double getSignalQualityMultiplier(SignalQualityFactor factor);

  /**
   * Returns the weight multiplier for this algorithm based on geometric quality.
   *
   * <p>Implementations should provide multipliers based on GDOP: - Excellent GDOP (< 2): typically
   * values around 1.0-1.3 - Good GDOP (2-4): typically values around 0.9-1.1 - Fair GDOP (4-6):
   * typically values around 0.6-1.2 - Poor GDOP (> 6): typically values around 0.3-1.3
   *
   * @param geometricQualityFactor The geometric quality factor
   * @return The weight multiplier value (e.g., 0.9, 1.1)
   */
  double getGeometricQualityMultiplier(GeometricQualityFactor geometricQualityFactor);

  /**
   * Returns the weight multiplier for this algorithm based on signal distribution.
   *
   * <p>Implementations should provide multipliers based on signal distribution: - Uniform signals:
   * typically values around 0.9-1.2 - Mixed signals: typically values around 0.7-1.3 - Signal
   * outliers: typically values around 0.5-1.4
   *
   * @param factor The signal distribution factor
   * @return The weight multiplier value (e.g., 0.9, 1.1)
   */
  double getSignalDistributionMultiplier(SignalDistributionFactor factor);

  /**
   * Calculates the final weight for this algorithm based on all factors. Default implementation
   * that can be overridden by specific algorithms.
   *
   * @param apCount Number of access points
   * @param wifiScan List of WiFi scan results
   * @param gdop Geometric Dilution of Precision value
   * @return The final calculated weight for this algorithm
   */
  default double calculateWeight(int apCount, List<WifiScanResult> wifiScan, double gdop) {
    APCountFactor apFactor = APCountFactor.fromCount(apCount);
    SignalQualityFactor signalFactor = SignalQualityFactor.fromWifiScans(wifiScan);
    GeometricQualityFactor geoFactor = GeometricQualityFactor.fromGDOP(gdop);
    SignalDistributionFactor distFactor = SignalDistributionFactor.fromWifiScans(wifiScan);

    double baseWeight = getBaseWeight(apFactor);
    double signalMultiplier = getSignalQualityMultiplier(signalFactor);
    double geoMultiplier = getGeometricQualityMultiplier(geoFactor);
    double distMultiplier = getSignalDistributionMultiplier(distFactor);

    return baseWeight * signalMultiplier * geoMultiplier * distMultiplier;
  }
}
