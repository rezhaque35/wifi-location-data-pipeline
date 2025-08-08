// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/config/properties/FirehoseConfigurationProperties.java
package com.wifi.measurements.transformer.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for AWS Kinesis Data Firehose integration. Handles batch size limits,
 * timeouts, retry configuration, and delivery stream settings.
 */
@ConfigurationProperties(prefix = "firehose")
@Validated
public record FirehoseConfigurationProperties(

    /** Whether Firehose integration is enabled. */
    boolean enabled,

    /** Name of the Kinesis Data Firehose delivery stream. */
    @NotBlank(message = "Delivery stream name is required") String deliveryStreamName,

    /** Maximum number of records per batch (AWS limit is 500). */
    @Min(value = 1, message = "Max batch size must be at least 1")
        @Max(value = 500, message = "Max batch size cannot exceed 500 (AWS limit)")
        int maxBatchSize,

    /** Maximum batch size in bytes (AWS limit is 4MB). */
    @Min(value = 1024, message = "Max batch size bytes must be at least 1KB")
        @Max(value = 4194304, message = "Max batch size bytes cannot exceed 4MB (AWS limit)")
        long maxBatchSizeBytes,

    /** Maximum time to wait for batch completion in milliseconds. */
    @Positive(message = "Batch timeout must be positive") long batchTimeoutMs,

    /** Maximum size per record in bytes (AWS limit is 1000KB). */
    @Min(value = 1024, message = "Max record size bytes must be at least 1KB")
        @Max(value = 1024000, message = "Max record size bytes cannot exceed 1000KB (AWS limit)")
        long maxRecordSizeBytes,

    /** Maximum number of retry attempts for failed Firehose operations. */
    @Min(value = 0, message = "Max retries cannot be negative")
        @Max(value = 10, message = "Max retries should not exceed 10")
        int maxRetries,

    /** Base backoff time in milliseconds for retry operations. */
    @Positive(message = "Retry backoff must be positive") long retryBackoffMs) {}
