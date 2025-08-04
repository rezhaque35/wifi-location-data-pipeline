# Local Kafka SSL Development Environment Scripts

This directory contains scripts to set up a local Kafka development environment with SSL/TLS support on Mac machines, including a specialized WiFi scan data message generator for testing positioning services.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Script Descriptions](#script-descriptions)
4. [WiFi Scan Message Generator](#wifi-scan-message-generator)
5. [Configuration Details](#configuration-details)
6. [Development Workflow](#development-workflow)
7. [Troubleshooting Guide](#troubleshooting-guide)
8. [Validation Checklist](#validation-checklist)
9. [Security Note](#security-note)
10. [Support](#support)

## Prerequisites

Before running these scripts, ensure you have the following installed on your Mac:

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

## Quick Start

### Option 1: Automated Setup (Recommended)
```bash
# Navigate to scripts directory
cd wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts

# Run the complete setup script (handles everything automatically)
./setup-dev-environment.sh
```

This script will:
- ✅ Check all prerequisites
- ✅ Set up the Kafka environment with SSL
- ✅ Start Kafka cluster
- ✅ Test SSL connectivity
- ✅ Create test topic
- ✅ Send and consume test messages
- ✅ Provide clear next steps

### Option 2: Manual Setup
```bash
# Make scripts executable
chmod +x *.sh

# Set up Kafka environment
./setup-local-kafka.sh

# Start Kafka cluster
./start-local-kafka.sh

# Test the setup
./test-ssl-connection.sh
./create-test-topic.sh
./send-test-message.sh "Hello SSL Kafka!"
./consume-test-messages.sh
```

### Option 3: Test with WiFi Scan Data
```bash
# After basic setup, test with realistic WiFi data
./send-wifi-scan-messages.sh --count 5 --interval 1
./consume-test-messages.sh wifi-scan-data
```

## Script Descriptions

### Core Setup Scripts

| Script | Purpose | Usage | Parameters | Duration |
|--------|---------|--------|------------|----------|
| `setup-dev-environment.sh` | **Complete automated setup** | `./setup-dev-environment.sh` | None | ~3-5 min |
| `setup-local-kafka.sh` | Kafka environment setup only | `./setup-local-kafka.sh` | None | ~2-3 min |
| `generate-ssl-certs.sh` | Generate SSL certificates | `./generate-ssl-certs.sh` | None | ~30 sec |
| `start-local-kafka.sh` | Start Kafka cluster | `./start-local-kafka.sh` | None | ~30 sec |
| `stop-local-kafka.sh` | Stop and cleanup | `./stop-local-kafka.sh` | None | ~15 sec |

### Testing Scripts

| Script | Purpose | Usage | Parameters | Use Case |
|--------|---------|--------|------------|----------|
| `test-ssl-connection.sh` | Validate SSL connectivity | `./test-ssl-connection.sh` | None | SSL verification |
| `create-test-topic.sh` | Create test topic | `./create-test-topic.sh [topic-name]` | Optional: topic name | Topic management |
| `send-test-message.sh` | Send simple text messages | `./send-test-message.sh "message" [topic-name]` | Required: message, Optional: topic | Basic testing |
| `send-wifi-scan-messages.sh` | **Send realistic WiFi scan data** | `./send-wifi-scan-messages.sh [options]` | --count, --interval, --topic, --ssl | **Positioning service testing** |
| `validate-service-health.sh` | **End-to-end service validation** | `./validate-service-health.sh [options]` | --count, --interval, --timeout, --verbose | **Complete service validation** |
| `consume-test-messages.sh` | Consume messages | `./consume-test-messages.sh [topic-name]` | Optional: topic name | Message verification |

## WiFi Scan Message Generator

The `send-wifi-scan-messages.sh` script is a specialized tool that generates realistic WiFi scan data messages for testing the positioning service. This script creates messages that exactly match the `WifiPositioningRequest` format used by the WiFi positioning service.

### 🎯 Key Features

- **📡 Realistic Data Generation**: Creates valid MAC addresses, signal strengths (-30 to -100 dBm), and frequencies
- **📶 Multiple Frequency Bands**: Supports both 2.4GHz (2412-2472 MHz) and 5GHz (5180-5825 MHz) bands
- **🔢 Variable Scan Results**: Each message contains 1-5 scan results per positioning request
- **⚙️ Highly Configurable**: Count, interval, topic, SSL, and message structure options
- **📋 JSON Format**: Generates properly formatted JSON messages for the positioning service
- **🔄 Continuous Streaming**: Can generate continuous message streams for load testing

### 📖 Usage Examples

```bash
# Basic usage - Send 10 messages every 2 seconds (default)
./send-wifi-scan-messages.sh

# Quick test - Send 5 messages every 1 second
./send-wifi-scan-messages.sh --count 5 --interval 1

# Load testing - Send 100 messages rapidly
./send-wifi-scan-messages.sh --count 100 --interval 0.1

# SSL testing with custom topic
./send-wifi-scan-messages.sh --count 20 --topic wifi-positioning-test --ssl

# High-frequency continuous stream for stress testing
./send-wifi-scan-messages.sh --count 1000 --interval 0.05

# Production-like testing with realistic intervals
./send-wifi-scan-messages.sh --count 50 --interval 2 --topic wifi-scan-data
```

### 📋 Command Line Options

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `--count N` | Number of messages to send | 10 | `--count 50` |
| `--interval SECONDS` | Interval between messages | 2 | `--interval 1.5` |
| `--topic TOPIC` | Target Kafka topic | wifi-scan-data | `--topic my-topic` |
| `--ssl` | Use SSL connection | false | `--ssl` |
| `--help` | Show help message | - | `--help` |

### 📄 Sample Generated Message

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

### 🎲 Data Generation Details

**MAC Addresses**: Random valid format (XX:XX:XX:XX:XX:XX)
**Signal Strength**: -30 to -100 dBm (realistic WiFi range)
**Frequencies**: 
- 2.4GHz band: 2412, 2417, 2422, 2427, 2432, 2437, 2442, 2447, 2452, 2457, 2462, 2467, 2472 MHz
- 5GHz band: 5180, 5200, 5220, 5240, 5260, 5280, 5300, 5320, 5500-5825 MHz (various channels)

**SSIDs**: Realistic network names (HomeNetwork, OfficeWiFi, CoffeeShop, etc.)
**Link Speed**: 54-1054 Mbps
**Channel Width**: 20, 40, 80, 160 MHz
**Request IDs**: Unique timestamp-based IDs
**Calculation Detail**: Randomly true/false

### 🚀 Testing Scenarios

```bash
# Scenario 1: Basic functionality test
./send-wifi-scan-messages.sh --count 3 --interval 2

# Scenario 2: Performance testing
./send-wifi-scan-messages.sh --count 100 --interval 0.1

# Scenario 3: SSL connection testing
./send-wifi-scan-messages.sh --count 10 --ssl

# Scenario 4: Custom topic testing
./create-test-topic.sh wifi-positioning-input
./send-wifi-scan-messages.sh --count 20 --topic wifi-positioning-input

# Scenario 5: Long-running test
./send-wifi-scan-messages.sh --count 500 --interval 1
```

## Service Health Validation

The `validate-service-health.sh` script provides comprehensive end-to-end validation of your Spring Boot application. It sends WiFi scan messages and continuously monitors the service health indicators to ensure messages are being consumed correctly.

### 🎯 Key Features

- **📨 Automated Message Generation**: Uses `send-wifi-scan-messages.sh` to generate realistic test data
- **🔍 Real-time Health Monitoring**: Monitors overall health, readiness, and liveness endpoints
- **📊 Message Count Tracking**: Tracks message consumption through service metrics
- **⏱️ Configurable Timeouts**: Customizable validation timeouts and intervals
- **📋 Detailed Reporting**: Comprehensive validation reports with pass/fail status
- **🚨 Background Process Management**: Handles cleanup of background message senders

### 📖 Usage Examples

```bash
# Basic validation - Send 10 messages and monitor for 60 seconds
./validate-service-health.sh

# Quick validation - 5 messages with 30-second timeout
./validate-service-health.sh --count 5 --timeout 30

# Detailed validation with verbose output
./validate-service-health.sh --count 20 --interval 1 --verbose

# SSL testing with custom service URL
./validate-service-health.sh --ssl --service-url http://localhost:8081

# High-frequency testing
./validate-service-health.sh --count 50 --interval 0.5 --health-interval 2

# Production-like validation
./validate-service-health.sh --count 100 --interval 2 --timeout 300
```

### 📋 Command Line Options

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

### 🔍 What It Validates

1. **Prerequisites Check**:
   - Kafka container running
   - Required scripts available
   - curl and jq installed

2. **Service Availability**:
   - Spring Boot application responding
   - Health endpoints accessible
   - Initial service state

3. **Message Processing**:
   - Messages sent successfully
   - Message count increases in service metrics
   - Expected number of messages processed

4. **Health Indicators**:
   - Overall health status (UP/DOWN)
   - Readiness probe status
   - Liveness probe status
   - Continuous monitoring during test

5. **End-to-End Validation**:
   - Complete message flow verification
   - Service responsiveness
   - Health indicator reliability

### 📊 Sample Validation Report

```
=====================================>
VALIDATION REPORT
=====================================>
Service URL: http://localhost:8080
Message Processing:
  Initial count: 15
  Final count: 25
  Messages processed: 10
  Expected messages: 10
  Status: PASS

Health Monitoring:
  Total checks: 12
  Successful checks: 12
  Status: PASS

Overall Validation:
✅ VALIDATION PASSED
The service is correctly consuming messages and health indicators are working.
```

### 🚨 Failure Scenarios

The script detects and reports various failure conditions:

- **Message Processing Failures**: Messages not being consumed
- **Health Indicator Issues**: Health endpoints returning DOWN status
- **Service Unavailability**: Application not responding
- **Timeout Issues**: Validation taking too long
- **Prerequisites Missing**: Required components not available

### 🔧 Integration with Development Workflow

```bash
# Complete development validation workflow
./start-local-kafka.sh
cd ../ && mvn spring-boot:run &
cd scripts/
./validate-service-health.sh --verbose

# Automated CI/CD validation
./validate-service-health.sh --count 20 --timeout 120 --health-interval 3

# Performance testing validation
./validate-service-health.sh --count 100 --interval 0.1 --timeout 180
```

## Configuration Details

### SSL Configuration
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

## Development Workflow

### 🌅 Daily Development Startup
```bash
# 1. Start Kafka environment
./start-local-kafka.sh

# 2. Verify connectivity
./test-ssl-connection.sh

# 3. Create topic for your work (if needed)
./create-test-topic.sh my-dev-topic
```

### 🧪 Testing Message Flow
```bash
# Simple text message testing
./send-test-message.sh "Debug message" my-dev-topic
./consume-test-messages.sh my-dev-topic

# WiFi scan data testing (realistic data)
./send-wifi-scan-messages.sh --count 5 --topic my-dev-topic
./consume-test-messages.sh my-dev-topic

# End-to-end service validation (recommended)
./validate-service-health.sh --count 10 --verbose

# SSL testing
./send-wifi-scan-messages.sh --count 3 --ssl
./consume-test-messages.sh wifi-scan-data --ssl
```

### 🔧 Development with Spring Boot Application
```bash
# 1. Start Kafka
./start-local-kafka.sh

# 2. Generate test data
./send-wifi-scan-messages.sh --count 10 --interval 2

# 3. Start your Spring Boot application
cd ../
mvn spring-boot:run

# 4. Monitor application logs for message processing
# 5. Verify health endpoints
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health
```

### 🔄 Continuous Testing
```bash
# Terminal 1: Start continuous message generation
./send-wifi-scan-messages.sh --count 1000 --interval 1

# Terminal 2: Monitor Spring Boot application
cd ../ && mvn spring-boot:run

# Terminal 3: Monitor Kafka logs
docker logs -f kafka
```

### 🌙 Daily Shutdown
```bash
./stop-local-kafka.sh
```

### 🐛 Debugging Workflow
```bash
# 1. Check Kafka container status
docker ps | grep kafka

# 2. Check Kafka logs
docker logs kafka

# 3. Verify SSL connectivity
./test-ssl-connection.sh

# 4. Test message flow with debug data
./send-test-message.sh "Debug: $(date)" test-topic
./consume-test-messages.sh test-topic

# 5. Test WiFi data flow
./send-wifi-scan-messages.sh --count 3 --interval 1
./consume-test-messages.sh wifi-scan-data

# 6. Check Spring Boot application health
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/liveness

# 7. Run comprehensive validation
./validate-service-health.sh --count 5 --timeout 30 --verbose
```

## Directory Structure

After running the setup scripts, your directory structure will look like:

```
wifi-scan-queue-consumer/
├── scripts/
│   ├── README.md                      # This file
│   ├── setup-dev-environment.sh       # ⭐ Complete automated setup
│   ├── setup-local-kafka.sh          # Kafka environment setup
│   ├── generate-ssl-certs.sh         # SSL certificate generation
│   ├── start-local-kafka.sh          # Start Kafka cluster
│   ├── stop-local-kafka.sh           # Stop and cleanup
│   ├── test-ssl-connection.sh        # SSL connectivity test
│   ├── create-test-topic.sh          # Topic creation
│   ├── send-test-message.sh          # Simple message sender
│   ├── send-wifi-scan-messages.sh    # 📡 WiFi scan data generator
│   ├── validate-service-health.sh    # 🔍 End-to-end service validation
│   ├── consume-test-messages.sh      # Message consumer
│   ├── docker-compose.yml            # Docker configuration
│   └── kafka/                        # Generated during setup
│       └── secrets/
│           ├── kafka.keystore.jks     # SSL keystore
│           ├── kafka.truststore.jks   # SSL truststore
│           ├── ca-cert                # Certificate authority
│           └── ca-key                 # CA private key
├── src/main/resources/secrets/        # Application certificates
│   ├── kafka.keystore.jks
│   └── kafka.truststore.jks
├── pom.xml                           # Maven configuration
└── src/                              # Spring Boot application source
```

## Troubleshooting Guide

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
   keytool -list -v -keystore kafka/secrets/kafka.keystore.jks
   ```
4. Check certificate expiration:
   ```bash
   keytool -list -keystore kafka/secrets/kafka.keystore.jks | grep "Valid from"
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
   ./send-test-message.sh "Test message"
   ./consume-test-messages.sh
   ```

## Validation Checklist

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
- [ ] WiFi message generator works (`./send-wifi-scan-messages.sh --count 1`)

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
   ./stop-local-kafka.sh
   rm -rf kafka/
   rm -rf ../src/main/resources/secrets/
   docker system prune -f
   docker volume prune -f
   ```

2. **Run complete setup:**
   ```bash
   ./setup-dev-environment.sh
   ```

3. **Verify setup:**
   ```bash
   ./test-ssl-connection.sh
   ./create-test-topic.sh
   ./send-test-message.sh "Test message"
   ./send-wifi-scan-messages.sh --count 3
   ./consume-test-messages.sh
   ```

## Security Note

⚠️ **Important**: These scripts are designed for **local development only**. The certificates and passwords used are not secure and should never be used in production environments.

- Default passwords (`kafka123`) are hardcoded for development convenience
- Certificates are self-signed and not from a trusted CA
- SSL configuration is simplified for development use
- No authentication mechanisms are enabled

## Support

If you encounter issues not covered in this guide:

### 📋 Diagnostic Steps

1. **Check logs:**
   ```bash
   # Docker Desktop logs (via GUI)
   # Kafka container logs
   docker logs kafka
   
   # Application logs
   tail -f target/logs/application.log
   ```

2. **Verify configuration:**
   ```bash
   # Certificate validity
   keytool -list -v -keystore kafka/secrets/kafka.keystore.jks
   
   # Network connectivity
   telnet localhost 9093
   telnet localhost 9092
   
   # SSL handshake
   openssl s_client -connect localhost:9093
   ```

3. **Test individual components:**
   ```bash
   # Docker connectivity
   docker ps
   docker info
   
   # Kafka topics
   docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
   
   # Message flow
   ./send-test-message.sh "Debug message"
   ./consume-test-messages.sh
   ```

### 🔧 Common Fixes

- **Regenerate certificates**: `./generate-ssl-certs.sh`
- **Restart Docker Desktop**: Complete restart via GUI
- **Clear Docker cache**: `docker system prune -af`
- **Rebuild application**: `mvn clean compile`
- **Reset environment**: Follow clean environment test steps

### 📞 Getting Help

If problems persist:
1. Check container status: `docker ps -a`
2. Review all logs for error patterns
3. Verify all prerequisites are correctly installed
4. Try the clean environment test procedure
5. Check if ports are blocked by firewall/antivirus

## Mac-Specific Notes

### 🍎 macOS Compatibility

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

### 🔧 macOS-Specific Troubleshooting

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