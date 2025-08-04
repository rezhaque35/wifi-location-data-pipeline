#!/bin/bash

set -e

echo "Examining S3 files to understand data format..."

# Get list of files
echo "Getting file list..."
FILE_LIST=$(aws s3 ls s3://wifi-scan-data-bucket --recursive --profile localstack --endpoint-url=http://localhost:4566 | head -5)

echo "Found files:"
echo "$FILE_LIST"

echo ""
echo "=== EXAMINING SMALL FILE (135 bytes) ==="
# Download a small file
aws s3 cp "s3://wifi-scan-data-bucket/wifi-scan-data/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/2025/07/27/17/MVS-stream-2025-07-27-17-00-55-47131d6f-39cb-4ff0-b402-6876afb0b56c" /tmp/small_file.txt --profile localstack --endpoint-url=http://localhost:4566

echo "Raw content:"
cat /tmp/small_file.txt
echo ""

echo "Checking if it's base64..."
if cat /tmp/small_file.txt | base64 -d > /dev/null 2>&1; then
    echo "✅ Is valid base64"
    echo "Base64 decoded content:"
    cat /tmp/small_file.txt | base64 -d
    echo ""
    
    echo "Checking if decoded content is gzipped..."
    if cat /tmp/small_file.txt | base64 -d | gunzip > /dev/null 2>&1; then
        echo "✅ Is gzipped"
        echo "Final JSON content:"
        cat /tmp/small_file.txt | base64 -d | gunzip
        echo ""
    else
        echo "❌ Not gzipped"
    fi
else
    echo "❌ Not valid base64"
    echo "Checking if it's direct JSON..."
    if cat /tmp/small_file.txt | jq . > /dev/null 2>&1; then
        echo "✅ Is direct JSON"
    else
        echo "❌ Not valid JSON either"
    fi
fi

echo ""
echo "=== EXAMINING LARGE FILE (300+ bytes) ==="
# Download a large file
aws s3 cp "s3://wifi-scan-data-bucket/wifi-scan-data/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/2025/07/27/16/MVS-stream-2025-07-27-16-12-27-b83cbf31-a2e5-4d80-88f8-c92f4694839a" /tmp/large_file.txt --profile localstack --endpoint-url=http://localhost:4566

echo "Raw content (first 100 chars):"
head -c 100 /tmp/large_file.txt
echo ""

echo "Checking if it's base64..."
if cat /tmp/large_file.txt | base64 -d > /dev/null 2>&1; then
    echo "✅ Is valid base64"
    echo "Base64 decoded content (first 50 bytes):"
    cat /tmp/large_file.txt | base64 -d | head -c 50
    echo ""
    
    echo "Checking if decoded content is gzipped..."
    if cat /tmp/large_file.txt | base64 -d | gunzip > /dev/null 2>&1; then
        echo "✅ Is gzipped"
        echo "Final JSON content:"
        cat /tmp/large_file.txt | base64 -d | gunzip
        echo ""
    else
        echo "❌ Not gzipped"
    fi
else
    echo "❌ Not valid base64"
    echo "Checking if it's direct JSON..."
    if cat /tmp/large_file.txt | jq . > /dev/null 2>&1; then
        echo "✅ Is direct JSON"
    else
        echo "❌ Not valid JSON either"
    fi
fi

echo ""
echo "=== ANALYSIS COMPLETE ===" 