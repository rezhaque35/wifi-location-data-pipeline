# WiFi Positioning Integration Service - Test Suite

This directory contains comprehensive integration tests for the WiFi Positioning Integration Service.

## 📁 Directory Structure

```
scripts/test/
├── README.md                                    # This file
├── data/                                        # Test case JSON files
│   ├── single-ap-proximity.json                # Single AP proximity test
│   ├── dual-ap-rssi-ratio.json                 # Dual AP RSSI ratio test
│   ├── trilateration-test.json                 # Trilateration with 3 APs
│   ├── mixed-status-aps.json                   # Mixed AP statuses test
│   ├── unknown-mac-test.json                   # Unknown MAC addresses test
│   └── high-density-cluster.json               # High-density AP cluster test
└── run-comprehensive-integration-tests.sh      # Main test execution script
```

## 🧪 Test Cases

### 1. Single AP Proximity Test
- **File**: `single-ap-proximity.json`
- **MAC**: `00:11:22:33:44:01`
- **Purpose**: Tests single AP proximity detection algorithm
- **Expected**: HTTP 200, proximity algorithm selection

### 2. Dual AP RSSI Ratio Test
- **File**: `dual-ap-rssi-ratio.json`
- **MACs**: `00:11:22:33:44:01`, `00:11:22:33:44:02`
- **Purpose**: Tests dual AP RSSI ratio method
- **Expected**: HTTP 200, RSSI ratio algorithm selection

### 3. Trilateration Test
- **File**: `trilateration-test.json`
- **MACs**: `00:11:22:33:44:01`, `00:11:22:33:44:02`, `00:11:22:33:44:03`
- **Purpose**: Tests trilateration with three APs
- **Expected**: HTTP 200, trilateration algorithm selection

### 4. Mixed Status APs Test
- **File**: `mixed-status-aps.json`
- **MACs**: `00:11:22:33:44:41` through `00:11:22:33:44:45`
- **Purpose**: Tests AP enrichment with different statuses
- **Statuses**: active, warning, error, expired, wifi-hotspot
- **Expected**: HTTP 200, proper status filtering and eligibility

### 5. Unknown MAC Test
- **File**: `unknown-mac-test.json`
- **MACs**: `FF:FF:FF:FF:FF:01`, `FF:FF:FF:FF:FF:02`, `00:11:22:33:44:01`
- **Purpose**: Tests behavior with unknown MAC addresses
- **Expected**: HTTP 200, proper handling of found/not found APs

### 6. High-Density Cluster Test
- **File**: `high-density-cluster.json`
- **MACs**: `00:11:22:33:44:11` through `00:11:22:33:44:15`
- **Purpose**: Tests maximum likelihood algorithm with 5 APs
- **Expected**: HTTP 200, maximum likelihood algorithm selection

## 🚀 Running the Tests

### Prerequisites

1. **WiFi Positioning Integration Service** running on port 8083
2. **WiFi Positioning Service** running on port 8080
3. **Test data loaded** via `wifi-positioning-test-data.sh`
4. **curl and jq** commands available

### Quick Start

```bash
# Make script executable (first time only)
chmod +x scripts/test/run-comprehensive-integration-tests.sh

# Run all tests
./scripts/test/run-comprehensive-integration-tests.sh
```

### Individual Test Execution

```bash
# Test a specific scenario
curl -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -d @scripts/test/data/single-ap-proximity.json
```

## 📊 Test Validation

The test suite validates:

### Core Functionality
- ✅ **Request Mapping**: Sample interface → positioning service format
- ✅ **Service Integration**: HTTP calls to positioning service
- ✅ **Response Processing**: Integration report generation
- ✅ **Error Handling**: Graceful failure handling

### AP Enrichment
- ✅ **Status Tracking**: Active, warning, error, expired, wifi-hotspot
- ✅ **Location Data**: Latitude, longitude, altitude extraction
- ✅ **Usage Analysis**: Used vs eligible AP counting
- ✅ **Aggregate Metrics**: Found/not found percentages

### Comparison Metrics
- ✅ **Position Comparison**: Haversine distance calculation
- ✅ **Accuracy Analysis**: Delta calculations
- ✅ **Confidence Analysis**: Confidence delta calculations
- ✅ **Algorithm Selection**: Methods used tracking

## 🔧 Test Data Sources

All test MAC addresses are sourced from `wifi-positioning-test-data.sh`:

| Test Type | MAC Addresses | Source Data |
|-----------|---------------|-------------|
| Single AP | `00:11:22:33:44:01` | Single AP proximity test |
| Dual AP | `00:11:22:33:44:01`, `00:11:22:33:44:02` | RSSI ratio test |
| Trilateration | `00:11:22:33:44:01`, `00:11:22:33:44:02`, `00:11:22:33:44:03` | Trilateration test |
| Mixed Status | `00:11:22:33:44:41` through `00:11:22:33:44:45` | Status filtering tests |
| High Density | `00:11:22:33:44:11` through `00:11:22:33:44:15` | Maximum likelihood tests |

## 📝 Adding New Test Cases

### 1. Create Test JSON File

```json
{
  "sourceRequest": {
    "svcHeader": { "authToken": "test-token" },
    "svcBody": {
      "svcReq": {
        "clientId": "test-client",
        "requestId": "test-$(date +%s)",
        "wifiInfo": [
          {
            "id": "00:11:22:33:44:XX",
            "signalStrength": -65.0,
            "frequency": 2437,
            "ssid": "Test_AP"
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
    }
  },
  "options": { "calculationDetail": true },
  "metadata": {
    "correlationId": "test-$(date +%s)",
    "testCase": "new-test-case",
    "description": "Description of what this test validates"
  }
}
```

### 2. Update Test Script

Add your test case to the analysis section in `run-comprehensive-integration-tests.sh`:

```bash
echo "  New Test Cases: $(grep -l '"testCase": "new-test-case"' $TEST_DATA_DIR/*.json | wc -l)"
```

## 🐛 Troubleshooting

### Common Issues

1. **Service Not Running**
   ```bash
   # Check integration service
   curl http://localhost:8083/wifi-positioning-integration-service/health
   
   # Check positioning service
   curl http://localhost:8080/wifi-positioning-service/actuator/health
   ```

2. **Test Data Not Loaded**
   ```bash
   cd ../wifi-positioning-service
   ./scripts/setup/wifi-positioning-test-data.sh
   ```

3. **Port Conflicts**
   - Integration Service: Port 8083
   - Positioning Service: Port 8080
   - DynamoDB Local: Port 8000

### Debug Mode

```bash
# Run with verbose output
./scripts/test/run-comprehensive-integration-tests.sh 2>&1 | tee test-results.log

# Check specific test response
curl -v -X POST http://localhost:8083/wifi-positioning-integration-service/api/integration/report \
  -H "Content-Type: application/json" \
  -d @scripts/test/data/single-ap-proximity.json
```

## 📈 Performance Metrics

The test suite measures:

- **Response Time**: Total request processing time
- **Positioning Latency**: Time for positioning service calls
- **AP Enrichment**: Time for access point analysis
- **Success Rate**: Percentage of tests passing
- **Error Analysis**: Detailed failure reasons

## 🎯 Expected Results

### Success Criteria
- All tests return HTTP 200
- AP enrichment data is complete
- Comparison metrics are calculated correctly
- Positioning service integration works
- Error handling is graceful

### Performance Targets
- Response time < 2 seconds per test
- Positioning service latency < 1 second
- 100% test success rate
- Complete AP enrichment data

---

**The test suite provides comprehensive validation of the WiFi Positioning Integration Service functionality, ensuring production readiness and reliability.**
