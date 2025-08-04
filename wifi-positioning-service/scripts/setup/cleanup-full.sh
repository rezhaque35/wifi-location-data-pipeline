#!/bin/bash

# Comprehensive cleanup script for DynamoDB Local environment
# 
# This script provides different levels of cleanup for the DynamoDB Local environment:
# - basic: Delete table only (keeps container running for faster re-setup)
# - full:  Delete table and stop container (default - most common use case)
# - reset: Complete cleanup (table, container, image, credentials)
#
# Usage: ./cleanup-full.sh [basic|full|reset]
# Default: full (if no argument provided)

CLEANUP_LEVEL=${1:-full}

echo "DynamoDB Local Cleanup Script"
echo "Cleanup level: $CLEANUP_LEVEL"
echo "================================"

# Function to check if DynamoDB Local is running
# Returns: 0 if running, 1 if not running
# Output: Container ID if found, status message
check_dynamodb_running() {
    CONTAINER_ID=$(docker ps -q --filter "ancestor=amazon/dynamodb-local")
    if [ -n "$CONTAINER_ID" ]; then
        echo "✓ DynamoDB Local is running (Container: $CONTAINER_ID)"
        return 0
    else
        echo "✗ DynamoDB Local is not running"
        return 1
    fi
}

# Function to delete the wifi_access_points table
# This function:
# 1. Attempts to delete the table using AWS CLI
# 2. Waits for deletion to complete
# 3. Provides status feedback
# Note: 2>/dev/null suppresses error messages for non-existent tables
delete_table() {
    echo "Deleting wifi_access_points table..."
    aws dynamodb delete-table \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local 2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo "✓ Table deletion initiated"
        echo "Waiting for table deletion to complete..."
        aws dynamodb wait table-not-exists \
            --table-name wifi_access_points \
            --endpoint-url http://localhost:8000 \
            --profile dynamodb-local 2>/dev/null
        echo "✓ Table deleted successfully"
    else
        echo "ℹ Table does not exist or already deleted"
    fi
}

# Function to stop and remove the DynamoDB Local container
# This function:
# 1. Finds the running DynamoDB Local container
# 2. Stops the container gracefully
# 3. Removes the container to free up resources
# Note: Container removal is necessary to free up the port 8000
stop_container() {
    CONTAINER_ID=$(docker ps -q --filter "ancestor=amazon/dynamodb-local")
    if [ -n "$CONTAINER_ID" ]; then
        echo "Stopping DynamoDB Local container..."
        docker stop $CONTAINER_ID
        echo "Removing DynamoDB Local container..."
        docker rm $CONTAINER_ID
        echo "✓ Container stopped and removed"
    else
        echo "ℹ No running DynamoDB Local container found"
    fi
}

# Function to remove the DynamoDB Local Docker image
# This function:
# 1. Removes the amazon/dynamodb-local image from local cache
# 2. Frees up disk space (~100MB)
# 3. Forces fresh download on next setup (ensures latest version)
# Note: This will require re-downloading the image on next setup
remove_image() {
    echo "Removing DynamoDB Local Docker image..."
    docker rmi amazon/dynamodb-local 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✓ Docker image removed"
    else
        echo "ℹ Docker image not found or already removed"
    fi
}

# Function to clean AWS credentials and config files
# This function:
# 1. Removes the AWS credentials file (~/.aws/credentials)
# 2. Removes the AWS config file (~/.aws/config)
# 3. Provides feedback on what was removed
# Note: This removes the dynamodb-local profile configuration
clean_aws_credentials() {
    echo "Cleaning AWS credentials..."
    if [ -f ~/.aws/credentials ]; then
        rm ~/.aws/credentials
        echo "✓ AWS credentials removed"
    else
        echo "ℹ AWS credentials file not found"
    fi
    
    if [ -f ~/.aws/config ]; then
        rm ~/.aws/config
        echo "✓ AWS config removed"
    else
        echo "ℹ AWS config file not found"
    fi
}

# Main cleanup logic - executes different cleanup levels based on argument
case $CLEANUP_LEVEL in
    "basic")
        echo "Performing basic cleanup (table deletion only)..."
        echo "Note: Container will remain running for faster re-setup"
        if check_dynamodb_running; then
            delete_table
        else
            echo "Cannot delete table - DynamoDB Local is not running"
        fi
        ;;
    "full")
        echo "Performing full cleanup (table + container)..."
        echo "Note: This is the default behavior - most common use case"
        if check_dynamodb_running; then
            delete_table
            stop_container
        else
            echo "DynamoDB Local is not running, skipping table deletion"
            stop_container
        fi
        ;;
    "reset")
        echo "Performing complete reset (table + container + image + credentials)..."
        echo "Note: This will require re-downloading the Docker image on next setup"
        if check_dynamodb_running; then
            delete_table
            stop_container
        else
            echo "DynamoDB Local is not running, skipping table deletion"
            stop_container
        fi
        remove_image
        clean_aws_credentials
        ;;
    *)
        echo "Invalid cleanup level. Use: basic, full, or reset"
        echo "Usage: ./cleanup-full.sh [basic|full|reset]"
        echo ""
        echo "Cleanup levels:"
        echo "  basic  - Delete table only (keeps container running for faster re-setup)"
        echo "  full   - Delete table and stop container (DEFAULT - most common use case)"
        echo "  reset  - Complete cleanup (table, container, image, credentials)"
        echo ""
        echo "Examples:"
        echo "  ./cleanup-full.sh        # Uses default (full)"
        echo "  ./cleanup-full.sh basic  # Table only"
        echo "  ./cleanup-full.sh full   # Table + container"
        echo "  ./cleanup-full.sh reset  # Everything"
        exit 1
        ;;
esac

echo ""
echo "Cleanup completed successfully!"
echo "================================" 