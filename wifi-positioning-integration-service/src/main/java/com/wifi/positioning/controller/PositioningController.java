package com.wifi.positioning.controller;

import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.service.PositioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for WiFi positioning operations.
 * 
 * This controller handles HTTP requests for position calculation based on WiFi scan results.
 * It follows a response-based error handling approach where all errors are returned as
 * structured WifiPositioningResponse objects rather than throwing exceptions.
 * 
 * HTTP Status Code Mapping:
 * - 200 OK: For all responses, with error details in response body when applicable
 * - 400 Bad Request: For validation errors and malformed requests
 * - 500 Internal Server Error: For unexpected system errors
 */
@RestController
@RequestMapping("/api/positioning")
@Validated
@Tag(name = "WiFi Positioning", description = "APIs for WiFi-based indoor positioning")
public class PositioningController {



    private final PositioningService positioningService;
    
    @Autowired
    public PositioningController(PositioningService positioningService) {
        this.positioningService = positioningService;
    }
    
    @PostMapping(value = "/calculate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Calculate position", description = "Calculate position based on WiFi scan results")
    public ResponseEntity<WifiPositioningResponse> calculatePosition(
            @Valid @RequestBody WifiPositioningRequest request) {
        try {
            WifiPositioningResponse response = positioningService.calculatePosition(request);
            
            // Determine HTTP status code based on response result
            HttpStatus httpStatus = determineHttpStatus(response);
            
            return ResponseEntity.status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
                
        } catch (Exception e) {
            // Handle any other unexpected exceptions
            WifiPositioningResponse errorResponse = WifiPositioningResponse.error(e.getMessage(), request);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
        }
    }
    
    /**
     * Determines the appropriate HTTP status code based on the response content.
     * 
     * This method maps application-level success/error states to HTTP status codes:
     * - SUCCESS responses → 200 OK
     * - ERROR responses → 200 OK (error details are in response body)
     * 
     * Rationale: Using 200 OK for all structured responses allows clients to
     * consistently parse the response body for error details. HTTP error codes
     * are reserved for protocol-level issues (validation, server errors).
     *
     * @param response The positioning response to evaluate
     * @return The appropriate HTTP status code
     */
    private HttpStatus determineHttpStatus(WifiPositioningResponse response) {
        // For now, return 200 OK for all responses since error details are in the response body
        // This approach treats positioning errors as application-level responses rather than HTTP errors
        return HttpStatus.OK;
    }
} 