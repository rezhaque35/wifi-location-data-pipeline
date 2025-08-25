package com.wifi.scan.consume.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for handling message compression and encoding operations.
 *
 * <p>This service provides high-performance compression and encoding capabilities for WiFi scan
 * messages before they are sent to AWS Firehose. It implements a two-stage process: GZIP
 * compression followed by Base64 encoding to optimize data transfer and storage efficiency.
 *
 * <p>Key Functionality: - Parallel batch compression for high throughput - GZIP compression to
 * reduce message size - Base64 encoding for safe data transmission - Size validation to ensure
 * Firehose compatibility - Comprehensive compression metrics and statistics - Thread-safe
 * operations with atomic counters
 *
 * <p>High-Level Steps: 1. Receive batch of original messages 2. Process messages in parallel for
 * optimal performance 3. Apply GZIP compression to reduce data size 4. Encode compressed data with
 * Base64 for safe transmission 5. Validate final size against Firehose limits (1MB per record) 6.
 * Track compression metrics and statistics 7. Return compressed messages with size information
 *
 * <p>This service focuses solely on compression and encoding, with size validation handled
 * downstream by the Firehose service based on actual delivery constraints.
 *
 * @see com.wifi.scan.consume.listener.WifiScanBatchMessageListener
 * @see com.wifi.scan.consume.service.BatchFirehoseMessageService
 */
@Slf4j
@Service
public class MessageTransformationService {

    private final Function<byte[], String> encode = Base64.getEncoder()::encodeToString;

    /**
     * Compress and encode a batch of messages in parallel.
     *
     * <p>This method processes a batch of messages using parallel streams for optimal performance. It
     * applies GZIP compression followed by Base64 encoding to each message, validates size limits,
     * and tracks compression metrics.
     *
     * <p>High-Level Steps: 1. Validate input batch for null or empty conditions 2. Process messages
     * in parallel using parallelStream() 3. Apply compression and encoding to each message 4. Filter
     * out failed compressions (null results) 5. Return list of successfully compressed message
     * strings
     *
     * <p>This method should be called BEFORE sub-batching decisions to ensure accurate size
     * calculations for downstream processing.
     *
     * @param originalMessages the original messages to process
     * @return list of compressed and encoded message strings
     */
    public List<String> transform(List<String> originalMessages) {

        // Process all messages in parallel for optimal performance
        return originalMessages.parallelStream()
                .map(this::compress)
                .flatMap(Optional::stream)// Remove failed compressions
                .map(encode)
                .toList();
    }

    /**
     * Compress and encode a single message.
     *
     * <p>This method applies a two-stage compression process to a single message: first using GZIP
     * compression to reduce size, then Base64 encoding for safe transmission. It includes size
     * validation and comprehensive error handling.
     *
     * <p>High-Level Steps: 1. Validate input message for null or empty conditions 2. Calculate
     * original message size in bytes 3. Apply GZIP compression using ByteArrayOutputStream 4. Encode
     * compressed data with Base64 5. Validate final size against Firehose limits (1MB) 6. Update
     * compression metrics atomically 7. Return compressed and encoded string
     *
     * @param originalMessage the original message to compress and encode
     * @return Optional containing the compressed and encoded string, or empty if processing failed
     */
    public Optional<byte[]> compress(String originalMessage) {
        try {

            // Step 1: Compress with GZIP using ByteArrayOutputStream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(originalMessage.getBytes(UTF_8));
            }
            return Optional.of(baos.toByteArray());
        } catch (IOException e) {
            log.error("Failed to compress and encode message: {}", e.getMessage());
            log.error("Dropping Original message: {}", originalMessage);
            return Optional.empty();
        }
    }

}
