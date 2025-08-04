# WiFi Positioning Service

A service that provides indoor positioning using WiFi access points. The system uses a hybrid approach combining multiple positioning algorithms to provide the most accurate position estimation based on WiFi scan results.

## Key Features

- Combines multiple positioning algorithms for optimal results
- Adapts to different scenarios (signal strength, AP density, geometric distribution)
- Provides accuracy metrics and confidence levels
- Works with single measurements (no historical data needed)
- Supports both 2D and 3D positioning
- GDOP (Geometric Dilution of Precision) implementation for better accuracy estimation
- Handles collinear AP configurations and geometrically challenging scenarios
- Adjusts confidence and accuracy based on geometric quality assessment

## Architecture

The service is structured around the following components:

### Core Components

1. **Controller Layer**
   - `PositioningController` - Handles HTTP requests/responses for position calculation

2. **Service Layer**
   - `PositioningService` - Orchestrates the positioning process
   - Converts DTOs to internal models

3. **Algorithm Layer**
   - `PositioningAlgorithm` - Interface for all positioning algorithms
   - Algorithm implementations:
     - `ProximityAlgorithm` - Identifies the AP with strongest signal
     - `RSSIRatioAlgorithm` - Uses relative signal strength ratios 
     - `LogDistanceAlgorithm` - Physics-based signal propagation model
     - `WeightedCentroidAlgorithm` - Calculates weighted average of AP locations
     - `TriangulationAlgorithm` - Solves intersection of distance spheres
     - `MaximumLikelihoodAlgorithm` - Statistical position estimation
   - `WeightedAveragePositionCombiner` - Combines results from multiple algorithms

4. **Repository Layer**
   - `WifiAccessPointRepository` - Interface for access point data access
   - `DynamoWifiAccessPointRepository` - DynamoDB implementation

## Algorithm Details

### Primary Algorithms

1. **Proximity Detection**
   - Identifies AP with strongest signal and uses its location as the user's position
   - Key Formula: `position = bestAP.location`
   - Accuracy: Low (±15-50m)
   - Best for: Single AP scenarios with strong signals

2. **RSSI Ratio Method**
   - Uses ratios of signal strengths to estimate relative distances
   - Key Formula: `distanceRatio(AP1,AP2) = 10^((RSSI2 - RSSI1)/(10 * pathLossExponent))`
   - Accuracy: Medium (±8-25m)
   - Best for: 2-3 APs with similar signal strengths

3. **Log-Distance Path Loss**
   - Estimates distances using physics-based signal propagation models
   - Key Formula: `distance = 10^((A - RSSI)/(10 * n))`
   - Accuracy: Medium (±10-30m)
   - Best for: Known environment characteristics

4. **Weighted Centroid**
   - Calculates position as weighted average of AP locations
   - Key Formula: `position = Σ(AP positions * w(i)) / Σ(w(i))` 
   - Accuracy: Medium (±8-20m)
   - Best for: Well-distributed APs with mixed signals

### Enhanced Algorithms

1. **Maximum Likelihood**
   - Finds position with highest probability given observed signals
   - Uses statistical modeling of signal probabilities
   - Accuracy: Medium-High (±4-12m)
   - Best for: 4+ APs with strong signals

2. **Modified Trilateration**
   - Determines position by solving for intersection of distance spheres
   - Incorporates GDOP calculations for geometric quality assessment
   - Accuracy: Medium-High (±5-15m)
   - Best for: 3+ APs with good geometric distribution

### Position Combination

The system combines results from multiple algorithms using:

- Weighted position averaging based on algorithm confidence
- Geometric quality assessment using covariance matrices
- Special handling for collinear AP configurations
- Confidence and accuracy adjustments based on geometric quality

## Database Integration

The system uses Amazon DynamoDB to store information about known WiFi access points. When calculating positions, the system:

1. Takes WiFi scan results from client devices
2. Looks up MAC addresses in the DynamoDB database
3. Retrieves information about known access points (location, signal characteristics)
4. Uses this information to enhance position calculations

### Access Point Data Schema

The access point data is stored with the following schema:

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

### Data Fields

Each access point record contains:
- **Primary Key**: `mac_addr` - Unique identifier in XX:XX:XX:XX:XX:XX format
- **Location Data**: latitude, longitude, altitude, horizontal_accuracy, vertical_accuracy
- **Signal Data**: frequency, ssid, vendor
- **Metadata**: version, geohash, confidence, status

### Status Values
- `active`: Valid and current access point data
- `warning`: Data may be outdated or have reduced confidence
- `error`: Data contains errors or inconsistencies
- `expired`: Data is no longer valid
- `wifi-hotspot`: Special designation for public WiFi hotspots

Only access points with status "active" or "warning" are used for calculations.

## API Usage

The service exposes a single API endpoint:

```
POST /api/positioning/calculate
```

### Request Format

```json
{
  "wifiScanResults": [
    {
      "macAddress": "00:11:22:33:44:55",
      "signalStrength": -65,
      "frequency": 2437,
      "ssid": "MyWiFi"
    },
    {
      "macAddress": "AA:BB:CC:DD:EE:FF",
      "signalStrength": -72,
      "frequency": 5240,
      "ssid": "MyWiFi5G"
    }
  ],
  "client": "test-client",
  "requestId": "test-request-123",
  "application": "wifi-positioning-test-suite",
  "calculationDetail": true
}
```

### Response Format

```json
{
  "result": "SUCCESS",
  "message": "Request processed successfully",
  "requestId": "test-request-123",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1746821320281,

  "wifiPosition": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 10.0,
    "horizontalAccuracy": 25.0,
    "verticalAccuracy": 0.0,
    "confidence": 0.5,
    "methodsUsed": ["weighted_centroid", "rssi_ratio"],
    "apCount": 3,
    "calculationTimeMs": 42
  },
  "calculationInfo": "Detailed calculation information"
}
```

Note: `calculationInfo` is only present when `calculationDetail=true` is set in the request.

## Hybrid Algorithm Selection Framework

The system dynamically selects positioning algorithms through a three-phase process:

1. **Hard Constraints (Disqualification Phase)**
   - Eliminates algorithms that are mathematically or practically invalid
   - Examples: Single AP → Only Proximity and Log Distance; Collinear APs → Remove Trilateration

2. **Algorithm Weighting (Ranking Phase)**
   - Assigns base weights according to AP count
   - Applies adjustments for signal quality, geometric quality, and distribution
   - Example: 
     * For 3 APs with medium signal quality: Weighted Centroid (weight=0.728), RSSI Ratio (weight=0.353)

3. **Finalist Selection (Combination Phase)**
   - Selects algorithms to use based on adjusted weights
   - Combines results using the weighted position combiner

### Confidence and Accuracy Calculation

The system calculates confidence and accuracy through multi-step processes:

- **Confidence Calculation**:
  1. Each algorithm generates a base confidence value (0.0-1.0)
  2. Values are blended according to algorithm weights
  3. Adjustments are made based on geometric quality
  4. Final values are capped based on scenario-specific limits

- **Accuracy Calculation** (lower values = better precision):
  1. Base accuracy from each algorithm's internal assessment
  2. Geometric quality integration using GDOP principles
  3. Signal quality considerations (strong signals produce more reliable estimates)
  4. Final accuracy represents estimated position error in meters

## Development

To build and run the service:

```
./mvnw clean package
java -jar target/wifi-positioning-service-1.0.0.jar
```

### Active Profiles Configuration

The service uses Spring profiles to manage different configurations. By default, the service runs with the `non-local` profile. To run the service with a different profile, you can set the `SPRING_PROFILES_ACTIVE` environment variable:

```bash
# Run with non-local profile (default)
java -jar target/wifi-positioning-service-1.0.0.jar

# Run with local profile
SPRING_PROFILES_ACTIVE=local java -jar target/wifi-positioning-service-1.0.0.jar

# Run with multiple profiles
SPRING_PROFILES_ACTIVE=local,dev java -jar target/wifi-positioning-service-1.0.0.jar
```

For development with local DynamoDB:

```
docker-compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker (for local DynamoDB)
- AWS CLI v2

## Project Structure

```
wifi-positioning-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/wifi/positioning/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── algorithm/
│   │   │       ├── repository/
│   │   │       ├── model/
│   │   │       └── util/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/
│       │   └── com/wifi/positioning/
│       │       ├── controller/
│       │       ├── service/
│       │       ├── algorithm/
│       │       └── repository/
│       └── resources/
│           └── application-test.yml
├── pom.xml
└── README.md
```

## Setup & Running

1. Start Local DynamoDB:
```bash
docker run -p 8000:8000 amazon/dynamodb-local
```

2. Load Test Data:
```bash
./load-test-data.sh
```

3. Build the project:
```bash
mvn clean install
```

4. Run the application:
```bash
mvn spring-boot:run
```

The application will be available at http://localhost:8080

## API Documentation

Swagger UI is available at: http://localhost:8080/swagger-ui.html
API docs are available at: http://localhost:8080/api-docs

## Testing

Run unit tests:
```bash
mvn test
```

Run integration tests:
```bash
mvn verify
```

Run comprehensive tests (includes algorithm performance tests):
```bash
./run-comprehensive-tests.sh
```

## Test Coverage

- Unit Tests: 114 tests covering repository, utility, algorithm implementation, and service layers
- Integration Tests: 14 tests covering basic algorithms, advanced scenarios, and error cases
- All tests currently passing with 100% success rate

## Profiles

- local: Default profile for local development
- test: Profile for running tests with in-memory database
- prod: Production profile (requires AWS credentials)