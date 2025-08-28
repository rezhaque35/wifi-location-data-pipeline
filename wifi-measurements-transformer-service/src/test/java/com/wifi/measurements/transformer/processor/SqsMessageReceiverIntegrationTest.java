// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/processor/SqsMessageReceiverIntegrationTest.java
package com.wifi.measurements.transformer.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClient;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.*;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Optimized integration test for SQS Message Receiver using direct MessageProcessor approach.
 *
 * <p>Tests message processing scenarios efficiently without long-running processes,
 * validates core functionality using mocked AWS clients without external dependencies.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = com.wifi.measurements.transformer.WifiMeasurementsTransformerApplication.class)
@ActiveProfiles("test")
class SqsMessageReceiverIntegrationTest {

  private static final Logger logger =
      LoggerFactory.getLogger(SqsMessageReceiverIntegrationTest.class);

  @MockitoBean private SqsClient sqsClient;
  @MockitoBean private SqsAsyncClient sqsAsyncClient;
  @MockitoBean private S3Client s3Client;
  @MockitoBean private FirehoseClient firehoseClient;
  @MockitoBean private FirehoseAsyncClient firehoseAsyncClient;

  @Autowired private MessageProcessor messageProcessor;

  private String queueUrl;
  private int mockQueueMessageCount = 0;
  private java.util.concurrent.ConcurrentHashMap<String, String> mockS3Objects;

  @BeforeEach
  void setUp() {
    // Setup mock data
    mockQueueMessageCount = 0;
    mockS3Objects = new java.util.concurrent.ConcurrentHashMap<>();
    queueUrl = "http://localhost:4566/000000000000/test-queue";

    // Setup mock behaviors
    setupMockSqsClient();
    setupMockSqsAsyncClient();
    setupMockS3Client();
    setupMockFirehoseClient();
    setupMockFirehoseAsyncClient();

    logger.info("Test setup completed with queue: {}", queueUrl);
  }

  @AfterEach
  void tearDown() {
    // Clean up test data
    mockQueueMessageCount = 0;
    if (mockS3Objects != null) {
      mockS3Objects.clear();
    }
  }

  // Mock setup methods
  
  private void setupMockSqsClient() {
    // Mock SQS sendMessage
    when(sqsClient.sendMessage(any(SendMessageRequest.class)))
        .thenAnswer(invocation -> {
          mockQueueMessageCount++;
          logger.debug("Mock SQS: Message sent, queue count: {}", mockQueueMessageCount);
          return SendMessageResponse.builder().messageId("mock-message-id").build();
        });

    // Mock SQS getQueueAttributes
    when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
        .thenAnswer(invocation -> {
          Map<QueueAttributeName, String> attributes = Map.of(
              QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, String.valueOf(mockQueueMessageCount)
          );
          return GetQueueAttributesResponse.builder().attributes(attributes).build();
        });

    // Mock other SQS operations as needed
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(ReceiveMessageResponse.builder()
            .messages(java.util.Collections.emptyList())
            .build());
  }

  private void setupMockSqsAsyncClient() {
    // Mock async SQS operations to prevent infinite polling
    when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenAnswer(invocation -> {
          ReceiveMessageResponse response = ReceiveMessageResponse.builder()
              .messages(java.util.Collections.emptyList())
              .build();
          return java.util.concurrent.CompletableFuture.completedFuture(response);
        });

    when(sqsAsyncClient.deleteMessage(any(DeleteMessageRequest.class)))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
            DeleteMessageResponse.builder().build()));
  }

  private void setupMockS3Client() {
    // Mock S3 putObject
    when(s3Client.putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
        .thenAnswer(invocation -> {
          software.amazon.awssdk.services.s3.model.PutObjectRequest request = invocation.getArgument(0);
          String key = request.bucket() + "/" + request.key();
          mockS3Objects.put(key, "mock-content");
          logger.debug("Mock S3: Put object {}", key);
          return software.amazon.awssdk.services.s3.model.PutObjectResponse.builder().build();
        });

    // Mock S3 getObject with sample compressed WiFi scan data  
    String sampleWifiScanJson =
        "{\"wifiConnectedEvents\":[{\"timestamp\":1731091615562,\"eventId\":\"test-id\",\"eventType\":\"CONNECTED\",\"wifiConnectedInfo\":{\"bssid\":\"b8:f8:53:c0:1e:ff\",\"ssid\":\"TestNetwork\",\"rssi\":-58},\"location\":{\"latitude\":40.6768816,\"longitude\":-74.416391,\"accuracy\":100.0}}]}";
    
    try {
      java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
      try (java.util.zip.GZIPOutputStream gzipStream = new java.util.zip.GZIPOutputStream(byteStream)) {
        gzipStream.write(sampleWifiScanJson.getBytes());
      }
      String base64EncodedData = java.util.Base64.getEncoder().encodeToString(byteStream.toByteArray());
      
      when(s3Client.getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
          .thenAnswer(invocation -> {
            software.amazon.awssdk.services.s3.model.GetObjectRequest request = invocation.getArgument(0);
            String key = request.bucket() + "/" + request.key();
            logger.debug("Mock S3: Get object {}", key);
            
            java.io.InputStream inputStream = new java.io.ByteArrayInputStream((base64EncodedData + "\n").getBytes());
            software.amazon.awssdk.services.s3.model.GetObjectResponse getObjectResponse =
                software.amazon.awssdk.services.s3.model.GetObjectResponse.builder().build();
            return new software.amazon.awssdk.core.ResponseInputStream<>(getObjectResponse, inputStream);
          });
    } catch (Exception e) {
      throw new RuntimeException("Failed to setup mock S3 client", e);
    }
  }

  private void setupMockFirehoseClient() {
    // Mock Firehose synchronous operations
    when(firehoseClient.putRecord(any(PutRecordRequest.class)))
        .thenReturn(PutRecordResponse.builder().recordId("mock-record-id").build());

    when(firehoseClient.putRecordBatch(any(PutRecordBatchRequest.class)))
        .thenReturn(PutRecordBatchResponse.builder().failedPutCount(0).build());

    when(firehoseClient.createDeliveryStream(any(CreateDeliveryStreamRequest.class)))
        .thenReturn(CreateDeliveryStreamResponse.builder().build());
  }

  private void setupMockFirehoseAsyncClient() {
    // Mock Firehose asynchronous operations to prevent blocking
    when(firehoseAsyncClient.putRecord(any(PutRecordRequest.class)))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
            PutRecordResponse.builder().recordId("mock-async-record-id").build()));

    when(firehoseAsyncClient.putRecordBatch(any(PutRecordBatchRequest.class)))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
            PutRecordBatchResponse.builder().failedPutCount(0).build()));
  }

  @Test
  void shouldProcessValidS3EventSuccessfully() {
    // Given: Valid S3 event message
    String validS3Event = createValidS3Event();

    // When: Process the message directly using MessageProcessor
    Message sqsMessage = Message.builder()
        .body(validS3Event)
        .messageId("test-valid-s3-msg")
        .receiptHandle("test-receipt-handle-valid")
        .build();
    boolean processingResult = messageProcessor.processMessage(sqsMessage);

    // Then: Verify processing was successful
    assertTrue(processingResult, "Valid S3 event processing should succeed");
    
    logger.info("Valid S3 event processing test completed successfully!");
  }

  @Test
  void shouldHandleInvalidJsonMessage() {
    // Given: Invalid JSON message
    String invalidJson = "invalid json content";

    // When: Process the invalid message directly using MessageProcessor
    Message sqsMessage = Message.builder()
        .body(invalidJson)
        .messageId("test-invalid-json-msg")
        .receiptHandle("test-receipt-handle-invalid")
        .build();
    boolean processingResult = messageProcessor.processMessage(sqsMessage);

    // Then: Verify processing handled the invalid message gracefully
    // (depending on implementation, this might return false or throw exception - adjust as needed)
    assertFalse(processingResult, "Invalid JSON processing should fail gracefully");
    
    logger.info("Invalid JSON message handling test completed successfully!");
  }

  @Test 
  void shouldHandleS3EventWithProcessingError() {
    // Given: S3 event with malformed bucket name to trigger processing error
    String malformedS3Event = createS3EventWithMalformedBucket();

    // When: Process the malformed S3 event directly using MessageProcessor
    Message sqsMessage = Message.builder()
        .body(malformedS3Event)
        .messageId("test-malformed-s3-msg")
        .receiptHandle("test-receipt-handle-malformed")
        .build();
    boolean processingResult = messageProcessor.processMessage(sqsMessage);

    // Then: Verify processing handled the error gracefully
    assertFalse(processingResult, "Malformed S3 event processing should fail gracefully");
    
    logger.info("S3 event processing error handling test completed successfully!");
  }

  // Helper methods

  private String createValidS3Event() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode event = mapper.createObjectNode();

    // Create Records array
    var recordsArray = mapper.createArrayNode();
    ObjectNode recordNode = mapper.createObjectNode();

    // S3 Event Notification format
    recordNode.put("eventVersion", "2.1");
    recordNode.put("eventSource", "aws:s3");
    recordNode.put("awsRegion", "us-east-1");
    recordNode.put("eventTime", "2025-07-29T11:00:00.000Z");
    recordNode.put("eventName", "ObjectCreated:Put");

    // User identity
    ObjectNode userIdentity = mapper.createObjectNode();
    userIdentity.put("principalId", "AWS:AROA4QWKES4Y24IUPAV2J:AWSFirehoseToS3");
    recordNode.set("userIdentity", userIdentity);

    // Request parameters
    ObjectNode requestParameters = mapper.createObjectNode();
    requestParameters.put("sourceIPAddress", "10.20.19.21");
    recordNode.set("requestParameters", requestParameters);

    // Response elements
    ObjectNode responseElements = mapper.createObjectNode();
    responseElements.put("x-amz-request-id", "8C3VR6DWXN808YJP");
    responseElements.put("x-amz-id-2", "2BdlIpJXKQCEI7siGhF3KCU9M59dye7AJcn63aIjkANLeVX+9EFIJ7qzipO/g3RJFVIK5E7a20PqWDccojmXUmLJHK00bHFvRHDhbb9LMnw=");
    recordNode.set("responseElements", responseElements);

    // S3 details
    ObjectNode s3 = mapper.createObjectNode();
    s3.put("s3SchemaVersion", "1.0");
    s3.put("configurationId", "NjgyMTJiZTUtNDMwZC00OTVjLWIzOWEtM2UzZWM3MzYwNGE2");

    // Bucket info
    ObjectNode bucket = mapper.createObjectNode();
    bucket.put("name", "ingested-wifiscan-data");
    ObjectNode ownerIdentity = mapper.createObjectNode();
    ownerIdentity.put("principalId", "A3LJZCR20GC5IX");
    bucket.set("ownerIdentity", ownerIdentity);
    bucket.put("arn", "arn:aws:s3:::ingested-wifiscan-data");
    s3.set("bucket", bucket);

    // Object info  
    ObjectNode object = mapper.createObjectNode();
    object.put("key", "year%3D2025/month%3D07/day%3D29/hour%3D11/MVS-stream/test-file.txt");
    object.put("size", 1024);
    object.put("eTag", "d41d8cd98f00b204e9800998ecf8427e"); // Valid 32-char hex etag
    object.put("sequencer", "test-sequencer");
    s3.set("object", object);

    recordNode.set("s3", s3);
    recordsArray.add(recordNode);
    event.set("Records", recordsArray);

    return event.toString();
  }

  private String createS3EventWithMalformedBucket() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode event = mapper.createObjectNode();

    // Create Records array with malformed bucket data
    var recordsArray = mapper.createArrayNode();
    ObjectNode recordNode = mapper.createObjectNode();

    recordNode.put("eventVersion", "2.1");
    recordNode.put("eventSource", "aws:s3");
    recordNode.put("awsRegion", "us-east-1");
    recordNode.put("eventTime", "2025-07-29T11:00:00.000Z");
    recordNode.put("eventName", "ObjectCreated:Put");

    // S3 details with invalid/missing bucket name
    ObjectNode s3 = mapper.createObjectNode();
    s3.put("s3SchemaVersion", "1.0");

    // Malformed bucket info (missing bucket name)
    ObjectNode bucket = mapper.createObjectNode();
    // bucket.put("name", ""); // Missing or empty bucket name
    bucket.put("arn", "arn:aws:s3:::"); // Invalid ARN
    s3.set("bucket", bucket);

    // Object info  
    ObjectNode object = mapper.createObjectNode();
    object.put("key", "test-malformed-object.txt");
    object.put("size", 1024);
    s3.set("object", object);

    recordNode.set("s3", s3);
    recordsArray.add(recordNode);
    event.set("Records", recordsArray);

    return event.toString();
  }
}
