// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/MessageProcessor.java
package com.wifi.measurements.transformer.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wifi.measurements.transformer.dto.FeedUploadEvent;

import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Core message processor service for handling SQS messages containing S3 event notifications.
 *
 * <p>This service implements the primary message processing logic for the WiFi location data
 * pipeline. It extracts S3 event information from SQS messages and routes them to appropriate feed
 * processors based on the data stream type. The service provides robust error handling and
 * comprehensive logging for monitoring and debugging.
 *
 * <p><strong>Processing Flow:</strong>
 *
 * <ol>
 *   <li><strong>Message Reception:</strong> Receives SQS messages containing S3 event notifications
 *   <li><strong>Event Extraction:</strong> Extracts S3 event information using S3EventExtractor
 *   <li><strong>Processor Selection:</strong> Uses FeedProcessorFactory to select appropriate
 *       processor
 *   <li><strong>Event Processing:</strong> Routes S3 events to selected processor for data
 *       transformation
 *   <li><strong>Result Handling:</strong> Logs processing results and returns success/failure
 *       status
 * </ol>
 *
 * <p><strong>Error Handling Strategy:</strong>
 *
 * <ul>
 *   <li><strong>Extraction Failures:</strong> Logs detailed error information and returns false
 *   <li><strong>Processing Failures:</strong> Logs processor-specific errors and returns false
 *   <li><strong>Exception Handling:</strong> Catches and logs all exceptions to prevent message
 *       loss
 *   <li><strong>Graceful Degradation:</strong> Continues processing other messages despite
 *       individual failures
 * </ul>
 *
 * <p><strong>Monitoring and Observability:</strong>
 *
 * <ul>
 *   <li>Comprehensive logging at INFO level for successful operations
 *   <li>Detailed error logging for debugging and troubleshooting
 *   <li>Message ID tracking for correlation and traceability
 *   <li>Processor selection logging for understanding routing decisions
 * </ul>
 *
 * <p>This service is designed to be stateless and thread-safe, supporting concurrent processing of
 * multiple SQS messages while maintaining data integrity and providing comprehensive observability.
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class MessageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessor.class);
    private final FeedEventParser feedEventParser;
    private final FeedProcessorFactory feedProcessorFactory;

    /**
     * Constructs a new message processor with required dependencies.
     *
     * <p>This constructor initializes the message processor with an S3 event extractor for parsing
     * SQS message bodies and a feed processor factory for selecting appropriate processors based on
     * data stream types.
     *
     * @param feedEventParser     Service responsible for extracting S3 event information from SQS
     *                             message bodies
     * @param feedProcessorFactory Factory for creating appropriate feed processors based on data
     *                             stream types
     * @throws IllegalArgumentException if any required dependency is null
     */
    public MessageProcessor(
            FeedEventParser feedEventParser, FeedProcessorFactory feedProcessorFactory) {
        if (feedEventParser == null) {
            throw new IllegalArgumentException("S3EventExtractor cannot be null");
        }
        if (feedProcessorFactory == null) {
            throw new IllegalArgumentException("FeedProcessorFactory cannot be null");
        }

        this.feedEventParser = feedEventParser;
        this.feedProcessorFactory = feedProcessorFactory;

        logger.info("Message Processor initialized with S3 event extractor and feed processor factory");
    }

    /**
     * Processes a single SQS message containing an S3 event notification.
     *
     * <p>This method is the main entry point for message processing. It extracts S3 event information
     * from the SQS message, selects an appropriate feed processor based on the data stream type, and
     * routes the event for processing.
     *
     * <p><strong>Processing Steps:</strong>
     *
     * <ol>
     *   <li><strong>Message Validation:</strong> Validates message structure and content
     *   <li><strong>Event Extraction:</strong> Extracts S3 event information using S3EventExtractor
     *   <li><strong>Processor Selection:</strong> Uses FeedProcessorFactory to select appropriate
     *       processor
     *   <li><strong>Event Processing:</strong> Routes S3 event to selected processor for data
     *       transformation
     *   <li><strong>Result Logging:</strong> Logs processing results for monitoring and debugging
     * </ol>
     *
     * <p><strong>Error Handling:</strong>
     *
     * <ul>
     *   <li>Returns false if S3 event extraction fails
     *   <li>Returns false if feed processor selection fails
     *   <li>Returns false if event processing fails
     *   <li>Logs detailed error information for debugging
     *   <li>Catches and logs all exceptions to prevent message loss
     * </ul>
     *
     * <p><strong>Thread Safety:</strong>
     *
     * <ul>
     *   <li>Thread-safe operation - can be called concurrently
     *   <li>Stateless design - no shared mutable state
     *   <li>Exception isolation - exceptions don't affect other message processing
     * </ul>
     *
     * @param message The SQS message containing S3 event notification to process
     * @return true if message was processed successfully, false if processing failed
     * @throws IllegalArgumentException if message is null
     */
    public MessageProcessingResult processMessage(Message message) {
        String messageId = message.messageId();
        String body = message.body();

        logger.info("Processing SQS message: messageId={}", messageId);

        try {
            // Extract S3 event information using the new extractor
            return feedEventParser.parseFrom(body)
                    .map(e -> this.processFeed(e, message))
                    .orElse(handleFailure(message, messageId, body));

        } catch (Exception e) {
            logger.error("Error processing message: {}", messageId, e);
            return new MessageProcessingResult(false, message.receiptHandle());
        }
    }

    private static MessageProcessingResult handleFailure(Message message, String messageId, String body) {
        logger.error(
                "Failed to extract S3 event from message - messageId: {}, messageBody: {}",
                messageId,
                body);

        return new MessageProcessingResult(false, message.receiptHandle());
    }

    private MessageProcessingResult processFeed(FeedUploadEvent feedUploadEvent, Message message) {

        FeedProcessor processor = feedProcessorFactory.getProcessor(feedUploadEvent);
        boolean processingResult = processor.process(feedUploadEvent);

        if (processingResult) {
            logger.info(
                    "Successfully processed S3 event - messageId: {}, processor: {}, stream: {}",
                    message.messageId(),
                    processor.getClass().getSimpleName(),
                    feedUploadEvent.streamName());
        } else {
            logger.error(
                    "Failed to process S3 event - messageId: {}, processor: {}, stream: {}",
                    message.messageId(),
                    processor.getClass().getSimpleName(),
                    feedUploadEvent.streamName());
        }

        return new MessageProcessingResult(processingResult, message.receiptHandle());

    }
}

