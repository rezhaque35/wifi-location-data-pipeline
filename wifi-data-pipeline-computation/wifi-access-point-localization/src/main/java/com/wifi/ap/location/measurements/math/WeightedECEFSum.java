package com.wifi.ap.location.measurements.math;

/**
 * Simple data record for weighted ECEF vector sum results.
 * 
 * <p>This immutable record holds the result of summing multiple weighted ECEF vectors
 * along with metadata about the computation process. It serves as a data transfer object
 * between calculation steps in the GeographicCentroidCalculator.
 * 
 * <p><strong>Mathematical Representation:</strong>
 * <pre>
 * For measurements M₁, M₂, ..., Mₙ with weights w₁, w₂, ..., wₙ:
 * 
 * ecefVector = Σ(wᵢ * ECEFVector(Mᵢ))
 * totalWeight = Σ(wᵢ)
 * measurementCount = n
 * </pre>
 * 
 * <p>All calculation logic is handled by GeographicCentroidCalculator.
 * This record only holds the mathematical results.
 * 
 * @param ecefVector The summed weighted ECEF vector
 * @param totalWeight The sum of all weights applied
 * @param measurementCount The number of measurements processed
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
public record WeightedECEFSum(ECEFVector ecefVector, double totalWeight, int measurementCount) {

    /**
     * Returns a string representation of this WeightedECEFSum.
     * 
     * @return String representation with ECEF vector, total weight, and count
     */
    @Override
    public String toString() {
        return String.format(
            "WeightedECEFSum[ecef=%s, totalWeight=%.3f, count=%d]",
            ecefVector.toString(), totalWeight, measurementCount
        );
    }
}
