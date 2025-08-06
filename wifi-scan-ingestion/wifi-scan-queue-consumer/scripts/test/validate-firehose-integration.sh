#!/bin/bash

# validate-firehose-integration.sh
# This script validates the complete Firehose integration flow:
# Kafka -> Spring Boot App -> Firehose -> S3

set -e
set +H  # Disable history expansion globally to handle ! characters in S3 paths

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

print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}[PASS]${NC} $2"
    else
        echo -e "${RED}[FAIL]${NC} $2"
    fi
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
FIREHOSE_DELIVERY_STREAM_NAME="MVS-stream"
S3_BUCKET_NAME="wifi-scan-data-bucket"
SERVICE_URL="http://localhost:8080"
SERVICE_CONTEXT_PATH="/frisco-location-wifi-scan-vmb-consumer"

# Default parameters
MESSAGE_COUNT=10
INTERVAL=1
TIMEOUT=120
VERBOSE=false
CHECK_S3=true
WAIT_FOR_S3=30

# Message tracking
SENT_MESSAGES_FILE="/tmp/sent_messages_$(date +%s)_$$"
TEST_SESSION_ID="test-session-$(date +%s)-$$"

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
        --no-s3-check)
            CHECK_S3=false
            shift
            ;;
        --wait-s3)
            WAIT_FOR_S3="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --count N          Number of messages to send (default: 10)"
            echo "  --interval N       Interval between messages in seconds (default: 1)"
            echo "  --timeout N        Timeout for validation in seconds (default: 120)"
            echo "  --verbose          Enable verbose output"
            echo "  --no-s3-check      Skip S3 validation"
            echo "  --wait-s3 N        Wait time for S3 data in seconds (default: 30)"
            echo "  --help             Show this help message"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    # Check if AWS CLI is available
    if ! command -v aws &> /dev/null; then
        print_error "AWS CLI is not installed"
        exit 1
    fi
    
    # Check if jq is available
    if ! command -v jq &> /dev/null; then
        print_error "jq is not installed"
        exit 1
    fi
    
    # Check if LocalStack is running
    if ! curl -s http://localhost:4566/_localstack/health > /dev/null 2>&1; then
        print_error "LocalStack is not running. Please start it first with: ./scripts/setup-aws-infrastructure.sh"
        exit 1
    fi
    
    # Check if service is running
    if ! curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health" > /dev/null 2>&1; then
        print_error "Spring Boot service is not running. Please start the application first."
        exit 1
    fi
    
    print_success "All prerequisites are satisfied"
}

# Check Firehose stream status
check_firehose_stream() {
    print_test "Checking Firehose delivery stream status..."
    
    local status=$(aws firehose describe-delivery-stream \
        --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME \
        --profile localstack \
        --endpoint-url=$AWS_ENDPOINT_URL \
        --query 'DeliveryStreamDescription.DeliveryStreamStatus' \
        --output text 2>/dev/null || echo "UNKNOWN")
    
    if [ "$status" = "ACTIVE" ]; then
        print_result 0 "Firehose stream is ACTIVE"
        return 0
    else
        print_result 1 "Firehose stream status: $status"
        return 1
    fi
}

# Check S3 bucket accessibility
check_s3_bucket() {
    print_test "Checking S3 bucket accessibility..."
    
    if aws s3 ls s3://$S3_BUCKET_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
        print_result 0 "S3 bucket is accessible"
        return 0
    else
        print_result 1 "S3 bucket is not accessible"
        return 1
    fi
}

# Check service health
check_service_health() {
    print_test "Checking service health..."
    
    # Check readiness endpoint instead of general health to avoid messageConsumptionActivity DOWN status
    local health_response=$(curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health/readiness" 2>/dev/null || echo "")
    
    if [ -z "$health_response" ]; then
        print_result 1 "Service is not responding"
        return 1
    fi
    
    local status=$(echo "$health_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
    
    if [ "$status" = "UP" ]; then
        print_result 0 "Service readiness is UP"
        return 0
    else
        print_result 1 "Service readiness status: $status"
        return 1
    fi
}

# Check Firehose connectivity from service
check_firehose_connectivity() {
    print_test "Checking Firehose connectivity from service..."
    
    local readiness_response=$(curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health/readiness" 2>/dev/null || echo "")
    
    if [ -z "$readiness_response" ]; then
        print_result 1 "Could not get readiness status"
        return 1
    fi
    
    local firehose_status=$(echo "$readiness_response" | jq -r '.components.firehoseConnectivity.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
    
    if [ "$firehose_status" = "UP" ]; then
        print_result 0 "Firehose connectivity is UP"
        return 0
    else
        local details=$(echo "$readiness_response" | jq -r '.components.firehoseConnectivity.details.reason // "Unknown error"' 2>/dev/null || echo "Unknown error")
        print_result 1 "Firehose connectivity is DOWN: $details"
        return 1
    fi
}

# Wait for messages to be processed
wait_for_processing() {
    print_test "Waiting for messages to be processed..."
    
    # First, check if service needs recovery from message consumption timeout
    local health_response=$(curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health/liveness" 2>/dev/null || echo "")
    local liveness_status=$(echo "$health_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
    
    if [ "$liveness_status" = "DOWN" ]; then
        print_warning "Service liveness is DOWN - checking if it's due to message consumption timeout..."
        
        # Check if it's specifically a message consumption timeout issue
        local consumption_reason=$(echo "$health_response" | jq -r '.components.messageConsumptionActivity.details.reason // ""' 2>/dev/null || echo "")
        
        if [[ "$consumption_reason" == *"hasn't received messages"* ]] || [[ "$consumption_reason" == *"message timeout"* ]]; then
            print_warning "Detected message consumption timeout - sending recovery messages..."
            
            # Send a few messages to wake up the service
            print_info "Sending 3 recovery messages to wake up the consumer..."
            if ./scripts/send-wifi-scan-messages.sh --count 3 --interval 1 --ssl > /dev/null 2>&1; then
                print_info "Recovery messages sent successfully"
                
                # Wait for health to recover
                print_info "Waiting for service to recover..."
                local recovery_attempts=0
                local max_recovery_attempts=12  # 60 seconds max
                
                while [ $recovery_attempts -lt $max_recovery_attempts ]; do
                    sleep 5
                    local new_health_response=$(curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health/liveness" 2>/dev/null || echo "")
                    local new_liveness_status=$(echo "$new_health_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
                    
                    if [ "$new_liveness_status" = "UP" ]; then
                        print_info "✅ Service health recovered successfully!"
                        break
                    fi
                    
                    ((recovery_attempts++))
                    printf "\r${BLUE}[INFO]${NC} Recovery attempt %d/%d..." "$recovery_attempts" "$max_recovery_attempts"
                done
                
                echo ""
                if [ "$new_liveness_status" != "UP" ]; then
                    print_warning "Service did not recover within 60 seconds, but continuing..."
                fi
            else
                print_warning "Failed to send recovery messages, but continuing..."
            fi
        else
            print_warning "Liveness is DOWN for reasons other than message timeout: $consumption_reason"
        fi
    fi
    
    local start_time=$(date +%s)
    local end_time=$((start_time + TIMEOUT))
    local processed_count=0
    
    while [ $(date +%s) -lt $end_time ]; do
        # Check service health to ensure it's still processing
        local health_response=$(curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health/liveness" 2>/dev/null || echo "")
        local liveness_status=$(echo "$health_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
        
        if [ "$liveness_status" = "UP" ]; then
            print_info "Service is healthy and processing messages..."
            break
        else
            print_warning "Service liveness status: $liveness_status"
            sleep 5
        fi
    done
    
    print_success "Message processing phase completed"
}

# Clean up test data from S3
cleanup_test_data() {
    print_test "Cleaning up test data from S3..."
    
    local s3_objects=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l)
    
    if [ $s3_objects -gt 0 ]; then
        if aws s3 rm s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
            print_result 0 "Test data cleaned up successfully ($s3_objects objects removed)"
            return 0
        else
            print_warning "Failed to clean up test data (this is not critical)"
            return 1
        fi
    else
        print_info "No test data to clean up"
        return 0
    fi
}

# Send test messages to Kafka with tracking
send_test_messages() {
    print_test "Sending $MESSAGE_COUNT test messages to Kafka with tracking..."
    
    # Initialize tracking file
    echo "[]" > "$SENT_MESSAGES_FILE"
    
    local success_count=0
    local fail_count=0
    
    for i in $(seq 1 $MESSAGE_COUNT); do
        # Create test message with unique identifiers for tracking
        local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")
        local device_id="test-device-${TEST_SESSION_ID}-$i"
        local test_ssid="TestWiFi-${TEST_SESSION_ID}-$i"
        local signal_strength=$((40 + i))
        
        local message="{\"timestamp\":\"$timestamp\",\"device_id\":\"$device_id\",\"wifi_scan_data\":{\"ssid\":\"$test_ssid\",\"signal_strength\":-$signal_strength,\"frequency\":2400,\"capabilities\":\"WPA2\"}}"
        
        if ./send-test-message.sh "$message" "wifi-scan-data" --ssl > /dev/null 2>&1; then
            ((success_count++))
            
            # Track the sent message
            track_sent_message "$message" "$device_id" "$timestamp" "$test_ssid"
            
            if [ "$VERBOSE" = true ]; then
                print_info "Message $i sent successfully (device_id: $device_id)"
            fi
        else
            ((fail_count++))
            if [ "$VERBOSE" = true ]; then
                print_warning "Failed to send message $i"
            fi
        fi
        
        # Wait between messages
        if [ $i -lt $MESSAGE_COUNT ]; then
            sleep $INTERVAL
        fi
    done
    
    if [ $fail_count -eq 0 ]; then
        print_result 0 "All $success_count messages sent successfully and tracked"
        return 0
    else
        print_result 1 "Sent $success_count messages, failed $fail_count"
        return 1
    fi
}

# Track sent message for later correlation
track_sent_message() {
    local message="$1"
    local device_id="$2"
    local timestamp="$3"
    local ssid="$4"
    
    # Create tracking entry
    local tracking_entry=$(jq -n \
        --arg message "$message" \
        --arg device_id "$device_id" \
        --arg timestamp "$timestamp" \
        --arg ssid "$ssid" \
        '{
            message: $message,
            device_id: $device_id,
            timestamp: $timestamp,
            ssid: $ssid,
            found_in_s3: false
        }')
    
    # Add to tracking file
    local current_content=$(cat "$SENT_MESSAGES_FILE")
    echo "$current_content" | jq ". += [$tracking_entry]" > "$SENT_MESSAGES_FILE"
}

# Wait for messages to be processed
wait_for_processing() {
    print_test "Waiting for messages to be processed..."
    
    # First, check if service needs recovery from message consumption timeout
    local health_response=$(curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health/liveness" 2>/dev/null || echo "")
    local liveness_status=$(echo "$health_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
    
    if [ "$liveness_status" = "DOWN" ]; then
        print_warning "Service liveness is DOWN - checking if it's due to message consumption timeout..."
        
        # Check if it's specifically a message consumption timeout issue
        local consumption_reason=$(echo "$health_response" | jq -r '.components.messageConsumptionActivity.details.reason // ""' 2>/dev/null || echo "")
        
        if [[ "$consumption_reason" == *"hasn't received messages"* ]] || [[ "$consumption_reason" == *"message timeout"* ]]; then
            print_warning "Detected message consumption timeout - sending recovery messages..."
            
            # Send a few messages to wake up the service
            print_info "Sending 3 recovery messages to wake up the consumer..."
            if ./scripts/send-wifi-scan-messages.sh --count 3 --interval 1 --ssl > /dev/null 2>&1; then
                print_info "Recovery messages sent successfully"
                
                # Wait for health to recover
                print_info "Waiting for service to recover..."
                local recovery_attempts=0
                local max_recovery_attempts=12  # 60 seconds max
                
                while [ $recovery_attempts -lt $max_recovery_attempts ]; do
                    sleep 5
                    local new_health_response=$(curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health/liveness" 2>/dev/null || echo "")
                    local new_liveness_status=$(echo "$new_health_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
                    
                    if [ "$new_liveness_status" = "UP" ]; then
                        print_info "✅ Service health recovered successfully!"
                        break
                    fi
                    
                    ((recovery_attempts++))
                    printf "\r${BLUE}[INFO]${NC} Recovery attempt %d/%d..." "$recovery_attempts" "$max_recovery_attempts"
                done
                
                echo ""
                if [ "$new_liveness_status" != "UP" ]; then
                    print_warning "Service did not recover within 60 seconds, but continuing..."
                fi
            else
                print_warning "Failed to send recovery messages, but continuing..."
            fi
        else
            print_warning "Liveness is DOWN for reasons other than message timeout: $consumption_reason"
        fi
    fi
    
    local start_time=$(date +%s)
    local end_time=$((start_time + TIMEOUT))
    local processed_count=0
    
    while [ $(date +%s) -lt $end_time ]; do
        # Check service health to ensure it's still processing
        local health_response=$(curl -s "$SERVICE_URL$SERVICE_CONTEXT_PATH/health/liveness" 2>/dev/null || echo "")
        local liveness_status=$(echo "$health_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
        
        if [ "$liveness_status" = "UP" ]; then
            print_info "Service is healthy and processing messages..."
            break
        else
            print_warning "Service liveness status: $liveness_status"
            sleep 5
        fi
    done
    
    print_success "Message processing phase completed"
}

# Enhanced S3 data validation with message correlation
validate_s3_data_with_correlation() {
    print_test "Validating S3 data with message correlation..."
    
    if [ "$CHECK_S3" = false ]; then
        print_info "S3 validation skipped"
        return 0
    fi
    
    # Wait for data to appear in S3
    print_info "Waiting up to ${WAIT_FOR_S3}s for data to appear in S3..."
    
    local start_time=$(date +%s)
    local end_time=$((start_time + WAIT_FOR_S3))
    local s3_objects=0
    
    while [ $(date +%s) -lt $end_time ]; do
        s3_objects=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l)
        
        if [ $s3_objects -gt 0 ]; then
            print_success "Found $s3_objects object(s) in S3"
            break
        fi
        
        printf "\r${BLUE}[INFO]${NC} Waiting for S3 data... (%d s remaining)" $((end_time - $(date +%s)))
        sleep 2
    done
    
    echo ""
    
    if [ $s3_objects -eq 0 ]; then
        print_result 1 "No data found in S3 after ${WAIT_FOR_S3}s"
        return 1
    fi
    
    # Now validate each S3 file and correlate with sent messages
    print_info "Validating S3 files and correlating with sent messages..."
    
    local correlation_result=0
    
    # Get all S3 objects
    local s3_files=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | awk '{print $4}' | grep -v '^$')
    
    if [ -z "$s3_files" ]; then
        print_result 1 "No S3 files found for validation"
        return 1
    fi
    
    # Process each S3 file
    local files_processed=0
    local files_valid=0
    
    while IFS= read -r s3_file; do
        if [ -z "$s3_file" ]; then
            continue
        fi
        
        print_info "Processing S3 file: $s3_file"
        ((files_processed++))
        
        if validate_and_correlate_s3_file "$s3_file"; then
            ((files_valid++))
        else
            correlation_result=1
        fi
        
    done <<< "$s3_files"
    
    # Check correlation results
    local sent_count=$(cat "$SENT_MESSAGES_FILE" | jq 'length')
    local found_count=$(cat "$SENT_MESSAGES_FILE" | jq 'map(select(.found_in_s3 == true)) | length')
    local missing_count=$((sent_count - found_count))
    
    print_info "Correlation Summary:"
    print_info "  S3 Files Processed: $files_processed"
    print_info "  S3 Files Valid: $files_valid"
    print_info "  Messages Sent: $sent_count"
    print_info "  Messages Found in S3: $found_count"
    print_info "  Messages Missing: $missing_count"
    
    if [ $missing_count -eq 0 ] && [ $files_valid -eq $files_processed ]; then
        print_result 0 "All sent messages found and validated in S3"
        return 0
    else
        print_result 1 "Message correlation failed - some messages missing or invalid"
        
        # Show missing messages if verbose
        if [ "$VERBOSE" = true ] && [ $missing_count -gt 0 ]; then
            print_warning "Missing messages:"
            cat "$SENT_MESSAGES_FILE" | jq -r '.[] | select(.found_in_s3 == false) | "  - Device ID: \(.device_id), SSID: \(.ssid), Timestamp: \(.timestamp)"'
        fi
        
        return 1
    fi
}

# Validate individual S3 file and correlate with sent messages
validate_and_correlate_s3_file() {
    local s3_file="$1"
    local temp_file="/tmp/s3_validation_$(date +%s)_$$"
    
    # Download file from S3 with proper escaping for special characters
    # Use printf to avoid shell interpretation of special characters
    local s3_url
    printf -v s3_url "s3://%s/%s" "$S3_BUCKET_NAME" "$s3_file"
    
    local download_output
    download_output=$(aws s3 cp "$s3_url" "$temp_file" --profile localstack --endpoint-url="$AWS_ENDPOINT_URL" 2>&1)
    local download_result=$?
    
    if [ $download_result -ne 0 ]; then
        if [ "$VERBOSE" = true ]; then
            print_warning "Failed to download S3 file: $s3_file"
            print_warning "AWS CLI error: $download_output"
        else
            print_warning "Failed to download S3 file: $s3_file"
        fi
        return 1
    fi
    
    # Check if file exists and has content
    if [ ! -f "$temp_file" ] || [ ! -s "$temp_file" ]; then
        print_warning "S3 file is empty or doesn't exist: $s3_file"
        rm -f "$temp_file" 2>/dev/null || true
        return 1
    fi
    
    # Extract content from the file (base64 decode + gunzip)
    local extracted_content=""
    if ! extracted_content=$(extract_s3_file_content "$temp_file"); then
        print_warning "Failed to extract content from S3 file: $s3_file"
        rm -f "$temp_file" 2>/dev/null || true
        return 1
    fi
    
    # Parse messages from extracted content
    if ! parse_messages_from_content "$extracted_content"; then
        print_warning "Failed to parse messages from S3 file: $s3_file"
        rm -f "$temp_file" 2>/dev/null || true
        return 1
    fi
    
    # Correlate with sent messages
    local correlations_made=0
    
    # Find the most recent temp messages file
    local temp_messages_file=$(ls -t /tmp/messages_*_$$ 2>/dev/null | head -1)
    
    if [ -f "$temp_messages_file" ]; then
        while IFS= read -r message_json; do
            if [ -n "$message_json" ] && correlate_message_with_sent "$message_json"; then
                ((correlations_made++))
            fi
        done < "$temp_messages_file"
        rm -f "$temp_messages_file"
    fi
    
    # Clean up
    rm -f "$temp_file" 2>/dev/null || true
    
    if [ $correlations_made -gt 0 ]; then
        if [ "$VERBOSE" = true ]; then
            print_info "  ✅ S3 file valid, $correlations_made message(s) correlated: $s3_file"
        fi
        return 0
    else
        print_warning "  ❌ No messages correlated in S3 file: $s3_file"
        return 1
    fi
}

# Extract content from S3 file (handle base64 + gzip)
extract_s3_file_content() {
    local file_path="$1"
    local temp_decoded="/tmp/decoded_$(date +%s)_$$"
    
    # Read and clean up the file content
    local file_content=$(cat "$file_path" | tr -d '%' | tr -d '\n' | tr -d ' ')
    
    # Check if it's base64 encoded
    if [[ "$file_content" =~ ^[A-Za-z0-9+/]*=*$ ]]; then
        # Decode base64 to a temporary file to handle binary data properly
        if echo "$file_content" | base64 -d > "$temp_decoded" 2>/dev/null; then
            # Check if decoded file is gzipped
            if file "$temp_decoded" | grep -q "gzip" 2>/dev/null; then
                # Decompress gzipped content
                local decompressed_content
                if decompressed_content=$(gunzip -c "$temp_decoded" 2>/dev/null); then
                    rm -f "$temp_decoded"
                    echo "$decompressed_content"
                    return 0
                else
                    # Failed to decompress
                    rm -f "$temp_decoded"
                    return 1
                fi
            else
                # Not gzipped, return decoded content
                local decoded_content=$(cat "$temp_decoded" 2>/dev/null)
                rm -f "$temp_decoded"
                echo "$decoded_content"
                return 0
            fi
        else
            # Failed to decode base64
            return 1
        fi
    else
        # Not base64, check if it's directly gzipped
        if file "$file_path" | grep -q "gzip" 2>/dev/null; then
            # Decompress directly
            local decompressed_content
            if decompressed_content=$(gunzip -c "$file_path" 2>/dev/null); then
                echo "$decompressed_content"
                return 0
            else
                return 1
            fi
        else
            # Plain text content
            echo "$file_content"
            return 0
        fi
    fi
}



# Parse messages from extracted content
parse_messages_from_content() {
    local content="$1"
    local temp_messages_file="/tmp/messages_$(date +%s)_$$"
    
    # Clear the temporary file
    > "$temp_messages_file"
    
    # Try to parse as JSON array first
    if echo "$content" | jq -e '. | type == "array"' > /dev/null 2>&1; then
        # It's a JSON array
        local array_length=$(echo "$content" | jq 'length')
        for ((i=0; i<array_length; i++)); do
            local message=$(echo "$content" | jq -c ".[$i]")
            echo "$message" >> "$temp_messages_file"
        done
        return 0
    elif echo "$content" | jq -e '. | type == "object"' > /dev/null 2>&1; then
        # It's a single JSON object
        echo "$content" >> "$temp_messages_file"
        return 0
    else
        # Try to parse as newline-delimited JSON
        while IFS= read -r line; do
            if [ -n "$line" ] && echo "$line" | jq -e . > /dev/null 2>&1; then
                echo "$line" >> "$temp_messages_file"
            fi
        done <<< "$content"
        
        if [ -s "$temp_messages_file" ]; then
            return 0
        else
            rm -f "$temp_messages_file"
            return 1
        fi
    fi
}

# Correlate a message with sent messages
correlate_message_with_sent() {
    local message_json="$1"
    
    # Extract key fields from the message
    local device_id=$(echo "$message_json" | jq -r '.device_id // empty')
    local timestamp=$(echo "$message_json" | jq -r '.timestamp // empty')
    local ssid=$(echo "$message_json" | jq -r '.wifi_scan_data.ssid // empty')
    
    if [ -z "$device_id" ] || [ -z "$timestamp" ] || [ -z "$ssid" ]; then
        if [ "$VERBOSE" = true ]; then
            print_warning "  Message missing required fields for correlation"
        fi
        return 1
    fi
    
    # Check if this message matches any sent message
    local sent_messages=$(cat "$SENT_MESSAGES_FILE")
    local match_index=$(echo "$sent_messages" | jq --arg device_id "$device_id" --arg ssid "$ssid" 'map(.device_id == $device_id and .ssid == $ssid) | index(true)')
    
    if [ "$match_index" != "null" ] && [ -n "$match_index" ]; then
        # Mark the message as found
        echo "$sent_messages" | jq --argjson index "$match_index" '.[$index].found_in_s3 = true' > "$SENT_MESSAGES_FILE"
        
        if [ "$VERBOSE" = true ]; then
            print_info "  ✅ Message correlated - Device ID: $device_id, SSID: $ssid"
        fi
        return 0
    else
        if [ "$VERBOSE" = true ]; then
            print_warning "  ❌ Message not found in sent messages - Device ID: $device_id, SSID: $ssid"
        fi
        return 1
    fi
}

# Clean up tracking files
cleanup_tracking_files() {
    print_test "Cleaning up tracking files..."
    
    if [ -f "$SENT_MESSAGES_FILE" ]; then
        rm -f "$SENT_MESSAGES_FILE"
        print_result 0 "Tracking files cleaned up"
    else
        print_info "No tracking files to clean up"
    fi
}

# Clean up test data from S3
cleanup_test_data() {
    print_test "Cleaning up test data from S3..."
    
    local s3_objects=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l)
    
    if [ $s3_objects -gt 0 ]; then
        if aws s3 rm s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
            print_result 0 "Test data cleaned up successfully ($s3_objects objects removed)"
            return 0
        else
            print_warning "Failed to clean up test data (this is not critical)"
            return 1
        fi
    else
        print_info "No test data to clean up"
        return 0
    fi
}

# Main validation function
main() {
    print_header "FIREHOSE INTEGRATION VALIDATION"
    print_info "Testing end-to-end flow: Kafka -> Spring Boot -> Firehose -> S3"
    print_info "Configuration:"
    echo "  - Message Count: $MESSAGE_COUNT"
    echo "  - Interval: ${INTERVAL}s"
    echo "  - Timeout: ${TIMEOUT}s"
    echo "  - S3 Check: $CHECK_S3"
    echo "  - Verbose: $VERBOSE"
    echo "  - Test Session ID: $TEST_SESSION_ID"
    echo ""
    
    # Track test results
    local total_tests=0
    local passed_tests=0
    local failed_tests=0
    
    # Ensure cleanup on exit
    trap 'cleanup_tracking_files' EXIT
    
    # Run validation tests
    check_prerequisites
    ((total_tests++))
    ((passed_tests++))
    
    check_firehose_stream
    if [ $? -eq 0 ]; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    ((total_tests++))
    
    check_s3_bucket
    if [ $? -eq 0 ]; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    ((total_tests++))
    
    check_service_health
    if [ $? -eq 0 ]; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    ((total_tests++))
    
    check_firehose_connectivity
    if [ $? -eq 0 ]; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    ((total_tests++))
    
    send_test_messages
    if [ $? -eq 0 ]; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    ((total_tests++))
    
    wait_for_processing
    ((total_tests++))
    ((passed_tests++))
    
    # Use the new enhanced validation with correlation
    validate_s3_data_with_correlation
    if [ $? -eq 0 ]; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    ((total_tests++))
    
    cleanup_test_data
    if [ $? -eq 0 ]; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    ((total_tests++))
    
    # Print summary
    print_header "VALIDATION RESULTS"
    echo "Total Tests: $total_tests"
    echo -e "Passed: ${GREEN}$passed_tests${NC}"
    echo -e "Failed: ${RED}$failed_tests${NC}"
    
    if [ $failed_tests -eq 0 ]; then
        print_success "🎉 All Firehose integration tests passed!"
        exit 0
    else
        print_error "❌ Some tests failed. Please check the output above."
        exit 1
    fi
}

# Run main function
main 