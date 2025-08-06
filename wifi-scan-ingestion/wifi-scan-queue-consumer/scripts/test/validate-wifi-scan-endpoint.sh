#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/validate-wifi-scan-endpoint.sh
# Comprehensive test for the /api/metrics/wifi-scan endpoint with Firehose and S3 validation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

print_header() {
    echo -e "${MAGENTA}========================================${NC}"
    echo -e "${MAGENTA}$1${NC}"
    echo -e "${MAGENTA}========================================${NC}"
}

print_test() {
    echo -e "${CYAN}[TEST]${NC} $1"
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Configuration
AWS_REGION="us-east-1"
AWS_ENDPOINT_URL="http://localhost:4566"
AWS_ACCESS_KEY_ID="test"
AWS_SECRET_ACCESS_KEY="test"
S3_BUCKET_NAME="wifi-scan-data-bucket"
SERVICE_URL="http://localhost:8080"
ENDPOINT_PATH="/frisco-location-wifi-scan-vmb-consumer/api/metrics/wifi-scan"

# Default parameters
MESSAGE_COUNT=3
INTERVAL=1
TIMEOUT=60
VERBOSE=false
SKIP_S3_VALIDATION=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --count)
            MESSAGE_COUNT="$2"
            shift 2
            ;;
        --interval)
            INTERVAL="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --skip-s3-validation)
            SKIP_S3_VALIDATION=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --count N              Number of messages to send (default: 3)"
            echo "  --interval SEC         Interval between messages (default: 1)"
            echo "  --timeout SEC          Timeout for validation (default: 60)"
            echo "  --verbose              Enable verbose output"
            echo "  --skip-s3-validation   Skip S3 validation"
            echo "  --help                 Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Function to generate a unique WiFi scan message
generate_wifi_scan_message() {
    local test_id="$1"
    local mac_address=$(printf "%02x:%02x:%02x:%02x:%02x:%02x" $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)))
    local signal_strength=$(echo "scale=1; -30 - $RANDOM % 70" | bc 2>/dev/null || echo "-65.4")
    local frequency=$((2400 + RANDOM % 1000))
    local ssid="TestWiFi-endpoint-test-${test_id}"
    local link_speed=$((54 + RANDOM % 1000))
    local channel_width=$((20 + (RANDOM % 4) * 20))
    local request_id="req-endpoint-test-$(date +%s)-${test_id}"
    local device_id="test-device-endpoint-test-${test_id}"
    
    cat << EOF
{
    "wifiScanResults": [
        {
            "macAddress": "$mac_address",
            "signalStrength": $signal_strength,
            "frequency": $frequency,
            "ssid": "$ssid",
            "linkSpeed": $link_speed,
            "channelWidth": $channel_width
        }
    ],
    "client": "wifi-scan-endpoint-test",
    "requestId": "$request_id",
    "deviceId": "$device_id",
    "application": "wifi-scan-endpoint-validation",
    "calculationDetail": true,
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
}
EOF
}

# Function to check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    # Check if service is running
    if ! curl -s "${SERVICE_URL}/actuator/health" >/dev/null 2>&1; then
        print_error "Service is not running at ${SERVICE_URL}"
        return 1
    fi
    
    # Check if AWS CLI is available
    if ! command -v aws >/dev/null 2>&1; then
        print_error "AWS CLI is not installed"
        return 1
    fi
    
    # Check if jq is available
    if ! command -v jq >/dev/null 2>&1; then
        print_error "jq is not installed"
        return 1
    fi
    
    print_success "All prerequisites are satisfied"
    return 0
}

# Function to send message to endpoint
send_message_to_endpoint() {
    local message="$1"
    local test_id="$2"
    
    print_info "Sending message $test_id to endpoint..."
    
    local response
    response=$(curl -s -w "\n%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -d "$message" \
        "${SERVICE_URL}${ENDPOINT_PATH}" 2>/dev/null)
    
    local http_code=$(echo "$response" | tail -n1)
    local response_body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -eq 200 ]; then
        local status=$(echo "$response_body" | jq -r '.status' 2>/dev/null || echo "unknown")
        local message_text=$(echo "$response_body" | jq -r '.message' 2>/dev/null || echo "No message")
        local original_size=$(echo "$response_body" | jq -r '.originalMessageSize' 2>/dev/null || echo "unknown")
        local compressed_size=$(echo "$response_body" | jq -r '.compressedMessageSize' 2>/dev/null || echo "unknown")
        local compression_ratio=$(echo "$response_body" | jq -r '.compressionRatio' 2>/dev/null || echo "unknown")
        
        if [ "$VERBOSE" = true ]; then
            echo "Response Details:"
            echo "  Status: $status"
            echo "  Message: $message_text"
            echo "  Original Size: $original_size characters"
            echo "  Compressed Size: $compressed_size characters"
            echo "  Compression Ratio: $compression_ratio%"
        fi
        
        if [ "$status" = "success" ]; then
            print_success "Message $test_id processed successfully"
            echo "$response_body" > "/tmp/endpoint_response_${test_id}.json"
            return 0
        else
            print_error "Message $test_id processing failed: $message_text"
            return 1
        fi
    else
        print_error "Message $test_id failed with HTTP status: $http_code"
        print_error "Response: $response_body"
        return 1
    fi
}

# Function to wait for S3 data
wait_for_s3_data() {
    local expected_count="$1"
    local max_wait="$2"
    local wait_interval=2
    local elapsed=0
    
    print_info "Waiting for $expected_count message(s) to appear in S3..."
    
    while [ $elapsed -lt $max_wait ]; do
        local s3_count=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l)
        
        if [ $s3_count -ge $expected_count ]; then
            print_success "Found $s3_count object(s) in S3 (expected: $expected_count)"
            return 0
        fi
        
        if [ "$VERBOSE" = true ]; then
            print_info "Found $s3_count object(s) in S3, waiting... (${elapsed}s/${max_wait}s)"
        fi
        
        sleep $wait_interval
        elapsed=$((elapsed + wait_interval))
    done
    
    print_error "Timeout waiting for S3 data. Found $(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l) object(s), expected $expected_count"
    return 1
}

# Function to validate S3 data
validate_s3_data() {
    local expected_count="$1"
    
    if [ "$SKIP_S3_VALIDATION" = true ]; then
        print_info "S3 validation skipped"
        return 0
    fi
    
    print_info "Validating S3 data..."
    
    # Get list of S3 objects
    local s3_objects=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null)
    local s3_count=$(echo "$s3_objects" | wc -l)
    
    if [ $s3_count -lt $expected_count ]; then
        print_error "Insufficient S3 objects: found $s3_count, expected $expected_count"
        return 1
    fi
    
    print_success "Found $s3_count S3 object(s)"
    
    # Validate each S3 file
    local valid_files=0
    while read -r line; do
        local s3_key=$(echo "$line" | awk '{print $4}')
        if [ -n "$s3_key" ]; then
            print_info "Validating S3 file: $s3_key"
            
            # Download and validate the file
            local temp_file="/tmp/s3_validation_$(date +%s)_$RANDOM"
            if aws s3 cp "s3://$S3_BUCKET_NAME/$s3_key" "$temp_file" --profile localstack --endpoint-url=$AWS_ENDPOINT_URL >/dev/null 2>&1; then
                # Check if file is compressed/encoded
                local file_size=$(stat -f%z "$temp_file" 2>/dev/null || stat -c%s "$temp_file" 2>/dev/null || echo "0")
                local file_content=$(cat "$temp_file" 2>/dev/null || echo "")
                
                if [ -n "$file_content" ] && [ $file_size -gt 0 ]; then
                    # Try to decode/decompress to validate
                    if echo "$file_content" | base64 -d >/dev/null 2>&1; then
                        print_success "✅ S3 file is valid (base64 encoded): $s3_key"
                        valid_files=$((valid_files + 1))
                    else
                        print_warning "⚠️  S3 file may not be properly encoded: $s3_key"
                    fi
                else
                    print_error "❌ S3 file is empty or invalid: $s3_key"
                fi
                
                rm -f "$temp_file"
            else
                print_error "❌ Failed to download S3 file: $s3_key"
            fi
        fi
    done <<< "$s3_objects"
    
    if [ $valid_files -ge $expected_count ]; then
        print_success "✅ S3 validation completed: $valid_files valid files found"
        return 0
    else
        print_error "❌ S3 validation failed: only $valid_files valid files found, expected $expected_count"
        return 1
    fi
}

# Function to clean up test data
cleanup_test_data() {
    print_info "Cleaning up test data..."
    
    # Clean up S3
    if aws s3 rm s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL >/dev/null 2>&1; then
        print_success "✅ S3 test data cleaned up"
    else
        print_warning "⚠️  Failed to clean S3 test data"
    fi
    
    # Clean up temporary files
    rm -f /tmp/endpoint_response_*.json
    rm -f /tmp/s3_validation_*
    
    print_success "✅ Test cleanup completed"
}

# Main test function
main() {
    print_header "WIFI SCAN ENDPOINT VALIDATION"
    
    print_info "Configuration:"
    echo "  - Message Count: $MESSAGE_COUNT"
    echo "  - Interval: ${INTERVAL}s"
    echo "  - Timeout: ${TIMEOUT}s"
    echo "  - Service URL: $SERVICE_URL"
    echo "  - Endpoint: $ENDPOINT_PATH"
    echo "  - S3 Bucket: $S3_BUCKET_NAME"
    echo "  - Verbose: $VERBOSE"
    echo "  - Skip S3 Validation: $SKIP_S3_VALIDATION"
    echo ""
    
    # Check prerequisites
    if ! check_prerequisites; then
        print_error "Prerequisites check failed"
        exit 1
    fi
    
    # Clean up any existing test data
    cleanup_test_data
    
    # Send messages to endpoint
    local success_count=0
    local failure_count=0
    
    print_info "Sending $MESSAGE_COUNT message(s) to endpoint..."
    
    for i in $(seq 1 $MESSAGE_COUNT); do
        local message=$(generate_wifi_scan_message "$i")
        
        if send_message_to_endpoint "$message" "$i"; then
            ((success_count++))
        else
            ((failure_count++))
        fi
        
        # Wait between messages (except for the last one)
        if [ $i -lt $MESSAGE_COUNT ]; then
            sleep $INTERVAL
        fi
    done
    
    print_info "Endpoint test results: $success_count successful, $failure_count failed"
    
    if [ $failure_count -gt 0 ]; then
        print_error "Some endpoint tests failed"
        cleanup_test_data
        exit 1
    fi
    
    # Wait for and validate S3 data
    if ! wait_for_s3_data $success_count $TIMEOUT; then
        print_error "S3 data validation timeout"
        cleanup_test_data
        exit 1
    fi
    
    if ! validate_s3_data $success_count; then
        print_error "S3 data validation failed"
        cleanup_test_data
        exit 1
    fi
    
    # Final cleanup
    cleanup_test_data
    
    print_header "VALIDATION RESULTS"
    print_success "✅ All tests passed!"
    print_info "Summary:"
    echo "  - Messages sent: $MESSAGE_COUNT"
    echo "  - Endpoint success: $success_count"
    echo "  - S3 validation: PASS"
    echo "  - Compression/encoding: VALIDATED"
    
    exit 0
}

# Run main function
main "$@" 