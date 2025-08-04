# WiFi Measurements Transformer Service

A Java microservice that processes WiFi scan data from SQS events, transforms the data, and writes to AWS S3 Tables using Apache Iceberg format. The service implements feed-based processing with cost-effective SQS operations and efficient batch writing.

## Overview

This microservice is part of the WiFi location data pipeline and handles:

- **SQS Message Processing**: Long polling, batch receive, and parallel processing
- **Feed-Based Processing**: Pluggable feed-specific processors with factory pattern
- **Data Transformation**: WiFi scan data normalization and filtering
- **Data Quality Assessment**: Sanity checks and mobile hotspot detection
- **Dual Write Strategy**: Apache Iceberg for local development, AWS Data API for production
- **Monitoring & Observability**: Comprehensive metrics and structured logging

## Features

### Core Capabilities

- ✅ **Batch SQS Processing**: Process up to 10 messages per batch with long polling
- ✅ **Data Filtering**: Stage 1 sanity checks and quality assessment
- ✅ **Mobile Hotspot Detection**: Optional OUI-based MAC address filtering
- ✅ **Schema Mapping**: Transform WiFi scan data to normalized measurement records
- ✅ **Environment-Based Writing**: Iceberg for local, Data API for AWS
- ✅ **Structured Logging**: JSON logging with correlation IDs
- ✅ **Health Checks**: Kubernetes-ready probes and metrics

### Performance Targets

- Process 150MB files within 2-5 minutes
- Handle 5-10 concurrent message batches
- Maintain memory usage under 4GB
- Achieve 99.9% message processing success rate

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker (for LocalStack)
- AWS CLI v2

### Local Development Setup

1. **Start LocalStack**:
   ```bash
   docker run --rm -it -p 4566:4566 -p 4510-4559:4510-4559 localstack/localstack
   ```

2. **Setup Local Infrastructure**:
   ```bash
   cd wifi-measurements-transformer-service
   ./scripts/setup-localstack.sh
   ```

3. **Run the Application**:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

4. **Verify Setup**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### Cleanup Local Environment

```bash
./scripts/cleanup-localstack.sh
```

## Configuration

### Environment Profiles

- **local**: Uses LocalStack for all AWS services
- **development**: AWS services with enhanced logging
- **staging**: AWS services with production-like settings
- **production**: Optimized for production with Data API

### Key Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `sqs.queue-url` | LocalStack URL | SQS queue for processing |
| `sqs.max-messages` | 10 | Max messages per batch |
| `s3.bucket-name` | ingested-wifiscan-data | S3 bucket for input files |
| `filtering.max-location-accuracy` | 150.0 | GPS accuracy threshold (meters) |
| `filtering.mobile-hotspot.enabled` | false | Enable OUI-based filtering |
| `processing.batch-write-size` | 1000 | Records per write batch |

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SQS_QUEUE_URL` | Production | SQS queue URL |
| `S3_BUCKET_NAME` | Production | S3 bucket name |
| `AWS_REGION` | Production | AWS region |
| `ICEBERG_WAREHOUSE_PATH` | Production | Iceberg warehouse S3 path |

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
                    Environment-Based Writer
                                      ↓
              Local: Iceberg API | AWS: Data API
                                      ↓
                        S3 Tables (Iceberg)
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

#### Optional: Mobile Hotspot Detection
- OUI-based MAC address filtering
- Configurable action: FLAG, EXCLUDE, or LOG_ONLY
- Common mobile device manufacturer detection

## Monitoring

### Health Endpoints

- `/actuator/health` - Application health status
- `/actuator/health/readiness` - Kubernetes readiness probe
- `/actuator/health/liveness` - Kubernetes liveness probe

### Metrics

- `/actuator/metrics` - Micrometer metrics
- `/actuator/prometheus` - Prometheus format metrics

### Key Metrics

- `sqs.messages.processed` - SQS message processing rate
- `s3.files.processed` - File processing throughput
- `data.filtering.rejected` - Data quality filtering stats
- `iceberg.writes.success` - Write operation success rate

## Development

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests with TestContainers
mvn verify -Pintegration-tests

# Coverage report
mvn jacoco:report
```

### Code Quality

- Test coverage target: >90%
- SonarQube analysis: `mvn sonar:sonar`
- Security scanning: `mvn dependency-check:check`

### Adding New Feed Processors

1. Implement the `FeedProcessor` interface
2. Register in `FeedProcessorFactory`
3. Add configuration properties
4. Create unit and integration tests

## Deployment

### Docker

```bash
# Build
mvn spring-boot:build-image

# Run
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=production \
  -e SQS_QUEUE_URL=... \
  wifi-measurements-transformer-service:latest
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wifi-measurements-transformer
spec:
  replicas: 3
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
        image: wifi-measurements-transformer-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        resources:
          requests:
            memory: "2Gi"
            cpu: "500m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
```

## Troubleshooting

### Common Issues

1. **LocalStack Connection Failed**
   - Ensure LocalStack is running: `docker ps`
   - Check endpoint: `curl http://localhost:4566/health`

2. **SQS Queue Not Found**
   - Run setup script: `./scripts/setup-localstack.sh`
   - Verify queue exists: `aws --endpoint-url=http://localhost:4566 sqs list-queues`

3. **Memory Issues**
   - Adjust JVM heap size: `-Xmx4g`
   - Check processing batch size configuration
   - Monitor memory usage: `/actuator/metrics/jvm.memory.used`

### Debug Logging

Enable debug logging for specific components:

```yaml
logging:
  level:
    com.wifi.measurements.transformer: DEBUG
    software.amazon.awssdk: DEBUG
    org.apache.iceberg: DEBUG
```

## Contributing

1. Follow the established code style and patterns
2. Write comprehensive tests for new features
3. Update documentation for configuration changes
4. Ensure all CI checks pass before submitting PRs

## Performance Tuning

### JVM Options

```bash
java -Xmx4g -Xms2g \
     -XX:+UseG1GC \
     -XX:G1HeapRegionSize=16m \
     -XX:+UseStringDeduplication \
     -jar wifi-measurements-transformer-service.jar
```

### Production Settings

- Increase thread pool sizes for higher throughput
- Adjust batch sizes based on memory constraints
- Enable mobile hotspot filtering for data quality
- Use Data API for optimal write performance

## License

[Add your license information here] 