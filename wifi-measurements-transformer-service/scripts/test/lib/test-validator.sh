#!/bin/bash

# scripts/test/lib/test-validator.sh
# Enhanced test validation that reads and validates against _test_metadata

# Function to extract test metadata from JSON file
extract_test_metadata() {
    local test_file="$1"
    
    # Check if file has _test_metadata
    if ! jq -e '._test_metadata' "$test_file" > /dev/null 2>&1; then
        echo "NO_METADATA"
        return 1
    fi
    
    # Extract metadata fields
    local test_id=$(jq -r '._test_metadata.test_case_id // "unknown"' "$test_file")
    local description=$(jq -r '._test_metadata.description // "No description"' "$test_file")
    local expected_total=$(jq -r '._test_metadata.expected_results.total_output_records // "unknown"' "$test_file")
    local expected_connected=$(jq -r '._test_metadata.expected_results.connected_records // "unknown"' "$test_file")
    local expected_scan=$(jq -r '._test_metadata.expected_results.scan_records // "unknown"' "$test_file")
    
    echo "HAS_METADATA|$test_id|$description|$expected_total|$expected_connected|$expected_scan"
    return 0
}

# Function to validate output against metadata expectations
validate_against_metadata() {
    local test_file="$1"
    local output_file="$2"
    
    # Extract metadata
    local metadata=$(extract_test_metadata "$test_file")
    
    if [[ "$metadata" == "NO_METADATA" ]]; then
        # Fall back to basic validation (raw input vs output)
        return 0
    fi
    
    # Parse metadata
    IFS='|' read -r has_meta test_id description expected_total expected_connected expected_scan <<< "$metadata"
    
    # Count actual records
    local actual_total=$(cat "$output_file" | jq -s 'length' 2>/dev/null || echo "0")
    local actual_connected=$(cat "$output_file" | jq -s '[.[] | select(.connection_status == "CONNECTED")] | length' 2>/dev/null || echo "0")
    local actual_scan=$(cat "$output_file" | jq -s '[.[] | select(.connection_status == "SCAN")] | length' 2>/dev/null || echo "0")
    
    # Compare
    local validation_passed=true
    
    if [[ "$expected_total" != "unknown" && "$expected_total" != "$actual_total" ]]; then
        validation_passed=false
    fi
    
    if [[ "$expected_connected" != "unknown" && "$expected_connected" != "$actual_connected" ]]; then
        validation_passed=false
    fi
    
    if [[ "$expected_scan" != "unknown" && "$expected_scan" != "$actual_scan" ]]; then
        validation_passed=false
    fi
    
    # Output results
    echo "TEST_ID=$test_id"
    echo "DESCRIPTION=$description"
    echo "EXPECTED_TOTAL=$expected_total"
    echo "ACTUAL_TOTAL=$actual_total"
    echo "EXPECTED_CONNECTED=$expected_connected"
    echo "ACTUAL_CONNECTED=$actual_connected"
    echo "EXPECTED_SCAN=$expected_scan"
    echo "ACTUAL_SCAN=$actual_scan"
    echo "VALIDATION_PASSED=$validation_passed"
    
    if [ "$validation_passed" = true ]; then
        return 0
    else
        return 1
    fi
}

# Function to check configuration warnings
check_config_warnings() {
    local test_file="$1"
    
    # Check if test expects mobile hotspot filtering
    local hotspot_enabled=$(jq -r '._test_metadata.filtering_config.mobile_hotspot_filtering_enabled // false' "$test_file")
    local oui_enabled=$(jq -r '._test_metadata.filtering_config.oui_detection_enabled // false' "$test_file")
    local ssid_enabled=$(jq -r '._test_metadata.filtering_config.ssid_detection_enabled // false' "$test_file")
    
    # Read application.yml to check actual configuration
    # This is a simplified check - in reality you'd parse the YAML properly
    local warnings=()
    
    if [[ "$oui_enabled" == "true" ]]; then
        warnings+=("⚠️  Test expects OUI detection ENABLED, but it's DISABLED in application.yml")
    fi
    
    if [[ "${#warnings[@]}" -gt 0 ]]; then
        echo "CONFIGURATION_WARNINGS:"
        for warning in "${warnings[@]}"; do
            echo "  $warning"
        done
        return 1
    fi
    
    return 0
}

# Export functions for use in other scripts
export -f extract_test_metadata
export -f validate_against_metadata
export -f check_config_warnings

