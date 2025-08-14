#!/bin/bash

# wifi-measurements-transformer-service/scripts/test-with-data-file.sh
# Flexible end-to-end test script for WiFi Measurements Transformer Service
# Tests complete flow: S3 upload → SQS event → Service processing → Firehose → Destination S3
# 
# This script reads test data from files in the scripts/test/data directory instead of hardcoded strings
# Usage: ./test-with-data-file.sh [OPTIONS] [DATA_FILE]
#
# Features:
# - Reads test data from specified file or lists available test files
# - Comprehensive test data validation and processing
# - S3 file upload and SQS event simulation
# - Clear source message content display
# - Clear destination records content display
# - BSSID-by-BSSID expected vs actual comparison
# - Meaningful processing success/failure summary
# - Automatic cleanup of S3 files and local test files
# - Optional cleanup skipping with --skip-cleanup flag
# - Optional summary-only mode with --summary-only flag

set -e

echo "🧪 Starting Flexible End-to-End Test for WiFi Measurements Transformer Service..."

# Configuration
LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
SQS_QUEUE_NAME="wifi-scan-events"
S3_BUCKET_NAME="ingested-wifiscan-data"
S3_FIREHOSE_DESTINATION_BUCKET="wifi-measurements-table"
FIREHOSE_DELIVERY_STREAM_NAME="wifi-measurements-stream"
TEST_DATA_DIR="test/data"

# Parse command line arguments
SKIP_CLEANUP=false
SUMMARY_ONLY=false
DATA_FILE=""
LIST_FILES=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-cleanup)
            SKIP_CLEANUP=true
            shift
            ;;
        --summary-only)
            SUMMARY_ONLY=true
            shift
            ;;
        --list-files|-l)
            LIST_FILES=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS] [DATA_FILE]"
            echo ""
            echo "Options:"
            echo "  --skip-cleanup    Skip cleanup of S3 files and local test files"
            echo "  --summary-only    Show only processing summary (skip verbose content)"
            echo "  --list-files, -l  List available test data files and exit"
            echo "  --help, -h        Show this help message"
            echo ""
            echo "Arguments:"
            echo "  DATA_FILE         Path to test data file (relative to $TEST_DATA_DIR)"
            echo "                    If not specified, will use the first available .json file"
            echo ""
            echo "Examples:"
            echo "  $0                                    # Use first available test file"
            echo "  $0 sample-wifi-scan.json             # Use specific test file"
            echo "  $0 --list-files                      # List available test files"
            echo "  $0 --skip-cleanup multi-location-scan.json  # Use file and skip cleanup"
            exit 0
            ;;
        *)
            if [ -z "$DATA_FILE" ]; then
                DATA_FILE="$1"
            else
                echo "Error: Multiple data files specified. Use --help for usage information."
                exit 1
            fi
            shift
            ;;
    esac
done

# Test configuration
TEST_FILE_PREFIX="test-stream"
TEST_TIMESTAMP=$(date +"%Y/%m/%d/%H")
TEST_FILE_NAME="${TEST_FILE_PREFIX}-$(date +"%Y-%m-%d-%H-%M-%S")-$(uuidgen | cut -d'-' -f1).txt"
S3_KEY="${TEST_FILE_PREFIX}/${TEST_TIMESTAMP}/${TEST_FILE_NAME}"

# AWS CLI configuration for LocalStack
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="$AWS_REGION"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to list available test data files
list_test_files() {
    print_status $BLUE "📁 Available test data files in $TEST_DATA_DIR:"
    echo ""
    
    if [ ! -d "$TEST_DATA_DIR" ]; then
        print_status $RED "❌ Test data directory not found: $TEST_DATA_DIR"
        exit 1
    fi
    
    local files=($(find "$TEST_DATA_DIR" -name "*.json" -type f | sort))
    
    if [ ${#files[@]} -eq 0 ]; then
        print_status $YELLOW "⚠️  No test data files found in $TEST_DATA_DIR"
        echo "   Create some .json files with WiFi scan data to get started."
        exit 1
    fi
    
    for file in "${files[@]}"; do
        local filename=$(basename "$file")
        local size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "unknown")
        local modified=$(stat -f%Sm "$file" 2>/dev/null || stat -c%y "$file" 2>/dev/null || echo "unknown")
        
        echo "  📄 $filename"
        echo "     Size: $size bytes, Modified: $modified"
        
        # Show a brief preview of the file content
        if command -v jq >/dev/null 2>&1; then
            local bssid_count=$(jq -r '.wifiConnectedEvents[].wifiConnectedInfo.bssid, .scanResults[].results[].bssid' "$file" 2>/dev/null | grep -v "null" | sort | uniq | wc -l | tr -d ' ')
            local connected_count=$(jq '.wifiConnectedEvents | length' "$file" 2>/dev/null || echo "0")
            local scan_count=$(jq '[.scanResults[].results[]] | length' "$file" 2>/dev/null || echo "0")
            
            echo "     BSSIDs: $bssid_count, Connected: $connected_count, Scan: $scan_count"
        fi
        echo ""
    done
    
    print_status $GREEN "✅ Found ${#files[@]} test data file(s)"
    echo ""
    echo "Usage examples:"
    echo "  $0                                    # Use first available file"
    echo "  $0 sample-wifi-scan.json             # Use specific file"
    echo "  $0 --skip-cleanup multi-location-scan.json  # Use file and skip cleanup"
}

# Function to select test data file
select_test_data_file() {
    if [ "$LIST_FILES" = true ]; then
        list_test_files
        exit 0
    fi
    
    if [ -z "$DATA_FILE" ]; then
        # Find first available .json file
        local first_file=$(find "$TEST_DATA_DIR" -name "*.json" -type f | head -1)
        if [ -z "$first_file" ]; then
            print_status $RED "❌ No test data files found in $TEST_DATA_DIR"
            echo "   Use --list-files to see available files or create some .json files."
            exit 1
        fi
        DATA_FILE=$(basename "$first_file")
        print_status $YELLOW "ℹ️  No data file specified, using: $DATA_FILE"
    fi
    
    # Construct full path
    local full_path="$TEST_DATA_DIR/$DATA_FILE"
    
    if [ ! -f "$full_path" ]; then
        print_status $RED "❌ Test data file not found: $full_path"
        echo ""
        print_status $BLUE "Available files:"
        list_test_files
        exit 1
    fi
    
    print_status $GREEN "✅ Using test data file: $DATA_FILE"
    echo "   Full path: $full_path"
    
    # Validate JSON format
    if ! jq empty "$full_path" 2>/dev/null; then
        print_status $RED "❌ Invalid JSON format in file: $DATA_FILE"
        exit 1
    fi
    
    # Validate required fields
    local required_fields=("wifiConnectedEvents" "scanResults")
    for field in "${required_fields[@]}"; do
        if ! jq -e ".$field" "$full_path" >/dev/null 2>&1; then
            print_status $RED "❌ Missing required field '$field' in file: $DATA_FILE"
            exit 1
        fi
    done
    
    print_status $GREEN "✅ Test data file validation passed"
}

# Function to check if LocalStack is running
check_localstack() {
    print_status $BLUE "🔍 Checking if LocalStack is running..."
    if ! curl -s $LOCALSTACK_ENDPOINT/health > /dev/null; then
        print_status $RED "❌ LocalStack is not running. Please start LocalStack first:"
        echo "   docker run --rm -it -p 4566:4566 -p 4510-4559:4510-4559 localstack/localstack"
        exit 1
    fi
    print_status $GREEN "✅ LocalStack is running"
}

# Function to prepare test data from file
prepare_test_data() {
    print_status $BLUE "🔧 Preparing test data from file: $DATA_FILE..."
    
    local source_file="$TEST_DATA_DIR/$DATA_FILE"
    
    # Copy source file to temp location for processing
    cp "$source_file" /tmp/test-wifi-scan.json
    
    # Compress and encode the test data
    gzip -c /tmp/test-wifi-scan.json | base64 -w 0 > /tmp/test-encoded.txt
    
    print_status $GREEN "✅ Test data prepared from: $DATA_FILE"
}

# Function to upload test data to S3
upload_test_data() {
    print_status $BLUE "📤 Uploading test data to S3..."
    
    # Upload to S3
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 cp /tmp/test-encoded.txt \
        s3://$S3_BUCKET_NAME/$S3_KEY
    
    print_status $GREEN "✅ Test data uploaded to s3://$S3_BUCKET_NAME/$S3_KEY"
}

# Function to create and send S3 event to SQS
send_s3_event_to_sqs() {
    print_status $BLUE "📨 Creating and sending S3 event to SQS..."
    
    # Get SQS queue URL
    QUEUE_URL=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url \
        --queue-name $SQS_QUEUE_NAME --query 'QueueUrl' --output text)
    
    # Create S3 event message (S3 Event Notification format)
    cat > /tmp/s3-event.json << EOF
{
  "Records": [
    {
      "eventVersion": "2.1",
      "eventSource": "aws:s3",
      "awsRegion": "$AWS_REGION",
      "eventTime": "$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")",
      "eventName": "ObjectCreated:Put",
      "userIdentity": {
        "principalId": "AWS:AROA4QWKES4Y24IUPAV2J:AWSFirehoseToS3"
      },
      "requestParameters": {
        "sourceIPAddress": "127.0.0.1"
      },
      "responseElements": {
        "x-amz-request-id": "$(uuidgen | cut -d'-' -f1)",
        "x-amz-id-2": "2BdlIpJXKQCEI7siGhF3KCU9M59dye7AJcn63aIjkANLeVX+9EFIJ7qzipO/g3RJFVIK5E7a20PqWDccojmXUmLJHK00bHFvRHDhbb9LMnw="
      },
      "s3": {
        "s3SchemaVersion": "1.0",
        "configurationId": "NjgyMTJiZTUtNDMwZC00OTVjLWIzOWEtM2UzZWM3MzYwNGE2",
        "bucket": {
          "name": "$S3_BUCKET_NAME",
          "ownerIdentity": {
            "principalId": "A3LJZCR20GC5IX"
          },
          "arn": "arn:aws:s3:::$S3_BUCKET_NAME"
        },
        "object": {
          "key": "$S3_KEY",
          "size": $(stat -f%z /tmp/test-encoded.txt 2>/dev/null || stat -c%s /tmp/test-encoded.txt 2>/dev/null || echo 0),
          "eTag": "$(md5 -q /tmp/test-encoded.txt 2>/dev/null || md5sum /tmp/test-encoded.txt 2>/dev/null | cut -d' ' -f1 || echo 'unknown')",
          "sequencer": "0062E99A88DC407460"
        }
      }
    }
  ]
}
EOF
    
    # Send message to SQS
    aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs send-message \
        --queue-url "$QUEUE_URL" \
        --message-body "$(cat /tmp/s3-event.json)"
    
    print_status $GREEN "✅ S3 event sent to SQS queue"
}

# Function to wait for processing
wait_for_processing() {
    print_status $YELLOW "⏳ Waiting for service processing (30 seconds)..."
    sleep 30
    print_status $GREEN "✅ Processing wait completed"
}

# Function to check Firehose destination bucket
check_firehose_destination() {
    print_status $BLUE "🔍 Checking Firehose destination bucket..."
    
    # List objects in destination bucket
    print_status $BLUE "📋 Objects in destination bucket:"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_FIREHOSE_DESTINATION_BUCKET/ --recursive || echo "No objects found"
    
    # Find the most recent file
    LATEST_FILE=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_FIREHOSE_DESTINATION_BUCKET/ --recursive | tail -1 | awk '{print $4}')
    
    if [ -z "$LATEST_FILE" ]; then
        print_status $RED "❌ No files found in destination bucket"
        return 1
    fi
    
    print_status $GREEN "✅ Found destination file: $LATEST_FILE"
    
    # Download and examine the file
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 cp s3://$S3_FIREHOSE_DESTINATION_BUCKET/$LATEST_FILE /tmp/firehose-output.gz
    
    # Decompress if it's gzipped
    if file /tmp/firehose-output.gz | grep -q "gzip"; then
        gunzip -c /tmp/firehose-output.gz > /tmp/firehose-output.json
    else
        cp /tmp/firehose-output.gz /tmp/firehose-output.json
    fi
    
    print_status $GREEN "✅ Downloaded and decompressed destination file"
}

# Function to show source message content
show_source_message_content() {
    print_status $BLUE "📤 SOURCE MESSAGE CONTENT (from $DATA_FILE)"
    echo "=========================================="
    
    print_status $BLUE "Raw WiFi Scan Data (from test file):"
    cat /tmp/test-wifi-scan.json | jq '.'
    
    echo ""
    print_status $BLUE "Expected Record Counts from Source:"
    
    # Count expected CONNECTED records
    EXPECTED_CONNECTED=$(cat /tmp/test-wifi-scan.json | jq '.wifiConnectedEvents | length')
    echo "  CONNECTED Events: $EXPECTED_CONNECTED"
    
    # Count expected SCAN records  
    EXPECTED_SCAN=$(cat /tmp/test-wifi-scan.json | jq '[.scanResults[].results[]] | length')
    echo "  SCAN Results: $EXPECTED_SCAN"
    
    EXPECTED_TOTAL=$((EXPECTED_CONNECTED + EXPECTED_SCAN))
    echo "  Expected Total Records: $EXPECTED_TOTAL"
    
    echo ""
    print_status $BLUE "Expected BSSIDs:"
    echo "  CONNECTED BSSIDs:"
    cat /tmp/test-wifi-scan.json | jq -r '.wifiConnectedEvents[].wifiConnectedInfo.bssid' | sed 's/^/    /'
    echo "  SCAN BSSIDs:"
    cat /tmp/test-wifi-scan.json | jq -r '.scanResults[].results[].bssid' | sed 's/^/    /'
    
    echo ""
    print_status $GREEN "✅ Source message content displayed"
}

# Function to show destination records content
show_destination_records_content() {
    print_status $BLUE "📥 DESTINATION RECORDS CONTENT"
    echo "=========================================="
    
    if [ ! -f /tmp/firehose-output.json ]; then
        print_status $RED "❌ No destination records found!"
        return 1
    fi
    
    print_status $BLUE "Processed Records (from Firehose S3 destination):"
    cat /tmp/firehose-output.json | jq '.'
    
    echo ""
    print_status $GREEN "✅ Destination records content displayed"
}

# Function to provide clear processing summary
provide_processing_summary() {
    print_status $BLUE "📊 PROCESSING SUMMARY (Test File: $DATA_FILE)"
    echo "=========================================="
    
    # Get source data expectations
    EXPECTED_CONNECTED=$(cat /tmp/test-wifi-scan.json | jq '.wifiConnectedEvents | length')
    EXPECTED_SCAN=$(cat /tmp/test-wifi-scan.json | jq '[.scanResults[].results[]] | length')
    EXPECTED_TOTAL=$((EXPECTED_CONNECTED + EXPECTED_SCAN))
    
    # Get actual results
    ACTUAL_TOTAL=$(cat /tmp/firehose-output.json | jq -s 'length')
    ACTUAL_CONNECTED=$(cat /tmp/firehose-output.json | jq -s '[.[] | select(.connection_status == "CONNECTED")] | length')
    ACTUAL_SCAN=$(cat /tmp/firehose-output.json | jq -s '[.[] | select(.connection_status == "SCAN")] | length')
    
    print_status $BLUE "📈 RECORD COUNT COMPARISON:"
    echo "  Expected vs Actual:"
    printf "    %-15s %-10s %-10s %-10s\n" "Type" "Expected" "Actual" "Status"
    printf "    %-15s %-10s %-10s %-10s\n" "===============" "========" "======" "======"
    
    # CONNECTED records comparison
    if [ "$EXPECTED_CONNECTED" -eq "$ACTUAL_CONNECTED" ]; then
        printf "    %-15s %-10s %-10s %-10s\n" "CONNECTED" "$EXPECTED_CONNECTED" "$ACTUAL_CONNECTED" "✅ PASS"
    else
        printf "    %-15s %-10s %-10s %-10s\n" "CONNECTED" "$EXPECTED_CONNECTED" "$ACTUAL_CONNECTED" "❌ FAIL"
    fi
    
    # SCAN records comparison
    if [ "$EXPECTED_SCAN" -eq "$ACTUAL_SCAN" ]; then
        printf "    %-15s %-10s %-10s %-10s\n" "SCAN" "$EXPECTED_SCAN" "$ACTUAL_SCAN" "✅ PASS"
    else
        printf "    %-15s %-10s %-10s %-10s\n" "SCAN" "$EXPECTED_SCAN" "$ACTUAL_SCAN" "❌ FAIL"
    fi
    
    # Total records comparison
    if [ "$EXPECTED_TOTAL" -eq "$ACTUAL_TOTAL" ]; then
        printf "    %-15s %-10s %-10s %-10s\n" "TOTAL" "$EXPECTED_TOTAL" "$ACTUAL_TOTAL" "✅ PASS"
    else
        printf "    %-15s %-10s %-10s %-10s\n" "TOTAL" "$EXPECTED_TOTAL" "$ACTUAL_TOTAL" "❌ FAIL"
    fi
    
    echo ""
    print_status $BLUE "📋 BSSID-BY-BSSID COMPARISON:"
    
    # Get all expected BSSIDs with their expected connection status
    EXPECTED_CONNECTED_BSSIDS=$(cat /tmp/test-wifi-scan.json | jq -r '.wifiConnectedEvents[].wifiConnectedInfo.bssid')
    EXPECTED_SCAN_BSSIDS=$(cat /tmp/test-wifi-scan.json | jq -r '.scanResults[].results[].bssid')
    
    printf "    %-20s %-15s %-15s %-10s\n" "BSSID" "Expected Type" "Actual Type" "Status"
    printf "    %-20s %-15s %-15s %-10s\n" "===================" "=============" "===========" "======"
    
    # Check CONNECTED BSSIDs
    for bssid in $EXPECTED_CONNECTED_BSSIDS; do
        ACTUAL_TYPE=$(cat /tmp/firehose-output.json | jq -s --arg bssid "$bssid" '[.[] | select(.bssid == $bssid) | .connection_status] | .[0] // "MISSING"' | tr -d '"')
        if [ "$ACTUAL_TYPE" = "CONNECTED" ]; then
            printf "    %-20s %-15s %-15s %-10s\n" "$bssid" "CONNECTED" "$ACTUAL_TYPE" "✅ PASS"
        else
            printf "    %-20s %-15s %-15s %-10s\n" "$bssid" "CONNECTED" "$ACTUAL_TYPE" "❌ FAIL"
        fi
    done
    
    # Check SCAN BSSIDs (only unique ones not already processed as CONNECTED)
    for bssid in $EXPECTED_SCAN_BSSIDS; do
        # Skip if this BSSID was already processed as CONNECTED
        IS_CONNECTED=$(echo "$EXPECTED_CONNECTED_BSSIDS" | grep -q "^$bssid$" && echo "yes" || echo "no")
        if [ "$IS_CONNECTED" = "no" ]; then
            ACTUAL_TYPE=$(cat /tmp/firehose-output.json | jq -s --arg bssid "$bssid" '[.[] | select(.bssid == $bssid) | .connection_status] | .[0] // "MISSING"' | tr -d '"')
            if [ "$ACTUAL_TYPE" = "SCAN" ]; then
                printf "    %-20s %-15s %-15s %-10s\n" "$bssid" "SCAN" "$ACTUAL_TYPE" "✅ PASS"
            else
                printf "    %-20s %-15s %-15s %-10s\n" "$bssid" "SCAN" "$ACTUAL_TYPE" "❌ FAIL"
            fi
        fi
    done
    
    echo ""
    print_status $BLUE "🎯 FINAL TEST RESULT:"
    
    # Calculate success rate
    SUCCESS_COUNT=0
    TOTAL_CHECKS=0
    
    # Check record counts
    [ "$EXPECTED_CONNECTED" -eq "$ACTUAL_CONNECTED" ] && SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    [ "$EXPECTED_SCAN" -eq "$ACTUAL_SCAN" ] && SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    [ "$EXPECTED_TOTAL" -eq "$ACTUAL_TOTAL" ] && SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    TOTAL_CHECKS=$((TOTAL_CHECKS + 3))
    
    # Check BSSID processing
    BSSID_SUCCESS=0
    BSSID_TOTAL=0
    
    # Check CONNECTED BSSIDs
    for bssid in $EXPECTED_CONNECTED_BSSIDS; do
        ACTUAL_TYPE=$(cat /tmp/firehose-output.json | jq -s --arg bssid "$bssid" '[.[] | select(.bssid == $bssid) | .connection_status] | .[0] // "MISSING"' | tr -d '"')
        [ "$ACTUAL_TYPE" = "CONNECTED" ] && BSSID_SUCCESS=$((BSSID_SUCCESS + 1))
        BSSID_TOTAL=$((BSSID_TOTAL + 1))
    done
    
    # Check SCAN BSSIDs (unique ones only)
    for bssid in $EXPECTED_SCAN_BSSIDS; do
        IS_CONNECTED=$(echo "$EXPECTED_CONNECTED_BSSIDS" | grep -q "^$bssid$" && echo "yes" || echo "no")
        if [ "$IS_CONNECTED" = "no" ]; then
            ACTUAL_TYPE=$(cat /tmp/firehose-output.json | jq -s --arg bssid "$bssid" '[.[] | select(.bssid == $bssid) | .connection_status] | .[0] // "MISSING"' | tr -d '"')
            [ "$ACTUAL_TYPE" = "SCAN" ] && BSSID_SUCCESS=$((BSSID_SUCCESS + 1))
            BSSID_TOTAL=$((BSSID_TOTAL + 1))
        fi
    done
    
    SUCCESS_COUNT=$((SUCCESS_COUNT + BSSID_SUCCESS))
    TOTAL_CHECKS=$((TOTAL_CHECKS + BSSID_TOTAL))
    
    SUCCESS_RATE=$(echo "scale=1; $SUCCESS_COUNT * 100 / $TOTAL_CHECKS" | bc -l 2>/dev/null || echo "100")
    
    if [ "$SUCCESS_COUNT" -eq "$TOTAL_CHECKS" ]; then
        print_status $GREEN "✅ ALL TESTS PASSED: $SUCCESS_COUNT/$TOTAL_CHECKS (100%) - Processing was SUCCESSFUL!"
    else
        print_status $RED "❌ SOME TESTS FAILED: $SUCCESS_COUNT/$TOTAL_CHECKS ($SUCCESS_RATE%) - Processing had ISSUES!"
    fi
    
    echo ""
    print_status $GREEN "✅ Processing summary completed"
}

# Function to validate schema and data
validate_schema_and_data() {
    print_status $BLUE "🔍 Validating schema and data..."
    
    # Check required fields (JSON Lines format - check first record)
    REQUIRED_FIELDS=("bssid" "measurement_timestamp" "event_id" "device_id" "latitude" "longitude" "connection_status" "quality_weight")
    
    for field in "${REQUIRED_FIELDS[@]}"; do
        if ! cat /tmp/firehose-output.json | head -1 | jq -e ".$field" > /dev/null 2>&1; then
            print_status $RED "❌ Missing required field: $field"
            return 1
        fi
    done
    
    # Check BSSID format (MAC address) - check first record
    if ! cat /tmp/firehose-output.json | head -1 | jq -r '.bssid' | grep -E "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$" > /dev/null; then
        print_status $RED "❌ Invalid BSSID format"
        return 1
    fi
    
    # Check latitude/longitude ranges - check first record
    LATITUDE=$(cat /tmp/firehose-output.json | head -1 | jq -r '.latitude')
    LONGITUDE=$(cat /tmp/firehose-output.json | head -1 | jq -r '.longitude')
    
    # Simple numeric validation
    if ! echo "$LATITUDE" | grep -E '^-?[0-9]+\.?[0-9]*$' > /dev/null; then
        print_status $RED "❌ Invalid latitude format: $LATITUDE"
        return 1
    fi
    
    if ! echo "$LONGITUDE" | grep -E '^-?[0-9]+\.?[0-9]*$' > /dev/null; then
        print_status $RED "❌ Invalid longitude format: $LONGITUDE"
        return 1
    fi
    
    # Check quality weights
    CONNECTED_QUALITY=$(cat /tmp/firehose-output.json | jq -r 'select(.connection_status == "CONNECTED") | .quality_weight' | sort | uniq | head -1)
    SCAN_QUALITY=$(cat /tmp/firehose-output.json | jq -r 'select(.connection_status == "SCAN") | .quality_weight' | sort | uniq | head -1)
    
    # Validate quality weights are correct
    if [ -n "$CONNECTED_QUALITY" ] && [ "$CONNECTED_QUALITY" != "2" ] && [ "$CONNECTED_QUALITY" != "2.0" ]; then
        print_status $RED "❌ CONNECTED quality weight should be 2.0, got: $CONNECTED_QUALITY"
        return 1
    fi
    
    if [ -n "$SCAN_QUALITY" ] && [ "$SCAN_QUALITY" != "1" ] && [ "$SCAN_QUALITY" != "1.0" ]; then
        print_status $RED "❌ SCAN quality weight should be 1.0, got: $SCAN_QUALITY"
        return 1
    fi
    
    print_status $GREEN "✅ Schema and data validation completed successfully"
}

# Function to display test summary
display_test_summary() {
    echo ""
    print_status $GREEN "🎉 End-to-End Test Summary"
    echo ""
    echo "📋 Test Configuration:"
    echo "  Test Data File: $DATA_FILE"
    echo "  LocalStack Endpoint: $LOCALSTACK_ENDPOINT"
    echo "  S3 Source Bucket: s3://$S3_BUCKET_NAME"
    echo "  S3 Destination Bucket: s3://$S3_FIREHOSE_DESTINATION_BUCKET"
    echo "  SQS Queue: $SQS_QUEUE_NAME"
    echo "  Firehose Stream: $FIREHOSE_DELIVERY_STREAM_NAME"
    echo ""
    echo "📤 Test Data:"
    echo "  Source File: $S3_KEY"
    echo "  Original BSSIDs: $(cat /tmp/test-wifi-scan.json | jq -r '.wifiConnectedEvents[].wifiConnectedInfo.bssid, .scanResults[].results[].bssid' | sort | uniq | wc -l | tr -d ' ')"
    echo ""
    echo "📥 Processing Results:"
    echo "  Destination File: $(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_FIREHOSE_DESTINATION_BUCKET/ --recursive | tail -1 | awk '{print $4}' 2>/dev/null || echo 'Not found')"
    echo "  Total Records: $(cat /tmp/firehose-output.json | jq '. | length' 2>/dev/null || echo '0')"
    echo ""
    if [ "$SKIP_CLEANUP" = true ]; then
        echo "🧹 Cleanup: Skipped (--skip-cleanup flag provided)"
        echo "  Test files and S3 files preserved for inspection"
    else
        echo "🧹 Cleanup: Will be performed after summary"
    fi
    echo ""
    echo "✅ Test completed successfully!"
}

# Function to cleanup S3 files
cleanup_s3_files() {
    print_status $BLUE "🧹 Cleaning up S3 files..."
    
    # Clean up source bucket files
    print_status $BLUE "📤 Cleaning up source bucket files..."
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_BUCKET_NAME/$S3_KEY >/dev/null 2>&1; then
        aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rm s3://$S3_BUCKET_NAME/$S3_KEY
        print_status $GREEN "✅ Source bucket file cleaned up: $S3_KEY"
    else
        print_status $YELLOW "ℹ️  Source bucket file not found: $S3_KEY"
    fi
    
    # Clean up destination bucket files (all files with test prefix)
    print_status $BLUE "📥 Cleaning up destination bucket files..."
    DESTINATION_FILES=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_FIREHOSE_DESTINATION_BUCKET/ --recursive 2>/dev/null | grep "$TEST_FILE_PREFIX" | awk '{print $4}' || true)
    
    if [ -n "$DESTINATION_FILES" ]; then
        CLEANUP_COUNT=0
        echo "$DESTINATION_FILES" | while read -r file; do
            if [ -n "$file" ]; then
                if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rm s3://$S3_FIREHOSE_DESTINATION_BUCKET/$file >/dev/null 2>&1; then
                    print_status $GREEN "✅ Destination bucket file cleaned up: $file"
                    CLEANUP_COUNT=$((CLEANUP_COUNT + 1))
                else
                    print_status $YELLOW "⚠️  Failed to clean up destination file: $file"
                fi
            fi
        done
        if [ $CLEANUP_COUNT -gt 0 ]; then
            print_status $GREEN "✅ Cleaned up $CLEANUP_COUNT destination files"
        fi
    else
        print_status $YELLOW "ℹ️  No destination files found with test prefix to clean up"
    fi
    
    print_status $GREEN "✅ S3 cleanup completed"
}

# Function to cleanup test files
cleanup_test_files() {
    print_status $BLUE "🧹 Cleaning up test files..."
    rm -f /tmp/test-wifi-scan.json
    rm -f /tmp/test-encoded.txt
    rm -f /tmp/s3-event.json
    rm -f /tmp/firehose-output.gz
    rm -f /tmp/firehose-output.json
    print_status $GREEN "✅ Test files cleaned up"
}

# Main execution
main() {
    select_test_data_file
    check_localstack
    prepare_test_data
    upload_test_data
    send_s3_event_to_sqs
    wait_for_processing
    check_firehose_destination
    validate_schema_and_data
    
    # Show analysis based on verbosity preference
    if [ "$SUMMARY_ONLY" = false ]; then
        show_source_message_content
        show_destination_records_content
    fi
    
    provide_processing_summary
    display_test_summary
    
    if [ "$SKIP_CLEANUP" = false ]; then
        cleanup_s3_files
        cleanup_test_files
    else
        print_status $YELLOW "⏭️  Skipping cleanup (--skip-cleanup flag provided)"
        print_status $YELLOW "📁 Test files and S3 files will be preserved for inspection"
    fi
}

# Execute main function
main "$@"
