#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Verifying DynamoDB Local data...${NC}"

# Check if table exists
echo -e "\n${BLUE}Checking table existence...${NC}"
TABLE_CHECK=$(aws dynamodb describe-table \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local 2>/dev/null)

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: wifi_access_points table does not exist!${NC}"
    exit 1
fi

echo -e "${GREEN}Table exists.${NC}"

# Scan the table and count items
echo -e "\n${BLUE}Scanning for data...${NC}"
SCAN_RESULT=$(aws dynamodb scan \
    --table-name wifi_access_points \
    --select COUNT \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local)

ITEM_COUNT=$(echo $SCAN_RESULT | jq -r '.Count')
echo -e "Total items in table: ${YELLOW}$ITEM_COUNT${NC}"

# Function to get item by MAC address
get_item() {
    local mac=$1
    local result=$(aws dynamodb get-item \
        --table-name wifi_access_points \
        --key "{\"mac_addr\":{\"S\":\"$mac\"}}" \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local 2>/dev/null)
    
    if [ $? -eq 0 ] && [ ! -z "$result" ]; then
        echo -e "${GREEN}✓ Found${NC}"
        echo "$result" | jq -r '.Item | {mac_addr: .mac_addr.S, ssid: .ssid.S, signal_strength_avg: .signal_strength_avg.N, status: .status.S}'
    else
        echo -e "${RED}✗ Not Found${NC}"
    fi
}

# Function to verify a list of MAC addresses
verify_macs() {
    local section=$1
    shift
    local macs=("$@")
    
    echo -e "\n${BLUE}$section${NC}"
    echo -e "${BLUE}----------------------------------------${NC}"
    
    for mac in "${macs[@]}"; do
        echo -n "Checking $mac: "
        get_item "$mac"
    done
}

# SECTION 1: BASIC ALGORITHM TEST CASES
basic_macs=(
    "00:11:22:33:44:01"  # Single AP Test
    "00:11:22:33:44:02"  # Two APs Test
    "00:11:22:33:44:03"  # Three APs Test
    "00:11:22:33:44:04"  # Multiple APs Test
    "00:11:22:33:44:05"  # Weak Signals Test
)
verify_macs "SECTION 1: BASIC ALGORITHM TEST CASES" "${basic_macs[@]}"

# SECTION 2: ADVANCED SCENARIO TEST CASES
advanced_macs=(
    "00:11:22:33:44:06"  # Collinear APs Test
    "00:11:22:33:44:07"  # Collinear APs Test
    "00:11:22:33:44:08"  # Collinear APs Test
    "00:11:22:33:44:11"  # High Density Test
    "00:11:22:33:44:12"  # High Density Test
    "00:11:22:33:44:13"  # High Density Test
    "00:11:22:33:44:14"  # High Density Test
    "00:11:22:33:44:16"  # Mixed Signal Test
    "00:11:22:33:44:17"  # Mixed Signal Test
    "00:11:22:33:44:18"  # Mixed Signal Test
)
verify_macs "SECTION 2: ADVANCED SCENARIO TEST CASES" "${advanced_macs[@]}"

# SECTION 3: TEMPORAL AND ENVIRONMENTAL TEST CASES
temporal_macs=(
    "00:11:22:33:44:21"  # Time Series Test
    "00:11:22:33:44:22"  # Time Series Test
    "00:11:22:33:44:26"  # Path Loss Test
    "00:11:22:33:44:27"  # Path Loss Test
    "00:11:22:33:44:31"  # Stable Signal Test
    "00:11:22:33:44:32"  # Stable Signal Test
)
verify_macs "SECTION 3: TEMPORAL AND ENVIRONMENTAL TEST CASES" "${temporal_macs[@]}"

# SECTION 4: ERROR AND EDGE CASES
error_macs=(
    "00:11:22:33:44:36"  # Invalid coordinates
    "00:11:22:33:44:55"  # Very Weak Signal Test
)
verify_macs "SECTION 4: ERROR AND EDGE CASES" "${error_macs[@]}"

# SECTION 5: STATUS FILTERING TESTS
status_macs=(
    "00:11:22:33:44:41"  # Active status
    "00:11:22:33:44:42"  # Warning status
    "00:11:22:33:44:43"  # Error status
    "00:11:22:33:44:44"  # Expired status
    "00:11:22:33:44:45"  # WiFi-Hotspot status
)
verify_macs "SECTION 5: STATUS FILTERING TESTS" "${status_macs[@]}"

# SECTION 6: 2D POSITIONING TESTS
twod_macs=(
    "AA:BB:CC:00:00:50"  # Single AP 2D Test
    "AA:BB:CC:00:00:51"  # Two APs 2D Test
    "AA:BB:CC:00:00:52"  # Two APs 2D Test
    "AA:BB:CC:00:00:53"  # Three APs 2D Test
    "AA:BB:CC:00:00:54"  # Three APs 2D Test
    "AA:BB:CC:00:00:55"  # Three APs 2D Test
    "AA:BB:CC:00:00:56"  # Mixed Data Test
    "AA:BB:CC:00:00:57"  # Mixed Data Test
)
verify_macs "SECTION 6: 2D POSITIONING TESTS" "${twod_macs[@]}"

echo -e "\n${BLUE}Verification complete!${NC}" 