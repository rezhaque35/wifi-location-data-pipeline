package com.wifi.measurements.transformer.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for S3 file processing.
 * 
 * Configures S3 client settings including region, bucket names,
 * and file size limits.
 */
@ConfigurationProperties(prefix = "s3")
@Validated
public record S3ConfigurationProperties(
    
    @NotBlank(message = "S3 region is required")
    String region,
    
    @NotBlank(message = "S3 bucket name is required")
    String bucketName,
    
    @NotNull(message = "Max file size is required")
    @Min(value = 1024, message = "Max file size must be at least 1KB")
    Long maxFileSize,
    
    @NotNull(message = "Connection timeout is required")
    @Min(value = 1, message = "Connection timeout must be at least 1 second")
    Integer connectionTimeoutSeconds,
    
    @NotNull(message = "Read timeout is required")
    @Min(value = 1, message = "Read timeout must be at least 1 second")
    Integer readTimeoutSeconds
) {
    // No default constructor - all properties must be explicitly configured
} 