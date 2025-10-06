package com.wifi.positioning.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.dto.WifiPositioningResponse;

/**
 * Global exception handler for the WiFi positioning service. Handles all exceptions and provides
 * standardized error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String UNKNOWN_VALUE = "UNKNOWN";

  public static ResponseEntity<WifiPositioningResponse> errorResponseEntity(
      String message, HttpStatus status) {
    WifiPositioningResponse response =
        WifiPositioningResponse.genericError(message, status.value());
    return new ResponseEntity<>(response, status);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<WifiPositioningResponse> handleIllegalArgumentException(
      IllegalArgumentException ex) {
    return errorResponseEntity(ex.getMessage(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<WifiPositioningResponse> handleValidationExceptions(
      MethodArgumentNotValidException ex, WebRequest webRequest) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });

    // Extract requestId and client from the request object if available
    String requestId = extractRequestId(ex);
    String client = extractClient(ex);

    // Create a validation error message combining all field errors
    StringBuilder errorMsg = new StringBuilder("Validation failed: ");
    errors.forEach((field, msg) -> errorMsg.append(field).append(" - ").append(msg).append("; "));

    // Comprehensive logging for Splunk
    logValidationFailure(requestId, client, errors, webRequest);

    return errorResponseEntity(errorMsg.toString(), HttpStatus.BAD_REQUEST);
  }

  /**
   * Extracts requestId from the request object during validation failure. Attempts to retrieve from
   * the target object being validated.
   *
   * @param ex The validation exception
   * @return The requestId if available, or "UNKNOWN" if not extractable
   */
  private String extractRequestId(MethodArgumentNotValidException ex) {
    try {
      Object target = ex.getBindingResult().getTarget();
      if (target instanceof WifiPositioningRequest request) {
        return request.requestId() != null ? request.requestId() : UNKNOWN_VALUE;
      }
    } catch (Exception e) {
      logger.debug("Unable to extract requestId from validation exception", e);
    }
    return UNKNOWN_VALUE;
  }

  /**
   * Extracts client from the request object during validation failure.
   *
   * @param ex The validation exception
   * @return The client if available, or "UNKNOWN" if not extractable
   */
  private String extractClient(MethodArgumentNotValidException ex) {
    try {
      Object target = ex.getBindingResult().getTarget();
      if (target instanceof WifiPositioningRequest request) {
        return request.client() != null ? request.client() : UNKNOWN_VALUE;
      }
    } catch (Exception e) {
      logger.debug("Unable to extract client from validation exception", e);
    }
    return UNKNOWN_VALUE;
  }

  /**
   * Logs comprehensive validation failure details for monitoring and troubleshooting. Includes
   * structured information suitable for Splunk indexing and searching.
   *
   * @param requestId The request identifier
   * @param client The client identifier
   * @param errors Map of field names to validation error messages
   * @param webRequest The web request context
   */
  private void logValidationFailure(
      String requestId, String client, Map<String, String> errors, WebRequest webRequest) {
    String validationErrors =
        errors.entrySet().stream()
            .map(entry -> String.format("%s='%s'", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(", "));

    String requestPath =
        webRequest.getDescription(false).replace("uri=", ""); // Remove "uri=" prefix

    logger.error(
        "Validation failure - HTTP_STATUS=400 REQUEST_ID='{}' CLIENT='{}' PATH='{}' VALIDATION_ERRORS=[{}]",
        requestId,
        client,
        requestPath,
        validationErrors);

  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<WifiPositioningResponse> handleGlobalException(
      Exception ex, WebRequest request) {
    return errorResponseEntity(
        "An unexpected error occurred: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
