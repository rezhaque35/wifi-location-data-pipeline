#!/bin/bash
# scripts/test/test-validation-logging.sh
# Test script to trigger validation failures and verify logging

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Service endpoint
SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"
ENDPOINT="${SERVICE_URL}/wifi-positioning-service/v1/wifi/position"

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Validation Logging Test Script${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo -e "Testing endpoint: ${GREEN}${ENDPOINT}${NC}"
echo ""

# Test 1: Invalid signal strength (below minimum)
echo -e "${YELLOW}Test 1: Invalid Signal Strength (-150 dBm, below minimum of -100)${NC}"
echo "Request ID: req-test-invalid-signal-001"
echo "Expected: HTTP 400 with validation error"
echo ""

curl -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -d '{
    "wifiScanResults": [
        {
            "macAddress": "AA:BB:CC:DD:EE:FF",
            "signalStrength": -150,
            "frequency": 2400,
            "ssid": "TestNetwork"
        }
    ],
    "client": "test-script-client",
    "requestId": "req-test-invalid-signal-001",
    "application": "validation-test"
}'

echo -e "\n${GREEN}✓ Test 1 completed${NC}"
echo -e "${BLUE}Check logs for: REQUEST_ID='req-test-invalid-signal-001'${NC}\n"
sleep 2

# Test 2: Invalid MAC address format
echo -e "${YELLOW}Test 2: Invalid MAC Address Format${NC}"
echo "Request ID: req-test-invalid-mac-002"
echo "Expected: HTTP 400 with validation error"
echo ""

curl -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -d '{
    "wifiScanResults": [
        {
            "macAddress": "INVALID-MAC-ADDRESS",
            "signalStrength": -50,
            "frequency": 2400
        }
    ],
    "client": "test-script-client",
    "requestId": "req-test-invalid-mac-002",
    "application": "validation-test"
}'

echo -e "\n${GREEN}✓ Test 2 completed${NC}"
echo -e "${BLUE}Check logs for: REQUEST_ID='req-test-invalid-mac-002'${NC}\n"
sleep 2

# Test 3: Missing required fields
echo -e "${YELLOW}Test 3: Missing Required Fields (macAddress and signalStrength)${NC}"
echo "Request ID: req-test-missing-fields-003"
echo "Expected: HTTP 400 with multiple validation errors"
echo ""

curl -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -d '{
    "wifiScanResults": [
        {
            "frequency": 2400
        }
    ],
    "client": "test-script-client",
    "requestId": "req-test-missing-fields-003",
    "application": "validation-test"
}'

echo -e "\n${GREEN}✓ Test 3 completed${NC}"
echo -e "${BLUE}Check logs for: REQUEST_ID='req-test-missing-fields-003'${NC}\n"
sleep 2

# Test 4: Empty wifi scan results
echo -e "${YELLOW}Test 4: Empty WiFi Scan Results${NC}"
echo "Request ID: req-test-empty-scans-004"
echo "Expected: HTTP 400 with validation error"
echo ""

curl -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -d '{
    "wifiScanResults": [],
    "client": "test-script-client",
    "requestId": "req-test-empty-scans-004",
    "application": "validation-test"
}'

echo -e "\n${GREEN}✓ Test 4 completed${NC}"
echo -e "${BLUE}Check logs for: REQUEST_ID='req-test-empty-scans-004'${NC}\n"
sleep 2

# Test 5: Invalid frequency (out of range)
echo -e "${YELLOW}Test 5: Invalid Frequency (1000 MHz, below minimum of 2400)${NC}"
echo "Request ID: req-test-invalid-freq-005"
echo "Expected: HTTP 400 with validation error"
echo ""

curl -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -d '{
    "wifiScanResults": [
        {
            "macAddress": "11:22:33:44:55:66",
            "signalStrength": -60,
            "frequency": 1000,
            "ssid": "TestNetwork"
        }
    ],
    "client": "test-script-client",
    "requestId": "req-test-invalid-freq-005",
    "application": "validation-test"
}'

echo -e "\n${GREEN}✓ Test 5 completed${NC}"
echo -e "${BLUE}Check logs for: REQUEST_ID='req-test-invalid-freq-005'${NC}\n"
sleep 2

# Test 6: Missing client and requestId
echo -e "${YELLOW}Test 6: Missing Client and Request ID${NC}"
echo "Expected: HTTP 400 with validation errors for missing fields"
echo ""

curl -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -d '{
    "wifiScanResults": [
        {
            "macAddress": "AA:BB:CC:DD:EE:FF",
            "signalStrength": -50,
            "frequency": 2400
        }
    ]
}'

echo -e "\n${GREEN}✓ Test 6 completed${NC}"
echo -e "${BLUE}Check logs for: REQUEST_ID='UNKNOWN' CLIENT='UNKNOWN'${NC}\n"
sleep 2

echo ""
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}All validation tests completed!${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo -e "${YELLOW}To view the logs:${NC}"
echo -e "1. Check your application logs for validation failures"
echo -e "2. Search for: ${BLUE}HTTP_STATUS=400${NC}"
echo -e "3. Search for: ${BLUE}service=wifi-positioning action=validation_failed${NC}"
echo -e "4. Search by specific request IDs shown above"
echo ""
echo -e "${YELLOW}Example log queries:${NC}"
echo -e "  - All validation failures: ${BLUE}grep 'Validation failure' logs/application.log${NC}"
echo -e "  - Structured logs: ${BLUE}grep 'action=validation_failed' logs/application.log${NC}"
echo -e "  - Specific request: ${BLUE}grep 'req-test-invalid-signal-001' logs/application.log${NC}"
echo ""

