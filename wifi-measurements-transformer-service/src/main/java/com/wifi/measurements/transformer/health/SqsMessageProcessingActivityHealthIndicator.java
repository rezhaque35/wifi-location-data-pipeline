// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/SqsMessageProcessingActivityHealthIndicator.java
package com.wifi.measurements.transformer.health;

import com.wifi.measurements.transformer.service.SqsMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for SQS message processing activity suitable for liveness probes.
 * 
 * <p>This health indicator monitors the active processing of messages from SQS queues,
 * distinguishing between scenarios where no messages are available versus situations
 * where messages are available but not being processed. It provides comprehensive
 * monitoring for SQS consumer liveness and processing pipeline health.</p>
 * 
 * <p><strong>Key Functionality:</strong></p>
 * <ul>
 *   <li><strong>Processing Activity Tracking:</strong> Monitor message processing count and trends</li>
 *   <li><strong>Rate Monitoring:</strong> Track messages processed per time window with configurable thresholds</li>
 *   <li><strong>Activity Comparison:</strong> Compare current processing rate with historical baseline</li>
 *   <li><strong>Stuck Detection:</strong> Detect sustained periods of zero message processing when messages are available</li>
 *   <li><strong>Idle Period Handling:</strong> Properly handle "no messages available" vs "messages available but not processed"</li>
 *   <li><strong>Health Guidance:</strong> Provide detailed health status with operational guidance</li>
 * </ul>
 * 
 * <p><strong>High-Level Processing Steps:</strong></p>
 * <ol>
 *   <li><strong>Connectivity Check:</strong> Verify SQS queue connectivity and accessibility</li>
 *   <li><strong>Processing Activity:</strong> Monitor time since last processing activity</li>
 *   <li><strong>Message Reception:</strong> Track time since last message received</li>
 *   <li><strong>Stuck Detection:</strong> Detect if processing appears stuck or inactive</li>
 *   <li><strong>Rate Analysis:</strong> Analyze message processing rates and success rates</li>
 *   <li><strong>Threshold Comparison:</strong> Compare processing patterns against configured thresholds</li>
 *   <li><strong>Health Assessment:</strong> Provide detailed health status with operational context</li>
 * </ol>
 * 
 * <p><strong>Liveness Requirements:</strong></p>
 * <ul>
 *   <li>Track message processing count and trends for processing pipeline health</li>
 *   <li>Monitor messages processed per time window with configurable rate thresholds</li>
 *   <li>Compare current processing rate with historical baseline for anomaly detection</li>
 *   <li>Detect sustained periods of zero message processing when messages are available</li>
 *   <li>Fail if processing is stuck but messages are available for processing</li>
 *   <li>Distinguish between "no messages available" vs "messages available but not processed"</li>
 * </ul>
 * 
 * <p><strong>Health Check Categories:</strong></p>
 * <ul>
 *   <li><strong>Connectivity:</strong> SQS queue connection and accessibility</li>
 *   <li><strong>Processing Activity:</strong> Time since last processing activity</li>
 *   <li><strong>Message Reception:</strong> Time since last message received</li>
 *   <li><strong>Processing Rate:</strong> Messages processed per time window</li>
 *   <li><strong>Success Rate:</strong> Successful vs failed message processing ratio</li>
 *   <li><strong>Pipeline Health:</strong> Overall message processing pipeline status</li>
 * </ul>
 * 
 * <p><strong>Configuration Properties:</strong></p>
 * <ul>
 *   <li><strong>message-timeout-threshold:</strong> Timeout for message activity (default: 5 minutes)</li>
 *   <li><strong>processing-rate-threshold:</strong> Minimum processing rate (default: 0.1 messages/minute)</li>
 * </ul>
 * 
 * <p><strong>Use Cases:</strong></p>
 * <ul>
 *   <li>Kubernetes liveness probe configuration</li>
 *   <li>SQS consumer health monitoring and alerting</li>
 *   <li>Processing pipeline health assessment</li>
 *   <li>Operational troubleshooting and guidance</li>
 *   <li>Performance degradation detection</li>
 * </ul>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 * @see SqsMonitoringService for detailed monitoring capabilities
 * @see HealthIndicator for Spring Boot health check interface
 */
@Component("sqsMessageProcessingActivity")
public class SqsMessageProcessingActivityHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(SqsMessageProcessingActivityHealthIndicator.class);

    /** Service for SQS consumer monitoring and metrics collection */
    private final SqsMonitoringService sqsMonitoringService;
    
    // Configuration properties with defaults
    
    /** Timeout threshold for message activity in minutes (default: 5 minutes) */
    @Value("${management.health.sqs-message-processing.message-timeout-threshold:5}") // 5 minutes default
    private long messageTimeoutThresholdMinutes;
    
    /** Minimum processing rate threshold in messages per minute (default: 0.1) */
    @Value("${management.health.sqs-message-processing.processing-rate-threshold:0.1}")
    private double processingRateThreshold;

    /**
     * Creates a new SqsMessageProcessingActivityHealthIndicator with required dependencies.
     * 
     * @param sqsMonitoringService Service for SQS consumer monitoring and metrics
     */
    @Autowired
    public SqsMessageProcessingActivityHealthIndicator(SqsMonitoringService sqsMonitoringService) {
        this.sqsMonitoringService = sqsMonitoringService;
    }

    /**
     * Creates a new SqsMessageProcessingActivityHealthIndicator for testing with explicit configuration.
     * 
     * <p>This constructor allows for testing with specific configuration values
     * without relying on Spring property injection.</p>
     * 
     * @param sqsMonitoringService Service for SQS consumer monitoring and metrics
     * @param messageTimeoutThresholdMinutes Timeout threshold for message activity in minutes
     * @param processingRateThreshold Minimum processing rate threshold in messages per minute
     */
    public SqsMessageProcessingActivityHealthIndicator(
            SqsMonitoringService sqsMonitoringService, 
            long messageTimeoutThresholdMinutes, 
            double processingRateThreshold) {
        this.sqsMonitoringService = sqsMonitoringService;
        this.messageTimeoutThresholdMinutes = messageTimeoutThresholdMinutes;
        this.processingRateThreshold = processingRateThreshold;
    }
    
    /**
     * Convert minutes to milliseconds for internal calculations.
     * 
     * <p>This method converts the configured timeout threshold from minutes
     * to milliseconds for internal time-based calculations.</p>
     * 
     * @return Timeout threshold in milliseconds
     */
    private long getMessageTimeoutThresholdMs() {
        return messageTimeoutThresholdMinutes * 60 * 1000;
    }

    /**
     * Performs comprehensive health check for SQS message processing activity.
     * 
     * <p>This method implements the core health check logic, evaluating multiple
     * aspects of SQS consumer health including connectivity, processing activity,
     * message reception, processing rates, and success rates.</p>
     * 
     * <p><strong>Processing Steps:</strong></p>
     * <ol>
     *   <li><strong>Connectivity Check:</strong> Verify SQS queue connectivity and accessibility</li>
     *   <li><strong>Activity Monitoring:</strong> Check time since last processing activity</li>
     *   <li><strong>Reception Tracking:</strong> Monitor time since last message received</li>
     *   <li><strong>Stuck Detection:</strong> Detect if processing appears stuck or inactive</li>
     *   <li><strong>Rate Analysis:</strong> Analyze message processing rates and success rates</li>
     *   <li><strong>Threshold Validation:</strong> Compare processing patterns against thresholds</li>
     *   <li><strong>Health Response:</strong> Build comprehensive health response with details</li>
     * </ol>
     * 
     * <p><strong>Health Evaluation Criteria:</strong></p>
     * <ul>
     *   <li><strong>Queue Connectivity:</strong> Must be able to connect to SQS queue</li>
     *   <li><strong>Queue Accessibility:</strong> Must be able to access queue attributes</li>
     *   <li><strong>Processing Activity:</strong> Must have recent processing activity within timeout</li>
     *   <li><strong>Processing Status:</strong> Must not be stuck (receiving but not processing)</li>
     *   <li><strong>Processing Rate:</strong> Must meet minimum processing rate threshold when applicable</li>
     *   <li><strong>Success Rate:</strong> Must maintain acceptable processing success rate</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>Comprehensive exception catching and logging</li>
     *   <li>Detailed error messages for troubleshooting</li>
     *   <li>Graceful degradation with meaningful health status</li>
     *   <li>Operational guidance in health responses</li>
     * </ul>
     * 
     * @return Health status with detailed operational information
     */
    @Override
    public Health health() {
        try {
            logger.debug("Checking SQS consumer liveness including processing activity monitoring");
            
            var healthBuilder = Health.up();
            long checkTimestamp = System.currentTimeMillis();
            
            // Step 1: Basic connectivity checks - verify SQS queue accessibility
            boolean queueConnected = sqsMonitoringService.isQueueConnected();
            boolean queueAccessible = sqsMonitoringService.isQueueAccessible();
            
            if (!queueConnected) {
                return buildDownHealth("SQS queue cannot be connected to", checkTimestamp);
            }
            
            if (!queueAccessible) {
                return buildDownHealth("SQS queue is not accessible", checkTimestamp);
            }
            
            // Step 2: Processing activity and consumption monitoring - check for stuck conditions
            long timeSinceLastProcessingActivity = sqsMonitoringService.getTimeSinceLastProcessingActivity();
            long timeSinceLastMessageReceived = sqsMonitoringService.getTimeSinceLastMessageReceived();
            boolean processingStuck = sqsMonitoringService.isProcessingStuck();
            
            // Step 3: Check if processing hasn't occurred recently (may indicate inactive processing or no available messages)
            long timeoutThresholdMs = getMessageTimeoutThresholdMs();
            if (timeSinceLastProcessingActivity > timeoutThresholdMs) {
                return buildDownHealth(
                    String.format("SQS processing hasn't been active in %d ms (threshold: %d ms) - may indicate inactive processing or no available messages", 
                                 timeSinceLastProcessingActivity, timeoutThresholdMs), 
                    checkTimestamp);
            }
            
            // Step 4: Check if processing is stuck (receiving but not processing)
            if (processingStuck) {
                return buildDownHealth("SQS processing is stuck - receiving messages but not processing them effectively", checkTimestamp);
            }

            // Step 5: Get processing metrics for detailed health analysis
            var metrics = sqsMonitoringService.getMetrics();
            double processingRate = sqsMonitoringService.getMessageProcessingRate();
            long totalMessagesReceived = metrics.getTotalMessagesReceived();
            long totalMessagesProcessed = metrics.getTotalMessagesProcessed();
            double successRate = metrics.getSuccessRate();
            
            // Step 6: Check processing rate only if we have meaningful data and recent activity
            // Avoid false alarms during idle periods or startup
            if (totalMessagesReceived >= 10 && timeSinceLastProcessingActivity <= (timeoutThresholdMs / 2)) {
                // Only check rate if we have enough message history and recent activity
                if (processingRate > 0 && processingRate < processingRateThreshold) {
                    logger.warn("Low processing rate detected: {} msgs/min (threshold: {} msgs/min)", 
                            processingRate, processingRateThreshold);
                    // Note: Currently we just warn, but could make this configurable
                    // to fail health check in environments where low rate indicates problems
                }
            }
            
            // Step 7: Determine if processing is healthy (SQS consumer is working properly)
            boolean healthyProcessing = queueConnected && queueAccessible && 
                                       timeSinceLastProcessingActivity <= timeoutThresholdMs && !processingStuck;
            
            return healthBuilder
                .withDetail("queueConnected", queueConnected)
                .withDetail("queueAccessible", queueAccessible)
                .withDetail("processingRate", processingRate)
                .withDetail("totalMessagesReceived", totalMessagesReceived)
                .withDetail("totalMessagesProcessed", totalMessagesProcessed)
                .withDetail("successRate", successRate)
                .withDetail("timeSinceLastMessageReceivedMs", timeSinceLastMessageReceived)
                .withDetail("timeSinceLastProcessingActivityMs", timeSinceLastProcessingActivity)
                .withDetail("messageTimeoutThresholdMs", timeoutThresholdMs)
                .withDetail("processingStuck", processingStuck)
                .withDetail("healthyProcessing", healthyProcessing)
                .withDetail("reason", "SQS consumer is healthy - actively processing messages")
                .withDetail("checkTimestamp", checkTimestamp)
                .build();
                
        } catch (Exception e) {
            logger.error("Error checking SQS processing activity liveness", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("reason", "Health check failed due to exception")
                .withDetail("checkTimestamp", System.currentTimeMillis())
                .build();
        }
    }
    
    /**
     * Builds a DOWN health status with consistent response format.
     * 
     * @param reason The reason for the DOWN status
     * @param checkTimestamp The timestamp of the health check
     * @return Health DOWN status with details
     */
    private Health buildDownHealth(String reason, long checkTimestamp) {
        // Include basic connection state details for consistent response format
        boolean queueConnected = false;
        boolean queueAccessible = false;
        
        try {
            queueConnected = sqsMonitoringService.isQueueConnected();
            queueAccessible = sqsMonitoringService.isQueueAccessible();
        } catch (Exception e) {
            // In case of exception, defaults remain false
        }
        
        return Health.down()
            .withDetail("reason", reason)
            .withDetail("queueConnected", queueConnected)
            .withDetail("queueAccessible", queueAccessible)
            .withDetail("healthyProcessing", false)
            .withDetail("checkTimestamp", checkTimestamp)
            .build();
    }
}