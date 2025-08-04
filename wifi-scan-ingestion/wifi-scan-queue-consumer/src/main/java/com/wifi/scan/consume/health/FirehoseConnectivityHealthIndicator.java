package com.wifi.scan.consume.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.scan.consume.service.BatchFirehoseMessageService;
import com.wifi.scan.consume.service.BatchFirehoseMessageService.BatchFirehoseMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator for AWS Kinesis Data Firehose batch connectivity.
 * 
 * This health indicator monitors the connectivity and operational status of the
 * AWS Kinesis Data Firehose service used for delivering processed WiFi scan data.
 * It provides comprehensive health information including connectivity status,
 * delivery stream accessibility, and operational metrics.
 * 
 * Key Functionality:
 * - Tests connectivity to AWS Firehose delivery stream
 * - Monitors delivery stream accessibility and configuration
 * - Provides detailed operational metrics and statistics
 * - Reports batch processing success rates and throughput
 * - Integrates with Spring Boot Actuator health endpoints
 * - Supports Kubernetes readiness probes and monitoring
 * 
 * High-Level Steps:
 * 1. Test connectivity to AWS Firehose delivery stream
 * 2. Retrieve operational metrics from batch service
 * 3. Calculate success rates and performance statistics
 * 4. Build comprehensive health response with detailed metrics
 * 5. Handle connectivity failures with appropriate error details
 * 6. Log health check results for monitoring and debugging
 * 
 * The indicator is part of the readiness probe system and helps ensure that
 * the application is fully operational and capable of delivering data to
 * downstream systems before accepting traffic.
 * 
 * @see com.wifi.scan.consume.service.BatchFirehoseMessageService
 * @see org.springframework.boot.actuate.health.HealthIndicator
 */
@Slf4j
@Component("firehoseConnectivity")
public class FirehoseConnectivityHealthIndicator implements HealthIndicator {

    /** Service for batch Firehose operations and metrics */
    private final BatchFirehoseMessageService batchFirehoseService;

    /**
     * Constructor for Firehose connectivity health indicator.
     * 
     * Initializes the health indicator with the batch Firehose service
     * that provides connectivity testing and operational metrics.
     * 
     * @param batchFirehoseService Service for batch Firehose operations
     */
    @Autowired
    public FirehoseConnectivityHealthIndicator(BatchFirehoseMessageService batchFirehoseService) {
        this.batchFirehoseService = batchFirehoseService;
    }

    /**
     * Performs health check for AWS Firehose connectivity and operations.
     * 
     * This method tests the connectivity to the AWS Firehose delivery stream
     * and provides comprehensive health information including operational
     * metrics, success rates, and performance statistics.
     * 
     * High-Level Steps:
     * 1. Test connectivity to AWS Firehose delivery stream
     * 2. Retrieve operational metrics if connectivity is successful
     * 3. Calculate performance statistics (success rates, throughput)
     * 4. Build detailed health response with metrics and status
     * 5. Handle connectivity failures with appropriate error reporting
     * 6. Include timestamp for health check monitoring
     * 
     * The health check provides detailed information useful for monitoring
     * system performance, troubleshooting connectivity issues, and ensuring
     * data delivery reliability.
     * 
     * @return Health object with connectivity status and operational metrics
     */
    @Override
    public Health health() {
        try {
            // Test batch Firehose connectivity to delivery stream
            boolean isConnected = batchFirehoseService.testConnectivity();
            
            if (isConnected) {
                // Get batch Firehose metrics for comprehensive health information
                BatchFirehoseMetrics metrics = batchFirehoseService.getMetrics();
                
                // Build detailed health response with operational metrics
                return Health.up()
                        .withDetail("status", "Batch Firehose delivery stream accessible")
                        .withDetail("deliveryStreamName", metrics.getDeliveryStreamName())
                        .withDetail("successfulBatches", metrics.getSuccessfulBatches())
                        .withDetail("failedBatches", metrics.getFailedBatches())
                        .withDetail("totalMessagesProcessed", metrics.getTotalMessagesProcessed())
                        .withDetail("totalBytesProcessed", metrics.getTotalBytesProcessed())
                        .withDetail("batchSuccessRate", String.format("%.2f%%", metrics.getBatchSuccessRate() * 100))
                        .withDetail("averageMessagesPerBatch", String.format("%.1f", metrics.getAverageMessagesPerBatch()))
                        .withDetail("lastCheckTime", System.currentTimeMillis())
                        .build();
            } else {
                // Report connectivity failure with detailed error information
                return Health.down()
                        .withDetail("status", "Batch Firehose delivery stream not accessible")
                        .withDetail("reason", "Failed to describe delivery stream")
                        .withDetail("lastCheckTime", System.currentTimeMillis())
                        .build();
            }
            
        } catch (Exception e) {
            // Log and report unexpected errors during health check
            log.error("Batch Firehose connectivity health check failed", e);
            return Health.down()
                    .withDetail("status", "Batch Firehose connectivity check failed")
                    .withDetail("error", e.getMessage())
                    .withDetail("lastCheckTime", System.currentTimeMillis())
                    .build();
        }
    }
} 