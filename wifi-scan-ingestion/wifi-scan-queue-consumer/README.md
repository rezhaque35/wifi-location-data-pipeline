# WiFi Scan Queue Consumer

A high-performance Spring Boot Kafka consumer application designed to process WiFi scan messages with SSL/TLS support, comprehensive health monitoring, and production-ready features.

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)
- [Monitoring](#monitoring)
- [Scripts](#scripts)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [Firehose Integration](#firehose-integration)

## 🌟 Overview

The WiFi Scan Queue Consumer is a robust microservice that:
- Consumes WiFi scan data messages from Apache Kafka topics
- Provides SSL/TLS encrypted communication with Kafka brokers
- Offers comprehensive health monitoring and metrics
- Supports high-throughput message processing with configurable concurrency
- Implements production-ready logging, error handling, and observability

## ✨ Features

### Core Functionality
- **Kafka Integration**: High-performance message consumption with Spring Kafka
- **SSL/TLS Support**: Secure communication with encrypted Kafka clusters
- **AWS Firehose Integration**: Stream data to S3 with Kinesis Data Firehose
- **Health Monitoring**: Comprehensive health checks for service components
- **Metrics & Observability**: Built-in metrics collection and monitoring endpoints
- **Error Handling**: Robust error handling with retry mechanisms

### Production Ready
- **Auto-configuration**: Spring Boot auto-configuration for easy setup
- **Environment Profiles**: Support for development, test, and production environments
- **Actuator Endpoints**: Health checks, metrics, and application information
- **Graceful Shutdown**: Proper resource cleanup and shutdown handling
- **Memory Management**: Optimized memory usage with monitoring

### Developer Experience
- **Comprehensive Testing**: Unit tests, integration tests, and test automation
- **Development Scripts**: Automated setup and validation scripts
- **Documentation**: Extensive documentation and examples
- **Lombok Integration**: Reduced boilerplate code with Lombok annotations

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Kafka Broker  │───▶│  WiFi Consumer  │───▶│  AWS Firehose   │───▶│  S3 Bucket      │
│   (SSL/TLS)     │    │  Service        │    │  Delivery       │    │  (Partitioned)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │  Health &       │
                       │  Metrics        │
                       └─────────────────┘
```

### Package Structure

```
com.wifi.scan.consume/
├── config/           # Configuration classes
├── controller/       # REST controllers for metrics
├── health/          # Custom health indicators
├── listener/        # Kafka message listeners
├── metrics/         # Metrics collection
└── service/         # Business logic services
```

## 📋 Prerequisites

- **Java 21** or higher
- **Apache Maven 3.8+**
- **Docker & Docker Compose** (for local Kafka and LocalStack)
- **curl** (for health checks)
- **jq** (for JSON processing in scripts)
- **AWS CLI** (for AWS services - will be installed automatically)

### Optional
- **Apache Kafka** (if not using Docker)
- **SSL certificates** (for encrypted communication)
- **LocalStack** (for local AWS services development)

## 🚀 Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd wifi-scan-queue-consumer
mvn clean install
```

### 2. Complete Environment Setup

Run the comprehensive setup script that includes both Kafka and AWS infrastructure:

```bash
./scripts/setup-dev-environment.sh
```

This will set up:
- Kafka with SSL encryption
- LocalStack with AWS services (Firehose, S3, IAM)
- All necessary AWS resources
- Test the complete environment

### 3. Start the Application

```bash
mvn spring-boot:run
```

### 4. Run Integration Tests

```bash
# Run comprehensive test suite
./scripts/run-test-suite.sh

# Or test Firehose integration specifically
./scripts/validate-firehose-integration.sh
```

### 2. Start Local Kafka (Docker)

```bash
# Start Kafka with Docker Compose
./scripts/start-local-kafka.sh

# Create required topic
./scripts/create-test-topic.sh
```

### 3. Run the Application

```bash
# Development mode
mvn spring-boot:run

# Or run the JAR
java -jar target/wifi-scan-queue-consumer-1.0.0-SNAPSHOT.jar
```

### 4. Verify Installation

```bash
# Check health
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health

# Run validation tests
./scripts/run-test-suite.sh
```

## ⚙️ Configuration

### Application Properties

The application uses `application.yml` for configuration:

```yaml
spring:
  application:
    name: wifi-scan-queue-consumer
  profiles:
    active: development

kafka:
  bootstrap-servers: localhost:9092
  consumer:
    group-id: wifi-scan-consumer
    auto-offset-reset: earliest
  topic:
    name: wifi-scan-data
  ssl:
    enabled: false
```

### Environment-Specific Configuration

#### Development Profile
- Non-SSL Kafka connection
- Debug logging enabled
- Local Kafka broker (localhost:9092)

#### Production Profile
- SSL/TLS encryption enabled
- Optimized logging configuration
- Production Kafka cluster endpoints

#### Test Profile
- In-memory test configurations
- Mock services for unit testing
- Test-specific Kafka settings

### SSL Configuration

For production environments with SSL:

```yaml
kafka:
  ssl:
    enabled: true
    keystore:
      location: ${KAFKA_SSL_KEYSTORE_LOCATION:scripts/kafka/secrets/kafka.keystore.p12}
      password: ${KAFKA_KEYSTORE_PASSWORD:kafka123}
      type: PKCS12
    truststore:
      location: ${KAFKA_SSL_TRUSTSTORE_LOCATION:scripts/kafka/secrets/kafka.truststore.p12}
      password: ${KAFKA_TRUSTSTORE_PASSWORD:kafka123}
      type: PKCS12
```

## 🏥 Health Monitoring & Kubernetes Integration

### Health Check Architecture

The application implements a comprehensive health monitoring system with custom health indicators designed for production Kubernetes deployments. The system distinguishes between **infrastructure readiness** (can the service start?) and **operational liveness** (is the service working correctly?).

### Health Endpoints

All health endpoints are available at the following URLs:

| Endpoint | Purpose | Kubernetes Integration |
|----------|---------|----------------------|
| `http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/` | Overall application health | General monitoring |
| `http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness` | Readiness probe endpoint | K8s readiness probe |
| `http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/liveness` | Liveness probe endpoint | K8s liveness probe |
| `http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka` | Detailed operational metrics | Monitoring/alerting |

### Kubernetes Readiness Probe 🟢

**Purpose**: Determines if the application is ready to receive traffic and process messages.

**Kubernetes Configuration**:
```yaml
readinessProbe:
  httpGet:
    path: /frisco-location-wifi-scan-vmb-consumer/health/readiness
    port: 8080
  initialDelaySeconds: 30    # Allow time for Kafka connection establishment
  periodSeconds: 10          # Check every 10 seconds
  timeoutSeconds: 5          # 5-second timeout per check
  failureThreshold: 3        # 3 consecutive failures = remove from service
  successThreshold: 1        # 1 success = ready to receive traffic
```

**Health Indicators Included**:
- **`kafkaConsumerGroup`**: Consumer group registration and cluster connectivity
- **`kafkaTopicAccessibility`**: Access to configured Kafka topics
- **`sslCertificate`**: SSL/TLS certificate health and expiration monitoring
- **`messageProcessingReadiness`**: Service readiness for message processing

**Configuration**:
```yaml
health:
  indicator:
    timeout-seconds: 5
    consumption-timeout-minutes: 30    # Increased for idle tolerance
    certificate-expiration-warning-days: 30
    retry-attempts: 3
    enable-caching: true
    cache-ttl-seconds: 30

management:
  health:
    message-consumption:
      message-timeout-threshold: 300000  # 5 minutes - fails liveness if no messages received
      consumption-rate-threshold: 0.1
```

### Kubernetes Liveness Probe 🔴

**Purpose**: Determines if the application is still running and healthy (restart if unhealthy).

**Kubernetes Configuration**:
```yaml
livenessProbe:
  httpGet:
    path: /frisco-location-wifi-scan-vmb-consumer/health/liveness
    port: 8080
  initialDelaySeconds: 60    # Allow for application startup
  periodSeconds: 30          # Check every 30 seconds
  timeoutSeconds: 10         # 10-second timeout per check
  failureThreshold: 3        # 3 consecutive failures = restart pod
  successThreshold: 1        # 1 success = healthy
```

**Health Indicators Included**:
- **`messageConsumptionActivity`**: Consumer polling and message processing health
- **`jvmMemory`**: JVM memory usage monitoring

**Critical Fix**: The liveness probe now correctly handles **idle periods** where no messages are available for consumption. The service remains healthy during idle periods and immediately processes messages when they become available.

### SSL Certificate Monitoring 🔐

The application includes comprehensive SSL certificate monitoring with **proactive alerting timeline**:

#### Certificate Expiry Warning Timeline

| Days Before Expiry | Alert Level | Action Required | Kubernetes Behavior |
|-------------------|-------------|-----------------|-------------------|
| **30+ days** | 🟢 **HEALTHY** | No action needed | Pod remains in service |
| **30 days** | 🟡 **WARNING** | Plan certificate renewal | Pod remains in service |
| **15 days** | 🟠 **CRITICAL** | Execute certificate renewal | Pod remains in service |
| **7 days** | 🔴 **URGENT** | Emergency renewal procedures | Pod remains in service |
| **0 days (expired)** | ❌ **FAILED** | Manual certificate renewal | **Pod removed from service** |

#### SSL Certificate Health Response

**Healthy Certificate (>30 days)**:
```json
{
  "status": "UP",
  "details": {
    "sslEnabled": true,
    "sslConnectionHealthy": true,
    "keystoreExpired": false,
    "truststoreExpired": false,
    "keystoreExpiringSoon": false,
    "truststoreExpiringSoon": false,
    "keystoreDaysUntilExpiry": 364,
    "truststoreDaysUntilExpiry": 364,
    "minimumDaysUntilExpiry": 364,
    "checkTimestamp": 1674567890123
  }
}
```

**Certificate Expiring Soon (7-30 days)**:
```json
{
  "status": "UP",
  "details": {
    "sslEnabled": true,
    "sslConnectionHealthy": true,
    "keystoreExpiringSoon": true,
    "truststoreExpiringSoon": false,
    "keystoreDaysUntilExpiry": 15,
    "certificateWarning": "Certificate expires in 15 days - renewal recommended",
    "alertLevel": "CRITICAL"
  }
}
```

#### SSL Configuration for Certificate Monitoring

```yaml
kafka:
  ssl:
    enabled: true
    keystore:
      location: ${KAFKA_SSL_KEYSTORE_LOCATION:scripts/kafka/secrets/kafka.keystore.p12}
      password: ${KAFKA_KEYSTORE_PASSWORD:kafka123}
      type: PKCS12
    truststore:
      location: ${KAFKA_SSL_TRUSTSTORE_LOCATION:scripts/kafka/secrets/kafka.truststore.p12}
      password: ${KAFKA_TRUSTSTORE_PASSWORD:kafka123}
      type: PKCS12

health:
  indicator:
    certificate-expiration-warning-days: 30
    certificate-expiration-critical-days: 15
    certificate-expiration-urgent-days: 7
```

### Custom Health Indicators Detail

#### 1. KafkaConsumerGroupHealthIndicator
**Purpose**: Monitors consumer group registration and cluster connectivity
```json
{
  "kafkaConsumerGroup": {
    "status": "UP",
    "details": {
      "consumerConnected": true,
      "consumerGroupActive": true,
      "clusterNodeCount": 3,
      "checkTimestamp": 1674567890123
    }
  }
}
```

#### 2. TopicAccessibilityHealthIndicator
**Purpose**: Verifies access to configured Kafka topics
```json
{
  "kafkaTopicAccessibility": {
    "status": "UP",
    "details": {
      "topicsAccessible": true,
      "topicName": "wifi-scan-data",
      "checkTimestamp": 1674567890123
    }
  }
}
```

#### 3. MessageConsumptionActivityHealthIndicator
**Purpose**: Monitors consumer activity and message reception health

**🔧 CRITICAL SEMANTIC FIX**: This health check now accurately reports what it measures:
- ✅ **What it measures**: Time since last **message received** from Kafka
- ❌ **What it used to claim**: Time since last "poll" attempt
- ✅ **Accurate behavior**: Consumer can be actively polling every few seconds but health check correctly indicates idle periods

```json
{
  "messageConsumptionActivity": {
    "status": "UP",
    "details": {
      "reason": "Consumer is healthy - actively polling for messages",
      "consumerConnected": true,
      "consumerGroupActive": true,
      "consumptionRate": 2.5,
      "totalMessagesConsumed": 150,
      "totalMessagesProcessed": 148,
      "successRate": 98.67,
      "timeSinceLastMessageReceivedMs": 120000,
      "messageTimeoutThresholdMs": 300000,
      "checkTimestamp": 1674567890123
    }
  }
}
```

**Configuration Details**:
```yaml
management:
  health:
    message-consumption:
      message-timeout-threshold: 300000  # Time since last message received (not poll attempts)
      consumption-rate-threshold: 0.1    # Messages/minute - warns if processing rate too low (when active)
```

#### 4. JvmMemoryHealthIndicator
**Purpose**: Monitors JVM memory usage
```json
{
  "jvmMemory": {
    "status": "UP",
    "details": {
      "memoryHealthy": true,
      "memoryUsagePercentage": 65.2,
      "usedMemoryMB": 512,
      "totalMemoryMB": 1024,
      "maxMemoryMB": 2048,
      "threshold": 90,
      "checkTimestamp": 1674567890123
    }
  }
}
```

### Health Check Testing

Test health endpoints using the provided scripts:

```bash
# Test overall health
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/ | jq '.'

# Test readiness probe
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness | jq '.'

# Test liveness probe
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/liveness | jq '.'

# Comprehensive validation (tests idle tolerance + auto-recovery)
./scripts/run-test-suite.sh

# Test health after idle period
./scripts/validate-service-health.sh --count 5 --interval 1 --timeout 60
```

#### 🔧 Intelligent Message Timeout Recovery

The test suite includes **automatic timeout recovery** for scenarios where the service becomes unhealthy due to message consumption timeout:

**Problem**: Service idle >5 minutes → Liveness probe DOWN → Tests fail before sending messages

**Solution**: Enhanced `run-test-suite.sh` with intelligent pre-check:

```bash
# Before running tests, script automatically:
# 1. Checks liveness health status
# 2. Detects specific "message timeout" failures  
# 3. Sends recovery messages to wake consumer
# 4. Waits for health recovery (up to 60s)
# 5. Proceeds with normal test validation

./scripts/run-test-suite.sh   # Now handles idle timeout automatically
```

**Recovery Process**:
```
[INFO] Checking service health before starting tests...
[WARNING] Liveness probe is DOWN - checking if it's due to message consumption timeout...
[WARNING] Detected message consumption timeout - sending recovery messages...
[INFO] Sending 3 recovery messages to wake up the consumer...
[INFO] Recovery messages sent successfully
[INFO] ✅ Service health recovered successfully!
```

### Operational Metrics

Detailed operational metrics available at:
```
GET http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka
```

**Sample Response**:
```json
{
  "totalMessagesConsumed": 1250,
  "totalMessagesProcessed": 1248,
  "totalMessagesFailed": 2,
  "successRate": 99.84,
  "errorRate": 0.16,
  "averageProcessingTimeMs": 15.7,
  "consumptionRate": 2.8,
  "isPollingActive": true,
  "isConsumerConnected": true,
  "consumerGroupActive": true,
  "isConsumptionHealthy": true,
  "memoryUsagePercentage": 67.3,
  "timestamp": 1674567890123,
  "metricsVersion": "2.0.0"
}
```

### Configuration Properties

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env,kafka
  endpoint:
    health:
      show-details: always
      group:
        readiness:
          include: kafkaConsumerGroup,kafkaTopicAccessibility,sslCertificate,messageProcessingReadiness
        liveness:
          include: messageConsumptionActivity,jvmMemory
  health:
    kafka:
      enabled: true
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true

health:
  indicator:
    timeout-seconds: 5
    memory-threshold-percentage: 90
    consumption-timeout-minutes: 30    # Increased for idle tolerance
    minimum-consumption-rate: 0.0
    certificate-expiration-warning-days: 30
    certificate-expiration-critical-days: 15
    certificate-expiration-urgent-days: 7
    retry-attempts: 3
    enable-caching: true
    cache-ttl-seconds: 30
```

## 💻 Development

### Project Structure

```
wifi-scan-queue-consumer/
├── src/
│   ├── main/
│   │   ├── java/com/wifi/scan/consume/
│   │   │   ├── config/              # Spring configuration
│   │   │   ├── controller/          # REST endpoints
│   │   │   ├── health/              # Health indicators
│   │   │   ├── listener/            # Kafka listeners
│   │   │   ├── metrics/             # Metrics collection
│   │   │   └── service/             # Business services
│   │   └── resources/
│   │       └── application.yml      # Configuration
│   └── test/                        # Test classes
├── scripts/                         # Development scripts
│   └── kafka/
│       └── secrets/                 # SSL certificates (local dev)
├── documents/                       # Documentation
├── target/                          # Build output
└── pom.xml                         # Maven configuration
```

### Development Workflow

1. **Setup Environment**
   ```bash
   ./scripts/setup-dev-environment.sh
   ```

2. **Run Tests**
   ```bash
   mvn test                    # Unit tests
   mvn verify                  # Integration tests
   ./scripts/run-test-suite.sh # Full validation
   ```

3. **Code Quality**
   ```bash
   mvn clean compile          # Check compilation
   mvn spotbugs:check         # Static analysis
   ```

## 🧪 Testing

### Test Categories

#### Unit Tests
- **Location**: `src/test/java`
- **Framework**: JUnit 5, Mockito
- **Coverage**: Individual components and business logic

#### Integration Tests
- **Location**: `src/test/java/*IntegrationTest.java`
- **Framework**: Spring Boot Test, TestContainers
- **Coverage**: End-to-end scenarios with real Kafka

#### Validation Tests
- **Location**: `scripts/run-test-suite.sh`
- **Framework**: Shell scripts with curl/jq
- **Coverage**: Live service validation

### Running Tests

```bash
# Unit tests only
mvn test

# All tests (unit + integration)
mvn verify

# Live service validation
./scripts/run-test-suite.sh

# Specific test scenarios
./scripts/validate-service-health.sh --count 10 --verbose
```

### Test Configuration

Tests use the `test` profile with:
- Embedded Kafka for integration tests
- Mock services for unit tests
- Temporary test data and cleanup

## 🚀 Deployment

### Docker Deployment

```bash
# Build Docker image
docker build -t wifi-scan-consumer:latest .

# Run container
docker run -d \
  --name wifi-consumer \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=production \
  wifi-scan-consumer:latest
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wifi-scan-consumer
spec:
  replicas: 3
  selector:
    matchLabels:
      app: wifi-scan-consumer
  template:
    metadata:
      labels:
        app: wifi-scan-consumer
    spec:
      containers:
      - name: consumer
        image: wifi-scan-consumer:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `development` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker endpoints | `localhost:9092` |
| `KAFKA_CONSUMER_GROUP_ID` | Consumer group ID | `wifi-scan-consumer` |
| `KAFKA_SSL_ENABLED` | Enable SSL/TLS | `false` |
| `KAFKA_KEYSTORE_PASSWORD` | Keystore password | - |
| `KAFKA_TRUSTSTORE_PASSWORD` | Truststore password | - |

## 📊 Monitoring

### Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/frisco-location-wifi-scan-vmb-consumer/health` | Overall application health |
| `/frisco-location-wifi-scan-vmb-consumer/health/readiness` | Readiness probe |
| `/frisco-location-wifi-scan-vmb-consumer/health/liveness` | Liveness probe |
| `/frisco-location-wifi-scan-vmb-consumer/info` | Application information |
| `/frisco-location-wifi-scan-vmb-consumer/metrics` | Application metrics |

### Custom Health Indicators

- **Kafka Consumer Health**: Monitors Kafka consumer group status
- **Message Consumption Activity**: Tracks recent message processing
- **Memory Health**: Monitors JVM memory usage
- **SSL Certificate Health**: Validates SSL certificate status
- **Topic Accessibility**: Verifies Kafka topic access

### Metrics

Available at `/frisco-location-wifi-scan-vmb-consumer/metrics/kafka`:

```json
{
  "totalMessagesConsumed": 1250,
  "totalMessagesProcessed": 1250,
  "totalMessagesFailed": 0,
  "successRate": 100.0,
  "errorRate": 0.0,
  "averageProcessingTimeMs": 15.2,
  "memoryUsagePercentage": 35.5,
  "isConsumerConnected": true,
  "isPollingActive": true
}
```

## 📜 Scripts

### Development Scripts

| Script | Description |
|--------|-------------|
| `setup-dev-environment.sh` | Setup development environment |
| `start-local-kafka.sh` | Start local Kafka with Docker |
| `stop-local-kafka.sh` | Stop local Kafka containers |
| `create-test-topic.sh` | Create required Kafka topics |

### Testing Scripts

| Script | Description |
|--------|-------------|
| `run-test-suite.sh` | Comprehensive test suite |
| `validate-service-health.sh` | Service health validation |
| `send-test-message.sh` | Send test messages to Kafka |
| `send-wifi-scan-messages.sh` | Send WiFi scan test data |

### SSL Scripts

| Script | Description |
|--------|-------------|
| `generate-ssl-certs.sh` | Generate SSL certificates |
| `test-ssl-connection.sh` | Test SSL connectivity |

### Usage Examples

```bash
# Run comprehensive validation
./scripts/run-test-suite.sh

# Test with specific parameters
./scripts/validate-service-health.sh \
  --count 20 \
  --interval 0.5 \
  --timeout 120 \
  --verbose

# Send custom test messages
./scripts/send-wifi-scan-messages.sh \
  --count 100 \
  --interval 1 \
  --topic wifi-scan-data
```

## 🔧 Troubleshooting

### Common Issues

#### Service Won't Start
```bash
# Check Java version
java -version  # Should be 21+

# Check port availability
netstat -an | grep 8080

# Check Kafka connectivity
./scripts/test-ssl-connection.sh
```

#### Kafka Connection Issues
```bash
# Verify Kafka is running
docker ps | grep kafka

# Check topic exists
./scripts/create-test-topic.sh

# Test basic connectivity
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/
```

#### SSL/TLS Problems
```bash
# Regenerate certificates
./scripts/generate-ssl-certs.sh

# Test SSL connection
./scripts/test-ssl-connection.sh

# Check certificate validity
openssl x509 -in secrets/kafka.cert.pem -text -noout
```

#### Health Check Issues
```bash
# Test all health endpoints
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/ | jq '.'
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness | jq '.'
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/liveness | jq '.'

# Test idle tolerance (critical fix)
./scripts/run-test-suite.sh
sleep 600  # Wait 10 minutes
./scripts/run-test-suite.sh  # Should still pass
```

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.wifi.scan.consume: DEBUG
    org.springframework.kafka: DEBUG
    org.apache.kafka: INFO
```

### Performance Tuning

For high-throughput scenarios:

```yaml
kafka:
  consumer:
    max-poll-records: 500
    fetch-min-size: 1024
    fetch-max-wait: 500
  listener:
    concurrency: 5
    poll-timeout: 3000
```

## 🤝 Contributing

### Development Guidelines

1. **Code Style**: Follow Spring Boot and Java best practices
2. **Testing**: Maintain >80% test coverage
3. **Documentation**: Update README for significant changes
4. **Commits**: Use conventional commit messages

### Pull Request Process

1. Create feature branch from `main`
2. Implement changes with tests
3. Run full test suite: `./scripts/run-test-suite.sh`
4. Update documentation as needed
5. Submit pull request with description

### Local Development Setup

```bash
# Clone repository
git clone <repository-url>
cd wifi-scan-queue-consumer

# Setup development environment
./scripts/setup-dev-environment.sh

# Run tests to verify setup
mvn clean verify
./scripts/run-test-suite.sh
```

## 🔥 Firehose Integration

The WiFi Scan Queue Consumer includes comprehensive AWS Kinesis Data Firehose integration for streaming data to S3. This enables real-time data processing and storage in a scalable, managed environment.

### Features

- **LocalStack Support**: Full local development environment with AWS services
- **Firehose Delivery Stream**: Configured to buffer data and deliver to S3
- **S3 Integration**: Data is stored in partitioned folders by date/time
- **IAM Security**: Proper role-based access control
- **Health Monitoring**: Service health checks for Firehose connectivity
- **Comprehensive Testing**: End-to-end validation scripts

### Quick Setup

```bash
# Setup AWS infrastructure only
./scripts/setup-aws-infrastructure.sh

# Test Firehose integration
./scripts/validate-firehose-integration.sh

# Run comprehensive tests including Firehose
./scripts/run-test-suite.sh
```

### Configuration

The Firehose integration is configured in `application.yml`:

```yaml
aws:
  region: us-east-1
  endpoint-url: http://localhost:4566  # LocalStack endpoint
  firehose:
    delivery-stream-name: MVS-stream
    buffer-time: 300         # 5 minutes
    batch-processing:
      min-batch-size: 10
      max-batch-size: 150
```

### Data Flow

```
Kafka Topic → Spring Boot App → Firehose → S3 Bucket
     ↓              ↓              ↓         ↓
wifi-scan-data → Consumer → MVS-stream → wifi-scan-data-bucket/
                                              └── wifi-scan-data/
                                                  └── year=2024/
                                                      └── month=01/
                                                          └── day=15/
                                                              └── hour=14/
                                                                  └── data-files
```

### Health Monitoring

The service includes health indicators for Firehose connectivity:

```bash
# Check Firehose connectivity
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness | jq '.components.firehoseConnectivity'

# Check overall service health
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health
```

### Testing

```bash
# Basic Firehose test
./scripts/validate-firehose-integration.sh --count 10 --interval 1

# Verbose testing with detailed output
./scripts/validate-firehose-integration.sh --count 20 --interval 0.5 --verbose

# Skip S3 validation (useful for debugging)
./scripts/validate-firehose-integration.sh --no-s3-check
```

### Cleanup

```bash
# Clean up AWS infrastructure
./scripts/cleanup-aws-infrastructure.sh
```

For detailed documentation, see [FIREHOSE_INTEGRATION_README.md](scripts/FIREHOSE_INTEGRATION_README.md).

---

## 📞 Support

For support and questions:
- 📧 **Email**: [support@example.com]
- 📋 **Issues**: [GitHub Issues](https://github.com/your-org/wifi-scan-queue-consumer/issues)
- 📖 **Documentation**: [Wiki](https://github.com/your-org/wifi-scan-queue-consumer/wiki)

---

**Version**: 1.0.0-SNAPSHOT  
**Last Updated**: June 2025  
**License**: [MIT License](LICENSE)