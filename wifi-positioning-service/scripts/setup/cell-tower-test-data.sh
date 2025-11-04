#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ==============================================================================
# Cell Tower Test Data Loader
# ==============================================================================
#
# This script loads cell tower data into DynamoDB for testing cell tower-based
# filtering functionality. Cell towers are positioned near the centroids of
# each test case's access point groups.
#
# Cell Tower Positioning Strategy:
# - Cell towers are positioned at or near the centroid of each test case's AP group
# - Cell tower range is set to 1000m, giving effective range = 1000m + 500m = 1500m
# - This ensures all valid APs (within ~113m of centroid) are included
# - Outlier APs positioned 600m+ away will be filtered out
#
# Location: San Francisco, CA (37.77°N, 122.42°W)
# ==============================================================================

echo -e "${GREEN}Starting to load cell tower test data into DynamoDB Local...${NC}"

TIMESTAMP=$(date +%Y-%m-%dT%H:%M:%SZ)

# ==============================================================================
# SECTION 1: Basic Algorithm Test Cases (Test Cases 1-5)
# ==============================================================================

# Test Case 1: Single AP - Proximity Detection
# Cell tower at AP location (single AP, no centroid calculation needed)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1001"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7749"},
        "longitude": {"N": "-122.4194"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 2: Two APs - RSSI Ratio Method
# Cell tower at centroid of APs 02-03 (~37.77505, -122.41955)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1002"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.77505"},
        "longitude": {"N": "-122.41955"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 3: Three APs - Trilateration
# Cell tower at centroid of APs 03-05 (~37.7752, -122.4197)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1003"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7752"},
        "longitude": {"N": "-122.4197"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 4: Multiple APs - Maximum Likelihood
# Cell tower at centroid of APs 04-07 (~37.7755, -122.41965)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1004"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7755"},
        "longitude": {"N": "-122.41965"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 5: Weak Signals
# Cell tower at AP location (single AP)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1005"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7753"},
        "longitude": {"N": "-122.4198"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# ==============================================================================
# SECTION 2: Advanced Scenario Test Cases (Test Cases 6-20)
# ==============================================================================

# Test Case 6-10: Collinear APs
# Cell tower at centroid of APs 06-08 (~37.7756, -122.4194)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1006"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7756"},
        "longitude": {"N": "-122.4194"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 11-15: High Density AP Cluster
# Cell tower at centroid of APs 11-14 (~37.7762, -122.4202)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1011"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7762"},
        "longitude": {"N": "-122.4202"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 16-20: Mixed Signal Quality
# Cell tower at centroid of APs 16-18 (~37.7772, -122.4211)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1016"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7772"},
        "longitude": {"N": "-122.4211"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# ==============================================================================
# SECTION 3: Temporal and Environmental Test Cases (Test Cases 21-35)
# ==============================================================================

# Test Case 21-25: Time Series Data
# Cell tower at AP location (APs 21-22 are co-located at 37.7780, -122.4220)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1021"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7780"},
        "longitude": {"N": "-122.4220"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 26-30: Log-Distance Path Loss
# Cell tower at centroid of APs 26-27 (~37.77905, -122.4230)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1026"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.77905"},
        "longitude": {"N": "-122.4230"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 31-35: Stable Signal Quality
# Cell tower at AP location (APs 31-32 are co-located at 37.7800, -122.4240)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1031"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7800"},
        "longitude": {"N": "-122.4240"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# ==============================================================================
# SECTION 4: Status Filtering Test Cases (Test Case 40)
# ==============================================================================

# Test Case 40: Mixed Status APs
# Cell tower at AP location (APs 41-45 are co-located at 37.7820, -122.4260)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1040"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7820"},
        "longitude": {"N": "-122.4260"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# ==============================================================================
# SECTION 5: 2D Positioning Test Cases (Test Cases 50-57)
# ==============================================================================

# Test Case 50: Single AP with null altitude
# Cell tower at AP location (37.7750, -122.4194)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1050"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7750"},
        "longitude": {"N": "-122.4194"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 51-52: Two APs with null altitude
# Cell tower at centroid of APs 51-52 (~37.77525, -122.41915)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1051"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.77525"},
        "longitude": {"N": "-122.41915"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 53-55: Three APs with null altitude
# Cell tower at centroid of APs 53-55 (~37.7759, -122.4196)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1053"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7759"},
        "longitude": {"N": "-122.4196"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# Test Case 56-57: Mixed 2D/3D Data
# Cell tower at centroid of APs 56-57 (~37.77625, -122.41925)
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1056"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.77625"},
        "longitude": {"N": "-122.41925"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

# ==============================================================================
# SECTION 6: Status Filtering Test Cases (Test Cases 40-45)
# ==============================================================================

# Test Case 40-45: Status Filtering Tests
# Cell tower at location of APs 41-45 (37.7820, -122.4260)
# These APs have different statuses: active, warning, error, expired, wifi-hotspot
aws dynamodb put-item \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "cellId": {"S": "1041"},
        "cellType": {"S": "LTE"},
        "latitude": {"N": "37.7820"},
        "longitude": {"N": "-122.4260"},
        "radius": {"N": "1000"},
        "createdAt": {"S": "'$TIMESTAMP'"},
        "updatedAt": {"S": "'$TIMESTAMP'"}
    }'

echo -e "${GREEN}Cell tower test data loaded successfully.${NC}"


