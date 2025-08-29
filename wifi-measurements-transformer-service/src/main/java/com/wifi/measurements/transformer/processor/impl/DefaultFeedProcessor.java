// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/impl/DefaultFeedProcessor.java
package com.wifi.measurements.transformer.processor.impl;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.wifi.measurements.transformer.dto.WifiMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.measurements.transformer.dto.FeedUploadEvent;
import com.wifi.measurements.transformer.dto.WifiScanData;
import com.wifi.measurements.transformer.processor.FeedProcessor;
import com.wifi.measurements.transformer.service.DataDecodingService;
import com.wifi.measurements.transformer.service.S3FileProcessorService;
import com.wifi.measurements.transformer.service.WiFiMeasurementsPublisher;
import com.wifi.measurements.transformer.service.WifiDataTransformationService;

/**
 * Default feed processor for handling WiFi scan data and unknown feed types.
 *
 * <p>This processor implements the complete data processing pipeline for WiFi measurements: 1. S3
 * file download and streaming 2. Base64 decoding and decompression 3. JSON parsing and data
 * extraction 4. Data validation and sanity checking 5. Schema mapping and normalization 6. Writing
 * to storage (Kinesis Data Firehose)
 *
 * <p>This processor serves as the fallback for unknown feed types and the primary processor for
 * WiFi scan data streams.
 */
@Service
public class DefaultFeedProcessor implements FeedProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFeedProcessor.class);

    private final S3FileProcessorService s3FileProcessorService;
    private final DataDecodingService dataDecodingService;
    private final WifiDataTransformationService wifiDataTransformationService;
    private final WiFiMeasurementsPublisher measurementsPublisher;
    private final ObjectMapper objectMapper;

    public DefaultFeedProcessor(
            S3FileProcessorService s3FileProcessorService,
            DataDecodingService dataDecodingService,
            WifiDataTransformationService wifiDataTransformationService,
            WiFiMeasurementsPublisher measurementsPublisher,
            ObjectMapper objectMapper) {
        this.s3FileProcessorService = s3FileProcessorService;
        this.dataDecodingService = dataDecodingService;
        this.wifiDataTransformationService = wifiDataTransformationService;
        this.measurementsPublisher = measurementsPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSupportedFeedType() {
        return "unknown"; // Default processor handles unknown feed types
    }

    @Override
    public boolean canProcess(String feedType) {
        // Default processor can handle any feed type as a fallback
        return true;
    }

    @Override
    public boolean process(FeedUploadEvent feedUploadEvent) {

        String processingBatchId = UUID.randomUUID().toString();
        AtomicInteger processedLines = new AtomicInteger(0);
        AtomicInteger totalMeasurements = new AtomicInteger(0);
        boolean success = false;

        try {
            logBeginning(feedUploadEvent, processingBatchId);

            try (Stream<String> lines = s3FileProcessorService.streamFileLines(feedUploadEvent)) {
                lines
                        .peek(line -> processedLines.incrementAndGet())
                        .map(String::trim)
                        .filter(Predicate.not(String::isEmpty))
                        .flatMap(this::decodeData)
                        .flatMap(this::toWifiScanData)
                        .flatMap(toAPLocationMeasurements(processingBatchId))
                        .peek(measurement -> totalMeasurements.incrementAndGet())
                        .forEach(measurementsPublisher::publishMeasurement);

                logCompletion(processedLines, totalMeasurements);

                success = true;
            }

        } catch (Exception e) {
            handleError(feedUploadEvent, e);
        } finally {
            flushPublisher();
        }

        return success;
    }

    private void flushPublisher() {
        boolean success;
        // Always flush any remaining measurements in the batch
        try {
            measurementsPublisher.flushCurrentBatch();
            logger.debug("Flushed remaining batch after file processing");
        } catch (Exception e) {
            logger.error("Failed to flush remaining batch: {}", e.getMessage(), e);
            success = false;
        }
    }

    private static void handleError(FeedUploadEvent feedUploadEvent, Exception e) {
        logger.error(
                "Processing failed for object: {}, error: {}",
                feedUploadEvent.objectKey(),
                e.getMessage(),
                e);
    }

    private static void logCompletion(AtomicInteger processedLines, AtomicInteger totalMeasurements) {
        logger.info(
                "File processing completed: {} lines processed, {} measurements generated",
                processedLines.get(),
                totalMeasurements.get());
    }

    private Function<WifiScanData, Stream<? extends WifiMeasurement>> toAPLocationMeasurements(String processingBatchId) {
        return wifiScanData ->
                wifiDataTransformationService.transformToMeasurements(
                        wifiScanData, processingBatchId);
    }

    private static void logBeginning(FeedUploadEvent feedUploadEvent, String processingBatchId) {
        logger.debug(
                "Starting file processing: bucket={}, key={}, batchId={}",
                feedUploadEvent.bucketName(),
                feedUploadEvent.objectKey(),
                processingBatchId);
    }

    @Override
    public String getProcessingMetrics() {
        return String.format(
                "Processor: %s - Basic metrics not implemented", getClass().getSimpleName());
    }

    @Override
    public int getPriority() {
        return 0; // Default priority
    }

    /**
     * Parses a JSON string into WiFi scan data DTO.
     *
     * @param jsonData The JSON string containing WiFi scan data
     * @return Optional containing parsed WiFi scan data, empty if parsing fails
     */
    private Optional<WifiScanData> parseWifiScanData(String jsonData) {

        try {
            WifiScanData wifiScanData = objectMapper.readValue(jsonData, WifiScanData.class);

            logger.debug(
                    "Successfully parsed WiFi scan data: connectedEvents={}, scanResults={}",
                    wifiScanData.wifiConnectedEvents() != null
                            ? wifiScanData.wifiConnectedEvents().size()
                            : 0,
                    wifiScanData.scanResults() != null ? wifiScanData.scanResults().size() : 0);

            return Optional.of(wifiScanData);

        } catch (JsonProcessingException e) {
            logger.error("JSON parsing failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error during JSON parsing: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Safely decodes data, returning Optional.
     *
     * @param line The encoded line to decode
     * @return Optional containing decoded data
     */
    private Stream<String> decodeData(String line) {
        return dataDecodingService.decodeAndDecompress(line).stream();
    }

    /**
     * Safely parses WiFi scan data, returning null if parsing fails. Errors are logged but do not
     * interrupt the stream processing.
     *
     * @param decodedData The decoded JSON data
     * @return Parsed WiFi scan data or null if parsing fails
     */
    private Stream<WifiScanData> toWifiScanData(String decodedData) {
        return parseWifiScanData(decodedData).stream();
    }
}
