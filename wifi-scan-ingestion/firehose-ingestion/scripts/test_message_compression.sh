#!/bin/bash

# wifi-scan-ingestion/firehose-ingestion/scripts/test_message_compression.sh

# Message Compression and Encoding Test Script
# =============================================
# 
# This script demonstrates and tests the WiFi scan message compression
# and base64 encoding functionality using the sample data.

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
SAMPLE_DATA_FILE="${PROJECT_ROOT}/documents/smaple_wifiscan.json"

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

# Test message compression and encoding
test_compression() {
    log_info "Testing WiFi Scan Message Compression and Encoding"
    log_info "=================================================="
    
    # Check if sample file exists
    if [[ ! -f "${SAMPLE_DATA_FILE}" ]]; then
        log_error "Sample data file not found: ${SAMPLE_DATA_FILE}"
        exit 1
    fi
    
    log_info "Sample data file: ${SAMPLE_DATA_FILE}"
    
    # Get original file size
    local original_size
    original_size=$(wc -c < "${SAMPLE_DATA_FILE}")
    log_info "Original file size: ${original_size} bytes"
    
    # Test basic compression
    log_info "Testing basic compression and encoding..."
    echo
    python3 "${SCRIPT_DIR}/message_processor.py" \
        --compress \
        --file "${SAMPLE_DATA_FILE}" \
        --verbose
    
    echo
    log_info "Testing Firehose payload creation..."
    echo
    python3 "${SCRIPT_DIR}/message_processor.py" \
        --compress --firehose \
        --file "${SAMPLE_DATA_FILE}" \
        --verbose
    
    echo
    log_success "Message compression and encoding test completed successfully!"
}

# Test round-trip compression/decompression
test_roundtrip() {
    log_info "Testing round-trip compression/decompression..."
    
    # Create temporary directory
    local temp_dir="${SCRIPT_DIR}/temp_test"
    mkdir -p "${temp_dir}"
    
    # Compress and encode
    log_info "Compressing sample data for round-trip test..."
    python3 "${SCRIPT_DIR}/message_processor.py" \
        --compress \
        --file "${SAMPLE_DATA_FILE}" \
        --output "${temp_dir}/compressed.json" > /dev/null 2>&1
    
    local base64_data
    base64_data=$(jq -r '.encoded_data' "${temp_dir}/compressed.json")
    
    if [[ -z "${base64_data}" ]]; then
        log_error "Failed to get base64 data"
        return 1
    fi
    
    # Decompress and verify
    python3 "${SCRIPT_DIR}/message_processor.py" \
        --decompress \
        --base64 "${base64_data}" > "${temp_dir}/decompressed.json"
    
    # Verify content matches
    if python3 -c "
import json
with open('${SAMPLE_DATA_FILE}', 'r') as f: orig = json.load(f)
with open('${temp_dir}/decompressed.json', 'r') as f: decomp = json.load(f)
exit(0 if orig == decomp else 1)
    "; then
        log_success "Round-trip test passed - content matches original"
    else
        log_error "Round-trip test failed - content mismatch"
        return 1
    fi
    
    # Clean up
    rm -rf "${temp_dir}"
}

# Display compression statistics
show_compression_stats() {
    log_info "Compression Statistics Summary"
    log_info "============================="
    
    # Get detailed stats
    python3 "${SCRIPT_DIR}/message_processor.py" \
        --compress \
        --file "${SAMPLE_DATA_FILE}" \
        --verbose 2>/dev/null | grep -E "(Original size|Compressed size|Encoded size|Compression ratio)"
    
    echo
    log_info "Key Benefits of Compression:"
    log_info "  • Reduced storage costs in S3"
    log_info "  • Faster data transfer through Firehose"
    log_info "  • Lower bandwidth usage"
    log_info "  • Improved processing efficiency"
}

# Main execution
main() {
    log_info "Starting Message Compression Test Suite"
    log_info "======================================="
    echo
    
    test_compression
    echo
    test_roundtrip
    echo
    show_compression_stats
    
    echo
    log_success "All message compression tests completed successfully!"
    log_info "Ready to proceed with Phase 3 integration testing."
}

# Execute main function
main "$@" 