package com.wifi.positioning.algorithm;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.wifi.positioning.algorithm.impl.PositionCombiner;
import com.wifi.positioning.algorithm.selection.AlgorithmSelector;
import com.wifi.positioning.algorithm.selection.SelectionContext;
import com.wifi.positioning.algorithm.selection.SelectionContextBuilder;
import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.service.SignalPhysicsValidator;

import jakarta.annotation.PreDestroy;

/**
 * Implements a hybrid algorithm selector that chooses and combines multiple WiFi positioning
 * algorithms based on the available data and scenario characteristics.
 *
 * <p>Uses a flexible rule-based approach to select algorithms and calculate weights. Includes
 * detailed information about algorithm selection reasoning for better explainability.
 *
 * <p>Thread Safety: This class is thread-safe. The ExecutorService is shared across requests but
 * individual algorithm executions are isolated. The class uses immutable data structures where
 * possible and defensive copying for mutable collections.
 */
@Component
public class WifiPositioningCalculator {

  private static final Logger logger = LoggerFactory.getLogger(WifiPositioningCalculator.class);

  /**
   * Timeout for individual algorithm execution in seconds. Rationale: 5 seconds allows sufficient
   * time for complex algorithms (trilateration, maximum likelihood) while preventing indefinite
   * blocking. Based on empirical testing, most algorithms complete within 100-500ms, so 5s provides
   * ample safety margin.
   */
  private static final long ALGORITHM_EXECUTION_TIMEOUT_SECONDS = 5L;

  /**
   * Timeout for executor service shutdown in seconds. Rationale: Same as algorithm timeout - allows
   * running algorithms to complete gracefully before forcing shutdown during application
   * termination.
   */
  private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5L;

  /**
   * Minimum number of threads in the executor pool. Rationale: Ensures at least 2 threads are
   * available for parallel execution even on single-core systems, allowing for basic parallelism in
   * algorithm execution.
   */
  private static final int MIN_THREAD_POOL_SIZE = 2;

  /**
   * Divisor for calculating thread pool size based on available processors. Rationale: Uses half of
   * available processors to balance parallelism with system resource usage. WiFi positioning is
   * CPU-intensive but not the only system workload. This leaves resources for other application
   * components and system processes.
   */
  private static final int PROCESSOR_DIVISOR = 2;

  private final List<PositioningAlgorithm> algorithms;
  private final AlgorithmSelector algorithmSelector;
  private final SelectionContextBuilder contextBuilder;
  private final PositionCombiner positionCombiner;
  private final SignalPhysicsValidator signalPhysicsValidator;
  private final ExecutorService executorService;

  public WifiPositioningCalculator(
      List<PositioningAlgorithm> algorithms,
      AlgorithmSelector algorithmSelector,
      SelectionContextBuilder contextBuilder,
      PositionCombiner positionCombiner,
      SignalPhysicsValidator signalPhysicsValidator) {
    this.algorithms = algorithms;
    this.algorithmSelector = algorithmSelector;
    this.contextBuilder = contextBuilder;
    this.positionCombiner = positionCombiner;
    this.signalPhysicsValidator = signalPhysicsValidator;

    // Calculate optimal thread pool size: max(MIN_THREAD_POOL_SIZE, availableProcessors /
    // PROCESSOR_DIVISOR)
    // This ensures adequate parallelism while preserving system resources
    int threadPoolSize =
        Math.max(
            MIN_THREAD_POOL_SIZE, Runtime.getRuntime().availableProcessors() / PROCESSOR_DIVISOR);
    this.executorService = Executors.newFixedThreadPool(threadPoolSize);

    logger.info("Initialized WifiPositioningCalculator with thread pool size: {}", threadPoolSize);
  }

  /**
   * Calculate position using the best available algorithms for the given scenario.
   *
   * @param wifiScan List of WiFi scan results
   * @param knownAPs List of known access points
   * @return PositioningResult containing the calculated position and information about algorithms
   *     used, or null if position calculation fails
   */
  public PositioningResult calculatePosition(
      List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs) {
    if (wifiScan == null || wifiScan.isEmpty() || knownAPs == null || knownAPs.isEmpty()) {
      return null;
    }

    // Filter valid APs (those we know locations for)
    Map<String, WifiAccessPoint> apMap = createAPMap(knownAPs);
    List<WifiScanResult> validScans = filterValidScans(wifiScan, apMap);

    if (validScans.isEmpty()) {
      return null;
    }

    // Validate if the signal strengths are physically possible
    if (!signalPhysicsValidator.isPhysicallyPossible(validScans)) {
      // Return null when signals violate physical laws
      return null;
    }

    // 1. Evaluate scenario characteristics
    SelectionContext context = contextBuilder.buildContext(validScans, apMap);

    // 2. Apply algorithm selection rules
    AlgorithmSelector.AlgorithmSelectionInfo selectionInfo =
        algorithmSelector.selectAlgorithmsWithReasons(validScans, apMap, context);

    Map<PositioningAlgorithm, Double> weightedAlgorithms = selectionInfo.algorithmWeights();
    Map<PositioningAlgorithm, List<String>> selectionReasons = selectionInfo.selectionReasons();

    if (weightedAlgorithms.isEmpty()) {
      return null;
    }

    // 3. Calculate positions using selected algorithms in parallel
    List<PositionCombiner.WeightedPosition> positions =
        calculatePositionsInParallel(weightedAlgorithms, validScans, knownAPs);

    if (positions.isEmpty()) {
      return null;
    }

    // 4. Combine results using configured position combiner
    Position combinedPosition = positionCombiner.combinePositions(positions);

    if (combinedPosition == null) {
      return null;
    }

    return new PositioningResult(combinedPosition, weightedAlgorithms, selectionReasons, context);
  }

  /**
   * Calculates positions using multiple algorithms in parallel with proper exception handling. Each
   * algorithm runs in its own thread with a timeout to prevent hanging.
   *
   * <p>Exception Handling Strategy: - TimeoutException: Algorithm took too long, logged as warning
   * - InterruptedException: Thread was interrupted, preserves interrupt status -
   * ExecutionException: Algorithm threw an exception, logged as error - Other exceptions:
   * Unexpected errors, logged as error
   *
   * <p>Mathematical Foundation: Each algorithm produces a position estimate P_i with confidence
   * C_i. The final weight W_i = baseWeight_i * C_i where: - baseWeight_i: Algorithm's suitability
   * for the scenario (from selector) - C_i: Algorithm's confidence in its result (self-assessed)
   *
   * <p>This weighting scheme ensures that both algorithmic appropriateness and result confidence
   * contribute to the final position calculation.
   *
   * @param weightedAlgorithms Map of algorithms and their base weights from selection process
   * @param validScans List of valid WiFi scan results
   * @param knownAPs List of known access points
   * @return List of weighted positions calculated by successful algorithms
   */
  private List<PositionCombiner.WeightedPosition> calculatePositionsInParallel(
      Map<PositioningAlgorithm, Double> weightedAlgorithms,
      List<WifiScanResult> validScans,
      List<WifiAccessPoint> knownAPs) {

    List<CompletableFuture<PositionCombiner.WeightedPosition>> futures =
        submitAlgorithmTasks(weightedAlgorithms, validScans, knownAPs);

    return collectPositionResults(futures);
  }

  /**
   * Submits positioning algorithm tasks to the executor service for parallel execution. Each
   * algorithm is wrapped in a CompletableFuture with proper exception handling.
   *
   * @param weightedAlgorithms Map of algorithms and their base weights
   * @param validScans List of valid WiFi scan results
   * @param knownAPs List of known access points
   * @return List of CompletableFuture tasks for position calculation
   */
  private List<CompletableFuture<PositionCombiner.WeightedPosition>> submitAlgorithmTasks(
      Map<PositioningAlgorithm, Double> weightedAlgorithms,
      List<WifiScanResult> validScans,
      List<WifiAccessPoint> knownAPs) {

    List<CompletableFuture<PositionCombiner.WeightedPosition>> futures = new ArrayList<>();

    for (Map.Entry<PositioningAlgorithm, Double> entry : weightedAlgorithms.entrySet()) {
      PositioningAlgorithm algorithm = entry.getKey();
      Double baseWeight = entry.getValue();

      CompletableFuture<PositionCombiner.WeightedPosition> future =
          CompletableFuture.supplyAsync(
              () ->
                  executeAlgorithmWithExceptionHandling(
                      algorithm, baseWeight, validScans, knownAPs),
              executorService);

      futures.add(future);
    }

    return futures;
  }

  /**
   * Executes a single positioning algorithm with comprehensive exception handling. Calculates the
   * final weight by combining base weight with algorithm confidence.
   *
   * @param algorithm The positioning algorithm to execute
   * @param baseWeight The base weight assigned by the algorithm selector
   * @param validScans List of valid WiFi scan results
   * @param knownAPs List of known access points
   * @return WeightedPosition if successful, null if algorithm fails
   */
  private PositionCombiner.WeightedPosition executeAlgorithmWithExceptionHandling(
      PositioningAlgorithm algorithm,
      Double baseWeight,
      List<WifiScanResult> validScans,
      List<WifiAccessPoint> knownAPs) {

    try {
      Position position = algorithm.calculatePosition(validScans, knownAPs);
      if (position != null) {
        // Final weight calculation: W_i = baseWeight_i * confidence_i
        // This combines algorithmic suitability with result confidence
        double weight = baseWeight * algorithm.getConfidence();
        return new PositionCombiner.WeightedPosition(position, weight);
      }
      return null;
    } catch (Exception e) {
      logger.warn("Algorithm {} failed during execution: {}", algorithm.getName(), e.getMessage());
      return null;
    }
  }

  /**
   * Collects results from all algorithm futures with proper timeout and exception handling. Each
   * future is processed with individual timeout and error recovery strategies.
   *
   * @param futures List of CompletableFuture tasks for position calculation
   * @return List of successfully calculated weighted positions
   */
  private List<PositionCombiner.WeightedPosition> collectPositionResults(
      List<CompletableFuture<PositionCombiner.WeightedPosition>> futures) {

    return futures.stream()
        .map(this::retrievePositionWithTimeout)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Retrieves position result from a future with timeout and comprehensive exception handling.
   * Implements different recovery strategies for different types of failures.
   *
   * @param future The CompletableFuture containing the position calculation task
   * @return WeightedPosition if successful, null if failed or timed out
   */
  private PositionCombiner.WeightedPosition retrievePositionWithTimeout(
      CompletableFuture<PositionCombiner.WeightedPosition> future) {

    try {
      return future.get(ALGORITHM_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      logger.warn(
          "Algorithm execution timed out after {} seconds", ALGORITHM_EXECUTION_TIMEOUT_SECONDS);
      future.cancel(true); // Interrupt the algorithm if possible
      return null;
    } catch (InterruptedException e) {
      logger.warn("Algorithm execution was interrupted");
      Thread.currentThread().interrupt(); // Preserve interrupt status
      return null;
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      logger.error(
          "Algorithm execution failed with exception: {}",
          cause != null ? cause.getMessage() : e.getMessage());
      return null;
    } catch (Exception e) {
      logger.error("Unexpected error during algorithm execution: {}", e.getMessage());
      return null;
    }
  }

  private Map<String, WifiAccessPoint> createAPMap(List<WifiAccessPoint> knownAPs) {
    return knownAPs.stream()
        .collect(
            Collectors.toMap(
                WifiAccessPoint::getMacAddress,
                ap -> ap,
                (existing, replacement) -> existing // Keep first in case of duplicates
                ));
  }

  private List<WifiScanResult> filterValidScans(
      List<WifiScanResult> wifiScan, Map<String, WifiAccessPoint> apMap) {
    return wifiScan.stream()
        .filter(scan -> apMap.containsKey(scan.macAddress()))
        .collect(Collectors.toList());
  }

  /**
   * Clean up executor service on application shutdown with proper timeout handling. Follows the
   * recommended shutdown pattern from Java Concurrency in Practice.
   */
  @PreDestroy
  public void cleanup() {
    logger.info("Shutting down WifiPositioningCalculator executor service");
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        logger.warn("Executor did not terminate gracefully, forcing shutdown");
        executorService.shutdownNow();

        // Wait a bit more for tasks to respond to being cancelled
        if (!executorService.awaitTermination(
            EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          logger.error("Executor did not terminate after forced shutdown");
        }
      }
    } catch (InterruptedException e) {
      logger.warn("Interrupted while waiting for executor shutdown");
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Result of positioning calculation containing the calculated position, information about
   * algorithms used, reasons for algorithm selection, and selection context.
   */
  public record PositioningResult(
      Position position,
      Map<PositioningAlgorithm, Double> algorithmWeights,
      Map<PositioningAlgorithm, List<String>> selectionReasons,
      SelectionContext selectionContext) {
    /**
     * Constructor that doesn't require selection reasons and context for backward compatibility.
     */
    public PositioningResult(
        Position position, Map<PositioningAlgorithm, Double> algorithmWeights) {
      this(position, algorithmWeights, Map.of(), null);
    }

    /** Constructor that doesn't require selection context for backward compatibility. */
    public PositioningResult(
        Position position,
        Map<PositioningAlgorithm, Double> algorithmWeights,
        Map<PositioningAlgorithm, List<String>> selectionReasons) {
      this(position, algorithmWeights, selectionReasons, null);
    }

    /**
     * Returns a detailed string representation of the calculation information combining the
     * selection context and algorithm selection reasons.
     *
     * @return A string with detailed calculation information
     */
    public String getCalculationInfo() {
      StringBuilder info = new StringBuilder();

      // Add selection context information if available
      if (selectionContext != null) {
        info.append("Selection Context:\n");
        info.append("  AP Count: ").append(selectionContext.getApCountFactor()).append("\n");
        info.append("  Signal Quality: ").append(selectionContext.getSignalQuality()).append("\n");
        info.append("  Signal Distribution: ")
            .append(selectionContext.getSignalDistribution())
            .append("\n");
        info.append("  Geometric Quality: ")
            .append(selectionContext.getGeometricQuality())
            .append("\n");
        // Note: Collinearity is now directly handled by the GeometricQualityFactor.COLLINEAR value
        info.append("\n");
      }

      // Add algorithm weights information
      if (algorithmWeights != null && !algorithmWeights.isEmpty()) {
        info.append("Algorithm Weights:\n");
        algorithmWeights.forEach(
            (algorithm, weight) -> {
              info.append(
                  String.format(
                      "  %s (weight: %.2f)\n", algorithm.getName().toLowerCase(), weight));
            });
        info.append("\n");
      }

      // Add algorithm selection reasons if available
      if (selectionReasons != null && !selectionReasons.isEmpty()) {
        info.append("Algorithm Selection Reasons:\n");
        selectionReasons.forEach(
            (algorithm, reasons) -> {
              info.append("  ").append(algorithm.getName()).append(":\n");
              reasons.forEach(reason -> info.append("    - ").append(reason).append("\n"));
            });
      }

      return info.toString();
    }

    /**
     * Get the names of all methods used in the positioning calculation
     *
     * @return List of method names used
     */
    public List<String> getMethodsUsedNames() {
      return this.algorithmWeights.keySet().stream()
          .map(
              algorithm -> {
                String name = algorithm.getName();
                return name != null ? name.toLowerCase().replaceAll("\\s+", "") : "unknown";
              })
          .collect(Collectors.toList());
    }
  }
}
