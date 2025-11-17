# WiFi AP Localization Algorithm Selection Framework - Summary

## Overview

The algorithm selection follows a **two-phase hybrid approach**: initial build-up to establish high-quality prior estimates, followed by optimal iterative processing using Bayesian inference.

## Two-Phase Processing Strategy

### **Phase 1: Build-up Phase (Establishing Quality Prior)**

**Objective**: Accumulate sufficient measurements to create a robust, high-quality statistical foundation before switching to optimal Bayesian processing.

**Decision Logic:**
- **N < 20**: Wait for more data (insufficient for reliable localization)
- **20 ≤ N < 50**: Use **Weighted Centroid Localization (WCL)**
- **50 ≤ N < 100**: Use **Maximum Likelihood Estimation (MLE)**  
- **N ≥ 100**: Use **MLE** and mark AP as "Highly Mature"

**Processing Approach:**
- Process **cumulative measurements** (all historical + new measurements)
- Store all measurement data during this phase
- Each batch reprocesses the complete dataset with appropriate algorithm
- Continue until reaching N ≥ 100 total measurements
- **Switch trigger**: When AP reaches "Highly Mature" status (N ≥ 100)

**Example Processing Sequence:**
```
Batch 1: WCL(25 measurements) → Bootstrap State
Batch 2: WCL(45 total measurements) → Bootstrap State  
Batch 3: MLE(65 total measurements) → Mature State
Batch 4: MLE(85 total measurements) → Mature State
Batch 5: MLE(125 total measurements) → Highly Mature State → PHASE SWITCH
```

### **Phase 2: Iterative Refinement Phase**

**Objective**: Leverage the high-quality prior state from Phase 1 for optimal information fusion with new incoming measurements.

**Processing Approach:**
- Use **Bayesian Inference** exclusively for all subsequent processing
- **Prior**: High-quality MLE state established in Phase 1  
- **New Data**: Only fresh measurements (not historical data)
- **Data Management**: Delete historical measurement records, retain only state
- Process **incrementally** - each new batch updates the existing state
- Continue indefinitely with this iterative approach

**Example Processing Sequence:**
```
Batch 6: Bayesian(Phase 1 State + 20 new measurements) → Updated State
Batch 7: Bayesian(Previous State + 30 new measurements) → Updated State
Batch 8: Bayesian(Previous State + 15 new measurements) → Updated State
[Continue with Bayesian processing for all future batches...]
```

## Algorithm Characteristics and Selection Rationale

### **Weighted Centroid Localization (WCL)**
- **When**: First-time localization with 20-49 measurements
- **Purpose**: Quick bootstrap estimate with limited data
- **Characteristics**: Simple weighted average, susceptible to geometric bias
- **Quality Weighting**: CONNECTED measurements get 2.0x weight vs SCAN measurements

### **Maximum Likelihood Estimation (MLE)**  
- **When**: 50+ measurements during build-up phase
- **Purpose**: Physics-based localization using signal propagation models
- **Characteristics**: Uses log-distance path loss model, leverages rich metadata
- **Advantage**: Better accuracy than WCL, handles larger datasets effectively

### **Bayesian Inference**
- **When**: After Phase 1 completion, for all subsequent processing
- **Purpose**: Optimal fusion of prior knowledge with new measurements  
- **Characteristics**: Provides uncertainty quantification, enables iterative learning
- **Key Requirement**: Requires high-quality prior state from Phase 1

## Key Benefits of This Approach

### **Statistical Advantages**
- **Superior Prior Quality**: MLE(100+) provides much better prior than WCL(25) or smaller MLE
- **Optimal Information Fusion**: Bayesian inference optimally combines prior + new data
- **Better Convergence**: High-quality prior leads to faster, more stable convergence
- **Robust Foundation**: Large initial dataset better handles outliers and noise

### **Computational Advantages**  
- **Initial Investment**: Higher computational cost during build-up phase
- **Long-term Efficiency**: Highly efficient iterative processing after phase switch
- **Memory Management**: Historical data deleted after Phase 1, only state maintained
- **Scalable**: Constant processing time and memory usage in Phase 2

### **Practical Advantages**
- **Framework Alignment**: Respects algorithm maturity tiers from research framework  
- **Quality Assurance**: Ensures sufficient data before using most sophisticated algorithm
- **Flexibility**: Can handle varying batch sizes and irregular data arrival patterns
- **Monitoring**: Clear phase indicators for operational monitoring and debugging

## State Management Requirements

### **Build-up Phase State**
- Track processing phase (BUILD_UP vs ITERATIVE)
- Maintain historical measurement data
- Count total measurements processed
- Store current location estimate and uncertainty
- Monitor data quality metrics

### **Iterative Phase State**  
- Store only current location estimate and covariance matrix
- Track last processing timestamp
- Maintain algorithm performance metrics
- Historical measurement data no longer needed

## Transition Logic

### **Phase Switch Criteria**
- **Primary**: Total measurements ≥ 100
- **Secondary**: Successful MLE computation with reasonable uncertainty bounds
- **Action**: Delete historical measurement data, retain only final MLE state
- **Result**: Switch all subsequent processing to Bayesian inference

### **Post-Transition Processing**
- All future batches use Bayesian inference regardless of new measurement count
- Prior always comes from previous batch processing state
- New measurements provide fresh information for state updates
- State evolution continues indefinitely with optimal information fusion

This hybrid approach balances the need for high-quality initial estimates with long-term computational efficiency and optimal statistical performance.