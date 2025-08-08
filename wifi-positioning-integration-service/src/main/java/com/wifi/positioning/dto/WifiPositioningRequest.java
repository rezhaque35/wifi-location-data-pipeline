package com.wifi.positioning.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for positioning requests. This record captures all necessary data to perform
 * a positioning calculation.
 */
public record WifiPositioningRequest(
    @NotEmpty(message = "At least one WiFi scan result is required")
        @Size(min = 1, max = 20, message = "Between 1 and 20 WiFi scan results must be provided")
        @Valid
        List<WifiScanResult> wifiScanResults,
    @NotBlank(message = "Client is required")
        @Size(max = 50, message = "Client must be at most 50 characters")
        String client,
    @NotBlank(message = "Request ID is required")
        @Size(max = 64, message = "Request ID must be at most 64 characters")
        String requestId,
    @Size(max = 100, message = "Application must be at most 100 characters") String application,

    /**
     * When set to true, detailed calculation information will be included in the response. This
     * includes the selection context and algorithm selection reasoning.
     */
    Boolean calculationDetail) {
  /** Compact constructor for WifiPositioningRequest. Sets default values for optional fields. */
  public WifiPositioningRequest {
    // Set default value for calculationDetail if null
    calculationDetail = calculationDetail != null ? calculationDetail : false;
  }
}
