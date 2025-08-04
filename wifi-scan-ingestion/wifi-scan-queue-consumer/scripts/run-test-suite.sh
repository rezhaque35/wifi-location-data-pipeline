#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/run-test-suite.sh
# Comprehensive test suite for service validation

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

print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}[PASS]${NC} $2"
    else
        echo -e "${RED}[FAIL]${NC} $2"
    fi
}

# Configuration
AWS_REGION="us-east-1"
AWS_ENDPOINT_URL="http://localhost:4566"
AWS_ACCESS_KEY_ID="test"
AWS_SECRET_ACCESS_KEY="test"
S3_BUCKET_NAME="wifi-scan-data-bucket"

# Default parameters
SKIP_CLEANUP=false
BACKUP_OLD_DATA=false
VERBOSE=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-cleanup)
            SKIP_CLEANUP=true
            shift
            ;;
        --backup-old-data)
            BACKUP_OLD_DATA=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --skip-cleanup      Skip S3 bucket cleanup before tests"
            echo "  --backup-old-data   Backup old S3 data before cleanup"
            echo "  --verbose           Enable verbose output"
            echo "  --help              Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Test results tracking
declare -a test_results=()
declare -a test_names=()

# Function to clean up S3 bucket before running tests
cleanup_s3_bucket() {
    if [ "$SKIP_CLEANUP" = true ]; then
        print_info "S3 cleanup skipped (--skip-cleanup flag used)"
        return 0
    fi
    
    print_info "Cleaning up S3 bucket before running tests..."
    
    # Check if S3 bucket exists and has objects
    local s3_objects=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l)
    
    if [ $s3_objects -gt 0 ]; then
        print_info "Found $s3_objects object(s) in S3 bucket '$S3_BUCKET_NAME'"
        
        # Optional: backup old data before cleanup
        if [ "$BACKUP_OLD_DATA" = true ]; then
            local backup_dir="/tmp/s3_backup_$(date +%Y%m%d_%H%M%S)"
            print_info "Backing up old data to: $backup_dir"
            
            if aws s3 sync s3://$S3_BUCKET_NAME "$backup_dir" --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
                print_success "✅ Old data backed up successfully to: $backup_dir"
            else
                print_warning "⚠️  Failed to backup old data, but continuing with cleanup..."
            fi
        fi
        
        print_info "Removing $s3_objects object(s) from S3 bucket..."
        
        if aws s3 rm s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
            print_success "✅ S3 bucket cleaned successfully"
            
            # Verify cleanup was successful
            local remaining_objects=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l)
            if [ $remaining_objects -eq 0 ]; then
                print_success "✅ S3 bucket cleanup verified - 0 objects remaining"
            else
                print_warning "⚠️  S3 bucket cleanup incomplete - $remaining_objects objects remaining"
            fi
        else
            print_warning "⚠️  Failed to clean S3 bucket, but continuing with tests..."
        fi
    else
        print_info "✅ S3 bucket is already empty"
    fi
}

# Function to clean up S3 bucket after each test
cleanup_s3_after_test() {
    if [ "$SKIP_CLEANUP" = true ]; then
        return 0
    fi
    
    # Check if S3 bucket has objects from the test
    local s3_objects=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l)
    
    if [ $s3_objects -gt 0 ]; then
        if [ "$VERBOSE" = true ]; then
            print_info "Cleaning up $s3_objects test object(s) from S3..."
        fi
        
        if aws s3 rm s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
            if [ "$VERBOSE" = true ]; then
                print_success "✅ Test data cleaned from S3"
            fi
        else
            print_warning "⚠️  Failed to clean test data from S3, but continuing..."
        fi
    fi
}

# Function to display current configuration
display_configuration() {
    if [ "$VERBOSE" = true ]; then
        print_info "Current Configuration:"
        echo "  - AWS Region: $AWS_REGION"
        echo "  - AWS Endpoint: $AWS_ENDPOINT_URL"
        echo "  - S3 Bucket: $S3_BUCKET_NAME"
        echo "  - Skip Cleanup: $SKIP_CLEANUP"
        echo "  - Backup Old Data: $BACKUP_OLD_DATA"
        echo "  - Verbose Mode: $VERBOSE"
        echo ""
    fi
}

# Function to check and fix message consumption timeout
check_and_fix_message_timeout() {
    local service_url="http://localhost:8080"
    
    print_info "Checking service health before starting tests..."
    
    # Check if service is reachable (don't use -f flag, just check if we can connect)
    local general_health_response=$(curl -s "$service_url/frisco-location-wifi-scan-vmb-consumer/health" 2>/dev/null || echo "")
    if [ -z "$general_health_response" ]; then
        print_warning "Service is not reachable. Please ensure the application is running."
        return 1
    fi
    
    # Get liveness health status
    local liveness_response=$(curl -s "$service_url/frisco-location-wifi-scan-vmb-consumer/health/liveness" 2>/dev/null || echo "")
    
    if [ -z "$liveness_response" ]; then
        print_warning "Could not get liveness health status"
        return 1
    fi
    
    # Check if liveness is DOWN
    local liveness_status=$(echo "$liveness_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
    
    if [ "$liveness_status" = "DOWN" ]; then
        print_warning "Liveness probe is DOWN - checking if it's due to message consumption timeout..."
        
        # Check if it's specifically a message consumption timeout issue
        local consumption_reason=$(echo "$liveness_response" | jq -r '.components.messageConsumptionActivity.details.reason // ""' 2>/dev/null || echo "")
        
        if [[ "$consumption_reason" == *"hasn't received messages"* ]] || [[ "$consumption_reason" == *"message timeout"* ]]; then
            print_warning "Detected message consumption timeout - sending recovery messages..."
            
            # Send a few messages to wake up the service
            print_info "Sending 3 recovery messages to wake up the consumer..."
            if ./scripts/send-wifi-scan-messages.sh --count 3 --interval 1 > /dev/null 2>&1; then
                print_info "Recovery messages sent successfully"
                
                # Wait for health to recover
                print_info "Waiting for service to recover..."
                local recovery_attempts=0
                local max_recovery_attempts=12  # 60 seconds max
                
                while [ $recovery_attempts -lt $max_recovery_attempts ]; do
                    sleep 5
                    local new_liveness_response=$(curl -s "$service_url/frisco-location-wifi-scan-vmb-consumer/health/liveness" 2>/dev/null || echo "")
                    local new_liveness_status=$(echo "$new_liveness_response" | jq -r '.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
                    
                    if [ "$new_liveness_status" = "UP" ]; then
                        print_info "✅ Service health recovered successfully!"
                        return 0
                    fi
                    
                    ((recovery_attempts++))
                    printf "\r${BLUE}[INFO]${NC} Recovery attempt %d/%d..." "$recovery_attempts" "$max_recovery_attempts"
                done
                
                echo ""
                print_warning "Service did not recover within 60 seconds, but continuing with tests..."
            else
                print_warning "Failed to send recovery messages, but continuing with tests..."
            fi
        else
            print_warning "Liveness is DOWN for reasons other than message timeout: $consumption_reason"
            print_info "Continuing with tests anyway..."
        fi
    else
        print_info "✅ Service health is good ($liveness_status)"
    fi
    
    return 0
}

run_test() {
    local test_name="$1"
    local test_command="$2"
    
    print_test "Running: $test_name"
    
    if eval "$test_command"; then
        test_results+=(0)
        print_result 0 "$test_name"
    else
        test_results+=(1)
        print_result 1 "$test_name"
    fi
    
    test_names+=("$test_name")
    
    # Clean up S3 after each test
    cleanup_s3_after_test
    
    echo ""
}

print_header "SERVICE VALIDATION TEST SUITE"

echo "Starting comprehensive service testing..."
echo ""

# Display configuration if verbose mode is enabled
display_configuration

# PRE-CLEANUP: Clean up S3 bucket before running tests
cleanup_s3_bucket

echo ""

# PRE-CHECK: Handle message consumption timeout if present
if ! check_and_fix_message_timeout; then
    print_warning "Health pre-check had issues, but continuing with tests..."
fi

echo ""

# Test 1: Basic Functionality
run_test "Basic Functionality (3 messages)" \
    "./scripts/validate-service-health.sh --count 3 --interval 1"

# Test 2: Quick Processing
run_test "Quick Processing (5 messages, 0.5s interval)" \
    "./scripts/validate-service-health.sh --count 5 --interval 0.5 --timeout 60"

# Test 3: Moderate Load
run_test "Moderate Load (10 messages, 1s interval)" \
    "./scripts/validate-service-health.sh --count 10 --interval 1 --timeout 120"

# Test 4: Health Monitoring
run_test "Health Monitoring (8 messages, frequent checks)" \
    "./scripts/validate-service-health.sh --count 8 --interval 1 --health-interval 2 --timeout 90"

# Test 5: High Frequency
run_test "High Frequency (15 messages, 0.3s interval)" \
    "./scripts/validate-service-health.sh --count 15 --interval 0.3 --timeout 120"

# Test 6: Verbose Monitoring
run_test "Verbose Monitoring (5 messages with detailed output)" \
    "./scripts/validate-service-health.sh --count 5 --interval 1 --verbose"

# Test 7: Firehose Integration - Basic
run_test "Firehose Integration - Basic (5 messages)" \
    "./scripts/validate-firehose-integration.sh --count 5 --interval 1 --timeout 90"

# Test 8: Firehose Integration - Moderate Load
run_test "Firehose Integration - Moderate Load (10 messages)" \
    "./scripts/validate-firehose-integration.sh --count 10 --interval 1 --timeout 120"

# Test 9: Firehose Integration - High Frequency
run_test "Firehose Integration - High Frequency (15 messages, 0.5s interval)" \
    "./scripts/validate-firehose-integration.sh --count 15 --interval 0.5 --timeout 150"

# Test 10: Firehose Integration - Verbose
run_test "Firehose Integration - Verbose (8 messages with detailed output)" \
    "./scripts/validate-firehose-integration.sh --count 8 --interval 1 --verbose --timeout 120"

# Display cleanup summary
echo ""
print_info "Cleanup Summary:"
if [ "$BACKUP_OLD_DATA" = true ]; then
    echo "  - Old S3 data was backed up before cleanup"
    echo "  - Backup location: /tmp/s3_backup_*"
    echo "  - Use 'ls -la /tmp/s3_backup_*' to find backup directories"
fi
if [ "$SKIP_CLEANUP" = false ]; then
    echo "  - S3 bucket cleaned before each test run"
    echo "  - Test data cleaned after each individual test"
    echo "  - Each test started with a clean S3 environment"
else
    echo "  - S3 cleanup was skipped (--skip-cleanup flag used)"
fi

print_header "TEST RESULTS SUMMARY"

total_tests=${#test_results[@]}
passed_tests=0
failed_tests=0

for i in "${!test_results[@]}"; do
    result=${test_results[$i]}
    name=${test_names[$i]}
    
    if [ $result -eq 0 ]; then
        echo -e "${GREEN}✅ PASS${NC} - $name"
        ((passed_tests++))
    else
        echo -e "${RED}❌ FAIL${NC} - $name"
        ((failed_tests++))
    fi
done

echo ""
echo "Total Tests: $total_tests"
echo -e "Passed: ${GREEN}$passed_tests${NC}"
echo -e "Failed: ${RED}$failed_tests${NC}"

if [ $failed_tests -eq 0 ]; then
    echo -e "${GREEN}🎉 ALL TESTS PASSED!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Please check the output above.${NC}"
    exit 1
fi 