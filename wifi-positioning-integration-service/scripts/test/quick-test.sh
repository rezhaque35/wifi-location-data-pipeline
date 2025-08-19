#!/bin/bash

# Quick test script for individual test cases
# Usage: ./quick-test.sh [OPTIONS] <test-case-name>
# 
# Options:
#   -h, --host HOST[:PORT]    Host and optional port (default: localhost:8083)
#   -s, --https               Use HTTPS instead of HTTP
#   --help                    Show this help message
#
# Examples:
#   ./quick-test.sh single-ap-proximity                    # Use localhost:8083 with HTTP
#   ./quick-test.sh -h 192.168.1.100:8083 single-ap-proximity  # Use specific host:port
#   ./quick-test.sh -h api.example.com -s single-ap-proximity   # Use HTTPS with host

set -e

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
            echo "Usage: $0 [OPTIONS] <test-case-name>"
            echo ""
            echo "Options:"
            echo "  -h, --host HOST[:PORT]    Host and optional port (default: localhost:8083)"
            echo "  -s, --https               Use HTTPS instead of HTTP"
            echo "  --help                    Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0 single-ap-proximity                                    # Use localhost:8083 with HTTP"
            echo "  $0 -h 192.168.1.100:8083 single-ap-proximity             # Use specific host:port"
            echo "  $0 -h api.example.com -s single-ap-proximity              # Use HTTPS with host"
            echo ""
            echo "Available test cases:"
            echo "  single-ap-proximity"
            echo "  dual-ap-rssi-ratio"
            echo "  trilateration-test"
            echo "  mixed-status-aps"
            echo "  unknown-mac-test"
            echo "  high-density-cluster"
            exit 0
            ;;
        -*)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
        *)
            break
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

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Find the test data directory relative to this script
find_test_data_dir() {
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local project_root="$(cd "$script_dir/../.." && pwd)"
    
    # Try multiple possible locations
    local possible_paths=(
        "$script_dir/data"                    # From scripts/test directory
        "$project_root/scripts/test/data"      # From project root
        "./scripts/test/data"                  # From current directory
        "../scripts/test/data"                 # From parent directory
        "../../scripts/test/data"              # From grandparent directory
    )
    
    for path in "${possible_paths[@]}"; do
        if [ -d "$path" ]; then
            # Check if there are any JSON files in the directory
            if ls "$path"/*.json >/dev/null 2>&1; then
                echo "$path"
                return 0
            fi
        fi
    done
    
    return 1
}

# Find and validate test data directory
TEST_DATA_DIR=$(find_test_data_dir)
if [ -z "$TEST_DATA_DIR" ]; then
    echo -e "${RED}✗ Could not find test data directory${NC}"
    echo "Please ensure you're running this script from the project root or scripts/test directory"
    exit 1
fi

echo -e "${GREEN}✓ Using test data directory: $TEST_DATA_DIR${NC}"

if [ $# -eq 0 ]; then
    echo -e "${YELLOW}Usage: $0 [OPTIONS] <test-case-name>${NC}"
    echo ""
    echo -e "${BLUE}Configuration:${NC}"
    echo "  Host: $HOST"
    echo "  Port: $PORT"
    echo "  Protocol: $PROTOCOL"
    echo "  Service URL: $INTEGRATION_SERVICE_URL"
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
    echo ""
    echo "Use --help for more options"
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
echo -e "${BLUE}Configuration:${NC}"
echo "  Host: $HOST"
echo "  Port: $PORT"
echo "  Protocol: $PROTOCOL"
echo "  Service URL: $INTEGRATION_SERVICE_URL"
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
    "${INTEGRATION_SERVICE_URL}/vi/wifi/position/report")

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
