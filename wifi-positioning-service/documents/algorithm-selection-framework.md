# WiFi Positioning Hybrid Algorithm Selection Framework

This document outlines the algorithm selection framework implemented in the WiFi Positioning Service to select the optimal positioning algorithms based on the scenario characteristics.

## Overview

The framework uses a three-phase process for optimal algorithm selection:

1. **Hard Constraints (Disqualification Phase)** - Eliminate algorithms that are mathematically or practically invalid
2. **Algorithm Weighting (Ranking Phase)** - Assign and adjust weights based on various factors
3. **Finalist Selection (Combination Phase)** - Select the final set of algorithms based on weights

## 1. Hard Constraints (Disqualification Phase)

First, we eliminate algorithms that are mathematically or practically invalid for the scenario:

| Constraint | Action |
|------------|--------|
| AP Count = 1 | Only include Proximity and Log Distance; remove all others |
| AP Count = 2 | Remove Trilateration and Maximum Likelihood (mathematically underdetermined) |
| AP Count ≥ 3 | All algorithms eligible (subject to other constraints) |
| Collinear APs detected | Remove Trilateration (mathematically invalid) |
| Extremely weak signals (all < -95 dBm) | Remove all except Proximity |

## 2. Algorithm Weighting (Ranking Phase)

For remaining eligible algorithms, we apply base weights according to AP count:

### Base Weights by AP Count

| AP Count | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|----------|-----------|------------|-------------------|---------------|-------------------|--------------|
| 1 | 1.0 | - | - | - | - | 0.4 |
| 2 | 0.4 | 1.0 | 0.8 | - | - | 0.5 |
| 3 | 0.3 | 0.7 | 0.8 | 1.0 | - | 0.5 |
| 4+ | 0.2 | 0.5 | 0.7 | 0.8 | 1.0 | 0.4 |

### Signal Quality Adjustments

| Signal Quality | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|----------------|-----------|------------|-------------------|---------------|-------------------|--------------|
| Strong (> -70 dBm) | ×0.9 | ×1.0 | ×1.0 | ×1.1 | ×1.2 | ×1.0 |
| Medium (-70 to -85 dBm) | ×0.7 | ×0.9 | ×1.0 | ×0.8 | ×0.9 | ×0.8 |
| Weak (< -85 dBm) | ×0.4 | ×0.6 | ×0.8 | ×0.3 | ×0.5 | ×0.6 |
| Very Weak (< -95 dBm) | ×0.5 | ×0.0 | ×0.0 | ×0.0 | ×0.0 | ×0.0 |

### Geometric Quality

| Quality Level | GDOP Range | Description | Impact |
|---------------|------------|-------------|---------|
| Excellent | < 2 | APs form a well-distributed pattern | Optimal positioning accuracy |
| Good | 2-4 | APs form a reasonable pattern | Good positioning accuracy |
| Fair | 4-6 | APs form a less optimal pattern | Reduced positioning accuracy |
| Poor | > 6 | APs form a poor pattern | Significantly reduced accuracy |
| Collinear | N/A | APs are aligned in a straight line | Severely reduced accuracy, especially for trilateration |

### Geometric Quality Impact on Algorithms

| Algorithm | Excellent | Good | Fair | Poor | Collinear |
|-----------|-----------|------|------|------|-----------|
| Proximity | ×1.0 | ×1.0 | ×1.0 | ×1.0 | ×1.0 |
| RSSI Ratio | ×1.0 | ×1.0 | ×0.9 | ×0.8 | ×0.7 |
| Weighted Centroid | ×1.0 | ×1.1 | ×1.2 | ×1.3 | ×1.4 |
| Trilateration | ×1.3 | ×0.9 | ×0.6 | ×0.3 | ×0.0 |
| Maximum Likelihood | ×1.2 | ×1.1 | ×0.9 | ×0.7 | ×0.5 |
| Log Distance | ×1.0 | ×1.0 | ×0.8 | ×0.7 | ×0.6 |

### Signal Distribution Adjustments

| Distribution Pattern | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|----------------------|-----------|------------|-------------------|---------------|-------------------|--------------|
| Uniform signal levels | ×1.0 | ×1.2 | ×1.0 | ×1.1 | ×0.9 | ×1.1 |
| Mixed signal levels | ×0.7 | ×0.9 | ×1.2 | ×0.8 | ×1.3 | ×0.8 |
| Signal outliers present | ×0.9 | ×0.7 | ×1.4 | ×0.5 | ×1.2 | ×0.8 |

## 3. Finalist Selection (Combination Phase)

After applying all weights:

1. **Threshold Filter**: Remove algorithms with final weight < 0.4
2. **Adaptive Selection**:
   - If highest-weighted algorithm has weight > 0.8: Use it alone or with one backup
   - Otherwise: Select top 3 algorithms

## Implementation

The framework is implemented in the `AlgorithmSelector` class which evaluates each scenario using the selection process described above.

### Example Scenarios

#### Example 1: Single AP with Medium Signal

- Hard Constraints: Only Proximity and Log Distance remain
- Base Weights: Proximity = 1.0, Log Distance = 0.4
- Adjustments:
  - Proximity: 1.0 × 0.7 = 0.7
  - Log Distance: 0.4 × 0.8 = 0.32
- Selection: Use Proximity only (Log Distance below threshold)

#### Example 2: Three Collinear APs with Strong Signals

- Hard Constraints: Remove Trilateration (collinear)
- Base Weights: RSSI Ratio = 0.7, Weighted Centroid = 0.8, Proximity = 0.3, Log Distance = 0.5
- Adjustments:
  - RSSI Ratio: 0.7 × 1.0 × 0.8 = 0.56
  - Weighted Centroid: 0.8 × 1.0 × 1.3 = 1.04
  - Proximity: 0.3 × 0.9 × 1.0 = 0.27
  - Log Distance: 0.5 × 1.0 × 0.7 = 0.35
- Selection: Weighted Centroid (1.04) and RSSI Ratio (0.56); others removed by threshold

#### Example 3: Four APs with Mixed Signal Quality

- Hard Constraints: All algorithms eligible
- Base Weights: ML = 1.0, Trilateration = 0.8, Weighted Centroid = 0.7, RSSI Ratio = 0.5, Proximity = 0.2, Log Distance = 0.4
- Adjustments for Mixed Signals:
  - Maximum Likelihood: 1.0 × 1.3 = 1.3
  - Trilateration: 0.8 × 0.8 = 0.64
  - Weighted Centroid: 0.7 × 1.2 = 0.84
  - RSSI Ratio: 0.5 × 0.9 = 0.45
  - Proximity: 0.2 × 0.7 = 0.14
  - Log Distance: 0.4 × 0.8 = 0.32
- Selection: Maximum Likelihood (1.3), Weighted Centroid (0.84), Trilateration (0.64)

## Benefits of the Framework

1. **Mathematical Validity**: Ensures algorithms are only used when mathematically valid for the given inputs
2. **Adaptive Selection**: Adjusts weights based on signal quality, geometric distribution, and signal patterns
3. **Confidence-based Selection**: Uses fewer algorithms with higher confidence when one algorithm is clearly superior
4. **Graceful Degradation**: Falls back to simpler methods when more complex ones are unsuitable
5. **Explainable Decisions**: Records reasons for algorithm selection and weight adjustments for transparency

## Conclusion

This hybrid algorithm selection framework enables the WiFi Positioning Service to adapt to a wide range of scenarios by dynamically selecting the most appropriate positioning algorithms based on the characteristics of the input data. 