package com.wifi.ap.location.estimation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified sample size classification for localization algorithm confidence assessment.
 * 
 * <p>This enum consolidates sample size thresholds and confidence factors across all
 * localization algorithms, providing algorithm-specific scaling factors that are
 * mathematically appropriate for each context while ensuring consistent sample size
 * classification logic.
 * 
 * <p><strong>Research Foundation:</strong>
 * Sample size effects on measurement reliability are well-established in statistical theory.
 * The thresholds are based on the Algorithm Selection Framework requirements where:
 * - N < 20: Skip processing (insufficient data)
 * - 20 ≤ N < 50: Use WCL (bootstrap phase)
 * - 50 ≤ N < 100: Use MLE (iterative refinement phase)
 * - N ≥ 100: Use MLE to establish prior for Bayesian inference
 * 
 * <p><strong>Sample Size Tier Definitions:</strong>
 * <ul>
 *   <li><strong>INSUFFICIENT (<20):</strong> Too few measurements for reliable positioning</li>
 *   <li><strong>BOOTSTRAP (20-49):</strong> WCL algorithm range - basic positioning capability</li>
 *   <li><strong>ADEQUATE (50-69):</strong> Beginning MLE range - statistical reliability</li>
 *   <li><strong>GOOD (70-84):</strong> Solid MLE range - strong statistical confidence</li>
 *   <li><strong>EXCELLENT (85-99):</strong> Near highly mature transition - optimal MLE</li>
 *   <li><strong>HIGHLY_MATURE (100+):</strong> Bayesian inference range - maximum reliability</li>
 * </ul>
 * 
 * <p><strong>Algorithm-Specific Scaling:</strong>
 * Different algorithms require different confidence scaling approaches:
 * <ul>
 *   <li><strong>MLE Confidence Factors:</strong> Higher sample size → higher confidence (0.75-0.95)</li>
 *   <li><strong>WCL Accuracy Factors:</strong> Higher sample size → lower error multiplier (1.2-0.8)</li>
 *   <li><strong>General Quality Scores:</strong> Direct quality mapping for combinations (0.3-0.95)</li>
 * </ul>
 * 
 * <p><strong>Research Citations:</strong>
 * - Statistical theory: Sample size effects on measurement reliability and confidence intervals
 * - Algorithm Selection Framework: wifi-ap-localization-requirements.md - sample size ranges
 * - Representative sample studies: Statistical theory on sample size requirements (Specht, M. 2020 supports principles)
 * - Confidence interval theory: Larger samples provide tighter confidence bounds
 * 
 * <p><strong>Benefits of Unification:</strong>
 * <ul>
 *   <li><strong>Consistency:</strong> All algorithms use identical sample size classification logic</li>
 *   <li><strong>Maintainability:</strong> Single source of truth for sample size thresholds and factors</li>
 *   <li><strong>Framework Alignment:</strong> Direct mapping to Algorithm Selection Framework requirements</li>
 *   <li><strong>Algorithm Flexibility:</strong> Each algorithm gets appropriate confidence scaling factors</li>
 * </ul>
 * 
 * @author WiFi Access Point Localization Team
 * @since 1.0
 */
public enum SampleSizeTier {
    
    /**
     * Insufficient sample size (<20 measurements).
     * 
     * <p><strong>Characteristics:</strong>
     * - Too few measurements for reliable positioning
     * - Algorithm Selection Framework skips processing
     * - Statistical uncertainty too high for meaningful results
     * - Not used in any localization algorithm calculations
     * 
     * <p><strong>Framework Context:</strong>
     * Algorithm Selection Framework specifies N < 20 should skip processing
     * due to insufficient statistical reliability.
     */
    INSUFFICIENT(20, "Too few measurements for reliable positioning", 
                null, 1.5, 0.3),  // No MLE support, 50% accuracy penalty, very poor quality
    
    /**
     * Bootstrap sample size (20-49 measurements).
     * 
     * <p><strong>Characteristics:</strong>
     * - WCL algorithm operational range
     * - Basic positioning capability with moderate confidence
     * - Statistical foundation for more sophisticated algorithms
     * - Bootstrap phase in iterative refinement framework
     * 
     * <p><strong>Framework Context:</strong>
     * Algorithm Selection Framework uses WCL for 20 ≤ N < 50 to establish
     * initial positioning estimates before transitioning to MLE.
     */
    BOOTSTRAP(50, "WCL algorithm range - basic positioning capability", 
              null, 1.2, 0.5),  // No MLE support, 20% accuracy penalty, moderate quality
    
    /**
     * Adequate sample size (50-69 measurements).
     * 
     * <p><strong>Characteristics:</strong>
     * - Beginning of MLE algorithm range
     * - Statistical reliability sufficient for MLE processing
     * - Foundation for iterative refinement phase
     * - Moderate confidence in positioning results
     * 
     * <p><strong>Framework Context:</strong>
     * Algorithm Selection Framework transitions to MLE at N ≥ 50, marking
     * the beginning of the iterative refinement phase.
     */
    ADEQUATE(70, "Beginning MLE range - statistical reliability", 
             0.75, 1.0, 0.7),  // Adequate MLE confidence, baseline accuracy, good quality
    
    /**
     * Good sample size (70-84 measurements).
     * 
     * <p><strong>Characteristics:</strong>
     * - Solid MLE algorithm range
     * - Strong statistical confidence in positioning
     * - Optimal balance of data quality and processing efficiency
     * - High reliability for most positioning applications
     * 
     * <p><strong>Framework Context:</strong>
     * Represents the middle range of MLE processing where statistical
     * confidence is strong but before approaching highly mature thresholds.
     */
    GOOD(85, "Solid MLE range - strong statistical confidence", 
         0.85, 0.9, 0.8),  // Good MLE confidence, 10% accuracy improvement, very good quality
    
    /**
     * Excellent sample size (85-99 measurements).
     * 
     * <p><strong>Characteristics:</strong>
     * - Near highly mature transition point
     * - Optimal MLE performance range
     * - Maximum confidence before Bayesian inference
     * - Exceptional statistical reliability
     * 
     * <p><strong>Framework Context:</strong>
     * Represents the upper range of MLE processing, approaching the
     * highly mature threshold where Bayesian inference becomes viable.
     */
    EXCELLENT(100, "Near highly mature transition - optimal MLE", 
              0.95, 0.85, 0.9),  // Excellent MLE confidence, 15% accuracy improvement, excellent quality
    
    /**
     * Highly mature sample size (100+ measurements).
     * 
     * <p><strong>Characteristics:</strong>
     * - Bayesian inference algorithm range
     * - Maximum statistical reliability and confidence
     * - Sufficient data for prior establishment
     * - Exceptional positioning accuracy potential
     * 
     * <p><strong>Framework Context:</strong>
     * Algorithm Selection Framework uses N ≥ 100 for MLE to establish
     * priors for Bayesian inference, representing maximum data maturity.
     */
    HIGHLY_MATURE(Integer.MAX_VALUE, "Bayesian inference range - maximum reliability", 
                  0.95, 0.8, 0.95);  // Excellent MLE confidence, 20% accuracy improvement, exceptional quality
    
    private static final Logger logger = LoggerFactory.getLogger(SampleSizeTier.class);
    
    private final int threshold;
    private final String description;
    private final Double mleConfidenceFactor;  // Nullable for tiers that don't support MLE
    private final double wclAccuracyFactor;
    private final double qualityScore;
    
    /**
     * Constructs a sample size tier with specified threshold, description, and algorithm-specific factors.
     * 
     * @param threshold Minimum sample size (inclusive) for this tier
     * @param desc Human-readable description of positioning capability at this sample size
     * @param mleConfidence MLE confidence factor (null for tiers that don't support MLE)
     * @param wclAccuracy WCL accuracy factor for accuracy scaling
     * @param quality General quality score for algorithm combinations
     */
    SampleSizeTier(int threshold, String desc, Double mleConfidence, double wclAccuracy, double quality) {
        this.threshold = threshold;
        this.description = desc;
        this.mleConfidenceFactor = mleConfidence;
        this.wclAccuracyFactor = wclAccuracy;
        this.qualityScore = quality;
    }
    
    /**
     * Classifies sample size into appropriate tier based on Algorithm Selection Framework.
     * 
     * <p>This method provides the unified sample size assessment logic used across all
     * localization algorithms, ensuring consistent classification while allowing
     * algorithm-specific confidence factor application.
     * 
     * @param sampleSize Number of measurements (must be >= 0)
     * @return Corresponding sample size tier based on Algorithm Selection Framework
     */
    public static SampleSizeTier fromSampleSize(int sampleSize) {
        if (sampleSize < 0) {
            logger.warn("Negative sample size {} provided, defaulting to INSUFFICIENT", sampleSize);
            return INSUFFICIENT;
        }
        
        for (SampleSizeTier tier : values()) {
            if (sampleSize < tier.threshold) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Sample size {} classified as {} ({})", 
                                sampleSize, tier.name(), tier.description);
                }
                return tier;
            }
        }
        
        // Should never reach here due to HIGHLY_MATURE having MAX_VALUE threshold
        logger.warn("Sample size {} exceeded all thresholds, defaulting to HIGHLY_MATURE", sampleSize);
        return HIGHLY_MATURE;
    }
    
    /**
     * Gets confidence factor for MLE algorithm confidence calculations.
     * 
     * <p>These factors are designed for confidence assessment where higher sample size
     * should result in higher confidence values. The scale (0.75-0.95) provides meaningful
     * gradation for MLE confidence calculations within the MLE operational range (50+).
     * 
     * <p><strong>Mathematical Context:</strong>
     * Used in: confidence = (gps_quality_factor + sample_size_factor) / 2
     * Scale chosen to work effectively with GPS quality factors while
     * maintaining appropriate confidence differentiation.
     * 
     * <p><strong>Research Basis:</strong>
     * Factors derived from statistical theory on sample size effects on confidence
     * intervals, optimized for MLE algorithm operational requirements.
     * 
     * @return Confidence factor between 0.75 (adequate) and 0.95 (excellent/highly mature)
     * @throws IllegalArgumentException if this tier doesn't support MLE confidence calculation
     */
    public double getMleConfidenceFactor() {
        if (mleConfidenceFactor == null) {
            throw new IllegalArgumentException(
                this.name() + " sample size not supported in MLE confidence calculation");
        }
        return mleConfidenceFactor;
    }
    
    /**
     * Gets accuracy factor for WCL algorithm accuracy calculations.
     * 
     * <p>These factors are designed for accuracy scaling where higher sample size
     * should result in lower error factors (better accuracy). The baseline is 1.0
     * for adequate sample size with improvements and penalties applied relative to this.
     * 
     * <p><strong>Mathematical Context:</strong>
     * Used in: accuracy = base_accuracy * sample_size_factor
     * Values <1.0 represent accuracy improvement, values >1.0 represent accuracy penalty.
     * 
     * <p><strong>Research Basis:</strong>
     * Factors derived from representative sample studies showing accuracy degradation
     * patterns with insufficient sample sizes in positioning applications.
     * 
     * @return Accuracy factor between 0.8 (highly mature improvement) and 1.5 (insufficient penalty)
     */
    public double getWclAccuracyFactor() {
        return wclAccuracyFactor;
    }
    
    /**
     * Gets quality score for general algorithm confidence combinations.
     * 
     * <p>These scores are designed for general quality assessment where sample size
     * is combined with other factors. The scale (0.3-0.95) provides appropriate
     * input for various mathematical combinations.
     * 
     * <p><strong>Mathematical Context:</strong>
     * Used in various confidence and quality calculations where sample size
     * needs to be combined with GPS quality, spatial consistency, etc.
     * 
     * <p><strong>Research Basis:</strong>
     * Score mapping derived from statistical theory on sample size effects
     * on measurement reliability and positioning quality.
     * 
     * @return Quality score between 0.3 (insufficient) and 0.95 (highly mature)
     */
    public double getQualityScore() {
        return qualityScore;
    }
    
    /**
     * Gets the sample size threshold (inclusive) that defines this tier.
     * 
     * @return Minimum sample size for this tier
     */
    public int getThreshold() {
        return threshold;
    }
    
    /**
     * Gets human-readable description of positioning capability at this sample size.
     * 
     * @return Description of positioning capability and algorithm context
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        if (threshold == Integer.MAX_VALUE) {
            return String.format("%s (≥100): %s", name(), description);
        } else {
            return String.format("%s (<%d): %s", name(), threshold, description);
        }
    }
}
