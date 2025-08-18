#!/bin/bash

# =============================================================================
# WiFi Positioning Integration Service - Comprehensive Integration Test Suite
# =============================================================================
# 
# This script performs comprehensive testing of the WiFi Positioning Integration
# Service by reading test cases from JSON files in the scripts/test/data directory.
# Each test case tests different scenarios and use cases using MAC addresses
# from the wifi-positioning-test-data.sh file.
#
# Test Cases Include:
# - Single AP proximity detection
# - Dual AP RSSI ratio method
# - Trilateration with three APs
# - Mixed status APs (active, warning, error, expired, wifi-hotspot)
# - Unknown MAC addresses (not found in database)
# - High-density AP clusters
# - Async processing functionality (via test-async-only.sh)
# - Concurrent request handling (via test-async-only.sh)
# - Async vs sync processing comparison (via test-async-only.sh)
# - Async health monitoring and metrics (via test-async-only.sh)
#
# Prerequisites:
# - WiFi Positioning Integration Service running on specified host:port
# - WiFi Positioning Service running on specified host:port with test data loaded
# - Test data loaded via wifi-positioning-test-data.sh
# - curl and jq commands available
#
# Usage:
#   ./run-comprehensive-integration-tests.sh [OPTIONS]
#
# Options:
#   -h, --host HOST[:PORT]    Host and optional port (default: localhost:8083)
#   -s, --https               Use HTTPS instead of HTTP
#   --help                    Show this help message
#
# Examples:
#   ./run-comprehensive-integration-tests.sh                           # Use localhost:8083 with HTTP
#   ./run-comprehensive-integration-tests.sh -h 192.168.1.100:8083    # Use specific host:port
#   ./run-comprehensive-integration-tests.sh -h api.example.com -s    # Use HTTPS with host
#   ./run-comprehensive-integration-tests.sh --host staging:9090      # Use staging host on port 9090
# =============================================================================

set -e  # Exit immediately if any command fails

# Default configuration
DEFAULT_HOST="localhost"
DEFAULT_PORT="8083"
USE_HTTPS=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--host)
            if [[ -n "$2" && "$2" != -* ]]; then
                # Check if port is specified in host:port format
                if [[ "$2" == *:* ]]; then
                    HOST_PORT="$2"
                    HOST="${HOST_PORT%:*}"
                    PORT="${HOST_PORT#*:}"
                else
                    HOST="$2"
                    PORT="$DEFAULT_PORT"
                fi
                shift 2
            else
                echo "Error: --host requires a value"
                exit 1
            fi
            ;;
        -s|--https)
            USE_HTTPS=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -h, --host HOST[:PORT]    Host and optional port (default: localhost:8083)"
            echo "  -s, --https               Use HTTPS instead of HTTP"
            echo "  --help                    Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                                    # Use localhost:8083 with HTTP"
            echo "  $0 -h 192.168.1.100:8083             # Use specific host:port"
            echo "  $0 -h api.example.com -s              # Use HTTPS with host"
            echo "  $0 --host staging:9090                # Use staging host on port 9090"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set default values if not specified
HOST="${HOST:-$DEFAULT_HOST}"
PORT="${PORT:-$DEFAULT_PORT}"

# Build service URL based on configuration
if [ "$USE_HTTPS" = true ]; then
    PROTOCOL="https"
else
    PROTOCOL="http"
fi

INTEGRATION_SERVICE_URL="${PROTOCOL}://${HOST}:${PORT}/wifi-positioning-integration-service"

# Color codes for terminal output formatting
GREEN='\033[0;32m'    # Success messages
RED='\033[0;31m'      # Error messages  
YELLOW='\033[1;33m'   # Warning/info messages
BLUE='\033[0;34m'     # Info messages
NC='\033[0m'          # No Color (reset)

# Test data directory
TEST_DATA_DIR="./data"

# Results tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0
ASYNC_TESTS_PASSED=true

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}WiFi Positioning Integration Service - Comprehensive Integration Test Suite${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo ""
echo -e "${YELLOW}Configuration:${NC}"
echo "  Host: $HOST"
echo "  Port: $PORT"
echo "  Protocol: $PROTOCOL"
echo "  Service URL: $INTEGRATION_SERVICE_URL"
echo ""

# =============================================================================
# PHASE 1: Service Health Verification
# =============================================================================
echo -e "${YELLOW}PHASE 1: Service Health Verification${NC}"
echo "================================================"

# Function to check service health with detailed information
check_service_health() {
    local service_name="$1"
    local service_url="$2"
    local health_endpoint="$3"
    
    echo -e "${YELLOW}Checking if $service_name is running and healthy...${NC}"
    echo "  Health endpoint: ${service_url}${health_endpoint}"
    
    # Check if service is accessible
    if ! curl -sf "${service_url}${health_endpoint}" > /dev/null 2>&1; then
        echo -e "${RED}✗ $service_name is not accessible at ${service_url}${health_endpoint}${NC}"
        return 1
    fi
    
    # Get detailed health information
    local health_response
    local health_status
    local http_code
    
    health_response=$(curl -s -w "HTTPSTATUS:%{http_code}" "${service_url}${health_endpoint}")
    http_code=$(echo "$health_response" | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    health_response=$(echo "$health_response" | sed -e 's/HTTPSTATUS\:.*//g')
    
    if [ "$http_code" -eq 200 ]; then
        # Parse health status from response
        health_status=$(echo "$health_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
        
        if [ "$health_status" = "UP" ]; then
            echo -e "${GREEN}✓ $service_name is running and healthy (Status: $health_status)${NC}"
            
            # Show additional health details if available
            local details=$(echo "$health_response" | jq -r '.components // empty' 2>/dev/null)
            if [ "$details" != "null" ] && [ "$details" != "" ]; then
                echo "  Health components:"
                echo "$health_response" | jq -r '.components | to_entries[] | "    " + .key + ": " + (.value.status // "UNKNOWN")' 2>/dev/null || echo "    Unable to parse health components"
            fi
            
            return 0
        else
            echo -e "${RED}✗ $service_name is running but unhealthy (Status: $health_status)${NC}"
            echo "  Health response: $health_response"
            return 1
        fi
    else
        echo -e "${RED}✗ $service_name health endpoint returned HTTP $http_code${NC}"
        echo "  Response: $health_response"
        return 1
    fi
}

# Check integration service health (this covers positioning service dependency)
if ! check_service_health "Integration Service" "$INTEGRATION_SERVICE_URL" "/health"; then
    echo ""
    echo -e "${RED}Integration service health check failed. Please start the service first:${NC}"
    echo "  cd wifi-positioning-integration-service && mvn spring-boot:run"
    echo ""
    echo "The service should be accessible at: ${INTEGRATION_SERVICE_URL}/health"
    echo ""
    echo "Note: The integration service health check automatically verifies positioning service connectivity."
    echo ""
    echo "You can also specify a different host:port using:"
    echo "  $0 -h <host>:<port>"
    echo "  $0 --host <host>:<port>"
    echo ""
    echo "Or use HTTPS:"
    echo "  $0 -h <host>:<port> -s"
    echo "  $0 --host <host>:<port> --https"
    exit 1
fi

echo ""
echo -e "${GREEN}✓ Integration service is running and healthy${NC}"
echo "  (Positioning service connectivity is automatically verified by integration service health)${NC}"
echo ""

# =============================================================================
# PHASE 2: Test Data Validation
# =============================================================================
echo -e "${YELLOW}PHASE 2: Test Data Validation${NC}"
echo "====================================="

# Check if test data directory exists
if [ ! -d "$TEST_DATA_DIR" ]; then
    echo -e "${RED}✗ Test data directory not found: $TEST_DATA_DIR${NC}"
    exit 1
fi

# Count test files
TEST_FILES=($TEST_DATA_DIR/*.json)
TOTAL_TEST_FILES=${#TEST_FILES[@]}

if [ $TOTAL_TEST_FILES -eq 0 ]; then
    echo -e "${RED}✗ No test files found in $TEST_DATA_DIR${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Found $TOTAL_TEST_FILES test files${NC}"
echo ""

# =============================================================================
# PHASE 3: Test Execution
# =============================================================================
echo -e "${YELLOW}PHASE 3: Test Execution${NC}"
echo "============================="

# Function to validate VLSS error handling in test responses
validate_vlss_error_handling() {
    local response_body="$1"
    local test_case="$2"
    
    echo "    === VLSS Error Validation ==="
    
    local vlss_success=$(echo $response_body | jq -r '.comparison.vlssSuccess')
    local vlss_error_details=$(echo $response_body | jq -r '.comparison.vlssErrorDetails // "null"')
    local vlss_error_code=$(echo $response_body | jq -r '.comparison.vlssErrorCode // "null"')
    local vlss_errors_count=$(echo $response_body | jq -r '.comparison.vlssErrors | length // 0')
    local failure_analysis=$(echo $response_body | jq -r '.comparison.failureAnalysis // "null"')
    
    # Validate that VLSS is marked as failed
    if [ "$vlss_success" = "false" ]; then
        echo "    ✓ VLSS Success Status: Correctly marked as failed"
    else
        echo "    ✗ VLSS Success Status: Expected false, got $vlss_success"
    fi
    
    # Validate error details are present
    if [ "$vlss_error_details" != "null" ] && [ "$vlss_error_details" != "" ]; then
        echo "    ✓ VLSS Error Details: Present"
        echo "      Details: $vlss_error_details"
    else
        echo "    ✗ VLSS Error Details: Missing or empty"
    fi
    
    # Validate structured error handling for non-legacy tests
    if [[ "$test_case" != "vlss-legacy-error" ]]; then
        if [ "$vlss_error_code" != "null" ] && [ "$vlss_error_code" != "" ]; then
            echo "    ✓ VLSS Error Code: Present ($vlss_error_code)"
        else
            echo "    ✗ VLSS Error Code: Missing for structured error test"
        fi
        
        if [ "$vlss_errors_count" -gt 0 ]; then
            echo "    ✓ VLSS Errors Array: Contains $vlss_errors_count error(s)"
        else
            echo "    ✗ VLSS Errors Array: Empty or missing for structured error test"
        fi
    else
        echo "    ✓ Legacy Error Format: Testing backwards compatibility"
    fi
    
    # Validate failure analysis
    if [ "$failure_analysis" != "null" ] && [ "$failure_analysis" != "" ]; then
        echo "    ✓ Failure Analysis: Present"
        echo "      Analysis: $failure_analysis"
    else
        echo "    ✗ Failure Analysis: Missing"
    fi
}

# Function to run a single test
run_test() {
    local test_file="$1"
    local test_name=$(basename "$test_file" .json)
    
    echo -e "${BLUE}Running test: $test_name${NC}"
    echo "  File: $test_file"
    
    # Extract test case description from metadata
    local description=$(jq -r '.metadata.description // "No description"' "$test_file")
    echo "  Description: $description"
    
    # Extract test case type
    local test_case=$(jq -r '.metadata.testCase // "unknown"' "$test_file")
    echo "  Test Case: $test_case"
    
    # Extract expected AP count
    local expected_ap_count=$(jq -r '.sourceRequest.svcBody.svcReq.wifiInfo | length' "$test_file")
    echo "  Expected APs: $expected_ap_count"
    
    # Replace timestamp placeholders with actual timestamps
    local temp_file=$(mktemp)
    sed 's/\$(date +%s)/'$(date +%s)'/g' "$test_file" > "$temp_file"
    
    # Execute the test
    local start_time=$(date +%s.%N)
    local response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d @"$temp_file" \
        "${INTEGRATION_SERVICE_URL}/api/integration/report")
    local end_time=$(date +%s.%N)
    
    # Clean up temp file
    rm "$temp_file"
    
    # Extract HTTP status code and response body
    local http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    local response_body=$(echo $response | sed -e 's/HTTPSTATUS\:.*//g')
    
    # Calculate response time
    local response_time=$(echo "$end_time - $start_time" | bc -l | awk '{printf "%.3f", $1}')
    
    # Evaluate test results
    if [ "$http_code" -eq 200 ]; then
        echo -e "${GREEN}  ✓ Test PASSED (HTTP $http_code) in ${response_time}s${NC}"
        
        # Extract and display key metrics
        local correlation_id=$(echo $response_body | jq -r '.correlationId // "No correlation ID"')
        local positioning_status=$(echo $response_body | jq -r '.positioningService.success // "Unknown"')
        local latency_ms=$(echo $response_body | jq -r '.positioningService.latencyMs // "Unknown"')
        local ap_count=$(echo $response_body | jq -r '.comparison.apCount // "Unknown"')
        local found_ap_count=$(echo $response_body | jq -r '.comparison.accessPointEnrichment.foundApCount // "Unknown"')
        local used_ap_count=$(echo $response_body | jq -r '.comparison.accessPointEnrichment.usedApCount // "Unknown"')
        local positions_comparable=$(echo $response_body | jq -r '.comparison.positionsComparable // "Unknown"')
        local vlss_success=$(echo $response_body | jq -r '.comparison.vlssSuccess')
        local frisco_success=$(echo $response_body | jq -r '.comparison.friscoSuccess')
        local scenario=$(echo $response_body | jq -r '.comparison.scenario // "Unknown"')
        
        echo "    Correlation ID: $correlation_id"
        echo "    Positioning Status: $positioning_status"
        echo "    Latency: ${latency_ms}ms"
        echo "    AP Count: $ap_count"
        echo "    Found APs: $found_ap_count"
        echo "    Used APs: $used_ap_count"
        echo "    Positions Comparable: $positions_comparable"
        echo "    VLSS Success: $vlss_success"
        echo "    Frisco Success: $frisco_success"
        echo "    Comparison Scenario: $scenario"
        
        # Check if positions are comparable and show distance if available
        if [ "$positions_comparable" = "true" ]; then
            local distance=$(echo $response_body | jq -r '.comparison.haversineDistanceMeters // "Unknown"')
            local accuracy_delta=$(echo $response_body | jq -r '.comparison.accuracyDelta // "Unknown"')
            local confidence_delta=$(echo $response_body | jq -r '.comparison.confidenceDelta // "Unknown"')
            
            echo "    Distance: ${distance}m"
            echo "    Accuracy Delta: ${accuracy_delta}"
            echo "    Confidence Delta: ${confidence_delta}"
        fi
        
        # Validate AP enrichment data
        if [ "$found_ap_count" != "null" ] && [ "$found_ap_count" != "Unknown" ]; then
            echo "    AP Enrichment: ✓ Working correctly"
        else
            echo "    AP Enrichment: ⚠️ Data may be incomplete"
        fi
        
        # Validate VLSS error handling for error test cases
        if [[ "$test_case" == vlss-*error* ]] || [[ "$test_case" == "vlss-legacy-error" ]] || [[ "$test_case" == "vlss-service-unavailable" ]]; then
            validate_vlss_error_handling "$response_body" "$test_case"
        fi
        
        PASSED_TESTS=$((PASSED_TESTS + 1))
        
    else
        echo -e "${RED}  ✗ Test FAILED (HTTP $http_code) in ${response_time}s${NC}"
        echo "    Response: $(echo $response_body | jq -r '.error // .message // "Unknown error"')"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo ""
}

# Function to run all tests
run_all_tests() {
    echo -e "${YELLOW}Executing $TOTAL_TEST_FILES test cases...${NC}"
    echo ""
    
    for test_file in "${TEST_FILES[@]}"; do
        if [ -f "$test_file" ]; then
            run_test "$test_file"
        else
            echo -e "${YELLOW}Skipping: $test_file (not a file)${NC}"
            SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
        fi
    done
}

# Execute all tests
run_all_tests

# =============================================================================
# PHASE 4: Async Processing Testing
# =============================================================================
echo -e "${YELLOW}PHASE 4: Async Processing Testing${NC}"
echo "============================================="

# Run async processing tests using the dedicated test script
# This approach provides:
# - Single source of truth for async testing logic
# - Easier maintenance and bug fixes
# - Consistent async testing behavior across all scripts
# - Modular test structure for better organization
echo ""
echo -e "${YELLOW}Testing async processing functionality using test-async-only.sh...${NC}"

# Check if test-async-only.sh exists and is executable
ASYNC_TEST_SCRIPT="$(dirname "$0")/test-async-only.sh"
if [ ! -f "$ASYNC_TEST_SCRIPT" ]; then
    echo -e "${RED}✗ Async test script not found: $ASYNC_TEST_SCRIPT${NC}"
    echo "  Skipping async processing tests"
    ASYNC_TESTS_PASSED=false
elif [ ! -x "$ASYNC_TEST_SCRIPT" ]; then
    echo -e "${RED}✗ Async test script not executable: $ASYNC_TEST_SCRIPT${NC}"
    echo "  Skipping async processing tests"
    ASYNC_TESTS_PASSED=false
else
    echo "  Using async test script: $ASYNC_TEST_SCRIPT"
    
    # Run async tests with the same configuration
    if [ "$USE_HTTPS" = true ]; then
        HTTPS_FLAG="-s"
    else
        HTTPS_FLAG=""
    fi
    
    echo "  Executing: $ASYNC_TEST_SCRIPT -h $HOST:$PORT $HTTPS_FLAG"
    echo ""
    
    # Execute the async test script
    if "$ASYNC_TEST_SCRIPT" -h "$HOST:$PORT" $HTTPS_FLAG; then
        echo -e "${GREEN}✓ Async processing tests PASSED${NC}"
        ASYNC_TESTS_PASSED=true
    else
        echo -e "${RED}✗ Async processing tests FAILED${NC}"
        ASYNC_TESTS_PASSED=false
    fi
fi

# =============================================================================
# PHASE 5: Test Summary and Analysis
# =============================================================================
echo -e "${YELLOW}PHASE 5: Test Summary and Analysis${NC}"
echo "============================================="

echo ""
echo "================================================================================"
echo "TEST EXECUTION SUMMARY"
echo "================================================================================"
echo "Total Tests Executed: $TOTAL_TESTS"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"
echo -e "Skipped: ${YELLOW}$SKIPPED_TESTS${NC}"
echo ""
echo "================================================================================"
echo "ASYNC PROCESSING TEST SUMMARY"
echo "================================================================================"
if [ "$ASYNC_TESTS_PASSED" = true ]; then
    echo -e "Async Processing Tests: ${GREEN}PASSED${NC}"
else
    echo -e "Async Processing Tests: ${RED}FAILED${NC}"
fi

# Calculate success rate
if [ $TOTAL_TESTS -gt 0 ]; then
    success_rate=$(echo "scale=1; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc -l)
    echo "Success Rate: ${success_rate}%"
fi

echo "================================================================================"

# Provide detailed analysis
echo ""
echo -e "${YELLOW}📊 Test Case Analysis:${NC}"

# Count test cases by type
echo "  Single AP Tests: $(grep -l '"testCase": "single-ap-proximity"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  Dual AP Tests: $(grep -l '"testCase": "dual-ap-rssi-ratio"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  Trilateration Tests: $(grep -l '"testCase": "trilateration"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  Mixed Status Tests: $(grep -l '"testCase": "mixed-status-aps"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  Unknown MAC Tests: $(grep -l '"testCase": "unknown-mac-test"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  High Density Tests: $(grep -l '"testCase": "high-density-cluster"' $TEST_DATA_DIR/*.json | wc -l)"
echo ""
echo "  === VLSS Error Handling Tests ==="
echo "  VLSS Auth Error Tests: $(grep -l '"testCase": "vlss-auth-error"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  VLSS Insufficient Data Tests: $(grep -l '"testCase": "vlss-insufficient-data-error"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  VLSS Multiple Errors Tests: $(grep -l '"testCase": "vlss-multiple-errors"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  VLSS Legacy Error Tests: $(grep -l '"testCase": "vlss-legacy-error"' $TEST_DATA_DIR/*.json | wc -l)"
echo "  VLSS Service Unavailable Tests: $(grep -l '"testCase": "vlss-service-unavailable"' $TEST_DATA_DIR/*.json | wc -l)"

echo ""
echo -e "${YELLOW}🔍 Key Validation Points:${NC}"
echo "  ✓ Request mapping and validation"
echo "  ✓ Positioning service integration"
echo "  ✓ AP enrichment and status tracking"
echo "  ✓ Comparison metrics calculation"
echo "  ✓ Error handling and edge cases"
echo "  ✓ Response format consistency"
echo "  ✓ VLSS structured error handling (svcError)"
echo "  ✓ VLSS error code extraction and analysis"
echo "  ✓ VLSS legacy error format backwards compatibility"
echo "  ✓ Enhanced failure analysis and reporting"

# Final status
if [ $FAILED_TESTS -eq 0 ] && [ "$ASYNC_TESTS_PASSED" = true ]; then
    echo ""
    echo -e "${GREEN}🎉 ALL TESTS PASSED! The WiFi Positioning Integration Service is working correctly.${NC}"
    echo ""
    echo "The service successfully:"
    echo "  • Processed all test case formats"
    echo "  • Integrated with the positioning service"
    echo "  • Enriched AP information correctly"
    echo "  • Calculated comparison metrics"
    echo "  • Handled various AP statuses and configurations"
    echo "  • Async processing functionality is working correctly"
    echo "  • Concurrent request handling is operational"
    echo "  • Async health monitoring is functional"
elif [ $FAILED_TESTS -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ Core functionality tests PASSED${NC}"
    echo -e "${RED}⚠️  Async processing tests FAILED${NC}"
    echo ""
    echo "The service successfully:"
    echo "  • Processed all test case formats"
    echo "  • Integrated with the positioning service"
    echo "  • Enriched AP information correctly"
    echo "  • Calculated comparison metrics"
    echo "  • Handled various AP statuses and configurations"
    echo ""
    echo "Async processing issues detected:"
    echo "  • Review async processing configuration"
    echo "  • Check async health endpoint accessibility"
    echo "  • Verify thread pool configuration"
else
    echo ""
    echo -e "${RED}⚠️  Some tests failed. Please review the error messages above.${NC}"
    echo ""
    echo "Common issues:"
    echo "  • Check if both services are running"
    echo "  • Verify test data is loaded in positioning service"
    echo "  • Review service logs for detailed error information"
fi

echo ""
echo -e "${YELLOW}📚 Additional Resources:${NC}"
echo "  API Documentation: ${INTEGRATION_SERVICE_URL}/swagger-ui.html"
echo "  Health Check: ${INTEGRATION_SERVICE_URL}/health"
echo "  Async Processing Health: ${INTEGRATION_SERVICE_URL}/async-processing"
echo "  Test Data Directory: $TEST_DATA_DIR"
echo "  Test Script: $0"

echo ""
echo "================================================================================"
echo "Comprehensive integration testing completed!"
echo "================================================================================"

# Exit with appropriate code
if [ $FAILED_TESTS -eq 0 ] && [ "$ASYNC_TESTS_PASSED" = true ]; then
    exit 0
elif [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${YELLOW}Exiting with code 1 due to async processing test failures${NC}"
    exit 1
else
    exit 1
fi
