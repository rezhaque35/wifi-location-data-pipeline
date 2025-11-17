# WiFi AP Localization Service - Implementation Task Plan

## Current Implementation Status Summary

### ✅ COMPLETED (Phase 1-3 Foundation + Outlier Detection)
- **Project Setup**: Spring Boot 3.x with Java 21, Maven configuration, package structure
- **AWS Integration**: SQS message processing, DynamoDB read operations, health checks
- **Core Pipeline**: Main processing orchestrator (AccessPointLocationEstimator), message parsing, correlation IDs
- **Data Models**: WifiMeasurement, WifiAccessPointLocation, MessageMacAddress DTOs
- **Health Monitoring**: Comprehensive health indicators for SQS and DynamoDB
- **Outlier Detection**: Complete global and local (LOF) outlier detection with comprehensive testing

### 🚧 IN PROGRESS (Core Services)
- **SQS Processing**: ✅ Complete with long polling, batch processing, error handling
- **DynamoDB**: ✅ Read operations complete, ❌ Save operations (TODO in save method)
- **Athena Integration**: ❌ Not implemented (TODO in AccessPointMeasurementsLookUpService)

### ❌ PENDING (Algorithm Implementation)
- **Hotspot Detection**: Service created but implementation pending (WiFiHotspotDetectionService)
- **Post-Maturity Local Outlier Detection**: Statistical anomaly detection algorithms pending
- **Localization Algorithms**: Classes created but implementations pending (WCL, MLE, Bayesian)
- **Algorithm Selection**: Class created but implementation pending (AlgorithmSelector)

### 🔧 CONFIGURATION ISSUES
- **application.yml**: Duplicate AWS sections need fixing
- **Missing Dependencies**: Athena SDK, outlier detection libraries (ELKI/Smile)

### 📋 IMMEDIATE NEXT STEPS
1. Fix application.yml configuration
2. Add missing Athena SDK dependency
3. Implement AccessPointMeasurementsLookUpService
4. Implement DynamoDB save method
5. Implement Post-Maturity Statistical Outlier Detection (Phase 5.4)
6. Implement core localization algorithms (WCL, MLE, Bayesian)

---

## Phase 1: Project Setup and Infrastructure (Foundation)

### 1.1 Project Initialization
- [x] Create Spring Boot 3.x project with Java 21
- [x] Configure Maven/Gradle with required dependencies
- [x] Set up package structure (`com.wifi.ap.location.estimation`)
- [ ] Create application.yml with environment profiles (NEEDS FIXING - duplicate aws sections)
- [x] Configure logging with structured JSON format

### 1.2 AWS SDK Integration
- [x] Add AWS SDK v2 dependencies for SQS, S3, DynamoDB
- [ ] Add AWS SDK v2 dependencies for Athena (MISSING)
- [x] Create AWS configuration classes
- [x] Implement credentials provider configuration
- [x] Set up region configuration
- [x] Create service client beans for SQS and DynamoDB
- [ ] Create service client beans for Athena (MISSING)

### 1.3 LocalStack Development Environment
- [ ] Create docker-compose.yml for LocalStack
- [ ] Write setup script in `scripts/setup/local-aws-setup.sh`
- [ ] Create script to initialize SQS queue
- [ ] Create script to initialize DynamoDB table
- [ ] Create script to set up S3 buckets for Athena results
- [ ] Write test data generation script in `scripts/test/`

### 1.4 Basic Spring Configuration
- [ ] Configure Spring profiles (local, dev, staging, prod)
- [ ] Set up environment variable configuration
- [ ] Configure thread pools and executors
- [ ] Set up Spring scheduling for batch operations
- [ ] Create configuration properties classes

## Phase 2: Core AWS Service Integration

### 2.1 SQS Message Processing
- [x] Create SQS message listener component
- [x] Implement long polling configuration (20 seconds)
- [x] Set up batch message retrieval (10 messages - configurable)
- [x] Create MAC address validation
- [x] Implement message visibility timeout handling
- [x] Set up Dead Letter Queue configuration
- [x] Create message deletion after successful processing
- [ ] Write unit tests for SQS processing

### 2.2 DynamoDB Integration
- [x] Define AP state entity model
- [x] Create DynamoDB repository interface
- [ ] Implement batch write buffer (25 items max) - TODO in save method
- [ ] Create flush mechanism (buffer full or 5-second timeout)
- [ ] Implement backpressure handling
- [ ] Set up exponential backoff for throttling
- [x] Create state retrieval methods
- [ ] Implement version tracking for updates
- [ ] Write unit tests for DynamoDB operations

### 2.3 Athena Query Integration
- [ ] Create Athena query builder for measurements (TODO in AccessPointMeasurementsLookUpService)
- [ ] Implement async query execution
- [ ] Create query status polling mechanism
- [ ] Implement result retrieval from S3
- [ ] Set up result file cleanup
- [ ] Create retry logic for failed queries
- [ ] Handle query timeouts
- [ ] Write unit tests for Athena operations

### 2.4 Integration Testing for AWS Services
- [ ] Create integration tests with LocalStack
- [ ] Test SQS message flow
- [ ] Test DynamoDB batch operations
- [ ] Test Athena query execution
- [ ] Verify error handling across services

## Phase 3: Data Processing Pipeline

### 3.1 Main Processing Orchestrator
- [x] Create main processing service class (AccessPointLocationEstimator)
- [x] Implement processing pipeline coordinator
- [x] Set up correlation ID generation
- [x] Create processing state management
- [x] Implement error handling and recovery
- [x] Set up metrics collection points
- [ ] Write unit tests for orchestrator

### 3.2 Measurement Data Processor
- [x] Create measurement data model classes (WifiMeasurement)
- [ ] Implement Athena result parser (TODO in AccessPointMeasurementsLookUpService)
- [ ] Create data validation logic
- [ ] Filter measurements by lookback window
- [ ] Separate CONNECTED vs SCAN measurements
- [ ] Calculate initial statistics
- [ ] Write unit tests for data processing

## Phase 4: AP Classification Algorithms

### 4.1 Mobile Hotspot Detection
- [x] Create MobileHotspotDetector class (WiFiHotspotDetectionService)
- [x] Implement spatial distribution calculator (Geometric centroid calculation)
- [x] Calculate standard deviation of locations (Using Haversine distance formula)
- [x] Apply 500-meter threshold check (Configurable threshold implementation)
- [x] Validate minimum 20 measurements requirement (Input validation)
- [x] Create detection result model (WiFiHotspotResult)
- [x] Write comprehensive unit tests (15 tests covering all scenarios)

### 4.2 AP Relocation Detection
- [ ] Create APRelocationDetector class (NOT IMPLEMENTED)
- [ ] Implement change-point detection algorithm
- [ ] Calculate Mahalanobis distance
- [ ] Apply 100-meter threshold check
- [ ] Create time series analysis logic
- [ ] Implement state reset mechanism
- [ ] (Optional) Add bi-modal clustering if needed
- [ ] Write unit tests for relocation detection

## Phase 5: Outlier Detection Implementation

### 5.1 Global Outlier Detection
- [x] Create GlobalOutlierDetector class (Implemented with Spring @Component)
- [x] Add Apache Commons Math dependency
- [x] Implement geometric centroid calculation (Haversine distance formula)
- [x] Calculate distances from centroid (Spherical distance calculations)
- [x] Implement MAD (Median Absolute Deviation) calculation (DescriptiveStatistics)
- [x] Apply 3x MAD threshold (Configurable threshold implementation)
- [x] Create outlier flagging mechanism (Returns Set of outlier IDs)
- [x] Track outlier event IDs (Measurement ID tracking)
- [x] Write unit tests with edge cases (11 comprehensive tests)

### 5.2 Local Outlier Detection (Pre-Maturity - LOF Algorithm)
- [x] Create LocalOutlierDetector class (Using Smile library KDTree)
- [x] Add ELKI or Smile library dependency (Smile library integration)
- [x] Implement LOF (Local Outlier Factor) algorithm (Complete LOF implementation)
- [x] Set up minimum 5 points requirement (MIN_POINTS_FOR_LOF constant)
- [x] Apply 1.5 LOF threshold (Configurable LOF_THRESHOLD)
- [x] Combine with global outlier results (Set union in filter service)
- [x] Create outlier summary report (OutlierDetectionResult DTO)
- [x] Write unit tests for LOF (Integrated with main filter service tests)
- [x] **NEW**: Implement optimized LOF with distance matrix pre-computation
- [x] **NEW**: Add adaptive k-selection algorithm (√n rule with bounds)
- [x] **NEW**: Implement priority queue optimization for k-nearest neighbors
- [x] **NEW**: Add comprehensive JavaDoc documentation with mathematical explanations
- [x] **NEW**: Create comprehensive unit test suite (17 tests covering all scenarios)

### 5.3 Outlier Integration
- [x] Create outlier detection pipeline (APLocationMeasurementsFilterService - Fully implemented)
- [x] Implement sequential outlier processing (Global → Local → Combine)
- [x] Create outlier metrics collection (OutlierDetectionResult with comprehensive metrics)
- [x] Log outlier patterns for analysis (Pattern analysis and RSSI/accuracy logging)
- [x] Write integration tests (14 comprehensive tests covering all scenarios)
- [x] **NEW**: Refactor configuration to use Spring Boot @ConfigurationProperties
- [x] **NEW**: Implement clean dependency injection for all outlier detection components
- [x] **NEW**: Add performance optimizations (distance matrix, priority queue, array caching)

### 5.4 Post-Maturity Local Outlier Detection (Statistical Algorithms)
- [ ] Create StatisticalAnomalyOutlierDetector class (NOT IMPLEMENTED)
- [ ] Implement Mahalanobis distance calculation for multivariate outlier detection
- [ ] Add representative sampling algorithm for large datasets (N > 100)
- [ ] Implement spatial boundary threshold detection (95th percentile)
- [ ] Create virtual neighbor generation for sparse regions
- [ ] Apply statistical significance testing (p-value thresholds)
- [ ] Implement adaptive threshold based on data distribution
- [ ] Create confidence interval calculations for outlier scores
- [ ] Add performance optimizations for large datasets (N > 1000)
- [ ] Write comprehensive unit tests for statistical algorithms
- [ ] Create integration tests with real-world large datasets
- [ ] Document mathematical foundations and parameter tuning

## Phase 6: Localization Algorithms

### 6.1 Data Maturity Assessment
- [ ] Create DataMaturityAssessor class (NOT IMPLEMENTED)
- [ ] Implement measurement counting logic
- [ ] Define tier boundaries (Bootstrap/Mature/Highly Mature)
- [ ] Create algorithm selection logic
- [ ] Write unit tests for tier assignment

### 6.2 Weighted Centroid Localization (WCL)
- [x] Create WeightedCentroidLocalizer class (WeightedCentroidLocalization - TODO implementation)
- [ ] Implement weight calculation formula
- [ ] Apply quality_weight differentiation (2.0 vs 1.0)
- [ ] Implement situation goodness checks
- [ ] Check minimum spatial spread (10m)
- [ ] Verify angular coverage (90°)
- [ ] Create confidence score calculation
- [ ] Write comprehensive unit tests

### 6.3 Maximum Likelihood Estimation (MLE)
- [x] Create MaximumLikelihoodEstimator class (MaximumLikelihoodEstimation - TODO implementation)
- [x] Add Apache Commons Math for optimization
- [ ] Implement log-distance path loss model
- [ ] Create frequency-specific parameters
- [ ] Differentiate CONNECTED vs SCAN processing
- [ ] Implement gradient descent optimization
- [ ] Set convergence threshold
- [ ] Add iteration limits
- [ ] Write unit tests with various scenarios

### 6.4 Bayesian Inference
- [x] Create BayesianLocalizer class (BayesianInferenceLocalization - TODO implementation)
- [ ] Implement prior integration from DynamoDB state
- [ ] Create likelihood calculation
- [ ] Implement posterior calculation
- [ ] Update covariance matrix
- [ ] Calculate confidence bounds
- [ ] Write unit tests for Bayesian logic

### 6.5 Algorithm Integration
- [x] Create algorithm factory/selector (AlgorithmSelector - TODO implementation)
- [ ] Implement dynamic algorithm execution
- [x] Create unified result model (AlgorithmSelections)
- [ ] Add algorithm performance metrics
- [ ] Write integration tests for all algorithms

## Phase 7: State Persistence and Management

### 7.1 State Model Enhancement
- [ ] Enhance DynamoDB state model with all required fields
- [ ] Add calculation_state nested object
- [ ] Add quality_metrics nested object
- [ ] Implement covariance matrix storage
- [ ] Add TTL configuration

### 7.2 Batch Writing Optimization
- [ ] Implement write buffer with concurrent queue
- [ ] Create scheduled flush mechanism
- [ ] Implement transactional writes
- [ ] Add write failure recovery
- [ ] Create write metrics collection
- [ ] Write performance tests

## Phase 8: Health Monitoring and Observability

### 8.1 Health Check Implementation
- [ ] Create health controller with `/health/ready` endpoint
- [ ] Create `/health/live` endpoint
- [ ] Implement SQS connectivity check
- [ ] Implement Athena availability check
- [ ] Implement DynamoDB connection check
- [ ] Check S3 bucket accessibility
- [ ] Verify thread pool health
- [ ] Monitor memory usage
- [ ] Write health check tests

### 8.2 Metrics and Monitoring
- [ ] Add Micrometer dependency
- [ ] Configure Prometheus metrics export
- [ ] Create business metrics (APs processed, algorithms used)
- [ ] Create performance metrics (query time, write latency)
- [ ] Create error metrics (failures, throttling)
- [ ] Set up custom metric collectors
- [ ] Write metrics verification tests

### 8.3 Structured Logging
- [ ] Configure JSON logging format
- [ ] Implement MDC for correlation IDs
- [ ] Add contextual logging throughout pipeline
- [ ] Create audit logging for state changes
- [ ] Configure log levels per package
- [ ] Test log output format

## Phase 9: Error Handling and Resilience

### 9.1 Error Handling Framework
- [ ] Create custom exception hierarchy
- [ ] Implement global exception handler
- [ ] Add retry logic with exponential backoff
- [ ] Create circuit breaker for AWS services
- [ ] Implement fallback mechanisms
- [ ] Write error handling tests

### 9.2 Recovery Mechanisms
- [ ] Implement message reprocessing logic
- [ ] Create state recovery procedures
- [ ] Add data consistency checks
- [ ] Implement partial failure handling
- [ ] Write recovery scenario tests

## Phase 10: Performance Optimization

### 10.1 Performance Tuning
- [ ] Profile memory usage
- [ ] Optimize Athena query performance
- [ ] Tune batch sizes for optimal throughput
- [ ] Optimize DynamoDB write patterns
- [ ] Implement connection pooling
- [ ] Add caching where beneficial

### 10.2 Load Testing
- [ ] Create load test scenarios
- [ ] Test with 100 APs/minute throughput
- [ ] Verify 5-10 second processing time per AP
- [ ] Test memory usage under load (< 2GB)
- [ ] Verify batch write efficiency (> 80%)
- [ ] Test horizontal scaling capabilities

## Phase 11: Integration Testing

### 11.1 End-to-End Testing
- [ ] Create complete pipeline integration tests
- [ ] Test with various data scenarios
- [ ] Test mobile hotspot detection flow
- [ ] Test relocation detection flow
- [ ] Test all algorithm paths
- [ ] Verify state persistence

### 11.2 LocalStack Integration Testing
- [ ] Set up LocalStack test environment
- [ ] Create test data fixtures
- [ ] Run full pipeline with LocalStack
- [ ] Test error scenarios
- [ ] Verify metrics and logging

## Phase 12: Documentation and Deployment Preparation

### 12.1 Documentation
- [ ] Create README with setup instructions
- [ ] Document API endpoints
- [ ] Create configuration guide
- [ ] Document algorithm implementations
- [ ] Create troubleshooting guide
- [ ] Add inline code documentation

### 12.2 Deployment Artifacts
- [ ] Create Dockerfile
- [ ] Create Kubernetes manifests
- [ ] Set up ConfigMaps
- [ ] Configure Secrets management
- [ ] Create deployment scripts
- [ ] Document deployment procedures

## Completion Checklist

### Critical Path Items (Must Complete)
- [ ] SQS message processing working
- [ ] Athena queries retrieving data
- [x] **COMPLETED**: Global and Local (LOF) outlier detection working
- [ ] At least WCL algorithm implemented
- [ ] DynamoDB state persistence working
- [ ] Basic health checks implemented
- [ ] LocalStack testing functional

### Quality Gates
- [ ] Unit test coverage > 80%
- [ ] All integration tests passing
- [ ] Performance targets met (100 APs/minute)
- [ ] Memory usage < 2GB verified
- [ ] No critical security vulnerabilities
- [ ] Documentation complete

### Sign-off Criteria
- [ ] Code review completed
- [ ] Performance testing passed
- [ ] LocalStack environment fully functional
- [ ] Health endpoints responding correctly
- [ ] Metrics being collected
- [ ] Ready for deployment

## Implementation Notes

### Priority Order
1. **High Priority**: Core pipeline (Phases 1-3)
2. **Medium Priority**: Algorithms (Phases 4-6)
3. **Lower Priority**: Monitoring and optimization (Phases 7-10)

### Risk Areas Requiring Extra Attention
- DynamoDB batch write backpressure handling
- Athena query timeout management
- Memory management with large result sets
- Outlier detection accuracy
- Algorithm convergence issues

### Dependencies Between Phases
- Phase 2 must complete before Phase 3
- Phase 3 must complete before Phases 4-6
- Phases 4-6 can be developed in parallel
- Phase 7 depends on Phases 4-6
- Phases 8-9 can start after Phase 3

### Estimated Timeline
- **Week 1-2**: Phases 1-2 (Setup and AWS Integration)
- **Week 3**: Phase 3 (Processing Pipeline)
- **Week 4-5**: Phases 4-6 (Algorithms)
- **Week 6**: Phases 7-8 (State and Monitoring)
- **Week 7**: Phases 9-10 (Resilience and Performance)
- **Week 8**: Phases 11-12 (Testing and Documentation)

---

**This task plan provides a comprehensive roadmap for implementing the WiFi AP Localization Service. Check off tasks as completed and track progress through each phase.**