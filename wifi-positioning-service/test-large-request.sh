#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default values for URL and port
HOST="localhost"
PORT="8080"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        -h|--host)
            HOST="$2"
            shift 2
            ;;
        -p|--port)
            PORT="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "OPTIONS:"
            echo "  -h, --host HOST     Specify the host (default: localhost)"
            echo "  -p, --port PORT     Specify the port (default: 8080)"
            echo "  --help              Show this help message"
            exit 1
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Construct the API URL
API_URL="http://${HOST}:${PORT}/wifi-positioning-service/v1/wifi/position"

echo -e "${CYAN}Using API endpoint: ${API_URL}${NC}"

# Check if jq is installed
if ! command -v jq &> /dev/null; then
  echo "jq is not installed. Please install it with:"
  echo "  brew install jq"
  exit 1
fi

echo -e "\n${BLUE}====================================================${NC}"
echo -e "${BLUE}  LARGE REQUEST TEST WITH CALCULATION DETAILS${NC}"
echo -e "${BLUE}====================================================${NC}"

# Create a large request with multiple access points
LARGE_REQUEST='{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:01",
            "ssid": "SingleAP_Test",
            "signalStrength": -65.0,
            "frequency": 2437
        },
        {
            "macAddress": "00:11:22:33:44:02",
            "signalStrength": -68.5,
            "frequency": 5180,
            "ssid": "DualAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:03",
            "signalStrength": -62.3,
            "frequency": 2462,
            "ssid": "TriAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:04",
            "signalStrength": -71.2,
            "frequency": 5240,
            "ssid": "MultiAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85.5,
            "frequency": 2412,
            "ssid": "WeakSignal_Test"
        },
        {
            "macAddress": "00:11:22:33:44:06",
            "signalStrength": -70.0,
            "frequency": 2437,
            "ssid": "Collinear_Test_06"
        },
        {
            "macAddress": "00:11:22:33:44:07",
            "signalStrength": -68.0,
            "frequency": 2437,
            "ssid": "Collinear_Test_07"
        },
        {
            "macAddress": "00:11:22:33:44:08",
            "signalStrength": -66.0,
            "frequency": 2437,
            "ssid": "Collinear_Test_08"
        },
        {
            "macAddress": "00:11:22:33:44:09",
            "signalStrength": -64.0,
            "frequency": 2437,
            "ssid": "Collinear_Test_09"
        },
        {
            "macAddress": "00:11:22:33:44:10",
            "signalStrength": -62.0,
            "frequency": 2437,
            "ssid": "Collinear_Test_10"
        },
        {
            "macAddress": "00:11:22:33:44:11",
            "signalStrength": -65.0,
            "frequency": 5320,
            "ssid": "HighDensity_Test_11"
        },
        {
            "macAddress": "00:11:22:33:44:12",
            "signalStrength": -66.5,
            "frequency": 5320,
            "ssid": "HighDensity_Test_12"
        },
        {
            "macAddress": "00:11:22:33:44:13",
            "signalStrength": -68.0,
            "frequency": 5320,
            "ssid": "HighDensity_Test_13"
        },
        {
            "macAddress": "00:11:22:33:44:14",
            "signalStrength": -69.5,
            "frequency": 5320,
            "ssid": "HighDensity_Test_14"
        },
        {
            "macAddress": "00:11:22:33:44:15",
            "signalStrength": -71.0,
            "frequency": 5320,
            "ssid": "HighDensity_Test_15"
        },
        {
            "macAddress": "00:11:22:33:44:16",
            "signalStrength": -60.0,
            "frequency": 2412,
            "ssid": "MixedSignal_Test_16"
        },
        {
            "macAddress": "00:11:22:33:44:17",
            "signalStrength": -65.0,
            "frequency": 2417,
            "ssid": "MixedSignal_Test_17"
        },
        {
            "macAddress": "00:11:22:33:44:18",
            "signalStrength": -70.0,
            "frequency": 2422,
            "ssid": "MixedSignal_Test_18"
        },
        {
            "macAddress": "00:11:22:33:44:19",
            "signalStrength": -75.0,
            "frequency": 2427,
            "ssid": "MixedSignal_Test_19"
        },
        {
            "macAddress": "00:11:22:33:44:20",
            "signalStrength": -80.0,
            "frequency": 2432,
            "ssid": "MixedSignal_Test_20"
        },
        {
            "macAddress": "00:11:22:33:44:21",
            "signalStrength": -70.0,
            "frequency": 5500,
            "ssid": "TimeSeries_Test"
        },
        {
            "macAddress": "00:11:22:33:44:22",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "TimeSeries_Test"
        },
        {
            "macAddress": "00:11:22:33:44:23",
            "signalStrength": -66.0,
            "frequency": 5500,
            "ssid": "TimeSeries_Test"
        },
        {
            "macAddress": "00:11:22:33:44:24",
            "signalStrength": -64.0,
            "frequency": 5500,
            "ssid": "TimeSeries_Test"
        },
        {
            "macAddress": "00:11:22:33:44:25",
            "signalStrength": -62.0,
            "frequency": 5500,
            "ssid": "TimeSeries_Test"
        },
        {
            "macAddress": "00:11:22:33:44:26",
            "signalStrength": -50.0,
            "frequency": 2462,
            "ssid": "PathLoss_Test_26"
        },
        {
            "macAddress": "00:11:22:33:44:27",
            "signalStrength": -53.0,
            "frequency": 2462,
            "ssid": "PathLoss_Test_27"
        },
        {
            "macAddress": "00:11:22:33:44:28",
            "signalStrength": -56.0,
            "frequency": 2462,
            "ssid": "PathLoss_Test_28"
        },
        {
            "macAddress": "00:11:22:33:44:29",
            "signalStrength": -59.0,
            "frequency": 2462,
            "ssid": "PathLoss_Test_29"
        },
        {
            "macAddress": "00:11:22:33:44:30",
            "signalStrength": -62.0,
            "frequency": 2462,
            "ssid": "PathLoss_Test_30"
        },
        {
            "macAddress": "00:11:22:33:44:31",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "Historical_Test"
        },
        {
            "macAddress": "00:11:22:33:44:32",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "Historical_Test"
        },
        {
            "macAddress": "00:11:22:33:44:33",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "Historical_Test"
        },
        {
            "macAddress": "00:11:22:33:44:34",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "Historical_Test"
        },
        {
            "macAddress": "00:11:22:33:44:35",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "Historical_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:50",
            "signalStrength": -65.0,
            "frequency": 2437,
            "ssid": "2D_SingleAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:51",
            "signalStrength": -68.0,
            "frequency": 2477,
            "ssid": "2D_DualAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:52",
            "signalStrength": -71.0,
            "frequency": 2517,
            "ssid": "2D_DualAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:53",
            "signalStrength": -64.0,
            "frequency": 2437,
            "ssid": "2D_TriAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:54",
            "signalStrength": -67.0,
            "frequency": 2457,
            "ssid": "2D_TriAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:55",
            "signalStrength": -70.0,
            "frequency": 2477,
            "ssid": "2D_TriAP_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:56",
            "signalStrength": -63.0,
            "frequency": 2437,
            "ssid": "Mixed_2D3D_Test"
        },
        {
            "macAddress": "AA:BB:CC:00:00:57",
            "signalStrength": -66.0,
            "frequency": 2437,
            "ssid": "Mixed_2D3D_Test"
        }
    ],
    "client": "large-test-client",
    "requestId": "large-request-test-001",
    "application": "wifi-positioning-large-test-suite",
    "calculationDetail": true
}'

echo -e "\n${YELLOW}Making request with ${CYAN}42 access points${YELLOW} and calculationDetail=true...${NC}"
echo -e "${YELLOW}Request payload:${NC}"
echo "$LARGE_REQUEST" | jq '.'

echo -e "\n${YELLOW}Making API call...${NC}"

# Make the API call
response=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "$LARGE_REQUEST" \
    ${API_URL})

# Check if the request was successful
if [ $? -eq 0 ]; then
    echo -e "\n${GREEN}✓ API call successful${NC}"
    echo -e "\n${BLUE}====================================================${NC}"
    echo -e "${BLUE}  RESPONSE WITH CALCULATION DETAILS${NC}"
    echo -e "${BLUE}====================================================${NC}"
    
    # Pretty print the response
    echo "$response" | jq '.'
    
    # Extract key information
    echo -e "\n${CYAN}====================================================${NC}"
    echo -e "${CYAN}  KEY INFORMATION EXTRACTED${NC}"
    echo -e "${CYAN}====================================================${NC}"
    
    result=$(echo "$response" | jq -r '.result // "N/A"')
    message=$(echo "$response" | jq -r '.message // "N/A"')
    request_id=$(echo "$response" | jq -r '.requestId // "N/A"')
    client=$(echo "$response" | jq -r '.client // "N/A"')
    timestamp=$(echo "$response" | jq -r '.timestamp // "N/A"')
    
    echo -e "${YELLOW}Result:${NC} $result"
    echo -e "${YELLOW}Message:${NC} $message"
    echo -e "${YELLOW}Request ID:${NC} $request_id"
    echo -e "${YELLOW}Client:${NC} $client"
    echo -e "${YELLOW}Timestamp:${NC} $timestamp"
    
    # Extract position information
    if [ "$result" = "SUCCESS" ]; then
        echo -e "\n${GREEN}Position Information:${NC}"
        latitude=$(echo "$response" | jq -r '.wifiPosition.latitude // "N/A"')
        longitude=$(echo "$response" | jq -r '.wifiPosition.longitude // "N/A"')
        altitude=$(echo "$response" | jq -r '.wifiPosition.altitude // "N/A"')
        horizontal_accuracy=$(echo "$response" | jq -r '.wifiPosition.horizontalAccuracy // "N/A"')
        vertical_accuracy=$(echo "$response" | jq -r '.wifiPosition.verticalAccuracy // "N/A"')
        confidence=$(echo "$response" | jq -r '.wifiPosition.confidence // "N/A"')
        ap_count=$(echo "$response" | jq -r '.wifiPosition.apCount // "N/A"')
        calculation_time=$(echo "$response" | jq -r '.wifiPosition.calculationTimeMs // "N/A"')
        methods_used=$(echo "$response" | jq -r '.wifiPosition.methodsUsed // []' | jq -r 'join(", ")')
        
        echo -e "  ${YELLOW}Latitude:${NC} $latitude"
        echo -e "  ${YELLOW}Longitude:${NC} $longitude"
        echo -e "  ${YELLOW}Altitude:${NC} $altitude"
        echo -e "  ${YELLOW}Horizontal Accuracy:${NC} $horizontal_accuracy meters"
        echo -e "  ${YELLOW}Vertical Accuracy:${NC} $vertical_accuracy meters"
        echo -e "  ${YELLOW}Confidence:${NC} $confidence"
        echo -e "  ${YELLOW}AP Count:${NC} $ap_count"
        echo -e "  ${YELLOW}Calculation Time:${NC} $calculation_time ms"
        echo -e "  ${YELLOW}Methods Used:${NC} $methods_used"
        
        # Extract calculation info summary
        echo -e "\n${GREEN}Calculation Info Summary:${NC}"
        calc_info_exists=$(echo "$response" | jq -r 'has("calculationInfo")')
        if [ "$calc_info_exists" = "true" ]; then
            total_aps=$(echo "$response" | jq -r '.calculationInfo.accessPointSummary.total // "N/A"')
            used_aps=$(echo "$response" | jq -r '.calculationInfo.accessPointSummary.used // "N/A"')
            filtered_aps=$(echo "$response" | jq -r '.calculationInfo.accessPointSummary.filtered // "N/A"')
            
            echo -e "  ${YELLOW}Total APs Processed:${NC} $total_aps"
            echo -e "  ${YELLOW}APs Used in Calculation:${NC} $used_aps"
            echo -e "  ${YELLOW}APs Filtered Out:${NC} $filtered_aps"
            
            # Show status counts
            echo -e "\n${GREEN}Status Counts:${NC}"
            echo "$response" | jq -r '.calculationInfo.accessPointSummary.statusCounts // {}' | jq -r 'to_entries[] | "  " + .key + ": " + (.value | tostring)'
            
            # Show algorithm selection info
            echo -e "\n${GREEN}Algorithm Selection:${NC}"
            echo "$response" | jq -r '.calculationInfo.algorithmSelection[]? | "  " + .algorithm + " (selected: " + (.selected | tostring) + ", weight: " + (.weight | tostring) + ")"'
            
            # Show selection context
            echo -e "\n${GREEN}Selection Context:${NC}"
            signal_quality=$(echo "$response" | jq -r '.calculationInfo.selectionContext.signalQuality // "N/A"')
            geometric_quality=$(echo "$response" | jq -r '.calculationInfo.selectionContext.geometricQuality // "N/A"')
            distribution_pattern=$(echo "$response" | jq -r '.calculationInfo.selectionContext.distributionPattern // "N/A"')
            
            echo -e "  ${YELLOW}Signal Quality:${NC} $signal_quality"
            echo -e "  ${YELLOW}Geometric Quality:${NC} $geometric_quality"
            echo -e "  ${YELLOW}Distribution Pattern:${NC} $distribution_pattern"
        else
            echo -e "  ${RED}No calculation info found in response${NC}"
        fi
    else
        echo -e "\n${RED}Request failed with result: $result${NC}"
    fi
    
else
    echo -e "\n${RED}✗ API call failed${NC}"
    echo "Response: $response"
fi

echo -e "\n${CYAN}====================================================${NC}"
echo -e "${CYAN}  TEST COMPLETED${NC}"
echo -e "${CYAN}====================================================${NC}"



