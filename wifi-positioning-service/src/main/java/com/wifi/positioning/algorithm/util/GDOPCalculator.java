package com.wifi.positioning.algorithm.util;

import org.apache.commons.math3.linear.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for calculating Geometric Dilution of Precision (GDOP) and related metrics for WiFi
 * positioning systems.
 *
 * <p>GDOP is a measure of how the geometric configuration of access points affects the accuracy of
 * a position calculation. Better AP geometry provides better positioning accuracy, while poor
 * geometry (like collinear APs) reduces accuracy.
 *
 * <p>This class provides methods for: 1. Calculating GDOP from AP coordinates and estimated
 * position 2. Converting GDOP to scaling factors for accuracy/confidence adjustments 3. Detecting
 * collinearity and poor geometry conditions 4. Calculating condition number for geometric quality
 * assessment
 *
 * <p>Mathematical Foundation: All calculations are based on established academic principles from
 * satellite navigation theory (GPS/GNSS) adapted for indoor WiFi positioning systems. Key
 * references include Kaplan & Hegarty "Understanding GPS" and IEEE positioning standards.
 */
public class GDOPCalculator {

  private static final Logger logger = LoggerFactory.getLogger(GDOPCalculator.class);

  // ============================================================================
  // GDOP CALCULATION CONSTANTS
  // ============================================================================

  /**
   * Maximum allowed GDOP value for positioning calculations. Rationale: Values above 30 indicate
   * extremely poor geometry where positioning becomes unreliable. This threshold prevents numerical
   * instability in downstream calculations. Academic basis: Standard practice in GPS and indoor
   * positioning systems.
   */
  public static final double MAX_ALLOWED_GDOP = 30.0;

  /**
   * Minimum number of access points required for GDOP calculation. Mathematical requirement: Need
   * at least 3 APs for 2D positioning geometry. Rationale: GDOP calculation requires overdetermined
   * system (n ≥ dimensions + 1).
   */
  private static final int MIN_APS_FOR_GDOP = 3;

  // ============================================================================
  // EARTH COORDINATE SYSTEM CONSTANTS
  // ============================================================================

  /**
   * Earth's radius in meters (WGS84 mean radius). Value: 6,371,000 meters - standard value used in
   * geodetic calculations. Rationale: Required for converting latitude/longitude differences to
   * metric distances. Academic source: World Geodetic System 1984 (WGS84) definition.
   */
  private static final double EARTH_RADIUS_METERS = 6_371_000.0;

  /**
   * Conversion factor from degrees latitude to meters. Formula: EARTH_RADIUS × π / 180° Rationale:
   * 1 degree of latitude ≈ 111,320 meters (constant globally). Mathematical basis: Arc length =
   * radius × angle_in_radians.
   */
  private static final double DEGREES_LAT_TO_METERS = EARTH_RADIUS_METERS * Math.PI / 180.0;

  /**
   * Minimum distance threshold in meters for valid unit vector calculation. Rationale: Prevents
   * division by zero and numerical instability when AP position is extremely close to estimated
   * position. Academic basis: Common practice in trilateration algorithms to avoid singularities.
   */
  private static final double MIN_DISTANCE_THRESHOLD_METERS = 1.0;

  /**
   * Default unit vector component for APs closer than minimum threshold. Rationale: Provides stable
   * fallback vector when distance calculation fails. Mathematical basis: Unit vector [1,0,0]
   * represents positive X-axis direction.
   */
  private static final double DEFAULT_UNIT_VECTOR_X = 1.0;

  private static final double DEFAULT_UNIT_VECTOR_Y = 0.0;
  private static final double DEFAULT_UNIT_VECTOR_Z = 0.0;

  /**
   * Bias term value for positioning calculations. Rationale: Standard practice in positioning
   * systems to account for clock bias and systematic errors. Mathematical basis: Augments geometry
   * matrix for solving unknown bias parameter.
   */
  private static final double BIAS_TERM_VALUE = 1.0;

  /**
   * Zero value for safe initialization and fallback operations. Rationale: Explicit constant for
   * mathematical operations requiring zero, improving code readability and maintainability.
   * Mathematical basis: Additive identity element in real number arithmetic.
   */
  private static final double ZERO_VALUE = 0.0;

  // ============================================================================
  // COORDINATE DIMENSION CONSTANTS
  // ============================================================================

  /**
   * Index for X-coordinate (latitude) in coordinate arrays. Rationale: Explicit constant for array
   * indexing improves code clarity and reduces magic number usage. Geodetic mapping: X-axis
   * corresponds to latitude in geographic coordinates.
   */
  private static final int X_COORDINATE_INDEX = 0;

  /**
   * Index for Y-coordinate (longitude) in coordinate arrays. Rationale: Explicit constant for array
   * indexing improves code clarity. Geodetic mapping: Y-axis corresponds to longitude in geographic
   * coordinates.
   */
  private static final int Y_COORDINATE_INDEX = 1;

  /**
   * Index for Z-coordinate (altitude) in coordinate arrays. Rationale: Explicit constant for 3D
   * positioning support. Geodetic mapping: Z-axis corresponds to altitude above reference
   * ellipsoid.
   */
  private static final int Z_COORDINATE_INDEX = 2;

  /**
   * Minimum dimensions required for 2D positioning. Rationale: 2D positioning requires latitude and
   * longitude coordinates. Mathematical basis: Planar geometry requires minimum 2 spatial
   * dimensions.
   */
  private static final int MIN_2D_DIMENSIONS = 2;

  /**
   * Minimum dimensions required for 3D positioning. Rationale: 3D positioning adds altitude to
   * latitude and longitude. Mathematical basis: Spatial geometry requires 3 dimensions for volume
   * positioning.
   */
  private static final int MIN_3D_DIMENSIONS = 3;

  // ============================================================================
  // GDOP FACTOR CALCULATION CONSTANTS
  // ============================================================================

  /**
   * Base factor for excellent geometry (no adjustment needed). Mathematical meaning: Multiplicative
   * identity - no scaling applied. Rationale: Excellent geometry requires no accuracy/confidence
   * degradation. Used in: calculateGDOPFactor for GDOP ≤ EXCELLENT_GDOP threshold.
   */
  private static final double EXCELLENT_GEOMETRY_BASE_FACTOR = 1.0;

  /**
   * Increment step for GDOP factor calculation in good geometry range. Mathematical formula: Used
   * in linear interpolation between geometry ranges. Rationale: Provides smooth transition from
   * excellent (1.0) to fair (1.5) range. Academic basis: Linear scaling maintains proportional
   * error relationship.
   */
  private static final double GOOD_GEOMETRY_INCREMENT_STEP = 0.5;

  /**
   * Threshold factor between good and fair geometry ranges. Mathematical meaning: Factor = 1.5
   * represents 50% increase in uncertainty. Rationale: Moderate geometry degradation requires
   * proportional accuracy adjustment. Used in: Transition point between good and fair GDOP ranges.
   */
  private static final double GOOD_TO_FAIR_THRESHOLD_FACTOR = 1.5;

  /**
   * Base factor for poor geometry conditions. Mathematical meaning: Factor = 2.0 represents 100%
   * increase in uncertainty. Rationale: Poor geometry significantly impacts positioning
   * reliability. Academic basis: Doubling uncertainty reflects substantial geometric degradation.
   */
  private static final double POOR_GEOMETRY_BASE_FACTOR = 2.0;

  /**
   * Divisor for poor GDOP scaling calculation. Mathematical formula: Used in (gdop - FAIR_GDOP) /
   * POOR_GDOP_SCALING_DIVISOR. Rationale: Controls rate of factor increase for very poor geometry.
   * Academic basis: Logarithmic-like scaling prevents excessive penalty for marginally poor
   * geometry.
   */
  private static final double POOR_GDOP_SCALING_DIVISOR = 2.0;

  /**
   * Maximum cap for GDOP factor to prevent excessive penalties. Mathematical meaning: Factor = 4.0
   * represents 300% increase in uncertainty. Rationale: Prevents unlimited factor growth that could
   * make positioning meaningless. Academic basis: Practical limit based on positioning system
   * utility thresholds.
   */
  private static final double MAX_GDOP_FACTOR_CAP = 4.0;

  // ============================================================================
  // EIGENVALUE CALCULATION CONSTANTS
  // ============================================================================

  /**
   * Coefficient for determinant term in eigenvalue discriminant calculation. Mathematical formula:
   * discriminant = trace² - EIGENVALUE_DETERMINANT_COEFFICIENT × determinant. Rationale: Standard
   * coefficient (4) from quadratic formula for 2×2 matrix eigenvalues. Academic basis: Eigenvalue
   * formula λ₁,λ₂ = (trace ± √(trace² - 4×det))/2.
   */
  private static final int EIGENVALUE_DETERMINANT_COEFFICIENT = 4;

  /**
   * Divisor for eigenvalue calculation from discriminant. Mathematical formula: λ₁,λ₂ = (trace ±
   * √discriminant) / EIGENVALUE_CALCULATION_DIVISOR. Rationale: Standard divisor (2) from quadratic
   * formula for characteristic polynomial. Academic basis: Eigenvalues of 2×2 matrix A follow
   * quadratic equation det(A - λI) = 0.
   */
  private static final double EIGENVALUE_CALCULATION_DIVISOR = 2.0;

  // ============================================================================
  // GEOMETRIC QUALITY FACTOR CONSTANTS
  // ============================================================================

  /**
   * Base factor for geometric quality when condition number indicates good geometry. Mathematical
   * meaning: Factor = 1.0 means no geometric penalty applied. Rationale: Good geometry (low
   * condition number) requires no accuracy adjustment. Used in: calculateGeometricQualityFactor for
   * excellent geometric conditions.
   */
  private static final double GOOD_GEOMETRY_BASE_FACTOR = 1.0;

  /**
   * Threshold factor for transitioning from moderate to poor geometric quality. Mathematical
   * meaning: Factor = 2.0 represents geometric quality degradation threshold. Rationale: Marks
   * point where geometric penalties become significant. Academic basis: Condition number analysis
   * suggests 2× factor for poor geometry transition.
   */
  private static final double GEOMETRIC_POOR_THRESHOLD_FACTOR = 2.0;

  /**
   * Minimum constraint value for various geometric calculations. Mathematical meaning: Lower bound
   * = 1.0 for normalized geometric factors. Rationale: Prevents geometric quality factors from
   * improving beyond baseline (1.0). Academic basis: Geometric dilution can only worsen, never
   * improve baseline precision.
   */
  private static final double GEOMETRIC_MIN_CONSTRAINT = 1.0;

  // ============================================================================
  // GDOP QUALITY THRESHOLDS
  // ============================================================================

  // GDOP quality thresholds
  public static final double EXCELLENT_GDOP =
      2.0; // GDOP values below this indicate excellent AP geometry
  public static final double GOOD_GDOP = 4.0; // GDOP values below this indicate good AP geometry
  public static final double FAIR_GDOP = 6.0; // GDOP values below this indicate fair AP geometry

  // GDOP scaling factors for confidence and accuracy
  public static final double GDOP_CONFIDENCE_WEIGHT =
      0.30; // Weight of GDOP in confidence calculation (0-1)
  public static final double GDOP_ACCURACY_MULTIPLIER =
      0.5; // How much GDOP affects accuracy estimation

  // Constants for geometric quality assessment in position combining
  public static final double COLLINEARITY_THRESHOLD =
      0.01; // Variance ratio below this is considered collinear
  public static final double SINGULARITY_THRESHOLD =
      1e-10; // Determinant below this is considered singular
  public static final double GOOD_GEOMETRY_THRESHOLD =
      5.0; // Condition numbers below this indicate good geometry
  public static final double MODERATE_GEOMETRY_THRESHOLD =
      20.0; // Values between 5-20 indicate moderate geometry
  public static final double CONDITION_NUMBER_SCALING_FACTOR =
      15.0; // For moderate geometry scaling
  public static final double POOR_GEOMETRY_SCALING_FACTOR = 80.0; // For poor geometry scaling
  public static final double CONDITION_NUMBER_NORMALIZATION =
      10.0; // Normalizes condition number for accuracy scaling

  // Constants for collinear AP handling
  public static final double COLLINEAR_BASE_FACTOR = 2.0; // Base factor for collinear geometry
  public static final double COLLINEAR_LOG_SCALE =
      2.0; // Log scale divider for collinear condition number
  public static final double COLLINEAR_CONFIDENCE_MULTIPLIER =
      1.2; // Stronger confidence reduction for collinearity

  /** Private constructor to prevent instantiation of utility class */
  private GDOPCalculator() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Calculates Geometric Dilution of Precision (GDOP) for given AP configuration.
   *
   * <p>GDOP Algorithm Overview: 1. Validate inputs and ensure sufficient APs 2. Convert geographic
   * coordinates to metric distances 3. Calculate unit vectors from position to each AP 4. Construct
   * geometry matrix H with unit vectors 5. Compute covariance matrix Q = (H^T × H)^(-1) 6.
   * Calculate GDOP = √(trace(Q))
   *
   * <p>Mathematical Foundation: The geometry matrix H contains unit vectors representing the
   * spatial relationship between the estimated position and each access point. GDOP quantifies how
   * small errors in distance measurements translate to errors in position estimation.
   *
   * @param coordinates Array of AP coordinates [latitude, longitude, altitude]
   * @param position Estimated position [latitude, longitude, altitude]
   * @param includeBiasTerm Whether to include bias term for clock error modeling
   * @return GDOP value (lower is better, capped at MAX_ALLOWED_GDOP)
   */
  public static double calculateGDOP(
      double[][] coordinates, double[] position, boolean includeBiasTerm) {
    // Phase 1: Input validation at high abstraction level
    if (!isValidGDOPInput(coordinates, position)) {
      return MAX_ALLOWED_GDOP;
    }

    try {
      // Phase 2: Create geometry matrix with proper dimensions
      double[][] geometryMatrix = createGeometryMatrix(coordinates, position, includeBiasTerm);

      // Phase 3: Perform matrix operations to get covariance matrix
      RealMatrix covarianceMatrix = calculateCovarianceMatrix(geometryMatrix);

      // Phase 4: Compute final GDOP from matrix trace
      return computeGDOPFromTrace(covarianceMatrix, coordinates.length);

    } catch (Exception e) {
      logger.debug("GDOP calculation failed: {}", e.getMessage());
      return MAX_ALLOWED_GDOP;
    }
  }

  /**
   * Validates input parameters for GDOP calculation.
   *
   * <p>Validation Rules: - Minimum number of APs required for geometry analysis - Position array
   * must be non-null with valid dimensions - All coordinate arrays must match position dimensions
   *
   * @param coordinates Array of AP coordinates
   * @param position Estimated position coordinates
   * @return true if inputs are valid for GDOP calculation
   */
  private static boolean isValidGDOPInput(double[][] coordinates, double[] position) {
    if (coordinates.length < MIN_APS_FOR_GDOP || position == null) {
      logger.debug(
          "Insufficient APs ({}) or null position for GDOP calculation", coordinates.length);
      return false;
    }

    int positionDimensions = position.length;
    for (double[] coordinate : coordinates) {
      if (coordinate.length < positionDimensions) {
        logger.warn(
            "Coordinate dimensions ({}) do not match position dimensions ({})",
            coordinate.length,
            positionDimensions);
        return false;
      }
    }

    return true;
  }

  /**
   * Creates the geometry matrix H containing unit vectors from position to each AP.
   *
   * <p>Matrix Structure: H = [u₁ᵀ; u₂ᵀ; ...; uₙᵀ] where uᵢ = (APᵢ - position) / ||APᵢ - position||
   *
   * <p>Optional bias term augments matrix: H = [u₁ᵀ 1; u₂ᵀ 1; ...; uₙᵀ 1]
   *
   * @param coordinates Array of AP coordinates
   * @param position Estimated position
   * @param includeBiasTerm Whether to include bias column
   * @return Geometry matrix as double array
   */
  private static double[][] createGeometryMatrix(
      double[][] coordinates, double[] position, boolean includeBiasTerm) {
    int numAPs = coordinates.length;
    int dimensions = position.length;
    int matrixCols = includeBiasTerm ? dimensions + 1 : dimensions;

    double[][] geometryMatrix = new double[numAPs][matrixCols];

    for (int i = 0; i < numAPs; i++) {
      // Convert coordinates to metric distances and calculate unit vector
      double[] unitVector = calculateUnitVectorToAP(coordinates[i], position);

      // Populate geometry matrix row
      System.arraycopy(unitVector, 0, geometryMatrix[i], 0, dimensions);

      // Add bias term if requested
      if (includeBiasTerm) {
        geometryMatrix[i][matrixCols - 1] = BIAS_TERM_VALUE;
      }
    }

    return geometryMatrix;
  }

  /**
   * Calculates unit vector from estimated position to access point.
   *
   * <p>Mathematical Process: 1. Convert lat/lon differences to metric distances using Earth's
   * radius 2. Calculate Euclidean distance in 3D space 3. Normalize vector to unit length: û = Δr /
   * ||Δr||
   *
   * <p>Coordinate Conversion: - Latitude difference: Δlat × DEGREES_LAT_TO_METERS - Longitude
   * difference: Δlon × DEGREES_LAT_TO_METERS × cos(latitude) - Altitude difference: Δalt (already
   * in meters)
   *
   * @param apCoordinate AP position [lat, lon, alt]
   * @param position Estimated position [lat, lon, alt]
   * @return Unit vector components [ux, uy, uz]
   */
  private static double[] calculateUnitVectorToAP(double[] apCoordinate, double[] position) {
    int dimensions = position.length;

    // Convert geographic coordinates to metric distances
    MetricDistances distances = convertCoordinatesToMeters(apCoordinate, position);

    // Calculate Euclidean distance
    double euclideanDistance =
        Math.sqrt(
            distances.dx() * distances.dx()
                + distances.dy() * distances.dy()
                + distances.dz() * distances.dz());

    // Handle case where AP is too close to estimated position
    if (euclideanDistance < MIN_DISTANCE_THRESHOLD_METERS) {
      return createDefaultUnitVector(dimensions);
    }

    // Calculate and return normalized unit vector
    return normalizeVector(distances, euclideanDistance, dimensions);
  }

  /**
   * Converts geographic coordinate differences to metric distances.
   *
   * <p>Conversion Formulas: - dx = (lat_AP - lat_pos) × DEGREES_LAT_TO_METERS - dy = (lon_AP -
   * lon_pos) × DEGREES_LAT_TO_METERS × cos(lat_pos) - dz = alt_AP - alt_pos (if 3D positioning)
   *
   * <p>Mathematical Basis: Uses spherical Earth model with WGS84 radius for accurate distance
   * conversion. Longitude scaling accounts for meridian convergence at different latitudes.
   *
   * @param apCoordinate AP coordinates [lat, lon, alt]
   * @param position Estimated position [lat, lon, alt]
   * @return Metric distances as record
   */
  private static MetricDistances convertCoordinatesToMeters(
      double[] apCoordinate, double[] position) {
    double dx =
        (apCoordinate[X_COORDINATE_INDEX] - position[X_COORDINATE_INDEX]) * DEGREES_LAT_TO_METERS;

    // Longitude conversion factor varies with latitude (spherical Earth model)
    double lonToMeters =
        DEGREES_LAT_TO_METERS * Math.cos(Math.toRadians(position[X_COORDINATE_INDEX]));
    double dy = (apCoordinate[Y_COORDINATE_INDEX] - position[Y_COORDINATE_INDEX]) * lonToMeters;

    double dz =
        position.length > MIN_2D_DIMENSIONS
            ? apCoordinate[Z_COORDINATE_INDEX] - position[Z_COORDINATE_INDEX]
            : ZERO_VALUE;

    return new MetricDistances(dx, dy, dz);
  }

  /**
   * Creates default unit vector for APs too close to estimated position. Prevents numerical
   * instability in GDOP calculation.
   *
   * <p>Default Vector Structure: 2D: [1, 0] - Points along positive X-axis (eastward) 3D: [1, 0, 0]
   * - Points along positive X-axis with zero altitude component
   *
   * <p>Mathematical Rationale: Unit vector [1,0,0] provides stable geometric contribution without
   * introducing bias in any particular direction.
   *
   * @param dimensions Number of spatial dimensions
   * @return Default unit vector [1, 0, 0, ...]
   */
  private static double[] createDefaultUnitVector(int dimensions) {
    double[] unitVector = new double[dimensions];
    unitVector[X_COORDINATE_INDEX] = DEFAULT_UNIT_VECTOR_X;
    if (dimensions > MIN_2D_DIMENSIONS - 1) unitVector[Y_COORDINATE_INDEX] = DEFAULT_UNIT_VECTOR_Y;
    if (dimensions > MIN_3D_DIMENSIONS - 1) unitVector[Z_COORDINATE_INDEX] = DEFAULT_UNIT_VECTOR_Z;
    return unitVector;
  }

  /**
   * Normalizes distance vector to unit length.
   *
   * <p>Normalization Formula: û = Δr / ||Δr|| where ||Δr|| is the Euclidean norm of the distance
   * vector.
   *
   * <p>Vector Components: - X component: dx / euclideanDistance (latitude direction) - Y component:
   * dy / euclideanDistance (longitude direction) - Z component: dz / euclideanDistance (altitude
   * direction, if 3D)
   *
   * @param distances Metric distances between points
   * @param euclideanDistance Euclidean norm of distance vector
   * @param dimensions Number of spatial dimensions
   * @return Normalized unit vector
   */
  private static double[] normalizeVector(
      MetricDistances distances, double euclideanDistance, int dimensions) {
    double[] unitVector = new double[dimensions];
    unitVector[X_COORDINATE_INDEX] = distances.dx() / euclideanDistance;
    if (dimensions > MIN_2D_DIMENSIONS - 1)
      unitVector[Y_COORDINATE_INDEX] = distances.dy() / euclideanDistance;
    if (dimensions > MIN_3D_DIMENSIONS - 1)
      unitVector[Z_COORDINATE_INDEX] = distances.dz() / euclideanDistance;
    return unitVector;
  }

  /**
   * Calculates covariance matrix Q = (H^T × H)^(-1) for GDOP computation.
   *
   * <p>Matrix Operations: 1. Create Apache Commons Math RealMatrix from geometry matrix 2. Compute
   * H^T (transpose of geometry matrix) 3. Calculate H^T × H (normal equations matrix) 4. Compute
   * (H^T × H)^(-1) using QR decomposition
   *
   * <p>Error Handling: - Returns null if matrix is singular (non-invertible) - Uses QR
   * decomposition for numerical stability
   *
   * @param geometryMatrix The H matrix containing unit vectors
   * @return Covariance matrix or null if singular
   */
  private static RealMatrix calculateCovarianceMatrix(double[][] geometryMatrix) {
    RealMatrix H = new Array2DRowRealMatrix(geometryMatrix);
    RealMatrix HT = H.transpose();
    RealMatrix HTH = HT.multiply(H);

    DecompositionSolver solver = new QRDecomposition(HTH).getSolver();

    if (!solver.isNonSingular()) {
      logger.debug("Geometry matrix is singular, cannot compute covariance matrix");
      return null;
    }

    return solver.getInverse();
  }

  /**
   * Computes final GDOP value from covariance matrix trace.
   *
   * <p>GDOP Formula: GDOP = √(trace(Q)) where Q is the covariance matrix and trace(Q) = Σ(diagonal
   * elements)
   *
   * <p>Mathematical Significance: The trace represents the sum of variances along coordinate axes.
   * Square root converts variance measure to standard deviation scale.
   *
   * <p>Numerical Safety: - Ensures trace is non-negative using max(ZERO_VALUE, trace) - Caps result
   * at MAX_ALLOWED_GDOP to prevent numerical overflow
   *
   * <p>Academic Reference: This follows standard GDOP calculation methods from satellite navigation
   * theory as described in Kaplan & Hegarty "Understanding GPS".
   *
   * @param covarianceMatrix The Q matrix from (H^T × H)^(-1)
   * @param numAPs Number of access points (for logging)
   * @return GDOP value capped at maximum allowed value
   */
  private static double computeGDOPFromTrace(RealMatrix covarianceMatrix, int numAPs) {
    if (covarianceMatrix == null) {
      return MAX_ALLOWED_GDOP;
    }

    double trace = Math.max(ZERO_VALUE, covarianceMatrix.getTrace());
    double gdop = Math.sqrt(trace);

    logger.debug("Calculated GDOP = {} for {} APs", gdop, numAPs);

    return Math.min(MAX_ALLOWED_GDOP, gdop);
  }

  /**
   * Immutable record representing metric distances between two geographic points.
   *
   * @param dx Distance difference in X direction (latitude, meters)
   * @param dy Distance difference in Y direction (longitude, meters)
   * @param dz Distance difference in Z direction (altitude, meters)
   */
  private record MetricDistances(double dx, double dy, double dz) {

    /**
     * Validates that distance components are finite numbers.
     *
     * @throws IllegalArgumentException if any component is infinite or NaN
     */
    public MetricDistances {
      if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) {
        throw new IllegalArgumentException("Distance components must be finite numbers");
      }
    }
  }

  /**
   * Calculates a GDOP factor for adjusting accuracy and confidence values. Converts raw GDOP values
   * to usable scaling factors based on geometric quality.
   *
   * <p>The transformation follows a piecewise linear function with these segments: 1. Excellent
   * geometry (GDOP ≤ 2): Factor = 1.0 (no adjustment) 2. Good geometry (2 < GDOP ≤ 4): Factor
   * ranges from 1.0 to 1.5 3. Fair geometry (4 < GDOP ≤ 6): Factor ranges from 1.5 to 2.0 4. Poor
   * geometry (GDOP > 6): Factor ranges from 2.0 up to 4.0
   *
   * <p>This creates a continuous function where: - Better geometry (lower GDOP) results in factors
   * closer to 1.0 - Poorer geometry (higher GDOP) results in larger factors
   *
   * <p>The resulting factor is used to: 1. Scale accuracy values (higher factor = worse accuracy)
   * 2. Adjust confidence values (higher factor = lower confidence)
   *
   * <p>Mathematical Formula: For excellent geometry: factor = EXCELLENT_GEOMETRY_BASE_FACTOR For
   * good geometry: factor = EXCELLENT_GEOMETRY_BASE_FACTOR + GOOD_GEOMETRY_INCREMENT_STEP × ((gdop
   * - EXCELLENT_GDOP) / (GOOD_GDOP - EXCELLENT_GDOP)) For fair geometry: factor =
   * GOOD_TO_FAIR_THRESHOLD_FACTOR + GOOD_GEOMETRY_INCREMENT_STEP × ((gdop - GOOD_GDOP) / (FAIR_GDOP
   * - GOOD_GDOP)) For poor geometry: factor = min(MAX_GDOP_FACTOR_CAP, POOR_GEOMETRY_BASE_FACTOR +
   * (gdop - FAIR_GDOP) / POOR_GDOP_SCALING_DIVISOR)
   *
   * @param gdop Raw GDOP value as calculated from the geometry matrix
   * @return Scaling factor for accuracy/confidence adjustments (range: 1.0 to 4.0)
   */
  public static double calculateGDOPFactor(double gdop) {
    if (gdop <= EXCELLENT_GDOP) {
      // Excellent geometry, minimal adjustment
      return EXCELLENT_GEOMETRY_BASE_FACTOR;
    } else if (gdop <= GOOD_GDOP) {
      // Good geometry, small adjustment
      double factor =
          EXCELLENT_GEOMETRY_BASE_FACTOR
              + GOOD_GEOMETRY_INCREMENT_STEP
                  * ((gdop - EXCELLENT_GDOP) / (GOOD_GDOP - EXCELLENT_GDOP));
      return factor;
    } else if (gdop <= FAIR_GDOP) {
      // Fair geometry, moderate adjustment
      double factor =
          GOOD_TO_FAIR_THRESHOLD_FACTOR
              + GOOD_GEOMETRY_INCREMENT_STEP * ((gdop - GOOD_GDOP) / (FAIR_GDOP - GOOD_GDOP));
      return factor;
    } else {
      // Poor geometry, significant adjustment
      double factor = POOR_GEOMETRY_BASE_FACTOR + (gdop - FAIR_GDOP) / POOR_GDOP_SCALING_DIVISOR;
      return Math.min(MAX_GDOP_FACTOR_CAP, factor); // Cap at maximum allowed factor
    }
  }

  /**
   * Calculates the condition number of a covariance matrix, which is a key indicator of geometric
   * quality. Higher condition numbers indicate poor geometry.
   *
   * <p>Mathematical formula: 1. Calculate the eigenvalues (λ1, λ2) of the covariance matrix using:
   * λ1,λ2 = (trace ± √[(trace)² - EIGENVALUE_DETERMINANT_COEFFICIENT×det]) /
   * EIGENVALUE_CALCULATION_DIVISOR where: - trace = covLatLat + covLonLon - det =
   * covLatLat×covLonLon - covLatLon² - EIGENVALUE_DETERMINANT_COEFFICIENT = 4 (from quadratic
   * formula) - EIGENVALUE_CALCULATION_DIVISOR = 2 (from quadratic formula)
   *
   * <p>2. The condition number is: κ = |λmax| / |λmin|
   *
   * <p>The condition number measures how "stretched" the error ellipse is. For excellent geometry,
   * κ ≈ 1 For poor geometry (like collinearity), κ >> 1
   *
   * <p>Academic Reference: This follows the standard eigenvalue decomposition method for symmetric
   * 2×2 matrices as described in linear algebra texts (e.g., Strang's "Linear Algebra").
   *
   * @param covLatLat Covariance of first dimension with itself
   * @param covLonLon Covariance of second dimension with itself
   * @param covLatLon Cross-covariance between first and second dimensions
   * @return The condition number of the covariance matrix
   */
  public static double calculateConditionNumber(
      double covLatLat, double covLonLon, double covLatLon) {
    // Calculate trace and determinant of the covariance matrix
    double trace = covLatLat + covLonLon;
    double determinant = covLatLat * covLonLon - covLatLon * covLatLon;

    // Ensure determinant is not too close to zero to avoid numerical issues
    if (Math.abs(determinant) < SINGULARITY_THRESHOLD) {
      // Near-singular matrix indicates very poor geometry
      return Double.MAX_VALUE;
    }

    // Calculate discriminant for eigenvalue computation
    // Formula: discriminant = trace² - 4×determinant (from quadratic formula)
    double discriminant = trace * trace - EIGENVALUE_DETERMINANT_COEFFICIENT * determinant;

    // If discriminant is negative, the matrix has complex eigenvalues
    // This shouldn't happen with a covariance matrix (which is positive semi-definite)
    if (discriminant < ZERO_VALUE) {
      return Double.MAX_VALUE;
    }

    // Calculate eigenvalues using quadratic formula
    // λ₁,λ₂ = (trace ± √discriminant) / 2
    double sqrtDiscriminant = Math.sqrt(discriminant);
    double lambda1 = (trace + sqrtDiscriminant) / EIGENVALUE_CALCULATION_DIVISOR;
    double lambda2 = (trace - sqrtDiscriminant) / EIGENVALUE_CALCULATION_DIVISOR;

    // Condition number is ratio of largest to smallest eigenvalue
    return Math.abs(lambda1) / Math.max(Math.abs(lambda2), SINGULARITY_THRESHOLD);
  }

  /**
   * Calculates a geometric quality factor based on the condition number and collinearity. The
   * factor represents how much the accuracy should be inflated due to poor geometry.
   *
   * <p>Mathematical approach: For collinear cases: factor = COLLINEAR_BASE_FACTOR +
   * min(GEOMETRIC_MIN_CONSTRAINT, log10(conditionNumber)/COLLINEAR_LOG_SCALE)
   *
   * <p>For non-collinear cases, piecewise function: - Good geometry (κ < GOOD_GEOMETRY_THRESHOLD):
   * factor = GOOD_GEOMETRY_BASE_FACTOR - Moderate geometry (GOOD_GEOMETRY_THRESHOLD ≤ κ <
   * MODERATE_GEOMETRY_THRESHOLD): factor = GOOD_GEOMETRY_BASE_FACTOR + (κ -
   * GOOD_GEOMETRY_THRESHOLD)/CONDITION_NUMBER_SCALING_FACTOR - Poor geometry (κ ≥
   * MODERATE_GEOMETRY_THRESHOLD): factor = GEOMETRIC_POOR_THRESHOLD_FACTOR +
   * min(GEOMETRIC_MIN_CONSTRAINT, (κ - MODERATE_GEOMETRY_THRESHOLD)/POOR_GEOMETRY_SCALING_FACTOR)
   *
   * <p>Where: - κ is the condition number - The thresholds and scales are constants defining the
   * geometry quality ranges - GEOMETRIC_MIN_CONSTRAINT prevents factors from improving beyond
   * baseline
   *
   * <p>Academic Basis: The logarithmic scaling for collinear cases follows principles from
   * numerical analysis where condition numbers can grow exponentially with geometric degradation.
   *
   * @param conditionNumber Condition number of the covariance matrix
   * @param isCollinear Whether the positions are determined to be collinear
   * @return Geometric quality factor (1.0 for good geometry, >1.0 for poor geometry)
   */
  public static double calculateGeometricQualityFactor(
      double conditionNumber, boolean isCollinear) {
    if (isCollinear) {
      // For collinear cases, use a more aggressive scaling factor
      // Using logarithmic scaling to handle potentially very large condition numbers
      return COLLINEAR_BASE_FACTOR
          + Math.min(GEOMETRIC_MIN_CONSTRAINT, Math.log10(conditionNumber) / COLLINEAR_LOG_SCALE);
    } else {
      // For non-collinear cases, use standard geometric quality assessment
      if (conditionNumber < GOOD_GEOMETRY_THRESHOLD) {
        // Good geometry
        return GOOD_GEOMETRY_BASE_FACTOR;
      } else if (conditionNumber < MODERATE_GEOMETRY_THRESHOLD) {
        // Moderately poor geometry
        return GOOD_GEOMETRY_BASE_FACTOR
            + (conditionNumber - GOOD_GEOMETRY_THRESHOLD) / CONDITION_NUMBER_SCALING_FACTOR;
      } else {
        // Very poor geometry
        return GEOMETRIC_POOR_THRESHOLD_FACTOR
            + Math.min(
                GEOMETRIC_MIN_CONSTRAINT,
                (conditionNumber - MODERATE_GEOMETRY_THRESHOLD) / POOR_GEOMETRY_SCALING_FACTOR);
      }
    }
  }

  /**
   * Calculates statistical covariance matrix for position estimate distribution. This method
   * computes the covariance matrix of position estimates from different algorithms to assess the
   * geometric quality and agreement of positioning results.
   *
   * <p>MATHEMATICAL FOUNDATION: Statistical covariance quantifies how position estimates vary
   * together: Cov(X,Y) = (1/n) * Σ[(Xi - X_mean)(Yi - Y_mean)]
   *
   * <p>The resulting covariance matrix has the form: [Cov(lat,lat) Cov(lat,lon)] [Cov(lon,lat)
   * Cov(lon,lon)]
   *
   * <p>GEOMETRIC INTERPRETATION: - High covariance values indicate wide spread of position
   * estimates - Low covariance values indicate tight clustering of estimates - Cross-covariance
   * reveals correlation between latitude and longitude errors
   *
   * <p>ACADEMIC BASIS: This follows standard statistical covariance calculation from: - Johnson,
   * R.A. & Wichern, D.W. (2007). "Applied Multivariate Statistical Analysis" - Anderson, T.W.
   * (2003). "An Introduction to Multivariate Statistical Analysis"
   *
   * <p>Used for: 1. Condition number calculation for geometric quality assessment 2. Algorithm
   * agreement analysis 3. Confidence adjustment based on estimate consistency
   *
   * @param latitudes Array of latitude estimates from different algorithms
   * @param longitudes Array of longitude estimates from different algorithms
   * @param meanLat Mean latitude of all position estimates
   * @param meanLon Mean longitude of all position estimates
   * @return Array containing [covLatLat, covLonLon, covLatLon] covariance matrix elements
   * @throws IllegalArgumentException if arrays have different lengths or are empty
   */
  public static double[] calculatePositionCovarianceMatrix(
      double[] latitudes, double[] longitudes, double meanLat, double meanLon) {
    // Input validation
    if (latitudes.length != longitudes.length) {
      throw new IllegalArgumentException("Latitude and longitude arrays must have the same length");
    }
    if (latitudes.length == 0) {
      throw new IllegalArgumentException("Position arrays cannot be empty");
    }

    double covLatLat = 0, covLonLon = 0, covLatLon = 0;
    int n = latitudes.length;

    // Calculate covariance matrix elements
    for (int i = 0; i < n; i++) {
      double latDiff = latitudes[i] - meanLat;
      double lonDiff = longitudes[i] - meanLon;

      covLatLat += latDiff * latDiff;
      covLonLon += lonDiff * lonDiff;
      covLatLon += latDiff * lonDiff; // Cross-covariance
    }

    // Normalize by sample size
    covLatLat /= n;
    covLonLon /= n;
    covLatLon /= n;

    logger.debug(
        "Position covariance matrix: [{}, {}; {}, {}]", covLatLat, covLatLon, covLatLon, covLonLon);

    return new double[] {covLatLat, covLonLon, covLatLon};
  }
}
