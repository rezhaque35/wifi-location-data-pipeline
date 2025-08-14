#!/bin/bash

# wifi-measurements-transformer-service/scripts/run-tests.sh
# Simple test runner script for WiFi Measurements Transformer Service
# Provides easy access to all test options

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLEXIBLE_TEST_SCRIPT="$SCRIPT_DIR/test-with-data-file.sh"
ORIGINAL_TEST_SCRIPT="$SCRIPT_DIR/test-end-to-end-flow.sh"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

show_menu() {
    echo ""
    print_status $BLUE "🧪 WiFi Measurements Transformer Service - Test Runner"
    echo "=================================================="
    echo ""
    echo "Available test options:"
    echo ""
    echo "1. 🆕 Flexible Test (with data files)"
    echo "   - Use test data from JSON files"
    echo "   - More maintainable and reusable"
    echo ""
    echo "2. 📝 Original Test (hardcoded data)"
    echo "   - Quick testing with predefined data"
    echo "   - Good for basic functionality checks"
    echo ""
    echo "3. 📁 List available test data files"
    echo "   - See what test data is available"
    echo "   - View file metadata and content preview"
    echo ""
    echo "4. ❓ Help and usage information"
    echo "   - Detailed usage instructions"
    echo "   - Command line options"
    echo ""
    echo "5. 🚪 Exit"
    echo ""
}

run_flexible_test() {
    echo ""
    print_status $GREEN "🚀 Starting Flexible Test..."
    echo ""
    echo "Available options:"
    echo "  - Press Enter to use first available test file"
    echo "  - Type filename (e.g., sample-wifi-scan.json)"
    echo "  - Type 'back' to return to main menu"
    echo ""
    read -p "Enter test file name (or press Enter for default): " test_file
    
    if [ "$test_file" = "back" ]; then
        return
    fi
    
    if [ -z "$test_file" ]; then
        echo "Using first available test file..."
        $FLEXIBLE_TEST_SCRIPT
    else
        echo "Using test file: $test_file"
        $FLEXIBLE_TEST_SCRIPT "$test_file"
    fi
}

run_original_test() {
    echo ""
    print_status $GREEN "🚀 Starting Original Test..."
    echo ""
    echo "This will run the test with hardcoded data."
    echo "Press Enter to continue or 'back' to return to menu..."
    read -p "Continue? " choice
    
    if [ "$choice" = "back" ]; then
        return
    fi
    
    $ORIGINAL_TEST_SCRIPT
}

list_test_files() {
    echo ""
    print_status $GREEN "📁 Listing Test Data Files..."
    echo ""
    $FLEXIBLE_TEST_SCRIPT --list-files
}

show_help() {
    echo ""
    print_status $GREEN "❓ Help and Usage Information"
    echo "================================="
    echo ""
    echo "Flexible Test Script (test-with-data-file.sh):"
    echo "  Usage: ./test-with-data-file.sh [OPTIONS] [DATA_FILE]"
    echo ""
    echo "  Options:"
    echo "    --skip-cleanup    Skip cleanup of S3 files and local test files"
    echo "    --summary-only    Show only processing summary (skip verbose content)"
    echo "    --list-files, -l  List available test data files and exit"
    echo "    --help, -h        Show this help message"
    echo ""
    echo "  Examples:"
    echo "    ./test-with-data-file.sh                                    # Use first available file"
    echo "    ./test-with-data-file.sh sample-wifi-scan.json             # Use specific file"
    echo "    ./test-with-data-file.sh --skip-cleanup multi-location-scan.json"
    echo ""
    echo "Original Test Script (test-end-to-end-flow.sh):"
    echo "  Usage: ./test-end-to-end-flow.sh [OPTIONS]"
    echo ""
    echo "  Options:"
    echo "    --skip-cleanup    Skip cleanup of S3 files and local test files"
    echo "    --summary-only    Show only processing summary (skip verbose content)"
    echo "    --help, -h        Show this help message"
    echo ""
    echo "Prerequisites:"
    echo "  - LocalStack running on port 4566"
    echo "  - AWS CLI installed and configured for LocalStack"
    echo "  - jq, gzip, and other required tools"
    echo ""
    echo "Starting LocalStack:"
    echo "  docker run --rm -it -p 4566:4566 localstack/localstack"
    echo ""
}

main() {
    while true; do
        show_menu
        read -p "Select an option (1-5): " choice
        
        case $choice in
            1)
                run_flexible_test
                ;;
            2)
                run_original_test
                ;;
            3)
                list_test_files
                ;;
            4)
                show_help
                ;;
            5)
                echo ""
                print_status $GREEN "👋 Goodbye!"
                exit 0
                ;;
            *)
                echo ""
                print_status $YELLOW "⚠️  Invalid option. Please select 1-5."
                ;;
        esac
        
        echo ""
        read -p "Press Enter to continue..."
    done
}

# Check if scripts exist
if [ ! -f "$FLEXIBLE_TEST_SCRIPT" ]; then
    print_status $YELLOW "⚠️  Flexible test script not found: $FLEXIBLE_TEST_SCRIPT"
    exit 1
fi

if [ ! -f "$ORIGINAL_TEST_SCRIPT" ]; then
    print_status $YELLOW "⚠️  Original test script not found: $ORIGINAL_TEST_SCRIPT"
    exit 1
fi

# Make scripts executable
chmod +x "$FLEXIBLE_TEST_SCRIPT" 2>/dev/null || true
chmod +x "$ORIGINAL_TEST_SCRIPT" 2>/dev/null || true

# Run main function
main "$@"
