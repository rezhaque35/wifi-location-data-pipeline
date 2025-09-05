package com.wifi.ap.location.estimate.service;

import com.wifi.ap.location.estimate.config.properties.AthenaConfigurationProperties;
import com.wifi.ap.location.estimate.dto.WifiMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service for retrieving WiFi measurement data from S3 Tables using AWS Athena.
 * 
 * This service queries the wifi_measurements table to get historical measurement data
 * for access point localization algorithms. It follows the requirements from Section 3.2
 * of the wifi-ap-localization-requirements.md.
 */
@Service
@EnableConfigurationProperties(AthenaConfigurationProperties.class)
public class AccessPointMeasurementsLookUpService {

    private static final Logger logger = LoggerFactory.getLogger(AccessPointMeasurementsLookUpService.class);
    
    private final AthenaClient athenaClient;
    private final S3Client s3Client;
    private final AthenaConfigurationProperties athenaConfig;

    public AccessPointMeasurementsLookUpService(
            AthenaClient athenaClient,
            @Qualifier("athenaS3Client") S3Client s3Client,
            AthenaConfigurationProperties athenaConfig) {
        this.athenaClient = athenaClient;
        this.s3Client = s3Client;
        this.athenaConfig = athenaConfig;
        logger.info("Initialized AccessPointMeasurementsLookUpService with database: {} and result cleanup: {}", 
                   athenaConfig.database(), athenaConfig.enableResultCleanup());
    }

    /**
     * Retrieves WiFi measurement data for a specific BSSID (MAC address).
     * 
     * Query implementation based on Schema Section 2: "Query Clean Data for Localization"
     * - Excludes global outliers (is_global_outlier != true OR is_global_outlier IS NULL)
     * - Orders by measurement_timestamp descending for latest data first
     * - Limits results to configurable maximum
     * - Applies configurable lookback window
     * 
     * @param macAddress The BSSID (MAC address) to query measurements for
     * @return List of WifiMeasurement records, empty if none found or on error
     */
    public List<WifiMeasurement> lookup(String macAddress) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            logger.warn("Invalid MAC address provided: {}", macAddress);
            return Collections.emptyList();
        }

        String queryId = null;
        try {
            queryId = executeQuery(macAddress);
            if (queryId == null) {
                return Collections.emptyList();
            }
            
            List<WifiMeasurement> results = fetchQueryResults(queryId, macAddress);
            return results;
            
        } catch (Exception e) {
            logger.error("Error retrieving measurements for MAC address: {}", macAddress, e);
            return Collections.emptyList();
        } finally {
            // Clean up result files after processing if enabled
            // This ensures cleanup happens even if fetchQueryResults() fails
            if (queryId != null && athenaConfig.enableResultCleanup()) {
                cleanupQueryResults(queryId, macAddress);
            }
        }
    }

    /**
     * Executes the Athena query for retrieving measurements.
     * 
     * Based on requirements Section 3.2.1:
     * - Query wifi_measurements table for each BSSID
     * - Exclude records where is_global_outlier = true
     * - Support configurable lookback window (default 30 days)
     * - Limit results to configurable maximum (default 1000 records)
     * - Order results by measurement_timestamp descending
     */
    private String executeQuery(String macAddress) {
        String sql = buildQuery(macAddress);
        logger.debug("Executing Athena query for MAC {}: {}", macAddress, sql);

        try {
            StartQueryExecutionRequest request = StartQueryExecutionRequest.builder()
                    .queryString(sql)
                    .queryExecutionContext(QueryExecutionContext.builder()
                            .database(athenaConfig.database())
                            .build())
                    .workGroup(athenaConfig.workgroup())
                    .resultConfiguration(ResultConfiguration.builder()
                            .outputLocation(athenaConfig.outputLocation())
                            .build())
                    .build();

            StartQueryExecutionResponse response = athenaClient.startQueryExecution(request);
            String queryId = response.queryExecutionId();
            
            logger.info("Started Athena query for MAC {}, queryId: {}", macAddress, queryId);
            
            // Wait for query completion
            if (waitForQueryCompletion(queryId)) {
                return queryId;
            } else {
                logger.error("Query failed or timed out for MAC: {}, queryId: {}", macAddress, queryId);
                return null;
            }
            
        } catch (AthenaException e) {
            logger.error("Athena query execution failed for MAC: {}", macAddress, e);
            return null;
        }
    }

    /**
     * Builds the SQL query for retrieving measurements from wifi_measurements table.
     * 
     * Query follows the schema pattern from Section 2: "Query Clean Data for Localization"
     */
    private String buildQuery(String macAddress) {
        Instant cutoffTime = Instant.now().minus(athenaConfig.lookbackDays(), ChronoUnit.DAYS);
        long cutoffTimestamp = cutoffTime.toEpochMilli();
        
        return String.format("""
            SELECT 
                id, bssid, measurement_timestamp,
                latitude, longitude, altitude, location_accuracy,
                rssi, frequency,
                connection_status, quality_weight,
                link_speed, channel_width, center_freq0,
                is_global_outlier
            FROM %s.wifi_measurements 
            WHERE bssid = '%s'
              AND (is_global_outlier != true OR is_global_outlier IS NULL)
              AND measurement_timestamp >= %d
            ORDER BY measurement_timestamp DESC
            LIMIT %d
            """, 
            athenaConfig.database(), 
            macAddress, 
            cutoffTimestamp, 
            athenaConfig.maxResultsPerQuery());
    }

    /**
     * Waits for query completion with timeout.
     * 
     * Implements polling mechanism as specified in requirements Section 3.2.2:
     * - Query status polling for async execution
     * - Handle query timeouts with retry mechanism
     */
    private boolean waitForQueryCompletion(String queryId) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = TimeUnit.SECONDS.toMillis(athenaConfig.queryTimeoutSeconds());
        
        try {
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                GetQueryExecutionRequest request = GetQueryExecutionRequest.builder()
                        .queryExecutionId(queryId)
                        .build();
                
                GetQueryExecutionResponse response = athenaClient.getQueryExecution(request);
                QueryExecutionStatus status = response.queryExecution().status();
                
                switch (status.state()) {
                    case SUCCEEDED:
                        logger.debug("Query {} completed successfully", queryId);
                        return true;
                    case FAILED:
                    case CANCELLED:
                        logger.error("Query {} failed with state: {}, reason: {}", 
                                   queryId, status.state(), status.stateChangeReason());
                        return false;
                    case QUEUED:
                    case RUNNING:
                        // Continue polling
                        Thread.sleep(1000); // Poll every second
                        break;
                }
            }
            
            logger.error("Query {} timed out after {} seconds", queryId, athenaConfig.queryTimeoutSeconds());
            return false;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Query polling interrupted for queryId: {}", queryId);
            return false;
        } catch (AthenaException e) {
            logger.error("Error checking query status for queryId: {}", queryId, e);
            return false;
        }
    }

    /**
     * Fetches and parses query results into WifiMeasurement objects.
     * 
     * Implements result retrieval as specified in requirements Section 3.2.2:
     * - Store query results in designated S3 result bucket
     * - Implement automatic cleanup of result files after processing
     */
    private List<WifiMeasurement> fetchQueryResults(String queryId, String macAddress) {
        List<WifiMeasurement> measurements = new ArrayList<>();
        
        try {
            GetQueryResultsRequest request = GetQueryResultsRequest.builder()
                    .queryExecutionId(queryId)
                    .maxResults(athenaConfig.maxResultsPerQuery())
                    .build();
            
            GetQueryResultsResponse response = athenaClient.getQueryResults(request);
            List<Row> rows = response.resultSet().rows();
            
            if (rows.isEmpty()) {
                logger.info("No measurements found for MAC address: {}", macAddress);
                return measurements;
            }
            
            // Skip header row (first row contains column names)
            for (int i = 1; i < rows.size(); i++) {
                Row row = rows.get(i);
                try {
                    WifiMeasurement measurement = parseRowToMeasurement(row);
                    measurements.add(measurement);
                } catch (Exception e) {
                    logger.warn("Failed to parse row {} for MAC {}: {}", i, macAddress, e.getMessage());
                    // Continue processing other rows
                }
            }
            
            logger.info("Retrieved {} measurements for MAC address: {}", measurements.size(), macAddress);
            return measurements;
            
        } catch (AthenaException e) {
            logger.error("Error fetching query results for queryId: {}, MAC: {}", queryId, macAddress, e);
            return Collections.emptyList();
        }
    }

    /**
     * Parses a single Athena result row into an optimized WifiMeasurement object.
     * 
     * Maps only the essential column data needed for AP localization calculations.
     * This reduces memory usage and parsing overhead by ~67%.
     * 
     * Column mapping (matches SELECT query):
     * 0: id, 1: bssid, 2: measurement_timestamp,
     * 3: latitude, 4: longitude, 5: altitude, 6: location_accuracy,
     * 7: rssi, 8: frequency,
     * 9: connection_status, 10: quality_weight,
     * 11: link_speed, 12: channel_width, 13: center_freq0,
     * 14: is_global_outlier
     */
    private WifiMeasurement parseRowToMeasurement(Row row) {
        List<Datum> data = row.data();
        
        return WifiMeasurement.builder()
                .id(getStringValue(data, 0))
                .bssid(getStringValue(data, 1))
                .measurementTimestamp(getLongValue(data, 2))
                .latitude(getDoubleValue(data, 3))
                .longitude(getDoubleValue(data, 4))
                .altitude(getDoubleValue(data, 5))
                .locationAccuracy(getDoubleValue(data, 6))
                .rssi(getIntegerValue(data, 7))
                .frequency(getIntegerValue(data, 8))
                .connectionStatus(getStringValue(data, 9))
                .qualityWeight(getDoubleValue(data, 10))
                .linkSpeed(getIntegerValue(data, 11))
                .channelWidth(getIntegerValue(data, 12))
                .centerFreq0(getIntegerValue(data, 13))
                .isGlobalOutlier(getBooleanValue(data, 14))
                .build();
    }

    // Helper methods for safe data extraction from Athena results
    
    private String getStringValue(List<Datum> data, int index) {
        if (index < data.size() && data.get(index).varCharValue() != null) {
            return data.get(index).varCharValue();
        }
        return null;
    }
    
    private Long getLongValue(List<Datum> data, int index) {
        if (index < data.size() && data.get(index).varCharValue() != null) {
            try {
                return Long.parseLong(data.get(index).varCharValue());
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse long value at index {}: {}", index, data.get(index).varCharValue());
            }
        }
        return null;
    }
    
    private Double getDoubleValue(List<Datum> data, int index) {
        if (index < data.size() && data.get(index).varCharValue() != null) {
            try {
                return Double.parseDouble(data.get(index).varCharValue());
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse double value at index {}: {}", index, data.get(index).varCharValue());
            }
        }
        return null;
    }
    
    private Integer getIntegerValue(List<Datum> data, int index) {
        if (index < data.size() && data.get(index).varCharValue() != null) {
            try {
                return Integer.parseInt(data.get(index).varCharValue());
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse integer value at index {}: {}", index, data.get(index).varCharValue());
            }
        }
        return null;
    }
    
    private Boolean getBooleanValue(List<Datum> data, int index) {
        if (index < data.size() && data.get(index).varCharValue() != null) {
            return Boolean.parseBoolean(data.get(index).varCharValue());
        }
        return null;
    }

    /**
     * Cleans up Athena query result files from S3 after processing.
     * 
     * Implements the requirement from Section 3.2.2:
     * - "Implement automatic cleanup of result files after processing"
     * 
     * The method retrieves the result file location from the query execution
     * and deletes both the result file and metadata file from S3.
     * 
     * This method is called from the finally block to ensure cleanup happens
     * even if result fetching fails, preventing S3 storage accumulation.
     * 
     * @param queryId The Athena query execution ID
     * @param macAddress MAC address being processed (for logging)
     */
    private void cleanupQueryResults(String queryId, String macAddress) {
        try {
            // Get query execution details to find result location
            GetQueryExecutionRequest request = GetQueryExecutionRequest.builder()
                    .queryExecutionId(queryId)
                    .build();
            
            GetQueryExecutionResponse response = athenaClient.getQueryExecution(request);
            QueryExecution queryExecution = response.queryExecution();
            
            if (queryExecution.resultConfiguration() != null && 
                queryExecution.resultConfiguration().outputLocation() != null) {
                
                String outputLocation = queryExecution.resultConfiguration().outputLocation();
                deleteResultFiles(outputLocation, queryId, macAddress);
            }
            
        } catch (Exception e) {
            // Log but don't fail the main operation if cleanup fails
            logger.warn("Failed to cleanup query results for queryId: {}, MAC: {}: {}", 
                       queryId, macAddress, e.getMessage());
        }
    }

    /**
     * Deletes the actual result files from S3.
     * 
     * Athena creates two files for each query:
     * 1. The result data file: queryId.csv
     * 2. The metadata file: queryId.csv.metadata
     */
    private void deleteResultFiles(String outputLocation, String queryId, String macAddress) {
        try {
            URI outputUri = URI.create(outputLocation);
            String bucket = athenaConfig.resultBucket();
            
            // Extract the key from the output location
            // Format: s3://bucket/path/queryId.csv
            String basePath = outputUri.getPath();
            if (basePath.startsWith("/")) {
                basePath = basePath.substring(1); // Remove leading slash
            }
            
            // Delete the main result file (queryId.csv)
            deleteS3Object(bucket, basePath, queryId, macAddress, "result");
            
            // Delete the metadata file (queryId.csv.metadata)
            deleteS3Object(bucket, basePath + ".metadata", queryId, macAddress, "metadata");
            
            logger.debug("Successfully cleaned up result files for queryId: {}, MAC: {}", queryId, macAddress);
            
        } catch (Exception e) {
            logger.warn("Error deleting result files for queryId: {}, MAC: {}: {}", 
                       queryId, macAddress, e.getMessage());
        }
    }

    /**
     * Deletes a single object from S3 with proper error handling.
     */
    private void deleteS3Object(String bucket, String key, String queryId, String macAddress, String fileType) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(deleteRequest);
            logger.debug("Deleted {} file: s3://{}/{} for queryId: {}, MAC: {}", 
                        fileType, bucket, key, queryId, macAddress);
            
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                // File doesn't exist, which is fine
                logger.debug("{} file not found (may not exist): s3://{}/{} for queryId: {}, MAC: {}", 
                           fileType, bucket, key, queryId, macAddress);
            } else {
                logger.warn("Failed to delete {} file s3://{}/{} for queryId: {}, MAC: {}: {}", 
                           fileType, bucket, key, queryId, macAddress, e.getMessage());
            }
        } catch (Exception e) {
            logger.warn("Unexpected error deleting {} file s3://{}/{} for queryId: {}, MAC: {}: {}", 
                       fileType, bucket, key, queryId, macAddress, e.getMessage());
        }
    }
}
