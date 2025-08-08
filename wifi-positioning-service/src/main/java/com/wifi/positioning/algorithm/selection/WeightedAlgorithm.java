package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.PositioningAlgorithm;

/**
 * Represents a positioning algorithm with its associated weight and selection reason. Used to track
 * which algorithms are selected, their relative importance, and why they were selected.
 */
public record WeightedAlgorithm(PositioningAlgorithm algorithm, double weight, String reason) {
  /**
   * Constructor that provides only algorithm and weight without a reason.
   *
   * @param algorithm The positioning algorithm
   * @param weight The calculated weight for this algorithm
   */
  public WeightedAlgorithm(PositioningAlgorithm algorithm, double weight) {
    this(algorithm, weight, null);
  }
}
