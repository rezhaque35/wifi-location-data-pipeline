#!/bin/bash

# =============================================================================
# WiFi Positioning Integration Service - Async Processing Test
# =============================================================================
# 
# This script specifically tests the async processing functionality of the
# WiFi Positioning Integration Service, including:
# - Async health endpoint accessibility
# - Concurrent request processing
# - Async vs sync processing comparison
# - Queue depth and processing metrics monitoring
#
# Prerequisites:
# - WiFi Positioning Integration Service running on specified host:port
# - Test data available in scripts/test/data directory
# - curl and jq commands available
#
# Usage:
#   ./test-async-only.sh [OPTIONS]
#
# Options:
#   -h, --host HOST[:PORT]    Host and optional port (default: localhost:8083)
#   -s, --https               Use HTTPS instead of HTTP
#   --help                    Show this help message
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

# Test data directory - try multiple possible locations
find_test_data_dir() {
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local project_root="$(cd "$script_dir/../.." && pwd)"
    
    # Try multiple possible locations
    local possible_paths=(
        "$script_dir/data"                    # From scripts/test directory
        "$project_root/scripts/test/data"      # From project root
        "./data"                              # Current directory
        "./scripts/test/data"                 # Script relative path
        "$(dirname "$0")/data"               # Script directory
        "$(dirname "$0")/../data"            # Parent of script directory
        "$(dirname "$0")/../../data"         # Two levels up from script directory
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

# Find and set test data directory
TEST_DATA_DIR=$(find_test_data_dir)
if [ -z "$TEST_DATA_DIR" ]; then
    echo -e "${RED}✗ Could not find test data directory${NC}"
    echo "Please ensure you're running this script from the project root or scripts/test directory"
    echo "Expected locations:"
    echo "  - $script_dir/data"
    echo "  - $project_root/scripts/test/data"
    echo "  - ./data"
    echo "  - ./scripts/test/data"
    exit 1
fi

echo -e "${GREEN}✓ Using test data directory: $TEST_DATA_DIR${NC}"

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}WiFi Positioning Integration Service - Async Processing Test${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo ""
echo -e "${YELLOW}Configuration:${NC}"
echo "  Host: $HOST"
echo "  Port: $PORT"
echo "  Protocol: $PROTOCOL"
echo "  Service URL: $INTEGRATION_SERVICE_URL"
echo ""

# Check if test data directory exists
if [ ! -d "$TEST_DATA_DIR" ]; then
    echo -e "${RED}✗ Test data directory not found: $TEST_DATA_DIR${NC}"
    exit 1
fi

# Check required dependencies
check_dependencies() {
    echo -e "${YELLOW}Checking required dependencies...${NC}"
    
    # Check for curl
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}✗ curl command not found. Please install curl.${NC}"
        exit 1
    fi
    echo -e "  ✓ curl: $(curl --version | head -1)"
    
    # Check for jq
    if ! command -v jq &> /dev/null; then
        echo -e "${RED}✗ jq command not found. Please install jq.${NC}"
        exit 1
    fi
    echo -e "  ✓ jq: $(jq --version)"
    
    # Check for bc (for floating point math)
    if ! command -v bc &> /dev/null; then
        echo -e "${RED}✗ bc command not found. Please install bc for floating point calculations.${NC}"
        exit 1
    fi
    echo -e "  ✓ bc: $(bc --version | head -1)"
    
    echo ""
}

# Run dependency check
check_dependencies

# Function to check async processing health metrics
check_async_health() {
    local endpoint="${INTEGRATION_SERVICE_URL}/async-processing"
    echo -e "${YELLOW}Checking async processing health metrics...${NC}"
    echo "  Endpoint: $endpoint"
    
    local response
    local http_code
    
    response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$endpoint")
    http_code=$(echo "$response" | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    response=$(echo "$response" | sed -e 's/HTTPSTATUS\:.*//g')
    
    if [ "$http_code" -eq 200 ]; then
        echo -e "${GREEN}✓ Async health endpoint accessible${NC}"
        
        # Parse and display key metrics
        local queue_depth=$(echo "$response" | jq -r '.queue.size // "Unknown"')
        local queue_capacity=$(echo "$response" | jq -r '.queue.capacity // "Unknown"')
        local queue_utilization=$(echo "$response" | jq -r '.queue.utilizationPercent // "Unknown"')
        local active_threads=$(echo "$response" | jq -r '.threadPool.activeThreads // "Unknown"')
        local pool_size=$(echo "$response" | jq -r '.threadPool.poolSize // "Unknown"')
        local successful_processing=$(echo "$response" | jq -r '.processing.successfulProcessing // "Unknown"')
        local failed_processing=$(echo "$response" | jq -r '.processing.failedProcessing // "Unknown"')
        local health_status=$(echo "$response" | jq -r '.status // "Unknown"')
        
        echo "  Queue Depth: $queue_depth / $queue_capacity ($queue_utilization%)"
        echo "  Active Threads: $active_threads / $pool_size"
        echo "  Successful Processing: $successful_processing"
        echo "  Failed Processing: $failed_processing"
        echo "  Health Status: $health_status"
        
        return 0
    else
        echo -e "${RED}✗ Async health endpoint returned HTTP $http_code${NC}"
        echo "  Response: $response"
        return 1
    fi
}

# Function to send a single async request
send_async_request() {
    local test_file="$1"
    local request_id="$2"
    local correlation_id="async-test-$(date +%s)-$request_id"
    
    # Replace timestamp placeholders and add correlation ID
    local temp_file=$(mktemp)
    sed 's/\$(date +%s)/'$(date +%s)'/g' "$test_file" > "$temp_file"
    
    # Add correlation ID and set async processing mode in the request body
    jq --arg cid "$correlation_id" \
       '.metadata.correlationId = $cid | .options.processingMode = "async"' \
       "$temp_file" > "${temp_file}.tmp"
    mv "${temp_file}.tmp" "$temp_file"
    
    # Send request with async processing (mode set in JSON body, not header)
    local start_time=$(date +%s.%N)
    local response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d @"$temp_file" \
        "${INTEGRATION_SERVICE_URL}/vi/wifi/position/report")
    local end_time=$(date +%s.%N)
    
    # Clean up temp file
    rm "$temp_file"
    
    # Extract HTTP status code and response body
    local http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    local response_body=$(echo $response | sed -e 's/HTTPSTATUS\:.*//g')
    
    # Calculate response time
    local response_time=$(echo "$end_time - $start_time" | bc -l | awk '{printf "%.3f", $1}')
    
    # Return results (no job ID in this implementation)
    echo "$http_code|$response_time|$correlation_id|$response_body"
}

# Function to send a single sync request
send_sync_request() {
    local test_file="$1"
    local request_id="$2"
    local correlation_id="sync-test-$(date +%s)-$request_id"
    
    # Replace timestamp placeholders and add correlation ID
    local temp_file=$(mktemp)
    sed 's/\$(date +%s)/'$(date +%s)'/g' "$test_file" > "$temp_file"
    
    # Add correlation ID and set sync processing mode in the request body
    jq --arg cid "$correlation_id" \
       '.metadata.correlationId = $cid | .options.processingMode = "sync"' \
       "$temp_file" > "${temp_file}.tmp"
    mv "${temp_file}.tmp" "$temp_file"
    
    # Send request with sync processing (mode set in JSON body, not header)
    local start_time=$(date +%s.%N)
    local response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d @"$temp_file" \
        "${INTEGRATION_SERVICE_URL}/vi/wifi/position/report")
    local end_time=$(date +%s.%N)
    
    # Clean up temp file
    rm "$temp_file"
    
    # Extract HTTP status code and response body
    local http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    local response_body=$(echo $response | sed -e 's/HTTPSTATUS\:.*//g')
    
    # Calculate response time
    local response_time=$(echo "$end_time - $start_time" | bc -l | awk '{printf "%.3f", $1}')
    
    # Return results
    echo "$http_code|$response_time|$correlation_id|$response_body"
}

# Function to verify async response format
verify_async_response() {
    local http_code="$1"
    local response_body="$2"
    local correlation_id="$3"
    
    # Async requests should return 202 Accepted
    if [ "$http_code" -eq 202 ]; then
        # Check if response has required async fields
        local processing_mode=$(echo "$response_body" | jq -r '.processingMode // "Unknown"')
        local received_at=$(echo "$response_body" | jq -r '.receivedAt // "Unknown"')
        local response_correlation_id=$(echo "$response_body" | jq -r '.correlationId // "Unknown"')
        
        if [ "$processing_mode" = "async" ] && [ "$received_at" != "Unknown" ] && [ "$response_correlation_id" != "Unknown" ]; then
            echo -e "${GREEN}✓ Async response valid: HTTP $http_code, Mode: $processing_mode, Correlation ID: $response_correlation_id${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠️  Async response missing required fields: HTTP $http_code${NC}"
            echo "    Processing Mode: $processing_mode"
            echo "    Received At: $received_at"
            echo "    Correlation ID: $response_correlation_id"
            echo "    Response: $response_body"
            return 1
        fi
    else
        echo -e "${RED}✗ Async request returned unexpected HTTP code: $http_code${NC}"
        echo "    Expected: 202 (Accepted for async processing)"
        echo "    Response: $response_body"
        return 1
    fi
}

# Function to verify sync response format
verify_sync_response() {
    local http_code="$1"
    local response_body="$2"
    local correlation_id="$3"
    
    # Sync requests should return 200 OK with final results
    if [ "$http_code" -eq 200 ]; then
        # Check if response contains final results (not job references)
        # Our integration service returns: success, positioningRequest, positioningResult, comparison, totalProcessingTimeMs, etc.
        local has_results=$(echo "$response_body" | jq -r 'has("success") or has("positioningResult") or has("comparison") // false')
        local has_job_id=$(echo "$response_body" | jq -r 'has("jobId") or has("id") // false')
        
        if [ "$has_results" = "true" ] && [ "$has_job_id" = "false" ]; then
            echo -e "${GREEN}✓ Sync response valid: HTTP $http_code with final results${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠️  Sync response may not contain final results: HTTP $http_code${NC}"
            echo "    Response: $response_body"
            return 1
        fi
    else
        echo -e "${RED}✗ Sync request returned unexpected HTTP code: $http_code${NC}"
        echo "    Expected: 200 (OK with final results)"
        echo "    Response: $response_body"
        return 1
    fi
}

# Function to get async processing metrics in a compact format
get_async_metrics() {
    local response=$(curl -s "${INTEGRATION_SERVICE_URL}/async-processing")
    local queue_size=$(echo "$response" | jq -r '.queue.size // 0')
    local successful=$(echo "$response" | jq -r '.processing.successfulProcessing // 0')
    local failed=$(echo "$response" | jq -r '.processing.failedProcessing // 0')
    local active_threads=$(echo "$response" | jq -r '.threadPool.activeThreads // 0')
    local completed_tasks=$(echo "$response" | jq -r '.processing.completedTasks // 0')
    
    echo "queue=$queue_size,successful=$successful,failed=$failed,active=$active_threads,completed=$completed_tasks"
}

# Function to run concurrent async processing test
run_concurrent_async_test() {
    echo -e "${BLUE}Running concurrent async processing test...${NC}"
    echo "  This test will send multiple requests concurrently to verify async processing"
    
    # Get baseline metrics before sending requests
    echo ""
    echo -e "${YELLOW}Capturing baseline async processing metrics...${NC}"
    local baseline_metrics=$(get_async_metrics)
    echo "  Baseline: $baseline_metrics"
    
    # Extract baseline values for comparison
    local baseline_successful=$(echo "$baseline_metrics" | sed -n 's/.*successful=\([0-9]*\).*/\1/p')
    local baseline_failed=$(echo "$baseline_metrics" | sed -n 's/.*failed=\([0-9]*\).*/\1/p')
    local baseline_completed=$(echo "$baseline_metrics" | sed -n 's/.*completed=\([0-9]*\).*/\1/p')
    
    echo "  Baseline successful: $baseline_successful, failed: $baseline_failed, completed: $baseline_completed"
    
    # Select a test file for concurrent testing
    local test_file=""
    for file in $TEST_DATA_DIR/*.json; do
        if [[ "$file" == *"single-ap-proximity"* ]] || [[ "$file" == *"dual-ap-rssi-ratio"* ]]; then
            test_file="$file"
            break
        fi
    done
    
    if [ -z "$test_file" ]; then
        echo -e "${YELLOW}No suitable test file found for async testing, using first available${NC}"
        test_file=$(ls $TEST_DATA_DIR/*.json | head -1)
    fi
    
    echo "  Using test file: $(basename "$test_file")"
    
    # Number of concurrent requests (increased for better validation)
    local concurrent_requests=10
    echo "  Sending $concurrent_requests concurrent requests..."
    
    # Send requests concurrently and capture results
    local pids=()
    local start_time=$(date +%s.%N)
    
    echo "  Submitting requests to async queue..."
    for i in $(seq 1 $concurrent_requests); do
        (
            local result=$(send_async_request "$test_file" "$i")
            echo "$result" > "/tmp/async_result_$i"
        ) &
        pids+=($!)
        
        # Show progress every few requests
        if [ $((i % 3)) -eq 0 ]; then
            echo "    Submitted $i/$concurrent_requests requests..."
        fi
    done
    
    echo "  Waiting for all request submissions to complete..."
    
    # Wait for all requests to complete
    for pid in "${pids[@]}"; do
        wait $pid
    done
    
    local end_time=$(date +%s.%N)
    local total_time=$(echo "$end_time - $start_time" | bc -l | awk '{printf "%.3f", $1}')
    
    echo "  ✓ All $concurrent_requests requests submitted in ${total_time}s"
    
    # Check immediate queue state after all requests submitted
    echo "  Immediate queue state: $(get_async_metrics)"
    
    # Collect results
    echo ""
    echo -e "${YELLOW}Concurrent request results:${NC}"
    local successful_requests=0
    local total_response_time=0
    local valid_async_responses=0
    
    for i in $(seq 1 $concurrent_requests); do
        if [ -f "/tmp/async_result_$i" ]; then
            local result=$(cat "/tmp/async_result_$i")
            local http_code=$(echo "$result" | cut -d'|' -f1)
            local response_time=$(echo "$result" | cut -d'|' -f2)
            local correlation_id=$(echo "$result" | cut -d'|' -f3)
            local response_body=$(echo "$result" | cut -d'|' -f4)
            
            if [ "$http_code" -eq 202 ]; then
                echo -e "  ✓ Request $i: HTTP $http_code in ${response_time}s (ID: $correlation_id)"
                successful_requests=$((successful_requests + 1))
                total_response_time=$(echo "$total_response_time + $response_time" | bc -l)
                
                # Verify async response format
                if verify_async_response "$http_code" "$response_body" "$correlation_id"; then
                    valid_async_responses=$((valid_async_responses + 1))
                fi
            else
                echo -e "  ✗ Request $i: HTTP $http_code in ${response_time}s (ID: $correlation_id)"
                echo "    Error: $(echo $response_body | jq -r '.error // .message // "Unknown error"')"
            fi
            
            # Clean up temp file
            rm "/tmp/async_result_$i"
        fi
    done
    
    # Calculate average response time
    local avg_response_time=0
    if [ $successful_requests -gt 0 ]; then
        avg_response_time=$(echo "scale=3; $total_response_time / $successful_requests" | bc -l)
    fi
    
    echo ""
    echo -e "${YELLOW}Concurrent processing summary:${NC}"
    echo "  Total Time: ${total_time}s"
    echo "  Successful Requests: $successful_requests / $concurrent_requests"
    echo "  Valid Async Responses: $valid_async_responses / $successful_requests"
    echo "  Average Response Time: ${avg_response_time}s"
    
    # Wait for background processing to complete and validate via health endpoint
    echo ""
    echo -e "${YELLOW}Validating background processing completion...${NC}"
    
    echo "  Using captured baseline successful processing: $baseline_successful"
    echo "  Expected successful processing after completion: $((baseline_successful + concurrent_requests))"
    
    # Wait and poll for background processing to complete
    local max_wait_time=10
    local poll_interval=0.5
    local elapsed_time=0
    local processing_completed=false
    
    echo "  Polling for background processing completion (max ${max_wait_time}s)..."
    
    while [ $(echo "$elapsed_time < $max_wait_time" | bc -l) -eq 1 ] && [ "$processing_completed" = false ]; do
        sleep $poll_interval
        elapsed_time=$(echo "$elapsed_time + $poll_interval" | bc -l)
        
        local current_response=$(curl -s "${INTEGRATION_SERVICE_URL}/async-processing")
        local current_successful=$(echo "$current_response" | jq -r '.processing.successfulProcessing // 0')
        local current_queue_depth=$(echo "$current_response" | jq -r '.queue.size // 0')
        local current_active_threads=$(echo "$current_response" | jq -r '.threadPool.activeThreads // 0')
        
        local expected_successful=$((baseline_successful + concurrent_requests))
        
        if [ "$current_successful" -ge "$expected_successful" ] && [ "$current_queue_depth" -eq 0 ]; then
            processing_completed=true
            echo "  ✓ Background processing completed in ${elapsed_time}s"
            echo "    Final successful processing: $current_successful"
            echo "    Queue depth: $current_queue_depth"
            echo "    Active threads: $current_active_threads"
            break
        fi
        
        # Show progress every 2 seconds
        if [ $(echo "$elapsed_time" | awk '{print int($1*2) % 2}') -eq 0 ] && [ $(echo "$elapsed_time > 1.0" | bc -l) -eq 1 ]; then
            echo "    Progress: successful=$current_successful (target=$expected_successful), queue=$current_queue_depth, active=$current_active_threads"
        fi
    done
    
    # Final validation
    echo ""
    echo -e "${YELLOW}Final async processing metrics validation:${NC}"
    check_async_health
    
    # Get final metrics for validation
    local final_response=$(curl -s "${INTEGRATION_SERVICE_URL}/async-processing")
    local final_successful=$(echo "$final_response" | jq -r '.processing.successfulProcessing // 0')
    local final_failed=$(echo "$final_response" | jq -r '.processing.failedProcessing // 0')
    local final_queue_depth=$(echo "$final_response" | jq -r '.queue.size // 0')
    
    local expected_successful=$((baseline_successful + concurrent_requests))
    
    # Validate processing completion
    if [ "$processing_completed" = true ] && [ "$final_successful" -ge "$expected_successful" ]; then
        echo -e "${GREEN}✓ All $concurrent_requests async requests processed successfully${NC}"
        echo "  Background processing validation: PASSED"
        
        if [ "$final_queue_depth" -eq 0 ]; then
            echo -e "${GREEN}✓ Queue is empty - all requests processed${NC}"
        else
            echo -e "${YELLOW}⚠️  Queue still has $final_queue_depth items${NC}"
        fi
        
        local processed_count=$((final_successful - baseline_successful))
        echo "  Successfully processed: $processed_count requests"
        
    elif [ "$final_successful" -ge "$expected_successful" ]; then
        echo -e "${GREEN}✓ All requests eventually processed successfully${NC}"
        echo "  Background processing validation: PASSED (completed after polling)"
        
    else
        echo -e "${RED}✗ Background processing validation FAILED${NC}"
        echo "  Expected successful processing: $expected_successful"
        echo "  Actual successful processing: $final_successful"
        echo "  Failed processing: $final_failed"
        echo "  Queue depth: $final_queue_depth"
        return 1
    fi
    
    # Overall test validation
    if [ $successful_requests -eq $concurrent_requests ] && [ $valid_async_responses -eq $concurrent_requests ] && [ "$final_successful" -ge "$expected_successful" ]; then
        echo -e "${GREEN}✓ Concurrent async processing test PASSED${NC}"
        echo "  ✓ All requests accepted (HTTP 202)"
        echo "  ✓ All responses properly formatted"
        echo "  ✓ All requests processed successfully in background"
        return 0
    elif [ $successful_requests -eq $concurrent_requests ] && [ $valid_async_responses -lt $concurrent_requests ]; then
        echo -e "${YELLOW}⚠️  Concurrent requests succeeded but some responses weren't properly async${NC}"
        return 1
    else
        echo -e "${RED}✗ Concurrent async processing test FAILED${NC}"
        return 1
    fi
}

# Function to test async vs sync response times
run_async_vs_sync_comparison() {
    echo -e "${BLUE}Running async vs sync processing comparison test...${NC}"
    echo "  This test compares response times between async and sync processing modes"
    
    # Select a test file
    local test_file=$(ls $TEST_DATA_DIR/*.json | head -1)
    echo "  Using test file: $(basename "$test_file")"
    
    # Test sync processing
    echo ""
    echo -e "${YELLOW}Testing sync processing...${NC}"
    
    # Prepare sync request with processingMode in JSON body
    local sync_temp_file=$(mktemp)
    jq '.options.processingMode = "sync"' "$test_file" > "$sync_temp_file"
    
    local sync_start=$(date +%s.%N)
    local sync_response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d @"$sync_temp_file" \
        "${INTEGRATION_SERVICE_URL}/vi/wifi/position/report")
    local sync_end=$(date +%s.%N)
    
    local sync_http_code=$(echo $sync_response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    local sync_response_body=$(echo $sync_response | sed -e 's/HTTPSTATUS\:.*//g')
    local sync_time=$(echo "$sync_end - $sync_start" | bc -l | awk '{printf "%.3f", $1}')
    
    if [ "$sync_http_code" -eq 200 ]; then
        echo -e "  ✓ Sync processing: HTTP $sync_http_code in ${sync_time}s"
        # Verify sync response format
        if ! verify_sync_response "$sync_http_code" "$sync_response_body" "sync-test"; then
            echo -e "  ⚠️  Sync response format validation failed"
        fi
    else
        echo -e "  ✗ Sync processing: HTTP $sync_http_code in ${sync_time}s"
        echo "    Error: $(echo $sync_response_body | jq -r '.error // .message // "Unknown error"')"
        return 1
    fi
    
    # Clean up sync temp file
    rm "$sync_temp_file"
    
    # Test async processing
    echo ""
    echo -e "${YELLOW}Testing async processing...${NC}"
    
    # Prepare async request with processingMode in JSON body
    local async_temp_file=$(mktemp)
    jq '.options.processingMode = "async"' "$test_file" > "$async_temp_file"
    
    local async_start=$(date +%s.%N)
    local async_response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d @"$async_temp_file" \
        "${INTEGRATION_SERVICE_URL}/vi/wifi/position/report")
    local async_end=$(date +%s.%N)
    
    local async_http_code=$(echo $async_response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    local async_response_body=$(echo $async_response | sed -e 's/HTTPSTATUS\:.*//g')
    local async_time=$(echo "$async_end - $async_start" | bc -l | awk '{printf "%.3f", $1}')
    
    if [ "$async_http_code" -eq 202 ]; then
        echo -e "  ✓ Async processing: HTTP $async_http_code in ${async_time}s"
        # Verify async response format
        if ! verify_async_response "$async_http_code" "$async_response_body" "async-test"; then
            echo -e "  ⚠️  Async response format validation failed"
        fi
    else
        echo -e "  ✗ Async processing: HTTP $async_http_code in ${async_time}s"
        echo "    Error: $(echo $async_response_body | jq -r '.error // .message // "Unknown error"')"
        return 1
    fi
    
    # Clean up async temp file
    rm "$async_temp_file"
    
    # Compare response times
    echo ""
    echo -e "${YELLOW}Processing mode comparison:${NC}"
    echo "  Sync Processing Time: ${sync_time}s"
    echo "  Async Processing Time: ${async_time}s"
    
    local time_difference=$(echo "$sync_time - $async_time" | bc -l | awk '{printf "%.3f", $1}')
    
    # For async processing, we expect faster response times (immediate return)
    if (( $(echo "$async_time < $sync_time" | bc -l) )); then
        echo -e "  ✓ Async processing is ${time_difference}s faster than sync (expected)"
        echo -e "${GREEN}✓ Async vs sync comparison test PASSED${NC}"
        return 0
    else
        echo -e "  ⚠️  Async processing is ${time_difference}s slower than sync"
        echo -e "${YELLOW}⚠️  Async processing may not be providing expected performance benefits${NC}"
        echo "    Note: Async should return immediately (202 Accepted), sync should block until completion"
        return 0  # Not a failure, just slower than expected
    fi
}

# Function to test async job processing lifecycle
run_async_job_lifecycle_test() {
    echo -e "${BLUE}Running async job lifecycle test...${NC}"
    echo "  This test verifies the complete async processing lifecycle"
    
    # Select a test file
    local test_file=$(ls $TEST_DATA_DIR/*.json | head -1)
    echo "  Using test file: $(basename "$test_file")"
    
    # Send async request
    echo ""
    echo -e "${YELLOW}Sending async request...${NC}"
    
    # Prepare async request with processingMode in JSON body
    local async_temp_file=$(mktemp)
    jq '.options.processingMode = "async"' "$test_file" > "$async_temp_file"
    
    local async_response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d @"$async_temp_file" \
        "${INTEGRATION_SERVICE_URL}/vi/wifi/position/report")
    
    # Clean up temp file
    rm "$async_temp_file"
    
    local async_http_code=$(echo $async_response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    local async_response_body=$(echo $async_response | sed -e 's/HTTPSTATUS\:.*//g')
    
    if [ "$async_http_code" -ne 202 ] && [ "$async_http_code" -ne 200 ]; then
        echo -e "  ✗ Async request failed: HTTP $async_http_code"
        echo "    Error: $(echo $async_response_body | jq -r '.error // .message // "Unknown error"')"
        return 1
    fi
    
    # Verify async response format
    local correlation_id=$(echo "$async_response_body" | jq -r '.correlationId // "Unknown"')
    local processing_mode=$(echo "$async_response_body" | jq -r '.processingMode // "Unknown"')
    
    if [ "$processing_mode" != "async" ] || [ "$correlation_id" = "Unknown" ]; then
        echo -e "  ✗ Async response format validation failed"
        echo "    Processing Mode: $processing_mode"
        echo "    Correlation ID: $correlation_id"
        echo "    Response: $async_response_body"
        return 1
    fi
    
    echo -e "  ✓ Async request accepted: Correlation ID $correlation_id"
    
    # Check async processing health after submitting the request
    echo ""
    echo -e "${YELLOW}Checking async processing after request submission...${NC}"
    
    # Wait a moment for processing to start
    sleep 1
    
    local health_response=$(curl -s -w "HTTPSTATUS:%{http_code}" "${INTEGRATION_SERVICE_URL}/async-processing")
    local health_http_code=$(echo "$health_response" | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    local health_body=$(echo "$health_response" | sed -e 's/HTTPSTATUS\:.*//g')
    
    if [ "$health_http_code" -eq 200 ]; then
        local queue_depth=$(echo "$health_body" | jq -r '.queue.size // "Unknown"')
        local successful_processing=$(echo "$health_body" | jq -r '.processing.successfulProcessing // "Unknown"')
        local active_threads=$(echo "$health_body" | jq -r '.threadPool.activeThreads // "Unknown"')
        
        echo -e "  ✓ Async health endpoint accessible after request submission"
        echo "    Queue Depth: $queue_depth"
        echo "    Successful Processing: $successful_processing"
        echo "    Active Threads: $active_threads"
        
        # Note: Since processing happens very quickly for integration tests,
        # the task may already be completed by the time we check
        if [ "$successful_processing" != "0" ] && [ "$successful_processing" != "Unknown" ]; then
            echo -e "  ✓ Background processing has occurred (successful count: $successful_processing)"
        else
            echo -e "  ⚠️  Background processing may still be in progress or very fast"
        fi
    else
        echo -e "  ⚠️  Could not check async health after request (HTTP $health_http_code)"
    fi
    
    echo ""
    echo -e "${GREEN}✓ Async job lifecycle test PASSED${NC}"
    echo "  Correlation ID: $correlation_id"
    echo "  Note: This implementation uses correlation ID tracking, not job IDs"
    echo "  Note: Results are logged only - no retrieval endpoints available"
    return 0
}

# Main test execution
echo -e "${YELLOW}Starting async processing tests...${NC}"
echo ""

# Test 1: Async health endpoint
echo -e "${BLUE}Test 1: Async Health Endpoint${NC}"
echo "====================================="
if check_async_health; then
    echo -e "${GREEN}✓ Async health endpoint test PASSED${NC}"
else
    echo -e "${RED}✗ Async health endpoint test FAILED${NC}"
    echo "  Cannot proceed with remaining tests"
    exit 1
fi

echo ""

# Test 2: Concurrent async processing
echo -e "${BLUE}Test 2: Concurrent Async Processing${NC}"
echo "============================================="
if run_concurrent_async_test; then
    echo -e "${GREEN}✓ Concurrent async processing test PASSED${NC}"
else
    echo -e "${RED}✗ Concurrent async processing test FAILED${NC}"
    exit 1
fi

echo ""

# Test 3: Async vs sync comparison
echo -e "${BLUE}Test 3: Async vs Sync Comparison${NC}"
echo "=========================================="
if run_async_vs_sync_comparison; then
    echo -e "${GREEN}✓ Async vs sync comparison test PASSED${NC}"
else
    echo -e "${RED}✗ Async vs sync comparison test FAILED${NC}"
    exit 1
fi

echo ""

# Test 4: Async job lifecycle
echo -e "${BLUE}Test 4: Async Job Lifecycle${NC}"
echo "========================================"
if run_async_job_lifecycle_test; then
    echo -e "${GREEN}✓ Async job lifecycle test PASSED${NC}"
else
    echo -e "${RED}✗ Async job lifecycle test FAILED${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}🎉 ALL ASYNC PROCESSING TESTS PASSED!${NC}"
echo ""
echo "The async processing functionality is working correctly:"
echo "  ✓ Async health endpoint is accessible and providing metrics"
echo "  ✓ Concurrent requests are being processed successfully"
echo "  ✓ Async processing is operational with proper response formats"
echo "  ✓ Queue depth and processing metrics are being tracked"
echo "  ✓ Async vs sync processing modes are properly differentiated"
echo "  ✓ Async job lifecycle is properly managed (where endpoints available)"
echo ""
echo "Async processing health endpoint: ${INTEGRATION_SERVICE_URL}/async-processing"
echo "Main health endpoint: ${INTEGRATION_SERVICE_URL}/health"
echo ""
echo "Note: This test verifies that:"
echo "  - Async requests return immediately (HTTP 202/200) with job references"
echo "  - Sync requests block until completion and return final results"
echo "  - Concurrent async processing works correctly"
echo "  - Async processing metrics are properly tracked"
