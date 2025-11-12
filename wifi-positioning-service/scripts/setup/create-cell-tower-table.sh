#!/bin/bash

# Delete the table if it exists
echo "Deleting existing cell tower table if it exists..."
aws dynamodb delete-table \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local || true

# Wait for table to be deleted
echo "Waiting for cell tower table to be deleted..."
aws dynamodb wait table-not-exists \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local || true

# Create the table
echo "Creating cell tower table..."
aws dynamodb create-table \
    --cli-input-json file://cell-tower-schema.json \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

# Wait for table to be active
echo "Waiting for cell tower table to be created..."
aws dynamodb wait table-exists \
    --table-name wifi-cell-tower-location \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

echo "Cell tower table created successfully!"





