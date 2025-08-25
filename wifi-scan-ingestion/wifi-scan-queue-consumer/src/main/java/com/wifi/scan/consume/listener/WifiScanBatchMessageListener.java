package com.wifi.scan.consume.listener;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import com.wifi.scan.consume.service.BatchFirehoseMessageService;
import com.wifi.scan.consume.service.KafkaMonitoringService;
import com.wifi.scan.consume.service.MessageTransformationService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Batch message listener for processing WiFi scan data from Kafka. Implements an optimized batch
 * processing pipeline with Firehose integration.
 *
 * <p>Key Features: - Processes batches of 150 messages for optimal throughput - Implements message
 * compression for efficient storage - Manages dynamic Firehose sub-batching with natural rate
 * limiting - Provides comprehensive error handling and metrics
 *
 * <p>Processing Pipeline: 1. Receive 150-message batch from Kafka 2. Validate and extract valid
 * messages 3. Compress ALL messages (GZIP + Base64) 4. Send to Firehose with dynamic sub-batching
 * (rate limiting handled at delivery level) 5. Commit offset after successful delivery
 */
@Slf4j
@Component
public class WifiScanBatchMessageListener {

    public static final String FIREHOSE_FAIL_PROCESSING = "Failed to deliver all of the compressed batch to Firehose after processing";
    @Autowired
    private BatchFirehoseMessageService batchFirehoseService;

    @Autowired
    private KafkaConsumerMetrics metrics;

    @Autowired
    private KafkaMonitoringService monitoringService;

    @Autowired
    private MessageTransformationService compressionService;

    // Performance metrics
    @Getter
    private final AtomicLong processedBatchCount = new AtomicLong(0);

    @Getter
    private final AtomicLong processedMessageCount = new AtomicLong(0);

    @Getter
    private volatile long lastBatchProcessingTimeMs = 0;

    /**
     * Main batch message processing method. Handles batches of 150 messages from Kafka with manual
     * acknowledgment.
     *
     * <p>Processing Steps: 1. Validate batch and extract messages 2. Compress all messages (GZIP +
     * Base64) 3. Send to Firehose with dynamic sub-batching (rate limiting at delivery level) 4.
     * Commit offset after successful delivery
     *
     * <p>Error Handling: - Empty batches are acknowledged immediately - Processing errors are logged
     * with full context - Failed batches may be acknowledged to prevent reprocessing
     *
     * <p>Rate Limiting: - Handled naturally at Firehose delivery level through sub-batching - No
     * batch-level rate limiting that would discard entire batches - Maximizes throughput up to
     * Firehose limits
     *
     * @param records        Batch of Kafka records (typically 150 messages)
     * @param acknowledgment Manual acknowledgment callback
     */
    @KafkaListener(
            topics = "${kafka.topic.name}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "batchKafkaListenerContainerFactory")
    public void listenBatch(ConsumerRecords<String, String> records, Acknowledgment acknowledgment) {
        long batchStartTime = System.currentTimeMillis();
        int batchSize = records.count();

        if (isEmptyBatch(batchSize)) {
            handleEmptyBatch(acknowledgment, batchStartTime);
            return;
        }

        try {
            recordMetrics(batchSize);
            List<String> validMessages = extractValidMessages(records);

            if (validMessages.isEmpty()) {
                handleEmptyBatch(acknowledgment, batchStartTime);
                return;
            }

            List<String> compressedMessages = compressionService.transform(validMessages);

            boolean deliverySuccess = batchFirehoseService.deliverBatch(compressedMessages);

            if (deliverySuccess) {
                handleSuccessfulBatchCompletion(acknowledgment, batchStartTime, batchSize, compressedMessages.size());
            } else {
                handleBatchProcessingFailure(records, acknowledgment, batchStartTime, batchSize, FIREHOSE_FAIL_PROCESSING);
            }

        } catch (Exception e) {
            handleBatchProcessingFailure(records, acknowledgment, batchStartTime, batchSize, e);
        }
    }

    /**
     * Checks if the received batch is empty.
     *
     * @param batchSize Number of records in the batch
     * @return true if batch is empty, false otherwise
     */
    private boolean isEmptyBatch(int batchSize) {
        if (batchSize == 0) {
            log.debug("Received empty batch from Kafka");
            return true;
        }
        return false;
    }

    /**
     * Initializes batch processing by recording activity and starting metrics collection.
     *
     * @param batchSize Number of messages in the batch
     */
    private void recordMetrics(int batchSize) {
        log.info("Processing Kafka batch: {} messages", batchSize);
        monitoringService.recordPollActivity();
        metrics.recordBatchConsumed(batchSize);
    }


    /**
     * Handles successful batch completion by acknowledging offset and updating metrics.
     *
     * @param acknowledgment    Acknowledgment callback for offset commitment
     * @param batchStartTime    Processing start timestamp
     * @param originalBatchSize Original number of messages in batch
     * @param processedCount    Number of messages successfully processed
     */
    private void handleSuccessfulBatchCompletion(
            Acknowledgment acknowledgment,
            long batchStartTime,
            int originalBatchSize,
            int processedCount) {

        if (acknowledgment != null) {
            acknowledgment.acknowledge();
            log.debug("Batch acknowledged successfully after Firehose delivery");
        }

        updateBatchMetrics(batchStartTime, originalBatchSize, processedCount);
        log.info("Successfully processed batch: {} compressed messages delivered to Firehose", processedCount);
    }

    /**
     * Validates and extracts messages from Kafka records. Filters out invalid messages and performs
     * basic JSON validation.
     *
     * @param records ConsumerRecords to validate and extract from
     * @return List of valid message values
     */
    private List<String> extractValidMessages(ConsumerRecords<String, String> records) {
        List<String> validMessages = StreamSupport.stream(records.spliterator(), false)
                .map(ConsumerRecord::value)
                .filter(this::validateMessageFormat)
                .toList();
        log.info("Validated {} out of {} messages in batch", validMessages.size(), records.count());
        return validMessages;
    }

    /**
     * Handles empty batch scenario after compression. Acknowledges batch to prevent reprocessing and
     * updates metrics.
     *
     * @param acknowledgment Acknowledgment callback
     * @param batchStartTime Processing start timestamp
     */
    private void handleEmptyBatch(Acknowledgment acknowledgment, long batchStartTime) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
            log.debug("Empty batch acknowledged");
        }

        lastBatchProcessingTimeMs = System.currentTimeMillis() - batchStartTime;
        log.info("Empty batch processed in {} ms", lastBatchProcessingTimeMs);
    }

    /**
     * Handles batch processing exceptions. Logs error details and optionally acknowledges to prevent
     * reprocessing.
     *
     * @param records        Original consumer records
     * @param acknowledgment Acknowledgment callback
     * @param batchStartTime Processing start timestamp
     * @param batchSize      Original batch size
     * @param e              Exception that occurred
     */
    private void handleBatchProcessingFailure(
            ConsumerRecords<String, String> records,
            Acknowledgment acknowledgment,
            long batchStartTime,
            int batchSize,
            Exception e) {
        handleBatchProcessingFailure(records, acknowledgment, batchStartTime, batchSize, e.getMessage());
    }


    /**
     * Handles batch processing exceptions. Logs error details and optionally acknowledges to prevent
     * reprocessing.
     *
     * @param records        Original consumer records
     * @param acknowledgment Acknowledgment callback
     * @param batchStartTime Processing start timestamp
     * @param batchSize      Original batch size
     * @param e              Exception that occurred
     */
    private void handleBatchProcessingFailure(
            ConsumerRecords<String, String> records,
            Acknowledgment acknowledgment,
            long batchStartTime,
            int batchSize,
            String errorMessage) {
        log.error("Error processing WiFi scan batch: {} messages", batchSize, errorMessage);

        lastBatchProcessingTimeMs = System.currentTimeMillis() - batchStartTime;
        metrics.recordBatchFailed();

        logBatchDetails(records);

        if (acknowledgment != null) {
            try {
                acknowledgment.acknowledge();
                log.warn("Acknowledged failed batch to prevent reprocessing");
            } catch (Exception ackException) {
                log.error("Failed to acknowledge batch after processing error", ackException);
            }
        }
    }

    /**
     * Updates metrics for successful batch processing. Tracks processing time and validates against
     * performance targets.
     *
     * @param batchStartTime    Processing start timestamp
     * @param originalBatchSize Original number of messages
     * @param processedCount    Number of successfully processed messages
     */
    private void updateBatchMetrics(long batchStartTime, int originalBatchSize, int processedCount) {
        processedBatchCount.incrementAndGet();
        processedMessageCount.addAndGet(processedCount);
        lastBatchProcessingTimeMs = System.currentTimeMillis() - batchStartTime;

        metrics.recordBatchProcessed(lastBatchProcessingTimeMs, originalBatchSize);

        log.info(
                "Batch processed successfully: {} compressed messages in {} ms",
                processedCount,
                lastBatchProcessingTimeMs);

        if (lastBatchProcessingTimeMs > 1200) {
            log.warn(
                    "Total batch processing exceeded 1.2 second target: {} ms", lastBatchProcessingTimeMs);
        }
    }

    /**
     * Logs detailed batch information for debugging. Only logs when debug level is enabled.
     *
     * @param records Consumer records to log details for
     */
    private void logBatchDetails(ConsumerRecords<String, String> records) {
        if (log.isDebugEnabled()) {
            for (ConsumerRecord<String, String> record : records) {
                log.debug(
                        "Batch record - Topic: {}, Partition: {}, Offset: {}, Key: {}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key());
            }
        }
    }

    /**
     * Validates the format of a received message. Performs basic JSON validation and null checks.
     *
     * @param messageValue Message content to validate
     * @return true if message appears valid, false otherwise
     */
    public boolean validateMessageFormat(String messageValue) {
        if (messageValue == null || messageValue.trim().isEmpty()) {
            log.warn("Received null or empty message in batch");
            return false;
        }

        try {
            String trimmed = messageValue.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                log.trace("Message appears to be valid JSON format");
                return true;
            } else {
                log.warn("Message does not appear to be JSON format in batch");
                return false;
            }
        } catch (Exception e) {
            log.error("Error validating message format in batch", e);
            return false;
        }
    }
}
