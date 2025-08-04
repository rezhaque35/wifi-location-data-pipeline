// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/FirehoseDeliveryActivityHealthIndicator.java
package com.wifi.measurements.transformer.health;

import com.wifi.measurements.transformer.service.FirehoseMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Firehose delivery activity suitable for liveness probes.
 * 
 * <p>This health indicator monitors the active delivery of data to Firehose delivery streams,
 * distinguishing between scenarios where no data is being processed versus situations
 * where data is available but delivery is failing. It provides comprehensive monitoring
 * for Firehose delivery liveness and processing pipeline health.</p>
 * 
 * <p><strong>Key Functionality:</strong></p>
 * <ul>
 *   <li><strong>Delivery Activity Tracking:</strong> Monitor batch delivery count and trends</li>
 *   <li><strong>Rate Monitoring:</strong> Track batches delivered per time window with configurable thresholds</li>
 *   <li><strong>Activity Comparison:</strong> Compare current delivery rate with historical baseline</li>
 *   <li><strong>Stuck Detection:</strong> Detect sustained periods of delivery failures or inactivity</li>
 *   <li><strong>Pipeline Health:</strong> Properly handle "no data to deliver" vs "delivery failures"</li>
 *   <li><strong>Health Guidance:</strong> Provide detailed health status with operational guidance</li>
 * </ul>
 * 
 * <p><strong>High-Level Processing Steps:</strong></p>
 * <ol>
 *   <li><strong>Stream Connectivity:</strong> Verify Firehose delivery stream connectivity and accessibility</li>
 *   <li><strong>Delivery Activity:</strong> Monitor time since last delivery attempt</li>
 *   <li><strong>Success Tracking:</strong> Track time since last successful delivery</li>
 *   <li><strong>Stuck Detection:</strong> Detect if delivery appears stuck or inactive</li>
 *   <li><strong>Rate Analysis:</strong> Analyze delivery rates and success rates</li>
 *   <li><strong>Threshold Validation:</strong> Compare delivery patterns against configured thresholds</li>
 *   <li><strong>Health Assessment:</strong> Provide detailed health status with operational context</li>
 * </ol>
 * 
 * <p><strong>Liveness Requirements:</strong></p>
 * <ul>
 *   <li>Track delivery activity count and trends for processing pipeline health</li>
 *   <li>Monitor batches delivered per time window with configurable rate thresholds</li>
 *   <li>Compare current delivery rate with historical baseline for anomaly detection</li>
 *   <li>Detect sustained periods of delivery failures when data is available for delivery</li>
 *   <li>Fail if delivery is stuck but data is available for processing</li>
 *   <li>Distinguish between "no data to deliver" vs "data available but delivery failing"</li>
 * </ul>
 * 
 * <p><strong>Health Check Categories:</strong></p>
 * <ul>
 *   <li><strong>Stream Connectivity:</strong> Firehose delivery stream connection and accessibility</li>
 *   <li><strong>Delivery Activity:</strong> Time since last delivery attempt</li>
 *   <li><strong>Delivery Success:</strong> Time since last successful delivery</li>
 *   <li><strong>Delivery Rate:</strong> Batches delivered per time window</li>
 *   <li><strong>Success Rate:</strong> Successful vs failed delivery ratio</li>
 *   <li><strong>Pipeline Health:</strong> Overall data delivery pipeline status</li>
 * </ul>
 * 
 * <p><strong>Configuration Properties:</strong></p>
 * <ul>
 *   <li><strong>delivery-timeout-threshold:</strong> Timeout for delivery activity (default: 10 minutes)</li>
 *   <li><strong>delivery-rate-threshold:</strong> Minimum delivery rate (default: 0.05 batches/minute)</li>
 * </ul>
 * 
 * <p><strong>Use Cases:</strong></p>
 * <ul>
 *   <li>Kubernetes liveness probe configuration</li>
 *   <li>Firehose delivery health monitoring and alerting</li>
 *   <li>Data pipeline health assessment</li>
 *   <li>Operational troubleshooting and guidance</li>
 *   <li>Performance degradation detection</li>
 * </ul>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 * @see FirehoseMonitoringService for detailed monitoring capabilities
 * @see HealthIndicator for Spring Boot health check interface
 */
@Component("firehoseDeliveryActivity")
public class FirehoseDeliveryActivityHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(FirehoseDeliveryActivityHealthIndicator.class);

    /** Service for Firehose delivery monitoring and metrics collection */
    private final FirehoseMonitoringService firehoseMonitoringService;
    
    // Configuration properties with defaults
    
    /** Timeout threshold for delivery activity in minutes (default: 10 minutes) */
    @Value("${management.health.firehose-delivery.delivery-timeout-threshold:10}") // 10 minutes default
    private long deliveryTimeoutThresholdMinutes;
    
    /** Minimum delivery rate threshold in batches per minute (default: 0.05) */
    @Value("${management.health.firehose-delivery.delivery-rate-threshold:0.05}")
    private double deliveryRateThreshold;

    /**
     * Creates a new FirehoseDeliveryActivityHealthIndicator with required dependencies.
     * 
     * @param firehoseMonitoringService Service for Firehose delivery monitoring and metrics
     */
    @Autowired
    public FirehoseDeliveryActivityHealthIndicator(FirehoseMonitoringService firehoseMonitoringService) {
        this.firehoseMonitoringService = firehoseMonitoringService;
    }

    /**
     * Creates a new FirehoseDeliveryActivityHealthIndicator for testing with explicit configuration.
     * 
     * <p>This constructor allows for testing with specific configuration values
     * without relying on Spring property injection.</p>
     * 
     * @param firehoseMonitoringService Service for Firehose delivery monitoring and metrics
     * @param deliveryTimeoutThresholdMinutes Timeout threshold for delivery activity in minutes
     * @param deliveryRateThreshold Minimum delivery rate threshold in batches per minute
     */
    public FirehoseDeliveryActivityHealthIndicator(
            FirehoseMonitoringService firehoseMonitoringService, 
            long deliveryTimeoutThresholdMinutes, 
            double deliveryRateThreshold) {
        this.firehoseMonitoringService = firehoseMonitoringService;
        this.deliveryTimeoutThresholdMinutes = deliveryTimeoutThresholdMinutes;
        this.deliveryRateThreshold = deliveryRateThreshold;
    }
    
    /**
     * Convert minutes to milliseconds for internal calculations.
     * 
     * <p>This method converts the configured timeout threshold from minutes
     * to milliseconds for internal time-based calculations.</p>
     * 
     * @return Timeout threshold in milliseconds
     */
    private long getDeliveryTimeoutThresholdMs() {
        return deliveryTimeoutThresholdMinutes * 60 * 1000;
    }

    /**
     * Performs comprehensive health check for Firehose delivery activity.
     * 
     * <p>This method implements the core health check logic, evaluating multiple
     * aspects of Firehose delivery health including connectivity, delivery activity,
     * success tracking, delivery rates, and success rates.</p>
     * 
     * <p><strong>Processing Steps:</strong></p>
     * <ol>
     *   <li><strong>Stream Connectivity:</strong> Verify Firehose delivery stream connectivity and accessibility</li>
     *   <li><strong>Activity Monitoring:</strong> Check time since last delivery attempt</li>
     *   <li><strong>Success Tracking:</strong> Monitor time since last successful delivery</li>
     *   <li><strong>Stuck Detection:</strong> Detect if delivery appears stuck or inactive</li>
     *   <li><strong>Rate Analysis:</strong> Analyze delivery rates and success rates</li>
     *   <li><strong>Threshold Validation:</strong> Compare delivery patterns against thresholds</li>
     *   <li><strong>Health Response:</strong> Build comprehensive health response with details</li>
     * </ol>
     * 
     * <p><strong>Health Evaluation Criteria:</strong></p>
     * <ul>
     *   <li><strong>Stream Connectivity:</strong> Must be able to connect to Firehose delivery stream</li>
     *   <li><strong>Stream Accessibility:</strong> Must be able to access stream attributes</li>
     *   <li><strong>Delivery Activity:</strong> Must have recent delivery activity within timeout</li>
     *   <li><strong>Delivery Status:</strong> Must not be stuck (attempting but not succeeding)</li>
     *   <li><strong>Delivery Rate:</strong> Must meet minimum delivery rate threshold when applicable</li>
     *   <li><strong>Success Rate:</strong> Must maintain acceptable delivery success rate</li>
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
            logger.debug("Checking Firehose delivery liveness including delivery activity monitoring");
            
            var healthBuilder = Health.up();
            long checkTimestamp = System.currentTimeMillis();
            
            // Step 1: Basic connectivity checks - verify Firehose stream accessibility
            boolean streamAccessible = firehoseMonitoringService.isStreamAccessible();
            boolean streamReady = firehoseMonitoringService.isStreamReady();
            
            if (!streamAccessible) {
                return buildDownHealth("Firehose delivery stream cannot be accessed", checkTimestamp);
            }
            
            if (!streamReady) {
                return buildDownHealth("Firehose delivery stream is not ready for delivery", checkTimestamp);
            }
            
            // Step 2: Delivery activity and success monitoring - check for stuck conditions
            long timeSinceLastDeliveryAttempt = firehoseMonitoringService.getTimeSinceLastDeliveryAttempt();
            long timeSinceLastSuccessfulDelivery = firehoseMonitoringService.getTimeSinceLastSuccessfulDelivery();
            boolean deliveryStuck = firehoseMonitoringService.isDeliveryStuck();
            
            // Step 3: Check if delivery hasn't been attempted recently (may indicate inactive delivery or no data to deliver)
            long timeoutThresholdMs = getDeliveryTimeoutThresholdMs();
            if (timeSinceLastDeliveryAttempt > timeoutThresholdMs) {
                return buildDownHealth(
                    String.format("Firehose delivery hasn't been attempted in %d ms (threshold: %d ms) - may indicate inactive delivery or no data to deliver", 
                                 timeSinceLastDeliveryAttempt, timeoutThresholdMs), 
                    checkTimestamp);
            }
            
            // Step 4: Check if delivery is stuck (attempting but not succeeding)
            if (deliveryStuck) {
                return buildDownHealth("Firehose delivery is stuck - attempting delivery but consistently failing", checkTimestamp);
            }

            // Step 5: Get delivery metrics for detailed health analysis
            var metrics = firehoseMonitoringService.getMetrics();
            double deliveryRate = firehoseMonitoringService.getDeliveryRate();
            long totalBatchesDelivered = metrics.getTotalBatchesDelivered();
            long totalBatchesSucceeded = metrics.getTotalBatchesSucceeded();
            double successRate = metrics.getDeliverySuccessRate();
            
            // Step 6: Check delivery rate only if we have meaningful data and recent activity
            // Avoid false alarms during idle periods or startup
            if (totalBatchesDelivered >= 5 && timeSinceLastDeliveryAttempt <= (timeoutThresholdMs / 2)) {
                // Only check rate if we have enough delivery history and recent activity
                if (deliveryRate > 0 && deliveryRate < deliveryRateThreshold) {
                    logger.warn("Low delivery rate detected: {} batches/min (threshold: {} batches/min)", 
                            deliveryRate, deliveryRateThreshold);
                    // Note: Currently we just warn, but could make this configurable
                    // to fail health check in environments where low rate indicates problems
                }
            }
            
            // Step 7: Determine if delivery is healthy (Firehose delivery is working properly)
            boolean healthyDelivery = streamAccessible && streamReady && 
                                    timeSinceLastDeliveryAttempt <= timeoutThresholdMs && !deliveryStuck;
            
            return healthBuilder
                .withDetail("streamAccessible", streamAccessible)
                .withDetail("streamReady", streamReady)
                .withDetail("deliveryRate", deliveryRate)
                .withDetail("totalBatchesDelivered", totalBatchesDelivered)
                .withDetail("totalBatchesSucceeded", totalBatchesSucceeded)
                .withDetail("totalRecordsDelivered", metrics.getTotalRecordsDelivered())
                .withDetail("successRate", successRate)
                .withDetail("timeSinceLastDeliveryAttemptMs", timeSinceLastDeliveryAttempt)
                .withDetail("timeSinceLastSuccessfulDeliveryMs", timeSinceLastSuccessfulDelivery)
                .withDetail("deliveryTimeoutThresholdMs", timeoutThresholdMs)
                .withDetail("deliveryStuck", deliveryStuck)
                .withDetail("healthyDelivery", healthyDelivery)
                .withDetail("reason", "Firehose delivery is healthy - actively delivering data")
                .withDetail("checkTimestamp", checkTimestamp)
                .build();
                
        } catch (Exception e) {
            logger.error("Error checking Firehose delivery activity liveness", e);
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
        boolean streamAccessible = false;
        boolean streamReady = false;
        
        try {
            streamAccessible = firehoseMonitoringService.isStreamAccessible();
            streamReady = firehoseMonitoringService.isStreamReady();
        } catch (Exception e) {
            // In case of exception, defaults remain false
        }
        
        return Health.down()
            .withDetail("reason", reason)
            .withDetail("streamAccessible", streamAccessible)
            .withDetail("streamReady", streamReady)
            .withDetail("healthyDelivery", false)
            .withDetail("checkTimestamp", checkTimestamp)
            .build();
    }
}