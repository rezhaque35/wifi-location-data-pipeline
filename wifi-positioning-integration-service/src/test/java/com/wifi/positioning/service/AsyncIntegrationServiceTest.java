// com/wifi/positioning/service/AsyncIntegrationServiceTest.java
package com.wifi.positioning.service;

import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.*;
import com.wifi.positioning.health.AsyncProcessingHealthIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AsyncIntegrationService.
 */
@ExtendWith(MockitoExtension.class)
class AsyncIntegrationServiceTest {

    @Mock
    private IntegrationProcessingService integrationProcessingService;

    @Mock
    private IntegrationProperties integrationProperties;

    @Mock
    private IntegrationProperties.Processing processing;

    @Mock
    private IntegrationProperties.Processing.Async asyncConfig;

    @Mock
    private IntegrationReportRequest request;

    @Mock
    private AsyncProcessingHealthIndicator healthIndicator;

    private AsyncIntegrationService asyncIntegrationService;

    private String correlationId;
    private String requestId;
    private Instant receivedAt;

    @BeforeEach
    void setUp() {
        asyncIntegrationService = new AsyncIntegrationService(
            integrationProcessingService, integrationProperties, healthIndicator);

        correlationId = "test-correlation-123";
        requestId = "test-request-456";
        receivedAt = Instant.now();

        when(integrationProperties.getProcessing()).thenReturn(processing);
        when(processing.getAsync()).thenReturn(asyncConfig);
    }

    @Test
    void shouldReturnTrueWhenAsyncProcessingIsEnabled() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);

        // When
        boolean isAvailable = asyncIntegrationService.isAsyncProcessingAvailable();

        // Then
        assertThat(isAvailable).isTrue();
    }

    @Test
    void shouldReturnFalseWhenAsyncProcessingIsDisabled() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(false);

        // When
        boolean isAvailable = asyncIntegrationService.isAsyncProcessingAvailable();

        // Then
        assertThat(isAvailable).isFalse();
    }

    @Test
    void shouldReturnAsyncConfig() {
        // When
        IntegrationProperties.Processing.Async config = asyncIntegrationService.getAsyncConfig();

        // Then
        assertThat(config).isEqualTo(asyncConfig);
    }

    @Test
    void shouldThrowExceptionWhenAsyncProcessingIsDisabled() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> 
            asyncIntegrationService.processIntegrationReportAsync(
                correlationId, requestId, receivedAt, request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Async processing is not enabled");
    }

    @Test
    void shouldProcessIntegrationReportAsyncWhenEnabled() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        
        IntegrationProcessingService.ProcessingResult successResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .totalProcessingTimeMs(100L)
            .transformationTimeMs(10L)
            .build();
        
        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(successResult);

        // When
        CompletableFuture<Void> future = asyncIntegrationService.processIntegrationReportAsync(
            correlationId, requestId, receivedAt, request);

        // Then
        assertThat(future)
            .isNotNull()
            .isCompleted(); // Should complete immediately in test
        
        // Verify health indicator was updated for successful processing
        verify(healthIndicator).incrementSuccessfulProcessing();
    }

    @Test
    void shouldHandleExceptionInAsyncProcessing() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        when(integrationProcessingService.processIntegrationReport(any()))
            .thenThrow(new RuntimeException("Test exception"));

        // When
        CompletableFuture<Void> future = asyncIntegrationService.processIntegrationReportAsync(
            correlationId, requestId, receivedAt, request);

        // Then
        assertThat(future)
            .isNotNull()
            .isCompleted(); // Should complete even with exception
        
        // Verify health indicator was updated for failed processing
        verify(healthIndicator).incrementFailedProcessing();
    }

    @Test
    void shouldCreateAsyncConfigurationObject() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        when(asyncConfig.getQueueCapacity()).thenReturn(1000);
        when(asyncConfig.getWorkers()).thenReturn(4);

        // When
        IntegrationProperties.Processing.Async config = asyncIntegrationService.getAsyncConfig();

        // Then
        assertThat(config)
            .satisfies(c -> {
                assertThat(c.isEnabled()).isTrue();
                assertThat(c.getQueueCapacity()).isEqualTo(1000);
                assertThat(c.getWorkers()).isEqualTo(4);
            });
    }

    @Test
    void shouldProcessRequestWithComplexData() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        
        IntegrationProcessingService.ProcessingResult successResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .totalProcessingTimeMs(150L)
            .transformationTimeMs(15L)
            .build();
        
        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(successResult);

        // When
        CompletableFuture<Void> future = asyncIntegrationService.processIntegrationReportAsync(
            correlationId, requestId, receivedAt, request);

        // Then
        assertThat(future)
            .isNotNull()
            .isCompleted();
    }

    @Test
    void shouldHandleNullCorrelationId() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        
        IntegrationProcessingService.ProcessingResult successResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .totalProcessingTimeMs(100L)
            .transformationTimeMs(10L)
            .build();
        
        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(successResult);

        // When
        CompletableFuture<Void> future = asyncIntegrationService.processIntegrationReportAsync(
            null, requestId, receivedAt, request);

        // Then
        assertThat(future)
            .isNotNull()
            .isCompleted();
    }

    @Test
    void shouldHandleNullRequestId() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        
        IntegrationProcessingService.ProcessingResult successResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .totalProcessingTimeMs(100L)
            .transformationTimeMs(10L)
            .build();
        
        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(successResult);

        // When
        CompletableFuture<Void> future = asyncIntegrationService.processIntegrationReportAsync(
            correlationId, null, receivedAt, request);

        // Then
        assertThat(future)
            .isNotNull()
            .isCompleted();
    }

    @Test
    void shouldHandleNullReceivedAt() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        
        IntegrationProcessingService.ProcessingResult successResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .totalProcessingTimeMs(100L)
            .transformationTimeMs(10L)
            .build();
        
        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(successResult);

        // When
        CompletableFuture<Void> future = asyncIntegrationService.processIntegrationReportAsync(
            correlationId, requestId, null, request);

        // Then
        assertThat(future)
            .isNotNull()
            .isCompleted();
    }

    @Test
    void shouldIncrementFailedProcessingWhenResultIsNotSuccess() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        
        IntegrationProcessingService.ProcessingResult failedResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(false)
            .errorType("VALIDATION_ERROR")
            .errorMessage("Test validation error")
            .totalProcessingTimeMs(50L)
            .transformationTimeMs(5L)
            .build();
        
        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(failedResult);

        // When
        CompletableFuture<Void> future = asyncIntegrationService.processIntegrationReportAsync(
            correlationId, requestId, receivedAt, request);

        // Then
        assertThat(future)
            .isNotNull()
            .isCompleted();
        
        // Verify health indicator was updated for failed processing
        verify(healthIndicator).incrementFailedProcessing();
    }

    @Test
    void shouldIncrementSuccessfulProcessingWhenResultIsSuccess() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        
        IntegrationProcessingService.ProcessingResult successResult = IntegrationProcessingService.ProcessingResult.builder()
            .success(true)
            .totalProcessingTimeMs(120L)
            .transformationTimeMs(12L)
            .build();
        
        when(integrationProcessingService.processIntegrationReport(any())).thenReturn(successResult);

        // When
        CompletableFuture<Void> future = asyncIntegrationService.processIntegrationReportAsync(
            correlationId, requestId, receivedAt, request);

        // Then
        assertThat(future)
            .isNotNull()
            .isCompleted();
        
        // Verify health indicator was updated for successful processing
        verify(healthIndicator).incrementSuccessfulProcessing();
    }


}
