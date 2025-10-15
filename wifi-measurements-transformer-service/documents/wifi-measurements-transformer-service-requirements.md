# WiFi Data Processing Microservice - Requirements Document (Updated)

## Project Overview

A Java microservice that processes WiFi scan data from SQS events, transforms the data, and writes to AWS S3 Tables via AWS Kinesis Data Firehose. The service implements feed-based processing with cost-effective SQS operations and efficient batch writing to Firehose.

### Key Achievements

- ✅ **115+ Unit Tests** with >90% code coverage
- ✅ **12+ End-to-End Automated Tests** covering all functionality
- ✅ **Automated Test Framework** with single-command execution (`./scripts/test-runner.sh`)
- ✅ **Production Ready** with comprehensive testing and validation
- ✅ **Mobile Hotspot Detection** with OUI and SSID pattern matching
- ✅ **Kubernetes Ready** with health checks and graceful shutdown

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
  6. Apply sanity checks and data validation (location, RSSI, required fields)
  7. Normalize hierarchical data to flat measurement records (create APLocationMeasurement per BSSID)
  8. Optional: Apply mobile hotspot detection and filtering (OUI-based + SSID-based)
  9. Apply data sanitization
  10. Convert the measurement to JSON according to `wifi_measurements` table schema
  11. Write to Kinesis Firehose in batches

### 4. Data Transformation Schema
Transform from sample WiFi scan JSON to `wifi_measurements` table schema:

**Input Structure**:
```json
{
  "wifiConnectedEvents": [{ "wifiConnectedInfo": {...}, "location": {...} }],
  "scanResults": [{ "results": [{ "bssid": "...", "rssi": -60 }] }]
}
```

**Output Schema**: Map to **streamlined** `wifi_measurements` table columns (optimized for AP localization)
- Set `connection_status` ('CONNECTED' vs 'SCAN')
- Calculate `quality_weight` (2.0 for CONNECTED, 1.0 for SCAN)
- Generate measurement records for each BSSID
- Include S3 `source` file path for traceability
- **wifi measurement schema** (streamlined - ~60% storage reduction with SSID retained):
  ```sql
CREATE TABLE wifi_measurements (
  -- Primary Keys
  id                      STRING,    -- Unique measurement identifier
  bssid                   STRING,    -- Access point MAC address (normalized lowercase with colons)
  measurement_timestamp   BIGINT,    -- Timestamp when measurement was taken (epoch milliseconds)
  
  -- Location Data (GNSS/GPS) - Essential for all localization algorithms
  latitude                DOUBLE,    -- Device GPS latitude in decimal degrees
  longitude               DOUBLE,    -- Device GPS longitude in decimal degrees
  altitude                DOUBLE,    -- Device GPS altitude in meters (nullable)
  location_accuracy       DOUBLE,    -- GPS horizontal accuracy in meters
  
  -- WiFi Signal Data - Essential for signal propagation models
  ssid                    STRING,    -- Network name (useful for debugging and hotspot detection)
  rssi                    INT,       -- Received Signal Strength Indicator in dBm
  frequency               INT,       -- WiFi frequency in MHz (nullable for SCAN records)
  
  -- Data Quality and Connection Tier - Critical for algorithm selection and weighting
  connection_status       STRING,    -- 'CONNECTED' or 'SCAN'
  quality_weight          DOUBLE,    -- Measurement quality weight (2.0 for CONNECTED, 1.0 for SCAN)
  
  -- Connected-Only Advanced Algorithm Fields (NULL for SCAN records)
  -- Used by MLE and Bayesian algorithms for enhanced accuracy
  link_speed              INT,       -- Network link speed in Mbps
  channel_width           INT,       -- WiFi channel width in MHz
  center_freq0            INT,       -- Center frequency 0 in MHz
  
  -- Global Outlier Detection - For filtering invalid measurements
  is_global_outlier       BOOLEAN,   -- Flag indicating if measurement is a detected outlier
  
  -- Source and Processing Metadata
  source                  STRING,    -- S3 source file path (e.g., "s3://bucket/prefix/file.json")
  ingestion_timestamp     TIMESTAMP, -- When data was ingested into the system
  data_version            STRING,    -- Version of data schema/processing
  processing_batch_id     STRING     -- Batch identifier for processing tracking
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

**Schema Optimization:** Fields have been streamlined to include only those required by WiFi access point localization algorithms, plus SSID for debugging and mobile hotspot detection. Removed fields include: device information (device_id, device_model, etc.), extended location metadata (location_provider, speed, bearing), advanced network capabilities (capabilities, 802.11mc fields, etc.), and detailed outlier metadata. New `source` field added for S3 file traceability.


### 5. Data Filtering and Quality Assessment Pipeline

#### Stage 1: Initial Data Validation (Pre-Normalization)
**Sanity Checks on Raw WiFi Scan Data** (Discard invalid records before normalization):
- Missing or invalid coordinates (latitude/longitude outside valid ranges)
- Invalid RSSI values (outside -100 to 0 dBm range)
- Very high GPS accuracy values (> 150m - configurable threshold)
- Missing required fields (BSSID, timestamp)
- Invalid timestamp values (future dates, unreasonable past dates)
- Invalid BSSID format (non-MAC address format)

**Connection Status Quality Weighting** (Applied during normalization):
- Apply **quality_weight = 2.0** for CONNECTED data points
- Apply **quality_weight = 1.0** for SCAN data points
- For CONNECTED points with unusually low linkSpeed despite high RSSI, down-rank quality_weight to 1.5

#### Stage 2: Mobile Hotspot Detection and Filtering (Post-Normalization)
**Processing Context**: This filtering stage operates on `APLocationMeasurement` objects after data normalization. Each measurement represents a single access point (BSSID) with its associated location data. Within a single WiFi scan, there can be both legitimate stationary APs and mobile hotspots - we filter at the individual measurement level to exclude only the mobile hotspots while preserving legitimate AP measurements.

Mobile hotspots are non-stationary and can significantly degrade location accuracy. The service implements two complementary approaches to detect and filter mobile hotspot measurements:

**1. OUI-Based Mobile Hotspot Detection** (MAC Address Based):
- Use BSSID OUI (first 3 octets) to identify known mobile device manufacturers
- Maintain configurable OUI blacklist for common mobile hotspot vendors
- Applied to each `APLocationMeasurement.bssid`
- Flag and optionally exclude based on configuration
- Log mobile hotspot detection for monitoring

**2. SSID-Based Mobile Hotspot Detection** (Network Name Pattern Matching):
Mobile devices use predictable naming patterns for their hotspots. The service should detect and filter measurements matching these patterns:
- Applied to each `APLocationMeasurement.ssid`
- Case-insensitive pattern matching by default

**Common Mobile Hotspot SSID Patterns**:

| Device Type | SSID Patterns | Examples | Detection Strategy |
|-------------|---------------|----------|-------------------|
| **iPhone** | `.*iPhone.*` | "John's iPhone", "iPhone 12", "My iPhone" | Case-insensitive contains "iPhone" |
| **iPad** | `.*iPad.*` | "Sarah's iPad", "iPad Pro", "My iPad" | Case-insensitive contains "iPad" |
| **Android (Generic)** | `^AndroidAP.*` | "AndroidAP", "AndroidAP1234", "AndroidAP_5678" | Starts with "AndroidAP" |
| **Samsung Galaxy** | `.*Galaxy.*` | "David's Galaxy", "Galaxy S23", "Samsung Galaxy" | Case-insensitive contains "Galaxy" |
| **Google Pixel** | `.*Pixel.*` | "Pixel 7", "Mike's Pixel", "Google Pixel" | Case-insensitive contains "Pixel" |
| **OnePlus** | `.*OnePlus.*` | "OnePlus 10", "My OnePlus", "OnePlus_AP" | Case-insensitive contains "OnePlus" |
| **Huawei** | `^HUAWEI.*`, `.*Honor.*` | "HUAWEI-P30", "Honor 20", "HUAWEI_WiFi" | Starts with "HUAWEI" or contains "Honor" |
| **Xiaomi** | `^Xiaomi.*`, `.*Mi Phone.*`, `.*Redmi.*` | "Xiaomi_12", "Mi Phone", "Redmi Note" | Starts with "Xiaomi" or contains "Mi Phone"/"Redmi" |
| **MiFi Devices** | `^MiFi.*`, `.*-MiFi-.*` | "MiFi 8800L", "Verizon-MiFi-6620L" | Starts with "MiFi" or contains "-MiFi-" |
| **Verizon Devices** | `^Verizon.*Jetpack.*` | "Verizon-AC791L-Jetpack", "Verizon Jetpack" | Starts with "Verizon" and contains "Jetpack" |
| **Generic Hotspot** | `Hotspot` | "Mobile Hotspot", "Personal Hotspot", "John's Hotspot" | Contains "Hotspot" keyword indicating mobile device |

**Implementation Requirements**:
- **Case-Insensitive Matching**: All SSID pattern matching should be case-insensitive
- **Configurable Patterns**: Patterns should be externalized in configuration for easy updates
- **Regex Support**: Support both simple string matching (CONTAINS) and regex patterns (REGEX)
- **Action Configuration**: Allow per-pattern actions (exclude, flag, log-only)
- **Performance Optimizations**:
  - **3-Tier Matching**: Fast path (string.contains) → Medium path (simple regex) → Slow path (complex regex)
  - **Priority Ordering**: Most common patterns checked first (iPhone, " Hotspot")
  - **Pattern Consolidation**: Combine similar patterns to reduce checks (Galaxy|Pixel|OnePlus)
  - **Early Exit**: Stop on first match (short-circuit evaluation)
  - **Pre-compiled Regex**: Compile all patterns once at initialization
  - **Expected Performance**: 70-80% improvement over naive sequential regex matching
- **Logging**: Log all hotspot detections with SSID, BSSID, and matched pattern
- **Metrics**: Track hotspot detection rates by pattern type

**Filtering Strategy**:
```
For each APLocationMeasurement:
  1. Check BSSID against OUI blacklist (if enabled)
  2. Check SSID against hotspot patterns (if enabled)
  3. If either check matches:
     - Action = EXCLUDE: Skip measurement entirely
     - Action = FLAG: Mark as potential hotspot but include
     - Action = LOG_ONLY: Log detection but include measurement
  4. Record detection metrics for monitoring
```

**Configuration Example**:
```yaml
filtering:
  mobile-hotspot:
    enabled: true
    action: EXCLUDE  # EXCLUDE, FLAG, or LOG_ONLY
    
    # OUI-based detection
    oui-detection:
      enabled: true
      oui-blacklist:
        - "00:23:6C"  # Apple
        - "3C:15:C2"  # Apple
        - "58:55:CA"  # Apple
        - "40:B0:FA"  # Samsung
        - "E8:50:8B"  # Samsung
    
    # SSID-based detection
    ssid-detection:
      enabled: true
      case-sensitive: false
      patterns:
        - pattern: ".*iPhone.*"
          type: CONTAINS
          description: "iPhone hotspot"
        - pattern: ".*iPad.*"
          type: CONTAINS
          description: "iPad hotspot"
        - pattern: "^AndroidAP.*"
          type: REGEX
          description: "Android hotspot"
        - pattern: ".*Galaxy.*"
          type: CONTAINS
          description: "Samsung Galaxy hotspot"
        - pattern: ".*Pixel.*"
          type: CONTAINS
          description: "Google Pixel hotspot"
        - pattern: ".*OnePlus.*"
          type: CONTAINS
          description: "OnePlus hotspot"
        - pattern: "^HUAWEI.*"
          type: REGEX
          description: "Huawei hotspot"
        - pattern: ".*Honor.*"
          type: CONTAINS
          description: "Honor hotspot"
        - pattern: "^Xiaomi.*"
          type: REGEX
          description: "Xiaomi hotspot"
        - pattern: ".*Mi Phone.*"
          type: CONTAINS
          description: "Mi Phone hotspot"
        - pattern: ".*Redmi.*"
          type: CONTAINS
          description: "Redmi hotspot"
        - pattern: "^MiFi.*"
          type: REGEX
          description: "MiFi device"
        - pattern: ".*-MiFi-.*"
          type: CONTAINS
          description: "MiFi device variant"
        - pattern: "^Verizon.*Jetpack.*"
          type: REGEX
          description: "Verizon Jetpack"
        - pattern: "Hotspot"
          type: CONTAINS
          description: "Generic mobile hotspot keyword"
```

### 6. Schema Mapping and Data Normalization Requirements

Transform WiFi scan data to match **streamlined** `wifi_measurements` table schema with proper field mapping:

#### Primary Keys and Identifiers
```java
// Generate unique identifier for each measurement
id = UUID.randomUUID().toString()
bssid = normalizedBssid(wifiConnectedInfo.bssid OR result.bssid)  // Lowercase with colons
measurement_timestamp = wifiConnectedInfo.timestamp OR scanResults.timestamp
```

#### Location Data Flattening
```java
// From wifiConnectedEvents[].location and scanResults[].location
// Essential fields for localization algorithms only
latitude = location.latitude
longitude = location.longitude
altitude = location.altitude
location_accuracy = location.accuracy
```

#### WiFi Signal Data Extraction
```java
// For CONNECTED data from wifiConnectedEvents[].wifiConnectedInfo
ssid = wifiConnectedInfo.ssid
rssi = wifiConnectedInfo.rssi
frequency = wifiConnectedInfo.frequency
connection_status = "CONNECTED"
quality_weight = 2.0

// Connected-only advanced algorithm fields (for MLE/Bayesian)
link_speed = wifiConnectedInfo.linkSpeed
channel_width = wifiConnectedInfo.channelWidth
center_freq0 = wifiConnectedInfo.centerFreq0

// For SCAN data from scanResults[].results[]
bssid = result.bssid  
ssid = result.ssid
rssi = result.rssi
frequency = null  // Not available in scan results
connection_status = "SCAN"
quality_weight = 1.0
// All connected-only fields set to NULL
```

#### Source and Processing Metadata
```java
// Set during processing
source = s3_source_file_path  // NEW: e.g., "s3://bucket/prefix/file.json"
ingestion_timestamp = current_timestamp()
data_version = from_json_dataVersion
processing_batch_id = generated_uuid()

// Global outlier field - initialized to NULL (updated by localization service)
is_global_outlier = null
```

**Note:** Streamlined schema removes device information (device_id, device_model, etc.), extended location metadata (location_provider, speed, bearing), advanced network capabilities (capabilities, 802.11mc fields, etc.), and detailed outlier metadata for ~60% storage reduction. SSID is retained for debugging and mobile hotspot detection.

### 7. Data Sanitization Requirements
**Note**: Data sanitization occurs after mobile hotspot detection (Stage 2) has filtered out unwanted measurements. Sanitization focuses on normalizing and validating the remaining legitimate measurements.

#### BSSID Validation and Normalization
- Validate MAC address format (XX:XX:XX:XX:XX:XX)
- Convert to lowercase for consistency
- Replace hyphens with colons for standardization
- Filter out invalid MAC addresses (all zeros, broadcast addresses)
- **Note**: Mobile hotspot detection (OUI-based + SSID-based) is performed separately in Stage 2 filtering - see §5 for details

#### Location Data Validation
- Validate coordinate ranges (latitude: -90 to 90, longitude: -180 to 180)
- Validate altitude values (reasonable ranges)
- Ensure location accuracy is within acceptable thresholds (< 150m by default)

#### RSSI Value Sanitization
- Ensure RSSI values are within valid range (-100 to 0 dBm)
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

### 4. Mobile Hotspot Detection Database Management

**OUI Database Requirements** (MAC Address Based):
- Maintain a configurable list of OUI prefixes associated with mobile devices
- Support dynamic updates without service restart
- Include common manufacturers: Apple, Samsung, Google, LG, OnePlus, etc.
- Fast in-memory lookup using prefix matching
- Configurable action on match: flag, exclude, or log only

**SSID Pattern Database Requirements** (Network Name Based):
- Maintain a configurable list of SSID patterns for mobile hotspot detection
- Support both simple string matching (CONTAINS) and regex patterns (REGEX)
- Pre-compile regex patterns for performance optimization
- Case-insensitive pattern matching by default
- Pattern metadata including type, description, and detection strategy
- Fast pattern matching using compiled Pattern cache
- Configurable per-pattern or global action: EXCLUDE, FLAG, or LOG_ONLY
- Support pattern priority/ordering for efficient matching
- Metrics tracking for each pattern's match rate

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
- **Mobile hotspot detection metrics**:
  - OUI-based detection rate (matches per batch)
  - SSID-based detection rate (matches per pattern)
  - Per-pattern match counts and percentages
  - Total measurements excluded/flagged by hotspot detection
  - Detection performance (pattern matching time)

**Logging (Structured JSON)**:
- Processing stages with correlation IDs
- Firehose batch composition and delivery results
- Error details with context
- Performance timings
- Data volume statistics
- Mobile hotspot detection events (SSID, BSSID, matched pattern, action taken)

### 4. Kubernetes Requirements
**Health Checks**:
- **Readiness Probe**: Check SQS connectivity and Firehose delivery stream availability
- **Liveness Probe**: Monitor application health and thread pool status

**Health Indicator Architecture**:
The service implements a simplified health model that separates connectivity concerns from operational monitoring:

#### **Connectivity Health Indicators** (Readiness - Can go DOWN)
- **`sqsConnectivity`**: Only fails when SQS queue is unreachable
- **`firehoseConnectivity`**: Only fails when Firehose delivery stream is unreachable

#### **Activity Reporting Indicators** (Liveness - Always UP)
- **`sqsActivityReporting`**: Reports SQS processing activity, rates, and success metrics
- **`firehoseActivityReporting`**: Reports Firehose delivery activity, rates, and success metrics
- **`memoryUsageReporting`**: Reports JVM memory usage statistics and recommendations

**Key Benefits**:
- ✅ **Service Stability**: Service only goes DOWN for genuine connectivity issues
- ✅ **Rich Monitoring**: Comprehensive operational metrics without affecting health status
- ✅ **Cached Lookups**: 30-second cache for AWS API calls to avoid excessive requests
- ✅ **Kubernetes Ready**: Proper separation of readiness vs liveness concerns

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
  
  # Optional: Mobile Hotspot Detection (OUI and SSID based)
  mobile-hotspot:
    enabled: false  # Can be disabled entirely
    action: EXCLUDE  # EXCLUDE, FLAG, or LOG_ONLY
    
    # OUI-based detection (MAC address)
    oui-detection:
      enabled: false
      oui-blacklist:
        - "00:23:6C"  # Apple
        - "3C:15:C2"  # Apple  
        - "58:55:CA"  # Apple
        - "40:B0:FA"  # Samsung
        - "E8:50:8B"  # Samsung
    
    # SSID-based detection (network name patterns)
    ssid-detection:
      enabled: false
      case-sensitive: false
      patterns:
        - pattern: ".*iPhone.*"
          type: CONTAINS
          description: "iPhone hotspot"
        - pattern: ".*iPad.*"
          type: CONTAINS
          description: "iPad hotspot"
        - pattern: "^AndroidAP.*"
          type: REGEX
          description: "Android hotspot"
        - pattern: ".*Galaxy.*"
          type: CONTAINS
          description: "Samsung Galaxy hotspot"
        - pattern: ".*Pixel.*"
          type: CONTAINS
          description: "Google Pixel hotspot"
        - pattern: ".*OnePlus.*"
          type: CONTAINS
          description: "OnePlus hotspot"
        - pattern: "^HUAWEI.*"
          type: REGEX
          description: "Huawei hotspot"
        - pattern: ".*Honor.*"
          type: CONTAINS
          description: "Honor hotspot"
        - pattern: "^Xiaomi.*"
          type: REGEX
          description: "Xiaomi hotspot"
        - pattern: ".*Mi Phone.*"
          type: CONTAINS
          description: "Mi Phone hotspot"
        - pattern: ".*Redmi.*"
          type: CONTAINS
          description: "Redmi hotspot"
        - pattern: "^MiFi.*"
          type: REGEX
          description: "MiFi device"
        - pattern: ".*-MiFi-.*"
          type: CONTAINS
          description: "MiFi device variant"
        - pattern: "^Verizon.*Jetpack.*"
          type: REGEX
          description: "Verizon Jetpack"
        - pattern: ".*Hotspot.*"
          type: CONTAINS
          description: "Generic hotspot"
        - pattern: "^MyWiFi.*"
          type: REGEX
          description: "Generic personal WiFi"
        - pattern: ".*Personal.*"
          type: CONTAINS
          description: "Personal WiFi network"

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
  
# Health Check Configuration
health:
  indicator:
    timeout-seconds: 5
    memory-threshold-percentage: 90
    retry-attempts: 3
    enable-caching: true
    cache-ttl-seconds: 30  # Cache AWS API calls for 30 seconds

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
                    Stage 1: Initial Data Validation
                    (Sanity checks on raw WiFi scan data)
                                      ↓
                    Schema Mapping + Data Normalization
                 (Create APLocationMeasurement per BSSID
                        + Quality weighting)
                                      ↓
                   Stage 2: Mobile Hotspot Detection
                    (Filter APLocationMeasurement by
                     OUI-based + SSID pattern matching)
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

### Unit Testing (115+ Tests, >90% Coverage)
- ✅ **Comprehensive coverage** for transformation logic
- ✅ **Mock AWS services** for isolated testing
- ✅ **Mock Firehose client** for unit testing
- ✅ **Firehose batch accumulation** and serialization testing
- ✅ **Memory usage** and performance testing
- ✅ **Mobile hotspot detection testing**:
  - OUI-based filtering with known mobile device MACs
  - SSID pattern matching (CONTAINS and REGEX types)
  - Test that hotspot measurements are filtered while legitimate APs are preserved
  - Test mixed scans containing both hotspots and legitimate APs
  - Test case-insensitive SSID matching
  - Test all configured action types (EXCLUDE, FLAG, LOG_ONLY)
  - Pattern matching performance testing

### End-to-End Testing (12+ Automated Test Cases)

**Automated Test Framework:**
- ✅ **Unified test runner** (`scripts/test-runner.sh`) - Single command runs all tests
- ✅ **Auto-discovery** - Automatically finds all test cases in `scripts/test/data/`
- ✅ **Metadata-driven validation** - Expected results embedded in test files
- ✅ **Multi-level validation** - Record counts, BSSID presence, field-level comparisons
- ✅ **Detailed reporting** - Color-coded pass/fail with summary statistics

**Test Coverage:**

1. **Core Functionality (5 tests):**
   - Basic WiFi scan with CONNECTED + SCAN events
   - CONNECTED events only
   - SCAN results only
   - Location accuracy filtering (>150m threshold)
   - RSSI range validation (-100 to 0 dBm)

2. **Mobile Hotspot Detection (3 tests):**
   - OUI/MAC-based filtering
   - SSID pattern matching (iPhone, Android, Galaxy, Pixel, etc.)
   - Combined OUI + SSID detection (validates 12 filtered, 6 preserved)

3. **Quality & Edge Cases (3 tests):**
   - Quality weight adjustment for low link speed
   - Same BSSID in CONNECTED and SCAN
   - Hidden networks with null/empty SSID

4. **Advanced Scenarios (1 test):**
   - Multi-location iPhone test with movement

**Test Execution:**
```bash
# Run all tests
cd scripts/test && ./test-runner.sh

# Run specific test
cd scripts/test && ./test-with-data-file.sh sample-wifi-scan.json

# Skip cleanup for debugging
cd scripts/test && ./test-runner.sh --skip-cleanup
```

**Test Data Format:**
Each test file includes `_test_metadata` section with:
- Test case ID and description
- Filtering configuration
- Expected results (record counts, BSSIDs, field values)
- Automated validation against actual Firehose output

### Integration Testing
- ✅ **LocalStack integration** testing (SQS, S3, Firehose)
- ✅ **AWS service integration** testing (including Firehose)
- ✅ **End-to-end workflow** testing with real data flow
- ✅ **Firehose delivery stream** testing with validation

### Performance Testing
- Load testing with Firehose throughput limits
- Memory usage profiling with batch accumulation
- Latency testing for batch delivery
- 150MB file processing validation (2-5 minutes target)

## Success Criteria

### Performance Targets
- ✅ Process files efficiently while respecting Firehose 5,000 records/second limit
- ✅ Achieve optimal Firehose batch utilization (close to 500 records or 4 MB per batch)
- ✅ Maintain memory usage under 4GB including batch buffering
- ✅ Achieve 99.9% message processing success rate
- ✅ Maintain sub-second Firehose batch delivery latency

### Operational Excellence
- ✅ Zero data loss during processing and Firehose delivery
- ✅ Automated error recovery for Firehose delivery failures
- ✅ Comprehensive monitoring of Firehose delivery metrics
- ✅ Effective filtering of invalid/corrupted data
- ✅ Kubernetes-ready health checks (readiness and liveness probes)
- ✅ Graceful shutdown handling

### Data Quality Targets
- ✅ <1% invalid records after Stage 1 sanity checking
- ✅ Schema mapping completeness >98% for required fields
- ✅ Successful JSON serialization rate >99.9%
- ✅ Firehose delivery success rate >99.5%
- ✅ Maintain data integrity through validation and sanitization
- ✅ **Mobile hotspot filtering effectiveness** (when enabled):
  - Detect and filter common hotspot patterns (iPhone, Android, Galaxy, Pixel, etc.)
  - Preserve legitimate AP measurements from the same scan
  - <0.1% false positives (legitimate APs incorrectly filtered)
  - Log all hotspot detections for monitoring and tuning

### Testing Achievements
- ✅ **115+ unit tests** with >90% code coverage
- ✅ **12+ end-to-end automated tests** covering all functionality
- ✅ **Automated test framework** with single-command execution
- ✅ **Metadata-driven validation** for comprehensive result checking
- ✅ **100% core functionality** covered by automated tests
- ✅ **Easy test extensibility** - add new tests by creating JSON files

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