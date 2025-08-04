package com.wifi.scan.consume.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.services.firehose.FirehoseClient;

/**
 * Unit tests for FirehoseConfiguration.
 * Tests bean creation with different environment configurations.
 */
@ExtendWith(SpringExtension.class)
@DisplayName("Firehose Configuration Tests")
class FirehoseConfigurationTest {

    @Test
    @DisplayName("Should create Firehose client with LocalStack configuration")
    void shouldCreateFirehoseClientWithLocalStackConfiguration() {
        // Given
        FirehoseConfiguration config = new FirehoseConfiguration();
        ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
        ReflectionTestUtils.setField(config, "endpointUrl", "http://localhost:4566");
        ReflectionTestUtils.setField(config, "accessKey", "test");
        ReflectionTestUtils.setField(config, "secretKey", "test");
        ReflectionTestUtils.setField(config, "deliveryStreamName", "MVS-stream");

        // When
        FirehoseClient client = config.firehoseClient();
        String streamName = config.deliveryStreamName();

        // Then
        assertThat(client).isNotNull();
        assertThat(streamName).isEqualTo("MVS-stream");
    }

    @Test
    @DisplayName("Should create Firehose client with production configuration")
    void shouldCreateFirehoseClientWithProductionConfiguration() {
        // Given
        FirehoseConfiguration config = new FirehoseConfiguration();
        ReflectionTestUtils.setField(config, "awsRegion", "us-west-2");
        ReflectionTestUtils.setField(config, "endpointUrl", "");
        ReflectionTestUtils.setField(config, "accessKey", "");
        ReflectionTestUtils.setField(config, "secretKey", "");
        ReflectionTestUtils.setField(config, "deliveryStreamName", "production-stream");

        // When
        FirehoseClient client = config.firehoseClient();
        String streamName = config.deliveryStreamName();

        // Then
        assertThat(client).isNotNull();
        assertThat(streamName).isEqualTo("production-stream");
    }

    @Test
    @DisplayName("Should handle various delivery stream names")
    void shouldHandleVariousDeliveryStreamNames() {
        // Given
        FirehoseConfiguration config = new FirehoseConfiguration();
        ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
        ReflectionTestUtils.setField(config, "endpointUrl", "");
        ReflectionTestUtils.setField(config, "accessKey", "");
        ReflectionTestUtils.setField(config, "secretKey", "");

        // Test different stream names
        String[] streamNames = {"test-stream", "wifi-data-stream", "MVS-stream", "production-firehose"};

        for (String streamName : streamNames) {
            // When
            ReflectionTestUtils.setField(config, "deliveryStreamName", streamName);
            String result = config.deliveryStreamName();

            // Then
            assertThat(result).isEqualTo(streamName);
        }
    }
} 