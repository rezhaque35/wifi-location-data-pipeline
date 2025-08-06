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

### Required Software
- **Docker Desktop for Mac** (v4.0 or later)
  - Install from: https://www.docker.com/products/docker-desktop/
  - Ensure Docker Desktop is running
  - Minimum system requirements:
    - macOS 11 (Big Sur) or later
    - 4GB RAM minimum (8GB recommended)
    - 20GB free disk space

- **Java 21** (OpenJDK or Oracle)
  - Install via Homebrew: `brew install openjdk@21`
  - Set JAVA_HOME:
    ```bash
    echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
    echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.zshrc
    source ~/.zshrc
    ```

- **Maven 3.9+**
  - Install via Homebrew: `brew install maven`
  - Verify installation: `mvn --version`

- **Additional Tools**
  - **keytool** (included with Java)
  - **openssl** (pre-installed on Mac)
  - **jq** (for WiFi message generation)
    - Install via Homebrew: `brew install jq`
  - **bc** (for decimal calculations - usually pre-installed on Mac)
- **curl** (for health checks)
- **AWS CLI** (for AWS services - will be installed automatically)

### Verify Prerequisites
```bash
# Check versions
docker --version          # Should show v4.0+
docker-compose --version  # Should show v2.0+
java --version            # Should show Java 21
mvn --version             # Should show Maven 3.9+
keytool -help             # Should show keytool options
openssl version           # Should show OpenSSL version
jq --version              # Should show jq version (for WiFi message generation)
bc --version              # Should show bc version (for calculations)
```

## 🚀 Quick Start

### Option 1: Automated Setup (Recommended)
```bash
# Navigate to scripts directory
cd wifi-scan-ingestion/wifi-scan-queue-consumer/scripts

# Run the complete setup script (handles everything automatically)
./setup.sh
```

This script will:
- ✅ Check all prerequisites
- ✅ Set up the Kafka environment with SSL
- ✅ Start Kafka cluster
- ✅ Test SSL connectivity
- ✅ Create test topic
- ✅ Send and consume test messages
- ✅ Set up AWS infrastructure (LocalStack)
- ✅ Provide clear next steps

### Option 2: Manual Setup
```bash
# Navigate to scripts directory
cd wifi-scan-ingestion/wifi-scan-queue-consumer/scripts

# Make scripts executable
chmod +x *.sh
chmod +x test/*.sh

# Set up Kafka environment
./setup-local-kafka.sh

# Start Kafka cluster
./start-local-kafka.sh

# Test the setup
./test/test-ssl-connection.sh
./test/create-test-topic.sh
./test/send-test-message.sh "Hello SSL Kafka!"
./test/consume-test-messages.sh
```

### Option 3: Test with WiFi Scan Data
```bash
# After basic setup, test with realistic WiFi data
./test/send-wifi-scan-messages.sh --count 5 --interval 1
./test/consume-test-messages.sh wifi-scan-data
```

### 4. Run Integration Tests

```bash
# Run comprehensive test suite
./test/run-test-suite.sh

# Or test Firehose integration specifically
./test/validate-firehose-integration.sh
```

### 5. Start the Application

```bash
# Development mode
mvn spring-boot:run

# Or run the JAR
java -jar target/wifi-scan-queue-consumer-1.0.0-SNAPSHOT.jar
```

### 6. Verify Installation

```bash
# Check health
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health

# Run validation tests
./test/run-test-suite.sh
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

### SSL Configuration Details
- **Keystore Password**: `kafka123` (for local development only)
- **Truststore Password**: `kafka123` (for local development only)
- **SSL Port**: `9093`
- **Plaintext Port**: `9092`
- **Certificate Validity**: 365 days
- **Certificate Location**: 
  - Development: `src/main/resources/secrets/`
  - Docker: `kafka/secrets/`

### Kafka Configuration
- **Bootstrap Servers**: 
  - SSL: `localhost:9093`
  - Plaintext: `localhost:9092`
- **Zookeeper Port**: `2181`
- **Default Topics**: 
  - Simple messages: `test-topic`
  - WiFi scan data: `wifi-scan-data`
- **Default Consumer Group**: `local-test-group`
- **SSL Protocol**: TLSv1.2
- **Security Protocol**: SSL

### Topic Configuration
- **Partitions**: 3 (default for new topics)
- **Replication Factor**: 1 (single broker setup)
- **Retention**: 7 days (Kafka default)

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
- **Location**: `scripts/test/run-test-suite.sh`
- **Framework**: Shell scripts with curl/jq
- **Coverage**: Live service validation

### Running Tests

```bash
# Unit tests only
mvn test

# All tests (unit + integration)
mvn verify

# Live service validation
./test/run-test-suite.sh

# Specific test scenarios
./test/validate-service-health.sh --count 10 --verbose
```

### Test Configuration

Tests use the `test` profile with:
- Embedded Kafka for integration tests
- Mock services for unit tests
- Temporary test data and cleanup

## 📜 Scripts

### Directory Structure

```
wifi-scan-queue-consumer/
├── scripts/
│   ├── setup.sh                           # ⭐ Complete automated setup
│   ├── setup-local-kafka.sh              # Kafka environment setup
│   ├── setup-aws-infrastructure.sh       # AWS infrastructure setup
│   ├── start-local-kafka.sh              # Start Kafka cluster
│   ├── stop-local-kafka.sh               # Stop and cleanup
│   ├── cleanup.sh                        # Complete infrastructure cleanup
│   ├── generate-ssl-certs.sh             # SSL certificate generation
│   ├── docker-compose.yml                # Docker configuration
│   ├── test/                             # Test scripts directory
│   │   ├── run-test-suite.sh             # Comprehensive test suite
│   │   ├── test-ssl-connection.sh        # SSL connectivity test
│   │   ├── create-test-topic.sh          # Topic creation
│   │   ├── send-test-message.sh          # Simple message sender
│   │   ├── send-wifi-scan-messages.sh    # 📡 WiFi scan data generator
│   │   ├── validate-service-health.sh    # 🔍 End-to-end service validation
│   │   ├── validate-firehose-integration.sh # Firehose integration testing
│   │   ├── validate-wifi-scan-endpoint.sh # WiFi scan endpoint testing
│   │   ├── test-wifi-scan-endpoint.sh    # WiFi scan endpoint testing
│   │   └── consume-test-messages.sh      # Message consumer
│   └── kafka/                            # Generated during setup
│       └── secrets/
│           ├── kafka.keystore.p12        # SSL keystore
│           ├── kafka.truststore.p12      # SSL truststore
│           ├── ca-cert                   # Certificate authority
│           └── ca-key                    # CA private key
├── src/main/resources/secrets/           # Application certificates
│   ├── kafka.keystore.p12
│   └── kafka.truststore.p12
├── pom.xml                              # Maven configuration
└── src/                                 # Spring Boot application source
```

### Core Setup Scripts

| Script | Purpose | Usage | Parameters | Duration |
|--------|---------|--------|------------|----------|
| `setup.sh` | **Complete automated setup** | `./setup.sh` | None | ~3-5 min |
| `setup-local-kafka.sh` | Kafka environment setup only | `./setup-local-kafka.sh` | None | ~2-3 min |
| `setup-aws-infrastructure.sh` | AWS infrastructure setup | `./setup-aws-infrastructure.sh` | None | ~2-3 min |
| `generate-ssl-certs.sh` | Generate SSL certificates | `./generate-ssl-certs.sh` | None | ~30 sec |
| `start-local-kafka.sh` | Start Kafka cluster | `./start-local-kafka.sh` | None | ~30 sec |
| `stop-local-kafka.sh` | Stop and cleanup | `./stop-local-kafka.sh` | None | ~15 sec |
| `cleanup.sh` | Complete infrastructure cleanup | `./cleanup.sh` | None | ~1-2 min |

### Testing Scripts

| Script | Purpose | Usage | Parameters | Use Case |
|--------|---------|--------|------------|----------|
| `test/test-ssl-connection.sh` | Validate SSL connectivity | `./test/test-ssl-connection.sh` | None | SSL verification |
| `test/create-test-topic.sh` | Create test topic | `./test/create-test-topic.sh [topic-name]` | Optional: topic name | Topic management |
| `test/send-test-message.sh` | Send simple text messages | `./test/send-test-message.sh "message" [topic-name]` | Required: message, Optional: topic | Basic testing |
| `test/send-wifi-scan-messages.sh` | **Send realistic WiFi scan data** | `./test/send-wifi-scan-messages.sh [options]` | --count, --interval, --topic, --ssl | **Positioning service testing** |
| `test/validate-service-health.sh` | **End-to-end service validation** | `./test/validate-service-health.sh [options]` | --count, --interval, --timeout, --verbose | **Complete service validation** |
| `test/validate-firehose-integration.sh` | **Firehose integration testing** | `./test/validate-firehose-integration.sh [options]` | --count, --interval, --timeout, --verbose | **AWS integration testing** |
| `test/validate-wifi-scan-endpoint.sh` | **WiFi scan endpoint testing** | `./test/validate-wifi-scan-endpoint.sh [options]` | --count, --interval, --timeout, --verbose | **Endpoint validation** |
| `test/consume-test-messages.sh` | Consume messages | `./test/consume-test-messages.sh [topic-name]` | Optional: topic name | Message verification |

### WiFi Scan Message Generator

The `test/send-wifi-scan-messages.sh` script is a specialized tool that generates realistic WiFi scan data messages for testing the positioning service. This script creates messages that exactly match the `WifiPositioningRequest` format used by the WiFi positioning service.

#### 🎯 Key Features

- **📡 Realistic Data Generation**: Creates valid MAC addresses, signal strengths (-30 to -100 dBm), and frequencies
- **📶 Multiple Frequency Bands**: Supports both 2.4GHz (2412-2472 MHz) and 5GHz (5180-5825 MHz) bands
- **🔢 Variable Scan Results**: Each message contains 1-5 scan results per positioning request
- **⚙️ Highly Configurable**: Count, interval, topic, SSL, and message structure options
- **📋 JSON Format**: Generates properly formatted JSON messages for the positioning service
- **🔄 Continuous Streaming**: Can generate continuous message streams for load testing

#### 📖 Usage Examples

```bash
# Basic usage - Send 10 messages every 2 seconds (default)
./test/send-wifi-scan-messages.sh

# Quick test - Send 5 messages every 1 second
./test/send-wifi-scan-messages.sh --count 5 --interval 1

# Load testing - Send 100 messages rapidly
./test/send-wifi-scan-messages.sh --count 100 --interval 0.1

# SSL testing with custom topic
./test/send-wifi-scan-messages.sh --count 20 --topic wifi-positioning-test --ssl

# High-frequency continuous stream for stress testing
./test/send-wifi-scan-messages.sh --count 1000 --interval 0.05

# Production-like testing with realistic intervals
./test/send-wifi-scan-messages.sh --count 50 --interval 2 --topic wifi-scan-data
```

#### 📋 Command Line Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `--count N` | Number of messages to send | 10 | `--count 50` |
| `--interval SECONDS` | Interval between messages | 2 | `--interval 1.5` |
| `--topic TOPIC` | Target Kafka topic | wifi-scan-data | `--topic my-topic` |
| `--ssl` | Use SSL connection | false | `--ssl` |
| `--help` | Show help message | - | `--help` |

#### 📄 Sample Generated Message

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
    },
    {
      "macAddress": "11:22:33:44:55:66",
      "signalStrength": -72.1,
      "frequency": 5180,
      "ssid": "SecureAP",
      "linkSpeed": 1200,
      "channelWidth": 160
    },
    {
      "macAddress": "99:88:77:66:55:44",
      "signalStrength": -81.3,
      "frequency": 5240,
      "ssid": "CoffeeShop",
      "linkSpeed": 433,
      "channelWidth": 40
    }
  ],
  "client": "wifi-scan-generator",
  "requestId": "req-1735820400-12345",
  "application": "wifi-scan-test-suite",
  "calculationDetail": true
}
```

### Service Health Validation

The `test/validate-service-health.sh` script provides comprehensive end-to-end validation of your Spring Boot application. It sends WiFi scan messages and continuously monitors the service health indicators to ensure messages are being consumed correctly.

#### 🎯 Key Features

- **📨 Automated Message Generation**: Uses `send-wifi-scan-messages.sh` to generate realistic test data
- **🔍 Real-time Health Monitoring**: Monitors overall health, readiness, and liveness endpoints
- **📊 Message Count Tracking**: Tracks message consumption through service metrics
- **⏱️ Configurable Timeouts**: Customizable validation timeouts and intervals
- **📋 Detailed Reporting**: Comprehensive validation reports with pass/fail status
- **🚨 Background Process Management**: Handles cleanup of background message senders

#### 📖 Usage Examples

```bash
# Basic validation - Send 10 messages and monitor for 60 seconds
./test/validate-service-health.sh

# Quick validation - 5 messages with 30-second timeout
./test/validate-service-health.sh --count 5 --timeout 30

# Detailed validation with verbose output
./test/validate-service-health.sh --count 20 --interval 1 --verbose

# SSL testing with custom service URL
./test/validate-service-health.sh --ssl --service-url http://localhost:8081

# High-frequency testing
./test/validate-service-health.sh --count 50 --interval 0.5 --health-interval 2

# Production-like validation
./test/validate-service-health.sh --count 100 --interval 2 --timeout 300
```

#### 📋 Command Line Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `--count N` | Number of messages to send | 10 | `--count 20` |
| `--interval SECONDS` | Interval between messages | 2 | `--interval 1` |
| `--topic TOPIC` | Target Kafka topic | wifi-scan-data | `--topic my-topic` |
| `--service-url URL` | Service base URL | http://localhost:8080 | `--service-url http://localhost:8081` |
| `--timeout SECONDS` | Validation timeout | 60 | `--timeout 120` |
| `--health-interval SECONDS` | Health check interval | 5 | `--health-interval 3` |
| `--ssl` | Use SSL for Kafka | false | `--ssl` |
| `--verbose` | Detailed output | false | `--verbose` |

### Development Workflow

#### 🌅 Daily Development Startup
```bash
# Navigate to scripts directory
cd wifi-scan-ingestion/wifi-scan-queue-consumer/scripts

# 1. Start Kafka environment
./start-local-kafka.sh

# 2. Verify connectivity
./test/test-ssl-connection.sh

# 3. Create topic for your work (if needed)
./test/create-test-topic.sh my-dev-topic
```

#### 🧪 Testing Message Flow
```bash
# Simple text message testing
./test/send-test-message.sh "Debug message" my-dev-topic
./test/consume-test-messages.sh my-dev-topic

# WiFi scan data testing (realistic data)
./test/send-wifi-scan-messages.sh --count 5 --topic my-dev-topic
./test/consume-test-messages.sh my-dev-topic

# End-to-end service validation (recommended)
./test/validate-service-health.sh --count 10 --verbose

# SSL testing
./test/send-wifi-scan-messages.sh --count 3 --ssl
./test/consume-test-messages.sh wifi-scan-data --ssl
```

#### 🔧 Development with Spring Boot Application
```bash
# 1. Start Kafka
./start-local-kafka.sh

# 2. Generate test data
./test/send-wifi-scan-messages.sh --count 10 --interval 2

# 3. Start your Spring Boot application
cd ../
mvn spring-boot:run

# 4. Monitor application logs for message processing
# 5. Verify health endpoints
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health
```

#### 🔄 Continuous Testing
```bash
# Terminal 1: Start continuous message generation
./test/send-wifi-scan-messages.sh --count 1000 --interval 1

# Terminal 2: Monitor Spring Boot application
cd ../ && mvn spring-boot:run

# Terminal 3: Monitor Kafka logs
docker logs -f kafka
```

#### 🌙 Daily Shutdown
```bash
# Complete cleanup
./cleanup.sh
```

## 🏥 Health Monitoring & Kubernetes Integration ✅

✅ **PRODUCTION READY** - Comprehensive health monitoring system fully implemented and tested.

**🎯 CRITICAL FIX IMPLEMENTED**: Resolved the **10-minute idle timeout issue** where the service incorrectly became unhealthy after periods of no message activity. The service now correctly remains healthy during idle periods and immediately processes messages when they become available.

### 🔗 Health Endpoints

All health endpoints are accessible at the following **verified URLs**:

| Endpoint | Purpose | Kubernetes Integration |
|----------|---------|----------------------|
| `http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/` | Overall application health | General monitoring |
| `http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness` | Readiness probe endpoint | K8s readiness probe |
| `http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/liveness` | Liveness probe endpoint | K8s liveness probe |
| `http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka` | Detailed operational metrics | Monitoring/alerting |

**Testing URLs**:
```bash
# Test overall health
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/ | jq '.'

# Test readiness probe
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness | jq '.'

# Test liveness probe  
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/liveness | jq '.'

# Test operational metrics
curl -s http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka | jq '.'
```

### 🎯 Health Indicators Implemented

#### **1. KafkaConsumerGroupHealthIndicator** ✅
- **Purpose**: Monitors consumer group registration and cluster connectivity
- **Component Name**: `kafkaConsumerGroup`
- **Checks**: Consumer connection, group status, cluster node count
- **Endpoint**: Readiness probe

#### **2. TopicAccessibilityHealthIndicator** ✅  
- **Purpose**: Verifies access to configured Kafka topics
- **Component Name**: `kafkaTopicAccessibility`
- **Checks**: Topic existence, metadata availability, permissions
- **Endpoint**: Readiness probe

#### **3. MessageConsumptionActivityHealthIndicator** ✅
- **Purpose**: Monitors consumer activity and message reception health
- **Component Name**: `messageConsumptionActivity`
- **🔧 CRITICAL SEMANTIC FIX**: Measures time since last **message received** (not poll attempts)
- **Key Feature**: ✅ **Idle Tolerance** - Remains healthy during periods with no messages
- **Endpoint**: Liveness probe

#### **4. MemoryHealthIndicator** ✅
- **Purpose**: Monitors JVM memory usage with configurable thresholds
- **Component Name**: `jvmMemory`
- **Checks**: Heap memory usage (default threshold: 90%)
- **Endpoint**: Liveness probe

#### **5. SslCertificateHealthIndicator** ✅
- **Purpose**: Validates SSL/TLS certificate health and accessibility
- **Component Name**: `sslCertificate`
- **🔐 SSL Certificate Warning Timeline**:
  
  | Days Before Expiry | Alert Level | Action Required | Kubernetes Behavior |
  |-------------------|-------------|-----------------|-------------------|
  | **30+ days** | 🟢 **HEALTHY** | No action needed | Pod remains in service |
  | **30 days** | 🟡 **WARNING** | Plan certificate renewal | Pod remains in service |
  | **15 days** | 🟠 **CRITICAL** | Execute certificate renewal | Pod remains in service |
  | **7 days** | 🔴 **URGENT** | Emergency renewal procedures | Pod remains in service |
  | **0 days (expired)** | ❌ **FAILED** | Manual certificate renewal | **Pod removed from service** |

#### **6. EnhancedSslCertificateHealthIndicator** ✅
- **Purpose**: Advanced SSL certificate monitoring with CloudWatch integration
- **Component Name**: `enhancedSslCertificate`
- **Features**: Certificate health scoring, Kubernetes event generation, CloudWatch metrics

### 📊 Operational Metrics System

#### **MetricsController Endpoints**:
- `GET /frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka` - Comprehensive metrics JSON
- `GET /frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka/summary` - Human-readable summary
- `GET /frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka/status` - Operational status overview
- `POST /frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka/reset` - Reset metrics for testing

**Sample Metrics Response**:
```json
{
  "totalMessagesConsumed": 92,
  "totalMessagesProcessed": 92,
  "totalMessagesFailed": 0,
  "successRate": 100.0,
  "errorRate": 0.0,
  "averageProcessingTimeMs": 15.02,
  "minProcessingTimeMs": 13,
  "maxProcessingTimeMs": 23,
  "firstMessageTimestamp": "2025-06-13T17:24:30.103807",
  "lastMessageTimestamp": "2025-06-13T18:12:49.613871",
  "lastPollTimestamp": "2025-06-13T18:12:49.613871",
  "isPollingActive": true,
  "isConsumerConnected": true,
  "consumerGroupActive": true,
  "memoryUsagePercentage": 53.29,
  "usedMemoryMB": 72,
  "totalMemoryMB": 136,
  "maxMemoryMB": 12288,
  "consumptionRate": 1.92,
  "isConsumptionHealthy": true,
  "timestamp": 1749852773148,
  "metricsVersion": "2.0.0"
}
```

### 🧪 Test Coverage ✅

**HealthIndicatorIntegrationTest** - Comprehensive test suite:
- ✅ All health indicators properly autowired
- ✅ Main health endpoint returns UP status
- ✅ Readiness/Liveness endpoints contain correct components
- ✅ Memory and SSL certificate health validation
- ✅ Message consumption activity monitoring

**Test Results**: 
```
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS - All health indicators working correctly
```

**Idle Tolerance Testing**:
```bash
# Test 1: Immediate execution - ALL TESTS PASSED ✅
./scripts/test/run-test-suite.sh

# Test 2: After 44+ minutes idle - ALL TESTS PASSED ✅  
./scripts/test/run-test-suite.sh

# Conclusion: 10-minute idle timeout issue RESOLVED ✅
```

### 🚀 Kubernetes Integration

#### **Readiness Probe Configuration** ✅
```yaml
readinessProbe:
  httpGet:
    path: /frisco-location-wifi-scan-vmb-consumer/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

**Readiness Components**: `kafkaConsumerGroup`, `kafkaTopicAccessibility`, `sslCertificate`, `firehoseConnectivity`

#### **Liveness Probe Configuration** ✅
```yaml
livenessProbe:
  httpGet:
    path: /frisco-location-wifi-scan-vmb-consumer/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
  timeoutSeconds: 10
  failureThreshold: 3
```

**Liveness Components**: `messageConsumptionActivity`, `jvmMemory`

### ⚙️ Configuration Properties

```yaml
management:
  endpoints:
    web:
      base-path: /
      exposure:
        include: health,info,metrics,env,kafka
  endpoint:
    health:
      show-details: always
      group:
        readiness:
          include: kafkaConsumerGroup,kafkaTopicAccessibility,sslCertificate,firehoseConnectivity
        liveness:
          include: messageConsumptionActivity,jvmMemory
  health:
    kafka:
      enabled: true
    message-consumption:
      message-timeout-threshold: 5  # 5 minutes
      consumption-rate-threshold: 0.1

# Enhanced Health Indicator Configuration
health:
  indicator:
    timeout-seconds: 5
    memory-threshold-percentage: 90
    consumption-timeout-minutes: 30
    minimum-consumption-rate: 0.0
    retry-attempts: 3
    enable-caching: true
    cache-ttl-seconds: 30
    # SSL Certificate Configuration
    certificate-expiration-warning-days: 30
    certificate-expiration-urgent-days: 15
    certificate-expiration-critical-days: 7
    certificate-validation-timeout-seconds: 10
```

### 🔧 Critical Fixes Summary

#### **Message Consumption Activity Health Indicator Semantic Fix**

**Issue**: Misleading configuration and error messages created operational confusion.

**Before (Misleading)**:
```yaml
poll-timeout-threshold: 300000  # Suggested monitoring "poll attempts"
```
```
"reason": "Consumer hasn't polled in 1023566 ms"
```

**After (Accurate)**:
```yaml
message-timeout-threshold: 300000  # Time since last message received
```
```
"reason": "Consumer hasn't received messages in 1023566 ms - may indicate inactive consumer or no available messages"
```

**Benefits**:
- 🎯 **Accurate Monitoring**: Health checks represent what they actually measure
- 🚀 **Operational Clarity**: No confusion during normal idle periods
- 📊 **Better Alerting**: Teams understand actual service behavior
- 🔧 **Troubleshooting**: Clear distinction between connectivity vs idle state

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
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-cluster:9092"
        livenessProbe:
          httpGet:
            path: /frisco-location-wifi-scan-vmb-consumer/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /frisco-location-wifi-scan-vmb-consumer/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

## 🔧 Troubleshooting

### Common Issues and Solutions

#### 1. 🐳 Docker Desktop Issues
**Problem**: Docker Desktop not running
**Symptoms**:
- `Cannot connect to the Docker daemon`
- `docker: command not found`
**Solutions**:
1. Start Docker Desktop application
2. Check Docker Desktop status in menu bar
3. Verify Docker service is running:
   ```bash
   docker info
   ```
4. Restart Docker Desktop if needed

#### 2. 🔌 Port Conflicts
**Problem**: Port already in use
**Symptoms**:
- `Port 9093 is already in use`
- `Address already in use`
**Solutions**:
```bash
# Find process using port
lsof -i :9093
lsof -i :9092
lsof -i :2181

# Kill the process
kill -9 <PID>

# Or stop existing containers
./stop-local-kafka.sh
docker system prune -f
```

#### 3. 🔐 SSL/TLS Issues
**Problem**: SSL handshake failures
**Symptoms**:
- `SSL handshake failed`
- `Certificate not found`
- `javax.net.ssl.SSLHandshakeException`
**Solutions**:
1. Regenerate certificates:
```bash
   ./generate-ssl-certs.sh
   ```
2. Restart Kafka:
   ```bash
   ./stop-local-kafka.sh && ./start-local-kafka.sh
   ```
3. Verify certificate validity:
   ```bash
   keytool -list -v -keystore kafka/secrets/kafka.keystore.p12
   ```
4. Check certificate expiration:
   ```bash
   keytool -list -keystore kafka/secrets/kafka.keystore.p12 | grep "Valid from"
   ```

#### 4. ☕ Java Version Issues
**Problem**: Wrong Java version
**Symptoms**:
- `Unsupported major.minor version`
- `Java version not found`
**Solutions**:
1. Install Java 21:
```bash
   brew install openjdk@21
   ```
2. Set JAVA_HOME:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
   ```
3. Verify Java version:
   ```bash
   java --version
   echo $JAVA_HOME
   ```

#### 5. 📦 Maven Issues
**Problem**: Maven build failures
**Symptoms**:
- `Maven not found`
- `Build failed`
**Solutions**:
1. Install Maven:
   ```bash
   brew install maven
   ```
2. Clear Maven cache:
   ```bash
   mvn clean
   rm -rf ~/.m2/repository
   ```

#### 6. 🔧 jq/bc Missing (WiFi Generator Issues)
**Problem**: WiFi message generation fails
**Symptoms**:
- `jq: command not found`
- `bc: command not found`
**Solutions**:
```bash
# Install jq for JSON processing
brew install jq

# bc is usually pre-installed, but if missing:
brew install bc
```

#### 7. 📨 Message Production/Consumption Issues
**Problem**: Messages not being sent or received
**Symptoms**:
- No messages in topic
- Consumer not receiving messages
**Solutions**:
1. Verify topic exists:
```bash
   docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
   ```
2. Check topic details:
   ```bash
   docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic test-topic
   ```
3. Test with simple producer/consumer:
   ```bash
   ./test/send-test-message.sh "Test message"
   ./test/consume-test-messages.sh
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
3. Run full test suite: `./test/run-test-suite.sh`
4. Update documentation as needed
5. Submit pull request with description

### Local Development Setup

```bash
# Clone repository
git clone <repository-url>
cd wifi-scan-queue-consumer

# Setup development environment
cd scripts
./setup.sh
```

## 🔥 Firehose Integration

The application integrates with AWS Kinesis Data Firehose to stream processed data to S3. This enables:

- **Real-time Data Streaming**: Continuous data flow to S3
- **Automatic Partitioning**: Data organized by date/time
- **Scalable Storage**: Leverage S3's unlimited storage
- **Data Analytics**: Enable downstream analytics and processing

### Firehose Configuration

```yaml
aws:
  firehose:
    delivery-stream-name: MVS-stream
    region: us-east-1
    batch-size: 100
    batch-interval: 60
```

### Testing Firehose Integration

```bash
# Test Firehose integration
./test/validate-firehose-integration.sh --count 20 --interval 1

# Monitor S3 for delivered data
aws s3 ls s3://wifi-scan-data-bucket --recursive
```

## 🔒 Security Note

⚠️ **Important**: These scripts are designed for **local development only**. The certificates and passwords used are not secure and should never be used in production environments.

- Default passwords (`kafka123`) are hardcoded for development convenience
- Certificates are self-signed and not from a trusted CA
- SSL configuration is simplified for development use
- No authentication mechanisms are enabled

## 📊 Validation Checklist

### ✅ Prerequisites Checklist
- [ ] Docker Desktop installed and running
- [ ] Java 21 installed and configured ($JAVA_HOME set)
- [ ] Maven 3.9+ installed
- [ ] jq installed (for WiFi message generation)
- [ ] bc available (for calculations)
- [ ] All scripts are executable (`chmod +x *.sh`)
- [ ] Sufficient disk space (20GB+ free)

### ✅ Environment Setup Checklist
- [ ] Certificates generated successfully (`ls kafka/secrets/`)
- [ ] Kafka container running (`docker ps | grep kafka`)
- [ ] SSL port 9093 accessible (`telnet localhost 9093`)
- [ ] Plaintext port 9092 accessible (`telnet localhost 9092`)
- [ ] Test topic created
- [ ] Messages can be sent and consumed
- [ ] WiFi message generator works (`./test/send-wifi-scan-messages.sh --count 1`)

### ✅ Application Setup Checklist
- [ ] Spring Boot application builds successfully (`mvn clean compile`)
- [ ] Application connects to Kafka over SSL
- [ ] Health checks passing (`curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health`)
- [ ] Messages processed correctly (check application logs)
- [ ] No SSL errors in logs
- [ ] Consumer group registered in Kafka

### 🧪 Clean Environment Test

To test scripts on a fresh Mac:

1. **Reset environment:**
```bash
   ./cleanup.sh
   ```

2. **Run complete setup:**
   ```bash
   ./setup.sh
```

3. **Verify setup:**
```bash
   ./test/test-ssl-connection.sh
   ./test/create-test-topic.sh
   ./test/send-test-message.sh "Test message"
   ./test/send-wifi-scan-messages.sh --count 3
   ./test/consume-test-messages.sh
   ```

## 🍎 Mac-Specific Notes

### macOS Compatibility

1. **Docker Desktop:**
   - Requires macOS 11 (Big Sur) or later
   - Needs virtualization support enabled in System Preferences
   - May require additional memory allocation (8GB+ recommended)
   - Check "Use Rosetta for x86/AMD64 emulation" if on Apple Silicon

2. **Java 21:**
   - Use Homebrew for installation (recommended)
   - Set JAVA_HOME correctly for your shell (.zshrc for zsh)
   - Verify PATH includes Java bin directory
   - Apple Silicon Macs: Use ARM64 version for better performance

3. **File Permissions:**
   - Ensure all scripts are executable (`chmod +x *.sh`)
   - Check certificate file permissions (should be readable)
   - Verify Docker volume mounts work correctly

4. **Performance Optimization:**
   - Allocate sufficient memory to Docker (8GB+ recommended)
   - Monitor CPU usage during message processing
   - Check disk space regularly (Docker images can be large)
   - Consider increasing Docker Desktop resource limits

5. **Network Configuration:**
   - macOS firewall may block local ports
   - Antivirus software may interfere with Docker networking
   - VPN connections may affect localhost connectivity

### macOS-Specific Troubleshooting

```bash
# Check macOS version compatibility
sw_vers

# Verify Homebrew installation
brew --version

# Check Java installation paths
/usr/libexec/java_home -V

# Monitor system resources
top -o cpu
df -h

# Check port availability on macOS
sudo lsof -i :9093
sudo lsof -i :9092
```