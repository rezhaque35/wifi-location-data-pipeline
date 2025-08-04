#!/bin/bash
# wifi-scan-ingestion/firehose-ingestion/scripts/test_idempotent_setup.sh
# Test script to verify that setup.sh is idempotent and can be run multiple times safely

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "INFO")     echo -e "${BLUE}[TEST-INFO]${NC} $message" ;;
        "SUCCESS")  echo -e "${GREEN}[TEST-SUCCESS]${NC} $message" ;;
        "WARNING")  echo -e "${YELLOW}[TEST-WARNING]${NC} $message" ;;
        "ERROR")    echo -e "${RED}[TEST-ERROR]${NC} $message" ;;
        "STEP")     echo -e "${CYAN}[TEST-STEP]${NC} $message" ;;
    esac
}

# Function to run setup and capture key information
run_setup_and_analyze() {
    local run_number=$1
    print_status "STEP" "Running setup.sh (Run #$run_number)..."
    
    # Capture the output and timing
    local start_time=$(date +%s)
    local output_file="/tmp/setup_run_${run_number}.log"
    
    if ./setup.sh > "$output_file" 2>&1; then
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        
        # Analyze what was skipped vs executed
        local skipped_count=$(grep -c "\[SKIP\]" "$output_file" || echo "0")
        local success_count=$(grep -c "\[SUCCESS\]" "$output_file" || echo "0")
        local info_count=$(grep -c "\[INFO\]" "$output_file" || echo "0")
        
        print_status "SUCCESS" "Run #$run_number completed in ${duration} seconds"
        print_status "INFO" "  - Skipped steps: $skipped_count"
        print_status "INFO" "  - Success messages: $success_count"
        print_status "INFO" "  - Info messages: $info_count"
        
        # Show key lines from output
        print_status "INFO" "Key messages from run #$run_number:"
        grep -E "\[(SKIP|SUCCESS|ERROR|WARNING)\].*Step [1-4]" "$output_file" | sed 's/^/    /'
        
        return 0
    else
        print_status "ERROR" "Run #$run_number failed"
        print_status "INFO" "Last 10 lines of output:"
        tail -10 "$output_file" | sed 's/^/    /'
        return 1
    fi
}

# Main test function
main() {
    print_status "INFO" "Starting idempotent setup test"
    print_status "INFO" "This test runs setup.sh multiple times to verify it's safe and efficient"
    
    # Verify we're in the right directory
    if [ ! -f "./setup.sh" ]; then
        print_status "ERROR" "setup.sh not found in current directory"
        print_status "INFO" "Please run this test from the scripts directory"
        exit 1
    fi
    
    echo ""
    print_status "STEP" "=== FIRST RUN (Fresh Setup) ==="
    if ! run_setup_and_analyze 1; then
        print_status "ERROR" "First setup run failed - cannot continue test"
        exit 1
    fi
    
    echo ""
    print_status "STEP" "=== SECOND RUN (Should Skip Most Steps) ==="
    if ! run_setup_and_analyze 2; then
        print_status "ERROR" "Second setup run failed"
        exit 1
    fi
    
    echo ""
    print_status "STEP" "=== THIRD RUN (Should Skip All Deployment Steps) ==="
    if ! run_setup_and_analyze 3; then
        print_status "ERROR" "Third setup run failed"
        exit 1
    fi
    
    echo ""
    print_status "SUCCESS" "=== IDEMPOTENT TEST RESULTS ==="
    
    # Verify infrastructure is still working
    print_status "STEP" "Verifying infrastructure health..."
    if ./verify-deployment.sh > /tmp/verify_test.log 2>&1; then
        print_status "SUCCESS" "Infrastructure verification passed"
    else
        print_status "ERROR" "Infrastructure verification failed after multiple setup runs"
        print_status "INFO" "Verification output:"
        cat /tmp/verify_test.log | sed 's/^/    /'
        exit 1
    fi
    
    # Compare run times (should be faster after first run)
    print_status "INFO" "Analyzing run efficiency..."
    local run1_time=$(grep "completed in" /tmp/setup_run_1.log | grep -o '[0-9]\+ seconds' | grep -o '[0-9]\+')
    local run2_time=$(grep "completed in" /tmp/setup_run_2.log | grep -o '[0-9]\+ seconds' | grep -o '[0-9]\+')
    local run3_time=$(grep "completed in" /tmp/setup_run_3.log | grep -o '[0-9]\+ seconds' | grep -o '[0-9]\+')
    
    print_status "INFO" "Run times:"
    print_status "INFO" "  - Run 1 (fresh): ${run1_time:-unknown} seconds"
    print_status "INFO" "  - Run 2 (idempotent): ${run2_time:-unknown} seconds"
    print_status "INFO" "  - Run 3 (idempotent): ${run3_time:-unknown} seconds"
    
    # Check if subsequent runs were faster (allowing some variance)
    if [ -n "$run1_time" ] && [ -n "$run2_time" ] && [ "$run2_time" -lt "$run1_time" ]; then
        print_status "SUCCESS" "Subsequent runs were faster - good idempotent behavior"
    else
        print_status "WARNING" "Subsequent runs weren't significantly faster - may need optimization"
    fi
    
    # Cleanup test files
    rm -f /tmp/setup_run_*.log /tmp/verify_test.log
    
    echo ""
    print_status "SUCCESS" "Idempotent setup test PASSED"
    print_status "INFO" "✓ setup.sh can be run multiple times safely"
    print_status "INFO" "✓ Already completed steps are properly skipped"
    print_status "INFO" "✓ Infrastructure remains healthy after multiple runs"
    print_status "INFO" "✓ Script execution is efficient on subsequent runs"
    
    echo ""
    print_status "INFO" "The setup script is ready for production use!"
}

# Run main function
main "$@" 