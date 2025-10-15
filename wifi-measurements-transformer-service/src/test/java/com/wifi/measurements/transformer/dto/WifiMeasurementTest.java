// wifi-measurements-transformer-service/src/test/java/com/wifi/measurements/transformer/dto/WifiMeasurementTest.java

package com.wifi.measurements.transformer.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WifiMeasurement DTO Tests - Streamlined Schema")
class WifiMeasurementTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTest {

        @Test
        @DisplayName("Should create valid WifiMeasurement using builder with required fields")
        void builder_WithRequiredFields_ShouldCreateValidInstance() {
            // Given
            Instant now = Instant.now();
            
            // When
            WifiMeasurement measurement = WifiMeasurement.builder()
                .id("test-id-123")
                .bssid("aa:bb:cc:dd:ee:ff")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(37.7749)
                .longitude(-122.4194)
                .rssi(-50)
                .connectionStatus("CONNECTED")
                .qualityWeight(2.0)
                .source("s3://test-bucket/test-key.json")
                .ingestionTimestamp(now)
                .build();

            // Then
            assertThat(measurement).isNotNull();
            assertThat(measurement.id()).isEqualTo("test-id-123");
            assertThat(measurement.bssid()).isEqualTo("aa:bb:cc:dd:ee:ff");
            assertThat(measurement.measurementTimestamp()).isEqualTo(now.toEpochMilli());
            assertThat(measurement.latitude()).isEqualTo(37.7749);
            assertThat(measurement.longitude()).isEqualTo(-122.4194);
            assertThat(measurement.rssi()).isEqualTo(-50);
            assertThat(measurement.connectionStatus()).isEqualTo("CONNECTED");
            assertThat(measurement.qualityWeight()).isEqualTo(2.0);
            assertThat(measurement.source()).isEqualTo("s3://test-bucket/test-key.json");
            assertThat(measurement.ingestionTimestamp()).isEqualTo(now);
        }

        @Test
        @DisplayName("Should handle all optional fields correctly")
        void builder_WithAllOptionalFields_ShouldCreateCompleteInstance() {
            // Given
            Instant now = Instant.now();
            
            // When
            WifiMeasurement measurement = WifiMeasurement.builder()
                .id("test-id-456")
                .bssid("11:22:33:44:55:66")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(40.7128)
                .longitude(-74.0060)
                .altitude(10.5)
                .locationAccuracy(5.0)
                .rssi(-65)
                .frequency(2412)
                .connectionStatus("SCAN")
                .qualityWeight(1.0)
                .linkSpeed(150)
                .channelWidth(80)
                .centerFreq0(2422)
                .isGlobalOutlier(false)
                .source("s3://test-bucket/test-file.json")
                .ingestionTimestamp(now)
                .dataVersion("1.0.0")
                .processingBatchId("batch-123")
                .build();

            // Then
            assertThat(measurement).isNotNull();
            assertThat(measurement.altitude()).isEqualTo(10.5);
            assertThat(measurement.locationAccuracy()).isEqualTo(5.0);
            assertThat(measurement.frequency()).isEqualTo(2412);
            assertThat(measurement.linkSpeed()).isEqualTo(150);
            assertThat(measurement.channelWidth()).isEqualTo(80);
            assertThat(measurement.centerFreq0()).isEqualTo(2422);
            assertThat(measurement.isGlobalOutlier()).isFalse();
            assertThat(measurement.source()).isEqualTo("s3://test-bucket/test-file.json");
            assertThat(measurement.dataVersion()).isEqualTo("1.0.0");
            assertThat(measurement.processingBatchId()).isEqualTo("batch-123");
        }

        @Test
        @DisplayName("Should handle null optional fields gracefully")
        void builder_WithNullOptionalFields_ShouldCreateValidInstance() {
            // Given
            Instant now = Instant.now();
            
            // When
            WifiMeasurement measurement = WifiMeasurement.builder()
                .id("test-id-789")
                .bssid("77:88:99:aa:bb:cc")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(51.5074)
                .longitude(-0.1278)
                .rssi(-45)
                .connectionStatus("CONNECTED")
                .qualityWeight(2.0)
                .source("s3://test-bucket/test.json")
                .ingestionTimestamp(now)
                // Explicitly setting some fields to null
                .altitude(null)
                .frequency(null)
                .linkSpeed(null)
                .build();

            // Then
            assertThat(measurement).isNotNull();
            assertThat(measurement.altitude()).isNull();
            assertThat(measurement.frequency()).isNull();
            assertThat(measurement.linkSpeed()).isNull();
            // Verify required fields are still set
            assertThat(measurement.id()).isEqualTo("test-id-789");
            assertThat(measurement.bssid()).isEqualTo("77:88:99:aa:bb:cc");
            assertThat(measurement.latitude()).isEqualTo(51.5074);
            assertThat(measurement.longitude()).isEqualTo(-0.1278);
        }

        @Test
        @DisplayName("Should support method chaining in builder")
        void builder_MethodChaining_ShouldWork() {
            // Given
            Instant now = Instant.now();
            
            // When - all in one chain
            WifiMeasurement measurement = WifiMeasurement.builder()
                .id("chain-test")
                .bssid("ff:ee:dd:cc:bb:aa")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(35.6762)
                .longitude(139.6503)
                .rssi(-55)
                .connectionStatus("SCAN")
                .qualityWeight(1.0)
                .source("s3://test-bucket/chain.json")
                .ingestionTimestamp(now)
                .build();

            // Then
            assertThat(measurement).isNotNull();
            assertThat(measurement.id()).isEqualTo("chain-test");
        }
    }

    @Nested
    @DisplayName("Record Properties Tests")
    class RecordPropertiesTest {

        @Test
        @DisplayName("Should implement equals and hashCode correctly")
        void record_EqualsAndHashCode_ShouldWorkCorrectly() {
            // Given
            Instant now = Instant.now();
            WifiMeasurement measurement1 = createTestMeasurement("test-1", now);
            WifiMeasurement measurement2 = createTestMeasurement("test-1", now); // Same data
            WifiMeasurement measurement3 = createTestMeasurement("test-2", now); // Different ID

            // Then
            assertThat(measurement1).isEqualTo(measurement2);
            assertThat(measurement1).isNotEqualTo(measurement3);
            assertThat(measurement1.hashCode()).isEqualTo(measurement2.hashCode());
            assertThat(measurement1.hashCode()).isNotEqualTo(measurement3.hashCode());
        }

        @Test
        @DisplayName("Should implement toString correctly")
        void record_ToString_ShouldContainFieldValues() {
            // Given
            Instant now = Instant.now();
            WifiMeasurement measurement = createTestMeasurement("toString-test", now);

            // When
            String toString = measurement.toString();

            // Then
            assertThat(toString).contains("toString-test");
            assertThat(toString).contains("aa:bb:cc:dd:ee:ff");
            assertThat(toString).contains("CONNECTED");
            assertThat(toString).contains("37.7749");
            assertThat(toString).contains("-122.4194");
        }

        @Test
        @DisplayName("Should be immutable")
        void record_Immutability_ShouldNotAllowModification() {
            // Given
            Instant now = Instant.now();
            WifiMeasurement measurement = createTestMeasurement("immutable-test", now);

            // When & Then - Record fields should be final (this is enforced by the compiler)
            // We can't test direct field modification, but we can verify the getter values don't change
            String originalId = measurement.id();
            String originalBssid = measurement.bssid();
            
            // Multiple calls should return the same values
            assertThat(measurement.id()).isEqualTo(originalId);
            assertThat(measurement.bssid()).isEqualTo(originalBssid);
        }
    }

    @Nested
    @DisplayName("Data Validation Tests")
    class DataValidationTest {

        @Test
        @DisplayName("Should handle different connection statuses")
        void measurement_ConnectionStatus_ShouldSupportBothTypes() {
            // Given
            Instant now = Instant.now();

            // When
            WifiMeasurement connectedMeasurement = WifiMeasurement.builder()
                .id("connected-test")
                .bssid("11:22:33:44:55:66")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(37.7749)
                .longitude(-122.4194)
                .rssi(-40)
                .connectionStatus("CONNECTED")
                .qualityWeight(2.0)
                .source("s3://test-bucket/connected.json")
                .ingestionTimestamp(now)
                .build();

            WifiMeasurement scanMeasurement = WifiMeasurement.builder()
                .id("scan-test")
                .bssid("66:55:44:33:22:11")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(37.7749)
                .longitude(-122.4194)
                .rssi(-70)
                .connectionStatus("SCAN")
                .qualityWeight(1.0)
                .source("s3://test-bucket/scan.json")
                .ingestionTimestamp(now)
                .build();

            // Then
            assertThat(connectedMeasurement.connectionStatus()).isEqualTo("CONNECTED");
            assertThat(connectedMeasurement.qualityWeight()).isEqualTo(2.0);
            
            assertThat(scanMeasurement.connectionStatus()).isEqualTo("SCAN");
            assertThat(scanMeasurement.qualityWeight()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should handle typical RSSI ranges")
        void measurement_RssiValues_ShouldHandleTypicalRanges() {
            // Given
            Instant now = Instant.now();

            // When & Then - Test different RSSI values
            WifiMeasurement strongSignal = createMeasurementWithRssi("strong", -30, now);
            WifiMeasurement weakSignal = createMeasurementWithRssi("weak", -90, now);
            WifiMeasurement moderateSignal = createMeasurementWithRssi("moderate", -60, now);

            assertThat(strongSignal.rssi()).isEqualTo(-30);
            assertThat(weakSignal.rssi()).isEqualTo(-90);
            assertThat(moderateSignal.rssi()).isEqualTo(-60);
        }

        @Test
        @DisplayName("Should handle different frequency bands")
        void measurement_FrequencyBands_ShouldSupportCommonValues() {
            // Given
            Instant now = Instant.now();

            // When
            WifiMeasurement band2_4GHz = createMeasurementWithFrequency("2.4GHz", 2412, now);
            WifiMeasurement band5GHz = createMeasurementWithFrequency("5GHz", 5180, now);
            WifiMeasurement band6GHz = createMeasurementWithFrequency("6GHz", 6000, now);

            // Then
            assertThat(band2_4GHz.frequency()).isEqualTo(2412);
            assertThat(band5GHz.frequency()).isEqualTo(5180);
            assertThat(band6GHz.frequency()).isEqualTo(6000);
        }

        @Test
        @DisplayName("Should handle global outlier detection field")
        void measurement_GlobalOutlierField_ShouldDefaultToNull() {
            // Given
            Instant now = Instant.now();
            
            // When
            WifiMeasurement measurement = createTestMeasurement("outlier-test", now);

            // Then - Global outlier field should default to null (not yet processed)
            assertThat(measurement.isGlobalOutlier()).isNull();
        }
    }

    @Nested
    @DisplayName("Connected-Only Fields Tests")
    class ConnectedOnlyFieldsTest {

        @Test
        @DisplayName("Should handle connected-specific advanced algorithm fields")
        void measurement_ConnectedFields_ShouldSupportAdvancedFields() {
            // Given
            Instant now = Instant.now();
            
            // When
            WifiMeasurement connectedMeasurement = WifiMeasurement.builder()
                .id("connected-enriched")
                .bssid("aa:bb:cc:dd:ee:ff")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(37.7749)
                .longitude(-122.4194)
                .rssi(-45)
                .connectionStatus("CONNECTED")
                .qualityWeight(2.0)
                .linkSpeed(866)
                .channelWidth(80)
                .centerFreq0(5190)
                .source("s3://test-bucket/connected.json")
                .ingestionTimestamp(now)
                .build();

            // Then
            assertThat(connectedMeasurement.linkSpeed()).isEqualTo(866);
            assertThat(connectedMeasurement.channelWidth()).isEqualTo(80);
            assertThat(connectedMeasurement.centerFreq0()).isEqualTo(5190);
        }

        @Test
        @DisplayName("Should have null connected fields for SCAN measurements")
        void measurement_ScanMeasurement_ShouldHaveNullConnectedFields() {
            // Given
            Instant now = Instant.now();
            
            // When
            WifiMeasurement scanMeasurement = WifiMeasurement.builder()
                .id("scan-only")
                .bssid("aa:bb:cc:dd:ee:ff")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(37.7749)
                .longitude(-122.4194)
                .rssi(-65)
                .connectionStatus("SCAN")
                .qualityWeight(1.0)
                .source("s3://test-bucket/scan.json")
                .ingestionTimestamp(now)
                // Connected-only fields should be null for SCAN
                .linkSpeed(null)
                .channelWidth(null)
                .build();

            // Then
            assertThat(scanMeasurement.connectionStatus()).isEqualTo("SCAN");
            assertThat(scanMeasurement.linkSpeed()).isNull();
            assertThat(scanMeasurement.channelWidth()).isNull();
        }
    }

    @Nested
    @DisplayName("Source Field Tests")
    class SourceFieldTest {

        @Test
        @DisplayName("Should store S3 source file path")
        void measurement_Source_ShouldStoreS3Path() {
            // Given
            Instant now = Instant.now();
            String sourceFile = "s3://my-bucket/wifi-data/2024/01/15/scan-123.json";
            
            // When
            WifiMeasurement measurement = WifiMeasurement.builder()
                .id("source-test")
                .bssid("aa:bb:cc:dd:ee:ff")
                .measurementTimestamp(now.toEpochMilli())
                .latitude(37.7749)
                .longitude(-122.4194)
                .rssi(-50)
                .connectionStatus("CONNECTED")
                .qualityWeight(2.0)
                .source(sourceFile)
                .ingestionTimestamp(now)
                .build();

            // Then
            assertThat(measurement.source()).isEqualTo(sourceFile);
            assertThat(measurement.source()).startsWith("s3://");
            assertThat(measurement.source()).contains("my-bucket");
        }
    }

    // Helper methods
    private WifiMeasurement createTestMeasurement(String id, Instant timestamp) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("aa:bb:cc:dd:ee:ff")
            .measurementTimestamp(timestamp.toEpochMilli())
            .latitude(37.7749)
            .longitude(-122.4194)
            .rssi(-50)
            .connectionStatus("CONNECTED")
            .qualityWeight(2.0)
            .source("s3://test-bucket/test.json")
            .ingestionTimestamp(timestamp)
            .build();
    }

    private WifiMeasurement createMeasurementWithRssi(String id, Integer rssi, Instant timestamp) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("aa:bb:cc:dd:ee:ff")
            .measurementTimestamp(timestamp.toEpochMilli())
            .latitude(37.7749)
            .longitude(-122.4194)
            .rssi(rssi)
            .connectionStatus("CONNECTED")
            .qualityWeight(2.0)
            .source("s3://test-bucket/rssi-test.json")
            .ingestionTimestamp(timestamp)
            .build();
    }

    private WifiMeasurement createMeasurementWithFrequency(String id, Integer frequency, Instant timestamp) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("aa:bb:cc:dd:ee:ff")
            .measurementTimestamp(timestamp.toEpochMilli())
            .latitude(37.7749)
            .longitude(-122.4194)
            .rssi(-50)
            .frequency(frequency)
            .connectionStatus("CONNECTED")
            .qualityWeight(2.0)
            .source("s3://test-bucket/freq-test.json")
            .ingestionTimestamp(timestamp)
            .build();
    }
}
