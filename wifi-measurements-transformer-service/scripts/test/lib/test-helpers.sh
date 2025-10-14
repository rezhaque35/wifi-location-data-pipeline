#!/bin/bash

# wifi-measurements-transformer-service/scripts/test/lib/test-helpers.sh
# Helper functions library for WiFi Measurements Transformer Service test automation
# Provides reusable validation, comparison, and reporting functions

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Print colored status message
# Args: $1=color, $2=message
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Compare two numeric values with tolerance
# Args: $1=expected, $2=actual, $3=tolerance (optional, default 0.000001)
# Returns: 0 if match within tolerance, 1 otherwise
compare_numeric_values() {
    local expected=$1
    local actual=$2
    local tolerance=${3:-0.000001}
    
    # Use awk for floating point comparison
    local result=$(awk -v exp="$expected" -v act="$actual" -v tol="$tolerance" '
        BEGIN {
            diff = (exp - act);
            if (diff < 0) diff = -diff;
            if (diff <= tol) print "match";
            else print "nomatch";
        }
    ')
    
    if [ "$result" = "match" ]; then
        return 0
    else
        return 1
    fi
}

# Compare two field values with appropriate comparison logic
# Args: $1=field_name, $2=expected, $3=actual
# Returns: 0 if match, 1 otherwise
compare_field_values() {
    local field_name=$1
    local expected=$2
    local actual=$3
    
    # Handle null/empty values
    if [ -z "$expected" ] && [ -z "$actual" ]; then
        return 0
    fi
    
    if [ -z "$expected" ] || [ -z "$actual" ]; then
        return 1
    fi
    
    # Numeric fields with tolerance
    case "$field_name" in
        latitude|longitude|altitude|location_accuracy|quality_weight|quality_score)
            compare_numeric_values "$expected" "$actual" 0.000001
            return $?
            ;;
        rssi|frequency|link_speed|channel_width|center_freq0)
            # Integer comparison
            if [ "$expected" -eq "$actual" ] 2>/dev/null; then
                return 0
            else
                return 1
            fi
            ;;
        *)
            # String comparison
            if [ "$expected" = "$actual" ]; then
                return 0
            else
                return 1
            fi
            ;;
    esac
}

# Validate location data with precision handling
# Args: $1=expected_lat, $2=actual_lat, $3=expected_lon, $4=actual_lon
# Returns: 0 if match within tolerance, 1 otherwise
validate_location_data() {
    local exp_lat=$1
    local act_lat=$2
    local exp_lon=$3
    local act_lon=$4
    
    if compare_numeric_values "$exp_lat" "$act_lat" 0.000001 && \
       compare_numeric_values "$exp_lon" "$act_lon" 0.000001; then
        return 0
    else
        return 1
    fi
}

# Validate quality metrics (quality_weight and quality_score)
# Args: $1=expected_weight, $2=actual_weight, $3=expected_score(optional), $4=actual_score(optional)
# Returns: 0 if valid, 1 otherwise
validate_quality_metrics() {
    local exp_weight=$1
    local act_weight=$2
    local exp_score=${3:-}
    local act_score=${4:-}
    
    # Validate quality weight
    if ! compare_numeric_values "$exp_weight" "$act_weight" 0.01; then
        return 1
    fi
    
    # Validate quality score if provided
    if [ -n "$exp_score" ] && [ -n "$act_score" ]; then
        if ! compare_numeric_values "$exp_score" "$act_score" 0.01; then
            return 1
        fi
    fi
    
    return 0
}

# Extract all BSSIDs from Firehose output JSON
# Args: $1=firehose_output_file
# Outputs: List of BSSIDs (one per line)
extract_bssids_from_output() {
    local output_file=$1
    
    if [ ! -f "$output_file" ]; then
        echo ""
        return 1
    fi
    
    # Extract BSSIDs from JSON Lines format
    cat "$output_file" | jq -r 'select(.bssid != null) | .bssid' 2>/dev/null | sort | uniq
}

# Extract records by connection status from Firehose output
# Args: $1=firehose_output_file, $2=connection_status (CONNECTED or SCAN)
# Outputs: JSON array of matching records
extract_records_by_status() {
    local output_file=$1
    local status=$2
    
    if [ ! -f "$output_file" ]; then
        echo "[]"
        return 1
    fi
    
    # Extract and collect records into array
    cat "$output_file" | jq -s --arg status "$status" '[.[] | select(.connection_status == $status)]' 2>/dev/null
}

# Count records by connection status
# Args: $1=firehose_output_file, $2=connection_status
# Returns: Count of records
count_records_by_status() {
    local output_file=$1
    local status=$2
    
    if [ ! -f "$output_file" ]; then
        echo "0"
        return
    fi
    
    cat "$output_file" | jq -s --arg status "$status" '[.[] | select(.connection_status == $status)] | length' 2>/dev/null || echo "0"
}

# Get total record count
# Args: $1=firehose_output_file
# Returns: Total count
count_total_records() {
    local output_file=$1
    
    if [ ! -f "$output_file" ]; then
        echo "0"
        return
    fi
    
    cat "$output_file" | jq -s 'length' 2>/dev/null || echo "0"
}

# Find record by BSSID in Firehose output
# Args: $1=firehose_output_file, $2=bssid
# Outputs: JSON record or empty
find_record_by_bssid() {
    local output_file=$1
    local bssid=$2
    
    if [ ! -f "$output_file" ]; then
        echo "{}"
        return 1
    fi
    
    cat "$output_file" | jq -s --arg bssid "$bssid" '[.[] | select(.bssid == $bssid)] | .[0] // {}' 2>/dev/null
}

# Calculate success rate percentage
# Args: $1=success_count, $2=total_count
# Returns: Success rate as percentage string
calculate_success_rate() {
    local success=$1
    local total=$2
    
    if [ "$total" -eq 0 ]; then
        echo "0.0"
        return
    fi
    
    awk -v s="$success" -v t="$total" 'BEGIN { printf "%.1f", (s * 100.0) / t }'
}

# Format test result with color and status
# Args: $1=test_name, $2=status (PASS/FAIL/SKIP), $3=message(optional)
format_test_result() {
    local test_name=$1
    local status=$2
    local message=${3:-}
    
    local color=$GREEN
    local symbol="✅"
    
    case "$status" in
        FAIL)
            color=$RED
            symbol="❌"
            ;;
        SKIP)
            color=$YELLOW
            symbol="⏭️ "
            ;;
        WARN)
            color=$YELLOW
            symbol="⚠️ "
            ;;
    esac
    
    if [ -n "$message" ]; then
        print_status "$color" "$symbol $test_name: $status - $message"
    else
        print_status "$color" "$symbol $test_name: $status"
    fi
}

# Print section header
# Args: $1=title
print_section_header() {
    local title=$1
    echo ""
    print_status "$CYAN" "═══════════════════════════════════════════════════════════"
    print_status "$CYAN" "  $title"
    print_status "$CYAN" "═══════════════════════════════════════════════════════════"
    echo ""
}

# Print test case header
# Args: $1=test_case_id, $2=description
print_test_case_header() {
    local test_id=$1
    local description=$2
    echo ""
    print_status "$BLUE" "┌─────────────────────────────────────────────────────────┐"
    print_status "$BLUE" "│ Test Case: $test_id"
    print_status "$BLUE" "│ Description: $description"
    print_status "$BLUE" "└─────────────────────────────────────────────────────────┘"
}

# Cleanup test artifacts (S3 files and local temp files)
# Args: $1=s3_key, $2=s3_bucket, $3=localstack_endpoint, $4=cleanup_local_files (true/false)
cleanup_test_artifacts() {
    local s3_key=$1
    local s3_bucket=$2
    local localstack_endpoint=$3
    local cleanup_local=${4:-true}
    
    # AWS credentials should already be set in environment
    
    # Clean up S3 source file
    if [ -n "$s3_key" ] && [ -n "$s3_bucket" ]; then
        aws --endpoint-url="$localstack_endpoint" s3 rm "s3://$s3_bucket/$s3_key" >/dev/null 2>&1 || true
    fi
    
    # Clean up local temp files
    if [ "$cleanup_local" = "true" ]; then
        rm -f /tmp/test-*.json /tmp/test-*.txt /tmp/test-*.gz /tmp/s3-event-*.json 2>/dev/null || true
        rm -f /tmp/firehose-output*.json /tmp/firehose-output*.gz 2>/dev/null || true
    fi
}

# Parse test metadata from JSON file
# Args: $1=test_file_path
# Outputs: JSON object with metadata or empty object
parse_test_metadata() {
    local test_file=$1
    
    if [ ! -f "$test_file" ]; then
        echo "{}"
        return 1
    fi
    
    # Extract _test_metadata section
    jq '._test_metadata // {}' "$test_file" 2>/dev/null || echo "{}"
}

# Check if test has metadata
# Args: $1=test_file_path
# Returns: 0 if has metadata, 1 otherwise
has_test_metadata() {
    local test_file=$1
    
    local metadata=$(parse_test_metadata "$test_file")
    local test_id=$(echo "$metadata" | jq -r '.test_case_id // empty' 2>/dev/null)
    
    if [ -n "$test_id" ]; then
        return 0
    else
        return 1
    fi
}

# Create formatted comparison table
# Args: $1=field_name, $2=expected, $3=actual, $4=status
print_comparison_row() {
    local field=$1
    local expected=$2
    local actual=$3
    local status=$4
    
    local status_symbol="✅"
    [ "$status" = "FAIL" ] && status_symbol="❌"
    
    printf "  %-25s %-20s %-20s %s\n" "$field" "$expected" "$actual" "$status_symbol"
}

# Wait for file to appear in S3 with timeout
# Args: $1=s3_bucket, $2=s3_prefix, $3=localstack_endpoint, $4=timeout_seconds
# Returns: 0 if found, 1 if timeout
wait_for_s3_file() {
    local bucket=$1
    local prefix=$2
    local endpoint=$3
    local timeout=${4:-60}
    
    local elapsed=0
    local interval=2
    
    while [ $elapsed -lt $timeout ]; do
        local file_count=$(aws --endpoint-url="$endpoint" s3 ls "s3://$bucket/$prefix" --recursive 2>/dev/null | wc -l)
        
        if [ "$file_count" -gt 0 ]; then
            return 0
        fi
        
        sleep $interval
        elapsed=$((elapsed + interval))
    done
    
    return 1
}

# Export functions for use in other scripts
export -f print_status
export -f compare_numeric_values
export -f compare_field_values
export -f validate_location_data
export -f validate_quality_metrics
export -f extract_bssids_from_output
export -f extract_records_by_status
export -f count_records_by_status
export -f count_total_records
export -f find_record_by_bssid
export -f calculate_success_rate
export -f format_test_result
export -f print_section_header
export -f print_test_case_header
export -f cleanup_test_artifacts
export -f parse_test_metadata
export -f has_test_metadata
export -f print_comparison_row
export -f wait_for_s3_file

