// com/wifi/positioning/controller/IntegrationReportControllerAsyncTest.java
package com.wifi.positioning.controller;

import com.wifi.positioning.config.AsyncConfig;
import com.wifi.positioning.dto.*;
import com.wifi.positioning.exception.AsyncProcessingUnavailableException;
import com.wifi.positioning.service.AsyncIntegrationService;
import com.wifi.positioning.service.IntegrationProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IntegrationReportController async processing functionality.
 */
@ExtendWith(MockitoExtension.class)
class IntegrationReportControllerAsyncTest {

    @Mock
    private IntegrationProcessingService integrationProcessingService;

    @Mock
    private AsyncIntegrationService asyncIntegrationService;

    private IntegrationReportController controller;

    @BeforeEach
    void setUp() {
        controller = new IntegrationReportController(
            integrationProcessingService, asyncIntegrationService);
    }

    @Test
    void shouldReturnAcceptedForAsyncRequest() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequest();
        when(asyncIntegrationService.isAsyncProcessingAvailable()).thenReturn(true);
        when(asyncIntegrationService.processIntegrationReportAsync(any(), any(), any(), eq(request)))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        ResponseEntity<IntegrationReportResponse> response = controller.submitIntegrationReport(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProcessingMode()).isEqualTo("async");
        assertThat(response.getBody().getCorrelationId()).isNotNull();
        assertThat(response.getBody().getReceivedAt()).isNotNull();
    }

    @Test
    void shouldUseProvidedCorrelationIdForAsyncRequest() {
        // Given
        String providedCorrelationId = "test-correlation-123";
        IntegrationReportRequest request = createAsyncIntegrationRequestWithCorrelationId(providedCorrelationId);
        
        when(asyncIntegrationService.isAsyncProcessingAvailable()).thenReturn(true);
        when(asyncIntegrationService.processIntegrationReportAsync(
            eq(providedCorrelationId), any(), any(), eq(request)))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        ResponseEntity<IntegrationReportResponse> response = controller.submitIntegrationReport(request);

        // Then
        assertThat(response.getBody().getCorrelationId()).isEqualTo(providedCorrelationId);
        verify(asyncIntegrationService).processIntegrationReportAsync(
            eq(providedCorrelationId), any(), any(), eq(request));
    }

    @Test
    void shouldFallbackToSyncWhenAsyncIsDisabled() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequest();
        when(asyncIntegrationService.isAsyncProcessingAvailable()).thenReturn(false);
        
        // Mock sync processing result
        WifiPositioningRequest positioningRequest = createMockPositioningRequest();
        ClientResult positioningResult = createSuccessfulClientResult();
        ComparisonMetrics comparisonMetrics = createMockComparisonMetrics();
        
        IntegrationProcessingService.ProcessingResult syncResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .positioningRequest(positioningRequest)
            .positioningResult(positioningResult)
            .comparison(comparisonMetrics)
            .totalProcessingTimeMs(100L)
            .transformationTimeMs(10L)
            .build();

        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(syncResult);

        // When
        ResponseEntity<IntegrationReportResponse> response = controller.submitIntegrationReport(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getProcessingMode()).isEqualTo("sync");
        verify(asyncIntegrationService, never()).processIntegrationReportAsync(any(), any(), any(), any());
    }

    @Test
    void shouldThrowAsyncProcessingUnavailableWhenQueueIsFull() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequest();
        when(asyncIntegrationService.isAsyncProcessingAvailable()).thenReturn(true);
        when(asyncIntegrationService.processIntegrationReportAsync(any(), any(), any(), eq(request)))
            .thenThrow(new AsyncConfig.AsyncQueueFullException("Queue is full"));

        // When & Then
        assertThatThrownBy(() -> controller.submitIntegrationReport(request))
            .isInstanceOf(AsyncProcessingUnavailableException.class)
            .hasMessageContaining("Async processing queue is full");
    }

    @Test
    void shouldFallbackToSyncOnAsyncConfigurationError() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequest();
        when(asyncIntegrationService.isAsyncProcessingAvailable()).thenReturn(true);
        when(asyncIntegrationService.processIntegrationReportAsync(any(), any(), any(), eq(request)))
            .thenThrow(new IllegalStateException("Async not configured"));

        // Mock sync processing result
        WifiPositioningRequest positioningRequest = createMockPositioningRequest();
        ClientResult positioningResult = createSuccessfulClientResult();
        ComparisonMetrics comparisonMetrics = createMockComparisonMetrics();
        
        IntegrationProcessingService.ProcessingResult syncResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .positioningRequest(positioningRequest)
            .positioningResult(positioningResult)
            .comparison(comparisonMetrics)
            .totalProcessingTimeMs(100L)
            .transformationTimeMs(10L)
            .build();

        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(syncResult);

        // When
        ResponseEntity<IntegrationReportResponse> response = controller.submitIntegrationReport(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getProcessingMode()).isEqualTo("sync");
    }

    @Test
    void shouldFallbackToSyncOnUnexpectedAsyncError() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequest();
        when(asyncIntegrationService.isAsyncProcessingAvailable()).thenReturn(true);
        when(asyncIntegrationService.processIntegrationReportAsync(any(), any(), any(), eq(request)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Mock sync processing result
        WifiPositioningRequest positioningRequest = createMockPositioningRequest();
        ClientResult positioningResult = createSuccessfulClientResult();
        ComparisonMetrics comparisonMetrics = createMockComparisonMetrics();
        
        IntegrationProcessingService.ProcessingResult syncResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .positioningRequest(positioningRequest)
            .positioningResult(positioningResult)
            .comparison(comparisonMetrics)
            .totalProcessingTimeMs(100L)
            .transformationTimeMs(10L)
            .build();

        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(syncResult);

        // When
        ResponseEntity<IntegrationReportResponse> response = controller.submitIntegrationReport(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getProcessingMode()).isEqualTo("sync");
    }

    @Test
    void shouldDefaultToSyncProcessingWhenModeNotSpecified() {
        // Given
        IntegrationReportRequest request = createSyncIntegrationRequest();
        
        // Mock sync processing result
        WifiPositioningRequest positioningRequest = createMockPositioningRequest();
        ClientResult positioningResult = createSuccessfulClientResult();
        ComparisonMetrics comparisonMetrics = createMockComparisonMetrics();
        
        IntegrationProcessingService.ProcessingResult syncResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .positioningRequest(positioningRequest)
            .positioningResult(positioningResult)
            .comparison(comparisonMetrics)
            .totalProcessingTimeMs(100L)
            .transformationTimeMs(10L)
            .build();

        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(syncResult);

        // When
        ResponseEntity<IntegrationReportResponse> response = controller.submitIntegrationReport(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getProcessingMode()).isEqualTo("sync");
        verifyNoInteractions(asyncIntegrationService);
    }

    @Test
    void shouldCallAsyncServiceWithCorrectParameters() {
        // Given
        String requestId = "test-request-789";
        IntegrationReportRequest request = createAsyncIntegrationRequestWithRequestId(requestId);
        
        when(asyncIntegrationService.isAsyncProcessingAvailable()).thenReturn(true);
        when(asyncIntegrationService.processIntegrationReportAsync(any(), eq(requestId), any(), eq(request)))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        controller.submitIntegrationReport(request);

        // Then
        verify(asyncIntegrationService).processIntegrationReportAsync(
            any(), eq(requestId), any(), eq(request));
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() {
        // Given
        IntegrationReportRequest request = createAsyncIntegrationRequest();
        when(asyncIntegrationService.isAsyncProcessingAvailable()).thenReturn(true);
        when(asyncIntegrationService.processIntegrationReportAsync(any(), any(), any(), eq(request)))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        ResponseEntity<IntegrationReportResponse> response = controller.submitIntegrationReport(request);

        // Then
        assertThat(response.getBody().getCorrelationId()).isNotNull();
        assertThat(response.getBody().getCorrelationId()).isNotEmpty();
    }

    private IntegrationReportRequest createAsyncIntegrationRequest() {
        return createIntegrationRequest("async", null, "test-request-123");
    }

    private IntegrationReportRequest createAsyncIntegrationRequestWithCorrelationId(String correlationId) {
        return createIntegrationRequest("async", correlationId, "test-request-123");
    }

    private IntegrationReportRequest createAsyncIntegrationRequestWithRequestId(String requestId) {
        return createIntegrationRequest("async", null, requestId);
    }

    private IntegrationReportRequest createSyncIntegrationRequest() {
        return createIntegrationRequest("sync", null, "test-request-123");
    }

    private IntegrationReportRequest createIntegrationRequest(String processingMode, String correlationId, String requestId) {
        IntegrationReportRequest request = new IntegrationReportRequest();
        
        // Create options
        IntegrationOptions options = new IntegrationOptions();
        options.setProcessingMode(processingMode);
        options.setCalculationDetail(true);
        request.setOptions(options);

        // Create metadata if correlationId is provided
        if (correlationId != null) {
            IntegrationMetadata metadata = new IntegrationMetadata();
            metadata.setCorrelationId(correlationId);
            request.setMetadata(metadata);
        }

        // Create source request
        SampleInterfaceSourceRequest sourceRequest = new SampleInterfaceSourceRequest();
        SvcBody svcBody = new SvcBody();
        SvcReq svcReq = new SvcReq();
        svcReq.setRequestId(requestId);
        svcReq.setClientId("test-client");
        svcReq.setWifiInfo(List.of());
        svcReq.setCellInfo(List.of());
        
        svcBody.setSvcReq(svcReq);
        sourceRequest.setSvcBody(svcBody);
        request.setSourceRequest(sourceRequest);

        return request;
    }

    private WifiPositioningRequest createMockPositioningRequest() {
        WifiPositioningRequest request = new WifiPositioningRequest();
        request.setRequestId("test-request-123");
        request.setClient("test-client");
        request.setWifiScanResults(List.of());
        return request;
    }

    private ClientResult createSuccessfulClientResult() {
        ClientResult result = new ClientResult();
        result.setSuccess(true);
        result.setHttpStatus(200);
        result.setLatencyMs(100L);
        result.setResponseBody("{}");
        return result;
    }

    private ComparisonMetrics createMockComparisonMetrics() {
        ComparisonMetrics metrics = new ComparisonMetrics();
        metrics.setScenario(ComparisonScenario.BOTH_WIFI_SUCCESS);
        metrics.setPositioningMethod(PositioningMethod.WIFI_ONLY);
        return metrics;
    }
}
