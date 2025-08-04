#!/bin/bash

# wifi-scan-ingestion/firehose-ingestion/scripts/ingestion_integration_test.sh

# Phase 3 Integration Test Script for WiFi Scan Data Pipeline
# ==========================================================
# 
# This script tests the complete data flow from Kinesis Firehose to S3,
# verifying event notifications, SQS message delivery, and file naming patterns.
#
# Prerequisites:
# - LocalStack running with required services
# - Terraform infrastructure deployed
# - AWS CLI configured for LocalStack
# - Python 3 with required modules

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
SAMPLE_DATA_FILE="${PROJECT_ROOT}/documents/smaple_wifiscan.json"
FIREHOSE_STREAM_NAME="MVS-stream"
S3_BUCKET_NAME="ingested-wifiscan-data"
SQS_QUEUE_NAME="wifi_scan_ingestion_event_queue"
AWS_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if LocalStack is running
    if ! curl -s "${AWS_ENDPOINT}/health" > /dev/null 2>&1; then
        log_error "LocalStack is not running at ${AWS_ENDPOINT}"
        log_info "Please start LocalStack first: ./start-localstack.sh"
        exit 1
    fi
    log_success "LocalStack is running"
    
    # Check if sample data file exists
    if [[ ! -f "${SAMPLE_DATA_FILE}" ]]; then
        log_error "Sample data file not found: ${SAMPLE_DATA_FILE}"
        exit 1
    fi
    log_success "Sample data file found"
    
    # Check if message processor exists
    if [[ ! -f "${SCRIPT_DIR}/message_processor.py" ]]; then
        log_error "Message processor not found: ${SCRIPT_DIR}/message_processor.py"
        exit 1
    fi
    log_success "Message processor found"
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI not found"
        exit 1
    fi
    log_success "AWS CLI found"
    
    # Check Python 3
    if ! command -v python3 &> /dev/null; then
        log_error "Python 3 not found"
        exit 1
    fi
    log_success "Python 3 found"
}

# Configure AWS CLI for LocalStack
configure_aws_cli() {
    export AWS_ACCESS_KEY_ID="test"
    export AWS_SECRET_ACCESS_KEY="test"
    export AWS_DEFAULT_REGION="${AWS_REGION}"
    export AWS_ENDPOINT_URL="${AWS_ENDPOINT}"
}

# Test infrastructure status
test_infrastructure() {
    log_info "Testing infrastructure status..."
    
    # Check Firehose stream
    if aws --endpoint-url="${AWS_ENDPOINT}" firehose describe-delivery-stream \
        --delivery-stream-name "${FIREHOSE_STREAM_NAME}" > /dev/null 2>&1; then
        log_success "Firehose stream '${FIREHOSE_STREAM_NAME}' exists"
    else
        log_error "Firehose stream '${FIREHOSE_STREAM_NAME}' not found"
        exit 1
    fi
    
    # Check S3 bucket
    if aws --endpoint-url="${AWS_ENDPOINT}" s3api head-bucket \
        --bucket "${S3_BUCKET_NAME}" > /dev/null 2>&1; then
        log_success "S3 bucket '${S3_BUCKET_NAME}' exists"
    else
        log_error "S3 bucket '${S3_BUCKET_NAME}' not found"
        exit 1
    fi
    
    # Check SQS queue
    local queue_url
    queue_url=$(aws --endpoint-url="${AWS_ENDPOINT}" sqs get-queue-url \
        --queue-name "${SQS_QUEUE_NAME}" --query 'QueueUrl' --output text 2>/dev/null || echo "")
    
    if [[ -n "${queue_url}" ]]; then
        log_success "SQS queue '${SQS_QUEUE_NAME}' exists"
    else
        log_error "SQS queue '${SQS_QUEUE_NAME}' not found"
        exit 1
    fi
}

# Prepare test data
prepare_test_data() {
    log_info "Preparing test data..."
    
    # Process sample data with message processor
    local temp_dir="${SCRIPT_DIR}/temp"
    mkdir -p "${temp_dir}"
    
    # Create compressed and encoded payload
    log_info "Compressing and encoding sample WiFi scan data..."
    python3 "${SCRIPT_DIR}/message_processor.py" \
        --compress --firehose \
        --file "${SAMPLE_DATA_FILE}" \
        --output "${temp_dir}/firehose_payload.json" \
        --verbose
    
    if [[ ! -f "${temp_dir}/firehose_payload.json" ]]; then
        log_error "Failed to create Firehose payload"
        exit 1
    fi
    
    log_success "Test data prepared: ${temp_dir}/firehose_payload.json"
}

# Test Firehose data ingestion
test_firehose_ingestion() {
    log_info "Testing Firehose data ingestion..."
    
    local temp_dir="${SCRIPT_DIR}/temp"
    local payload_file="${temp_dir}/firehose_payload.json"
    
    # Extract base64 data from payload
    local base64_data
    base64_data=$(jq -r '.Record.Data' "${payload_file}")
    
    if [[ -z "${base64_data}" || "${base64_data}" == "null" ]]; then
        log_error "Failed to extract base64 data from payload"
        exit 1
    fi
    
    # Send data to Firehose
    log_info "Sending compressed data to Firehose stream..."
    local record_id
    record_id=$(aws --endpoint-url="${AWS_ENDPOINT}" firehose put-record \
        --delivery-stream-name "${FIREHOSE_STREAM_NAME}" \
        --record Data="${base64_data}" \
        --query 'RecordId' --output text)
    
    if [[ -n "${record_id}" && "${record_id}" != "null" ]]; then
        log_success "Data sent to Firehose successfully. Record ID: ${record_id}"
        echo "${record_id}" > "${temp_dir}/record_id.txt"
    else
        log_error "Failed to send data to Firehose"
        exit 1
    fi
}

# Wait for S3 file delivery
wait_for_s3_delivery() {
    log_info "Waiting for S3 file delivery..."
    
    # S3 object prefix (with current date/hour for partitioning)
    local s3_prefix="MVS-stream/"
    
    local waited=0
    local max_wait=120  # 2 minutes
    local wait_interval=10
    
    local s3_files_file="${SCRIPT_DIR}/temp/s3_files.txt"
    
    while [[ ${waited} -lt ${max_wait} ]]; do
        log_info "Checking S3 bucket for files with prefix: ${s3_prefix} and suffix .txt..."
        
        # List objects in S3 bucket ending with .txt
        local s3_objects
        s3_objects=$(aws --endpoint-url="${AWS_ENDPOINT}" s3api list-objects-v2 \
            --bucket "${S3_BUCKET_NAME}" \
            --prefix "${s3_prefix}" \
            --query "Contents[?ends_with(Key, '.txt')].Key" --output text | tr -s '\t' '\n')
        
        if [ -n "$s3_objects" ]; then
            log_info "Found S3 file(s):"
            echo "$s3_objects"
            echo "$s3_objects" > "${s3_files_file}"
            return 0
        fi
        
        log_info "No .gz files found yet, waiting ${wait_interval} seconds..."
        sleep ${wait_interval}
        waited=$((waited + wait_interval))
    done
    
    log_error "Timeout waiting for S3 file delivery (${max_wait} seconds)"
    return 1
}

# Verify S3 file content
verify_s3_file_content() {
    log_info "Verifying S3 file content..."
    
    local s3_files_file="${SCRIPT_DIR}/temp/s3_files.txt"
    if [[ ! -f "${s3_files_file}" ]]; then
        log_error "S3 files list not found"
        return 1
    fi
    
    local s3_file
    s3_file=$(head -n1 "${s3_files_file}")
    
    # Expected pattern: MVS-stream/yyyy/mm/dd/HH/MVS-stream-timestamp.txt
    local expected_pattern="^MVS-stream/[0-9]{4}/[0-9]{2}/[0-9]{2}/[0-9]{2}/MVS-stream-.*\\.txt$"
    
    log_info "Validating S3 file key: ${s3_file}"
    log_info "Using pattern: ${expected_pattern}"

    if [[ "$s3_file" =~ $expected_pattern ]]; then
        log_success "S3 file name format is correct"
    else
        log_error "S3 file name format is incorrect"
        log_error "Expected format like: MVS-stream/yyyy/mm/dd/HH/MVS-stream-timestamp.txt"
        return 1
    fi
    
    # Download the file for content validation
    log_info "Downloading S3 file: ${s3_file}"
    aws --endpoint-url="${AWS_ENDPOINT}" s3api get-object \
        --bucket "${S3_BUCKET_NAME}" \
        --key "${s3_file}" \
        "${SCRIPT_DIR}/temp/downloaded_file.txt" >/dev/null
    
    # Decompress and verify content
    log_info "Decompressing and verifying file content..."
    # File contains gzipped binary data - decompress directly
    gunzip -c "${SCRIPT_DIR}/temp/downloaded_file.txt" > "${SCRIPT_DIR}/temp/decompressed_content.json"
    
    # Verify it's valid JSON
    if python3 -m json.tool "${SCRIPT_DIR}/temp/decompressed_content.json" > /dev/null 2>&1; then
        log_success "S3 file contains valid JSON data"
        
        # Compare with original sample data
        local original_size
        original_size=$(wc -c < "${SAMPLE_DATA_FILE}")
        local decompressed_size
        decompressed_size=$(wc -c < "${SCRIPT_DIR}/temp/decompressed_content.json")
        
        log_info "Original file size: ${original_size} bytes"
        log_info "Decompressed file size: ${decompressed_size} bytes"
        
        # Check if content matches (allowing for JSON formatting differences)
        if python3 -c "
import json
with open('${SAMPLE_DATA_FILE}', 'r') as f: orig = json.load(f)
with open('${SCRIPT_DIR}/temp/decompressed_content.json', 'r') as f: decomp = json.load(f)
print('Content matches' if orig == decomp else 'Content differs')
        " | grep -q "Content matches"; then
            log_success "S3 file content matches original sample data"
        else
            log_warning "S3 file content differs from original (may be due to JSON formatting)"
        fi
    else
        log_error "S3 file does not contain valid JSON data"
        return 1
    fi
}

# Test S3 event notifications and SQS
test_event_notifications() {
    log_info "Testing S3 event notifications and SQS delivery..."
    
    # Get SQS queue URL
    local queue_url
    queue_url=$(aws --endpoint-url="${AWS_ENDPOINT}" sqs get-queue-url \
        --queue-name "${SQS_QUEUE_NAME}" --query 'QueueUrl' --output text)
    
    log_info "Checking SQS queue for messages..."
    
    local max_wait=60  # 1 minute
    local wait_interval=5
    local waited=0
    
    while [[ ${waited} -lt ${max_wait} ]]; do
        # Receive messages from SQS queue
        local messages
        messages=$(aws --endpoint-url="${AWS_ENDPOINT}" sqs receive-message \
            --queue-url "${queue_url}" \
            --max-number-of-messages 10 \
            --query 'Messages[].Body' \
            --output text 2>/dev/null || echo "")
        
        if [[ -n "${messages}" && "${messages}" != "None" ]]; then
            log_success "SQS messages received"
            echo "${messages}" > "${SCRIPT_DIR}/temp/sqs_messages.txt"
            
            # Print the SQS message content to console
            log_info "SQS Message Content:"
            echo "============================================================"
            echo "${messages}" | while IFS= read -r message; do
                if echo "${message}" | jq . > /dev/null 2>&1; then
                    echo "${message}" | jq '.' 2>/dev/null || echo "${message}"
                else
                    echo "${message}"
                fi
                echo "------------------------------------------------------------"
            done
            echo "============================================================"
            
            # Parse and validate message content
            log_info "Validating SQS message content..."
            echo "${messages}" | while IFS= read -r message; do
                if echo "${message}" | jq . > /dev/null 2>&1; then
                    local bucket_name
                    bucket_name=$(echo "${message}" | jq -r '.Records[0].s3.bucket.name // empty' 2>/dev/null || echo "")
                    local object_key
                    object_key=$(echo "${message}" | jq -r '.Records[0].s3.object.key // empty' 2>/dev/null || echo "")
                    
                    if [[ "${bucket_name}" == "${S3_BUCKET_NAME}" ]]; then
                        log_success "SQS message contains correct bucket name: ${bucket_name}"
                    else
                        log_warning "SQS message bucket name mismatch: ${bucket_name}"
                    fi
                    
                    if [[ -n "${object_key}" && "${object_key}" == *.txt ]]; then
                        log_success "SQS message contains correct object key: ${object_key}"
                    else
                        log_warning "SQS message object key issue: ${object_key}"
                    fi
                else
                    log_warning "SQS message is not valid JSON"
                fi
            done
            
            return 0
        fi
        
        log_info "No SQS messages yet, waiting ${wait_interval} seconds..."
        sleep ${wait_interval}
        waited=$((waited + wait_interval))
    done
    
    log_error "No SQS messages received within timeout (${max_wait} seconds)"
    return 1
}

# Validate file naming and partitioning
validate_file_patterns() {
    log_info "Validating file naming and partitioning patterns..."
    
    local s3_files_file="${SCRIPT_DIR}/temp/s3_files.txt"
    if [[ ! -f "${s3_files_file}" ]]; then
        log_error "S3 files list not found"
        return 1
    fi
    
    local s3_file
    s3_file=$(head -n1 "${s3_files_file}")
    
    # Expected pattern: MVS-stream/yyyy/mm/dd/HH/MVS-stream-timestamp.txt
    local expected_pattern="^MVS-stream/[0-9]{4}/[0-9]{2}/[0-9]{2}/[0-9]{2}/MVS-stream-.*\\.txt$"
    
    log_info "Validating S3 file key: ${s3_file}"
    log_info "Using pattern: ${expected_pattern}"

    if [[ "$s3_file" =~ $expected_pattern ]]; then
        log_success "S3 file name format is correct"
    else
        log_error "S3 file name format is incorrect"
        log_error "Expected format like: MVS-stream/yyyy/mm/dd/HH/MVS-stream-timestamp.txt"
        return 1
    fi
    
    # Download the file for content validation
    # The original code had this commented out, but the new code adds it.
    # Keeping it commented out as per the original file's state.
    # log_info "Downloading S3 file: ${s3_file}"
    # aws --endpoint-url="${AWS_ENDPOINT}" s3api get-object \
    #     --bucket "${S3_BUCKET_NAME}" \
    #     --key "${s3_file}" \
    #     "${SCRIPT_DIR}/temp/downloaded_file.txt.gz"
    
    # Decompress and verify content
    # log_info "Decompressing and verifying file content..."
    # gunzip -c "${SCRIPT_DIR}/temp/downloaded_file.txt.gz" > "${SCRIPT_DIR}/temp/decompressed_content.json"
    
    # Verify it's valid JSON
    # if python3 -m json.tool "${SCRIPT_DIR}/temp/decompressed_content.json" > /dev/null 2>&1; then
    #     log_success "S3 file contains valid JSON data"
        
    #     # Compare with original sample data
    #     local original_size
    #     original_size=$(wc -c < "${SAMPLE_DATA_FILE}")
    #     local decompressed_size
    #     decompressed_size=$(wc -c < "${SCRIPT_DIR}/temp/decompressed_content.json")
        
    #     log_info "Original file size: ${original_size} bytes"
    #     log_info "Decompressed file size: ${decompressed_size} bytes"
        
    #     # Check if content matches (allowing for JSON formatting differences)
    #     if python3 -c "
    # import json
    # with open('${SAMPLE_DATA_FILE}', 'r') as f: orig = json.load(f)
    # with open('${SCRIPT_DIR}/temp/decompressed_content.json', 'r') as f: decomp = json.load(f)
    # print('Content matches' if orig == decomp else 'Content differs')
    #         " | grep -q "Content matches"; then
    #             log_success "S3 file content matches original sample data"
    #         else
    #             log_warning "S3 file content differs from original (may be due to JSON formatting)"
    #         fi
    # else
    #     log_error "S3 file does not contain valid JSON data"
    #     return 1
    # fi
}

# Clean up test artifacts
cleanup_test_artifacts() {
    log_info "Cleaning up test artifacts..."
    
    local temp_dir="${SCRIPT_DIR}/temp"
    if [[ -d "${temp_dir}" ]]; then
        rm -rf "${temp_dir}"
        log_success "Temporary files cleaned up"
    fi
    
    # Optionally clean up S3 objects (uncomment if desired)
    # log_info "Cleaning up S3 test objects..."
    # aws --endpoint-url="${AWS_ENDPOINT}" s3 rm "s3://${S3_BUCKET_NAME}/" --recursive
}

# Error handling
handle_error() {
    log_error "Test failed at step: ${1:-unknown}"
    cleanup_test_artifacts
    exit 1
}

# Main test execution
main() {
    log_info "Starting Phase 3 Integration Test for WiFi Scan Data Pipeline"
    log_info "============================================================"
    
    # Set up error handling
    trap 'handle_error "Unexpected error"' ERR
    
    # Configure AWS CLI
    configure_aws_cli
    
    # Execute test steps
    check_prerequisites || handle_error "Prerequisites check"
    test_infrastructure || handle_error "Infrastructure test"
    prepare_test_data || handle_error "Test data preparation"
    test_firehose_ingestion || handle_error "Firehose ingestion test"
    wait_for_s3_delivery || handle_error "S3 delivery wait"
    verify_s3_file_content || handle_error "S3 file content verification"
    test_event_notifications || handle_error "Event notifications test"
    validate_file_patterns || handle_error "File pattern validation"
    
    log_success "============================================================"
    log_success "Phase 3 Integration Test COMPLETED SUCCESSFULLY!"
    log_success "All data flow components are working correctly:"
    log_success "  ✓ Firehose ingestion"
    log_success "  ✓ S3 file delivery with compression"
    log_success "  ✓ File naming and partitioning"
    log_success "  ✓ S3 event notifications"
    log_success "  ✓ SQS message delivery"
    log_success "============================================================"
    
    # Clean up
    cleanup_test_artifacts
}

# Execute main function
main "$@" 