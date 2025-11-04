package com.wifi.positioning.repository;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

@Configuration
public class DynamoDBConfig {

  @Value("${aws.dynamodb.endpoint:}")
  private String dynamoDbEndpoint;

  @Value("${aws.dynamodb.region}")
  private String region;

  @Bean
  @Profile("local")
  public DynamoDbAsyncClient localDynamoDbAsyncClient() {
    // For local development with DynamoDB local
    return DynamoDbAsyncClient.builder()
        .endpointOverride(URI.create(dynamoDbEndpoint))
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
        .build();
  }

  @Bean
  @Profile("!local")
  public DynamoDbAsyncClient awsDynamoDbAsyncClient() {
    // For AWS environments - uses the default credential provider chain
    if (dynamoDbEndpoint != null && !dynamoDbEndpoint.isEmpty()) {
      return DynamoDbAsyncClient.builder()
          .endpointOverride(URI.create(dynamoDbEndpoint))
          .region(Region.of(region))
          .build();
    } else {
      return DynamoDbAsyncClient.builder().region(Region.of(region)).build();
    }
  }

  @Bean
  public DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient(DynamoDbAsyncClient asyncClient) {
    return DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(asyncClient).build();
  }
}
