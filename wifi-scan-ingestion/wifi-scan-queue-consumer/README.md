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

## 🔐 SSL Certificate Setup (First-Time Users)

**⚠️ IMPORTANT**: Before running tests or starting the application, you need to generate SSL certificates for Kafka communication. This is a **one-time setup** that's required for both development and testing.

### Quick SSL Setup (Recommended)
```bash
# Navigate to setup directory
cd wifi-scan-ingestion/wifi-scan-queue-consumer/scripts/setup

# Generate SSL certificates (takes ~30 seconds)
./generate-ssl-certs.sh
```

This script will:
- ✅ Create `kafka/secrets/` directory with SSL certificates
- ✅ Generate keystore and truststore files
- ✅ **Automatically copy keystore/truststore to test resources**
- ✅ Set up proper file permissions
- ✅ Validate certificate generation

**✨ Smart Behavior**: The script automatically detects if certificates already exist and **skips regeneration** to avoid disrupting your running services. Use `--force` flag only when you need to regenerate (requires service restart).

### What Gets Created
```
scripts/
├── kafka/secrets/                    # Main SSL certificates (git-ignored)
│   ├── kafka.keystore.p12           # Kafka keystore
│   ├── kafka.truststore.p12         # Kafka truststore
│   ├── ca-cert                      # Certificate authority
│   ├── ca-key                       # CA private key
│   └── *_creds                      # Credential files
└── src/test/resources/secrets/      # Test resources (git-tracked)
    ├── kafka.keystore.p12           # ✅ Copied for unit tests
    └── kafka.truststore.p12         # ✅ Copied for unit tests
```

### Manual SSL Setup (If Needed)
```bash
# Navigate to scripts directory
cd wifi-scan-ingestion/wifi-scan-queue-consumer/scripts

# Make script executable
chmod +x generate-ssl-certs.sh

# Generate certificates
./generate-ssl-certs.sh

# Verify files were created
ls -la kafka/secrets/
ls -la ../src/test/resources/secrets/
```

### SSL Certificate Details
- **Keystore Password**: `kafka123` (for local development only)
- **Truststore Password**: `kafka123` (for local development only)
- **Certificate Validity**: 365 days
- **Format**: PKCS12 (.p12)
- **Auto-copy**: Keystore/truststore automatically copied to test resources

### Troubleshooting SSL Setup
```bash
# If you get permission errors
chmod +x scripts/generate-ssl-certs.sh

# If certificates are expired or corrupted
rm -rf scripts/kafka/secrets/
rm -rf src/test/resources/secrets/*.p12
./scripts/generate-ssl-certs.sh

# Verify SSL setup worked
./scripts/setup/test-ssl-connection.sh
```

### ✅ SSL Setup Complete!
Once you see the success message "✅ Keystore and truststore files have been automatically copied to test resources!", you're ready to proceed with testing and development.

## 🚀 Quick Start

### Option 1: Automated Setup (Recommended)
```bash
# Navigate to setup scripts directory
cd wifi-scan-ingestion/wifi-scan-queue-consumer/scripts/setup

# Run the complete setup script (handles everything automatically)
./setup.sh
```

This script will:
- ✅ Check all prerequisites (Docker, Java, Maven, etc.)
- ✅ **Auto-install Docker Compose** via Homebrew if needed
- ✅ **Generate SSL certificates** (if not already done)
- ✅ Set up the Kafka environment with SSL
- ✅ Start Kafka cluster
- ✅ Test SSL connectivity
- ✅ Create test topic
- ✅ Send and consume test messages
- ✅ Set up AWS infrastructure (LocalStack)
- ✅ Provide clear next steps

**💡 Note**: The script uses intelligent path resolution and can find the `kafka/` directory regardless of where it's run from.

### Option 2: Manual Setup
```bash
# Navigate to setup directory
cd wifi-scan-ingestion/wifi-scan-queue-consumer/scripts/setup

# Make scripts executable
chmod +x *.sh
chmod +x ../test/*.sh

# Generate SSL certificates (required for first-time setup)
./generate-ssl-certs.sh

# Set up Kafka environment  
./setup-local-kafka.sh

# Start Kafka cluster (from parent scripts directory)
cd ..
./start-local-kafka.sh

# Test the setup
./setup/test-ssl-connection.sh
./setup/create-test-topic.sh
./test/send-test-message.sh "Hello SSL Kafka!"
./setup/consume-test-messages.sh
```

### Option 3: Test with WiFi Scan Data
```bash
# After basic setup, test with realistic WiFi data
./test/send-generated-wifi-scan-messages.sh --count 5 --interval 1
./setup/consume-test-messages.sh wifi-scan-data
```

### 4. Run Integration Tests

```bash
# Run comprehensive test suite
./test/run-test-suite.sh

# Or test Firehose integration specifically
./test/validate-firehose-integration.sh
```

**🔐 SSL Requirement**: Make sure you've run `./generate-ssl-certs.sh` before running tests, as unit tests require the keystore and truststore files in `src/test/resources/secrets/`.

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

### 🔐 SSL Certificate Requirement for Testing

**⚠️ IMPORTANT**: Before running any tests, ensure SSL certificates are generated:

```bash
# Generate SSL certificates (required for unit tests)
cd scripts && ./generate-ssl-certs.sh
```

Unit tests require keystore and truststore files in `src/test/resources/secrets/`. The `generate-ssl-certs.sh` script automatically copies these files to the correct location.

### Test Categories

#### Unit Tests
- **Location**: `src/test/java`
- **Framework**: JUnit 5, Mockito
- **Coverage**: Individual components and business logic
- **SSL Requirement**: Requires `kafka.keystore.p12` and `kafka.truststore.p12` in `src/test/resources/secrets/`

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

### 🧪 Comprehensive Test Suite (`run-test-suite.sh`)

The `run-test-suite.sh` script provides **comprehensive end-to-end validation** of the entire WiFi scan queue consumer service. It runs **13 different test scenarios** covering all major use cases and edge cases.

#### 🎯 **Test Categories & Use Cases**

##### **1. Basic Functionality Tests**
- **Test**: Basic Functionality (3 messages)
- **Purpose**: Validates core message processing capabilities
- **Use Case**: Ensures the service can handle basic WiFi scan data ingestion
- **Validation**: Message consumption, health monitoring, S3 data delivery

##### **2. Performance & Load Testing**
- **Test**: Quick Processing (5 messages, 0.5s interval)
- **Purpose**: Tests rapid message processing capabilities
- **Use Case**: High-frequency WiFi scan data from mobile devices
- **Validation**: Processing speed, memory usage, response times

- **Test**: Moderate Load (10 messages, 1s interval)
- **Purpose**: Validates steady-state processing performance
- **Use Case**: Continuous WiFi scan data streams from multiple sources
- **Validation**: Throughput, stability, resource utilization

- **Test**: High Frequency (15 messages, 0.3s interval)
- **Purpose**: Stress tests the service under high message volume
- **Use Case**: Peak traffic scenarios, mobile app usage spikes
- **Validation**: System resilience, error handling, performance degradation

##### **3. Health Monitoring & Observability**
- **Test**: Health Monitoring (8 messages, frequent checks)
- **Purpose**: Validates health check endpoints and monitoring capabilities
- **Use Case**: Production monitoring, alerting, and observability
- **Validation**: Health endpoints, metrics collection, status reporting

- **Test**: Verbose Monitoring (5 messages with detailed output)
- **Purpose**: Tests detailed logging and debugging capabilities
- **Use Case**: Development debugging, production troubleshooting
- **Validation**: Log output, error reporting, diagnostic information

##### **4. AWS Firehose Integration Testing**
- **Test**: Firehose Integration - Basic (5 messages)
- **Purpose**: Validates AWS Kinesis Firehose data delivery
- **Use Case**: Streaming data to S3 data lake for analytics
- **Validation**: Firehose delivery, S3 storage, data format

- **Test**: Firehose Integration - Moderate Load (10 messages)
- **Purpose**: Tests Firehose performance under normal load
- **Use Case**: Production data pipeline reliability
- **Validation**: Delivery reliability, data consistency, error handling

- **Test**: Firehose Integration - High Frequency (15 messages, 0.5s interval)
- **Purpose**: Stress tests Firehose integration under high volume
- **Use Case**: Peak data ingestion scenarios
- **Validation**: Throughput limits, backpressure handling, data loss prevention

- **Test**: Firehose Integration - Verbose (8 messages with detailed output)
- **Purpose**: Detailed validation of Firehose data transformation
- **Use Case**: Data quality assurance, format validation
- **Validation**: Data transformation, encoding, compression

##### **5. WiFi Scan Endpoint Testing**
- **Test**: WiFi Scan Endpoint - Basic (3 messages)
- **Purpose**: Validates REST API endpoint for WiFi scan data
- **Use Case**: Direct API integration with mobile apps
- **Validation**: HTTP endpoints, request/response handling, data validation

- **Test**: WiFi Scan Endpoint - Moderate Load (5 messages)
- **Purpose**: Tests endpoint performance under normal usage
- **Use Case**: Production API usage patterns
- **Validation**: Response times, error handling, rate limiting

- **Test**: WiFi Scan Endpoint - Verbose (3 messages with detailed output)
- **Purpose**: Detailed API validation and debugging
- **Use Case**: API development and troubleshooting
- **Validation**: Request/response logging, error details, data format

#### 🔧 **Test Suite Features**

##### **Automated Environment Management**
- **S3 Bucket Cleanup**: Automatically cleans S3 bucket before and after tests
- **Data Backup**: Optional backup of existing S3 data before cleanup
- **Health Recovery**: Automatically detects and recovers from message consumption timeouts
- **Resource Cleanup**: Ensures clean test environment for each test

##### **Comprehensive Validation**
- **Message Processing**: Validates end-to-end message flow from Kafka to S3
- **Health Monitoring**: Checks service health, readiness, and liveness endpoints
- **Data Integrity**: Verifies data transformation and storage in S3
- **Error Handling**: Tests error scenarios and recovery mechanisms
- **Performance Metrics**: Monitors processing times and throughput

##### **Flexible Configuration**
```bash
# Basic test suite run
./test/run-test-suite.sh

# Skip cleanup (preserve test data)
./test/run-test-suite.sh --skip-cleanup

# Backup existing data before cleanup
./test/run-test-suite.sh --backup-old-data

# Verbose output for debugging
./test/run-test-suite.sh --verbose

# Combine options
./test/run-test-suite.sh --skip-cleanup --verbose
```

#### 📊 **Test Results & Reporting**

The test suite provides comprehensive reporting:
- **Individual Test Results**: Pass/fail status for each test scenario
- **Summary Statistics**: Total tests, passed, failed counts
- **Detailed Output**: Verbose mode shows detailed test execution
- **Error Details**: Specific failure reasons and debugging information

#### 🎯 **Use Cases Validated**

1. **📱 Mobile App Integration**: WiFi scan data ingestion from mobile devices
2. **🏢 Enterprise WiFi Monitoring**: Large-scale WiFi network monitoring
3. **📊 Data Analytics Pipeline**: Real-time data processing for analytics
4. **🔍 IoT Device Integration**: WiFi-enabled IoT device data collection
5. **🌐 Multi-tenant Services**: Shared infrastructure for multiple clients
6. **⚡ High-frequency Data**: Rapid WiFi scan data from dense environments
7. **🛡️ Production Reliability**: Enterprise-grade reliability and monitoring
8. **🔧 Development Workflow**: Local development and testing capabilities

#### 🚀 **Running the Test Suite**

```bash
# Navigate to scripts directory
cd wifi-scan-ingestion/wifi-scan-queue-consumer/scripts

# Run complete test suite
./test/run-test-suite.sh

# Run with verbose output for debugging
./test/run-test-suite.sh --verbose

# Run without cleanup (preserve test data)
./test/run-test-suite.sh --skip-cleanup

# Run with data backup
./test/run-test-suite.sh --backup-old-data --verbose
```

**Expected Output**:
```
========================================
SERVICE VALIDATION TEST SUITE
========================================

[TEST] Running: Basic Functionality (3 messages)
[PASS] Basic Functionality (3 messages)

[TEST] Running: Quick Processing (5 messages, 0.5s interval)
[PASS] Quick Processing (5 messages, 0.5s interval)

...

========================================
TEST RESULTS SUMMARY
========================================
✅ PASS - Basic Functionality (3 messages)
✅ PASS - Quick Processing (5 messages, 0.5s interval)
...

Total Tests: 13
Passed: 13
Failed: 0

🎉 ALL TESTS PASSED!
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
│   ├── setup/                            # 🆕 Setup scripts directory
│   │   ├── setup.sh                      # ⭐ Complete automated setup (single service)
│   │   ├── setup-ingestion-store-stage.sh # 🔄 Complete pipeline setup (both stages)
│   │   ├── setup-local-kafka.sh         # Kafka environment setup
│   │   ├── setup-aws-infrastructure.sh  # AWS infrastructure setup
│   │   ├── generate-ssl-certs.sh        # SSL certificate generation
│   │   ├── start-local-kafka.sh         # Start Kafka cluster
│   │   ├── stop-local-kafka.sh          # Stop and cleanup
│   │   ├── cleanup.sh                   # Complete infrastructure cleanup
│   │   ├── cleanup-pipeline.sh          # 🔄 Pipeline cleanup (both stages)
│   │   ├── monitor-pipeline.sh          # 🔄 Real-time pipeline monitoring
│   │   ├── test-end-to-end.sh           # 🔄 End-to-end pipeline test
│   │   ├── test-ssl-connection.sh        # 🔐 SSL connectivity test (moved from test/)
│   │   ├── create-test-topic.sh          # 📝 Topic creation (moved from test/)
│   │   ├── consume-test-messages.sh      # 📨 Message consumer (moved from test/)
│   │   ├── docker-compose.yml           # 🆕 Kafka Docker config (generated)
│   │   └── docker-compose-localstack.yml # 🆕 LocalStack Docker config (generated)
│   ├── test/                            # Test scripts directory
│   │   ├── run-test-suite.sh             # Comprehensive test suite
│   │   ├── send-test-message.sh          # Simple message sender
│   │   ├── send-generated-wifi-scan-messages.sh    # 📡 WiFi scan data generator
│   │   ├── validate-service-health.sh    # 🔍 End-to-end service validation
│   │   ├── validate-firehose-integration.sh # Firehose integration testing
│   │   ├── validate-wifi-scan-endpoint.sh # WiFi scan endpoint testing
│   │   └── test-wifi-scan-endpoint.sh    # WiFi scan endpoint testing
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

🆕 **All setup scripts have been moved to `scripts/setup/` directory for better organization. They can be run from any location and will automatically handle path resolution.**

#### 📁 Script Organization Update

**🔄 Recent Reorganization**: Scripts have been reorganized for better logical grouping:

- **Setup Utility Scripts** (moved from `test/` to `setup/`):
  - `test-ssl-connection.sh` - SSL connectivity validation
  - `create-test-topic.sh` - Kafka topic creation
  - `consume-test-messages.sh` - Message consumption utilities

- **Comprehensive Test Scripts** (remain in `test/`):
  - `run-test-suite.sh` - Complete test suite with 13 scenarios
  - `validate-service-health.sh` - End-to-end service validation
  - `validate-firehose-integration.sh` - AWS Firehose integration testing
  - `send-generated-wifi-scan-messages.sh` - Realistic WiFi data generation

**🎯 Rationale**: Scripts used primarily by setup processes are now grouped with setup utilities, while comprehensive testing scripts remain in the test directory. This improves maintainability and makes the script structure more intuitive.

| Script | Purpose | Usage | Parameters | Duration |
|--------|---------|--------|------------|----------|
| `setup/setup.sh` | **Complete automated setup** | `cd scripts/setup && ./setup.sh` | `--force-certs` | ~3-5 min |
| `setup/setup-local-kafka.sh` | Kafka environment setup only | `cd scripts/setup && ./setup-local-kafka.sh` | `--force-certs` | ~2-3 min |
| `setup/setup-aws-infrastructure.sh` | AWS infrastructure setup | `cd scripts/setup && ./setup-aws-infrastructure.sh` | None | ~2-3 min |
| `setup/generate-ssl-certs.sh` | Generate/manage SSL certificates | `cd scripts/setup && ./generate-ssl-certs.sh` | `--force` | ~30 sec |
| `setup/start-local-kafka.sh` | Start Kafka cluster | `cd scripts/setup && ./start-local-kafka.sh` | None | ~30 sec |
| `setup/stop-local-kafka.sh` | Stop Kafka cluster | `cd scripts/setup && ./stop-local-kafka.sh` | `--force` | ~15 sec |
| `setup/cleanup.sh` | Complete infrastructure cleanup | `cd scripts/setup && ./cleanup.sh` | `--no-docker`, `--no-aws` | ~1-2 min |

**Key Benefits:**
- ✅ **Path-independent**: Run from `scripts/setup/` directory without path issues
- ✅ **Automatic path resolution**: Scripts find `kafka/` directory in parent `scripts/` dir
- ✅ **Better organization**: Setup scripts separated from operational scripts
- ✅ **Smart SSL management**: Certificates are only generated once, reused on subsequent runs
- ✅ **Service continuity**: Re-running setup won't require application restarts (unless `--force-certs` used)

### 🔐 SSL Certificate Management

The setup scripts now include **intelligent SSL certificate management** that prevents service disruptions:

#### Default Behavior (Smart Mode)
```bash
cd scripts/setup
./setup.sh                    # Skips cert generation if they exist
./setup-local-kafka.sh        # Skips cert generation if they exist
./generate-ssl-certs.sh       # Skips if certificates exist
```

**What happens:**
- ✅ Checks if SSL certificates already exist
- ✅ If found, reuses existing certificates (no regeneration)
- ✅ **No service restart required** - your running application continues working
- ✅ Perfect for re-running setup scripts multiple times during development

#### Force Regeneration Mode
```bash
cd scripts/setup
./setup.sh --force-certs              # Forces new certificate generation
./setup-local-kafka.sh --force-certs  # Forces new certificate generation
./generate-ssl-certs.sh --force       # Forces new certificate generation
```

**What happens:**
- ⚠️ Regenerates SSL certificates even if they exist
- ⚠️ **Requires Spring Boot application restart** after completion
- ✅ Use when certificates expire or need to be refreshed

#### Why This Matters

**Problem it solves:**
- When SSL certificates are regenerated, Kafka loads the new certificates
- Your running Spring Boot application still has old certificates in memory
- This causes `SSLHandshakeException: Path does not chain with any of the trust anchors`

**Solution:**
- Certificates are generated once and reused
- Re-running setup scripts won't disrupt your running services
- Explicit `--force-certs` flag when you intentionally want to regenerate (with awareness of restart requirement)

#### Certificate Locations
```
scripts/
├── kafka/
│   └── secrets/                      # Used by Kafka broker (Docker volume)
│       ├── kafka.keystore.p12
│       ├── kafka.truststore.p12
│       └── *_creds files
└── ../src/test/resources/secrets/    # Used by Spring Boot application
    ├── kafka.keystore.p12
    └── kafka.truststore.p12
```

**Note:** Both locations are kept in sync automatically by `generate-ssl-certs.sh`

### 🔄 End-to-End Pipeline Setup Scripts

The following scripts set up and manage the complete data pipeline (Ingestion + Store stages):

| Script | Purpose | Usage | Duration |
|--------|---------|-------|----------|
| `setup/setup-ingestion-store-stage.sh` | **Complete pipeline setup** | `cd scripts/setup && ./setup-ingestion-store-stage.sh` | ~3-5 min |
| `setup/monitor-pipeline.sh` | **Real-time pipeline monitoring** | `cd scripts/setup && ./monitor-pipeline.sh [--watch]` | Ongoing |
| `setup/test-end-to-end.sh` | **End-to-end pipeline test** | `cd scripts/setup && ./test-end-to-end.sh` | ~2-3 min |
| `setup/cleanup-pipeline.sh` | **Pipeline cleanup** | `cd scripts/setup && ./cleanup-pipeline.sh [--full]` | ~1-2 min |

#### setup-ingestion-store-stage.sh

**Purpose**: Complete automated setup for both ingestion and store stages of the data pipeline.

**Data Flow**:
```
Kafka → Queue Consumer → Firehose (MVS) → S3 (wifi-scan-data-bucket)
  ↓
S3 Event → SQS (wifi-scan-events) → Transformer → Firehose → S3 (wifi-measurements-table)
```

**What it creates**:
- ✅ Kafka cluster with SSL (ingestion stage)
- ✅ LocalStack with AWS services (both stages)
- ✅ 2 S3 buckets (wifi-scan-data-bucket, wifi-measurements-table)
- ✅ 2 SQS queues (wifi-scan-events, wifi-scan-events-dlq)
- ✅ 2 Firehose delivery streams (MVS-stream, wifi-measurements-stream)
- ✅ S3 → SQS event notifications

**Options**:
```bash
# Full setup
./setup-ingestion-store-stage.sh

# Clean before setup
./setup-ingestion-store-stage.sh --cleanup

# Force regenerate SSL certificates
./setup-ingestion-store-stage.sh --force-certs

# Skip specific stages
./setup-ingestion-store-stage.sh --skip-ingestion  # Only setup store stage
./setup-ingestion-store-stage.sh --skip-store      # Only setup ingestion stage
```

**After setup, start both services**:
```bash
# Terminal 1 - Queue Consumer
cd wifi-scan-queue-consumer
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 - Transformer Service
cd wifi-measurements-transformer-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

#### monitor-pipeline.sh

**Purpose**: Real-time monitoring dashboard for the entire pipeline.

**Monitors**:
- 🐳 Docker containers (Kafka, LocalStack)
- 💚 LocalStack health
- 📨 Kafka topics
- 🗄️ S3 bucket file counts
- 📬 SQS queue metrics
- 🔥 Firehose stream status
- ☕ Java service status
- 📈 Pipeline conversion rates

**Usage**:
```bash
# One-time snapshot
./monitor-pipeline.sh

# Continuous monitoring (refresh every 5 seconds)
./monitor-pipeline.sh --watch
```

**Example output**:
```
╔════════════════════════════════════════╗
║  DATA PIPELINE MONITORING DASHBOARD   ║
╚════════════════════════════════════════╝

Pipeline Statistics:
  📥 Ingested Files (S3): 142
  📨 Pending Events (SQS): 0
  📤 Processed Files (S3): 138
  Conversion Rate: 97.18%
```

#### test-end-to-end.sh

**Purpose**: Complete end-to-end validation of the data pipeline.

**Test flow**:
1. Checks prerequisites (Kafka, LocalStack, services)
2. Sends test WiFi scan message to Kafka
3. Waits for file in ingestion S3 bucket (60-90s)
4. Checks for S3 event in SQS queue
5. Waits for processed file in output S3 bucket (60-90s)
6. Displays summary and validates success

**Usage**:
```bash
# Ensure both services are running first
./test-end-to-end.sh
```

**Expected latency**: 70-100 seconds (due to Firehose buffering)

#### cleanup-pipeline.sh

**Purpose**: Clean up pipeline resources.

**Options**:
```bash
# Standard cleanup (stops services, cleans AWS resources)
./cleanup-pipeline.sh

# Full cleanup (removes all containers, volumes, certificates)
./cleanup-pipeline.sh --full
```

**What it cleans**:
- 🧹 Stops Java services
- 🗑️ Deletes AWS resources (SQS, S3, Firehose)
- 🛑 Stops Kafka cluster
- 🛑 Stops LocalStack
- 💥 (Full only) Removes Docker containers and volumes

#### Quick Pipeline Workflow

```bash
# 1. Setup everything
cd scripts/setup
./setup-ingestion-store-stage.sh

# 2. Start services (in separate terminals)
# Terminal 1: cd wifi-scan-queue-consumer && mvn spring-boot:run -Dspring-boot.run.profiles=local
# Terminal 2: cd wifi-measurements-transformer-service && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. Monitor pipeline
./monitor-pipeline.sh --watch

# 4. Test end-to-end (in another terminal)
./test-end-to-end.sh

# 5. Cleanup when done
./cleanup-pipeline.sh
```

**Important Notes**:
- ⏱️ Firehose buffering causes 60-90 second delays (expected)
- 🔄 Both services must be running for end-to-end flow
- 📦 Only 2 S3 buckets created (LocalStack doesn't support Iceberg)
- 🧪 Test data flows through entire pipeline automatically

### Testing Scripts

| Script | Purpose | Usage | Parameters | Use Case |
|--------|---------|--------|------------|----------|
| `test/run-test-suite.sh` | **🎯 Comprehensive test suite (13 scenarios)** | `./test/run-test-suite.sh [options]` | --skip-cleanup, --backup-old-data, --verbose | **Complete service validation** |
| `test/send-test-message.sh` | Send simple text messages | `./test/send-test-message.sh "message" [topic-name]` | Required: message, Optional: topic | Basic testing |
| `test/send-generated-wifi-scan-messages.sh` | **📡 Send generated WiFi scan data** | `./test/send-generated-wifi-scan-messages.sh [options]` | --count, --interval, --topic, --ssl | **Load/performance testing** |
| `test/send-file-wifi-scan-messages.sh` | **📄 Send WiFi scan data from file** | `./test/send-file-wifi-scan-messages.sh --file FILE [options]` | --file (required), --interval, --topic, --ssl | **Bug reproduction/troubleshooting** |
| `test/validate-service-health.sh` | **End-to-end service validation** | `./test/validate-service-health.sh [options]` | --count, --interval, --timeout, --verbose | **Complete service validation** |
| `test/validate-firehose-integration.sh` | **Firehose integration testing** | `./test/validate-firehose-integration.sh [options]` | --count, --interval, --timeout, --verbose | **AWS integration testing** |
| `test/validate-wifi-scan-endpoint.sh` | **WiFi scan endpoint testing** | `./test/validate-wifi-scan-endpoint.sh [options]` | --count, --interval, --timeout, --verbose | **Endpoint validation** |
| `test/test-wifi-scan-endpoint.sh` | WiFi scan endpoint testing | `./test/test-wifi-scan-endpoint.sh [options]` | --count, --interval, --timeout, --verbose | **Endpoint validation** |

### Setup Utility Scripts

**🆕 Scripts moved from `test/` to `setup/` directory for better organization:**

| Script | Purpose | Usage | Parameters | Use Case |
|--------|---------|--------|------------|----------|
| `setup/test-ssl-connection.sh` | Validate SSL connectivity | `./setup/test-ssl-connection.sh` | None | SSL verification |
| `setup/create-test-topic.sh` | Create test topic | `./setup/create-test-topic.sh [topic-name]` | Optional: topic name | Topic management |
| `setup/consume-test-messages.sh` | Consume messages | `./setup/consume-test-messages.sh [topic-name]` | Optional: topic name | Message verification |

**📝 Note**: These scripts were moved because they are primarily used by setup scripts and provide infrastructure utilities rather than comprehensive testing functionality.

### 🎯 Comprehensive Test Suite (`run-test-suite.sh`)

The `run-test-suite.sh` script is the **flagship testing tool** that validates the entire service through **13 comprehensive test scenarios**. It provides **end-to-end validation** of all major use cases and production scenarios.

#### 📋 **Test Scenarios Overview**

| Test Category | Test Name | Messages | Interval | Purpose |
|---------------|-----------|----------|----------|---------|
| **Basic Functionality** | Basic Functionality | 3 | 1s | Core message processing |
| **Performance** | Quick Processing | 5 | 0.5s | Rapid processing validation |
| **Performance** | Moderate Load | 10 | 1s | Steady-state performance |
| **Performance** | High Frequency | 15 | 0.3s | Stress testing |
| **Monitoring** | Health Monitoring | 8 | 1s | Health check validation |
| **Monitoring** | Verbose Monitoring | 5 | 1s | Detailed logging validation |
| **Firehose** | Firehose Integration - Basic | 5 | 1s | AWS Firehose validation |
| **Firehose** | Firehose Integration - Moderate | 10 | 1s | Firehose performance |
| **Firehose** | Firehose Integration - High Freq | 15 | 0.5s | Firehose stress testing |
| **Firehose** | Firehose Integration - Verbose | 8 | 1s | Firehose detailed validation |
| **API** | WiFi Scan Endpoint - Basic | 3 | 1s | REST API validation |
| **API** | WiFi Scan Endpoint - Moderate | 5 | 1s | API performance |
| **API** | WiFi Scan Endpoint - Verbose | 3 | 1s | API detailed validation |

#### 🚀 **Quick Start**

```bash
# Run complete test suite (recommended)
./test/run-test-suite.sh

# Run with verbose output for debugging
./test/run-test-suite.sh --verbose

# Run without cleanup (preserve test data)
./test/run-test-suite.sh --skip-cleanup

# Run with data backup before cleanup
./test/run-test-suite.sh --backup-old-data --verbose
```

#### 🎯 **What Gets Validated**

1. **📱 Message Processing**: End-to-end flow from Kafka to S3
2. **🏥 Health Monitoring**: Service health, readiness, and liveness
3. **📊 Data Integrity**: S3 data transformation and storage
4. **⚡ Performance**: Processing speed and throughput
5. **🛡️ Reliability**: Error handling and recovery
6. **🔗 AWS Integration**: Firehose delivery and S3 storage
7. **🌐 API Functionality**: REST endpoint validation
8. **📈 Observability**: Logging and monitoring capabilities

### WiFi Scan Message Generator Scripts

#### 📡 Generated Messages: `send-generated-wifi-scan-messages.sh`

The `test/send-generated-wifi-scan-messages.sh` script is a specialized tool that generates realistic WiFi scan data messages for testing the positioning service. This script creates messages that exactly match the `WifiPositioningRequest` format used by the WiFi positioning service.

#### 📄 File-Based Messages: `send-file-wifi-scan-messages.sh`

The `test/send-file-wifi-scan-messages.sh` script sends WiFi scan messages from files, perfect for troubleshooting, bug reproduction, and testing specific scenarios. It supports multiple file formats and reads configuration from `application.yml` just like the generator script.

#### 🎯 Key Features

- **📡 Realistic Data Generation**: Creates valid MAC addresses, signal strengths (-30 to -100 dBm), and frequencies
- **📶 Multiple Frequency Bands**: Supports both 2.4GHz (2412-2472 MHz) and 5GHz (5180-5825 MHz) bands
- **🔢 Variable Scan Results**: Each message contains 1-5 scan results per positioning request
- **⚙️ Configuration from application.yml**: Automatically reads Kafka broker, topic, and SSL settings from `application.yml` to ensure consistency with the service
- **🔄 Single Source of Truth**: Eliminates configuration drift by using the same configuration as the Spring Boot application
- **📋 JSON Format**: Generates properly formatted JSON messages for the positioning service
- **🔄 Continuous Streaming**: Can generate continuous message streams for load testing
- **🎛️ Override Support**: Allows command-line overrides for testing different configurations

#### 📖 Usage Examples

```bash
# Basic usage - Uses configuration from application.yml, sends 10 messages
./test/send-generated-wifi-scan-messages.sh

# Quick test - Send 5 messages every 1 second (uses application.yml for broker/topic)
./test/send-generated-wifi-scan-messages.sh --count 5 --interval 1

# Load testing - Send 100 messages rapidly
./test/send-generated-wifi-scan-messages.sh --count 100 --interval 0.1

# Override topic from application.yml (not recommended - causes warning)
./test/send-generated-wifi-scan-messages.sh --count 20 --topic wifi-positioning-test

# Force plaintext connection (overrides SSL setting from application.yml)
./test/send-generated-wifi-scan-messages.sh --count 20 --no-ssl

# High-frequency continuous stream for stress testing
./test/send-generated-wifi-scan-messages.sh --count 1000 --interval 0.05

# Production-like testing with realistic intervals
./test/send-generated-wifi-scan-messages.sh --count 50 --interval 2
```

#### 🔧 Configuration Source

The script automatically reads the following from `src/main/resources/application.yml`:
- **Bootstrap Servers** (`kafka.bootstrap-servers`)
- **Topic Name** (`kafka.topic.name`)
- **SSL Enabled** (`kafka.ssl.enabled`)
- **SSL Keystore/Truststore** locations and passwords

This ensures messages are **always sent to the same broker and topic** that your Spring Boot service is consuming from, eliminating configuration drift.

---

### 📄 File-Based Message Sender (`send-file-wifi-scan-messages.sh`)

#### 🎯 Purpose

Perfect for **developer troubleshooting** and **bug reproduction**. Send specific WiFi scan messages from files to test exact scenarios, reproduce production issues, or validate edge cases.

#### 📋 Supported File Formats

1. **Single JSON Object** - Send one message
   ```json
   {
     "wifiScanResults": [...],
     "client": "debug-client",
     "requestId": "test-001"
   }
   ```

2. **JSON Array** - Send multiple messages
   ```json
   [
     {"wifiScanResults": [...], "client": "test-1"},
     {"wifiScanResults": [...], "client": "test-2"}
   ]
   ```

3. **JSONL (Newline-Delimited JSON)** - Send messages from log files
   ```
   {"wifiScanResults": [...], "client": "test-1"}
   {"wifiScanResults": [...], "client": "test-2"}
   ```

#### 📖 Usage Examples

```bash
# Send single debug message once
./test/send-file-wifi-scan-messages.sh --file samples/single-message.json

# Send same message 5 times for load testing
./test/send-file-wifi-scan-messages.sh --file samples/single-message.json --count 5

# Send multiple test cases with 1s interval
./test/send-file-wifi-scan-messages.sh --file samples/multiple-messages.json --interval 1

# Repeat array 3 times (each element sent 3 times)
./test/send-file-wifi-scan-messages.sh --file samples/multiple-messages.json --count 3

# Reproduce bug from production logs (JSONL format)
./test/send-file-wifi-scan-messages.sh --file bug-reproduction.jsonl

# Replay production logs 10 times for stress testing
./test/send-file-wifi-scan-messages.sh --file production-issue.jsonl --count 10

# Send to custom topic for isolated testing
./test/send-file-wifi-scan-messages.sh --file debug-case.json --topic debug-topic
```

#### 📋 Command Line Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `--file FILE` | JSON file with WiFi scan messages (required) | - | `--file test.json` |
| `--count N` | Number of times to repeat message(s) from file | 1 | `--count 5` |
| `--interval SECONDS` | Interval between messages | 2 | `--interval 1` |
| `--topic TOPIC` | Override topic from application.yml | From application.yml | `--topic debug` |
| `--ssl` | Force SSL connection | From application.yml | `--ssl` |
| `--no-ssl` | Force plaintext connection | From application.yml | `--no-ssl` |
| `--help` | Show help message | - | `--help` |

#### 🔧 Sample Files Included

The script comes with sample files in `scripts/test/samples/`:

**`single-message.json`**
- **Format**: Single JSON object
- **Messages**: 1
- **Use Case**: Quick debug testing, single message reproduction
```bash
./test/send-file-wifi-scan-messages.sh --file samples/single-message.json
./test/send-file-wifi-scan-messages.sh --file samples/single-message.json --count 10  # Send 10 times
```

**`multiple-messages.json`**
- **Format**: JSON array
- **Messages**: 3
- **Use Case**: Multiple test scenarios, batch testing
```bash
./test/send-file-wifi-scan-messages.sh --file samples/multiple-messages.json --interval 1
./test/send-file-wifi-scan-messages.sh --file samples/multiple-messages.json --count 5  # Each element sent 5 times
```

**`bug-reproduction.jsonl`**
- **Format**: JSONL (newline-delimited JSON)
- **Messages**: 2 valid + 1 invalid (for testing error handling)
- **Use Case**: Production log replay, bug reproduction from log files
```bash
./test/send-file-wifi-scan-messages.sh --file samples/bug-reproduction.jsonl
./test/send-file-wifi-scan-messages.sh --file samples/bug-reproduction.jsonl --count 3  # Replay 3 times
```

#### 💡 Use Cases

- **🐛 Bug Reproduction**: Save problematic messages from logs and replay them multiple times
- **🧪 Edge Case Testing**: Test specific signal strengths, frequencies, or configurations
- **📊 Integration Testing**: Send predefined test scenarios with configurable repetition
- **🔍 Debugging**: Isolate and test specific message formats
- **📝 Documentation**: Create reproducible test cases
- **⚡ Load Testing**: Repeat messages from file to test performance under load
- **🔄 Stress Testing**: Send production-like message patterns repeatedly

#### 🔧 Creating Your Own Test Files

**Single JSON Object**
```json
{
  "wifiScanResults": [
    {
      "macAddress": "aa:bb:cc:dd:ee:ff",
      "signalStrength": -65.4,
      "frequency": 2437,
      "ssid": "TestNetwork",
      "linkSpeed": 866,
      "channelWidth": 80
    }
  ],
  "client": "your-test-client",
  "requestId": "test-001",
  "application": "debugging",
  "calculationDetail": true
}
```

**JSON Array (Multiple Messages)**
```json
[
  {
    "wifiScanResults": [...],
    "client": "test-1",
    "requestId": "req-001"
  },
  {
    "wifiScanResults": [...],
    "client": "test-2",
    "requestId": "req-002"
  }
]
```

**JSONL Format (From Logs)**
```
{"wifiScanResults":[...],"client":"prod-1","requestId":"req-001"}
{"wifiScanResults":[...],"client":"prod-2","requestId":"req-002"}
```

#### 💡 Tips & Best Practices

1. **Extract from Production Logs**: Use `grep` or `jq` to extract messages from logs
   ```bash
   grep "WifiPositioningRequest" app.log | jq -c . > production-issue.jsonl
   ```

2. **Validate JSON**: Always validate your JSON before sending
   ```bash
   jq empty your-file.json  # Will show errors if invalid
   ```

3. **Test Locally First**: Use `--topic test-topic` to avoid affecting production data
   ```bash
   ./test/send-file-wifi-scan-messages.sh --file test.json --topic test-debug
   ```

4. **Monitor Service**: Check metrics after sending
   ```bash
   curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka
   ```

#### 🐛 Bug Reproduction Workflow

1. **Identify Issue**: Find problematic message in logs
2. **Extract Message**: Save to JSON file
3. **Validate Format**: Check with `jq empty file.json`
4. **Send to Test Topic**: Use `--topic debug-topic`
5. **Monitor Service**: Check logs and metrics
6. **Repeat if Needed**: Use `--count N` to reproduce intermittent issues
7. **Fix & Verify**: Apply fix and replay message

---

### 📊 Comparison: Generated vs File-Based

| Feature | Generated Messages | File-Based Messages |
|---------|-------------------|---------------------|
| **Use Case** | General testing, load testing | Troubleshooting, bug reproduction |
| **Data Source** | Random generation | File (JSON/JSONL) |
| **Reproducibility** | Different each time | Exact same messages |
| **Best For** | Performance testing | Debugging specific issues |
| **Configuration** | From application.yml | From application.yml |

---

#### 📋 Command Line Options (Generated Messages)

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `--count N` | Number of messages to send | 10 | `--count 50` |
| `--interval SECONDS` | Interval between messages | 2 | `--interval 1.5` |
| `--topic TOPIC` | Override topic from application.yml | From application.yml | `--topic my-topic` |
| `--ssl` | Force SSL connection | From application.yml | `--ssl` |
| `--no-ssl` | Force plaintext connection | From application.yml | `--no-ssl` |
| `--help` | Show help message | - | `--help` |

**Note**: The script reads Kafka configuration from `application.yml` by default. Command-line flags override these settings and display a warning to indicate the override.

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

- **📨 Automated Message Generation**: Uses `send-generated-wifi-scan-messages.sh` to generate realistic test data
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

# 0. Generate SSL certificates (first-time setup only)
./generate-ssl-certs.sh

# 1. Start Kafka environment
./start-local-kafka.sh

# 2. Verify connectivity
./setup/test-ssl-connection.sh

# 3. Create topic for your work (if needed)
./setup/create-test-topic.sh my-dev-topic
```

**💡 Note**: SSL certificate generation is only needed once. On subsequent runs, you can skip step 0.

#### 🧪 Testing Message Flow
```bash
# Simple text message testing
./test/send-test-message.sh "Debug message" my-dev-topic
./setup/consume-test-messages.sh my-dev-topic

# WiFi scan data testing (realistic data)
./test/send-generated-wifi-scan-messages.sh --count 5 --topic my-dev-topic
./setup/consume-test-messages.sh my-dev-topic

# End-to-end service validation (recommended)
./test/validate-service-health.sh --count 10 --verbose

# SSL testing
./test/send-generated-wifi-scan-messages.sh --count 3 --ssl
./setup/consume-test-messages.sh wifi-scan-data --ssl
```

#### 🔧 Development with Spring Boot Application
```bash
# 1. Start Kafka
./start-local-kafka.sh

# 2. Generate test data
./test/send-generated-wifi-scan-messages.sh --count 10 --interval 2

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
./test/send-generated-wifi-scan-messages.sh --count 1000 --interval 1

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

##### A. SSL Handshake Failure - Certificate Mismatch
**Problem**: `SSLHandshakeException: Path does not chain with any of the trust anchors`
**Symptoms**:
- Application was working, then SSL handshake errors appear
- Error after running setup scripts
- `org.apache.kafka.common.errors.SslAuthenticationException`
- `javax.net.ssl.SSLHandshakeException: PKIX path validation failed`

**Root Cause**:
- SSL certificates were regenerated while your Spring Boot application was running
- Kafka broker has new certificates, but your application has old ones in memory

**Solutions**:
1. **✅ Best Practice**: Re-run setup without regenerating certificates:
   ```bash
   cd scripts/setup
   ./setup.sh              # Skips cert generation if they exist
   ```
   This won't cause SSL issues because certificates aren't regenerated.

2. **If you must regenerate certificates**:
   ```bash
   cd scripts/setup
   ./setup.sh --force-certs    # Regenerates certificates
   ```
   **⚠️ Important**: Restart your Spring Boot application after this!

3. **Quick fix**: Just restart your Spring Boot application
   - It will reload the certificates from disk
   - Application will sync with Kafka's certificates

##### B. Missing SSL Certificates
**Problem**: Certificates don't exist
**Symptoms**:
- `Certificate not found`
- `FileNotFoundException: kafka.keystore.p12`
- `Unit tests failing with SSL errors`

**Solutions**:
1. **First-time setup**: Generate SSL certificates:
   ```bash
   cd scripts/setup
   ./generate-ssl-certs.sh
   ```

2. **Verify certificates exist**:
   ```bash
   ls -la scripts/kafka/secrets/
   # Should show: kafka.keystore.p12 and kafka.truststore.p12
   
   ls -la src/test/resources/secrets/
   # Should show: kafka.keystore.p12 and kafka.truststore.p12
   ```

3. **If certificates are corrupted**, regenerate them:
   ```bash
   cd scripts/setup
   ./generate-ssl-certs.sh --force
   ```
   Then restart both Kafka and your Spring Boot application.

##### C. Certificate Verification
**Useful commands for debugging**:
```bash
# Verify certificate validity
keytool -list -v -keystore scripts/kafka/secrets/kafka.keystore.p12 -storepass kafka123

# Check certificate expiration
keytool -list -keystore scripts/kafka/secrets/kafka.keystore.p12 -storepass kafka123 | grep "Valid"

# Test SSL connectivity
cd scripts && ./setup/test-ssl-connection.sh

# Verify certificates are in sync
diff <(md5 scripts/kafka/secrets/kafka.keystore.p12 | awk '{print $4}') \
     <(md5 src/test/resources/secrets/kafka.keystore.p12 | awk '{print $4}')
```

##### D. When to Force Regenerate Certificates
Only use `--force-certs` when:
- ✅ Certificates are expired (after 365 days)
- ✅ Certificates are corrupted
- ✅ You need to change certificate parameters
- ❌ NOT for routine setup re-runs (causes service disruption)

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

#### 6. 🧪 Unit Test Failures (SSL Related)
**Problem**: Unit tests failing due to missing SSL certificates
**Symptoms**:
- `FileNotFoundException: kafka.keystore.p12`
- `SSL handshake failed` in unit tests
- `No such file or directory: src/test/resources/secrets/`
**Solutions**:
1. **Generate SSL certificates for tests**:
   ```bash
   cd scripts && ./generate-ssl-certs.sh
   ```
2. **Verify test resources directory**:
   ```bash
   ls -la src/test/resources/secrets/
   # Should contain: kafka.keystore.p12, kafka.truststore.p12
   ```
3. **Check .gitignore exceptions**:
   ```bash
   git status src/test/resources/secrets/
   # Should show the .p12 files as tracked
   ```
4. **Re-run tests**:
   ```bash
   mvn test
   ```
5. **If still failing, regenerate and commit**:
   ```bash
   rm -rf src/test/resources/secrets/*.p12
   ./scripts/generate-ssl-certs.sh
   git add src/test/resources/secrets/*.p12
   git commit -m "Update SSL certificates for tests"
   mvn test
   ```
   ```

#### 7. 🔧 jq/bc Missing (WiFi Generator Issues)
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

#### 8. 📨 Message Production/Consumption Issues
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
- [ ] WiFi message generator works (`./test/send-generated-wifi-scan-messages.sh --count 1`)

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
   ./setup/test-ssl-connection.sh
   ./setup/create-test-topic.sh
   ./test/send-test-message.sh "Test message"
   ./test/send-generated-wifi-scan-messages.sh --count 3
   ./setup/consume-test-messages.sh
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