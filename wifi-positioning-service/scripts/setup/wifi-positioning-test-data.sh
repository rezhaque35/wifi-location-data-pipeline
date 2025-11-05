#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ==============================================================================
# WiFi Positioning Service Test Data Loader
# ==============================================================================
#
# IMPORTANT: Coordinate Design for Centroid Filtering
# ------------------------------------------------------------------------------
# When cell tower information is NOT available, the positioning service applies
# centroid-based outlier filtering with a 500m threshold. Access points that are
# more than 500m from the geographic centroid of all APs in a request are
# discarded as outliers.
#
# All coordinates in this file are designed to ensure that APs used together in
# test cases remain within 400m of their centroid (100m safety margin).
#
# Centroid Analysis Results (as of implementation):
# - Maximum distance from centroid across all test cases: 113.4m
# - All test cases: PASS (well within 500m threshold)
#
# Location: San Francisco, CA (37.77°N, 122.42°W)
# At this latitude: ~1 degree ≈ 111 km, so 400m ≈ 0.0036 degrees
# ==============================================================================

echo -e "${GREEN}Starting to load test data into DynamoDB Local...${NC}"

# ==============================================================================
# SECTION 1: Basic Algorithm Test APs (01-05)
# Used in: Test Cases 1-5
# Centroid distances: All within 14.2m of their respective test centroids
# ==============================================================================

# Test Case 1: Single AP - Proximity Detection
# AP 01: Used alone (no centroid filtering for single AP)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:01"},
        "version": {"S": "20240411-120000"},
        "latitude": {"N": "37.7749"},
        "longitude": {"N": "-122.4194"},
        "altitude": {"N": "10.5"},
        "horizontal_accuracy": {"N": "50.0"},
        "vertical_accuracy": {"N": "8.0"},
        "confidence": {"N": "0.65"},
        "ssid": {"S": "SingleAP_Test"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Cisco"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# Test Case 2: Two APs - RSSI Ratio Method  
# APs 02-03: Used together (max distance from centroid: 7.1m)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:02"},
        "version": {"S": "20240411-120100"},
        "latitude": {"N": "37.7750"},
        "longitude": {"N": "-122.4195"},
        "altitude": {"N": "12.5"},
        "horizontal_accuracy": {"N": "25.0"},
        "vertical_accuracy": {"N": "5.0"},
        "confidence": {"N": "0.78"},
        "ssid": {"S": "DualAP_Test"},
        "frequency": {"N": "5180"},
        "vendor": {"S": "Aruba"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# Test Case 3: Three APs - Trilateration (Well Distributed)
# APs 03-05: Used together (max distance from centroid: 14.2m)
# Note: AP 03 also used in Test Case 2
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:03"},
        "version": {"S": "20240411-120200"},
        "latitude": {"N": "37.7751"},
        "longitude": {"N": "-122.4196"},
        "altitude": {"N": "15.0"},
        "horizontal_accuracy": {"N": "8.5"},
        "vertical_accuracy": {"N": "3.0"},
        "confidence": {"N": "0.92"},
        "ssid": {"S": "TriAP_Test"},
        "frequency": {"N": "2462"},
        "vendor": {"S": "Ubiquiti"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# APs 04-05: Also used in Test Case 3
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:04"},
        "version": {"S": "20240411-120300"},
        "latitude": {"N": "37.7752"},
        "longitude": {"N": "-122.4197"},
        "altitude": {"N": "18.0"},
        "horizontal_accuracy": {"N": "15.5"},
        "vertical_accuracy": {"N": "4.0"},
        "confidence": {"N": "0.85"},
        "ssid": {"S": "MultiAP_Test"},
        "frequency": {"N": "5240"},
        "vendor": {"S": "TP-Link"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# Test Case 5: Weak Signals Scenario
# AP 05: Used alone (no centroid filtering for single AP)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:05"},
        "version": {"S": "20240411-120400"},
        "latitude": {"N": "37.7753"},
        "longitude": {"N": "-122.4198"},
        "altitude": {"N": "20.0"},
        "horizontal_accuracy": {"N": "35.0"},
        "vertical_accuracy": {"N": "10.0"},
        "confidence": {"N": "0.45"},
        "ssid": {"S": "WeakSignal_Test"},
        "frequency": {"N": "2412"},
        "vendor": {"S": "Netgear"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "warning"}
    }'

# ==============================================================================
# SECTION 2: Collinear APs (06-10)
# Used in: Test Cases 6-10, Test Case 4 (uses 06-07), Missing Frequency Test
# Pattern: Collinear arrangement along latitude line
# Centroid distances: Max 11.1m (Test 6-10), 22.7m (Test 4 with APs 04-07)
# ==============================================================================

# Test Case 6-10: Collinear APs Scenario (Testing geometric distribution impact)
# APs 06-08: Used together in Test 6-10 (max distance from centroid: 11.1m)
# APs 06-07: Also used in Test Case 4 with APs 04-05
for i in {6..10}; do
    # Format the mac address and version with leading zeros
    padded_i=$(printf "%02d" $i)
    
    # Calculate values separately to avoid bc issues
    lat=$(echo "scale=6; 37.7754 + ($i-6)*0.0001" | bc)
    alt=$(echo "scale=1; 15.0 + ($i-6)*2" | bc)
    sig=$(echo "scale=1; -70.0 + ($i-6)*2" | bc)
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$padded_i'"},
            "version": {"S": "20240411-120'$padded_i'00"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "-122.4194"},
            "altitude": {"N": "'$alt'"},
            "horizontal_accuracy": {"N": "18.5"},
            "vertical_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.72"},
            "ssid": {"S": "Collinear_Test_'$padded_i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Cisco"},
            "signal_strength_avg": {"N": "'$sig'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# ==============================================================================
# SECTION 3: High Density Cluster (11-15)
# Used in: Test Cases 11-15
# Pattern: Diagonal cluster with both lat/lon varying
# Centroid distances: Max 42.5m
# ==============================================================================

# Test Case 11-15: High Density AP Cluster (Testing maximum likelihood in dense environments)
# APs 11-14: Used together (max distance from centroid: 42.5m)
for i in {11..15}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7760 + ($i-11)*0.0002" | bc)'"},
            "longitude": {"N": "'$(echo "-122.4200 + ($i-11)*0.0002" | bc)'"},
            "altitude": {"N": "25.0"},
            "horizontal_accuracy": {"N": "12.0"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "0.88"},
            "ssid": {"S": "HighDensity_Test_'$i'"},
            "frequency": {"N": "5320"},
            "vendor": {"S": "Aruba"},
            "signal_strength_avg": {"N": "'$(echo "-65.0 + ($i-11)*1.5" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# ==============================================================================
# SECTION 4: Mixed Signal Quality (16-20)
# Used in: Test Cases 16-20
# Pattern: Spread with varying signal quality
# Centroid distances: Max 34.5m
# ==============================================================================

# Test Case 16-20: Mixed Signal Quality Scenario
# APs 16-18: Used together (max distance from centroid: 34.5m)
for i in {16..20}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7770 + ($i-16)*0.0003" | bc)'"},
            "longitude": {"N": "'$(echo "-122.4210 + ($i-16)*0.0001" | bc)'"},
            "altitude": {"N": "'$(echo "30.0 + ($i-16)*1.5" | bc)'"},
            "horizontal_accuracy": {"N": "'$(echo "15.0 + ($i-16)*3" | bc)'"},
            "vertical_accuracy": {"N": "6.0"},
            "confidence": {"N": "'$(echo "0.90 - ($i-16)*0.1" | bc)'"},
            "ssid": {"S": "MixedSignal_Test_'$i'"},
            "frequency": {"N": "'$((2412 + (i-16)*5))'"},
            "vendor": {"S": "Ubiquiti"},
            "signal_strength_avg": {"N": "'$(echo "-60.0 - ($i-16)*5" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# ==============================================================================
# SECTION 5: Temporal and Environmental Test Cases (21-35)
# ==============================================================================

# Test Case 21-25: Time Series Data (Testing temporal variations)
# APs 21-22: Used together (co-located at same coordinates)
for i in {21..25}; do
    hour=$((12 + i-21))
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "37.7780"},
            "longitude": {"N": "-122.4220"},
            "altitude": {"N": "22.0"},
            "horizontal_accuracy": {"N": "'$(echo "10.0 + ($i-21)*2" | bc)'"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "'$(echo "0.85 - ($i-21)*0.05" | bc)'"},
            "ssid": {"S": "TimeSeries_Test"},
            "frequency": {"N": "5500"},
            "vendor": {"S": "TP-Link"},
            "signal_strength_avg": {"N": "'$(echo "-70.0 + ($hour-12)*2" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 26-30: Log-Distance Path Loss Algorithm Scenarios
# APs 26-27: Used together (max distance from centroid: 5.6m)
for i in {26..30}; do
    distance=$((i-25))  # Distance in meters from reference point
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7790 + ($distance*0.0001)" | bc)'"},
            "longitude": {"N": "-122.4230"},
            "altitude": {"N": "20.0"},
            "horizontal_accuracy": {"N": "'$(echo "5.0 + ($distance*2)" | bc)'"},
            "confidence": {"N": "'$(echo "0.95 - ($distance*0.05)" | bc)'"},
            "ssid": {"S": "PathLoss_Test_'$i'"},
            "frequency": {"N": "2462"},
            "signal_strength_avg": {"N": "'$(echo "-50.0 - ($distance*3)" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 31-35: Stable Signal Quality Scenarios
# APs 31-32: Used together (co-located at same coordinates)
for i in {31..35}; do
    days_ago=$((i-30))
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "37.7800"},
            "longitude": {"N": "-122.4240"},
            "altitude": {"N": "25.0"},
            "horizontal_accuracy": {"N": "8.0"},
            "confidence": {"N": "0.88"},
            "ssid": {"S": "Historical_Test"},
            "frequency": {"N": "5500"},
            "signal_strength_avg": {"N": "-68.0"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# ==============================================================================
# SECTION 6: Error Cases and Edge Scenarios (36-45)
# ==============================================================================

# Test Case 36-40: Error Cases and Edge Scenarios
for i in {36..40}; do
    case $((i-35)) in
        1)  # Cell tower outlier - 2000m north of cell tower 1002 (outside 1500m effective range)
            lat="37.79305"
            lon="-122.41955"
            status="active"
            ;;
        2)  # Expired TTL
            lat="37.7810"
            lon="-122.4250"
            status="expired"
            ;;
        3)  # Insufficient data
            lat="37.7811"
            lon="-122.4251"
            status="error"
            ;;
        4)  # Algorithm failure
            lat="37.7812"
            lon="-122.4252"
            status="error"
            ;;
        5)  # Calibration required
            lat="37.7813"
            lon="-122.4253"
            status="warning"
            ;;
    esac

    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "0.0"},
            "horizontal_accuracy": {"N": "999.9"},
            "confidence": {"N": "0.1"},
            "ssid": {"S": "ErrorCase_'$i'"},
            "frequency": {"N": "2412"},
            "signal_strength_avg": {"N": "-99.9"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "'$status'"}
        }'
done

# Test Case 41-45: Mixed Status APs (For Status Filtering Tests)
# APs 41-45: Used together (co-located at same coordinates)
for i in {41..45}; do
    case $((i-40)) in
        1)  # Active status
            status="active"
            ;;
        2)  # Warning status
            status="warning"
            ;;
        3)  # Error status
            status="error"
            ;;
        4)  # Expired status
            status="expired"
            ;;
        5)  # WiFi-Hotspot status
            status="wifi-hotspot"
            ;;
    esac

    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "37.7820"},
            "longitude": {"N": "-122.4260"},
            "altitude": {"N": "15.0"},
            "horizontal_accuracy": {"N": "20.0"},
            "vertical_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.75"},
            "ssid": {"S": "StatusTest_'$i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Generic"},
            "signal_strength_avg": {"N": "-70.0"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "'$status'"}
        }'
done

echo -e "${GREEN}Test data loaded successfully.${NC}"

# ==============================================================================
# SECTION 7: 2D Positioning Tests (50-57)
# Used in: 2D positioning test cases (null altitude data)
# Centroid distances: Max 113.4m (Test 2D-3)
# ==============================================================================

# Test Case 50-55: Missing Altitude Data Tests
# These test cases have null altitude and verticalAccuracy
# to test 2D-only positioning algorithms
echo -e "${YELLOW}Adding test data for 2D positioning tests (null altitude)...${NC}"

# Test Case 50: Single AP with null altitude (Proximity Detection)
# AP 50: Used alone (no centroid filtering for single AP)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "AA:BB:CC:00:00:50"},
        "version": {"S": "20240411-120050"},
        "latitude": {"N": "37.7750"},
        "longitude": {"N": "-122.4194"},
        "horizontal_accuracy": {"N": "10.0"},
        "confidence": {"N": "0.80"},
        "ssid": {"S": "2D_SingleAP_Test"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Cisco"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# Test Case 51-52: Two APs with null altitude (RSSI Ratio and Weighted Centroid)
# APs 51-52: Used together (max distance from centroid: 35.4m)
for i in {51..52}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:00:00:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7750 + ($i-51)*0.0005" | bc)'"},
            "longitude": {"N": "'$(echo "-122.4194 + ($i-51)*0.0005" | bc)'"},
            "horizontal_accuracy": {"N": "8.0"},
            "confidence": {"N": "0.80"},
            "ssid": {"S": "2D_DualAP_Test"},
            "frequency": {"N": "'$((2437 + (i-51)*40))'"},
            "vendor": {"S": "Cisco"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 53-55: Three APs with null altitude (Trilateration, Maximum Likelihood)
# APs 53-55: Used together (max distance from centroid: 113.4m)
for i in {53..55}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:00:00:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7755 + ($i-53)*0.0008" | bc)'"},
            "longitude": {"N": "'$(echo "-122.4196 + ($i-53)*0.0008" | bc)'"},
            "horizontal_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.85"},
            "ssid": {"S": "2D_TriAP_Test"},
            "frequency": {"N": "'$((2437 + (i-53)*20))'"},
            "vendor": {"S": "Aruba"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

echo -e "${GREEN}2D positioning test data loaded successfully.${NC}"

# Test Case 56-57: Mixed Data Test (One AP with altitude, one without)
# APs 56-57: Used together (max distance from centroid: 35.4m)
# This tests the behavior when some APs have altitude data and some don't
echo -e "${YELLOW}Adding test data for mixed 2D/3D positioning tests...${NC}"

# AP with altitude data
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "AA:BB:CC:00:00:56"},
        "version": {"S": "20240411-120056"},
        "latitude": {"N": "37.7760"},
        "longitude": {"N": "-122.4195"},
        "altitude": {"N": "30.0"},
        "horizontal_accuracy": {"N": "8.0"},
        "vertical_accuracy": {"N": "5.0"},
        "confidence": {"N": "0.85"},
        "ssid": {"S": "Mixed_2D3D_Test"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Aruba"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# AP without altitude data
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "AA:BB:CC:00:00:57"},
        "version": {"S": "20240411-120057"},
        "latitude": {"N": "37.7765"},
        "longitude": {"N": "-122.4190"},
        "horizontal_accuracy": {"N": "6.0"},
        "confidence": {"N": "0.80"},
        "ssid": {"S": "Mixed_2D3D_Test"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Cisco"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

echo -e "${GREEN}Mixed 2D/3D positioning test data loaded successfully.${NC}"

# ==============================================================================
# SECTION 8: Additional Edge Case Tests
# ==============================================================================

# Test Case 38: Very Weak Signal Test
# AP 55: Used alone (no centroid filtering for single AP)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:55"},
        "version": {"S": "20240411-120055"},
        "latitude": {"N": "37.7844"},
        "longitude": {"N": "-122.4276"},
        "altitude": {"N": "15.0"},
        "horizontal_accuracy": {"N": "10.0"},
        "vertical_accuracy": {"N": "0.0"},
        "confidence": {"N": "0.0"},
        "ssid": {"S": "TestAP1"},
        "frequency": {"N": "2412"},
        "vendor": {"S": "Generic"},
        "signal_strength_avg": {"N": "-99.9"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# ==============================================================================
# SECTION 9: Status Filtering Test Cases (Test Cases 40-45)
# ==============================================================================

# Test Case 40-45: Status Filtering Tests
# Location: 37.7820, -122.4260
# These APs test the filtering of different statuses

# AP 41: active status (VALID - should be used)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:41"},
        "version": {"S": "20240411-120041"},
        "latitude": {"N": "37.7820"},
        "longitude": {"N": "-122.4260"},
        "altitude": {"N": "12.0"},
        "horizontal_accuracy": {"N": "15.0"},
        "vertical_accuracy": {"N": "4.0"},
        "confidence": {"N": "0.80"},
        "ssid": {"S": "StatusTest_41"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Cisco"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# AP 42: warning status (VALID - should be used)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:42"},
        "version": {"S": "20240411-120042"},
        "latitude": {"N": "37.7820"},
        "longitude": {"N": "-122.4260"},
        "altitude": {"N": "12.0"},
        "horizontal_accuracy": {"N": "15.0"},
        "vertical_accuracy": {"N": "4.0"},
        "confidence": {"N": "0.75"},
        "ssid": {"S": "StatusTest_42"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Aruba"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "warning"}
    }'

# AP 43: error status (INVALID - should be filtered)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:43"},
        "version": {"S": "20240411-120043"},
        "latitude": {"N": "37.7820"},
        "longitude": {"N": "-122.4260"},
        "altitude": {"N": "12.0"},
        "horizontal_accuracy": {"N": "15.0"},
        "vertical_accuracy": {"N": "4.0"},
        "confidence": {"N": "0.70"},
        "ssid": {"S": "StatusTest_43"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "TP-Link"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "error"}
    }'

# AP 44: expired status (INVALID - should be filtered)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:44"},
        "version": {"S": "20240411-120044"},
        "latitude": {"N": "37.7820"},
        "longitude": {"N": "-122.4260"},
        "altitude": {"N": "12.0"},
        "horizontal_accuracy": {"N": "15.0"},
        "vertical_accuracy": {"N": "4.0"},
        "confidence": {"N": "0.65"},
        "ssid": {"S": "StatusTest_44"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Ubiquiti"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "expired"}
    }'

# AP 45: wifi-hotspot status (INVALID - should be filtered)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:45"},
        "version": {"S": "20240411-120045"},
        "latitude": {"N": "37.7820"},
        "longitude": {"N": "-122.4260"},
        "altitude": {"N": "12.0"},
        "horizontal_accuracy": {"N": "15.0"},
        "vertical_accuracy": {"N": "4.0"},
        "confidence": {"N": "0.60"},
        "ssid": {"S": "StatusTest_45"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Linksys"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "wifi-hotspot"}
    }' 