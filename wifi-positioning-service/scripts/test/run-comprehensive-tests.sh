#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

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

# Function to run a test case
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
    
    # Make the API call
    response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$payload" \
        http://localhost:8080/wifi-positioning-service/api/positioning/calculate)
    
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

echo -e "\n${BLUE}SECTION 2: ADVANCED SCENARIO TEST CASES${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case 6-10: Collinear APs
# Base weights: Weighted Centroid: 0.8, RSSI Ratio: 0.7
# Signal Quality (Medium): ×0.7, GDOP (Poor): ×1.3 for Weighted Centroid, ×0.8 for RSSI Ratio
# Distribution (Collinear): ×1.0 for Weighted Centroid, ×0.9 for RSSI Ratio
# Final weights:
# - Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
# - RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528 (Secondary)
# Note: Collinear APs should return SUCCESS with low confidence and high accuracy
run_test '{
    "wifiScanResults": [
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
        },
        {
            "macAddress": "00:11:22:33:44:08",
            "signalStrength": -66.0,
            "frequency": 2437,
            "ssid": "Collinear_Test_08"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-6-10",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 70 85 0.35 0.45 "weighted_centroid rssiratio" false false

# Test Case 11-15: High Density AP Cluster
# Base weights: Maximum Likelihood: 1.0, Trilateration: 0.8, Weighted Centroid: 0.7
# Signal Quality (Strong): ×0.9, GDOP (Poor): ×0.7 for Maximum Likelihood, ×1.3 for Weighted Centroid
# Distribution (Mixed): ×0.8 for Maximum Likelihood, ×1.0 for Weighted Centroid
# Final weights:
# - Weighted Centroid: 0.7 × 0.9 × 1.3 × 1.0 = 0.819 (Primary)
# - Maximum Likelihood: 1.0 × 0.9 × 0.7 × 0.8 = 0.504 (Secondary)
# - Trilateration: 0.8 × 0.9 × 0.7 × 0.8 = 0.4032 (Below threshold)
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:11",
            "signalStrength": -65.0,
            "frequency": 5320,
            "ssid": "HighDensity_Test_11"
        },
        {
            "macAddress": "00:11:22:33:44:12",
            "signalStrength": -63.5,
            "frequency": 5320,
            "ssid": "HighDensity_Test_12"
        },
        {
            "macAddress": "00:11:22:33:44:13",
            "signalStrength": -62.0,
            "frequency": 5320,
            "ssid": "HighDensity_Test_13"
        },
        {
            "macAddress": "00:11:22:33:44:14",
            "signalStrength": -60.5,
            "frequency": 5320,
            "ssid": "HighDensity_Test_14"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-11-15",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 50 60 0.35 0.55 "weighted_centroid maximum_likelihood" false false

# Test Case 16-20: Mixed Signal Quality
# Base weights: Trilateration: 1.0, Weighted Centroid: 0.8, RSSI Ratio: 0.7
# Signal Quality (Medium): ×0.7, GDOP (Poor): ×0.7 for Trilateration, ×1.3 for Weighted Centroid
# Distribution (Mixed): ×0.8 for Trilateration, ×1.0 for Weighted Centroid
# Final weights:
# - Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
# - RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528 (Secondary)
# - Trilateration: 1.0 × 0.7 × 0.7 × 0.8 = 0.392 (Below threshold)
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:16",
            "signalStrength": -60.0,
            "frequency": 2412,
            "ssid": "MixedSignal_Test_16"
        },
        {
            "macAddress": "00:11:22:33:44:17",
            "signalStrength": -65.0,
            "frequency": 2417,
            "ssid": "MixedSignal_Test_17"
        },
        {
            "macAddress": "00:11:22:33:44:18",
            "signalStrength": -70.0,
            "frequency": 2422,
            "ssid": "MixedSignal_Test_18"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-16-20",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 60 75 0.35 0.55 "weighted_centroid rssi ratio" false false

echo -e "\n${BLUE}SECTION 3: TEMPORAL AND ENVIRONMENTAL TEST CASES${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case 21-25: Time Series Data
# Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
# Signal Quality (Medium): ×0.7, GDOP (Poor): ×0.8 for RSSI Ratio, ×1.3 for Weighted Centroid
# Distribution (Uniform): ×1.1 for RSSI Ratio, ×1.0 for Weighted Centroid
# Final weights:
# - Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
# - RSSI Ratio: 1.0 × 0.7 × 0.8 × 1.1 = 0.616 (Secondary)
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:21",
            "signalStrength": -70.0,
            "frequency": 5500,
            "ssid": "TimeSeries_Test"
        },
        {
            "macAddress": "00:11:22:33:44:22",
            "signalStrength": -72.0,
            "frequency": 5500,
            "ssid": "TimeSeries_Test"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-21-25",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 45 60 0.35 0.55 "weighted_centroid rssi ratio" false false

# Test Case 26-30: Log-Distance Path Loss
# Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
# Signal Quality (Strong): ×0.9, GDOP (Poor): ×0.8 for RSSI Ratio, ×1.3 for Weighted Centroid
# Distribution (Mixed): ×0.8 for RSSI Ratio, ×1.0 for Weighted Centroid
# Final weights:
# - Weighted Centroid: 0.8 × 0.9 × 1.3 × 1.0 = 0.936 (Primary)
# - RSSI Ratio: 1.0 × 0.9 × 0.8 × 0.8 = 0.576 (Secondary)
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:26",
            "signalStrength": -50.0,
            "frequency": 2462,
            "ssid": "PathLoss_Test_26"
        },
        {
            "macAddress": "00:11:22:33:44:27",
            "signalStrength": -53.0,
            "frequency": 2462,
            "ssid": "PathLoss_Test_27"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-26-30",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 20 35 0.40 0.60 "weighted_centroid rssi ratio" false false

# Test Case 31-35: Stable Signal Quality
# Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
# Signal Quality (Medium): ×0.7, GDOP (Poor): ×0.8 for RSSI Ratio, ×1.3 for Weighted Centroid
# Distribution (Stable): ×1.0 for both methods
# Final weights:
# - Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
# - RSSI Ratio: 1.0 × 0.7 × 0.8 × 1.0 = 0.560 (Secondary)
# Note: Stable signals with same frequency and SSID result in better accuracy and confidence
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:31",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "StableSignal_Test"
        },
        {
            "macAddress": "00:11:22:33:44:32",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "StableSignal_Test"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-31-35",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 5 15 0.65 0.80 "weighted_centroid rssi ratio" false false

echo -e "\n${BLUE}SECTION 4: ERROR AND EDGE CASES${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case 36: Invalid coordinates
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:36",
            "signalStrength": -99.9,
            "frequency": 2412,
            "ssid": "ErrorCase_invalid_coordinates"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-36",
    "application": "wifi-positioning-test-suite"
}' "ERROR" false false

# Test Case 38: Very Weak Signal (Single AP)
# According to algorithm selection framework:
# - Signal strength -99.9 dBm is "Very Weak" (< -95 dBm)
# - Only Proximity algorithm gets non-zero weight (×0.5)
# - All other algorithms get zero weight
# Note: For unknown APs with very weak signals, service returns:
# - Lower accuracy (10m) due to proximity fallback
# - Zero confidence due to unknown AP
run_test '{
    "wifiScanResults": [{
        "macAddress": "00:11:22:33:44:55",
        "ssid": "TestAP1",
        "signalStrength": -99.9,
        "frequency": 2412
    }],
    "client": "test-client",
    "requestId": "test-request-38",
    "application": "wifi-positioning-test-suite"
}' "SUCCESS" 5 15 0.0 0.1 "proximity" false false

# Test Case 39: Algorithm Failure
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:55",
            "ssid": "TestAP1",
            "signalStrength": -40,
            "frequency": 2412
        },
        {
            "macAddress": "AA:BB:CC:DD:EE:FF",
            "ssid": "TestAP2",
            "signalStrength": -90,
            "frequency": 2412
        },
        {
            "macAddress": "11:22:33:44:55:66",
            "ssid": "TestAP3",
            "signalStrength": -95,
            "frequency": 2412
        }
    ],
    "client": "test-client",
    "requestId": "test-request-39",
    "application": "wifi-positioning-test-suite"
}' "ERROR" false false

echo -e "\n${BLUE}SECTION 5: STATUS FILTERING TESTS${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case: 40 Mixed Status APs Test
# This test verifies that only APs with active or warning status are used for positioning
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:41",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "StatusTest_41"
        },
        {
            "macAddress": "00:11:22:33:44:42",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "StatusTest_42"
        },
        {
            "macAddress": "00:11:22:33:44:43",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "StatusTest_43"
        },
        {
            "macAddress": "00:11:22:33:44:44",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "StatusTest_44"
        },
        {
            "macAddress": "00:11:22:33:44:45",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "StatusTest_45"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-status-filtering",
    "application": "wifi-positioning-test-suite",
    "calculationDetail": true
}' "SUCCESS" 15 25 0.65 0.75 "weighted_centroid rssiratio" false false

echo -e "\n${BLUE}SECTION 6: 2D POSITIONING TESTS (NULL ALTITUDE DATA)${NC}"
echo -e "${BLUE}====================================================${NC}"
echo -e "${YELLOW}Note: Using existing access points with altitude data, but our modified algorithm should handle 2D positioning correctly${NC}"

# Test Case 50: Single AP Test (Using existing data but 2D positioning)
# Testing the algorithm's ability to properly work with 2D data
# Expected: 
# - Proximity algorithm will be used
# - 2D positioning works correctly
run_test '{
    "wifiScanResults": [{
        "macAddress": "AA:BB:CC:00:00:50",
        "ssid": "2D_SingleAP_Test",
        "signalStrength": -65.0,
        "frequency": 2437
    }],
    "client": "test-client",
    "requestId": "test-request-2d-1",
    "application": "wifi-positioning-test-suite",
    "calculationDetail": true
}' "SUCCESS" 8 55 0.35 0.60 "proximity" true false

# Test Case 51-52: Two APs Test (Using existing data but 2D positioning)
# Testing the algorithm's ability to properly work with 2D data
# Expected:
# - RSSI Ratio and Weighted Centroid algorithms will be used
# - 2D positioning works correctly
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "AA:BB:CC:00:00:51",
            "signalStrength": -68.5,
            "frequency": 5180,
            "ssid": "2D_DualAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:52",
            "signalStrength": -62.3,
            "frequency": 2462,
            "ssid": "2D_DualAP_Test"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-2d-2",
    "application": "wifi-positioning-test-suite",
    "calculationDetail": true
}' "SUCCESS" 5 70 0.40 0.60 "weighted_centroid rssi ratio" true false

# Test Case 53-55: Three APs Test (Using existing data but 2D positioning)
# Testing the algorithm's ability to properly work with 3D data
# Expected:
# - Trilateration, Maximum Likelihood, and Weighted Centroid algorithms may be used
# - 2D positioning works correctly
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "AA:BB:CC:00:00:53",
            "signalStrength": -62.3,
            "frequency": 2462,
            "ssid": "2D_TriAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:54",
            "signalStrength": -71.2,
            "frequency": 5240,
            "ssid": "2D_TriAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:55",
            "signalStrength": -85.5,
            "frequency": 2412,
            "ssid": "2D_TriAP_Test"
        }
    ],
    "client": "test-client",
    "requestId": "test-request-2d-3",
    "application": "wifi-positioning-test-suite",
    "calculationDetail": true
}' "SUCCESS" 4 105 0.35 0.55 "weighted_centroid rssi ratio" true false

# Test Case 56-57: Mixed Data Test (Testing algorithms with a mix of AP data)
# Testing the algorithm's handling of mixed data
# Expected:
# - Algorithms should use available data
# - Position should be calculated correctly
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "AA:BB:CC:00:00:56", 
            "ssid": "Mixed_2D3D_Test",
            "signalStrength": -65.0,
            "frequency": 2437
        },
        {
            "macAddress": "AA:BB:CC:00:00:57",
            "ssid": "Mixed_2D3D_Test",
            "signalStrength": -67.0,
            "frequency": 2437
        }
    ],
    "client": "test-client",
    "requestId": "test-request-mixed-data",
    "application": "wifi-positioning-test-suite",
    "calculationDetail": true
}' "SUCCESS" 5 80 0.40 0.60 "weighted_centroid rssi ratio" false true

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