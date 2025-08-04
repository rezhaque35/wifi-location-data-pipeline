// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/config/properties/FirehoseConfigurationPropertiesTest.java
package com.wifi.measurements.transformer.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FirehoseConfigurationProperties.
 */
class FirehoseConfigurationPropertiesTest {

    @Test
    void shouldCreateValidFirehoseConfigurationProperties() {
        // Given
        var properties = new FirehoseConfigurationProperties(
            true,
            "wifi-measurements-stream",
            500,
            4194304L, // 4MB
            5000L,    // 5 seconds
            1024000L, // 1000KB
            3,
            1000L
        );

        // Then
        assertThat(properties.enabled()).isTrue();
        assertThat(properties.deliveryStreamName()).isEqualTo("wifi-measurements-stream");
        assertThat(properties.maxBatchSize()).isEqualTo(500);
        assertThat(properties.maxBatchSizeBytes()).isEqualTo(4194304L);
        assertThat(properties.batchTimeoutMs()).isEqualTo(5000L);
        assertThat(properties.maxRecordSizeBytes()).isEqualTo(1024000L);
        assertThat(properties.maxRetries()).isEqualTo(3);
        assertThat(properties.retryBackoffMs()).isEqualTo(1000L);
    }

    @Test
    void shouldHaveDefaultValuesWhenUsingBuilderPattern() {
        // Given/When
        var properties = new FirehoseConfigurationProperties(
            false,
            "",
            0,
            0L,
            0L,
            0L,
            0,
            0L
        );

        // Then - these are the minimum values, actual defaults will be in application.yml
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.deliveryStreamName()).isEmpty();
        assertThat(properties.maxBatchSize()).isZero();
        assertThat(properties.maxBatchSizeBytes()).isZero();
        assertThat(properties.batchTimeoutMs()).isZero();
        assertThat(properties.maxRecordSizeBytes()).isZero();
        assertThat(properties.maxRetries()).isZero();
        assertThat(properties.retryBackoffMs()).isZero();
    }
} 