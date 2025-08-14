// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/processor/SqsMessageReceiverIntegrationTest.java
package com.wifi.measurements.transformer.processor;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wifi.measurements.transformer.config.properties.SqsConfigurationProperties;
import com.wifi.measurements.transformer.listener.SqsMessageReceiver;
import com.wifi.measurements.transformer.processor.impl.DefaultFeedProcessor;
import com.wifi.measurements.transformer.service.SqsMonitoringService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

/**
 * Integration test for SQS Message Receiver with LocalStack.
 *
 * <p>Tests both success and failure scenarios, verifies metrics and core functionality.
 */
@Testcontainers
class SqsMessageReceiverIntegrationTest {

  private static final Logger logger =
      LoggerFactory.getLogger(SqsMessageReceiverIntegrationTest.class);

  @Container
  static LocalStackContainer localStack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0.0"))
          .withServices(LocalStackContainer.Service.SQS);

  private SqsClient sqsClient;
  private String queueUrl;
  private SqsMessageReceiver messageReceiver;
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() throws Exception {
    // Setup SQS client
    sqsClient =
        SqsClient.builder()
            .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localStack.getAccessKey(), localStack.getSecretKey())))
            .region(Region.of(localStack.getRegion()))
            .build();

    // Create test queue
    CreateQueueRequest createQueueRequest =
        CreateQueueRequest.builder().queueName("test-queue").build();
    CreateQueueResponse createQueueResponse = sqsClient.createQueue(createQueueRequest);
    queueUrl = createQueueResponse.queueUrl();
    logger.info("Created test queue: {}", queueUrl);

    // Setup meter registry for testing
    meterRegistry = new SimpleMeterRegistry();

    // Create message receiver with test configuration
    messageReceiver = createTestMessageReceiver();
  }

  @AfterEach
  void tearDown() {
    if (messageReceiver != null && messageReceiver.isRunning()) {
      messageReceiver.stop();
    }
    if (sqsClient != null) {
      try {
        sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build());
      } catch (Exception e) {
        logger.warn("Failed to delete test queue", e);
      }
    }
  }

  @Test
  void shouldProcessValidS3EventSuccessfully() throws Exception {
    // Given: Valid S3 event message
    String validS3Event = createValidS3Event();

    // When: Send message to queue and start processing
    sendMessageToQueue(validS3Event);
    messageReceiver.start();

    // Then: Wait for message to be processed and metrics updated
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () -> {
              Counter receivedCounter = meterRegistry.counter("sqs.messages.received");
              Counter processedCounter = meterRegistry.counter("sqs.messages.processed");
              Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

              logger.info(
                  "Test metrics - Received: {}, Processed: {}, Deleted: {}, Queue count: {}",
                  receivedCounter.count(),
                  processedCounter.count(),
                  deletedCounter.count(),
                  getMessageCount());

              // Wait for all metrics to be updated AND queue to be empty
              return receivedCounter.count() >= 1.0
                  && processedCounter.count() >= 1.0
                  && deletedCounter.count() >= 1.0
                  && getMessageCount() == 0;
            });

    // Verify final metrics
    Counter processedCounter = meterRegistry.counter("sqs.messages.processed");
    Counter receivedCounter = meterRegistry.counter("sqs.messages.received");
    Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

    assertEquals(1.0, receivedCounter.count(), "Should have received 1 message");
    assertEquals(1.0, processedCounter.count(), "Should have processed 1 message");
    assertEquals(1.0, deletedCounter.count(), "Should have deleted 1 message");
  }

  @Test
  void shouldHandleInvalidJsonMessage() throws Exception {
    // Given: Invalid JSON message
    String invalidJson = "invalid json content";

    // When: Send message to queue and start processing
    sendMessageToQueue(invalidJson);
    messageReceiver.start();

    // Then: Wait for message to be processed (failed) and metrics updated
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () -> {
              Counter failedCounter = meterRegistry.counter("sqs.messages.failed");
              Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

              logger.info(
                  "Test metrics - Failed: {}, Deleted: {}, Queue count: {}",
                  failedCounter.count(),
                  deletedCounter.count(),
                  getMessageCount());

              // Wait for metrics to be updated AND queue to be empty
              return failedCounter.count() >= 1.0
                  && deletedCounter.count() >= 1.0
                  && getMessageCount() == 0;
            });

    // Verify metrics
    Counter failedCounter = meterRegistry.counter("sqs.messages.failed");
    Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

    assertEquals(1.0, failedCounter.count(), "Should have failed 1 message");
    assertEquals(1.0, deletedCounter.count(), "Should have deleted 1 message");
  }

  @Test
  void shouldHandleMissingRequiredFields() throws Exception {
    // Given: S3 event with missing required fields
    String invalidS3Event = createInvalidS3EventMissingFields();

    // When: Send message to queue and start processing
    sendMessageToQueue(invalidS3Event);
    messageReceiver.start();

    // Then: Wait for message to be processed (failed) and metrics updated
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () -> {
              Counter failedCounter = meterRegistry.counter("sqs.messages.failed");
              Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

              logger.info(
                  "Test metrics - Failed: {}, Deleted: {}, Queue count: {}",
                  failedCounter.count(),
                  deletedCounter.count(),
                  getMessageCount());

              // Wait for metrics to be updated AND queue to be empty
              return failedCounter.count() >= 1.0
                  && deletedCounter.count() >= 1.0
                  && getMessageCount() == 0;
            });

    // Verify metrics
    Counter failedCounter = meterRegistry.counter("sqs.messages.failed");
    assertEquals(1.0, failedCounter.count(), "Should have failed 1 message");
  }

  @Test
  void shouldHandleMultipleMessagesInBatch() throws Exception {
    // Given: Multiple messages (mix of valid and invalid)
    String validS3Event = createValidS3Event();
    String invalidJson = "invalid json";
    String anotherValidS3Event = createValidS3Event();

    // When: Send messages to queue and start processing
    sendMessageToQueue(validS3Event);
    sendMessageToQueue(invalidJson);
    sendMessageToQueue(anotherValidS3Event);
    messageReceiver.start();

    // Then: Wait for all messages to be processed and metrics updated
    await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              Counter processedCounter = meterRegistry.counter("sqs.messages.processed");
              Counter failedCounter = meterRegistry.counter("sqs.messages.failed");
              Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

              logger.info(
                  "Test metrics - Processed: {}, Failed: {}, Deleted: {}, Queue count: {}",
                  processedCounter.count(),
                  failedCounter.count(),
                  deletedCounter.count(),
                  getMessageCount());

              // Wait for all metrics to be updated AND queue to be empty
              return processedCounter.count() >= 2.0
                  && failedCounter.count() >= 1.0
                  && deletedCounter.count() >= 3.0
                  && getMessageCount() == 0;
            });

    // Verify metrics
    Counter processedCounter = meterRegistry.counter("sqs.messages.processed");
    Counter failedCounter = meterRegistry.counter("sqs.messages.failed");
    Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

    assertEquals(2.0, processedCounter.count(), "Should have processed 2 valid messages");
    assertEquals(1.0, failedCounter.count(), "Should have failed 1 invalid message");
    assertEquals(3.0, deletedCounter.count(), "Should have deleted all 3 messages");
  }

  @Test
  void shouldHandleEmptyQueueGracefully() throws Exception {
    // When: Start processing with empty queue
    messageReceiver.start();

    // Then: Should not crash and continue running
    await().atMost(Duration.ofSeconds(5)).until(() -> messageReceiver.isRunning());

    assertTrue(messageReceiver.isRunning(), "Message receiver should continue running");

    // Verify no metrics are incremented
    Counter receivedCounter = meterRegistry.counter("sqs.messages.received");
    assertEquals(0.0, receivedCounter.count(), "Should not have received any messages");
  }

  @Test
  void shouldStopGracefully() throws Exception {
    // Given: Started message receiver
    messageReceiver.start();
    assertTrue(messageReceiver.isRunning(), "Message receiver should be running");

    // When: Stop the receiver
    messageReceiver.stop();

    // Then: Should stop gracefully
    await().atMost(Duration.ofSeconds(5)).until(() -> !messageReceiver.isRunning());

    assertFalse(messageReceiver.isRunning(), "Message receiver should be stopped");
  }

  @Test
  void shouldProcessLargeBatchOfMessages() throws Exception {
    // Given: Large batch of messages
    int messageCount = 25; // More than max batch size to test batching

    // When: Send messages to queue and start processing
    for (int i = 0; i < messageCount; i++) {
      String message = (i % 2 == 0) ? createValidS3Event() : "invalid json " + i;
      sendMessageToQueue(message);
    }
    messageReceiver.start();

    // Calculate expected counts
    int expectedProcessed = (messageCount + 1) / 2; // Every other message is valid
    int expectedFailed = messageCount / 2; // Every other message is invalid

    // Then: Wait for all messages to be processed and metrics updated
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              Counter processedCounter = meterRegistry.counter("sqs.messages.processed");
              Counter failedCounter = meterRegistry.counter("sqs.messages.failed");
              Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

              logger.info(
                  "Test metrics - Processed: {}, Failed: {}, Deleted: {}, Queue count: {}, Expected: P={}, F={}, D={}",
                  processedCounter.count(),
                  failedCounter.count(),
                  deletedCounter.count(),
                  getMessageCount(),
                  expectedProcessed,
                  expectedFailed,
                  messageCount);

              // Wait for all metrics to be updated AND queue to be empty
              return processedCounter.count() >= expectedProcessed
                  && failedCounter.count() >= expectedFailed
                  && deletedCounter.count() >= messageCount
                  && getMessageCount() == 0;
            });

    // Verify metrics
    Counter processedCounter = meterRegistry.counter("sqs.messages.processed");
    Counter failedCounter = meterRegistry.counter("sqs.messages.failed");
    Counter deletedCounter = meterRegistry.counter("sqs.messages.deleted");

    assertEquals(
        expectedProcessed,
        processedCounter.count(),
        "Should have processed correct number of valid messages");
    assertEquals(
        expectedFailed,
        failedCounter.count(),
        "Should have failed correct number of invalid messages");
    assertEquals(messageCount, deletedCounter.count(), "Should have deleted all messages");
  }

  // Helper methods

  private SqsMessageReceiver createTestMessageReceiver() {
    // Create test configuration using record constructor
    SqsConfigurationProperties testConfig =
        new SqsConfigurationProperties(
            queueUrl, // queueUrl
            null, // queueName (not needed for tests)
            10, // maxMessages
            1, // waitTimeSeconds (short for tests)
            30, // visibilityTimeoutSeconds
            1, // maxConcurrentBatches
            3 // maxRetries
            );

    // Create SQS client for the receiver
    SqsAsyncClient sqsAsyncClient =
        SqsAsyncClient.builder()
            .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localStack.getAccessKey(), localStack.getSecretKey())))
            .region(Region.of(localStack.getRegion()))
            .build();

    // Create message processor with REAL components but mocked external dependencies
    S3EventExtractor s3EventExtractor = new S3EventExtractor();

    // Create real service dependencies for integration testing
    // Mock only the S3 client for external calls
    S3Client mockS3Client = mock(S3Client.class);

    // Mock S3 response with sample data for successful processing
    String sampleWifiScanJson =
        "{\"wifiConnectedEvents\":[{\"timestamp\":1731091615562,\"eventId\":\"test-id\",\"eventType\":\"CONNECTED\",\"wifiConnectedInfo\":{\"bssid\":\"b8:f8:53:c0:1e:ff\",\"ssid\":\"TestNetwork\",\"rssi\":-58},\"location\":{\"latitude\":40.6768816,\"longitude\":-74.416391,\"accuracy\":100.0}}]}";
    java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
    try (java.util.zip.GZIPOutputStream gzipStream =
        new java.util.zip.GZIPOutputStream(byteStream)) {
      gzipStream.write(sampleWifiScanJson.getBytes());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    String base64EncodedCompressedData =
        java.util.Base64.getEncoder().encodeToString(byteStream.toByteArray());

    InputStream inputStream =
        new java.io.ByteArrayInputStream((base64EncodedCompressedData + "\n").getBytes());
    software.amazon.awssdk.services.s3.model.GetObjectResponse getObjectResponse =
        software.amazon.awssdk.services.s3.model.GetObjectResponse.builder().build();
    software.amazon.awssdk.core.ResponseInputStream<
            software.amazon.awssdk.services.s3.model.GetObjectResponse>
        responseInputStream =
            new software.amazon.awssdk.core.ResponseInputStream<>(getObjectResponse, inputStream);

    when(mockS3Client.getObject(
            any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
        .thenReturn(responseInputStream);

    // Create REAL service components (this is what we want to test in integration)
    com.wifi.measurements.transformer.config.properties.S3ConfigurationProperties s3Config =
        new com.wifi.measurements.transformer.config.properties.S3ConfigurationProperties(1048576L);

    com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties
            .MobileHotspotConfiguration
        mobileHotspotConfig =
            new com.wifi.measurements.transformer.config.properties
                .DataFilteringConfigurationProperties.MobileHotspotConfiguration(
                false,
                java.util.Set.of("00:00:00"),
                com.wifi.measurements.transformer.config.properties
                    .DataFilteringConfigurationProperties.MobileHotspotAction.LOG_ONLY);

    com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties
        filteringConfig =
            new com.wifi.measurements.transformer.config.properties
                .DataFilteringConfigurationProperties(
                150.0, -100, 0, 2.0, 1.0, 1.5, mobileHotspotConfig);

    com.wifi.measurements.transformer.service.S3FileProcessorService s3FileProcessorService =
        new com.wifi.measurements.transformer.service.S3FileProcessorService(
            mockS3Client, s3Config);
    com.wifi.measurements.transformer.service.DataDecodingService dataDecodingService =
        new com.wifi.measurements.transformer.service.DataDecodingService();

    com.wifi.measurements.transformer.service.DataValidationService dataValidationService =
        new com.wifi.measurements.transformer.service.DataValidationService(
            filteringConfig, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    com.wifi.measurements.transformer.service.WifiDataTransformationService
        wifiDataTransformationService =
            new com.wifi.measurements.transformer.service.WifiDataTransformationService(
                dataValidationService, filteringConfig);

    // Create Firehose dependencies for DefaultFeedProcessor
    com.wifi.measurements.transformer.service.WiFiMeasurementsPublisher
        mockWiFiMeasurementsPublisher =
            mock(com.wifi.measurements.transformer.service.WiFiMeasurementsPublisher.class);

    com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties
        firehoseConfig =
            new com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties(
                false, // disabled for tests
                "test-wifi-measurements-stream",
                10, // smaller batch for tests
                1048576, // 1MB for tests
                1000, // faster timeout for tests
                102400, // 100KB for tests
                1, // single retry for tests
                100 // fast backoff for tests
                );

    // Create REAL DefaultFeedProcessor with REAL dependencies
    com.fasterxml.jackson.databind.ObjectMapper objectMapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
    DefaultFeedProcessor realFeedProcessor =
        new DefaultFeedProcessor(
            s3FileProcessorService,
            dataDecodingService,
            wifiDataTransformationService,
            mockWiFiMeasurementsPublisher,
            objectMapper);

    // Create REAL FeedProcessorFactory with our properly configured processor
    FeedProcessorFactory feedProcessorFactory = new FeedProcessorFactory(realFeedProcessor);

    MessageProcessor messageProcessor =
        new MessageProcessor(s3EventExtractor, feedProcessorFactory);

    // Create mock SqsMonitoringService for testing
    SqsMonitoringService mockSqsMonitoringService = mock(SqsMonitoringService.class);

    return new SqsMessageReceiver(
        sqsAsyncClient, testConfig, messageProcessor, mockSqsMonitoringService, meterRegistry, queueUrl);
  }

  private void sendMessageToQueue(String messageBody) {
    SendMessageRequest request =
        SendMessageRequest.builder().queueUrl(queueUrl).messageBody(messageBody).build();
    sqsClient.sendMessage(request);
    logger.info(
        "Sent message to queue: {}", messageBody.substring(0, Math.min(100, messageBody.length())));
  }

  private int getMessageCount() {
    GetQueueAttributesRequest request =
        GetQueueAttributesRequest.builder()
            .queueUrl(queueUrl)
            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
            .build();
    GetQueueAttributesResponse response = sqsClient.getQueueAttributes(request);
    return Integer.parseInt(
        response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
  }

  private String createValidS3Event() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode event = mapper.createObjectNode();

    // Create Records array
    var records = mapper.createArrayNode();
    ObjectNode record = mapper.createObjectNode();

    // S3 Event Notification format
    record.put("eventVersion", "2.1");
    record.put("eventSource", "aws:s3");
    record.put("awsRegion", "us-east-1");
    record.put("eventTime", "2025-07-29T11:00:00.000Z");
    record.put("eventName", "ObjectCreated:Put");

    // User identity
    ObjectNode userIdentity = mapper.createObjectNode();
    userIdentity.put("principalId", "AWS:AROA4QWKES4Y24IUPAV2J:AWSFirehoseToS3");
    record.set("userIdentity", userIdentity);

    // Request parameters
    ObjectNode requestParameters = mapper.createObjectNode();
    requestParameters.put("sourceIPAddress", "10.20.19.21");
    record.set("requestParameters", requestParameters);

    // Response elements
    ObjectNode responseElements = mapper.createObjectNode();
    responseElements.put("x-amz-request-id", "8C3VR6DWXN808YJP");
    responseElements.put("x-amz-id-2", "2BdlIpJXKQCEI7siGhF3KCU9M59dye7AJcn63aIjkANLeVX+9EFIJ7qzipO/g3RJFVIK5E7a20PqWDccojmXUmLJHK00bHFvRHDhbb9LMnw=");
    record.set("responseElements", responseElements);

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

    record.set("s3", s3);
    records.add(record);
    event.set("Records", records);

    return event.toString();
  }

  private String createInvalidS3EventMissingFields() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode event = mapper.createObjectNode();

    // Missing required fields like version, id, detail-type, etc.
    event.put("someField", "someValue");

    return event.toString();
  }

  /** Test implementation of DefaultFeedProcessor for integration tests. */
  // Test processors are no longer needed since the simplified architecture
  // uses the DefaultFeedProcessor for all feed types
}
