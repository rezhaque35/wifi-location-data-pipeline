// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/FeedProcessor.java
package com.wifi.measurements.transformer.processor;

import com.wifi.measurements.transformer.dto.S3EventRecord;

/**
 * Interface for processing different types of data feeds from S3 events.
 * 
 * This interface provides a pluggable architecture for handling different
 * data feed types (e.g., WiFi scans, GPS data, sensor data) with feed-specific
 * processing logic while maintaining a common processing contract.
 * 
 * Implementations should be stateless and thread-safe to support concurrent processing.
 */
public interface FeedProcessor {
    
    /**
     * Gets the feed type that this processor supports.
     * 
     * @return The feed type identifier (e.g., "MVS-stream", "GPS-data", "unknown")
     */
    String getSupportedFeedType();
    
    /**
     * Determines if this processor can handle the given feed type.
     * 
     * @param feedType The feed type to check
     * @return true if this processor can handle the feed type, false otherwise
     */
    boolean canProcess(String feedType);
    
    /**
     * Processes an S3 event record for the supported feed type.
     * 
     * This method should:
     * 1. Download and process the S3 object
     * 2. Apply feed-specific transformation logic
     * 3. Validate and filter data according to feed requirements
     * 4. Write processed data to the appropriate destination
     * 5. Handle errors and log processing statistics
     * 
     * @param s3EventRecord The S3 event record to process
     * @return true if processing was successful, false otherwise
     */
    boolean processS3Event(S3EventRecord s3EventRecord);
    
    /**
     * Gets processing metrics for monitoring and observability.
     * 
     * @return Processing metrics as a formatted string
     */
    String getProcessingMetrics();
    
    /**
     * Gets the priority of this processor for feed type resolution.
     * Higher values indicate higher priority.
     * 
     * @return The processor priority (default implementations should return 0)
     */
    default int getPriority() {
        return 0;
    }
} 