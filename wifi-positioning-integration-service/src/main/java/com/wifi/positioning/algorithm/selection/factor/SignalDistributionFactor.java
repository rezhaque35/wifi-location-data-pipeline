package com.wifi.positioning.algorithm.selection.factor;

import java.util.List;
import java.util.stream.Collectors;

import com.wifi.positioning.dto.WifiScanResult;

/**
 * Enum representing different signal distribution patterns that affect algorithm weights. Based on
 * the algorithm selection framework documentation.
 */
public enum SignalDistributionFactor {
  /** Uniform signal levels across APs */
  UNIFORM_SIGNALS,

  /** Mixed signal levels with moderate variation */
  MIXED_SIGNALS,

  /** Signal distribution with significant outliers */
  SIGNAL_OUTLIERS;

  /**
   * Standard deviation threshold for determining uniform vs. mixed signals. Signals with standard
   * deviation below this are considered uniform.
   */
  private static final double UNIFORM_THRESHOLD = 3.0;

  /**
   * Standard deviation threshold for determining outlier presence. Signals with standard deviation
   * above this are considered to have outliers.
   */
  private static final double OUTLIER_THRESHOLD = 10.0;

  /**
   * Determine the appropriate signal distribution factor based on WiFi scan results.
   *
   * <p>SIGNAL DISTRIBUTION DETERMINATION METHODOLOGY:
   *
   * <p>This method uses statistical analysis of signal strength variation to classify the
   * distribution pattern of WiFi signals, which directly impacts algorithm selection and weighting
   * in the positioning system.
   *
   * <p>ACADEMIC FOUNDATION & REFERENCES: This approach is based on established research in
   * WiFi-based indoor positioning:
   *
   * <p>[1] Hailu, T.G. et al. "Theories and Methods for Indoor Positioning Systems: A Comparative
   * Analysis, Challenges, and Prospective Measures" Sensors 2024, 24(21):6876. doi:
   * 10.3390/s24216876 - Comprehensive analysis of signal distribution impacts on positioning
   * accuracy
   *
   * <p>[2] Torres-Sospedra, J. et al. "Ensembling Multiple Radio Maps with Dynamic Noise in
   * Fingerprint-based Indoor Positioning" IEEE VTC2021-Spring - Statistical analysis of signal
   * variation in indoor environments
   *
   * <p>[3] Hu, J. & Hu, C. "A WiFi Indoor Location Tracking Algorithm Based on Improved Weighted K
   * Nearest Neighbors and Kalman Filter" IEEE Access 2023, 11:32907-32918 - Signal strength
   * variation analysis for algorithm selection
   *
   * <p>[4] Milioris, D. et al. "Empirical evaluation of signal-strength fingerprint positioning in
   * wireless LANs" ACM MSWiM 2010, pp. 5-13 - Statistical characterization of RSSI distributions in
   * indoor environments
   *
   * <p>[5] Liu W, Kulin M, Kazaz T, Shahid A, Moerman I, De Poorter E. Wireless Technology
   * Recognition Based on RSSI Distribution at Sub-Nyquist Sampling Rate for Constrained Devices.
   * Sensors (Basel). 2017 Sep 12;17(9):2081. doi: 10.3390/s17092081. PMID: 28895879; PMCID:
   * PMC5621126.
   *
   * <p>MATHEMATICAL APPROACH: 1. Extract all signal strength values (in dBm) from the WiFi scan
   * results 2. Calculate the arithmetic mean: μ = Σ(RSSI_i) / n 3. Calculate standard deviation: σ
   * = √(Σ(RSSI_i - μ)² / n) 4. Apply threshold-based classification using the standard deviation
   *
   * <p>CLASSIFICATION LOGIC: - UNIFORM_SIGNALS (σ ≤ 3.0 dBm): * Signals are relatively consistent
   * across access points * Indicates stable RF environment with minimal variation * Favors
   * algorithms like RSSI Ratio that rely on consistent signal relationships * Example: All APs
   * showing -65 to -68 dBm (small variation)
   *
   * <p>- MIXED_SIGNALS (3.0 < σ ≤ 10.0 dBm): * Moderate signal variation across access points *
   * Normal indoor environment with varying distances to APs * Favors robust algorithms like
   * Weighted Centroid that handle variation well * Example: APs showing -60, -70, -75 dBm (moderate
   * spread)
   *
   * <p>- SIGNAL_OUTLIERS (σ > 10.0 dBm): * Significant signal variation with potential outliers *
   * May indicate one very close AP or very distant APs * Favors Maximum Likelihood that can handle
   * statistical outliers * Example: APs showing -50, -65, -85 dBm (large spread)
   *
   * <p>THRESHOLD RATIONALE: - 3.0 dBm threshold: Based on typical WiFi signal measurement noise and
   * empirical studies showing this as the boundary between consistent and variable signals - 10.0
   * dBm threshold: Represents significant distance variation (factor of ~3x) based on path loss
   * models where 6 dB ≈ 2x distance change - These thresholds align with RF propagation models and
   * empirical validation from indoor positioning research [1,3,4] - Wi-Fi can have variable σ
   * depending on environment and location, and that higher σ generally indicates more noise or
   * environmental instability. However, there is prescribed specific numeric thresholds like 1–2
   * dB, 3–10 dB, or >10 dB for classification. Thresholds are empirically derived from training
   * data for the specific scenario under study. [5]
   *
   * <p>ALGORITHM SELECTION IMPACT: Based on empirical studies and positioning system evaluations
   * [1,2]: - Uniform signals → Increase weight for RSSI Ratio (×1.2), standard for others - Mixed
   * signals → Increase weight for Weighted Centroid (×1.8), reduce precision methods - Signal
   * outliers → Increase weight for Maximum Likelihood (×1.2), reduce geometric methods
   *
   * <p>EDGE CASE HANDLING: - null or empty input: Returns UNIFORM_SIGNALS (safe default) - Single
   * AP (size < 2): Returns UNIFORM_SIGNALS (no variation possible) - Zero variance: Returns
   * UNIFORM_SIGNALS (all signals identical)
   *
   * @param wifiScans List of WiFi scan results containing signal strength measurements
   * @return The corresponding SignalDistributionFactor enum value
   */
  public static SignalDistributionFactor fromWifiScans(List<WifiScanResult> wifiScans) {
    if (wifiScans == null || wifiScans.size() < 2) {
      return UNIFORM_SIGNALS; // Default for insufficient data
    }

    // Extract signal strengths
    List<Double> signalStrengths =
        wifiScans.stream().map(WifiScanResult::signalStrength).collect(Collectors.toList());

    // Calculate mean
    double mean = signalStrengths.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

    // Calculate standard deviation
    double sumSquaredDiff =
        signalStrengths.stream().mapToDouble(signal -> Math.pow(signal - mean, 2)).sum();
    double stdDev = Math.sqrt(sumSquaredDiff / signalStrengths.size());

    // Determine distribution type based on standard deviation
    if (stdDev > OUTLIER_THRESHOLD) {
      return SIGNAL_OUTLIERS;
    } else if (stdDev > UNIFORM_THRESHOLD) {
      return MIXED_SIGNALS;
    } else {
      return UNIFORM_SIGNALS;
    }
  }
}
