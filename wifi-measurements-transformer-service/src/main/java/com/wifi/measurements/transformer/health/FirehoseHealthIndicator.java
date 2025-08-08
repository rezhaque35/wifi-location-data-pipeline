// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/health/FirehoseHealthIndicator.java
package com.wifi.measurements.transformer.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;
import com.wifi.measurements.transformer.service.FirehoseMonitoringService;

import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.DeliveryStreamStatus;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamRequest;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamResponse;

/**
 * Enhanced health indicator for Kinesis Data Firehose delivery stream connectivity and readiness
 * monitoring.
 *
 * <p>This health indicator provides comprehensive readiness checks for the Firehose delivery
 * stream, ensuring the service is ready to receive traffic and deliver data. It performs detailed
 * connectivity checks, stream status validation, and delivery readiness assessment based on the
 * pattern from the wifi-scan-queue-consumer implementation.
 *
 * <p><strong>Readiness Health Checks:</strong>
 *
 * <ul>
 *   <li><strong>Stream Connectivity:</strong> Basic Firehose delivery stream accessibility and
 *       responsiveness
 *   <li><strong>Stream Status:</strong> Comprehensive stream status validation (ACTIVE required)
 *   <li><strong>Stream Configuration:</strong> Delivery stream configuration and destination
 *       validation
 *   <li><strong>Delivery Readiness:</strong> Service readiness for data delivery operations
 *   <li><strong>Connection Stability:</strong> Recent connection success tracking and failure
 *       monitoring
 * </ul>
 *
 * <p><strong>Health Status Criteria:</strong>
 *
 * <ul>
 *   <li><strong>UP:</strong> Stream is ACTIVE, accessible, and ready for data delivery
 *   <li><strong>DOWN:</strong> Stream is unreachable, not ACTIVE, or delivery not ready
 * </ul>
 *
 * <p><strong>Readiness Validation:</strong>
 *
 * <ul>
 *   <li>Delivery stream accessibility and valid configuration
 *   <li>AWS credentials and permissions validation
 *   <li>Data delivery pipeline readiness
 *   <li>Recent stream connectivity within acceptable timeframe
 * </ul>
 */
@Component("firehose")
public class FirehoseHealthIndicator implements HealthIndicator {

  private static final Logger logger = LoggerFactory.getLogger(FirehoseHealthIndicator.class);
  private static final String DELIVERY_STREAM_NAME_KEY = "deliveryStreamName";
  private static final String ERROR_KEY = "error";

  private final FirehoseClient firehoseClient;
  private final FirehoseConfigurationProperties firehoseConfig;
  private final FirehoseMonitoringService firehoseMonitoringService;

  public FirehoseHealthIndicator(
      FirehoseClient firehoseClient,
      FirehoseConfigurationProperties firehoseConfig,
      FirehoseMonitoringService firehoseMonitoringService) {
    this.firehoseClient = firehoseClient;
    this.firehoseConfig = firehoseConfig;
    this.firehoseMonitoringService = firehoseMonitoringService;
  }

  @Override
  public Health health() {
    try {
      logger.debug("Performing Firehose readiness health check");
      long checkTimestamp = System.currentTimeMillis();

      // Step 1: Check if Firehose is enabled
      if (!firehoseConfig.enabled()) {
        return Health.up()
            .withDetail("enabled", false)
            .withDetail("reason", "Firehose integration disabled - considered ready")
            .withDetail("deliveryReadiness", true)
            .withDetail("checkTimestamp", checkTimestamp)
            .build();
      }

      // Step 2: Comprehensive delivery stream status check for readiness
      DescribeDeliveryStreamRequest request =
          DescribeDeliveryStreamRequest.builder()
              .deliveryStreamName(firehoseConfig.deliveryStreamName())
              .build();

      DescribeDeliveryStreamResponse response = firehoseClient.describeDeliveryStream(request);
      var streamDescription = response.deliveryStreamDescription();
      DeliveryStreamStatus streamStatus = streamDescription.deliveryStreamStatus();

      // Step 3: Get monitoring service metrics for readiness assessment
      FirehoseMonitoringService.FirehoseMetrics metrics = firehoseMonitoringService.getMetrics();
      boolean streamAccessible = firehoseMonitoringService.isStreamAccessible();
      boolean streamReady = firehoseMonitoringService.isStreamReady();

      // Step 4: Assess overall readiness status
      boolean isReady =
          streamAccessible
              && streamReady
              && (streamStatus == DeliveryStreamStatus.ACTIVE)
              && (metrics.getConsecutiveStreamCheckFailures() < 3);

      // Step 5: Build comprehensive health response
      Health.Builder healthBuilder = isReady ? Health.up() : Health.down();

      return healthBuilder
          .withDetail("enabled", true)
          .withDetail(DELIVERY_STREAM_NAME_KEY, firehoseConfig.deliveryStreamName())
          .withDetail("streamAccessible", streamAccessible)
          .withDetail("streamReady", streamReady)
          .withDetail("streamStatus", streamStatus.toString())
          .withDetail("streamArn", streamDescription.deliveryStreamARN())
          .withDetail("deliveryStreamType", streamDescription.deliveryStreamType().toString())
          .withDetail("totalBatchesDelivered", metrics.getTotalBatchesDelivered())
          .withDetail("totalBatchesSucceeded", metrics.getTotalBatchesSucceeded())
          .withDetail("totalRecordsDelivered", metrics.getTotalRecordsDelivered())
          .withDetail("deliverySuccessRate", metrics.getDeliverySuccessRate())
          .withDetail("consecutiveStreamCheckFailures", metrics.getConsecutiveStreamCheckFailures())
          .withDetail("consecutiveDeliveryFailures", metrics.getConsecutiveDeliveryFailures())
          .withDetail("lastSuccessfulStreamCheck", metrics.getLastSuccessfulStreamCheck())
          .withDetail("deliveryReadiness", isReady)
          .withDetail(
              "reason",
              isReady
                  ? "Firehose delivery stream is ACTIVE and ready for data delivery"
                  : "Firehose delivery stream is not ready - connectivity or status issues detected")
          .withDetail("checkTimestamp", checkTimestamp)
          .build();

    } catch (Exception e) {
      logger.error("Firehose readiness check failed", e);
      return Health.down()
          .withDetail("enabled", firehoseConfig.enabled())
          .withDetail(DELIVERY_STREAM_NAME_KEY, firehoseConfig.deliveryStreamName())
          .withDetail("streamAccessible", false)
          .withDetail("streamReady", false)
          .withDetail(ERROR_KEY, e.getMessage())
          .withDetail("errorType", e.getClass().getSimpleName())
          .withDetail("deliveryReadiness", false)
          .withDetail("reason", "Firehose connectivity check failed: " + e.getMessage())
          .withDetail("checkTimestamp", System.currentTimeMillis())
          .build();
    }
  }
}
