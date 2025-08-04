## Cost Considerations (Production)

### Terraform Cost Management
- **Resource Tagging**: Consistent tagging strategy for cost allocation
- **Environment-Specific Sizing**: Different resource sizes per environment
- **Automated Cleanup**: Terraform# Firehose Ingestion Requirements for WiFi Scan Data Pipeline

## Overview
This document outlines the requirements for implementing a Kinesis Firehose-based ingestion pipeline for WiFi scan data using LocalStack on macOS. The pipeline will consume streaming data from API Gateway or EKS services, batch the data using size/time windows, and store it in S3 with automatic event notifications.

## Architecture Components

### 1. Data Flow
```
API Gateway/EKS → Kinesis Firehose → S3 Bucket → CloudWatch Event → SQS Queue
```

### 2. Core AWS Services Required
- **Kinesis Firehose**: Stream data batching and delivery
- **S3**: Data storage with organized partitioning
- **CloudWatch Events (EventBridge)**: Event-driven triggers
- **SQS**: Event queue for downstream processing
- **IAM**: Service permissions and roles

## Technical Requirements

### 1. Kinesis Firehose Configuration

#### Stream Details
- **Stream Name**: `MVS-stream`
- **Source**: Direct PUT from API Gateway or EKS service
- **Destination**: S3 bucket

#### Buffering Configuration
- **Buffer Size**: 5 MB (configurable based on volume)
- **Buffer Interval**: 60 seconds (1 minute)
- **Compression**: GZIP (optional, recommended for cost optimization)



### 2. S3 Bucket Configuration

#### Bucket Structure
- **Bucket Name**: `ingested-wifiscan-data`
- **Partitioning Pattern**: `/yyyy/mm/dd/HH/MVS-stream/`
- **File Naming Convention**: `MVS-stream-timestamp.txt`

#### Example S3 Path
```
s3://ingested-wifiscan-data/2025/07/16/16/MVS-stream/MVS-stream-2025-07-16-16-33-50-785527d6-002b-43fd-abb6-e61b4b98fb5c.txt
```

#### S3 Event Configuration
- **Event Type**: `s3:ObjectCreated:Put`
- **Target**: CloudWatch Events (EventBridge)
- **Filter**: `*.txt` files only

### 3. CloudWatch Events/EventBridge Configuration

#### Event Rule
- **Rule Name**: `wifi-scan-s3-put-rule`
- **Event Source**: `aws.s3`
- **Event Type**: `Object Created`
- **Target**: SQS Queue

#### Event Pattern
```json
{
  "source": ["aws.s3"],
  "detail-type": ["Object Created"],
  "detail": {
    "bucket": {
      "name": ["ingested-wifiscan-data"]
    },
    "object": {
      "key": [{
        "suffix": ".txt"
      }]
    }
  }
}
```

### 4. SQS Queue Configuration

#### Queue Details
- **Queue Name**: `wifi_scan_ingestion_event_queue`
- **Queue Type**: Standard Queue
- **Visibility Timeout**: 30 seconds
- **Message Retention**: 14 days
- **Dead Letter Queue**: Recommended for error handling

#### Message Format
Messages will contain S3 event details including:
- Bucket name
- Object key (file path)
- Event time
- File size
- ETag

### 5. IAM Roles and Policies

#### Firehose Service Role
- **Role Name**: `firehose-delivery-role`
- **Permissions**:
  - S3 bucket write access
  - CloudWatch Logs write access
  - Kinesis Data Firehose service permissions

#### S3 to EventBridge Role
- **Role Name**: `s3-eventbridge-role`
- **Permissions**:
  - EventBridge PutEvents permission
  - S3 event source permissions

#### EventBridge to SQS Role
- **Role Name**: `eventbridge-sqs-role`
- **Permissions**:
  - SQS SendMessage permission

## LocalStack Setup Requirements

### 1. LocalStack Configuration

#### Required Services
```bash
SERVICES=kinesis,firehose,s3,events,sqs,iam,logs
```

### 2. Environment Variables
```bash
export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_ENDPOINT_URL=http://localhost:4566
```

### 3. Required Tools
- **LocalStack**: Latest community version
- **Terraform**: Latest version (>= 1.0)
- **AWS CLI**: Configured for LocalStack
- **Docker**: For LocalStack container
- **curl/Postman**: For API testing
- **jq**: For JSON processing

## Implementation Strategy

### Infrastructure as Code with Terraform

This implementation uses Terraform to deploy the entire AWS infrastructure, enabling:
- **Environment Parity**: Identical infrastructure between LocalStack and AWS
- **Version Control**: Infrastructure changes tracked in Git
- **Reproducibility**: Consistent deployments across environments
- **Testing**: Easy teardown/rebuild cycles during development
- **Production Readiness**: Same Terraform code deploys to real AWS

### Additional Requirements for Terraform Implementation

#### 1. Terraform Project Structure
- Modular architecture with separate modules for each AWS service
- Environment-specific variable files (localstack, dev, staging, prod)
- Consistent naming conventions and tagging strategy
- Remote state management for production environments

#### 2. Environment Configuration Requirements
- **LocalStack**: Optimized for rapid development and testing
- **Production**: Enhanced security, monitoring, and cost optimization
- **Variable Management**: Environment-specific configurations
- **State Management**: Remote backend for team collaboration

#### 3. Terraform Provider Requirements
- AWS Provider with conditional LocalStack endpoints
- Version constraints for reproducible deployments
- Provider configuration for both LocalStack and AWS environments

#### 4. Resource Modules Required
- **S3 Module**: Bucket configuration, lifecycle policies, notifications
- **Kinesis Firehose Module**: Stream configuration, buffering, partitioning
- **EventBridge Module**: Event rules and targets
- **SQS Module**: Queue configuration with dead letter queue
- **IAM Module**: Roles and policies for service integration

## Implementation Steps

## Implementation Steps

### Phase 1: Terraform Infrastructure Setup
1. Initialize Terraform project with modular structure
2. Configure LocalStack and AWS provider endpoints
3. Deploy infrastructure to LocalStack for testing
4. Verify resource creation and configuration

### Phase 2: Integration Testing
1. Test data flow from Firehose to S3
2. Verify S3 event notifications trigger SQS messages
3. Validate file naming and partitioning patterns
4. Test error handling and dead letter queue functionality

### Phase 3: Production Deployment
1. Configure AWS credentials and remote state
2. Deploy infrastructure to AWS environments
3. Verify production resource configuration
4. Implement monitoring and alerting

### Phase 4: Automation and CI/CD
1. Set up automated testing pipeline
2. Implement infrastructure validation
3. Configure deployment workflows
4. Establish operational procedures

## Testing Strategy

### Terraform-Specific Testing Requirements
1. **Infrastructure Validation**: Terraform validation, formatting, and plan verification
2. **Environment Parity**: Ensure LocalStack and AWS deployments are identical
3. **Integration Testing**: End-to-end data flow testing across environments
4. **Compliance Testing**: Security and cost optimization validation

### Testing Tools and Frameworks
- **Terratest**: Infrastructure testing framework
- **Kitchen-Terraform**: Test Kitchen integration for Terraform
- **Checkov**: Static analysis for Terraform security compliance

## Sample Data Format

### Input JSON (from API Gateway/EKS)
```json
{
  "timestamp": 1731091615562,
  "bssid": "b8:f8:53:c0:1e:ff",
  "ssid": "Sweethome",
  "rssi": -58,
  "location": {
    "latitude": 40.6768816,
    "longitude": -74.416391,
    "accuracy": 100.0
  },
  "device_info": {
    "model": "SM-A536V",
    "manufacturer": "samsung"
  }
}
```

### Output File Format (S3)
- **Format**: Line-delimited JSON (NDJSON)
- **Compression**: GZIP (optional)
- **Encoding**: UTF-8

## Monitoring and Troubleshooting

### Key Metrics to Monitor
- Firehose delivery success rate
- S3 put operations
- SQS message count
- Error rates across services

### Common Issues
1. **IAM Permission Errors**: Verify role policies
2. **Firehose Buffer Issues**: Check buffer size/time settings
3. **S3 Event Delivery**: Ensure proper event configuration
4. **SQS Message Processing**: Monitor dead letter queue

### Logging Configuration
- Enable CloudWatch Logs for Firehose
- Set up S3 access logging
- Configure SQS CloudWatch metrics

## Testing Strategy

### Unit Tests
- Individual service configurations
- IAM policy validation
- Event routing verification

### Integration Tests
- End-to-end data flow
- Error handling scenarios
- Performance under load

### Test Data Sets
- Use provided sample WiFi scan data
- Create synthetic data for volume testing
- Test edge cases and malformed data

## Security Considerations

### Data Protection
- Encrypt S3 data at rest
- Use secure transmission (HTTPS)
- Implement proper access controls

### Access Management
- Principle of least privilege
- Regular credential rotation
- Audit logging enabled

## Performance Optimization

### Firehose Tuning
- Optimize buffer size based on data volume
- Consider parallel processing for high throughput
- Monitor and adjust compression settings

### S3 Optimization
- Use appropriate storage classes
- Implement lifecycle policies
- Optimize prefix distribution

## Terraform Deployment Commands

### LocalStack Development
```bash
# Quick development cycle
make localstack-up        # Start LocalStack
make terraform-plan-local # Plan changes
make terraform-apply-local # Apply changes
make test-integration     # Run integration tests
make localstack-down      # Clean up
```

### Production Deployment
```bash
# Production deployment
make terraform-plan-prod  # Plan production changes
make terraform-apply-prod # Apply to production (requires approval)
```

### Makefile Example
```makefile
.PHONY: localstack-up localstack-down terraform-plan-local terraform-apply-local

localstack-up:
	docker run -d --name localstack -p 4566:4566 localstack/localstack

localstack-down:
	docker stop localstack && docker rm localstack

terraform-plan-local:
	cd terraform && terraform plan -var-file="environments/localstack.tfvars"

terraform-apply-local:
	cd terraform && terraform apply -var-file="environments/localstack.tfvars" -auto-approve

terraform-plan-prod:
	cd terraform && terraform plan -var-file="environments/prod.tfvars"

terraform-apply-prod:
	cd terraform && terraform apply -var-file="environments/prod.tfvars"

test-integration:
	./scripts/test-data-flow.sh
```

## Benefits of Terraform Approach

### Development Benefits
- **Rapid Iteration**: Quick infrastructure changes and testing
- **Consistent Environments**: Same infrastructure across dev/staging/prod
- **Version Control**: Infrastructure changes tracked in Git
- **Collaboration**: Team can review infrastructure changes

### Production Benefits
- **Reliability**: Tested infrastructure patterns
- **Scalability**: Easy to modify and scale resources
- **Compliance**: Infrastructure as code enables compliance tracking
- **Disaster Recovery**: Quick infrastructure reconstruction

### Cost Benefits
- **Resource Management**: Easy cleanup of unused resources
- **Environment Optimization**: Different configurations per environment
- **Cost Tracking**: Tag-based cost allocation

### Firehose Costs
- Data ingestion volume
- Data transformation costs
- Delivery attempts

### S3 Costs
- Storage costs
- Request costs
- Data transfer costs

### EventBridge/SQS Costs
- Event processing
- Message delivery
- Queue storage

## Next Steps After Implementation

1. **Monitoring Setup**: Implement comprehensive monitoring
2. **Alerting**: Set up alerts for failures
3. **Scaling**: Plan for increased data volumes
4. **Backup/Recovery**: Implement data backup strategies
5. **Documentation**: Create operational runbooks

## Resources and References

## Resources and References

### Terraform Resources
- [Terraform AWS Provider Documentation](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [Terraform Best Practices](https://www.terraform.io/docs/cloud/guides/recommended-practices/index.html)
- [AWS Terraform Modules](https://github.com/terraform-aws-modules)

### LocalStack Documentation
- [LocalStack Kinesis Firehose](https://docs.localstack.cloud/user-guide/aws/kinesis-firehose/)
- [LocalStack S3 Events](https://docs.localstack.cloud/user-guide/aws/s3/)
- [LocalStack EventBridge](https://docs.localstack.cloud/user-guide/aws/events/)
- [LocalStack Terraform Integration](https://docs.localstack.cloud/user-guide/integrations/terraform/)

### AWS Documentation
- [Kinesis Data Firehose Developer Guide](https://docs.aws.amazon.com/kinesis/latest/dev/)
- [S3 Event Notifications](https://docs.aws.amazon.com/AmazonS3/latest/userguide/notification-how-to.html)
- [EventBridge User Guide](https://docs.aws.amazon.com/eventbridge/latest/userguide/)
- [Terraform on AWS](https://aws.amazon.com/blogs/apn/terraform-beyond-the-basics-with-aws/)

### Testing and Automation
- [Terratest Documentation](https://terratest.gruntwork.io/)
- [Kitchen-Terraform](https://newcontext-oss.github.io/kitchen-terraform/)
- [Checkov by Bridgecrew](https://www.checkov.io/)

This requirements document provides the comprehensive foundation needed to implement the Firehose ingestion pipeline using Terraform with LocalStack for development and AWS for production, following the architecture patterns from your WiFi localization system.