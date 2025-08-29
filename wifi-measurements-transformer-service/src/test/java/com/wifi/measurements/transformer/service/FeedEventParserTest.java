// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/S3EventExtractorTest.java
package com.wifi.measurements.transformer.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wifi.measurements.transformer.dto.FeedUploadEvent;
import com.wifi.measurements.transformer.processor.FeedEventParser;

/**
 * Comprehensive unit tests for S3EventExtractor using S3 Event Notification format.
 *
 * <p>Tests cover: - Valid S3 event extraction - Field validation - Security validation - Edge cases
 * and error handling - Stream name extraction with URL decoding
 */
class FeedEventParserTest {

  private FeedEventParser extractor;

  @BeforeEach
  void setUp() {
    extractor = new FeedEventParser();
  }

  @Test
  @DisplayName("Should extract valid S3 event successfully")
  void shouldExtractValidS3Event() {
    // Given
    String validMessage =
        """
            {
              "Records": [
                {
                  "eventVersion": "2.1",
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "eventName": "ObjectCreated:Put",
                  "userIdentity": {
                    "principalId": "AWS:AROA4QWKES4Y24IUPAV2J:AWSFirehoseToS3"
                  },
                  "requestParameters": {
                    "sourceIPAddress": "10.20.19.21"
                  },
                  "responseElements": {
                    "x-amz-request-id": "8C3VR6DWXN808YJP",
                    "x-amz-id-2": "2BdlIpJXKQCEI7siGhF3KCU9M59dye7AJcn63aIjkANLeVX+9EFIJ7qzipO/g3RJFVIK5E7a20PqWDccojmXUmLJHK00bHFvRHDhbb9LMnw="
                  },
                  "s3": {
                    "s3SchemaVersion": "1.0",
                    "configurationId": "NjgyMTJiZTUtNDMwZC00OTVjLWIzOWEtM2UzZWM3MzYwNGE2",
                    "bucket": {
                      "name": "vsf-dev-oh-frisco-ingested-wifiscan-data",
                      "ownerIdentity": {
                        "principalId": "A3LJZCR20GC5IX"
                      },
                      "arn": "arn:aws:s3:::vsf-dev-oh-frisco-ingested-wifiscan-data"
                    },
                    "object": {
                      "key": "year%3D2025/month%3D08/day%3D13/hour%3D22/MVS-stream/frisco-wifiscan-mvs-stream-4-2025-08-13-22-29-19-176e8954-348c-4c9b-8798-1fc44872c0ad",
                      "size": 1049040,
                      "eTag": "af789ccf52246f4d7c9eea1925176409",
                      "sequencer": "00689D11F29F29282F"
                    }
                  }
                }
              ]
            }
            """;

    // When
    Optional<FeedUploadEvent> result = extractor.parseFrom(validMessage);

    // Then
    assertTrue(result.isPresent());
    FeedUploadEvent eventRecord = result.get();

    assertEquals("2.1", eventRecord.id()); // eventVersion
    assertEquals(Instant.parse("2025-08-13T22:30:10.778Z"), eventRecord.time());
    assertEquals("us-east-2", eventRecord.region());
    assertEquals(List.of(), eventRecord.resources()); // No resources in S3 event notifications
    assertEquals("vsf-dev-oh-frisco-ingested-wifiscan-data", eventRecord.bucketName());
    assertEquals(
        "year%3D2025/month%3D08/day%3D13/hour%3D22/MVS-stream/frisco-wifiscan-mvs-stream-4-2025-08-13-22-29-19-176e8954-348c-4c9b-8798-1fc44872c0ad",
        eventRecord.objectKey());
    assertEquals(1049040L, eventRecord.objectSize());
    assertEquals("af789ccf52246f4d7c9eea1925176409", eventRecord.etag());
    assertEquals("00689D11F29F29282F", eventRecord.versionId()); // sequencer
    assertEquals("8C3VR6DWXN808YJP", eventRecord.requestId());
    assertEquals("MVS-stream", eventRecord.streamName());
  }

  @Test
  @DisplayName("Should extract stream name correctly from various object key formats")
  void shouldExtractStreamNameFromVariousFormats() {
    // Test cases covering all supported formats - stream name is always right before filename
    String[][] testCases = {
      // Format: [objectKey, expectedStreamName, description]
      
      // Firehose partitioned with URL encoding
      {"year%3D2025/month%3D08/day%3D13/hour%3D22/MVS-stream/frisco-wifiscan-mvs-stream-4-2025-08-13-22-29-19.txt", "MVS-stream", "URL-encoded partitioned"},
      {"year%3D2025/month%3D08/day%3D13/hour%3D22/GPS-data/gps-data-file.json", "GPS-data", "URL-encoded with different stream"},
      {"year%3D2025/month%3D08/day%3D13/hour%3D22/wifi_scan_data/wifi-scan-file.txt", "wifi_scan_data", "URL-encoded with underscores"},
      
      // Firehose partitioned without URL encoding
      {"year=2025/month=08/day=13/hour=22/MVS-stream/frisco-wifiscan-mvs-stream-4-2025-08-13-22-29-19.txt", "MVS-stream", "Non-encoded partitioned"},
      {"year=2025/month=08/day=13/hour=22/Stream123/stream123-file.csv", "Stream123", "Non-encoded with numbers"},
      {"year=2025/month=08/day=13/hour=22/sensor-data/sensor-readings.txt", "sensor-data", "Non-encoded with hyphens"},
      
      // Date-only format
      {"2025/08/13/22/MVS-stream/file.txt", "MVS-stream", "Date path format"},
      {"2024/12/31/23/GPS-data/data.json", "GPS-data", "Date path different stream"},
      
      // Prefixed format  
      {"prefix/2025/08/13/22/MVS-stream/file.txt", "MVS-stream", "Prefixed path format"},
      {"someOtherStuff/2025/08/13/22/sensor-data/readings.csv", "sensor-data", "Complex prefix format"},
      
      // Simple format
      {"MVS-stream/simple-file.txt", "MVS-stream", "Simple format"},
      {"sensor_readings/data.csv", "sensor_readings", "Simple with underscores"},
      {"stream-name/file.json", "stream-name", "Simple with hyphens"}
    };

    for (String[] testCase : testCases) {
      String objectKey = testCase[0];
      String expectedStreamName = testCase[1];
      String description = testCase[2];

      String actualStreamName = FeedUploadEvent.extractStreamName(objectKey);
      assertEquals(expectedStreamName, actualStreamName, 
          String.format("Failed for %s: %s", description, objectKey));
    }
  }

  @Test
  @DisplayName("Should handle edge cases and error conditions robustly")
  void shouldHandleEdgeCasesRobustly() {
    // Null and empty cases
    assertEquals("unknown", FeedUploadEvent.extractStreamName(null));
    assertEquals("unknown", FeedUploadEvent.extractStreamName(""));
    assertEquals("unknown", FeedUploadEvent.extractStreamName("   "));

    // Single component (no stream name possible)
    assertEquals("unknown", FeedUploadEvent.extractStreamName("filename.txt"));
    assertEquals("unknown", FeedUploadEvent.extractStreamName("just-a-file"));

    // Invalid URL encoding (should gracefully handle by returning original)
    assertEquals("MVS-stream", FeedUploadEvent.extractStreamName("year%3D2025/month%3D08/day%3D13/hour%3D22/MVS-stream/file.txt")); // valid
    assertEquals("stream", FeedUploadEvent.extractStreamName("year%ZZ2025/month%3D08/day%3D13/hour%3D22/stream/file.txt")); // invalid encoding, still works

    // Valid long stream name
    String longStreamName = "valid-stream-name-with-many-components";
    String longKey = "year%3D2025/month%3D08/day%3D13/hour%3D22/" + longStreamName + "/file.txt";
    assertEquals(longStreamName, FeedUploadEvent.extractStreamName(longKey));

    // Very long stream name (should be rejected)
    String tooLongStreamName = "a".repeat(250);
    String tooLongKey = "year%3D2025/month%3D08/day%3D13/hour%3D22/" + tooLongStreamName + "/file.txt";
    assertEquals("unknown", FeedUploadEvent.extractStreamName(tooLongKey));

    // Empty path components (double slash creates empty component)
    assertEquals("unknown", FeedUploadEvent.extractStreamName("prefix/stream//file.txt")); // double slash creates empty component before filename
    assertEquals("unknown", FeedUploadEvent.extractStreamName("//file.txt")); // starts with slashes, not enough components
    
    // Stream names that could be edge cases but are now accepted
    assertEquals("2025", FeedUploadEvent.extractStreamName("prefix/2025/file.txt")); // numbers are now accepted
    assertEquals("month=08", FeedUploadEvent.extractStreamName("prefix/month=08/file.txt")); // partition-looking names are accepted
  }

  @Test
  @DisplayName("Should handle special characters and encoding edge cases")
  void shouldHandleSpecialCharactersAndEncoding() {
    // Test cases with special characters and encoding edge cases
    String[][] specialCases = {
      // Mixed encoding/non-encoding scenarios
      {"year=2025/month%3D08/day=13/hour%3D22/stream-name/file.txt", "stream-name", "Mixed encoding"},
      
      // No URL encoding at all
      {"year=2025/month=08/day=13/hour=22/stream-name/file.txt", "stream-name", "No encoding"},
      
      // Stream names with special characters
      {"year=2025/month=08/day=13/hour=22/stream-name_v2/file.txt", "stream-name_v2", "Underscores in stream"},
      {"year=2025/month=08/day=13/hour=22/stream-name-v2/file.txt", "stream-name-v2", "Hyphens in stream"},
      {"prefix/stream_name_v2/file.txt", "stream_name_v2", "Simple with special chars"},
      
      // Simple format edge cases
      {"stream.name/file.txt", "stream.name", "Dots in stream name"},
      {"stream@domain/file.txt", "stream@domain", "At symbol in stream name"},
      
      // Files without extension
      {"stream-name/datafile", "stream-name", "No file extension"},
      {"complex-stream/data", "complex-stream", "No extension complex"}
    };

    for (String[] testCase : specialCases) {
      String objectKey = testCase[0];
      String expectedStreamName = testCase[1];
      String description = testCase[2];

      String actualStreamName = FeedUploadEvent.extractStreamName(objectKey);
      assertEquals(expectedStreamName, actualStreamName, 
          String.format("Failed for %s: %s", description, objectKey));
    }
  }

  @Test
  @DisplayName("Should return empty for null or empty message body")
  void shouldReturnEmptyForNullOrEmptyMessageBody() {
    assertTrue(extractor.parseFrom(null).isEmpty());
    assertTrue(extractor.parseFrom("").isEmpty());
    assertTrue(extractor.parseFrom("   ").isEmpty());
  }

  @Test
  @DisplayName("Should return empty for invalid JSON")
  void shouldReturnEmptyForInvalidJson() {
    assertTrue(extractor.parseFrom("invalid json").isEmpty());
    assertTrue(extractor.parseFrom("{").isEmpty());
    assertTrue(extractor.parseFrom("null").isEmpty());
  }

  @Test
  @DisplayName("Should return empty for missing Records array")
  void shouldReturnEmptyForMissingRecordsArray() {
    String missingRecords =
        """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "source": "aws:s3"
            }
            """;

    assertTrue(extractor.parseFrom(missingRecords).isEmpty());
  }

  @Test
  @DisplayName("Should return empty for empty Records array")
  void shouldReturnEmptyForEmptyRecordsArray() {
    String emptyRecords =
        """
            {
              "Records": []
            }
            """;

    assertTrue(extractor.parseFrom(emptyRecords).isEmpty());
  }

  @Test
  @DisplayName("Should return empty for wrong event source")
  void shouldReturnEmptyForWrongEventSource() {
    String wrongEventSource =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:lambda",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": 100}
                  }
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(wrongEventSource).isEmpty());
  }

  @Test
  @DisplayName("Should return empty for missing s3 section")
  void shouldReturnEmptyForMissingS3Section() {
    String missingS3 =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z"
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(missingS3).isEmpty());
  }

  @Test
  @DisplayName("Should validate event time format and range")
  void shouldValidateEventTimeFormatAndRange() {
    // Invalid time format
    String invalidTimeFormat =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "invalid-time",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": 100}
                  }
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(invalidTimeFormat).isEmpty());

    // Time too far in the past
    String pastTime =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2020-01-01T00:00:00Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": 100}
                  }
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(pastTime).isEmpty());

    // Time too far in the future
    String futureTime =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2030-01-01T00:00:00Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": 100}
                  }
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(futureTime).isEmpty());
  }

  @Test
  @DisplayName("Should validate bucket name format")
  void shouldValidateBucketNameFormat() {
    String invalidBucketName =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "s3": {
                    "bucket": {"name": "Invalid_Bucket_Name"},
                    "object": {"key": "test-key", "size": 100}
                  }
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(invalidBucketName).isEmpty());
  }

  @Test
  @DisplayName("Should validate object key security")
  void shouldValidateObjectKeySecurity() {
    String dangerousKey =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "../../../etc/passwd", "size": 100}
                  }
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(dangerousKey).isEmpty());
  }

  @Test
  @DisplayName("Should validate object size range")
  void shouldValidateObjectSizeRange() {
    // Negative size
    String negativeSize =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": -1}
                  }
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(negativeSize).isEmpty());

    // Too large size
    String tooLargeSize =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": 10000000000}
                  }
                }
              ]
            }
            """;

    assertTrue(extractor.parseFrom(tooLargeSize).isEmpty());
  }

  @Test
  @DisplayName("Should handle optional fields gracefully")
  void shouldHandleOptionalFieldsGracefully() {
    String minimalMessage =
        """
            {
              "Records": [
                {
                  "eventVersion": "2.1",
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": 100}
                  }
                }
              ]
            }
            """;

    Optional<FeedUploadEvent> result = extractor.parseFrom(minimalMessage);
    assertTrue(result.isPresent());

    FeedUploadEvent eventRecord = result.get();
    assertNull(eventRecord.etag());
    assertNull(eventRecord.versionId()); // sequencer
    assertNull(eventRecord.requestId());
  }

  @Test
  @DisplayName("Should validate ETag format when present")
  void shouldValidateEtagFormatWhenPresent() {
    String invalidEtag =
        """
            {
              "Records": [
                {
                  "eventVersion": "2.1",
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {
                      "key": "test-key",
                      "size": 100,
                      "eTag": "invalid-etag"
                    }
                  }
                }
              ]
            }
            """;

    Optional<FeedUploadEvent> result = extractor.parseFrom(invalidEtag);
    assertTrue(result.isPresent());
    assertNull(result.get().etag()); // Should be null for invalid ETag
  }

  @Test
  @DisplayName("Should extract request ID from responseElements when present")
  void shouldExtractRequestIdFromResponseElements() {
    String messageWithRequestId =
        """
            {
              "Records": [
                {
                  "eventVersion": "2.1",
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "responseElements": {
                    "x-amz-request-id": "8C3VR6DWXN808YJP"
                  },
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": 100}
                  }
                }
              ]
            }
            """;

    Optional<FeedUploadEvent> result = extractor.parseFrom(messageWithRequestId);
    assertTrue(result.isPresent());
    assertEquals("8C3VR6DWXN808YJP", result.get().requestId());
  }

  @Test
  @DisplayName("Should handle missing eventVersion gracefully")
  void shouldHandleMissingEventVersionGracefully() {
    String missingEventVersion =
        """
            {
              "Records": [
                {
                  "eventSource": "aws:s3",
                  "awsRegion": "us-east-2",
                  "eventTime": "2025-08-13T22:30:10.778Z",
                  "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {"key": "test-key", "size": 100}
                  }
                }
              ]
            }
            """;

    Optional<FeedUploadEvent> result = extractor.parseFrom(missingEventVersion);
    assertTrue(result.isPresent());
    assertNull(result.get().id()); // eventVersion should be null
  }
}