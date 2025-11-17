// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/SqsMessageReceiver.java
package com.wifi.ap.location.application.listener;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

import com.wifi.ap.location.estimation.MessageProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wifi.ap.location.config.properties.SqsConfigurationProperties;
import com.wifi.ap.location.estimation.service.LocationEstimationService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;


@Service
public class SqsMessageReceiver {

    private static final Logger logger = LoggerFactory.getLogger(SqsMessageReceiver.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    private final SqsAsyncClient sqsAsyncClient;
    private final SqsConfigurationProperties sqsConfig;
    private final LocationEstimationService wifiAPLocationEstimator;
    private final SqsMonitoringService sqsMonitoringService;
    private final String queueUrl;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Comprehensive metrics for monitoring and observability
    private final Timer batchReceiveTimer;

    public SqsMessageReceiver(
            SqsAsyncClient sqsAsyncClient,
            SqsConfigurationProperties sqsConfig,
            LocationEstimationService wifiAPLocationEstimator,
            SqsMonitoringService sqsMonitoringService,
            MeterRegistry meterRegistry,
            @Value("#{@resolvedQueueUrl}") String queueUrl) {
        if (sqsAsyncClient == null) {
            throw new IllegalArgumentException("SqsAsyncClient cannot be null");
        }
        if (sqsConfig == null) {
            throw new IllegalArgumentException("SqsConfigurationProperties cannot be null");
        }
        if (wifiAPLocationEstimator == null) {
            throw new IllegalArgumentException("MessageProcessor cannot be null");
        }
        if (sqsMonitoringService == null) {
            throw new IllegalArgumentException("SqsMonitoringService cannot be null");
        }
        if (meterRegistry == null) {
            throw new IllegalArgumentException("MeterRegistry cannot be null");
        }

        this.sqsAsyncClient = sqsAsyncClient;
        this.sqsConfig = sqsConfig;
        this.wifiAPLocationEstimator = wifiAPLocationEstimator;
        this.sqsMonitoringService = sqsMonitoringService;
        this.queueUrl = queueUrl;

        // Initialize comprehensive metrics for monitoring and observability
        // These metrics provide insights into processing performance and error rates
        this.batchReceiveTimer =
                Timer.builder("sqs.batch.receive.duration")
                     .description("Time taken to receive SQS message batches")
                     .register(meterRegistry);

        logger.info("SQS Message Receiver initialized with configuration and metrics");
    }

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info(
                    "Starting SQS message receiver with configuration: "
                            + "queueUrl={}, maxMessages={}, waitTimeSeconds={}, maxRetries={}",
                    queueUrl,
                    sqsConfig.maxMessages(),
                    sqsConfig.waitTimeSeconds(),
                    sqsConfig.maxRetries());

            // Start the continuous message receiving loop
            // This begins the long-polling process for SQS messages
            receiveMessagesLoop();
        }
    }

    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping SQS message receiver");
        }
    }

    private void receiveMessagesLoop() {

        // Create ReceiveMessageRequest once during initialization to eliminate object creation overhead
        // This improves performance by reusing the immutable request configuration across all polling operations
        //Step 1 : create receiveMessageRequest with longer poll
        ReceiveMessageRequest receiveMessageRequest = buildReceiveRequest();

        // Start asynchronous message processing loop
        // This prevents blocking the main application thread during message reception
        CompletableFuture.runAsync(
                                 () -> {
                                     while (running.get()) {
                                         try {
                                             // Process message batches continuously while service is running
                                             // This implements the core long-polling and batch processing logic
                                             pullMessageWith(receiveMessageRequest);
                                         } catch (Exception e) {
                                             if (handlePullingError(e)) break;
                                         }
                                     }
                                 })
                         .exceptionally(SqsMessageReceiver::handleFatalReceiverCrash);
    }

    private static Void handleFatalReceiverCrash(Throwable throwable) {
        // Handle fatal errors that could crash the entire message processing loop
        // This ensures the service can recover from catastrophic failures
        logger.error("Fatal error in SQS message receiver", throwable);
        return null;
    }

    private static boolean handlePullingError(Exception e) {
        // Log errors but continue processing to maintain service availability
        // Individual batch failures should not stop the entire message processing loop
        logger.error("Error in SQS message receiving loop", e);

        // Brief pause before retrying to avoid tight error loops
        // This prevents excessive CPU usage during repeated failures
        try {
            Thread.sleep(Duration.ofSeconds(5)
                                 .toMillis());
        } catch (InterruptedException ie) {
            // Proper interruption handling for clean shutdown
            Thread.currentThread()
                  .interrupt();
            logger.warn("SQS message receiver interrupted");
            return true;
        }
        return false;
    }

    private ReceiveMessageRequest buildReceiveRequest() {
        return ReceiveMessageRequest.builder()
                                    .queueUrl(queueUrl)
                                    .maxNumberOfMessages(sqsConfig.maxMessages())
                                    .waitTimeSeconds(sqsConfig.waitTimeSeconds())
                                    .visibilityTimeout(sqsConfig.visibilityTimeoutSeconds())
                                    .attributeNames(QueueAttributeName.ALL)
                                    .messageAttributeNames("All")
                                    .build();
    }

    private void pullMessageWith(ReceiveMessageRequest receiveMessageRequest) {
        // Generate unique correlation ID for request tracing and debugging
        // This allows tracking of individual batch processing operations
        String correlationId = UUID.randomUUID()
                                   .toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);

        try {
            Timer.Sample receiveSample = Timer.start();

            // Asynchronously receive messages using the pre-built request
            // This eliminates object creation overhead by reusing the immutable request configuration

            sqsAsyncClient.receiveMessage(receiveMessageRequest)
                          .thenAccept(r -> this.process(r, receiveSample, correlationId))
                          .exceptionally(handleError(receiveSample))
                          .join(); // Wait for completion to ensure proper error handling

        } finally {
            // Always clean up correlation context to prevent memory leaks
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    private Function<Throwable, Void> handleError(Timer.Sample receiveSample) {
        return throwable -> {
            // Handle reception errors and stop timing
            receiveSample.stop(batchReceiveTimer);
            logger.error("Failed to receive messages from SQS queue", throwable);
            return null;
        };
    }

    private void process(ReceiveMessageResponse response, Timer.Sample receiveSample, String correlationId) {


        try {

            // Stop timing and record batch receive duration
            receiveSample.stop(batchReceiveTimer);
            MDC.put(CORRELATION_ID_KEY, correlationId);

            List<Message> messages = response.messages();
            if (!messages.isEmpty()) {
                // Log batch reception and record metrics
                logger.info("Received {} messages from SQS queue", messages.size());
                sqsMonitoringService.recordMessageReceived(messages.size());
                // Process the received message batch

                // Process messages declaratively and collect results
                List<MessageProcessingResult> results = this.processMessages(messages);

                // Delete all messages to prevent replay
                deleteBatch(results);

            } else {
                // Log empty response for debugging (debug level to avoid noise)
                logger.debug("No messages received from SQS queue");
            }
        } catch (Exception e) {
            logger.error("Error processing message batch", e);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
            MDC.remove("batchSize");
        }
    }


    private void logBatchProcessingResults(int totalMessages, int successfulMessages) {
        logger.info(
                "Batch processing completed: {} total messages, {} successful, {} failed, {} deleted",
                totalMessages,
                successfulMessages,
                totalMessages - successfulMessages,
                totalMessages);
    }


    private List<MessageProcessingResult> processMessages(List<Message> messages) {
        try {
            // Process the message using the message processor
            List<MessageProcessingResult> results = wifiAPLocationEstimator.estimateLocation(messages)
                                                                           .toList();
            // Record processing activity in monitoring service
            logResults(messages, results);

            return results;
        } catch (Exception e) {
            logger.error("Error processing SQS messages: {}", messages.stream()
                                                                      .map(Message::messageId)
                                                                      .toList(), e);
            sqsMonitoringService.recordFailedBatch(messages.size());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void logResults(List<Message> messages, List<MessageProcessingResult> results) {
        sqsMonitoringService.recordFailedBatch(results);

        List<String> failedMessageIds = results.stream()
                                               .filter(Predicate.not(MessageProcessingResult::status))
                                               .map(MessageProcessingResult::messageId)
                                               .toList();
        if (!failedMessageIds.isEmpty()) {
            logger.error("Failed to process SQS messages (will be deleted): {}", failedMessageIds);
        }
        logBatchProcessingResults(messages.size(), messages.size() - failedMessageIds.size());
    }

    private void deleteBatch(List<MessageProcessingResult> results) {

        DeleteMessageBatchRequest deleteRequest = buildDeleteBatchRequest(results);
        try {

            sqsAsyncClient
                    .deleteMessageBatch(deleteRequest)
                    .thenAccept(this::handleDelete)
                    .exceptionally(
                            throwable -> {
                                logger.error("Failed to delete message batch", throwable);
                                return null;
                            })
                    .join();
        } catch (Exception e) {
            logger.error("Error deleting message batch", e);
        }

    }

    private void handleDelete(DeleteMessageBatchResponse response) {
        List<DeleteMessageBatchResultEntry> deletedEntries = response.successful();
        List<BatchResultErrorEntry> failed = response.failed();

        // Message deletion count tracking is handled by SqsMonitoringService

        if (!failed.isEmpty()) {
            logger.warn("Failed to delete {} messages from batch", failed.size());
            failed.forEach(
                    error ->
                            logger.warn(
                                    "Delete failed for message: {} - {}", error.id(), error.message()));
        }

        logger.debug(
                "Batch delete completed: {} deletedEntries, {} failed",
                deletedEntries.size(),
                failed.size());
    }

    private DeleteMessageBatchRequest buildDeleteBatchRequest(List<MessageProcessingResult> results) {
        return results.stream()
                      .map(MessageProcessingResult::receiptHandle)
                      .map(this::getBatchRequestEntry)
                      .collect(collectingAndThen(toList(), this::getBatchRequest));

    }

    private DeleteMessageBatchRequest getBatchRequest(List<DeleteMessageBatchRequestEntry> deleteEntries) {
        return DeleteMessageBatchRequest.builder()
                                        .queueUrl(queueUrl)
                                        .entries(deleteEntries)
                                        .build();
    }

    private DeleteMessageBatchRequestEntry getBatchRequestEntry(String receiptHandle) {
        return DeleteMessageBatchRequestEntry.builder()
                                             .id(UUID.randomUUID()
                                                     .toString())
                                             .receiptHandle(receiptHandle)
                                             .build();
    }

    /**
     * Check if the message receiver is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get current metrics summary using SqsMonitoringService.
     */
    public String getMetricsSummary() {
        var metrics = sqsMonitoringService.getMetrics();
        return String.format(
                "SQS Metrics - Received: %d, Processed: %d, Succeeded: %d, Failed: %d, Success Rate: %.2f%%",
                metrics.getTotalMessagesReceived(),
                metrics.getTotalMessagesProcessed(),
                metrics.getTotalMessagesSucceeded(),
                metrics.getTotalMessagesFailed(),
                metrics.getSuccessRate() * 100);
    }
}
