#!/bin/bash

# scripts/test/run-data-driven-tests.sh
# Data-driven test runner for WiFi positioning service filtering tests

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

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="$SCRIPT_DIR/data"

# Check if jq is installed
if ! command -v jq &> /dev/null; then
  echo "jq is not installed. Please install it with:"
  echo "  brew install jq"
  exit 1
fi

# Source validation functions from run-comprehensive-tests.sh
source <(sed '/^echo -e "${CYAN}====================================================${NC}"/q' "$SCRIPT_DIR/run-comprehensive-tests.sh")

# Function to check if a value is in range
check_range() {
    local value=$1
    local min=$2
    local max=$3
    
    if [[ -z "$value" || "$value" == "null" ]]; then
        return 1
    fi
    
    if (( $(echo "$value >= $min" | bc -l 2>/dev/null || echo 0) )) && (( $(echo "$value <= $max" | bc -l 2>/dev/null || echo 0) )); then
        return 0
    else
        return 1
    fi
}

# Function to validate location (exact match with tolerance)
validate_location() {
    local response=$1
    local expected_lat=$2
    local expected_lon=$3
    local tolerance=${4:-0.0002}
    
    local cleaned_response=$(echo "$response" | tr -d '\000-\037')
    local actual_lat=$(echo "$cleaned_response" | jq -r ".wifiPosition.latitude // \"\"" 2>/dev/null || echo "")
    local actual_lon=$(echo "$cleaned_response" | jq -r ".wifiPosition.longitude // \"\"" 2>/dev/null || echo "")
    
    local validation_errors=()
    
    if [[ -z "$actual_lat" ]] || [[ "$actual_lat" == "null" ]]; then
        validation_errors+=("latitude not found in response")
    else
        local lat_diff=$(echo "$actual_lat - $expected_lat" | bc -l 2>/dev/null || echo "999")
        local lat_abs_diff=$(echo "if ($lat_diff < 0) -($lat_diff) else $lat_diff" | bc -l 2>/dev/null || echo "999")
        if (( $(echo "$lat_abs_diff > $tolerance" | bc -l 2>/dev/null || echo 1) )); then
            validation_errors+=("latitude mismatch: expected $expected_lat, got $actual_lat (diff: $lat_abs_diff)")
        fi
    fi
    
    if [[ -z "$actual_lon" ]] || [[ "$actual_lon" == "null" ]]; then
        validation_errors+=("longitude not found in response")
    else
        local lon_diff=$(echo "$actual_lon - $expected_lon" | bc -l 2>/dev/null || echo "999")
        local lon_abs_diff=$(echo "if ($lon_diff < 0) -($lon_diff) else $lon_diff" | bc -l 2>/dev/null || echo "999")
        if (( $(echo "$lon_abs_diff > $tolerance" | bc -l 2>/dev/null || echo 1) )); then
            validation_errors+=("longitude mismatch: expected $expected_lon, got $actual_lon (diff: $lon_abs_diff)")
        fi
    fi
    
    if [ ${#validation_errors[@]} -eq 0 ]; then
        return 0
    else
        printf '%s\n' "${validation_errors[@]}"
        return 1
    fi
}

# Function to validate accuracy range
validate_accuracy() {
    local response=$1
    local min_acc=$2
    local max_acc=$3
    
    local cleaned_response=$(echo "$response" | tr -d '\000-\037')
    local actual_acc=$(echo "$cleaned_response" | jq -r ".wifiPosition.horizontalAccuracy // \"\"" 2>/dev/null || echo "")
    
    if [[ -z "$actual_acc" ]] || [[ "$actual_acc" == "null" ]]; then
        echo "horizontalAccuracy not found in response"
        return 1
    fi
    
    if ! check_range "$actual_acc" "$min_acc" "$max_acc"; then
        echo "horizontalAccuracy $actual_acc not in range $min_acc-$max_acc"
        return 1
    fi
    
    return 0
}

# Function to validate confidence range
validate_confidence() {
    local response=$1
    local min_conf=$2
    local max_conf=$3
    
    local cleaned_response=$(echo "$response" | tr -d '\000-\037')
    local actual_conf=$(echo "$cleaned_response" | jq -r ".wifiPosition.confidence // \"\"" 2>/dev/null || echo "")
    
    if [[ -z "$actual_conf" ]] || [[ "$actual_conf" == "null" ]]; then
        echo "confidence not found in response"
        return 1
    fi
    
    if ! check_range "$actual_conf" "$min_conf" "$max_conf"; then
        echo "confidence $actual_conf not in range $min_conf-$max_conf"
        return 1
    fi
    
    return 0
}

# Function to validate access point summary
validate_ap_summary() {
    local response=$1
    local expected_total=$2
    local expected_known=$3
    local expected_used=$4
    
    local cleaned_response=$(echo "$response" | tr -d '\000-\037')
    local validation_errors=()
    
    local actual_total=$(echo "$cleaned_response" | jq -r ".calculationInfo.accessPointSummary.total // \"\"" 2>/dev/null || echo "")
    local actual_known=$(echo "$cleaned_response" | jq -r ".calculationInfo.accessPointSummary.known // \"\"" 2>/dev/null || echo "")
    local actual_used=$(echo "$cleaned_response" | jq -r ".calculationInfo.accessPointSummary.used // \"\"" 2>/dev/null || echo "")
    
    if [[ -z "$actual_total" ]] || [[ "$actual_total" == "null" ]]; then
        validation_errors+=("accessPointSummary.total not found")
    elif [[ "$actual_total" != "$expected_total" ]]; then
        validation_errors+=("accessPointSummary.total mismatch: expected $expected_total, got $actual_total")
    fi
    
    if [[ -z "$actual_known" ]] || [[ "$actual_known" == "null" ]]; then
        validation_errors+=("accessPointSummary.known not found")
    elif [[ "$actual_known" != "$expected_known" ]]; then
        validation_errors+=("accessPointSummary.known mismatch: expected $expected_known, got $actual_known")
    fi
    
    if [[ -z "$actual_used" ]] || [[ "$actual_used" == "null" ]]; then
        validation_errors+=("accessPointSummary.used not found")
    elif [[ "$actual_used" != "$expected_used" ]]; then
        validation_errors+=("accessPointSummary.used mismatch: expected $expected_used, got $actual_used")
    fi
    
    if [ ${#validation_errors[@]} -eq 0 ]; then
        return 0
    else
        printf '%s\n' "${validation_errors[@]}"
        return 1
    fi
}

# Function to validate status counts
validate_status_counts() {
    local response=$1
    local expected_status_counts_json=$2
    
    local cleaned_response=$(echo "$response" | tr -d '\000-\037')
    local validation_errors=()
    
    # Get actual status counts from response
    local actual_status_counts=$(echo "$cleaned_response" | jq -r '.calculationInfo.accessPointSummary.statusCounts // []' 2>/dev/null || echo "[]")
    
    # Check each expected status count using process substitution to avoid subshell issues
    while IFS=':' read -r status expected_count; do
        if [[ -z "$status" ]] || [[ -z "$expected_count" ]]; then
            continue
        fi
        
        local actual_count=$(echo "$actual_status_counts" | jq -r ".[] | select(.status == \"$status\") | .count // 0" 2>/dev/null || echo "0")
        
        if [[ "$actual_count" != "$expected_count" ]]; then
            validation_errors+=("statusCounts.$status mismatch: expected $expected_count, got $actual_count")
        fi
    done < <(echo "$expected_status_counts_json" | jq -r 'to_entries[] | "\(.key):\(.value)"' 2>/dev/null)
    
    if [ ${#validation_errors[@]} -eq 0 ]; then
        return 0
    else
        printf '%s\n' "${validation_errors[@]}"
        return 1
    fi
}

# Function to validate methods used
validate_methods_used() {
    local response=$1
    local expected_methods_json=$2
    
    local cleaned_response=$(echo "$response" | tr -d '\000-\037')
    local actual_methods=$(echo "$cleaned_response" | jq -r ".wifiPosition.methodsUsed | if . == null then \"\" else join(\", \") end" 2>/dev/null || echo "")
    
    local validation_errors=()
    
    # Check each expected method using process substitution to avoid subshell issues
    while IFS= read -r method; do
        if [[ -z "$method" ]]; then
            continue
        fi
        
        if [[ ! "$actual_methods" =~ $method ]]; then
            validation_errors+=("Expected method $method not found in methodsUsed: $actual_methods")
        fi
    done < <(echo "$expected_methods_json" | jq -r '.[]' 2>/dev/null)
    
    if [ ${#validation_errors[@]} -eq 0 ]; then
        return 0
    else
        printf '%s\n' "${validation_errors[@]}"
        return 1
    fi
}

# Function to run a data-driven test
run_data_driven_test() {
    local test_file=$1
    
    ((TOTAL_TESTS++))
    
    # Load test case
    local test_json=$(cat "$test_file" 2>/dev/null)
    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ Failed to load test file: $test_file${NC}"
        ((FAILED_TESTS++))
        return 1
    fi
    
    local test_name=$(echo "$test_json" | jq -r '.testName // "Unknown Test"' 2>/dev/null)
    local description=$(echo "$test_json" | jq -r '.description // ""' 2>/dev/null)
    local request_payload=$(echo "$test_json" | jq -c '.request' 2>/dev/null)
    local expected_json=$(echo "$test_json" | jq -c '.expected' 2>/dev/null)
    
    echo -e "\n${BLUE}Running: $test_name${NC}"
    if [[ -n "$description" ]]; then
        echo -e "${YELLOW}  $description${NC}"
    fi
    
    # Make API call
    local response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$request_payload" \
        http://localhost:8080/wifi-positioning-service/v1/wifi/position)
    
    local validation_errors=()
    
    # Extract expected values
    local expected_result=$(echo "$expected_json" | jq -r '.result // "SUCCESS"' 2>/dev/null)
    
    # Validate result
    local actual_result=$(echo "$response" | tr -d '\000-\037' | jq -r '.result // ""' 2>/dev/null || echo "")
    if [[ "$actual_result" != "$expected_result" ]]; then
        validation_errors+=("Result mismatch: expected $expected_result, got $actual_result")
    fi
    
    # If SUCCESS, validate other fields
    if [[ "$expected_result" == "SUCCESS" ]] && [[ "$actual_result" == "SUCCESS" ]]; then
        local wifi_pos=$(echo "$expected_json" | jq -c '.wifiPosition // {}' 2>/dev/null)
        
        if [[ -n "$wifi_pos" ]] && [[ "$wifi_pos" != "null" ]] && [[ "$wifi_pos" != "{}" ]]; then
            # Validate location
            local expected_lat=$(echo "$wifi_pos" | jq -r '.latitude // ""' 2>/dev/null)
            local expected_lon=$(echo "$wifi_pos" | jq -r '.longitude // ""' 2>/dev/null)
            if [[ -n "$expected_lat" ]] && [[ -n "$expected_lon" ]]; then
                if ! location_validation=$(validate_location "$response" "$expected_lat" "$expected_lon"); then
                    validation_errors+=($location_validation)
                fi
            fi
            
            # Validate accuracy
            local acc_range=$(echo "$wifi_pos" | jq -c '.accuracy // {}' 2>/dev/null)
            if [[ -n "$acc_range" ]] && [[ "$acc_range" != "null" ]] && [[ "$acc_range" != "{}" ]]; then
                local min_acc=$(echo "$acc_range" | jq -r '.min // ""' 2>/dev/null)
                local max_acc=$(echo "$acc_range" | jq -r '.max // ""' 2>/dev/null)
                if [[ -n "$min_acc" ]] && [[ -n "$max_acc" ]]; then
                    if ! acc_validation=$(validate_accuracy "$response" "$min_acc" "$max_acc"); then
                        validation_errors+=($acc_validation)
                    fi
                fi
            fi
            
            # Validate confidence
            local conf_range=$(echo "$wifi_pos" | jq -c '.confidence // {}' 2>/dev/null)
            if [[ -n "$conf_range" ]] && [[ "$conf_range" != "null" ]] && [[ "$conf_range" != "{}" ]]; then
                local min_conf=$(echo "$conf_range" | jq -r '.min // ""' 2>/dev/null)
                local max_conf=$(echo "$conf_range" | jq -r '.max // ""' 2>/dev/null)
                if [[ -n "$min_conf" ]] && [[ -n "$max_conf" ]]; then
                    if ! conf_validation=$(validate_confidence "$response" "$min_conf" "$max_conf"); then
                        validation_errors+=($conf_validation)
                    fi
                fi
            fi
            
            # Validate methods used
            local expected_methods=$(echo "$wifi_pos" | jq -c '.methodsUsed // []' 2>/dev/null)
            if [[ -n "$expected_methods" ]] && [[ "$expected_methods" != "null" ]] && [[ "$expected_methods" != "[]" ]]; then
                if ! methods_validation=$(validate_methods_used "$response" "$expected_methods"); then
                    validation_errors+=($methods_validation)
                fi
            fi
        fi
        
        # Validate calculationInfo
        local calc_info=$(echo "$expected_json" | jq -c '.calculationInfo // {}' 2>/dev/null)
        if [[ -n "$calc_info" ]] && [[ "$calc_info" != "null" ]] && [[ "$calc_info" != "{}" ]]; then
            # Validate access point summary
            local ap_summary=$(echo "$calc_info" | jq -c '.accessPointSummary // {}' 2>/dev/null)
            if [[ -n "$ap_summary" ]] && [[ "$ap_summary" != "null" ]] && [[ "$ap_summary" != "{}" ]]; then
                local expected_total=$(echo "$ap_summary" | jq -r '.total // ""' 2>/dev/null)
                local expected_known=$(echo "$ap_summary" | jq -r '.known // ""' 2>/dev/null)
                local expected_used=$(echo "$ap_summary" | jq -r '.used // ""' 2>/dev/null)
                
                if [[ -n "$expected_total" ]] && [[ -n "$expected_known" ]] && [[ -n "$expected_used" ]]; then
                    if ! summary_validation=$(validate_ap_summary "$response" "$expected_total" "$expected_known" "$expected_used"); then
                        validation_errors+=($summary_validation)
                    fi
                fi
            fi
            
            # Validate status counts
            local status_counts=$(echo "$calc_info" | jq -c '.statusCounts // {}' 2>/dev/null)
            if [[ -n "$status_counts" ]] && [[ "$status_counts" != "null" ]] && [[ "$status_counts" != "{}" ]]; then
                if ! status_validation=$(validate_status_counts "$response" "$status_counts"); then
                    validation_errors+=($status_validation)
                fi
            fi
        fi
    fi
    
    # Report results
    if [ ${#validation_errors[@]} -eq 0 ]; then
        echo -e "${GREEN}✓ Test Passed${NC}"
        ((PASSED_TESTS++))
        return 0
    else
        echo -e "${RED}✗ Test Failed${NC}"
        echo "Validation errors:"
        printf '%s\n' "${validation_errors[@]}"
        ((FAILED_TESTS++))
        return 1
    fi
}

# Main execution
echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}  DATA-DRIVEN FILTERING TESTS${NC}"
echo -e "${CYAN}====================================================${NC}"

# Check if data directory exists
if [[ ! -d "$DATA_DIR" ]]; then
    echo -e "${RED}Error: Test data directory not found: $DATA_DIR${NC}"
    exit 1
fi

# Find all test JSON files and sort them
test_files=$(find "$DATA_DIR" -name "test-*.json" | sort)

if [[ -z "$test_files" ]]; then
    echo -e "${YELLOW}No test files found in $DATA_DIR${NC}"
    exit 0
fi

# Run each test
while IFS= read -r test_file; do
    run_data_driven_test "$test_file"
done <<< "$test_files"

# Print summary
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

# Exit with appropriate code
if [ "$FAILED_TESTS" -eq 0 ]; then
    exit 0
else
    exit 1
fi
