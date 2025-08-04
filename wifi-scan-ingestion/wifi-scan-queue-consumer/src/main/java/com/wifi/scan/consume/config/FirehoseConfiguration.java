package com.wifi.scan.consume.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.FirehoseClientBuilder;

/**
 * Configuration class for AWS Kinesis Data Firehose client.
 * 
 * This configuration class sets up the AWS Firehose client for delivering processed
 * WiFi scan data to downstream storage systems. It supports both LocalStack for
 * development and AWS production environments with flexible credential management.
 * 
 * Key Functionality:
 * - Configures Firehose client with AWS region and credentials
 * - Supports LocalStack development environment with custom endpoints
 * - Provides flexible credential management (static or default provider chain)
 * - Exposes delivery stream name as a configurable bean
 * - Handles both production AWS and development LocalStack configurations
 * 
 * High-Level Steps:
 * 1. Read configuration properties for AWS region, credentials, and endpoints
 * 2. Create FirehoseClientBuilder with specified AWS region
 * 3. Configure credentials provider (static or default chain)
 * 4. Set custom endpoint for LocalStack development if specified
 * 5. Build and return configured FirehoseClient instance
 * 6. Expose delivery stream name as a Spring bean
 * 
 * The configuration automatically adapts to the environment by checking for
 * LocalStack-specific properties and falling back to AWS defaults when not provided.
 * This enables seamless development and production deployment with the same codebase.
 * 
 * @see com.wifi.scan.consume.service.BatchFirehoseMessageService
 * @see software.amazon.awssdk.services.firehose.FirehoseClient
 */
@Slf4j
@Configuration
public class FirehoseConfiguration {

    /** AWS region for Firehose operations, defaults to us-east-1 */
    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /** Custom endpoint URL for LocalStack development environment */
    @Value("${aws.endpoint-url:}")
    private String endpointUrl;

    /** AWS access key for static credentials (used with LocalStack) */
    @Value("${aws.credentials.access-key:}")
    private String accessKey;

    /** AWS secret key for static credentials (used with LocalStack) */
    @Value("${aws.credentials.secret-key:}")
    private String secretKey;

    /** Firehose delivery stream name for data delivery */
    @Value("${aws.firehose.delivery-stream-name:MVS-stream}")
    private String deliveryStreamName;

    /**
     * Creates and configures the AWS Firehose client.
     * 
     * This bean method creates a fully configured FirehoseClient that can be used
     * throughout the application for delivering processed WiFi scan data to AWS
     * Kinesis Data Firehose. The client supports both production AWS and LocalStack
     * development environments.
     * 
     * High-Level Steps:
     * 1. Create FirehoseClientBuilder with specified AWS region
     * 2. Check if static credentials are provided (LocalStack scenario)
     * 3. Configure credentials provider based on available credentials
     * 4. Set custom endpoint if specified (for LocalStack development)
     * 5. Build and return the configured FirehoseClient
     * 6. Log configuration details for monitoring and debugging
     * 
     * The method automatically detects the environment by checking for LocalStack-
     * specific configuration properties and adapts the client configuration accordingly.
     * 
     * @return Configured FirehoseClient ready for data delivery operations
     */
    @Bean
    public FirehoseClient firehoseClient() {
        log.info("Configuring Firehose client for region: {}", awsRegion);
        
        // Create FirehoseClientBuilder with specified AWS region
        FirehoseClientBuilder builder = FirehoseClient.builder()
                .region(Region.of(awsRegion));

        // Configure credentials if provided (for LocalStack development)
        if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
            log.info("Using static credentials for Firehose client");
            // Create static credentials provider for LocalStack development
            AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            );
            builder.credentialsProvider(credentialsProvider);
        } else {
            // Use default AWS credentials provider chain for production
            log.info("Using default credentials provider chain for Firehose client");
        }

        // Configure custom endpoint for LocalStack development environment
        if (!endpointUrl.isEmpty()) {
            log.info("Using custom endpoint for Firehose client: {}", endpointUrl);
            builder.endpointOverride(URI.create(endpointUrl));
        }

        // Build and return the configured FirehoseClient
        FirehoseClient client = builder.build();
        log.info("Firehose client configured successfully for delivery stream: {}", deliveryStreamName);
        
        return client;
    }

    /**
     * Exposes the delivery stream name as a Spring bean.
     * 
     * This bean method provides the configured delivery stream name to other
     * components in the application. The delivery stream name is used by the
     * Firehose service to identify the target stream for data delivery.
     * 
     * @return The configured delivery stream name for Firehose operations
     */
    @Bean
    public String deliveryStreamName() {
        return deliveryStreamName;
    }
} 