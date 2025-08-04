#!/bin/bash

# Delete the table if it exists
echo "Deleting existing table if it exists..."
aws dynamodb delete-table \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local || true

# Wait for table to be deleted
echo "Waiting for table to be deleted..."
aws dynamodb wait table-not-exists \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local || true

# Create the table
echo "Creating new table..."
aws dynamodb create-table \
    --cli-input-json file://wifi-access-points-schema.json \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

# Wait for table to be active
echo "Waiting for table to be created..."
aws dynamodb wait table-exists \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

# Load the test data
echo "Loading test data..."
source wifi-positioning-test-data.sh

echo "Setup complete!" 