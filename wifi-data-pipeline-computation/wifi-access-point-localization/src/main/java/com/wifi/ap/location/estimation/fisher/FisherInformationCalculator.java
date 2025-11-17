// wifi-data-pipeline-computation/wifi-access-point-localization/src/main/java/com/wifi/ap/location/estimation/fisher/FisherInformationCalculator.java
package com.wifi.ap.location.estimation.fisher;

import com.wifi.ap.location.Location;
import com.wifi.ap.location.estimation.WiFiFrequencyBand;
import com.wifi.ap.location.estimation.state.CovarianceMatrix;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.linear.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <h1>Fisher Information Matrix Calculator for WiFi Access Point Localization</h1>
 * 
 * <p>This utility class provides statistically rigorous Fisher Information Matrix (FIM) calculations
 * for WiFi access point localization using crowdsourced RSSI (Received Signal Strength Indicator) measurements.
 * It serves as a centralized implementation ensuring consistency across multiple localization algorithms.
 * 
 * <h2>Table of Contents</h2>
 * <ol>
 *   <li><a href="#understanding-mle">Understanding Maximum Likelihood Estimation (MLE)</a></li>
 *   <li><a href="#fisher-information">Fisher Information Matrix: What and Why</a></li>
 *   <li><a href="#mathematical-foundation">Mathematical Foundation</a></li>
 *   <li><a href="#calculation-methods">Two Calculation Methods</a></li>
 *   <li><a href="#wifi-specific">WiFi-Specific Application</a></li>
 *   <li><a href="#research-sources">Research Sources and Citations</a></li>
 * </ol>
 * 
 * <h2 id="understanding-mle">1. Understanding Maximum Likelihood Estimation (MLE)</h2>
 * 
 * <p><strong>What is MLE?</strong>
 * <p>Maximum Likelihood Estimation is a method for finding the "best guess" of an unknown parameter
 * (like an AP's location) by finding the value that makes your observed measurements most probable.
 * 
 * <p><strong>Simple Example:</strong>
 * <p>Imagine you measure WiFi signal strengths at different locations. MLE finds the AP location that
 * would most likely produce the signal strengths you actually measured. It's like working backwards from
 * effects (RSSI measurements) to find the cause (AP location).
 * 
 * <p><strong>Why Use MLE for WiFi Localization?</strong>
 * <ul>
 *   <li><strong>Physics-Based:</strong> Uses the log-distance path loss model (signal weakens with distance)</li>
 *   <li><strong>Statistically Optimal:</strong> Under Gaussian noise, MLE provides best possible accuracy</li>
 *   <li><strong>Handles Uncertainty:</strong> Automatically accounts for measurement noise and GPS accuracy</li>
 *   <li><strong>Uncertainty Quantification:</strong> Tells us not just location, but how confident we are</li>
 * </ul>
 * 
 * <p><strong>MLE Process for WiFi:</strong>
 * <ol>
 *   <li><strong>Model:</strong> RSSI = ReferenceP power - 10×n×log₁₀(distance) + noise</li>
 *   <li><strong>Likelihood:</strong> Probability of observing our RSSI measurements given AP location</li>
 *   <li><strong>Optimization:</strong> Find AP location that maximizes this probability</li>
 *   <li><strong>Result:</strong> Best location estimate plus uncertainty bounds (this class's job!)</li>
 * </ol>
 * 
 * <h2 id="fisher-information">2. Fisher Information Matrix: What and Why</h2>
 * 
 * <p><strong>What is Fisher Information?</strong>
 * <p>Fisher Information measures "how much information" your measurements contain about the parameter
 * you're trying to estimate. More information = more confidence = smaller uncertainty.
 * 
 * <p><strong>Key Insight:</strong>
 * <p>The Fisher Information Matrix tells us the theoretical best accuracy we can achieve. No algorithm
 * can do better than this bound (Cramér-Rao Lower Bound).
 * 
 * <p><strong>Why Matrix?</strong>
 * <p>We're estimating 2D location (latitude, longitude), so we need a 2×2 matrix to capture:
 * <ul>
 *   <li><strong>Diagonal elements:</strong> Uncertainty in each direction independently</li>
 *   <li><strong>Off-diagonal elements:</strong> Correlation between lat/lon errors</li>
 * </ul>
 * 
 * <p><strong>The Magic Relationship:</strong>
 * <pre>
 * Covariance Matrix = (Fisher Information Matrix)⁻¹
 * 
 * In other words:
 * - High information → Small covariance → High accuracy
 * - Low information → Large covariance → Low accuracy
 * </pre>
 * 
 * <h2 id="mathematical-foundation">3. Mathematical Foundation</h2>
 * 
 * <p><strong>3.1 The Fisher Information Matrix Formula:</strong>
 * <pre>
 * J = Σᵢ (1/σᵢ²) × ∇h(θ) × ∇h(θ)ᵀ
 * 
 * Where:
 * - J = Fisher Information Matrix (2×2 for 2D location)
 * - Σᵢ = sum over all measurements i
 * - σᵢ² = noise variance for measurement i (16 dBm² for CONNECTED, 64 dBm² for SCAN)
 * - ∇h(θ) = gradient of signal strength model with respect to location
 * - θ = [latitude, longitude]ᵀ = parameters we're estimating
 * - ×ᵀ = outer product (creates 2×2 matrix from 2×1 vector)
 * </pre>
 * 
 * <p><strong>Breaking It Down:</strong>
 * <ul>
 *   <li><strong>1/σᵢ²:</strong> Measurements with less noise contribute more information</li>
 *   <li><strong>∇h(θ):</strong> How sensitive signal strength is to location changes</li>
 *   <li><strong>Outer product:</strong> Combines horizontal and vertical sensitivity</li>
 *   <li><strong>Sum:</strong> Total information from all measurements</li>
 * </ul>
 * 
 * <p><strong>3.2 WiFi Path Loss Model:</strong>
 * <pre>
 * RSSI(d) = P_ref - 10 × n × log₁₀(d/d₀) + noise
 * 
 * Where:
 * - RSSI = Received Signal Strength Indicator (measured in dBm)
 * - P_ref = Reference transmit power at d₀ (frequency-specific, ~13 dBm for 2.4 GHz)
 * - n = Path loss exponent (environment-specific, typically 2.1 for mixed indoor/outdoor)
 * - d = Distance from AP to measurement point (meters)
 * - d₀ = Reference distance (1 meter)
 * - noise = Gaussian random noise (σ=4 dBm for CONNECTED, σ=8 dBm for SCAN)
 * </pre>
 * 
 * <p><strong>3.3 Path Loss Gradient (Analytical Method):</strong>
 * <pre>
 * ∇h(θ) = -10n/ln(10) × (θ - mᵢ) / ||θ - mᵢ||²
 * 
 * In components:
 * ∂h/∂lat = -10n/ln(10) × (lat_AP - lat_measurement) / distance²
 * ∂h/∂lon = -10n/ln(10) × (lon_AP - lon_measurement) / distance²
 * 
 * Where:
 * - θ = [lat_AP, lon_AP]ᵀ = AP location we're estimating
 * - mᵢ = [lat_i, lon_i]ᵀ = measurement i location
 * - ||θ - mᵢ|| = Haversine distance between AP and measurement (accounts for Earth curvature)
 * - n = path loss exponent (2.1 for 2.4 GHz, 2.5 for 5 GHz)
 * - ln(10) ≈ 2.3026 (natural logarithm of 10, for base conversion)
 * </pre>
 * 
 * <p><strong>Physical Interpretation:</strong> The gradient tells us "if I move the AP 1 degree north,
 * how much does the signal strength change at this measurement point?" Steeper gradient = more information.
 * 
 * <p><strong>3.4 Hessian Matrix (Numerical Method):</strong>
 * <p>For MLE, we approximate the Hessian (matrix of second derivatives) of the negative log-likelihood:
 * <pre>
 * H = [[∂²L/∂lat², ∂²L/∂lat∂lon],
 *      [∂²L/∂lon∂lat, ∂²L/∂lon²]]
 * 
 * Where L(θ) is the negative log-likelihood function
 * 
 * Using central finite differences with step size h:
 * ∂²L/∂lat² ≈ (L(lat+h, lon) - 2L(lat, lon) + L(lat-h, lon)) / h²
 * ∂²L/∂lon² ≈ (L(lat, lon+h) - 2L(lat, lon) + L(lat, lon-h)) / h²
 * ∂²L/∂lat∂lon ≈ (L(lat+h,lon+h) - L(lat+h,lon-h) - L(lat-h,lon+h) + L(lat-h,lon-h)) / (4h²)
 * 
 * Step size h = ε^(1/3) ≈ 6.055×10⁻⁶ where ε = machine epsilon
 * (Optimal balance between truncation error and roundoff error)
 * </pre>
 * 
 * <p><strong>3.5 Covariance Matrix and Accuracy:</strong>
 * <pre>
 * Σ = J⁻¹  (Covariance = inverse of Fisher Information)
 * 
 * Σ = [[σ²_lat,    σ_lat,lon],
 *      [σ_lon,lat, σ²_lon  ]]
 * 
 * Standard errors:
 * σ_lat = √(Σ₁₁) = standard deviation in latitude
 * σ_lon = √(Σ₂₂) = standard deviation in longitude
 * 
 * Horizontal accuracy (95% confidence):
 * accuracy = 1.96 × √(σ²_lat + σ²_lon)
 * 
 * Where:
 * - 1.96 = z-score for 95% confidence interval
 * - √(σ²_lat + σ²_lon) = RSS (root sum square) of standard deviations
 * </pre>
 * 
 * <h2 id="calculation-methods">4. Two Calculation Methods</h2>
 * 
 * <p><strong>4.1 Gradient-Based Method (Analytical)</strong>
 * <p><strong>Used by:</strong> Sequential Bayesian Update
 * <p><strong>When:</strong> When you have a mathematical model and can compute gradients analytically
 * <p><strong>Advantage:</strong> Exact gradients, computationally efficient
 * <p><strong>Steps:</strong>
 * <ol>
 *   <li>For each measurement, calculate path loss gradient ∇h(θ)</li>
 *   <li>Compute outer product: ∇h × ∇hᵀ (creates 2×2 matrix)</li>
 *   <li>Weight by inverse noise variance: (1/σ²) × matrix</li>
 *   <li>Sum all measurement contributions: J = Σ (weighted matrices)</li>
 *   <li>Invert J to get covariance: Σ = J⁻¹</li>
 * </ol>
 * 
 * <p><strong>4.2 Hessian-Based Method (Numerical)</strong>
 * <p><strong>Used by:</strong> Maximum Likelihood Estimation accuracy calculation
 * <p><strong>When:</strong> After MLE optimization finds the optimal location
 * <p><strong>Advantage:</strong> Works with any objective function, captures all nonlinear effects
 * <p><strong>Steps:</strong>
 * <ol>
 *   <li>Take the optimized objective function from MLE</li>
 *   <li>Numerically approximate Hessian using finite differences</li>
 *   <li>Hessian ≈ Fisher Information Matrix (at optimal point)</li>
 *   <li>Invert Hessian to get covariance: Σ = H⁻¹</li>
 *   <li>Extract standard errors from diagonal: σ = √(diagonal elements)</li>
 * </ol>
 * 
 * <h2 id="wifi-specific">5. WiFi-Specific Application</h2>
 * 
 * <p><strong>5.1 Noise Models (Research-Backed)</strong>
 * <pre>
 * CONNECTED measurements (active WiFi connection):
 * - Standard deviation: σ = 4.0 dBm
 * - Variance: σ² = 16.0 dBm²
 * - Precision (inverse variance): 1/σ² = 0.0625 dBm⁻²
 * - Rationale: Active connection has signal quality feedback, more stable
 * 
 * SCAN measurements (passive WiFi scan):
 * - Standard deviation: σ = 8.0 dBm  
 * - Variance: σ² = 64.0 dBm²
 * - Precision (inverse variance): 1/σ² = 0.015625 dBm⁻²
 * - Rationale: Passive scan without connection, less stable, 2× higher noise
 * 
 * Weight ratio: CONNECTED measurements contribute 4× more information than SCAN
 * 
 * Research sources:
 * - Olsson & Öhrström (2017): "The precision of RSSI-fingerprinting based on connected Wi-Fi devices"
 * - PMC 7472118 (2020): "Comparison of 2.4 GHz WiFi FTM- and RSSI-Based Indoor Positioning"
 * - PMC 7436166: "An RSSI Classification and Tracing Algorithm to Improve Trilateration-Based Positioning"
 * </pre>
 * 
 * <p><strong>5.2 Frequency-Specific Parameters</strong>
 * <pre>
 * 2.4 GHz Band:
 * - Path loss exponent: n = 2.1 (typical mixed indoor/outdoor)
 * - Reference power: P_ref ≈ 13 dBm (ETSI/FCC regulatory standards)
 * - Most common WiFi band, better range, more congestion
 * 
 * 5 GHz Band:
 * - Path loss exponent: n = 2.5 (higher attenuation)
 * - Reference power: P_ref ≈ 13 dBm (regulatory standards)
 * - Less congestion, faster speeds, shorter range
 * 
 * Research source: Obeidat et al. (2018) for path loss exponents
 * </pre>
 * 
 * <p><strong>5.3 Practical Accuracy Bounds</strong>
 * <pre>
 * Minimum accuracy: 3.0 meters
 * - Physical limit due to WiFi signal propagation characteristics
 * - Below this, GPS accuracy dominates positioning error
 * 
 * Maximum accuracy: 100.0 meters
 * - Indicates poor measurement geometry or insufficient data quality
 * - Used as fallback when FIM calculation fails
 * 
 * Source: WiFi positioning literature consensus, empirical studies
 * </pre>
 * 
 * <h2 id="research-sources">6. Research Sources and Citations</h2>
 * 
 * <p><strong>6.1 Core Statistical Theory</strong>
 * <ul>
 *   <li><strong>Kay, Steven M.</strong> (1993). "Fundamentals of Statistical Signal Processing, 
 *       Volume I: Estimation Theory." Prentice Hall.
 *       <br>→ Chapter 3: Fisher Information Matrix theory and Cramér-Rao Lower Bound</li>
 *   
 *   <li><strong>Van Trees, Harry L.</strong> (2001). "Detection, Estimation, and Modulation Theory, Part I."
 *       John Wiley & Sons.
 *       <br>→ Comprehensive treatment of estimation theory and CRLB applications</li>
 *   
 *   <li><strong>Cover, Thomas M. & Thomas, Joy A.</strong> (2006). "Elements of Information Theory" (2nd ed.).
 *       Wiley-Interscience.
 *       <br>→ Information gain and entropy measures</li>
 * </ul>
 * 
 * <p><strong>6.2 WiFi Localization</strong>
 * <ul>
 *   <li><strong>Rappaport, Theodore S.</strong> (2001). "Wireless Communications: Principles and Practice" (2nd ed.).
 *       Prentice Hall.
 *       <br>→ Chapter 4: Path loss models and signal propagation</li>
 *   
 *   <li><strong>Patwari, Neal et al.</strong> (2005). "Locating the nodes: cooperative localization in
 *       wireless sensor networks." IEEE Signal Processing Magazine, 22(4), 54-69.
 *       <br>→ RSSI-based MLE for wireless localization</li>
 *   
 *   <li><strong>Gezici, Sinan et al.</strong> (2008). "Localization via Ultra-Wideband Radios: A Look at
 *       Positioning Aspects." IEEE Signal Processing Magazine, 25(4), 70-84.
 *       <br>→ Theoretical accuracy bounds for wireless positioning</li>
 *   
 *   <li><strong>Wymeersch, Henk et al.</strong> (2009). "Cooperative Localization in Wireless Networks."
 *       Proceedings of the IEEE, 97(2), 427-450.
 *       <br>→ FIM applications in cooperative positioning</li>
 * </ul>
 * 
 * <p><strong>6.3 RSSI Noise Characterization</strong>
 * <ul>
 *   <li><strong>Olsson, Marcus & Öhrström, Mattias</strong> (2017). "The precision of RSSI-fingerprinting
 *       based on connected Wi-Fi devices." IEEE Conference.
 *       <br>→ RSSI stability improves with connected device activity</li>
 *   
 *   <li><strong>PMC 7472118</strong> (2020). "Comparison of 2.4 GHz WiFi FTM- and RSSI-Based Indoor Positioning."
 *       <br>→ RSSI noise variance 2-15 dBm range, environment-specific tuning</li>
 *   
 *   <li><strong>PMC 7436166</strong>. "An RSSI Classification and Tracing Algorithm to Improve
 *       Trilateration-Based Positioning."
 *       <br>→ Empirical validation of 4-8 dBm noise range</li>
 * </ul>
 * 
 * <p><strong>6.4 Numerical Methods</strong>
 * <ul>
 *   <li><strong>Press, William H. et al.</strong> (2007). "Numerical Recipes: The Art of Scientific Computing"
 *       (3rd ed.). Cambridge University Press.
 *       <br>→ Chapter 5: Finite difference methods and optimal step sizes for derivatives</li>
 * </ul>
 * 
 * <h2>Summary</h2>
 * 
 * <p>This class implements Fisher Information Matrix calculations to quantify uncertainty in WiFi
 * access point localization. It provides two methods (analytical gradients and numerical Hessian)
 * that both leverage the fundamental relationship between information content and uncertainty.
 * The implementation is backed by rigorous statistical theory and WiFi-specific empirical research,
 * providing theoretically optimal accuracy bounds for location estimates.
 * 
 * @author WiFi Access Point Localization Team
 * @version 1.0
 * @since 1.0
 * @see com.wifi.ap.location.estimation.accuracy.FisherInfoAccuracyEstimator
 * @see com.wifi.ap.location.estimation.algorithm.impl.SequentialBayesianUpdate
 * @see com.wifi.ap.location.estimation.algorithm.impl.MaximumLikelihoodEstimation
 */
public class FisherInformationCalculator {
    
    private static final Logger logger = LoggerFactory.getLogger(FisherInformationCalculator.class);
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private FisherInformationCalculator() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }
    
    // ========================================================================
    // Constants
    // ========================================================================
    
    /**
     * Finite difference step size for Hessian approximation.
     * 
     * <p><strong>Research-Backed Value:</strong>
     * Uses machine epsilon^(1/3) for optimal balance between truncation and roundoff errors
     * in second derivative approximation via finite differences.
     * 
     * <p><strong>Source:</strong> Numerical analysis theory for finite difference methods
     * - Press et al. (2007): "Numerical Recipes" - Optimal step size for second derivatives
     */
    private static final double HESSIAN_DELTA = Math.pow(Math.ulp(1.0), 1.0/3.0); // ≈ 6.055e-6
    
    /**
     * Noise standard deviation for CONNECTED measurements (dBm).
     * Source: WiFi positioning literature consensus
     */
    private static final double NOISE_STD_CONNECTED = 4.0;
    
    /**
     * Noise standard deviation for SCAN measurements (dBm).
     * Source: WiFi positioning literature consensus
     */
    private static final double NOISE_STD_SCAN = 8.0;
    
    // ========================================================================
    // Gradient-Based Fisher Information Matrix (Analytical)
    // ========================================================================
    
    /**
     * Calculates Fisher Information Matrix from analytical path loss gradients.
     * 
     * <p><strong>Use Case:</strong> Sequential Bayesian Update, where gradients are computed
     * analytically from the log-distance path loss model.
     * 
     * <p><strong>Mathematical Formula:</strong>
     * <pre>
     * J = Σᵢ (1/σᵢ²) × ∇h(θ) × ∇h(θ)ᵀ
     * 
     * Where:
     * - ∇h(θ) = -10n/ln(10) × (θ - mᵢ) / ||θ - mᵢ||²
     * - θ = AP location, mᵢ = measurement location
     * - n = path loss exponent (frequency-dependent)
     * - σᵢ² = noise variance (connection status-dependent)
     * </pre>
     * 
     * @param apLocation Current AP location estimate
     * @param measurementLocation Measurement location
     * @param band WiFi frequency band (determines path loss exponent)
     * @param connectionStatus "CONNECTED" or "SCAN" (determines noise variance)
     * @return Fisher Information Matrix contribution from this measurement
     */
    public static RealMatrix calculateGradientBasedFIM(Location apLocation, 
                                                       Location measurementLocation,
                                                       WiFiFrequencyBand band,
                                                       String connectionStatus) {
        
        // Calculate path loss gradient
        RealVector gradient = calculatePathLossGradient(apLocation, measurementLocation, band);
        
        // Calculate inverse noise variance (precision)
        double inverseNoiseVariance = calculateInverseNoiseVariance(connectionStatus);
        
        // Fisher Information contribution: (1/σ²) × ∇h × ∇hᵀ
        return gradient.outerProduct(gradient).scalarMultiply(inverseNoiseVariance);
    }
    
    /**
     * Calculates the gradient of the log-distance path loss model with respect to AP location.
     * 
     * <p><strong>Mathematical Derivation:</strong>
     * <pre>
     * Path loss model: h(θ) = P_ref - 10n × log₁₀(||θ - m||/d₀)
     * 
     * Gradient: ∇h(θ) = -10n/ln(10) × (θ - m) / ||θ - m||²
     * 
     * In geographic coordinates:
     * - ∂h/∂lat = -10n/ln(10) × (lat_AP - lat_m) / distance²
     * - ∂h/∂lon = -10n/ln(10) × (lon_AP - lon_m) / distance²
     * 
     * Units: dBm per degree (geographic coordinates)
     * </pre>
     * 
     * <p><strong>Physical Interpretation:</strong>
     * The gradient indicates how sensitive the RSSI is to changes in AP location.
     * Steeper gradients provide more information about location.
     * 
     * @param apLocation Current AP location estimate [lat, lon] in degrees
     * @param measurementLocation Measurement location [lat, lon] in degrees
     * @param band WiFi frequency band (determines path loss exponent)
     * @return Gradient vector [∂h/∂lat, ∂h/∂lon] in dBm/degree
     */
    public static RealVector calculatePathLossGradient(Location apLocation,
                                                       Location measurementLocation, 
                                                       WiFiFrequencyBand band) {
        
        // Calculate geographic distance using Haversine formula (meters)
        double distance = apLocation.distanceTo(measurementLocation);
        
        // Path loss exponent (frequency-dependent)
        double pathLossExponent = band.getPathLossExponent();
        
        // Gradient scale factor: -10n / (ln(10) × d²)
        double gradientScale = -10.0 * pathLossExponent / (Math.log(10) * distance * distance);
        
        // Coordinate differences (degrees)
        double deltaLat = apLocation.latitude() - measurementLocation.latitude();
        double deltaLon = apLocation.longitude() - measurementLocation.longitude();
        
        // Gradient vector [∂h/∂lat, ∂h/∂lon]
        return new ArrayRealVector(new double[]{
            gradientScale * deltaLat,
            gradientScale * deltaLon
        });
    }
    
    // ========================================================================
    // Hessian-Based Fisher Information Matrix (Numerical)
    // ========================================================================
    
    /**
     * Computes Fisher Information Matrix via numerical Hessian approximation.
     * 
     * <p><strong>Use Case:</strong> MLE accuracy estimation, where the Hessian of the
     * negative log-likelihood function is approximated numerically at the optimal point.
     * 
     * <p><strong>Mathematical Foundation:</strong>
     * <pre>
     * For MLE with negative log-likelihood L(θ):
     * Fisher Information Matrix ≈ Hessian of L(θ) at θ̂_MLE
     * 
     * Hessian approximation using central finite differences:
     * ∂²L/∂xᵢ∂xⱼ ≈ (L(x+hᵢ+hⱼ) - L(x+hᵢ-hⱼ) - L(x-hᵢ+hⱼ) + L(x-hᵢ-hⱼ)) / (4h²)
     * </pre>
     * 
     * <p><strong>Consistency Guarantee:</strong>
     * Uses the exact same objective function from MLE optimization to ensure the FIM
     * reflects the actual optimization problem.
     * 
     * @param location Location at which to compute Hessian [lat, lon]
     * @param objectiveFunction Negative log-likelihood function from MLE
     * @return Hessian matrix (2×2) approximating Fisher Information
     */
    public static RealMatrix computeHessianMatrix(double[] location, 
                                                  MultivariateFunction objectiveFunction) {
        
        double lat = location[0];
        double lon = location[1];

        // Evaluate function at center point
        double f0 = objectiveFunction.value(location);
        
        // Initialize Hessian matrix
        double[][] hessian = new double[2][2];
        
        // ∂²f/∂lat² (second derivative with respect to latitude)
        double fLatPlus = objectiveFunction.value(new double[]{lat + HESSIAN_DELTA, lon});
        double fLatMinus = objectiveFunction.value(new double[]{lat - HESSIAN_DELTA, lon});
        hessian[0][0] = (fLatPlus - 2 * f0 + fLatMinus) / (HESSIAN_DELTA * HESSIAN_DELTA);
        
        // ∂²f/∂lon² (second derivative with respect to longitude)
        double fLonPlus = objectiveFunction.value(new double[]{lat, lon + HESSIAN_DELTA});
        double fLonMinus = objectiveFunction.value(new double[]{lat, lon - HESSIAN_DELTA});
        hessian[1][1] = (fLonPlus - 2 * f0 + fLonMinus) / (HESSIAN_DELTA * HESSIAN_DELTA);
        
        // ∂²f/∂lat∂lon (mixed partial derivative)
        double fLatLonPlusPlus = objectiveFunction.value(new double[]{lat + HESSIAN_DELTA, lon + HESSIAN_DELTA});
        double fLatLonPlusMinus = objectiveFunction.value(new double[]{lat + HESSIAN_DELTA, lon - HESSIAN_DELTA});
        double fLatLonMinusPlus = objectiveFunction.value(new double[]{lat - HESSIAN_DELTA, lon + HESSIAN_DELTA});
        double fLatLonMinusMinus = objectiveFunction.value(new double[]{lat - HESSIAN_DELTA, lon - HESSIAN_DELTA});
        
        hessian[0][1] = hessian[1][0] = (fLatLonPlusPlus - fLatLonPlusMinus - fLatLonMinusPlus + fLatLonMinusMinus) 
                                       / (4 * HESSIAN_DELTA * HESSIAN_DELTA);
        
        return new Array2DRowRealMatrix(hessian);
    }
    
    // ========================================================================
    // Covariance Matrix Computation
    // ========================================================================
    
    /**
     * Computes covariance matrix by inverting the Fisher Information Matrix.
     * 
     * <p><strong>Mathematical Relationship:</strong>
     * <pre>
     * Σ = J⁻¹
     * 
     * Where:
     * - Σ = covariance matrix (parameter uncertainty)
     * - J = Fisher Information Matrix
     * </pre>
     * 
     * <p><strong>Cramér-Rao Lower Bound:</strong>
     * The inverse Fisher Information provides the theoretical minimum variance
     * achievable by any unbiased estimator under Gaussian assumptions.
     * 
     * @param fisherInformation Fisher Information Matrix to invert
     * @return Covariance matrix representing parameter uncertainty
     * @throws SingularMatrixException if Fisher Information Matrix is singular
     */
    public static RealMatrix computeCovarianceMatrix(RealMatrix fisherInformation) 
            throws SingularMatrixException {
        try {
            LUDecomposition lu = new LUDecomposition(fisherInformation);
            return lu.getSolver().getInverse();
        } catch (SingularMatrixException e) {
            logger.warn("Singular Fisher Information Matrix - cannot invert for covariance");
            throw e;
        }
    }
    
    /**
     * Converts Apache Commons Math RealMatrix to domain CovarianceMatrix.
     * 
     * @param realMatrix 2×2 covariance matrix from Apache Commons Math
     * @return CovarianceMatrix domain object
     */
    public static CovarianceMatrix toCovarianceMatrix(RealMatrix realMatrix) {
        return new CovarianceMatrix(
            realMatrix.getEntry(0, 0), // σ²_lat (variance in latitude)
            realMatrix.getEntry(0, 1), // σ_lat,lon (covariance)
            realMatrix.getEntry(1, 0), // σ_lon,lat (covariance)
            realMatrix.getEntry(1, 1)  // σ²_lon (variance in longitude)
        );
    }
    
    // ========================================================================
    // Helper Methods
    // ========================================================================
    
    /**
     * Calculates inverse noise variance (precision) based on connection status.
     * 
     * <p><strong>Noise Models:</strong>
     * <pre>
     * CONNECTED measurements: σ² = 16.0 dBm² → 1/σ² = 0.0625
     * SCAN measurements:      σ² = 64.0 dBm² → 1/σ² = 0.015625
     * </pre>
     * 
     * <p><strong>Research Basis:</strong>
     * CONNECTED measurements have lower noise variance due to active connection
     * and signal quality feedback mechanisms.
     * 
     * @param connectionStatus "CONNECTED" or "SCAN"
     * @return Inverse noise variance (1/σ²) in (dBm)⁻²
     */
    public static double calculateInverseNoiseVariance(String connectionStatus) {
        double noiseStd = "CONNECTED".equals(connectionStatus) ? NOISE_STD_CONNECTED : NOISE_STD_SCAN;
        return 1.0 / (noiseStd * noiseStd);
    }
    
    /**
     * Calculates noise variance based on connection status.
     * 
     * @param connectionStatus "CONNECTED" or "SCAN"
     * @return Noise variance (σ²) in dBm²
     */
    public static double calculateNoiseVariance(String connectionStatus) {
        double noiseStd = "CONNECTED".equals(connectionStatus) ? NOISE_STD_CONNECTED : NOISE_STD_SCAN;
        return noiseStd * noiseStd;
    }
    
    /**
     * Calculates information gain from prior to posterior distribution.
     * 
     * <p><strong>Mathematical Formula:</strong>
     * <pre>
     * Information Gain = 0.5 × ln(det(Σ_prior) / det(Σ_posterior))
     * 
     * Measured in nats (natural units of information)
     * </pre>
     * 
     * <p><strong>Interpretation:</strong>
     * - Positive value indicates uncertainty reduction
     * - Larger values indicate more informative measurements
     * - Zero indicates no new information gained
     * 
     * <p><strong>Source:</strong> Cover & Thomas (2006) "Elements of Information Theory"
     * 
     * @param priorDeterminant Determinant of prior covariance matrix
     * @param posteriorDeterminant Determinant of posterior covariance matrix
     * @return Information gain in nats
     */
    public static double calculateInformationGain(double priorDeterminant, 
                                                  double posteriorDeterminant) {
        if (posteriorDeterminant <= 0 || priorDeterminant <= 0) {
            logger.warn("Invalid covariance determinants for information gain: prior={}, posterior={}", 
                       priorDeterminant, posteriorDeterminant);
            return 0.0;
        }
        return 0.5 * Math.log(priorDeterminant / posteriorDeterminant);
    }
    
    /**
     * Validates that a matrix is positive definite.
     * 
     * <p>Covariance and Fisher Information matrices must be positive definite.
     * This checks that all eigenvalues are positive.
     * 
     * @param matrix Matrix to validate
     * @return true if positive definite, false otherwise
     */
    public static boolean isPositiveDefinite(RealMatrix matrix) {
        try {
            EigenDecomposition eigen = new EigenDecomposition(matrix);
            double[] eigenvalues = eigen.getRealEigenvalues();
            
            for (double eigenvalue : eigenvalues) {
                if (eigenvalue <= 0) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger.warn("Error checking positive definiteness: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Calculates the condition number of a matrix.
     * 
     * <p><strong>Definition:</strong>
     * Condition number = λ_max / λ_min (ratio of largest to smallest eigenvalue)
     * 
     * <p><strong>Interpretation:</strong>
     * - Low values (< 100): Well-conditioned, numerically stable
     * - High values (> 1000): Ill-conditioned, numerical instability risk
     * 
     * @param matrix Matrix to analyze
     * @return Condition number
     */
    public static double calculateConditionNumber(RealMatrix matrix) {
        EigenDecomposition eigen = new EigenDecomposition(matrix);
        double[] eigenvalues = eigen.getRealEigenvalues();
        
        double maxEigen = Double.NEGATIVE_INFINITY;
        double minEigen = Double.POSITIVE_INFINITY;
        
        for (double eigenvalue : eigenvalues) {
            if (eigenvalue > maxEigen) maxEigen = eigenvalue;
            if (eigenvalue < minEigen && eigenvalue > 0) minEigen = eigenvalue;
        }
        
        return maxEigen / minEigen;
    }
}

