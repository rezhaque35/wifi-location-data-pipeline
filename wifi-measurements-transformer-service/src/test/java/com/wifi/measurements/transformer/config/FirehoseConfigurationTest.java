// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/config/FirehoseConfigurationTest.java
package com.wifi.measurements.transformer.config;

import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FirehoseConfiguration.
 */
@ExtendWith(MockitoExtension.class)
class FirehoseConfigurationTest {

    @Mock
    private AwsCredentialsProvider credentialsProvider;

    @Test
    void shouldCreateFirehoseAsyncClient() {
        // Given
        var firehoseProperties = new FirehoseConfigurationProperties(
            true,
            "test-delivery-stream",
            500,
            4194304L, // 4MB
            5000L,    // 5 seconds
            1024000L, // 1000KB
            3,
            1000L
        );

        var firehoseConfiguration = new FirehoseConfiguration();
        
        // Set the private fields using reflection
        try {
            var awsRegionField = FirehoseConfiguration.class.getDeclaredField("awsRegion");
            awsRegionField.setAccessible(true);
            awsRegionField.set(firehoseConfiguration, "us-east-1");
            
            var endpointUrlField = FirehoseConfiguration.class.getDeclaredField("endpointUrl");
            endpointUrlField.setAccessible(true);
            endpointUrlField.set(firehoseConfiguration, "");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set fields", e);
        }

        // When
        FirehoseAsyncClient firehoseClient = firehoseConfiguration.firehoseAsyncClient(
            credentialsProvider, firehoseProperties);

        // Then
        assertThat(firehoseClient).isNotNull();
    }

    @Test
    void shouldCreateFirehoseAsyncClientWithLocalStackEndpoint() {
        // Given
        var firehoseProperties = new FirehoseConfigurationProperties(
            true,
            "test-delivery-stream",
            500,
            4194304L,
            5000L,
            1024000L,
            3,
            1000L
        );

        var firehoseConfiguration = new FirehoseConfiguration();
        
        // Set the private fields using reflection
        try {
            var awsRegionField = FirehoseConfiguration.class.getDeclaredField("awsRegion");
            awsRegionField.setAccessible(true);
            awsRegionField.set(firehoseConfiguration, "us-east-1");
            
            var endpointUrlField = FirehoseConfiguration.class.getDeclaredField("endpointUrl");
            endpointUrlField.setAccessible(true);
            endpointUrlField.set(firehoseConfiguration, "http://localhost:4566");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set fields", e);
        }

        // When
        FirehoseAsyncClient firehoseClient = firehoseConfiguration.firehoseAsyncClient(
            credentialsProvider, firehoseProperties);

        // Then
        assertThat(firehoseClient).isNotNull();
    }
} 