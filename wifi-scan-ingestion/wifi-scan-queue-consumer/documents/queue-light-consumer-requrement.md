# Kafka to Firehose Consumer Application Requirements

## Project Overview
A Spring Boot application that consumes messages from Apache Kafka (with SSL/TLS security) and delivers them to AWS Kinesis Data Firehose, which automatically handles buffering, batching, and writing to S3.

## Technical Requirements

### Core Technologies
- Java 21
- Spring Boot 3.2.x
- Apache Kafka 3.6.x
- AWS SDK for Java 2.x (Kinesis Data Firehose)
- **Bucket4j 8.x** (Rate limiting with Spring Boot integration)
- Maven for dependency management

### Security Requirements
1. Kafka SSL/TLS Configuration
   - PKCS12 format for certificates
   - Support for both development and production certificate locations
   - Development: Certificates stored in classpath
   - Production: Certificates mounted in known location
   - Secure handling of keystore/truststore passwords

2. AWS Firehose Security
   - IAM role-based access
   - Encryption at rest and in transit
   - Secure credential management

### Functional Requirements

1. Kafka Consumer (Updated for Batch Processing)
   - **Batch processing architecture** (process entire 150-message poll result as single unit)
   - **Single-threaded consumption** (analysis shows 53-67% thread utilization is optimal)
   - **Poll size**: 150 messages per poll (provides 1.5-second processing window)
   - **Session timeout**: 45 seconds (handles Firehose retries and rate limit pauses)
   - **Max poll interval**: 300 seconds (5 minutes - allows extended rate limit backoff)
   - **Manual acknowledgment mode** (commit only after successful batch delivery to Firehose)
   - **Full batch processing** (replace current single-message processing with batch operations)
     - Accept ConsumerRecords<String, String> as input (full poll result)
     - Process all 150 messages as single batch operation
     - Apply rate limiting to entire batch rather than individual messages
     - Use parallel stream processing for message transformation within batch
   - **Batch-aware error handling** (handle individual message failures within batch context)
   - **Container-Level Pause/Resume Strategy** (NEW)
     - Use KafkaListenerEndpointRegistry for container-level pause/resume
     - Pause entire container when rate limiting occurs (stops all polling)
     - Resume container after rate limit period resolves
     - Prevents message accumulation during processing delays
     - Coordinates with Firehose rate limiting and buffer full scenarios
     - Maintains single-threaded processing model integrity
   - Poll interval optimization: Every 1.5 seconds at target consumption rate
   - Configurable consumer groups
   - Batch-level offset management (commit only after entire batch success)
   - SSL/TLS connection support

2. Firehose Integration (Updated for Batch Processing Architecture)
   - **Dynamic Firehose sub-batch sizing from full Kafka batch**:
     - Analyze full 150-message Kafka batch to determine optimal Firehose delivery strategy
     - Calculate Firehose sub-batch sizes based on: MIN(4MB ÷ avg_message_size, available_records, rate_limit_tokens)
     - Split 150-message Kafka batch into multiple optimized Firehose sub-batches
     - Firehose sub-batch size range: 40-136 records depending on message size distribution
       - 30KB messages: Up to 136 records per Firehose sub-batch
       - 100KB messages: Up to 40 records per Firehose sub-batch
     - Ensure each Firehose sub-batch stays within 4MB limit
   - **Firehose buffer configuration**:
     - Buffer size: 128MB (as specified)
     - **Buffer time: 300 seconds (5 minutes)** - optimized based on analysis
     - Expected buffer fill times: 13-44 seconds (size-based flushing will dominate)
   - **Enhanced Firehose Buffer Management and Retry Strategy**:
     - **Advanced Exception Classification**:
       - Parse AWS SDK exceptions to identify buffer-related vs. other failures
       - Classify exceptions as: Buffer Full, Rate Limit, Network Issue, or Generic Failure
       - Look for specific error codes like `ServiceUnavailableException` or rate limiting indicators
       - Create exception response matrix for appropriate handling strategies
     - **Intelligent Retry Strategy for Buffer Full Scenarios**:
       - Graduated backoff for buffer full: 5s → 15s → 45s → 2min → 5min
       - Maximum retry attempts: 5-7 times over ~15 minutes (aligns with Firehose 5-minute buffer flush)
       - Distinguish buffer full retries from rate limiting retries
       - Implement retry coordination with Kafka offset management
     - **Enhanced Kafka Offset Management During Retries**:
       - Hold offset commits until successful Firehose delivery for buffer full scenarios
       - Implement "retry window" - only acknowledge after maximum retry attempts exceeded
       - Prevent message loss during buffer full conditions
       - Coordinate offset management with batch-level retry logic
     - **Buffer-Specific Monitoring and Alerting**:
       - Track buffer full exception frequency and patterns
       - Monitor retry success rates for different exception types (Buffer Full vs Rate Limit vs Network)
       - Measure time-to-recovery from buffer full scenarios
       - Alert when buffer full rate exceeds thresholds (e.g., >10% of batches)
       - Create separate metrics for buffer health vs. rate limiting events
       - Monitor impact on overall throughput during buffer events
     - **Operational Dashboard Requirements**:
       - Show buffer full recovery times and success patterns
       - Track buffer full vs rate limiting event distinctions
       - Display retry attempt distributions and success rates by exception type
       - Monitor consumer pause events caused by buffer full vs rate limiting
   - **Legacy Batch-Coordinated Retry Handling** (Enhanced Above):
     - Coordinate with batch-level rate limiter during throttling
     - Exponential backoff with jitter (1s to 30s max delay) for non-buffer-full scenarios
     - Maximum 5 retry attempts per Firehose sub-batch for standard failures
     - Track sub-batch delivery success/failure for entire Kafka batch management
     - Consumer pause during sustained Firehose throttling
     - Ensure all Firehose sub-batches complete before committing Kafka batch offset

3. Batch Message Processing (Updated for Full Batch Operations)
   - **Batch transformation pipeline**:
     - Process entire 150-message batch as single operation
     - Parallel stream processing for concurrent message transformation
     - GZIP compression applied to all messages in parallel
     - Base64 encoding applied to compressed messages in parallel
     - Batch-level size validation (ensure ≤1MB per record after processing)
   - **Batch processing performance requirements**:
     - Total batch processing: ≤1 second for 150 messages
     - Total processing per poll cycle: ≤1.2 seconds (80% of 1.5s poll interval)
     - Parallel processing utilization within single thread
   - **Batch-aware error handling with rate limit coordination**:
     - Handle individual message failures within batch context
     - Distinguish between processing errors and rate limit throttling
     - Failed message logging with batch context and size classification
     - Circuit breaker integration with batch-level rate limiter
     - Partial batch recovery (isolate and retry failed messages)
   - **Batch validation and sanitization**
     - Batch-level message validation and sanitization
     - Bulk error handling for entire batch with detailed logging
     - Track processing time per batch for performance monitoring
   - **Message Format**: WiFi scan data messages consumed from Kafka topic
     - **Sample Message**: See `documents/smaple_wifiscan.json` for complete message structure
     - JSON format with device metadata, location data, and WiFi scan results
     - Messages processed as batch unit rather than individual processing

4. Rate Limiting Requirements (NEW SECTION)

   ### Critical Finding
   **Target consumption rate of 100 msg/sec with message sizes 30-100KB will exceed AWS Kinesis Firehose rate limits:**
   - 100KB messages: Only 51 msg/sec sustainable (5MB/sec ÷ 100KB)
   - 65KB average: Only 78 msg/sec sustainable  
   - 30KB messages: 170 msg/sec sustainable

   ### Mandatory Rate Limiting Implementation
   1. **Application-Level Rate Limiting with Bucket4j** (AWS SDK does NOT provide proactive rate limiting)
      - **Bucket4j Spring Boot Integration** with token bucket algorithm (75 tokens/second baseline rate)
      - **Multiple Bucket4j buckets** for dynamic rate adjustment based on message size detection
      - **Configurable Bucket4j rate limits** per message size threshold:
      - Small messages (≤40KB): Allow up to 100 msg/sec using dedicated Bucket4j bucket
      - Large messages (≥80KB): Limit to 50 msg/sec using dedicated Bucket4j bucket
      - **Bucket4j coordination** with Kafka consumer polling using tryConsume() methods

   2. **Consumer Management During Bucket4j Rate Limiting**
      - Automatic consumer pause when Bucket4j bucket approaches limit (≤10% tokens available)
      - Exponential backoff for pause duration (5s to 60s)
      - Consumer resume when Bucket4j bucket has sufficient tokens
      - Prevent consumer timeout during rate limiting pauses
      - Integration with Bucket4j tryConsume() and tryConsumeAndReturnRemaining() for non-blocking operations

   3. **Bucket4j Rate Limiting Monitoring**
      - Real-time Bucket4j token utilization metrics across all buckets
      - Bucket4j rate limiting event logging and alerting
      - Message size distribution tracking with bucket-specific metrics
      - Sustainable throughput monitoring per message size using Bucket4j metrics
      - Integration with Micrometer for Bucket4j metrics export to Prometheus
      - Bucket4j bandwidth consumption and refill rate monitoring

### Bucket4j Integration Requirements (NEW SECTION)

#### Core Bucket4j Features Required
1. **Spring Boot Starter Integration**
   - Use `bucket4j-spring-boot-starter` for seamless Spring Boot integration
   - Automatic configuration and bean management
   - Spring Boot properties-based configuration

2. **Multiple Bucket Strategy**
   - **Small Message Bucket**: 100 tokens/second, 150 capacity for messages ≤40KB
   - **Large Message Bucket**: 50 tokens/second, 150 capacity for messages ≥80KB  
   - **Medium Message Bucket**: Dynamic scaling between 50-100 tokens/second for 40-80KB messages
   - Bucket selection based on runtime message size analysis

3. **Bucket4j Configuration Features**
   - **Bandwidth Configuration**: Time-based token refill with configurable intervals
   - **Cache Integration**: In-memory cache for bucket state (JCache compatible)
   - **Metrics Integration**: Built-in Micrometer metrics support
   - **Non-blocking Operations**: Use `tryConsume()` and `tryConsumeAndReturnRemaining()`

4. **Production-Ready Features**
   - **Thread Safety**: Bucket4j provides thread-safe token bucket operations
   - **Performance**: Optimized lock-free implementation for high throughput
   - **Monitoring**: Built-in metrics and monitoring capabilities
   - **Configurability**: Runtime configuration changes without restart

#### Implementation Architecture
```
Message Processing Flow with Bucket4j:
1. Kafka Consumer polls 150 messages
2. Analyze message sizes to determine bucket selection
3. For each batch: tryConsume(batch_size) from appropriate Bucket4j bucket
4. If tokens available: Process batch and send to Firehose
5. If tokens insufficient: Pause consumer, wait for refill, resume
6. Metrics: Track token consumption, bucket utilization, pause events
```

### Non-Functional Requirements

## Updated Non-Functional Requirements

1. Performance (Updated)
   - **Throughput targets**:
     - Baseline: 75 messages/second sustainable rate
     - Peak (small messages): Up to 100 messages/second  
     - Peak (large messages): Up to 50 messages/second
   - **Latency requirements**:
     - Message processing: ≤800ms per 150-message poll
     - End-to-end delivery: ≤60 seconds (including Firehose buffering)
   - **Thread utilization**:
     - Single consumer thread: 53-67% utilization
     - Safety margin: 500-900ms per poll cycle
   - **Memory efficiency**:
     - Poll-based processing: ~150 messages in memory maximum
     - No message queuing (direct poll-to-Firehose processing)

2.  Reliability (Updated)
    - **Rate limit resilience**:
      - Zero message loss during rate limiting events
      - Automatic recovery from throttling conditions
      - Consumer timeout prevention during extended rate limiting
    - **Firehose delivery guarantees**:
      - At-least-once delivery semantics
      - Manual offset commit after successful Firehose acknowledgment
      - Buffer boundary handling (prevent partial batch acceptance issues)

3. Scalability
   - Horizontal scaling support
   - Configurable consumer threads
   - Dynamic throughput adjustment
   - Efficient resource utilization

4. Monitoring
   - Health check endpoints
   - Metrics collection
   - Performance monitoring
   - Error tracking
   - Firehose delivery status monitoring
   - Comprehensive Spring Boot Actuator health indicators (detailed requirements below)


### Updated Configuration Requirements

#### Critical Application Configuration Parameters
```yaml
# Kafka Consumer Configuration
spring.kafka.consumer:
  max-poll-records: 150
  session-timeout: 45s
  max-poll-interval: 300s  # 5 minutes
  heartbeat-interval: 15s
  fetch-min-size: 4608KB   # ~150 × 30KB minimum
  fetch-max-wait: 500ms

# Bucket4j Rate Limiting Configuration
bucket4j:
  enabled: true
  default-metric-tags:
    - key: "service"
      value: "wifi-scan-consumer"
  filters:
    - cache-name: buckets
      rate-limits:
        - bandwidths:
            - capacity: 150      # 2x poll size
              time: 1
              unit: seconds
              refill-speed: interval
              refill-tokens: 75  # baseline rate

app.rate-limiting:
  dynamic-adjustment: true
  small-message-threshold: 40KB
  large-message-threshold: 80KB
  small-message-max-rate: 100
  large-message-max-rate: 50
  bucket-names:
    small-messages: "small-msg-bucket"
    large-messages: "large-msg-bucket"
    medium-messages: "medium-msg-bucket"

# Firehose Configuration  
aws.firehose:
  buffer-time: 300         # 5 minutes (optimized)
  batch-processing:
    min-batch-size: 10
    max-batch-size: 150    # Can use full poll
    dynamic-sizing: true
    timeout: 5s

# Consumer Pause/Resume
app.consumer.rate-limit-management:
  pause-threshold: 0.1     # Pause when <10% tokens available
  min-pause-duration: 5s
  max-pause-duration: 60s
  pause-multiplier: 1.5
```

### Health Indicator Requirements

#### Readiness Probe Requirements
**Purpose**: Determine if the application is ready to receive traffic and process messages

##### Core Readiness Checks:
1. **Spring Boot Built-in Kafka Health Indicator** *(Already Enabled)*
   - Spring Boot's built-in `KafkaHealthIndicator` provides basic broker connectivity checking
   - Uses Kafka Admin API `describeCluster()` for broker reachability testing
   - Enabled via `management.health.kafka.enabled: true` (already configured)
   - Provides cluster ID, node count, and basic connection status
   - **Note**: Does NOT check consumer-specific functionality or SSL certificates

2. **Kafka Consumer Group Registration Health Indicator**
   - Confirm consumer group is properly registered with Kafka cluster
   - Verify consumer group coordinator assignment
   - Check that consumer has been assigned partitions (if auto-assignment enabled)
   - Validate consumer group membership status

3. **Topic Accessibility Health Indicator**
   - Verify access to configured topics without consuming messages
   - Check topic metadata availability
   - Validate topic permissions for the configured consumer group
   - Confirm topics exist and are not marked for deletion

4. **Enhanced SSL/TLS Certificate Health Indicator**
   - **Strategic Integration with Kubernetes Readiness Monitoring**: Leverage readiness probe for SSL certificate validation and CloudWatch integration
   - **CloudWatch Integration via Readiness**: Use Kubernetes readiness metrics flowing to CloudWatch for automated certificate expiry alerting
   - **Certificate Expiry Timeline Management**:
     - PASS (UP): Certificate valid and not expiring soon (>30 days)
     - PASS with WARNING (UP + details): Certificate expiring within configurable timeframe (30/15/7 days)
     - FAIL (DOWN): Certificate expired or completely invalid - removes pod from service traffic
   - **Proactive Alert Timeline via Readiness**:
     - 30 days: CloudWatch warning alert → Plan certificate renewal
     - 15 days: CloudWatch critical alert → Execute certificate renewal
     - 7 days: CloudWatch urgent alert → Emergency certificate renewal procedures
     - 0 days (expired): Readiness failure → Pod removed from service, manual certificate renewal
   - **Validate keystore and truststore accessibility from configured paths**
   - **Check certificate chain integrity and SSL/TLS handshake capability**
   - **Zero Infrastructure Overhead**: Uses existing Kubernetes readiness probe and CloudWatch monitoring stack
   - **Graceful Degradation**: Pod removed from traffic but stays running, allowing time for certificate renewal

##### Readiness Configuration Requirements:
- Configurable timeout for each health check (default: 10 seconds total)
- Configurable retry attempts for transient failures
- Ability to disable specific health checks via configuration
- Fail-fast behavior when critical dependencies are unavailable
- Health check caching to prevent excessive broker calls (cache TTL: 30 seconds)
- **Enhanced SSL Certificate Configuration**:
  - Configurable warning thresholds (default: 30, 15, 7 days before expiry)
  - Configurable certificate check intervals within readiness probe frequency
  - Certificate validation timeout settings (separate from overall readiness timeout)
  - Enable/disable certificate expiry warnings vs immediate failures
- **CloudWatch Integration Configuration**:
  - Kubernetes readiness metric export configuration for certificate expiry timeline
  - CloudWatch alert threshold configuration for certificate warnings
  - Event-based alerting setup for certificate state changes via readiness probe

#### Liveness Probe Requirements
**Purpose**: Determine if the application is still running and healthy

##### Core Liveness Checks:
1. **Application Thread Health Indicator**
   - Monitor Kafka consumer thread pool status
   - Detect deadlocked or hanging consumer threads
   - Verify consumer poll loop is active and responsive
   - Check for consumer thread exceptions or crash detection

2. **Memory Health Indicator**
   - Monitor JVM heap memory usage (fail if >90% for sustained period)
   - Check for memory leaks in message processing
   - Monitor off-heap memory if applicable
   - Detect excessive garbage collection activity

3. **Message Consumption Activity Health Indicator**
   - Track consumer poll activity (not just message count)
   - Monitor time since last successful poll operation (fail if >configurable threshold, default: 5 minutes)
   - Verify consumer is actively polling even when no messages available
   - Track consumer position advancement over time
   - Monitor consumer session activity and heartbeat status
   - Detect consumer stuck conditions (polling but not advancing)
   - **Track message consumption count and trends for processing pipeline health**
     - Monitor messages processed per time window (e.g., last 10 minutes)
     - Compare current consumption rate with historical baseline
     - Detect sustained periods of zero message processing when messages are available
     - Fail if consumer is polling but consistently failing to process available messages
     - Distinguish between "no messages available" vs "messages available but not processed"
     - Use consumer lag metrics to determine if messages are waiting to be processed
     - Track message processing success/failure ratios
     - Configure consumption rate thresholds based on expected message volume

4. **Consumer Heartbeat Health Indicator**
   - Monitor Kafka consumer heartbeat status
   - Detect consumer session timeouts
   - Verify consumer is still part of the consumer group
   - Check for rebalancing issues



##### Liveness Configuration Requirements:
- Less frequent checks than readiness (default: every 30 seconds)
- Higher tolerance for temporary failures
- Configurable thresholds for memory usage
- Configurable timeout for message consumption activity (default: 5 minutes without poll activity)
- Configurable message consumption rate thresholds (messages per time window)
- Configurable baseline period for establishing consumption rate patterns
- Graceful degradation for non-critical failures

#### Health Endpoint Configuration Requirements


##### Response Format Requirements:
- Standard Spring Boot Actuator health response format
- Detailed status information in response body
- Timestamp of last successful check
- Error details for failed health checks
- Performance metrics in health response
- Message consumption activity metrics

#### Kubernetes Integration Requirements:
1. **Readiness Probe Configuration**
   - HTTP endpoint: `/frisco-location-wifi-scan-vmb-consumer/health/readiness`
   - Initial delay: 30 seconds (allow for Kafka connection establishment)
   - Period: 10 seconds
   - Timeout: 5 seconds
   - Failure threshold: 3 consecutive failures
   - Success threshold: 1 success to mark ready
   - **Enhanced SSL Certificate Integration**: Include SSL certificate health as part of readiness monitoring
   - **CloudWatch Metrics Export**: Configure automatic export of certificate expiry metrics to CloudWatch via readiness probe
   - **Event Generation**: Enable Kubernetes event generation for certificate state changes via readiness monitoring

2. **Liveness Probe Configuration**
   - HTTP endpoint: `/frisco-location-wifi-scan-vmb-consumer/health/liveness`
   - Initial delay: 60 seconds (allow for application startup)
   - Period: 30 seconds
   - Timeout: 10 seconds
   - Failure threshold: 3 consecutive failures
   - Success threshold: 1 success to mark healthy

#### Custom Health Indicator Requirements

##### Kafka-Specific Health Indicators:
**Note**: Spring Boot's built-in `KafkaHealthIndicator` already provides basic broker connectivity checking via Kafka Admin API (enabled via `management.health.kafka.enabled: true`), so custom connectivity indicators are not needed.

1. **KafkaConsumerHealthIndicator**
   - Monitors consumer group status
   - Tracks partition assignments
   - Reports consumer lag metrics
   - Includes last poll timestamp and message consumption activity

2. **KafkaSSLHealthIndicator** *(Enhanced Readiness Monitoring)*
   - **Strategic Kubernetes Integration**: Leverages readiness probe monitoring for certificate management
   - **CloudWatch-Driven Alerting**: Uses Kubernetes readiness metric export for automated certificate expiry alerts
   - **Validates SSL/TLS configuration and certificate accessibility**
   - **Checks certificate expiration with configurable warning timeline (30/15/7 days)**
   - **Reports SSL handshake status and cipher suite information**
   - **Graceful service degradation for expired certificates (pod removed from traffic)**
   - **Zero additional monitoring infrastructure required**

3. **MessageConsumptionActivityHealthIndicator**
   - Tracks consumer polling activity
   - Monitors message processing throughput and count trends
   - Reports time since last poll operation
   - Includes consumer position advancement metrics
   - Reports message consumption rate and baseline comparisons

#### Health Indicator Grouping:
- **Readiness Group**: Include connectivity and dependency checks (kafkaConsumerGroup, kafkaTopicAccessibility, sslCertificate)
- **Liveness Group**: Include essential application health checks (messageConsumptionActivity, jvmMemory)
- **SSL Certificate Strategic Placement**: Remains in readiness for proper Kubernetes behavior and CloudWatch integration
- Custom groups for specific monitoring requirements

#### Monitoring and Alerting Requirements:

##### Metrics Integration:
- Export health check results as metrics (Micrometer/Prometheus)
- Track health check execution time
- Monitor health check failure rates
- Alert on sustained health check failures
- Export message consumption activity metrics and trends
- **SSL Certificate CloudWatch Integration**:
  - Export certificate expiry timeline metrics from Kubernetes readiness probes
  - Configure CloudWatch alarms for certificate expiry warnings (30/15/7 days)
  - Monitor certificate renewal events and automation success rates
  - Track certificate-related readiness failures and service degradation events
  - **Operational Alert Timeline**:
    - Day -30: CloudWatch WARNING → Plan certificate renewal
    - Day -15: CloudWatch CRITICAL → Execute certificate renewal
    - Day -7: CloudWatch URGENT → Emergency renewal procedures
    - Day 0: Readiness FAILURE → Pod removed from service, manual certificate renewal required
-  **Rate Limiting Metrics (NEW)**
   - **Token utilization percentage** (alert when >90%)
   - **Rate limiting events per hour** (alert when >10)
   - **Message size distribution** (track 30KB, 65KB, 100KB message percentages)
   - **Consumer pause/resume events** (alert on excessive pausing)
   - **Sustainable throughput per message size** (compare actual vs theoretical limits)
- **Processing Performance Metrics (Updated)**
  - **Poll processing time** (alert when >1.2 seconds)
  - **Thread utilization percentage** (alert when >80%)
  - **Buffer fill time tracking** (expected: 13-44 seconds)
  - **Firehose batch composition** (track batch size distribution 40-136 records)
  - **Consumer lag with rate limit context** (distinguish lag causes)

- **Firehose Integration Metrics (Updated)**
  - **Batch rejection rate** (alert when >5%)
  - **Dynamic batch size effectiveness** (track size vs success rate correlation)
  - **Firehose throttling events** (distinguish from application rate limiting)

- **Enhanced Firehose Buffer Management Metrics (NEW)**
  - **Buffer Full Exception Frequency**:
    - Track buffer full exception count per hour/day
    - Alert when buffer full rate exceeds 10% of total batches
    - Monitor buffer full patterns by time of day and message volume
  - **Retry Success Rates by Exception Type**:
    - Buffer Full retry success rate (target: >90%)
    - Rate Limit retry success rate (target: >95%)
    - Network Issue retry success rate (target: >85%)
    - Generic Failure retry success rate (target: >70%)
  - **Time-to-Recovery Metrics**:
    - Average recovery time from buffer full scenarios
    - Maximum recovery time tracking (alert if >15 minutes)
    - Recovery time distribution (5s, 15s, 45s, 2min, 5min intervals)
  - **Operational Dashboard Metrics**:
    - Buffer health status vs rate limiting status (separate indicators)
    - Buffer full recovery patterns and success trends
    - Retry attempt distribution by exception type
    - Consumer pause events: buffer full vs rate limiting causes
    - Throughput impact during buffer events (percentage degradation)
  - **Enhanced Offset Management Metrics**:
    - Messages held during buffer full retries (count and duration)
    - Offset commit delays due to buffer full conditions
    - Message loss prevention effectiveness (should be 0%)
    - Retry window utilization and effectiveness


##### Logging Requirements:
- Log health check failures with detailed error information
- Structured logging for monitoring system integration
- Configurable log levels for health check events
- Performance logging for slow health checks
- Log message consumption activity events and rate changes

#### Error Handling Requirements:
- Graceful degradation when health checks fail
- Circuit breaker pattern for external dependency checks
- Retry logic with exponential backoff
- Fallback responses for critical health check failures

### Configuration Requirements

1. Environment-Specific Configurations
   - Development
   - Testing
   - Production
   - Support for different regions
   - Configurable endpoints

2. Kafka Configuration
   - Bootstrap servers
   - Consumer group ID
   - SSL/TLS settings
   - Topic configuration
   - Partition assignment
   - Consumer batch size
   - Poll timeout settings

3. Firehose Configuration
   - **Delivery stream name**: `MVS-stream` (configured in LocalStack)
   - **AWS region**: `us-east-1`
   - **LocalStack endpoint**: `http://localhost:4566` (development)
   - **AWS_ACCESS_KEY_ID**=test
   - **AWS_SECRET_ACCESS_KEY**=test
   - Retry configuration
   - Error handling settings
   - Batch delivery settings
   - Timeout configurations


#### Configuration Benefits
- **Environment Variable Support**: All values can be overridden via environment variables using `${ENV_VAR:default_value}` syntax
- **LocalStack Ready**: Default values work with your configured LocalStack setup
- **Production Ready**: Easy to externalize sensitive values for other environments



### Development Requirements

## LocalStack Firehose Configuration (Development Environment)

### Configured Delivery Stream
- **Stream Name**: `MVS-stream`
- **LocalStack Endpoint**: `http://localhost:4566`
- **AWS Region**: `us-east-1`
- **Environment Variables**:
  ```bash
  AWS_ACCESS_KEY_ID=test
  AWS_SECRET_ACCESS_KEY=test
  AWS_ENDPOINT_URL=http://localhost:4566
  AWS_DEFAULT_REGION=us-east-1
  ```

### LocalStack Integration Requirements
1. **Firehose Service Configuration**
   - Kinesis Data Firehose service enabled in LocalStack
   - S3 service for destination bucket
   - IAM service for role-based permissions

2. **Application Configuration**
   - Configurable endpoints for LocalStack vs AWS
   - Environment-specific credential handling
   - Endpoint URL override for development

### Message Flow (LocalStack)
```
Kafka Topic → Consumer Application → LocalStack Firehose (MVS-stream) → LocalStack S3 (ingested-wifiscan-data)
```

## AWS Firehose Configuration (Production Environment)

### Delivery Stream Settings
- Buffer size: 128 MB (configurable)
- Buffer interval: 300 seconds (configurable)
- Destination: S3 bucket with organized path structure


## Success Criteria
1. Successful message consumption from Kafka with SSL/TLS
2. Reliable delivery to Kinesis Data Firehose
3. Configurable performance parameters
4. Comprehensive monitoring and alerting
5. Secure handling of credentials and certificates
6. Scalable and maintainable architecture
7. Cost-effective operation within budget constraints


## Dependencies
1. Apache Kafka cluster with SSL/TLS
2. **LocalStack Kinesis Data Firehose**: `MVS-stream` delivery stream (development)
3. **LocalStack S3 bucket**: `ingested-wifiscan-data` (configured via Firehose)
4. SSL/TLS certificates
5. **LocalStack Services**: Firehose, S3, IAM (running on localhost:4566)
6. **Bucket4j Library**: 
   - `bucket4j-spring-boot-starter` for Spring Boot integration
   - JCache implementation (caffeine-jcache or similar) for bucket state storage
   - Micrometer integration for metrics collection

## Architecture Decision Rationale

### Single-Threaded Consumer Decision
**Analysis Result**: Feasible with 53-67% thread utilization
- **Poll processing time**: 600-1000ms per 150-message batch
- **Available processing window**: 1500ms per poll cycle  
- **Safety margin**: 500-900ms for error handling and retries
- **Scaling limit**: ~130 msg/sec maximum before requiring multi-threading

### Buffer Time Optimization Decision  
**Analysis Result**: 300 seconds optimal vs 900 seconds default
- **Buffer fill times**: 13-44 seconds (size-based flushing dominates)
- **Risk mitigation**: Provides 6-23x safety margin over actual fill times
- **Operational benefit**: Faster data availability in S3 for downstream processing

### Rate Limiting Strategy Decision
**Analysis Result**: Application-level mandatory, SDK insufficient
- **SDK limitation**: Only provides reactive retries, no proactive rate limiting
- **Risk without rate limiting**: Cascading failures, consumer timeouts, message replay
- **Implementation requirement**: Token bucket with consumer coordination

## Cost Impact Analysis

### Rate Limiting Cost Benefits
- **Prevent throttling charges**: Avoid sustained Firehose throttling penalties
- **Predictable throughput**: Enable accurate cost forecasting
- **Efficient resource utilization**: Optimize Firehose buffer usage vs cost

### Expected Operational Costs
- **Data ingestion**: ~$0.029 per GB at 75 msg/sec average rate
- **Processing efficiency**: 99%+ buffer utilization (minimal waste)
- **Monitoring overhead**: Standard CloudWatch costs for rate limiting metrics
