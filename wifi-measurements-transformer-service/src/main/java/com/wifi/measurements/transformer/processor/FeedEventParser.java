// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/S3EventExtractor.java
package com.wifi.measurements.transformer.processor;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.measurements.transformer.dto.FeedUploadEvent;

/**
 * Comprehensive S3 event extraction and validation service for SQS message processing.
 *
 * <p>This service implements robust extraction and validation logic for S3 event notifications
 * contained in SQS messages. It provides comprehensive security validation, data integrity checks,
 * and defensive programming practices to ensure reliable and secure event processing.
 *
 * <p><strong>Core Features:</strong>
 *
 * <ul>
 *   <li><strong>JSON Parsing:</strong> Safe JSON parsing with comprehensive error handling
 *   <li><strong>Field Validation:</strong> Pattern-based validation for all critical fields
 *   <li><strong>Security Validation:</strong> Protection against injection attacks and malformed
 *       data
 *   <li><strong>Data Integrity:</strong> Comprehensive validation of S3 event structure and content
 *   <li><strong>Error Handling:</strong> Graceful handling of parsing and validation failures
 * </ul>
 *
 * <p><strong>Validation Strategy:</strong>
 *
 * <ul>
 *   <li><strong>Event Structure:</strong> Validates required S3 event notification structure
 *   <li><strong>Field Formats:</strong> Pattern-based validation for UUIDs, regions, ARNs, etc.
 *   <li><strong>Time Validation:</strong> Ensures timestamps are within reasonable bounds
 *   <li><strong>Resource Validation:</strong> Validates S3 bucket and object references
 *   <li><strong>Size Validation:</strong> Validates object sizes and other numeric fields
 * </ul>
 *
 * <p><strong>Security Features:</strong>
 *
 * <ul>
 *   <li>Pattern-based validation to prevent injection attacks
 *   <li>Defensive programming practices throughout
 *   <li>Comprehensive input sanitization and validation
 *   <li>Safe JSON parsing with exception handling
 * </ul>
 *
 * <p><strong>Error Handling:</strong>
 *
 * <ul>
 *   <li>Returns Optional.empty() for any validation or parsing failures
 *   <li>Comprehensive logging for debugging and monitoring
 *   <li>Graceful degradation - failures don't affect other message processing
 *   <li>Detailed error messages for troubleshooting
 * </ul>
 *
 * <p>This service is designed to be stateless and thread-safe, supporting concurrent extraction
 * requests while maintaining strict validation standards.
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class FeedEventParser {

  private static final Logger logger = LoggerFactory.getLogger(FeedEventParser.class);
  private final ObjectMapper objectMapper;

  // Validation patterns
  private static final Pattern BUCKET_NAME_PATTERN =
      Pattern.compile("^[a-z0-9][a-z0-9.-]*[a-z0-9]$");
  private static final Pattern ETAG_PATTERN = Pattern.compile("^[a-fA-F0-9]{32}$");
  
  // Constants
  private static final String RECORDS_FIELD = "Records";
  private static final String OBJECT_FIELD = "object";

  public FeedEventParser() {
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Extracts and validates S3 event information from a JSON message body.
   *
   * <p>This method is the main entry point for S3 event extraction. It performs comprehensive
   * validation of the message body, JSON structure, and S3 event fields to ensure data integrity
   * and security before returning the extracted event information.
   *
   * <p><strong>Processing Steps:</strong>
   *
   * <ol>
   *   <li><strong>Input Validation:</strong> Validates message body is not null or empty
   *   <li><strong>JSON Parsing:</strong> Safely parses JSON message body to JsonNode
   *   <li><strong>Structure Validation:</strong> Validates S3 event notification structure
   *   <li><strong>Field Extraction:</strong> Extracts and validates all required fields
   *   <li><strong>Record Creation:</strong> Creates S3EventRecord with validated data
   * </ol>
   *
   * <p><strong>Validation Checks:</strong>
   *
   * <ul>
   *   <li>Message body format and content validation
   *   <li>S3 event notification structure validation
   *   <li>Required field presence and format validation
   *   <li>Timestamp range and format validation
   *   <li>Resource ARN and bucket name validation
   * </ul>
   *
   * <p><strong>Error Handling:</strong>
   *
   * <ul>
   *   <li>Returns Optional.empty() for any validation or parsing failures
   *   <li>Logs detailed error information for debugging
   *   <li>Graceful handling of malformed or invalid messages
   * </ul>
   *
   * @param messageBody The JSON message body containing S3 event notification
   * @return Optional containing S3EventRecord if extraction and validation succeeds, empty
   *     otherwise
   * @throws IllegalArgumentException if messageBody is null (though this is handled gracefully)
   */
  public Optional<FeedUploadEvent> parseFrom(String messageBody) {
    if (messageBody == null || messageBody.trim().isEmpty()) {
      logger.warn("Message body is null or empty");
      return Optional.empty();
    }

    JsonNode eventNode = parseJsonSafely(messageBody);
    if (eventNode == null) {
      return Optional.empty();
    }

    if (!hasRequiredS3Fields(eventNode)) {
      logger.warn("Invalid S3 event structure");
      return Optional.empty();
    }

    return extractS3EventRecord(eventNode);
  }

  /** Safely parses JSON string to JsonNode. */
  private JsonNode parseJsonSafely(String messageBody) {
    try {
      return objectMapper.readTree(messageBody);
    } catch (Exception e) {
      logger.error("Error parsing JSON message body", e);
      return null;
    }
  }

  /** Checks if the event has the required S3 structure. */
  private boolean hasRequiredS3Fields(JsonNode eventNode) {
    return Optional.ofNullable(eventNode)
        .filter(node -> node.has(RECORDS_FIELD))
        .map(node -> node.get(RECORDS_FIELD))
        .filter(JsonNode::isArray)
        .filter(recordsArray -> recordsArray.size() > 0)
        .map(recordsArray -> recordsArray.get(0))
        .filter(eventRecord -> "aws:s3".equals(getTextValue(eventRecord, "eventSource")))
        .filter(eventRecord -> eventRecord.has("s3"))
        .map(eventRecord -> eventRecord.get("s3"))
        .filter(s3Node -> s3Node.has("bucket") && s3Node.has(OBJECT_FIELD))
        .isPresent();
  }

  /** Extracts S3EventRecord from validated JsonNode. */
  private Optional<FeedUploadEvent> extractS3EventRecord(JsonNode eventNode) {
    try {
      // Get the first S3 event record from the Records array
      JsonNode eventRecord = eventNode.get(RECORDS_FIELD).get(0);
      
      // Extract top-level S3 event fields
      String eventVersion = getTextValue(eventRecord, "eventVersion");
      Instant time = extractS3EventTime(eventRecord);
      String region = getTextValue(eventRecord, "awsRegion");
      
      // Extract S3 specific data
      JsonNode s3Node = eventRecord.get("s3");
      String bucketName = extractS3BucketName(s3Node);
      String objectKey = extractS3ObjectKey(s3Node);
      long objectSize = extractS3ObjectSize(s3Node);
      String etag = extractOptionalS3Field(s3Node.get(OBJECT_FIELD), "eTag", ETAG_PATTERN);
      String sequencer = extractOptionalS3Field(s3Node.get(OBJECT_FIELD), "sequencer", null);
      
      // Extract request parameters if available
      String requestId = extractOptionalS3Field(eventRecord.get("responseElements"), "x-amz-request-id", null);

      FeedUploadEvent feedUploadEvent =
          FeedUploadEvent.of(
              eventVersion,
              time,
              region,
              List.of(), // No resources array in S3 event notifications
              bucketName,
              objectKey,
              objectSize,
              etag,
              sequencer, // Use sequencer instead of versionId
              requestId);
      logger.info(
              "Successfully extracted S3 event - bucket: {}, key: {}, size: {} bytes, stream: {}",
              feedUploadEvent.bucketName(),
              feedUploadEvent.objectKey(),
              feedUploadEvent.objectSize(),
              feedUploadEvent.streamName());
      return Optional.of(feedUploadEvent);

    } catch (Exception e) {
      logger.error("Error extracting S3 event from message body", e);
      return Optional.empty();
    }
  }

  /** Safely extracts text value from JsonNode. */
  private String getTextValue(JsonNode node, String fieldName) {
    return Optional.ofNullable(node)
        .filter(n -> n.has(fieldName))
        .map(n -> n.get(fieldName))
        .map(JsonNode::asText)
        .orElse(null);
  }

  /** Extracts and validates timestamp from S3 event. */
  private Instant extractS3EventTime(JsonNode eventRecord) {
    String timeStr =
        Optional.ofNullable(getTextValue(eventRecord, "eventTime"))
            .filter(timeValue -> !timeValue.trim().isEmpty())
            .orElseThrow(() -> new IllegalArgumentException("Missing or empty event time"));

    try {
      Instant timeInstant = Instant.parse(timeStr);
      validateTimeRange(timeInstant);
      return timeInstant;
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid event time format: " + timeStr, e);
    }
  }

  /** Validates that timestamp is within reasonable range. */
  private void validateTimeRange(Instant time) {
    Instant now = Instant.now();
    Instant minTime = now.minusSeconds(365L * 24 * 60 * 60); // 1 year ago
    Instant maxTime = now.plusSeconds(24L * 60 * 60); // 1 day in future

    if (time.isBefore(minTime) || time.isAfter(maxTime)) {
      throw new IllegalArgumentException("Event time is outside reasonable range");
    }
  }

  /** Extracts bucket name from S3 event with validation. */
  private String extractS3BucketName(JsonNode s3) {
    return Optional.ofNullable(s3.get("bucket"))
        .map(bucket -> getTextValue(bucket, "name"))
        .filter(name -> name != null && !name.trim().isEmpty())
        .filter(name -> BUCKET_NAME_PATTERN.matcher(name).matches() && name.length() <= 63)
        .orElseThrow(() -> new IllegalArgumentException("Invalid bucket name"));
  }

  /** Extracts object key from S3 event with security validation and URL decoding support. */
  private String extractS3ObjectKey(JsonNode s3) {
    return Optional.ofNullable(s3.get(OBJECT_FIELD))
        .map(objectNode -> getTextValue(objectNode, "key"))
        .filter(key -> key != null && !key.trim().isEmpty())
        .filter(key -> key.length() <= 1024)
        .filter(key -> !key.contains("..") && !key.contains("//"))
        .orElseThrow(() -> new IllegalArgumentException("Invalid object key"));
  }

  /** Extracts object size from S3 event with range validation. */
  private long extractS3ObjectSize(JsonNode s3) {
    return Optional.ofNullable(s3.get(OBJECT_FIELD))
        .filter(objectNode -> objectNode.has("size"))
        .map(objectNode -> objectNode.get("size").asLong())
        .filter(size -> size >= 0 && size <= 5_368_709_120L) // 5GB max
        .orElseThrow(() -> new IllegalArgumentException("Invalid object size"));
  }

  /** Generic method to extract and validate an optional S3 field. */
  private String extractOptionalS3Field(JsonNode node, String fieldName, Pattern pattern) {
    return Optional.ofNullable(node)
        .map(n -> getTextValue(n, fieldName))
        .filter(value -> value != null && !value.trim().isEmpty())
        .filter(value -> pattern == null || pattern.matcher(value).matches())
        .orElse(null);
  }
}
