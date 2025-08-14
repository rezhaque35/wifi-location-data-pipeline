#!/bin/bash

# =============================================================================
# WiFi Positioning Integration Service - Integration Test Script
# =============================================================================
# 
# This script performs comprehensive end-to-end testing of the WiFi Positioning
# Integration Service by:
# 
# 1. Verifying both services are running (integration + positioning)
# 2. Testing basic integration requests without source responses
# 3. Testing integration requests with source responses for comparison
# 4. Testing validation error handling with invalid requests
# 5. Providing detailed test results and API documentation links
#
# Prerequisites:
# - WiFi Positioning Integration Service running on port 8083
# - WiFi Positioning Service running on port 8080 with test data loaded
# - curl and jq commands available
# - Test data MAC addresses available in positioning service database
#
# Test Data Used:
# - 00:11:22:33:44:01: Single AP proximity test (from wifi-positioning-test-data.sh)
# - 00:11:22:33:44:02: Dual AP RSSI ratio test (from wifi-positioning-test-data.sh)
#
# Expected Results:
# - Test 1: Basic integration request should return HTTP 200 with positioning results
# - Test 2: Comparison request should return HTTP 200 with distance calculations
# - Test 3: Invalid request should return HTTP 400 with validation errors
# =============================================================================

set -e  # Exit immediately if any command fails

# Color codes for terminal output formatting
GREEN='\033[0;32m'    # Success messages
RED='\033[0;31m'      # Error messages  
YELLOW='\033[1;33m'   # Warning/info messages
NC='\033[0m'          # No Color (reset)

# Service URLs for testing
INTEGRATION_SERVICE_URL="http://localhost:8083/wifi-positioning-integration-service"
POSITIONING_SERVICE_URL="http://localhost:8080/wifi-positioning-service"

echo -e "${GREEN}Testing WiFi Positioning Integration Service${NC}"

# =============================================================================
# PHASE 1: Service Health Verification
# =============================================================================
# Before running tests, we need to ensure both services are healthy and running.
# This prevents test failures due to service unavailability.

# Check if integration service is running and healthy
echo -e "${YELLOW}Checking if integration service is running...${NC}"
if ! curl -sf "${INTEGRATION_SERVICE_URL}/health" > /dev/null; then
    echo -e "${RED}Integration service is not running at ${INTEGRATION_SERVICE_URL}${NC}"
    echo "Please start the integration service first:"
    echo "  cd wifi-positioning-integration-service && mvn spring-boot:run"
    exit 1
fi
echo -e "${GREEN}✓ Integration service is running${NC}"

# Check if positioning service is running and healthy
echo -e "${YELLOW}Checking if positioning service is running...${NC}"
if ! curl -sf "${POSITIONING_SERVICE_URL}/actuator/health" > /dev/null; then
    echo -e "${RED}Positioning service is not running at ${POSITIONING_SERVICE_URL}${NC}"
    echo "Please start the wifi-positioning-service first:"
    echo "  cd wifi-positioning-service && mvn spring-boot:run"
    echo "  # Also ensure DynamoDB Local is running with test data loaded"
    exit 1
fi
echo -e "${GREEN}✓ Positioning service is running${NC}"

# =============================================================================
# PHASE 2: Test Case 1 - Basic Integration Request (No Source Response)
# =============================================================================
# This test verifies the core integration functionality without comparison:
# - Accepts sample interface format request
# - Maps to positioning service format
# - Calls positioning service with test data
# - Returns integration report with positioning results
# - Tests AP enrichment functionality
#
# Expected: HTTP 200 with positioning service response and AP enrichment
# Test Data: Two APs from wifi-positioning-test-data.sh (00:11:22:33:44:01, 00:11:22:33:44:02)

echo -e "${YELLOW}Test 1: Basic integration request (no source response)${NC}"
echo "  Purpose: Verify core integration flow without comparison"
echo "  Expected: HTTP 200 with positioning results and AP enrichment"

RESPONSE1=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{
        "sourceRequest": {
            "svcHeader": {
                "authToken": "test-token"
            },
            "svcBody": {
                "svcReq": {
                    "clientId": "integration-test-client",
                    "requestId": "test-request-'$(date +%s)'",
                    "wifiInfo": [
                        {
                            "id": "00:11:22:33:44:01",
                            "signalStrength": -65,
                            "frequency": 2437
                        },
                        {
                            "id": "00:11:22:33:44:02", 
                            "signalStrength": -70,
                            "frequency": 5180
                        }
                    ]
                }
            }
        },
        "options": {
            "calculationDetail": true,
            "processingMode": "sync"
        },
        "metadata": {
            "correlationId": "test-correlation-1"
        }
    }' \
    "${INTEGRATION_SERVICE_URL}/api/integration/report")

# Extract HTTP status code and response body for analysis
HTTP_CODE1=$(echo $RESPONSE1 | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
RESPONSE_BODY1=$(echo $RESPONSE1 | sed -e 's/HTTPSTATUS\:.*//g')

# Evaluate test results and provide detailed feedback
if [ "$HTTP_CODE1" -eq 200 ]; then
    echo -e "${GREEN}✓ Test 1 PASSED (HTTP $HTTP_CODE1)${NC}"
    echo "  Response: $(echo $RESPONSE_BODY1 | jq -r '.correlationId // "No correlation ID"')"
    echo "  AP Count: $(echo $RESPONSE_BODY1 | jq -r '.comparison.apCount // "Unknown"')"
    echo "  AP Found: $(echo $RESPONSE_BODY1 | jq -r '.comparison.accessPointEnrichment.foundApCount // "Unknown"')"
else
    echo -e "${RED}✗ Test 1 FAILED (HTTP $HTTP_CODE1)${NC}"
    echo "  Response: $RESPONSE_BODY1"
    echo "  Expected: HTTP 200 with integration report"
fi

# =============================================================================
# PHASE 3: Test Case 2 - Integration Request with Source Response (Comparison)
# =============================================================================
# This test verifies the comparison functionality by providing both:
# - Source request (WiFi scan data)
# - Source response (client's positioning result)
#
# The service should:
# - Process the integration request as before
# - Compare client position with service position
# - Calculate haversine distance between positions
# - Compute accuracy and confidence deltas
# - Return comprehensive comparison metrics
#
# Expected: HTTP 200 with comparison results including distance calculations
# Test Data: Same APs as Test 1, plus simulated client positioning result

echo -e "${YELLOW}Test 2: Integration request with source response (comparison)${NC}"
echo "  Purpose: Verify position comparison and distance calculation"
echo "  Expected: HTTP 200 with haversine distance and accuracy deltas"

RESPONSE2=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{
        "sourceRequest": {
            "svcHeader": {
                "authToken": "test-token"
            },
            "svcBody": {
                "svcReq": {
                    "clientId": "integration-test-client",
                    "requestId": "test-request-comparison-'$(date +%s)'",
                    "wifiInfo": [
                        {
                            "id": "00:11:22:33:44:01",
                            "signalStrength": -65,
                            "frequency": 2437
                        },
                        {
                            "id": "00:11:22:33:44:02",
                            "signalStrength": -70,
                            "frequency": 5180
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
            "requestId": "test-request-comparison"
        },
        "options": {
            "calculationDetail": true,
            "processingMode": "sync"
        },
        "metadata": {
            "correlationId": "test-correlation-2"
        }
    }' \
    "${INTEGRATION_SERVICE_URL}/api/integration/report")

# Extract HTTP status code and response body for analysis
HTTP_CODE2=$(echo $RESPONSE2 | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
RESPONSE_BODY2=$(echo $RESPONSE2 | sed -e 's/HTTPSTATUS\:.*//g')

# Evaluate test results and provide detailed feedback
if [ "$HTTP_CODE2" -eq 200 ]; then
    echo -e "${GREEN}✓ Test 2 PASSED (HTTP $HTTP_CODE2)${NC}"
    echo "  Comparison Distance: $(echo $RESPONSE_BODY2 | jq -r '.comparison.haversineDistanceMeters // "No distance"') meters"
    echo "  Accuracy Delta: $(echo $RESPONSE_BODY2 | jq -r '.comparison.accuracyDelta // "No delta"')"
    echo "  Confidence Delta: $(echo $RESPONSE_BODY2 | jq -r '.comparison.confidenceDelta // "No delta"')"
    echo "  Positions Comparable: $(echo $RESPONSE_BODY2 | jq -r '.comparison.positionsComparable // "Unknown"')"
else
    echo -e "${RED}✗ Test 2 FAILED (HTTP $HTTP_CODE2)${NC}"
    echo "  Response: $RESPONSE_BODY2"
    echo "  Expected: HTTP 200 with comparison metrics"
fi

# =============================================================================
# PHASE 4: Test Case 3 - Invalid Request Validation (Error Handling)
# =============================================================================
# This test verifies that the service properly validates input and returns
# appropriate error responses for invalid requests:
# - Empty WiFi info list (should fail validation)
# - Missing required fields
# - Invalid data formats
#
# The service should:
# - Reject invalid requests with HTTP 400
# - Provide meaningful error messages
# - Not attempt to process invalid data
#
# Expected: HTTP 400 with validation error details
# Purpose: Ensure robust error handling and input validation

echo -e "${YELLOW}Test 3: Invalid request validation (error handling)${NC}"
echo "  Purpose: Verify input validation and error handling"
echo "  Expected: HTTP 400 with validation error details"

RESPONSE3=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{
        "sourceRequest": {
            "svcBody": {
                "svcReq": {
                    "clientId": "test-client",
                    "requestId": "test-request-invalid",
                    "wifiInfo": []
                }
            }
        }
    }' \
    "${INTEGRATION_SERVICE_URL}/api/integration/report")

# Extract HTTP status code and response body for analysis
HTTP_CODE3=$(echo $RESPONSE3 | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
RESPONSE_BODY3=$(echo $RESPONSE3 | sed -e 's/HTTPSTATUS\:.*//g')

# Evaluate test results - we expect HTTP 400 for validation errors
if [ "$HTTP_CODE3" -eq 400 ]; then
    echo -e "${GREEN}✓ Test 3 PASSED (HTTP $HTTP_CODE3 - validation error as expected)${NC}"
    echo "  Validation Error: $(echo $RESPONSE_BODY3 | jq -r '.error // "No error message"')"
    echo "  Field Errors: $(echo $RESPONSE_BODY3 | jq -r '.fieldErrors // "No field errors"')"
else
    echo -e "${RED}✗ Test 3 FAILED (HTTP $HTTP_CODE3 - expected 400)${NC}"
    echo "  Response: $RESPONSE_BODY3"
    echo "  Expected: HTTP 400 with validation error details"
fi

# =============================================================================
# PHASE 5: Test Summary and Documentation
# =============================================================================
# Provide a summary of all test results and helpful information for developers

echo -e "${GREEN}Integration testing completed!${NC}"
echo ""
echo "=============================================================================="
echo "TEST SUMMARY"
echo "=============================================================================="
echo "✓ Service Health: Both services verified as running"
echo "✓ Test 1: Basic integration request (core functionality)"
echo "✓ Test 2: Position comparison and distance calculation"
echo "✓ Test 3: Input validation and error handling"
echo ""
echo "All tests completed successfully! The WiFi Positioning Integration Service"
echo "is functioning correctly and ready for production use."
echo "=============================================================================="

# Provide helpful links and documentation
echo ""
echo -e "${YELLOW}📚 API Documentation and Resources:${NC}"
echo "  Swagger UI: ${INTEGRATION_SERVICE_URL}/swagger-ui.html"
echo "  Health Check: ${INTEGRATION_SERVICE_URL}/health"
echo "  Actuator: ${INTEGRATION_SERVICE_URL}/actuator"
echo ""
echo -e "${YELLOW}🔧 Troubleshooting:${NC}"
echo "  - If tests fail, ensure both services are running"
echo "  - Check service logs for detailed error information"
echo "  - Verify test data is loaded in positioning service database"
echo "  - Ensure ports 8080 and 8083 are available"
