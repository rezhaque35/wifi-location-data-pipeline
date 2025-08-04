# WiFi Scan Endpoint Documentation

## Overview

The WiFi Scan Queue Consumer application now includes a REST endpoint that accepts individual WiFi scan messages as JSON and processes them through the compression and Firehose delivery pipeline. This endpoint provides a direct API for sending WiFi scan data without requiring Kafka message consumption.

## Endpoint Details

### URL
```
POST /api/metrics/wifi-scan
```

### Content-Type
```
application/json
```

### Request Body
The endpoint accepts a WiFi scan message in the following JSON format:

```json
{
  "wifiScanResults": [
    {
      "macAddress": "aa:bb:cc:dd:ee:ff",
      "signalStrength": -65.4,
      "frequency": 2437,
      "ssid": "OfficeWiFi",
      "linkSpeed": 866,
      "channelWidth": 80
    }
  ],
  "client": "wifi-scan-generator",
  "requestId": "req-1735820400-12345",
  "application": "wifi-scan-test-suite",
  "calculationDetail": true
}
```

### Field Descriptions

#### wifiScanResults (Array)
Array of WiFi scan results, each containing:
- **macAddress**: MAC address of the WiFi access point (format: XX:XX:XX:XX:XX:XX)
- **signalStrength**: Signal strength in dBm (typically -30 to -100)
- **frequency**: Frequency in MHz (e.g., 2437 for 2.4GHz, 5180 for 5GHz)
- **ssid**: Service Set Identifier (WiFi network name)
- **linkSpeed**: Link speed in Mbps
- **channelWidth**: Channel width in MHz (20, 40, 80, or 160)

#### Metadata Fields
- **client**: Identifier for the client sending the data
- **requestId**: Unique request identifier
- **application**: Application name or identifier
- **calculationDetail**: Boolean flag for calculation detail level

## Response Format

### Success Response (200 OK)
```json
{
  "status": "success",
  "message": "WiFi scan message delivered successfully to Firehose",
  "originalMessageSize": 245,
  "compressedMessageSize": 156,
  "compressionRatio": 36.3,
  "timestamp": 1735820400123
}
```

### Error Response (400 Bad Request)
```json
{
  "status": "error",
  "message": "Failed to convert message to JSON format",
  "timestamp": 1735820400123
}
```

### Error Response (500 Internal Server Error)
```json
{
  "status": "error",
  "message": "Failed to compress and encode message",
  "timestamp": 1735820400123
}
```

## Processing Pipeline

The endpoint follows this processing pipeline:

1. **JSON Validation**: Validates the incoming JSON message format
2. **Message Conversion**: Converts the object to a JSON string
3. **Single-Element List**: Creates a list with the single message for batch processing
4. **Compression**: Uses `MessageCompressionService` to compress and encode the message
5. **Firehose Delivery**: Uses `BatchFirehoseMessageService` to deliver to AWS Kinesis Data Firehose
6. **Response Generation**: Returns processing status and metrics

## Testing

### Using the Test Script

A test script is provided to verify the endpoint functionality:

```bash
# Basic test
./scripts/test-wifi-scan-endpoint.sh

# Test with custom port
./scripts/test-wifi-scan-endpoint.sh -p 8081

# Send multiple messages
./scripts/test-wifi-scan-endpoint.sh -c 5 -i 2

# Use custom message file
./scripts/test-wifi-scan-endpoint.sh -m custom-message.json
```

### Manual Testing with curl

```bash
# Basic test
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "aa:bb:cc:dd:ee:ff",
        "signalStrength": -65.4,
        "frequency": 2437,
        "ssid": "TestWiFi",
        "linkSpeed": 866,
        "channelWidth": 80
      }
    ],
    "client": "test-client",
    "requestId": "test-123",
    "application": "test-app",
    "calculationDetail": true
  }' \
  http://localhost:8080/api/metrics/wifi-scan
```

## Error Handling

The endpoint includes comprehensive error handling:

- **Invalid JSON**: Returns 400 Bad Request for malformed JSON
- **Compression Failures**: Returns 500 Internal Server Error if compression fails
- **Firehose Delivery Failures**: Returns 500 Internal Server Error if delivery fails
- **General Exceptions**: Catches and handles all unexpected errors

## Performance Considerations

- **Single Message Processing**: Optimized for individual message processing
- **Compression Metrics**: Provides compression ratio information
- **Size Validation**: Validates message sizes before processing
- **Error Recovery**: Graceful handling of processing failures

## Integration Notes

- **No Kafka Dependency**: This endpoint bypasses Kafka and processes messages directly
- **Same Pipeline**: Uses the same compression and Firehose services as the Kafka consumer
- **Metrics Integration**: Integrates with existing monitoring and metrics collection
- **Health Checks**: Endpoint availability is included in application health checks

## Security Considerations

- **Input Validation**: Validates JSON format and structure
- **Size Limits**: Enforces Firehose size limits (1MB per record)
- **Error Information**: Provides meaningful error messages without exposing internal details
- **Logging**: Comprehensive logging for monitoring and debugging

## Monitoring

The endpoint provides detailed response information including:
- Processing status (success/error)
- Original and compressed message sizes
- Compression ratio
- Processing timestamp

This information can be used for monitoring endpoint performance and data compression efficiency. 