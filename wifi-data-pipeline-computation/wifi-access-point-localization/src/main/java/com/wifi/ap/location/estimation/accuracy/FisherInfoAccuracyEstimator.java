// src/main/java/com/wifi/ap/location/estimation/accuracy/FisherInformationMLEAccuracyEstimator.java
package com.wifi.ap.location.estimation.accuracy;

import com.wifi.ap.location.estimation.fisher.FisherInformationCalculator;
import com.wifi.ap.location.estimation.state.CovarianceMatrix;
import lombok.Getter;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fisher Information Matrix (FIM) based accuracy estimator for Maximum Likelihood Estimation.
 * 
 * <p><strong>Statistical Foundation:</strong>
 * This estimator derives positioning accuracy directly from the curvature of the likelihood landscape
 * at the MLE solution, following the Cramér-Rao Lower Bound (CRLB) theory. The approach provides
 * statistically rigorous confidence bounds tied to the actual optimization problem.
 * 
 * <p><strong>Mathematical Model:</strong>
 * <pre>
 * Covariance Matrix: Σ = H⁻¹
 * Where H is the Hessian matrix of the negative log-likelihood function at the MLE point
 * 
 * Standard Errors: σ_lat = √(Σ₁₁), σ_lon = √(Σ₂₂)
 * Horizontal Accuracy: r = k × √(σ_lat² + σ_lon²)
 * Where k is the confidence multiplier (e.g., 1.96 for 95% confidence)
 * </pre>
 * 
 * <p><strong>Key Advantages over Heuristic Methods:</strong>
 * <ul>
 *   <li><strong>Statistically Grounded:</strong> Based on MLE theory and Fisher Information</li>
 *   <li><strong>Problem-Specific:</strong> Reflects actual measurement geometry and quality</li>
 *   <li><strong>Adaptive:</strong> Automatically adjusts to measurement distribution and noise</li>
 *   <li><strong>Theoretically Sound:</strong> Provides optimal lower bounds under Gaussian assumptions</li>
 *   <li><strong>Consistent Weighting:</strong> Uses exact same inverse variance weighting as MLE optimization</li>
 * </ul>
 * 
 * <p><strong>Research Citations:</strong>
 * - Kay, S.M. (1993): "Fundamentals of Statistical Signal Processing, Volume I" - Fisher Information theory
 * - Van Trees, H.L. (2001): "Detection, Estimation, and Modulation Theory" - Cramér-Rao bounds
 * - Patwari et al. (2005): "Locating the nodes: cooperative localization in wireless sensor networks" - RSSI-based MLE
 * - Gezici et al. (2008): "Localization via Ultra-Wideband Radios" - Positioning accuracy bounds
 * 
 * @author WiFi Access Point Localization Team
 * @since 1.0
 */
public class FisherInfoAccuracyEstimator {
    
    private static final Logger logger = LoggerFactory.getLogger(FisherInfoAccuracyEstimator.class);
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private FisherInfoAccuracyEstimator() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }
    
    // Constants for numerical computation
    
    // NOTE: HESSIAN_DELTA is now managed by FisherInformationCalculator
    
    
    /**
     * Minimum horizontal accuracy bound for WiFi positioning (meters).
     * 
     * <p><strong>Research Basis:</strong> Based on physical limitations of WiFi signal propagation
     * and typical indoor positioning accuracy. Conservative bound acknowledging that WiFi positioning
     * cannot achieve sub-meter accuracy due to signal propagation characteristics.
     * 
     * <p><strong>Source:</strong> Indoor positioning literature consensus - WiFi positioning typically
     * achieves 2-5 meter accuracy in optimal conditions. This represents a conservative lower bound.
     * 
     * <p><strong>Status:</strong> Framework-derived - requires empirical validation
     */
    private static final double MIN_ACCURACY_METERS = 3.0;
    
    /**
     * Maximum horizontal accuracy bound for WiFi positioning (meters).
     * 
     * <p><strong>Research Basis:</strong> Conservative upper bound indicating poor positioning quality
     * when Fisher Information Matrix calculation fails or provides unreliable estimates.
     * 
     * <p><strong>Source:</strong> WiFi positioning literature - accuracy beyond 100m indicates
     * fundamental measurement geometry issues or insufficient data quality.
     * 
     * <p><strong>Status:</strong> Framework-derived - requires empirical validation
     */
    private static final double MAX_ACCURACY_METERS = 100.0;
    
    // ========================================================================
    // Confidence Interval Z-Score Multipliers (Standard Normal Distribution)
    // ========================================================================
    
    /**
     * 68% confidence interval multiplier (1 standard deviation).
     * 
     * <p><strong>Statistical Foundation:</strong> For a standard normal distribution,
     * approximately 68% of values lie within ±1 standard deviation of the mean.</p>
     * 
     * <p><strong>Z-Score:</strong> 1.0 corresponds to the 84th percentile (one-tailed)
     * or ±1σ for two-tailed 68% confidence interval.</p>
     * 
     * <p><strong>Sources:</strong>
     * <ul>
     *   <li>Standard normal distribution tables (any statistics textbook)</li>
     *   <li>The empirical rule (68-95-99.7 rule) for normal distributions</li>
     *   <li>Montgomery, D.C. & Runger, G.C. (2010). "Applied Statistics and Probability for Engineers"</li>
     * </ul></p>
     */
    private static final double CONFIDENCE_68_MULTIPLIER = 1.0;
    
    /**
     * 95% confidence interval multiplier (1.96 standard deviations).
     * 
     * <p><strong>Statistical Foundation:</strong> For a standard normal distribution,
     * approximately 95% of values lie within ±1.96 standard deviations of the mean.</p>
     * 
     * <p><strong>Z-Score Derivation:</strong> 1.96 is derived from the standard normal
     * cumulative distribution function (CDF), where Φ(1.96) ≈ 0.975, giving a two-tailed
     * 95% confidence interval (2.5% in each tail).</p>
     * 
     * <p><strong>Sources:</strong>
     * <ul>
     *   <li>Standard normal distribution tables (z-table): P(|Z| ≤ 1.96) = 0.95</li>
     *   <li>NIST/SEMATECH e-Handbook of Statistical Methods</li>
     *   <li>Casella, G. & Berger, R.L. (2001). "Statistical Inference" (2nd ed.)</li>
     *   <li>ISO/IEC Guide 98-3:2008 (GUM) - Uncertainty of Measurement</li>
     * </ul></p>
     * 
     * <p><strong>Note:</strong> Often approximated as 2.0 for quick calculations, but 1.96
     * is the precise value for 95% confidence intervals.</p>
     */
    private static final double CONFIDENCE_95_MULTIPLIER = 1.96;
    
    /**
     * 99% confidence interval multiplier (2.58 standard deviations).
     * 
     * <p><strong>Statistical Foundation:</strong> For a standard normal distribution,
     * approximately 99% of values lie within ±2.58 standard deviations of the mean.</p>
     * 
     * <p><strong>Z-Score Derivation:</strong> 2.58 is derived from the standard normal
     * cumulative distribution function (CDF), where Φ(2.58) ≈ 0.995, giving a two-tailed
     * 99% confidence interval (0.5% in each tail).</p>
     * 
     * <p><strong>Sources:</strong>
     * <ul>
     *   <li>Standard normal distribution tables (z-table): P(|Z| ≤ 2.58) = 0.99</li>
     *   <li>NIST/SEMATECH e-Handbook of Statistical Methods</li>
     *   <li>Casella, G. & Berger, R.L. (2001). "Statistical Inference" (2nd ed.)</li>
     *   <li>ISO/IEC Guide 98-3:2008 (GUM) - Uncertainty of Measurement</li>
     * </ul></p>
     * 
     * <p><strong>Note:</strong> More precise value is 2.5758, but 2.58 is standard
     * in statistical tables and widely accepted for practical applications.</p>
     */
    private static final double CONFIDENCE_99_MULTIPLIER = 2.58;
    
    /**
     * Confidence levels supported by this estimator.
     */
    public enum ConfidenceLevel {
        CONFIDENCE_68(CONFIDENCE_68_MULTIPLIER, 0.68),
        CONFIDENCE_95(CONFIDENCE_95_MULTIPLIER, 0.95),
        CONFIDENCE_99(CONFIDENCE_99_MULTIPLIER, 0.99);
        
        private final double multiplier;
        private final double level;
        
        ConfidenceLevel(double multiplier, double level) {
            this.multiplier = multiplier;
            this.level = level;
        }
        
        public double getMultiplier() { return multiplier; }
        public double getLevel() { return level; }
    }
    
    /**
     * Result container for FIM-based accuracy calculation with optional covariance matrix.
     * 
     * <p>This unified result class supports both basic accuracy calculations and extended
     * MLE→Bayesian transition scenarios where covariance matrix information is needed.
     * 
     * <p><strong>Usage Patterns:</strong>
     * <ul>
     *   <li><strong>Basic Accuracy:</strong> Use without covariance matrix for positioning applications</li>
     *   <li><strong>MLE→Bayesian Transition:</strong> Include covariance matrix for state transitions</li>
     * </ul>
     */
    @Getter
    public static class AccuracyResult {
        private final double horizontalAccuracy;
        private final double latitudeStandardError;
        private final double longitudeStandardError;
        private final double confidenceLevel;
        private final boolean isValid;
        private final String calculationMethod;
        private final CovarianceMatrix covarianceMatrix; // Optional
        
        private AccuracyResult(double horizontalAccuracy, double latitudeStandardError, 
                              double longitudeStandardError, double confidenceLevel, 
                              boolean isValid, String calculationMethod,
                              com.wifi.ap.location.estimation.state.CovarianceMatrix covarianceMatrix) {
            this.horizontalAccuracy = horizontalAccuracy;
            this.latitudeStandardError = latitudeStandardError;
            this.longitudeStandardError = longitudeStandardError;
            this.confidenceLevel = confidenceLevel;
            this.isValid = isValid;
            this.calculationMethod = calculationMethod;
            this.covarianceMatrix = covarianceMatrix;
        }

        // Factory methods for basic accuracy results
        public static AccuracyResult valid(double horizontalAccuracy, double latStdErr, 
                                         double lonStdErr, double confidence, String method) {
            return new AccuracyResult(horizontalAccuracy, latStdErr, lonStdErr, confidence, true, method, null);
        }
        
        public static AccuracyResult invalid(double fallbackAccuracy, String reason) {
            return new AccuracyResult(fallbackAccuracy, 0.0, 0.0, 0.0, false, reason, null);
        }
        
        // Factory methods for MLE→Bayesian transition with covariance matrix
        public static AccuracyResult validWithCovariance(double horizontalAccuracy, double latStdErr,
                                                        double lonStdErr, double confidence, String method,
                                                        com.wifi.ap.location.estimation.state.CovarianceMatrix covarianceMatrix) {
            return new AccuracyResult(horizontalAccuracy, latStdErr, lonStdErr, confidence, true, method, covarianceMatrix);
        }
        
        public static AccuracyResult invalidWithCovariance(double fallbackAccuracy, String reason) {
            // Create minimal covariance matrix for fallback cases
            com.wifi.ap.location.estimation.state.CovarianceMatrix fallbackCovariance = 
                new com.wifi.ap.location.estimation.state.CovarianceMatrix(1e-6, 0.0, 0.0, 1e-6);
            return new AccuracyResult(fallbackAccuracy, 0.0, 0.0, 0.0, false, reason, fallbackCovariance);
        }
    }
    
    
    /**
     * Calculates statistically grounded horizontal accuracy using Fisher Information Matrix approach.
     * 
     * <p><strong>Statistical Foundation:</strong>
     * This method implements the theoretical optimal approach for deriving parameter uncertainty
     * from Maximum Likelihood Estimation by computing the inverse of the Fisher Information Matrix
     * (equivalently, the inverse Hessian of the negative log-likelihood function).
     * 
     * <p><strong>Mathematical Process:</strong>
     * <ol>
     *   <li>Compute the Hessian matrix of the negative log-likelihood at the MLE solution</li>
     *   <li>Invert the Hessian to obtain the covariance matrix (Σ = H⁻¹)</li>
     *   <li>Extract standard errors from diagonal elements: σ_lat = √Σ₁₁, σ_lon = √Σ₂₂</li>
     *   <li>Calculate horizontal accuracy radius: r = k√(σ_lat² + σ_lon²)</li>
     * </ol>
     * 
     * <p><strong>Advantages:</strong>
     * - Theoretically optimal under Gaussian noise assumptions
     * - Directly reflects the information content of measurements
     * - Automatically adapts to measurement geometry and quality
     * - Provides statistically meaningful confidence intervals
     * 
     * @param optimizedLocation the MLE solution location
     * @param objectiveFunction the negative log-likelihood function used in MLE optimization - guarantees consistency
     * @param confidenceLevel the desired confidence level for accuracy calculation
     * @return AccuracyResult containing horizontal accuracy and statistical details
     */
    public static AccuracyResult calculateHorizontalAccuracy(com.wifi.ap.location.Location optimizedLocation,
                                                           MultivariateFunction objectiveFunction,
                                                           ConfidenceLevel confidenceLevel) {
        
        logger.debug("Computing FIM-based accuracy at location: [{}, {}]",
                    optimizedLocation.latitude(), optimizedLocation.longitude());
        
        try {
            // Convert Location to double array for internal calculations
            double[] locationArray = optimizedLocation.asArray();
            
            // Compute numerical Hessian matrix using FisherInformationCalculator
            RealMatrix hessian = FisherInformationCalculator.computeHessianMatrix(locationArray, objectiveFunction);
            
            // Compute covariance matrix (inverse Hessian) using FisherInformationCalculator
            RealMatrix covariance = FisherInformationCalculator.computeCovarianceMatrix(hessian);
            
            // Extract standard errors
            double latitudeStandardError = Math.sqrt(Math.abs(covariance.getEntry(0, 0)));
            double longitudeStandardError = Math.sqrt(Math.abs(covariance.getEntry(1, 1)));
            
            // Calculate horizontal accuracy radius
            double rawAccuracy = confidenceLevel.getMultiplier() * 
                Math.sqrt(latitudeStandardError * latitudeStandardError + 
                         longitudeStandardError * longitudeStandardError);
            
            // Apply research-backed bounds
            double boundedAccuracy = Math.clamp(rawAccuracy, MIN_ACCURACY_METERS, MAX_ACCURACY_METERS);
            
            if (logger.isDebugEnabled()) {
                logger.debug("FIM accuracy calculation: lat_std={}, lon_std={}, raw={}m, bounded={}m", 
                           String.format("%.4f", latitudeStandardError), 
                           String.format("%.4f", longitudeStandardError), 
                           String.format("%.2f", rawAccuracy), 
                           String.format("%.2f", boundedAccuracy));
            }
            
            return AccuracyResult.valid(boundedAccuracy, latitudeStandardError, longitudeStandardError, 
                                      confidenceLevel.getLevel(), "Fisher Information Matrix");
            
        } catch (Exception e) {
            logger.warn("FIM accuracy calculation failed: {}, using fallback", e.getMessage());
            return AccuracyResult.invalid(MAX_ACCURACY_METERS, "FIM computation failed: " + e.getMessage());
        }
    }
    
    /**
     * Calculates both horizontal accuracy and covariance matrix for MLE→Bayesian transition.
     * 
     * <p><strong>MLE→Bayesian Integration:</strong>
     * This method provides everything needed for transitioning from MLE to Bayesian processing:
     * <ul>
     *   <li><strong>Accuracy:</strong> Standard horizontal accuracy for positioning applications</li>
     *   <li><strong>Covariance Matrix:</strong> Full 2×2 covariance matrix for Bayesian prior distribution</li>
     *   <li><strong>State Transition:</strong> Enables APState creation with proper uncertainty quantification</li>
     * </ul>
     * 
     * <p><strong>Mathematical Process:</strong>
     * <ol>
     *   <li>Compute Fisher Information Matrix (Hessian of negative log-likelihood)</li>
     *   <li>Invert to obtain parameter covariance matrix: Σ = H⁻¹</li>
     *   <li>Extract accuracy for positioning: r = k√(σ_lat² + σ_lon²)</li>
     *   <li>Return both accuracy and full covariance matrix for comprehensive state</li>
     * </ol>
     * 
     * <p><strong>Covariance Matrix Usage:</strong>
     * The returned covariance matrix can be directly used by:
     * - APState.withCovarianceMatrix() for storing MLE uncertainty
     * - BayesianInferenceLocalization for prior distribution sampling
     * - Uncertainty propagation in positioning calculations
     * 
     * @param optimizedLocation the MLE solution location
     * @param objectiveFunction the negative log-likelihood function used in MLE optimization
     * @param confidenceLevel the desired confidence level for accuracy calculation
     * @return AccuracyResult containing both accuracy and covariance information
     */
    public static AccuracyResult calculateAccuracy(com.wifi.ap.location.Location optimizedLocation,
                                                   MultivariateFunction objectiveFunction,
                                                   ConfidenceLevel confidenceLevel) {
        
        logger.debug("Computing FIM-based accuracy with covariance at location: [{}, {}]",
                    optimizedLocation.latitude(), optimizedLocation.longitude());
        
        try {
            // Convert Location to double array for internal calculations
            double[] locationArray = optimizedLocation.asArray();
            
            // Compute numerical Hessian matrix using FisherInformationCalculator
            RealMatrix hessian = FisherInformationCalculator.computeHessianMatrix(locationArray, objectiveFunction);
            
            // Compute covariance matrix (inverse Hessian) using FisherInformationCalculator
            RealMatrix covarianceMatrixRaw = FisherInformationCalculator.computeCovarianceMatrix(hessian);
            
            // Extract standard errors for accuracy calculation
            double latitudeStandardError = Math.sqrt(Math.abs(covarianceMatrixRaw.getEntry(0, 0)));
            double longitudeStandardError = Math.sqrt(Math.abs(covarianceMatrixRaw.getEntry(1, 1)));
            
            // Calculate horizontal accuracy radius
            double rawAccuracy = confidenceLevel.getMultiplier() * 
                Math.sqrt(latitudeStandardError * latitudeStandardError + 
                         longitudeStandardError * longitudeStandardError);
            
            // Apply research-backed bounds for accuracy
            double boundedAccuracy = Math.clamp(rawAccuracy, MIN_ACCURACY_METERS, MAX_ACCURACY_METERS);
            
            // Create CovarianceMatrix object for APState
            com.wifi.ap.location.estimation.state.CovarianceMatrix covarianceMatrix = 
                new com.wifi.ap.location.estimation.state.CovarianceMatrix(
                    covarianceMatrixRaw.getEntry(0, 0), // σ_lat²
                    covarianceMatrixRaw.getEntry(0, 1), // σ_lat,lon 
                    covarianceMatrixRaw.getEntry(1, 0), // σ_lon,lat 
                    covarianceMatrixRaw.getEntry(1, 1)  // σ_lon²
                );
            
            if (logger.isDebugEnabled()) {
                logger.debug("FIM accuracy+covariance: lat_std={}, lon_std={}, accuracy={}m, det={}", 
                           String.format("%.4f", latitudeStandardError), 
                           String.format("%.4f", longitudeStandardError), 
                           String.format("%.2f", boundedAccuracy),
                           String.format("%.2e", covarianceMatrix.determinant()));
            }
            
            return AccuracyResult.validWithCovariance(boundedAccuracy, latitudeStandardError, longitudeStandardError, 
                                                     confidenceLevel.getLevel(), "Fisher Information Matrix", 
                                                     covarianceMatrix);
            
        } catch (Exception e) {
            logger.warn("FIM accuracy+covariance calculation failed: {}, using fallback", e.getMessage());
            return AccuracyResult.invalidWithCovariance(MAX_ACCURACY_METERS, "FIM computation failed: " + e.getMessage());
        }
    }

    // NOTE: Hessian and covariance matrix computation methods moved to FisherInformationCalculator
    // These methods are now centralized for reuse across multiple algorithms
    
    /**
     * Convenience method for 95% confidence calculation using provided objective function.
     * 
     * <p><strong>Recommended API:</strong> This is the preferred method when the objective function
     * is already available from MLE optimization, ensuring perfect consistency and avoiding
     * unnecessary function recreation.
     * 
     * @param optimizedLocation the MLE solution location
     * @param objectiveFunction the negative log-likelihood function used in MLE optimization
     * @return AccuracyResult containing horizontal accuracy at 95% confidence
     */
    public static AccuracyResult calculateHorizontalAccuracy95(com.wifi.ap.location.Location optimizedLocation, MultivariateFunction objectiveFunction) {
        return calculateHorizontalAccuracy(optimizedLocation, objectiveFunction, ConfidenceLevel.CONFIDENCE_95);
    }
}
