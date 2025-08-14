// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/FirehoseConnectivityHealthIndicator.java
package com.wifi.measurements.transformer.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;
import com.wifi.measurements.transformer.service.FirehoseMonitoringService;

import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamRequest;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamResponse;

/**
 * Health indicator for Firehose connectivity that only marks service as DOWN 
 * when there's no connection to Firehose.
 *
 * <p>This health indicator is designed to be used for service readiness checks and
 * only fails when Firehose is genuinely unreachable. It does not fail for delivery 
 * issues, activity timeouts, or stream status problems - those are handled by 
 * separate reporting indicators.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>Connectivity Only:</strong> Only checks basic Firehose connectivity</li>
 *   <li><strong>Cached Results:</strong> Uses caching to avoid excessive AWS API calls</li>
 *   <li><strong>Simple Criteria:</strong> UP if can connect to Firehose, DOWN if cannot</li>
 *   <li><strong>Activity Reporting:</strong> Includes activity metrics for monitoring</li>
 * </ul>
 *
 * <p><strong>Health Status Criteria:</strong>
 * <ul>
 *   <li><strong>UP:</strong> Can connect to Firehose and describe delivery stream</li>
 *   <li><strong>DOWN:</strong> Cannot connect to Firehose (network/auth/config issues)</li>
 * </ul>
 */
@Component("firehoseConnectivity")
public class FirehoseConnectivityHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(FirehoseConnectivityHealthIndicator.class);
    private static final String CACHE_KEY = "firehose-connectivity";
    
    // Constants for repeated string literals
    private static final String ENABLED_KEY = "enabled";
    private static final String CONNECTED_KEY = "connected";
    private static final String CONNECTIVITY_REASON_KEY = "connectivityReason";
    private static final String REASON_KEY = "reason";
    private static final String CHECK_TIMESTAMP_KEY = "checkTimestamp";

    private final FirehoseClient firehoseClient;
    private final FirehoseConfigurationProperties firehoseConfig;
    private final FirehoseMonitoringService firehoseMonitoringService;
    private final HealthCheckCacheService cacheService;

    public FirehoseConnectivityHealthIndicator(
            FirehoseClient firehoseClient,
            FirehoseConfigurationProperties firehoseConfig,
            FirehoseMonitoringService firehoseMonitoringService,
            HealthCheckCacheService cacheService) {
        this.firehoseClient = firehoseClient;
        this.firehoseConfig = firehoseConfig;
        this.firehoseMonitoringService = firehoseMonitoringService;
        this.cacheService = cacheService;
    }

    @Override
    public Health health() {
        try {
            logger.debug("Checking simplified Firehose connectivity");
            long checkTimestamp = System.currentTimeMillis();

            // Check if Firehose is enabled
            if (!firehoseConfig.enabled()) {
                return Health.up()
                    .withDetail(ENABLED_KEY, false)
                    .withDetail(CONNECTED_KEY, true)
                    .withDetail(CONNECTIVITY_REASON_KEY, "Firehose integration disabled - considered connected")
                    .withDetail(REASON_KEY, "Firehose integration disabled")
                    .withDetail(CHECK_TIMESTAMP_KEY, checkTimestamp)
                    .build();
            }

            // Use cached connectivity check to avoid excessive API calls
            ConnectivityResult connectivityResult = cacheService.getCachedOrRefresh(
                CACHE_KEY, this::checkFirehoseConnectivity);

            // Get current activity metrics (not cached, for real-time reporting)
            FirehoseMonitoringService.FirehoseMetrics metrics = firehoseMonitoringService.getMetrics();

            // Determine health status - only DOWN if connectivity issue
            Health.Builder healthBuilder = connectivityResult.isConnected ? Health.up() : Health.down();

            return healthBuilder
                .withDetail(ENABLED_KEY, true)
                .withDetail(CONNECTED_KEY, connectivityResult.isConnected)
                .withDetail("deliveryStreamName", firehoseConfig.deliveryStreamName())
                .withDetail("streamArn", connectivityResult.streamArn)
                .withDetail("streamStatus", connectivityResult.streamStatus)
                .withDetail(CONNECTIVITY_REASON_KEY, connectivityResult.reason)
                // Activity metrics for monitoring (but don't affect health status)
                .withDetail("totalBatchesDelivered", metrics.getTotalBatchesDelivered())
                .withDetail("totalBatchesSucceeded", metrics.getTotalBatchesSucceeded())
                .withDetail("totalRecordsDelivered", metrics.getTotalRecordsDelivered())
                .withDetail("deliverySuccessRate", metrics.getDeliverySuccessRate())
                .withDetail("lastSuccessfulStreamCheck", metrics.getLastSuccessfulStreamCheck())
                .withDetail(CHECK_TIMESTAMP_KEY, checkTimestamp)
                .withDetail(REASON_KEY, connectivityResult.isConnected ? 
                    "Firehose delivery stream is accessible" : 
                    "Firehose delivery stream is not accessible: " + connectivityResult.reason)
                .build();

        } catch (Exception e) {
            logger.error("Firehose connectivity check failed", e);
            return Health.down()
                .withDetail(ENABLED_KEY, firehoseConfig.enabled())
                .withDetail(CONNECTED_KEY, false)
                .withDetail("deliveryStreamName", firehoseConfig.deliveryStreamName())
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail(CONNECTIVITY_REASON_KEY, "Exception during connectivity check")
                .withDetail(REASON_KEY, "Firehose connectivity check failed: " + e.getMessage())
                .withDetail(CHECK_TIMESTAMP_KEY, System.currentTimeMillis())
                .build();
        }
    }

    /**
     * Performs the actual Firehose connectivity check.
     * This method is called by the cache service when a fresh check is needed.
     *
     * @return ConnectivityResult with connection status and details
     */
    private ConnectivityResult checkFirehoseConnectivity() {
        try {
            logger.debug("Performing fresh Firehose connectivity check");

            // Attempt to describe delivery stream - this verifies connectivity
            DescribeDeliveryStreamRequest request = DescribeDeliveryStreamRequest.builder()
                .deliveryStreamName(firehoseConfig.deliveryStreamName())
                .build();

            DescribeDeliveryStreamResponse response = firehoseClient.describeDeliveryStream(request);
            var streamDescription = response.deliveryStreamDescription();
            
            return new ConnectivityResult(
                true, 
                "Connected successfully", 
                streamDescription.deliveryStreamARN(),
                streamDescription.deliveryStreamStatus().toString()
            );

        } catch (Exception e) {
            logger.debug("Firehose connectivity check failed: {}", e.getMessage());
            return new ConnectivityResult(false, e.getMessage(), null, null);
        }
    }

    /**
     * Result of a connectivity check.
     */
    private static class ConnectivityResult {
        final boolean isConnected;
        final String reason;
        final String streamArn;
        final String streamStatus;

        ConnectivityResult(boolean isConnected, String reason, String streamArn, String streamStatus) {
            this.isConnected = isConnected;
            this.reason = reason;
            this.streamArn = streamArn;
            this.streamStatus = streamStatus;
        }
    }
}
