# WiFi Positioning Integration Service - Test Scripts

This directory contains comprehensive test scripts for the WiFi Positioning Integration Service.

## Test Scripts

### 1. `run-comprehensive-integration-tests.sh` (Main Test Suite)
**Purpose**: Comprehensive testing of all service functionality including async processing

**Features**:
- **Core Functionality Tests**: All existing test cases (single AP, dual AP, trilateration, etc.)
- **Async Processing Tests**: New comprehensive async functionality testing
- **Concurrent Request Testing**: Verifies async processing with multiple simultaneous requests
- **Performance Comparison**: Async vs sync processing response time comparison
- **Health Monitoring**: Tests async health endpoint and metrics

**Usage**:
```bash
# Run with default settings (localhost:8083)
./run-comprehensive-integration-tests.sh

# Run on specific host:port
./run-comprehensive-integration-tests.sh -h 192.168.1.100:8083

# Run with HTTPS
./run-comprehensive-integration-tests.sh -h api.example.com -s

# Show help
./run-comprehensive-integration-tests.sh --help
```

### 2. `test-async-only.sh` (Focused Async Testing)
**Purpose**: Focused testing of async processing functionality only

**Features**:
- **Async Health Endpoint**: Tests `/async-processing` endpoint accessibility
- **Concurrent Processing**: Sends 5 concurrent requests to verify async handling
- **Performance Comparison**: Direct async vs sync response time comparison
- **Metrics Validation**: Verifies queue depth, thread utilization, and processing counts

**Usage**:
```bash
# Run async tests only
./test-async-only.sh

# Run on specific host:port
./test-async-only.sh -h 192.168.1.100:8083

# Show help
./test-async-only.sh --help
```

## Async Processing Test Coverage

The expanded test suite now includes comprehensive testing of the async processing functionality:

### Phase 4: Async Processing Testing
1. **Async Health Endpoint Test**
   - Verifies `/async-processing` endpoint is accessible
   - Validates response format and metrics structure
   - Checks queue depth, thread utilization, and processing counts

2. **Concurrent Async Processing Test**
   - Sends 5 concurrent requests simultaneously
   - Measures total processing time vs individual response times
   - Verifies all requests are processed successfully
   - Compares baseline and post-request metrics

3. **Async vs Sync Comparison Test**
   - Sends identical requests in both sync and async modes
   - Measures and compares response times
   - Validates performance benefits of async processing

### Key Metrics Monitored
- **Queue Metrics**: Current depth, capacity, utilization percentage
- **Thread Metrics**: Active threads, pool size, utilization percentage
- **Processing Metrics**: Successful/failed counts, rejection counts, success rates
- **Health Status**: Overall async processing health (HEALTHY, DEGRADED, CRITICAL)

## Test Data Requirements

The test scripts require test data files in the `./data` directory:
- JSON files with WiFi scan data
- Various test scenarios (single AP, dual AP, trilateration, etc.)
- Error handling test cases

## Prerequisites

- WiFi Positioning Integration Service running
- WiFi Positioning Service running with test data loaded
- `curl` command available
- `jq` command available for JSON parsing
- `bc` command available for floating-point math

## Test Results

### Success Criteria
- **Core Tests**: All functionality tests pass
- **Async Tests**: All async processing tests pass
- **Health Checks**: Both main health and async health endpoints accessible
- **Performance**: Async processing provides expected performance benefits

### Exit Codes
- **0**: All tests passed (core + async)
- **1**: Some tests failed or async processing issues detected

## Monitoring and Debugging

### Async Health Endpoint
- **URL**: `/async-processing`
- **Purpose**: Monitor async processing metrics without affecting main health
- **Metrics**: Queue depth, thread utilization, processing success rates

### Main Health Endpoint
- **URL**: `/health`
- **Purpose**: Overall service health (unaffected by async processing)
- **Dependencies**: Positioning service connectivity only

## Example Test Output

```
PHASE 4: Async Processing Testing
=============================================

Testing async processing functionality...

Test 1: Async Health Endpoint
=====================================
✓ Async health endpoint test PASSED

Test 2: Concurrent Async Processing
=============================================
✓ Concurrent async processing test PASSED

Test 3: Async vs Sync Comparison
==========================================
✓ Async vs sync comparison test PASSED

🎉 ALL ASYNC PROCESSING TESTS PASSED!

The async processing functionality is working correctly:
  ✓ Async health endpoint is accessible and providing metrics
  ✓ Concurrent requests are being processed successfully
  ✓ Async processing is operational
  ✓ Queue depth and processing metrics are being tracked
```

## Troubleshooting

### Common Issues
1. **Async Health Endpoint Unavailable**
   - Check if service is running
   - Verify `/async-processing` endpoint is exposed in configuration
   - Check service logs for startup errors

2. **Concurrent Request Failures**
   - Verify thread pool configuration in `application.yml`
   - Check queue capacity settings
   - Review service logs for async processing errors

3. **Performance Issues**
   - Monitor queue utilization metrics
   - Check thread pool sizing
   - Verify positioning service response times

### Debug Commands
```bash
# Check async health metrics
curl http://localhost:8083/wifi-positioning-integration-service/async-processing

# Check main health
curl http://localhost:8083/wifi-positioning-integration-service/health

# Run focused async tests
./test-async-only.sh

# Run comprehensive tests
./run-comprehensive-integration-tests.sh
```
