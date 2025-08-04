package com.wifi.positioning.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class DynamoDBConfig {

    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;

    @Value("${aws.dynamodb.region}")
    private String region;

    @Bean
    @Profile("local")
    public DynamoDbClient localDynamoDbClient() {
        // For local development with DynamoDB local
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(dynamoDbEndpoint))
                .region(Region.of(region))
                // For local DynamoDB, we need to disable CBOR protocol
                .dualstackEnabled(false)
                // Use static credentials for local development
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("dummy", "dummy")))
                .build();
    }
    
    @Bean
    @Profile("!local")
    public DynamoDbClient awsDynamoDbClient() {
        // For AWS environments - uses the default credential provider chain
        // which will use the EKS pod's IAM role
        if (dynamoDbEndpoint != null && !dynamoDbEndpoint.isEmpty()) {
            return DynamoDbClient.builder()
                    .endpointOverride(URI.create(dynamoDbEndpoint))
                    .region(Region.of(region))
                    .build();
        } else {
            return DynamoDbClient.builder()
                    .region(Region.of(region))
                    .build();
        }
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
} 