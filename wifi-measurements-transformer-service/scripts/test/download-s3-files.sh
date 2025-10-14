#!/bin/bash

# download-s3-files.sh
# Script to download S3 files from LocalStack Firehose destination bucket

set -e

# Configuration
LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
S3_BUCKET="wifi-measurements-table"

# AWS CLI configuration for LocalStack
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="$AWS_REGION"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to list available files
list_files() {
    print_status $BLUE "📋 Available S3 files:"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_BUCKET/ --recursive
}

# Function to download latest file
download_latest() {
    print_status $BLUE "📥 Downloading latest S3 file..."
    
    # Get the latest file
    LATEST_FILE=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_BUCKET/ --recursive | tail -1 | awk '{print $4}')
    
    if [ -z "$LATEST_FILE" ]; then
        print_status $RED "❌ No files found in S3 bucket"
        return 1
    fi
    
    # Download the file
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 cp "s3://$S3_BUCKET/$LATEST_FILE" ./latest-s3-file.json
    
    print_status $GREEN "✅ Downloaded: $LATEST_FILE"
    
    # Format the JSON data
    print_status $BLUE "🔧 Formatting JSON data..."
    cat ./latest-s3-file.json | sed 's/}{/}\n{/g' > ./latest-s3-file-formatted.json
    
    # Show summary
    RECORD_COUNT=$(wc -l < ./latest-s3-file-formatted.json)
    FILE_SIZE=$(ls -lh ./latest-s3-file.json | awk '{print $5}')
    
    print_status $GREEN "📊 Summary:"
    echo "  Records: $RECORD_COUNT"
    echo "  File size: $FILE_SIZE"
    echo "  Files created:"
    echo "    - latest-s3-file.json (raw)"
    echo "    - latest-s3-file-formatted.json (readable)"
}

# Function to download specific file
download_file() {
    local file_path="$1"
    local output_name="$2"
    
    if [ -z "$file_path" ]; then
        print_status $RED "❌ Please provide file path"
        return 1
    fi
    
    if [ -z "$output_name" ]; then
        output_name="downloaded-file"
    fi
    
    print_status $BLUE "📥 Downloading: $file_path"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 cp "s3://$S3_BUCKET/$file_path" "./$output_name.json"
    
    # Format the JSON data
    print_status $BLUE "🔧 Formatting JSON data..."
    cat "./$output_name.json" | sed 's/}{/}\n{/g' > "./$output_name-formatted.json"
    
    print_status $GREEN "✅ Downloaded and formatted: $output_name"
}

# Function to show help
show_help() {
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  list                    List all available S3 files"
    echo "  latest                  Download the latest S3 file"
    echo "  download <path> [name]  Download specific file by path"
    echo "  help                    Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 list"
    echo "  $0 latest"
    echo "  $0 download 'wifi-measurements/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/2025/09/04/13/wifi-measurements-stream-2025-09-04-13-47-44-cb49ccc7-a6b1-4bfb-beb5-df8f3658bd88' my-file"
}

# Main execution
case "${1:-latest}" in
    "list")
        list_files
        ;;
    "latest")
        download_latest
        ;;
    "download")
        download_file "$2" "$3"
        ;;
    "help"|"-h"|"--help")
        show_help
        ;;
    *)
        print_status $RED "❌ Unknown command: $1"
        show_help
        exit 1
        ;;
esac
