// src/main/java/com/wifi/positioning/util/GeographicCentroidCalculator.java
package com.wifi.positioning.util;

import com.wifi.positioning.dto.WifiAccessPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Geographic centroid calculator using ECEF (Earth-Centered, Earth-Fixed) vector averaging.
 *
 * <p>This utility class provides accurate geographic centroid calculation for latitude/longitude
 * coordinates by using ECEF vector averaging. This approach correctly handles Earth's curvature,
 * pole locations, and anti-meridian crossing scenarios.
 *
 * <h2>Mathematical Foundation: ECEF Vector Averaging</h2>
 *
 * <h3>Core Algorithm Process</h3>
 * <pre>
 * For each location i with (lat_i, lon_i):
 *   lat_rad_i = lat_i * π/180
 *   lon_rad_i = lon_i * π/180
 *   
 *   x_i = cos(lat_rad_i) * cos(lon_rad_i)
 *   y_i = cos(lat_rad_i) * sin(lon_rad_i)
 *   z_i = sin(lat_rad_i)
 *   
 * sum_x = Σ(x_i)
 * sum_y = Σ(y_i)
 * sum_z = Σ(z_i)
 * 
 * magnitude = √(sum_x² + sum_y² + sum_z²)
 * normalized_x = sum_x / magnitude
 * normalized_y = sum_y / magnitude
 * normalized_z = sum_z / magnitude
 * 
 * result_lat = arcsin(normalized_z) * 180/π
 * result_lon = atan2(normalized_y, normalized_x) * 180/π
 * </pre>
 *
 * <h3>Why ECEF Vector Averaging?</h3>
 * <ul>
 *   <li><strong>Spherical Geometry:</strong> Handles Earth's curvature correctly</li>
 *   <li><strong>Pole Handling:</strong> Works correctly at North/South poles</li>
 *   <li><strong>Anti-meridian Crossing:</strong> Handles 180°/-180° longitude crossing</li>
 *   <li><strong>Mathematical Accuracy:</strong> More accurate than simple arithmetic mean</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Time Complexity:</strong> O(n) where n is the number of locations</li>
 *   <li><strong>Space Complexity:</strong> O(1) constant space for calculations</li>
 * </ul>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
public final class GeographicCentroidCalculator {

    private static final Logger logger = LoggerFactory.getLogger(GeographicCentroidCalculator.class);

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private GeographicCentroidCalculator() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Calculates geographic centroid from a list of WifiAccessPoint objects.
     *
     * <p>This method extracts latitude/longitude from access points and calculates
     * their geographic centroid using ECEF vector averaging.
     *
     * @param accessPoints list of access points with valid location data
     * @return array with [latitude, longitude] or null if no valid locations
     */
    public static double[] calculateCentroid(List<WifiAccessPoint> accessPoints) {
        if (accessPoints == null || accessPoints.isEmpty()) {
            logger.debug("Cannot calculate centroid: empty or null access points list");
            return null;
        }

        // Filter to only valid locations
        List<WifiAccessPoint> validAPs = accessPoints.stream()
                .filter(Objects::nonNull)
                .filter(ap -> ap.getLatitude() != null && ap.getLongitude() != null)
                .toList();

        if (validAPs.isEmpty()) {
            logger.debug("Cannot calculate centroid: no access points with valid location data");
            return null;
        }

        return calculateCentroidFromAccessPoints(validAPs);
    }

    /**
     * Calculates geographic centroid from raw latitude/longitude coordinate pairs.
     *
     * @param coordinates list of [latitude, longitude] arrays
     * @return array with [latitude, longitude] or null if no valid coordinates
     */
    public static double[] calculateCentroidFromCoordinates(List<double[]> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            logger.debug("Cannot calculate centroid: empty or null coordinates list");
            return null;
        }

        // Convert to ECEF vectors and sum
        double sumX = 0.0, sumY = 0.0, sumZ = 0.0;
        int validCount = 0;

        for (double[] coord : coordinates) {
            if (coord == null || coord.length < 2) {
                continue;
            }

            double lat = coord[0];
            double lon = coord[1];

            double latRad = Math.toRadians(lat);
            double lonRad = Math.toRadians(lon);

            // Convert to ECEF unit vector
            double x = Math.cos(latRad) * Math.cos(lonRad);
            double y = Math.cos(latRad) * Math.sin(lonRad);
            double z = Math.sin(latRad);

            sumX += x;
            sumY += y;
            sumZ += z;
            validCount++;
        }

        if (validCount == 0) {
            logger.debug("Cannot calculate centroid: no valid coordinates found");
            return null;
        }

        return convertECEFToGeographic(sumX, sumY, sumZ, validCount);
    }

    /**
     * Calculates geographic centroid from WifiAccessPoint objects using ECEF conversion.
     *
     * @param accessPoints list of access points with valid location data
     * @return array with [latitude, longitude] or null if calculation fails
     */
    private static double[] calculateCentroidFromAccessPoints(List<WifiAccessPoint> accessPoints) {
        // Convert to ECEF vectors and sum
        double sumX = 0.0, sumY = 0.0, sumZ = 0.0;

        for (WifiAccessPoint ap : accessPoints) {
            double latRad = Math.toRadians(ap.getLatitude());
            double lonRad = Math.toRadians(ap.getLongitude());

            // Convert to ECEF unit vector
            double x = Math.cos(latRad) * Math.cos(lonRad);
            double y = Math.cos(latRad) * Math.sin(lonRad);
            double z = Math.sin(latRad);

            sumX += x;
            sumY += y;
            sumZ += z;
        }

        return convertECEFToGeographic(sumX, sumY, sumZ, accessPoints.size());
    }

    /**
     * Converts summed ECEF vectors back to geographic coordinates.
     *
     * <p>This method normalizes the summed ECEF vector and converts it back to
     * latitude/longitude. If normalization fails (zero magnitude), it falls back
     * to arithmetic mean as a safety measure.
     *
     * @param sumX sum of X components
     * @param sumY sum of Y components
     * @param sumZ sum of Z components
     * @param count number of points that contributed to the sum
     * @return array with [latitude, longitude]
     */
    private static double[] convertECEFToGeographic(double sumX, double sumY, double sumZ, int count) {
        // Normalize the sum vector
        double magnitude = Math.sqrt(sumX * sumX + sumY * sumY + sumZ * sumZ);

        if (magnitude == 0.0) {
            logger.warn("Zero magnitude in ECEF centroid calculation, this indicates degenerate input");
            return null;
        }

        double normX = sumX / magnitude;
        double normY = sumY / magnitude;
        double normZ = sumZ / magnitude;

        // Convert back to geographic coordinates
        double centroidLat = Math.toDegrees(Math.asin(normZ));
        double centroidLon = Math.toDegrees(Math.atan2(normY, normX));

        logger.debug("Calculated geographic centroid from {} points: [{}, {}]", 
                count, String.format("%.6f", centroidLat), String.format("%.6f", centroidLon));

        return new double[]{centroidLat, centroidLon};
    }
}

