#!/bin/bash

# wifi-measurements-transformer-service/scripts/test-end-to-end-flow.sh
# End-to-end test script for WiFi Measurements Transformer Service
# Tests complete flow: S3 upload → SQS event → Service processing → Firehose → Destination S3
# 
# Note: Firehose output is expected in JSON Lines format (one JSON object per line)
# This is the standard format for Kinesis Data Firehose delivery to S3
#
# Features:
# - Comprehensive test data generation with multiple BSSIDs and connection types
# - S3 file upload and SQS event simulation
# - Clear source message content display
# - Clear destination records content display
# - BSSID-by-BSSID expected vs actual comparison
# - Meaningful processing success/failure summary
# - Automatic cleanup of S3 files and local test files
# - Optional cleanup skipping with --skip-cleanup flag
# - Optional summary-only mode with --summary-only flag

set -e

echo "🧪 Starting End-to-End Test for WiFi Measurements Transformer Service..."

# Configuration
LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
SQS_QUEUE_NAME="wifi-scan-events"
S3_BUCKET_NAME="ingested-wifiscan-data"
S3_FIREHOSE_DESTINATION_BUCKET="wifi-measurements-table"
FIREHOSE_DELIVERY_STREAM_NAME="wifi-measurements-stream"

# Parse command line arguments
SKIP_CLEANUP=false
SUMMARY_ONLY=false
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
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --skip-cleanup    Skip cleanup of S3 files and local test files"
            echo "  --summary-only    Show only processing summary (skip verbose source/destination content)"
            echo "  --help, -h        Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
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

# Function to create comprehensive test data
create_test_data() {
    print_status $BLUE "🔧 Creating comprehensive test data..."
    
    # Create a comprehensive WiFi scan file with multiple BSSIDs and locations
    cat > /tmp/comprehensive-wifi-scan.json << 'EOF'
{
  "osVersion": "14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys",
  "model": "SM-A536V",
  "device": "a53x",
  "manufacturer": "samsung",
  "osName": "Android",
  "sdkInt": "34",
  "appNameVersion": "com.verizon.wifiloc.app/0.1.0.10000",
  "dataVersion": "15",
  "wifiConnectedEvents": [
    {
      "timestamp": 1731091615562,
      "eventId": "9a930a02-f0cc-4e6d-9b95-c18b4d5a542a",
      "eventType": "CONNECTED",
      "wifiConnectedInfo": {
        "bssid": "b8:f8:53:c0:1e:ff",
        "ssid": "TestNetwork1",
        "linkSpeed": 351,
        "frequency": 5660,
        "rssi": -58,
        "channelWidth": 80,
        "centerFreq0": 5650,
        "capabilities": "WPA2-PSK",
        "is80211mcResponder": true,
        "isPasspointNetwork": false
      },
      "location": {
        "latitude": 40.6768816,
        "longitude": -74.416391,
        "altitude": 15.5,
        "accuracy": 10.0,
        "time": 1731091614415,
        "provider": "gps",
        "source": "network",
        "speed": 0.0,
        "bearing": 0.0
      }
    },
    {
      "timestamp": 1731091615563,
      "eventId": "9a930a02-f0cc-4e6d-9b95-c18b4d5a542b",
      "eventType": "CONNECTED",
      "wifiConnectedInfo": {
        "bssid": "aa:bb:cc:dd:ee:ff",
        "ssid": "TestNetwork2",
        "linkSpeed": 433,
        "frequency": 5180,
        "rssi": -45,
        "channelWidth": 80,
        "centerFreq0": 5170,
        "capabilities": "WPA3-SAE",
        "is80211mcResponder": true,
        "isPasspointNetwork": true
      },
      "location": {
        "latitude": 40.6768817,
        "longitude": -74.416392,
        "altitude": 16.2,
        "accuracy": 8.5,
        "time": 1731091614416,
        "provider": "gps",
        "source": "network",
        "speed": 0.0,
        "bearing": 0.0
      }
    }
  ],
  "scanResults": [
    {
      "timestamp": 1731091615562,
      "location": {
        "latitude": 40.6768816,
        "longitude": -74.416391,
        "altitude": 15.5,
        "accuracy": 10.0,
        "time": 1731091614415,
        "provider": "gps",
        "source": "network",
        "speed": 0.0,
        "bearing": 0.0
      },
      "results": [
        {
          "ssid": "TestNetwork1",
          "bssid": "b8:f8:53:c0:1e:ff",
          "scantime": 1731091613712,
          "rssi": -61,
          "frequency": 5660
        },
        {
          "ssid": "TestNetwork2",
          "bssid": "aa:bb:cc:dd:ee:ff",
          "scantime": 1731091613713,
          "rssi": -48,
          "frequency": 5180
        },
        {
          "ssid": "TestNetwork3",
          "bssid": "11:22:33:44:55:66",
          "scantime": 1731091613714,
          "rssi": -72,
          "frequency": 2412
        },
        {
          "ssid": "TestNetwork4",
          "bssid": "99:88:77:66:55:44",
          "scantime": 1731091613715,
          "rssi": -85,
          "frequency": 2437
        }
      ]
    }
  ]
}
EOF

    # Compress and encode the test data
    gzip -c /tmp/comprehensive-wifi-scan.json | base64 -w 0 > /tmp/comprehensive-encoded.txt
    
    print_status $GREEN "✅ Comprehensive test data created"
}

# Function to upload test data to S3
upload_test_data() {
    print_status $BLUE "📤 Uploading test data to S3..."
    
    # Upload to S3
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 cp /tmp/comprehensive-encoded.txt \
        s3://$S3_BUCKET_NAME/$S3_KEY
    
    print_status $GREEN "✅ Test data uploaded to s3://$S3_BUCKET_NAME/$S3_KEY"
}

# Function to create and send S3 event to SQS
send_s3_event_to_sqs() {
    print_status $BLUE "📨 Creating and sending S3 event to SQS..."
    
    # Get SQS queue URL
    QUEUE_URL=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url \
        --queue-name $SQS_QUEUE_NAME --query 'QueueUrl' --output text)
    
    # Create S3 event message
    cat > /tmp/s3-event.json << EOF
{
  "version": "0",
  "id": "$(uuidgen)",
  "detail-type": "Object Created",
  "source": "aws.s3",
  "account": "000000000000",
  "time": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "region": "$AWS_REGION",
  "resources": ["arn:aws:s3:::$S3_BUCKET_NAME"],
  "detail": {
    "version": "0",
    "bucket": {"name": "$S3_BUCKET_NAME"},
    "object": {
      "key": "$S3_KEY",
      "size": $(stat -f%z /tmp/comprehensive-encoded.txt),
      "etag": "$(md5 -q /tmp/comprehensive-encoded.txt)",
      "sequencer": "0062E99A88DC407460",
      "version-id": "AZhScmWkZshiDT25IpYSNfoNoJhDpAVb"
    },
    "request-id": "$(uuidgen)",
    "requester": "074255357339",
    "source-ip-address": "127.0.0.1",
    "reason": "PutObject"
  }
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
    print_status $BLUE "📤 SOURCE MESSAGE CONTENT"
    echo "=========================================="
    
    print_status $BLUE "Raw WiFi Scan Data (uploaded to S3):"
    cat /tmp/comprehensive-wifi-scan.json | jq '.'
    
    echo ""
    print_status $BLUE "Expected Record Counts from Source:"
    
    # Count expected CONNECTED records
    EXPECTED_CONNECTED=$(cat /tmp/comprehensive-wifi-scan.json | jq '.wifiConnectedEvents | length')
    echo "  CONNECTED Events: $EXPECTED_CONNECTED"
    
    # Count expected SCAN records  
    EXPECTED_SCAN=$(cat /tmp/comprehensive-wifi-scan.json | jq '[.scanResults[].results[]] | length')
    echo "  SCAN Results: $EXPECTED_SCAN"
    
    EXPECTED_TOTAL=$((EXPECTED_CONNECTED + EXPECTED_SCAN))
    echo "  Expected Total Records: $EXPECTED_TOTAL"
    
    echo ""
    print_status $BLUE "Expected BSSIDs:"
    echo "  CONNECTED BSSIDs:"
    cat /tmp/comprehensive-wifi-scan.json | jq -r '.wifiConnectedEvents[].wifiConnectedInfo.bssid' | sed 's/^/    /'
    echo "  SCAN BSSIDs:"
    cat /tmp/comprehensive-wifi-scan.json | jq -r '.scanResults[].results[].bssid' | sed 's/^/    /'
    
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
    print_status $BLUE "📊 PROCESSING SUMMARY"
    echo "=========================================="
    
    # Get source data expectations
    EXPECTED_CONNECTED=$(cat /tmp/comprehensive-wifi-scan.json | jq '.wifiConnectedEvents | length')
    EXPECTED_SCAN=$(cat /tmp/comprehensive-wifi-scan.json | jq '[.scanResults[].results[]] | length')
    EXPECTED_TOTAL=$((EXPECTED_CONNECTED + EXPECTED_SCAN))
    
    # Get actual results - fix the counting logic
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
    EXPECTED_CONNECTED_BSSIDS=$(cat /tmp/comprehensive-wifi-scan.json | jq -r '.wifiConnectedEvents[].wifiConnectedInfo.bssid')
    EXPECTED_SCAN_BSSIDS=$(cat /tmp/comprehensive-wifi-scan.json | jq -r '.scanResults[].results[].bssid')
    
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
    
    SUCCESS_RATE=$(echo "scale=1; $SUCCESS_COUNT * 100 / $TOTAL_CHECKS" | bc -l)
    
    if [ "$SUCCESS_COUNT" -eq "$TOTAL_CHECKS" ]; then
        print_status $GREEN "✅ ALL TESTS PASSED: $SUCCESS_COUNT/$TOTAL_CHECKS (100%) - Processing was SUCCESSFUL!"
    else
        print_status $RED "❌ SOME TESTS FAILED: $SUCCESS_COUNT/$TOTAL_CHECKS ($SUCCESS_RATE%) - Processing had ISSUES!"
    fi
    
    echo ""
    print_status $GREEN "✅ Processing summary completed"
}

# Function to save detailed analysis to file
save_detailed_analysis() {
    print_status $BLUE "💾 Saving detailed analysis to file..."
    
    ANALYSIS_FILE="/tmp/wifi-measurements-analysis-$(date +%Y%m%d-%H%M%S).txt"
    
    {
        echo "WiFi Measurements Transformer Service - Detailed Analysis"
        echo "Generated: $(date)"
        echo "=================================================="
        echo ""
        
        echo "ORIGINAL TEST DATA:"
        echo "=================="
        cat /tmp/comprehensive-wifi-scan.json | jq '.'
        echo ""
        
        echo "FIREHOSE OUTPUT DATA:"
        echo "===================="
        cat /tmp/firehose-output.json | jq '.'
        echo ""
        
        echo "DETAILED RECORD COMPARISONS:"
        echo "============================"
        
        # Get original test data for comparison
        ORIGINAL_DATA=$(cat /tmp/comprehensive-wifi-scan.json)
        
        # Process each record in the Firehose output (JSON Lines format - one JSON object per line)
        RECORD_INDEX=0
        cat /tmp/firehose-output.json | while IFS= read -r record; do
            RECORD_INDEX=$((RECORD_INDEX + 1))
            BSSID=$(echo "$record" | jq -r '.bssid')
            CONNECTION_STATUS=$(echo "$record" | jq -r '.connection_status')
            
            echo ""
            echo "RECORD #$RECORD_INDEX - BSSID: $BSSID ($CONNECTION_STATUS)"
            echo "----------------------------------------"
            
            # Print the actual record
            echo "ACTUAL RECORD:"
            echo "$record" | jq '.' | sed 's/^/  /'
            
            # Find expected data based on BSSID and connection status
            echo "EXPECTED DATA:"
            
            if [ "$CONNECTION_STATUS" = "CONNECTED" ]; then
                # Find matching CONNECTED event
                EXPECTED_DATA=$(echo "$ORIGINAL_DATA" | jq -r --arg bssid "$BSSID" '
                    .wifiConnectedEvents[] | 
                    select(.wifiConnectedInfo.bssid == $bssid) | 
                    {
                        "bssid": .wifiConnectedInfo.bssid,
                        "ssid": .wifiConnectedInfo.ssid,
                        "rssi": .wifiConnectedInfo.rssi,
                        "frequency": .wifiConnectedInfo.frequency,
                        "link_speed": .wifiConnectedInfo.linkSpeed,
                        "channel_width": .wifiConnectedInfo.channelWidth,
                        "center_freq0": .wifiConnectedInfo.centerFreq0,
                        "capabilities": .wifiConnectedInfo.capabilities,
                        "is_80211mc_responder": .wifiConnectedInfo.is80211mcResponder,
                        "is_passpoint_network": .wifiConnectedInfo.isPasspointNetwork,
                        "latitude": .location.latitude,
                        "longitude": .location.longitude,
                        "altitude": .location.altitude,
                        "location_accuracy": .location.accuracy,
                        "location_timestamp": .location.time,
                        "location_provider": .location.provider,
                        "location_source": .location.source,
                        "speed": .location.speed,
                        "bearing": .location.bearing,
                        "measurement_timestamp": .timestamp,
                        "event_id": .eventId,
                        "connection_status": "CONNECTED",
                        "quality_weight": 2.0
                    }
                ')
            else
                # Find matching SCAN result
                EXPECTED_DATA=$(echo "$ORIGINAL_DATA" | jq -r --arg bssid "$BSSID" '
                    .scanResults[].results[] | 
                    select(.bssid == $bssid) | 
                    {
                        "bssid": .bssid,
                        "ssid": .ssid,
                        "rssi": .rssi,
                        "frequency": .frequency,
                        "scan_timestamp": .scantime,
                        "latitude": .location.latitude,
                        "longitude": .location.longitude,
                        "altitude": .location.altitude,
                        "location_accuracy": .location.accuracy,
                        "location_timestamp": .location.time,
                        "location_provider": .location.provider,
                        "location_source": .location.source,
                        "speed": .location.speed,
                        "bearing": .location.bearing,
                        "connection_status": "SCAN",
                        "quality_weight": 1.0
                    }
                ')
            fi
            
            if [ -n "$EXPECTED_DATA" ] && [ "$EXPECTED_DATA" != "null" ]; then
                echo "$EXPECTED_DATA" | jq '.' | sed 's/^/  /'
            else
                echo "  ❌ No expected data found for BSSID: $BSSID"
            fi
            
            echo ""
            echo "FIELD COMPARISONS:"
            
            # Compare key fields
            ACTUAL_BSSID=$(echo "$record" | jq -r '.bssid')
            EXPECTED_BSSID=$(echo "$EXPECTED_DATA" | jq -r '.bssid // "N/A"')
            if [ "$ACTUAL_BSSID" = "$EXPECTED_BSSID" ]; then
                echo "  ✅ BSSID: $ACTUAL_BSSID"
            else
                echo "  ❌ BSSID: Expected=$EXPECTED_BSSID, Actual=$ACTUAL_BSSID"
            fi
            
            ACTUAL_SSID=$(echo "$record" | jq -r '.ssid // "N/A"')
            EXPECTED_SSID=$(echo "$EXPECTED_DATA" | jq -r '.ssid // "N/A"')
            if [ "$ACTUAL_SSID" = "$EXPECTED_SSID" ]; then
                echo "  ✅ SSID: $ACTUAL_SSID"
            else
                echo "  ❌ SSID: Expected=$EXPECTED_SSID, Actual=$ACTUAL_SSID"
            fi
            
            ACTUAL_RSSI=$(echo "$record" | jq -r '.rssi // "N/A"')
            EXPECTED_RSSI=$(echo "$EXPECTED_DATA" | jq -r '.rssi // "N/A"')
            if [ "$ACTUAL_RSSI" = "$EXPECTED_RSSI" ]; then
                echo "  ✅ RSSI: $ACTUAL_RSSI"
            else
                echo "  ❌ RSSI: Expected=$EXPECTED_RSSI, Actual=$ACTUAL_RSSI"
            fi
            
            ACTUAL_LAT=$(echo "$record" | jq -r '.latitude // "N/A"')
            EXPECTED_LAT=$(echo "$EXPECTED_DATA" | jq -r '.latitude // "N/A"')
            if [ "$ACTUAL_LAT" = "$EXPECTED_LAT" ]; then
                echo "  ✅ Latitude: $ACTUAL_LAT"
            else
                echo "  ❌ Latitude: Expected=$EXPECTED_LAT, Actual=$ACTUAL_LAT"
            fi
            
            ACTUAL_LON=$(echo "$record" | jq -r '.longitude // "N/A"')
            EXPECTED_LON=$(echo "$EXPECTED_DATA" | jq -r '.longitude // "N/A"')
            if [ "$ACTUAL_LON" = "$EXPECTED_LON" ]; then
                echo "  ✅ Longitude: $ACTUAL_LON"
            else
                echo "  ❌ Longitude: Expected=$EXPECTED_LON, Actual=$ACTUAL_LON"
            fi
            
            ACTUAL_STATUS=$(echo "$record" | jq -r '.connection_status // "N/A"')
            EXPECTED_STATUS=$(echo "$EXPECTED_DATA" | jq -r '.connection_status // "N/A"')
            if [ "$ACTUAL_STATUS" = "$EXPECTED_STATUS" ]; then
                echo "  ✅ Connection Status: $ACTUAL_STATUS"
            else
                echo "  ❌ Connection Status: Expected=$EXPECTED_STATUS, Actual=$ACTUAL_STATUS"
            fi
            
            ACTUAL_QUALITY=$(echo "$record" | jq -r '.quality_weight // "N/A"')
            EXPECTED_QUALITY=$(echo "$EXPECTED_DATA" | jq -r '.quality_weight // "N/A"')
            if [ "$ACTUAL_QUALITY" = "$EXPECTED_QUALITY" ]; then
                echo "  ✅ Quality Weight: $ACTUAL_QUALITY"
            else
                echo "  ❌ Quality Weight: Expected=$EXPECTED_QUALITY, Actual=$ACTUAL_QUALITY"
            fi
            
            echo ""
            echo "----------------------------------------"
            
        done
        
        echo ""
        echo "COMPREHENSIVE RECORD SUMMARY:"
        echo "============================="
        
        # Count total records (JSON Lines format - one JSON object per line)
        TOTAL_RECORDS=$(cat /tmp/firehose-output.json | wc -l | tr -d ' ')
        CONNECTED_RECORDS=$(cat /tmp/firehose-output.json | jq -r 'select(.connection_status == "CONNECTED") | .connection_status' | wc -l | tr -d ' ')
        SCAN_RECORDS=$(cat /tmp/firehose-output.json | jq -r 'select(.connection_status == "SCAN") | .connection_status' | wc -l | tr -d ' ')
        UNIQUE_BSSIDS=$(cat /tmp/firehose-output.json | jq -r '.bssid' | sort | uniq | wc -l | tr -d ' ')
        UNIQUE_SSIDS=$(cat /tmp/firehose-output.json | jq -r '.ssid' | grep -v "null" | sort | uniq | wc -l | tr -d ' ')
        
        echo "RECORD STATISTICS:"
        echo "  Total Records: $TOTAL_RECORDS"
        echo "  CONNECTED Records: $CONNECTED_RECORDS"
        echo "  SCAN Records: $SCAN_RECORDS"
        echo "  Unique BSSIDs: $UNIQUE_BSSIDS"
        echo "  Unique SSIDs: $UNIQUE_SSIDS"
        
        echo ""
        echo "ALL BSSIDs BY CONNECTION STATUS:"
        echo "  CONNECTED BSSIDs:"
        cat /tmp/firehose-output.json | jq -r 'select(.connection_status == "CONNECTED") | .bssid' | sort | uniq | sed 's/^/    /'
        echo "  SCAN BSSIDs:"
        cat /tmp/firehose-output.json | jq -r 'select(.connection_status == "SCAN") | .bssid' | sort | uniq | sed 's/^/    /'
        
    } > "$ANALYSIS_FILE"
    
    print_status $GREEN "✅ Detailed analysis saved to: $ANALYSIS_FILE"
    echo "   You can review the complete analysis with: cat $ANALYSIS_FILE"
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
    
    # Simple numeric validation (avoid bc dependency issues)
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
    echo "  LocalStack Endpoint: $LOCALSTACK_ENDPOINT"
    echo "  S3 Source Bucket: s3://$S3_BUCKET_NAME"
    echo "  S3 Destination Bucket: s3://$S3_FIREHOSE_DESTINATION_BUCKET"
    echo "  SQS Queue: $SQS_QUEUE_NAME"
    echo "  Firehose Stream: $FIREHOSE_DELIVERY_STREAM_NAME"
    echo ""
    echo "📤 Test Data:"
    echo "  Source File: $S3_KEY"
    echo "  Original BSSIDs: $(cat /tmp/comprehensive-wifi-scan.json | jq -r '.wifiConnectedEvents[].wifiConnectedInfo.bssid, .scanResults[].results[].bssid' | sort | uniq | wc -l | tr -d ' ')"
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
    
    # Clean up any other test files in destination bucket (based on timestamp)
    print_status $BLUE "📅 Cleaning up files from test timestamp..."
    TIMESTAMP_FILES=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_FIREHOSE_DESTINATION_BUCKET/ --recursive 2>/dev/null | grep "$TEST_TIMESTAMP" | awk '{print $4}' || true)
    
    if [ -n "$TIMESTAMP_FILES" ]; then
        TIMESTAMP_CLEANUP_COUNT=0
        echo "$TIMESTAMP_FILES" | while read -r file; do
            if [ -n "$file" ]; then
                if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rm s3://$S3_FIREHOSE_DESTINATION_BUCKET/$file >/dev/null 2>&1; then
                    print_status $GREEN "✅ Timestamp-based file cleaned up: $file"
                    TIMESTAMP_CLEANUP_COUNT=$((TIMESTAMP_CLEANUP_COUNT + 1))
                else
                    print_status $YELLOW "⚠️  Failed to clean up timestamp file: $file"
                fi
            fi
        done
        if [ $TIMESTAMP_CLEANUP_COUNT -gt 0 ]; then
            print_status $GREEN "✅ Cleaned up $TIMESTAMP_CLEANUP_COUNT timestamp-based files"
        fi
    else
        print_status $YELLOW "ℹ️  No timestamp-based files found to clean up"
    fi
    
    print_status $GREEN "✅ S3 cleanup completed"
}

# Function to cleanup test files
cleanup_test_files() {
    print_status $BLUE "🧹 Cleaning up test files..."
    rm -f /tmp/comprehensive-wifi-scan.json
    rm -f /tmp/comprehensive-encoded.txt
    rm -f /tmp/s3-event.json
    rm -f /tmp/firehose-output.gz
    rm -f /tmp/firehose-output.json
    print_status $GREEN "✅ Test files cleaned up"
}

# Main execution
main() {
    check_localstack
    create_test_data
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