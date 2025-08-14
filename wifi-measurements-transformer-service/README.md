# WiFi Measurements Transformer Service

A Java microservice that processes WiFi scan data from SQS events, transforms the data, and writes to AWS S3 Tables using Kinesis Data Firehose. The service implements feed-based processing with cost-effective SQS operations and efficient batch writing.

## Overview

This microservice is part of the WiFi location data pipeline and handles:

- **SQS Message Processing**: Long polling, batch receive, and parallel processing
- **Feed-Based Processing**: Pluggable feed-specific processors with factory pattern
- **Data Transformation**: WiFi scan data normalization and filtering
- **Data Quality Assessment**: Sanity checks and mobile hotspot detection
- **Kinesis Data Firehose Integration**: Optimized batch writing to S3 Tables
- **Monitoring & Observability**: Comprehensive metrics and structured logging

## Features

### Core Capabilities

- ✅ **Batch SQS Processing**: Process up to 10 messages per batch with long polling
- ✅ **Data Filtering**: Stage 1 sanity checks and quality assessment
- ✅ **Mobile Hotspot Detection**: Optional OUI-based MAC address filtering
- ✅ **Schema Mapping**: Transform WiFi scan data to normalized measurement records
- ✅ **Kinesis Data Firehose**: Optimized batch writing (500 records or 4MB limit)
- ✅ **Structured Logging**: JSON logging with correlation IDs
- ✅ **Health Checks**: Kubernetes-ready probes and metrics
- ✅ **Comprehensive Testing**: 115+ unit and integration tests with >90% coverage

### Performance Targets

- Process 150MB files within 2-5 minutes
- Process up to 10 messages per batch (single batch processing)
- Maintain memory usage under 4GB
- Achieve 99.9% message processing success rate

### Memory Requirements

#### File Processing Analysis
Based on sample WiFi scan data analysis:
- **Single WiFi scan JSON**: ~1,794 lines, ~50KB uncompressed
- **Compression ratio**: Base64 + GZIP achieves ~70-80% compression
- **Compressed size**: ~10-15KB per scan
- **150MB file**: Contains ~10,000-15,000 compressed WiFi scans

#### Memory Usage Breakdown

**1. S3FileProcessorService (Streaming - Low Memory)**
- **Memory per line**: ~1-2KB (compressed Base64 string)
- **Total memory**: ~15-30MB for entire file processing
- **Approach**: Streaming - only one line in memory at a time

**2. DataDecodingService (Per Message - Moderate Memory)**
- **Base64 decoding**: ~1.5x memory expansion (10-15KB → 15-22KB)
- **GZIP decompression**: ~3-4x memory expansion (15-22KB → 45-88KB)
- **Per message memory**: ~50-100KB (uncompressed JSON)
- **Peak memory**: ~500KB-1MB (for 10 messages in batch)

**3. DefaultFeedProcessor (Processing - Variable Memory)**
- **JSON parsing**: ~2-3x memory overhead for object creation
- **Per scan memory**: ~100-300KB (parsed objects + processing)
- **Batch processing**: ~1-3MB for 10 messages
- **Data transformation**: Additional ~50-100KB per message

**4. Firehose Writing (Minimal Memory)**
- **Batch writing**: ~10-50KB per batch
- **No accumulation**: Data written immediately

#### Total Memory Requirements

**Peak Memory Usage:**
- **Minimum**: ~2-3GB for normal operation
- **Recommended**: ~4GB for safe processing with headroom
- **Maximum**: ~6GB for worst-case scenarios

**Memory Efficiency Features:**
- ✅ **Streaming file processing**: No full file loading
- ✅ **Sequential batch processing**: One batch at a time
- ✅ **Immediate data writing**: No accumulation in memory
- ✅ **Garbage collection friendly**: Objects released after processing

**Memory Optimization:**
- **JVM Heap**: 3-4GB recommended
- **Garbage Collection**: G1GC with aggressive young generation
- **Memory monitoring**: Built-in health checks for memory usage

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker (for LocalStack)
- AWS CLI (for LocalStack setup)

### Local Development Setup

1. **Clone and navigate to the project:**
   ```bash
   cd wifi-measurements-transformer-service
   ```

2. **Setup LocalStack infrastructure (automatically starts LocalStack):**
   ```bash
   ./scripts/setup.sh
   ```
   
   This script will:
   - ✅ Automatically start LocalStack if not running
   - ✅ Create SQS queues (main + DLQ)
   - ✅ Create S3 buckets for ingestion and warehouse
   - ✅ Setup Kinesis Data Firehose delivery stream
   - ✅ Configure EventBridge for S3 events
   - ✅ Create sample test data

3. **Start the application:**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

4. **Test the service:**
   ```bash
   # Check health
   curl http://localhost:8081/actuator/health
   
   # Check metrics
   curl http://localhost:8081/actuator/metrics
   ```

5. **Verify setup (optional but recommended):**
   ```bash
   ./scripts/verify-setup.sh
   ```

### Setup Script Commands

The setup script supports multiple commands:

```bash
./scripts/setup.sh setup    # Setup infrastructure (default)
./scripts/setup.sh start    # Start LocalStack only
./scripts/setup.sh help     # Show help
```

### Cleanup Script

For cleanup operations, use the dedicated cleanup script:

```bash
./scripts/cleanup.sh                    # Cleanup with confirmation prompt
./scripts/cleanup.sh --force            # Cleanup without confirmation
./scripts/cleanup.sh --remove-image     # Cleanup + remove Docker image (~500MB)
./scripts/cleanup.sh --force --remove-image # Cleanup + remove image without confirmation
```

The cleanup script will:
- 🧹 Delete all LocalStack resources (SQS, S3, Firehose, EventBridge)
- 🗑️ Remove all data and configurations
- 📁 Clean up local logs directory
- 🐳 Stop and remove LocalStack Docker container
- ⚠️ Prompt for confirmation (unless --force is used)
- 🖼️ Remove Docker image (only with --remove-image flag)

### Offline Development

The scripts are designed for offline development:

**First Run (Internet Required):**
- Downloads LocalStack Docker image
- Sets up all infrastructure

**Subsequent Runs (Offline Capable):**
- ✅ **Setup**: Works offline - uses cached Docker image
- ✅ **Cleanup**: Works offline - preserves Docker image
- ✅ **Restart**: Works offline - no internet dependency

**Workflow:**
```bash
# First time (with internet)
./scripts/setup.sh          # Downloads image, sets up infrastructure
./scripts/cleanup.sh        # Removes container, preserves image

# Later (offline)
./scripts/setup.sh          # Works offline - uses cached image
./scripts/cleanup.sh        # Works offline - preserves image
```

**Note:** The Docker image (`localstack/localstack`) is preserved between cleanup/setup cycles, enabling offline development after the initial download.

### End-to-End Testing

Run the comprehensive end-to-end test to verify the complete data pipeline:

```bash
./scripts/test-end-to-end-flow.sh
```

This test validates:
- S3 file upload → SQS event → Service processing → Firehose → Destination S3
- Data transformation and filtering logic
- BSSID-by-BSSID comparison
- Schema validation and quality metrics

### Troubleshooting

#### SQS Queue Issues

If you encounter `QueueDoesNotExistException`, try these steps:

1. **Run verification script:**
   ```bash
   ./scripts/verify-setup.sh
   ```

2. **Verify LocalStack is running:**
   ```bash
   curl http://localhost:4566/health
   ```

3. **Check if queues exist:**
   ```bash
   aws --endpoint-url=http://localhost:4566 sqs list-queues
   ```

4. **Re-run setup with verification:**
   ```bash
   ./scripts/cleanup.sh --force
   ./scripts/setup.sh
   ./scripts/verify-setup.sh
   ```

5. **Verify queue URL matches configuration:**
   ```bash
   aws --endpoint-url=http://localhost:4566 sqs get-queue-url --queue-name wifi-scan-events
   ```
   
   Expected URL: `http://localhost:4566/000000000000/wifi-scan-events`

6. **Check application configuration:**
   - Verify `sqs.queue-url` in `application.yml` matches the created queue
   - Ensure LocalStack endpoint is correct: `http://localhost:4566`

#### Common Issues

1. **LocalStack not ready**: Wait for LocalStack to fully start (30-60 seconds)
2. **Port conflicts**: Ensure port 4566 is available
3. **Docker issues**: Restart Docker if containers aren't starting properly
4. **AWS CLI version**: Ensure AWS CLI v2 is installed for LocalStack compatibility

#### S3EventExtractor UUID Format Issues

If you encounter `Invalid event ID format for field: id` errors:

**Cause**: The S3EventExtractor validates UUIDs and ETAGs against specific patterns

**Solution**: The validation patterns have been updated to accept both uppercase and lowercase formats:
- UUIDs: `[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}`
- ETAGs: `[a-fA-F0-9]{32}` (32 hex characters)

**Example of valid formats**:
```json
{
  "id": "D374C619-84A8-4DAD-AF15-1767F1D9976D",  // Uppercase
  "id": "d374c619-84a8-4dad-af15-1767f1d9976d",  // Lowercase
  "etag": "D8E8FCA2DC0F896FD7CB4CB0031BA249",    // Uppercase
  "etag": "d8e8fca2dc0f896fd7cb4cb0031ba249"     // Lowercase
}
```

**Note**: The test script uses standard `uuidgen` and `md5` commands which generate valid formats.

### Cleanup Local Environment

```bash
./scripts/cleanup.sh
```

## Configuration

### Environment Profiles

- **local**: Uses LocalStack for all AWS services
- **non-local**:  integration with AWS services .

### Key Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `sqs.queue-url` | LocalStack URL | SQS queue for processing |
| `sqs.max-messages` | 10 | Max messages per batch |
| `s3.bucket-name` | ingested-wifiscan-data | S3 bucket for input files |
| `filtering.max-location-accuracy` | 150.0 | GPS accuracy threshold (meters) |
| `filtering.mobile-hotspot.enabled` | false | Enable OUI-based filtering |
| `firehose.delivery-stream-name` | wifi-measurements-stream | Firehose delivery stream |
| `firehose.max-batch-size` | 500 | Records per batch |
| `firehose.max-batch-size-bytes` | 4194304 | 4MB batch size limit |

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SQS_QUEUE_URL` | Production | SQS queue URL |
| `S3_BUCKET_NAME` | Production | S3 bucket name |
| `FIREHOSE_DELIVERY_STREAM_NAME` | Production | Firehose delivery stream name |
| `AWS_REGION` | Production | AWS region |

## Architecture

```
SQS Events → Feed Detection → Feed Processor Factory
                                      ↓
                            Default WiFi Processor
                                      ↓
S3 Download → Base64 Decode → Unzip → JSON Parse
                                      ↓
                         Stage 1: Sanity Checks
                    (Invalid data filtering + Quality weighting)
                                      ↓
                      Optional: OUI-Based Mobile Hotspot Check
                         (MAC address OUI lookup)
                                      ↓
                    Schema Mapping + Data Normalization
                                      ↓
                              Data Sanitization
                                      ↓
                    Kinesis Data Firehose Batch Writer
                                      ↓
                        S3 Tables (Iceberg Format)
```

## Data Processing

### Input Format

WiFi scan JSON files (Base64 encoded and gzipped) containing:
- Device metadata (model, manufacturer, OS version)
- WiFi connection events with signal strength
- WiFi scan results with location data

### Output Schema

Normalized `wifi_measurements` table with:
- Device information
- Location data (GPS coordinates, accuracy)
- WiFi signal data (RSSI, frequency, SSID)
- Connection status and quality weights
- Data quality and processing metadata

### Data Filtering

#### Stage 1: Sanity Checks
- Invalid coordinates filtering
- RSSI range validation (-100 to 0 dBm)
- GPS accuracy threshold (configurable, default 150m)
- Required field validation
- BSSID format validation (MAC address format)
- Timestamp validation (reasonable date ranges)

#### Optional: Mobile Hotspot Detection
- OUI-based MAC address filtering
- Configurable action: FLAG, EXCLUDE, or LOG_ONLY
- Common mobile device manufacturer detection

## Monitoring

### Health Endpoints

- `/actuator/health` - Application health status
- `/actuator/health/readiness` - Kubernetes readiness probe (SQS + Firehose connectivity)
- `/actuator/health/liveness` - Kubernetes liveness probe (application state + processing activity)

### Metrics

- `/actuator/metrics` - Micrometer metrics
- `/actuator/prometheus` - Prometheus format metrics

### Key Metrics

- `sqs.messages.processed` - SQS message processing rate
- `s3.files.processed` - File processing throughput
- `firehose.records.written` - Records written to Firehose
- `processing.errors` - Processing error rate
- `memory.usage` - Memory usage metrics

## S3 Event Format Support

### Production S3 Event Notification Format

This service processes **S3 Event Notifications** sent directly to SQS when files are uploaded to S3. The service has been updated to handle the actual AWS S3 Event Notification format used in production.

#### Supported Event Format

```json
{
  "Records": [
    {
      "eventVersion": "2.1",
      "eventSource": "aws:s3",
      "awsRegion": "us-east-2",
      "eventTime": "2025-08-13T22:30:10.778Z",
      "eventName": "ObjectCreated:Put",
      "userIdentity": {
        "principalId": "AWS:AROA4QWKES4Y24IUPAV2J:AWSFirehoseToS3"
      },
      "requestParameters": {
        "sourceIPAddress": "10.20.19.21"
      },
      "responseElements": {
        "x-amz-request-id": "8C3VR6DWXN808YJP",
        "x-amz-id-2": "2BdlIpJXKQCEI7siGhF3KCU9M59dye7AJcn63aIjkANLeVX+9EFIJ7qzipO/g3RJFVIK5E7a20PqWDccojmXUmLJHK00bHFvRHDhbb9LMnw="
      },
      "s3": {
        "s3SchemaVersion": "1.0",
        "configurationId": "NjgyMTJiZTUtNDMwZC00OTVjLWIzOWEtM2UzZWM3MzYwNGE2",
        "bucket": {
          "name": "vsf-dev-oh-frisco-ingested-wifiscan-data",
          "ownerIdentity": {
            "principalId": "A3LJZCR20GC5IX"
          },
          "arn": "arn:aws:s3:::vsf-dev-oh-frisco-ingested-wifiscan-data"
        },
        "object": {
          "key": "year%3D2025/month%3D08/day%3D13/hour%3D22/MVS-stream/frisco-wifiscan-mvs-stream-4-2025-08-13-22-29-19-176e8954-348c-4c9b-8798-1fc44872c0ad",
          "size": 1049040,
          "eTag": "af789ccf52246f4d7c9eea1925176409",
          "sequencer": "00689D11F29F29282F"
        }
      }
    }
  ]
}
```

### Stream Name Extraction

The service intelligently extracts stream names from S3 object keys using a robust algorithm that handles various path formats:

#### Supported Path Formats

1. **Firehose partitioned (URL-encoded)**: `year%3D2025/month%3D08/day%3D13/hour%3D22/MVS-stream/file.txt` → `MVS-stream`
2. **Firehose partitioned (non-encoded)**: `year=2025/month=08/day=13/hour=22/MVS-stream/file.txt` → `MVS-stream`  
3. **Date-only**: `2025/08/13/22/MVS-stream/file.txt` → `MVS-stream`
4. **Prefixed**: `someOtherStuff/2025/08/13/22/MVS-stream/file.txt` → `MVS-stream`
5. **Simple**: `MVS-stream/file.txt` → `MVS-stream`

#### URL Encoding Handling

- ✅ **Automatic Detection**: Checks for `%` characters to determine if URL decoding is needed
- ✅ **Graceful Handling**: Returns original key if URL decoding fails (robustness)  
- ✅ **Mixed Formats**: Handles both encoded and non-encoded paths in the same system

#### Algorithm

The stream name is extracted as **the component immediately before the filename**, making it robust across different S3 key structures.

### Recent Updates

#### ✅ **2025-08: S3 Event Format Alignment**

- **Updated from**: EventBridge format to direct S3 Event Notification format  
- **Reason**: Match actual AWS production behavior where S3 events are sent directly to SQS
- **Impact**: All tests, scripts, and integration code now use the correct format
- **Stream Name Logic**: Simplified to always extract the component before the filename

## Testing

### Unit Tests

Run the comprehensive test suite:

```bash
mvn test
```

**Current Coverage**: 18 unit tests for S3EventExtractor with 100% pass rate covering:
- ✅ Valid S3 Event Notification parsing
- ✅ Stream name extraction from various path formats  
- ✅ URL encoding/decoding edge cases
- ✅ Error handling for invalid JSON and malformed events
- ✅ Field validation and data type checking

### Integration Tests

Run integration tests with LocalStack:

```bash
mvn test -Dtest=*IntegrationTest
```

**Integration Test Coverage**:
- ✅ **SqsMessageReceiverIntegrationTest**: 7 tests for SQS message processing
- ✅ **ComprehensiveIntegrationTest**: End-to-end pipeline validation  
- ✅ **DataFilteringIntegrationTest**: Data filtering and validation
- ✅ **FirehoseIntegrationServiceTest**: Firehose delivery testing

## Development

### Contributing

1. Follow Java coding standards and use provided linting rules
2. Ensure all tests pass before submitting PRs
3. Update documentation for any API or format changes
4. Test with both LocalStack and AWS environments when possible

### Adding New Feed Processors

1. Implement the `FeedProcessor` interface
2. Register in `FeedProcessorFactory`  
3. Add comprehensive unit tests
4. Update configuration documentation

## Deployment

### Environment Requirements

- **Java 17+** runtime
- **Memory**: 4GB recommended (see Memory Requirements section)
- **Network**: Access to SQS, S3, and Firehose endpoints
- **Permissions**: SQS read/delete, S3 read, Firehose write

### AWS IAM Permissions

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:DeleteMessageBatch",
        "sqs:ChangeMessageVisibility"
      ],
      "Resource": "arn:aws:sqs:*:*:wifi-scan-events*"
    },
    {
      "Effect": "Allow", 
      "Action": [
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::ingested-wifiscan-data/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "firehose:PutRecord",
        "firehose:PutRecordBatch"
      ],
      "Resource": "arn:aws:firehose:*:*:deliverystream/wifi-measurements-stream"
    }
  ]
}
```

### Docker Deployment

```dockerfile
FROM openjdk:17-jre-slim

COPY target/wifi-measurements-transformer-service-*.jar app.jar

EXPOSE 8081

ENV JAVA_OPTS="-Xmx4g -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wifi-measurements-transformer
spec:
  replicas: 2
  selector:
    matchLabels:
      app: wifi-measurements-transformer
  template:
    metadata:
      labels:
        app: wifi-measurements-transformer
    spec:
      containers:
      - name: app
        image: wifi-measurements-transformer:latest
        ports:
        - containerPort: 8081
        resources:
          requests:
            memory: "3Gi"
            cpu: "500m"
          limits:
            memory: "4Gi" 
            cpu: "2"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        env:
        - name: SQS_QUEUE_URL
          value: "https://sqs.us-east-1.amazonaws.com/123456789012/wifi-scan-events"
        - name: S3_BUCKET_NAME
          value: "ingested-wifiscan-data"
        - name: FIREHOSE_DELIVERY_STREAM_NAME
          value: "wifi-measurements-stream"
        - name: AWS_REGION
          value: "us-east-1"
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.