package com.wifi.measurements.transformer.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for SQS message processing.
 * 
 * Configures SQS client settings including long polling, batch processing,
 * and visibility timeout management.
 */
@ConfigurationProperties(prefix = "sqs")
@Validated
public record SqsConfigurationProperties(
    
    @NotBlank(message = "SQS queue URL is required")
    String queueUrl,
    
    @NotNull(message = "Max messages is required")
    @Min(value = 1, message = "Max messages must be at least 1")
    @Max(value = 10, message = "Max messages cannot exceed 10")
    Integer maxMessages,
    
    @NotNull(message = "Wait time seconds is required")
    @Min(value = 0, message = "Wait time seconds cannot be negative")
    @Max(value = 20, message = "Wait time seconds cannot exceed 20")
    Integer waitTimeSeconds,
    
    @NotNull(message = "Visibility timeout seconds is required")
    @Min(value = 30, message = "Visibility timeout must be at least 30 seconds")
    @Max(value = 43200, message = "Visibility timeout cannot exceed 12 hours")
    Integer visibilityTimeoutSeconds,
    
    @NotNull(message = "Max concurrent batches is required")
    @Min(value = 1, message = "Max concurrent batches must be at least 1")
    @Max(value = 20, message = "Max concurrent batches cannot exceed 20")
    Integer maxConcurrentBatches,
    
    @NotNull(message = "Max retries is required")
    @Min(value = 1, message = "Max retries must be at least 1")
    @Max(value = 10, message = "Max retries cannot exceed 10")
    Integer maxRetries
) {
    // No default constructor - all properties must be explicitly configured
} 