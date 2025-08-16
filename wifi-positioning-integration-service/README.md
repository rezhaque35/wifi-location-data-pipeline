# WiFi Positioning Integration Service

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-green.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-orange.svg)](https://maven.apache.org/)

> **A Spring Boot service that provides integration between client positioning systems and the WiFi Positioning Service, enabling comprehensive comparison and analysis of positioning results.**

## 🎯 Overview

The WiFi Positioning Integration Service acts as a bridge between client positioning systems and the core WiFi positioning infrastructure. It accepts client requests in a standardized format, processes them through the positioning service, and provides detailed comparison metrics and access point enrichment analysis.

### Key Features

- **🔄 Integration Endpoint**: Single REST endpoint for comprehensive positioning analysis
- **📊 Comparison Metrics**: Haversine distance calculations and accuracy/confidence deltas
- **📡 AP Enrichment**: Detailed access point analysis from positioning service responses
- **🔍 Sample Interface Support**: Full compatibility with client sample interface format
- **⚡ Non-blocking Integration**: WebClient-based positioning service communication
- **📝 Structured Logging**: Comprehensive logging with correlation IDs and metrics

## 🏗️ Architecture

```
┌─────────────────────┐    ┌─────────────────────────────┐    ┌─────────────────────┐
│   Client Request    │───▶│  Integration Service       │───▶│ WiFi Positioning    │
│  (Sample Interface) │    │                             │    │     Service        │
└─────────────────────┘    │  • Request Mapping         │    └─────────────────────┘
                           │  • Positioning Call        │              │
┌─────────────────────┐    │  • Comparison Analysis    │              │
│   Client Response   │◀───│  • AP Enrichment          │◀─────────────┘
│   (Comparison)      │    │  • Integration Report     │
└─────────────────────┘    └─────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- **Java 21** or higher
- **Maven 3.8+**
- **WiFi Positioning Service** running (for full integration testing)
- **DynamoDB Local** with test data loaded (for positioning service)

### 1. Clone and Build

```bash
git clone <repository-url>
cd wifi-positioning-integration-service
mvn clean install
```

### 2. Start the Service

```bash
mvn spring-boot:run
```

The service will start on **port 8083** with context path `/wifi-positioning-integration-service`.

### 3. Verify Health

```bash
curl http://localhost:8083/wifi-positioning-integration-service/health
```

### 4. Test Basic Functionality

```bash
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -d '{
    "sourceRequest": {
      "svcBody": {
        "svcReq": {
          "clientId": "test-client",
          "requestId": "test-123",
          "wifiInfo": [
            {
              "id": "00:11:22:33:44:01",
              "signalStrength": -65,
              "frequency": 2437
            }
          ]
        }
      }
    },
    "options": {
      "calculationDetail": true
    }
  }'
```

## 📡 **CURL Command Examples**

### **Health & Monitoring Commands**

#### **1. Service Health Check**
```bash
# Basic health check
curl -s http://localhost:8083/wifi-positioning-integration-service/health | jq '.'

# Health with details
curl -s http://localhost:8083/wifi-positioning-integration-service/health | jq '.components.wifiPositioningService'

# Liveness probe (Kubernetes)
curl -s http://localhost:8083/wifi-positioning-integration-service/health/liveness

# Readiness probe (Kubernetes)
curl -s http://localhost:8083/wifi-positioning-integration-service/health/readiness
```

#### **2. Service Information**
```bash
# Service info
curl -s http://localhost:8083/wifi-positioning-integration-service/actuator/info | jq '.'

# Metrics
curl -s http://localhost:8083/wifi-positioning-integration-service/actuator/metrics | jq '.'

# HTTP request metrics
curl -s http://localhost:8083/wifi-positioning-integration-service/actuator/metrics/http.server.requests | jq '.'
```

### **Integration Report Commands**

#### **3. Single AP Proximity Test**
```bash
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-single-ap-001" \
  -d '{
    "sourceRequest": {
      "svcBody": {
        "svcReq": {
          "clientId": "curl-test-client",
          "requestId": "single-ap-test-001",
          "wifiInfo": [
            {
              "id": "00:11:22:33:44:01",
              "signalStrength": -65.0,
              "frequency": 2437,
              "ssid": "TestNetwork"
            }
          ]
        }
      }
    },
    "sourceResponse": {
      "success": true,
      "locationInfo": {
        "latitude": 37.7749,
        "longitude": -122.4194,
        "accuracy": 50.0,
        "confidence": 0.8
      },
      "requestId": "single-ap-test-001"
    },
    "options": {
      "calculationDetail": true,
      "processingMode": "sync"
    },
    "metadata": {
      "correlationId": "test-single-ap-001"
    }
  }' | jq '.'
```

#### **4. Dual AP RSSI Ratio Test**
```bash
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-dual-ap-002" \
  -d '{
    "sourceRequest": {
      "svcBody": {
        "svcReq": {
          "clientId": "curl-test-client",
          "requestId": "dual-ap-test-002",
          "wifiInfo": [
            {
              "id": "00:11:22:33:44:01",
              "signalStrength": -65.0,
              "frequency": 2437,
              "ssid": "TestNetwork1"
            },
            {
              "id": "00:11:22:33:44:02",
              "signalStrength": -72.0,
              "frequency": 2412,
              "ssid": "TestNetwork2"
            }
          ]
        }
      }
    },
    "sourceResponse": {
      "success": true,
      "locationInfo": {
        "latitude": 37.7750,
        "longitude": -122.4195,
        "accuracy": 45.0,
        "confidence": 0.85
      },
      "requestId": "dual-ap-test-002"
    },
    "options": {
      "calculationDetail": true,
      "processingMode": "sync"
    },
    "metadata": {
      "correlationId": "test-dual-ap-002"
    }
  }' | jq '.'
```

#### **5. Trilateration Test (3+ APs)**
```bash
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-trilateration-003" \
  -d '{
    "sourceRequest": {
      "svcBody": {
        "svcReq": {
          "clientId": "curl-test-client",
          "requestId": "trilateration-test-003",
          "wifiInfo": [
            {
              "id": "00:11:22:33:44:01",
              "signalStrength": -65.0,
              "frequency": 2437,
              "ssid": "TestNetwork1"
            },
            {
              "id": "00:11:22:33:44:02",
              "signalStrength": -72.0,
              "frequency": 2412,
              "ssid": "TestNetwork2"
            },
            {
              "id": "00:11:22:33:44:03",
              "signalStrength": -68.0,
              "frequency": 2462,
              "ssid": "TestNetwork3"
            }
          ]
        }
      }
    },
    "sourceResponse": {
      "success": true,
      "locationInfo": {
        "latitude": 37.7751,
        "longitude": -122.4196,
        "accuracy": 40.0,
        "confidence": 0.9
      },
      "requestId": "trilateration-test-003"
    },
    "options": {
      "calculationDetail": true,
      "processingMode": "sync"
    },
    "metadata": {
      "correlationId": "test-trilateration-003"
    }
  }' | jq '.'
```

#### **6. High-Density Cluster Test**
```bash
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-cluster-004" \
  -d '{
    "sourceRequest": {
      "svcBody": {
        "svcReq": {
          "clientId": "curl-test-client",
          "requestId": "cluster-test-004",
          "wifiInfo": [
            {
              "id": "00:11:22:33:44:11",
              "signalStrength": -60.0,
              "frequency": 2437
            },
            {
              "id": "00:11:22:33:44:12",
              "signalStrength": -62.0,
              "frequency": 2412
            },
            {
              "id": "00:11:22:33:44:13",
              "signalStrength": -64.0,
              "frequency": 2462
            },
            {
              "id": "00:11:22:33:44:14",
              "signalStrength": -66.0,
              "frequency": 2437
            },
            {
              "id": "00:11:22:33:44:15",
              "signalStrength": -68.0,
              "frequency": 2412
            }
          ]
        }
      }
    },
    "sourceResponse": {
      "success": true,
      "locationInfo": {
        "latitude": 37.7752,
        "longitude": -122.4197,
        "accuracy": 35.0,
        "confidence": 0.95
      },
      "requestId": "cluster-test-004"
    },
    "options": {
      "calculationDetail": true,
      "processingMode": "sync"
    },
    "metadata": {
      "correlationId": "test-cluster-004"
    }
  }' | jq '.'
```

#### **7. Async Processing Test**
```bash
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-async-005" \
  -d '{
    "sourceRequest": {
      "svcBody": {
        "svcReq": {
          "clientId": "curl-test-client",
          "requestId": "async-test-005",
          "wifiInfo": [
            {
              "id": "00:11:22:33:44:01",
              "signalStrength": -65.0,
              "frequency": 2437
            }
          ]
        }
      }
    },
    "options": {
      "calculationDetail": true,
      "processingMode": "async"
    },
    "metadata": {
      "correlationId": "test-async-005"
    }
  }' | jq '.'
```

#### **8. Error Handling Test (Invalid Request)**
```bash
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: test-error-006" \
  -d '{
    "sourceRequest": {
      "svcBody": {
        "svcReq": {
          "clientId": "curl-test-client",
          "requestId": "error-test-006",
          "wifiInfo": []
        }
      }
    }
  }' | jq '.'
```

### **Advanced Usage Examples**

#### **9. Custom Headers and Correlation**
```bash
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: custom-correlation-123" \
  -H "X-Client-Version: 2.1.0" \
  -H "X-Request-Source: mobile-app" \
  -d @scripts/test/data/single-ap-proximity.json | jq '.'
```

#### **10. Performance Testing (Multiple Requests)**
```bash
# Test with 10 concurrent requests
for i in {1..10}; do
  curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: perf-test-$i" \
    -d "{
      \"sourceRequest\": {
        \"svcBody\": {
          \"svcReq\": {
            \"clientId\": \"perf-test-client\",
            \"requestId\": \"perf-test-$i\",
            \"wifiInfo\": [
              {
                \"id\": \"00:11:22:33:44:01\",
                \"signalStrength\": -65.0,
                \"frequency\": 2437
              }
            ]
          }
        }
      },
      \"options\": {
        \"calculationDetail\": true,
        \"processingMode\": \"sync\"
      }
    }" > /dev/null 2>&1 &
done
wait
echo "Performance test completed"
```

#### **11. Response Analysis with jq**
```bash
# Extract specific fields from response
curl -s -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -d @scripts/test/data/single-ap-proximity.json | \
  jq '{
    correlationId: .correlationId,
    processingMode: .processingMode,
    positioningSuccess: .positioningService.success,
    positioningLatency: .positioningService.latencyMs,
    haversineDistance: .comparison.haversineDistanceMeters,
    accuracyDelta: .comparison.accuracyDelta,
    confidenceDelta: .comparison.confidenceDelta,
    apEnrichment: .comparison.accessPointEnrichment
  }'
```

#### **12. Health Monitoring Script**
```bash
#!/bin/bash
# health-monitor.sh

SERVICE_URL="http://localhost:8083/wifi-positioning-integration-service"
HEALTH_ENDPOINT="$SERVICE_URL/health"
INTEGRATION_ENDPOINT="$SERVICE_URL/api/integration/report"

echo "🔍 WiFi Positioning Integration Service Health Monitor"
echo "=================================================="

# Check service health
echo "📊 Service Health:"
HEALTH_RESPONSE=$(curl -s "$HEALTH_ENDPOINT")
echo "$HEALTH_RESPONSE" | jq -r '.status'

# Check positioning service health
echo "📡 Positioning Service Health:"
POSITIONING_HEALTH=$(echo "$HEALTH_RESPONSE" | jq -r '.components.wifiPositioningService.status')
echo "Positioning Service: $POSITIONING_HEALTH"

# Test integration endpoint
echo "🧪 Testing Integration Endpoint:"
TEST_RESPONSE=$(curl -s -X POST "$INTEGRATION_ENDPOINT" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceRequest": {
      "svcBody": {
        "svcReq": {
          "clientId": "health-monitor",
          "requestId": "health-check-'$(date +%s)'",
          "wifiInfo": [
            {
              "id": "00:11:22:33:44:01",
              "signalStrength": -65.0,
              "frequency": 2437
            }
          ]
        }
      }
    },
    "options": {
      "calculationDetail": true
    }
  }')

if [ $? -eq 0 ]; then
  echo "✅ Integration endpoint: OK"
  LATENCY=$(echo "$TEST_RESPONSE" | jq -r '.positioningService.latencyMs')
  echo "⏱️  Positioning latency: ${LATENCY}ms"
else
  echo "❌ Integration endpoint: FAILED"
fi

echo "=================================================="
echo "Health check completed at $(date)"
```

### **Response Examples**

#### **Successful Response Structure**
```json
{
  "correlationId": "test-single-ap-001",
  "receivedAt": "2025-08-15T23:30:00.000Z",
  "processingMode": "sync",
  "derivedRequest": {
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:01",
        "signalStrength": -65.0,
        "frequency": 2437,
        "ssid": "TestNetwork"
      }
    ],
    "client": "curl-test-client",
    "requestId": "single-ap-test-001",
    "application": "wifi-positioning-integration-service",
    "calculationDetail": true
  },
  "positioningService": {
    "httpStatus": 200,
    "latencyMs": 150,
    "response": {
      "result": "SUCCESS",
      "wifiPosition": {
        "latitude": 37.7749,
        "longitude": -122.4194,
        "horizontalAccuracy": 50.0,
        "confidence": 0.8,
        "apCount": 1,
        "calculationTimeMs": 5,
        "methodsUsed": ["proximity"]
      }
    },
    "success": true
  },
  "sourceResponse": {
    "success": true,
    "locationInfo": {
      "latitude": 37.7749,
      "longitude": -122.4194,
      "accuracy": 50.0,
      "confidence": 0.8
    },
    "requestId": "single-ap-test-001"
  },
  "comparison": {
    "haversineDistanceMeters": 0.0,
    "accuracyDelta": 0.0,
    "confidenceDelta": 0.0,
    "methodsUsed": ["proximity"],
    "apCount": 1,
    "calculationTimeMs": 5,
    "positionsComparable": true,
    "accessPointEnrichment": {
      "foundApCount": 1,
      "notFoundApCount": 0,
      "percentRequestFound": 100.0,
      "usedApCount": 1,
      "percentFoundUsed": 100.0
    }
  }
}
```

#### **Error Response Example**
```json
{
  "timestamp": "2025-08-15T23:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "sourceRequest.svcBody.svcReq.wifiInfo": "WiFi info must contain at least one access point"
  },
  "path": "/api/integration/report"
}
```

## 🧪 Full Integration Testing

To test with both services running:

### 1. Start WiFi Positioning Service (in separate terminal)
```bash
cd ../wifi-positioning-service
# Ensure DynamoDB Local and test data are loaded
./scripts/setup/wifi-positioning-test-data.sh
mvn spring-boot:run
```

### 2. Run Integration Tests

#### Quick Test (Individual Test Cases)
```bash
cd ../wifi-positioning-integration-service
# Test specific scenarios
./scripts/test/quick-test.sh single-ap-proximity
./scripts/test/quick-test.sh dual-ap-rssi-ratio --full
```

#### Comprehensive Test Suite
```bash
cd ../wifi-positioning-integration-service
# Run all test cases with detailed analysis
./scripts/test/run-comprehensive-integration-tests.sh
```

**What the comprehensive test provides:**
- ✅ Verify both services are running
- ✅ Test all 6 test scenarios automatically
- ✅ Validate AP enrichment and comparison metrics
- ✅ Performance analysis and success rate calculation
- ✅ Detailed error reporting and troubleshooting

## 📋 API Reference

### Endpoint

**`POST /api/integration/report`**

### Request Format

The service accepts requests in the sample interface format:

```json
{
  "sourceRequest": {
    "svcHeader": {
      "authToken": "client-auth-token"
    },
    "svcBody": {
      "svcReq": {
        "clientId": "client-identifier",
        "requestId": "unique-request-id",
        "wifiInfo": [
          {
            "id": "00:11:22:33:44:01",
            "signalStrength": -65.0,
            "frequency": 2437,
            "ssid": "optional-ssid"
          }
        ],
        "cellInfo": []
      }
    }
  },
  "sourceResponse": {
    "success": true,
    "locationInfo": {
      "latitude": 37.7749,
      "longitude": -122.4194,
      "accuracy": 50.0,
      "confidence": 0.8
    },
    "requestId": "client-request-id"
  },
  "options": {
    "calculationDetail": true,
    "processingMode": "sync"
  },
  "metadata": {
    "correlationId": "correlation-identifier"
  }
}
```

### Response Format

```json
{
  "correlationId": "correlation-identifier",
  "receivedAt": "2025-08-12T15:53:43.506827Z",
  "processingMode": "sync",
  "derivedRequest": {
    "wifiScanResults": [...],
    "client": "client-identifier",
    "requestId": "unique-request-id",
    "application": "wifi-positioning-integration-service",
    "calculationDetail": true
  },
  "positioningService": {
    "httpStatus": 200,
    "latencyMs": 608,
    "response": {...},
    "success": true
  },
  "sourceResponse": {...},
  "comparison": {
    "haversineDistanceMeters": 5.8,
    "accuracyDelta": 94.7,
    "confidenceDelta": -0.27,
    "methodsUsed": ["proximity"],
    "apCount": 1,
    "calculationTimeMs": 2,
    "positionsComparable": true,
    "accessPointEnrichment": {
      "foundApCount": 1,
      "notFoundApCount": 0,
      "percentRequestFound": 100.0,
      "usedApCount": 1,
      "percentFoundUsed": 100.0
    }
  }
}
```

## ⚙️ Configuration

### Application Properties

```yaml
# Integration Service Configuration
integration:
  positioning:
    base-url: http://localhost:8080/wifi-positioning-service
    path: /api/positioning/calculate
    connect-timeout-ms: 300
    read-timeout-ms: 800
  processing:
    default-mode: sync  # or async
    async:
      enabled: false
      queue-capacity: 1000
      workers: 4
  mapping:
    drop-missing-frequency: true
    default-frequency-mhz: 2412
  logging:
    include-payloads: false

# Server Configuration
server:
  port: 8083
  servlet:
    context-path: /wifi-positioning-integration-service

# Actuator Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

### Environment Variables

```bash
# Override default configuration
export INTEGRATION_POSITIONING_BASE_URL=http://prod-positioning-service:8080
export INTEGRATION_POSITIONING_READ_TIMEOUT_MS=1500
export INTEGRATION_PROCESSING_DEFAULT_MODE=async
```

## 📊 Test Data

The service has been tested with MAC addresses from the WiFi positioning service test data:

### Core Test Scenarios
| MAC Address | Test Scenario | Description | Expected Algorithm |
|-------------|---------------|-------------|-------------------|
| `00:11:22:33:44:01` | Single AP | Proximity detection test | `proximity` |
| `00:11:22:33:44:02` | Dual AP | RSSI ratio method test | `rssi_ratio` |
| `00:11:22:33:44:03` | Multiple AP | Trilateration test | `trilateration` |
| `00:11:22:33:44:04` | Edge Case | Signal quality variations | Various |

### Extended Test Coverage
| MAC Range | Purpose | Status Types | Test Focus |
|-----------|---------|--------------|------------|
| `00:11:22:33:44:01` - `00:11:22:33:44:10` | Basic algorithms | `active` | Algorithm selection |
| `00:11:22:33:44:11` - `00:11:22:33:44:15` | High-density cluster | `active` | Maximum likelihood |
| `00:11:22:33:44:16` - `00:11:22:33:44:20` | Mixed signal quality | `active` | Signal processing |
| `00:11:22:33:44:21` - `00:11:22:33:44:25` | Time series data | `active` | Temporal variations |
| `00:11:22:33:44:26` - `00:11:22:33:44:30` | Path loss scenarios | `active` | Distance modeling |
| `00:11:22:33:44:31` - `00:11:22:33:44:35` | Historical analysis | `active` | Data consistency |
| `00:11:22:33:44:36` - `00:11:22:33:44:40` | Error cases | `error`, `expired` | Error handling |
| `00:11:22:33:44:41` - `00:11:22:33:44:45` | Status filtering | `active`, `warning`, `error`, `expired`, `wifi-hotspot` | Status validation |
| `AA:BB:CC:00:00:50` - `AA:BB:CC:00:00:57` | 2D positioning | `active` | Altitude handling |

### Test Data Loading
```bash
# Load comprehensive test data
cd ../wifi-positioning-service
./scripts/setup/wifi-positioning-test-data.sh

# Verify data is loaded
aws dynamodb scan \
  --table-name wifi_access_points \
  --endpoint-url http://localhost:8000 \
  --profile dynamodb-local \
  --select COUNT
```

## 🔍 Monitoring and Health

### Health Endpoints

- **Health Check**: `/health`
- **Actuator**: `/actuator`
- **Info**: `/actuator/info`
- **Metrics**: `/actuator/metrics`

### Logging

The service provides structured logging with:
- **Correlation IDs** for request tracking
- **Latency metrics** for performance monitoring
- **AP enrichment statistics** for analysis
- **Comparison results** for quality assessment

### Metrics

Key metrics available via Actuator:
- HTTP request counts and latencies
- Positioning service call statistics
- AP enrichment success rates
- Comparison calculation times

## 🧪 Testing

### Test Suite Overview

The service includes a comprehensive test suite that validates different use cases and scenarios using real MAC addresses from the `wifi-positioning-test-data.sh` file. The tests verify:

- **Request Mapping**: Sample interface → positioning service format
- **AP Enrichment**: Status tracking, location data, usage analysis
- **Comparison Metrics**: Distance calculations, accuracy deltas
- **Error Handling**: Graceful failure scenarios
- **Integration**: End-to-end service communication

### Test Cases

The test suite includes 6 comprehensive test scenarios:

| Test Case | MAC Addresses | Purpose | Expected Algorithm |
|-----------|---------------|---------|-------------------|
| **Single AP Proximity** | `00:11:22:33:44:01` | Single AP proximity detection | `proximity` |
| **Dual AP RSSI Ratio** | `00:11:22:33:44:01`, `00:11:22:33:44:02` | Dual AP RSSI ratio method | `rssi_ratio` or `weighted_centroid` |
| **Trilateration** | `00:11:22:33:44:01`, `00:11:22:33:44:02`, `00:11:22:33:44:03` | 3D positioning with 3 APs | `trilateration` or `weighted_centroid` |
| **Mixed Status APs** | `00:11:22:33:44:41` through `00:11:22:33:44:45` | Status filtering and eligibility | Various based on status |
| **Unknown MAC** | `FF:FF:FF:FF:FF:01`, `FF:FF:FF:FF:FF:02`, `00:11:22:33:44:01` | Unknown MAC handling | Based on known APs |
| **High-Density Cluster** | `00:11:22:33:44:11` through `00:11:22:33:44:15` | Maximum likelihood with 5 APs | `maximum_likelihood` |

### Test Execution Scripts

#### Quick Test (Individual Test Cases)
```bash
# Test a specific scenario
./scripts/test/quick-test.sh single-ap-proximity
./scripts/test/quick-test.sh dual-ap-rssi-ratio --full

# Available test cases
./scripts/test/quick-test.sh
```

#### Comprehensive Test Suite
```bash
# Run all test cases with detailed analysis
./scripts/test/run-comprehensive-integration-tests.sh
```

**What the comprehensive test provides:**
- ✅ Service health verification
- ✅ All test case execution
- ✅ Performance metrics and analysis
- ✅ Success rate calculation
- ✅ Detailed error reporting

### AP Enrichment Validation

The test suite validates comprehensive AP analysis:

#### Status Tracking
- **`active`**: Normal operation, eligible for positioning
- **`warning`**: Operational but with concerns
- **`error`**: Not operational, ineligible
- **`expired`**: Data expired, ineligible
- **`wifi-hotspot`**: Special status, may have different rules

#### Location Data Extraction
Tests verify that AP location data is correctly extracted from the positioning service response:

```json
{
  "accessPointEnrichment": {
    "foundApCount": 3,
    "notFoundApCount": 2,
    "percentRequestFound": 60.0,
    "usedApCount": 2,
    "percentFoundUsed": 66.7
  }
}
```

#### Usage Analysis
Tests validate the relationship between:
- **Eligible APs**: Status ∈ {active, warning, wifi-hotspot}
- **Used APs**: APs actually used in positioning calculation
- **Unknown Exclusions**: Eligible but unused APs

### Test Data Dependencies

#### Required Services
1. **WiFi Positioning Integration Service** (Port 8083)
2. **WiFi Positioning Service** (Port 8080)
3. **DynamoDB Local** (Port 8000)

#### Test Data Loading
```bash
# Load test data into DynamoDB Local
cd ../wifi-positioning-service
./scripts/setup/wifi-positioning-test-data.sh

# Verify data is loaded
aws dynamodb scan \
  --table-name wifi_access_points \
  --endpoint-url http://localhost:8000 \
  --profile dynamodb-local \
  --select COUNT
```

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
# Start both services first
./scripts/test/run-comprehensive-integration-tests.sh
```

### Manual Testing

```bash
# Test with sample data
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -d @scripts/test/data/single-ap-proximity.json
```

### Performance Validation

#### Response Time
- **Target**: < 2 seconds per test
- **Measurement**: Total request processing time
- **Breakdown**: Integration service + positioning service latency

#### Positioning Service Latency
- **Target**: < 1 second
- **Measurement**: Time for positioning service calls
- **Monitoring**: Via `positioningService.latencyMs`

#### AP Enrichment Performance
- **Target**: Complete data extraction
- **Measurement**: All required fields populated
- **Validation**: No null/undefined values in critical fields

### Test Structure

```
scripts/test/
├── README.md                                    # Test suite documentation
├── TEST_STRUCTURE.md                           # Detailed test mapping
├── data/                                        # Test case JSON files
│   ├── single-ap-proximity.json                # Single AP proximity test
│   ├── dual-ap-rssi-ratio.json                 # Dual AP RSSI ratio test
│   ├── trilateration-test.json                 # Trilateration with 3 APs
│   ├── mixed-status-aps.json                   # Mixed AP statuses test
│   ├── unknown-mac-test.json                   # Unknown MAC addresses test
│   └── high-density-cluster.json               # High-density AP cluster test
├── run-comprehensive-integration-tests.sh      # Full test suite execution
└── quick-test.sh                               # Individual test execution
```

## 🏗️ Development

### Project Structure

```
src/
├── main/java/com/wifi/positioning/
│   ├── controller/          # REST endpoints
│   ├── service/            # Business logic
│   ├── client/             # External service clients
│   ├── mapper/             # Data transformation
│   ├── dto/                # Data transfer objects
│   └── config/             # Configuration classes
├── test/java/              # Test classes
└── resources/              # Configuration files
```

### Key Components

- **`IntegrationReportController`**: Main REST endpoint
- **`SampleInterfaceMapper`**: Request format conversion
- **`PositioningServiceClient`**: WiFi positioning service integration
- **`ComparisonService`**: Position comparison calculations
- **`AccessPointEnrichmentService`**: AP analysis and metrics

### Adding New Features

1. **Create DTOs** in `dto/` package
2. **Implement services** in `service/` package
3. **Add controllers** in `controller/` package
4. **Write tests** in corresponding test packages
5. **Update configuration** in `application.yml`

## 🚀 Deployment

### Docker

```dockerfile
FROM openjdk:21-jdk-slim
COPY target/wifi-positioning-integration-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wifi-positioning-integration-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: wifi-positioning-integration-service
  template:
    metadata:
      labels:
        app: wifi-positioning-integration-service
    spec:
      containers:
      - name: integration-service
        image: wifi-positioning-integration-service:latest
        ports:
        - containerPort: 8083
        env:
        - name: INTEGRATION_POSITIONING_BASE_URL
          value: "http://wifi-positioning-service:8080"
```

## 🔧 Troubleshooting

### Common Issues

1. **Service won't start**
   - Check Java version (requires Java 21+)
   - Verify port 8083 is available
   - Check application.yml configuration

2. **Integration tests fail**
   - Ensure WiFi positioning service is running
   - Verify test data is loaded in DynamoDB
   - Check service URLs and ports

3. **AP enrichment shows 0 found**
   - Verify MAC addresses in test data
   - Check positioning service database
   - Review positioning service logs

### Debug Mode

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Ddebug=true"
```

### Log Analysis

```bash
# View recent logs
tail -f logs/application.log | grep "IntegrationReportController"

# Search for specific correlation ID
grep "correlation-id-123" logs/application.log
```

## 📚 Additional Resources

- **API Documentation**: [Swagger UI](http://localhost:8083/wifi-positioning-integration-service/swagger-ui.html)
- **Requirements**: [Integration Requirements](Documents/integration-requirements.md)
- **Task Plan**: [Implementation Task Plan](Documents/wifi-positioning-integration-service-task-plan.md)
- **Sample Interface**: [Client Interface Format](Documents/sample_interface.txt)
- **Test Suite**: [Test Documentation](scripts/test/README.md)
- **Test Structure**: [Test Case Mapping](scripts/test/TEST_STRUCTURE.md)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🎉 Status

**✅ IMPLEMENTATION COMPLETE**

The WiFi Positioning Integration Service is fully implemented, tested, and ready for production use! All core requirements have been met, and the service has been validated with real positioning service data.

---

**Built with ❤️ using Spring Boot 3.4.5 and Java 21**
