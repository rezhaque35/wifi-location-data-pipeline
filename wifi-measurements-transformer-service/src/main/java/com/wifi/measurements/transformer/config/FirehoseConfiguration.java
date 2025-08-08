// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/config/FirehoseConfiguration.java
package com.wifi.measurements.transformer.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;
import com.wifi.measurements.transformer.service.FirehoseIntegrationService;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClient;
import software.amazon.awssdk.services.firehose.FirehoseClient;

/**
 * Configuration for AWS Kinesis Data Firehose client. Provides async clients for both AWS and
 * LocalStack environments.
 */
@Configuration
@EnableConfigurationProperties(FirehoseConfigurationProperties.class)
public class FirehoseConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(FirehoseConfiguration.class);

  @Value("${aws.region:us-east-1}")
  private String awsRegion;

  @Value("${aws.endpoint-url:}")
  private String endpointUrl;

  /**
   * Creates a single Firehose async client that works for all environments. Uses region from
   * application.yml and automatically detects LocalStack endpoint.
   */
  @Bean
  public FirehoseAsyncClient firehoseAsyncClient(
      AwsCredentialsProvider credentialsProvider,
      FirehoseConfigurationProperties firehoseProperties) {

    Region region = Region.of(awsRegion);
    logger.info("Configuring Firehose async client in region: {}", region);
    logger.info("Firehose delivery stream: {}", firehoseProperties.deliveryStreamName());

    var builder =
        FirehoseAsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(region)
            .overrideConfiguration(
                config ->
                    config
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)));

    // Configure custom endpoint for LocalStack development if specified
    if (!endpointUrl.isEmpty()) {
      logger.info("Using LocalStack endpoint: {}", endpointUrl);
      builder.endpointOverride(URI.create(endpointUrl));
    }

    return builder.build();
  }

  /**
   * Creates a synchronous Firehose client for health checks and monitoring. Uses the same
   * configuration as the async client but provides synchronous operations.
   */
  @Bean
  public FirehoseClient firehoseClient(
      AwsCredentialsProvider credentialsProvider,
      FirehoseConfigurationProperties firehoseProperties) {

    Region region = Region.of(awsRegion);
    logger.info("Configuring Firehose sync client in region: {}", region);

    var builder =
        FirehoseClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(region)
            .overrideConfiguration(
                config ->
                    config
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)));

    // Configure custom endpoint for LocalStack development if specified
    if (!endpointUrl.isEmpty()) {
      logger.info("Using LocalStack endpoint for sync client: {}", endpointUrl);
      builder.endpointOverride(URI.create(endpointUrl));
    }

    return builder.build();
  }

  /** Creates a ScheduledExecutorService for Firehose retry operations. */
  @Bean
  public ScheduledExecutorService firehoseScheduledExecutorService() {
    logger.info("Creating ScheduledExecutorService for Firehose operations");
    return Executors.newScheduledThreadPool(
        2,
        r -> {
          Thread t = new Thread(r, "firehose-scheduler");
          t.setDaemon(true);
          return t;
        });
  }

  /**
   * Creates a Consumer function that writes pre-serialized JSON byte arrays to Firehose. This is
   * used by WiFiMeasurementsPublisher to handle batch writing.
   */
  @Bean
  public Consumer<List<byte[]>> firehoseBatchConsumer(
      FirehoseIntegrationService firehoseIntegrationService) {
    logger.info("Creating Firehose batch consumer");
    return jsonRecords -> {
      if (jsonRecords != null && !jsonRecords.isEmpty()) {
        logger.debug("Consuming batch of {} JSON byte arrays for Firehose", jsonRecords.size());
        firehoseIntegrationService
            .writeBatch(jsonRecords)
            .exceptionally(
                throwable -> {
                  logger.error("Error writing batch to Firehose", throwable);
                  return null;
                });
      }
    };
  }
}
