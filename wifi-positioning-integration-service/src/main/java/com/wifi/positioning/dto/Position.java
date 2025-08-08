package com.wifi.positioning.dto;

public record Position(
    Double latitude, Double longitude, Double altitude, Double accuracy, Double confidence) {
  public Position {
    if (latitude != null && (latitude < -90 || latitude > 90)) {
      throw new IllegalArgumentException("Invalid latitude value");
    }
    if (longitude != null && (longitude < -180 || longitude > 180)) {
      throw new IllegalArgumentException("Invalid longitude value");
    }
    if (accuracy != null && accuracy < 0) {
      throw new IllegalArgumentException("Invalid accuracy value");
    }
    if (confidence != null && (confidence < 0 || confidence > 1)) {
      throw new IllegalArgumentException("Confidence must be between 0 and 1");
    }
  }

  public static Position of(double latitude, double longitude) {
    return new Position(latitude, longitude, null, 1.0, 1.0);
  }

  /**
   * Validates if the position coordinates are valid. A position is considered valid if: - Latitude
   * is not null and not NaN - Longitude is not null and not NaN
   *
   * <p>Note: This method only checks for null and NaN values. The constructor already validates the
   * range constraints (-90 to 90 for latitude, -180 to 180 for longitude).
   *
   * @return true if both latitude and longitude are valid numbers, false otherwise
   */
  public boolean isValid() {
    return latitude != null
        && longitude != null
        && !Double.isNaN(latitude)
        && !Double.isNaN(longitude);
  }
}
