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
   - `GlobalExceptionHandler` - Centralized error handling and response formatting

2. **Service Layer**
   - `PositioningService` - Orchestrates the positioning process
   - `CacheService` - Manages access point data caching
   - `ValidationService` - Input validation and data quality checks

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
   - `AlgorithmSelectionService` - Dynamic algorithm selection logic

4. **Repository Layer**
   - `WifiAccessPointRepository` - Interface for access point data access
   - `WifiAccessPointRepositoryImpl` - DynamoDB implementation with connection pooling
   - `DynamoDBConfig` - Database configuration and client setup

5. **Health Monitoring Layer**
   - `DynamoDBReadinessHealthIndicator` - Repository-based DynamoDB health checks
   - `ServiceLivenessHealthIndicator` - Service availability and uptime monitoring

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

  "calculationInfo": {
    "accessPoints": [
      {
        "bssid": "00:11:22:33:44:55",
        "location": {
          "latitude": 37.7749,
          "longitude": -122.4194,
          "altitude": 10.5
        },
        "status": "active",
        "usage": "used"
      },
      {
        "bssid": "AA:BB:CC:DD:EE:FF",
        "location": {
          "latitude": 37.7750,
          "longitude": -122.4195,
          "altitude": 12.0
        },
        "status": "warning",
        "usage": "used"
      }
    ],
    "accessPointSummary": {
      "total": 2,
      "used": 2,
      "statusCounts": [
        {"status": "active", "count": 1},
        {"status": "warning", "count": 1}
      ]
    },
    "selectionContext": {
      "apCountFactor": "TWO_APS",
      "signalQuality": "STRONG_SIGNAL",
      "signalDistribution": "UNIFORM_SIGNALS",
      "geometricQuality": "GOOD_GDOP"
    },
    "algorithmSelection": [
      {
        "algorithm": "weighted_centroid",
        "selected": true,
        "reasons": ["Primary algorithm for this scenario", "Good geometric distribution"],
        "weight": 0.728
      },
      {
        "algorithm": "rssi_ratio",
        "selected": true,
        "reasons": ["Secondary algorithm as backup", "Strong signal quality"],
        "weight": 0.560
      },
      {
        "algorithm": "trilateration",
        "selected": false,
        "reasons": ["Insufficient AP count for trilateration"],
        "weight": null
      }
    ]
  }
}
```

Note: `calculationInfo` is only present when `calculationDetail=true` is set in the request.

### Calculation Information Details

When `calculationDetail=true` is specified in the request, the response includes a structured `calculationInfo` object with comprehensive details about the positioning calculation:

#### Access Points (`accessPoints`)
Contains detailed information about each access point found in the database:
- **`bssid`**: The MAC address of the access point
- **`location`**: Geographic coordinates (latitude, longitude, altitude)
- **`status`**: Access point status (`active`, `warning`, `error`, `expired`, `wifi-hotspot`)
- **`usage`**: Whether the AP was `used` in calculations or `filtered` out

#### Access Point Summary (`accessPointSummary`)
Provides statistics about access point usage:
- **`total`**: Total number of access points found in database
- **`used`**: Number of access points actually used in calculations
- **`statusCounts`**: Breakdown of access points by status with counts

#### Selection Context (`selectionContext`)
Shows the factors that influenced algorithm selection:
- **`apCountFactor`**: Classification based on number of APs (`ONE_AP`, `TWO_APS`, `THREE_APS`, `FOUR_PLUS_APS`)
- **`signalQuality`**: Overall signal strength assessment (`STRONG_SIGNAL`, `MEDIUM_SIGNAL`, `WEAK_SIGNAL`, `VERY_WEAK_SIGNAL`)
- **`signalDistribution`**: Pattern of signal strengths (`UNIFORM_SIGNALS`, `MIXED_SIGNALS`, `SIGNAL_OUTLIERS`)
- **`geometricQuality`**: Geometric distribution quality (`EXCELLENT_GDOP`, `GOOD_GDOP`, `FAIR_GDOP`, `POOR_GDOP`, `COLLINEAR_APS`)

#### Algorithm Selection (`algorithmSelection`)
Details about each algorithm considered:
- **`algorithm`**: Algorithm name (`proximity`, `weighted_centroid`, `rssi_ratio`, `trilateration`, `log_distance`, `maximum_likelihood`)
- **`selected`**: Whether the algorithm was used in the final calculation
- **`reasons`**: List of reasons explaining why the algorithm was selected or rejected
- **`weight`**: Numerical weight assigned to the algorithm (null if not selected)

This structured information allows developers to understand:
- Which access points contributed to the positioning calculation
- Why specific algorithms were chosen or rejected
- How the geometric and signal conditions influenced the calculation
- The relative importance (weights) of different algorithms in the final result

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

## Health Monitoring System

The service implements comprehensive health monitoring using Spring Boot Actuator with custom health indicators to ensure system reliability and operational visibility.

### Health Check Architecture

The health monitoring system consists of two specialized health indicators:

1. **DynamoDB Readiness Health Indicator** (`DynamoDBReadinessHealthIndicator`)
2. **Service Liveness Health Indicator** (`ServiceLivenessHealthIndicator`)

### DynamoDB Readiness Health Check

**Purpose**: Validates that the service is ready to handle requests by ensuring DynamoDB dependency is accessible and functional.

**Key Features**:
- Repository-based health validation (separation of concerns)
- High-precision response time measurement using `System.nanoTime()`
- Comprehensive error handling for different failure scenarios
- Detailed health information including table accessibility and item counts

**Health Status Logic**:
- **UP**: Repository reports healthy status with good performance
- **DOWN**: Repository reports issues (poor performance, accessibility problems, table not found)
- **OUT_OF_SERVICE**: Unexpected errors requiring investigation

**Response Time Measurement**:
```
response_time_ms = (end_time_nanos - start_time_nanos) / 1,000,000
```

**Health Check Details Provided**:
- Database connection status
- Table accessibility and read permissions
- Response time measurements
- Item count validation for data availability
- Last check timestamp
- Detailed error information when failures occur

**Example Response**:
```json
{
  "status": "UP",
  "components": {
    "dynamoDBReadiness": {
      "status": "UP",
      "details": {
        "status": "DynamoDB is accessible",
        "database": "DynamoDB",
        "tableName": "wifi_access_points",
        "lastChecked": "2024-01-15T10:30:45.123Z",
        "responseTimeMs": 45,
        "itemCount": 54
      }
    }
  }
}
```

### Service Liveness Health Check

**Purpose**: Indicates whether the service is alive and running, following Kubernetes liveness probe best practices.

**Key Features**:
- Always returns UP status (unless service is completely non-functional)
- Tracks service uptime since startup
- Provides service identification and version information
- Minimal overhead with efficient uptime calculation

**Uptime Calculation**:
```
uptime_ms = current_time_ms - startup_time_ms
```

**Uptime Display Logic**:
- If uptime < 5 seconds: Display in milliseconds (e.g., "1234 ms")
- If uptime ≥ 5 seconds: Display in seconds with decimal precision (e.g., "12.34 seconds")

**Example Response**:
```json
{
  "status": "UP",
  "components": {
    "serviceLiveness": {
      "status": "UP",
      "details": {
        "status": "Service is alive and running",
        "serviceName": "WiFi Positioning Service",
        "version": "1.0.0",
        "startupTime": "2024-01-15T09:15:30.456Z",
        "uptime": "75.67 seconds"
      }
    }
  }
}
```

### Health Monitoring Benefits

**Operational Visibility**:
- Real-time system health status
- Performance monitoring through response time metrics
- Detailed error diagnostics for troubleshooting
- Service uptime tracking

**Integration Ready**:
- Compatible with Kubernetes readiness and liveness probes
- Spring Boot Actuator standard compliance
- Structured JSON responses for monitoring tools
- Configurable health check endpoints

**Reliability Features**:
- Repository abstraction ensures consistency with application data access patterns
- Comprehensive error handling prevents health check failures from affecting service
- High-precision timing for accurate performance measurement
- Graceful degradation with meaningful error messages

### Health Endpoint Access

Health information is available through Spring Boot Actuator endpoints:

```bash
# Overall health status
GET /actuator/health

# Detailed health information
GET /actuator/health/dynamoDBReadiness
GET /actuator/health/serviceLiveness
```

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
├── documents/                          # Documentation and context
│   ├── algorithm-selection-framework.md
│   ├── dynamodb-access-pattern-optimizations.md
│   └── wifi-positioning-implementation-context.md
├── scripts/                            # Automation and testing scripts
│   ├── setup/                         # Environment setup scripts
│   │   ├── cleanup-full.sh
│   │   ├── cleanup.sh
│   │   ├── create-and-load.sh
│   │   ├── setup.sh
│   │   ├── verify-dynamodb-data.sh
│   │   ├── wifi-access-points-schema.json
│   │   ├── wifi-positioning-test-data.sh
│   │   └── README.md
│   └── test/                          # Test execution scripts
│       ├── run-comprehensive-tests.sh
│       └── wifi-positioing-complete-test.sh
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/wifi/positioning/
│   │   │       ├── algorithm/
│   │   │       │   ├── impl/            # Algorithm implementations
│   │   │       │   ├── selection/      # Algorithm selection logic
│   │   │       │   └── util/           # Algorithm utilities
│   │   │       ├── controller/         # REST API controllers
│   │   │       ├── dto/                # Data transfer objects
│   │   │       ├── health/             # Health check indicators
│   │   │       ├── repository/         # Data access layer
│   │   │       ├── service/            # Business logic services
│   │   │       └── WifiPositioningServiceApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/
│       │   └── com/wifi/positioning/
│       │       ├── algorithm/
│       │       ├── config/
│       │       ├── controller/
│       │       ├── dto/
│       │       ├── health/
│       │       ├── repository/
│       │       └── service/
│       └── resources/
│           ├── application-test.yml
│           ├── repository-implementation-status.md
│           └── test-results-summary.md
├── deploy-aws.sh                       # AWS deployment automation
├── pom.xml
└── README.md
```

## Scripts and Automation

The service includes comprehensive scripts for setup, testing, and deployment automation.

### Setup Scripts (`scripts/setup/`)

The setup directory contains scripts to manage the DynamoDB Local environment:

#### Core Setup Scripts

- **`setup.sh`** - Complete environment setup
  - Installs dependencies (Homebrew, AWS CLI, Docker)
  - Sets up AWS credentials for DynamoDB Local
  - Starts DynamoDB Local container on port 8000
  - Creates `wifi_access_points` table
  - Loads comprehensive test data (54 test access points)
  - Verifies the complete setup

- **`create-and-load.sh`** - Table creation and data loading
  - Deletes existing table (if any)
  - Creates new `wifi_access_points` table
  - Loads test data organized in categories:
    - Basic Algorithm Test Cases (5 APs)
    - Advanced Scenario Test Cases (8 APs)
    - Temporal and Environmental Test Cases (6 APs)
    - Error and Edge Cases (2 APs)
    - Status Filtering Tests (5 APs)
    - 2D Positioning Tests (8 APs)
    - Mixed 2D/3D Positioning Tests (2 APs)

- **`verify-dynamodb-data.sh`** - Data verification
  - Verifies table existence and accessibility
  - Scans and displays all loaded data
  - Shows test data organized by categories
  - Validates data integrity

#### Cleanup Scripts

- **`cleanup.sh`** - Basic cleanup
  - Deletes the `wifi_access_points` table
  - Stops and removes DynamoDB Local container

- **`cleanup-full.sh`** - Comprehensive cleanup with options
  - **Usage**: `./cleanup-full.sh [basic|full|reset]`
  - **basic**: Delete table only (keeps container running)
  - **full**: Delete table and stop container (DEFAULT)
  - **reset**: Complete cleanup (table, container, image, credentials)

### Test Scripts (`scripts/test/`)

- **`run-comprehensive-tests.sh`** - Comprehensive test suite
  - Executes 20+ test cases covering all positioning scenarios
  - Validates positioning algorithms and accuracy
  - Tests 2D/3D positioning capabilities
  - Verifies error handling and edge cases
  - Includes detailed response validation
  - Provides colored output and test statistics

- **`wifi-positioing-complete-test.sh`** - Complete integration test
  - End-to-end testing workflow
  - Service integration validation

### Deployment Scripts

- **`deploy-aws.sh`** - AWS deployment automation
  - Builds application with AWS profile
  - Creates Docker image
  - Generates Kubernetes deployment files
  - Provides ECR integration templates
  - Creates ConfigMaps and Services for EKS

## Setup & Running

### Quick Start

1. **Complete Setup** (Recommended):
```bash
# Run complete environment setup
./scripts/setup/setup.sh
```

2. **Manual Setup**:
```bash
# Start DynamoDB Local
docker run -p 8000:8000 amazon/dynamodb-local

# Create table and load data
./scripts/setup/create-and-load.sh

# Verify setup
./scripts/setup/verify-dynamodb-data.sh
```

3. **Build and Run**:
```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

### Verification

```bash
# Verify DynamoDB is running
docker ps | grep dynamodb-local

# Check loaded data
./scripts/setup/verify-dynamodb-data.sh

# Run comprehensive tests
./scripts/test/run-comprehensive-tests.sh
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
./scripts/test/run-comprehensive-tests.sh
```

## Deployment

### AWS Deployment

The service includes automated AWS deployment capabilities through the `deploy-aws.sh` script.

#### Deployment Features

- **Automated Build Process**:
  - Builds application with AWS profile (`-Paws`)
  - Creates optimized Docker image
  - Skips tests for faster deployment builds

- **Container Orchestration**:
  - Generates Kubernetes deployment manifests
  - Creates ConfigMaps for AWS configuration
  - Sets up Services for load balancing
  - Configures resource limits and requests

- **ECR Integration** (Template provided):
  - AWS Elastic Container Registry push commands
  - Automated image tagging
  - Cross-region deployment support

#### Quick Deployment

```bash
# Automated AWS deployment
./deploy-aws.sh
```

#### Manual Deployment Steps

1. **Build for AWS**:
```bash
./mvnw clean package -DskipTests -Paws
```

2. **Create Docker Image**:
```bash
docker build -t wifi-positioning-service:latest .
```

3. **Deploy to Kubernetes**:
```bash
# Apply generated manifests
kubectl apply -f k8s/
```

#### Generated Kubernetes Resources

The deployment script creates:

- **Deployment** (`k8s/deployment.yaml`):
  - 2 replica pods for high availability
  - Resource limits: 1 CPU, 1Gi memory
  - Resource requests: 500m CPU, 512Mi memory
  - AWS profile activation
  - Health check configuration

- **Service** (`k8s/service.yaml`):
  - ClusterIP service for internal load balancing
  - Port mapping: 80 → 8080
  - Label-based pod selection

- **ConfigMap** (`k8s/configmap.yaml`):
  - AWS region configuration
  - Environment-specific settings
  - External configuration management

#### Prerequisites for AWS Deployment

- **AWS CLI v2** configured with appropriate credentials
- **Docker** for container building
- **kubectl** configured for target EKS cluster
- **EKS cluster** with proper IAM roles for DynamoDB access
- **DynamoDB table** (`wifi_access_points`) in target AWS region

#### IAM Requirements

Ensure your EKS cluster has IAM roles with the following permissions:
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "dynamodb:GetItem",
                "dynamodb:Query",
                "dynamodb:Scan",
                "dynamodb:BatchGetItem",
                "dynamodb:DescribeTable"
            ],
            "Resource": "arn:aws:dynamodb:*:*:table/wifi_access_points"
        }
    ]
}
```

## Test Coverage

- Unit Tests: 114 tests covering repository, utility, algorithm implementation, and service layers
- Integration Tests: 14 tests covering basic algorithms, advanced scenarios, and error cases
- All tests currently passing with 100% success rate

## Profiles

- local: Default profile for local development
- test: Profile for running tests with in-memory database
- prod: Production profile (requires AWS credentials)