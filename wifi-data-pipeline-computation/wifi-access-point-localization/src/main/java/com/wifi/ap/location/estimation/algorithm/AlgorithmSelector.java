package com.wifi.ap.location.estimation.algorithm;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.estimation.algorithm.impl.LocalizationAlgorithmType;
import com.wifi.ap.location.estimation.state.DataMaturityTier;
import com.wifi.ap.location.measurements.WifiMeasurements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Selects appropriate localization algorithms based on two-phase hybrid algorithm selection framework.
 *
 * <p>Implements Section 3.6.1 of the requirements for two-phase hybrid algorithm selection:
 * 
 * <p><strong>Phase 1: Build-up Phase (Establishing Quality Prior)</strong>
 * <ul>
 *   <li>N < 20: Skip processing, log warning (insufficient for reliable localization)</li>
 *   <li>20 ≤ N < 50: Use Weighted Centroid Localization (WCL)</li>
 *   <li>50 ≤ N < 100: Use Maximum Likelihood Estimation (MLE)</li>
 *   <li>N ≥ 100: Use MLE and mark AP as "Highly Mature" → PHASE SWITCH</li>
 * </ul>
 * 
 * <p><strong>Phase 2: Iterative Refinement Phase</strong>
 * <ul>
 *   <li>Use Bayesian Inference exclusively for all subsequent processing</li>
 *   <li>Process only new measurements (not historical data)</li>
 *   <li>Historical measurement data deleted, only state maintained</li>
 *   <li>Continue indefinitely with iterative approach</li>
 * </ul>
 *
 * <p>The selector evaluates both measurement count and current processing phase to determine
 * the most appropriate algorithm and whether phase transition should occur.
 */
@Component
public class AlgorithmSelector {

    private static final Logger logger = LoggerFactory.getLogger(AlgorithmSelector.class);



    /**
     * Selects algorithm based on two-phase hybrid framework with current AP location context.
     *
     * @param measurements List of filtered measurements (after outlier removal)
     * @param currentLocation Current AP location including state and location information for Bayesian prior
     * @return Algorithm selection with reasoning
     */
    public Optional<AlgorithmSelections> select(WifiMeasurements measurements, APLocation currentLocation) {
        if (measurements == null) {
            throw new IllegalArgumentException("Measurements cannot be null");
        }

        // Check if current state is already highly mature - if so, always use Bayesian
        if (isAlreadyHighlyMature(currentLocation)) {
            return selectBayesianForMatureState(measurements, currentLocation);
        }

        // Otherwise, use measurement maturity for algorithm selection
        return selectBasedOnMeasurementMaturity(measurements);
    }

    private static boolean isAlreadyHighlyMature(APLocation currentLocation) {
        return currentLocation != null && currentLocation.getApState() != null &&
                currentLocation.getApState()
                               .dataMaturityTier() == DataMaturityTier.HIGHLY_MATURE;
    }

    /**
     * Selects algorithm based on two-phase hybrid framework for new AP (no existing state).
     *
     * @param measurements List of filtered measurements (after outlier removal)
     * @return Algorithm selection with reasoning
     */
    public Optional<AlgorithmSelections> select(WifiMeasurements measurements) {
        if (measurements == null) {
            throw new IllegalArgumentException("Measurements cannot be null");
        }

        return selectBasedOnMeasurementMaturity(measurements);
    }

    /**
     * Selects Bayesian inference algorithm for APs that are already in highly mature state.
     * 
     * <p>Validates that sufficient measurements are available for Bayesian processing
     * per Section 3.2 requirement (minimum 20 measurements per batch).
     * 
     * @param measurements List of new measurements to process
     * @param currentLocation Current AP location with highly mature state
     * @return Algorithm selection for Bayesian inference, or empty if insufficient measurements
     */
    private Optional<AlgorithmSelections> selectBayesianForMatureState(WifiMeasurements measurements, APLocation currentLocation) {
        int measurementCount = measurements.size();
        
        // Check batch size requirement for Bayesian processing (Section 3.2 of bayesian_pso_spec.md)
        if (measurementCount < 20) {
            String reason = String.format("Insufficient measurements for Bayesian update: %d < 20 (required batch size)", measurementCount);
            logger.debug("Skipping Bayesian inference: {}", reason);
            return Optional.empty(); // Let system accumulate more measurements
        }
        
        String reason = String.format("Current state is highly mature, using Bayesian inference with %d new measurements", measurementCount);
        logger.info("Selected Bayesian Inference: {}", reason);
        
        return Optional.of(AlgorithmSelections.single(
                () -> LocalizationAlgorithmType.BAYESIAN.implementation.estimateLocation(measurements, currentLocation),
                LocalizationAlgorithmType.BAYESIAN,
                reason
        ));
    }

    /**
     * Selects algorithm based on measurement count maturity tier (for new APs or non-mature existing APs).
     * 
     * @param measurements List of filtered measurements
     * @return Algorithm selection based on measurement maturity
     */
    private Optional<AlgorithmSelections> selectBasedOnMeasurementMaturity(WifiMeasurements measurements) {
        int measurementCount = measurements.size();
        DataMaturityTier maturityTier = DataMaturityTier.fromMeasurementCount(measurementCount);

        logger.debug("Algorithm selection based on measurement maturity - Tier: {}, Measurements: {}", maturityTier, measurementCount);

        return switch (maturityTier) {
            case INSUFFICIENT -> {
                String reason = getInsufficientReason(measurementCount);
                logger.warn("Skipping algorithm execution: {}", reason);
                yield Optional.empty();
            }

            case BOOTSTRAP -> {
                String reason = String.format("Bootstrap tier: %d measurements, using WCL algorithm", measurementCount);
                logger.info("Selected Weighted Centroid Localization: {}", reason);
                yield Optional.of(AlgorithmSelections.single(
                        () -> LocalizationAlgorithmType.WCL.implementation.estimateLocation(measurements),
                        LocalizationAlgorithmType.WCL,
                        reason
                ));
            }

            case MATURE -> {
                String reason = String.format("Mature tier: %d measurements, using MLE algorithm", measurementCount);
                logger.info("Selected Maximum Likelihood Estimation: {}", reason);
                yield Optional.of(AlgorithmSelections.single(
                        () -> LocalizationAlgorithmType.MLE.implementation.estimateLocation(measurements),
                        LocalizationAlgorithmType.MLE,
                        reason
                ));
            }

            case HIGHLY_MATURE -> {
                // Reaching highly mature for first time - use MLE to establish high-quality prior
                String reason = String.format("Reaching highly mature tier: %d measurements, using MLE algorithm to establish prior", measurementCount);
                logger.info("Selected Maximum Likelihood Estimation: {}", reason);
                yield Optional.of(AlgorithmSelections.single(
                        () -> LocalizationAlgorithmType.MLE.implementation.estimateLocation(measurements),
                        LocalizationAlgorithmType.MLE,
                        reason
                ));
            }
        };
    }


    private static String getInsufficientReason(int measurementCount) {
        return String.format("Insufficient data: %d measurements (minimum %d required)",
                             measurementCount, DataMaturityTier.getInsufficientThreshold());
    }
}
