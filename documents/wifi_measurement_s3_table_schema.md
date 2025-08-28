# Wi-Fi Access Point Localization Data Schema for AWS S3 Tables (Apache Iceberg)

## Table Schema Definition

```sql
CREATE TABLE wifi_measurements (
  -- Primary Keys
  bssid                    STRING,
  id                       STRING,

  measurement_timestamp    BIGINT,

  -- Device Information
  event_id                STRING,
  device_id               STRING,
  device_model            STRING,
  device_manufacturer     STRING,
  os_version              STRING,
  app_version             STRING,
  
  -- Location Data (GNSS/GPS)
  latitude                DOUBLE,
  longitude               DOUBLE,
  altitude                DOUBLE,
  location_accuracy       DOUBLE,
  location_timestamp      BIGINT,
  location_provider       STRING,
  location_source         STRING,
  speed                   DOUBLE,
  bearing                 DOUBLE,
  
  -- WiFi Signal Data
  ssid                    STRING,
  rssi                    INT,
  frequency               INT,
  scan_timestamp          BIGINT,
  
  -- Data Quality and Connection Tier
  connection_status       STRING,  -- 'CONNECTED' or 'SCAN'
  quality_weight          DOUBLE,  -- 2.0 for CONNECTED, 1.0 for SCAN
  
  -- Connected-Only Enrichment Fields (NULL for SCAN records)
  link_speed              INT,
  channel_width           INT,
  center_freq0            INT,
  center_freq1            INT,
  capabilities            STRING,
  is_80211mc_responder    BOOLEAN,
  is_passpoint_network    BOOLEAN,
  operator_friendly_name  STRING,
  venue_name              STRING,
  is_captive              BOOLEAN,
  num_scan_results        INT,
  
  -- Global Outlier Detection (stable, persistent flags)
  is_global_outlier           BOOLEAN,
  global_outlier_distance     DOUBLE,    -- Distance from AP centroid in meters
  global_outlier_threshold    DOUBLE,    -- Threshold used for detection
  global_detection_algorithm  STRING,    -- 'MAD', 'IQR', 'PERCENTILE'
  global_detection_timestamp  TIMESTAMP,
  global_detection_version    STRING,    -- Track algorithm improvements
  
  -- Ingestion and Processing Metadata
  ingestion_timestamp     TIMESTAMP,
  data_version            STRING,
  processing_batch_id     STRING,
  quality_score           DOUBLE     -- Overall quality score (0.0-1.0)
)
USING ICEBERG
PARTITIONED BY (years(ingestion_timestamp), months(ingestion_timestamp), days(ingestion_timestamp))
TBLPROPERTIES (
  'write.target-file-size-bytes' = '134217728',  -- 128MB target files
  'write.delete.mode' = 'merge-on-read',         -- Efficient deletes
  'write.update.mode' = 'merge-on-read',         -- Efficient updates
  'format-version' = '2'                         -- Enable row-level operations
);
```

## Core Query Patterns

### 1. Query for Candidate Selection (New Data Since Last Run)

```sql
-- Get new measurements for multiple APs since last processing run
SELECT bssid, event_id, measurement_timestamp, latitude, longitude, 
       rssi, connection_status, quality_weight, location_accuracy,
       ingestion_timestamp
FROM wifi_measurements 
WHERE ingestion_timestamp > ?  -- Last processing timestamp
  AND bssid IN (?, ?, ?, ...)  -- List of APs to process
  AND (is_global_outlier != true OR is_global_outlier IS NULL)
ORDER BY bssid, ingestion_timestamp;
```

### 2. Query Clean Data for Localization (Excluding Global Outliers)

```sql
-- Get clean measurement data for multiple APs (exclude global outliers)
SELECT bssid, latitude, longitude, rssi, frequency, link_speed,
       connection_status, quality_weight, measurement_timestamp,
       location_accuracy, ingestion_timestamp
FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...)  -- List of APs
  AND (is_global_outlier != true OR is_global_outlier IS NULL)
  AND ingestion_timestamp >= ?  -- Optional time filter based on ingestion
ORDER BY bssid, ingestion_timestamp DESC;
```

### 3. Update Global Outlier Labels (Batch Update)

```sql
-- Label measurements as global outliers for multiple APs
UPDATE wifi_measurements 
SET is_global_outlier = true,
    global_outlier_distance = CASE 
      WHEN event_id = ? THEN ?  -- event_id_1 -> distance_1
      WHEN event_id = ? THEN ?  -- event_id_2 -> distance_2
      -- ... more cases
      ELSE global_outlier_distance 
    END,
    global_outlier_threshold = ?,  -- Same threshold for batch
    global_detection_algorithm = ?,
    global_detection_timestamp = current_timestamp(),
    global_detection_version = ?
WHERE event_id IN (?, ?, ?, ...);  -- List of outlier event_ids
```

### 4. Delete Global Outliers (Batch Delete by AP)

```sql
-- Delete all global outliers for multiple APs
DELETE FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...)  -- List of APs
  AND is_global_outlier = true;
```

### 5. Delete All Data for Mobile Hotspots (Complete AP Removal)

```sql
-- Delete all measurements for identified mobile hotspots
DELETE FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...);  -- List of mobile hotspot BSSIDs
```

## Optimized Batch Processing Patterns

### Multi-AP Data Retrieval with Aggregation

```sql
-- Get measurement counts and basic stats for multiple APs
SELECT bssid,
       COUNT(*) as total_measurements,
       COUNT(CASE WHEN connection_status = 'CONNECTED' THEN 1 END) as connected_count,
       COUNT(CASE WHEN is_global_outlier = true THEN 1 END) as outlier_count,
       MIN(ingestion_timestamp) as first_ingestion,
       MAX(ingestion_timestamp) as last_ingestion,
       AVG(location_accuracy) as avg_accuracy
FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...)
GROUP BY bssid;
```

### Efficient AP Status Check

```sql
-- Check which APs have sufficient data for processing
SELECT bssid, 
       COUNT(*) as measurement_count,
       COUNT(CASE WHEN connection_status = 'CONNECTED' THEN 1 END) as connected_count
FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...)
  AND (is_global_outlier != true OR is_global_outlier IS NULL)
GROUP BY bssid
HAVING COUNT(*) >= 20  -- Minimum threshold for processing
ORDER BY bssid;
```

### Bulk Quality Assessment

```sql
-- Get quality metrics for multiple APs for processing decisions
SELECT bssid,
       AVG(quality_weight) as avg_quality,
       STDDEV(latitude) as spatial_variance_lat,
       STDDEV(longitude) as spatial_variance_lon,
       COUNT(DISTINCT DATE(ingestion_timestamp)) as active_ingestion_days
FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...)
  AND (is_global_outlier != true OR is_global_outlier IS NULL)
  AND ingestion_timestamp >= ?  -- Recent ingestion window
GROUP BY bssid;
```

## Index and Performance Considerations

### Recommended Query Optimization
- **Primary lookups**: Always include `bssid` in WHERE clause
- **Time-based filtering**: Use `ingestion_timestamp` for temporal queries and processing windows
- **Outlier exclusion**: Standard pattern `(is_global_outlier != true OR is_global_outlier IS NULL)`
- **Batch operations**: Use `IN` clauses for multiple BSSIDs to reduce query count
- **Partitioning**: Queries automatically benefit from ingestion-time based partition pruning

### Iceberg Automatic Optimizations
- **File clustering**: Automatic optimization based on query patterns
- **Partition pruning**: Time-based partitions automatically excluded
- **Compaction**: Small files automatically merged
- **Statistics**: Column statistics maintained for query planning

This schema provides the essential structure and query patterns needed for your programmatic outlier detection and AP localization processing while leveraging AWS S3 Tables' Iceberg capabilities for performance and data management.