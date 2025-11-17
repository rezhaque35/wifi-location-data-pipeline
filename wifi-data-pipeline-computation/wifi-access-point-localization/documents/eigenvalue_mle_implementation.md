# Eigenvalue-Based MLE Implementation Guide for AP Localization

## Overview

This document provides a complete implementation guide for adapting the eigenvalue-based MLE approach from the PMC paper "Efficient Localization Method Based on RSSI for AP Clusters" to your Java AP localization framework.

## Key Algorithmic Adaptation

### Problem Transformation
- **PMC Paper**: Target localization (unknown device position, known AP positions)
- **Your Framework**: AP localization (unknown AP position, known device positions)
- **Solution**: Mathematically, the problems are equivalent - just swap the roles of target and AP in the equations.

## Algorithm Components

### 1. RDW Algorithm Adaptation (RSSI Optimization)

The RDW algorithm estimates optimal RSSI values from measurement clusters. For AP localization:

```java
/**
 * Relative Distance Weighting (RDW) Algorithm for AP Localization
 * Adapts the PMC paper's RDW algorithm for estimating optimal RSSI values
 */
public class RdwRssiOptimizer {
    
    /**
     * Estimates optimal RSSI for each measurement location using distance-based weighting
     * 
     * @param measurements WiFi measurements for a single AP
     * @param referencePower Reference power (P0) at 1 meter
     * @param pathLossExponent Path loss exponent (eta/gamma)
     * @return Map of measurement locations to optimized RSSI values
     */
    public Map<Location, Double> estimateOptimalRssi(
            List<WifiMeasurement> measurements, 
            double referencePower, 
            double pathLossExponent) {
        
        Map<Location, Double> result = new HashMap<>();
        
        for (WifiMeasurement measurement : measurements) {
            Location measurementLocation = Location.fromMeasurement(measurement);
            
            // Calculate sample mean RSSI for this location (if multiple samples)
            double meanRssi = getMeanRssiAtLocation(measurements, measurementLocation);
            
            // Calculate distance estimate from mean RSSI
            double meanDistance = calculateDistanceFromRssi(meanRssi, referencePower, pathLossExponent);
            
            // Calculate weights based on distance differences
            double totalWeight = 0.0;
            double weightedRssiSum = 0.0;
            
            for (WifiMeasurement sample : getMeasurementsAtLocation(measurements, measurementLocation)) {
                double sampleDistance = calculateDistanceFromRssi(sample.rssi(), referencePower, pathLossExponent);
                
                // Weight calculation: inverse of distance difference
                double weight = 1.0 / Math.max(0.1, Math.abs(sampleDistance - meanDistance));
                
                totalWeight += weight;
                weightedRssiSum += weight * sample.rssi();
            }
            
            // Normalized weighted RSSI
            double optimizedRssi = totalWeight > 0 ? weightedRssiSum / totalWeight : meanRssi;
            result.put(measurementLocation, optimizedRssi);
        }
        
        return result;
    }
    
    private double calculateDistanceFromRssi(double rssi, double referencePower, double pathLossExponent) {
        // log-distance path loss model: RSSI = P0 - 10*n*log10(d)
        // Solve for d: d = 10^((P0 - RSSI) / (10*n))
        return Math.pow(10, (referencePower - rssi) / (10.0 * pathLossExponent));
    }
    
    // Helper methods for grouping measurements by location...
}
```

### 2. Eigenvalue-Based Target Localization (ETL) Algorithm

The core eigenvalue-based optimization adapted for AP localization:

```java
/**
 * Eigenvalue-Based Target Localization (ETL) Algorithm for AP Localization
 * Adapts the PMC paper's ETL algorithm for finding AP location
 */
public class EigenvalueMleLocalizer {
    
    /**
     * Estimates AP location using eigenvalue-based MLE approach
     * 
     * @param optimizedRssiMap Map of measurement locations to optimized RSSI values
     * @param referencePower Reference power (P0) at 1 meter  
     * @param pathLossExponent Path loss exponent
     * @return Estimated AP location
     */
    public APLocation estimateApLocation(
            Map<Location, Double> optimizedRssiMap,
            double referencePower,
            double pathLossExponent) {
        
        List<Location> measurementLocations = new ArrayList<>(optimizedRssiMap.keySet());
        int N = measurementLocations.size();
        
        if (N < 3) {
            throw new IllegalArgumentException("Need at least 3 measurement locations for eigenvalue MLE");
        }
        
        // Step 1: Calculate distance estimates from optimized RSSI values
        Map<Location, Double> distanceEstimates = calculateDistanceEstimates(
            optimizedRssiMap, referencePower, pathLossExponent);
        
        // Step 2: Calculate normalized weights based on distance estimates  
        double[] weights = calculateNormalizedWeights(distanceEstimates);
        
        // Step 3: Calculate weighted centroid of measurement locations
        Location weightedCentroid = calculateWeightedCentroid(measurementLocations, weights);
        
        // Step 4: Transform coordinate system (translate to centroid as origin)
        List<Location> transformedLocations = transformCoordinates(measurementLocations, weightedCentroid);
        
        // Step 5: Build matrices for eigenvalue problem
        EigenvalueProblem problem = buildEigenvalueProblem(
            transformedLocations, distanceEstimates, weights);
        
        // Step 6: Solve eigenvalue problem for candidate AP locations
        List<Location> candidates = solveEigenvalueProblem(problem, weightedCentroid);
        
        // Step 7: Select best candidate based on cost function
        Location bestApLocation = selectBestCandidate(candidates, optimizedRssiMap, 
            referencePower, pathLossExponent);
        
        // Step 8: Calculate confidence and accuracy
        double confidence = calculateConfidence(optimizedRssiMap, bestApLocation, 
            referencePower, pathLossExponent);
        double horizontalAccuracy = calculateHorizontalAccuracy(confidence);
        
        return APLocation.builder()
            .latitude(bestApLocation.latitude())
            .longitude(bestApLocation.longitude())
            .confidence(confidence)
            .horizontalAccuracy(horizontalAccuracy)
            .build();
    }
    
    /**
     * Builds the eigenvalue problem matrices from transformed coordinates and distances
     */
    private EigenvalueProblem buildEigenvalueProblem(
            List<Location> transformedLocations,
            Map<Location, Double> distanceEstimates, 
            double[] weights) {
        
        int N = transformedLocations.size();
        
        // Build A matrix (coordinate differences matrix)
        double[][] A = new double[N-1][2];
        for (int i = 0; i < N-1; i++) {
            Location loc1 = transformedLocations.get(0);
            Location loc_i = transformedLocations.get(i+1);
            
            A[i][0] = loc_i.latitude() - loc1.latitude();
            A[i][1] = loc_i.longitude() - loc1.longitude();
        }
        
        // Build b vector (distance differences)
        double[] b = new double[N-1];
        Location firstLoc = transformedLocations.get(0);
        double firstDistance = distanceEstimates.get(firstLoc);
        
        for (int i = 0; i < N-1; i++) {
            Location loc_i = transformedLocations.get(i+1);
            double distance_i = distanceEstimates.get(loc_i);
            
            b[i] = (firstDistance * firstDistance - distance_i * distance_i) / 2.0;
        }
        
        return new EigenvalueProblem(A, b, weights);
    }
    
    /**
     * Solves the eigenvalue problem to find candidate AP locations
     */
    private List<Location> solveEigenvalueProblem(EigenvalueProblem problem, Location centroid) {
        // This is the core eigenvalue computation from the PMC paper
        
        // Step 1: Compute M matrix as shown in the paper
        double[][] M = computeDataMatrix(problem);
        
        // Step 2: Eigenvalue decomposition of M
        EigenDecomposition eigen = new EigenDecomposition(new Array2DRowRealMatrix(M));
        double[] eigenvalues = eigen.getRealEigenvalues();
        double[][] eigenvectors = eigen.getV().getData();
        
        // Step 3: Extract candidate solutions from eigenvectors
        List<Location> candidates = new ArrayList<>();
        
        for (int i = 0; i < eigenvalues.length; i++) {
            // Extract x, y coordinates from eigenvector (elements 2 and 3 of normalized eigenvector)
            double[] eigenvector = getColumn(eigenvectors, i);
            
            // Normalize eigenvector so first two elements sum to 1
            double normalizationFactor = eigenvector[0] + eigenvector[1];
            if (Math.abs(normalizationFactor) > 1e-10) {
                double x = eigenvector[2] / normalizationFactor;
                double y = eigenvector[3] / normalizationFactor;
                
                // Transform back to original coordinate system
                Location candidate = Location.of(
                    centroid.latitude() + x,
                    centroid.longitude() + y
                );
                candidates.add(candidate);
            }
        }
        
        return candidates;
    }
    
    /**
     * Selects the best candidate location based on cost function minimization
     */
    private Location selectBestCandidate(
            List<Location> candidates,
            Map<Location, Double> optimizedRssiMap,
            double referencePower,
            double pathLossExponent) {
        
        Location bestCandidate = null;
        double minCost = Double.MAX_VALUE;
        
        for (Location candidate : candidates) {
            double cost = calculateCostFunction(candidate, optimizedRssiMap, 
                referencePower, pathLossExponent);
            
            if (cost < minCost) {
                minCost = cost;
                bestCandidate = candidate;
            }
        }
        
        return bestCandidate != null ? bestCandidate : candidates.get(0);
    }
    
    /**
     * Calculates the cost function for a candidate AP location
     */
    private double calculateCostFunction(
            Location candidateAp,
            Map<Location, Double> optimizedRssiMap,
            double referencePower,
            double pathLossExponent) {
        
        double totalCost = 0.0;
        
        for (Map.Entry<Location, Double> entry : optimizedRssiMap.entrySet()) {
            Location measurementLoc = entry.getKey();
            double observedRssi = entry.getValue();
            
            // Calculate expected RSSI at this location
            double distance = candidateAp.distanceTo(measurementLoc);
            double expectedRssi = referencePower - 10.0 * pathLossExponent * Math.log10(distance);
            
            // Add squared residual to cost
            double residual = observedRssi - expectedRssi;
            totalCost += residual * residual;
        }
        
        return totalCost;
    }
    
    // Additional helper methods for matrix operations, coordinate transformations, etc.
}
```

## Integration with Your Existing Framework

### 3. Enhanced MLE Algorithm Class

Update your `MaximumLikelihoodEstimation` class to include the eigenvalue approach:

```java
/**
 * Add to your MaximumLikelihoodEstimation class
 */
public class MaximumLikelihoodEstimation implements LocalizationAlgorithm {
    
    private final RdwRssiOptimizer rssiOptimizer;
    private final EigenvalueMleLocalizer eigenvalueLocalizer;
    private boolean useEigenvalueApproach = true; // Configuration flag
    
    @Override
    public APLocation estimateLocation(WifiMeasurements measurements) {
        if (measurements == null || measurements.isEmpty()) {
            throw new IllegalArgumentException("Measurements cannot be null or empty for MLE");
        }
        
        logger.info("Starting MLE localization with {} measurements", measurements.size());
        
        try {
            if (useEigenvalueApproach && measurements.size() >= 20) {
                return estimateLocationUsingEigenvalueMethod(measurements);
            } else {
                return estimateLocationUsingOptimizationMethod(measurements);
            }
        } catch (Exception e) {
            logger.error("MLE estimation failed: {}", e.getMessage(), e);
            return fallbackToWeightedCentroid(measurements);
        }
    }
    
    /**
     * Eigenvalue-based MLE approach adapted from PMC paper
     */
    private APLocation estimateLocationUsingEigenvalueMethod(WifiMeasurements measurements) {
        // Step 1: Get path loss parameters
        WiFiFrequencyBand band = determineFrequencyBand(measurements);
        double referencePower = band.getReferencePowerDbm();
        double pathLossExponent = band.getPathLossExponent();
        
        // Step 2: Apply RDW algorithm to optimize RSSI values
        Map<Location, Double> optimizedRssi = rssiOptimizer.estimateOptimalRssi(
            measurements.measurements(), referencePower, pathLossExponent);
        
        // Step 3: Use ETL algorithm for eigenvalue-based localization
        APLocation eigenvalueResult = eigenvalueLocalizer.estimateApLocation(
            optimizedRssi, referencePower, pathLossExponent);
        
        logger.info("Eigenvalue MLE completed - Location: ({}, {}), Confidence: {}", 
                   eigenvalueResult.latitude(), eigenvalueResult.longitude(), 
                   eigenvalueResult.confidence());
        
        return eigenvalueResult;
    }
    
    /**
     * Your existing optimization-based MLE approach (as fallback)
     */
    private APLocation estimateLocationUsingOptimizationMethod(WifiMeasurements measurements) {
        // Your existing implementation using Nelder-Mead optimization
        // Keep this as a fallback for small datasets or when eigenvalue method fails
        return estimateLocationUsingCurrentApproach(measurements);
    }
}
```

## Key Implementation Considerations

### 1. Matrix Operations
You'll need to add Apache Commons Math dependency for eigenvalue decomposition:

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-math3</artifactId>
    <version>3.6.1</version>
</dependency>
```

### 2. Coordinate System Handling
- Transform geographic coordinates (lat/lon) to local Cartesian system for matrix operations
- Transform results back to geographic coordinates
- Handle the coordinate system translation to centroid as origin

### 3. Algorithm Selection Strategy
```java
/**
 * Algorithm selection based on data characteristics
 */
private boolean shouldUseEigenvalueApproach(WifiMeasurements measurements) {
    return measurements.size() >= 20 &&  // Sufficient sample size
           hasSufficientSpatialDistribution(measurements) && // Good geometric distribution
           measurements.getConnectedPercentage() >= 0.3; // Sufficient connected measurements
}
```

### 4. Error Handling and Fallbacks
- If eigenvalue decomposition fails → fallback to your existing Nelder-Mead approach
- If insufficient spatial distribution → use optimization approach
- If matrix operations encounter numerical instability → apply regularization or fallback

## Performance Comparison

The eigenvalue approach offers several advantages over iterative optimization:

1. **Computational Efficiency**: Direct solution vs. iterative search
2. **Global Optimum**: Avoids local minima issues of iterative methods  
3. **Deterministic Results**: Same input always produces same output
4. **Better Convergence**: No convergence threshold tuning required

## Testing Strategy

1. **Unit Tests**: Test RDW and ETL algorithms separately
2. **Integration Tests**: Compare eigenvalue vs. optimization results
3. **Performance Tests**: Measure execution time improvements
4. **Accuracy Tests**: Validate against known AP locations

## Configuration Options

Add configuration properties to control the algorithm behavior:

```yaml
mle:
  eigenvalue:
    enabled: true
    min-sample-size: 20
    regularization-factor: 1e-6
    max-condition-number: 1e12
  fallback:
    use-optimization: true
    use-weighted-centroid: true
```

This implementation provides a complete framework for integrating the eigenvalue-based MLE approach while maintaining compatibility with your existing architecture and providing robust fallback mechanisms.
