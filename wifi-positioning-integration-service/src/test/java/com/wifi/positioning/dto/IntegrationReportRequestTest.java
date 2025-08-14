// com/wifi/positioning/dto/IntegrationReportRequestTest.java
package com.wifi.positioning.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IntegrationReportRequest DTO validation.
 */
class IntegrationReportRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequest() {
        // Given
        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setId("00:11:22:33:44:55");
        wifiInfo.setSignalStrength(-65.0);
        wifiInfo.setFrequency(2437);

        SvcReq svcReq = new SvcReq();
        svcReq.setClientId("test-client");
        svcReq.setRequestId("test-request-123");
        svcReq.setWifiInfo(List.of(wifiInfo));

        SvcBody svcBody = new SvcBody();
        svcBody.setSvcReq(svcReq);

        SampleInterfaceSourceRequest sourceRequest = new SampleInterfaceSourceRequest();
        sourceRequest.setSvcBody(svcBody);

        IntegrationReportRequest request = new IntegrationReportRequest();
        request.setSourceRequest(sourceRequest);

        // When
        Set<ConstraintViolation<IntegrationReportRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty(), "Valid request should not have validation errors");
    }

    @Test
    void requestWithNullSourceRequest() {
        // Given
        IntegrationReportRequest request = new IntegrationReportRequest();
        request.setSourceRequest(null);

        // When
        Set<ConstraintViolation<IntegrationReportRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("sourceRequest")));
    }

    @Test
    void requestWithInvalidWifiInfo() {
        // Given
        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setId(""); // Invalid: empty MAC address
        wifiInfo.setSignalStrength(-65.0);

        SvcReq svcReq = new SvcReq();
        svcReq.setClientId("test-client");
        svcReq.setRequestId("test-request-123");
        svcReq.setWifiInfo(List.of(wifiInfo));

        SvcBody svcBody = new SvcBody();
        svcBody.setSvcReq(svcReq);

        SampleInterfaceSourceRequest sourceRequest = new SampleInterfaceSourceRequest();
        sourceRequest.setSvcBody(svcBody);

        IntegrationReportRequest request = new IntegrationReportRequest();
        request.setSourceRequest(sourceRequest);

        // When
        Set<ConstraintViolation<IntegrationReportRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().contains("wifiInfo")));
    }
}
