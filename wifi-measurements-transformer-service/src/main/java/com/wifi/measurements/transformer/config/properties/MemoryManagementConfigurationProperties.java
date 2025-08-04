// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/config/properties/MemoryManagementConfigurationProperties.java
package com.wifi.measurements.transformer.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for memory management and performance optimization.
 * 
 * Configures memory thresholds, GC optimization, batch throttling, and performance monitoring.
 */
@ConfigurationProperties(prefix = "memory-management")
@Validated
public record MemoryManagementConfigurationProperties(
    
    /**
     * Enable memory monitoring and management features.
     */
    boolean enabled,
    
    /**
     * Maximum heap usage threshold as percentage (0.0-1.0) before triggering memory pressure actions.
     */
    @NotNull(message = "Memory pressure threshold is required")
    @DecimalMin(value = "0.5", message = "Memory pressure threshold must be at least 0.5 (50%)")
    @DecimalMax(value = "0.95", message = "Memory pressure threshold cannot exceed 0.95 (95%)")
    Double memoryPressureThreshold,
    
    /**
     * Maximum memory allowed for Firehose batch accumulation in bytes.
     */
    @NotNull(message = "Max batch memory is required")
    @Positive(message = "Max batch memory must be positive")
    Long maxBatchMemoryBytes,
    
    /**
     * Memory check interval in milliseconds for monitoring heap usage.
     */
    @NotNull(message = "Memory check interval is required")
    @Min(value = 1000, message = "Memory check interval must be at least 1 second")
    @Max(value = 60000, message = "Memory check interval cannot exceed 60 seconds")
    Long memoryCheckIntervalMs,
    
    /**
     * Enable automatic batch size reduction when memory pressure is detected.
     */
    boolean enableBatchThrottling,
    
    /**
     * Minimum batch size when throttling is active (cannot go below this).
     */
    @NotNull(message = "Min throttled batch size is required")
    @Min(value = 1, message = "Min throttled batch size must be at least 1")
    @Max(value = 100, message = "Min throttled batch size cannot exceed 100")
    Integer minThrottledBatchSize,
    
    /**
     * Enable performance profiling and timing metrics.
     */
    boolean enablePerformanceProfiling,
    
    /**
     * Enable streaming JSON serialization optimization.
     */
    boolean enableStreamingSerialization,
    
    /**
     * Garbage collection optimization settings.
     */
    @Valid
    @NotNull(message = "GC optimization configuration is required")
    GcOptimizationConfiguration gcOptimization
) {
    
    // No default constructor - all properties must be explicitly configured
    
    /**
     * Configuration for garbage collection optimization.
     */
    public record GcOptimizationConfiguration(
        
        /**
         * Enable GC optimization recommendations.
         */
        boolean enabled,
        
        /**
         * Trigger System.gc() when memory pressure threshold is reached.
         */
        boolean suggestGcOnPressure,
        
        /**
         * Log GC events and memory statistics.
         */
        boolean logGcEvents,
        
        /**
         * Batch processing pause interval in milliseconds to allow GC.
         */
        @NotNull(message = "GC pause interval is required")
        @Min(value = 0, message = "GC pause interval cannot be negative")
        @Max(value = 10000, message = "GC pause interval cannot exceed 10 seconds")
        Long gcPauseIntervalMs
    ) {
        
        // No default constructor - all properties must be explicitly configured
    }
}