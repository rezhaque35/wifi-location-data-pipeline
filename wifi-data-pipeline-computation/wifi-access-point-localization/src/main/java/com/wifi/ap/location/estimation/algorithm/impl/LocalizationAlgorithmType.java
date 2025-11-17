package com.wifi.ap.location.estimation.algorithm.impl;

import com.wifi.ap.location.estimation.algorithm.LocalizationAlgorithm;
import com.wifi.ap.location.estimation.accuracy.WclAccuracyCalculator;
import com.wifi.ap.location.measurements.math.GeographicCentroidCalculator;

/**
 * Enumeration of localization algorithm types used in the AP localization service.
 * 
 * <p>Represents the different algorithms available for estimating Access Point locations
 * based on WiFi measurement data. Each algorithm is instantiated with its required dependencies.
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
public enum LocalizationAlgorithmType {
    
    /**
     * Weighted Centroid Localization.
     * Used for bootstrap tier (20-49 measurements).
     */
    WCL(new WeightedCentroidLocalization(
        new GeographicCentroidCalculator(), 
        new WclAccuracyCalculator()
    )),
    
    /**
     * Maximum Likelihood Estimation.
     * Used for mature tier (50-99 measurements) and initial highly mature tier.
     */
    MLE(new MaximumLikelihoodEstimation()),
    
    /**
     * Sequential Bayesian Update.
     * Used for iterative processing when AP is already highly mature.
     * Requires prior AP location from MLE.
     */
    BAYESIAN(new SequentialBayesianUpdate());

    LocalizationAlgorithmType(LocalizationAlgorithm algorithm) {
        this.implementation = algorithm;
    }

    public final LocalizationAlgorithm implementation;

}
