// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/service/S3EventExtractorTest.java
package com.wifi.measurements.transformer.service;

import com.wifi.measurements.transformer.dto.S3EventRecord;
import com.wifi.measurements.transformer.processor.S3EventExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for S3EventExtractor.
 * 
 * Tests cover:
 * - Valid S3 event extraction
 * - Field validation
 * - Security validation
 * - Edge cases and error handling
 * - Stream name extraction
 */
class S3EventExtractorTest {

    private S3EventExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new S3EventExtractor();
    }

    @Test
    @DisplayName("Should extract valid S3 event successfully")
    void shouldExtractValidS3Event() {
        // Given
        String validMessage = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "account": "000000000000",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "resources": [
                "arn:aws:s3:::ingested-wifiscan-data"
              ],
              "detail": {
                "version": "0",
                "bucket": {
                  "name": "ingested-wifiscan-data"
                },
                "object": {
                  "key": "MVS-stream/2025/07/28/19/MVS-stream-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.txt",
                  "size": 2463,
                  "etag": "7cf201071659efefb45197abb52fcb92",
                  "sequencer": "0062E99A88DC407460",
                  "version-id": "AZhScmWkZshiDT25IpYSNfoNoJhDpAVb"
                },
                "request-id": "3ebe7d3d-fdc6-4821-a4b5-bf0ff1972450",
                "requester": "074255357339",
                "source-ip-address": "127.0.0.1",
                "reason": "PutObject"
              }
            }
            """;

        // When
        Optional<S3EventRecord> result = extractor.extractS3Event(validMessage);

        // Then
        assertTrue(result.isPresent());
        S3EventRecord record = result.get();
        
        assertEquals("bae85d73-bf72-4251-85c8-a2af9c4721f3", record.id());
        assertEquals(Instant.parse("2025-07-28T19:12:23Z"), record.time());
        assertEquals("us-east-1", record.region());
        assertEquals(List.of("arn:aws:s3:::ingested-wifiscan-data"), record.resources());
        assertEquals("ingested-wifiscan-data", record.bucketName());
        assertEquals("MVS-stream/2025/07/28/19/MVS-stream-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.txt", record.objectKey());
        assertEquals(2463L, record.objectSize());
        assertEquals("7cf201071659efefb45197abb52fcb92", record.etag());
        assertEquals("AZhScmWkZshiDT25IpYSNfoNoJhDpAVb", record.versionId());
        assertEquals("3ebe7d3d-fdc6-4821-a4b5-bf0ff1972450", record.requestId());
        assertEquals("MVS-stream", record.streamName());
    }

    @Test
    @DisplayName("Should extract stream name correctly from object key")
    void shouldExtractStreamNameCorrectly() {
        // Test various stream name extraction scenarios
        String[] testCases = {
            // Standard case
            "MVS-stream/2025/07/28/19/MVS-stream-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.txt",
            // Different stream name
            "GPS-data/2025/07/28/19/GPS-data-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.json",
            // Stream name with numbers
            "Stream123/2025/07/28/19/Stream123-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.csv",
            // Stream name with underscores
            "wifi_scan_data/2025/07/28/19/wifi_scan_data-2025-07-28-19-12-23-15993907-a5fe-4793-8182-064acc85cf20.txt"
        };

        String[] expectedStreamNames = {
            "MVS-stream",
            "GPS-data", 
            "Stream123",
            "wifi_scan_data"
        };

        for (int i = 0; i < testCases.length; i++) {
            String objectKey = testCases[i];
            String expectedStreamName = expectedStreamNames[i];
            
            String actualStreamName = S3EventRecord.extractStreamName(objectKey);
            assertEquals(expectedStreamName, actualStreamName, 
                "Failed for object key: " + objectKey);
        }
    }

    @Test
    @DisplayName("Should handle edge cases for stream name extraction")
    void shouldHandleEdgeCasesForStreamNameExtraction() {
        // Test edge cases
        assertEquals("unknown", S3EventRecord.extractStreamName(null));
        assertEquals("unknown", S3EventRecord.extractStreamName(""));
        assertEquals("unknown", S3EventRecord.extractStreamName("   "));
        
        // No datetime pattern
        assertEquals("filename.txt", S3EventRecord.extractStreamName("filename.txt"));
        assertEquals("complex-name.json", S3EventRecord.extractStreamName("complex-name.json"));
        
        // Root level file
        assertEquals("file.txt", S3EventRecord.extractStreamName("file.txt"));
        
        // Deep path
        assertEquals("stream", S3EventRecord.extractStreamName("path/to/deep/stream-2025-07-28-19-12-23.txt"));
    }

    @Test
    @DisplayName("Should return empty for null or empty message body")
    void shouldReturnEmptyForNullOrEmptyMessageBody() {
        assertTrue(extractor.extractS3Event(null).isEmpty());
        assertTrue(extractor.extractS3Event("").isEmpty());
        assertTrue(extractor.extractS3Event("   ").isEmpty());
    }

    @Test
    @DisplayName("Should return empty for invalid JSON")
    void shouldReturnEmptyForInvalidJson() {
        assertTrue(extractor.extractS3Event("invalid json").isEmpty());
        assertTrue(extractor.extractS3Event("{").isEmpty());
        assertTrue(extractor.extractS3Event("null").isEmpty());
    }

    @Test
    @DisplayName("Should return empty for missing required fields")
    void shouldReturnEmptyForMissingRequiredFields() {
        String missingDetailType = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(missingDetailType).isEmpty());
    }

    @Test
    @DisplayName("Should return empty for wrong event type")
    void shouldReturnEmptyForWrongEventType() {
        String wrongEventType = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Deleted",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(wrongEventType).isEmpty());
    }

    @Test
    @DisplayName("Should return empty for wrong source")
    void shouldReturnEmptyForWrongSource() {
        String wrongSource = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.lambda",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(wrongSource).isEmpty());
    }

    @Test
    @DisplayName("Should validate event ID format")
    void shouldValidateEventIdFormat() {
        String invalidId = """
            {
              "version": "0",
              "id": "invalid-uuid",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(invalidId).isEmpty());
    }

    @Test
    @DisplayName("Should validate event time format and range")
    void shouldValidateEventTimeFormatAndRange() {
        // Invalid time format
        String invalidTimeFormat = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "invalid-time",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(invalidTimeFormat).isEmpty());
        
        // Time too far in the past
        String pastTime = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2020-01-01T00:00:00Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(pastTime).isEmpty());
        
        // Time too far in the future
        String futureTime = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2030-01-01T00:00:00Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(futureTime).isEmpty());
    }

    @Test
    @DisplayName("Should validate region format")
    void shouldValidateRegionFormat() {
        String invalidRegion = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "invalid_region",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(invalidRegion).isEmpty());
    }

    @Test
    @DisplayName("Should validate bucket name format")
    void shouldValidateBucketNameFormat() {
        String invalidBucketName = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "Invalid_Bucket_Name"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(invalidBucketName).isEmpty());
    }

    @Test
    @DisplayName("Should validate object key security")
    void shouldValidateObjectKeySecurity() {
        String dangerousKey = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "../../../etc/passwd", "size": 100}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(dangerousKey).isEmpty());
    }

    @Test
    @DisplayName("Should validate object size range")
    void shouldValidateObjectSizeRange() {
        // Negative size
        String negativeSize = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": -1}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(negativeSize).isEmpty());
        
        // Too large size
        String tooLargeSize = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 10000000000}
              }
            }
            """;
        
        assertTrue(extractor.extractS3Event(tooLargeSize).isEmpty());
    }

    @Test
    @DisplayName("Should handle optional fields gracefully")
    void shouldHandleOptionalFieldsGracefully() {
        String minimalMessage = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "resources": ["arn:aws:s3:::test-bucket"],
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        Optional<S3EventRecord> result = extractor.extractS3Event(minimalMessage);
        assertTrue(result.isPresent());
        
        S3EventRecord record = result.get();
        assertNull(record.etag());
        assertNull(record.versionId());
        assertNull(record.requestId());
    }

    @Test
    @DisplayName("Should validate ETag format when present")
    void shouldValidateEtagFormatWhenPresent() {
        String invalidEtag = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "resources": ["arn:aws:s3:::test-bucket"],
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {
                  "key": "test-key", 
                  "size": 100,
                  "etag": "invalid-etag"
                }
              }
            }
            """;
        
        Optional<S3EventRecord> result = extractor.extractS3Event(invalidEtag);
        assertTrue(result.isPresent());
        assertNull(result.get().etag()); // Should be null for invalid ETag
    }

    @Test
    @DisplayName("Should validate request ID format when present")
    void shouldValidateRequestIdFormatWhenPresent() {
        String invalidRequestId = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "resources": ["arn:aws:s3:::test-bucket"],
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100},
                "request-id": "invalid-request-id"
              }
            }
            """;
        
        Optional<S3EventRecord> result = extractor.extractS3Event(invalidRequestId);
        assertTrue(result.isPresent());
        assertNull(result.get().requestId()); // Should be null for invalid request ID
    }

    @Test
    @DisplayName("Should handle resources array with invalid ARNs")
    void shouldHandleResourcesArrayWithInvalidArns() {
        String invalidResources = """
            {
              "version": "0",
              "id": "bae85d73-bf72-4251-85c8-a2af9c4721f3",
              "detail-type": "Object Created",
              "source": "aws.s3",
              "time": "2025-07-28T19:12:23Z",
              "region": "us-east-1",
              "resources": [
                "arn:aws:s3:::valid-bucket",
                "invalid-arn",
                "arn:aws:lambda:::invalid-service"
              ],
              "detail": {
                "bucket": {"name": "test-bucket"},
                "object": {"key": "test-key", "size": 100}
              }
            }
            """;
        
        Optional<S3EventRecord> result = extractor.extractS3Event(invalidResources);
        assertTrue(result.isPresent());
        
        List<String> resources = result.get().resources();
        assertEquals(1, resources.size());
        assertEquals("arn:aws:s3:::valid-bucket", resources.get(0));
    }
} 