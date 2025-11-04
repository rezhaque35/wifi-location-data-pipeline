// src/main/java/com/wifi/positioning/algorithm/outlier/SimpleLOFDetector.java
package com.wifi.positioning.algorithm.outlier;

import com.wifi.positioning.dto.WifiAPWithScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Simplified Local Outlier Factor (LOF) detector for WiFi access points.
 *
 * <p>This is a utility class that implements LOF algorithm specifically for access point location 
 * outlier detection in the positioning service. It identifies access points that are significantly 
 * isolated from their local neighborhood, indicating possible location errors or unusual positioning scenarios.
 *
 * <h2>Algorithm Process</h2>
 *
 * <ol>
 *   <li><strong>Distance Matrix:</strong> Calculate Haversine distances between all AP pairs</li>
 *   <li><strong>k-Nearest Neighbors:</strong> Identify k-nearest neighbors where k = (n/2) + 1</li>
 *   <li><strong>Local Reachability Density (LRD):</strong> Compute density measure for each point</li>
 *   <li><strong>LOF Score Calculation:</strong> Calculate LOF score = average(LRD of neighbors) / LRD of point</li>
 *   <li><strong>Outlier Threshold:</strong> Flag APs with LOF > 1.5 as outliers</li>
 * </ol>
 *
 * <h2>Mathematical Formula</h2>
 *
 * <pre>
 * LRD(p) = 1 / (Σ reach_dist(p, o) / |N_k(p)|)
 * LOF(p) = (Σ LRD(o) / LRD(p)) / |N_k(p)|
 * 
 * Where:
 * - reach_dist(p, o) = max(k-distance(o), distance(p, o))
 * - N_k(p) = k-nearest neighbors of point p
 * - LOF(p) > 1.5 indicates outlier
 * </pre>
 *
 * <h2>Simplifications</h2>
 * <ul>
 *   <li>Uses direct distances instead of full reachability distance calculation for performance</li>
 *   <li>Fixed k formula: k = (n/2) + 1</li>
 *   <li>Fixed LOF threshold: 1.5</li>
 *   <li>Geographic location only (no signal strength weighting)</li>
 * </ul>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
public final class SimpleLOFDetector {

    private static final Logger logger = LoggerFactory.getLogger(SimpleLOFDetector.class);
    private static final double LOF_THRESHOLD = 1.5;
    private static final int MIN_POINTS_FOR_LOF = 3;
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    
    // Private constructor to prevent instantiation
    private SimpleLOFDetector() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Detects local outliers using LOF algorithm on access point locations.
     *
     * <p>This method identifies access points that are significantly isolated from their local
     * neighborhood based on the Local Outlier Factor algorithm. All provided pairs are considered
     * valid and ready for analysis (caller is responsible for pre-filtering).
     *
     * @param pairs List of WifiAPWithScan pairs containing access points and their scan data (all should have valid location data)
     * @return Set of MAC addresses identified as local outliers
     */
    public static Set<String> detectLocalOutliers(List<WifiAPWithScan> pairs) {
        // Filter to only pairs with valid location data
        List<WifiAPWithScan> validPairs = pairs.stream()
                .filter(p -> p.wifiAccessPoint() != null)
                .filter(p -> p.wifiAccessPoint().getLatitude() != null)
                .filter(p -> p.wifiAccessPoint().getLongitude() != null)
                .toList();

        if (validPairs.size() < MIN_POINTS_FOR_LOF) {
            logger.debug("Insufficient points for LOF detection: {} (minimum {})",
                    validPairs.size(), MIN_POINTS_FOR_LOF);
            return Collections.emptySet();
        }

        try {
            // Build distance matrix
            double[][] distances = buildDistanceMatrix(validPairs);

            // Calculate k for k-nearest neighbors
            int k = calculateK(validPairs.size());

            // Calculate LOF scores for each point
            double[] lofScores = new double[validPairs.size()];
            for (int i = 0; i < validPairs.size(); i++) {
                lofScores[i] = calculateLOF(i, distances, k);
            }

            // Identify outliers
            Set<String> outliers = new HashSet<>();
            for (int i = 0; i < validPairs.size(); i++) {
                if (lofScores[i] > LOF_THRESHOLD) {
                    String macAddress = validPairs.get(i).wifiAccessPoint().getMacAddress();
                    outliers.add(macAddress);
                    logger.debug("LOF outlier detected: MAC={}, LOF={}", 
                            macAddress, String.format("%.2f", lofScores[i]));
                }
            }

            logger.info("LOF detection: {} outliers found out of {} access points", 
                    outliers.size(), validPairs.size());

            return outliers;

        } catch (Exception e) {
            logger.error("Error during LOF detection: {}", e.getMessage(), e);
            return Collections.emptySet(); // Return empty set on error
        }
    }

    /**
     * Builds distance matrix using Haversine formula for all AP pairs.
     *
     * @param pairs List of valid WifiAPWithScan pairs
     * @return 2D array of distances in meters
     */
    private static double[][] buildDistanceMatrix(List<WifiAPWithScan> pairs) {
        int n = pairs.size();
        double[][] distances = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    distances[i][j] = 0.0;
                } else {
                    distances[i][j] = calculateHaversineDistance(
                            pairs.get(i).wifiAccessPoint().getLatitude(),
                            pairs.get(i).wifiAccessPoint().getLongitude(),
                            pairs.get(j).wifiAccessPoint().getLatitude(),
                            pairs.get(j).wifiAccessPoint().getLongitude()
                    );
                }
            }
        }

        return distances;
    }

    /**
     * Calculates k for k-nearest neighbors using formula k = (n/2) + 1.
     *
     * @param n Total number of points
     * @return k value for k-nearest neighbors
     */
    private static int calculateK(int n) {
        return (n / 2) + 1;
    }

    /**
     * Calculates Local Reachability Density (LRD) for a point.
     *
     * <p>LRD measures the local density of a point based on the reachability distance
     * to its k-nearest neighbors. Lower LRD indicates the point is more isolated.
     *
     * @param pointIndex Index of the point
     * @param distances Distance matrix
     * @param k Number of nearest neighbors
     * @return LRD value for the point
     */
    private static double calculateLRD(int pointIndex, double[][] distances, int k) {
        // Get k-nearest neighbors (sorted distances excluding self)
        List<Double> sortedDistances = new ArrayList<>();
        for (int i = 0; i < distances[pointIndex].length; i++) {
            if (i != pointIndex) {
                sortedDistances.add(distances[pointIndex][i]);
            }
        }
        Collections.sort(sortedDistances);

        // Take k nearest neighbors
        List<Double> kNearestDistances = sortedDistances.subList(0, Math.min(k, sortedDistances.size()));

        // Calculate sum of reachability distances
        double sumReachDist = 0.0;
        for (double dist : kNearestDistances) {
            sumReachDist += dist;
        }

        if (sumReachDist == 0.0) {
            return Double.MAX_VALUE; // Avoid division by zero, high density
        }

        // LRD = 1 / (average reachability distance)
        return kNearestDistances.size() / sumReachDist;
    }

    /**
     * Calculates Local Outlier Factor (LOF) for a point.
     *
     * <p>LOF compares the local density of a point to the local densities of its neighbors.
     * A value close to 1 indicates similar density to neighbors (inlier), while values
     * significantly greater than 1 indicate the point is an outlier.
     *
     * @param pointIndex Index of the point
     * @param distances Distance matrix
     * @param k Number of nearest neighbors
     * @return LOF score for the point
     */
    private static double calculateLOF(int pointIndex, double[][] distances, int k) {
        // Get k-nearest neighbor indices
        List<Integer> kNearestIndices = getKNearestNeighbors(pointIndex, distances, k);

        // Calculate LRD for this point
        double lrdPoint = calculateLRD(pointIndex, distances, k);

        if (lrdPoint == 0.0) {
            return 1.0; // If point has zero density, treat as normal
        }

        // Calculate sum of LRD ratios for neighbors
        double sumLRDRatio = 0.0;
        for (int neighborIndex : kNearestIndices) {
            double lrdNeighbor = calculateLRD(neighborIndex, distances, k);
            sumLRDRatio += lrdNeighbor / lrdPoint;
        }

        // LOF = average LRD ratio
        return sumLRDRatio / kNearestIndices.size();
    }

    /**
     * Gets indices of k-nearest neighbors for a point.
     *
     * @param pointIndex Index of the point
     * @param distances Distance matrix
     * @param k Number of nearest neighbors
     * @return List of indices of k-nearest neighbors
     */
    private static List<Integer> getKNearestNeighbors(int pointIndex, double[][] distances, int k) {
        // Create list of (index, distance) pairs excluding self
        List<Map.Entry<Integer, Double>> distanceList = new ArrayList<>();
        for (int i = 0; i < distances[pointIndex].length; i++) {
            if (i != pointIndex) {
                distanceList.add(Map.entry(i, distances[pointIndex][i]));
            }
        }

        // Sort by distance and take k nearest
        distanceList.sort(Map.Entry.comparingByValue());

        return distanceList.stream()
                .limit(k)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Calculates Haversine distance between two geographic points.
     *
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in meters
     */
    private static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}
