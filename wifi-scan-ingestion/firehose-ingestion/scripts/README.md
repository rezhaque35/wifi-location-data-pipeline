# WiFi Scan Ingestion Pipeline - Complete Script Guide

This directory contains all automation scripts for the WiFi scan ingestion pipeline, from infrastructure setup to data processing and integration testing.

## 📋 Quick Reference

| Script | Purpose | Execution Order |
|--------|---------|----------------|
| `setup.sh` | Master setup - runs everything | **1st** (New setup) |
| `install-dependencies.sh` | Install required tools | 1st (Manual setup) |
| `start-localstack.sh` | Start LocalStack services | 2nd (Manual setup) |
| `deploy-infrastructure.sh` | Deploy Terraform infrastructure | 3rd (Manual setup) |
| `verify-deployment.sh` | Verify infrastructure health | After deployment |
| `message_processor.py` | WiFi data compression utility | For data processing |
| `test_message_compression.sh` | Test compression functionality | 4th (Testing) |
| `ingestion_integration_test.sh` | Full pipeline integration test | 5th (Testing) |
| `test_idempotent_setup.sh` | Test idempotent behavior | For validation |
| `cleanup.sh` | Clean up resources | As needed |

## 🔄 **Idempotent Design**

All scripts are designed to be **idempotent** - safe to run multiple times:
- ✅ **Smart Checks**: Each step verifies if work is already done before proceeding
- ✅ **Skip Completed Tasks**: Already satisfied dependencies and deployments are skipped
- ✅ **Clear Messaging**: Shows what's being done vs what's being skipped
- ✅ **Fast Re-runs**: Subsequent executions are much faster than initial setup
- ✅ **Safe Interruption**: Can be interrupted and resumed without corruption

## 🚀 Quick Start Workflows

### New Machine Setup (Automated)
```bash
# Complete automated setup
./setup.sh

# Verify everything works
./verify-deployment.sh

# Test data compression
./test_message_compression.sh

# Run full integration test
./ingestion_integration_test.sh
```

### Manual Step-by-Step Setup
```bash
# 1. Install dependencies
./install-dependencies.sh

# 2. Start LocalStack
./start-localstack.sh

# 3. Deploy infrastructure
./deploy-infrastructure.sh

# 4. Verify deployment
./verify-deployment.sh

# 5. Test compression
./test_message_compression.sh

# 6. Full integration test
./ingestion_integration_test.sh
```

## 📖 Detailed Script Documentation

---

## 🏗️ Infrastructure Setup Scripts

### `setup.sh` - Master Orchestrator
**Purpose**: Complete automated setup from zero to fully functional pipeline

```bash
./setup.sh
```

**What it does**:
- Installs all dependencies via `install-dependencies.sh`
- Starts LocalStack via `start-localstack.sh`
- Deploys infrastructure via `deploy-infrastructure.sh`
- Verifies deployment via `verify-deployment.sh`
- Provides next steps for testing

**Best for**: New machines or complete fresh start

### `install-dependencies.sh` - Dependency Manager
**Purpose**: Cross-platform installation of required tools

```bash
./install-dependencies.sh
```

**Installs**:
- **Docker**: Desktop (macOS) / Engine (Linux)
- **Terraform**: Latest version with provider management
- **AWS CLI v2**: LocalStack compatibility
- **jq**: JSON processing for scripts
- **curl**: Health checks and API calls

**Features**:
- Cross-platform support (macOS, Linux)
- Checks existing installations to avoid conflicts
- Uses native package managers (Homebrew, apt)
- Provides manual installation guides for unsupported systems

### `start-localstack.sh` - LocalStack Manager
**Purpose**: Manages LocalStack container lifecycle with comprehensive health monitoring

```bash
./start-localstack.sh
```

**Services Configured**:
- S3 (data storage)
- Kinesis Firehose (data ingestion)
- SQS (message queues)
- EventBridge (event routing)
- IAM (permissions)
- CloudWatch Logs (monitoring)

**Features**:
- Automatic health checks (30 attempts with exponential backoff)
- Container restart if unhealthy
- Service availability verification
- Persistent volume management
- Port configuration (4566)

### `deploy-infrastructure.sh` - Infrastructure Deployer
**Purpose**: Terraform-based infrastructure deployment with intelligent state management

```bash
./deploy-infrastructure.sh
```

**Smart Deployment Logic**:
- **No state**: Fresh deployment
- **State up-to-date**: Shows outputs, skips deployment
- **State with changes**: Updates infrastructure incrementally
- **Corrupted state**: Reinitializes and redeploys

**Resources Deployed**:
- S3 bucket with versioning and notifications
- Kinesis Firehose delivery stream
- SQS queues (main + dead letter)
- EventBridge rule and targets
- IAM roles and policies

---

## ✅ Verification & Management Scripts

### `verify-deployment.sh` - Health Checker
**Purpose**: Comprehensive verification of all deployed components

```bash
./verify-deployment.sh
```

**Verification Matrix**:
- ✅ **LocalStack Health**: Service availability and version
- ✅ **S3 Bucket**: Existence, versioning, event notifications
- ✅ **SQS Queues**: Main queue and DLQ configuration  
- ✅ **Kinesis Firehose**: Stream status and S3 destination
- ✅ **EventBridge Rule**: Event patterns and target mappings
- ✅ **IAM Roles**: All required roles and policy attachments
- ✅ **Terraform State**: Validity and resource counts

**Output**: Color-coded pass/fail report with detailed diagnostics

### `cleanup.sh` - Resource Manager
**Purpose**: Flexible cleanup with multiple operation modes

```bash
# Interactive cleanup (default)
./cleanup.sh

# Specific cleanup modes
./cleanup.sh terraform    # Destroy only infrastructure
./cleanup.sh localstack   # Stop only LocalStack
./cleanup.sh docker       # Clean Docker resources
./cleanup.sh force        # No confirmation prompts

# Show all options
./cleanup.sh help
```

**Cleanup Modes**:
- **`all/full`**: Complete environment reset
- **`terraform/tf`**: Infrastructure destruction only
- **`localstack/ls`**: LocalStack container management
- **`docker`**: Docker volume and network cleanup
- **`force`**: Automated cleanup (CI/CD friendly)

---

## 🔬 Data Processing & Testing Scripts

### `message_processor.py` - WiFi Data Compression Engine
**Purpose**: Advanced WiFi scan data compression and encoding for Firehose ingestion

```bash
# Basic compression
python3 message_processor.py --compress --file sample_data.json

# Create Firehose payload
python3 message_processor.py --compress --firehose --file sample_data.json

# Decompress data
python3 message_processor.py --decompress --base64 <encoded_string>

# Full options
python3 message_processor.py --help
```

**Features**:
- **GZIP Compression**: Configurable compression levels (1-9)
- **Base64 Encoding**: Firehose-compatible data format
- **Round-trip Support**: Compress → decompress verification
- **Metadata Tracking**: Processing timestamps and statistics
- **Format Validation**: JSON structure verification
- **Batch Processing**: Multiple file support

**Performance with Sample Data** (`smaple_wifiscan.json`):
- Original: 26,645 bytes
- Compressed: 2,463 bytes
- Encoded: 3,284 bytes
- **Compression Ratio: 90.76% reduction**

### `test_message_compression.sh` - Compression Validator
**Purpose**: Automated testing of compression functionality with the sample WiFi data

```bash
./test_message_compression.sh
```

**Test Matrix**:
- ✅ **File Processing**: Sample data loading and validation
- ✅ **Compression**: GZIP compression with size verification
- ✅ **Encoding**: Base64 encoding for Firehose compatibility
- ✅ **Round-trip**: Compress → decompress → verify integrity
- ✅ **Performance**: Compression ratio calculation
- ✅ **Error Handling**: Invalid data handling

**Output**: Detailed test results with compression statistics

### `test_idempotent_setup.sh` - Idempotent Behavior Validator
**Purpose**: Validates that setup.sh is truly idempotent and can be run multiple times safely

```bash
./test_idempotent_setup.sh
```

**Test Process**:
1. **Fresh Setup**: Runs setup.sh from clean state
2. **Second Run**: Verifies steps are skipped appropriately 
3. **Third Run**: Confirms consistent idempotent behavior
4. **Performance Analysis**: Compares execution times
5. **Health Verification**: Ensures infrastructure remains functional

**Validation Checks**:
- ✅ **Safety**: Multiple runs don't break existing setup
- ✅ **Efficiency**: Subsequent runs are faster than initial setup
- ✅ **Correctness**: Infrastructure remains healthy after multiple runs
- ✅ **Messaging**: Clear indication of skipped vs executed steps

**Output**: Comprehensive test report with timing analysis and recommendations

### `ingestion_integration_test.sh` - Complete Pipeline Tester
**Purpose**: End-to-end integration testing of the entire data pipeline

```bash
./ingestion_integration_test.sh
```

**Prerequisites**: 
- LocalStack running (`./start-localstack.sh`)
- Infrastructure deployed (`./deploy-infrastructure.sh`)

**Integration Test Flow**:
1. **Data Preparation**: Compress sample WiFi scan data
2. **Firehose Ingestion**: Send data through Kinesis Firehose
3. **S3 Delivery**: Verify file creation with correct naming/partitioning
4. **Event Notification**: Confirm S3 → EventBridge event triggering
5. **SQS Message**: Validate message delivery to SQS queue
6. **Content Verification**: Decompress and verify data integrity
7. **Error Testing**: Dead letter queue and failure scenarios
8. **Cleanup**: Temporary resource cleanup

**Test Scenarios**:
- ✅ Normal data flow (compressed WiFi scans)
- ✅ File naming patterns (YYYY/MM/DD/HH partitioning)
- ✅ Event notification chain (S3 → EventBridge → SQS)
- ✅ Data integrity (compression → storage → retrieval)
- ✅ Error handling (malformed data, service failures)
- ✅ Dead letter queue functionality

---

## 🎯 Usage Scenarios

### Daily Development Workflow
```bash
# Check if everything is running
./verify-deployment.sh

# If LocalStack is down, restart it
./start-localstack.sh

# Deploy any infrastructure changes
./deploy-infrastructure.sh

# Test your data processing
./test_message_compression.sh
```

### Debugging Issues
```bash
# Check current status
./verify-deployment.sh

# Check LocalStack logs
docker logs wifi-localstack

# Restart LocalStack if needed
./cleanup.sh localstack
./start-localstack.sh

# Redeploy infrastructure
./deploy-infrastructure.sh
```

### Clean Environment for Testing
```bash
# Complete cleanup
./cleanup.sh

# Fresh setup
./setup.sh

# Run all tests
./test_message_compression.sh
./ingestion_integration_test.sh
```

### CI/CD Pipeline Usage
```bash
# Non-interactive setup
./setup.sh 2>&1 | tee setup.log

# Run tests
./test_message_compression.sh
./ingestion_integration_test.sh

# Force cleanup
./cleanup.sh force
```

---

## 🔧 Configuration & Customization

### Environment Variables
```bash
# Debug mode for all scripts
export DEBUG=1

# Show executed commands
export BASH_XTRACING=1

# Custom LocalStack endpoint
export LOCALSTACK_ENDPOINT=http://localhost:4566

# AWS credentials (automatically set for LocalStack)
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
```

### File Locations
```
scripts/
├── README.md                      # This comprehensive guide
├── setup.sh                      # Master setup orchestrator
├── install-dependencies.sh       # Cross-platform dependency installer
├── start-localstack.sh           # LocalStack lifecycle manager
├── deploy-infrastructure.sh      # Terraform infrastructure deployer
├── verify-deployment.sh          # Comprehensive health checker
├── cleanup.sh                    # Multi-mode resource cleanup
├── message_processor.py          # WiFi data compression engine
├── test_message_compression.sh   # Compression functionality tester
├── ingestion_integration_test.sh    # End-to-end pipeline validator
└── test_idempotent_setup.sh      # Idempotent behavior validator
```

---

## 🚨 Troubleshooting Guide

### Common Issues & Solutions

#### 🔴 Docker Not Running
```bash
# macOS: Start Docker Desktop application
open -a Docker

# Linux: Start Docker service
sudo systemctl start docker

# Verify Docker is running
docker version
```

#### 🔴 LocalStack Health Check Fails
```bash
# Check container status
docker ps | grep localstack

# View container logs
docker logs wifi-localstack

# Restart LocalStack
./cleanup.sh localstack
./start-localstack.sh
```

#### 🔴 Terraform State Issues
```bash
# Check state status
cd ../terraform && terraform state list

# Force refresh state
terraform refresh

# Complete state reset (if needed)
./cleanup.sh terraform
./deploy-infrastructure.sh
```

#### 🔴 Permission Denied (Linux)
```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Apply group changes (logout/login required)
newgrp docker

# Verify access
docker ps
```

#### 🔴 Integration Test Failures
```bash
# Verify prerequisites
./verify-deployment.sh

# Check AWS CLI configuration
aws --endpoint-url=http://localhost:4566 s3 ls

# Test basic connectivity
curl -s http://localhost:4566/health

# Run tests with debug output
DEBUG=1 ./ingestion_integration_test.sh
```

### Script Debugging

Enable verbose output:
```bash
# Debug mode for detailed logging
export DEBUG=1
./script_name.sh

# Show all executed commands
export BASH_XTRACING=1
./script_name.sh

# Combine both for maximum visibility
DEBUG=1 BASH_XTRACING=1 ./script_name.sh
```

### Log Locations
- **LocalStack**: `docker logs wifi-localstack`
- **Terraform**: `terraform/terraform.log` (if TF_LOG is set)
- **Script Output**: Console with color-coded status messages
- **AWS CLI**: `~/.aws/cli/cache/` (for LocalStack)

---

## 📊 Performance Metrics

### Compression Performance
**Sample WiFi Scan Data** (`documents/smaple_wifiscan.json`):
- **Original Size**: 26,645 bytes
- **Compressed Size**: 2,463 bytes  
- **Encoded Size**: 3,284 bytes
- **Compression Ratio**: 90.76% size reduction
- **Processing Time**: <100ms for single scan

### Infrastructure Deployment Times
- **Fresh Setup**: 2-3 minutes
- **Incremental Updates**: 30-60 seconds
- **LocalStack Startup**: 30-45 seconds
- **Health Verification**: 10-15 seconds

### Test Execution Times
- **Compression Test**: 5-10 seconds
- **Integration Test**: 2-3 minutes
- **Full Pipeline Verification**: 1-2 minutes

---

## 🔒 Security & Best Practices

### LocalStack Security
- Uses dummy AWS credentials (`test`/`test`)
- All services run in isolated Docker container
- No real AWS resources created
- Data stays local to development machine

### Script Security
- Input validation on all user parameters
- Safe temporary file handling with automatic cleanup
- Error handling prevents partial state corruption
- No hardcoded credentials or sensitive data

### Production Considerations
- Scripts designed for development/testing only
- Replace LocalStack endpoints with real AWS for production
- Implement proper IAM roles and policies for production
- Add encryption for sensitive WiFi location data

---

## 🎯 Business Impact & Benefits

### Cost Optimization
- **90.76% storage reduction** through compression
- **Bandwidth savings** for high-volume WiFi data
- **Processing efficiency** improvements
- **Scalable architecture** for growth

### Development Efficiency  
- **One-command setup** for new developers
- **Automated testing** reduces manual verification
- **Comprehensive logging** speeds troubleshooting
- **Modular scripts** enable targeted operations

### Production Readiness
- **Infrastructure as Code** with Terraform
- **Comprehensive error handling** and validation
- **Monitoring and health checks** built-in
- **Automated cleanup** prevents resource leaks

---

## 📞 Support & Next Steps

### Getting Help
1. **Check this README** for your specific use case
2. **Run verification script**: `./verify-deployment.sh`
3. **Enable debug mode**: `DEBUG=1 ./script_name.sh`
4. **Check logs**: `docker logs wifi-localstack`
5. **Review troubleshooting section** above

### Contributing
When adding new scripts:
1. Follow the existing naming convention
2. Add comprehensive help output (`--help`)
3. Include error handling and cleanup
4. Update this README with script documentation
5. Add the script to the execution order table

### License
These scripts are part of the WiFi location data pipeline project.

---

**Last Updated**: Phase 3 Implementation Complete  
**Status**: All infrastructure and testing scripts operational ✅ 