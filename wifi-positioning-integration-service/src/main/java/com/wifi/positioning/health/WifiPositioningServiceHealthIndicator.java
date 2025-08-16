// wifi-positioning-integration-service/src/main/java/com/wifi/positioning/health/WifiPositioningServiceHealthIndicator.java
package com.wifi.positioning.health;

import com.wifi.positioning.client.PositioningServiceClient;
import com.wifi.positioning.config.IntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for WiFi Positioning Service connectivity that only marks service as DOWN 
 * when there's no connection to the positioning service.
 *
 * <p>This health indicator is designed to be used for service readiness checks and
 * only fails when the positioning service is genuinely unreachable. It monitors the
 * health endpoint of the wifi-positioning-service to determine overall system health.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>Connectivity Only:</strong> Only checks basic positioning service connectivity</li>
 *   <li><strong>Cached Results:</strong> Uses caching to avoid excessive health endpoint calls</li>
 *   <li><strong>Simple Criteria:</strong> UP if positioning service health is UP, DOWN if not accessible</li>
 *   <li><strong>Dependency Chain:</strong> This service's health directly depends on positioning service health</li>
 * </ul>
 *
 * <p><strong>Health Status Criteria:</strong>
 * <ul>
 *   <li><strong>UP:</strong> Can connect to positioning service and its health endpoint returns UP</li>
 *   <li><strong>DOWN:</strong> Cannot connect to positioning service or its health endpoint returns DOWN</li>
 * </ul>
 */
@Component("wifiPositioningService")
public class WifiPositioningServiceHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(WifiPositioningServiceHealthIndicator.class);
    private static final String CACHE_KEY = "wifi-positioning-service-health";

    // Constants for repeated string literals
    private static final String CONNECTED_KEY = "connected";
    private static final String CONNECTIVITY_REASON_KEY = "connectivityReason";
    private static final String REASON_KEY = "reason";
    private static final String CHECK_TIMESTAMP_KEY = "checkTimestamp";
    private static final String POSITIONING_SERVICE_URL_KEY = "positioningServiceUrl";
    private static final String RESPONSE_TIME_MS_KEY = "responseTimeMs";
    private static final String HTTP_STATUS_KEY = "httpStatus";

    private final PositioningServiceClient positioningServiceClient;
    private final HealthCheckCacheService cacheService;
    private final IntegrationProperties properties;

    public WifiPositioningServiceHealthIndicator(
            PositioningServiceClient positioningServiceClient,
            HealthCheckCacheService cacheService,
            IntegrationProperties properties) {
        this.positioningServiceClient = positioningServiceClient;
        this.cacheService = cacheService;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            logger.debug("Checking WiFi positioning service health");
            long checkTimestamp = System.currentTimeMillis();

            // Use cached health check to avoid excessive endpoint calls
            PositioningServiceClient.HealthResult healthResult = cacheService.getCachedOrRefresh(
                CACHE_KEY, positioningServiceClient::checkHealth);

            // Determine health status based on positioning service health
            Health.Builder healthBuilder = healthResult.isHealthy() ? Health.up() : Health.down();

            // Build connectivity reason
            String connectivityReason;
            if (healthResult.isHealthy()) {
                connectivityReason = "Positioning service is accessible and reports status: " + healthResult.getHealthStatus();
            } else {
                connectivityReason = "Positioning service is not accessible or reports unhealthy status: " + healthResult.getHealthStatus();
                if (healthResult.getErrorMessage() != null) {
                    connectivityReason += " - " + healthResult.getErrorMessage();
                }
            }

            // Build reason message
            String reason;
            if (healthResult.isHealthy()) {
                reason = "WiFi positioning service is up and running (status: " + healthResult.getHealthStatus() + ")";
            } else {
                reason = "WiFi positioning service is down (status: " + healthResult.getHealthStatus() + ")";
                if (healthResult.getErrorMessage() != null) {
                    reason += " - " + healthResult.getErrorMessage();
                }
            }

            Health.Builder resultBuilder = healthBuilder
                .withDetail(CONNECTED_KEY, healthResult.isHealthy())
                .withDetail(POSITIONING_SERVICE_URL_KEY, properties.getPositioning().getBaseUrl())
                .withDetail(RESPONSE_TIME_MS_KEY, healthResult.getLatencyMs())
                .withDetail("positioningServiceStatus", healthResult.getHealthStatus())
                .withDetail(CONNECTIVITY_REASON_KEY, connectivityReason)
                .withDetail(CHECK_TIMESTAMP_KEY, checkTimestamp)
                .withDetail(REASON_KEY, reason);

            // Only add HTTP status if not null
            if (healthResult.getHttpStatus() != null) {
                resultBuilder.withDetail(HTTP_STATUS_KEY, healthResult.getHttpStatus());
            }

            // Only add response if not null
            if (healthResult.getResponse() != null) {
                resultBuilder.withDetail("positioningServiceResponse", healthResult.getResponse());
            }

            return resultBuilder.build();

        } catch (Exception e) {
            logger.error("WiFi positioning service health check failed", e);
            return Health.down()
                .withDetail(CONNECTED_KEY, false)
                .withDetail(POSITIONING_SERVICE_URL_KEY, properties.getPositioning().getBaseUrl())
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail(CONNECTIVITY_REASON_KEY, "Exception during health check")
                .withDetail(REASON_KEY, "WiFi positioning service health check failed: " + e.getMessage())
                .withDetail(CHECK_TIMESTAMP_KEY, System.currentTimeMillis())
                .build();
        }
    }
}
