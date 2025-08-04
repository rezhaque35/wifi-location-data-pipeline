package com.wifi.positioning.algorithm.impl;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalDistributionFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.algorithm.util.GDOPCalculator;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.dto.WifiAccessPoint;
import org.springframework.stereotype.Component;
import org.apache.commons.math3.linear.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

/**
 * Implementation of Maximum Likelihood Estimation for WiFi positioning.
 * 
 * This algorithm implements a probabilistic approach that maximizes the likelihood function
 * P(position | measurements) using gradient descent optimization. It provides the most
 * accurate positioning when sufficient high-quality measurements are available.
 * 
 * ALGORITHM OVERVIEW:
 * 1. Creates initial position estimate using weighted centroid method
 * 2. Builds measurement models incorporating signal strength probability distributions
 * 3. Iteratively refines position using gradient descent on log-likelihood function
 * 4. Calculates confidence based on convergence quality and signal characteristics
 * 5. Applies GDOP analysis to refine accuracy estimates
 * 
 * MATHEMATICAL MODEL:
 * The algorithm maximizes P(position | measurements) using:
 * 
 * 1. Likelihood Function:
 *    L(pos) = Π P(measurement_i | pos)
 *    where each measurement probability follows a normal distribution:
 *    P(RSSI | pos) = N(RSSI; μ(d), σ²)
 *    - μ(d) = expected RSSI at distance d using path loss model
 *    - σ² = signal variance (adaptive based on signal strength)
 * 
 * 2. Log-Likelihood Maximization:
 *    LL(pos) = Σ log(P(measurement_i | pos))
 *    Gradient: ∇LL(pos) = Σ (RSSI_i - μ(d_i))/σ² * ∇d_i
 * 
 * 3. Position Update using Gradient Descent:
 *    pos_new = pos + α * ∇LL(pos)
 *    where α is adaptive learning rate that decreases if likelihood doesn't improve
 * 
 * 4. Geometric Dilution of Precision (GDOP):
 *    GDOP = sqrt(trace((H^T * H)^-1))
 *    where H is the geometry matrix containing unit vectors from position to APs
 * 
 * USE CASES:
 * - Best suited for environments with many APs (4+ APs optimal)
 * - Effective in complex indoor environments with multipath
 * - Ideal when high accuracy and confidence estimates are required
 * - Good for scenarios with mixed signal quality (handles outliers well)
 * 
 * STRENGTHS:
 * - Most accurate algorithm when sufficient data available
 * - Handles noisy measurements robustly through probabilistic modeling
 * - Provides realistic confidence estimates based on convergence
 * - Accounts for AP geometry quality via GDOP analysis
 * - Adaptive signal variance estimation based on signal strength
 * 
 * WEAKNESSES:
 * - Computationally intensive due to iterative optimization
 * - Requires more measurements than simpler methods (minimum 4 APs)
 * - May converge slowly in challenging scenarios
 * - Higher memory usage for measurement models
 */
@Component
public class MaximumLikelihoodAlgorithm implements PositioningAlgorithm {

    // ============================================================================
    // OPTIMIZATION PARAMETERS
    // ============================================================================
    
    /**
     * Spatial resolution for grid-based optimization (meters).
     * Determines the granularity of position search space.
     * Value based on typical indoor positioning requirements.
     */
    private static final double GRID_RESOLUTION_METERS = 1.0;
    
    /**
     * Maximum number of gradient descent iterations.
     * Prevents infinite loops while allowing sufficient convergence time.
     * Value determined from empirical analysis of convergence patterns.
     */
    private static final int MAX_OPTIMIZATION_ITERATIONS = 100;
    
    /**
     * Convergence threshold for gradient descent (meters).
     * Algorithm stops when learning rate falls below this value.
     * Balances convergence quality with computational efficiency.
     */
    private static final double CONVERGENCE_THRESHOLD_METERS = 0.1;
    
    /**
     * Initial learning rate for gradient descent optimization.
     * Higher values enable faster convergence but risk overshooting.
     * Value chosen to balance speed and stability.
     */
    private static final double INITIAL_LEARNING_RATE = 1.0;
    
    /**
     * Learning rate reduction factor when likelihood doesn't improve.
     * Multiplicative factor applied to reduce step size.
     * Value of 0.5 provides good balance between exploration and exploitation.
     */
    private static final double LEARNING_RATE_REDUCTION_FACTOR = 0.5;

    // ============================================================================
    // SIGNAL PROPAGATION MODEL PARAMETERS
    // ============================================================================
    
    /**
     * Reference distance for path loss model (meters).
     * Distance at which reference RSSI is measured.
     * Standard value used in WiFi signal propagation modeling.
     */
    private static final double REFERENCE_DISTANCE_METERS = 1.0;
    
    /**
     * Speed of light in meters per second.
     * Used in free-space path loss calculations.
     * Fundamental physical constant for RF propagation.
     */
    private static final double SPEED_OF_LIGHT_MPS = 299792458.0;
    
    /**
     * Path loss exponent for signal propagation model.
     * Describes how signal strength decreases with distance.
     * Value of 3.0 represents typical indoor environment with obstacles.
     * - Free space: ~2.0
     * - Indoor with obstacles: 2.5-4.0
     * - Dense urban: 3.5-5.0
     */
    private static final double PATH_LOSS_EXPONENT = 3.0;
    
    /**
     * Earth radius in meters for geographic distance calculations.
     * Used in Haversine formula for calculating distances between coordinates.
     * Standard value for Earth's mean radius.
     */
    private static final double EARTH_RADIUS_METERS = 6371000;
    
    /**
     * Default average signal strength when calculation fails (dBm).
     * Fallback value representing typical weak indoor WiFi signal.
     * Used to prevent errors in edge cases.
     */
    private static final double DEFAULT_AVERAGE_SIGNAL_STRENGTH_DBM = -85.0;

    // ============================================================================
    // SIGNAL STRENGTH THRESHOLDS AND STANDARD DEVIATIONS
    // ============================================================================
    
    /**
     * Signal strength threshold for "strong" signals (dBm).
     * Signals above this level have high reliability for distance estimation.
     * Based on empirical analysis of WiFi signal quality vs. positioning accuracy.
     */
    private static final double STRONG_SIGNAL_THRESHOLD_DBM = -60.0;
    
    /**
     * Signal strength threshold for "weak" signals (dBm).
     * Signals below this level have reduced reliability.
     * Boundary between medium and weak signal categories.
     */
    private static final double WEAK_SIGNAL_THRESHOLD_DBM = -80.0;
    
    /**
     * Standard deviation for strong WiFi signals (dBm).
     * Represents measurement uncertainty for high-quality signals.
     * Value derived from empirical studies of WiFi signal variation.
     */
    private static final double STRONG_SIGNAL_STD_DEV_DBM = 2.5;
    
    /**
     * Standard deviation for medium strength WiFi signals (dBm).
     * Default measurement uncertainty for typical indoor environments.
     * Balances between strong and weak signal uncertainties.
     */
    private static final double MEDIUM_SIGNAL_STD_DEV_DBM = 4.0;
    
    /**
     * Standard deviation for weak WiFi signals (dBm).
     * Higher uncertainty reflects increased noise in weak signals.
     * Accounts for multipath and interference effects.
     */
    private static final double WEAK_SIGNAL_STD_DEV_DBM = 6.0;

    // ============================================================================
    // ACCURACY ESTIMATION PARAMETERS
    // ============================================================================
    
    /**
     * Minimum accuracy value for strong signals (meters).
     * Lower bound on position accuracy estimates for high-quality measurements.
     * Represents best-case positioning accuracy achievable.
     */
    private static final double MIN_ACCURACY_METERS = 1.0;
    
    /**
     * Maximum accuracy value for strong signals (meters).
     * Upper bound on position accuracy for strong signal scenarios.
     * Prevents overly optimistic accuracy claims.
     */
    private static final double MAX_ACCURACY_STRONG_SIGNALS_METERS = 5.0;
    
    /**
     * Maximum accuracy value for any scenario (meters).
     * Global upper bound on accuracy estimates to prevent unrealistic values.
     * Represents reasonable worst-case accuracy expectation.
     */
    private static final double MAX_ACCURACY_ANY_SCENARIO_METERS = 25.0;
    
    /**
     * Base accuracy for strong signals in initial estimate (meters).
     * Starting point for accuracy calculation with high-quality signals.
     * Represents middle of expected accuracy range (1-5m) for strong signals.
     */
    private static final double BASE_ACCURACY_STRONG_SIGNALS_METERS = 3.0;
    
    /**
     * Base accuracy offset for signal-strength based calculation (meters).
     * Used in accuracy formula for medium/weak signals.
     * Provides baseline accuracy before signal-dependent adjustments.
     */
    private static final double BASE_ACCURACY_OFFSET_METERS = 6.0;
    
    /**
     * Signal strength reference point for accuracy calculation (dBm).
     * Used in formula: baseAccuracy + |signal - reference| * multiplier
     * Represents transition point between good and poor signal quality.
     */
    private static final double ACCURACY_SIGNAL_REFERENCE_DBM = -70.0;
    
    /**
     * Multiplier for signal strength in accuracy calculation.
     * Determines how much signal quality affects accuracy estimates.
     * Higher values increase sensitivity to signal strength variations.
     */
    private static final double ACCURACY_SIGNAL_MULTIPLIER = 0.2;

    // ============================================================================
    // CONFIDENCE CALCULATION PARAMETERS
    // ============================================================================
    
    /**
     * Minimum confidence value for any positioning result.
     * Lower bound prevents unrealistically low confidence estimates.
     * Represents minimum useful confidence for positioning applications.
     */
    private static final double MIN_CONFIDENCE = 0.6;
    
    /**
     * Maximum confidence value for any positioning result.
     * Upper bound prevents overconfident estimates.
     * Leaves room for uncertainty even in ideal conditions.
     */
    private static final double MAX_CONFIDENCE = 0.95;
    
    /**
     * Minimum confidence threshold for strong signals.
     * Ensures high-quality measurements maintain high confidence.
     * Represents expected confidence for good positioning scenarios.
     */
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;
    
    /**
     * Maximum confidence cap for weak signals.
     * Prevents weak signals from claiming high confidence.
     * Reflects increased uncertainty with poor signal quality.
     */
    private static final double WEAK_SIGNAL_CONFIDENCE_CAP = 0.65;
    
    /**
     * Default likelihood convergence factor.
     * Used when likelihood calculation fails or produces invalid results.
     * Represents neutral convergence quality.
     */
    private static final double DEFAULT_LIKELIHOOD_FACTOR = 0.7;
    
    /**
     * Very weak signal threshold for confidence calculation (dBm).
     * Signals below this level receive minimal confidence.
     * Represents boundary of useful signal information.
     */
    private static final double VERY_WEAK_SIGNAL_THRESHOLD_DBM = -100.0;
    
    /**
     * Minimum AP count for meaningful AP count factor calculation.
     * Below this count, AP count factor is zero.
     * Represents minimum APs needed for reliable positioning.
     */
    private static final int MIN_AP_COUNT_FOR_FACTOR = 2;
    
    /**
     * Maximum AP count for AP count factor scaling.
     * AP count factor scales from 0 (at MIN_AP_COUNT) to 1 (at this value).
     * Represents point where additional APs provide diminishing returns.
     */
    private static final int MAX_AP_COUNT_FOR_FACTOR = 8;
    
    /**
     * Weight for signal quality in confidence calculation.
     * Determines relative importance of signal strength vs. other factors.
     * Higher values prioritize signal quality in confidence estimates.
     */
    private static final double CONFIDENCE_SIGNAL_WEIGHT = 0.7;
    
    /**
     * Weight for AP count in confidence calculation (strong signals).
     * Balances signal quality with geometric diversity.
     * Lower weight reflects signal quality dominance.
     */
    private static final double CONFIDENCE_AP_COUNT_WEIGHT_STRONG = 0.3;
    
    /**
     * Weight for AP count in confidence calculation (weak signals).
     * Higher weight for weak signals emphasizes geometric diversity.
     * Compensates for reduced signal quality information.
     */
    private static final double CONFIDENCE_AP_COUNT_WEIGHT_WEAK = 0.2;
    
    /**
     * Weight for AP count in confidence calculation (medium signals).
     * Balanced weight between strong and weak signal scenarios.
     * Provides moderate emphasis on geometric diversity.
     */
    private static final double CONFIDENCE_AP_COUNT_WEIGHT_MEDIUM = 0.25;
    
    /**
     * Weight for likelihood convergence in confidence calculation (strong signals).
     * Lower weight reflects reduced importance when signals are strong.
     * Strong signals provide good positioning regardless of convergence.
     */
    private static final double CONFIDENCE_LIKELIHOOD_WEIGHT_STRONG = 0.1;
    
    /**
     * Weight for likelihood convergence in confidence calculation (weak signals).
     * Higher weight emphasizes convergence quality for poor signals.
     * Good convergence can partially compensate for weak signals.
     */
    private static final double CONFIDENCE_LIKELIHOOD_WEIGHT_WEAK = 0.1;
    
    /**
     * Weight for likelihood convergence in confidence calculation (medium signals).
     * Balanced weight providing moderate convergence emphasis.
     * Reflects intermediate dependence on optimization quality.
     */
    private static final double CONFIDENCE_LIKELIHOOD_WEIGHT_MEDIUM = 0.15;
    
    /**
     * Lower bound for likelihood factor normalization.
     * Used in formula: (exp(likelihood/count) - lower) / (upper - lower)
     * Represents minimum meaningful likelihood value.
     */
    private static final double LIKELIHOOD_NORMALIZATION_LOWER_BOUND = 0.1;
    
    /**
     * Upper bound for likelihood factor normalization.
     * Completes normalization range for likelihood factor calculation.
     * Represents maximum meaningful likelihood value.
     */
    private static final double LIKELIHOOD_NORMALIZATION_UPPER_BOUND = 0.9;

    // ============================================================================
    // ALGORITHM SELECTION FRAMEWORK WEIGHTS
    // ============================================================================

    /**
     * Weight constants from the algorithm selection framework.
     * These reflect the strengths and weaknesses of the Maximum Likelihood algorithm:
     * - Works best with 4+ APs (optimal with many APs)
     * - Highly dependent on signal quality (works best with strong signals)
     * - Moderately sensitive to geometric quality
     * - Extremely effective with mixed signals and outliers
     */
    
    // AP Count weights - Maximum Likelihood requires multiple APs for effectiveness
    private static final double MAXIMUM_LIKELIHOOD_SINGLE_AP_WEIGHT = 0.0;    // Not applicable for single AP
    private static final double MAXIMUM_LIKELIHOOD_TWO_APS_WEIGHT = 0.0;      // Not applicable for two APs
    private static final double MAXIMUM_LIKELIHOOD_THREE_APS_WEIGHT = 0.0;    // Not applicable for three APs (needs more APs)
    private static final double MAXIMUM_LIKELIHOOD_FOUR_PLUS_APS_WEIGHT = 1.0;// Optimal for four+ APs
    
    // Signal quality multipliers - Algorithm performance varies significantly with signal quality
    private static final double MAXIMUM_LIKELIHOOD_STRONG_SIGNAL_MULTIPLIER = 1.2;  // Significant improvement with strong signals
    private static final double MAXIMUM_LIKELIHOOD_MEDIUM_SIGNAL_MULTIPLIER = 0.9;  // Slight reduction with medium signals
    private static final double MAXIMUM_LIKELIHOOD_WEAK_SIGNAL_MULTIPLIER = 0.5;    // Major reduction for weak signals
    private static final double MAXIMUM_LIKELIHOOD_VERY_WEAK_SIGNAL_MULTIPLIER = 0.0; // ×0.0 for very weak signals
    
    // Geometric quality multipliers - Moderate sensitivity to AP geometric distribution
    private static final double MAXIMUM_LIKELIHOOD_EXCELLENT_GDOP_MULTIPLIER = 1.2; // Significant boost for excellent geometry
    private static final double MAXIMUM_LIKELIHOOD_GOOD_GDOP_MULTIPLIER = 1.1;      // Good boost for good geometry
    private static final double MAXIMUM_LIKELIHOOD_FAIR_GDOP_MULTIPLIER = 0.9;      // Some reduction for fair geometry
    private static final double MAXIMUM_LIKELIHOOD_POOR_GDOP_MULTIPLIER = 0.7;      // Significant reduction for poor geometry
    
    // Signal distribution multipliers - Algorithm handles mixed signals and outliers well
    private static final double MAXIMUM_LIKELIHOOD_UNIFORM_SIGNALS_MULTIPLIER = 0.9;  // Slightly reduced with uniform signals
    private static final double MAXIMUM_LIKELIHOOD_MIXED_SIGNALS_MULTIPLIER = 1.1;    // Some improvement with mixed signals
    private static final double MAXIMUM_LIKELIHOOD_SIGNAL_OUTLIERS_MULTIPLIER = 1.2;  // Significant improvement with outliers
    
    /**
     * Calculates position using Maximum Likelihood Estimation.
     * 
     * This method orchestrates the complete maximum likelihood positioning process:
     * 1. Input validation and preprocessing
     * 2. Initial position estimation using weighted centroid
     * 3. Measurement model creation for likelihood calculations
     * 4. Iterative position refinement using gradient descent
     * 5. Final accuracy and confidence calculation with GDOP analysis
     *
     * @param wifiScan List of WiFi scan results containing signal strengths
     * @param knownAPs List of known access points with their locations
     * @return Calculated position with confidence metrics, or null if calculation fails
     */
    @Override
    public Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        if (!isValidInput(wifiScan, knownAPs)) {
            return null;
        }

        // Create AP lookup map for efficient access during calculations
        Map<String, WifiAccessPoint> apMap = createApLookupMap(knownAPs);

        // Create initial position estimate using weighted centroid method
        Position initialEstimate = calculateInitialEstimate(wifiScan, apMap);
        if (initialEstimate == null) {
            return null;
        }

        // Create measurement models for each AP observation
        List<MeasurementModel> measurements = createMeasurementModels(wifiScan, apMap);
        if (measurements.isEmpty()) {
            return initialEstimate;
        }

        // Refine position using gradient descent optimization
        OptimizationResult optimizationResult = optimizePositionUsingGradientDescent(initialEstimate, measurements);

        // Calculate final position with GDOP analysis and confidence metrics
        return finalizePositionWithGDOPAnalysis(optimizationResult, measurements, wifiScan);
    }

    /**
     * Validates input parameters for position calculation.
     * Ensures that both WiFi scan results and known AP lists are valid and non-empty.
     * 
     * @param wifiScan List of WiFi scan results to validate
     * @param knownAPs List of known access points to validate
     * @return true if inputs are valid, false otherwise
     */
    private boolean isValidInput(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
        return wifiScan != null && !wifiScan.isEmpty() && 
               knownAPs != null && !knownAPs.isEmpty();
    }

    /**
     * Creates a lookup map from known access points for efficient access during calculations.
     * Uses concurrent map for thread-safe operations during parallel processing.
     * In case of duplicate MAC addresses, keeps the first occurrence.
     * 
     * @param knownAPs List of known access points
     * @return Map with MAC addresses as keys and WifiAccessPoint objects as values
     */
    private Map<String, WifiAccessPoint> createApLookupMap(List<WifiAccessPoint> knownAPs) {
        return knownAPs.parallelStream()
            .collect(Collectors.toConcurrentMap(
                WifiAccessPoint::getMacAddress,
                ap -> ap,
                (ap1, ap2) -> ap1 // In case of duplicates, keep the first one
            ));
    }

    /**
     * Optimizes position using gradient descent on the log-likelihood function.
     * 
     * The optimization process iteratively improves the position estimate by:
     * 1. Computing the gradient of the log-likelihood function
     * 2. Updating position in the direction of steepest ascent
     * 3. Adapting learning rate based on likelihood improvement
     * 4. Converging when learning rate falls below threshold
     * 
     * Mathematical Foundation:
     * - Gradient: ∇LL(pos) = Σ (RSSI_i - μ(d_i))/σ² * ∇d_i
     * - Update: pos_new = pos + α * ∇LL(pos)
     * - Learning rate adaptation: α_new = α * LEARNING_RATE_REDUCTION_FACTOR (if no improvement)
     * 
     * @param initialPosition Starting position for optimization
     * @param measurements List of measurement models for likelihood calculation
     * @return OptimizationResult containing best position and achieved likelihood
     */
    private OptimizationResult optimizePositionUsingGradientDescent(Position initialPosition, 
                                                                    List<MeasurementModel> measurements) {
        Position currentPosition = initialPosition;
        Position bestPosition = initialPosition;
        double bestLikelihood = Double.NEGATIVE_INFINITY;
        double learningRate = INITIAL_LEARNING_RATE;

        for (int iteration = 0; iteration < MAX_OPTIMIZATION_ITERATIONS; iteration++) {
            // Calculate gradient of log-likelihood function at current position
            double[] gradient = calculateLogLikelihoodGradient(currentPosition, measurements);
            
            // Update position using gradient ascent
            Position newPosition = updatePositionWithGradient(currentPosition, gradient, learningRate);

            // Evaluate likelihood at new position
            double newLikelihood = calculateLogLikelihood(newPosition, measurements);

            // Update best position if likelihood improved
            if (newLikelihood > bestLikelihood) {
                bestLikelihood = newLikelihood;
                bestPosition = newPosition;
                currentPosition = newPosition;
            } else {
                // Reduce learning rate if no improvement
                learningRate *= LEARNING_RATE_REDUCTION_FACTOR;
            }

            // Check convergence condition
            if (learningRate < CONVERGENCE_THRESHOLD_METERS) {
                break;
            }
        }

        return new OptimizationResult(bestPosition, bestLikelihood);
    }

    /**
     * Updates position using gradient information from likelihood function.
     * 
     * Applies gradient ascent update rule:
     * pos_new = pos_old + learningRate * gradient
     * 
     * @param currentPosition Current position estimate
     * @param gradient Gradient vector [dLat, dLon, dAlt]
     * @param learningRate Step size for position update
     * @return Updated position with same accuracy and confidence as input
     */
    private Position updatePositionWithGradient(Position currentPosition, double[] gradient, double learningRate) {
        return new Position(
            currentPosition.latitude() + learningRate * gradient[0],
            currentPosition.longitude() + learningRate * gradient[1],
            currentPosition.altitude() + learningRate * gradient[2],
            currentPosition.accuracy(),
            currentPosition.confidence()
        );
    }

    /**
     * Finalizes position calculation with GDOP analysis and confidence metrics.
     * 
     * This method performs the final steps of position calculation:
     * 1. Prepares coordinate arrays for GDOP calculation
     * 2. Calculates Geometric Dilution of Precision (GDOP)
     * 3. Computes average signal strength for accuracy/confidence
     * 4. Refines accuracy estimate using GDOP factor
     * 5. Calculates final confidence based on multiple factors
     * 
     * @param optimizationResult Result from gradient descent optimization
     * @param measurements List of measurement models used in calculation
     * @param wifiScan Original WiFi scan results for signal analysis
     * @return Final position with refined accuracy and confidence values
     */
    private Position finalizePositionWithGDOPAnalysis(OptimizationResult optimizationResult,
                                                      List<MeasurementModel> measurements,
                                                      List<WifiScanResult> wifiScan) {
        Position bestPosition = optimizationResult.bestPosition;
        double bestLikelihood = optimizationResult.bestLikelihood;

        // Prepare coordinate arrays for GDOP calculation
        GDOPCoordinates gdopCoordinates = prepareGDOPCoordinates(bestPosition, measurements);
        
        // Calculate GDOP using the GDOPCalculator utility
        double gdop = GDOPCalculator.calculateGDOP(gdopCoordinates.coordinates, gdopCoordinates.position, gdopCoordinates.is3D);
        double gdopFactor = GDOPCalculator.calculateGDOPFactor(gdop);
        
        // Calculate average signal strength for accuracy and confidence calculations
        double avgSignalStrength = calculateAverageSignalStrength(wifiScan);
        
        // Calculate refined accuracy using GDOP factor
        double refinedAccuracy = calculateAccuracy(bestPosition.accuracy(), gdopFactor, avgSignalStrength);
        
        // Calculate final confidence based on likelihood surface and GDOP
        double confidence = calculateConfidence(bestPosition, measurements, bestLikelihood, 
                                               gdopFactor, avgSignalStrength, wifiScan.size());

        return new Position(
            bestPosition.latitude(),
            bestPosition.longitude(),
            bestPosition.altitude(),
            refinedAccuracy,
            confidence
        );
    }

    /**
     * Prepares coordinate arrays and position data for GDOP calculation.
     * Handles both 2D and 3D scenarios based on altitude data availability.
     * 
     * @param position Current position estimate
     * @param measurements List of measurement models containing AP coordinates
     * @return GDOPCoordinates object containing prepared data for GDOP calculation
     */
    private GDOPCoordinates prepareGDOPCoordinates(Position position, List<MeasurementModel> measurements) {
        boolean is3D = position.altitude() != null;
        
        double[][] coordinates;
        if (is3D) {
            coordinates = new double[measurements.size()][3];
            for (int i = 0; i < measurements.size(); i++) {
                MeasurementModel ap = measurements.get(i);
                coordinates[i][0] = ap.lat;
                coordinates[i][1] = ap.lon;
                coordinates[i][2] = ap.alt;
            }
        } else {
            coordinates = new double[measurements.size()][2];
            for (int i = 0; i < measurements.size(); i++) {
                MeasurementModel ap = measurements.get(i);
                coordinates[i][0] = ap.lat;
                coordinates[i][1] = ap.lon;
            }
        }
        
        // Prepare position array for GDOP calculation
        double[] positionArray;
        if (is3D) {
            positionArray = new double[] {
                position.latitude(),
                position.longitude(),
                position.altitude()
            };
        } else {
            positionArray = new double[] {
                position.latitude(),
                position.longitude()
            };
        }
        
        return new GDOPCoordinates(coordinates, positionArray, is3D);
    }

    /**
     * Calculates average signal strength from WiFi scan results.
     * Uses stream processing for efficient calculation with fallback to default value.
     * 
     * @param wifiScan List of WiFi scan results
     * @return Average signal strength in dBm, or default value if calculation fails
     */
    private double calculateAverageSignalStrength(List<WifiScanResult> wifiScan) {
        return wifiScan.stream()
            .mapToDouble(WifiScanResult::signalStrength)
            .average()
            .orElse(DEFAULT_AVERAGE_SIGNAL_STRENGTH_DBM);
    }

    /**
     * Data class to hold GDOP calculation coordinates and metadata.
     * Encapsulates coordinate arrays, position data, and dimensionality information.
     */
    private static class GDOPCoordinates {
        final double[][] coordinates;
        final double[] position;
        final boolean is3D;

        GDOPCoordinates(double[][] coordinates, double[] position, boolean is3D) {
            this.coordinates = coordinates;
            this.position = position;
            this.is3D = is3D;
        }
    }

    /**
     * Data class to hold optimization results from gradient descent.
     * Encapsulates the best position found and its associated likelihood value.
     */
    private static class OptimizationResult {
        final Position bestPosition;
        final double bestLikelihood;

        OptimizationResult(Position bestPosition, double bestLikelihood) {
            this.bestPosition = bestPosition;
            this.bestLikelihood = bestLikelihood;
        }
    }

    /**
     * Calculates initial position estimate using weighted centroid method.
     * 
     * This method creates a starting point for gradient descent optimization by:
     * 1. Computing weighted centroid of AP locations using signal strength as weights
     * 2. Calculating initial accuracy based on signal quality and GDOP
     * 3. Setting preliminary confidence for further refinement
     * 
     * The weighted centroid approach gives stronger signals more influence on the initial position,
     * providing a reasonable starting point that is typically close to the optimal solution.
     * 
     * Mathematical Foundation:
     * - Weight calculation: w(i) = 10^(RSSI(i)/10) (converts dBm to linear scale)
     * - Position: lat = Σ(lat(i) * w(i)) / Σ(w(i)), similar for lon and alt
     * - GDOP analysis for geometry quality assessment
     * 
     * @param wifiScan List of WiFi scan results containing signal measurements
     * @param apMap Map of known AP locations for efficient lookup
     * @return Initial position estimate with preliminary accuracy and confidence, or null if calculation fails
     */
    private Position calculateInitialEstimate(List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        // Calculate weighted position using signal strengths as weights
        WeightedPositionResult weightedResult = calculateWeightedPosition(wifiScan, apMap);
        if (weightedResult == null) {
            return null;
        }
        
        // Calculate average signal strength for accuracy estimation
        double avgSignalStrength = weightedResult.totalSignal / wifiScan.size();
        
        // Create measurement models and initial position for GDOP analysis
        List<MeasurementModel> measurements = createMeasurementModels(wifiScan, apMap);
        Position initialPosition = new Position(
            weightedResult.latitude, 
            weightedResult.longitude, 
            weightedResult.altitude, 
            0.0, 
            0.0
        );
        
        // Calculate GDOP for geometry quality assessment
        GDOPCoordinates gdopCoordinates = prepareGDOPCoordinates(initialPosition, measurements);
        double gdop = GDOPCalculator.calculateGDOP(gdopCoordinates.coordinates, gdopCoordinates.position, gdopCoordinates.is3D);
        double gdopFactor = GDOPCalculator.calculateGDOPFactor(gdop);
        
        // Calculate initial accuracy based on signal strength and geometry
        double initialAccuracy = calculateInitialAccuracy(avgSignalStrength, gdopFactor);

        return new Position(
            weightedResult.latitude, 
            weightedResult.longitude, 
            weightedResult.altitude, 
            initialAccuracy, 
            0.5 // Initial confidence will be refined later
        );
    }

    /**
     * Calculates weighted position using signal strengths as weights.
     * 
     * Uses exponential weighting based on signal strength to give stronger signals
     * more influence on position estimation. Handles both 2D and 3D positioning
     * based on altitude data availability.
     * 
     * @param wifiScan List of WiFi scan results
     * @param apMap Map of known AP locations
     * @return WeightedPositionResult containing calculated position and metadata, or null if no valid APs
     */
    private WeightedPositionResult calculateWeightedPosition(List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        // Use atomic accumulators for thread-safe parallel calculations
        DoubleAdder totalWeight = new DoubleAdder();
        DoubleAdder weightedLat = new DoubleAdder();
        DoubleAdder weightedLon = new DoubleAdder();
        DoubleAdder weightedAlt = new DoubleAdder();
        DoubleAdder totalSignal = new DoubleAdder();
        DoubleAdder altitudeWeightSum = new DoubleAdder(); // Track altitude weights separately

        // Process scans in parallel for efficiency
        wifiScan.parallelStream().forEach(scan -> {
            WifiAccessPoint ap = apMap.get(scan.macAddress());
            if (ap == null) return;

            // Calculate exponential weight from signal strength (converts dBm to linear scale)
            double weight = Math.pow(10, scan.signalStrength() / 10.0);
            
            // Accumulate weighted coordinates
            weightedLat.add(ap.getLatitude() * weight);
            weightedLon.add(ap.getLongitude() * weight);
            
            // Handle altitude data if available
            if (ap.getAltitude() != null) {
                weightedAlt.add(ap.getAltitude() * weight);
                altitudeWeightSum.add(weight);
            }
            
            totalWeight.add(weight);
            totalSignal.add(scan.signalStrength());
        });

        if (totalWeight.doubleValue() == 0) {
            return null;
        }
        
        // Calculate final weighted position
        double latitude = weightedLat.doubleValue() / totalWeight.doubleValue();
        double longitude = weightedLon.doubleValue() / totalWeight.doubleValue();
        
        // Calculate altitude only if valid altitude data exists
        double altitude = 0.0;
        boolean has3DData = altitudeWeightSum.doubleValue() > 0;
        if (has3DData) {
            altitude = weightedAlt.doubleValue() / altitudeWeightSum.doubleValue();
        }
        
        return new WeightedPositionResult(latitude, longitude, altitude, totalSignal.doubleValue(), has3DData);
    }

    /**
     * Calculates initial accuracy estimate based on signal strength and geometric quality.
     * 
     * Uses different calculation strategies for strong vs. weak signals:
     * - Strong signals: Fixed base accuracy with controlled GDOP adjustment
     * - Weak signals: Signal-dependent base accuracy with full GDOP adjustment
     * 
     * Mathematical Foundation:
     * - Strong signals: accuracy = BASE_ACCURACY * (1 + (gdopFactor - 1) * GDOP_MULTIPLIER)
     * - Weak signals: accuracy = (BASE_OFFSET + |signal - reference| * multiplier) * gdopFactor
     * 
     * @param avgSignalStrength Average signal strength across all measurements (dBm)
     * @param gdopFactor Geometric quality factor derived from GDOP analysis
     * @return Initial accuracy estimate in meters
     */
    private double calculateInitialAccuracy(double avgSignalStrength, double gdopFactor) {
        double baseAccuracy;
        
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            // Strong signals: use fixed base accuracy
            baseAccuracy = BASE_ACCURACY_STRONG_SIGNALS_METERS;
        } else {
            // Weak/medium signals: use signal-dependent base accuracy
            baseAccuracy = BASE_ACCURACY_OFFSET_METERS + 
                          Math.abs(avgSignalStrength - ACCURACY_SIGNAL_REFERENCE_DBM) * ACCURACY_SIGNAL_MULTIPLIER;
        }
        
        // Apply GDOP adjustment based on signal strength
        double initialAccuracy;
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            // Controlled GDOP adjustment for strong signals
            initialAccuracy = baseAccuracy * (1.0 + (gdopFactor - 1.0) * GDOPCalculator.GDOP_ACCURACY_MULTIPLIER);
        } else {
            // Full GDOP adjustment for weaker signals
            initialAccuracy = baseAccuracy * gdopFactor;
        }
        
        // Ensure accuracy is within reasonable bounds
        return Math.max(MIN_ACCURACY_METERS, Math.min(MAX_ACCURACY_ANY_SCENARIO_METERS, initialAccuracy));
    }

    /**
     * Data class to hold weighted position calculation results.
     * Encapsulates position coordinates, signal data, and 3D capability flag.
     */
    private static class WeightedPositionResult {
        final double latitude;
        final double longitude;
        final double altitude;
        final double totalSignal;
        final boolean has3DData;

        WeightedPositionResult(double latitude, double longitude, double altitude, double totalSignal, boolean has3DData) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.totalSignal = totalSignal;
            this.has3DData = has3DData;
        }
    }

    /**
     * Creates measurement models for each AP observation.
     * Models incorporate:
     * 1. Historical signal strength patterns
     * 2. AP-specific standard deviations
     * 3. Location confidence from AP database
     * 4. Operating frequency for accurate path loss calculation
     * 
     * @param wifiScan List of WiFi scan results
     * @param apMap Map of known AP locations
     * @return List of measurement models for gradient descent
     */
    private List<MeasurementModel> createMeasurementModels(List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
        // Create measurement models in parallel
        return wifiScan.parallelStream()
            .map(scan -> {
                WifiAccessPoint ap = apMap.get(scan.macAddress());
                if (ap == null) return null;

                double stdDev = calculateAdaptiveStdDev(scan.signalStrength());
                
                // Convert frequency from MHz to Hz for proper calculation
                double frequencyHz = scan.frequency() * 1e6; // Convert MHz to Hz

                return new MeasurementModel(
                    ap.getLatitude(),
                    ap.getLongitude(),
                    ap.getAltitude(),
                    scan.signalStrength(),
                    stdDev,
                    ap.getConfidence(),
                    frequencyHz
                );
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Calculates the gradient of the log-likelihood function.
     * This drives the gradient descent optimization by:
     * 1. Computing partial derivatives for each coordinate
     * 2. Weighting contributions by measurement confidence
     * 3. Combining gradients from all measurements
     * 4. Using frequency-dependent expected RSSI calculation
     * 
     * @param position Current position estimate
     * @param measurements List of measurement models
     * @return Gradient vector [dLat, dLon, dAlt]
     */
    private double[] calculateLogLikelihoodGradient(Position position, List<MeasurementModel> measurements) {
        // These arrays will be updated by multiple threads, so we need thread-safe handling
        double[] gradient = new double[3];
        final Object lock = new Object();
        
        measurements.parallelStream().forEach(m -> {
            double distance = calculateDistance(
                position.latitude(), position.longitude(), position.altitude(),
                m.lat, m.lon, m.alt
            );
            
            double expectedRSSI = calculateExpectedRSSI(distance, m.frequencyHz);
            double error = m.rssi - expectedRSSI;
            
            // Partial derivatives
            double scale = error / (m.stdDev * m.stdDev * distance);
            double gradLat = scale * (position.latitude() - m.lat) * m.confidence;
            double gradLon = scale * (position.longitude() - m.lon) * m.confidence;
            double gradAlt = scale * (position.altitude() - m.alt) * m.confidence;
            
            // Thread-safe update of gradient array
            synchronized(lock) {
                gradient[0] += gradLat;
                gradient[1] += gradLon;
                gradient[2] += gradAlt;
            }
        });
        
        return gradient;
    }

    /**
     * Calculates the log-likelihood of a position given the measurements.
     * Higher values indicate better fit to the measurements.
     * Incorporates:
     * 1. Frequency-dependent signal strength error terms
     * 2. Measurement standard deviations
     * 3. AP location confidence weights
     * 
     * @param position Position to evaluate
     * @param measurements List of measurement models
     * @return Log-likelihood value
     */
    private double calculateLogLikelihood(Position position, List<MeasurementModel> measurements) {
        // Use atomic accumulator for thread-safe summation
        DoubleAdder logLikelihood = new DoubleAdder();
        
        measurements.parallelStream().forEach(m -> {
            double distance = calculateDistance(
                position.latitude(), position.longitude(), position.altitude(),
                m.lat, m.lon, m.alt
            );
            
            double expectedRSSI = calculateExpectedRSSI(distance, m.frequencyHz);
            double error = m.rssi - expectedRSSI;
            
            logLikelihood.add(-(error * error) / (2 * m.stdDev * m.stdDev) * m.confidence);
        });
        
        return logLikelihood.doubleValue();
    }

    /**
     * Calculates confidence based on multiple factors:
     * 1. Signal strength quality
     * 2. Number of access points 
     * 3. Likelihood convergence quality
     * 4. Geometric distribution of APs (GDOP)
     * 
     * The confidence calculation uses a multi-factor approach that adapts to signal quality:
     * - Strong signals: Emphasizes signal quality with minor GDOP adjustment
     * - Medium signals: Balanced consideration of all factors
     * - Weak signals: Emphasizes AP count and convergence quality
     * 
     * Mathematical Foundation:
     * Base confidence = MIN_CONF + (MAX_CONF - MIN_CONF) * 
     *                  (signalWeight * signalFactor + apWeight * apCountFactor + likelihoodWeight * likelihoodFactor)
     * 
     * Final confidence = baseConfidence * (1.0 - GDOP_WEIGHT * (1.0 - 1.0/gdopFactor))
     * 
     * @param position Final position estimate
     * @param measurements List of measurement models
     * @param maxLikelihood Best achieved likelihood value
     * @param gdopFactor GDOP factor indicating geometric quality
     * @param avgSignalStrength Average signal strength (dBm)
     * @param apCount Number of APs used in calculation
     * @return Confidence value between 0 and 1
     */
    private double calculateConfidence(Position position, List<MeasurementModel> measurements, 
                                     double maxLikelihood, double gdopFactor, 
                                     double avgSignalStrength, int apCount) {
        // Calculate individual confidence factors
        ConfidenceFactors factors = calculateConfidenceFactors(maxLikelihood, measurements.size(), 
                                                              avgSignalStrength, apCount);
        
        // Determine signal strength category and calculate base confidence
        SignalCategory signalCategory = determineSignalCategory(avgSignalStrength);
        double baseConfidence = calculateBaseConfidence(factors, signalCategory);
        
        // Apply GDOP adjustment based on signal category
        double finalConfidence = applyGDOPAdjustment(baseConfidence, gdopFactor, signalCategory);
        
        return finalConfidence;
    }

    /**
     * Calculates individual factors that contribute to confidence estimation.
     * 
     * Each factor represents a different aspect of positioning quality:
     * - Signal factor: Quality based on average signal strength
     * - AP count factor: Diversity based on number of access points
     * - Likelihood factor: Convergence quality from optimization
     * 
     * @param maxLikelihood Maximum likelihood achieved during optimization
     * @param measurementCount Number of measurements used
     * @param avgSignalStrength Average signal strength across measurements
     * @param apCount Number of access points used
     * @return ConfidenceFactors object containing all calculated factors
     */
    private ConfidenceFactors calculateConfidenceFactors(double maxLikelihood, int measurementCount,
                                                        double avgSignalStrength, int apCount) {
        // Calculate signal quality factor (0-1 scale)
        double signalFactor = calculateSignalQualityFactor(avgSignalStrength);
        
        // Calculate AP count factor (0-1 scale)
        double apCountFactor = Math.min(1.0, Math.max(0.0, 
            (apCount - MIN_AP_COUNT_FOR_FACTOR) / (double)(MAX_AP_COUNT_FOR_FACTOR - MIN_AP_COUNT_FOR_FACTOR)));
        
        // Calculate likelihood convergence factor (0-1 scale)
        double likelihoodFactor = calculateLikelihoodConvergenceFactor(maxLikelihood, measurementCount);
        
        return new ConfidenceFactors(signalFactor, apCountFactor, likelihoodFactor);
    }

    /**
     * Calculates signal quality factor based on average signal strength.
     * 
     * Uses different scaling approaches for strong vs. weak signals:
     * - Strong signals: Scale relative to weak signal threshold
     * - Weak signals: Scale relative to very weak signal threshold
     * 
     * @param avgSignalStrength Average signal strength in dBm
     * @return Signal quality factor between 0 and 1
     */
    private double calculateSignalQualityFactor(double avgSignalStrength) {
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            // Strong signals: scale from weak to strong threshold
            return Math.min(1.0, Math.max(0.0, 
                           (avgSignalStrength - WEAK_SIGNAL_THRESHOLD_DBM) / 
                           (STRONG_SIGNAL_THRESHOLD_DBM - WEAK_SIGNAL_THRESHOLD_DBM)));
        } else {
            // Weaker signals: scale from very weak to weak threshold
            return Math.min(1.0, Math.max(0.0, 
                           (avgSignalStrength - VERY_WEAK_SIGNAL_THRESHOLD_DBM) / 
                           (WEAK_SIGNAL_THRESHOLD_DBM - VERY_WEAK_SIGNAL_THRESHOLD_DBM)));
        }
    }

    /**
     * Calculates likelihood convergence factor from optimization results.
     * 
     * Evaluates how well the optimization converged by examining the final likelihood value.
     * Higher likelihood values indicate better fit to the measurement model.
     * 
     * @param maxLikelihood Maximum likelihood achieved during optimization
     * @param measurementCount Number of measurements for normalization
     * @return Likelihood convergence factor between 0 and 1
     */
    private double calculateLikelihoodConvergenceFactor(double maxLikelihood, int measurementCount) {
        if (Double.isInfinite(maxLikelihood) || Double.isNaN(maxLikelihood)) {
            return DEFAULT_LIKELIHOOD_FACTOR;
        }
        
        // Normalize likelihood by measurement count and map to 0-1 scale
        double normalizedLikelihood = Math.exp(maxLikelihood / measurementCount);
        return Math.min(1.0, Math.max(0.0, 
                       (normalizedLikelihood - LIKELIHOOD_NORMALIZATION_LOWER_BOUND) / 
                       (LIKELIHOOD_NORMALIZATION_UPPER_BOUND - LIKELIHOOD_NORMALIZATION_LOWER_BOUND)));
    }

    /**
     * Determines signal strength category for confidence calculation strategy.
     * 
     * @param avgSignalStrength Average signal strength in dBm
     * @return SignalCategory enum indicating strength level
     */
    private SignalCategory determineSignalCategory(double avgSignalStrength) {
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            return SignalCategory.STRONG;
        } else if (avgSignalStrength >= WEAK_SIGNAL_THRESHOLD_DBM) {
            return SignalCategory.MEDIUM;
        } else {
            return SignalCategory.WEAK;
        }
    }

    /**
     * Calculates base confidence using weighted combination of factors.
     * 
     * Different signal categories use different weighting strategies:
     * - Strong signals: Emphasize signal quality, minimal likelihood weight
     * - Medium signals: Balanced weights across all factors
     * - Weak signals: Emphasize AP count and geometric diversity
     * 
     * @param factors Calculated confidence factors
     * @param signalCategory Signal strength category
     * @return Base confidence value before GDOP adjustment
     */
    private double calculateBaseConfidence(ConfidenceFactors factors, SignalCategory signalCategory) {
        double weightedScore;
        double confidenceRange;
        double baselineConfidence;
        
        switch (signalCategory) {
            case STRONG:
                // Strong signals: high baseline with signal quality emphasis
                baselineConfidence = HIGH_CONFIDENCE_THRESHOLD;
                confidenceRange = MAX_CONFIDENCE - HIGH_CONFIDENCE_THRESHOLD;
                weightedScore = CONFIDENCE_SIGNAL_WEIGHT * factors.signalFactor + 
                               CONFIDENCE_AP_COUNT_WEIGHT_STRONG * factors.apCountFactor + 
                               CONFIDENCE_LIKELIHOOD_WEIGHT_STRONG * factors.likelihoodFactor;
                break;
                
            case WEAK:
                // Weak signals: lower baseline with geometric emphasis
                baselineConfidence = MIN_CONFIDENCE;
                confidenceRange = WEAK_SIGNAL_CONFIDENCE_CAP - MIN_CONFIDENCE;
                weightedScore = CONFIDENCE_SIGNAL_WEIGHT * factors.signalFactor + 
                               CONFIDENCE_AP_COUNT_WEIGHT_WEAK * factors.apCountFactor + 
                               CONFIDENCE_LIKELIHOOD_WEIGHT_WEAK * factors.likelihoodFactor;
                break;
                
            case MEDIUM:
            default:
                // Medium signals: balanced approach
                baselineConfidence = WEAK_SIGNAL_CONFIDENCE_CAP;
                confidenceRange = HIGH_CONFIDENCE_THRESHOLD - WEAK_SIGNAL_CONFIDENCE_CAP;
                weightedScore = CONFIDENCE_SIGNAL_WEIGHT * factors.signalFactor + 
                               CONFIDENCE_AP_COUNT_WEIGHT_MEDIUM * factors.apCountFactor + 
                               CONFIDENCE_LIKELIHOOD_WEIGHT_MEDIUM * factors.likelihoodFactor;
                break;
        }
        
        return baselineConfidence + confidenceRange * weightedScore;
    }

    /**
     * Applies GDOP (Geometric Dilution of Precision) adjustment to base confidence.
     * 
     * The adjustment strength varies by signal category:
     * - Strong signals: Minor adjustment to maintain high confidence
     * - Weak signals: Stronger adjustment reflecting geometric sensitivity
     * - Medium signals: Balanced adjustment
     * 
     * Formula: confidence * (1.0 - GDOP_WEIGHT * (1.0 - 1.0/gdopFactor))
     * 
     * @param baseConfidence Base confidence before GDOP adjustment
     * @param gdopFactor GDOP factor indicating geometric quality
     * @param signalCategory Signal strength category
     * @return Final confidence with GDOP adjustment and appropriate bounds
     */
    private double applyGDOPAdjustment(double baseConfidence, double gdopFactor, SignalCategory signalCategory) {
        // Apply GDOP adjustment
        double adjustedConfidence = baseConfidence * 
            (1.0 - GDOPCalculator.GDOP_CONFIDENCE_WEIGHT * (1.0 - 1.0/Math.max(1.0, gdopFactor)));
        
        // Apply signal category-specific bounds
        switch (signalCategory) {
            case STRONG:
                return Math.max(HIGH_CONFIDENCE_THRESHOLD, Math.min(MAX_CONFIDENCE, adjustedConfidence));
                
            case WEAK:
                return Math.min(WEAK_SIGNAL_CONFIDENCE_CAP, adjustedConfidence);
                
            case MEDIUM:
            default:
                return adjustedConfidence;
        }
    }

    /**
     * Enumeration for signal strength categories used in confidence calculation.
     */
    private enum SignalCategory {
        STRONG,   // Above STRONG_SIGNAL_THRESHOLD_DBM
        MEDIUM,   // Between WEAK_SIGNAL_THRESHOLD_DBM and STRONG_SIGNAL_THRESHOLD_DBM
        WEAK      // Below WEAK_SIGNAL_THRESHOLD_DBM
    }

    /**
     * Data class to hold calculated confidence factors.
     * Encapsulates the three main factors that contribute to confidence estimation.
     */
    private static class ConfidenceFactors {
        final double signalFactor;      // Quality factor based on signal strength (0-1)
        final double apCountFactor;     // Diversity factor based on AP count (0-1)
        final double likelihoodFactor;  // Convergence factor based on optimization (0-1)

        ConfidenceFactors(double signalFactor, double apCountFactor, double likelihoodFactor) {
            this.signalFactor = signalFactor;
            this.apCountFactor = apCountFactor;
            this.likelihoodFactor = likelihoodFactor;
        }
    }

    /**
     * Calculates expected RSSI at a given distance using the Close-In (CI) free-space reference distance model.
     * 
     * This implementation follows the CI path loss model established by Rappaport et al. (2015) and 
     * Sun et al. (2016) for wireless communication systems. The model uses a 1-meter reference distance
     * with 0 dBm transmit power assumption for the following academically-supported reasons:
     * 
     * 1. MATHEMATICAL INVARIANCE: Maximum likelihood estimators are invariant to constant additive
     *    terms (Van Trees, 2001). The choice of reference transmit power only affects the absolute
     *    RSSI scale, not the relative positioning accuracy or likelihood function optimization.
     * 
     * 2. STANDARDIZATION: The CI model with 1m reference distance and 0 dBm provides a standardized
     *    approach widely adopted in 5G channel modeling (Sun et al., 2016; 3GPP TR 38.901).
     *    Formula: RSSI = FSPL(f,1m) + 10×n×log₁₀(d) + shadow_fading
     *    where FSPL(f,1m) = 32.4 + 20×log₁₀(f_GHz) dB
     * 
     * 3. PARAMETER STABILITY: Studies show CI models exhibit superior parameter stability across
     *    frequencies and distances compared to floating-intercept models (Sun et al., 2016).
     * 
     * 4. COMPUTATIONAL SIMPLICITY: Using 0 dBm reference allows direct computation without
     *    needing actual AP transmit power knowledge, which is often unavailable or varies.
     * 
     * 5. POSITIONING ACCURACY: Indoor positioning studies (Bruno et al., 2014; Wang et al., 2021)
     *    demonstrate that relative RSSI differences (fingerprinting basis) are unaffected by
     *    absolute transmit power assumptions.
     * 
     * References:
     * - Rappaport et al., "Wideband millimeter-wave propagation measurements...", IEEE Trans. Comm., 2015
     * - Sun et al., "Investigation of Prediction Accuracy...", IEEE Trans. Veh. Tech., 2016  
     * - Bruno et al., "Indoor Positioning in Wireless Local Area Networks...", Sci. World J., 2014
     * - Van Trees, "Detection, Estimation, and Modulation Theory", Wiley, 2001
     * 
     * @param distance Distance in meters from reference point
     * @param frequency Operating frequency in Hz
     * @return Expected RSSI in dBm at the given distance
     */
    private double calculateExpectedRSSI(double distance, double frequency) {
        // Calculate free-space path loss at 1 meter reference distance
        // FSPL(f, 1m) = 20 × log₁₀(4π × 1m × f / c)
        double fspl1m = 20.0 * Math.log10(4.0 * Math.PI * frequency / SPEED_OF_LIGHT_MPS);
        
        // Calculate expected RSSI using Close-In (CI) model
        // RSSI = -FSPL(f,1m) - 10×n×log₁₀(d/1m)
        // Note: Negative FSPL because we're calculating received power relative to 0 dBm transmit power
        return -fspl1m - 10.0 * PATH_LOSS_EXPONENT * Math.log10(distance / REFERENCE_DISTANCE_METERS);
    }

    /**
     * Calculates expected RSSI using default frequency for backward compatibility.
     * Uses 2.4 GHz as default frequency when frequency information is not available.
     * 
     * @param distance Distance in meters
     * @return Expected RSSI in dBm
     * @deprecated Use calculateExpectedRSSI(double distance, double frequency) for better accuracy
     */
    @Deprecated
    private double calculateExpectedRSSI(double distance) {
        // Default to 2.4 GHz (2437 MHz center frequency) for backward compatibility
        double defaultFrequency = 2.437e9; // 2.437 GHz in Hz
        return calculateExpectedRSSI(distance, defaultFrequency);
    }

    /**
     * Calculates 3D distance between two points using Haversine formula.
     * Accounts for Earth's curvature in horizontal distance.
     * When altitude data is missing, falls back to 2D distance calculation.
     * 
     * @param lat1 First point latitude
     * @param lon1 First point longitude
     * @param alt1 First point altitude (can be null)
     * @param lat2 Second point latitude
     * @param lon2 Second point longitude
     * @param alt2 Second point altitude (can be null)
     * @return 3D or 2D distance in meters
     */
    private double calculateDistance(double lat1, double lon1, Double alt1, 
                                   double lat2, double lon2, Double alt2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                  Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                  Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double horizontalDist = EARTH_RADIUS_METERS * c;
        
        // Only include vertical component if altitude data is available for both points
        if (alt1 != null && alt2 != null) {
            double verticalDist = alt2 - alt1;
            return Math.sqrt(horizontalDist * horizontalDist + verticalDist * verticalDist);
        } else {
            // 2D distance only (horizontal)
            return horizontalDist;
        }
    }

    @Override
    public double getConfidence() {
        return HIGH_CONFIDENCE_THRESHOLD; // Base confidence for maximum likelihood method
    }

    @Override
    public String getName() {
        return "maximum_likelihood";
    }

    @Override
    public double getSignalDistributionMultiplier(SignalDistributionFactor factor) {
        switch (factor) {
            case UNIFORM_SIGNALS:
                return MAXIMUM_LIKELIHOOD_UNIFORM_SIGNALS_MULTIPLIER;
            case MIXED_SIGNALS:
                return MAXIMUM_LIKELIHOOD_MIXED_SIGNALS_MULTIPLIER;
            case SIGNAL_OUTLIERS:
                return MAXIMUM_LIKELIHOOD_SIGNAL_OUTLIERS_MULTIPLIER;
            default:
                return MAXIMUM_LIKELIHOOD_MIXED_SIGNALS_MULTIPLIER;
        }
    }

    @Override
    public double getBaseWeight(APCountFactor factor) {
        switch (factor) {
            case SINGLE_AP:
                return MAXIMUM_LIKELIHOOD_SINGLE_AP_WEIGHT;      // Not applicable for single AP
            case TWO_APS:
                return MAXIMUM_LIKELIHOOD_TWO_APS_WEIGHT;        // Not applicable for two APs
            case THREE_APS:
                return MAXIMUM_LIKELIHOOD_THREE_APS_WEIGHT;      // Not applicable for three APs
            case FOUR_PLUS_APS:
                return MAXIMUM_LIKELIHOOD_FOUR_PLUS_APS_WEIGHT;  // Optimal for four+ APs
            default:
                return 0.0;
        }
    }
    
    @Override
    public double getSignalQualityMultiplier(SignalQualityFactor factor) {
        switch (factor) {
            case STRONG_SIGNAL:
                return MAXIMUM_LIKELIHOOD_STRONG_SIGNAL_MULTIPLIER;
            case MEDIUM_SIGNAL:
                return MAXIMUM_LIKELIHOOD_MEDIUM_SIGNAL_MULTIPLIER;
            case WEAK_SIGNAL:
                return MAXIMUM_LIKELIHOOD_WEAK_SIGNAL_MULTIPLIER;
            case VERY_WEAK_SIGNAL:
                return MAXIMUM_LIKELIHOOD_VERY_WEAK_SIGNAL_MULTIPLIER;
            default:
                return MAXIMUM_LIKELIHOOD_MEDIUM_SIGNAL_MULTIPLIER;
        }
    }
    
    @Override
    public double getGeometricQualityMultiplier(GeometricQualityFactor factor) {
        switch (factor) {
            case EXCELLENT_GDOP:
                return MAXIMUM_LIKELIHOOD_EXCELLENT_GDOP_MULTIPLIER;
            case GOOD_GDOP:
                return MAXIMUM_LIKELIHOOD_GOOD_GDOP_MULTIPLIER;
            case FAIR_GDOP:
                return MAXIMUM_LIKELIHOOD_FAIR_GDOP_MULTIPLIER;
            case POOR_GDOP:
                return MAXIMUM_LIKELIHOOD_POOR_GDOP_MULTIPLIER;
            default:
                return MAXIMUM_LIKELIHOOD_GOOD_GDOP_MULTIPLIER;
        }
    }

    /**
     * Measurement model for a single access point.
     * Encapsulates all information needed for likelihood calculation:
     * - AP location (lat, lon, alt)
     * - Signal strength (RSSI)
     * - Measurement uncertainty (stdDev)
     * - Location confidence from database
     * - Operating frequency for accurate path loss modeling
     */
    private static class MeasurementModel {
        final double lat;
        final double lon;
        final double alt;
        final double rssi;
        final double stdDev;
        final double confidence;
        final double frequencyHz;

        MeasurementModel(double lat, double lon, Double alt, double rssi, 
                       double stdDev, double confidence, double frequencyHz) {
            this.lat = lat;
            this.lon = lon;
            this.alt = alt != null ? alt : 0.0;
            this.rssi = rssi;
            this.stdDev = stdDev;
            this.confidence = confidence;
            this.frequencyHz = frequencyHz;
        }
    }

    /**
     * Calculates accuracy based on base accuracy value, GDOP factor, and signal strength.
     * The calculation differs for strong vs. weak signals:
     * 
     * For strong signals:
     *   accuracy = baseAccuracy * (1.0 + (gdopFactor - 1.0) * GDOP_ACCURACY_MULTIPLIER)
     * 
     * For weaker signals:
     *   accuracy = baseAccuracy * gdopFactor
     * 
     * This ensures that GDOP has a controlled effect on strong signals (maintaining high accuracy)
     * while having a stronger impact on weak signals (where geometry is more critical).
     * 
     * @param baseAccuracy The initial accuracy estimate
     * @param gdopFactor The GDOP factor (1.0-4.0)
     * @param avgSignalStrength Average signal strength (dBm)
     * @return Refined accuracy value (meters)
     */
    private double calculateAccuracy(double baseAccuracy, double gdopFactor, double avgSignalStrength) {
        double refinedAccuracy;
        
        if (avgSignalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            // For strong signals, apply a controlled GDOP adjustment
            refinedAccuracy = baseAccuracy * (1.0 + (gdopFactor - 1.0) * GDOPCalculator.GDOP_ACCURACY_MULTIPLIER);
            
            // Ensure accuracy is within expected range for strong signals
            refinedAccuracy = Math.max(MIN_ACCURACY_METERS, Math.min(MAX_ACCURACY_STRONG_SIGNALS_METERS, refinedAccuracy));
        } else {
            // For weaker signals, apply full GDOP adjustment
            refinedAccuracy = baseAccuracy * gdopFactor;
            
            // Cap maximum accuracy value
            refinedAccuracy = Math.min(MAX_ACCURACY_ANY_SCENARIO_METERS, refinedAccuracy);
        }
        
        return refinedAccuracy;
    }

    /**
     * Calculates adaptive standard deviation based on signal strength.
     * 
     * @param signalStrength Signal strength in dBm
     * @return Adaptive standard deviation in dBm
     */
    private double calculateAdaptiveStdDev(double signalStrength) {
        if (signalStrength >= STRONG_SIGNAL_THRESHOLD_DBM) {
            return STRONG_SIGNAL_STD_DEV_DBM;
        } else if (signalStrength >= WEAK_SIGNAL_THRESHOLD_DBM) {
            return MEDIUM_SIGNAL_STD_DEV_DBM;
        } else {
            return WEAK_SIGNAL_STD_DEV_DBM;
        }
    }
} 