package com.wifi.positioning.algorithm;

import com.wifi.positioning.algorithm.impl.*;

/** Enum representing all implemented positioning algorithms. */
public enum PositioningAlgorithmType {
  PROXIMITY(new ProximityDetectionAlgorithm()),
  LOG_DISTANCE(new LogDistancePathLossAlgorithm()),
  RSSI_RATIO(new RSSIRatioAlgorithm()),
  WEIGHTED_CENTROID(new WeightedCentroidAlgorithm()),
  TRILATERATION(new TrilaterationAlgorithm()),
  MAXIMUM_LIKELIHOOD(new MaximumLikelihoodAlgorithm());

  private final PositioningAlgorithm implementation;

  PositioningAlgorithmType(PositioningAlgorithm implementation) {
    this.implementation = implementation;
  }

  public PositioningAlgorithm getImplementation() {
    return implementation;
  }

  public static PositioningAlgorithmType fromName(String name) {
    for (PositioningAlgorithmType type : values()) {
      if (type.getImplementation().getName().equalsIgnoreCase(name)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown algorithm type: " + name);
  }
}
