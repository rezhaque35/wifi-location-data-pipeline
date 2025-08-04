#!/bin/bash

echo "Cleaning up DynamoDB Local environment..."

# Step 1: Check if DynamoDB Local is running
echo "Checking DynamoDB Local status..."
CONTAINER_ID=$(docker ps -q --filter "ancestor=amazon/dynamodb-local")

if [ -z "$CONTAINER_ID" ]; then
    echo "DynamoDB Local is not running."
else
    echo "Found DynamoDB Local container: $CONTAINER_ID"
    
    # Step 2: Delete the table if it exists
    echo "Deleting wifi_access_points table..."
    aws dynamodb delete-table \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local 2>/dev/null || echo "Table does not exist or already deleted"
    
    # Step 3: Wait for table to be deleted
    echo "Waiting for table deletion to complete..."
    aws dynamodb wait table-not-exists \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local 2>/dev/null || echo "Table deletion completed"
    
    # Step 4: Stop the DynamoDB Local container
    echo "Stopping DynamoDB Local container..."
    docker stop $CONTAINER_ID
    
    # Step 5: Remove the container
    echo "Removing DynamoDB Local container..."
    docker rm $CONTAINER_ID
    
    echo "DynamoDB Local container stopped and removed."
fi

# Step 6: Optional - Remove the DynamoDB Local image (uncomment if needed)
# echo "Removing DynamoDB Local image..."
# docker rmi amazon/dynamodb-local 2>/dev/null || echo "Image not found or already removed"

# Step 7: Clean up AWS credentials (optional - uncomment if you want to remove them)
# echo "Cleaning up AWS credentials..."
# rm -f ~/.aws/credentials
# rm -f ~/.aws/config

echo "Cleanup complete!"
echo "DynamoDB Local environment has been cleaned up." 