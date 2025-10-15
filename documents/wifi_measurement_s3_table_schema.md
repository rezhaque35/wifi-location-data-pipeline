# Wi-Fi Access Point Localization Data Schema for AWS S3 Tables (Apache Iceberg)

## Overview

This optimized schema contains only the essential fields required for WiFi access point localization algorithms, plus SSID for debugging and hotspot detection, reducing storage requirements by approximately 60% compared to the full measurement schema.

## Table Schema Definition

```sql
CREATE TABLE wifi_measurements (
  -- Primary Keys
  id                      STRING,    -- Unique measurement identifier
  bssid                   STRING,    -- Access point MAC address (normalized lowercase with colons)
  measurement_timestamp   BIGINT,    -- Timestamp when measurement was taken (epoch milliseconds)
  
  -- Location Data (GNSS/GPS) - Essential for all localization algorithms
  latitude                DOUBLE,    -- Device GPS latitude in decimal degrees
  longitude               DOUBLE,    -- Device GPS longitude in decimal degrees
  altitude                DOUBLE,    -- Device GPS altitude in meters (nullable)
  location_accuracy       DOUBLE,    -- GPS horizontal accuracy in meters
  
  -- WiFi Signal Data - Essential for signal propagation models
  ssid                    STRING,    -- Network name (useful for debugging and hotspot detection)
  rssi                    INT,       -- Received Signal Strength Indicator in dBm
  frequency               INT,       -- WiFi frequency in MHz (nullable for SCAN records)
  
  -- Data Quality and Connection Tier - Critical for algorithm selection and weighting
  connection_status       STRING,    -- 'CONNECTED' or 'SCAN'
  quality_weight          DOUBLE,    -- Measurement quality weight (2.0 for CONNECTED, 1.0 for SCAN)
  
  -- Connected-Only Advanced Algorithm Fields (NULL for SCAN records)
  -- Used by MLE and Bayesian algorithms for enhanced accuracy
  link_speed              INT,       -- Network link speed in Mbps
  channel_width           INT,       -- WiFi channel width in MHz
  center_freq0            INT,       -- Center frequency 0 in MHz
  
  -- Global Outlier Detection - For filtering invalid measurements
  is_global_outlier       BOOLEAN,   -- Flag indicating if measurement is a detected outlier
  
  -- Source and Processing Metadata
  source                  STRING,    -- S3 source file path (e.g., "s3://bucket/prefix/file.json")
  ingestion_timestamp     TIMESTAMP, -- When data was ingested into the system
  data_version            STRING,    -- Version of data schema/processing
  processing_batch_id     STRING     -- Batch identifier for processing tracking
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

## Storage Optimization

**Removed Fields (from original schema):**
- Device information (device_id, device_model, device_manufacturer, os_version, app_version)
- Extended location metadata (location_timestamp, location_provider, location_source, speed, bearing)
- Advanced network capabilities (capabilities, 802.11mc fields, Passpoint fields, venue info)
- Redundant timestamps (scan_timestamp, event_id)
- Detailed outlier metadata (outlier_distance, threshold, algorithm, detection_timestamp, detection_version)
- Derived quality score (quality_weight is sufficient)
- Unused frequency fields (center_freq1)
- Connection metadata (is_captive, num_scan_results)

**Retained Field (for operational benefits):**
- SSID: Kept for debugging, mobile hotspot detection, and network identification

**Storage Reduction:** Approximately 60% reduction in storage footprint

## Core Query Patterns

### 1. Query for Candidate Selection (New Data Since Last Run)

```sql
-- Get new measurements for multiple APs since last processing run
SELECT id, bssid, measurement_timestamp, 
       latitude, longitude, altitude, location_accuracy,
       rssi, frequency, connection_status, quality_weight,
       link_speed, channel_width, center_freq0,
       ingestion_timestamp, source
FROM wifi_measurements 
WHERE ingestion_timestamp > ?  -- Last processing timestamp
  AND bssid IN (?, ?, ?, ...)  -- List of APs to process
  AND (is_global_outlier != true OR is_global_outlier IS NULL)
ORDER BY bssid, ingestion_timestamp;
```

### 2. Query Clean Data for Localization (Excluding Global Outliers)

```sql
-- Get clean measurement data for multiple APs (exclude global outliers)
SELECT id, bssid, measurement_timestamp,
       latitude, longitude, altitude, location_accuracy,
       rssi, frequency, connection_status, quality_weight,
       link_speed, channel_width, center_freq0
FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...)  -- List of APs
  AND (is_global_outlier != true OR is_global_outlier IS NULL)
  AND ingestion_timestamp >= ?  -- Optional time filter based on ingestion
ORDER BY bssid, ingestion_timestamp DESC;
```

### 3. Update Global Outlier Labels (Batch Update)

```sql
-- Label measurements as global outliers for multiple APs (simplified)
UPDATE wifi_measurements 
SET is_global_outlier = true
WHERE id IN (?, ?, ?, ...);  -- List of outlier measurement IDs
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

### 6. Query by Source File (Track specific ingestion batches)

```sql
-- Get all measurements from specific source file
SELECT id, bssid, measurement_timestamp, 
       latitude, longitude, rssi, connection_status
FROM wifi_measurements 
WHERE source = ?  -- S3 source file path
ORDER BY bssid, measurement_timestamp;
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
       AVG(location_accuracy) as avg_accuracy,
       AVG(quality_weight) as avg_quality_weight
FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...)
GROUP BY bssid;
```

### Efficient AP Status Check

```sql
-- Check which APs have sufficient data for processing
SELECT bssid, 
       COUNT(*) as measurement_count,
       COUNT(CASE WHEN connection_status = 'CONNECTED' THEN 1 END) as connected_count,
       AVG(location_accuracy) as avg_accuracy
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
       COUNT(DISTINCT DATE(ingestion_timestamp)) as active_ingestion_days,
       COUNT(DISTINCT source) as source_file_count
FROM wifi_measurements 
WHERE bssid IN (?, ?, ?, ...)
  AND (is_global_outlier != true OR is_global_outlier IS NULL)
  AND ingestion_timestamp >= ?  -- Recent ingestion window
GROUP BY bssid;
```

## Index and Performance Considerations

### Recommended Query Optimization
- **Primary lookups**: Always include `bssid` in WHERE clause for best performance
- **Time-based filtering**: Use `ingestion_timestamp` for temporal queries and processing windows
- **Outlier exclusion**: Standard pattern `(is_global_outlier != true OR is_global_outlier IS NULL)`
- **Batch operations**: Use `IN` clauses for multiple BSSIDs to reduce query count
- **Partitioning**: Queries automatically benefit from ingestion-time based partition pruning
- **Source tracking**: Use `source` field to track and query specific ingestion batches

### Iceberg Automatic Optimizations
- **File clustering**: Automatic optimization based on query patterns
- **Partition pruning**: Time-based partitions automatically excluded
- **Compaction**: Small files automatically merged
- **Statistics**: Column statistics maintained for query planning

## Field Mapping from Original Schema

For reference, here's how the streamlined schema maps to the original fields:

| New Schema Field      | Original Schema Field(s)         | Notes                                    |
|-----------------------|----------------------------------|------------------------------------------|
| id                    | id                               | Unchanged - unique measurement ID        |
| bssid                 | bssid                            | Unchanged - AP MAC address               |
| measurement_timestamp | measurement_timestamp            | Unchanged - measurement time             |
| latitude              | latitude                         | Unchanged - GPS latitude                 |
| longitude             | longitude                        | Unchanged - GPS longitude                |
| altitude              | altitude                         | Unchanged - GPS altitude                 |
| location_accuracy     | location_accuracy                | Unchanged - GPS accuracy                 |
| ssid                  | ssid                             | Unchanged - network name                 |
| rssi                  | rssi                             | Unchanged - signal strength              |
| frequency             | frequency                        | Unchanged - WiFi frequency               |
| connection_status     | connection_status                | Unchanged - CONNECTED/SCAN               |
| quality_weight        | quality_weight                   | Unchanged - measurement weight           |
| link_speed            | link_speed                       | Unchanged - network speed                |
| channel_width         | channel_width                    | Unchanged - WiFi channel width           |
| center_freq0          | center_freq0                     | Unchanged - center frequency             |
| is_global_outlier     | is_global_outlier                | Simplified - only the flag retained      |
| source                | N/A (NEW)                        | New field - S3 source file path          |
| ingestion_timestamp   | ingestion_timestamp              | Unchanged - ingestion time               |
| data_version          | data_version                     | Unchanged - schema version               |
| processing_batch_id   | processing_batch_id              | Unchanged - batch identifier             |

This optimized schema provides the essential structure needed for AP localization processing while reducing storage requirements by approximately 60%, leveraging AWS S3 Tables' Iceberg capabilities for performance and data management.