package com.wifi.measurements.transformer.config;

import com.wifi.measurements.transformer.config.properties.S3ConfigurationProperties;
import com.wifi.measurements.transformer.config.properties.SqsConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.net.URI;
import java.time.Duration;

/**
 * AWS SDK v2 Configuration for SQS and S3 services.
 * 
 * Unified configuration that adapts to environment based on application.yml settings.
 * Supports both LocalStack (development) and AWS (production) environments.
 */
@Configuration
@EnableConfigurationProperties({
    SqsConfigurationProperties.class,
    S3ConfigurationProperties.class
})
public class AwsConfiguration {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.endpoint-url:}")
    private String endpointUrl;

    @Value("${aws.credentials.access-key:}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:}")
    private String secretKey;

    /**
     * Common client override configuration with retry policy and timeouts.
     */
    @Bean
    public ClientOverrideConfiguration clientOverrideConfiguration() {
        return ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofMinutes(5))
            .apiCallAttemptTimeout(Duration.ofSeconds(30))
            .retryPolicy(RetryPolicy.builder()
                .numRetries(3)
                .backoffStrategy(BackoffStrategy.defaultStrategy())
                .build())
            .build();
    }

    /**
     * Unified AWS credentials provider that adapts to environment.
     */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        // Use static credentials if provided (LocalStack development)
        if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            );
        }
        // Use default credentials provider chain for AWS environments
        return DefaultCredentialsProvider.create();
    }

    /**
     * Unified SQS async client that adapts to environment.
     */
    @Bean
    public SqsAsyncClient sqsAsyncClient(
            AwsCredentialsProvider credentialsProvider,
            ClientOverrideConfiguration clientOverrideConfiguration) {
        
        var builder = SqsAsyncClient.builder()
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
     * Unified SQS synchronous client for health checks and monitoring.
     * Uses the same configuration as the async client but provides synchronous operations.
     */
    @Bean
    public SqsClient sqsClient(
            AwsCredentialsProvider credentialsProvider,
            ClientOverrideConfiguration clientOverrideConfiguration) {
        
        var builder = SqsClient.builder()
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
     * Unified S3 client that adapts to environment.
     */
    @Bean
    public S3Client s3Client(
            AwsCredentialsProvider credentialsProvider,
            ClientOverrideConfiguration clientOverrideConfiguration) {
        
        var builder = S3Client.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(clientOverrideConfiguration);

        // Configure custom endpoint and path style for LocalStack development
        if (!endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl))
                   .forcePathStyle(true);  // Required for LocalStack
        }

        return builder.build();
    }
} 