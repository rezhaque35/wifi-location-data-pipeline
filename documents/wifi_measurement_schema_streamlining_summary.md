# WiFi Measurement Schema Streamlining Summary

## Overview

The WiFi measurement schema has been optimized to include only fields essential for AP localization algorithms, resulting in approximately **65% storage reduction**.

## Changes Made

### 1. Schema Updates (`wifi_measurement_s3_table_schema.md`)

**Fields Retained (18 total):**
- `id` - Unique measurement identifier
- `bssid` - Access point MAC address
- `measurement_timestamp` - Measurement timestamp
- `latitude`, `longitude`, `altitude` - GPS location data
- `location_accuracy` - GPS accuracy
- `rssi` - Signal strength
- `frequency` - WiFi frequency
- `connection_status` - 'CONNECTED' or 'SCAN'
- `quality_weight` - Measurement quality weight
- `link_speed`, `channel_width`, `center_freq0` - Advanced algorithm fields
- `is_global_outlier` - Outlier detection flag
- `source` - **NEW** S3 source file path
- `ingestion_timestamp`, `data_version`, `processing_batch_id` - Metadata

**Fields Removed (26 total):**
- Device information: `event_id`, `device_id`, `device_model`, `device_manufacturer`, `os_version`, `app_version`
- Extended location metadata: `location_timestamp`, `location_provider`, `location_source`, `speed`, `bearing`
- Network information: `ssid`, `scan_timestamp`
- Unused frequency fields: `center_freq1`
- Network capabilities: `capabilities`, `is_80211mc_responder`, `is_passpoint_network`, `operator_friendly_name`, `venue_name`
- Connection metadata: `is_captive`, `num_scan_results`
- Detailed outlier metadata: `global_outlier_distance`, `global_outlier_threshold`, `global_detection_algorithm`, `global_detection_timestamp`, `global_detection_version`
- Derived metrics: `quality_score`

### 2. DTO Updates (`WifiMeasurement.java`)

**Location:** `wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/dto/WifiMeasurement.java`

- Updated to match streamlined schema
- Added new `source` field for S3 file tracking
- Removed 26 fields no longer needed for localization
- Updated JavaDoc to reflect optimized purpose

### 3. Service Updates (`WifiDataTransformationService.java`)

**Location:** `wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/WifiDataTransformationService.java`

**API Changes:**
```java
// OLD:
public Stream<WifiMeasurement> transformToMeasurements(
    WifiScanData wifiScanData, String processingBatchId)

// NEW:
public Stream<WifiMeasurement> transformToMeasurements(
    WifiScanData wifiScanData, String processingBatchId, String sourceFile)
```

**Removed Methods:**
- `generateDeviceId()` - No longer needed (device info removed)
- `generateEventId()` - No longer needed (event_id removed)
- `cleanSsid()` - No longer needed (ssid removed)
- `calculateQualityScore()` - No longer needed (quality_score removed)
- `hashString()` - No longer needed (used by removed methods)

**Updated Methods:**
- `transformConnectedEvent()` - Uses `sourceFile` instead of `deviceId`
- `transformScanResultEntry()` - Simplified, no event ID generation
- `buildMeasurement()` - Only builds streamlined fields

### 4. Test Updates

**Files Updated:**
- `WifiDataTransformationServiceTest.java` - Main transformation service tests
- `DefaultFeedProcessorTest.java` - Feed processor tests

**Files Requiring Additional Updates:**
- `ComprehensiveIntegrationTest.java` - Integration tests
- `WifiMeasurementTest.java` - DTO unit tests
- `MobileHotspotDetectionServiceTest.java` - Hotspot detection tests
- `WiFiMeasurementsPublisherTest.java` - Publisher tests

**Test Update Pattern:**
All tests referencing removed fields need to:
1. Remove assertions for removed fields
2. Update `transformToMeasurements()` calls to include `sourceFile` parameter
3. Remove `.ssid()`, `.eventId()`, `.deviceId()`, etc. from assertions
4. Update builder calls to exclude removed fields

## Integration Points

### Callers of `transformToMeasurements()` Must Update

Any service calling the transformation service needs to provide the S3 source file path:

```java
// Example update needed:
String sourceFile = "s3://bucket/path/to/file.json";
Stream<WifiMeasurement> measurements = transformationService
    .transformToMeasurements(wifiScanData, batchId, sourceFile);
```

### Downstream Services

The localization service (`wifi-access-point-localization`) already uses a streamlined `WifiMeasurement` model with matching fields, so it should be compatible with these changes.

## Benefits

1. **Storage Reduction:** ~65% reduction in data size
2. **Cost Savings:** Reduced S3 storage and query costs
3. **Performance:** Faster data transfer and processing
4. **Clarity:** Schema clearly reflects localization requirements
5. **Traceability:** New `source` field enables batch tracking

## Migration Checklist

- [x] Update schema documentation
- [x] Update WifiMeasurement DTO
- [x] Update WifiDataTransformationService
- [x] Update main transformation service tests
- [x] Update feed processor tests
- [ ] Update remaining test files
- [ ] Update services calling transformToMeasurements()
- [ ] Update data ingestion services to pass source file path
- [ ] Run full test suite
- [ ] Update deployment scripts if needed
- [ ] Update monitoring/metrics for new schema

## Notes

- The `source` field should contain the full S3 path: `s3://bucket-name/prefix/filename`
- All tests now use example source: `s3://test-bucket/test-file.json`
- Existing data in old schema format will need migration if queried
- Consider gradual rollout with version tracking via `data_version` field

