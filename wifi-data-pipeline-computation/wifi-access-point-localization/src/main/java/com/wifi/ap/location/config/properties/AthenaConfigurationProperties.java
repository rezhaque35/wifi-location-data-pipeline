package com.wifi.ap.location.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration properties for AWS Athena integration.
 * 
 * These properties control how the service interacts with Athena for querying
 * wifi_measurements table in S3, including result file management.
 * 
 * Default Behavior:
 * - enableResultCleanup defaults to true (cleanup enabled by default)
 * - Users must explicitly set enableResultCleanup: false to disable cleanup
 * - This ensures cost optimization and proper resource management out of the box
 */
@ConfigurationProperties(prefix = "aws.athena")
public record AthenaConfigurationProperties(
    String database,
    String outputLocation,
    String resultBucket,
    String resultPrefix,
    int queryTimeoutSeconds,
    int resultFetchTimeoutSeconds,
    int maxResultsPerQuery,
    int lookbackDays,
    boolean enableResultCleanup
) {
    
    @ConstructorBinding
    public AthenaConfigurationProperties(
            String database,
            String outputLocation,
            String resultBucket,
            String resultPrefix,
            Integer queryTimeoutSeconds,
            Integer resultFetchTimeoutSeconds,
            Integer maxResultsPerQuery,
            Integer lookbackDays,
            Boolean enableResultCleanup) {
        this(
            database != null ? database : "wifi_measurements_db",
            outputLocation != null ? outputLocation : "s3://athena-query-results/wifi-ap-localization/",
            resultBucket != null ? resultBucket : "athena-query-results",
            resultPrefix != null ? resultPrefix : "wifi-ap-localization/",
            queryTimeoutSeconds != null ? queryTimeoutSeconds : 30,
            resultFetchTimeoutSeconds != null ? resultFetchTimeoutSeconds : 60,
            maxResultsPerQuery != null ? maxResultsPerQuery : 1000,
            lookbackDays != null ? lookbackDays : 30,
            enableResultCleanup != null ? enableResultCleanup : true  // Default to true - cleanup enabled by default
        );
    }
}
