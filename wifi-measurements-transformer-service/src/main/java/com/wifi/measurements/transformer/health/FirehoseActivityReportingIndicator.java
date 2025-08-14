// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/FirehoseActivityReportingIndicator.java
package com.wifi.measurements.transformer.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.measurements.transformer.service.FirehoseMonitoringService;

/**
 * Activity reporting indicator for Firehose delivery that never marks service as DOWN.
 *
 * <p>This indicator provides comprehensive monitoring and reporting of Firehose delivery
 * activity, delivery rates, success rates, and operational metrics. Unlike traditional
 * health indicators, this component always returns UP status and focuses on providing
 * detailed operational visibility rather than failing health checks.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>Always UP:</strong> Never marks service as DOWN regardless of activity</li>
 *   <li><strong>Detailed Reporting:</strong> Provides comprehensive delivery metrics</li>
 *   <li><strong>Real-time Data:</strong> Reports current delivery state and trends</li>
 *   <li><strong>Operational Insights:</strong> Includes recommendations and status analysis</li>
 * </ul>
 *
 * <p><strong>Reported Metrics:</strong>
 * <ul>
 *   <li><strong>Delivery Activity:</strong> Time since last delivery attempt</li>
 *   <li><strong>Delivery Success:</strong> Time since last successful delivery</li>
 *   <li><strong>Delivery Rates:</strong> Batches delivered per minute</li>
 *   <li><strong>Success Rates:</strong> Delivery success percentage</li>
 *   <li><strong>Operational Status:</strong> Whether delivery appears stuck or healthy</li>
 * </ul>
 */
@Component("firehoseActivityReporting")
public class FirehoseActivityReportingIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(FirehoseActivityReportingIndicator.class);

    private final FirehoseMonitoringService firehoseMonitoringService;

    // Configuration properties with defaults (for reporting purposes)
    @Value("${management.health.firehose-delivery.delivery-timeout-threshold:10}")
    private long deliveryTimeoutThresholdMinutes;

    @Value("${management.health.firehose-delivery.delivery-rate-threshold:0.05}")
    private double deliveryRateThreshold;

    public FirehoseActivityReportingIndicator(FirehoseMonitoringService firehoseMonitoringService) {
        this.firehoseMonitoringService = firehoseMonitoringService;
    }

    @Override
    public Health health() {
        try {
            logger.debug("Generating Firehose delivery activity report");
            long checkTimestamp = System.currentTimeMillis();

            // Get comprehensive delivery metrics
            FirehoseMonitoringService.FirehoseMetrics metrics = firehoseMonitoringService.getMetrics();
            long timeSinceLastDeliveryAttempt = firehoseMonitoringService.getTimeSinceLastDeliveryAttempt();
            long timeSinceLastSuccessfulDelivery = firehoseMonitoringService.getTimeSinceLastSuccessfulDelivery();
            double deliveryRate = firehoseMonitoringService.getDeliveryRate();
            boolean deliveryStuck = firehoseMonitoringService.isDeliveryStuck();
            boolean streamAccessible = firehoseMonitoringService.isStreamAccessible();
            boolean streamReady = firehoseMonitoringService.isStreamReady();

            // Calculate operational status for reporting
            long timeoutThresholdMs = deliveryTimeoutThresholdMinutes * 60 * 1000;
            boolean recentActivity = timeSinceLastDeliveryAttempt <= timeoutThresholdMs;
            boolean healthyRate = deliveryRate >= deliveryRateThreshold || metrics.getTotalBatchesDelivered() < 5;
            
            // Determine activity status (for reporting, not health)
            String activityStatus = determineActivityStatus(
                streamAccessible, streamReady, recentActivity, deliveryStuck, 
                healthyRate, metrics.getTotalBatchesDelivered());
            
            String operationalRecommendation = generateRecommendation(
                streamAccessible, streamReady, recentActivity, deliveryStuck, 
                healthyRate, deliveryRate, timeSinceLastDeliveryAttempt);

            // Always return UP - this is a reporting indicator
            return Health.up()
                .withDetail("activityStatus", activityStatus)
                .withDetail("operationalRecommendation", operationalRecommendation)
                // Stream status
                .withDetail("streamAccessible", streamAccessible)
                .withDetail("streamReady", streamReady)
                // Delivery metrics
                .withDetail("deliveryRate", deliveryRate)
                .withDetail("totalBatchesDelivered", metrics.getTotalBatchesDelivered())
                .withDetail("totalBatchesSucceeded", metrics.getTotalBatchesSucceeded())
                .withDetail("totalRecordsDelivered", metrics.getTotalRecordsDelivered())
                .withDetail("deliverySuccessRate", metrics.getDeliverySuccessRate())
                .withDetail("consecutiveStreamCheckFailures", metrics.getConsecutiveStreamCheckFailures())
                .withDetail("consecutiveDeliveryFailures", metrics.getConsecutiveDeliveryFailures())
                // Timing information
                .withDetail("timeSinceLastDeliveryAttemptMs", timeSinceLastDeliveryAttempt)
                .withDetail("timeSinceLastSuccessfulDeliveryMs", timeSinceLastSuccessfulDelivery)
                .withDetail("deliveryTimeoutThresholdMs", timeoutThresholdMs)
                // Status flags
                .withDetail("recentActivity", recentActivity)
                .withDetail("deliveryStuck", deliveryStuck)
                .withDetail("healthyDeliveryRate", healthyRate)
                .withDetail("lastSuccessfulStreamCheck", metrics.getLastSuccessfulStreamCheck())
                // Metadata
                .withDetail("checkTimestamp", checkTimestamp)
                .withDetail("reason", "Firehose delivery activity reporting - always UP for monitoring purposes")
                .build();

        } catch (Exception e) {
            logger.warn("Error generating Firehose delivery activity report", e);
            // Even on error, return UP with error details for monitoring
            return Health.up()
                .withDetail("activityStatus", "ERROR")
                .withDetail("operationalRecommendation", "Check service logs for error details")
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail("checkTimestamp", System.currentTimeMillis())
                .withDetail("reason", "Firehose delivery activity reporting error - returning UP for monitoring")
                .build();
        }
    }

    /**
     * Determines the current activity status for reporting.
     */
    private String determineActivityStatus(boolean streamAccessible, boolean streamReady, 
                                         boolean recentActivity, boolean deliveryStuck, 
                                         boolean healthyRate, long totalBatches) {
        if (!streamAccessible) {
            return "STREAM_INACCESSIBLE";
        } else if (!streamReady) {
            return "STREAM_NOT_READY";
        } else if (deliveryStuck) {
            return "STUCK";
        } else if (!recentActivity) {
            return totalBatches == 0 ? "IDLE_NO_DATA" : "IDLE_TIMEOUT";
        } else if (!healthyRate) {
            return "SLOW_DELIVERY";
        } else {
            return "ACTIVE";
        }
    }

    /**
     * Generates operational recommendations based on current status.
     */
    private String generateRecommendation(boolean streamAccessible, boolean streamReady, 
                                        boolean recentActivity, boolean deliveryStuck, 
                                        boolean healthyRate, double deliveryRate, 
                                        long timeSinceLastAttempt) {
        if (!streamAccessible) {
            return "Firehose stream is not accessible - check AWS connectivity and permissions";
        } else if (!streamReady) {
            return "Firehose stream is not ready - check stream status in AWS console";
        } else if (deliveryStuck) {
            return "Delivery appears stuck - check for delivery failures and stream configuration";
        } else if (!recentActivity) {
            long inactiveMinutes = timeSinceLastAttempt / (60 * 1000);
            if (inactiveMinutes > 60) {
                return "No delivery activity for " + inactiveMinutes + " minutes - check data availability and processing pipeline";
            } else {
                return "Recent inactivity - normal if no data to deliver";
            }
        } else if (!healthyRate) {
            return String.format("Delivery rate (%.2f batches/min) below threshold - check for performance issues", deliveryRate);
        } else {
            return "Delivery activity appears normal";
        }
    }
}
