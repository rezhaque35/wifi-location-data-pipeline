// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/dto/S3EventRecord.java
package com.wifi.measurements.transformer.dto;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Record representing extracted S3 event information from SQS messages.
 *
 * <p>This record contains all the essential information needed for processing S3 object creation
 * events, including metadata for feed detection and data processing.
 */
public record FeedUploadEvent(
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
    String streamName) {

  /** Default value for unknown stream names. */
  private static final String UNKNOWN_STREAM = "unknown";

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
  public static FeedUploadEvent of(
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

    return new FeedUploadEvent(
        id,
        time,
        region,
        resources,
        bucketName,
        objectKey,
        objectSize,
        etag,
        versionId,
        requestId,
        streamName);
  }

  /**
   * Extracts the stream name from the S3 object key.
   *
   * <p>The stream name is always the component immediately before the filename.
   * Supports various path formats:
   * 1. "year=2025/month=08/day=13/hour=22/STREAM-NAME/filename"
   * 2. "2025/08/13/22/STREAM-NAME/filename"  
   * 3. "someOtherStuff/2025/08/13/22/STREAM-NAME/filename"
   * 4. "STREAM-NAME/filename"
   * 
   * <p>Examples:
   * - "year%3D2025/month%3D08/day%3D13/hour%3D22/MVS-stream/file.txt" → "MVS-stream"
   * - "year=2025/month=08/day=13/hour=22/MVS-stream/file.txt" → "MVS-stream"
   * - "2025/08/13/22/MVS-stream/file.txt" → "MVS-stream"
   * - "prefix/2025/08/13/22/MVS-stream/file.txt" → "MVS-stream"
   * - "MVS-stream/file.txt" → "MVS-stream"
   *
   * @param objectKey The S3 object key (may be URL-encoded)
   * @return The extracted stream name, or "unknown" if extraction fails
   */
  public static String extractStreamName(String objectKey) {
    if (objectKey == null || objectKey.trim().isEmpty()) {
      return UNKNOWN_STREAM;
    }

    try {
      // First, URL decode the object key to handle partitioning (safe for non-encoded keys)
      String decodedKey = urlDecode(objectKey);
      
      // Split by '/' to get path components
      String[] pathComponents = decodedKey.split("/");
      
      if (pathComponents.length < 2) {
        // Need at least 2 components: stream-name/filename
        return UNKNOWN_STREAM;
      }

      // The stream name is always the second-to-last component (right before filename)
      String streamName = pathComponents[pathComponents.length - 2];
      
      // Validate the stream name
      if (isValidStreamName(streamName)) {
        return streamName;
      }

      // If validation fails, return unknown
      return UNKNOWN_STREAM;

    } catch (Exception e) {
      // Log error and return unknown
      return UNKNOWN_STREAM;
    }
  }



  /**
   * Validates if a string could be a valid stream name.
   * Very permissive - just checks for basic validity.
   */
  private static boolean isValidStreamName(String candidate) {
    return candidate != null && 
           !candidate.trim().isEmpty() && 
           candidate.length() <= 200; // reasonable length limit
  }

  /**
   * URL decodes the object key to handle Firehose partitioning.
   * Gracefully handles both URL-encoded and non-URL-encoded keys.
   * 
   * @param objectKey The potentially URL-encoded object key
   * @return The decoded object key, or original key if decoding fails
   */
  private static String urlDecode(String objectKey) {
    if (objectKey == null || objectKey.trim().isEmpty()) {
      return objectKey;
    }
    
    try {
      // Only decode if the key actually contains URL-encoded characters
      if (objectKey.contains("%")) {
        return URLDecoder.decode(objectKey, StandardCharsets.UTF_8);
      } else {
        // Return as-is if no URL encoding detected
        return objectKey;
      }
    } catch (Exception e) {
      // If decoding fails for any reason, return the original key
      // This ensures robustness when dealing with malformed or non-encoded keys
      return objectKey;
    }
  }


}
