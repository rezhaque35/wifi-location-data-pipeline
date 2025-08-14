// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/SqsConnectivityHealthIndicator.java
package com.wifi.measurements.transformer.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.measurements.transformer.service.SqsMonitoringService;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Health indicator for SQS connectivity that only marks service as DOWN 
 * when there's no connection to SQS.
 *
 * <p>This health indicator is designed to be used for service readiness checks and
 * only fails when SQS is genuinely unreachable. It does not fail for processing 
 * issues, activity timeouts, or other operational concerns - those are handled by 
 * separate reporting indicators.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>Connectivity Only:</strong> Only checks basic SQS connectivity</li>
 *   <li><strong>Cached Results:</strong> Uses caching to avoid excessive AWS API calls</li>
 *   <li><strong>Simple Criteria:</strong> UP if can connect to SQS, DOWN if cannot</li>
 *   <li><strong>Activity Reporting:</strong> Includes activity metrics for monitoring</li>
 * </ul>
 *
 * <p><strong>Health Status Criteria:</strong>
 * <ul>
 *   <li><strong>UP:</strong> Can connect to SQS and retrieve queue attributes</li>
 *   <li><strong>DOWN:</strong> Cannot connect to SQS (network/auth/config issues)</li>
 * </ul>
 */
@Component("sqsConnectivity")
public class SqsConnectivityHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(SqsConnectivityHealthIndicator.class);
    private static final String CACHE_KEY = "sqs-connectivity";

    private final SqsClient sqsClient;
    private final SqsMonitoringService sqsMonitoringService;
    private final HealthCheckCacheService cacheService;
    private final String queueUrl;

    public SqsConnectivityHealthIndicator(
            SqsClient sqsClient,
            SqsMonitoringService sqsMonitoringService,
            HealthCheckCacheService cacheService,
            @Value("#{@resolvedQueueUrl}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.sqsMonitoringService = sqsMonitoringService;
        this.cacheService = cacheService;
        this.queueUrl = queueUrl;
    }

    @Override
    public Health health() {
        try {
            logger.debug("Checking simplified SQS connectivity");
            long checkTimestamp = System.currentTimeMillis();

            // Use cached connectivity check to avoid excessive API calls
            ConnectivityResult connectivityResult = cacheService.getCachedOrRefresh(
                CACHE_KEY, this::checkSqsConnectivity);

            // Get current activity metrics (not cached, for real-time reporting)
            SqsMonitoringService.SqsMetrics metrics = sqsMonitoringService.getMetrics();

            // Determine health status - only DOWN if connectivity issue
            Health.Builder healthBuilder = connectivityResult.isConnected ? Health.up() : Health.down();

            return healthBuilder
                .withDetail("connected", connectivityResult.isConnected)
                .withDetail("queueUrl", queueUrl)
                .withDetail("queueArn", connectivityResult.queueArn)
                .withDetail("connectivityReason", connectivityResult.reason)
                // Activity metrics for monitoring (but don't affect health status)
                .withDetail("totalMessagesReceived", metrics.getTotalMessagesReceived())
                .withDetail("totalMessagesProcessed", metrics.getTotalMessagesProcessed())
                .withDetail("successRate", metrics.getSuccessRate())
                .withDetail("lastSuccessfulConnection", 
                    metrics.getLastSuccessfulConnection() != null ? 
                    metrics.getLastSuccessfulConnection() : "Never")
                .withDetail("checkTimestamp", checkTimestamp)
                .withDetail("reason", connectivityResult.isConnected ? 
                    "SQS queue is accessible" : 
                    "SQS queue is not accessible: " + connectivityResult.reason)
                .build();

        } catch (Exception e) {
            logger.error("SQS connectivity check failed", e);
            return Health.down()
                .withDetail("connected", false)
                .withDetail("queueUrl", queueUrl)
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail("connectivityReason", "Exception during connectivity check")
                .withDetail("reason", "SQS connectivity check failed: " + e.getMessage())
                .withDetail("checkTimestamp", System.currentTimeMillis())
                .build();
        }
    }

    /**
     * Performs the actual SQS connectivity check.
     * This method is called by the cache service when a fresh check is needed.
     *
     * @return ConnectivityResult with connection status and details
     */
    private ConnectivityResult checkSqsConnectivity() {
        try {
            logger.debug("Performing fresh SQS connectivity check");

            // Attempt to get basic queue attributes - this verifies connectivity
            GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build();

            GetQueueAttributesResponse response = sqsClient.getQueueAttributes(request);
            String queueArn = response.attributes().get(QueueAttributeName.QUEUE_ARN);

            return new ConnectivityResult(true, "Connected successfully", queueArn);

        } catch (Exception e) {
            logger.debug("SQS connectivity check failed: {}", e.getMessage());
            return new ConnectivityResult(false, e.getMessage(), null);
        }
    }

    /**
     * Result of a connectivity check.
     */
    private static class ConnectivityResult {
        final boolean isConnected;
        final String reason;
        final String queueArn;

        ConnectivityResult(boolean isConnected, String reason, String queueArn) {
            this.isConnected = isConnected;
            this.reason = reason;
            this.queueArn = queueArn;
        }
    }
}
