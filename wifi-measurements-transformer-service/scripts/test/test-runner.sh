#!/bin/bash

# WiFi Measurements Transformer Service - Consolidated Test Runner
# Runs all test cases from scripts/test/data/ with automated validation

set -e

# Script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST_DATA_DIR="$SCRIPT_DIR/data"

# AWS Configuration
LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="$AWS_REGION"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
declare -a FAILED_TEST_NAMES

# Parse arguments
SKIP_CLEANUP=false
SPECIFIC_TEST=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-cleanup) SKIP_CLEANUP=true; shift ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS] [TEST_FILE]"
            echo "Options:"
            echo "  --skip-cleanup  Skip cleanup after tests"
            echo "  --help          Show this help"
            exit 0
            ;;
        *) SPECIFIC_TEST="$1"; shift ;;
    esac
done

print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

check_localstack() {
    if ! curl -s $LOCALSTACK_ENDPOINT/health > /dev/null 2>&1; then
        print_status "$RED" "❌ LocalStack is not running"
        exit 1
    fi
}

run_test_case() {
    local test_file="$1"
    local test_name=$(basename "$test_file" .json)
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    print_status "$BLUE" "\n════════════════════════════════════════════════"
    print_status "$BLUE" "🧪 Test #$TOTAL_TESTS: $test_name"
    print_status "$BLUE" "════════════════════════════════════════════════"
    
    # Run test and capture exit code (NOT grep for emoji!)
    local test_output_file="/tmp/test-output-$test_name.log"
    local exit_code=0
    
    (cd "$SCRIPT_DIR" && ./test-with-data-file.sh --summary-only "$(basename "$test_file")") > "$test_output_file" 2>&1 || exit_code=$?
    
    # Show output
    cat "$test_output_file"
    
    # Check actual exit code, not grep for success emoji
    if [ $exit_code -eq 0 ]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        print_status "$GREEN" "✅ PASSED: $test_name"
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        FAILED_TEST_NAMES+=("$test_name")
        print_status "$RED" "❌ FAILED: $test_name (exit code: $exit_code)"
        print_status "$RED" "    Log saved to: $test_output_file"
    fi
    
    # Add delay between tests to allow Firehose to flush its buffer
    # This prevents data from multiple tests being batched together
    if [ $TOTAL_TESTS -lt $(find "$TEST_DATA_DIR" -name "*.json" -type f | wc -l | tr -d ' ') ]; then
        print_status "$YELLOW" "⏳ Waiting 3s for Firehose buffer flush..."
        sleep 3
    fi
}

# Main execution
main() {
    print_status "$BLUE" "\n╔═══════════════════════════════════════════════════╗"
    print_status "$BLUE" "║  WiFi Transformer Service - Test Suite Runner    ║"
    print_status "$BLUE" "╚═══════════════════════════════════════════════════╝\n"
    
    check_localstack
    
    # Discover tests
    if [ -n "$SPECIFIC_TEST" ]; then
        test_files=("$TEST_DATA_DIR/$SPECIFIC_TEST")
        if [ ! -f "${test_files[0]}" ]; then
            print_status "$RED" "❌ Test file not found: $SPECIFIC_TEST"
            exit 1
        fi
    else
        test_files=($(find "$TEST_DATA_DIR" -name "*.json" -type f | sort))
    fi
    
    if [ ${#test_files[@]} -eq 0 ]; then
        print_status "$RED" "❌ No test files found in $TEST_DATA_DIR"
        exit 1
    fi
    
    print_status "$BLUE" "📋 Found ${#test_files[@]} test case(s)\n"
    
    # Run each test
    for test_file in "${test_files[@]}"; do
        run_test_case "$test_file"
    done
    
    # Summary
    print_status "$BLUE" "\n╔═══════════════════════════════════════════════════╗"
    print_status "$BLUE" "║              TEST SUITE SUMMARY                   ║"
    print_status "$BLUE" "╚═══════════════════════════════════════════════════╝\n"
    
    echo "Total Tests:  $TOTAL_TESTS"
    print_status "$GREEN" "Passed:       $PASSED_TESTS"
    print_status "$RED" "Failed:       $FAILED_TESTS"
    
    if [ $FAILED_TESTS -gt 0 ]; then
        print_status "$RED" "\n❌ Failed Tests:"
        for test_name in "${FAILED_TEST_NAMES[@]}"; do
            echo "  - $test_name"
        done
        exit 1
    else
        print_status "$GREEN" "\n✅ All tests passed!"
        exit 0
    fi
}

main "$@"
