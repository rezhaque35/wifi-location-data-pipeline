package com.wifi.positioning.algorithm.selection.factor;

/**
 * Enum representing different AP count scenarios that affect algorithm weights. Based on the
 * algorithm selection framework documentation.
 */
public enum APCountFactor {
  /** Single AP scenario */
  SINGLE_AP(1),

  /** Two APs scenario */
  TWO_APS(2),

  /** Three APs scenario */
  THREE_APS(3),

  /** Four or more APs scenario */
  FOUR_PLUS_APS(4);

  private final int minimumCount;

  APCountFactor(int minimumCount) {
    this.minimumCount = minimumCount;
  }

  /**
   * Get the minimum AP count for this factor.
   *
   * @return The minimum AP count
   */
  public int getMinimumCount() {
    return minimumCount;
  }

  /**
   * Determine the appropriate AP count factor based on the number of APs.
   *
   * @param apCount The number of access points
   * @return The corresponding APCountFactor
   */
  public static APCountFactor fromCount(int apCount) {
    if (apCount >= 4) {
      return FOUR_PLUS_APS;
    } else if (apCount == 3) {
      return THREE_APS;
    } else if (apCount == 2) {
      return TWO_APS;
    } else {
      return SINGLE_AP;
    }
  }
}
