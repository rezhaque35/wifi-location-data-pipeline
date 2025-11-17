package com.wifi.ap.location.measurements.service;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import com.wifi.ap.location.measurements.outlier.Outliers;
import com.wifi.ap.location.measurements.outlier.global.GlobalOutlierDetector;
import com.wifi.ap.location.measurements.outlier.local.LocalOutlierDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MeasurementFilteringService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MeasurementFilteringService")
class MeasurementFilteringServiceTest {

    @Mock
    private GlobalOutlierDetector globalOutlierDetector;
    
    @Mock
    private LocalOutlierDetector localOutlierDetector;
    
    private MeasurementFilteringService filterService;

    @BeforeEach
    void setUp() {
        filterService = new MeasurementFilteringService(globalOutlierDetector, localOutlierDetector);
    }

    @Nested
    @DisplayName("Filter Method Tests")
    class FilterMethodTests {

        @Test
        @DisplayName("Should return empty when input is empty")
        void filter_WhenInputEmpty_ShouldReturnEmpty() {
            // Given
            WifiMeasurements wifiMeasurements = WifiMeasurements.of(Collections.emptyList());
            when(globalOutlierDetector.detectGlobalOutliers(any())).thenReturn(new Outliers(Set.of()));
            when(localOutlierDetector.detectLocalOutliers(any(), any())).thenReturn(new Outliers(Set.of()));

            // When
            WifiMeasurements result = filterService.filter(wifiMeasurements, Optional.empty());

            // Then
            assertThat(result.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should perform outlier detection and filtering when measurements provided")
        void filter_WhenMeasurementsProvided_ShouldPerformOutlierDetection() {
            // Given
            List<WifiMeasurement> measurements = createValidMeasurements(15);
            WifiMeasurements wifiMeasurements = WifiMeasurements.of(measurements);
            Outliers globalOutliers = new Outliers(Set.of("1", "2"));
            Outliers localOutliers = new Outliers(Set.of("3", "4"));
            
            when(globalOutlierDetector.detectGlobalOutliers(any())).thenReturn(globalOutliers);
            when(localOutlierDetector.detectLocalOutliers(any(), any())).thenReturn(localOutliers);

            // When
            WifiMeasurements result = filterService.filter(wifiMeasurements, Optional.empty());

            // Then
            assertThat(result.size()).isEqualTo(11); // 15 - 4 outliers
            
            // Verify outliers are excluded
            Set<String> resultIds = result.measurements().stream().map(WifiMeasurement::id).collect(java.util.stream.Collectors.toSet());
            assertThat(resultIds).doesNotContain("1", "2", "3", "4");
            
            verify(globalOutlierDetector).detectGlobalOutliers(any());
            verify(localOutlierDetector).detectLocalOutliers(any(), any());
        }

        @Test
        @DisplayName("Should handle overlapping global and local outliers")
        void filter_WhenOverlappingOutliers_ShouldHandleCorrectly() {
            // Given
            List<WifiMeasurement> measurements = createValidMeasurements(12);
            WifiMeasurements wifiMeasurements = WifiMeasurements.of(measurements);
            Outliers globalOutliers = new Outliers(Set.of("1", "2", "3"));
            Outliers localOutliers = new Outliers(Set.of("2", "3", "4")); // 2 and 3 overlap
            
            when(globalOutlierDetector.detectGlobalOutliers(any())).thenReturn(globalOutliers);
            when(localOutlierDetector.detectLocalOutliers(any(), any())).thenReturn(localOutliers);

            // When
            WifiMeasurements result = filterService.filter(wifiMeasurements, Optional.empty());

            // Then
            assertThat(result.size()).isEqualTo(8); // 12 - 4 unique outliers (1,2,3,4)
            
            Set<String> resultIds = result.measurements().stream().map(WifiMeasurement::id).collect(java.util.stream.Collectors.toSet());
            assertThat(resultIds).doesNotContain("1", "2", "3", "4");
        }

        @Test
        @DisplayName("Should handle empty outlier detection results")
        void filter_WhenNoOutliersDetected_ShouldReturnAllValidMeasurements() {
            // Given
            List<WifiMeasurement> measurements = createValidMeasurements(12);
            WifiMeasurements wifiMeasurements = WifiMeasurements.of(measurements);
            
            when(globalOutlierDetector.detectGlobalOutliers(any())).thenReturn(new Outliers(Set.of()));
            when(localOutlierDetector.detectLocalOutliers(any(), any())).thenReturn(new Outliers(Set.of()));

            // When
            WifiMeasurements result = filterService.filter(wifiMeasurements, Optional.empty());

            // Then
            assertThat(result.size()).isEqualTo(12);
        }

        @Test
        @DisplayName("Should handle current estimation parameter")
        void filter_WithCurrentEstimation_ShouldAcceptParameter() {
            // Given
            List<WifiMeasurement> measurements = createValidMeasurements(5);
            WifiMeasurements wifiMeasurements = WifiMeasurements.of(measurements);
            APLocation currentEstimation = APLocation.builder()
                                                     .macAddress("00:11:22:33:44:55")
                                                     .latitude(37.7749)
                                                     .longitude(-122.4194)
                                                     .build();
            
            when(globalOutlierDetector.detectGlobalOutliers(any())).thenReturn(new Outliers(Set.of()));
            when(localOutlierDetector.detectLocalOutliers(any(), any())).thenReturn(new Outliers(Set.of()));

            // When
            WifiMeasurements result = filterService.filter(wifiMeasurements, Optional.of(currentEstimation));

            // Then
            assertThat(result.size()).isEqualTo(5); // Should work normally regardless of current estimation
        }
    }

    // Helper methods for creating test data

    /**
     * Creates measurements with valid location data.
     */
    private List<WifiMeasurement> createValidMeasurements(int count) {
        List<WifiMeasurement> measurements = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            // Small variations around San Francisco
            double lat = 37.7749 + (i * 0.001);
            double lon = -122.4194 + (i * 0.001);
            measurements.add(createMeasurement(String.valueOf(i), lat, lon));
        }
        
        return measurements;
    }

    /**
     * Creates a single measurement with specified coordinates.
     */
    private WifiMeasurement createMeasurement(String id, Double lat, Double lon) {
        return WifiMeasurement.builder()
            .id(id)
            .bssid("00:11:22:33:44:55")
            .measurementTimestamp(System.currentTimeMillis())
            .latitude(lat)
            .longitude(lon)
            .rssi(-50)
            .connectionStatus("CONNECTED")
            .build();
    }
}