// com/wifi/positioning/client/PositioningServiceClient.java
package com.wifi.positioning.client;

import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.ClientResult;
import com.wifi.positioning.dto.WifiPositioningRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Client for calling the WiFi positioning service.
 * Handles HTTP communication, timeouts, and error mapping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositioningServiceClient {

    private final WebClient webClient;
    private final IntegrationProperties properties;

    /**
     * Invokes the positioning service with a WiFi positioning request.
     * Measures latency and handles errors gracefully.
     * 
     * @param request The WiFi positioning request
     * @return ClientResult containing status, response, and latency
     */
    public ClientResult invoke(WifiPositioningRequest request) {
        log.debug("Invoking positioning service for request ID: {}", request.getRequestId());
        
        long startTime = System.nanoTime();
        
        try {
            String url = buildUrl();
            
            // Make the HTTP call with configured timeouts
            Object response = webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(Duration.ofMillis(properties.getPositioning().getReadTimeoutMs()))
                .block();
            
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            
            log.debug("Successfully received response from positioning service in {}ms", latencyMs);
            return ClientResult.success(200, response, latencyMs);
            
        } catch (WebClientResponseException e) {
            // HTTP error response (4xx, 5xx)
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            
            log.warn("Positioning service returned HTTP error {}: {}", 
                e.getStatusCode().value(), e.getMessage());
            
            String errorMessage = String.format("HTTP %d: %s", 
                e.getStatusCode().value(), e.getStatusText());
            
            // Try to extract response body for error details
            Object responseBody = null;
            try {
                String responseBodyString = e.getResponseBodyAsString();
                if (responseBodyString != null && !responseBodyString.isEmpty()) {
                    responseBody = responseBodyString;
                }
            } catch (Exception ex) {
                log.debug("Could not extract response body from error: {}", ex.getMessage());
            }
            
            return ClientResult.error(e.getStatusCode().value(), responseBody, errorMessage, latencyMs);
            
        } catch (WebClientRequestException e) {
            // Connection/timeout error
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            
            log.error("Failed to connect to positioning service: {}", e.getMessage());
            
            String errorMessage = "Connection failed: " + e.getMessage();
            return ClientResult.error(null, errorMessage, latencyMs);
            
        } catch (Exception e) {
            // Other errors (timeout, serialization, etc.)
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            
            log.error("Unexpected error calling positioning service: {}", e.getMessage(), e);
            
            String errorMessage = "Unexpected error: " + e.getMessage();
            return ClientResult.error(null, errorMessage, latencyMs);
        }
    }

    /**
     * Builds the full URL for the positioning service endpoint.
     * 
     * @return Complete URL for positioning service
     */
    private String buildUrl() {
        String baseUrl = properties.getPositioning().getBaseUrl();
        String path = properties.getPositioning().getPath();
        
        // Ensure baseUrl doesn't end with slash and path starts with slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        return baseUrl + path;
    }
}
