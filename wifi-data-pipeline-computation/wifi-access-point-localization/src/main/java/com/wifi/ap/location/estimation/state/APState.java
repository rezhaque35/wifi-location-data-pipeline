package com.wifi.ap.location.estimation.state;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.estimation.algorithm.impl.LocalizationAlgorithmType;

import java.time.Instant;

/**
 * Unified AP Localization State record for both MLE and Bayesian algorithms.
 * 
 * <p>This record provides all state information required for localization algorithms,
 * serving both Maximum Likelihood Estimation (MLE) and Bayesian inference processing.
 * It complements {@link APLocation} by providing algorithm-specific state that persists
 * between processing iterations.
 * 
 * <p><strong>Unified State Categories:</strong>
 * <ul>
 *   <li><strong>Calculation State:</strong> Data maturity, measurement counts, algorithm history</li>
 *   <li><strong>Uncertainty Representation:</strong> Covariance matrix for both MLE and Bayesian algorithms</li>
 *   <li><strong>Bayesian State:</strong> PSO convergence status, information gain, iteration counts</li>
 *   <li><strong>Quality Metrics:</strong> Outlier counts, accuracy metrics, consistency scores</li>
 *   <li><strong>Processing Metadata:</strong> Timestamps and versioning for concurrent updates</li>
 * </ul>
 * 
 * <p><strong>Design Principles:</strong>
 * <ul>
 *   <li><strong>Algorithm Agnostic:</strong> Single covariance matrix serves both MLE and Bayesian needs</li>
 *   <li><strong>State Persistence:</strong> Bayesian inference builds upon MLE state</li>
 *   <li><strong>Unified Interface:</strong> Eliminates adapter patterns for cleaner design</li>
 * </ul>
 * 
 * <p><strong>Usage Pattern:</strong>
 * <pre>
 * APLocation apLocation = // current location estimate
 * APState apState = // algorithm state (MLE → Bayesian progression)
 * 
 * // MLE processing updates covariance matrix
 * APState mleUpdatedState = apState.withCovarianceMatrix(mleCovariance);
 * 
 * // Bayesian processing uses and updates the same covariance matrix
 * APState bayesianUpdatedState = mleUpdatedState.withBayesianUpdate(
 *     newCovariance, informationGain, convergenceAchieved);
 * </pre>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 2.0
 * @since 1.0
 */
public record APState(
    
    // ==================== Calculation State ====================
    /**
     * Data maturity tier based on measurement count after outlier removal.
     * Values: INSUFFICIENT, BOOTSTRAP, MATURE, HIGHLY_MATURE
     */
    DataMaturityTier dataMaturityTier,
    
    /**
     * Total number of measurements processed for this AP.
     */
    Integer totalMeasurementCount,
    
    /**
     * Number of valid measurements after outlier removal.
     */
    Integer validMeasurementCount,
    
    /**
     * Last localization algorithm used.
     * Values: WCL, MLE, BAYESIAN
     */
    LocalizationAlgorithmType lastAlgorithmUsed,
    
    /**
     * Reasoning for algorithm selection.
     */
    String algorithmUsedReasoning,
    
    // ==================== Unified Uncertainty Representation ====================
    /**
     * Covariance matrix representing location uncertainty.
     * 
     * <p>This matrix serves both MLE and Bayesian algorithms:
     * <ul>
     *   <li><strong>MLE Phase:</strong> Computed from Fisher Information Matrix or accuracy estimates</li>
     *   <li><strong>Bayesian Phase:</strong> Used as prior, updated to posterior covariance</li>
     * </ul>
     * 
     * <p>Mathematical representation: 2×2 matrix [σ²_lat, σ_lat,lon; σ_lat,lon, σ²_lon]
     * enabling proper uncertainty quantification for geographic coordinates.
     */
    CovarianceMatrix covarianceMatrix,
    
    // ==================== Bayesian Algorithm State ====================
    /**
     * Information gain from last Bayesian update (in nats).
     * 
     * <p>Measures the reduction in entropy achieved by incorporating new measurements
     * into the prior distribution. Higher values indicate more informative updates.
     * 
     * <p>Null if Bayesian algorithm has never been applied to this AP.
     */
    Double informationGain,
    
    /**
     * Whether PSO algorithm achieved convergence in last Bayesian update.
     * 
     * <p>Convergence indicates the particle swarm reached a stable solution within
     * the iteration limit. Non-convergence may indicate insufficient measurement
     * quality or complex likelihood landscape.
     * 
     * <p>Null if Bayesian algorithm has never been applied to this AP.
     */
    Boolean psoConvergenceAchieved,
    
    /**
     * Number of PSO iterations performed in last Bayesian update.
     * 
     * <p>Tracks computational effort and can be used for performance monitoring.
     * Lower iteration counts with convergence indicate good measurement geometry.
     * 
     * <p>Null if Bayesian algorithm has never been applied to this AP.
     */
    Integer psoIterations,
    
    // ==================== Quality Metrics ====================
    /**
     * Number of global outliers detected.
     */
    Integer globalOutlierCount,
    
    /**
     * Number of local outliers detected.
     */
    Integer localOutlierCount,
    
    /**
     * Average location accuracy of measurements used.
     */
    Double averageLocationAccuracy,
    
    /**
     * Average RSSI of measurements used.
     */
    Double averageRssi,
    
    /**
     * Consistency score based on measurement distribution.
     */
    Double consistencyScore,
    
    // ==================== Processing Metadata ====================
    /**
     * Timestamp when this AP state was last updated.
     */
    Instant lastUpdatedTimestamp,
    
    /**
     * Version counter for concurrent update detection.
     */
    Long stateVersion
) {
    
    
    
    
    /**
     * Creates a new APState with updated measurement counts and maturity tier.
     * 
     * @param newTotalCount New total measurement count
     * @param newValidCount New valid measurement count (after outlier removal)
     * @return New APState with updated counts and appropriate maturity tier
     */
    public APState withUpdatedCounts(int newTotalCount, int newValidCount) {
        DataMaturityTier newTier = determineMaturityTier(newValidCount);
        
        return new APState(
            newTier, newTotalCount, newValidCount,
            lastAlgorithmUsed, algorithmUsedReasoning,
            covarianceMatrix,
            informationGain, psoConvergenceAchieved, psoIterations,
            globalOutlierCount, localOutlierCount,
            averageLocationAccuracy, averageRssi, consistencyScore,
            Instant.now(), stateVersion != null ? stateVersion + 1 : 1L
        );
    }
    
    /**
     * Creates a new APState with updated algorithm information.
     * 
     * @param algorithm Algorithm used for this estimate
     * @param algorithmReasoning Reasoning for algorithm selection
     * @return New APState with updated algorithm information
     */
    public APState withUpdatedAlgorithm(LocalizationAlgorithmType algorithm, String algorithmReasoning) {
        return new APState(
            dataMaturityTier, totalMeasurementCount, validMeasurementCount,
            algorithm, algorithmReasoning,
            covarianceMatrix,
            informationGain, psoConvergenceAchieved, psoIterations,
            globalOutlierCount, localOutlierCount,
            averageLocationAccuracy, averageRssi, consistencyScore,
            Instant.now(), stateVersion != null ? stateVersion + 1 : 1L
        );
    }
    
    /**
     * Creates a new APState with updated covariance matrix from MLE processing.
     * 
     * @param newCovarianceMatrix Computed covariance matrix from MLE or other algorithm
     * @return New APState with updated uncertainty representation
     */
    public APState withCovarianceMatrix(CovarianceMatrix newCovarianceMatrix) {
        return new APState(
            dataMaturityTier, totalMeasurementCount, validMeasurementCount,
            lastAlgorithmUsed, algorithmUsedReasoning,
            newCovarianceMatrix,
            informationGain, psoConvergenceAchieved, psoIterations,
            globalOutlierCount, localOutlierCount,
            averageLocationAccuracy, averageRssi, consistencyScore,
            Instant.now(), stateVersion != null ? stateVersion + 1 : 1L
        );
    }
    
    /**
     * Creates a new APState with Bayesian inference update results and updated quality metrics.
     * 
     * @param newCovarianceMatrix Updated posterior covariance matrix
     * @param newInformationGain Information gain from Bayesian update (nats)
     * @param convergenceAchieved Whether PSO convergence was achieved
     * @param psoIterationCount Number of PSO iterations performed
     * @param newMeasurements New measurements used in this Bayesian update
     * @return New APState with Bayesian update information and aggregated quality metrics
     */
    public APState withBayesianUpdate(CovarianceMatrix newCovarianceMatrix, 
                                     double newInformationGain,
                                     boolean convergenceAchieved, 
                                     int psoIterationCount,
                                     com.wifi.ap.location.measurements.WifiMeasurements newMeasurements) {
        
        int newMeasurementCount = newMeasurements.size();
        
        // Update measurement counts with new batch
        int updatedTotalCount = (totalMeasurementCount != null ? totalMeasurementCount : 0) + newMeasurementCount;
        int updatedValidCount = (validMeasurementCount != null ? validMeasurementCount : 0) + newMeasurementCount;
        
        // Determine new maturity tier based on updated valid count
        DataMaturityTier updatedTier = determineMaturityTier(updatedValidCount);
        
        // Calculate aggregated quality metrics (weighted by measurement count)
        int previousCount = (validMeasurementCount != null ? validMeasurementCount : 0);
        
        // Aggregate location accuracy with new measurements
        double newLocationAccuracy = newMeasurements.getAverageGpsAccuracy();
        Double updatedLocationAccuracy = aggregateWeightedAverage(
            averageLocationAccuracy, previousCount, newLocationAccuracy, newMeasurementCount);
        
        // Aggregate RSSI with new measurements  
        double newAverageRssi = calculateAverageRssi(newMeasurements);
        Double updatedAverageRssi = aggregateWeightedAverage(
            averageRssi, previousCount, newAverageRssi, newMeasurementCount);
        
        // Update consistency score (RSSI standard deviation - lower is better)
        double newConsistencyScore = newMeasurements.getRssiStandardDeviation();
        Double updatedConsistencyScore = aggregateWeightedAverage(
            consistencyScore, previousCount, newConsistencyScore, newMeasurementCount);
        
        return new APState(
            updatedTier, updatedTotalCount, updatedValidCount,
            LocalizationAlgorithmType.BAYESIAN, "Bayesian PSO-enhanced inference",
            newCovarianceMatrix,
            newInformationGain, convergenceAchieved, psoIterationCount,
            globalOutlierCount, localOutlierCount,
            updatedLocationAccuracy, updatedAverageRssi, updatedConsistencyScore,
            Instant.now(), stateVersion != null ? stateVersion + 1 : 1L
        );
    }
    
    /**
     * Aggregates historical average with new measurements using weighted averaging.
     * 
     * @param historicalValue Historical average value (null if no history)
     * @param historicalCount Count of measurements in historical average  
     * @param newValue New average from current measurements
     * @param newCount Count of new measurements
     * @return Weighted average combining historical and new data
     */
    private static Double aggregateWeightedAverage(Double historicalValue, int historicalCount, 
                                                 double newValue, int newCount) {
        if (historicalValue == null || historicalCount == 0) {
            return newValue; // No historical data, use new value
        }
        
        if (newCount == 0) {
            return historicalValue; // No new data, keep historical
        }
        
        // Weighted average: (historical_sum + new_sum) / total_count
        double historicalSum = historicalValue * historicalCount;
        double newSum = newValue * newCount;
        int totalCount = historicalCount + newCount;
        
        return (historicalSum + newSum) / totalCount;
    }
    
    /**
     * Calculates average RSSI from WiFi measurements.
     * 
     * @param measurements WiFi measurements collection
     * @return Average RSSI in dBm, or -80.0 as conservative default if no RSSI data
     */
    private static double calculateAverageRssi(com.wifi.ap.location.measurements.WifiMeasurements measurements) {
        return measurements.measurements().stream()
            .filter(m -> m.rssi() != null)
            .mapToDouble(m -> m.rssi().doubleValue())
            .average()
            .orElse(-80.0); // Conservative default for unknown RSSI
    }
    
    /**
     * Determines the data maturity tier based on valid measurement count.
     * 
     * @param validCount Number of valid measurements after outlier removal
     * @return Appropriate DataMaturityTier
     */
    private static DataMaturityTier determineMaturityTier(int validCount) {
        return DataMaturityTier.fromMeasurementCount(validCount);
    }
    
    
    /**
     * Checks if the AP state is mature enough for Bayesian processing.
     * 
     * <p>Based on Section 2.2 of bayesian_pso_spec.md requirements:
     * - AP must have completed MLE localization phase
     * - Must have valid covariance matrix
     * - Must meet minimum maturity requirements
     * 
     * @return true if state is ready for Bayesian inference
     */
    public boolean isMatureForBayesianProcessing() {
        // Must have reached at least MATURE tier
        if (dataMaturityTier == null || 
            dataMaturityTier == DataMaturityTier.INSUFFICIENT || 
            dataMaturityTier == DataMaturityTier.BOOTSTRAP) {
            return false;
        }
        
        // Must have valid covariance matrix from MLE processing
        if (covarianceMatrix == null) {
            return false;
        }
        
        // Must have gone through MLE processing
        return lastAlgorithmUsed != null && 
               (lastAlgorithmUsed == LocalizationAlgorithmType.MLE || 
                lastAlgorithmUsed == LocalizationAlgorithmType.BAYESIAN);
    }
    
    /**
     * Gets effective sample size from valid measurement count.
     * 
     * @return Effective sample size for uncertainty calculations
     */
    public int getEffectiveSampleSize() {
        return validMeasurementCount != null ? validMeasurementCount : 0;
    }
    
    /**
     * Creates a basic APState when no specific state is available.
     * 
     * @return Basic APState with default values
     */
    public static APState createBasic() {
        return new APState(
            DataMaturityTier.INSUFFICIENT, 0, 0,
            null, null,
            null,
            null, null, null,
            0, 0,
            null, null, null,
            Instant.now(), 1L
        );
    }
}
