// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/S3FileProcessorService.java
package com.wifi.measurements.transformer.service;

import com.wifi.measurements.transformer.config.properties.S3ConfigurationProperties;
import com.wifi.measurements.transformer.dto.S3EventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/**
 * Service for processing S3 files with streaming support and retry logic.
 * 
 * Provides functionality to download S3 files and read them line by line
 * for efficient memory usage during processing.
 */
@Service
public class S3FileProcessorService {
    
    private static final Logger logger = LoggerFactory.getLogger(S3FileProcessorService.class);
    
    private final S3Client s3Client;
    private final S3ConfigurationProperties s3Config;
    
    public S3FileProcessorService(S3Client s3Client, S3ConfigurationProperties s3Config) {
        this.s3Client = s3Client;
        this.s3Config = s3Config;
    }
    
    /**
     * Downloads an S3 file and returns a stream of lines for processing.
     * 
     * @param s3EventRecord The S3 event record containing file information
     * @return Stream of lines from the file
     * @throws IOException If file cannot be read
     * @throws IllegalArgumentException If file is too large
     */
    // TODO: Add retry logic using manual retry or spring-retry dependency
    public Stream<String> streamFileLines(S3EventRecord s3EventRecord) throws IOException {
        validateFileSize(s3EventRecord);
        
        logger.info("Downloading S3 file: bucket={}, key={}, size={} bytes", 
                   s3EventRecord.bucketName(), s3EventRecord.objectKey(), s3EventRecord.objectSize());
        
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3EventRecord.bucketName())
                .key(s3EventRecord.objectKey())
                .build();
            
            ResponseInputStream<GetObjectResponse> responseStream = s3Client.getObject(getObjectRequest);
            
            // Create buffered reader for efficient line-by-line reading
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseStream, StandardCharsets.UTF_8)
            );
            
            // Return stream of lines with proper resource management
            return reader.lines()
                .onClose(() -> {
                    try {
                        reader.close();
                        responseStream.close();
                    } catch (IOException e) {
                        logger.warn("Error closing S3 response stream: {}", e.getMessage());
                    }
                });
                
        } catch (NoSuchKeyException e) {
            logger.error("S3 object not found: bucket={}, key={}", 
                        s3EventRecord.bucketName(), s3EventRecord.objectKey());
            throw new IOException("S3 object not found: " + s3EventRecord.objectKey(), e);
        } catch (S3Exception e) {
            logger.error("S3 error downloading file: bucket={}, key={}, error={}", 
                        s3EventRecord.bucketName(), s3EventRecord.objectKey(), e.getMessage());
            throw new IOException("S3 error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validates that the file size is within configured limits.
     * 
     * @param s3EventRecord The S3 event record to validate
     * @throws IllegalArgumentException If file is too large
     */
    private void validateFileSize(S3EventRecord s3EventRecord) {
        if (s3EventRecord.objectSize() > s3Config.maxFileSize()) {
            throw new IllegalArgumentException(
                String.format("File size %d bytes exceeds maximum allowed size %d bytes", 
                             s3EventRecord.objectSize(), s3Config.maxFileSize())
            );
        }
        
        logger.debug("File size validation passed: {} bytes (max: {} bytes)", 
                    s3EventRecord.objectSize(), s3Config.maxFileSize());
    }

} 