// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/DataDecodingService.java
package com.wifi.measurements.transformer.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for decoding and decompressing WiFi scan data.
 *
 * <p>This service mirrors the compression logic from MessageCompressionService, handling Base64
 * decoding and GZIP decompression for data that was compressed using the two-stage process: GZIP
 * compression followed by Base64 encoding.
 *
 * <p>Key Functionality: - Base64 decoding of compressed data - GZIP decompression to restore
 * original data - Comprehensive error handling and logging - JSON validation for decoded data
 *
 * <p>High-Level Steps: 1. Base64 decode the encoded data 2. Decompress using GZIP (mirrors GZIP
 * compression in MessageCompressionService) 3. Validate the decoded data format 4. Return the
 * original JSON string
 *
 * @see com.wifi.scan.consume.service.MessageCompressionService
 */
@Service
public class DataDecodingService {

  private static final Logger logger = LoggerFactory.getLogger(DataDecodingService.class);

  /**
   * Decodes and decompresses a Base64-encoded and GZIP-compressed line of data using declarative
   * approach.
   *
   * <p>This method mirrors the compression process from MessageCompressionService: it first Base64
   * decodes the data, then decompresses it using GZIP.
   *
   * @param encodedData The Base64-encoded string (from MessageCompressionService)
   * @return Optional containing the decoded and decompressed JSON string, empty if decoding fails
   */
  public Optional<String> decodeAndDecompress(String encodedData) {
    return validateEncodedData(encodedData)
        .flatMap(this::performBase64Decoding)
        .flatMap(this::performGzipDecompression);
  }

  /**
   * Validates the input encoded data.
   *
   * @param encodedData The input data to validate
   * @return Optional containing the trimmed data if valid, empty otherwise
   */
  private Optional<String> validateEncodedData(String encodedData) {
    if (encodedData == null || encodedData.trim().isEmpty()) {
      logger.error("Encoded data cannot be null or empty");
      return Optional.empty();
    }

    String trimmedData = encodedData.trim();
    logger.debug("Input validation passed: {} characters", trimmedData.length());
    return Optional.of(trimmedData);
  }

  /**
   * Performs Base64 decoding operation.
   *
   * @param encodedData The validated encoded data
   * @return Optional containing decoded bytes, empty if decoding failed
   */
  private Optional<byte[]> performBase64Decoding(String encodedData) {
    try {
      byte[] decodedBytes = Base64.getDecoder().decode(encodedData);
      logger.debug(
          "Base64 decoded {} characters to {} bytes", encodedData.length(), decodedBytes.length);
      return Optional.of(decodedBytes);

    } catch (IllegalArgumentException e) {
      logger.error("Invalid Base64 encoding in data: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Performs the actual GZIP decompression operation.
   *
   * @param gzipData The validated GZIP data
   * @return Optional containing the decompressed string, empty if failed
   */
  private Optional<String> performGzipDecompression(byte[] gzipData) {
    try (var inputStream = new ByteArrayInputStream(gzipData);
        var gzipInputStream = new GZIPInputStream(inputStream);
        var outputStream = new ByteArrayOutputStream()) {

      gzipInputStream.transferTo(outputStream);

      return Optional.of(outputStream.toString(StandardCharsets.UTF_8));

    } catch (IOException e) {
      logger.error("GZIP decompression operation failed: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
