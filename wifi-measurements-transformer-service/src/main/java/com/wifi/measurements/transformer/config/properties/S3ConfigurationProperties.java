package com.wifi.measurements.transformer.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for S3 file processing.
 *
 * <p>Configures S3 client settings including region, bucket names, and file size limits.
 */
@ConfigurationProperties(prefix = "s3")
@Validated
public record S3ConfigurationProperties(
    @NotNull(message = "Max file size is required")
        @Min(value = 1024, message = "Max file size must be at least 1KB")
        Long maxFileSize) {
  // No default constructor - all properties must be explicitly configured
}
