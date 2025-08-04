package com.wifi.scan.consume.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wifi.scan.consume.service.BatchFirehoseMessageService;
import com.wifi.scan.consume.service.BatchFirehoseMessageService.BatchFirehoseMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for exposing batch Firehose delivery metrics and connectivity status.
 * 
 * This controller provides comprehensive REST endpoints for monitoring AWS Kinesis Data Firehose
 * batch delivery performance, connectivity status, and operational health. It serves as the
 * primary interface for external monitoring systems to track Firehose delivery metrics.
 * 
 * Key Functionality:
 * - Real-time batch delivery metrics retrieval
 * - Connectivity testing and status reporting
 * - Metrics summary for quick health assessment
 * - Metrics reset for testing and monitoring purposes
 * - Health status determination based on success rates
 * 
 * High-Level Processing Steps:
 * 1. Receive HTTP requests for metrics or connectivity testing
 * 2. Retrieve current metrics from BatchFirehoseMessageService
 * 3. Transform raw metrics into appropriate response formats
 * 4. Apply business logic for health determination
 * 5. Return structured JSON responses with appropriate HTTP status codes
 * 
 * Monitoring Integration:
 * - Compatible with Prometheus, Grafana, and other monitoring tools
 * - Provides both detailed and summary metrics endpoints
 * - Supports automated health checks and alerting
 * 
 * @see BatchFirehoseMessageService
 * @see BatchFirehoseMetrics
 */
@Slf4j
@RestController
@RequestMapping("/api/metrics/firehose")
public class FirehoseMetricsController {

    /** Service responsible for batch Firehose message delivery and metrics collection */
    private final BatchFirehoseMessageService batchFirehoseService;

    /**
     * Creates a new FirehoseMetricsController with the required dependencies.
     * 
     * @param batchFirehoseService Service for batch Firehose operations and metrics
     */
    @Autowired
    public FirehoseMetricsController(BatchFirehoseMessageService batchFirehoseService) {
        this.batchFirehoseService = batchFirehoseService;
    }

    /**
     * Get detailed batch Firehose metrics for comprehensive monitoring.
     * 
     * This endpoint provides complete metrics including batch delivery counts,
     * success rates, bytes processed, and detailed performance statistics.
     * Suitable for detailed monitoring dashboards and performance analysis.
     * 
     * Processing Steps:
     * 1. Retrieve current metrics from BatchFirehoseMessageService
     * 2. Log debug information for monitoring purposes
     * 3. Return complete metrics object with HTTP 200 status
     * 4. Handle exceptions with appropriate error responses
     * 
     * @return ResponseEntity containing detailed BatchFirehoseMetrics or error response
     */
    @GetMapping
    public ResponseEntity<BatchFirehoseMetrics> getFirehoseMetrics() {
        try {
            // Retrieve current metrics from the service
            BatchFirehoseMetrics metrics = batchFirehoseService.getMetrics();
            
            // Log debug information for monitoring and troubleshooting
            log.debug("Batch Firehose metrics requested: {} successful, {} failed batches", 
                    metrics.getSuccessfulBatches(), metrics.getFailedBatches());
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error retrieving batch Firehose metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get batch Firehose metrics summary for quick health assessment.
     * 
     * This endpoint provides a simplified metrics summary including key performance
     * indicators and health status determination. Suitable for quick health checks
     * and automated monitoring systems.
     * 
     * Processing Steps:
     * 1. Retrieve detailed metrics from the service
     * 2. Calculate summary statistics and health indicators
     * 3. Determine health status based on success rate threshold (95%)
     * 4. Build and return summary response
     * 5. Handle exceptions with appropriate error responses
     * 
     * Health Determination Logic:
     * - Healthy: Batch success rate > 95%
     * - Unhealthy: Batch success rate ≤ 95%
     * 
     * @return ResponseEntity containing FirehoseMetricsSummary or error response
     */
    @GetMapping("/summary")
    public ResponseEntity<FirehoseMetricsSummary> getFirehoseMetricsSummary() {
        try {
            // Retrieve detailed metrics from the service
            BatchFirehoseMetrics metrics = batchFirehoseService.getMetrics();
            
            // Build summary with calculated health indicators
            FirehoseMetricsSummary summary = FirehoseMetricsSummary.builder()
                    .deliveryStreamName(metrics.getDeliveryStreamName())
                    .totalBatches(metrics.getSuccessfulBatches() + metrics.getFailedBatches())
                    .successfulBatches(metrics.getSuccessfulBatches())
                    .failedBatches(metrics.getFailedBatches())
                    .batchSuccessRate(metrics.getBatchSuccessRate())
                    .totalMessagesProcessed(metrics.getTotalMessagesProcessed())
                    .totalBytesProcessed(metrics.getTotalBytesProcessed())
                    .averageMessagesPerBatch(metrics.getAverageMessagesPerBatch())
                    .isHealthy(metrics.getBatchSuccessRate() > 0.95) // Consider healthy if >95% batch success rate
                    .build();
            
            log.debug("Batch Firehose metrics summary requested");
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error retrieving batch Firehose metrics summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Reset batch Firehose metrics for testing and monitoring purposes.
     * 
     * This endpoint allows external systems to reset accumulated metrics,
     * useful for testing scenarios, monitoring resets, and performance baselines.
     * 
     * Processing Steps:
     * 1. Call service method to reset all accumulated metrics
     * 2. Log the reset operation for audit purposes
     * 3. Return confirmation message with HTTP 200 status
     * 4. Handle exceptions with appropriate error responses
     * 
     * Use Cases:
     * - Testing and development environments
     * - Monitoring system resets
     * - Performance baseline establishment
     * - Troubleshooting scenarios
     * 
     * @return ResponseEntity containing reset confirmation or error response
     */
    @PostMapping("/reset")
    public ResponseEntity<String> resetFirehoseMetrics() {
        try {
            // Reset all accumulated metrics in the service
            batchFirehoseService.resetMetrics();
            
            // Log the reset operation for audit and monitoring purposes
            log.info("Batch Firehose metrics reset via API endpoint");
            
            return ResponseEntity.ok("Batch Firehose metrics reset successfully");
        } catch (Exception e) {
            log.error("Error resetting batch Firehose metrics", e);
            return ResponseEntity.internalServerError().body("Failed to reset batch Firehose metrics");
        }
    }

    /**
     * Test batch Firehose connectivity and return status information.
     * 
     * This endpoint performs an active connectivity test to AWS Kinesis Data Firehose
     * and returns detailed status information including connection status,
     * delivery stream information, and test timestamp.
     * 
     * Processing Steps:
     * 1. Perform connectivity test through the service
     * 2. Capture current timestamp for status tracking
     * 3. Build connectivity status response with test results
     * 4. Return structured status information
     * 5. Handle exceptions with appropriate error responses
     * 
     * Connectivity Test Information:
     * - Connection status (connected/disconnected)
     * - Delivery stream name and configuration
     * - Last test timestamp for monitoring
     * - Status message with additional details
     * 
     * @return ResponseEntity containing FirehoseConnectivityStatus or error response
     */
    @GetMapping("/connectivity")
    public ResponseEntity<FirehoseConnectivityStatus> testFirehoseConnectivity() {
        try {
            // Perform connectivity test and get delivery stream information
            boolean isConnected = batchFirehoseService.testConnectivity();
            BatchFirehoseMetrics metrics = batchFirehoseService.getMetrics();
            
            // Build connectivity status response
            FirehoseConnectivityStatus status = FirehoseConnectivityStatus.builder()
                    .connected(isConnected)
                    .deliveryStreamName(metrics.getDeliveryStreamName())
                    .lastTestTime(System.currentTimeMillis())
                    .message(isConnected ? "Successfully connected to Firehose" : "Failed to connect to Firehose")
                    .build();
            
            log.debug("Firehose connectivity test performed: connected={}", isConnected);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error testing Firehose connectivity", e);
            
            // Return error status with exception information
            FirehoseConnectivityStatus errorStatus = FirehoseConnectivityStatus.builder()
                    .connected(false)
                    .deliveryStreamName("unknown")
                    .lastTestTime(System.currentTimeMillis())
                    .message("Error testing connectivity: " + e.getMessage())
                    .build();
            
            return ResponseEntity.ok(errorStatus);
        }
    }

    /**
     * Summary metrics response for quick health assessment and monitoring.
     * 
     * This class provides a simplified view of Firehose metrics including
     * key performance indicators and health status determination.
     * 
     * Key Features:
     * - Essential metrics for health monitoring
     * - Calculated success rates and averages
     * - Boolean health status for automated systems
     * - Delivery stream identification
     * 
     * High-Level Data Structure:
     * 1. Basic identification (delivery stream name)
     * 2. Batch delivery statistics (total, successful, failed)
     * 3. Performance metrics (success rate, messages, bytes)
     * 4. Health determination (boolean status)
     */
    @lombok.Builder
    @lombok.Data
    public static class FirehoseMetricsSummary {
        /** Name of the AWS Kinesis Data Firehose delivery stream */
        private String deliveryStreamName;
        
        /** Total number of batches processed (successful + failed) */
        private long totalBatches;
        
        /** Number of successfully delivered batches */
        private long successfulBatches;
        
        /** Number of failed batch deliveries */
        private long failedBatches;
        
        /** Calculated batch success rate (0.0 to 1.0) */
        private double batchSuccessRate;
        
        /** Total number of individual messages processed */
        private long totalMessagesProcessed;
        
        /** Total bytes processed across all batches */
        private long totalBytesProcessed;
        
        /** Average number of messages per batch */
        private double averageMessagesPerBatch;
        
        /** Health status determined by success rate threshold (>95%) */
        private boolean isHealthy;
    }

    /**
     * Connectivity status response for Firehose connection testing.
     * 
     * This class provides detailed connectivity information including
     * connection status, delivery stream details, and test metadata.
     * 
     * Key Features:
     * - Boolean connection status
     * - Delivery stream identification
     * - Test timestamp for monitoring
     * - Descriptive status message
     * 
     * High-Level Data Structure:
     * 1. Connection status (connected/disconnected)
     * 2. Delivery stream information
     * 3. Test metadata (timestamp, message)
     */
    @lombok.Builder
    @lombok.Data
    public static class FirehoseConnectivityStatus {
        /** Whether the connection to Firehose is currently active */
        private boolean connected;
        
        /** Name of the AWS Kinesis Data Firehose delivery stream */
        private String deliveryStreamName;
        
        /** Timestamp of the last connectivity test (milliseconds since epoch) */
        private long lastTestTime;
        
        /** Descriptive message about the connectivity status or error */
        private String message;
    }
} 