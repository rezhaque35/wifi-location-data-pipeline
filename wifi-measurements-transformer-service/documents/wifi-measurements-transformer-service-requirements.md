# WiFi Data Processing Microservice - Requirements Document (Updated)

## Project Overview

A Java microservice that processes WiFi scan data from SQS events, transforms the data, and writes to AWS S3 Tables via AWS Kinesis Data Firehose. The service implements feed-based processing with cost-effective SQS operations and efficient batch writing to Firehose.

## Architecture Requirements

### Core Components
- **Spring Boot 3.x** application with reactive processing capabilities
- **AWS SDK v2** for all AWS integrations
- **SQS Consumer** with long polling, batch receive, and single-threaded processing
- **Feed Processor Factory** with pluggable feed-specific processors
- **Data Transformation Engine** for WiFi scan data normalization
- **Kinesis Data Firehose Writer** for S3 Tables delivery
- **Kubernetes-ready** with health checks and metrics

## Functional Requirements

### 1. SQS Message Processing
- **Long Polling**: Configure 20-second receive message wait time
- **Batch Receive**: Process up to 10 messages per batch
- **Single-threaded Processing**: Single batch processing to avoid complexity
- **Batch Deletion**: Delete processed messages in batches for cost efficiency
- **Dead Letter Queue**: Handle failed messages with configurable retry attempts
- **Message Visibility**: Extend visibility timeout during long-running processing
- **SQS Message Format**:
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
          "x-amz-id-2": "2BdlIpJXKQCEI7siGhF3KCU9M59dye7AJcn63aIjkANLeVX+9EFIJ7qzipO/g3RJFVIK5E7a20PqWDccojmXUmLJHK00HfRHDhbb9LMnw="
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

**Key Differences from Previous Requirements:**
- **Event Structure**: Uses `Records` array containing S3 event notifications (not EventBridge format)
- **Event Source**: `"aws:s3"` instead of `"aws.s3"`
- **Event Type**: `"ObjectCreated:Put"` instead of `"Object Created"`
- **S3 Object Details**: Located in `s3.object` path instead of `detail.object`
- **Bucket Information**: Located in `s3.bucket` path instead of `detail.bucket`
- **Object Key**: Contains URL-encoded partitioning structure (`year%3D2025/month%3D08/day%3D13/hour%3D22/`)
- **Stream Detection**: Stream name extraction from object key prefix (e.g., `MVS-stream` from the path)

> **📋 Note (Updated August 2025)**: This format has been validated against actual AWS production behavior. All implementation code, test scripts, and integration tests have been updated to use this correct S3 Event Notification format instead of the previous EventBridge-style format. The stream name extraction logic has been simplified to always extract the component immediately before the filename, making it robust across various S3 object key patterns.

### 2. Feed Processing Architecture
```
SQS Event → Feed Detector → Feed-Specific Processor OR Default Processor
```
- **Feed Detection**: Extract feed type from S3 object key prefix. The object key follows the pattern: `year%3D{year}/month%3D{month}/day%3D{day}/hour%3D{hour}/{stream-name}/{filename}`. For example, if the key is `"year%3D2025/month%3D08/day%3D13/hour%3D22/MVS-stream/frisco-wifiscan-mvs-stream-4-2025-08-13-22-29-19-176e8954-348c-4c9b-8798-1fc44872c0ad"`, the stream name is extracted as `"MVS-stream"` (the segment after the hour partition and before the filename).
- **URL Decoding**: The object key contains URL-encoded partitioning (`%3D` = `=`, `%2F` = `/`), so the actual path structure is `year=2025/month=08/day=13/hour=22/MVS-stream/filename`.
- **Processor Registry**: Map feed types to specific processor implementations
- **Default Processor**: Handle unknown feeds with generic WiFi scan processing
- **Processor Interface**: Common interface for all feed processors

### 3. S3 File Processing (Default Feed Processor)
- **Multi-step Transformation Pipeline**:
  1. Read each record from the file line by line
  2. Base64 decode the record/line
  3. Unzip/decompress the decoded content
  4. Parse JSON content (handle large JSON efficiently)
  5. Extract `wifiConnectedEvents` and `scanResults` arrays
  6. Apply sanity checks and data validation
  7. Optional: Apply OUI-based mobile hotspot detection
  8. Normalize hierarchical data to flat measurement records
  9. Apply data sanitization
  10. convert the mesurement to json accroding to   to `wifi_measurements` table schema
  11. write to Kinesis firehose in batches.

### 4. Data Transformation Schema
Transform from sample WiFi scan JSON to `wifi_measurements` table schema:

**Input Structure**:
```json
{
  "wifiConnectedEvents": [{ "wifiConnectedInfo": {...}, "location": {...} }],
  "scanResults": [{ "results": [{ "bssid": "...", "rssi": -60 }] }]
}
```

**Output Schema**: Map to `wifi_measurements` table columns
- Extract device metadata (model, manufacturer, OS version)
- Flatten location data (latitude, longitude, accuracy, timestamp)
- Set `connection_status` ('CONNECTED' vs 'SCAN')
- Calculate `quality_weight` (2.0 for CONNECTED, 1.0 for SCAN)
- Generate measurement records for each BSSID
- **wifi mesearuement schema** here is the schema
  ```sql
CREATE TABLE wifi_measurements (
  -- Primary Keys
  bssid                    STRING,
  measurement_timestamp    BIGINT,
  event_id                STRING,
  
  -- Device Information
  device_id               STRING,
  device_model            STRING,
  device_manufacturer     STRING,
  os_version              STRING,
  app_version             STRING,
  
  -- Location Data (GNSS/GPS)
  latitude                DOUBLE,
  longitude               DOUBLE,
  altitude                DOUBLE,
  location_accuracy       DOUBLE,
  location_timestamp      BIGINT,
  location_provider       STRING,
  location_source         STRING,
  speed                   DOUBLE,
  bearing                 DOUBLE,
  
  -- WiFi Signal Data
  ssid                    STRING,
  rssi                    INT,
  frequency               INT,
  scan_timestamp          BIGINT,
  
  -- Data Quality and Connection Tier
  connection_status       STRING,  -- 'CONNECTED' or 'SCAN'
  quality_weight          DOUBLE,  -- 2.0 for CONNECTED, 1.0 for SCAN
  
  -- Connected-Only Enrichment Fields (NULL for SCAN records)
  link_speed              INT,
  channel_width           INT,
  center_freq0            INT,
  center_freq1            INT,
  capabilities            STRING,
  is_80211mc_responder    BOOLEAN,
  is_passpoint_network    BOOLEAN,
  operator_friendly_name  STRING,
  venue_name              STRING,
  is_captive              BOOLEAN,
  num_scan_results        INT,
  
  -- Global Outlier Detection (stable, persistent flags)
  is_global_outlier           BOOLEAN,
  global_outlier_distance     DOUBLE,    -- Distance from AP centroid in meters
  global_outlier_threshold    DOUBLE,    -- Threshold used for detection
  global_detection_algorithm  STRING,    -- 'MAD', 'IQR', 'PERCENTILE'
  global_detection_timestamp  TIMESTAMP,
  global_detection_version    STRING,    -- Track algorithm improvements
  
  -- Ingestion and Processing Metadata
  ingestion_timestamp     TIMESTAMP,
  data_version            STRING,
  processing_batch_id     STRING,
  quality_score           DOUBLE     -- Overall quality score (0.0-1.0)
)
USING ICEBERG
PARTITIONED BY (years(ingestion_timestamp), months(ingestion_timestamp), days(ingestion_timestamp))
TBLPROPERTIES (
  'write.target-file-size-bytes' = '134217728',  -- 128MB target files
  'write.delete.mode' = 'merge-on-read',         -- Efficient deletes
  'write.update.mode' = 'merge-on-read',         -- Efficient updates
  'format-version' = '2'                         -- Enable row-level operations
);
```


### 5. Initial Data Filtering and Quality Assessment

#### Stage 1: Initial Data Ingestion and Sanity Checking
**Sanity Checks** (Discard invalid records):
- Missing or invalid coordinates (latitude/longitude outside valid ranges)
- Invalid RSSI values (outside -100 to 0 dBm range)
- Very high GPS accuracy values (> 150m - configurable threshold)
- Missing required fields (BSSID, timestamp)
- Invalid timestamp values (future dates, unreasonable past dates)
- Invalid BSSID format (non-MAC address format)

**Connection Status Quality Weighting**:
- Apply **quality_weight = 2.0** for CONNECTED data points
- Apply **quality_weight = 1.0** for SCAN data points
- For CONNECTED points with unusually low linkSpeed despite high RSSI, down-rank quality_weight to 1.5

#### Optional: Basic Mobile Hotspot Detection (MAC Address Based)
**OUI-Based Mobile Hotspot Detection** (if enabled):
- Use BSSID OUI (first 3 octets) to identify known mobile device manufacturers
- Maintain configurable OUI blacklist for common mobile hotspot vendors
- Flag and optionally exclude based on configuration
- Log mobile hotspot detection for monitoring

### 6. Schema Mapping and Data Normalization Requirements

Transform WiFi scan data to match `wifi_measurements` table schema with proper field mapping:

#### Device Information Mapping
```java
// From JSON root level
device_id = sha256(deviceId)  // Hash for privacy
device_model = model
device_manufacturer = manufacturer  
os_version = osVersion
app_version = appNameVersion
```

#### Location Data Flattening
```java
// From wifiConnectedEvents[].location and scanResults[].location
latitude = location.latitude
longitude = location.longitude
altitude = location.altitude
location_accuracy = location.accuracy
location_timestamp = location.time
location_provider = location.provider
location_source = location.source
speed = location.speed
bearing = location.bearing
```

#### WiFi Signal Data Extraction
```java
// For CONNECTED data from wifiConnectedEvents[].wifiConnectedInfo
ssid = wifiConnectedInfo.ssid
rssi = wifiConnectedInfo.rssi
frequency = wifiConnectedInfo.frequency
connection_status = "CONNECTED"
quality_weight = 2.0

// Connected-only enrichment fields
link_speed = wifiConnectedInfo.linkSpeed
channel_width = wifiConnectedInfo.channelWidth
center_freq0 = wifiConnectedInfo.centerFreq0
capabilities = wifiConnectedInfo.capabilities
is_80211mc_responder = wifiConnectedInfo.is80211mcResponder
is_passpoint_network = wifiConnectedInfo.isPasspointNetwork

// For SCAN data from scanResults[].results[]
ssid = result.ssid
bssid = result.bssid  
rssi = result.rssi
scan_timestamp = result.scantime
connection_status = "SCAN"
quality_weight = 1.0
// All connected-only fields set to NULL
```

#### Data Quality and Processing Metadata
```java
// Set during processing
ingestion_timestamp = current_timestamp()
data_version = from_json_dataVersion
processing_batch_id = generated_uuid()
quality_score = calculated_based_on_filtering_results
measurement_timestamp = wifiConnectedInfo.timestamp OR scanResults.timestamp

// Global outlier fields - set to NULL (not implemented in this service)
is_global_outlier = null
global_outlier_distance = null
global_outlier_threshold = null
global_detection_algorithm = null
global_detection_timestamp = null
global_detection_version = null
```

### 7. Data Sanitization Requirements

#### BSSID Validation and Mobile Hotspot Detection
- Validate MAC address format (XX:XX:XX:XX:XX:XX)
- Convert to lowercase for consistency
- Filter out invalid MAC addresses (all zeros, broadcast addresses)
- **Optional OUI-Based Mobile Hotspot Detection**:
  - Extract OUI (first 3 octets) from BSSID
  - Compare against configurable OUI blacklist for known mobile device manufacturers
  - Common mobile device OUIs include Apple, Samsung, Google, etc.
  - Flag or exclude based on configuration settings
  - Log detection results for monitoring

#### SSID Processing
- Handle empty/null SSID values (common in scan results)
- Trim whitespace and normalize encoding
- Filter out SSID values containing only null characters

#### Location Data Validation
- Validate coordinate ranges (latitude: -90 to 90, longitude: -180 to 180)
- Check for impossible location jumps (speed validation)
- Validate altitude values (reasonable ranges)

#### RSSI Value Sanitization
- Ensure RSSI values are within valid range (-100 to 0 dBm)
- Handle device-specific RSSI calibration if metadata available
- Flag unusual RSSI patterns for quality assessment

### 8. Kinesis Data Firehose Write Strategy
**Single Write Strategy**: AWS Kinesis Data Firehose → S3 Tables

**Kinesis Data Firehose Writing Requirements**:
- **Batch Size Optimization**: Accumulate up to 500 records or 4 MB per batch (whichever is reached first)
- **Record Size Limits**: Ensure individual JSON records are under 1000 KB
- **JSON Format**: Transform measurement records to JSON matching `wifi_measurements` schema
- **Throughput Management**: Respect 5,000 records/second limit per delivery stream
- **Error Handling**: Handle Firehose delivery failures with retry logic and DLQ
- **Partitioning**: Leverage Firehose's built-in partitioning by ingestion timestamp

**Firehose Batch Writing Strategy**:
```java
// Batch accumulation strategy
- Collect records until batch reaches 500 records OR 4 MB total size
- Send batch to Firehose using PutRecordBatch API
- Handle partial failures and retry failed records
- Handle rate limiting and throttling and network issue with fixed  number of retry with jitter. 
- Handel ResourceNotFoundException and InvalidArgumentException  and unknwown error by writing to error log without any retry.
- Monitor batch success rates and delivery latency
```

**Local Development Strategy**:
- **Log-based Testing**: Log JSON records to verify transformation correctness
- **LocalStack**: Use LocalStack Firehose simulation if available

## Technical Requirements

### 1. Spring Boot Configuration
- **Profiles**: `local`, `development`, `staging`, `production`
- **Configuration Management**: Externalized config via ConfigMaps/Secrets
- **Async Processing**: Use `@Async` and CompletableFuture for parallel operations
- **Transaction Management**: Handle batch operations transactionally

**S3 Event Structure Note**: The service receives S3 Event Notifications (not EventBridge events) with the structure `{"Records": [{"s3": {...}}]}`. This is the standard format when S3 is configured to send notifications directly to SQS queues.

### 2. SQS Integration (AWS SDK v2)
```java
// Target configuration
SqsAsyncClient sqsClient = SqsAsyncClient.builder()
    .credentialsProvider(credentialsProvider)
    .region(region)
    .build();

ReceiveMessageRequest request = ReceiveMessageRequest.builder()
    .queueUrl(queueUrl)
    .maxNumberOfMessages(10)
    .waitTimeSeconds(20)  // Long polling
    .visibilityTimeoutSeconds(300)
    .build();
```

### 3. AWS Kinesis Data Firehose Integration
**Dependencies**:
- `software.amazon.awssdk:firehose` (AWS SDK v2)

**Configuration**:
```java
FirehoseAsyncClient firehoseClient = FirehoseAsyncClient.builder()
    .credentialsProvider(credentialsProvider)
    .region(region)
    .build();

// Batch writing configuration
PutRecordBatchRequest batchRequest = PutRecordBatchRequest.builder()
    .deliveryStreamName(deliveryStreamName)
    .records(recordBatch)
    .build();
```

**Firehose Delivery Stream Requirements**:
- **Destination**: S3 Tables (Iceberg format)
- **Buffering**: 128 MB or 60 seconds (whichever comes first)
- **Error Handling**: Configure error record delivery to S3 error bucket
- **Monitoring**: CloudWatch metrics for delivery success/failure rates
- **Data Format Conversion**: Enable Parquet conversion for S3 Tables

### 4. Mobile Hotspot OUI Database Management

**OUI Database Requirements**:
- Maintain a configurable list of OUI prefixes associated with mobile devices
- Support dynamic updates without service restart
- Include common manufacturers: Apple, Samsung, Google, LG, OnePlus, etc.
- Fast in-memory lookup using prefix matching
- Configurable action on match: flag, exclude, or log only

### 5. Memory Management
- **Memory Limits**: Configure JVM heap for line-by-line file processing
- **Garbage Collection**: Optimize for batch processing patterns
- **Memory Monitoring**: Track heap usage and object allocation
- **Batch Buffer Management**: Monitor Firehose batch accumulation memory usage

## Non-Functional Requirements

### 1. Performance (Updated for Firehose Limits)
- **Throughput**: Process 10 messages respecting Firehose 5,000 records/second limit
- **Batch Efficiency**: Optimize batches to reach 500 records or 4 MB efficiently
- **Memory Efficiency**: Keep heap usage under 4GB including Firehose batching
- **Write Efficiency**: Minimize Firehose API calls through optimal batching

### 2. Reliability
- **Error Handling**: Comprehensive exception handling for each processing stage
- **Retry Logic**: Exponential backoff for transient failures
- **Circuit Breaker**: Prevent cascade failures
- **Data Integrity**: Ensure no data loss during processing
- **Firehose Resilience**: Handle delivery stream errors and throttling

### 3. Monitoring & Observability (Updated)
**Metrics (Micrometer + Prometheus)**:
- SQS message processing rates and latencies
- File processing times and sizes
- **Firehose write operation metrics**:
  - Batch sizes and success rates
  - Record serialization times
  - Delivery latencies and failures
  - Throttling events
- Memory and CPU utilization
- Custom business metrics (records processed, feeds detected)
- Data filtering metrics

**Logging (Structured JSON)**:
- Processing stages with correlation IDs
- Firehose batch composition and delivery results
- Error details with context
- Performance timings
- Data volume statistics

### 4. Kubernetes Requirements
**Health Checks**:
- **Readiness Probe**: Check SQS connectivity and Firehose delivery stream availability
- **Liveness Probe**: Monitor application health and thread pool status

**Resource Limits**:
```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "500m"
  limits:
    memory: "4Gi" 
    cpu: "2000m"
```

### 5. Security
- **IAM Roles**: Minimal required permissions for SQS, S3, and Kinesis Data Firehose
- **Encryption**: Use AWS KMS for sensitive data
- **Network Security**: VPC configuration for private subnets
- **Secret Management**: AWS Secrets Manager integration

## Configuration Structure (Updated)

### Application Properties
```yaml
# SQS Configuration
sqs:
  queue-url: ${SQS_QUEUE_URL}
  max-messages: 10
  wait-time-seconds: 20
  visibility-timeout-seconds: 300

# S3 Configuration  
s3:
  region: ${AWS_REGION}
  bucket-name: ${S3_BUCKET_NAME}
  max-file-size: 157286400  # 150MB

# Data Filtering Configuration
filtering:
  max-location-accuracy: 150.0  # meters
  min-rssi: -100  # dBm
  max-rssi: 0     # dBm
  connected-quality-weight: 2.0
  scan-quality-weight: 1.0
  low-link-speed-quality-weight: 1.5
  
  # Optional: Basic Mobile Hotspot Detection (MAC/OUI based)
  mobile-hotspot:
    enabled: false  # Can be disabled entirely
    oui-blacklist:
      - "00:23:6C"  # Apple
      - "3C:15:C2"  # Apple  
      - "58:55:CA"  # Apple
      - "40:B0:FA"  # Samsung
      - "E8:50:8B"  # Samsung

# Kinesis Data Firehose Configuration
firehose:
  enabled: ${FIREHOSE_ENABLED:true}
  delivery-stream-name: ${FIREHOSE_DELIVERY_STREAM_NAME:wifi-measurements-stream}
  max-batch-size: 500  # Maximum records per batch
  max-batch-size-bytes: 4194304  # 4 MB maximum batch size
  batch-timeout-ms: 5000  # Maximum time to wait for batch completion
  max-record-size-bytes: 1024000  # 1000 KB maximum record size
  max-retries: 3
  retry-backoff-ms: 1000

# Processing Configuration
processing:
  thread-pool-size: 1  # Single-threaded processing
  max-memory-threshold: 3221225472  # 3GB
  enable-data-sanitization: true
  
# Monitoring Configuration
monitoring:
  firehose-metrics-enabled: true
  batch-composition-logging: true
  performance-timing-enabled: true
```

## Error Handling Strategy (Updated)

### 1. Processing Stages Error Handling
- **S3 Download Failures**: Retry with exponential backoff
- **Decode/Unzip Failures**: Log and move to DLQ
- **JSON Parse Errors**: Attempt partial processing, log corruption details
- **Data Filtering Failures**: Log failed records count, continue processing valid records
- **Transformation Errors**: Skip malformed records, continue batch
- **Schema Mapping Errors**: Log field mapping failures, use default values where possible
- **Firehose Write Failures**: 
  - Handle partial batch failures
  - Retry failed records with exponential backoff
  - Move persistently failing records to DLQ
  - Monitor delivery stream health

### 2. SQS Message Lifecycle
```
Receive → Process → Transform → Firehose Write → Success: Delete | Failure: Return to queue → DLQ after max retries
```

## Data Flow Architecture (Updated)

```
SQS Events → S3 Event Extraction (Records[0].s3) → Feed Detection → Feed Processor Factory
                                      ↓
                            Default WiFi Processor
                                      ↓
S3 Download → Base64 Decode → Unzip → JSON Parse (Line by Line)
                                      ↓
                            Data Extraction
                 (wifiConnectedEvents + scanResults)
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
                         JSON Record Serialization
                                      ↓
                         Firehose Batch Accumulation
                    (500 records or 4 MB batches)
                                      ↓
                           Kinesis Data Firehose
                                      ↓
                        S3 Tables (Iceberg/Parquet)
```

**S3 Event Processing Details:**
- **Event Extraction**: Parse `Records[0].s3.bucket.name` and `Records[0].s3.object.key`
- **URL Decoding**: Decode object key to extract partitioning and stream name
- **Stream Detection**: Extract stream name from decoded object key path
- **File Processing**: Download and process the S3 object based on extracted information

## Development Environment Setup

### Local Development Stack
- **LocalStack**: S3, SQS, and Kinesis Data Firehose simulation
- **Docker Compose**: Container orchestration
- **TestContainers**: Integration testing
- **Mock Services**: Mock Firehose client for unit testing

### AWS Development Environment
- **AWS SQS**: Message queue
- **AWS S3**: File storage
- **AWS Kinesis Data Firehose**: Data delivery to S3 Tables
- **AWS CloudWatch**: Monitoring

## Testing Strategy

### Unit Testing
- Comprehensive coverage for transformation logic
- Mock AWS services for isolated testing
- Mock Firehose client for unit testing
- Firehose batch accumulation and serialization testing
- Memory usage and performance testing

### Integration Testing
- LocalStack integration testing (SQS, S3, Firehose)
- AWS service integration testing (including Firehose)
- End-to-end workflow testing
- Firehose delivery stream testing

### Performance Testing
- Load testing with Firehose throughput limits
- Memory usage profiling with batch accumulation
- Latency testing for batch delivery

## Success Criteria (Updated)

### Performance Targets
- Process files efficiently while respecting Firehose 5,000 records/second limit
- Achieve optimal Firehose batch utilization (close to 500 records or 4 MB per batch)
- Maintain memory usage under 4GB including batch buffering
- Achieve 99.9% message processing success rate
- Maintain sub-second Firehose batch delivery latency

### Operational Excellence
- Zero data loss during processing and Firehose delivery
- Automated error recovery for Firehose delivery failures
- Comprehensive monitoring of Firehose delivery metrics
- Effective filtering of invalid/corrupted data

### Data Quality Targets
- <1% invalid records after sanity checking
- Schema mapping completeness >98% for required fields
- Successful JSON serialization rate >99.9%
- Firehose delivery success rate >99.5%
- Maintain data integrity through validation and sanitization

## Firehose Delivery Stream Configuration

### Required Firehose Settings
```yaml
DeliveryStreamName: wifi-measurements-stream
DeliveryStreamType: DirectPut
Destination: ExtendedS3
ExtendedS3Configuration:
  BucketARN: arn:aws:s3:::wifi-measurements-table
  BufferingHints:
    SizeInMBs: 128
    IntervalInSeconds: 60
  CompressionFormat: GZIP
  DataFormatConversionConfiguration:
    Enabled: true
    OutputFormatConfiguration:
      Serializer:
        ParquetSerDe: {}
  DynamicPartitioning:
    Enabled: true
  ProcessingConfiguration:
    Enabled: false
  CloudWatchLoggingOptions:
    Enabled: true
    LogGroupName: /aws/kinesisfirehose/wifi-measurements-stream
```

### IAM Permissions Required
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "firehose:PutRecord",
        "firehose:PutRecordBatch",
        "firehose:DescribeDeliveryStream"
      ],
      "Resource": "arn:aws:firehose:*:*:deliverystream/wifi-measurements-stream"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::ingested-wifiscan-data/*",
        "arn:aws:s3:::wifi-measurements-table/*"
      ]
    }
  ]
}
```