# WiFi Measurements Transformer Service - Test Scripts

This directory contains test scripts and data files for testing the WiFi Measurements Transformer Service end-to-end flow.

## Test Scripts Overview

### 1. `run-tests.sh` (NEW - Interactive Test Runner)

An interactive menu-driven test runner that provides easy access to all testing options.

**Features:**
- Interactive menu interface
- Easy access to all test scripts
- User-friendly file selection
- Built-in help and documentation

**Usage:**
```bash
cd wifi-measurements-transformer-service/scripts
./run-tests.sh
```

**Menu Options:**
1. **Flexible Test** - Use test data from JSON files
2. **Original Test** - Use hardcoded test data
3. **List Test Files** - View available test data
4. **Help** - Show usage information
5. **Exit** - Close the test runner

### 2. `test-with-data-file.sh` (NEW - Flexible)

A flexible test script that reads test data from JSON files instead of using hardcoded strings. This makes it easy to test with different data scenarios.

**Features:**
- Reads test data from files in the `data/` subdirectory
- Automatically validates JSON format and required fields
- Lists available test files with metadata
- Same comprehensive testing as the original script
- More maintainable and reusable
- **Raw file mode**: Upload JSON files directly without compression/encoding
- **Standard mode**: Compress and encode files (default behavior)

**Usage:**
```bash
# List available test data files
./test-with-data-file.sh --list-files

# Use first available test file
./test-with-data-file.sh

# Use specific test file
./test-with-data-file.sh sample-wifi-scan.json

# Use specific file and skip cleanup
./test-with-data-file.sh --skip-cleanup multi-location-scan.json

# Show only summary (skip verbose output)
./test-with-data-file.sh --summary-only sample-wifi-scan.json

# Upload raw JSON file without compression/encoding
./test-with-data-file.sh --raw-file sample-wifi-scan.json

# Get help
./test-with-data-file.sh --help
```

### 3. `test-end-to-end-flow.sh` (Original)

The original test script with hardcoded test data. Still useful for quick testing with predefined data.

## Processing Modes

### Standard Mode (Default)
- **Behavior**: Compresses and base64-encodes JSON files before S3 upload
- **Use Case**: Simulates production environment where files are compressed/encoded
- **Command**: `./test-with-data-file.sh sample-wifi-scan.json`

### Raw File Mode
- **Behavior**: Uploads files directly to S3 without compression/encoding
- **Use Case**: Testing with encoded files, raw binary files, or when JSON validation isn't needed
- **Command**: `./test-with-data-file.sh --raw-file sample-raw-wifi-scan.txt`
- **Benefits**: Faster processing, easier debugging, direct file inspection, skips JSON validation

### Skip End Validation Mode
- **Behavior**: Skips schema and data validation at the end of processing
- **Use Case**: Troubleshooting production files, testing ingestion pipeline without validation overhead
- **Command**: `./test-with-data-file.sh --skip-end-validation sample-raw-wifi-scan.txt`
- **Benefits**: Faster execution, focus on pipeline flow rather than data quality, useful for debugging

**Usage:**
```bash
# Run with default options
./test-end-to-end-flow.sh

# Skip cleanup
./test-end-to-end-flow.sh --skip-cleanup

# Show only summary
./test-end-to-end-flow.sh --summary-only

# Get help
./test-end-to-end-flow.sh --help
```

## Quick Start Guide

### Option 1: Interactive Test Runner (Recommended for Beginners)
```bash
cd wifi-measurements-transformer-service/scripts
./run-tests.sh
```
Then select option 1 for flexible testing or option 2 for quick testing.

### Option 2: Direct Script Usage
```bash
cd wifi-measurements-transformer-service/scripts

# List available test data
./test-with-data-file.sh --list-files

# Run test with specific data file
./test-with-data-file.sh sample-wifi-scan.json

# Run original test
./test-end-to-end-flow.sh
```

## Test Data Files

Place your WiFi scan test data files in the `data/` subdirectory. Files should be in JSON format with the following structure:

### Required Fields
- `wifiConnectedEvents`: Array of connected WiFi events
- `scanResults`: Array of WiFi scan results

### Example Structure
```json
{
  "osVersion": "14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys",
  "model": "SM-A536V",
  "device": "a53x",
  "manufacturer": "samsung",
  "osName": "Android",
  "sdkInt": "34",
  "appNameVersion": "com.verizon.wifiloc.app/0.1.0.10000",
  "dataVersion": "15",
  "wifiConnectedEvents": [
    {
      "timestamp": 1731091615562,
      "eventId": "unique-event-id",
      "eventType": "CONNECTED",
      "wifiConnectedInfo": {
        "bssid": "b8:f8:53:c0:1e:ff",
        "ssid": "NetworkName",
        "linkSpeed": 351,
        "frequency": 5660,
        "rssi": -58,
        "channelWidth": 80,
        "centerFreq0": 5650,
        "capabilities": "WPA2-PSK",
        "is80211mcResponder": true,
        "isPasspointNetwork": false
      },
      "location": {
        "latitude": 40.6768816,
        "longitude": -74.416391,
        "altitude": 15.5,
        "accuracy": 10.0,
        "time": 1731091614415,
        "provider": "gps",
        "source": "network",
        "speed": 0.0,
        "bearing": 0.0
      }
    }
  ],
  "scanResults": [
    {
      "timestamp": 1731091615562,
      "location": {
        "latitude": 40.6768816,
        "longitude": -74.416391,
        "altitude": 15.5,
        "accuracy": 10.0,
        "time": 1731091614415,
        "provider": "gps",
        "source": "network",
        "speed": 0.0,
        "bearing": 0.0
      },
      "results": [
        {
          "ssid": "NetworkName",
          "bssid": "b8:f8:53:c0:1e:ff",
          "scantime": 1731091613712,
          "rssi": -61,
          "frequency": 5660
        }
      ]
    }
  ]
}
```

## Available Test Data Files

### 1. `sample-wifi-scan.json`
- **Device**: Samsung Galaxy A53
- **OS**: Android 14
- **Networks**: 4 WiFi networks (2 connected, 2 scan only)
- **Location**: Single location with multiple BSSIDs
- **Use Case**: Basic functionality testing

### 2. `multi-location-scan.json`
- **Device**: iPhone 15 Pro
- **OS**: iOS 15
- **Networks**: 5 WiFi networks (1 connected, 4 scan)
- **Location**: Multiple locations with movement
- **Use Case**: Multi-location and movement testing

## Prerequisites

1. **LocalStack**: Must be running on port 4566
2. **AWS CLI**: Installed and configured for LocalStack
3. **jq**: JSON processor for data analysis
4. **gzip**: For compression/decompression
5. **bc**: For mathematical calculations (optional)

## Starting LocalStack

```bash
docker run --rm -it -p 4566:4566 -p 4510-4559:4510-4559 localstack/localstack
```

## Running Tests

### Interactive Test Runner (Easiest)
```bash
cd wifi-measurements-transformer-service/scripts
./run-tests.sh
```
Follow the interactive menu to select your test option.

### Quick Test with Default Data
```bash
cd wifi-measurements-transformer-service/scripts
./test-with-data-file.sh
```

### Test with Specific Data File
```bash
./test-with-data-file.sh multi-location-scan.json
```

### Test with Raw/Encoded File (No Compression, No JSON Validation)
```bash
./test-with-data-file.sh --raw-file sample-raw-wifi-scan.txt
```

### Test with Production File (Skip End Validation for Troubleshooting)
```bash
./test-with-data-file.sh --raw-file --skip-end-validation production-wifi-scan.txt
```

### List Available Test Files
```bash
./test-with-data-file.sh --list-files
```

### Test and Preserve Files for Inspection
```bash
./test-with-data-file.sh --skip-cleanup sample-wifi-scan.json
```

### Run Original Test Script
```bash
./test-end-to-end-flow.sh
```

## Test Flow

1. **Data Validation**: Validates JSON format and required fields
2. **Data Preparation**: Compresses and encodes test data
3. **S3 Upload**: Uploads encoded data to source S3 bucket
4. **SQS Event**: Creates and sends S3 event notification to SQS
5. **Processing Wait**: Waits for service processing (30 seconds)
6. **Result Check**: Downloads and examines Firehose output
7. **Validation**: Validates schema and data integrity
8. **Analysis**: Compares expected vs actual results
9. **Cleanup**: Removes test files and S3 objects (unless skipped)

## Test Results

The scripts provide comprehensive analysis including:

- **Record Count Comparison**: Expected vs actual CONNECTED and SCAN records
- **BSSID-by-BSSID Analysis**: Individual BSSID processing verification
- **Schema Validation**: Required field presence and format validation
- **Data Quality Checks**: Quality weight validation and location accuracy
- **Success Rate Calculation**: Overall test pass/fail percentage

## Script Comparison

| Feature | Interactive Runner | Flexible Script | Original Script |
|---------|-------------------|-----------------|-----------------|
| **Ease of Use** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Flexibility** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **Maintainability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **Quick Testing** | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Data Variety** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |

## Troubleshooting

### Common Issues

1. **LocalStack not running**
   - Start LocalStack: `docker run --rm -it -p 4566:4566 localstack/localstack`

2. **No test data files found**
   - Create JSON files in the `data/` directory
   - Use `--list-files` to see available files

3. **Invalid JSON format**
   - Validate your JSON files with `jq empty filename.json`
   - Check for missing commas, brackets, or syntax errors

4. **Missing required fields**
   - Ensure `wifiConnectedEvents` and `scanResults` arrays are present
   - Check field names match exactly (case-sensitive)

### Debug Mode

Use `--skip-cleanup` to preserve test files for inspection:

```bash
./test-with-data-file.sh --skip-cleanup sample-wifi-scan.json
```

Files will be preserved in `/tmp/` for manual inspection.

## Adding New Test Data

1. Create a new JSON file in the `data/` directory
2. Follow the required structure shown above
3. Include realistic WiFi scan data with various scenarios
4. Test with: `./test-with-data-file.sh your-new-file.json`

## Performance Notes

- **Processing Wait**: Default 30 seconds - adjust if needed for your environment
- **File Sizes**: Large test files may take longer to process
- **Memory Usage**: Scripts use `/tmp/` for temporary files
- **Cleanup**: Automatic cleanup prevents S3 bucket clutter

## Best Practices

1. **Start with Interactive Runner**: Use `./run-tests.sh` for your first tests
2. **Use Specific Files**: Specify test files for reproducible results
3. **Skip Cleanup for Debugging**: Use `--skip-cleanup` when investigating issues
4. **Validate Test Data**: Ensure your JSON files are properly formatted
5. **Check Prerequisites**: Verify LocalStack and tools are running before testing
