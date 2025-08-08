package com.wifi.positioning.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.wifi.positioning.dto.WifiPositioningResponse;

/**
 * Global exception handler for the WiFi positioning service. Handles all exceptions and provides
 * standardized error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
      MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });

    // Create a validation error message combining all field errors
    StringBuilder errorMsg = new StringBuilder("Validation failed: ");
    errors.forEach((field, msg) -> errorMsg.append(field).append(" - ").append(msg).append("; "));

    return errorResponseEntity(errorMsg.toString(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<WifiPositioningResponse> handleGlobalException(
      Exception ex, WebRequest request) {
    return errorResponseEntity(
        "An unexpected error occurred: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
