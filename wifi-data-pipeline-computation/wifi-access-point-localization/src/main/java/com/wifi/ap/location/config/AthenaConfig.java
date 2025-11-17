package com.wifi.ap.location.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Configuration for AWS Athena client and S3 client for querying S3 Tables.
 * 
 * This configuration supports both LocalStack (for development) and AWS production environments.
 * Includes S3 client for managing Athena query result files.
 */
@Configuration
public class AthenaConfig {

    @Bean
    public AthenaClient athenaClient(
            AwsCredentialsProvider credentialsProvider,
            @Value("${aws.region}") String region,
            @Value("${aws.endpoint-url:}") String endpointUrl) {
        
        var clientBuilder = AthenaClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region));
        
        // Configure endpoint for LocalStack if provided
        if (endpointUrl != null && !endpointUrl.trim().isEmpty()) {
            clientBuilder.endpointOverride(URI.create(endpointUrl));
        }
        
        return clientBuilder.build();
    }

    /**
     * S3 client specifically for Athena result management.
     * Uses the same configuration as the main S3 client but dedicated for Athena operations.
     */
    @Bean("athenaS3Client")
    public S3Client athenaS3Client(
            AwsCredentialsProvider credentialsProvider,
            @Value("${aws.region}") String region,
            @Value("${aws.endpoint-url:}") String endpointUrl) {
        
        var clientBuilder = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region));
        
        // Configure endpoint for LocalStack if provided
        if (endpointUrl != null && !endpointUrl.trim().isEmpty()) {
            clientBuilder.endpointOverride(URI.create(endpointUrl))
                          .forcePathStyle(true); // Required for LocalStack
        }
        
        return clientBuilder.build();
    }
}
