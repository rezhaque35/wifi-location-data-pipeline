# WiFi Positioning Service

A Spring Boot microservice that calculates geographic positions based on WiFi access point (AP) scan results from client devices. The service uses a hybrid multi-algorithm approach to provide accurate indoor/outdoor positioning without requiring GPS.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [Running the Service](#running-the-service)
- [Health Checks](#health-checks)
- [Testing](#testing)
- [Development](#development)

## Overview

The WiFi Positioning Service is a production-ready Spring Boot application that:

- **Calculates positions** from WiFi scan results using multiple positioning algorithms
- **Filters access points** through a multi-stage pipeline to ensure data quality
- **Selects optimal algorithms** dynamically based on signal quality, AP count, and geometric distribution
- **Processes requests asynchronously** using non-blocking I/O for high throughput
- **Provides detailed diagnostics** for debugging and monitoring

### Core Capabilities

- **Multi-Algorithm Positioning**: Combines 6 positioning algorithms (Proximity, RSSI Ratio, Log Distance, Weighted Centroid, Trilateration, Maximum Likelihood)
- **Dynamic Algorithm Selection**: Automatically selects optimal algorithms based on signal quality, AP count, and geometric distribution
- **Robust Filtering Pipeline**: Comprehensive multi-stage filtering to handle erroneous AP data
- **Async Non-Blocking Processing**: Fully asynchronous data retrieval and processing using CompletableFuture
- **Detailed Calculation Info**: Optional detailed breakdown of positioning calculation for debugging

## Features

### 1. Multi-Stage Filtering Pipeline

The service implements a comprehensive filtering pipeline to ensure only reliable access points are used:

1. **Signal Strength Filtering**: Selects top 20 strongest signals
2. **Location Lookup**: Retrieves AP locations from DynamoDB
3. **Status Validation**: Filters APs based on database status (active, warning, error, expired, hotspot)
4. **Global Outlier Filtering**: Uses cell tower information or centroid-based filtering to remove geographically impossible APs
5. **Local Outlier Detection**: Employs Local Outlier Factor (LOF) algorithm for statistical outlier detection

### 2. Cell Tower-Based Filtering

When cell tower information is available, the service uses it to filter out APs beyond the effective range:
- **Effective Range**: `cell_tower_range + max_wifi_distance` (e.g., 1000m + 500m = 1500m)
- **Fallback**: Centroid-based filtering when cell tower information is unavailable

### 3. Dynamic Algorithm Selection

The service automatically selects and combines algorithms based on:
- **AP Count**: Different algorithms work better with different numbers of APs
- **Signal Quality**: Strong signals enable more accurate algorithms
- **Geometric Distribution**: Good distribution enables geometric algorithms (trilateration)
- **Signal Distribution**: Uniform vs. mixed signal strengths

### 4. Positioning Algorithms

The service implements six positioning algorithms:

1. **Proximity Detection**: Uses the strongest signal AP's location
2. **RSSI Ratio**: Compares signal strength ratios between APs
3. **Log Distance Path Loss**: Uses path loss model with RSSI
4. **Weighted Centroid**: Calculates weighted average of AP positions
5. **Trilateration**: Uses geometric intersection of circles (requires 3+ APs)
6. **Maximum Likelihood**: Statistical estimation using probability distributions

### 5. Comprehensive Diagnostics

When `calculationDetail=true` in the request, the service provides:
- **Access Point Usage**: Which APs were used and why others were discarded
- **Algorithm Selection**: Which algorithms were selected and why
- **Selection Context**: AP count, signal quality, distribution factors
- **Performance Metrics**: Calculation time and accuracy metrics

## Prerequisites

### Required

- **Java 21**: Amazon Corretto 21 recommended
- **Maven 3.6+**: For building and dependency management
- **DynamoDB**: Either AWS DynamoDB or DynamoDB Local for development

### Optional (for local development)

- **Docker**: For running DynamoDB Local
- **AWS CLI**: For managing DynamoDB tables and test data

## Getting Started

### 1. Clone and Navigate

```bash
cd wifi-positioning-service
```

### 2. Set Up DynamoDB

#### Option A: Using DynamoDB Local (Recommended for Development)

```bash
cd scripts/setup
./setup.sh
```

This script will:
- Install required tools (Homebrew, AWS CLI, Docker)
- Start DynamoDB Local on port 8000
- Create tables (`wifi_access_points`, `wifi-cell-tower-location`)
- Load test data

#### Option B: Using AWS DynamoDB

1. Create tables in AWS DynamoDB:
   - `wifi_access_points` (partition key: `mac_address`)
   - `wifi-cell-tower-location` (partition key: `cell_id`, sort key: `lac`)

2. Configure AWS credentials:
   ```bash
   aws configure
   ```

3. Update `application.yml` to remove the DynamoDB endpoint configuration

### 3. Build the Application

```bash
mvn clean install
```

### 4. Run the Service

```bash
mvn spring-boot:run
```

The service will start on `http://localhost:8080/wifi-positioning-service`

### 5. Verify Health

```bash
curl http://localhost:8080/wifi-positioning-service/health
```

## API Documentation

### Base URL

```
http://localhost:8080/wifi-positioning-service
```

### Endpoints

#### Calculate Position

**POST** `/v1/wifi/position`

Calculates geographic position based on WiFi scan results.

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "wifiScanResults": [
    {
      "macAddress": "00:11:22:33:44:55",
      "signalStrength": -65,
      "frequency": 2437,
      "ssid": "MyWiFi",
      "channelWidth": 20,
      "timestamp": 1704723456789
    }
  ],
  "cellInfo": [
    {
      "cellId": 12345,
      "lac": 100,
      "mcc": 310,
      "mnc": 410,
      "signalStrength": -85
    }
  ],
  "client": "mobile-app",
  "requestId": "req-123",
  "application": "my-app",
  "calculationDetail": true
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `wifiScanResults` | Array | Yes | List of WiFi scan results |
| `macAddress` | String | Yes | MAC address of the access point |
| `signalStrength` | Double | Yes | RSSI in dBm (typically -100 to 0) |
| `frequency` | Integer | No | WiFi frequency in MHz |
| `ssid` | String | No | Network name |
| `channelWidth` | Integer | No | Channel width in MHz |
| `timestamp` | Long | No | Scan timestamp in milliseconds |
| `cellInfo` | Array | No | Cell tower information for filtering |
| `cellId` | Integer | Yes | Cell tower ID |
| `lac` | Integer | Yes | Location Area Code |
| `mcc` | Integer | Yes | Mobile Country Code |
| `mnc` | Integer | Yes | Mobile Network Code |
| `signalStrength` | Double | Yes | Cell tower signal strength in dBm |
| `client` | String | No | Client identifier |
| `requestId` | String | No | Request tracking ID |
| `application` | String | No | Application name |
| `calculationDetail` | Boolean | No | Include detailed calculation info |

**Success Response (200 OK):**
```json
{
  "result": "SUCCESS",
  "message": "Request processed successfully",
  "requestId": "req-123",
  "client": "mobile-app",
  "application": "my-app",
  "timestamp": 1704723456789,
  "wifiPosition": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 10.0,
    "horizontalAccuracy": 25.0,
    "verticalAccuracy": 0.0,
    "confidence": 0.75,
    "methodsUsed": ["weighted_centroid", "rssi_ratio"],
    "apCount": 8,
    "calculationTimeMs": 42
  },
  "calculationInfo": {
    "accessPoints": [...],
    "accessPointSummary": {...},
    "selectionContext": {...},
    "algorithmSelection": [...]
  }
}
```

**Error Response (200 OK with error details):**
```json
{
  "result": "ERROR",
  "message": "No known access points found in database",
  "requestId": "req-123",
  "client": "mobile-app",
  "application": "my-app",
  "timestamp": 1704723456789,
  "calculationInfo": {
    "accessPoints": [...],
    "accessPointSummary": {...},
    "selectionContext": {
      "apCountFactor": "NO_VALID_AP",
      "signalQuality": "NO_VALID_AP",
      "signalDistribution": "NO_VALID_AP",
      "geometricQuality": "NO_VALID_AP"
    },
    "algorithmSelection": []
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `result` | String | `SUCCESS` or `ERROR` |
| `message` | String | Human-readable message |
| `wifiPosition` | Object | Calculated position (only on success) |
| `latitude` | Double | Latitude in decimal degrees |
| `longitude` | Double | Longitude in decimal degrees |
| `altitude` | Double | Altitude in meters |
| `horizontalAccuracy` | Double | Horizontal accuracy in meters |
| `verticalAccuracy` | Double | Vertical accuracy in meters |
| `confidence` | Double | Confidence score (0.0 to 1.0) |
| `methodsUsed` | Array | List of algorithm names used |
| `apCount` | Integer | Number of APs used in calculation |
| `calculationTimeMs` | Long | Calculation duration in milliseconds |
| `calculationInfo` | Object | Detailed calculation info (if requested) |

### Interactive API Documentation

Swagger UI is available at:
```
http://localhost:8080/wifi-positioning-service/swagger-ui.html
```

OpenAPI documentation is available at:
```
http://localhost:8080/wifi-positioning-service/api-docs
```

## Architecture

### Service Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                         │
│  PositioningController, GlobalExceptionHandler              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                     Service Layer                           │
│  PositioningService (Orchestration)                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Data Preparation                          │
│  WifiAccessPoints (Filtering Pipeline)                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Algorithm Layer                           │
│  WifiPositioningCalculator, Algorithm Implementations       │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                  Repository Layer                           │
│  WifiAccessPointRepository, CellTowerRepository             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    Data Store                               │
│  DynamoDB (wifi_access_points, cell_towers)                 │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### PositioningService
- **Responsibility**: Orchestrates the complete positioning workflow
- **Features**: Async data preparation, position calculation, response building
- **Dependencies**: WifiAccessPointRepository, CellTowerRepository, WifiPositioningCalculator

#### WifiAccessPoints
- **Responsibility**: Immutable container for WiFi APs with filtering pipeline
- **Features**: Multi-stage filtering, viability tracking, detailed diagnostics
- **Pipeline Stages**: Signal strength → Location lookup → Status → Global outliers → Local outliers

#### WifiPositioningCalculator
- **Responsibility**: Selects and executes positioning algorithms
- **Features**: Dynamic algorithm selection, parallel execution, result combination
- **Algorithms**: 6 algorithms with automatic selection based on data characteristics

#### Repositories
- **WifiAccessPointRepository**: Async DynamoDB access for WiFi access points
- **CellTowerRepository**: Async DynamoDB access for cell tower information

### Data Flow

1. **Request Validation**: Validates incoming request data
2. **Async Data Preparation**: 
   - Parallel lookups: Cell tower + AP locations
   - Top 20 signal selection
   - Filtering pipeline execution
3. **Position Calculation**: 
   - Algorithm selection based on data characteristics
   - Parallel algorithm execution
   - Result combination
4. **Response Building**: 
   - Success/error response assembly
   - Optional calculation details
5. **Logging**: Structured logging for monitoring and debugging

## Configuration

### Application Properties

The service uses Spring Boot configuration files:

**`application.yml`** (main configuration):
```yaml
spring:
  application:
    name: wifi-positioning-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:non-local}

server:
  servlet:
    context-path: /wifi-positioning-service
  port: 8080

aws:
  dynamodb:
    endpoint: http://localhost:8000  # Remove for AWS DynamoDB
    region: us-east-1
    table-name: wifi_access_points
    cell-tower-table-name: wifi-cell-tower-location

logging:
  level:
    root: INFO
    com.wifi.positioning: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: '*'
  endpoint:
    health:
      show-details: always
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `non-local` |
| `AWS_REGION` | AWS region | `us-east-1` |
| `DYNAMODB_ENDPOINT` | DynamoDB endpoint (for local) | `http://localhost:8000` |

### Filtering Configuration

Constants in `PositioningService`:
- `TOP_STRONGEST_SIGNALS_LIMIT = 20`: Maximum number of strongest signals to process
- `MAX_WIFI_DISTANCE_METERS = 500.0`: Maximum reasonable WiFi distance for filtering

Constants in `SimpleLOFDetector`:
- `LOF_THRESHOLD = 1.5`: Local Outlier Factor threshold
- `MIN_POINTS_FOR_LOF = 3`: Minimum points required for LOF calculation

## Running the Service

### Development Mode

```bash
mvn spring-boot:run
```

### Production Mode

```bash
java -jar target/wifi-positioning-service-0.0.1-SNAPSHOT.jar
```

### With Custom Profile

```bash
SPRING_PROFILES_ACTIVE=production mvn spring-boot:run
```

## Health Checks

The service provides health check endpoints for monitoring:

### Health Endpoint

```
GET /health
```

Returns overall service health status.

### Liveness Probe

```
GET /health/liveness
```

Returns service liveness status. Use this for Kubernetes liveness probes.

### Readiness Probe

```
GET /health/readiness
```

Returns service readiness status including DynamoDB connectivity. Use this for Kubernetes readiness probes.

### Health Groups

The service configures health groups:
- **liveness**: Includes `serviceLiveness` indicator
- **readiness**: Includes `dynamoDBReadiness` indicator

### Health Indicator Details

**ServiceLivenessHealthIndicator**: Checks if the service is alive
**DynamoDBReadinessHealthIndicator**: Checks DynamoDB connectivity and table existence

## Testing

### Unit Tests

Run all unit tests:
```bash
mvn test
```

Run specific test class:
```bash
mvn test -Dtest=PositioningServiceTest
```

### Integration Tests

The service includes comprehensive integration tests:
- Repository tests with mocked DynamoDB
- Service tests with mocked repositories
- Algorithm tests with various scenarios

### Data-Driven Tests

The service includes data-driven test scripts:

```bash
cd scripts/test
./run-data-driven-tests.sh
```

This script runs comprehensive tests using JSON test data files located in `scripts/test/data/`.

### Test Scenarios

The test suite covers:
- Single AP scenarios
- Multiple AP scenarios (20, 25, 30 APs)
- Status filtering (active, warning, error, expired, hotspot)
- Cell tower filtering (within range, outliers)
- Combined filtering scenarios
- Edge cases and error conditions

## Development

### Project Structure

```
src/
├── main/
│   ├── java/com/wifi/positioning/
│   │   ├── algorithm/          # Positioning algorithms
│   │   ├── config/              # Configuration classes
│   │   ├── controller/          # REST controllers
│   │   ├── dto/                 # Data transfer objects
│   │   ├── health/              # Health check indicators
│   │   ├── repository/          # DynamoDB repositories
│   │   ├── service/             # Business logic
│   │   └── util/                # Utility classes
│   └── resources/
│       └── application.yml      # Application configuration
└── test/
    ├── java/com/wifi/positioning/
    │   └── [test classes mirroring main structure]
    └── resources/
        └── application-test.yml # Test configuration
```

### Code Quality Standards

The service follows:
- **SOLID Principles**: Single responsibility, dependency injection
- **DRY**: Don't Repeat Yourself
- **KISS**: Keep It Simple, Stupid
- **YAGNI**: You Aren't Gonna Need It
- **SLAP**: Single Level of Abstraction Principle
- **Immutable Data**: All DTOs are immutable
- **Declarative Style**: Prefer declarative over imperative code

### Building

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package JAR
mvn package

# Skip tests
mvn package -DskipTests
```

### Dependencies

Key dependencies:
- **Spring Boot 3.4.5**: Framework
- **AWS SDK 2.25.1**: DynamoDB client
- **Apache Commons Math 3.6.1**: Mathematical operations
- **Lombok 1.18.30**: Code generation
- **SpringDoc OpenAPI 2.3.0**: API documentation
- **Logstash Logback Encoder 8.0**: Structured logging

### Logging

The service uses structured logging with Logstash encoder:
- **Request Logging**: All incoming requests are logged with structured data
- **Response Logging**: All responses are logged with structured data
- **Calculation Info Logging**: Detailed calculation info is logged (when not included in response)

Log levels:
- **DEBUG**: Detailed debugging information
- **INFO**: General information and request/response logging
- **WARN**: Warning messages (algorithm failures, timeouts)
- **ERROR**: Error messages (calculation failures, exceptions)

## License

[Add your license information here]

## Support

For issues, questions, or contributions, please refer to the project documentation or contact the development team.

