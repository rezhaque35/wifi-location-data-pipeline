# WiFi Access Point Localization Service - Requirements Document

## 1. Executive Summary

### 1.1 Project Overview
Develop a Java-based microservice that performs Access Point (AP) localization using crowdsourced WiFi measurement data. The service processes batches of AP MAC addresses from AWS SQS, retrieves measurements from AWS S3 Tables (Apache Iceberg format) via Athena, performs multi-stage localization algorithms, and persists results to DynamoDB.

### 1.2 Core Processing Pipeline
Based on Section 7 of the AP Localization Framework, the service must implement the following pipeline for each Access Point:
1. Retrieve measurements from S3 Tables via Athena
2. Retrieve current AP state from DynamoDB
3. Run AP Dynamic State Classification (Hotspot and Relocation Detection)
4. Apply Advanced Outlier Detection (Global and Local)
5. Execute Dynamic Localization Algorithm (WCL, MLE, or Bayesian)
6. Persist updated state to DynamoDB

### 1.3 Scope Exclusions
- **Iterative Refinement and Online Learning Loop** described in Section 6 of the framework
- **Kalman Filter** implementation for iterative state updates
- **Stage 1 Data Processing**: Initial data ingestion, sanity checking, and quality weighting (already completed)
- **Real-time processing**: Only batch processing is in scope

## 2. Technical Stack Requirements

### 2.1 Core Technology Requirements
- **Programming Language**: Java 21 with modern language features
- **Framework**: Spring Boot 3.x with Spring Cloud AWS integration
- **AWS SDK**: Version 2 for all AWS service integrations
- **Build System**: Maven or Gradle with dependency management
- **Testing Framework**: JUnit 5, Mockito, TestContainers
- **Local Development**: LocalStack for AWS service simulation

### 2.2 AWS Services Integration Requirements
- **SQS**: For receiving batch processing requests
- **S3 Tables (Apache Iceberg)**: For measurement data storage
- **Athena**: For querying S3 Tables
- **DynamoDB**: For AP state persistence
- **CloudWatch**: For metrics and logging

### 2.3 Development Environment Requirements
- **LocalStack Integration**: Primary local development environment using LocalStack for all AWS services
- **LocalStack Services**: SQS, S3, Athena, DynamoDB, CloudWatch must be fully supported
- **Environment Profiles**: Support for local (LocalStack), development, staging, and production
- **Configuration Management**: Externalized configuration via environment variables with LocalStack-specific settings
- **Script Organization**: Setup scripts in `scripts/setup/`, test scripts in `scripts/test/`, data files for test scripts can be stored in `scripts/test/data`, LocalStack initialization scripts
- **LocalStack Data Seeding**: Automated setup of test data, queues, tables, and buckets for local development

## 3. Functional Requirements

### 3.1 SQS Message Processing

#### 3.1.1 Message Structure Requirements
- Each SQS message contains a single MAC address as a simple string (e.g., "00:11:22:33:44:55")
- No additional metadata or wrapper objects required
- Support standard MAC address format validation

#### 3.1.2 Polling Requirements
- Implement long polling with 20-second wait time
- Batch size optimized for DynamoDB write limits (25 messages per batch)
- Configure visibility timeout of 300 seconds
- Support visibility timeout extension during long processing
- Implement Dead Letter Queue after 3 failed attempts

#### 3.1.3 Processing Model Requirements
- Single-threaded processing with backpressure handling
- Block new message polling when DynamoDB write buffer is full
- Support graceful shutdown with in-flight message completion
- Implement message deletion only after successful processing

### 3.2 Measurement Data Retrieval

#### 3.2.1 Athena Query Requirements
- Query wifi_measurements table for each BSSID
- Exclude records where is_global_outlier = true
- Support configurable lookback window (default 30 days)
- Limit results to configurable maximum (default 1000 records)
- Order results by measurement_timestamp descending

#### 3.2.2 Query Result Management Requirements
- Store query results in designated S3 result bucket
- Implement automatic cleanup of result files after processing
- No caching required (processing occurs weekly per MAC)
- Handle query timeouts with retry mechanism
- Implement query status polling for async execution

#### 3.2.3 Data Fields to Retrieve
- Essential fields: bssid, latitude, longitude, rssi, frequency
- Connection metadata: link_speed, connection_status, quality_weight
- Timestamps: measurement_timestamp, location_accuracy
- Identifiers: event_id for outlier tracking

### 3.3 AP State Management

#### 3.3.1 DynamoDB Schema Requirements
Must store and maintain the following state information:
- **Basic AP Information**: MAC address, SSID, vendor, frequency
- **Location Estimate**: Latitude, longitude, altitude, accuracy metrics
- **Confidence Metrics**: Horizontal accuracy, vertical accuracy, confidence score
- **Calculation State**: Data maturity tier, measurement counts, last algorithm used, algo used reasoning, Hotspot algorithm used, Hotspot result reasoning, relocation detection algorithm, relocation result reasoning
- **Covariance Matrix**: 2x2 matrix for location uncertainty
- **Quality Metrics**: Outlier counts, average accuracies, consistency scores
- **Temporal Data**: Created/updated timestamps, TTL for data expiration

Add the following fields to support post-maturity local outlier detection:

### Post-Maturity Outlier Detection State
- **Spatial State**: JSON object containing:
  - **spatial_centroid**: Latitude/longitude of measurement distribution center
  - **covariance_matrix**: 2x2 matrix for directional uncertainty [xx, xy, yx, yy]
  - **distance_statistics**: Mean, std_dev, percentile_90, percentile_95, percentile_99
  - **quality_statistics**: Mean quality_weight, std_dev, connected_percentage
- **Representative Sample**: JSON array of representative measurements (optional, configurable)
- **Spatial Boundaries**: JSON object containing:
  - **convex_hull_points**: Array of boundary coordinates
  - **extreme_distances**: Min/max distances in cardinal directions
- **Outlier State Metadata**:
  - **state_computed_timestamp**: When spatial state was calculated
  - **source_measurement_count**: Number of measurements used for state computation
  - **state_version**: Version counter for state updates
  - **algorithm_transition_flag**: Boolean indicating if using post-maturity detection

### Configuration Fields
- **outlier_detection_config**: JSON object for algorithm-specific parameters and thresholds


#### 3.3.2 Batch Write Requirements
- Buffer up to 25 items (DynamoDB limit) before writing
- Implement flush triggers on buffer full or 5-second timeout
- Use transactional writes for data consistency
- Implement backpressure by blocking when write queue is full
- Support auto-scaling with exponential backoff for throttling

#### 3.3.3 State Retrieval Requirements
- Retrieve existing AP state before processing
- Handle missing state for new APs
- Support partial state updates
- Maintain version tracking for concurrent update detection

### 3.4 AP Dynamic State Classification

#### 3.4.1 Mobile Hotspot Detection Requirements
Must implement detection based on:
- **Spatial Distribution Analysis**: 
  - Calculate standard deviation of measurement locations
  - Flag as mobile if exceeds 500-meter threshold
  - Require minimum 20 measurements for classification
  
Note: SSID pattern matching and OUI filtering occur upstream in the data pipeline, so MAC addresses for obvious mobile hotspots will not reach this service.

#### 3.4.2 AP Relocation Detection Requirements
Must detect when stationary APs are moved:
- **Change-Point Detection** (Primary Method):
  - Monitor location measurement time series
  - Detect sudden shifts in location centroid
  - Use Mahalanobis distance for change detection
  - Flag relocation if distance exceeds 100-meter threshold
  
- **Optional: Bi-modal Clustering** (If needed for accuracy):
  - Apply clustering algorithm (DBSCAN) to location data
  - Detect emergence of geographically separate clusters
  - Implement only if change-point detection proves insufficient
  
- **State Reset on Relocation**:
  - Invalidate historical measurements before relocation
  - Reset data maturity tier to bootstrap
  - Clear covariance matrix and quality metrics

### 3.5 Advanced Outlier Detection

#### 3.5.1 Global Outlier Detection Requirements
- **Centroid-based Detection** (Primary Method):
  - Calculate geographic centroid of all measurements using ECEF vector averaging
  - Compute Haversine distance of each point from centroid
  - Use Median Absolute Deviation (MAD) with 3x multiplier
  - Flag points exceeding threshold as global outliers
  
- **Optional Robustness Enhancement** (Configurable):
  - For unusually skewed datasets, optionally switch to geometric median or medoid
  - Practical guardrail: compute both geographic centroid and geometric median
  - If separation between centroid and median > configurable threshold (default 100-200m), use geometric median
  - Otherwise, maintain default centroid-based approach for compliance
  - This enhancement should be behind configuration flags and require ADR approval
  
- **Implementation Notes**:
  - Use Apache Commons Math for statistical calculations
  - Alternative: Smile library for outlier detection algorithms
  - Geographic centroid: Convert lat/lon to ECEF unit vectors, average, normalize, convert back
  - Geometric median: Iterative Weiszfeld algorithm on ECEF unit vectors (3-5 iterations)
  - Medoid: Actual measurement point minimizing total distance to others
  
- **Outlier Flagging**:
  - Mark outliers in measurement data (don't delete)
  - Track outlier event_ids for exclusion from processing
  - Update global_outlier_distance in measurement records
  - Log outlier detection metrics and center selection reasoning

## 3.5.2 Local Outlier Detection Requirements

Local outlier detection operates in two distinct phases based on AP data maturity status, optimizing for both accuracy and computational efficiency.

### 3.5.2.1 Pre-Highly Mature Algorithm (N < 100)
- **Algorithm Type**: Full Local Outlier Factor (LOF) computation
- **Trigger Conditions**: Apply when AP has not yet reached highly mature status (N < 100 measurements)
- **Processing Approach**:
  - Operate on complete measurement dataset after global outlier removal
  - Calculate Haversine distances between all measurement pairs
  - Identify k-nearest neighbors for each point (k = 5-8, adaptive based on dataset size)
  - Compute Local Reachability Density (LRD) for each measurement
  - Calculate LOF scores as ratio of average neighbor LRD to point's LRD
  - Flag measurements exceeding LOF threshold (1.7) as local outliers
- **Implementation Notes**:
  - Maximum dataset size bounded at 100 measurements due to state persistence approach
  - Processing time target: <0.2 seconds for typical datasets
  - No historical state required - each calculation is independent
  - Use minimum 5 points for neighborhood calculation

### 3.5.2.2 Post-Highly Mature Algorithm (N ≥ 100)
- **Algorithm Type**: Statistical anomaly detection against established spatial model
- **Trigger Conditions**: Apply when AP reaches highly mature status and spatial state has been computed
- **Processing Approach**:
  - Process only new measurement batches (typically 10-50 measurements)
  - Apply Mahalanobis distance analysis using stored covariance matrix
  - Compare distances from spatial centroid against learned distribution parameters
  - Estimate local density using stored representative sample as virtual neighbors
  - Apply quality-weighted thresholds based on connection_status
  - Cross-reference against stored spatial boundary conditions
- **Performance Characteristics**:
  - Faster processing than full LOF (operates on new measurements only)
  - Potentially lower sensitivity to subtle local anomalies
  - Relies on stored spatial state from previous calculations

### 3.5.2.3 Spatial State Storage Requirements

When transitioning to highly mature status, the system must compute and persist spatial state information for post-maturity outlier detection:

#### Essential Statistical State
- **Spatial Centroid**: Geographic center point (latitude, longitude) of historical measurement distribution
- **Covariance Matrix**: 2x2 matrix capturing directional uncertainty and correlation between lat/lon coordinates
- **Distance Statistics**: Mean, standard deviation, and key percentiles (90th, 95th, 99th) of distances from centroid observed in historical data
- **Quality Distribution Statistics**: Mean and standard deviation of quality_weight values and proportion of CONNECTED vs SCAN measurements

#### Spatial Boundary Information
- **Convex Hull Points**: 8-12 geographic points defining outer boundary of measurement distribution
- **Extreme Value Records**: Minimum and maximum distances observed in cardinal directions (N, S, E, W) from centroid
- **Density Gradient Parameters**: Approximate density values at different distance rings from centroid (optional enhancement)

#### Representative Sample Storage
- **Sample Collection**: Store 30-50 representative measurements preserving spatial and quality distribution
- **Storage Optimization**: For cost constraints, minimum viable option is storing 8-10 spatial boundary points
- **Update Metadata**: Timestamp of last state computation, sample size used, update iteration counter

### 3.5.2.4 Representative Sample Selection Process

#### Stratified Spatial Sampling Strategy
1. **Geographic Grid Division**: Divide measurement area into regular grid (4x4 to 6x6 cells based on distribution spread)
2. **Cell-Based Selection**: From each grid cell containing measurements:
   - Select highest quality measurement (CONNECTED preferred over SCAN)
   - Choose measurement with strongest RSSI within quality tier
   - Ensure minimum spatial separation (20-50 meters) between representatives
3. **Quality-Proportional Representation**: Maintain same proportion of CONNECTED vs SCAN measurements as full dataset
4. **Boundary Emphasis**: Deliberately over-sample measurements near spatial boundaries of distribution
5. **Temporal Distribution**: Include measurements from different time periods to capture temporal variations

#### Selection Criteria Priority Order
1. **Spatial Coverage**: Ensure representatives span full geographic extent
2. **Connection Quality**: Prefer CONNECTED measurements with rich metadata
3. **Signal Strength**: Within quality tier, prefer stronger RSSI values
4. **Temporal Diversity**: Include measurements from different collection periods
5. **Geometric Distribution**: Maintain coverage of different angles/directions from centroid

#### Sample Size Determination
- **Target Size**: 30-50 representatives for typical AP distributions
- **Minimum Coverage**: At least 3-4 representatives per major spatial cluster
- **Boundary Requirement**: At least 8-10 representatives defining convex hull
- **Quality Balance**: Maintain minimum 60% CONNECTED representatives when available

### 3.5.2.5 Algorithm Transition Logic

#### Transition Triggers
- **Initial Transition**: Switch to post-maturity algorithm when AP first reaches highly mature status (N ≥ 100)
- **State Computation**: Calculate and persist spatial state during transition
- **Subsequent Processing**: All future processing uses post-maturity approach until reset conditions

#### State Reset Conditions
- **AP Relocation Detection**: Reset to pre-maturity algorithm and clear spatial state
- **Quality Distribution Changes**: Significant shifts in measurement quality patterns may trigger state recalculation
- **State Expiration**: Extended periods without measurements (configurable threshold) may trigger state reset
- **Manual Override**: Administrative capability to force state reset for debugging/maintenance

### 3.5.2.6 Integration Requirements
- **Processing Pipeline**: Apply after global outlier detection, before localization algorithms
- **Result Combination**: Merge local outliers with global outliers for complete exclusion set
- **Logging Requirements**: Log algorithm selection reasoning, outlier patterns, and performance metrics
- **Error Handling**: Graceful fallback to distance-based detection if LOF computation fails
- **Configuration Support**: Configurable thresholds, sample sizes, and algorithm selection parameters

### 3.6 Dynamic Localization Algorithms

#### 3.6.1 Two-Phase Hybrid Algorithm Selection Framework

The algorithm selection follows a **two-phase hybrid approach**: initial build-up to establish high-quality prior estimates, followed by optimal iterative processing using Bayesian inference.

##### 3.6.1.1 Phase 1: Build-up Phase (Establishing Quality Prior)

**Objective**: Accumulate sufficient measurements to create a robust, high-quality statistical foundation before switching to optimal Bayesian processing.

**Decision Logic:**
- **N < 20**: Skip processing, log warning (insufficient for reliable localization)
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

##### 3.6.1.2 Phase 2: Iterative Refinement Phase

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

##### 3.6.1.3 Phase Transition Logic

**Phase Switch Criteria:**
- **Primary**: Total measurements ≥ 100
- **Secondary**: Successful MLE computation with reasonable uncertainty bounds
- **Action**: Delete historical measurement data, retain only final MLE state
- **Result**: Switch all subsequent processing to Bayesian inference

**Post-Transition Processing:**
- All future batches use Bayesian inference regardless of new measurement count
- Prior always comes from previous batch processing state
- New measurements provide fresh information for state updates
- State evolution continues indefinitely with optimal information fusion

#### 3.6.2 Weighted Centroid Localization (WCL) Requirements

**When Used**: First-time localization with 20-49 measurements during Phase 1
**Purpose**: Quick bootstrap estimate with limited data
**Characteristics**: Research-driven weighted average with comprehensive quality assessment

##### 3.6.2.1 Mathematical Foundation

**Weighted Centroid Calculation**:
```
weight = quality_weight × signal_weight × accuracy_weight

Where:
- quality_weight = 2.0 for CONNECTED measurements, 1.0 for SCAN measurements
- signal_weight = 10^(RSSI/10) for exponential RSSI weighting  
- accuracy_weight = 1/(GPS_accuracy²) for inverse variance GPS accuracy weighting
```

**ECEF Vector Averaging**:
- Uses spherical geometry for geographic accuracy
- Converts lat/lon to ECEF unit vectors, averages, normalizes, converts back
- Provides geometric robustness for non-planar distributions

##### 3.6.2.2 Research-Based Accuracy Estimation

**Multi-Factor Accuracy Algorithm**:
```
Primary Accuracy = spatialStdDev × 1.96  // 95% confidence interval

Final Accuracy = (Spatial × 0.60) + (GPS Quality × 0.25) + (Sample Size × 0.15)

Final Accuracy × Connection Factor × RSSI Factor × Geometric Factor
```

**Research Sources and Weight Justification**:
- **Spatial Consistency (60% weight)**: Primary factor - most direct measure of WCL reliability based on measurement distribution
- **GPS Quality (25% weight)**: Secondary factor - affects input data quality from PMC 7763701 study (168,286+ GPS measurements)
- **Sample Size (15% weight)**: Tertiary factor - statistical confidence indicator from representative sample studies

**Environmental Quality Factors** (applied as multipliers):
- **Connection Quality**: CONNECTED vs SCAN ratio (0.85-1.15× multiplier)
- **RSSI Consistency**: Based on RSSI standard deviation (0.9-1.2× multiplier)  
- **Geometric Quality**: Spatial spread and angular coverage (0.85-1.3× multiplier)

**Accuracy Bounds**: 5-100 meters based on comprehensive WiFi positioning literature review

##### 3.6.2.3 Confidence Calculation

**Geometric Mean Approach**:
```
spatial_consistency = 1.0 - (spatial_std_dev / 50.0)  // Normalized to [0.2, 1.0]
gps_quality = threshold_based_mapping(avg_gps_accuracy)  // Quality tier mapping
confidence = sqrt(spatial_consistency × gps_quality)  // Geometric mean
```

**GPS Quality Mapping** (based on PMC 7763701 research):
- Excellent (≤5m): 0.9 confidence
- Very Good (≤15m): 0.8 confidence  
- Good (≤30m): 0.7 confidence
- Acceptable (≤50m): 0.6 confidence
- Poor (≤75m): 0.4 confidence
- Very Poor (≤100m): 0.3 confidence
- Terrible (>100m): 0.2 confidence

##### 3.6.2.4 Research Citations and Validation

**Primary Research Sources**:
- **PMC 7763701**: Specht et al. (2020) - Statistical Distribution Analysis of Navigation Positioning System Errors (168,286+ GPS measurements)
- **Statistical Theory**: 95% confidence interval (1.96 multiplier) - universally accepted mathematical principle
- **WiFi Localization Studies**: RSSI consistency thresholds (2-15 dBm range) from indoor positioning research
- **Geometric Studies**: GDOP and angular coverage requirements from WiFi positioning literature
- **Framework Specification**: CONNECTED vs SCAN quality differentiation (2× weight)

**Implementation Status**: ✅ **IMPLEMENTED AND VALIDATED**
- Complete WCL implementation with research-based accuracy estimation
- Comprehensive quality assessment using all available measurement factors
- All constants traceable to peer-reviewed research sources
- Detailed logging and performance monitoring capabilities

#### 3.6.3 Maximum Likelihood Estimation (MLE) Requirements

**When Used**: 50+ measurements during Phase 1 build-up
**Purpose**: Physics-based localization using signal propagation models
**Characteristics**: Uses log-distance path loss model, leverages rich metadata
**Advantage**: Better accuracy than WCL, handles larger datasets effectively

##### 3.6.3.1 Mathematical Foundation

**Log-Distance Path Loss Model**:
```
RSSI(d) = P_tx - 10 × n × log₁₀(d/d₀) + X_σ
```
Where:
- P_tx = Reference transmit power (frequency-specific, regulatory standards)
- n = Path loss exponent (frequency-specific, environment-dependent)
- d = Distance from AP to measurement location
- d₀ = Reference distance (1 meter)
- X_σ = Zero-mean Gaussian noise with connection-specific standard deviation

**Negative Log-Likelihood Function**:
```
-LL(θ) = Σᵢ [wᵢ × (RSSI_obs - RSSI_expected)² / (2σᵢ²)]
```
Where θ = (latitude, longitude) are the AP location parameters to be estimated.

##### 3.6.3.2 Measurement Weighting Strategy

**Inverse Variance Accuracy-Based Weighting** (Primary Strategy):
- **GPS Accuracy Weighting**: `weight = 1/(GPS_accuracy²)` - statistically optimal inverse variance weighting
- **Signal Strength Weighting**: `weight = 10^(RSSI/10)` - exponential RSSI weighting
- **Connection Quality Weighting**: CONNECTED=2.0, SCAN=1.0 - research-validated quality factors
- **Combined Weight**: `total_weight = gps_weight × signal_weight × quality_weight`
- **Normalization**: Normalize by total weight to maintain mathematical correctness

**Research Foundation for Weighting**:
- **Inverse Variance Weighting**: Based on Open Access Surveying Library Chapter E and statistical estimation theory
- **GPS Accuracy Treatment**: GPS accuracy estimates treated as standard deviation proxies (common practice in positioning literature)
- **No Arbitrary Constants**: Pure inverse variance approach without normalization factors

##### 3.6.3.3 Model Parameters

**Frequency-Specific Parameters**:
- **2.4 GHz Band**: Reference power from ETSI/FCC regulatory standards, path loss exponent from Obeidat et al. (2018)
- **5 GHz Band**: Frequency-specific regulatory power limits and propagation characteristics
- **6 GHz Band**: Latest regulatory standards and empirical propagation data

**Connection-Specific Noise Models**:
- **CONNECTED Measurements**: σ = 4.0 dBm (research-validated for stable connections)
- **SCAN Measurements**: σ = 8.0 dBm (research-validated for passive scanning, 2:1 ratio)

**RSSI Noise Standard Deviation Research Foundation**:
- **Academic Sources**: Multiple studies document RSSI noise variance in 2-15 dBm range
- **Olsson & Öhrström (2017)**: "The precision of RSSI-fingerprinting based on connected Wi-Fi devices" - RSSI stability improves with connected device activity
- **PMC 7472118 (2020)**: "Comparison of 2.4 GHz WiFi FTM- and RSSI-Based Indoor Positioning" - RSSI noise variance 2-15 dBm range, environment-specific tuning
- **PMC 7436166**: "An RSSI Classification and Tracing Algorithm to Improve Trilateration-Based Positioning" - Empirical validation of 4-8 dBm range
- **October 2022**: "Machine Learning for Location Prediction Using RSSI on Wi-Fi 2.4 GHz" - RSSI variability analysis for indoor localization
- **Status**: Peer-reviewed research validated - literature-backed default for practical deployments

##### 3.6.3.4 Optimization Implementation

**Apache Commons Math Integration**:
- **Algorithm**: Nelder-Mead simplex optimization (derivative-free, robust for noisy objective functions)
- **Initial Guess**: Weighted centroid from WifiMeasurements cached calculation
- **Convergence**: Configurable threshold (1e-6) for sub-meter precision
- **Iteration Limit**: Maximum 1000 function evaluations to prevent infinite loops
- **Simplex Size**: 0.01 degrees (≈1.1 km) for appropriate WiFi range exploration

**Shared Objective Function Architecture**:
- **Single Function Creation**: Create MultivariateFunction once for both optimization and accuracy calculation
- **Consistency Guarantee**: FIM accuracy calculation uses identical objective function as MLE optimization
- **Performance Optimization**: Eliminates function recreation and ensures mathematical consistency

##### 3.6.3.5 Fisher Information Matrix (FIM) Accuracy Calculation

**Statistical Foundation**:
- **Cramér-Rao Lower Bound**: Optimal theoretical accuracy bound for MLE estimates
- **Fisher Information Matrix**: Quantifies information content of measurements about location parameters
- **Covariance Matrix**: Σ = H⁻¹ where H is the Hessian matrix of negative log-likelihood function

**Mathematical Process**:
1. **Hessian Approximation**: Numerically compute 2×2 Hessian matrix using central finite differences
2. **Step Size Calculation**: `HESSIAN_DELTA = Math.pow(Math.ulp(1.0), 1.0/3.0)` ≈ 6.055e-6 (principled approach)
3. **Matrix Inversion**: LU decomposition for numerical stability with singular matrix detection
4. **Standard Errors**: σ_lat = √Σ₁₁, σ_lon = √Σ₂₂ from diagonal elements
5. **Horizontal Accuracy**: r = k × √(σ_lat² + σ_lon²) where k is confidence multiplier

**Confidence Levels**:
- **68% Confidence**: k = 1.0 (1 standard deviation)
- **95% Confidence**: k = 1.96 (1.96 standard deviations) - default
- **99% Confidence**: k = 2.58 (2.58 standard deviations)

**Research-Backed Accuracy Bounds**:
- **Minimum Accuracy**: 3.0 meters (framework-derived from WiFi positioning literature)
- **Maximum Accuracy**: 100.0 meters (framework-derived for poor measurement geometry)
- **Fallback Strategy**: Return maximum accuracy with diagnostic logging when FIM calculation fails

**Implementation Advantages**:
- **Statistically Optimal**: Provides theoretically optimal accuracy bounds under Gaussian noise assumptions
- **Problem-Specific**: Reflects actual measurement geometry and quality
- **Adaptive**: Automatically adjusts to measurement distribution and noise characteristics
- **Consistent**: Uses exact same weighting strategy as MLE optimization

##### 3.6.3.6 Research Citations and Validation

**Core MLE Theory**:
- **Kay, S.M. (1993)**: "Fundamentals of Statistical Signal Processing, Volume I: Estimation Theory" - Fisher Information theory
- **Van Trees, H.L. (2001)**: "Detection, Estimation, and Modulation Theory, Part I" - Cramér-Rao bounds

**RSSI-Based Localization**:
- **Patwari et al. (2005)**: "Locating the nodes: cooperative localization in wireless sensor networks" - RSSI-based MLE
- **Gezici et al. (2008)**: "Localization via Ultra-Wideband Radios: A Look at Positioning Aspects" - Positioning accuracy bounds

**Weighting Strategy Research**:
- **Open Access Surveying Library, Chapter E**: Inverse variance weighting in surveying applications
- **Statistical Estimation Theory**: Optimal weighting for measurements with different uncertainties
- **Positioning Systems Literature**: GPS accuracy as standard deviation proxies in sensor fusion

**Implementation Status**: ✅ **IMPLEMENTED AND VALIDATED**
- Complete MLE implementation with Apache Commons Math optimization
- FIM-based accuracy calculation with principled numerical methods
- Research-backed constants and weighting strategies
- Comprehensive unit tests with 100% coverage of critical paths
- Shared objective function architecture for mathematical consistency

#### 3.6.4 Bayesian Inference Requirements

**When Used**: After Phase 1 completion, for all subsequent processing
**Purpose**: Optimal fusion of prior knowledge with new measurements  
**Characteristics**: Provides uncertainty quantification, enables iterative learning
**Key Requirement**: Requires high-quality prior state from Phase 1

- **Prior Integration**:
  - Use previous location estimate as prior
  - Incorporate previous covariance matrix
  - Weight prior based on age and confidence
  
- **Implementation Notes**:
  - Consider Apache Commons Math for Bayesian calculations
  - Alternative: Smile or JScience libraries for statistical functions
  
- **Posterior Calculation**:
  - Combine prior with likelihood from measurements
  - Update covariance matrix for uncertainty
  - Return posterior estimate with confidence bounds

#### 3.6.5 State Management Requirements

##### 3.6.5.1 Build-up Phase State
- Track processing phase (BUILD_UP vs ITERATIVE)
- Maintain historical measurement data
- Count total measurements processed
- Store current location estimate and uncertainty
- Monitor data quality metrics

##### 3.6.5.2 Iterative Phase State  
- Store only current location estimate and covariance matrix
- Track last processing timestamp
- Maintain algorithm performance metrics
- Historical measurement data no longer needed

#### 3.6.6 Key Benefits of Two-Phase Approach

##### 3.6.6.1 Statistical Advantages
- **Superior Prior Quality**: MLE(100+) provides much better prior than WCL(25) or smaller MLE
- **Optimal Information Fusion**: Bayesian inference optimally combines prior + new data
- **Better Convergence**: High-quality prior leads to faster, more stable convergence
- **Robust Foundation**: Large initial dataset better handles outliers and noise

##### 3.6.6.2 Computational Advantages  
- **Initial Investment**: Higher computational cost during build-up phase
- **Long-term Efficiency**: Highly efficient iterative processing after phase switch
- **Memory Management**: Historical data deleted after Phase 1, only state maintained
- **Scalable**: Constant processing time and memory usage in Phase 2

##### 3.6.6.3 Practical Advantages
- **Framework Alignment**: Respects algorithm maturity tiers from research framework  
- **Quality Assurance**: Ensures sufficient data before using most sophisticated algorithm
- **Flexibility**: Can handle varying batch sizes and irregular data arrival patterns
- **Monitoring**: Clear phase indicators for operational monitoring and debugging

#### 3.6.7 Accuracy Weighting Research Foundation

##### 3.6.7.1 Inverse Variance Accuracy Weighting

**Research Foundation**: The service implements statistically correct inverse variance weighting for GPS accuracy measurements, following established principles from surveying, geodesy, and statistical estimation theory.

**Mathematical Formulation**:
```
weight = 1 / σ²
```
Where σ represents the GPS accuracy estimate (standard deviation of position error).

**Research Sources**:
- **Open Access Surveying Library, Chapter E**: "A measurement's weight is related to its error—the smaller the error, the greater the weight. The weight is inversely proportional to the square of its error." This principle is fundamental to weighted least squares estimation and measurement adjustment in surveying applications.
- **Statistical Estimation Theory**: Inverse variance weighting is the optimal approach for combining measurements with different uncertainties in maximum likelihood estimation and Bayesian inference frameworks.
- **Positioning Systems Literature**: Multiple studies in indoor/outdoor positioning systems use inverse variance weighting for sensor fusion, treating GPS accuracy estimates as standard deviation proxies.

**Implementation Rationale**:
- **Statistical Correctness**: Direct application of inverse variance weighting without arbitrary normalization constants
- **Research Validation**: Based on established surveying and statistical literature rather than ad-hoc scaling factors
- **Universal Applicability**: Same weighting approach used consistently across all localization algorithms (WCL, MLE, Bayesian)
- **No Arbitrary Thresholds**: Eliminates arbitrary minimum accuracy thresholds that lack research backing

**Quality Assurance**:
- GPS accuracy estimates treated as standard deviation proxies (common practice in positioning literature)
- Weight calculation follows standard statistical formulation: `weight = 1/(accuracy_meters²)`
- No normalization or scaling factors applied (pure inverse variance approach)
- Consistent application across all measurement weighting scenarios

### 3.7 Performance Requirements

#### 3.7.1 Throughput Requirements
- Process minimum 100 APs per minute across all service instances
- Complete single AP processing within 5-10 seconds
- Support horizontal scaling with multiple service instances
- Maintain processing rate under varying load

#### 3.7.2 Resource Constraints
- Maximum heap memory usage: 2GB
- CPU optimization for 2 vCPU cores
- Network bandwidth efficiency for large result sets
- Storage optimization for temporary query results

#### 3.7.3 Efficiency Targets
- Athena query response time < 10 seconds
- DynamoDB batch write efficiency > 80%
- Memory allocation efficiency > 70%

### 3.8 Reliability Requirements

#### 3.8.1 Availability Requirements
- Service availability: 99.9% uptime
- Automatic recovery from transient failures
- Graceful degradation under high load
- Support for rolling updates without downtime

#### 3.8.2 Data Integrity Requirements
- Zero data corruption during processing
- Idempotent message processing
- Consistent state updates in DynamoDB
- Accurate outlier detection without false positives

#### 3.8.3 Error Handling Requirements
- Comprehensive exception handling for all AWS operations
- Exponential backoff for transient errors
- Circuit breaker pattern for downstream services
- Dead letter queue for persistent failures

### 3.9 Monitoring and Observability Requirements

#### 3.9.1 Business Metrics Requirements
Track and export the following metrics:
- Total APs processed per minute
- Mobile hotspots detected count
- AP relocations detected count
- Average measurements per AP
- Localization confidence distribution
- Algorithm usage distribution

#### 3.9.2 Performance Metrics Requirements
- Athena query execution time
- DynamoDB write latency
- SQS message processing time
- Memory usage percentage
- CPU utilization
- Thread pool saturation

#### 3.9.3 Error Metrics Requirements
- Outlier detection failure rate
- Localization algorithm failures
- AWS service throttling occurrences
- Message processing failures
- DLQ message count

#### 3.9.4 Logging Requirements
- Structured JSON logging format
- Correlation ID for request tracking
- MDC (Mapped Diagnostic Context) for context
- Log levels: ERROR, WARN, INFO, DEBUG
- Separate application and audit logs

### 3.10 Health Check Requirements

#### 3.10.1 Readiness Probe Requirements
Service is ready when:
- SQS queue is accessible
- Athena database connection verified
- DynamoDB table accessible
- S3 result bucket writable
- Configuration loaded successfully

#### 3.10.2 Liveness Probe Requirements
Service is alive when:
- Processing thread pool responsive
- Memory usage below critical threshold (90%)
- No deadlocks detected
- Message processing loop active
- Last heartbeat within timeout

#### 3.10.3 Health Check Endpoints
- Provide `/health/ready` endpoint for readiness
- Provide `/health/live` endpoint for liveness
- Return appropriate HTTP status codes
- Include diagnostic information in response
- Support Kubernetes probe configuration

## 4. LocalStack Development Requirements

### 4.1 LocalStack Setup Requirements
- **LocalStack Version**: Use LocalStack Pro or Community Edition with required services enabled
- **Required Services**: SQS, S3, Athena, DynamoDB, CloudWatch, IAM, STS
- **Docker Integration**: LocalStack must run in Docker container with proper networking
- **Port Configuration**: Standard LocalStack ports (4566 for all services)
- **Data Persistence**: LocalStack data persistence for development data retention

### 4.2 LocalStack Service Configuration Requirements
- **SQS Configuration**: 
  - Create AP processing queue with proper attributes
  - Configure Dead Letter Queue (DLQ) for failed messages
  - Set appropriate visibility timeout and message retention
- **S3 Configuration**:
  - Create buckets for measurement data and query results
  - Configure bucket policies and CORS settings
  - Set up Apache Iceberg table structure
- **Athena Configuration**:
  - Create database and table definitions
  - Configure query result location
  - Set up proper IAM permissions
- **DynamoDB Configuration**:
  - Create AP state table with proper schema
  - Configure TTL settings
  - Set up local secondary indexes if needed
- **CloudWatch Configuration**:
  - Configure log groups and streams
  - Set up custom metrics and dashboards

### 4.3 LocalStack Data Management Requirements
- **Test Data Seeding**: Automated scripts to populate LocalStack with realistic test data
- **Data Cleanup**: Scripts to reset LocalStack state between test runs
- **Data Validation**: Tools to verify LocalStack data integrity
- **Performance Testing Data**: Large datasets for performance testing in LocalStack

### 4.4 LocalStack Development Workflow Requirements
- **Environment Detection**: Automatic detection of LocalStack vs AWS environment
- **Configuration Switching**: Seamless switching between LocalStack and AWS configurations
- **Service Health Checks**: Health checks that work with LocalStack services
- **Debugging Support**: Enhanced logging and debugging for LocalStack environment
- **Development Scripts**: Comprehensive scripts for LocalStack setup, teardown, and management

## 5. Configuration Requirements

### 5.1 Environment-Specific Configuration
- Override configuration via environment variables
- Secure credential management (no hardcoded secrets)
- Dynamic configuration updates without restart

### 5.2 AWS Service Configuration
- Configurable AWS region
- Support for IAM role-based authentication
- **LocalStack Endpoint Configuration**: Automatic detection and configuration of LocalStack endpoints
- **LocalStack Service URLs**: Configurable endpoints for SQS, S3, Athena, DynamoDB, CloudWatch
- **LocalStack Credentials**: Support for LocalStack's default access keys and region
- Timeout and retry configuration per service
- **Environment Detection**: Automatic switching between LocalStack and AWS based on environment

### 5.3 Processing Configuration
- Adjustable measurement lookback window
- Configurable outlier detection thresholds
- Tunable localization algorithm parameters
- Batch size and timing parameters

#### 5.3.1 Global Outlier Detection Configuration
- **MAD Multiplier**: Configurable threshold multiplier (default: 3.0)
- **Robustness Enhancement**: Enable/disable geometric median fallback (default: disabled)
- **Center Selection Threshold**: Distance threshold for centroid vs median selection (default: 150m)
- **Geometric Median Iterations**: Maximum iterations for Weiszfeld algorithm (default: 5)
- **Center Selection Logging**: Enable detailed logging of center selection reasoning

### 5.4 Monitoring Configuration
- Metrics export configuration (Prometheus format)
- Log level configuration per package
- Health check timeout configuration
- Alert threshold configuration

## 6. Security Requirements

### 6.1 Authentication and Authorization
- Use IAM roles for AWS service access
- Implement least privilege principle
- Support temporary credentials with STS
- Rotate credentials regularly

### 6.2 Data Protection
- Encrypt data in transit (TLS)
- Encrypt data at rest (AWS KMS)
- Sanitize logs to prevent data leakage
- Implement PII handling guidelines

### 6.3 Input Validation
- Validate MAC address format
- Sanitize SSID strings
- Verify coordinate boundaries
- Check timestamp validity

## 7. Testing Requirements

### 7.1 Unit Testing Requirements
- Minimum 80% code coverage
- Test all algorithm implementations
- Mock AWS service interactions
- Test error handling paths

### 7.2 Integration Testing Requirements
- Test complete processing pipeline
- **LocalStack Integration Tests**: Comprehensive testing with LocalStack for all AWS services
- **LocalStack Test Environment**: Automated LocalStack setup and teardown for integration tests
- **LocalStack Data Management**: Test data seeding and cleanup in LocalStack environment
- Test batch processing scenarios
- Validate error recovery mechanisms
- **LocalStack Service Simulation**: Verify behavior matches AWS service behavior

### 7.3 Performance Testing Requirements
- Load test with 1000 APs
- Memory leak detection tests
- Throughput validation tests
- Latency measurement tests

### 7.4 Test Data Requirements
- Generate realistic measurement data
- Create edge case scenarios
- Include mobile hotspot patterns
- Simulate AP relocation events

## 8. Documentation Requirements

### 8.1 Technical Documentation
- API documentation for all services
- Configuration parameter documentation
- Algorithm implementation details
- Error code reference

### 8.2 Operational Documentation
- Deployment procedures
- Monitoring and alerting guide
- Troubleshooting procedures
- Performance tuning guide

### 8.3 Development Documentation
- **LocalStack Setup Guide**: Comprehensive guide for setting up LocalStack development environment
- **LocalStack Configuration**: Documentation for configuring all AWS services in LocalStack
- **LocalStack Data Setup**: Guide for seeding test data and creating required resources
- **Local Development Workflow**: Step-by-step guide for local development using LocalStack
- Testing procedures
- Code contribution guidelines
- Architecture decision records

#### 8.3.1 Architecture Decision Records (ADRs)
- **ADR-001**: Global Outlier Detection Robustness Enhancement
  - Decision: Enable configurable geometric median fallback for skewed datasets
  - Rationale: Improve outlier detection accuracy for non-uniform measurement distributions
  - Implementation: Configuration-driven center selection with distance threshold guardrail
  - Status: Future enhancement (requires ADR approval before implementation)

- **ADR-002**: Inverse Variance Accuracy Weighting Implementation
  - Decision: Implement pure inverse variance weighting (1/σ²) for GPS accuracy measurements
  - Rationale: Replace arbitrary normalization approaches with statistically correct weighting based on surveying literature
  - Research Foundation: Open Access Surveying Library Chapter E, statistical estimation theory, positioning systems literature
  - Implementation: Direct application of `weight = 1/(accuracy_meters²)` without normalization constants
  - Status: Implemented and validated across all localization algorithms (WCL, MLE, Bayesian)

- **ADR-003**: Fisher Information Matrix (FIM) Accuracy Calculation
  - Decision: Implement statistically grounded accuracy calculation based on Fisher Information Matrix
  - Rationale: Replace heuristic accuracy estimation with theoretically optimal approach based on MLE theory
  - Research Foundation: Kay (1993), Van Trees (2001), Patwari et al. (2005), Gezici et al. (2008)
  - Implementation: Numerical Hessian approximation with principled step size calculation
  - Mathematical Process: Covariance matrix from inverse Hessian, standard errors, confidence intervals
  - Advantages: Statistically optimal, problem-specific, adaptive to measurement geometry
  - Status: ✅ **IMPLEMENTED AND VALIDATED** - Complete FIM-based accuracy calculation with comprehensive unit tests

- **ADR-004**: Shared Objective Function Architecture
  - Decision: Pass same MultivariateFunction from MLE optimization to FIM accuracy calculation
  - Rationale: Eliminate function duplication and guarantee mathematical consistency between optimization and accuracy calculation
  - Implementation: Single function creation in MLE, pass to FIM accuracy estimator
  - Benefits: Performance optimization, mathematical consistency, reduced code complexity
  - Status: ✅ **IMPLEMENTED** - Shared objective function eliminates duplication and ensures consistency

- **ADR-005**: Principled Numerical Constants
  - Decision: Replace magic numbers with research-backed or mathematically principled constants
  - Rationale: Improve code quality and maintainability by eliminating arbitrary constants
  - Implementation: 
    - HESSIAN_DELTA: `Math.pow(Math.ulp(1.0), 1.0/3.0)` ≈ 6.055e-6 (numerical analysis theory)
    - Confidence multipliers: Standard statistical constants (1.0, 1.96, 2.58)
    - Accuracy bounds: Framework-derived from WiFi positioning literature (3.0m - 100.0m)
  - Status: ✅ **IMPLEMENTED** - All magic numbers replaced with principled calculations

---

**This requirements document defines all functional and non-functional requirements for the WiFi AP Localization Service implementation.**