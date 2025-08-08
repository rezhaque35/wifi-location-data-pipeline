package com.wifi.positioning.algorithm.selection;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
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

  // Signal strength thresholds
  private static final double EXTREMELY_WEAK_SIGNAL_THRESHOLD = -95.0;

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
    Map<PositioningAlgorithmType, List<String>> reasons = createReasonMap();

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      if (type == PositioningAlgorithmType.PROXIMITY) {
        reasons.get(type).add(ONLY_VIABLE_FOR_WEAK);
      } else {
        reasons.get(type).add(DISQUALIFIED_SIGNAL_TOO_WEAK);
      }
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /** Initialize algorithms for single AP scenario (proximity and log distance) */
  private static SelectedAlgorithms initializeSingleApAlgorithms() {
    Set<PositioningAlgorithmType> eligibleTypes =
        Set.of(PositioningAlgorithmType.PROXIMITY, PositioningAlgorithmType.LOG_DISTANCE);
    Map<PositioningAlgorithmType, List<String>> reasons = createReasonMap();

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      if (type == PositioningAlgorithmType.PROXIMITY) {
        reasons.get(type).add(VALID_FOR_SINGLE_AP);
      } else if (type == PositioningAlgorithmType.LOG_DISTANCE) {
        reasons.get(type).add(VALID_FOR_SINGLE_AP_WITH_MODEL);
      } else {
        reasons.get(type).add(DISQUALIFIED_INSUFFICIENT_APS);
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
    Map<PositioningAlgorithmType, List<String>> reasons = createReasonMap();

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      if (eligibleTypes.contains(type)) {
        reasons.get(type).add(VALID_FOR_TWO_APS);
      } else if (type == PositioningAlgorithmType.TRILATERATION) {
        reasons.get(type).add(TRILAT_REQUIRES_3_APS);
      } else if (type == PositioningAlgorithmType.MAXIMUM_LIKELIHOOD) {
        reasons.get(type).add(ML_REQUIRES_4_APS);
      }
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /** Initialize algorithms for three APs scenario (all except maximum likelihood) */
  private static SelectedAlgorithms initializeThreeApsAlgorithms() {
    Set<PositioningAlgorithmType> eligibleTypes =
        new HashSet<>(Arrays.asList(PositioningAlgorithmType.values()));
    eligibleTypes.remove(PositioningAlgorithmType.MAXIMUM_LIKELIHOOD);

    Map<PositioningAlgorithmType, List<String>> reasons = createReasonMap();

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      if (eligibleTypes.contains(type)) {
        reasons.get(type).add(VALID_FOR_THREE_APS);
      } else {
        reasons.get(type).add(ML_REQUIRES_4_APS);
      }
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /** Initialize algorithms for four+ APs scenario (all algorithms eligible) */
  private static SelectedAlgorithms initializeFourPlusApsAlgorithms() {
    Set<PositioningAlgorithmType> eligibleTypes =
        new HashSet<>(Arrays.asList(PositioningAlgorithmType.values()));
    Map<PositioningAlgorithmType, List<String>> reasons = createReasonMap();

    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      reasons.get(type).add(VALID_FOR_FOUR_PLUS_APS);
    }

    return new SelectedAlgorithms(eligibleTypes, reasons);
  }

  /** Helper method to create a reason map with empty lists for all algorithm types */
  private static Map<PositioningAlgorithmType, List<String>> createReasonMap() {
    Map<PositioningAlgorithmType, List<String>> reasons = new HashMap<>();
    for (PositioningAlgorithmType type : PositioningAlgorithmType.values()) {
      reasons.put(type, new ArrayList<>());
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
  record SelectedAlgorithms(
      Set<PositioningAlgorithmType> eligibleAlgorithmTypes,
      Map<PositioningAlgorithmType, List<String>> reasons) {
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

      // Create a deep copy of reasons
      Map<PositioningAlgorithmType, List<String>> newReasons = new HashMap<>();
      reasons.forEach((type, reasonList) -> newReasons.put(type, new ArrayList<>(reasonList)));

      // Add reasons for excluded algorithms
      eligibleAlgorithmTypes.stream()
          .filter(type -> !filter.test(type))
          .forEach(type -> newReasons.get(type).add(reasonForExclusion));

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
  private record WeightedAlgorithm(PositioningAlgorithm algorithm, double weight, String reason) {}

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
    String reason =
        String.format(
            "Weight=%1$.2f: base(%2$.2f) × signal(%3$.2f) × geometric(%4$.2f) × distribution(%5$.2f)",
            weight, baseWeight, signalMultiplier, geometricMultiplier, distributionMultiplier);

    return new WeightedAlgorithm(algorithm, weight, reason);
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

    logger.debug(
        "After hard constraints, eligible algorithms: {}",
        eligibleAlgorithms.eligibleAlgorithmTypes.stream()
            .map(type -> type.getImplementation().getName())
            .collect(Collectors.joining(", ")));

    // -------------- PHASE TWO: ALGORITHM WEIGHTING ----------------
    Set<WeightedAlgorithm> weightedAlgorithms =
        applyAlgorithmWeighting(eligibleAlgorithms, context);

    // -------------- PHASE THREE: FINALIST SELECTION ----------------
    Set<WeightedAlgorithm> finalSelectedAlgorithms =
        applyFinalistSelection(weightedAlgorithms, context);

    logger.debug(
        "Final algorithm selection: {}",
        weightedAlgorithms.stream()
            .map(e -> e.algorithm().getName() + "=" + e.weight())
            .collect(Collectors.joining(", ")));

    return createAlgorithmSelectionInfo(
        finalSelectedAlgorithms, mergeReasons(eligibleAlgorithms, weightedAlgorithms));
  }

  /**
   * Applies the finalist selection phase of the algorithm selection framework. This phase filters
   * the algorithms based on signal quality and confidence thresholds. According to the framework:
   * 1. Threshold Filter: Remove algorithms with final weight < 0.4 2. Adaptive Selection: - If
   * highest-weighted algorithm has weight > 0.8: Use it alone or with one backup - Otherwise:
   * Select top 3 algorithms
   *
   * @param weightedAlgorithms The set of weighted algorithms after phase two
   * @param context The selection context
   * @return A filtered set of finalist algorithms
   */
  private Set<WeightedAlgorithm> applyFinalistSelection(
      Set<WeightedAlgorithm> weightedAlgorithms, SelectionContext context) {

    logger.debug("Applying finalist selection phase");

    // ---------- STEP 1: THRESHOLD FILTER ----------
    // Remove algorithms with weight below 0.4
    double minimumWeightThreshold =
        weightedAlgorithms.size() == 1
            ? weightedAlgorithms.stream().findFirst().map(WeightedAlgorithm::weight).orElse(0.4)
            : 0.4;
    weightedAlgorithms =
        weightedAlgorithms.stream()
            .filter(algorithm -> algorithm.weight() >= minimumWeightThreshold)
            .collect(Collectors.toSet());

    logger.debug(
        "After threshold filter (min weight {}): {} algorithms remain",
        minimumWeightThreshold,
        weightedAlgorithms.size());

    // ---------- STEP 2: ADAPTIVE SELECTION ----------
    // Find the highest weight
    Optional<WeightedAlgorithm> highestWeighted =
        weightedAlgorithms.stream().max(Comparator.comparingDouble(WeightedAlgorithm::weight));

    if (highestWeighted.isPresent() && highestWeighted.get().weight() > HIGH_CONFIDENCE_THRESHOLD) {
      // High confidence scenario: Use highest weighted algorithm with at most one backup
      logger.debug(
          "High confidence algorithm detected with weight > {}: {}",
          HIGH_CONFIDENCE_THRESHOLD,
          highestWeighted.get().algorithm().getName());

      // Limit to top 2 algorithms (primary + one backup)
      weightedAlgorithms =
          weightedAlgorithms.stream()
              .sorted(Comparator.comparingDouble(WeightedAlgorithm::weight).reversed())
              .limit(2)
              .collect(Collectors.toSet());

      logger.debug("Limited to primary algorithm with one backup due to high confidence");
    } else if (weightedAlgorithms.size() > 3) {
      // No high confidence algorithm, but more than 3 candidates
      // Select top 3 algorithms
      weightedAlgorithms =
          weightedAlgorithms.stream()
              .sorted(Comparator.comparingDouble(WeightedAlgorithm::weight).reversed())
              .limit(3)
              .collect(Collectors.toSet());

      logger.debug("Limited to top 3 algorithms (no high confidence algorithm)");
    }

    return weightedAlgorithms;
  }

  /**
   * Creates the final AlgorithmSelectionInfo from the list of weighted algorithms and reasons
   *
   * @param weightedAlgorithms List of algorithms with their weights
   * @param reasons Map of reasons for algorithm selection
   * @return AlgorithmSelectionInfo containing the algorithms, weights and reasons
   */
  private AlgorithmSelectionInfo createAlgorithmSelectionInfo(
      Set<WeightedAlgorithm> weightedAlgorithms, Map<PositioningAlgorithm, List<String>> reasons) {

    // Convert weightedAlgorithms list to a map of algorithm -> weight
    Map<PositioningAlgorithm, Double> algorithmWeights =
        weightedAlgorithms.stream()
            .collect(Collectors.toMap(WeightedAlgorithm::algorithm, WeightedAlgorithm::weight));

    return new AlgorithmSelectionInfo(algorithmWeights, reasons);
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

  /** Check if all signals in the scan results are extremely weak */
  private boolean areAllSignalsExtremelyWeak(List<WifiScanResult> validScans) {
    return validScans.stream()
        .allMatch(scan -> scan.signalStrength() <= EXTREMELY_WEAK_SIGNAL_THRESHOLD);
  }

  private Map<PositioningAlgorithm, List<String>> mergeReasons(
      SelectedAlgorithms eligibleAlgorithms, Set<WeightedAlgorithm> weightedAlgorithms) {

    // Create a map from algorithm to its weight reason
    Map<PositioningAlgorithm, String> weightedAlgorithmsMap =
        weightedAlgorithms.stream()
            .collect(Collectors.toMap(WeightedAlgorithm::algorithm, WeightedAlgorithm::reason));

    // Process each algorithm type
    Map<PositioningAlgorithm, List<String>> collect =
        Arrays.stream(PositioningAlgorithmType.values())
            .map(
                algorithm -> {
                  List<String> reasonList =
                      new ArrayList<>(
                          eligibleAlgorithms.reasons.getOrDefault(algorithm, List.of()));
                  return Pair.of(algorithm, reasonList);
                })
            .map(
                P -> {
                  PositioningAlgorithm algorithm = P.getKey().getImplementation();
                  List<String> reasonList = P.getValue();
                  // Add weight reasons if available
                  if (weightedAlgorithmsMap.containsKey(algorithm)) {
                    reasonList.add(weightedAlgorithmsMap.get(algorithm));
                  }
                  return Pair.of(algorithm, reasonList);
                })
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    return collect;
  }

  /** Merge hard constraint reasons for a specific algorithm type */
}
