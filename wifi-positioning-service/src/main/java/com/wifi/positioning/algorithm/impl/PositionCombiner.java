package com.wifi.positioning.algorithm.impl;

import java.util.List;

import com.wifi.positioning.dto.Position;

/**
 * Interface for combining position results from multiple algorithms. Implementations can provide
 * different strategies for weighted position averaging.
 */
public interface PositionCombiner {

  /**
   * Combine multiple weighted positions into a single position result.
   *
   * @param positions List of positions with their weights
   * @return The combined position
   */
  Position combinePositions(List<WeightedPosition> positions);

  /** Represents a position result with its associated weight. */
  record WeightedPosition(Position position, double weight) {}
}
