// com/wifi/positioning/service/VlssErrorHandlingTest.java
package com.wifi.positioning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for VLSS error handling in ComparisonService.
 */
@ExtendWith(MockitoExtension.class)
class VlssErrorHandlingTest {

    @Mock
    private AccessPointEnrichmentService enrichmentService;

    private ComparisonService comparisonService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        comparisonService = new ComparisonService(objectMapper, enrichmentService);
    }

    @Test
    void shouldExtractVlssErrorDetailsFromStructuredResponse() {
        // Given: VLSS response with structured error
        SourceResponse sourceResponse = createVlssErrorResponse();
        Object friscoResponse = createSuccessfulFriscoResponse();

        // When: Comparing results
        ComparisonMetrics metrics = comparisonService.compareResults(sourceResponse, friscoResponse);

        // Then: VLSS error details should be extracted
        assertThat(metrics.getVlssSuccess()).isFalse();
        assertThat(metrics.getVlssErrorCode()).isEqualTo(1401);
        assertThat(metrics.getVlssErrorDetails()).contains("Code 1401: WHY (What to do.)");
        assertThat(metrics.getVlssErrors()).hasSize(1);
        assertThat(metrics.getVlssErrors().get(0).getCode()).isEqualTo(1401);
        assertThat(metrics.getVlssErrors().get(0).getMessage()).isEqualTo("WHY");
        assertThat(metrics.getVlssErrors().get(0).getDescription()).isEqualTo("What to do.");
        assertThat(metrics.getFailureAnalysis()).contains("VLSS authentication or authorization failed");
    }

    @Test
    void shouldHandleMultipleVlssErrors() {
        // Given: VLSS response with multiple errors
        SourceResponse sourceResponse = createVlssMultipleErrorResponse();
        Object friscoResponse = createSuccessfulFriscoResponse();

        // When: Comparing results
        ComparisonMetrics metrics = comparisonService.compareResults(sourceResponse, friscoResponse);

        // Then: All errors should be captured
        assertThat(metrics.getVlssSuccess()).isFalse();
        assertThat(metrics.getVlssErrorCode()).isEqualTo(1404); // Primary error code
        assertThat(metrics.getVlssErrors()).hasSize(2);
        assertThat(metrics.getVlssErrorDetails()).contains("Code 1404")
                                                  .contains("Code 1500");
    }

    @Test
    void shouldHandleVlssErrorWithoutStructuredData() {
        // Given: VLSS response with basic error info only
        SourceResponse sourceResponse = new SourceResponse();
        sourceResponse.setSuccess(false);
        sourceResponse.setErrorMessage("Basic error message");
        sourceResponse.setErrorCode("BASIC_ERROR");

        Object friscoResponse = createSuccessfulFriscoResponse();

        // When: Comparing results
        ComparisonMetrics metrics = comparisonService.compareResults(sourceResponse, friscoResponse);

        // Then: Basic error info should be captured
        assertThat(metrics.getVlssSuccess()).isFalse();
        assertThat(metrics.getVlssErrorDetails()).isEqualTo("Basic error message");
        assertThat(metrics.getVlssErrors()).isNull();
        assertThat(metrics.getVlssErrorCode()).isNull();
    }

    @Test
    void shouldSetCorrectScenarioForVlssFailure() {
        // Given: VLSS fails, Frisco succeeds
        SourceResponse sourceResponse = createVlssErrorResponse();
        Object friscoResponse = createSuccessfulFriscoResponse();

        // When: Comparing results
        ComparisonMetrics metrics = comparisonService.compareResults(sourceResponse, friscoResponse);

        // Then: Scenario should be set correctly
        assertThat(metrics.getScenario()).isEqualTo(ComparisonScenario.VLSS_ERROR_FRISCO_SUCCESS);
        assertThat(metrics.getVlssSuccess()).isFalse();
        assertThat(metrics.getFriscoSuccess()).isTrue();
    }

    private SourceResponse createVlssErrorResponse() {
        SourceResponse response = new SourceResponse();
        response.setSuccess(false);
        response.setRequestId("test-123");
        
        // Create structured error
        VlssError error = new VlssError();
        error.setCode(1401);
        error.setMessage("WHY");
        error.setDescription("What to do.");
        
        VlssServiceError svcError = new VlssServiceError();
        svcError.setErrors(Arrays.asList(error));
        
        response.setSvcError(svcError);
        return response;
    }

    private SourceResponse createVlssMultipleErrorResponse() {
        SourceResponse response = new SourceResponse();
        response.setSuccess(false);
        response.setRequestId("test-456");
        
        // Create multiple structured errors
        VlssError error1 = new VlssError();
        error1.setCode(1404);
        error1.setMessage("Not Found");
        error1.setDescription("Location data not found");
        
        VlssError error2 = new VlssError();
        error2.setCode(1500);
        error2.setMessage("Internal Error");
        error2.setDescription("Server processing error");
        
        VlssServiceError svcError = new VlssServiceError();
        svcError.setErrors(Arrays.asList(error1, error2));
        
        response.setSvcError(svcError);
        return response;
    }

    private Object createSuccessfulFriscoResponse() {
        return Map.of(
            "result", "SUCCESS",
            "wifiPosition", Map.of(
                "latitude", 34.0522,
                "longitude", -118.2437,
                "horizontalAccuracy", 50.0,
                "confidence", 0.85,
                "apCount", 8,
                "calculationTimeMs", 150,
                "methodsUsed", List.of("wifi_positioning")
            )
        );
    }
}
