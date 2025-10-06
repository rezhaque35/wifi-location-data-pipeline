package com.wifi.positioning.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Combined response object for WiFi positioning calculations. This record encapsulates both the API
 * response metadata and positioning data in a flattened structure for easier client consumption.
 */
public record WifiPositioningResponse(
    // API Response fields
    String result, // SUCCESS or ERROR
    String message, // Success or error message
    String requestId, // ID that matches the original request ID
    String client, // Client that made the request
    String application, // Application that made the request
    Long timestamp, // Timestamp of the response

    // Position data (may be null in error scenarios)
    WifiPosition wifiPosition,

    // Calculation information (optional)
    CalculationInfo calculationInfo) {
  /** Creates a success response with position data. */
  public static WifiPositioningResponse success(
      WifiPositioningRequest request, WifiPosition wifiPosition, CalculationInfo calculationInfo) {
    return new WifiPositioningResponse(
        "SUCCESS",
        "Request processed successfully",
        request.requestId(),
        request.client(),
        request.application(),
        Instant.now().toEpochMilli(),
        wifiPosition,
        calculationInfo);
  }

  /** Creates an error response with a specific error message and optional calculation info. */
  public static WifiPositioningResponse error(
      String message, WifiPositioningRequest request, CalculationInfo calculationInfo) {
    return new WifiPositioningResponse(
        "ERROR",
        message,
        request.requestId(),
        request.client(),
        request.application(),
        Instant.now().toEpochMilli(),
        null,
        calculationInfo);
  }

  /** Creates an error response with a specific error message (without calculation info). */
  public static WifiPositioningResponse error(String message, WifiPositioningRequest request) {
    return error(message, request, null);
  }

  /**
   * Creates a generic error response for global exception handling. This method is used when a
   * WifiPositioningRequest is not available.
   *
   * @param errorMessage The error message to display
   * @param statusCode Optional HTTP status code to include in the message
   * @return A WifiPositioningResponse with error details
   */
  public static WifiPositioningResponse genericError(String errorMessage, Integer statusCode) {
    String formattedMessage =
        statusCode != null ? "Error " + statusCode + ": " + errorMessage : errorMessage;

    return new WifiPositioningResponse(
        "ERROR",
        formattedMessage,
        "error-" + Instant.now().toEpochMilli(),
        "system",
        "global-exception-handler",
        Instant.now().toEpochMilli(),
        null,
        null);
  }

  /** Position data specific to WiFi positioning. */
  public record WifiPosition(
      Double latitude,
      Double longitude,
      Double altitude,
      Double horizontalAccuracy,
      Double verticalAccuracy,
      Double confidence,
      List<String> methodsUsed,
      Integer apCount,
      Long calculationTimeMs) {
    /** Convenience constructor that sets default values for optional fields. */
    public WifiPosition {
      if (methodsUsed == null) {
        methodsUsed = Collections.emptyList();
      }
    }

    /**
     * Converts WifiPosition to a Map for structured logging.
     * 
     * @return Map representation of the WifiPosition
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("latitude", latitude);
      map.put("longitude", longitude);
      map.put("altitude", altitude);
      map.put("horizontalAccuracy", horizontalAccuracy);
      map.put("verticalAccuracy", verticalAccuracy);
      map.put("confidence", confidence);
      map.put("methodsUsed", methodsUsed != null ? methodsUsed : Collections.emptyList());
      map.put("apCount", apCount);
      map.put("calculationTimeMs", calculationTimeMs);
      return map;
    }
  }

  /**
   * Converts WifiPositioningResponse to a Map for structured logging.
   * Includes all response fields and nested objects.
   * 
   * @return Map representation of the WifiPositioningResponse
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("result", result);
    map.put("message", message);
    map.put("requestId", requestId);
    map.put("client", client);
    map.put("application", application);
    map.put("timestamp", timestamp);
    map.put("wifiPosition", wifiPosition != null ? wifiPosition.toMap() : Collections.emptyMap());
    map.put("calculationInfo", calculationInfo != null ? calculationInfo.toMap() : Collections.emptyMap());
    return map;
  }
}
