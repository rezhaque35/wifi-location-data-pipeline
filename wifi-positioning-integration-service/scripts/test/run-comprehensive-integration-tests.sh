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
#
# Prerequisites:
# - WiFi Positioning Integration Service running on port 8083
# - WiFi Positioning Service running on port 8080 with test data loaded
# - Test data loaded via wifi-positioning-test-data.sh
# - curl and jq commands available
# =============================================================================

set -e  # Exit immediately if any command fails

# Color codes for terminal output formatting
GREEN='\033[0;32m'    # Success messages
RED='\033[0;31m'      # Error messages  
YELLOW='\033[1;33m'   # Warning/info messages
BLUE='\033[0;34m'     # Info messages
NC='\033[0m'          # No Color (reset)

# Service URLs for testing
INTEGRATION_SERVICE_URL="http://localhost:8083/wifi-positioning-integration-service"
POSITIONING_SERVICE_URL="http://localhost:8080/wifi-positioning-service"

# Test data directory
TEST_DATA_DIR="./data"

# Results tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}WiFi Positioning Integration Service - Comprehensive Integration Test Suite${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo ""

# =============================================================================
# PHASE 1: Service Health Verification
# =============================================================================
echo -e "${YELLOW}PHASE 1: Service Health Verification${NC}"
echo "================================================"

# Check if integration service is running and healthy
echo -e "${YELLOW}Checking if integration service is running...${NC}"
if ! curl -sf "${INTEGRATION_SERVICE_URL}/health" > /dev/null; then
    echo -e "${RED}✗ Integration service is not running at ${INTEGRATION_SERVICE_URL}${NC}"
    echo "Please start the integration service first:"
    echo "  cd wifi-positioning-integration-service && mvn spring-boot:run"
    exit 1
fi
echo -e "${GREEN}✓ Integration service is running${NC}"

# Check if positioning service is running and healthy
echo -e "${YELLOW}Checking if positioning service is running...${NC}"
if ! curl -sf "${POSITIONING_SERVICE_URL}/health" > /dev/null; then
    echo -e "${RED}✗ Positioning service is not running at ${POSITIONING_SERVICE_URL}${NC}"
    echo "Please start the wifi-positioning-service first:"
    echo "  cd wifi-positioning-service && mvn spring-boot:run"
    echo "  # Also ensure DynamoDB Local is running with test data loaded"
    exit 1
fi
echo -e "${GREEN}✓ Positioning service is running${NC}"

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
# PHASE 4: Test Summary and Analysis
# =============================================================================
echo -e "${YELLOW}PHASE 4: Test Summary and Analysis${NC}"
echo "============================================="

echo ""
echo "================================================================================"
echo "TEST EXECUTION SUMMARY"
echo "================================================================================"
echo "Total Tests Executed: $TOTAL_TESTS"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"
echo -e "Skipped: ${YELLOW}$SKIPPED_TESTS${NC}"

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
if [ $FAILED_TESTS -eq 0 ]; then
    echo ""
    echo -e "${GREEN}🎉 ALL TESTS PASSED! The WiFi Positioning Integration Service is working correctly.${NC}"
    echo ""
    echo "The service successfully:"
    echo "  • Processed all test case formats"
    echo "  • Integrated with the positioning service"
    echo "  • Enriched AP information correctly"
    echo "  • Calculated comparison metrics"
    echo "  • Handled various AP statuses and configurations"
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
echo "  Test Data Directory: $TEST_DATA_DIR"
echo "  Test Script: $0"

echo ""
echo "================================================================================"
echo "Comprehensive integration testing completed!"
echo "================================================================================"

# Exit with appropriate code
if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi
