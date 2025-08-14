// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/SqsActivityReportingIndicator.java
package com.wifi.measurements.transformer.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.measurements.transformer.service.SqsMonitoringService;

/**
 * Activity reporting indicator for SQS message processing that never marks service as DOWN.
 *
 * <p>This indicator provides comprehensive monitoring and reporting of SQS message processing
 * activity, processing rates, success rates, and operational metrics. Unlike traditional
 * health indicators, this component always returns UP status and focuses on providing
 * detailed operational visibility rather than failing health checks.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>Always UP:</strong> Never marks service as DOWN regardless of activity</li>
 *   <li><strong>Detailed Reporting:</strong> Provides comprehensive activity metrics</li>
 *   <li><strong>Real-time Data:</strong> Reports current processing state and trends</li>
 *   <li><strong>Operational Insights:</strong> Includes recommendations and status analysis</li>
 * </ul>
 *
 * <p><strong>Reported Metrics:</strong>
 * <ul>
 *   <li><strong>Processing Activity:</strong> Time since last processing activity</li>
 *   <li><strong>Message Reception:</strong> Time since last message received</li>
 *   <li><strong>Processing Rates:</strong> Messages processed per minute</li>
 *   <li><strong>Success Rates:</strong> Processing success percentage</li>
 *   <li><strong>Operational Status:</strong> Whether processing appears stuck or healthy</li>
 * </ul>
 */
@Component("sqsActivityReporting")
public class SqsActivityReportingIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(SqsActivityReportingIndicator.class);

    private final SqsMonitoringService sqsMonitoringService;

    // Configuration properties with defaults (for reporting purposes)
    @Value("${management.health.sqs-message-processing.message-timeout-threshold:5}")
    private long messageTimeoutThresholdMinutes;

    @Value("${management.health.sqs-message-processing.processing-rate-threshold:0.1}")
    private double processingRateThreshold;

    public SqsActivityReportingIndicator(SqsMonitoringService sqsMonitoringService) {
        this.sqsMonitoringService = sqsMonitoringService;
    }

    @Override
    public Health health() {
        try {
            logger.debug("Generating SQS activity report");
            long checkTimestamp = System.currentTimeMillis();

            // Get comprehensive activity metrics
            SqsMonitoringService.SqsMetrics metrics = sqsMonitoringService.getMetrics();
            long timeSinceLastProcessingActivity = sqsMonitoringService.getTimeSinceLastProcessingActivity();
            long timeSinceLastMessageReceived = sqsMonitoringService.getTimeSinceLastMessageReceived();
            double processingRate = sqsMonitoringService.getMessageProcessingRate();
            boolean processingStuck = sqsMonitoringService.isProcessingStuck();

            // Calculate operational status for reporting
            long timeoutThresholdMs = messageTimeoutThresholdMinutes * 60 * 1000;
            boolean recentActivity = timeSinceLastProcessingActivity <= timeoutThresholdMs;
            boolean healthyRate = processingRate >= processingRateThreshold || metrics.getTotalMessagesReceived() < 10;
            
            // Determine activity status (for reporting, not health)
            String activityStatus = determineActivityStatus(
                recentActivity, processingStuck, healthyRate, metrics.getTotalMessagesReceived());
            
            String operationalRecommendation = generateRecommendation(
                recentActivity, processingStuck, healthyRate, processingRate, timeSinceLastProcessingActivity);

            // Always return UP - this is a reporting indicator
            return Health.up()
                .withDetail("activityStatus", activityStatus)
                .withDetail("operationalRecommendation", operationalRecommendation)
                // Processing metrics
                .withDetail("processingRate", processingRate)
                .withDetail("totalMessagesReceived", metrics.getTotalMessagesReceived())
                .withDetail("totalMessagesProcessed", metrics.getTotalMessagesProcessed())
                .withDetail("successRate", metrics.getSuccessRate())
                .withDetail("consecutiveConnectionFailures", metrics.getConsecutiveConnectionFailures())
                // Timing information
                .withDetail("timeSinceLastMessageReceivedMs", timeSinceLastMessageReceived)
                .withDetail("timeSinceLastProcessingActivityMs", timeSinceLastProcessingActivity)
                .withDetail("messageTimeoutThresholdMs", timeoutThresholdMs)
                // Status flags
                .withDetail("recentActivity", recentActivity)
                .withDetail("processingStuck", processingStuck)
                .withDetail("healthyProcessingRate", healthyRate)
                .withDetail("lastSuccessfulConnection", 
                    metrics.getLastSuccessfulConnection() != null ? 
                    metrics.getLastSuccessfulConnection() : "Never")
                // Metadata
                .withDetail("checkTimestamp", checkTimestamp)
                .withDetail("reason", "SQS activity reporting - always UP for monitoring purposes")
                .build();

        } catch (Exception e) {
            logger.warn("Error generating SQS activity report", e);
            // Even on error, return UP with error details for monitoring
            return Health.up()
                .withDetail("activityStatus", "ERROR")
                .withDetail("operationalRecommendation", "Check service logs for error details")
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail("checkTimestamp", System.currentTimeMillis())
                .withDetail("reason", "SQS activity reporting error - returning UP for monitoring")
                .build();
        }
    }

    /**
     * Determines the current activity status for reporting.
     */
    private String determineActivityStatus(boolean recentActivity, boolean processingStuck, 
                                         boolean healthyRate, long totalMessages) {
        if (processingStuck) {
            return "STUCK";
        } else if (!recentActivity) {
            return totalMessages == 0 ? "IDLE_NO_DATA" : "IDLE_TIMEOUT";
        } else if (!healthyRate) {
            return "SLOW_PROCESSING";
        } else {
            return "ACTIVE";
        }
    }

    /**
     * Generates operational recommendations based on current status.
     */
    private String generateRecommendation(boolean recentActivity, boolean processingStuck, 
                                        boolean healthyRate, double processingRate, 
                                        long timeSinceLastActivity) {
        if (processingStuck) {
            return "Processing appears stuck - check for thread deadlocks or resource issues";
        } else if (!recentActivity) {
            long inactiveMinutes = timeSinceLastActivity / (60 * 1000);
            if (inactiveMinutes > 30) {
                return "No processing activity for " + inactiveMinutes + " minutes - check message availability and consumer health";
            } else {
                return "Recent inactivity - normal if no messages available";
            }
        } else if (!healthyRate) {
            return String.format("Processing rate (%.2f msg/min) below threshold - check for performance issues", processingRate);
        } else {
            return "Processing activity appears normal";
        }
    }
}
