#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ==============================================================================
# Extended WiFi Positioning Test Data Loader
# ==============================================================================
#
# This script loads additional access points that will be filtered out by
# cell tower or geographic centroid filtering. These outlier APs are positioned
# 600m+ from the centroids of test case AP groups.
#
# Outlier AP Positioning Strategy:
# - Outliers positioned 600m+ from centroids (for centroid filtering)
# - Outliers positioned outside cellRange + 500m = 1500m (for cell tower filtering)
# - At San Francisco latitude (37.77°N): 600m ≈ 0.0054 degrees, 700m ≈ 0.0063 degrees
# - Use MAC addresses: AA:BB:CC:TEST:XX:YY (XX:YY = test case : outlier number)
#
# Location: San Francisco, CA (37.77°N, 122.42°W)
# ==============================================================================

echo -e "${YELLOW}Adding outlier access points for filtering tests...${NC}"

# ==============================================================================
# SECTION 1: Basic Algorithm Test Cases - Outlier APs (Test Cases 1-5)
# ==============================================================================

# Test Case 1: Single AP - Outliers positioned 700m north and south
# Centroid: 37.7749, -122.4194
# Outlier 1: 700m north (37.7749 + 0.0063 = 37.7812)
# Outlier 2: 700m south (37.7749 - 0.0063 = 37.7686)
for i in {1..2}; do
    if [ $i -eq 1 ]; then
        lat=$(echo "37.7749 + 0.0063" | bc)
        lon="-122.4194"
    else
        lat=$(echo "37.7749 - 0.0063" | bc)
        lon="-122.4194"
    fi
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:01:0'$i'"},
            "version": {"S": "20240411-130001"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "10.0"},
            "horizontal_accuracy": {"N": "50.0"},
            "vertical_accuracy": {"N": "8.0"},
            "confidence": {"N": "0.65"},
            "ssid": {"S": "Outlier_Test_01_0'$i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Cisco"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 2: Two APs - Outliers positioned 700m from centroid
# Centroid: ~37.77505, -122.41955
for i in {1..3}; do
    case $i in
        1)  # North
            lat=$(echo "37.77505 + 0.0063" | bc)
            lon="-122.41955"
            ;;
        2)  # East
            lat="37.77505"
            lon=$(echo "-122.41955 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.77505 - 0.0063" | bc)
            lon="-122.41955"
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:02:0'$i'"},
            "version": {"S": "20240411-130002"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "12.0"},
            "horizontal_accuracy": {"N": "25.0"},
            "vertical_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.78"},
            "ssid": {"S": "Outlier_Test_02_0'$i'"},
            "frequency": {"N": "5180"},
            "vendor": {"S": "Aruba"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 3: Three APs - Outliers positioned 700m from centroid
# Centroid: ~37.7752, -122.4197
for i in {1..4}; do
    case $i in
        1)  # North
            lat=$(echo "37.7752 + 0.0063" | bc)
            lon="-122.4197"
            ;;
        2)  # East
            lat="37.7752"
            lon=$(echo "-122.4197 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.7752 - 0.0063" | bc)
            lon="-122.4197"
            ;;
        4)  # West
            lat="37.7752"
            lon=$(echo "-122.4197 - 0.0063" | bc)
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:03:0'$i'"},
            "version": {"S": "20240411-130003"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "15.0"},
            "horizontal_accuracy": {"N": "8.5"},
            "vertical_accuracy": {"N": "3.0"},
            "confidence": {"N": "0.92"},
            "ssid": {"S": "Outlier_Test_03_0'$i'"},
            "frequency": {"N": "2462"},
            "vendor": {"S": "Ubiquiti"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 4: Multiple APs - Outliers positioned 700m from centroid
# Centroid: ~37.7755, -122.41965
for i in {1..5}; do
    case $i in
        1)  # North
            lat=$(echo "37.7755 + 0.0063" | bc)
            lon="-122.41965"
            ;;
        2)  # East
            lat="37.7755"
            lon=$(echo "-122.41965 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.7755 - 0.0063" | bc)
            lon="-122.41965"
            ;;
        4)  # West
            lat="37.7755"
            lon=$(echo "-122.41965 - 0.0063" | bc)
            ;;
        5)  # Northeast
            lat=$(echo "37.7755 + 0.0045" | bc)
            lon=$(echo "-122.41965 + 0.0045" | bc)
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:04:0'$i'"},
            "version": {"S": "20240411-130004"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "18.0"},
            "horizontal_accuracy": {"N": "15.5"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "0.85"},
            "ssid": {"S": "Outlier_Test_04_0'$i'"},
            "frequency": {"N": "5240"},
            "vendor": {"S": "TP-Link"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 5: Weak Signals - Outliers positioned 700m from AP
# Centroid: 37.7753, -122.4198
for i in {1..2}; do
    if [ $i -eq 1 ]; then
        lat=$(echo "37.7753 + 0.0063" | bc)
        lon="-122.4198"
    else
        lat=$(echo "37.7753 - 0.0063" | bc)
        lon="-122.4198"
    fi
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:05:0'$i'"},
            "version": {"S": "20240411-130005"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "20.0"},
            "horizontal_accuracy": {"N": "35.0"},
            "vertical_accuracy": {"N": "10.0"},
            "confidence": {"N": "0.45"},
            "ssid": {"S": "Outlier_Test_05_0'$i'"},
            "frequency": {"N": "2412"},
            "vendor": {"S": "Netgear"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "warning"}
        }'
done

# ==============================================================================
# SECTION 2: Advanced Scenario Test Cases - Outlier APs (Test Cases 6-20)
# ==============================================================================

# Test Case 6-10: Collinear APs - Outliers positioned 700m from centroid
# Centroid: ~37.7756, -122.4194
for i in {1..3}; do
    case $i in
        1)  # North
            lat=$(echo "37.7756 + 0.0063" | bc)
            lon="-122.4194"
            ;;
        2)  # East
            lat="37.7756"
            lon=$(echo "-122.4194 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.7756 - 0.0063" | bc)
            lon="-122.4194"
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:06:0'$i'"},
            "version": {"S": "20240411-130006"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "15.0"},
            "horizontal_accuracy": {"N": "18.5"},
            "vertical_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.72"},
            "ssid": {"S": "Outlier_Test_06_0'$i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Cisco"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 11-15: High Density AP Cluster - Outliers positioned 700m from centroid
# Centroid: ~37.7762, -122.4202
for i in {1..4}; do
    case $i in
        1)  # North
            lat=$(echo "37.7762 + 0.0063" | bc)
            lon="-122.4202"
            ;;
        2)  # East
            lat="37.7762"
            lon=$(echo "-122.4202 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.7762 - 0.0063" | bc)
            lon="-122.4202"
            ;;
        4)  # West
            lat="37.7762"
            lon=$(echo "-122.4202 - 0.0063" | bc)
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:11:0'$i'"},
            "version": {"S": "20240411-130011"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "25.0"},
            "horizontal_accuracy": {"N": "12.0"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "0.88"},
            "ssid": {"S": "Outlier_Test_11_0'$i'"},
            "frequency": {"N": "5320"},
            "vendor": {"S": "Aruba"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 16-20: Mixed Signal Quality - Outliers positioned 700m from centroid
# Centroid: ~37.7772, -122.4211
for i in {1..3}; do
    case $i in
        1)  # North
            lat=$(echo "37.7772 + 0.0063" | bc)
            lon="-122.4211"
            ;;
        2)  # East
            lat="37.7772"
            lon=$(echo "-122.4211 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.7772 - 0.0063" | bc)
            lon="-122.4211"
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:16:0'$i'"},
            "version": {"S": "20240411-130016"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "30.0"},
            "horizontal_accuracy": {"N": "15.0"},
            "vertical_accuracy": {"N": "6.0"},
            "confidence": {"N": "0.90"},
            "ssid": {"S": "Outlier_Test_16_0'$i'"},
            "frequency": {"N": "2412"},
            "vendor": {"S": "Ubiquiti"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# ==============================================================================
# SECTION 3: Temporal and Environmental Test Cases - Outlier APs (Test Cases 21-35)
# ==============================================================================

# Test Case 21-25: Time Series Data - Outliers positioned 700m from centroid
# Centroid: 37.7780, -122.4220
for i in {1..2}; do
    if [ $i -eq 1 ]; then
        lat=$(echo "37.7780 + 0.0063" | bc)
        lon="-122.4220"
    else
        lat=$(echo "37.7780 - 0.0063" | bc)
        lon="-122.4220"
    fi
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:21:0'$i'"},
            "version": {"S": "20240411-130021"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "22.0"},
            "horizontal_accuracy": {"N": "10.0"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "0.85"},
            "ssid": {"S": "Outlier_Test_21_0'$i'"},
            "frequency": {"N": "5500"},
            "vendor": {"S": "TP-Link"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 26-30: Log-Distance Path Loss - Outliers positioned 700m from centroid
# Centroid: ~37.77905, -122.4230
for i in {1..3}; do
    case $i in
        1)  # North
            lat=$(echo "37.77905 + 0.0063" | bc)
            lon="-122.4230"
            ;;
        2)  # East
            lat="37.77905"
            lon=$(echo "-122.4230 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.77905 - 0.0063" | bc)
            lon="-122.4230"
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:26:0'$i'"},
            "version": {"S": "20240411-130026"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "20.0"},
            "horizontal_accuracy": {"N": "5.0"},
            "vertical_accuracy": {"N": "3.0"},
            "confidence": {"N": "0.95"},
            "ssid": {"S": "Outlier_Test_26_0'$i'"},
            "frequency": {"N": "2462"},
            "vendor": {"S": "Cisco"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 31-35: Stable Signal Quality - Outliers positioned 700m from centroid
# Centroid: 37.7800, -122.4240
for i in {1..2}; do
    if [ $i -eq 1 ]; then
        lat=$(echo "37.7800 + 0.0063" | bc)
        lon="-122.4240"
    else
        lat=$(echo "37.7800 - 0.0063" | bc)
        lon="-122.4240"
    fi
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:31:0'$i'"},
            "version": {"S": "20240411-130031"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "25.0"},
            "horizontal_accuracy": {"N": "8.0"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "0.88"},
            "ssid": {"S": "Outlier_Test_31_0'$i'"},
            "frequency": {"N": "5500"},
            "vendor": {"S": "Aruba"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# ==============================================================================
# SECTION 4: Status Filtering Test Cases - Outlier APs (Test Case 40)
# ==============================================================================

# Test Case 40: Mixed Status APs - Outliers positioned 700m from centroid
# Centroid: 37.7820, -122.4260
for i in {1..3}; do
    case $i in
        1)  # North
            lat=$(echo "37.7820 + 0.0063" | bc)
            lon="-122.4260"
            ;;
        2)  # East
            lat="37.7820"
            lon=$(echo "-122.4260 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.7820 - 0.0063" | bc)
            lon="-122.4260"
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:40:0'$i'"},
            "version": {"S": "20240411-130040"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "15.0"},
            "horizontal_accuracy": {"N": "20.0"},
            "vertical_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.75"},
            "ssid": {"S": "Outlier_Test_40_0'$i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Generic"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# ==============================================================================
# SECTION 5: 2D Positioning Test Cases - Outlier APs (Test Cases 50-57)
# ==============================================================================

# Test Case 50: Single AP with null altitude - Outliers positioned 700m from AP
# Centroid: 37.7750, -122.4194
for i in {1..2}; do
    if [ $i -eq 1 ]; then
        lat=$(echo "37.7750 + 0.0063" | bc)
        lon="-122.4194"
    else
        lat=$(echo "37.7750 - 0.0063" | bc)
        lon="-122.4194"
    fi
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:50:0'$i'"},
            "version": {"S": "20240411-130050"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "horizontal_accuracy": {"N": "10.0"},
            "confidence": {"N": "0.80"},
            "ssid": {"S": "Outlier_Test_50_0'$i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Cisco"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 51-52: Two APs with null altitude - Outliers positioned 700m from centroid
# Centroid: ~37.77525, -122.41915
for i in {1..3}; do
    case $i in
        1)  # North
            lat=$(echo "37.77525 + 0.0063" | bc)
            lon="-122.41915"
            ;;
        2)  # East
            lat="37.77525"
            lon=$(echo "-122.41915 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.77525 - 0.0063" | bc)
            lon="-122.41915"
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:51:0'$i'"},
            "version": {"S": "20240411-130051"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "horizontal_accuracy": {"N": "8.0"},
            "confidence": {"N": "0.80"},
            "ssid": {"S": "Outlier_Test_51_0'$i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Cisco"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 53-55: Three APs with null altitude - Outliers positioned 700m from centroid
# Centroid: ~37.7759, -122.4196
for i in {1..4}; do
    case $i in
        1)  # North
            lat=$(echo "37.7759 + 0.0063" | bc)
            lon="-122.4196"
            ;;
        2)  # East
            lat="37.7759"
            lon=$(echo "-122.4196 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.7759 - 0.0063" | bc)
            lon="-122.4196"
            ;;
        4)  # West
            lat="37.7759"
            lon=$(echo "-122.4196 - 0.0063" | bc)
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:53:0'$i'"},
            "version": {"S": "20240411-130053"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "horizontal_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.85"},
            "ssid": {"S": "Outlier_Test_53_0'$i'"},
            "frequency": {"N": "2462"},
            "vendor": {"S": "Aruba"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 56-57: Mixed 2D/3D Data - Outliers positioned 700m from centroid
# Centroid: ~37.77625, -122.41925
for i in {1..3}; do
    case $i in
        1)  # North
            lat=$(echo "37.77625 + 0.0063" | bc)
            lon="-122.41925"
            ;;
        2)  # East
            lat="37.77625"
            lon=$(echo "-122.41925 + 0.0063" | bc)
            ;;
        3)  # South
            lat=$(echo "37.77625 - 0.0063" | bc)
            lon="-122.41925"
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "AA:BB:CC:TEST:56:0'$i'"},
            "version": {"S": "20240411-130056"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "30.0"},
            "horizontal_accuracy": {"N": "8.0"},
            "vertical_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.85"},
            "ssid": {"S": "Outlier_Test_56_0'$i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Aruba"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

echo -e "${GREEN}Extended outlier access point data loaded successfully.${NC}"


