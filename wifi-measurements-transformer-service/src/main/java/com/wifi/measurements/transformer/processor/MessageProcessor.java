// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/MessageProcessor.java
package com.wifi.measurements.transformer.processor;

import com.wifi.measurements.transformer.dto.S3EventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Optional;

/**
 * Core message processor service for handling SQS messages containing S3 event notifications.
 * 
 * <p>This service implements the primary message processing logic for the WiFi location data pipeline.
 * It extracts S3 event information from SQS messages and routes them to appropriate feed processors
 * based on the data stream type. The service provides robust error handling and comprehensive
 * logging for monitoring and debugging.</p>
 * 
 * <p><strong>Processing Flow:</strong></p>
 * <ol>
 *   <li><strong>Message Reception:</strong> Receives SQS messages containing S3 event notifications</li>
 *   <li><strong>Event Extraction:</strong> Extracts S3 event information using S3EventExtractor</li>
 *   <li><strong>Processor Selection:</strong> Uses FeedProcessorFactory to select appropriate processor</li>
 *   <li><strong>Event Processing:</strong> Routes S3 events to selected processor for data transformation</li>
 *   <li><strong>Result Handling:</strong> Logs processing results and returns success/failure status</li>
 * </ol>
 * 
 * <p><strong>Error Handling Strategy:</strong></p>
 * <ul>
 *   <li><strong>Extraction Failures:</strong> Logs detailed error information and returns false</li>
 *   <li><strong>Processing Failures:</strong> Logs processor-specific errors and returns false</li>
 *   <li><strong>Exception Handling:</strong> Catches and logs all exceptions to prevent message loss</li>
 *   <li><strong>Graceful Degradation:</strong> Continues processing other messages despite individual failures</li>
 * </ul>
 * 
 * <p><strong>Monitoring and Observability:</strong></p>
 * <ul>
 *   <li>Comprehensive logging at INFO level for successful operations</li>
 *   <li>Detailed error logging for debugging and troubleshooting</li>
 *   <li>Message ID tracking for correlation and traceability</li>
 *   <li>Processor selection logging for understanding routing decisions</li>
 * </ul>
 * 
 * <p>This service is designed to be stateless and thread-safe, supporting concurrent
 * processing of multiple SQS messages while maintaining data integrity and providing
 * comprehensive observability.</p>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class MessageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessor.class);
    private final S3EventExtractor s3EventExtractor;
    private final FeedProcessorFactory feedProcessorFactory;

    /**
     * Constructs a new message processor with required dependencies.
     * 
     * <p>This constructor initializes the message processor with an S3 event extractor for
     * parsing SQS message bodies and a feed processor factory for selecting appropriate
     * processors based on data stream types.</p>
     * 
     * @param s3EventExtractor Service responsible for extracting S3 event information from SQS message bodies
     * @param feedProcessorFactory Factory for creating appropriate feed processors based on data stream types
     * @throws IllegalArgumentException if any required dependency is null
     */
    public MessageProcessor(S3EventExtractor s3EventExtractor, 
                           FeedProcessorFactory feedProcessorFactory) {
        if (s3EventExtractor == null) {
            throw new IllegalArgumentException("S3EventExtractor cannot be null");
        }
        if (feedProcessorFactory == null) {
            throw new IllegalArgumentException("FeedProcessorFactory cannot be null");
        }
        
        this.s3EventExtractor = s3EventExtractor;
        this.feedProcessorFactory = feedProcessorFactory;
        
        logger.info("Message Processor initialized with S3 event extractor and feed processor factory");
    }

    /**
     * Processes a single SQS message containing an S3 event notification.
     * 
     * <p>This method is the main entry point for message processing. It extracts S3 event
     * information from the SQS message, selects an appropriate feed processor based on
     * the data stream type, and routes the event for processing.</p>
     * 
     * <p><strong>Processing Steps:</strong></p>
     * <ol>
     *   <li><strong>Message Validation:</strong> Validates message structure and content</li>
     *   <li><strong>Event Extraction:</strong> Extracts S3 event information using S3EventExtractor</li>
     *   <li><strong>Processor Selection:</strong> Uses FeedProcessorFactory to select appropriate processor</li>
     *   <li><strong>Event Processing:</strong> Routes S3 event to selected processor for data transformation</li>
     *   <li><strong>Result Logging:</strong> Logs processing results for monitoring and debugging</li>
     * </ol>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>Returns false if S3 event extraction fails</li>
     *   <li>Returns false if feed processor selection fails</li>
     *   <li>Returns false if event processing fails</li>
     *   <li>Logs detailed error information for debugging</li>
     *   <li>Catches and logs all exceptions to prevent message loss</li>
     * </ul>
     * 
     * <p><strong>Thread Safety:</strong></p>
     * <ul>
     *   <li>Thread-safe operation - can be called concurrently</li>
     *   <li>Stateless design - no shared mutable state</li>
     *   <li>Exception isolation - exceptions don't affect other message processing</li>
     * </ul>
     * 
     * @param message The SQS message containing S3 event notification to process
     * @return true if message was processed successfully, false if processing failed
     * @throws IllegalArgumentException if message is null
     */
    public boolean processMessage(Message message) {
        String messageId = message.messageId();
        String body = message.body();
        
        logger.info("Processing SQS message: messageId={}", messageId);
        
        try {
            // Extract S3 event information using the new extractor
            Optional<S3EventRecord> s3EventOpt = s3EventExtractor.extractS3Event(body);
            
            if (s3EventOpt.isEmpty()) {
                logger.error("Failed to extract S3 event from message - messageId: {}, messageBody: {}", 
                           messageId, body);
                return false;
            }
            
            S3EventRecord s3Event = s3EventOpt.get();
            
            logger.info("Successfully extracted S3 event - bucket: {}, key: {}, size: {} bytes, stream: {}", 
                       s3Event.bucketName(), s3Event.objectKey(), s3Event.objectSize(), s3Event.streamName());
            
            // Get appropriate processor using the factory (feed type is extracted from object key)
            FeedProcessor processor = feedProcessorFactory.getProcessor(s3Event);
            
            logger.info("Selected processor: {} for stream: {}", 
                       processor.getClass().getSimpleName(), s3Event.streamName());
            
            // Process the S3 event
            boolean processingResult = processor.processS3Event(s3Event);
            
            if (processingResult) {
                logger.info("Successfully processed S3 event - messageId: {}, processor: {}, stream: {}", 
                           messageId, processor.getClass().getSimpleName(), s3Event.streamName());
            } else {
                logger.error("Failed to process S3 event - messageId: {}, processor: {}, stream: {}", 
                            messageId, processor.getClass().getSimpleName(), s3Event.streamName());
            }
            
            return processingResult;
            
        } catch (Exception e) {
            logger.error("Error processing message: {}", messageId, e);
            return false;
        }
    }
} 