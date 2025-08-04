# DynamoDB Local Setup and Cleanup Scripts

This directory contains scripts to manage the DynamoDB Local environment for the WiFi Positioning Service.

## Scripts Overview

### Setup Scripts

1. **`setup.sh`** - Complete environment setup
   - Installs dependencies (Homebrew, AWS CLI, Docker)
   - Sets up AWS credentials for DynamoDB Local
   - Starts DynamoDB Local container
   - Creates `wifi_access_points` table
   - Loads comprehensive test data
   - Verifies the setup

2. **`create-and-load.sh`** - Table creation and data loading
   - Deletes existing table (if any)
   - Creates new `wifi_access_points` table
   - Loads test data from `wifi-positioning-test-data.sh`

3. **`verify-dynamodb-data.sh`** - Data verification
   - Verifies table existence
   - Scans and displays all loaded data
   - Shows test data organized by categories

### Cleanup Scripts

1. **`cleanup.sh`** - Basic cleanup
   - Deletes the `wifi_access_points` table
   - Stops and removes DynamoDB Local container

2. **`cleanup-full.sh`** - Comprehensive cleanup with options
   - **Usage**: `./cleanup-full.sh [basic|full|reset]`
   - **basic**: Delete table only (keeps container running for faster re-setup)
   - **full**: Delete table and stop container (DEFAULT - most common use case)
   - **reset**: Complete cleanup (table, container, image, credentials)

## Usage Examples

### Initial Setup
```bash
# Run complete setup
./setup.sh

# Or run individual components
./create-and-load.sh
./verify-dynamodb-data.sh
```

### Cleanup Operations
```bash
# Default cleanup (table + container) - most common use case
./cleanup.sh
# or
./cleanup-full.sh
# or
./cleanup-full.sh full

# Basic cleanup (table only - keeps container running)
./cleanup-full.sh basic

# Complete reset (everything)
./cleanup-full.sh reset
```

### Verification
```bash
# Check if DynamoDB Local is running
docker ps | grep dynamodb-local

# List tables
aws dynamodb list-tables --endpoint-url http://localhost:8000 --profile dynamodb-local

# Verify data
./verify-dynamodb-data.sh
```

## Test Data Categories

The setup loads 54 test access points organized into categories:

1. **Basic Algorithm Test Cases** (5 APs)
   - Single AP, Dual AP, Triple AP, Multi AP, Weak Signal tests

2. **Advanced Scenario Test Cases** (8 APs)
   - Collinear, High Density, Mixed Signal tests

3. **Temporal and Environmental Test Cases** (6 APs)
   - Time Series, Path Loss, Historical tests

4. **Error and Edge Cases** (2 APs)
   - Error conditions and edge cases

5. **Status Filtering Tests** (5 APs)
   - Different status values (active, warning, error, expired, wifi-hotspot)

6. **2D Positioning Tests** (8 APs)
   - 2D positioning scenarios with null altitude

7. **Mixed 2D/3D Positioning Tests** (2 APs)
   - Mixed 2D and 3D positioning scenarios

## Configuration

- **DynamoDB Local**: Runs on port 8000
- **AWS Profile**: `dynamodb-local` with dummy credentials
- **Table Name**: `wifi_access_points`
- **Primary Key**: `mac_addr` (String)

## Troubleshooting

### Common Issues

1. **Port 8000 already in use**
   ```bash
   # Stop existing container
   ./cleanup-full.sh full
   # Then run setup again
   ./setup.sh
   ```

2. **Table creation fails**
   ```bash
   # Clean up and retry
   ./cleanup-full.sh full
   ./create-and-load.sh
   ```

3. **Docker not running**
   ```bash
   # Start Docker Desktop manually
   # Then run setup
   ./setup.sh
   ```

### Reset Everything
```bash
# Complete reset (removes everything)
./cleanup-full.sh reset
# Then run setup again
./setup.sh
``` 