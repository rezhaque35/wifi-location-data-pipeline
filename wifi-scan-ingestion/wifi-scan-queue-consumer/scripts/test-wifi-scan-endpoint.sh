#!/bin/bash

# Test script for the new WiFi scan endpoint
# This script tests the /api/metrics/wifi-scan endpoint that accepts WiFi scan messages
# and processes them through compression and Firehose delivery

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Configuration
DEFAULT_PORT=8080
DEFAULT_HOST="localhost"
ENDPOINT_PATH="/api/metrics/wifi-scan"

# Function to generate a sample WiFi scan message
generate_wifi_scan_message() {
    local mac_address=$(printf "%02x:%02x:%02x:%02x:%02x:%02x" $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)))
    local signal_strength=$(echo "scale=1; -30 - $RANDOM % 70" | bc 2>/dev/null || echo "-65.4")
    local frequency=$((2400 + RANDOM % 1000))
    local ssid="TestWiFi-${RANDOM}"
    local link_speed=$((54 + RANDOM % 1000))
    local channel_width=$((20 + (RANDOM % 4) * 20))
    local request_id="req-$(date +%s)-$RANDOM"
    
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
    "application": "wifi-scan-test-suite",
    "calculationDetail": true
}
EOF
}

# Function to test the endpoint
test_wifi_scan_endpoint() {
    local host="$1"
    local port="$2"
    local message="$3"
    
    local url="http://${host}:${port}${ENDPOINT_PATH}"
    
    print_status "Testing WiFi scan endpoint at: $url"
    print_status "Sending message with request ID: $(echo "$message" | jq -r '.requestId')"
    
    # Send POST request to the endpoint
    local response
    response=$(curl -s -w "\n%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -d "$message" \
        "$url" 2>/dev/null)
    
    # Extract HTTP status code (last line)
    local http_code=$(echo "$response" | tail -n1)
    local response_body=$(echo "$response" | head -n -1)
    
    print_status "HTTP Status Code: $http_code"
    
    if [ "$http_code" -eq 200 ]; then
        print_success "Endpoint responded successfully!"
        
        # Parse and display response details
        local status=$(echo "$response_body" | jq -r '.status' 2>/dev/null || echo "unknown")
        local message_text=$(echo "$response_body" | jq -r '.message' 2>/dev/null || echo "No message")
        local original_size=$(echo "$response_body" | jq -r '.originalMessageSize' 2>/dev/null || echo "unknown")
        local compressed_size=$(echo "$response_body" | jq -r '.compressedMessageSize' 2>/dev/null || echo "unknown")
        local compression_ratio=$(echo "$response_body" | jq -r '.compressionRatio' 2>/dev/null || echo "unknown")
        
        echo "Response Details:"
        echo "  Status: $status"
        echo "  Message: $message_text"
        echo "  Original Size: $original_size characters"
        echo "  Compressed Size: $compressed_size characters"
        echo "  Compression Ratio: $compression_ratio%"
        
        if [ "$status" = "success" ]; then
            print_success "WiFi scan message processed and delivered successfully!"
            return 0
        else
            print_error "Message processing failed: $message_text"
            return 1
        fi
    else
        print_error "Endpoint failed with HTTP status: $http_code"
        print_error "Response: $response_body"
        return 1
    fi
}

# Function to check if the application is running
check_application_running() {
    local host="$1"
    local port="$2"
    
    print_status "Checking if application is running on $host:$port"
    
    if curl -s "http://${host}:${port}/actuator/health" >/dev/null 2>&1; then
        print_success "Application is running and health endpoint is accessible"
        return 0
    else
        print_error "Application is not running or health endpoint is not accessible"
        return 1
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --host HOST     Application host (default: localhost)"
    echo "  -p, --port PORT     Application port (default: 8080)"
    echo "  -m, --message FILE  Use custom message from file"
    echo "  -c, --count N       Number of test messages to send (default: 1)"
    echo "  -i, --interval SEC  Interval between messages in seconds (default: 1)"
    echo "  --help              Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Basic test with default settings"
    echo "  $0 -p 8081                           # Test on port 8081"
    echo "  $0 -c 5 -i 2                         # Send 5 messages with 2-second intervals"
    echo "  $0 -m custom-message.json            # Use custom message from file"
    echo ""
}

# Main execution
main() {
    local host="$DEFAULT_HOST"
    local port="$DEFAULT_PORT"
    local message_file=""
    local count=1
    local interval=1
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--host)
                host="$2"
                shift 2
                ;;
            -p|--port)
                port="$2"
                shift 2
                ;;
            -m|--message)
                message_file="$2"
                shift 2
                ;;
            -c|--count)
                count="$2"
                shift 2
                ;;
            -i|--interval)
                interval="$2"
                shift 2
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    print_status "WiFi Scan Endpoint Test Script"
    print_status "=============================="
    print_status "Host: $host"
    print_status "Port: $port"
    print_status "Message Count: $count"
    print_status "Interval: ${interval}s"
    
    # Check if application is running
    if ! check_application_running "$host" "$port"; then
        print_error "Please start the application first"
        exit 1
    fi
    
    # Test the endpoint
    local success_count=0
    local failure_count=0
    
    for i in $(seq 1 "$count"); do
        print_status "Test $i/$count"
        
        # Generate or load message
        local message
        if [ -n "$message_file" ] && [ -f "$message_file" ]; then
            message=$(cat "$message_file")
            print_status "Using custom message from: $message_file"
        else
            message=$(generate_wifi_scan_message)
            print_status "Generated test message"
        fi
        
        # Test the endpoint
        if test_wifi_scan_endpoint "$host" "$port" "$message"; then
            ((success_count++))
        else
            ((failure_count++))
        fi
        
        # Wait between tests (except for the last one)
        if [ $i -lt "$count" ]; then
            print_status "Waiting ${interval}s before next test..."
            sleep "$interval"
        fi
    done
    
    # Summary
    echo ""
    print_status "Test Summary"
    print_status "============"
    print_status "Total Tests: $count"
    print_status "Successful: $success_count"
    print_status "Failed: $failure_count"
    
    if [ $failure_count -eq 0 ]; then
        print_success "All tests passed!"
        exit 0
    else
        print_error "Some tests failed!"
        exit 1
    fi
}

# Run main function with all arguments
main "$@" 