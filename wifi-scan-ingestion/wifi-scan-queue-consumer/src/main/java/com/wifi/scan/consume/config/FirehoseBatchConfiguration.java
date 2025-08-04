package com.wifi.scan.consume.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration class for Firehose batch processing constraints.
 * 
 * This class reads batch constraint values from application.yml, allowing
 * for different configurations between production and test environments.
 * In production, these values should match AWS Firehose hard limits.
 * In test environments, smaller values can be used for easier testing.
 * 
 * Production values (AWS limits):
 * - max-batch-size: 500 records
 * - max-batch-size-bytes: 4MB (4,194,304 bytes)
 * - max-record-size-bytes: 1MB (1,048,576 bytes)
 * 
 * Test values (for easier testing):
 * - max-batch-size: 10 records
 * - max-batch-size-bytes: 1KB (1,024 bytes)
 * - max-record-size-bytes: 512 bytes
 */
@Configuration
@ConfigurationProperties(prefix = "aws.firehose.batch-constraints")
@Data
public class FirehoseBatchConfiguration {
    
    /**
     * Maximum number of records per batch.
     * AWS Firehose limit: 500 records
     * Test value: 10 records for easier testing
     */
    private int maxBatchSize = 500; // Default to AWS limit
    
    /**
     * Maximum batch size in bytes.
     * AWS Firehose limit: 4MB (4,194,304 bytes)
     * Test value: 1KB (1,024 bytes) for easier testing
     */
    private long maxBatchSizeBytes = 4 * 1024 * 1024; // Default to 4MB AWS limit
    
    /**
     * Maximum individual record size in bytes.
     * AWS Firehose limit: 1MB (1,048,576 bytes)
     * Test value: 512 bytes for easier testing
     */
    private long maxRecordSizeBytes = 1024 * 1024; // Default to 1MB AWS limit
} 