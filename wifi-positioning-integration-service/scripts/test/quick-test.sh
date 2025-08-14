#!/bin/bash

# Quick test script for individual test cases
# Usage: ./quick-test.sh <test-case-name>
# Example: ./quick-test.sh single-ap-proximity

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

INTEGRATION_SERVICE_URL="http://localhost:8083/wifi-positioning-integration-service"
TEST_DATA_DIR="scripts/test/data"

if [ $# -eq 0 ]; then
    echo -e "${YELLOW}Usage: $0 <test-case-name>${NC}"
    echo ""
    echo "Available test cases:"
    echo "  single-ap-proximity"
    echo "  dual-ap-rssi-ratio"
    echo "  trilateration-test"
    echo "  mixed-status-aps"
    echo "  unknown-mac-test"
    echo "  high-density-cluster"
    echo ""
    echo "Example: $0 single-ap-proximity"
    exit 1
fi

TEST_CASE="$1"
TEST_FILE="$TEST_DATA_DIR/${TEST_CASE}.json"

if [ ! -f "$TEST_FILE" ]; then
    echo -e "${RED}Test case '$TEST_CASE' not found.${NC}"
    echo "Available test files:"
    ls -1 "$TEST_DATA_DIR"/*.json | sed 's/.*\///' | sed 's/\.json$//'
    exit 1
fi

echo -e "${BLUE}Running test case: $TEST_CASE${NC}"
echo "Test file: $TEST_FILE"
echo ""

# Check if service is running
if ! curl -sf "${INTEGRATION_SERVICE_URL}/health" > /dev/null; then
    echo -e "${RED}Integration service is not running.${NC}"
    echo "Please start the service first: mvn spring-boot:run"
    exit 1
fi

# Replace timestamp placeholders
TEMP_FILE=$(mktemp)
sed 's/\$(date +%s)/'$(date +%s)'/g' "$TEST_FILE" > "$TEMP_FILE"

# Run the test
echo -e "${YELLOW}Executing test...${NC}"
RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d @"$TEMP_FILE" \
    "${INTEGRATION_SERVICE_URL}/api/integration/report")

# Clean up temp file
rm "$TEMP_FILE"

# Extract results
HTTP_CODE=$(echo $RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
RESPONSE_BODY=$(echo $RESPONSE | sed -e 's/HTTPSTATUS\:.*//g')

echo ""
if [ "$HTTP_CODE" -eq 200 ]; then
    echo -e "${GREEN}✓ Test PASSED (HTTP $HTTP_CODE)${NC}"
    
    # Show key metrics
    echo ""
    echo -e "${BLUE}Key Metrics:${NC}"
    echo "  Correlation ID: $(echo $RESPONSE_BODY | jq -r '.correlationId // "N/A"')"
    echo "  Positioning Status: $(echo $RESPONSE_BODY | jq -r '.positioningService.success // "N/A"')"
    echo "  Latency: $(echo $RESPONSE_BODY | jq -r '.positioningService.latencyMs // "N/A"')ms"
    echo "  AP Count: $(echo $RESPONSE_BODY | jq -r '.comparison.apCount // "N/A"')"
    echo "  Found APs: $(echo $RESPONSE_BODY | jq -r '.comparison.accessPointEnrichment.foundApCount // "N/A"')"
    echo "  Used APs: $(echo $RESPONSE_BODY | jq -r '.comparison.accessPointEnrichment.usedApCount // "N/A"')"
    
    # Show comparison if available
    if [ "$(echo $RESPONSE_BODY | jq -r '.comparison.positionsComparable // false')" = "true" ]; then
        echo "  Distance: $(echo $RESPONSE_BODY | jq -r '.comparison.haversineDistanceMeters // "N/A"')m"
        echo "  Accuracy Delta: $(echo $RESPONSE_BODY | jq -r '.comparison.accuracyDelta // "N/A"')"
        echo "  Confidence Delta: $(echo $RESPONSE_BODY | jq -r '.comparison.confidenceDelta // "N/A"')"
    fi
    
    # Show full response if requested
    if [ "$2" = "--full" ]; then
        echo ""
        echo -e "${BLUE}Full Response:${NC}"
        echo "$RESPONSE_BODY" | jq .
    fi
    
else
    echo -e "${RED}✗ Test FAILED (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo -e "${BLUE}Error Response:${NC}"
    echo "$RESPONSE_BODY" | jq .
fi

echo ""
echo -e "${YELLOW}Test completed.${NC}"
