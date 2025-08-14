# Test Structure Overview

## 🎯 Purpose

This test suite validates the WiFi Positioning Integration Service by testing different use cases and scenarios using real MAC addresses from the `wifi-positioning-test-data.sh` file. The tests verify:

1. **Request Mapping**: Sample interface → positioning service format
2. **AP Enrichment**: Status tracking, location data, usage analysis
3. **Comparison Metrics**: Distance calculations, accuracy deltas
4. **Error Handling**: Graceful failure scenarios
5. **Integration**: End-to-end service communication

## 📊 Test Case Mapping

### 1. Single AP Proximity Test
- **Test File**: `single-ap-proximity.json`
- **MAC Address**: `00:11:22:33:44:01`
- **Source Data**: Single AP proximity test from `wifi-positioning-test-data.sh`
- **Expected Algorithm**: `proximity`
- **Validation**: 
  - AP found in database
  - Status: `active`
  - Location: `37.7749, -122.4194, 10.5`
  - Single AP algorithm selection

### 2. Dual AP RSSI Ratio Test
- **Test File**: `dual-ap-rssi-ratio.json`
- **MAC Addresses**: 
  - `00:11:22:33:44:01` (SingleAP_Test)
  - `00:11:22:33:44:02` (DualAP_Test)
- **Source Data**: RSSI ratio method test
- **Expected Algorithm**: `rssi_ratio` or `weighted_centroid`
- **Validation**:
  - Both APs found in database
  - Status: `active`
  - Dual AP algorithm selection
  - RSSI-based positioning

### 3. Trilateration Test
- **Test File**: `trilateration-test.json`
- **MAC Addresses**:
  - `00:11:22:33:44:01` (SingleAP_Test)
  - `00:11:22:33:44:02` (DualAP_Test)
  - `00:11:22:33:44:03` (TriAP_Test)
- **Source Data**: Trilateration test with well-distributed APs
- **Expected Algorithm**: `trilateration` or `weighted_centroid`
- **Validation**:
  - All three APs found
  - Good geometric distribution
  - 3D positioning capability

### 4. Mixed Status APs Test
- **Test File**: `mixed-status-aps.json`
- **MAC Addresses**: `00:11:22:33:44:41` through `00:11:22:33:44:45`
- **Source Data**: Status filtering tests
- **Statuses**:
  - `00:11:22:33:44:41`: `active`
  - `00:11:22:33:33:44:42`: `warning`
  - `00:11:22:33:44:43`: `error`
  - `00:11:22:33:44:44`: `expired`
  - `00:11:22:33:44:45`: `wifi-hotspot`
- **Validation**:
  - Status-based filtering
  - Eligibility determination
  - AP enrichment metrics

### 5. Unknown MAC Test
- **Test File**: `unknown-mac-test.json`
- **MAC Addresses**:
  - `FF:FF:FF:FF:FF:01` (Unknown AP 1)
  - `FF:FF:FF:FF:FF:02` (Unknown AP 2)
  - `00:11:22:33:44:01` (Known AP)
- **Purpose**: Test behavior with unknown MAC addresses
- **Validation**:
  - Unknown APs marked as not found
  - Known APs properly enriched
  - Mixed found/not found handling

### 6. High-Density Cluster Test
- **Test File**: `high-density-cluster.json`
- **MAC Addresses**: `00:11:22:33:44:11` through `00:11:22:33:44:15`
- **Source Data**: High-density AP cluster for maximum likelihood
- **Expected Algorithm**: `maximum_likelihood`
- **Validation**:
  - 5 APs in cluster
  - Maximum likelihood algorithm selection
  - Dense environment handling

## 🔍 AP Enrichment Validation

### Status Tracking
The tests validate that the service correctly identifies and categorizes AP statuses:

- **`active`**: Normal operation, eligible for positioning
- **`warning`**: Operational but with concerns
- **`error`**: Not operational, ineligible
- **`expired`**: Data expired, ineligible
- **`wifi-hotspot`**: Special status, may have different rules

### Location Data Extraction
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

### Usage Analysis
Tests validate the relationship between:
- **Eligible APs**: Status ∈ {active, warning, wifi-hotspot}
- **Used APs**: APs actually used in positioning calculation
- **Unknown Exclusions**: Eligible but unused APs

## 📈 Performance Validation

### Response Time
- **Target**: < 2 seconds per test
- **Measurement**: Total request processing time
- **Breakdown**: Integration service + positioning service latency

### Positioning Service Latency
- **Target**: < 1 second
- **Measurement**: Time for positioning service calls
- **Monitoring**: Via `positioningService.latencyMs`

### AP Enrichment Performance
- **Target**: Complete data extraction
- **Measurement**: All required fields populated
- **Validation**: No null/undefined values in critical fields

## 🧪 Test Execution Flow

### 1. Service Health Check
```bash
# Verify both services are running
curl http://localhost:8083/wifi-positioning-integration-service/health
curl http://localhost:8080/wifi-positioning-service/actuator/health
```

### 2. Test Data Validation
```bash
# Ensure test data is loaded
cd ../wifi-positioning-service
./scripts/setup/wifi-positioning-test-data.sh
```

### 3. Test Execution
```bash
# Run comprehensive test suite
./scripts/test/run-comprehensive-integration-tests.sh

# Or run individual tests
./scripts/test/quick-test.sh single-ap-proximity
./scripts/test/quick-test.sh dual-ap-rssi-ratio --full
```

### 4. Results Analysis
- **Success Rate**: 100% target
- **Performance Metrics**: Response time, latency
- **Data Completeness**: AP enrichment validation
- **Error Analysis**: Detailed failure reasons

## 🔧 Test Data Dependencies

### Required Services
1. **WiFi Positioning Integration Service** (Port 8083)
2. **WiFi Positioning Service** (Port 8080)
3. **DynamoDB Local** (Port 8000)

### Test Data Loading
```bash
# Load test data into DynamoDB Local
cd wifi-positioning-service
./scripts/setup/wifi-positioning-test-data.sh

# Verify data is loaded
aws dynamodb scan \
  --table-name wifi_access_points \
  --endpoint-url http://localhost:8000 \
  --profile dynamodb-local \
  --select COUNT
```

### MAC Address Coverage
The test suite covers MAC addresses from the test data:
- **Range**: `00:11:22:33:44:01` to `00:11:22:33:44:45`
- **Special Cases**: `AA:BB:CC:00:00:50` to `AA:BB:CC:00:00:57` (2D positioning)
- **Unknown MACs**: `FF:FF:FF:FF:FF:01`, `FF:FF:FF:FF:FF:02`

## 📝 Adding New Test Cases

### 1. Identify Test Scenario
- Choose appropriate MAC addresses from test data
- Define expected algorithm selection
- Determine validation criteria

### 2. Create Test JSON
- Follow existing format structure
- Include realistic source response data
- Add descriptive metadata

### 3. Update Test Scripts
- Add test case to analysis section
- Update test count calculations
- Include in quick test options

### 4. Validate Test Case
- Run individual test
- Verify expected results
- Check AP enrichment data
- Validate comparison metrics

## 🎯 Success Criteria

### Functional Requirements
- ✅ All tests return HTTP 200
- ✅ Request mapping works correctly
- ✅ AP enrichment data is complete
- ✅ Comparison metrics are calculated
- ✅ Error handling is graceful

### Performance Requirements
- ✅ Response time < 2 seconds
- ✅ Positioning latency < 1 second
- ✅ 100% test success rate
- ✅ Complete data extraction

### Integration Requirements
- ✅ Service communication works
- ✅ Data flow is correct
- ✅ Error propagation is handled
- ✅ Logging is comprehensive

---

**This test structure ensures comprehensive validation of the WiFi Positioning Integration Service using real-world test data and realistic scenarios.**
