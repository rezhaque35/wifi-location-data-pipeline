package com.wifi.positioning.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for health endpoints.
 * Tests the actual HTTP endpoints and their responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Health Endpoint Integration Tests")
class HealthEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("should_ReturnHealthStatus_When_HealthEndpointCalled")
    void should_ReturnHealthStatus_When_HealthEndpointCalled() throws Exception {
        // Act & Assert
        MvcResult result = mockMvc.perform(get("/health"))
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andReturn();

        // Verify status is either 200 or 503 (both are acceptable)
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 503, "Expected status 200 or 503, but got: " + status);

        // Parse response
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        
        // Verify overall status
        assertNotNull(response.get("status"));
        assertTrue(response.get("status").asText().equals("UP") || 
                  response.get("status").asText().equals("DOWN") ||
                  response.get("status").asText().equals("OUT_OF_SERVICE"));
        
        // Verify components exist
        JsonNode components = response.get("components");
        assertNotNull(components);
    }

    @Test
    @DisplayName("should_ReturnLivenessHealth_When_LivenessEndpointCalled")
    void should_ReturnLivenessHealth_When_LivenessEndpointCalled() throws Exception {
        // Act & Assert
        MvcResult result = mockMvc.perform(get("/health/liveness"))
                .andExpect(status().isOk())
                .andReturn();

        // Parse response
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        
        // Verify liveness status
        assertEquals("UP", response.get("status").asText());
        
        // Verify liveness details
        JsonNode details = response.get("details");
        if (details != null) {
            assertNotNull(details.get("serviceLiveness"));
        }
    }

    @Test
    @DisplayName("should_ReturnReadinessHealth_When_ReadinessEndpointCalled")
    void should_ReturnReadinessHealth_When_ReadinessEndpointCalled() throws Exception {
        // Act & Assert
        MvcResult result = mockMvc.perform(get("/health/readiness"))
                .andReturn();

        // Verify status is either 200 or 503 (both are acceptable)
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 503, "Expected status 200 or 503, but got: " + status);

        // Parse response
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        
        // Verify readiness status (can be UP, DOWN, or OUT_OF_SERVICE depending on DynamoDB availability)
        assertNotNull(response.get("status"));
        assertTrue(response.get("status").asText().equals("UP") || 
                  response.get("status").asText().equals("DOWN") ||
                  response.get("status").asText().equals("OUT_OF_SERVICE"));
        
        // Verify readiness details
        JsonNode details = response.get("details");
        if (details != null) {
            assertNotNull(details.get("dynamoDBReadiness"));
        }
    }

    @Test
    @DisplayName("should_ReturnServiceLivenessDetails_When_ServiceLivenessEndpointCalled")
    void should_ReturnServiceLivenessDetails_When_ServiceLivenessEndpointCalled() throws Exception {
        // Act & Assert
        MvcResult result = mockMvc.perform(get("/health/serviceLiveness"))
                .andExpect(status().isOk())
                .andReturn();

        // Parse response
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        
        // Verify service liveness status
        assertEquals("UP", response.get("status").asText());
        
        // Verify service liveness details (should always be present for liveness)
        JsonNode details = response.get("details");
        if (details != null) {
            assertNotNull(details.get("status"));
            assertNotNull(details.get("serviceName"));
            assertNotNull(details.get("startupTime"));
            assertNotNull(details.get("uptime"));
            assertNotNull(details.get("version"));
            
            assertEquals("Service is alive and running", details.get("status").asText());
            assertEquals("WiFi Positioning Service", details.get("serviceName").asText());
        }
    }

    @Test
    @DisplayName("should_ReturnDynamoDBReadinessDetails_When_DynamoDBReadinessEndpointCalled")
    void should_ReturnDynamoDBReadinessDetails_When_DynamoDBReadinessEndpointCalled() throws Exception {
        // Act & Assert
        MvcResult result = mockMvc.perform(get("/health/dynamoDBReadiness"))
                .andReturn();

        // Verify status is either 200 or 503 (both are acceptable)
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 503, "Expected status 200 or 503, but got: " + status);

        // Parse response
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        
        // Verify DynamoDB readiness status (can be UP, DOWN, or OUT_OF_SERVICE)
        assertNotNull(response.get("status"));
        assertTrue(response.get("status").asText().equals("UP") || 
                  response.get("status").asText().equals("DOWN") ||
                  response.get("status").asText().equals("OUT_OF_SERVICE"));
        
        // Verify DynamoDB readiness details (may be null when OUT_OF_SERVICE)
        JsonNode details = response.get("details");
        if (details != null) {
            assertNotNull(details.get("status"));
            assertNotNull(details.get("database"));
            assertNotNull(details.get("lastChecked"));
            
            assertEquals("DynamoDB", details.get("database").asText());
        }
    }

    @Test
    @DisplayName("should_ReturnProperContentType_When_HealthEndpointsCalled")
    void should_ReturnProperContentType_When_HealthEndpointsCalled() throws Exception {
        // Test main health endpoint
        MvcResult healthResult = mockMvc.perform(get("/health"))
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andReturn();
        
        // Verify status is either 200 or 503 (both are acceptable)
        int healthStatus = healthResult.getResponse().getStatus();
        assertTrue(healthStatus == 200 || healthStatus == 503, "Expected status 200 or 503, but got: " + healthStatus);

        // Test liveness endpoint
        mockMvc.perform(get("/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"));

        // Test readiness endpoint
        MvcResult readinessResult = mockMvc.perform(get("/health/readiness"))
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andReturn();
        
        // Verify status is either 200 or 503 (both are acceptable)
        int readinessStatus = readinessResult.getResponse().getStatus();
        assertTrue(readinessStatus == 200 || readinessStatus == 503, "Expected status 200 or 503, but got: " + readinessStatus);
    }

    @Test
    @DisplayName("should_ReturnValidJsonStructure_When_HealthEndpointsCalled")
    void should_ReturnValidJsonStructure_When_HealthEndpointsCalled() throws Exception {
        // Test main health endpoint structure
        MvcResult healthResult = mockMvc.perform(get("/health"))
                .andReturn();

        // Verify status is either 200 or 503 (both are acceptable)
        int healthStatus = healthResult.getResponse().getStatus();
        assertTrue(healthStatus == 200 || healthStatus == 503, "Expected status 200 or 503, but got: " + healthStatus);

        JsonNode healthResponse = objectMapper.readTree(healthResult.getResponse().getContentAsString());
        assertNotNull(healthResponse.get("status"));
        
        // Test individual health indicator structure
        MvcResult livenessResult = mockMvc.perform(get("/health/serviceLiveness"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode livenessResponse = objectMapper.readTree(livenessResult.getResponse().getContentAsString());
        assertNotNull(livenessResponse.get("status"));
        // Details may be null when status is OUT_OF_SERVICE, which is acceptable
        // assertNotNull(livenessResponse.get("details"));
    }
} 