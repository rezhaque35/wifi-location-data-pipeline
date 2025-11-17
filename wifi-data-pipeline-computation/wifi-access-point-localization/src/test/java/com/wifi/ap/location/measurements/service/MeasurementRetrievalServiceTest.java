package com.wifi.ap.location.measurements.service;

import com.wifi.ap.location.config.properties.AthenaConfigurationProperties;
import com.wifi.ap.location.measurements.service.MeasurementRetrievalService;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccessPointMeasurementsLookUpService.
 * 
 * These tests verify the Athena integration for retrieving WiFi measurement data,
 * using sample data from wifi-measurements-sample.json and mocking AWS SDK calls.
 */
@ExtendWith(MockitoExtension.class)
class MeasurementRetrievalServiceTest {

    @Mock
    private AthenaClient athenaClient;
    
    @Mock
    private S3Client s3Client;

    private AthenaConfigurationProperties athenaConfig;
    private MeasurementRetrievalService service;

    // Test data based on wifi-measurements-sample.json
    private static final String TEST_MAC_ADDRESS = "b8:f8:53:c0:1e:ff";
    private static final String TEST_QUERY_ID = "test-query-id-12345";
    
    @BeforeEach
    void setUp() {
        athenaConfig = new AthenaConfigurationProperties(
                "wifi_measurements_db",
                "s3://athena-query-results/wifi-ap-localization/",
                "athena-query-results",
                "wifi-ap-localization/",
                30,    // queryTimeoutSeconds
                60,    // resultFetchTimeoutSeconds
                1000,  // maxResultsPerQuery
                30,    // lookbackDays
                true   // enableResultCleanup
        );
        
        service = new MeasurementRetrievalService(athenaClient, s3Client, athenaConfig);
    }

    @Test
    void lookup_WithValidMacAddress_ReturnsWifiMeasurements() {
        // Arrange
        setupSuccessfulQueryExecution();
        setupQueryResults();

        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isPresent();
        WifiMeasurements results = optionalResults.get();
        assertThat(results.size()).isEqualTo(2);
        
        // Verify first measurement (CONNECTED)
        WifiMeasurement firstMeasurement = results.measurements().get(0);
        assertThat(firstMeasurement.id()).isEqualTo("4ad8b312-b5a6-4ddf-874c-02d62fce279d");
        assertThat(firstMeasurement.bssid()).isEqualTo(TEST_MAC_ADDRESS);
        assertThat(firstMeasurement.connectionStatus()).isEqualTo("CONNECTED");
        assertThat(firstMeasurement.qualityWeight()).isEqualTo(2.0);
        assertThat(firstMeasurement.rssi()).isEqualTo(-58);
        assertThat(firstMeasurement.latitude()).isEqualTo(40.6768816);
        assertThat(firstMeasurement.longitude()).isEqualTo(-74.416391);
        assertThat(firstMeasurement.frequency()).isEqualTo(5660);
        assertThat(firstMeasurement.linkSpeed()).isEqualTo(351);
        assertThat(firstMeasurement.channelWidth()).isEqualTo(80);
        assertThat(firstMeasurement.centerFreq0()).isEqualTo(5650);
        assertThat(firstMeasurement.isGlobalOutlier()).isFalse();
        
        // Verify second measurement (SCAN)
        WifiMeasurement secondMeasurement = results.measurements().get(1);
        assertThat(secondMeasurement.id()).isEqualTo("b9532b20-8612-4d69-9e7a-590a58834246");
        assertThat(secondMeasurement.bssid()).isEqualTo(TEST_MAC_ADDRESS);
        assertThat(secondMeasurement.connectionStatus()).isEqualTo("SCAN");
        assertThat(secondMeasurement.qualityWeight()).isEqualTo(1.0);
        assertThat(secondMeasurement.rssi()).isEqualTo(-61);
        assertThat(secondMeasurement.frequency()).isNull(); // Not available for SCAN
        assertThat(secondMeasurement.linkSpeed()).isNull(); // Not available for SCAN
        assertThat(secondMeasurement.isGlobalOutlier()).isFalse();
        
        // Verify proper query was executed
        verifyQueryExecution();
        
        // Verify S3 cleanup was called
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void lookup_WithNullMacAddress_ReturnsEmptyList() {
        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(null);

        // Assert
        assertThat(optionalResults).isEmpty();
        verifyNoInteractions(athenaClient);
    }

    @Test
    void lookup_WithEmptyMacAddress_ReturnsEmptyList() {
        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup("");

        // Assert
        assertThat(optionalResults).isEmpty();
        verifyNoInteractions(athenaClient);
    }

    @Test
    void lookup_WithWhitespaceMacAddress_ReturnsEmptyList() {
        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup("   ");

        // Assert
        assertThat(optionalResults).isEmpty();
        verifyNoInteractions(athenaClient);
    }

    @Test
    void lookup_QueryExecutionFails_ReturnsEmptyList() {
        // Arrange
        when(athenaClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenThrow(AthenaException.builder()
                        .message("Query execution failed")
                        .build());

        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isEmpty();
        verify(athenaClient).startQueryExecution(any(StartQueryExecutionRequest.class));
    }

    @Test
    void lookup_QueryTimesOut_ReturnsEmptyList() {
        // Arrange
        when(athenaClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenReturn(StartQueryExecutionResponse.builder()
                        .queryExecutionId(TEST_QUERY_ID)
                        .build());

        // Mock query status to always return RUNNING (simulating timeout)
        when(athenaClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(GetQueryExecutionResponse.builder()
                        .queryExecution(QueryExecution.builder()
                                .status(QueryExecutionStatus.builder()
                                        .state(QueryExecutionState.RUNNING)
                                        .build())
                                .build())
                        .build());

        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isEmpty();
        verify(athenaClient).startQueryExecution(any(StartQueryExecutionRequest.class));
        verify(athenaClient, atLeastOnce()).getQueryExecution(any(GetQueryExecutionRequest.class));
    }

    @Test
    void lookup_QueryFailed_ReturnsEmptyList() {
        // Arrange
        when(athenaClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenReturn(StartQueryExecutionResponse.builder()
                        .queryExecutionId(TEST_QUERY_ID)
                        .build());

        when(athenaClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(GetQueryExecutionResponse.builder()
                        .queryExecution(QueryExecution.builder()
                                .status(QueryExecutionStatus.builder()
                                        .state(QueryExecutionState.FAILED)
                                        .stateChangeReason("Query failed due to syntax error")
                                        .build())
                                .build())
                        .build());

        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isEmpty();
        verify(athenaClient).startQueryExecution(any(StartQueryExecutionRequest.class));
        verify(athenaClient).getQueryExecution(any(GetQueryExecutionRequest.class));
    }

    @Test
    void lookup_EmptyQueryResults_ReturnsEmptyList() {
        // Arrange
        setupSuccessfulQueryExecution();
        
        // Return empty result set (only header row)
        when(athenaClient.getQueryResults(any(GetQueryResultsRequest.class)))
                .thenReturn(GetQueryResultsResponse.builder()
                        .resultSet(ResultSet.builder()
                                .rows(createHeaderRow())
                                .build())
                        .build());

        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isEmpty();
    }

    @Test
    void buildQuery_GeneratesCorrectSQL() {
        // Arrange
        setupSuccessfulQueryExecution();
        setupQueryResults();

        // Act
        service.lookup(TEST_MAC_ADDRESS);

        // Assert
        ArgumentCaptor<StartQueryExecutionRequest> captor = 
                ArgumentCaptor.forClass(StartQueryExecutionRequest.class);
        verify(athenaClient).startQueryExecution(captor.capture());
        
        StartQueryExecutionRequest request = captor.getValue();
        String sql = request.queryString();
        
        // Verify SQL structure
        assertThat(sql).contains("SELECT");
        assertThat(sql).contains("FROM wifi_measurements_db.wifi_measurements");
        assertThat(sql).contains("WHERE bssid = '" + TEST_MAC_ADDRESS + "'");
        assertThat(sql).contains("AND (is_global_outlier != true OR is_global_outlier IS NULL)");
        assertThat(sql).contains("AND measurement_timestamp >=");
        assertThat(sql).contains("ORDER BY measurement_timestamp DESC");
        assertThat(sql).contains("LIMIT 1000");
        
        // Verify query execution context
        assertThat(request.queryExecutionContext().database()).isEqualTo("wifi_measurements_db");
        assertThat(request.workGroup()).isNull(); // Workgroup is optional - uses default when not specified
        assertThat(request.resultConfiguration().outputLocation()).isEqualTo("s3://athena-query-results/wifi-ap-localization/");
    }

    @Test
    void lookup_WithResultCleanupEnabled_DeletesS3Files() {
        // Arrange
        setupSuccessfulQueryExecution();
        setupQueryResults();

        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isPresent();
        WifiMeasurements results = optionalResults.get();
        assertThat(results.size()).isEqualTo(2);
        
        // Verify S3 cleanup operations
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(2)).deleteObject(deleteCaptor.capture());
        
        List<DeleteObjectRequest> deleteRequests = deleteCaptor.getAllValues();
        
        // Verify deletion of result file
        assertThat(deleteRequests.get(0).bucket()).isEqualTo("athena-query-results");
        assertThat(deleteRequests.get(0).key()).contains(TEST_QUERY_ID + ".csv");
        
        // Verify deletion of metadata file
        assertThat(deleteRequests.get(1).bucket()).isEqualTo("athena-query-results");
        assertThat(deleteRequests.get(1).key()).contains(TEST_QUERY_ID + ".csv.metadata");
    }

    @Test
    void lookup_WithResultCleanupDisabled_SkipsS3Deletion() {
        // Arrange
        AthenaConfigurationProperties configWithoutCleanup = new AthenaConfigurationProperties(
                "wifi_measurements_db",
                "s3://athena-query-results/wifi-ap-localization/",
                "athena-query-results",
                "wifi-ap-localization/",
                30, 60, 1000, 30,
                false // disable cleanup
        );
        
        MeasurementRetrievalService serviceWithoutCleanup =
                new MeasurementRetrievalService(athenaClient, s3Client, configWithoutCleanup);

        // Setup minimal mocking - only what's needed for this test
        when(athenaClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenReturn(StartQueryExecutionResponse.builder()
                        .queryExecutionId(TEST_QUERY_ID)
                        .build());

        when(athenaClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(GetQueryExecutionResponse.builder()
                        .queryExecution(QueryExecution.builder()
                                .status(QueryExecutionStatus.builder()
                                        .state(QueryExecutionState.SUCCEEDED)
                                        .build())
                                .build())
                        .build());
        
        setupQueryResults();

        // Act
        Optional<WifiMeasurements> optionalResults = serviceWithoutCleanup.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isPresent();
        WifiMeasurements results = optionalResults.get();
        assertThat(results.size()).isEqualTo(2);
        
        // Verify no S3 cleanup operations were called
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void lookup_WithDefaultConfiguration_EnablesCleanupByDefault() {
        // Arrange - Test with minimal configuration (null values should default to cleanup enabled)
        AthenaConfigurationProperties defaultConfig = new AthenaConfigurationProperties(
                "wifi_measurements_db",
                "s3://athena-query-results/wifi-ap-localization/",
                "athena-query-results",
                "wifi-ap-localization/",
                30, 60, 1000, 30,
                null // This should default to true
        );
        
        MeasurementRetrievalService serviceWithDefaults =
                new MeasurementRetrievalService(athenaClient, s3Client, defaultConfig);

        setupSuccessfulQueryExecution();
        setupQueryResults();

        // Act
        Optional<WifiMeasurements> optionalResults = serviceWithDefaults.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isPresent();
        WifiMeasurements results = optionalResults.get();
        assertThat(results.size()).isEqualTo(2);
        
        // Verify cleanup is enabled by default (S3 operations should be called)
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void lookup_WhenFetchResultsFails_StillPerformsCleanup() {
        // Arrange
        when(athenaClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenReturn(StartQueryExecutionResponse.builder()
                        .queryExecutionId(TEST_QUERY_ID)
                        .build());

        when(athenaClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(GetQueryExecutionResponse.builder()
                        .queryExecution(QueryExecution.builder()
                                .status(QueryExecutionStatus.builder()
                                        .state(QueryExecutionState.SUCCEEDED)
                                        .build())
                                .resultConfiguration(ResultConfiguration.builder()
                                        .outputLocation("s3://athena-query-results/wifi-ap-localization/" + TEST_QUERY_ID + ".csv")
                                        .build())
                                .build())
                        .build());

        // Mock fetchQueryResults to throw an exception
        when(athenaClient.getQueryResults(any(GetQueryResultsRequest.class)))
                .thenThrow(new RuntimeException("Simulated query results fetch failure"));

        // Mock S3 cleanup operations
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isEmpty(); // Should return empty list on error
        
        // Verify cleanup still happens despite the fetch failure
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        
        // Verify the deletion requests for both result and metadata files
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(2)).deleteObject(deleteCaptor.capture());
        
        List<DeleteObjectRequest> deleteRequests = deleteCaptor.getAllValues();
        assertThat(deleteRequests.get(0).bucket()).isEqualTo("athena-query-results");
        assertThat(deleteRequests.get(0).key()).contains(TEST_QUERY_ID + ".csv");
        assertThat(deleteRequests.get(1).bucket()).isEqualTo("athena-query-results");
        assertThat(deleteRequests.get(1).key()).contains(TEST_QUERY_ID + ".csv.metadata");
    }

    @Test
    void lookup_WhenQueryExecutionFails_NoCleanupPerformed() {
        // Arrange - Mock query execution to fail (return null queryId)
        when(athenaClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenThrow(new RuntimeException("Simulated query execution failure"));

        // Act
        Optional<WifiMeasurements> optionalResults = service.lookup(TEST_MAC_ADDRESS);

        // Assert
        assertThat(optionalResults).isEmpty();
        
        // Verify no cleanup operations (since queryId is null)
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    private void setupSuccessfulQueryExecution() {
        when(athenaClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenReturn(StartQueryExecutionResponse.builder()
                        .queryExecutionId(TEST_QUERY_ID)
                        .build());

        when(athenaClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(GetQueryExecutionResponse.builder()
                        .queryExecution(QueryExecution.builder()
                                .status(QueryExecutionStatus.builder()
                                        .state(QueryExecutionState.SUCCEEDED)
                                        .build())
                                .resultConfiguration(ResultConfiguration.builder()
                                        .outputLocation("s3://athena-query-results/wifi-ap-localization/" + TEST_QUERY_ID + ".csv")
                                        .build())
                                .build())
                        .build());
        
        // Mock S3 cleanup operations
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
    }

    private void setupQueryResults() {
        when(athenaClient.getQueryResults(any(GetQueryResultsRequest.class)))
                .thenReturn(GetQueryResultsResponse.builder()
                        .resultSet(ResultSet.builder()
                                .rows(createTestRows())
                                .build())
                        .build());
    }

    /**
     * Creates test rows based on data from wifi-measurements-sample.json
     */
    private List<Row> createTestRows() {
        return List.of(
                createHeaderRow(),
                createConnectedMeasurementRow(),
                createScanMeasurementRow()
        );
    }

    private Row createHeaderRow() {
        return Row.builder()
                .data(List.of(
                        Datum.builder().varCharValue("id").build(),
                        Datum.builder().varCharValue("bssid").build(),
                        Datum.builder().varCharValue("measurement_timestamp").build(),
                        Datum.builder().varCharValue("latitude").build(),
                        Datum.builder().varCharValue("longitude").build(),
                        Datum.builder().varCharValue("altitude").build(),
                        Datum.builder().varCharValue("location_accuracy").build(),
                        Datum.builder().varCharValue("rssi").build(),
                        Datum.builder().varCharValue("frequency").build(),
                        Datum.builder().varCharValue("connection_status").build(),
                        Datum.builder().varCharValue("quality_weight").build(),
                        Datum.builder().varCharValue("link_speed").build(),
                        Datum.builder().varCharValue("channel_width").build(),
                        Datum.builder().varCharValue("center_freq0").build(),
                        Datum.builder().varCharValue("is_global_outlier").build()
                ))
                .build();
    }

    /**
     * Creates a CONNECTED measurement row based on first entry in wifi-measurements-sample.json
     */
    private Row createConnectedMeasurementRow() {
        return Row.builder()
                .data(List.of(
                        Datum.builder().varCharValue("4ad8b312-b5a6-4ddf-874c-02d62fce279d").build(), // id
                        Datum.builder().varCharValue("b8:f8:53:c0:1e:ff").build(), // bssid
                        Datum.builder().varCharValue("1731091615562").build(), // measurement_timestamp
                        Datum.builder().varCharValue("40.6768816").build(), // latitude
                        Datum.builder().varCharValue("-74.416391").build(), // longitude
                        Datum.builder().varCharValue("15.5").build(), // altitude
                        Datum.builder().varCharValue("10.0").build(), // location_accuracy
                        Datum.builder().varCharValue("-58").build(), // rssi
                        Datum.builder().varCharValue("5660").build(), // frequency
                        Datum.builder().varCharValue("CONNECTED").build(), // connection_status
                        Datum.builder().varCharValue("2.0").build(), // quality_weight
                        Datum.builder().varCharValue("351").build(), // link_speed
                        Datum.builder().varCharValue("80").build(), // channel_width
                        Datum.builder().varCharValue("5650").build(), // center_freq0
                        Datum.builder().varCharValue("false").build() // is_global_outlier
                ))
                .build();
    }

    /**
     * Creates a SCAN measurement row based on third entry in wifi-measurements-sample.json
     */
    private Row createScanMeasurementRow() {
        return Row.builder()
                .data(List.of(
                        Datum.builder().varCharValue("b9532b20-8612-4d69-9e7a-590a58834246").build(), // id
                        Datum.builder().varCharValue("b8:f8:53:c0:1e:ff").build(), // bssid
                        Datum.builder().varCharValue("1731091615562").build(), // measurement_timestamp
                        Datum.builder().varCharValue("40.6768816").build(), // latitude
                        Datum.builder().varCharValue("-74.416391").build(), // longitude
                        Datum.builder().varCharValue("15.5").build(), // altitude
                        Datum.builder().varCharValue("10.0").build(), // location_accuracy
                        Datum.builder().varCharValue("-61").build(), // rssi
                        Datum.builder().varCharValue(null).build(), // frequency - not available for SCAN
                        Datum.builder().varCharValue("SCAN").build(), // connection_status
                        Datum.builder().varCharValue("1.0").build(), // quality_weight
                        Datum.builder().varCharValue(null).build(), // link_speed - not available for SCAN
                        Datum.builder().varCharValue(null).build(), // channel_width - not available for SCAN
                        Datum.builder().varCharValue(null).build(), // center_freq0 - not available for SCAN
                        Datum.builder().varCharValue("false").build() // is_global_outlier
                ))
                .build();
    }

    private void verifyQueryExecution() {
        verify(athenaClient).startQueryExecution(any(StartQueryExecutionRequest.class));
        verify(athenaClient, times(2)).getQueryExecution(any(GetQueryExecutionRequest.class)); // Once for status, once for cleanup
        verify(athenaClient).getQueryResults(any(GetQueryResultsRequest.class));
    }
}
