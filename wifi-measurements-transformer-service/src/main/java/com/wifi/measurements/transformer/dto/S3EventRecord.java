// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/dto/S3EventRecord.java
package com.wifi.measurements.transformer.dto;

import java.time.Instant;
import java.util.List;

/**
 * Record representing extracted S3 event information from SQS messages.
 * 
 * This record contains all the essential information needed for processing
 * S3 object creation events, including metadata for feed detection and
 * data processing.
 */
public record S3EventRecord(
    String id,
    Instant time,
    String region,
    List<String> resources,
    String bucketName,
    String objectKey,
    long objectSize,
    String etag,
    String versionId,
    String requestId,
    String streamName
) {
    
    /**
     * Creates an S3EventRecord with the stream name extracted from the object key.
     * 
     * @param id Event ID
     * @param time Event timestamp
     * @param region AWS region
     * @param resources S3 bucket ARNs
     * @param bucketName S3 bucket name
     * @param objectKey S3 object key
     * @param objectSize Object size in bytes
     * @param etag Object ETag
     * @param versionId Object version ID
     * @param requestId Request ID
     * @return S3EventRecord with extracted stream name
     */
    public static S3EventRecord of(
            String id,
            Instant time,
            String region,
            List<String> resources,
            String bucketName,
            String objectKey,
            long objectSize,
            String etag,
            String versionId,
            String requestId) {
        
        String streamName = extractStreamName(objectKey);
        
        return new S3EventRecord(
            id, time, region, resources, bucketName, objectKey, 
            objectSize, etag, versionId, requestId, streamName
        );
    }
    
    /**
     * Extracts the stream name from the S3 object key.
     * 
     * The stream name is the prefix of the filename before the datetime.
     * Example: "MVS-stream/2025/07/28/19/MVS-stream-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.txt"
     * Stream name: "MVS-stream"
     * 
     * @param objectKey The S3 object key
     * @return The extracted stream name, or "unknown" if extraction fails
     */
    public static String extractStreamName(String objectKey) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            return "unknown";
        }
        
        try {
            // Split by '/' to get path components
            String[] pathComponents = objectKey.split("/");
            
            // Get the filename (last component)
            String filename = pathComponents[pathComponents.length - 1];
            
            // Find the first occurrence of datetime pattern (YYYY-MM-DD-HH-MM-SS)
            // This pattern appears after the stream name prefix
            String[] filenameParts = filename.split("-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
            
            if (filenameParts.length > 0 && !filenameParts[0].isEmpty()) {
                return filenameParts[0];
            }
            
            // Fallback: if no datetime pattern found, return the filename without extension
            int lastDotIndex = filename.lastIndexOf('.');
            if (lastDotIndex > 0) {
                return filename.substring(0, lastDotIndex);
            }
            
            return filename;
            
        } catch (Exception e) {
            // Log error and return unknown
            return "unknown";
        }
    }
} 