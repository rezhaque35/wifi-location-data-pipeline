package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;

import lombok.Builder;
import lombok.Data;

/**
 * Holds context information about the current positioning scenario. This context is shared between
 * different selection rules and can be enriched as the selection process progresses.
 *
 * <p>The context includes factors that affect algorithm selection and weighting: - AP count: Number
 * of available access points - Signal quality: Overall signal strength (STRONG/MEDIUM/WEAK) -
 * Signal distribution: Variation in signal strengths (UNIFORM/MIXED/OUTLIERS) - Geometric quality:
 * AP arrangement and GDOP, including detection of collinear arrangements
 */
@Data
@Builder
public class SelectionContext {
  /**
   * AP count factor based on the number of available access points. SINGLE_AP: 1 AP TWO_APS: 2 APs
   * THREE_APS: 3 APs FOUR_PLUS_APS: 4 or more APs
   *
   * <p>This factor is crucial for algorithm selection as different algorithms perform optimally
   * with different numbers of APs.
   */
  private APCountFactor apCountFactor;

  /**
   * Signal quality factor based on average signal strength. STRONG_SIGNAL: > -70 dBm MEDIUM_SIGNAL:
   * -70 to -85 dBm WEAK_SIGNAL: < -85 dBm
   */
  private SignalQualityFactor signalQuality;

  /**
   * Signal distribution factor based on signal strength variation. UNIFORM_SIGNALS: std dev < 3.0
   * dB MIXED_SIGNALS: std dev 3.0-10.0 dB SIGNAL_OUTLIERS: std dev > 10.0 dB
   */
  private SignalDistributionFactor signalDistribution;

  /**
   * Geometric quality factor based on GDOP (Geometric Dilution of Precision). EXCELLENT_GDOP: GDOP
   * < 2.0 GOOD_GDOP: GDOP 2.0-4.0 FAIR_GDOP: GDOP 4.0-6.0 POOR_GDOP: GDOP > 6.0 COLLINEAR: Special
   * case where APs lie approximately on a straight line
   */
  private GeometricQualityFactor geometricQuality;
}
