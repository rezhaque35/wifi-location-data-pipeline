#!/bin/bash

# Default values for URL and port
HOST="localhost"
PORT="8080"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Help function
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo "OPTIONS:"
    echo "  -h, --host HOST     Specify the host (default: localhost)"
    echo "  -p, --port PORT     Specify the port (default: 8080)"
    echo "  --help              Show this help message"
    exit 1
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        -h|--host)
            HOST="$2"
            shift 2
            ;;
        -p|--port)
            PORT="$2"
            shift 2
            ;;
        --help)
            show_usage
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            ;;
    esac
done

# Construct the API URL
API_URL="http://${HOST}:${PORT}/api/positioning/calculate"

echo -e "${CYAN}Using API endpoint: ${API_URL}${NC}"

# Initialize counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Check if jq is installed
if ! command -v jq &> /dev/null; then
  echo "jq is not installed. Please install it with:"
  echo "  brew install jq"
  exit 1
fi

# Function to check if a value is in range
check_range() {
    local value=$1
    local min=$2
    local max=$3
    
    # Handle empty or non-numeric values
    if [[ -z "$value" || "$value" == "null" ]]; then
        return 1
    fi
    
    # Use bc for floating point comparison with error handling
    if (( $(echo "$value >= $min" | bc -l 2>/dev/null || echo 0) )) && (( $(echo "$value <= $max" | bc -l 2>/dev/null || echo 0) )); then
        return 0
    else
        return 1
    fi
}

# Function to extract value from JSON response using jq
extract_json_value() {
    local json=$1
    local field=$2
    
    # Remove any control characters that might cause parsing issues
    local cleaned_json=$(echo "$json" | tr -d '\000-\037')
    
    # Handle different fields based on their location in the JSON
    case "$field" in
        # Top-level fields
        result|message|requestId|client|application|timestamp|calculationInfo)
            echo "$cleaned_json" | jq -r ".$field // \"\"" 2>/dev/null || echo ""
            ;;
        # Fields in wifiPosition object
        latitude|longitude|altitude|horizontalAccuracy|verticalAccuracy|confidence|apCount|calculationTimeMs)
            echo "$cleaned_json" | jq -r ".wifiPosition.$field // \"\"" 2>/dev/null || echo ""
            ;;
        # Handle methodsUsed array
        methodsUsed)
            echo "$cleaned_json" | jq -r ".wifiPosition.methodsUsed | if . == null then \"\" else join(\", \") end" 2>/dev/null || echo ""
            ;;
        # Default case
        *)
            echo "$cleaned_json" | jq -r ".$field // \"\"" 2>/dev/null || echo ""
            ;;
    esac
}

# Function to validate altitude handling in 2D positioning
check_altitude_handling() {
    local response=$1
    
    # Clean the JSON by removing control characters
    local cleaned_json=$(echo "$response" | tr -d '\000-\037')
    
    # Extract the altitude field if it exists
    local altitude=$(echo "$cleaned_json" | jq -r ".wifiPosition.altitude // \"not_present\"" 2>/dev/null || echo "not_present")
    
    # Check if altitude is not present or is 0.0
    if [[ "$altitude" == "not_present" ]] || [[ "$altitude" == "null" ]] || [[ $(echo "$altitude == 0.0" | bc -l 2>/dev/null) -eq 1 ]]; then
        return 0  # Valid 2D positioning (no altitude or 0.0)
    else
        echo "Expected altitude to be 0.0 or not present for 2D positioning, got: $altitude"
        return 1  # Invalid altitude handling
    fi
}

# Function to validate non-zero altitude (for mixed 2D/3D tests)
check_non_zero_altitude() {
    local response=$1
    
    # Clean the JSON by removing control characters
    local cleaned_json=$(echo "$response" | tr -d '\000-\037')
    
    # Extract the altitude field if it exists
    local altitude=$(echo "$cleaned_json" | jq -r ".wifiPosition.altitude // \"not_present\"" 2>/dev/null || echo "not_present")
    
    # Check if altitude is present and not 0.0
    if [[ "$altitude" != "not_present" ]] && [[ "$altitude" != "null" ]]; then
        # Use bc for safe floating point comparison
        local is_zero=$(echo "$altitude == 0.0" | bc -l 2>/dev/null || echo 0)
        if [[ "$is_zero" != "1" ]]; then
            return 0  # Valid mixed 2D/3D positioning (non-zero altitude)
        fi
    fi
    
    echo "Expected non-zero altitude for mixed 2D/3D positioning, got: $altitude"
    return 1  # Invalid altitude handling
}

# Function to validate response against detailed criteria
validate_response() {
    local response=$1
    local result_check=$2
    local horiz_acc_min=$3
    local horiz_acc_max=$4
    local confidence_min=$5
    local confidence_max=$6
    local expected_methods=$7
    local check_2d_positioning=${8:-false}
    local check_non_zero_alt=${9:-false}
    
    local validation_errors=()
    
    # Check for basic SUCCESS/ERROR
    local result=$(extract_json_value "$response" "result")
    if [[ "$result" != "$result_check" ]]; then
        validation_errors+=("Expected result:\"$result_check\" got:\"$result\"")
    fi
    
    # If we're checking a SUCCESS response, validate the other fields
    if [[ "$result_check" == *"SUCCESS"* ]]; then
        # Extract and validate horizontalAccuracy
        local h_accuracy=$(extract_json_value "$response" "horizontalAccuracy")
        if [[ -n "$h_accuracy" ]]; then
            if ! check_range "$h_accuracy" "$horiz_acc_min" "$horiz_acc_max"; then
                validation_errors+=("horizontalAccuracy $h_accuracy not in range $horiz_acc_min-$horiz_acc_max")
            fi
        else
            validation_errors+=("horizontalAccuracy not found in response")
        fi
        
        # Extract and validate confidence
        local confidence=$(extract_json_value "$response" "confidence")
        if [[ -n "$confidence" ]]; then
            if ! check_range "$confidence" "$confidence_min" "$confidence_max"; then
                validation_errors+=("confidence $confidence not in range $confidence_min-$confidence_max")
            fi
        else
            validation_errors+=("confidence not found in response")
        fi

        # Extract and validate methodsUsed
        local methods_used=$(extract_json_value "$response" "methodsUsed")
        if [[ -n "$methods_used" ]]; then
            for expected_method in $expected_methods; do
                if [[ ! "$methods_used" =~ $expected_method ]]; then
                    validation_errors+=("Expected method $expected_method not found in methodsUsed")
                fi
            done
        else
            validation_errors+=("methodsUsed not found in response")
        fi
        
        # If we need to check 2D positioning specifically
        if [[ "$check_2d_positioning" == "true" ]]; then
            if ! altitude_validation=$(check_altitude_handling "$response"); then
                validation_errors+=("$altitude_validation")
            fi
        fi
        
        # If we need to check for non-zero altitude (mixed 2D/3D tests)
        if [[ "$check_non_zero_alt" == "true" ]]; then
            if ! altitude_validation=$(check_non_zero_altitude "$response"); then
                validation_errors+=("$altitude_validation")
            fi
        fi
    fi
    
    # Return validation result
    if [ ${#validation_errors[@]} -eq 0 ]; then
        return 0  # Validation passed
    else
        printf '%s\n' "${validation_errors[@]}"
        return 1  # Validation failed
    fi
}

# Function to format the output
format_output() {
    echo "----------------------------------------"
    echo "Request Payload:"
    echo "$1"
    echo
    echo "Response:"
    echo "$2"
    echo
    
    if [ "$3" = true ]; then
        echo -e "${GREEN}✓ Test Passed${NC}"
        ((PASSED_TESTS++))
    else
        echo -e "${RED}✗ Test Failed${NC}"
        echo "Validation errors:"
        printf '%s\n' "${validation_errors[@]}"
        ((FAILED_TESTS++))
    fi
    echo "----------------------------------------"
}

# Function to run a test case - USING DYNAMIC API_URL
run_test() {
    local payload="$1"
    local expected_result="$2"
    local horiz_acc_min="${3:-0}"
    local horiz_acc_max="${4:-999.9}"
    local confidence_min="${5:-0}"
    local confidence_max="${6:-1}"
    local expected_methods="${7:-}"
    local check_2d_positioning="${8:-false}"
    local check_non_zero_alt="${9:-false}"
    
    ((TOTAL_TESTS++))
    
    # Make the API call - using the API_URL variable
    response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$payload" \
        ${API_URL})
    
    # Validate the response against all criteria
    validation_errors=()
    if ! validation_output=$(validate_response "$response" "$expected_result" "$horiz_acc_min" "$horiz_acc_max" "$confidence_min" "$confidence_max" "$expected_methods" "$check_2d_positioning" "$check_non_zero_alt"); then
        validation_errors=($validation_output)
        format_output "$payload" "$response" false
    else
        format_output "$payload" "$response" true
    fi
}

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}  WIFI POSITIONING SERVICE COMPREHENSIVE TESTS${NC}"
echo -e "${CYAN}====================================================${NC}"

echo -e "\n${BLUE}SECTION 1: BASIC ALGORITHM TEST CASES${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case 1: Single AP - Proximity Detection
# Base weight: 1.0, Signal Quality (Strong): ×0.9, GDOP (Poor): ×0.7, Distribution (Uniform): ×1.1
# Proximity: 1.0 × 0.9 = 0.9
# Log Distance: 0.4 × 1.0 × 0.7 × 1.1 = 0.308
run_test '{
    "wifiScanResults": [{
        "macAddress": "00:11:22:33:44:01",
        "ssid": "SingleAP_Test",
        "signalStrength": -65.0,
        "frequency": 2437
    }],
    "client": "test-client",
    "requestId": "test-request-1",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 45 55 0.35 0.55 "proximity" false false

# Test Case 2: Two APs - RSSI Ratio Method
# Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8, Proximity: 0.4, Log Distance: 0.5
# Signal Quality (Strong): ×1.0, GDOP (Poor): ×0.8 for RSSI Ratio, ×1.3 for Weighted Centroid
# Distribution (Uniform): ×1.2 for RSSI Ratio, ×1.0 for Weighted Centroid
# Final weights:
# - Weighted Centroid: 0.8 × 1.0 × 1.3 × 1.0 = 1.04 (Primary)
# - RSSI Ratio: 1.0 × 1.0 × 0.8 × 1.2 = 0.96 (Secondary)
# - Log Distance: 0.5 × 1.0 × 0.7 × 1.1 = 0.385 (Below threshold)
# - Proximity: 0.4 × 0.9 × 1.0 × 1.0 = 0.36 (Below threshold)
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:02",
            "signalStrength": -68.5,
            "frequency": 5180,
            "ssid": "DualAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:03",
            "signalStrength": -62.3,
            "frequency": 2462,
            "ssid": "TriAP_Test"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-2",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 55 70 0.40 0.60 "weighted_centroid rssi ratio" false false

# Test Case 3: Three APs - Trilateration
# Base weights: Trilateration: 1.0, Weighted Centroid: 0.8, RSSI Ratio: 0.7
# Signal Quality (Medium): ×0.7, GDOP (Poor): ×0.7 for Trilateration, ×1.3 for Weighted Centroid
# Distribution (Signal Outliers): ×0.8 for Trilateration, ×1.0 for Weighted Centroid
# Final weights:
# - Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
# - RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528 (Secondary)
# - Trilateration: 1.0 × 0.7 × 0.7 × 0.8 = 0.392 (Below threshold)
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:03",
            "signalStrength": -62.3,
            "frequency": 2462,
            "ssid": "TriAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:04",
            "signalStrength": -71.2,
            "frequency": 5240,
            "ssid": "MultiAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85.5,
            "frequency": 2412,
            "ssid": "WeakSignal_Test"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-3",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 90 105 0.35 0.55 "weighted_centroid rssi ratio" false false

# Test Case 4: Multiple APs - Maximum Likelihood
# Base weights: Maximum Likelihood: 1.0, Weighted Centroid: 0.7
# Signal Quality (Medium): ×0.9 for Maximum Likelihood, ×1.0 for Weighted Centroid
# Geometric Quality (Excellent): ×1.2 for Maximum Likelihood, ×1.0 for Weighted Centroid
# Distribution (Mixed): ×1.1 for Maximum Likelihood, ×1.8 for Weighted Centroid
# Final weights:
# - Maximum Likelihood: 1.0 × 0.9 × 1.2 × 1.1 = 1.19
# - Weighted Centroid: 0.7 × 1.0 × 1.0 × 1.8 = 1.26
# Note: Collinear APs detected, Trilateration disqualified
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:04",
            "signalStrength": -71.2,
            "frequency": 5240,
            "ssid": "MultiAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85.5,
            "frequency": 2412,
            "ssid": "WeakSignal_Test"
        },
        {
            "macAddress": "00:11:22:33:44:06",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "Collinear_Test_06"
        },
        {
            "macAddress": "00:11:22:33:44:07",
            "signalStrength": -68.0,
            "frequency": 2437,
            "ssid": "Collinear_Test_07"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-4",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 65 75 0.35 0.40 "maximum_likelihood weighted_centroid" false false

# Test Case 5: Weak Signals
# Base weights: Proximity: 1.0, Log Distance: 0.4
# Signal Quality (Weak): ×0.4, GDOP (Poor): ×0.7, Distribution (Uniform): ×1.1
# Final weights:
# - Proximity: 1.0 × 0.4 × 0.7 × 1.1 = 0.308 (Primary)
# - Log Distance: 0.4 × 0.4 × 0.7 × 1.1 = 0.1232 (Below threshold)
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85.5,
            "frequency": 2412,
            "ssid": "WeakSignal_Test"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-5",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 30 80 0.05 0.15 "proximity" false false

# Print test summary
echo -e "\n${CYAN}====================================================${NC}"
echo -e "${CYAN}                TEST SUMMARY${NC}"
echo -e "${CYAN}====================================================${NC}"
echo -e "Total Tests:  ${TOTAL_TESTS}"
echo -e "Passed:       ${GREEN}${PASSED_TESTS}${NC}"
echo -e "Failed:       ${RED}${FAILED_TESTS}${NC}"
if [ "$TOTAL_TESTS" -gt 0 ]; then
    SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
    echo -e "Success Rate: ${YELLOW}${SUCCESS_RATE}%${NC}"
fi
echo -e "${CYAN}====================================================${NC}" 