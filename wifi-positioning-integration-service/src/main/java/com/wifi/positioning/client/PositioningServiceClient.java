// com/wifi/positioning/client/PositioningServiceClient.java
package com.wifi.positioning.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.config.IntegrationProperties;
import com.wifi.positioning.dto.ClientResult;
import com.wifi.positioning.dto.WifiPositioningRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String PATH_SEPARATOR = "/";
    
    private final WebClient webClient;
    private final IntegrationProperties properties;
    private final ObjectMapper objectMapper;
    
    @Value("${integration.positioning.health-path:/health}")
    private String healthPath;

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
     * Checks the health of the positioning service.
     * Used by health indicators to monitor service availability.
     * 
     * @return HealthResult containing health status and details
     */
    public HealthResult checkHealth() {
        log.debug("Checking positioning service health");
        
        long startTime = System.nanoTime();
        
        try {
            String baseUrl = properties.getPositioning().getBaseUrl();
            // Ensure proper URL construction
            if (baseUrl.endsWith(PATH_SEPARATOR)) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            // Handle null healthPath (for tests) with fallback
            String healthPathToUse = (healthPath != null) ? healthPath : "/health";
            String healthEndpoint = healthPathToUse.startsWith(PATH_SEPARATOR) ? healthPathToUse : PATH_SEPARATOR + healthPathToUse;
            String fullHealthUrl = baseUrl + healthEndpoint;
            
            // Make the health check call with shorter timeout
            String responseString = webClient
                .get()
                .uri(fullHealthUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(Math.min(3000, properties.getPositioning().getReadTimeoutMs())))
                .block();
            
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            
            log.debug("Positioning service health check received response in {}ms", latencyMs);
            
            // Parse and evaluate the health response
            HealthEvaluation evaluation = evaluateHealthResponse(responseString);
            
            if (evaluation.isHealthy()) {
                log.debug("Positioning service reports healthy status: {}", evaluation.getStatus());
                return HealthResult.healthy(evaluation.getParsedResponse(), latencyMs, evaluation.getStatus());
            } else {
                log.warn("Positioning service reports unhealthy status: {}", evaluation.getStatus());
                return HealthResult.unhealthy(
                    "Service status: " + evaluation.getStatus(), 
                    200, // HTTP was successful but service is unhealthy
                    latencyMs, 
                    evaluation.getStatus(),
                    evaluation.getParsedResponse()
                );
            }
            
        } catch (WebClientResponseException e) {
            // HTTP error response (4xx, 5xx)
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            
            log.warn("Positioning service health endpoint returned HTTP error {}: {}", 
                e.getStatusCode().value(), e.getMessage());
            
            String errorMessage = String.format("HTTP %d: %s", 
                e.getStatusCode().value(), e.getStatusText());
            
            return HealthResult.unhealthy(errorMessage, e.getStatusCode().value(), latencyMs);
            
        } catch (WebClientRequestException e) {
            // Connection/timeout error
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            
            log.error("Failed to connect to positioning service health endpoint: {}", e.getMessage());
            
            String errorMessage = "Connection failed: " + e.getMessage();
            return HealthResult.unhealthy(errorMessage, null, latencyMs);
            
        } catch (Exception e) {
            // Other errors (timeout, serialization, etc.)
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            
            log.error("Unexpected error checking positioning service health: {}", e.getMessage(), e);
            
            String errorMessage = "Health check failed: " + e.getMessage();
            return HealthResult.unhealthy(errorMessage, null, latencyMs);
        }
    }

    /**
     * Evaluates the health response to determine if the service is healthy.
     * Parses the JSON response and checks the status field.
     * 
     * @param responseString Raw health response
     * @return HealthEvaluation containing parsed status and health determination
     */
    private HealthEvaluation evaluateHealthResponse(String responseString) {
        final String unknownStatus = "UNKNOWN";
        
        try {
            if (responseString == null || responseString.trim().isEmpty()) {
                log.warn("Health response is empty, considering unhealthy");
                return new HealthEvaluation(false, unknownStatus, "Empty response", null);
            }

            // Parse JSON response
            JsonNode jsonResponse = objectMapper.readTree(responseString);
            
            // Extract status field (Spring Boot Actuator format)
            String status = unknownStatus;
            if (jsonResponse.has("status")) {
                status = jsonResponse.get("status").asText();
            }
            
            // Determine if healthy based on status
            boolean isHealthy = "UP".equalsIgnoreCase(status);
            
            log.debug("Parsed health response - status: {}, healthy: {}", status, isHealthy);
            
            return new HealthEvaluation(isHealthy, status, "Parsed successfully", jsonResponse);
            
        } catch (Exception e) {
            log.warn("Failed to parse health response as JSON: {}, treating as unhealthy", e.getMessage());
            
            // If we can't parse as JSON, check if it's a simple string response
            if (responseString != null) {
                String trimmed = responseString.trim();
                if ("UP".equalsIgnoreCase(trimmed)) {
                    return new HealthEvaluation(true, "UP", "Simple string response", trimmed);
                } else if ("DOWN".equalsIgnoreCase(trimmed)) {
                    return new HealthEvaluation(false, "DOWN", "Simple string response", trimmed);
                }
            }
            
            return new HealthEvaluation(false, unknownStatus, "Parse failed: " + e.getMessage(), responseString);
        }
    }

    /**
     * Container for health evaluation results.
     */
    private static class HealthEvaluation {
        private final boolean isHealthy;
        private final String status;
        private final String reason;
        private final Object parsedResponse;

        public HealthEvaluation(boolean isHealthy, String status, String reason, Object parsedResponse) {
            this.isHealthy = isHealthy;
            this.status = status;
            this.reason = reason;
            this.parsedResponse = parsedResponse;
        }

        public boolean isHealthy() {
            return isHealthy;
        }

        public String getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        public Object getParsedResponse() {
            return parsedResponse;
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
        if (baseUrl.endsWith(PATH_SEPARATOR)) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!path.startsWith(PATH_SEPARATOR)) {
            path = PATH_SEPARATOR + path;
        }
        
        return baseUrl + path;
    }



    /**
     * Result of a health check operation.
     */
    public static class HealthResult {
        private final boolean isHealthy;
        private final String errorMessage;
        private final Integer httpStatus;
        private final Object response;
        private final long latencyMs;
        private final String healthStatus;

        private HealthResult(boolean isHealthy, String errorMessage, Integer httpStatus, Object response, long latencyMs, String healthStatus) {
            this.isHealthy = isHealthy;
            this.errorMessage = errorMessage;
            this.httpStatus = httpStatus;
            this.response = response;
            this.latencyMs = latencyMs;
            this.healthStatus = healthStatus;
        }

        public static HealthResult healthy(Object response, long latencyMs) {
            return new HealthResult(true, null, 200, response, latencyMs, "UP");
        }

        public static HealthResult healthy(Object response, long latencyMs, String healthStatus) {
            return new HealthResult(true, null, 200, response, latencyMs, healthStatus);
        }

        public static HealthResult unhealthy(String errorMessage, Integer httpStatus, long latencyMs) {
            return new HealthResult(false, errorMessage, httpStatus, null, latencyMs, "DOWN");
        }

        public static HealthResult unhealthy(String errorMessage, Integer httpStatus, long latencyMs, Object response) {
            return new HealthResult(false, errorMessage, httpStatus, response, latencyMs, "DOWN");
        }

        public static HealthResult unhealthy(String errorMessage, Integer httpStatus, long latencyMs, String healthStatus) {
            return new HealthResult(false, errorMessage, httpStatus, null, latencyMs, healthStatus);
        }

        public static HealthResult unhealthy(String errorMessage, Integer httpStatus, long latencyMs, String healthStatus, Object response) {
            return new HealthResult(false, errorMessage, httpStatus, response, latencyMs, healthStatus);
        }

        public boolean isHealthy() {
            return isHealthy;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Integer getHttpStatus() {
            return httpStatus;
        }

        public Object getResponse() {
            return response;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public String getHealthStatus() {
            return healthStatus;
        }
    }
}
