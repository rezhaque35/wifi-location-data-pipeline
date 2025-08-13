package com.wifi.measurements.transformer.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wifi.measurements.transformer.config.properties.S3ConfigurationProperties;
import com.wifi.measurements.transformer.config.properties.SqsConfigurationProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

/**
 * AWS SDK v2 Configuration for SQS and S3 services.
 *
 * <p>Unified configuration that adapts to environment based on application.yml settings. Supports
 * both LocalStack (development) and AWS (production) environments.
 */
@Configuration
@EnableConfigurationProperties({SqsConfigurationProperties.class, S3ConfigurationProperties.class})
public class AwsConfiguration {

  @Value("${aws.region:us-east-1}")
  private String awsRegion;

  @Value("${aws.endpoint-url:}")
  private String endpointUrl;

  @Value("${aws.credentials.access-key:}")
  private String accessKey;

  @Value("${aws.credentials.secret-key:}")
  private String secretKey;

  /** Common client override configuration with retry policy and timeouts. */
  @Bean
  public ClientOverrideConfiguration clientOverrideConfiguration() {
    return ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofMinutes(5))
        .apiCallAttemptTimeout(Duration.ofSeconds(30))
        .retryPolicy(
            RetryPolicy.builder()
                .numRetries(3)
                .backoffStrategy(BackoffStrategy.defaultStrategy())
                .build())
        .build();
  }

  /** Unified AWS credentials provider that adapts to environment. */
  @Bean
  public AwsCredentialsProvider awsCredentialsProvider() {
    // Use static credentials if provided (LocalStack development)
    if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
      return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
    // Use default credentials provider chain for AWS environments
    return DefaultCredentialsProvider.create();
  }

  /** Unified SQS async client that adapts to environment. */
  @Bean
  public SqsAsyncClient sqsAsyncClient(
      AwsCredentialsProvider credentialsProvider,
      ClientOverrideConfiguration clientOverrideConfiguration) {

    var builder =
        SqsAsyncClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(clientOverrideConfiguration);

    // Configure custom endpoint for LocalStack development
    if (!endpointUrl.isEmpty()) {
      builder.endpointOverride(URI.create(endpointUrl));
    }

    return builder.build();
  }

  /**
   * Unified SQS synchronous client for health checks and monitoring. Uses the same configuration as
   * the async client but provides synchronous operations.
   */
  @Bean
  public SqsClient sqsClient(
      AwsCredentialsProvider credentialsProvider,
      ClientOverrideConfiguration clientOverrideConfiguration) {

    var builder =
        SqsClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(clientOverrideConfiguration);

    // Configure custom endpoint for LocalStack development
    if (!endpointUrl.isEmpty()) {
      builder.endpointOverride(URI.create(endpointUrl));
    }

    return builder.build();
  }

  /**
   * Environment-agnostic SQS queue URL resolver.
   * 
   * <p>Resolves queue URL based on configuration:
   * <ul>
   *   <li>If queue-url is provided: uses it directly (LocalStack compatibility)
   *   <li>If queue-name is provided: constructs URL using AWS SQS service (AWS standard)
   * </ul>
   */
  @Bean
  public String resolvedQueueUrl(
      SqsClient sqsClient,
      SqsConfigurationProperties sqsConfig) {
    
    // Use direct URL if provided (LocalStack development)
    if (sqsConfig.hasDirectUrl()) {
      return sqsConfig.queueUrl();
    }
    
    // Construct URL from queue name (AWS production)
    if (sqsConfig.hasQueueName()) {
      try {
        var request = GetQueueUrlRequest.builder()
            .queueName(sqsConfig.queueName())
            .build();
        
        return sqsClient.getQueueUrl(request).queueUrl();
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to resolve queue URL for queue name: " + sqsConfig.queueName(), e);
      }
    }
    
    // This should never happen due to validation in SqsConfigurationProperties
    throw new IllegalStateException("Neither queue URL nor queue name is configured");
  }

  /** Unified S3 client that adapts to environment. */
  @Bean
  public S3Client s3Client(
      AwsCredentialsProvider credentialsProvider,
      ClientOverrideConfiguration clientOverrideConfiguration) {

    var builder =
        S3Client.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(clientOverrideConfiguration);

    // Configure custom endpoint and path style for LocalStack development
    if (!endpointUrl.isEmpty()) {
      builder
          .endpointOverride(URI.create(endpointUrl))
          .forcePathStyle(true); // Required for LocalStack
    }

    return builder.build();
  }
}
