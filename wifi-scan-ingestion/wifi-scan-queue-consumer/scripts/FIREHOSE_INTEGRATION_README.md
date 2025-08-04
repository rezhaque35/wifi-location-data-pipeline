# Firehose Integration Guide

This document describes the AWS Kinesis Data Firehose integration for the WiFi Scan Queue Consumer service, including setup, testing, and validation procedures.

## Overview

The WiFi Scan Queue Consumer service now includes comprehensive AWS Kinesis Data Firehose integration for streaming data to S3. The integration includes:

- **LocalStack Support**: Full local development environment with AWS services
- **Firehose Delivery Stream**: Configured to buffer data and deliver to S3
- **S3 Integration**: Data is stored in partitioned folders by date/time
- **IAM Security**: Proper role-based access control
- **Health Monitoring**: Service health checks for Firehose connectivity
- **Comprehensive Testing**: End-to-end validation scripts

## Architecture

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

## Configuration

### Application Configuration

The service is configured via `application.yml`:

```yaml
aws:
  region: us-east-1
  endpoint-url: http://localhost:4566  # LocalStack endpoint
  credentials:
    access-key: test
    secret-key: test
  firehose:
    delivery-stream-name: MVS-stream
    buffer-time: 300         # 5 minutes
    batch-processing:
      min-batch-size: 10
      max-batch-size: 150
```

### Firehose Configuration

- **Buffer Size**: 128MB
- **Buffer Time**: 60 seconds
- **Compression**: None (for development)
- **Partitioning**: Year/Month/Day/Hour structure
- **Error Handling**: Separate error prefix for failed records

## Setup Instructions

### 1. Complete Environment Setup

Run the comprehensive setup script that includes both Kafka and AWS infrastructure:

```bash
./scripts/setup-dev-environment.sh
```

This script will:
- Install all dependencies (Docker, AWS CLI, jq, etc.)
- Set up Kafka with SSL
- Set up LocalStack with Firehose and S3
- Create all necessary AWS resources
- Test the complete setup

### 2. AWS Infrastructure Only

If you only need to set up AWS infrastructure:

```bash
./scripts/setup-aws-infrastructure.sh
```

This script is **idempotent** and will:
- Install missing dependencies
- Start LocalStack container
- Configure AWS CLI for LocalStack
- Create S3 bucket
- Create IAM role and policy
- Create Firehose delivery stream
- Test the setup

### 3. Manual Setup Steps

If you prefer to set up manually:

#### Prerequisites
```bash
# Install dependencies (macOS)
brew install awscli jq docker-compose

# Install dependencies (Linux)
sudo apt-get update
sudo apt-get install -y awscli jq docker-compose
```

#### Start LocalStack
```bash
# Create docker-compose file
cat > docker-compose-localstack.yml << EOF
version: '3.8'
services:
  localstack:
    image: localstack/localstack:latest
    container_name: localstack
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3,firehose,iam
      - DEBUG=1
      - AWS_DEFAULT_REGION=us-east-1
      - AWS_ACCESS_KEY_ID=test
      - AWS_SECRET_ACCESS_KEY=test
EOF

# Start LocalStack
docker-compose -f docker-compose-localstack.yml up -d
```

#### Configure AWS CLI
```bash
aws configure set aws_access_key_id test --profile localstack
aws configure set aws_secret_access_key test --profile localstack
aws configure set region us-east-1 --profile localstack
aws configure set output json --profile localstack
```

#### Create AWS Resources
```bash
# Create S3 bucket
aws s3 mb s3://wifi-scan-data-bucket --profile localstack --endpoint-url=http://localhost:4566

# Create IAM policy
aws iam create-policy \
  --policy-name FirehoseS3AccessPolicy \
  --policy-document file://firehose-policy.json \
  --profile localstack \
  --endpoint-url=http://localhost:4566

# Create IAM role
aws iam create-role \
  --role-name FirehoseDeliveryRole \
  --assume-role-policy-document file://trust-policy.json \
  --profile localstack \
  --endpoint-url=http://localhost:4566

# Attach policy to role
aws iam attach-role-policy \
  --role-name FirehoseDeliveryRole \
  --policy-arn arn:aws:iam::000000000000:policy/FirehoseS3AccessPolicy \
  --profile localstack \
  --endpoint-url=http://localhost:4566

# Create Firehose delivery stream
aws firehose create-delivery-stream \
  --cli-input-json file://firehose-config.json \
  --profile localstack \
  --endpoint-url=http://localhost:4566
```

## Testing and Validation

### 1. Comprehensive Test Suite

Run the complete test suite that includes both Kafka and Firehose integration:

```bash
./scripts/run-test-suite.sh
```

This includes:
- Basic functionality tests
- Load testing
- Health monitoring
- Firehose integration tests
- End-to-end validation

### 2. Firehose Integration Only

Test just the Firehose integration:

```bash
./scripts/validate-firehose-integration.sh
```

Options:
```bash
./scripts/validate-firehose-integration.sh --count 10 --interval 1 --verbose
./scripts/validate-firehose-integration.sh --count 20 --interval 0.5 --timeout 180
./scripts/validate-firehose-integration.sh --no-s3-check  # Skip S3 validation
```

### 3. Individual Component Tests

#### Test Firehose Stream Status
```bash
aws firehose describe-delivery-stream \
  --delivery-stream-name MVS-stream \
  --profile localstack \
  --endpoint-url=http://localhost:4566
```

#### Test Direct Firehose Put
```bash
aws firehose put-record \
  --delivery-stream-name MVS-stream \
  --record "Data={\"test\":\"data\"}" \
  --profile localstack \
  --endpoint-url=http://localhost:4566
```

#### Check S3 for Data
```bash
aws s3 ls s3://wifi-scan-data-bucket --recursive \
  --profile localstack \
  --endpoint-url=http://localhost:4566
```

#### Test Service Health
```bash
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness
```

## Monitoring and Health Checks

### Service Health Endpoints

- **General Health**: `/health`
- **Readiness**: `/health/readiness` (includes Firehose connectivity)
- **Liveness**: `/health/liveness` (includes message consumption activity)

### Firehose Health Indicators

The service includes health indicators for:
- **Firehose Connectivity**: Checks if the service can connect to Firehose
- **Message Consumption Activity**: Monitors if messages are being processed
- **SSL Certificate Health**: Validates SSL certificates for Kafka

### CloudWatch Integration

The service is configured to send metrics to CloudWatch (when running in AWS):
- SSL certificate expiry days
- Firehose delivery metrics
- Service performance metrics

## Troubleshooting

### Common Issues

#### 1. LocalStack Not Starting
```bash
# Check if port 4566 is in use
lsof -i :4566

# Check LocalStack logs
docker logs localstack

# Restart LocalStack
docker-compose -f docker-compose-localstack.yml restart
```

#### 2. Firehose Stream Not Active
```bash
# Check stream status
aws firehose describe-delivery-stream \
  --delivery-stream-name MVS-stream \
  --profile localstack \
  --endpoint-url=http://localhost:4566

# Wait for stream to become active (may take time in LocalStack)
```

#### 3. S3 Data Not Appearing
```bash
# Check if data is being sent to Firehose
aws firehose put-record \
  --delivery-stream-name MVS-stream \
  --record "Data={\"test\":\"data\"}" \
  --profile localstack \
  --endpoint-url=http://localhost:4566

# Check S3 bucket
aws s3 ls s3://wifi-scan-data-bucket --recursive \
  --profile localstack \
  --endpoint-url=http://localhost:4566
```

#### 4. Service Health Issues
```bash
# Check service logs
tail -f logs/application.log

# Check health endpoints
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness | jq

# Check specific health indicators
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health/readiness | jq '.components.firehoseConnectivity'
```

### Debug Mode

Enable debug logging in `application.yml`:
```yaml
logging:
  level:
    com.wifidata.consumer: DEBUG
    org.springframework.kafka: DEBUG
    com.amazonaws: DEBUG
```

## Cleanup

### Complete Cleanup
```bash
./scripts/cleanup-aws-infrastructure.sh
```

This will:
- Stop LocalStack
- Clean up AWS resources
- Remove temporary files
- Clean up Docker resources

### Selective Cleanup
```bash
# Stop only LocalStack
docker-compose -f docker-compose-localstack.yml down

# Clean up Docker resources
docker system prune -f

# Remove temporary files
rm -f /tmp/firehose-*.json
```

## Production Considerations

When deploying to production:

1. **Remove LocalStack**: Use real AWS services
2. **Update Configuration**: Set real AWS credentials and endpoints
3. **Security**: Use IAM roles instead of access keys
4. **Monitoring**: Enable CloudWatch metrics and alarms
5. **Backup**: Configure S3 lifecycle policies
6. **Encryption**: Enable encryption for Firehose and S3

### Production Configuration Example
```yaml
aws:
  region: us-east-1
  # endpoint-url: # Remove for production
  # credentials: # Remove for production (use IAM roles)
  firehose:
    delivery-stream-name: ${FIREHOSE_STREAM_NAME}
    buffer-time: 300
    batch-processing:
      min-batch-size: 10
      max-batch-size: 150
```

## Script Reference

### Setup Scripts
- `setup-aws-infrastructure.sh`: Sets up AWS infrastructure
- `setup-dev-environment.sh`: Complete development environment setup

### Validation Scripts
- `validate-firehose-integration.sh`: Firehose integration testing
- `run-test-suite.sh`: Comprehensive test suite

### Cleanup Scripts
- `cleanup-aws-infrastructure.sh`: Clean up AWS infrastructure
- `stop-local-kafka.sh`: Stop Kafka environment

### Utility Scripts
- `send-test-message.sh`: Send test messages to Kafka
- `consume-test-messages.sh`: Consume messages from Kafka
- `test-ssl-connection.sh`: Test Kafka SSL connectivity

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review service logs
3. Run validation scripts to identify specific issues
4. Check LocalStack logs for AWS service issues 