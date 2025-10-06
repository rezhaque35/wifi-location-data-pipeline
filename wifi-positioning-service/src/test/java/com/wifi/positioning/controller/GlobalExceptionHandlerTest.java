package com.wifi.positioning.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiScanResult;

/**
 * Unit tests for the GlobalExceptionHandler. Verifies that exceptions are handled correctly and
 * appropriate WifiPositioningResponse objects are returned with the correct error messages and
 * status codes.
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void should_ReturnBadRequest_When_HandlingIllegalArgumentException() {
    // Arrange
    IllegalArgumentException exception = new IllegalArgumentException("Invalid argument");

    // Act
    ResponseEntity<WifiPositioningResponse> response =
        handler.handleIllegalArgumentException(exception);

    // Assert
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("ERROR", response.getBody().result());
    assertEquals("Error 400: Invalid argument", response.getBody().message());
    assertNull(response.getBody().wifiPosition());
  }

  @Test
  void should_ReturnValidationErrors_When_HandlingMethodArgumentNotValidException() {
    // Arrange
    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    WebRequest webRequest = mock(WebRequest.class);

    FieldError fieldError1 = new FieldError("object", "field1", "Field 1 error");
    FieldError fieldError2 = new FieldError("object", "field2", "Field 2 error");

    when(exception.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getAllErrors())
        .thenReturn(java.util.Arrays.asList(fieldError1, fieldError2));
    when(bindingResult.getTarget()).thenReturn(null);
    when(webRequest.getDescription(false)).thenReturn("uri=/v1/wifi/position");

    // Act
    ResponseEntity<WifiPositioningResponse> response =
        handler.handleValidationExceptions(exception, webRequest);

    // Assert
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("ERROR", response.getBody().result());
    assertTrue(response.getBody().message().contains("Validation failed"));
    assertTrue(response.getBody().message().contains("field1 - Field 1 error"));
    assertTrue(response.getBody().message().contains("field2 - Field 2 error"));
    assertNull(response.getBody().wifiPosition());
  }

  @Test
  void should_ReturnInternalServerError_When_HandlingGenericException() {
    // Arrange
    Exception exception = new RuntimeException("Something went wrong");
    WebRequest request = mock(WebRequest.class);

    // Act
    ResponseEntity<WifiPositioningResponse> response =
        handler.handleGlobalException(exception, request);

    // Assert
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("ERROR", response.getBody().result());
    assertEquals(
        "Error 500: An unexpected error occurred: Something went wrong",
        response.getBody().message());
    assertNull(response.getBody().wifiPosition());
    assertNotNull(response.getBody().requestId());
    assertEquals("system", response.getBody().client());
    assertEquals("global-exception-handler", response.getBody().application());
  }

  @Test
  void should_CreateGenericError_When_CallingErrorResponseEntityMethod() {
    // Act
    ResponseEntity<WifiPositioningResponse> response =
        GlobalExceptionHandler.errorResponseEntity("Test error", HttpStatus.BAD_GATEWAY);

    // Assert
    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    assertEquals("ERROR", response.getBody().result());
    assertEquals("Error 502: Test error", response.getBody().message());
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void should_LogValidationFailureWithRequestIdAndClient_When_ValidationFails(
      CapturedOutput output) {
    // Arrange
    WifiScanResult scanResult =
        new WifiScanResult("AA:BB:CC:DD:EE:FF", -50.0, 2400, "TestSSID", 100, 20);
    WifiPositioningRequest request =
        new WifiPositioningRequest(
            List.of(scanResult), "test-client", "test-request-id-123", "test-app", null);

    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    WebRequest webRequest = mock(WebRequest.class);

    FieldError fieldError = new FieldError("object", "wifiScanResults", "Invalid scan results");

    when(exception.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getAllErrors()).thenReturn(Collections.singletonList(fieldError));
    when(bindingResult.getTarget()).thenReturn(request);
    when(webRequest.getDescription(false)).thenReturn("uri=/v1/wifi/position");

    // Act
    ResponseEntity<WifiPositioningResponse> response =
        handler.handleValidationExceptions(exception, webRequest);

    // Assert
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertTrue(response.getBody().message().contains("Validation failed"));

    // Verify logging output contains expected information
    String logOutput = output.toString();
    assertTrue(
        logOutput.contains("test-request-id-123"), "Log should contain the requestId");
    assertTrue(logOutput.contains("test-client"), "Log should contain the client");
    assertTrue(logOutput.contains("HTTP_STATUS=400"), "Log should contain HTTP status 400");
    assertTrue(
        logOutput.contains("Validation failure"), "Log should indicate validation failure");
    assertTrue(
        logOutput.contains("wifiScanResults"), "Log should contain the field that failed");
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void should_LogUnknownValuesWhenRequestIdNotAvailable_When_ValidationFails(
      CapturedOutput output) {
    // Arrange - no target object available
    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    WebRequest webRequest = mock(WebRequest.class);

    FieldError fieldError = new FieldError("object", "field1", "Field 1 error");

    when(exception.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getAllErrors()).thenReturn(Collections.singletonList(fieldError));
    when(bindingResult.getTarget()).thenReturn(null); // No target available
    when(webRequest.getDescription(false)).thenReturn("uri=/v1/wifi/position");

    // Act
    ResponseEntity<WifiPositioningResponse> response =
        handler.handleValidationExceptions(exception, webRequest);

    // Assert
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

    // Verify logging output contains UNKNOWN for missing values
    String logOutput = output.toString();
    assertTrue(logOutput.contains("UNKNOWN"), "Log should contain UNKNOWN for missing values");
    assertTrue(logOutput.contains("HTTP_STATUS=400"), "Log should contain HTTP status 400");
    assertTrue(logOutput.contains("field1"), "Log should contain the field that failed");
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void should_LogValidationErrorDetails_When_MultipleFieldsFail(CapturedOutput output) {
    // Arrange
    WifiScanResult scanResult =
        new WifiScanResult("AA:BB:CC:DD:EE:FF", -50.0, 2400, "TestSSID", 100, 20);
    WifiPositioningRequest request =
        new WifiPositioningRequest(
            List.of(scanResult),
            "mobile-app-client",
            "req-456-789",
            "location-tracker",
            null);

    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    WebRequest webRequest = mock(WebRequest.class);

    FieldError fieldError1 = new FieldError("object", "signalStrength", "Out of range");
    FieldError fieldError2 = new FieldError("object", "macAddress", "Invalid format");

    when(exception.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getAllErrors())
        .thenReturn(java.util.Arrays.asList(fieldError1, fieldError2));
    when(bindingResult.getTarget()).thenReturn(request);
    when(webRequest.getDescription(false)).thenReturn("uri=/v1/wifi/position");

    // Act
    handler.handleValidationExceptions(exception, webRequest);

    // Assert - Verify ERROR log format
    String logOutput = output.toString();
    assertTrue(
        logOutput.contains("Validation failure"), "Log should contain validation failure message");
    assertTrue(logOutput.contains("HTTP_STATUS=400"), "Log should contain HTTP status code");
    assertTrue(logOutput.contains("REQUEST_ID='req-456-789'"), "Log should contain requestId");
    assertTrue(
        logOutput.contains("CLIENT='mobile-app-client'"), "Log should contain client");
    assertTrue(logOutput.contains("PATH='/v1/wifi/position'"), "Log should contain request path");
    assertTrue(
        logOutput.contains("signalStrength") && logOutput.contains("macAddress"),
        "Log should contain all failed fields");
  }
}
