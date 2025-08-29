package com.wifi.measurements.transformer.processor;

/**
 * Processing result for a single message, containing  status and receipt handle.
 *
 * @param status        whether the message was processed successfully
 * @param receiptHandle the SQS receipt handle for message deletion
 */
public record MessageProcessingResult(boolean status, String receiptHandle) {
}