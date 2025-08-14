package com.wifi.positioning.algorithm.selection;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.wifi.positioning.algorithm.PositioningAlgorithm;
import com.wifi.positioning.algorithm.PositioningAlgorithmType;
import com.wifi.positioning.algorithm.selection.factor.APCountFactor;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.selection.factor.SignalQualityFactor;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;

/**
 * Implements the WiFi Positioning Hybrid Algorithm Selection Framework. This framework uses a
 * three-phase process for optimal algorithm selection: 1. Hard Constraints (Disqualification Phase)
 * - Eliminate algorithms that are mathematically or practically invalid 2. Algorithm Weighting
 * (Ranking Phase) - Assign and adjust weights based on various factors 3. Finalist Selection
 * (Combination Phase) - Select the final set of algorithms based on weights
 */
@Component
public class AlgorithmSelector {

  private static final Logger logger = LoggerFactory.getLogger(AlgorithmSelector.class);

  // Weight threshold for finalist selection
  private static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;

 
  // Predefined algorithm sets for constraints
  private static final String DISQUALIFIED_INSUFFICIENT_APS = "DISQUALIFIED (insufficient APs)";
  private static final String DISQUALIFIED_COLLINEAR = "DISQUALIFIED (collinear APs)";
  private static final String DISQUALIFIED_POOR_GEOMETRY = "DISQUALIFIED (poor geometry)";
  private static final String DISQUALIFIED_SIGNAL_TOO_WEAK = "DISQUALIFIED (signal too weak)";
  private static final String VALID_FOR_SINGLE_AP = "Valid for single AP";
  private static final String VALID_FOR_SINGLE_AP_WITH_MODEL =
      "Valid for single AP with path loss model";
  private static final String VALID_FOR_TWO_APS = "Valid for two APs";
  private static final String VALID_FOR_THREE_APS = "Valid for three APs";
  private static final String VALID_FOR_FOUR_PLUS_APS = "Valid for 4+ APs";
  private static final String TRILAT_REQUIRES_3_APS = "DISQUALIFIED (requires at least 3 APs)";
  private static final String ML_REQUIRES_4_APS = "DISQUALIFIED (requires at least 4 APS)";
  private static final String ONLY_VIABLE_FOR_WEAK =
      "Only viable algorithm for extremely weak signals";

  // Pre-computed selected algorithms for common scenarios
  private static final SelectedAlgorithms VERY_WEAK_SIGNAL_ALGORITHMS;
  private static final SelectedAlgorithms SINGLE_AP_ALGORITHMS;
  private static final SelectedAlgorithms TWO_APS_ALGORITHMS;
  private static final SelectedAlgorithms THREE_APS_ALGORITHMS;
  private static final SelectedAlgorithms FOUR_PLUS_APS_ALGORITHMS;

  // Static initializer for pre-computed selected algorithms
  static {
    VERY_WEAK_SIGNAL_ALGORITHMS = initializeVeryWeakSignalAlgorithms();
    SINGLE_AP_ALGORITHMS = initializeSingleApAlgorithms();
    TWO_APS_ALGORITHMS = initializeTwoApsAlgorithms();
    THREE_APS_ALGORITHMS = initializeThreeApsAlgorithms();
    FOUR_PLUS_APS_ALGORITHMS = initializeFourPlusApsAlgorithms();
  }

  /** Initialize algorithms for very weak signal scenario (only proximity) */
  private static SelectedAlgorithms initializeVeryWeakSignalAlgorithms() {
    Set<PositioningAlgorithmType> eligibleTypes = Set.of(PositioningAlgorithmType.PROXIMITY);
    Map<PositioningAlgorithmType, SelectionReasoning> reasons = createReasonMap(eligibleTypes);

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      SelectionReasoning sr = reasons.get(type);
      if (type == PositioningAlgorithmType.PROXIMITY) {
        sr.reasonings().add(ONLY_VIABLE_FOR_WEAK);
      } else {
        sr.reasonings().add(DISQUALIFIED_SIGNAL_TOO_WEAK);
      }
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /** Initialize algorithms for single AP scenario (proximity and log distance) */
  private static SelectedAlgorithms initializeSingleApAlgorithms() {
    Set<PositioningAlgorithmType> eligibleTypes =
        Set.of(PositioningAlgorithmType.PROXIMITY, PositioningAlgorithmType.LOG_DISTANCE);
    Map<PositioningAlgorithmType, SelectionReasoning> reasons = createReasonMap(eligibleTypes);

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      SelectionReasoning sr = reasons.get(type);
      if (type == PositioningAlgorithmType.PROXIMITY) {
        sr.reasonings().add(VALID_FOR_SINGLE_AP);
      } else if (type == PositioningAlgorithmType.LOG_DISTANCE) {
        sr.reasonings().add(VALID_FOR_SINGLE_AP_WITH_MODEL);
      } else {
        sr.reasonings().add(DISQUALIFIED_INSUFFICIENT_APS);
      }
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /**
   * Initialize algorithms for two APs scenario (proximity, RSSI ratio, weighted centroid, log
   * distance)
   */
  private static SelectedAlgorithms initializeTwoApsAlgorithms() {
    Set<PositioningAlgorithmType> eligibleTypes =
        Set.of(
            PositioningAlgorithmType.PROXIMITY,
            PositioningAlgorithmType.RSSI_RATIO,
            PositioningAlgorithmType.WEIGHTED_CENTROID,
            PositioningAlgorithmType.LOG_DISTANCE);
    Map<PositioningAlgorithmType, SelectionReasoning> reasons = createReasonMap(eligibleTypes);

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      SelectionReasoning sr = reasons.get(type);
      if (eligibleTypes.contains(type)) {
        sr.reasonings().add(VALID_FOR_TWO_APS);
      } else if (type == PositioningAlgorithmType.TRILATERATION) {
        sr.reasonings().add(TRILAT_REQUIRES_3_APS);
      } else if (type == PositioningAlgorithmType.MAXIMUM_LIKELIHOOD) {
        sr.reasonings().add(ML_REQUIRES_4_APS);
      }
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /** Initialize algorithms for three APs scenario (all except maximum likelihood) */
  private static SelectedAlgorithms initializeThreeApsAlgorithms() {
    Set<PositioningAlgorithmType> eligibleTypes =
        new HashSet<>(Arrays.asList(PositioningAlgorithmType.values()));
    eligibleTypes.remove(PositioningAlgorithmType.MAXIMUM_LIKELIHOOD);

    Map<PositioningAlgorithmType, SelectionReasoning> reasons = createReasonMap(eligibleTypes);

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      SelectionReasoning sr = reasons.get(type);
      if (eligibleTypes.contains(type)) {
        sr.reasonings().add(VALID_FOR_THREE_APS);
      } else {
        sr.reasonings().add(ML_REQUIRES_4_APS);
      }
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /** Initialize algorithms for four+ APs scenario (all algorithms eligible) */
  private static SelectedAlgorithms initializeFourPlusApsAlgorithms() {
    Set<PositioningAlgorithmType> eligibleTypes =
        new HashSet<>(Arrays.asList(PositioningAlgorithmType.values()));
    Map<PositioningAlgorithmType, SelectionReasoning> reasons = createReasonMap(eligibleTypes);

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      reasons.get(type).reasonings().add(VALID_FOR_FOUR_PLUS_APS);
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /** Helper to create structured reason map initialized with selected flag by eligibility */
  private static Map<PositioningAlgorithmType, SelectionReasoning> createReasonMap(
      Set<PositioningAlgorithmType> eligibleTypes) {
    Map<PositioningAlgorithmType, SelectionReasoning> reasons = new HashMap<>();
    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      boolean selected = eligibleTypes != null && eligibleTypes.contains(type);
      reasons.put(type, new SelectionReasoning(type, selected, new ArrayList<>()));
    }
    return reasons;
  }

  // Algorithms
  private final PositioningAlgorithm proximityAlgorithm;
  private final PositioningAlgorithm rssiRatioAlgorithm;
  private final PositioningAlgorithm weightedCentroidAlgorithm;
  private final PositioningAlgorithm trilaterationAlgorithm;
  private final PositioningAlgorithm maximumLikelihoodAlgorithm;
  private final PositioningAlgorithm logDistanceAlgorithm;

  /** Record that holds algorithm selection information including weights and reasoning */
  public record AlgorithmSelectionInfo(
      Map<PositioningAlgorithm, Double> algorithmWeights,
      Map<PositioningAlgorithm, List<String>> selectionReasons) {}

  /**
   * Record to hold the result of algorithm selection after applying hard constraints Contains both
   * eligible algorithm types and the reasons for inclusion/exclusion
   */
  public record SelectionReasoning(
      PositioningAlgorithmType type, boolean selected, List<String> reasonings) {}

  /**
   * Record to hold the result of algorithm selection including both selected algorithms 
   * and updated selection reasons.
   */
  private record AlgorithmSelection(
      Set<WeightedAlgorithm> selectedAlgorithms,
      Map<PositioningAlgorithmType, SelectionReasoning> updatedReasons) {}

  record SelectedAlgorithms(
      Set<PositioningAlgorithmType> eligibleAlgorithmTypes,
      Map<PositioningAlgorithmType, SelectionReasoning> reasons) {
    /**
     * Creates a copy of this SelectedAlgorithms but applies a filter to the eligible algorithms.
     * Algorithms that don't pass the filter will be removed and given the specified reason.
     *
     * @param filter The predicate to filter algorithms
     * @param reasonForExclusion The reason to add for excluded algorithms
     * @return A new SelectedAlgorithms instance with filtered algorithms
     */
    public SelectedAlgorithms copyWithFilter(
        java.util.function.Predicate<PositioningAlgorithmType> filter, String reasonForExclusion) {

      // Create a copy of the eligible algorithm types that pass the filter
      Set<PositioningAlgorithmType> filteredTypes =
          eligibleAlgorithmTypes.stream().filter(filter).collect(Collectors.toSet());

      // Create a deep copy of reasons and update selection flags
      Map<PositioningAlgorithmType, SelectionReasoning> newReasons = new HashMap<>();
      for (Map.Entry<PositioningAlgorithmType, SelectionReasoning> entry : reasons.entrySet()) {
        PositioningAlgorithmType type = entry.getKey();
        SelectionReasoning existing = entry.getValue();
        List<String> updated = new ArrayList<>(existing.reasonings());
        boolean stillSelected = filteredTypes.contains(type);
        if (!stillSelected) {
          updated.add(reasonForExclusion);
        }
        newReasons.put(type, new SelectionReasoning(type, stillSelected, updated));
      }

      return new SelectedAlgorithms(filteredTypes, newReasons);
    }
  }

  /** Default constructor - uses algorithms from PositioningAlgorithmType enum */
  public AlgorithmSelector() {
    this.proximityAlgorithm = PositioningAlgorithmType.PROXIMITY.getImplementation();
    this.rssiRatioAlgorithm = PositioningAlgorithmType.RSSI_RATIO.getImplementation();
    this.weightedCentroidAlgorithm = PositioningAlgorithmType.WEIGHTED_CENTROID.getImplementation();
    this.trilaterationAlgorithm = PositioningAlgorithmType.TRILATERATION.getImplementation();
    this.maximumLikelihoodAlgorithm =
        PositioningAlgorithmType.MAXIMUM_LIKELIHOOD.getImplementation();
    this.logDistanceAlgorithm = PositioningAlgorithmType.LOG_DISTANCE.getImplementation();
  }

  /**
   * Record to hold the result of algorithm weight calculation including the algorithm, its
   * calculated weight and the reason formula
   */
  private record WeightedAlgorithm(PositioningAlgorithm algorithm, double weight, String weightCalculation) {}

  /**
   * Apply algorithm weighting based on various factors Returns a list of weight results using a
   * functional approach
   *
   * @param eligibleAlgorithms Set of algorithms that passed the hard constraints
   * @param apCountFactor Factor representing the number of access points
   * @param context Context information about the environment
   * @return List of weight results for each algorithm
   */
  private Set<WeightedAlgorithm> applyAlgorithmWeighting(
      SelectedAlgorithms hardConstraintsResult, SelectionContext context) {

    return hardConstraintsResult.eligibleAlgorithmTypes.stream()
        .map(PositioningAlgorithmType::getImplementation)
        .map(algorithm -> calculateWeight(algorithm, context))
        .collect(Collectors.toSet());
  }

  /** Calculate weight result for a single algorithm */
  private WeightedAlgorithm calculateWeight(
      PositioningAlgorithm algorithm, SelectionContext context) {

    // Calculate all multipliers
    double baseWeight = algorithm.getBaseWeight(context.getApCountFactor());
    double signalMultiplier = algorithm.getSignalQualityMultiplier(context.getSignalQuality());
    double geometricMultiplier =
        algorithm.getGeometricQualityMultiplier(context.getGeometricQuality());
    double distributionMultiplier =
        algorithm.getSignalDistributionMultiplier(context.getSignalDistribution());

    // Calculate final weight
    double weight = baseWeight * signalMultiplier * geometricMultiplier * distributionMultiplier;

    // Create reason with the complete formula
    String weightCalculation =
        String.format(
            "Weight=%1$.2f: base(%2$.2f) × signal(%3$.2f) × geometric(%4$.2f) × distribution(%5$.2f)",
            weight, baseWeight, signalMultiplier, geometricMultiplier, distributionMultiplier);

    return new WeightedAlgorithm(algorithm, weight, weightCalculation);
  }

  /**
   * Apply the algorithm selection framework to select and weight appropriate algorithms. This
   * method implements the three-phase selection process: 1. Hard Constraints - Eliminate algorithms
   * that are mathematically or practically invalid 2. Algorithm Weighting - Assign and adjust
   * weights based on various factors 3. Finalist Selection - Select the final set of algorithms
   * based on weights
   *
   * @param validScans The valid WiFi scan results
   * @param apMap Map of known access points by MAC address
   * @param context Additional context information about the scenario
   * @return AlgorithmSelectionInfo containing weights and detailed selection reasons
   */
  public AlgorithmSelectionInfo selectAlgorithmsWithReasons(
      List<WifiScanResult> validScans,
      Map<String, WifiAccessPoint> apMap,
      SelectionContext context) {

    logger.debug("Starting algorithm selection process with context: {}", context);

    // -------------- PHASE ONE: HARD CONSTRAINTS ----------------
    SelectedAlgorithms eligibleAlgorithms = selectAlgorithmsBasedOnHardConstraints(context);


    // -------------- PHASE TWO: ALGORITHM WEIGHTING ----------------
    Set<WeightedAlgorithm> weightedAlgorithms =
        applyAlgorithmWeighting(eligibleAlgorithms, context);

    // -------------- PHASE THREE: FINALIST SELECTION ----------------
    AlgorithmSelection finalSelectedAlgorithms =
        applyFinalistSelection(weightedAlgorithms, eligibleAlgorithms.reasons);

 
    return createAlgorithmSelectionInfo(
        finalSelectedAlgorithms);
  }

  /**
   * Applies the finalist selection phase of the algorithm selection framework. This phase filters
   * the algorithms based on signal quality and confidence thresholds. According to the framework:
   * 1. Threshold Filter: Remove algorithms with final weight < 0.4 2. Adaptive Selection: - If
   * highest-weighted algorithm has weight > 0.8: Use it alone or with one backup - Otherwise:
   * Select top 3 algorithms
   *
   * @param weightedAlgorithms The set of weighted algorithms after phase two
   * @param selectionReasons Map of selection reasons to update with discard info
   * @return A filtered set of finalist algorithms
   */
  private AlgorithmSelection applyFinalistSelection(
      Set<WeightedAlgorithm> weightedAlgorithms, Map<PositioningAlgorithmType, SelectionReasoning> selectionReasons) {

    logger.debug("Applying finalist selection phase");

    AlgorithmSelection algorithmSelection = applyThresholdFilter(weightedAlgorithms, selectionReasons);
    
 
    
    return applyAdaptiveSelection(algorithmSelection);
  }

  /**
   * Applies threshold filtering to remove algorithms with weights below the minimum threshold.
   * 
   * @param weightedAlgorithms The algorithms to filter
   * @param selectionReasons Map to record discard reasons
   * @return AlgorithmSelection containing both filtered algorithms and updated reasons
   */
  private AlgorithmSelection applyThresholdFilter(
      Set<WeightedAlgorithm> weightedAlgorithms, Map<PositioningAlgorithmType, SelectionReasoning> selectionReasons) {
    
    double minimumWeightThreshold = calculateMinimumWeightThreshold(weightedAlgorithms);
    
    Set<WeightedAlgorithm> belowThreshold = findAlgorithmsBelowThreshold(weightedAlgorithms, minimumWeightThreshold);
    Map<PositioningAlgorithmType, SelectionReasoning> updatedReasons = 
        recordThresholdDiscards(belowThreshold, minimumWeightThreshold, selectionReasons);
    
    Set<WeightedAlgorithm> aboveThreshold = filterAlgorithmsAboveThreshold(weightedAlgorithms, minimumWeightThreshold);
    
    logger.debug("After threshold filter (min weight {}): {} algorithms remain", 
                 minimumWeightThreshold, aboveThreshold.size());
    
    return new AlgorithmSelection(aboveThreshold, updatedReasons);
  }

  /**
   * Applies adaptive selection strategy based on the highest algorithm weight.
   * 
   * @param weightedAlgorithms Algorithms that passed threshold filtering
   * @param selectionReasons Map to record discard reasons
   * @return Final selected algorithms
   */
  private AlgorithmSelection applyAdaptiveSelection( AlgorithmSelection algorithmSelection) {
    
    Optional<WeightedAlgorithm> highestWeighted = findHighestWeightedAlgorithm(algorithmSelection.selectedAlgorithms());
    
    if (isHighConfidenceScenario(highestWeighted)) {
      return applyHighConfidenceSelection(algorithmSelection);
    } else if (algorithmSelection.selectedAlgorithms().size() > 3) {
      return applyNormalConfidenceSelection(algorithmSelection);
    } else {
      return algorithmSelection; // Use all algorithms if 3 or fewer
    }
  }

  /**
   * Selects top algorithms for high confidence scenarios (top 2 algorithms).
   */
  private AlgorithmSelection applyHighConfidenceSelection( AlgorithmSelection algorithmSelection){
    
    logger.debug("High confidence scenario: selecting top 2 algorithms");
    return selectTopAlgorithms(algorithmSelection, 2, "DISQUALIFIED (not in top 2 High Confidence)");
  }

  /**
   * Selects top algorithms for normal confidence scenarios (top 3 algorithms).
   */
  private AlgorithmSelection applyNormalConfidenceSelection(
      AlgorithmSelection algorithmSelection) {
    
    logger.debug("Normal confidence scenario: selecting top 3 algorithms");
    return selectTopAlgorithms(algorithmSelection, 3, "DISQUALIFIED (not in top 3 below High Confidence)");
  }

  /**
   * Generic method to select top N algorithms and record discard reasons for the rest.
   */
  private AlgorithmSelection selectTopAlgorithms(
      AlgorithmSelection algorithmSelection, int topN, String discardReason) {
    
    List<WeightedAlgorithm> sorted = sortAlgorithmsByWeightDescending(algorithmSelection.selectedAlgorithms());
    Set<WeightedAlgorithm> kept = new HashSet<>(sorted.subList(0, Math.min(topN, sorted.size())));
    Set<WeightedAlgorithm> discarded = createDiscardedSet(sorted, kept);
    
    var updatedReasons = recordSelectionDiscards(discarded, discardReason, algorithmSelection.updatedReasons());
    
    return new AlgorithmSelection(kept, updatedReasons);
  }

  // Helper methods for better readability and DRY principle
  
  private double calculateMinimumWeightThreshold(Set<WeightedAlgorithm> weightedAlgorithms) {
    return weightedAlgorithms.size() == 1
        ? weightedAlgorithms.stream().findFirst().map(WeightedAlgorithm::weight).orElse(0.4)
        : 0.4;
  }

  private Set<WeightedAlgorithm> findAlgorithmsBelowThreshold(Set<WeightedAlgorithm> algorithms, double threshold) {
    return algorithms.stream()
        .filter(wa -> wa.weight() < threshold)
        .collect(Collectors.toSet());
  }

  private Set<WeightedAlgorithm> filterAlgorithmsAboveThreshold(Set<WeightedAlgorithm> algorithms, double threshold) {
    return algorithms.stream()
        .filter(algorithm -> algorithm.weight() >= threshold)
        .collect(Collectors.toSet());
  }

  private Optional<WeightedAlgorithm> findHighestWeightedAlgorithm(Set<WeightedAlgorithm> algorithms) {
    return algorithms.stream().max(Comparator.comparingDouble(WeightedAlgorithm::weight));
  }

  private boolean isHighConfidenceScenario(Optional<WeightedAlgorithm> highestWeighted) {
    return highestWeighted.isPresent() && highestWeighted.get().weight() > HIGH_CONFIDENCE_THRESHOLD;
  }

  private List<WeightedAlgorithm> sortAlgorithmsByWeightDescending(Set<WeightedAlgorithm> algorithms) {
    return algorithms.stream()
        .sorted(Comparator.comparingDouble(WeightedAlgorithm::weight).reversed())
        .toList();
  }

  private Set<WeightedAlgorithm> createDiscardedSet(List<WeightedAlgorithm> sorted, Set<WeightedAlgorithm> kept) {
    Set<WeightedAlgorithm> discarded = new HashSet<>(sorted);
    discarded.removeAll(kept);
    return discarded;
  }

  private Map<PositioningAlgorithmType, SelectionReasoning> recordThresholdDiscards(Set<WeightedAlgorithm> discarded, double threshold, 
                                      Map<PositioningAlgorithmType, SelectionReasoning> selectionReasons) {
    for (WeightedAlgorithm wa : discarded) {
      // Format to maintain test compatibility: tests expect reasons starting with "Weight="
      String reason = String.format("DISQUALIFIED  (below threshold %.2f) . Weight Calculation: %s", 
           threshold, wa.weightCalculation());
      PositioningAlgorithmType type = findTypeForAlgorithm(wa.algorithm());
      if (type != null) {
        selectionReasons.computeIfPresent(type, (k, v) -> appendDiscardReason(v, reason));
      }
    }
    return selectionReasons;
  }

    private Map<PositioningAlgorithmType, SelectionReasoning> recordSelectionDiscards(Set<WeightedAlgorithm> discarded, String baseReason, 
                                      Map<PositioningAlgorithmType, SelectionReasoning> selectionReasons) {
    for (WeightedAlgorithm wa : discarded) {
      String reason = String.format("%s. Weight Calculation: %s", baseReason, wa.weightCalculation());
      PositioningAlgorithmType type = findTypeForAlgorithm(wa.algorithm());
      if (type != null) {
        selectionReasons.computeIfPresent(type, (k, v) -> appendDiscardReason(v, reason));
      }
    }
    return selectionReasons;
  }



  /**
   * Finds the PositioningAlgorithmType for a given algorithm implementation.
   *
   * @param algorithm The algorithm implementation to find the type for
   * @return The matching PositioningAlgorithmType, or null if not found
   */
  private PositioningAlgorithmType findTypeForAlgorithm(PositioningAlgorithm algorithm) {
    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      if (type.getImplementation().getClass().equals(algorithm.getClass())) {
        return type;
      }
    }
    return null;
  }

  /**
   * Creates the final AlgorithmSelectionInfo from the AlgorithmSelection.
   * Adds "Selected. Weight Calculation: ..." reasons for selected algorithms.
   *
   * @param algorithmSelection The final algorithm selection with algorithms and reasons
   * @return AlgorithmSelectionInfo containing the algorithms, weights and updated reasons
   */
  private AlgorithmSelectionInfo createAlgorithmSelectionInfo(AlgorithmSelection algorithmSelection) {

    // Extract selected algorithms and their weights
    Map<PositioningAlgorithm, Double> algorithmWeights =
        algorithmSelection.selectedAlgorithms().stream()
            .collect(Collectors.toMap(WeightedAlgorithm::algorithm, WeightedAlgorithm::weight));

    // Build reasons map and append "Selected" reasons for selected algorithms
    Map<PositioningAlgorithm, List<String>> algorithmReasons = buildFinalReasons(algorithmSelection);

    return new AlgorithmSelectionInfo(algorithmWeights, algorithmReasons);
  }

  /**
   * Builds the final reasons map by converting from PositioningAlgorithmType-based reasons
   * to PositioningAlgorithm-based reasons, and adding "Selected" reasons for chosen algorithms.
   *
   * @param algorithmSelection The algorithm selection containing reasons and selected algorithms
   * @return Map of algorithms to their selection reasons
   */
  private Map<PositioningAlgorithm, List<String>> buildFinalReasons(AlgorithmSelection algorithmSelection) {
    Map<PositioningAlgorithm, List<String>> algorithmReasons = new HashMap<>();
    
    // Convert SelectionReasoning map to algorithm-based reasons map
    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      PositioningAlgorithm algorithm = type.getImplementation();
      List<String> reasons = new ArrayList<>();
      
      // Add existing reasons from the selection process
      algorithmSelection.updatedReasons().computeIfPresent(type, (k, v) -> {
        if (v.reasonings() != null) {
          reasons.addAll(v.reasonings());
        }
        return v;
      });
      
      // Add "Selected" reason if this algorithm was chosen
      algorithmSelection.selectedAlgorithms().stream()
          .filter(wa -> wa.algorithm().getClass().equals(algorithm.getClass()))
          .findFirst()
          .ifPresent(selectedAlgorithm -> {
            String selectedReason = String.format(
                "SELECTED. Weight Calculation: %s", 
                selectedAlgorithm.weightCalculation());
            reasons.add(selectedReason);
          });
      
      algorithmReasons.put(algorithm, reasons);
    }
    
    return algorithmReasons;
  }

  /**
   * Apply hard constraints to eliminate algorithms that are mathematically or practically invalid
   * Returns a SelectedAlgorithms containing eligible algorithms and reasons for inclusion/exclusion
   */
  private SelectedAlgorithms selectAlgorithmsBasedOnHardConstraints(SelectionContext context) {

    // Handle extremely weak signals - ONLY proximity algorithm is usable
    if (context.getSignalQuality() == SignalQualityFactor.VERY_WEAK_SIGNAL) {
      logger.debug("Extremely weak signals detected, using pre-computed algorithm selection");
      return VERY_WEAK_SIGNAL_ALGORITHMS;
    }

    // Get base algorithm selection based on AP count
    SelectedAlgorithms baseSelection = getAlgorithmsForApCount(context.getApCountFactor());
    logger.debug("Applied AP count constraints for {}", context.getApCountFactor());

    // Handle collinearity or poor geometry if needed
    if (context.getGeometricQuality() == GeometricQualityFactor.COLLINEAR
        || context.getGeometricQuality() == GeometricQualityFactor.POOR_GDOP) {
      return handleGeometricConstraints(baseSelection, context);
    }

    return baseSelection;
  }

  /**
   * Handles geometric constraints (collinearity or poor geometry) by filtering out unsuitable
   * algorithms.
   *
   * @param baseSelection The base algorithm selection before geometric constraints
   * @param context The selection context containing geometric information
   * @return A new SelectedAlgorithms instance with geometric constraints applied
   */
  private SelectedAlgorithms handleGeometricConstraints(
      SelectedAlgorithms baseSelection, SelectionContext context) {

    String reason =
        context.getGeometricQuality() == GeometricQualityFactor.COLLINEAR
            ? DISQUALIFIED_COLLINEAR
            : DISQUALIFIED_POOR_GEOMETRY;

    // Filter out trilateration for collinear or poor geometry scenarios
    SelectedAlgorithms filteredSelection =
        baseSelection.copyWithFilter(
            type -> type != PositioningAlgorithmType.TRILATERATION, reason);

    logger.debug(
        "Applied geometric constraints (geometry quality: {})", context.getGeometricQuality());

    return filteredSelection;
  }

  /** Get pre-computed algorithm selection based on AP count */
  private SelectedAlgorithms getAlgorithmsForApCount(APCountFactor apCountFactor) {
    return switch (apCountFactor) {
      case SINGLE_AP -> SINGLE_AP_ALGORITHMS;
      case TWO_APS -> TWO_APS_ALGORITHMS;
      case THREE_APS -> THREE_APS_ALGORITHMS;
      default -> FOUR_PLUS_APS_ALGORITHMS;
    };
  }

 
  private Map<PositioningAlgorithm, List<String>> mergeReasons(
      SelectedAlgorithms eligibleAlgorithms, Set<WeightedAlgorithm> weightedAlgorithms) {

    // Create a map from algorithm to its weight reason
    Map<PositioningAlgorithm, String> weightedAlgorithmsMap =
        weightedAlgorithms.stream()
            .collect(Collectors.toMap(WeightedAlgorithm::algorithm, WeightedAlgorithm::weightCalculation));

    // Build flattened reasons combining hard-constraint reasons and weight formulas
    Map<PositioningAlgorithm, List<String>> collect = new HashMap<>();
    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      PositioningAlgorithm algo = type.getImplementation();
      List<String> reasonList = new ArrayList<>();

      eligibleAlgorithms.reasons.computeIfPresent(type, (k, v) -> {
        if (v.reasonings() != null) {
          reasonList.addAll(v.reasonings());
        }
        return v;
      });

      weightedAlgorithmsMap.computeIfPresent(algo, (k, v) -> {
        reasonList.add(v);
        return v;
      });

      collect.put(algo, reasonList);
    }
    return collect;
  }

  /**
   * Appends a discard reason to the SelectionReasoning for the given algorithm.
   *
   * @param selectionReasons Map of selection reasons to update
   * @param algorithm The algorithm that was discarded
   * @param reason The reason for discarding the algorithm
   */
  private SelectionReasoning appendDiscardReason(
      SelectionReasoning selectionReason,
      String reason) {

    
    // Create a new list with existing reasons plus the new reason
    List<String> updatedReasons = new ArrayList<>(selectionReason.reasonings());
    updatedReasons.add(reason);
    
    // Create new SelectionReasoning with updated reasons list
    return new SelectionReasoning(selectionReason.type(), false, updatedReasons);
   
  }

}
