#!/bin/bash

# Create new package directory structure
mkdir -p src/main/java/com/wifi/scan/consume
mkdir -p src/test/java/com/wifi/scan/consume

# Move main source files
mv src/main/java/com/wifidata/consumer/* src/main/java/com/wifi/scan/consume/

# Move test source files
mv src/test/java/com/wifidata/consumer/* src/test/java/com/wifi/scan/consume/

# Remove old directories
rm -rf src/main/java/com/wifidata
rm -rf src/test/java/com/wifidata

# Update package declarations in all Java files
find src -name "*.java" -type f -exec sed -i '' 's/package com.wifidata.consumer/package com.wifi.scan.consume/g' {} +

# Update imports in all Java files
find src -name "*.java" -type f -exec sed -i '' 's/import com.wifidata.consumer/import com.wifi.scan.consume/g' {} +

echo "Package refactoring completed!" 