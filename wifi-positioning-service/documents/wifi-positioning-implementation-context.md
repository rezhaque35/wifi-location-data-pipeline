# WiFi Positioning Service Implementation Context

## Problem Statement & Requirements
Create a hybrid WiFi positioning system that combines multiple algorithms to provide accurate indoor positioning using WiFi access points.

### Key Requirements & Constraints
1. Support multiple positioning methods
2. Adapt to different scenarios (varying signal strengths, AP densities)
3. Provide accuracy metrics and confidence levels
4. Handle both 2D and 3D positioning
5. Support real-time positioning updates
6. Handle temporal variations in signal strength
7. **Single Measurement Constraint**: System must work with single measurements, not requiring historical or multiple measurements
8. **Limited Input Data**: Primary inputs are:
   - Required: RSSI and frequency only
   - Optional: Link speed, ssid
   - No environmental/external data available
   - Note: Channel is automatically derived from frequency internally
9. Access point location data is stored in DynamoDB table `wifi_access_points` with schema definition as follows:
   ```json
   {
     "TableName": "wifi_access_points",
     "AttributeDefinitions": [
       {
         "AttributeName": "mac_address",
         "AttributeType": "S"
       }
     ],
     "KeySchema": [
       {
         "AttributeName": "mac_address",
         "KeyType": "HASH"
       }
     ],
     "BillingMode": "PAY_PER_REQUEST"
   }
   ```
10. Data stored in the `wifi_access_points` table is in the following format:
    ```json
    {
      "mac_addr": {"S": "00:11:22:33:44:01"},
      "version": {"S": "20240411-120000"},
      "latitude": {"N": "37.7749"},
      "longitude": {"N": "-122.4194"},
      "altitude": {"N": "10.5"},
      "horizontal_accuracy": {"N": "50.0"},
      "vertical_accuracy": {"N": "8.0"},
      "confidence": {"N": "0.65"},
      "ssid": {"S": "SingleAP_Test"},
      "frequency": {"N": "2437"},
      "vendor": {"S": "Cisco"},
      "geohash": {"S": "9q8yyk"},
      "status": {"S": "active"}
    }
    ```
11. The status field in `wifi_access_points` database can have the following values:
    - `active`: Valid and current access point data
    - `error`: Data contains errors or inconsistencies
    - `expired`: Data is no longer valid 
    - `warning`: Data may be outdated or have reduced confidence
    - `wifi-hotspot`: Special designation for public WiFi hotspots
12. Application will only use data with status "active" or "warning" for calculations.
13. Application will include all found Access Point locations as part of response's calculation info element when flagged to send in request.

## Input Parameters and Their Roles

### Request Format

The WiFi Positioning Service exposes a REST API endpoint that accepts positioning requests at `/api/positioning/calculate`. The API uses HTTP POST method with JSON request and response bodies.

#### Core Request Parameters

1. **wifiScanResults** (Required)
   - Type: Array of WifiScanResult objects
   - Validation: 1-20 scan results required
   - Purpose: Contains scan results from visible WiFi access points
   - Each WifiScanResult contains:
     * **macAddress** (Required)
       - Format: String in XX:XX:XX:XX:XX:XX format
       - Validation: Must match MAC address pattern
       - Purpose: Unique identifier for AP matching against database
     
     * **signalStrength** (Required)
       - Format: Double (-100.0 to 0.0 dBm)
       - Validation: Must be between -100 and 0 dBm
       - Purpose: Primary metric for distance estimation
       - Algorithm weighting:
         * > -70 dBm: High weight in calculations (strong signal)
         * -70 to -85 dBm: Medium weight (medium signal)
         * < -85 dBm: Low weight (weak signal)
         * < -95 dBm: Very weak signal, only proximity algorithm used
     
     * **frequency** (Required)
       - Format: Integer (2400-6000 MHz)
       - Validation: Must be between 2400 and 6000 MHz
       - Purpose: Determines signal propagation characteristics
       - Impact:
         * 2.4 GHz band (2400-2500 MHz): Better penetration, longer range
         * 5 GHz band (5000-6000 MHz): More precise, shorter range
     
     * **ssid** (Optional)
       - Format: String
       - Purpose: Network identification and AP grouping
     
     * **linkSpeed** (Optional)
       - Format: Integer (Mbps)
       - Validation: Must be non-negative
       - Purpose: Connection quality assessment in advanced algorithms
     
     * **channelWidth** (Optional)
       - Format: Integer (20-160 MHz)
       - Validation: Must be between 20 and 160 MHz
       - Purpose: Better path loss estimation in trilateration

2. **client** (Required)
   - Type: String (max 50 characters)
   - Validation: Cannot be blank, max 50 characters
   - Purpose: Identifies the client system making the request
   - Usage: Included in response, used for logging and analytics

3. **requestId** (Required)
   - Type: String (max 64 characters)
   - Validation: Cannot be blank, max 64 characters
   - Purpose: Unique identifier for the request
   - Usage: Included in response for request correlation

4. **application** (Optional)
   - Type: String (max 100 characters)
   - Validation: Max 100 characters if provided
   - Purpose: Identifies the application making the request
   - Usage: Included in response, used for analytics

5. **calculationDetail** (Optional)
   - Type: Boolean
   - Default: false
   - Purpose: When true, includes detailed calculation information in response
   - Usage: Provides algorithm selection reasoning and processing details

### Response Format

The service responds with a flattened JSON structure that combines API metadata and positioning data for easier consumption:

#### Response Fields

1. **API Metadata Fields**
   - **result**: String - "SUCCESS" or "ERROR"
   - **message**: String - Success or error message
   - **requestId**: String - Echo of original request ID
   - **client**: String - Echo of client identifier
   - **application**: String - Echo of application identifier (if provided)
   - **timestamp**: Long - Response timestamp (epoch milliseconds)

2. **Position Data Fields** (in wifiPosition object, null for errors)
   - **latitude**: Double - Latitude in degrees
   - **longitude**: Double - Longitude in degrees
   - **altitude**: Double - Altitude in meters (optional)
   - **horizontalAccuracy**: Double - Horizontal accuracy in meters
   - **verticalAccuracy**: Double - Vertical accuracy in meters (if altitude provided)
   - **confidence**: Double - Confidence level (0.0-1.0)
   - **methodsUsed**: Array - Algorithms used for positioning
   - **apCount**: Integer - Number of APs used in calculation
   - **calculationTimeMs**: Long - Calculation time in milliseconds

3. **Calculation Information**
   - **calculationInfo**: String - Detailed calculation information (present only when calculationDetail=true)

### Sample Request and Response

#### Example 1: Single AP Positioning (Proximity Method)

**Request:**
```json
{
    "wifiScanResults": [{
        "macAddress": "00:11:22:33:44:01",
        "ssid": "SingleAP_Test",
        "signalStrength": -65.0,
        "frequency": 2437
    }],
    "client": "test-client",
    "requestId": "test-request-1",
    "application": "wifi-positioning-test-suite"
}
```

**Response:**
```json
{
  "result": "SUCCESS",
  "message": "Request processed successfully",
  "requestId": "test-request-1",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1746821320281,
  "wifiPosition": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 10.5,
    "horizontalAccuracy": 52.3,
    "verticalAccuracy": 0.0,
    "confidence": 0.45,
    "methodsUsed": ["proximity"],
    "apCount": 1,
    "calculationTimeMs": 18
  }
}
```

#### Example 2: Multiple APs Positioning (Weighted Centroid and RSSI Ratio)

**Request:**
```json
{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:02",
            "signalStrength": -68.5,
            "frequency": 5180,
            "ssid": "DualAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:03",
            "signalStrength": -62.3,
            "frequency": 2462,
            "ssid": "TriAP_Test"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-2",
    "application": "wifi-positioning-test-suite"
}
```

**Response:**
```json
{
  "result": "SUCCESS",
  "message": "Request processed successfully",
  "requestId": "test-request-2",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1746821320781,
  "wifiPosition": {
    "latitude": 37.7750,
    "longitude": -122.4195,
    "altitude": 10.2,
    "horizontalAccuracy": 65.8,
    "verticalAccuracy": 0.0,
    "confidence": 0.52,
    "methodsUsed": ["weighted_centroid", "rssi_ratio"],
    "apCount": 2,
    "calculationTimeMs": 24
  }
}
```

#### Example 3: Error Response (Invalid or Missing APs)

**Request:**
```json
{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:36",
            "signalStrength": -99.9,
            "frequency": 2412,
            "ssid": "ErrorCase_invalid_coordinates"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-36",
    "application": "wifi-positioning-test-suite"
}
```

**Response:**
```json
{
  "result": "ERROR",
  "message": "No valid access points found in database",
  "requestId": "test-request-36",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1746821321281,
  "wifiPosition": null
}
```

#### Example 4: Request with Calculation Detail

**Request:**
```json
{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:41",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "StatusTest_41"
        },
        {
            "macAddress": "00:11:22:33:44:42",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "StatusTest_42"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-status-filtering",
    "application": "wifi-positioning-test-suite",
    "calculationDetail": true
}
```

**Response:**
```json
{
  "result": "SUCCESS",
  "message": "Request processed successfully",
  "requestId": "test-request-status-filtering",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1746821321781,
  "wifiPosition": {
    "latitude": 37.7751,
    "longitude": -122.4196,
    "altitude": 10.1,
    "horizontalAccuracy": 22.4,
    "verticalAccuracy": 0.0,
    "confidence": 0.72,
    "methodsUsed": ["weighted_centroid", "rssi_ratio"],
    "apCount": 2,
    "calculationTimeMs": 28
  },
  "calculationInfo": "Algorithm selection: Initial candidates=[weighted_centroid, rssi_ratio, log_distance]. Signal quality=Medium. AP Count=2. Final selection=[weighted_centroid (weight=0.728), rssi_ratio (weight=0.560)]. Distance estimates: AP1=42.8m, AP2=42.8m."
}
```

## Implementation Details

### 1. Data Preprocessing
- Input validation and normalization
  * Verify required fields (macAddress, signalStrength, frequency)
  * Convert units if needed
  * Derive channel from frequency
  * Filter out invalid or extremely weak signals

### Access Point Location Data

The WiFi Positioning Service relies on a DynamoDB table `wifi_access_points` for storing and retrieving access point location information. This data is crucial for accurate positioning calculations.

#### Database Schema

The DynamoDB table uses the following schema:
```json
{
  "TableName": "wifi_access_points",
  "AttributeDefinitions": [
    {
      "AttributeName": "mac_address",
      "AttributeType": "S"
    }
  ],
  "KeySchema": [
    {
      "AttributeName": "mac_address",
      "KeyType": "HASH"
    }
  ],
  "BillingMode": "PAY_PER_REQUEST"
}
```

#### Data Fields

Each access point record contains the following fields:

1. **Primary Key**
   - `mac_addr` (String): Unique identifier for the access point in XX:XX:XX:XX:XX:XX format
   - Used as the hash key for DynamoDB lookups

2. **Location Data**
   - `latitude` (Number): Geographic latitude in decimal degrees
   - `longitude` (Number): Geographic longitude in decimal degrees
   - `altitude` (Number): Height above ground level in meters
   - `horizontal_accuracy` (Number): Estimated horizontal position accuracy in meters
   - `vertical_accuracy` (Number): Estimated vertical position accuracy in meters

3. **Signal Characteristics**
   - `frequency` (Number): Operating frequency in MHz (e.g., 2437 for 2.4GHz channel 6)
   - `ssid` (String): Network name identifier
   - `vendor` (String): Manufacturer of the access point

4. **Metadata**
   - `version` (String): Timestamp-based version identifier (format: YYYYMMDD-HHMMSS)
   - `geohash` (String): Geohash representation of the location for spatial queries
   - `confidence` (Number): Confidence level of the location data (0.0-1.0)
   - `status` (String): Current status of the access point record

#### Status Values

The `status` field can have the following values:
- `active`: Valid and current access point data
- `warning`: Data may be outdated or have reduced confidence
- `error`: Data contains errors or inconsistencies
- `expired`: Data is no longer valid
- `wifi-hotspot`: Special designation for public WiFi hotspots

#### Data Usage Rules

1. **Status Filtering**
   - Only records with `status` = "active" or "warning" are used in position calculations
   - Other statuses are excluded to ensure data quality

2. **Version Control**
   - The `version` field helps track data freshness
   - Newer versions are preferred when multiple records exist for the same MAC address

3. **Confidence Integration**
   - The `confidence` value influences algorithm weighting
   - Higher confidence records receive greater weight in position calculations

4. **Accuracy Metrics**
   - `horizontal_accuracy` and `vertical_accuracy` are used to:
     * Adjust final position accuracy estimates
     * Weight different access points in multi-AP scenarios
     * Validate position results

5. **Geohash Usage**
   - Enables efficient spatial queries
   - Helps identify nearby access points
   - Supports quick filtering of irrelevant access points

#### Example Record

```json
{
    "mac_addr": {"S": "00:11:22:33:44:01"},
    "version": {"S": "20240411-120000"},
    "latitude": {"N": "37.7749"},
    "longitude": {"N": "-122.4194"},
    "altitude": {"N": "10.5"},
    "horizontal_accuracy": {"N": "50.0"},
    "vertical_accuracy": {"N": "8.0"},
    "confidence": {"N": "0.65"},
    "ssid": {"S": "SingleAP_Test"},
    "frequency": {"N": "2437"},
    "vendor": {"S": "Cisco"},
    "geohash": {"S": "9q8yyk"},
    "status": {"S": "active"}
}
```

### Primary Algorithms (Using only RSSI and Frequency)

The WiFi Positioning Service implements several algorithms that derive location from WiFi signals, each with different approaches and strengths. All algorithms implement the `PositioningAlgorithm` interface, which defines the standard method `calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs)`.

1. **Proximity Detection** (`ProximityAlgorithm.java`)
   - **Approach**: Identifies the AP with strongest signal and uses its location as the user's position
   - **Mathematical Model**: Position = Position of strongest AP
   - **Key Formula**: 
     ```
     bestAP = argmax(signalStrength) for all APs
     position = bestAP.location
     confidence = min(0.65, bestAP.confidence * signalQualityFactor)
     accuracy = max(15, bestAP.horizontalAccuracy * (1 + signalFactor))
     ```
     where signalFactor = (|signalStrength| - 60) / 35 [scaled 0-1]
   - **Accuracy**: Low (±15-50m)
   - **Confidence**: 0.35-0.65 (signal strength dependent)
   - **Best for**: Single AP scenarios with strong signals

2. **RSSI Ratio Method** (`RSSIRatioAlgorithm.java`)
   - **Approach**: Uses ratios of signal strengths to estimate relative distances from multiple APs
   - **Mathematical Model**: Distance ratios correspond to signal strength ratios
   - **Key Formula**:
     ```
     distanceRatio(AP1,AP2) = 10^((RSSI2 - RSSI1)/(10 * pathLossExponent))
     w(i) = 10^(signalStrength(i)/10) [weight of each AP]
     position = interpolatePositions(AP positions, distanceRatios, weights)
     ```
     Interpolation uses weighted averaging based on distance ratios
   - **Accuracy**: Medium (±8-25m)
   - **Confidence**: 0.40-0.75 (geometry dependent)
   - **Best for**: 2-3 APs with similar signal strengths

3. **Log-Distance Path Loss Model** (`LogDistanceAlgorithm.java`)
   - **Approach**: Estimates distances using physics-based signal propagation models
   - **Mathematical Model**: Signal strength decreases logarithmically with distance
   - **Key Formula**:
     ```
     distance = 10^((A - RSSI)/(10 * n))
     ```
     where:
     - A = reference power at 1m (calibrated by frequency, typically -40dBm at 2.4GHz)
     - n = path loss exponent (typically 2.0-4.0, environment dependent)
     - RSSI = measured signal strength in dBm
   - **Distance to Position Conversion**:
     ```
     weights(i) = 1/distance(i)^2
     position = Σ(AP positions * weights) / Σ(weights)
     ```
   - **Accuracy**: Medium (±10-30m)
   - **Confidence**: 0.45-0.80 (environment dependent)
   - **Best for**: Known environment characteristics

4. **Weighted Centroid** (`WeightedCentroidAlgorithm.java`)
   - **Approach**: Calculates position as weighted average of AP locations
   - **Mathematical Model**: User position is center of mass of weighted AP locations
   - **Key Formula**:
     ```
     w(i) = (signalStrength(i) + 100)^α
     position = Σ(AP positions * w(i)) / Σ(w(i))
     ```
     where α is the weight exponent (typically 1.5-2.5)
   - **Confidence Calculation**:
     ```
     standardDeviation = √(Σ(distance(position, AP)^2 * w(i)) / Σ(w(i)))
     confidence = min(0.75, 1.0/(1.0 + standardDeviation/100))
     ```
   - **Accuracy**: Medium (±8-20m)
   - **Confidence**: 0.40-0.75 (AP distribution dependent)
   - **Best for**: Well-distributed APs with mixed signals

### Enhanced Algorithms (When Additional Data Available)

1. **Maximum Likelihood with Limited Data** (`MaximumLikelihoodAlgorithm.java`)
   - **Approach**: Finds position with highest probability given observed signals
   - **Mathematical Model**: Maximizes probability function across possible locations
   - **Key Formula**:
     ```
     P(x,y) = Π P(RSSI(i) | distance(x,y,AP(i)))
     ```
     where P(RSSI|distance) models probability of observing a signal strength at given distance

   - **Implementation Details**:
     ```
     logLikelihood(x,y) = Σ(log(P(RSSI(i) | distance(x,y,AP(i)))))
     position = argmax(logLikelihood) from grid search
     ```
     AP quality factors modify probabilities based on link speed (if available)
   - **Accuracy**: Medium-High (±4-12m)
   - **Confidence**: 0.45-0.90 (signal quality dependent)
   - **Best for**: 4+ APs with strong signals

2. **Modified Trilateration** (`TriangulationAlgorithm.java`)
   - **Approach**: Determines position by solving for intersection of distance spheres
   - **Mathematical Model**: Minimizes error in system of quadratic equations
   - **Key Formula**:
     ```
     ||p - p_i||^2 = d_i^2 for each AP(i)
     ```
     where:
     - p = user position (x,y,z)
     - p_i = position of AP(i)
     - d_i = estimated distance to AP(i)

   - **Implementation**:
     ```
     Linearized form: Ax = b
     A = 2[ (p1-pn)' (p2-pn)' ... (pn-1-pn)' ]
     b = [ ||p1||^2 - ||pn||^2 - d1^2 + dn^2, ..., ||pn-1||^2 - ||pn||^2 - dn-1^2 + dn^2 ]
     Solution: x = (A'A)^-1 A'b
     ```
     Incorporates GDOP calculation (listed below) to assess geometric quality
   - **Accuracy**: Medium-High (±5-15m)
   - **Confidence**: 0.50-0.85 (geometry dependent)
   - **Implementation includes Geometric Dilution of Precision (GDOP)**:
     ```
     GDOP = √(trace((H'H)^-1))
     ```
     where H is the geometry matrix with unit vectors from position to each AP
   - **Best for**: 3+ APs with good geometric distribution

### Trilateration Algorithm Enhancement
The trilateration algorithm has been enhanced with GDOP (Geometric Dilution of Precision) calculation to improve accuracy estimation and confidence metrics.

#### GDOP Implementation
- **Mathematical Model**: GDOP = sqrt(trace((H^T * H)^-1))
  - H is the geometry matrix with unit vectors from position to each AP
  - Measures how AP geometric distribution affects positioning accuracy
  - Lower values indicate better geometry, higher values indicate poorer geometry

- **GDOP Quality Classifications**:
  - Excellent: GDOP < 2.0 
  - Good: 2.0 ≤ GDOP < 4.0
  - Fair: 4.0 ≤ GDOP < 6.0
  - Poor: GDOP ≥ 6.0

- **Effects on Accuracy Estimation**:
  - Strong signals: Accuracy ranges from 1-5m, adjusted by GDOP
  - Weak signals: Base accuracy scaled by GDOP factor
  - Poor geometry significantly increases accuracy values (worse accuracy)

- **Effects on Confidence Calculation**:
  - Confidence reduced for poor AP geometry
  - Strong signals: Minor GDOP influence to maintain high confidence
  - Weak signals: Stronger GDOP influence, further reducing confidence
  - Medium signals: Balanced GDOP influence

- **Implementation Benefits**:
  - More realistic accuracy estimates based on AP geometry
  - Improved confidence metrics that reflect positioning quality
  - Better handling of challenging AP distributions
  - Enhanced error detection for collinear/problematic AP arrangements

The GDOP implementation satisfies the improvement suggestion for "Implement GDOP for better accuracy estimation" while maintaining compatibility with existing tests. 

## WiFi Positioning Hybrid Algorithm Selection 

This document outlines the algorithm selection framework implemented in the WiFi Positioning Service to select the optimal positioning algorithms based on the scenario characteristics.

### Overview

The framework uses a three-phase process for optimal algorithm selection:

1. **Hard Constraints (Disqualification Phase)** - Eliminate algorithms that are mathematically or practically invalid
2. **Algorithm Weighting (Ranking Phase)** - Assign and adjust weights based on various factors
3. **Finalist Selection (Combination Phase)** - Select the final set of algorithms based on weights

### 1. Hard Constraints (Disqualification Phase)

First, we eliminate algorithms that are mathematically or practically invalid for the scenario:

| Constraint | Action |
|------------|--------|
| AP Count = 1 | Only include Proximity and Log Distance; remove all others |
| AP Count = 2 | Remove Trilateration and Maximum Likelihood (mathematically underdetermined) |
| AP Count ≥ 3 | All algorithms eligible (subject to other constraints) |
| Collinear APs detected | Remove Trilateration (mathematically invalid) |
| Extremely weak signals (all < -95 dBm) | Remove all except Proximity |

### 2. Algorithm Weighting (Ranking Phase)

For remaining eligible algorithms, we apply base weights according to AP count:

#### Base Weights by AP Count

| AP Count | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|----------|-----------|------------|-------------------|---------------|-------------------|--------------|
| 1 | 1.0 | - | - | - | - | 0.4 |
| 2 | 0.4 | 1.0 | 0.8 | - | - | 0.5 |
| 3 | 0.3 | 0.7 | 0.8 | 1.0 | - | 0.5 |
| 4+ | 0.2 | 0.5 | 0.7 | 0.8 | 1.0 | 0.4 |

#### Signal Quality Adjustments

| Signal Quality | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|----------------|-----------|------------|-------------------|---------------|-------------------|--------------|
| Strong (> -70 dBm) | ×0.9 | ×1.0 | ×1.0 | ×1.1 | ×1.2 | ×1.0 |
| Medium (-70 to -85 dBm) | ×0.7 | ×0.9 | ×1.0 | ×0.8 | ×0.9 | ×0.8 |
| Weak (< -85 dBm) | ×0.4 | ×0.6 | ×0.8 | ×0.3 | ×0.5 | ×0.6 |
| Very Weak (< -95 dBm) | ×0.5 | ×0.0 | ×0.0 | ×0.0 | ×0.0 | ×0.0 |

#### Geometric Quality Adjustments

| Geometric Quality | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|-------------------|-----------|------------|-------------------|---------------|-------------------|--------------|
| Excellent GDOP (< 2) | ×1.0 | ×1.0 | ×1.0 | ×1.3 | ×1.2 | ×1.0 |
| Good GDOP (2-4) | ×1.0 | ×1.0 | ×1.1 | ×0.9 | ×1.1 | ×1.0 |
| Fair GDOP (4-6) | ×1.0 | ×0.9 | ×1.2 | ×0.6 | ×0.9 | ×0.8 |
| Poor GDOP (> 6) | ×1.0 | ×0.8 | ×1.3 | ×0.3 | ×0.7 | ×0.7 |
| Collinear APs | ×1.0 | ×0.7 | ×1.4 | ×0.0 | ×0.5 | ×0.6 |

### Signal Distribution Adjustments

| Distribution Pattern | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|----------------------|-----------|------------|-------------------|---------------|-------------------|--------------|
| Uniform signal levels | ×1.0 | ×1.2 | ×1.0 | ×1.1 | ×0.9 | ×1.1 |
| Mixed signal levels | ×0.7 | ×0.9 | ×1.8 | ×0.8 | ×1.1 | ×0.8 |
| Signal outliers present | ×0.9 | ×0.7 | ×1.4 | ×0.5 | ×1.2 | ×0.8 |


#### Test Case Examples and Algorithm Selection

##### 1. Single AP Test (Test Case 1)
- **Input**: Single AP with -65.0 dBm signal at 2.4GHz
- **Base Weights**: 
  * Proximity: 1.0
  * Log Distance: 0.4
- **Adjustments**:
  * Signal Quality (Strong): ×0.9
  * GDOP (Poor): ×0.7
  * Distribution (Uniform): ×1.1
- **Final Weights**:
  * Proximity: 1.0 × 0.9 = 0.9 (Selected)
  * Log Distance: 0.4 × 1.0 × 0.7 × 1.1 = 0.308 (Below threshold)
- **Expected**: Position with accuracy 45-55m, confidence 0.35-0.55

##### 2. Two APs Test (Test Case 2)
- **Input**: Two APs with -68.5 dBm and -62.3 dBm
- **Base Weights**:
  * RSSI Ratio: 1.0
  * Weighted Centroid: 0.8
  * Proximity: 0.4
  * Log Distance: 0.5
- **Adjustments**:
  * Signal Quality (Strong): ×1.0
  * GDOP (Poor): ×0.8 for RSSI Ratio, ×1.3 for Weighted Centroid
  * Distribution (Uniform): ×1.2 for RSSI Ratio, ×1.0 for Weighted Centroid
- **Final Weights**:
  * Weighted Centroid: 0.8 × 1.0 × 1.3 × 1.0 = 1.04 (Primary)
  * RSSI Ratio: 1.0 × 1.0 × 0.8 × 1.2 = 0.96 (Secondary)
  * Log Distance: 0.5 × 1.0 × 0.7 × 1.1 = 0.385 (Below threshold)
  * Proximity: 0.4 × 0.9 × 1.0 × 1.0 = 0.36 (Below threshold)
- **Expected**: Accuracy 55-70m, confidence 0.40-0.60

##### 3. Three APs Test (Test Case 3)
- **Input**: Three APs with varying signal strengths (-62.3, -71.2, -85.5 dBm)
- **Base Weights**:
  * Trilateration: 1.0
  * Weighted Centroid: 0.8
  * RSSI Ratio: 0.7
- **Adjustments**:
  * Signal Quality (Medium): ×0.7
  * GDOP (Poor): ×0.7 for Trilateration, ×1.3 for Weighted Centroid
  * Distribution (Signal Outliers): ×0.8 for Trilateration, ×1.0 for Weighted Centroid
- **Final Weights**:
  * Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
  * RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528 (Secondary)
  * Trilateration: 1.0 × 0.7 × 0.7 × 0.8 = 0.392 (Below threshold)
- **Expected**: Accuracy 90-105m, confidence 0.35-0.55

##### 4. Collinear APs Test (Test Cases 6-10)
- **Input**: Three APs in linear arrangement (-70.0, -68.0, -66.0 dBm)
- **Base Weights**:
  * Weighted Centroid: 0.8
  * RSSI Ratio: 0.7
- **Adjustments**:
  * Signal Quality (Medium): ×0.7
  * GDOP (Collinear): ×1.4 for Weighted Centroid, ×0.7 for RSSI Ratio
  * Distribution (Collinear): ×1.0 for Weighted Centroid, ×0.9 for RSSI Ratio
- **Final Weights**:
  * Weighted Centroid: 0.8 × 0.7 × 1.4 × 1.0 = 0.784 (Primary)
  * RSSI Ratio: 0.7 × 0.7 × 0.7 × 0.9 = 0.3087 (Secondary)
  * Trilateration: 0.0 (Disqualified due to collinear geometry)
- **Expected**: Accuracy 70-85m, confidence 0.35-0.45

##### 5. High Density Cluster Test (Test Cases 11-15)
- **Input**: Four APs with strong signals (-65.0 to -60.5 dBm)
- **Base Weights**:
  * Maximum Likelihood: 1.0
  * Trilateration: 0.8
  * Weighted Centroid: 0.7
- **Adjustments**:
  * Signal Quality (Strong): ×0.9
  * GDOP (Poor): ×0.7 for Maximum Likelihood, ×1.3 for Weighted Centroid
  * Distribution (Mixed): ×0.8 for Maximum Likelihood, ×1.0 for Weighted Centroid
- **Final Weights**:
  * Weighted Centroid: 0.7 × 0.9 × 1.3 × 1.0 = 0.819 (Primary)
  * Maximum Likelihood: 1.0 × 0.9 × 0.7 × 0.8 = 0.504 (Secondary)
  * Trilateration: 0.8 × 0.9 × 0.7 × 0.8 = 0.4032 (Below threshold)
- **Expected**: Accuracy 50-60m, confidence 0.35-0.55

##### 6. Stable Signal Quality Test (Test Cases 31-35)
- **Input**: Two APs with identical signal strengths (-68.0 dBm)
- **Base Weights**:
  * RSSI Ratio: 1.0
  * Weighted Centroid: 0.8
- **Adjustments**:
  * Signal Quality (Medium): ×0.7
  * GDOP (Poor): ×0.8 for RSSI Ratio, ×1.3 for Weighted Centroid
  * Distribution (Stable): ×1.0 for both methods
- **Final Weights**:
  * Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
  * RSSI Ratio: 1.0 × 0.7 × 0.8 × 1.0 = 0.560 (Secondary)
- **Expected**: Accuracy 5-15m, confidence 0.65-0.80

#### Error Cases and Edge Scenarios

##### 1. Very Weak Signal Test (Test Case 38)
- **Input**: Single AP with -99.9 dBm signal
- **Base Weights**:
  * Proximity: 1.0
  * Log Distance: 0.4
- **Adjustments**:
  * Signal Quality (Very Weak): ×0.5 for Proximity, ×0.0 for others
  * GDOP (Poor): ×0.7
  * Distribution (Uniform): ×1.1
- **Final Weights**:
  * Proximity: 1.0 × 0.5 × 0.7 × 1.1 = 0.385 (Selected)
  * Log Distance: 0.4 × 0.0 = 0.0 (Below threshold)
- **Expected**: Accuracy 5-15m, confidence 0.0-0.1

#### #2. Algorithm Failure Test (Test Case 39)
- **Input**: Three APs with physically impossible signal relationships
- **Result**: ERROR status
- **Rationale**: Signal relationships violate physical constraints
- **Expected**: Error response with appropriate message

### Access Point Location Data Used by Different Algorithms

The WiFi positioning service leverages different data fields from WifiAccessPoint and WifiScanResult classes depending on the specific algorithm being used. This table outlines which data fields are utilized by each algorithm and the position combiner.

#### Data Field Usage by Algorithm

| Data Field | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Log Distance | Max Likelihood | Position Combiner |
|------------|:---------:|:----------:|:-----------------:|:-------------:|:------------:|:--------------:|:-----------------:|
| **WifiAccessPoint Fields** |
| macAddress | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| latitude | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| longitude | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| altitude | ✓ | ○ | ✓ | ✓ | ○ | ✓ | ✓ |
| horizontalAccuracy | ✓ | ○ | ✓ | ✓ | ✓ | ✓ | ✓ |
| verticalAccuracy | ○ | ○ | ○ | ✓ | ○ | ✓ | ○ |
| confidence | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| ssid | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| frequency | ○ | ○ | ○ | ○ | ✓ | ○ | ○ |
| vendor | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| status | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| geohash | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| **WifiScanResult Fields** |
| macAddress | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| signalStrength | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| frequency | ○ | ○ | ○ | ○ | ✓ | ✓ | ○ |
| ssid | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| linkSpeed | ○ | ○ | ○ | ○ | ○ | ✓ | ○ |
| channelWidth | ○ | ○ | ○ | ✓ | ✓ | ○ | ○ |

Legend:
- ✓ = Essential field for algorithm calculation
- ○ = Supplementary field (used if available, but not essential)

#### Algorithm-Specific Data Usage Details

1. **Proximity Algorithm**
   - **Primary Fields**: macAddress, latitude, longitude, signalStrength
   - **Usage**: Selects the closest AP based on signal strength and returns its location
   - **Notable Dependencies**: Heavily relies on WifiAccessPoint.horizontalAccuracy for error estimation

2. **RSSI Ratio Method**
   - **Primary Fields**: signalStrength (multiple APs), latitude, longitude
   - **Usage**: Uses relative signal strength differences between APs without absolute calibration
   - **Key Calculations**: Compares signal strength ratios to distance ratios

3. **Weighted Centroid**
   - **Primary Fields**: signalStrength, latitude, longitude, altitude
   - **Usage**: Weights AP locations by signal strength to calculate a position
   - **Mathematics**: Position = Σ(location × weight) / Σ(weight), where weight is derived from signalStrength

4. **Trilateration**
   - **Primary Fields**: signalStrength, latitude, longitude, altitude, horizontalAccuracy
   - **Usage**: Calculates position by triangulation from multiple AP distances
   - **Advanced Usage**: Incorporates channelWidth for better path loss estimation
   - **GDOP Analysis**: Uses geometric distribution of APs to assess position quality

5. **Log Distance Path Loss Model**
   - **Primary Fields**: signalStrength, frequency, horizontalAccuracy
   - **Usage**: Uses frequency-dependent signal propagation modeling
   - **Notable Dependency**: WifiAccessPoint.frequency and WifiScanResult.frequency for propagation characteristics

6. **Maximum Likelihood Method**
   - **Primary Fields**: signalStrength, latitude, longitude, altitude, confidence
   - **Advanced Fields**: linkSpeed for connection quality assessment
   - **Usage**: Statistical maximum likelihood estimation of position
   - **Complex Integration**: Combines multiple data fields to improve statistical estimates

7. **Position Combiner**
   - **Primary Fields**: latitude, longitude, altitude, horizontalAccuracy, confidence
   - **Usage**: Combines multiple algorithm results based on confidence and geometric quality
   - **Mathematical Process**: Weighted average with geometric quality adjustments
   - **Advanced Analysis**: Performs covariance analysis to detect collinear configurations

### Weighted Algorithm Result Combiner to derive final Location

The WiFi Positioning Service utilizes a sophisticated weighted algorithm result combiner to derive the final location from multiple positioning algorithms. This process is implemented in the `WeightedAveragePositionCombiner` class and follows these key steps:

1. **Algorithm Selection and Execution**
   - Multiple positioning algorithms are selected based on the algorithm selection framework
   - Each algorithm produces a position with its own confidence level
   - Algorithms are assigned weights based on their suitability for the scenario

2. **Weighted Position Combination**
   - Each position is weighted according to its algorithm's confidence and scenario-specific weight
   - A normalized weight is calculated for each position: `normalizedWeight = algorithmWeight / totalWeight`
   - Weighted coordinates are calculated: 
     ```
     weightedLat = Σ(position.latitude × normalizedWeight)
     weightedLon = Σ(position.longitude × normalizedWeight)
     weightedAlt = Σ(position.altitude × normalizedWeight)
     ```

3. **Geometric Quality Assessment**
   - The system analyzes how well the estimated positions from different algorithms are distributed in space
   - It calculates a covariance matrix that measures the spatial relationships between estimated positions
   - This statistical tool reveals how positions vary together and identifies geometric weaknesses in the positioning
   - The condition number is extracted from this matrix as a key indicator of geometric quality
   - Higher condition numbers indicate poorer geometric distribution of reference points
   - The system specifically checks for collinearity, a problematic condition where access points form a straight line
   - Collinearity creates ambiguity in positioning as multiple locations could equally satisfy the measurements
   - A geometric quality factor is computed that combines both condition number analysis and collinearity detection
   - This factor serves as a scaling parameter for both accuracy and confidence adjustments
   - Strong geometric distributions with well-spaced access points receive minimal adjustments
   - Poor distributions or collinear arrangements receive significant corrections to properly reflect positioning uncertainty

4. **Output Adjustment**
   - Confidence and accuracy values are adjusted based on geometric quality
   - Final position incorporates adjusted accuracy and confidence values
   - Methods used in calculation are tracked and included in response

This approach ensures that the final position properly accounts for the geometric distribution of access points, the quality of signals, and the strengths of different positioning algorithms. By combining multiple algorithms, the system achieves superior accuracy compared to single-algorithm approaches while adapting to different environmental conditions.

#### Handling Special Geometric Cases

The combiner implements specific handling for challenging geometric configurations:

1. **Collinear Access Points**
   - When access points are aligned in a straight line, trilateration becomes mathematically invalid
   - The system detects collinearity and increases weights for algorithms like Weighted Centroid
   - Confidence values are capped at a maximum of 0.69 for collinear configurations
   - Minimum accuracy is enforced at 6.0 meters for collinear cases

2. **Poor Geometric Dilution of Precision (GDOP)**
   - High GDOP values indicate poor geometric distribution of reference points
   - Accuracy values are scaled proportionally to the geometric quality factor
   - For poor geometries, the scaled base accuracy is increased: `baseAccuracy * geometricQualityFactor`

3. **Signal Outliers**
   - When signal strength distribution contains outliers, algorithms are weighted differently
   - Weighted Centroid receives increased weight (×1.4) while Trilateration is reduced (×0.5)

### Confidence Calculation

Confidence values in the WiFi Positioning System represent the statistical reliability of the calculated position and are critical for downstream applications to evaluate positioning quality. The system calculates confidence through a multi-step process:

1. **Individual Algorithm Confidence**
   - Each positioning algorithm generates a base confidence value (0.0-1.0)
   - These values reflect algorithm-specific assessments of position reliability
   - Factors affecting base confidence include:
     * Signal strength quality (-70 dBm: high confidence, -85 dBm: medium, -95 dBm: low)
     * Number of access points used (more APs generally increase confidence)
     * Signal consistency (consistent signal levels increase confidence)

2. **Combined Weighted Confidence**
   - Individual confidence values are blended according to their algorithm's weight in the position calculation
   - Algorithms with higher weights contribute more significantly to the final confidence
   - This weighted averaging approach preserves the confidence characteristics of the dominant algorithms
   - The system ensures that no single outlier algorithm overly influences the overall confidence

3. **Geometric Quality Adjustment**
   - The combined confidence value is adjusted based on the geometric distribution of access points
   - Confidence decreases more significantly when access points have poor geometric distribution
   - For collinear configurations (APs in a straight line), confidence is reduced more aggressively
   - A special multiplier is applied to collinear scenarios to reflect their inherent positioning ambiguity
   - For standard configurations, a more moderate adjustment is applied to balance geometric concerns
   - The adjustment uses the geometric quality factor determined during position combination

4. **Confidence Capping and Normalization**
   - Final confidence values are kept within realistic boundaries based on the scenario
   - Upper limits are enforced for different scenarios to prevent unrealistically high confidence
   - Collinear AP configurations have a strict maximum confidence cap (0.69)
   - Very weak signals result in low maximum confidence values (0.1)
   - Single AP positions are assigned confidence values reflective of their limited accuracy (0.35-0.55)
   - All confidence values are normalized to fall within the standard 0.0 to 1.0 range

This comprehensive confidence calculation approach ensures that the reported reliability metrics accurately correlate with actual position uncertainty. Applications using the positioning service can make informed decisions about how much trust to place in the calculated positions, enabling appropriate risk management for location-dependent features.

### Accuracy Calculation

Accuracy values in the WiFi Positioning Service represent the estimated position error in meters. Lower accuracy values indicate better precision (e.g., 5m accuracy is better than 50m accuracy). The accuracy calculation process involves:

1. **Base Accuracy Determination**
   - Each positioning algorithm provides its own initial accuracy estimate
   - These individual estimates reflect each algorithm's assessment of position error
   - The system tracks both the average accuracy across all algorithms and the maximum (worst) accuracy
   - This dual approach provides a balanced view of position error and prevents overly optimistic estimates
   - Algorithms use signal strength, distance modeling, and internal confidence assessments in their estimates

2. **Geometric Quality Integration**
   - The system adjusts accuracy based on the geometric distribution of access points
   - For standard configurations, the adjustment balances average and maximum accuracy values
   - For collinear access point arrangements, a more complex adjustment is required
   - The system calculates a "geometric weakness" score based on the condition number
   - A baseline accuracy is established using both average and maximum values as inputs
   - This baseline is then scaled according to the geometric quality assessment
   - A minimum accuracy threshold ensures that collinear configurations never report unrealistically high precision

3. **GDOP-Based Scaling**
   - Geometric Dilution of Precision (GDOP) principles from satellite positioning are applied to WiFi positioning
   - GDOP quantifies how the geometric arrangement of reference points affects position uncertainty
   - The system classifies geometric quality into four categories:
     * Excellent (GDOP < 2.0): Minimal scaling applied to accuracy
     * Good (GDOP 2.0-4.0): Moderate scaling applied
     * Fair (GDOP 4.0-6.0): Significant scaling applied
     * Poor (GDOP > 6.0): Substantial scaling applied to reflect high uncertainty
   - These categories guide accuracy adjustments proportionally to geometric quality

4. **Signal Quality Considerations**
   - Signal strength directly impacts the reliability of distance estimates
   - Strong signals (better than -70 dBm) produce the most reliable distance estimates
   - Medium signals (-70 to -85 dBm) introduce moderate uncertainty
   - Weak signals (worse than -85 dBm) lead to significantly increased accuracy values
   - Very weak signals (worse than -95 dBm) result in highly conservative accuracy values (30-80m)
   - Signal quality influences both individual algorithm accuracy estimates and their weighting

The resulting accuracy value represents a well-calibrated estimate of position error that accounts for all relevant factors: signal characteristics, algorithm reliability, and geometric considerations. This provides applications with a realistic assessment of position uncertainty, critical for location-based decision making, navigation, and geofencing applications.

## Test Coverage Summary

### Unit Tests
- Total Tests: 114
- All tests passing
- Coverage includes:
  - Repository tests for DynamoDB interaction
  - Utility tests for geohashing
  - Algorithm implementation tests
  - Controller and service layer tests
  - Signal physics validation tests

### Integration Tests
- Total Tests: 14
- Success Rate: 100%
- Coverage includes:
  - Basic algorithm scenarios
  - Advanced positioning scenarios
  - Temporal and environmental tests
  - Error and edge cases

### Additional Test Recommendations
1. Load testing scenarios
2. Concurrent request handling tests
3. More temporal variation tests
4. Cross-frequency interference tests
5. Environmental factor simulation tests

## Test Cases Implementation Details

### Basic Algorithm Test Cases (1-5)
1. **Single AP - Proximity Detection**
   - **Purpose**: Validate basic proximity-based positioning with minimal data
   - **Input**: Single AP with -65.0 dBm signal at 2.4GHz
   - **Expected**: Position with accuracy 45-55m, confidence 0.35-0.55
   - **Best Method**: "proximity"
   - **Rationale**: Tests system's ability to handle simplest positioning scenario with strong signal
   - **Signal Quality Impact**: Strong signal (-65 dBm) provides better accuracy than typical single AP scenarios
   - **Note**: Base weight: 1.0, Signal Quality (Strong): ×0.9, GDOP (Poor): ×0.7, Distribution (Uniform): ×1.1
     * Final weights: Proximity: 1.0 × 0.9 = 0.9, Log Distance: 0.4 × 1.0 × 0.7 × 1.1 = 0.308

2. **Two APs - RSSI Ratio Method**
   - **Purpose**: Test relative signal strength positioning
   - **Input**: Two APs with -68.5 dBm and -62.3 dBm at different frequencies (5GHz and 2.4GHz)
   - **Expected**: Accuracy 55-70m, confidence 0.40-0.60
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates positioning without absolute signal calibration
   - **Note**: Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8, Proximity: 0.4, Log Distance: 0.5
     * Final weights: Weighted Centroid: 0.8 × 1.0 × 1.3 × 1.0 = 1.04, RSSI Ratio: 1.0 × 1.0 × 0.8 × 1.2 = 0.96

3. **Three APs - Trilateration**
   - **Purpose**: Test geometric positioning with mixed signal quality
   - **Input**: Three APs with varying signal strengths (-62.3, -71.2, -85.5 dBm)
   - **Expected**: Accuracy 90-105m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Tests trilateration with mixed signal qualities
   - **Note**: Base weights: Trilateration: 1.0, Weighted Centroid: 0.8, RSSI Ratio: 0.7
     * Final weights: Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728, RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528

4. **Multiple APs - Maximum Likelihood**
   - **Purpose**: Test advanced positioning with redundant measurements
   - **Input**: Four APs with mixed signal qualities (-71.2 to -68.0 dBm)
   - **Expected**: Accuracy 135-150m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates statistical positioning approach

5. **Weak Signals**
   - **Purpose**: Test system behavior with poor signal conditions
   - **Input**: Single AP with -85.5 dBm signal
   - **Expected**: Accuracy 30-80m, confidence 0.05-0.15
   - **Best Method**: "proximity"
   - **Rationale**: Validates graceful degradation
   - **Note**: Base weights: Proximity: 1.0, Log Distance: 0.4
     * Final weights: Proximity: 1.0 × 0.4 × 0.7 × 1.1 = 0.308, Log Distance: 0.4 × 0.4 × 0.7 × 1.1 = 0.1232

### Advanced Scenario Test Cases (6-20)
1. **Collinear APs (6-10)**
   - **Purpose**: Test geometric dilution of precision handling
   - **Input**: Three APs in linear arrangement (-70.0, -68.0, -66.0 dBm)
   - **Expected**: ERROR status due to poor geometry
   - **Rationale**: Validates geometry quality assessment

2. **High Density Cluster (11-15)**
   - **Purpose**: Test positioning in AP-rich environments
   - **Input**: Four APs with strong signals (-65.0 to -60.5 dBm)
   - **Expected**: Accuracy 50-60m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid maximum_likelihood"
   - **Rationale**: Tests algorithm selection in optimal conditions
   - **Note**: Base weights: Maximum Likelihood: 1.0, Trilateration: 0.8, Weighted Centroid: 0.7
     * Final weights: Weighted Centroid: 0.7 × 0.9 × 1.3 × 1.0 = 0.819, Maximum Likelihood: 1.0 × 0.9 × 0.7 × 0.8 = 0.504

3. **Mixed Signal Quality (16-20)**
   - **Purpose**: Test adaptive algorithm selection
   - **Input**: Three APs with progressive signal degradation (-60.0 to -70.0 dBm)
   - **Expected**: Accuracy 60-75m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Tests dynamic algorithm weighting
   - **Note**: Base weights: Trilateration: 1.0, Weighted Centroid: 0.8, RSSI Ratio: 0.7
     * Final weights: Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728, RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528

### Temporal and Environmental Test Cases (21-35)
1. **Time Series Data (21-25)**
   - **Purpose**: Test temporal stability
   - **Input**: Two APs with consistent signals (-70.0, -72.0 dBm)
   - **Expected**: Accuracy 45-60m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates temporal robustness
   - **Note**: Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
     * Final weights: Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728, RSSI Ratio: 1.0 × 0.7 × 0.8 × 1.1 = 0.616

2. **Log-Distance Path Loss (26-30)**
   - **Purpose**: Test distance-based modeling
   - **Input**: Two APs with strong signals (-50.0, -53.0 dBm)
   - **Expected**: Accuracy 20-35m, confidence 0.40-0.60
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates path loss model
   - **Note**: Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
     * Final weights: Weighted Centroid: 0.8 × 0.9 × 1.3 × 1.0 = 0.936, RSSI Ratio: 1.0 × 0.9 × 0.8 × 0.8 = 0.576

3. **Stable Signal Quality (31-35)**
   - **Purpose**: Test positioning with stable signals
   - **Input**: Two APs with identical signal strengths (-68.0 dBm)
   - **Expected**: Accuracy 5-15m, confidence 0.65-0.80
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates long-term stability
   - **Note**: Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
     * Final weights: Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728, RSSI Ratio: 1.0 × 0.7 × 0.8 × 1.0 = 0.560

### Error and Edge Cases (36-40)
1. **Invalid Coordinates Test**
   - **Purpose**: Test handling of invalid location data
   - **Input**: Single AP with extremely weak signal (-99.9 dBm)
   - **Expected**: ERROR status
   - **Rationale**: Validates input validation

2. **Very Weak Signal Test**
   - **Purpose**: Test handling of very weak signals
   - **Input**: Single AP with -99.9 dBm signal
   - **Expected**: Accuracy 5-15m, confidence 0.0-0.1
   - **Best Method**: "proximity"
   - **Rationale**: Validates algorithm selection framework for very weak signals
   - **Note**: According to algorithm selection framework:
     * Signal strength -99.9 dBm is "Very Weak" (< -95 dBm)
     * Only Proximity algorithm gets non-zero weight (×0.5)
     * All other algorithms get zero weight

3. **Algorithm Failure Test**
   - **Purpose**: Test handling of physically impossible scenarios
   - **Input**: Three APs with physically impossible signal relationships
   - **Expected**: ERROR status
   - **Rationale**: Validates physics-based validation

Note: All test cases include additional parameters:
- `preferHighAccuracy`: Boolean flag to enable advanced algorithms
- `returnAllMethods`: Boolean flag to return results from all applicable algorithms
- Comprehensive validation of response fields including horizontalAccuracy, confidence, and bestMethod

### Notable Metrics:
- Strong signals provided better confidence scores (0.47-0.60)
- Weak signals correctly returned lower confidence (0.35) and higher accuracy values (750m)
- Physically impossible signal relationships were properly detected and rejected

## Areas for Improvement

### 1. Confidence Calculation
- Consider adjusting confidence calculation for multiple APs
- Current implementation shows lower confidence (0.39) with more APs
- Should generally increase with more APs unless signals are weak/inconsistent
- ✓ Implemented in Trilateration Algorithm: Confidence now accounts for AP geometry using GDOP

### 2. Accuracy Metrics
- High density cluster shows relatively high accuracy (15m) but low confidence (0.45)
- Consider aligning accuracy and confidence metrics more closely
- ✓ Implemented in Trilateration Algorithm: GDOP (Geometric Dilution of Precision) for better accuracy estimation
- GDOP factors are used to scale accuracy based on AP geometric distribution quality

### 3. Algorithm Selection
- Add weighted combination of multiple methods for overlapping scenarios
- Implement fallback strategies for each algorithm
- Consider signal stability over time for algorithm selection


