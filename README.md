# WiFi Location Data Pipeline

A comprehensive data processing pipeline for WiFi access point localization using crowdsourced WiFi scan data. This project implements a robust, scalable architecture for processing, filtering, and analyzing WiFi measurement data to determine access point locations.

## 🏗️ Architecture Overview

The pipeline consists of multiple microservices working together to process WiFi scan data:

```
WiFi Scan Data → S3 → SQS → Transformer → Firehose → Data Lake → Positioning Service
```

### Core Components

1. **WiFi Scan Ingestion** - Handles data ingestion from various sources
2. **WiFi Measurements Transformer** - Processes and filters raw WiFi scan data
3. **WiFi Positioning Service** - Calculates access point locations using positioning algorithms
4. **WiFi Positioning Integration Service** - Orchestrates the positioning workflow

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (for LocalStack)
- AWS CLI configured (for production deployment)

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd wifi-location-data-pipeline
   ```

2. **Setup LocalStack for local AWS services**
   ```bash
   cd wifi-scan-ingestion/firehose-ingestion/scripts
   ./setup-localstack.sh
   ```

3. **Build all services**
   ```bash
   # Build WiFi Measurements Transformer
   cd wifi-measurements-transformer-service
   mvn clean install
   
   # Build WiFi Positioning Service
   cd ../wifi-positioning-service
   mvn clean install
   
   # Build WiFi Positioning Integration Service
   cd ../wifi-positioning-integration-service
   mvn clean install
   ```

4. **Run services locally**
   ```bash
   # Start WiFi Measurements Transformer
   cd wifi-measurements-transformer-service
   mvn spring-boot:run
   
   # Start WiFi Positioning Service (in another terminal)
   cd wifi-positioning-service
   mvn spring-boot:run
   ```

## 📁 Project Structure

```
wifi-location-data-pipeline/
├── documents/                          # Project documentation
├── wifi-measurements-transformer-service/  # Data processing service
├── wifi-positioning-service/           # Access point positioning service
├── wifi-positioning-integration-service/   # Integration orchestration
└── wifi-scan-ingestion/               # Data ingestion components
    ├── firehose-ingestion/            # AWS Firehose setup
    └── wifi-scan-queue-consumer/      # Queue processing service
```

## 🔧 Configuration

### Environment Variables

Each service can be configured using environment variables or application properties:

- `AWS_REGION` - AWS region for services
- `AWS_ACCESS_KEY_ID` - AWS access key (for local development)
- `AWS_SECRET_ACCESS_KEY` - AWS secret key (for local development)
- `SQS_QUEUE_URL` - SQS queue URL for message processing
- `FIREHOSE_DELIVERY_STREAM` - Kinesis Firehose delivery stream name

### Application Properties

Each service has its own `application.yml` configuration file with service-specific settings.

## 🧪 Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific service tests
cd wifi-measurements-transformer-service
mvn test

# Run integration tests
mvn verify
```

### Test Coverage

The project maintains high test coverage with comprehensive unit and integration tests. Coverage reports are generated in the `target/site/jacoco/` directory for each service.

## 📊 Data Processing Pipeline

### Phase 1: Data Ingestion
- WiFi scan data uploaded to S3
- EventBridge triggers SQS message processing
- Data validation and format checking

### Phase 2: Data Transformation
- RSSI filtering and quality assessment
- Coordinate validation and GPS accuracy checks
- BSSID format validation
- Data enrichment and normalization

### Phase 3: Data Delivery
- Processed data sent to Kinesis Firehose
- Data stored in S3 data lake
- Real-time streaming capabilities

### Phase 4: Access Point Positioning
- Log-distance path loss algorithm implementation
- Multi-point triangulation
- Confidence scoring and quality metrics
- Location estimation with uncertainty bounds

## 🏛️ Architecture Patterns

- **Event-Driven Architecture** - Using SQS and EventBridge for loose coupling
- **Microservices** - Independent, deployable services
- **Data Pipeline** - Stream processing with filtering and transformation
- **Domain-Driven Design** - Clear separation of business domains
- **Test-Driven Development** - Comprehensive test coverage

## 🔒 Security

- AWS IAM roles and policies for service authentication
- Input validation and sanitization
- Secure configuration management
- Audit logging and monitoring

## 📈 Monitoring and Health Checks

Each service includes health indicators:
- AWS service connectivity checks
- Database connection monitoring
- Custom business logic health indicators
- Metrics collection and reporting

## 🚀 Deployment

### AWS Deployment

```bash
# Deploy infrastructure
cd wifi-scan-ingestion/firehose-ingestion/terraform
terraform init
terraform plan
terraform apply

# Deploy services
cd wifi-positioning-service
./deploy-aws.sh
```

### Docker Deployment

```bash
# Build Docker images
docker build -t wifi-transformer-service .
docker build -t wifi-positioning-service .

# Run with Docker Compose
docker-compose up -d
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For support and questions:
- Create an issue in the repository
- Check the documentation in the `documents/` directory
- Review the test examples for usage patterns

## 📚 Additional Resources

- [WiFi Positioning Algorithm Documentation](documents/A%20Framework%20for%20Robust%20and%20Iterative%20Access%20Point%20Localization%20from%20Crowdsourced%20Wi-Fi%20Data.md)
- [Architecture Diagram](documents/Access%20point%20localization%20data%20pipeline%20architecture.png)
- [Task Plans](documents/*-task-plan.md)
- [Requirements Documents](documents/*-requirements.md) 