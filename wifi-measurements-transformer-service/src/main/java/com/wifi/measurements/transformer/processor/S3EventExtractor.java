// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/S3EventExtractor.java
package com.wifi.measurements.transformer.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.measurements.transformer.dto.S3EventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Comprehensive S3 event extraction and validation service for SQS message processing.
 * 
 * <p>This service implements robust extraction and validation logic for S3 event notifications
 * contained in SQS messages. It provides comprehensive security validation, data integrity
 * checks, and defensive programming practices to ensure reliable and secure event processing.</p>
 * 
 * <p><strong>Core Features:</strong></p>
 * <ul>
 *   <li><strong>JSON Parsing:</strong> Safe JSON parsing with comprehensive error handling</li>
 *   <li><strong>Field Validation:</strong> Pattern-based validation for all critical fields</li>
 *   <li><strong>Security Validation:</strong> Protection against injection attacks and malformed data</li>
 *   <li><strong>Data Integrity:</strong> Comprehensive validation of S3 event structure and content</li>
 *   <li><strong>Error Handling:</strong> Graceful handling of parsing and validation failures</li>
 * </ul>
 * 
 * <p><strong>Validation Strategy:</strong></p>
 * <ul>
 *   <li><strong>Event Structure:</strong> Validates required S3 event notification structure</li>
 *   <li><strong>Field Formats:</strong> Pattern-based validation for UUIDs, regions, ARNs, etc.</li>
 *   <li><strong>Time Validation:</strong> Ensures timestamps are within reasonable bounds</li>
 *   <li><strong>Resource Validation:</strong> Validates S3 bucket and object references</li>
 *   <li><strong>Size Validation:</strong> Validates object sizes and other numeric fields</li>
 * </ul>
 * 
 * <p><strong>Security Features:</strong></p>
 * <ul>
 *   <li>Pattern-based validation to prevent injection attacks</li>
 *   <li>Defensive programming practices throughout</li>
 *   <li>Comprehensive input sanitization and validation</li>
 *   <li>Safe JSON parsing with exception handling</li>
 * </ul>
 * 
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>Returns Optional.empty() for any validation or parsing failures</li>
 *   <li>Comprehensive logging for debugging and monitoring</li>
 *   <li>Graceful degradation - failures don't affect other message processing</li>
 *   <li>Detailed error messages for troubleshooting</li>
 * </ul>
 * 
 * <p>This service is designed to be stateless and thread-safe, supporting concurrent
 * extraction requests while maintaining strict validation standards.</p>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class S3EventExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(S3EventExtractor.class);
    private final ObjectMapper objectMapper;
    
    // Validation patterns
    private static final Pattern UUID_PATTERN = Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$");
    private static final Pattern REGION_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    private static final Pattern S3_ARN_PATTERN = Pattern.compile("^arn:aws:s3:::[a-zA-Z0-9.-]+$");
    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9.-]*[a-z0-9]$");
    private static final Pattern ETAG_PATTERN = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern VERSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    
    public S3EventExtractor() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Extracts and validates S3 event information from a JSON message body.
     * 
     * <p>This method is the main entry point for S3 event extraction. It performs comprehensive
     * validation of the message body, JSON structure, and S3 event fields to ensure data
     * integrity and security before returning the extracted event information.</p>
     * 
     * <p><strong>Processing Steps:</strong></p>
     * <ol>
     *   <li><strong>Input Validation:</strong> Validates message body is not null or empty</li>
     *   <li><strong>JSON Parsing:</strong> Safely parses JSON message body to JsonNode</li>
     *   <li><strong>Structure Validation:</strong> Validates S3 event notification structure</li>
     *   <li><strong>Field Extraction:</strong> Extracts and validates all required fields</li>
     *   <li><strong>Record Creation:</strong> Creates S3EventRecord with validated data</li>
     * </ol>
     * 
     * <p><strong>Validation Checks:</strong></p>
     * <ul>
     *   <li>Message body format and content validation</li>
     *   <li>S3 event notification structure validation</li>
     *   <li>Required field presence and format validation</li>
     *   <li>Timestamp range and format validation</li>
     *   <li>Resource ARN and bucket name validation</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>Returns Optional.empty() for any validation or parsing failures</li>
     *   <li>Logs detailed error information for debugging</li>
     *   <li>Graceful handling of malformed or invalid messages</li>
     * </ul>
     * 
     * @param messageBody The JSON message body containing S3 event notification
     * @return Optional containing S3EventRecord if extraction and validation succeeds, empty otherwise
     * @throws IllegalArgumentException if messageBody is null (though this is handled gracefully)
     */
    public Optional<S3EventRecord> extractS3Event(String messageBody) {
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
    
    /**
     * Safely parses JSON string to JsonNode.
     */
    private JsonNode parseJsonSafely(String messageBody) {
        try {
            return objectMapper.readTree(messageBody);
        } catch (Exception e) {
            logger.error("Error parsing JSON message body", e);
            return null;
        }
    }
    
    /**
     * Checks if the event has the required S3 structure.
     */
    private boolean hasRequiredS3Fields(JsonNode eventNode) {
        return Optional.ofNullable(eventNode)
            .filter(node -> "Object Created".equals(getTextValue(node, "detail-type")))
            .filter(node -> "aws.s3".equals(getTextValue(node, "source")))
            .map(node -> node.get("detail"))
            .filter(detail -> detail.has("bucket") && detail.has("object"))
            .isPresent();
    }
    
    /**
     * Extracts S3EventRecord from validated JsonNode.
     */
    private Optional<S3EventRecord> extractS3EventRecord(JsonNode eventNode) {
        try {
            String id = extractValidatedField(eventNode, "id", UUID_PATTERN, "Invalid event ID format");
            Instant time = extractTime(eventNode);
            String region = extractValidatedField(eventNode, "region", REGION_PATTERN, "Invalid region format");
            List<String> resources = extractResources(eventNode);
            
            JsonNode detail = eventNode.get("detail");
            String bucketName = extractBucketName(detail);
            String objectKey = extractObjectKey(detail);
            long objectSize = extractObjectSize(detail);
            String etag = extractOptionalField(detail.get("object"), "etag", ETAG_PATTERN);
            String versionId = extractOptionalField(detail.get("object"), "version-id", VERSION_ID_PATTERN);
            String requestId = extractOptionalField(detail, "request-id", UUID_PATTERN);
            
            S3EventRecord record = S3EventRecord.of(
                id, time, region, resources, bucketName, objectKey,
                objectSize, etag, versionId, requestId
            );
            
            logger.debug("Successfully extracted S3 event: bucket={}, key={}, stream={}", 
                        bucketName, objectKey, record.streamName());
            
            return Optional.of(record);
            
        } catch (Exception e) {
            logger.error("Error extracting S3 event from message body", e);
            return Optional.empty();
        }
    }
    
    /**
     * Generic method to extract and validate a required field.
     */
    private String extractValidatedField(JsonNode node, String fieldName, Pattern pattern, String errorMessage) {
        return Optional.ofNullable(getTextValue(node, fieldName))
            .filter(value -> !value.trim().isEmpty())
            .filter(value -> pattern.matcher(value).matches())
            .orElseThrow(() -> new IllegalArgumentException(errorMessage + " for field: " + fieldName));
    }
    
    /**
     * Generic method to extract and validate an optional field.
     */
    private String extractOptionalField(JsonNode node, String fieldName, Pattern pattern) {
        return Optional.ofNullable(node)
            .map(n -> getTextValue(n, fieldName))
            .filter(value -> value != null && !value.trim().isEmpty())
            .filter(value -> pattern.matcher(value).matches())
            .orElse(null);
    }
    
    /**
     * Safely extracts text value from JsonNode.
     */
    private String getTextValue(JsonNode node, String fieldName) {
        return Optional.ofNullable(node)
            .filter(n -> n.has(fieldName))
            .map(n -> n.get(fieldName))
            .map(JsonNode::asText)
            .orElse(null);
    }
    
    /**
     * Extracts and validates timestamp.
     */
    private Instant extractTime(JsonNode eventNode) {
        String timeStr = Optional.ofNullable(getTextValue(eventNode, "time"))
            .filter(time -> !time.trim().isEmpty())
            .orElseThrow(() -> new IllegalArgumentException("Missing or empty event time"));
        
        try {
            Instant time = Instant.parse(timeStr);
            validateTimeRange(time);
            return time;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid event time format: " + timeStr, e);
        }
    }
    
    /**
     * Validates that timestamp is within reasonable range.
     */
    private void validateTimeRange(Instant time) {
        Instant now = Instant.now();
        Instant minTime = now.minusSeconds(365 * 24 * 60 * 60); // 1 year ago
        Instant maxTime = now.plusSeconds(24 * 60 * 60); // 1 day in future
        
        if (time.isBefore(minTime) || time.isAfter(maxTime)) {
            throw new IllegalArgumentException("Event time is outside reasonable range");
        }
    }
    
    /**
     * Extracts and validates resources array.
     */
    private List<String> extractResources(JsonNode eventNode) {
        return Optional.ofNullable(eventNode.get("resources"))
            .filter(JsonNode::isArray)
            .map(this::parseResourcesArray)
            .orElseThrow(() -> new IllegalArgumentException("Missing or invalid resources array"));
    }
    
    /**
     * Parses resources array and validates S3 ARNs.
     */
    private List<String> parseResourcesArray(JsonNode resourcesArray) {
        List<String> resources = new ArrayList<>();
        for (JsonNode resourceNode : resourcesArray) {
            Optional.ofNullable(resourceNode.asText())
                .filter(resource -> !resource.trim().isEmpty())
                .filter(resource -> S3_ARN_PATTERN.matcher(resource).matches())
                .ifPresent(resources::add);
        }
        return resources;
    }
    
    /**
     * Extracts bucket name with validation.
     */
    private String extractBucketName(JsonNode detail) {
        return Optional.ofNullable(detail.get("bucket"))
            .map(bucket -> getTextValue(bucket, "name"))
            .filter(name -> name != null && !name.trim().isEmpty())
            .filter(name -> BUCKET_NAME_PATTERN.matcher(name).matches() && name.length() <= 63)
            .orElseThrow(() -> new IllegalArgumentException("Invalid bucket name"));
    }
    
    /**
     * Extracts object key with security validation.
     */
    private String extractObjectKey(JsonNode detail) {
        return Optional.ofNullable(detail.get("object"))
            .map(object -> getTextValue(object, "key"))
            .filter(key -> key != null && !key.trim().isEmpty())
            .filter(key -> key.length() <= 1024)
            .filter(key -> !key.contains("..") && !key.contains("//"))
            .orElseThrow(() -> new IllegalArgumentException("Invalid object key"));
    }
    
    /**
     * Extracts object size with range validation.
     */
    private long extractObjectSize(JsonNode detail) {
        return Optional.ofNullable(detail.get("object"))
            .filter(object -> object.has("size"))
            .map(object -> object.get("size").asLong())
            .filter(size -> size >= 0 && size <= 5_368_709_120L) // 5GB max
            .orElseThrow(() -> new IllegalArgumentException("Invalid object size"));
    }
} 